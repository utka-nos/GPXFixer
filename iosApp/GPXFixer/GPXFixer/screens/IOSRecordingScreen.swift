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
                Text(formatDuration(stats.elapsedMillis))
                    .font(.system(size: 52, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .frame(maxWidth: .infinity)

                statRow("Distance", formatDistance(stats.distanceMeters))
                statRow("Speed", formatSpeed(stats.currentSpeedMetersPerSecond))
                statRow("Points", "\(stats.pointCount)")

                if stats.state == RecordingState.paused {
                    Text("Paused")
                        .foregroundStyle(.orange)
                } else if stats.pointCount == 0 {
                    Text("Searching for GPS signal…")
                        .foregroundStyle(.secondary)
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
