import Foundation
import Combine
import CoreLocation
import shared

/// Owns the shared TrackRecorder, feeds it CLLocationManager fixes, mirrors
/// every event into the crash-safe journal and saves the finished recording.
/// A singleton so the recording survives navigation away from the screen.
final class RecordingSession: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = RecordingSession()

    @Published private(set) var stats: RecordingStats?
    @Published private(set) var lastSaveMessage: String?
    @Published private(set) var authorizationDenied = false
    @Published private(set) var recoveredRecording: RecordedActivity?

    private let facade = IosRecordingFacade()
    private let powerSensorFacade = IosPowerSensorFacade()
    private let manager = CLLocationManager()
    private let journalQueue = DispatchQueue(label: "com.gpxeditor.recording.journal")
    private var recorder: TrackRecorder?
    private var ticker: Timer?
    private var pendingStart = false

    private override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.activityType = .fitness
        manager.pausesLocationUpdatesAutomatically = false
    }

    var isRecording: Bool { stats != nil }

    var isPaused: Bool { stats?.state == RecordingState.paused }

    // MARK: - Controls

    func start() {
        guard recorder == nil else { return }

        switch manager.authorizationStatus {
        case .notDetermined:
            pendingStart = true
            manager.requestWhenInUseAuthorization()
            return
        case .denied, .restricted:
            authorizationDenied = true
            return
        default:
            break
        }

        authorizationDenied = false
        lastSaveMessage = nil

        let startedAt = nowMillis()
        let startedRecorder = TrackRecorder(
            config: TrackRecorderConfig(sport: "cycling", maxHorizontalAccuracyMeters: 50.0)
        )
        recorder = startedRecorder
        startedRecorder.start(atEpochMillis: startedAt)

        journalQueue.async {
            self.removeJournalFile()
            self.appendJournalLine(RecordingJournal.shared.startLine(epochMillis: startedAt))
        }

        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        powerSensorFacade.connectSaved()
        startTicker()
        publishStats()
    }

    func pause() {
        guard let recorder, recorder.state == RecordingState.recording else { return }

        let pausedAt = nowMillis()
        recorder.pause(atEpochMillis: pausedAt)
        journalQueue.async {
            self.appendJournalLine(RecordingJournal.shared.pauseLine(epochMillis: pausedAt))
        }
        manager.stopUpdatingLocation()
        publishStats()
    }

    func resume() {
        guard let recorder, recorder.state == RecordingState.paused else { return }

        let resumedAt = nowMillis()
        recorder.resume(atEpochMillis: resumedAt)
        journalQueue.async {
            self.appendJournalLine(RecordingJournal.shared.resumeLine(epochMillis: resumedAt))
        }
        manager.startUpdatingLocation()
        publishStats()
    }

    func stop() {
        guard let recorder else { return }
        self.recorder = nil

        let recorded = recorder.stop(atEpochMillis: nowMillis(), name: nil)
        stats = nil
        stopTicker()
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        powerSensorFacade.disconnect()

        journalQueue.async {
            let message = self.saveClearingJournal(recorded.document)
            DispatchQueue.main.async {
                self.lastSaveMessage = message
            }
        }
    }

    // MARK: - Crash recovery

    func checkForUnfinishedRecording() {
        guard recorder == nil, recoveredRecording == nil else { return }

        journalQueue.async {
            guard let content = try? String(contentsOf: self.journalURL, encoding: .utf8),
                  !content.isEmpty else {
                return
            }

            let recovered = RecordingJournal.shared.recover(content: content)
            DispatchQueue.main.async {
                if let recovered, recovered.document.pointCount > 0, self.recorder == nil {
                    self.recoveredRecording = recovered
                } else if self.recorder == nil {
                    self.journalQueue.async { self.removeJournalFile() }
                }
            }
        }
    }

    func restoreRecoveredRecording(onFinished: @escaping (String) -> Void) {
        guard let recovered = recoveredRecording else { return }
        recoveredRecording = nil
        let recordingIsActive = recorder != nil

        journalQueue.async {
            let message: String
            if let result = self.facade.saveRecordingOrNull(document: recovered.document) {
                if !recordingIsActive {
                    self.removeJournalFile()
                }
                if let success = result as? SaveRecordedTrackResultSuccess {
                    message = "Restored \(success.importedTrack.displayName)"
                } else if let failure = result as? SaveRecordedTrackResultFailure {
                    message = failure.message
                } else {
                    message = "Failed to restore recording"
                }
            } else {
                message = "Failed to restore recording"
            }

            DispatchQueue.main.async { onFinished(message) }
        }
    }

    func discardRecoveredRecording() {
        recoveredRecording = nil
        let recordingIsActive = recorder != nil

        journalQueue.async {
            if !recordingIsActive {
                self.removeJournalFile()
            }
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            authorizationDenied = false
            if pendingStart {
                pendingStart = false
                start()
            }
        case .denied, .restricted:
            pendingStart = false
            authorizationDenied = true
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let recorder else { return }

        for location in locations {
            let sample = LocationSample(
                epochMillis: Int64(location.timestamp.timeIntervalSince1970 * 1_000),
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                elevationMeters: location.verticalAccuracy >= 0
                    ? KotlinDouble(value: location.altitude)
                    : nil,
                horizontalAccuracyMeters: location.horizontalAccuracy >= 0
                    ? KotlinDouble(value: location.horizontalAccuracy)
                    : nil,
                speedMetersPerSecond: location.speed >= 0
                    ? KotlinDouble(value: location.speed)
                    : nil
            )

            if recorder.onLocation(sample: sample) {
                journalQueue.async {
                    self.appendJournalLine(RecordingJournal.shared.pointLine(sample: sample))
                }
            }
        }
    }

    // MARK: - Internals

    /// Saves the finished document. The journal is kept when saving throws,
    /// so the recording can still be restored on the next launch.
    private func saveClearingJournal(_ document: ActivityDocument) -> String {
        guard let result = facade.saveRecordingOrNull(document: document) else {
            return "Failed to save recording"
        }

        removeJournalFile()
        if let success = result as? SaveRecordedTrackResultSuccess {
            return "Saved \(success.importedTrack.displayName)"
        }
        if let failure = result as? SaveRecordedTrackResultFailure {
            return failure.message
        }
        return "Failed to save recording"
    }

    private func startTicker() {
        ticker = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.publishStats()
        }
    }

    private func stopTicker() {
        ticker?.invalidate()
        ticker = nil
    }

    private func publishStats() {
        stats = recorder?.stats(nowEpochMillis: nowMillis())
    }

    private func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1_000)
    }

    private var journalURL: URL {
        FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("recording/journal.log")
    }

    private func appendJournalLine(_ line: String) {
        let url = journalURL
        let data = Data((line + "\n").utf8)
        let fileManager = FileManager.default

        if !fileManager.fileExists(atPath: url.path) {
            try? fileManager.createDirectory(
                at: url.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            fileManager.createFile(atPath: url.path, contents: data)
            return
        }

        guard let handle = try? FileHandle(forWritingTo: url) else { return }
        defer { try? handle.close() }
        _ = try? handle.seekToEnd()
        try? handle.write(contentsOf: data)
    }

    private func removeJournalFile() {
        try? FileManager.default.removeItem(at: journalURL)
    }
}
