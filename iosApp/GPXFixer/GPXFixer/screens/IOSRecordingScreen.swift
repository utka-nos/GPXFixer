import SwiftUI
import shared

struct IOSRecordingScreen: View {
    @ObservedObject private var session = RecordingSession.shared
    @State private var isShowingStopConfirmation = false

    var body: some View {
        List {
            if let stats = session.stats {
                activeSection(stats: stats)
            } else {
                idleSection
            }
        }
        .navigationTitle("Record track")
        .confirmationDialog(
            "Stop recording?",
            isPresented: $isShowingStopConfirmation,
            titleVisibility: .visible
        ) {
            Button("Stop", role: .destructive) {
                session.stop()
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var idleSection: some View {
        Section {
            if session.authorizationDenied {
                Text(
                    "GPXFixer needs location access to record your track. "
                        + "Allow location access in Settings."
                )
                .foregroundStyle(.secondary)

                if let settingsURL = URL(string: UIApplication.openSettingsURLString) {
                    Link("Open Settings", destination: settingsURL)
                }
            }

            if let message = session.lastSaveMessage {
                Text(message)
                    .foregroundStyle(.green)
            }

            Button {
                session.start()
            } label: {
                Label("Start recording", systemImage: "record.circle")
            }
        }
    }

    private func activeSection(stats: RecordingStats) -> some View {
        Group {
            Section {
                LiveRecordingMapView(segments: session.routeSegments)
                    .frame(height: 240)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
            }

            Section {
                Text(formatDuration(stats.elapsedMillis))
                    .font(.system(size: 52, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .frame(maxWidth: .infinity)

                statRow("Distance", formatDistance(stats.distanceMeters))
                statRow("Speed", formatSpeed(stats.currentSpeedMetersPerSecond))
                statRow("Points", "\(stats.pointCount)")
                statRow("Power", stats.currentPowerWatts.map { "\($0.intValue) W" } ?? "—")
                statRow("Cadence", stats.currentCadenceRpm.map { "\($0.intValue) rpm" } ?? "—")
                statRow("Heart rate", stats.currentHeartRateBpm.map { "\($0.intValue) bpm" } ?? "—")
                Text(powerSensorStatusText)
                    .foregroundStyle(
                        session.powerSensorStatus == .connected ? Color.green : Color.secondary
                    )
                Text(heartRateSensorStatusText)
                    .foregroundStyle(
                        session.heartRateSensorStatus == .connected ? Color.green : Color.secondary
                    )

                if stats.state == RecordingState.paused {
                    Text("Paused")
                        .foregroundStyle(.orange)
                } else if stats.pointCount == 0 {
                    Text("Searching for GPS signal…")
                        .foregroundStyle(.secondary)
                }
            }

            if LiveChartWindow.shared.liveWindow(samples: session.powerChartSamples) != nil {
                Section {
                    LivePowerChartView(samples: session.powerChartSamples)
                        .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                }
            }

            Section {
                if stats.state == RecordingState.paused {
                    Button {
                        session.resume()
                    } label: {
                        Label("Resume", systemImage: "play.fill")
                    }
                } else {
                    Button {
                        session.pause()
                    } label: {
                        Label("Pause", systemImage: "pause.fill")
                    }
                }

                Button(role: .destructive) {
                    isShowingStopConfirmation = true
                } label: {
                    Label("Stop", systemImage: "stop.fill")
                }
            }
        }
    }

    private var powerSensorStatusText: String {
        switch session.powerSensorStatus {
        case .connected:
            return "Power sensor connected"
        case .reconnecting:
            return "Power sensor reconnecting…"
        case .notConnected:
            return "Power sensor not connected"
        case .notConfigured:
            return "No power sensor configured"
        default:
            return "Power sensor not connected"
        }
    }

    private var heartRateSensorStatusText: String {
        switch session.heartRateSensorStatus {
        case .connected:
            return "Heart rate sensor connected"
        case .reconnecting:
            return "Heart rate sensor reconnecting…"
        case .notConnected:
            return "Heart rate sensor not connected"
        case .notConfigured:
            return "No heart rate sensor configured"
        default:
            return "Heart rate sensor not connected"
        }
    }

    private func statRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
    }
}
