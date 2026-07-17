import SwiftUI
import shared

struct IOSImportScreen: View {
    @StateObject private var viewModel = IOSImportViewModel()
    @ObservedObject private var recordingSession = RecordingSession.shared
    @State private var isShowingImporter = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        isShowingImporter = true
                    } label: {
                        Label("Import track", systemImage: "square.and.arrow.down")
                    }
                    .disabled(viewModel.isImporting)

                    NavigationLink {
                        IOSRecordingScreen()
                    } label: {
                        Label(
                            recordingSession.isRecording ? "Recording…" : "Record track",
                            systemImage: "record.circle"
                        )
                    }

                    NavigationLink {
                        IOSPowerSensorScreen()
                    } label: {
                        Label("Power sensor", systemImage: "sensor")
                    }

                    NavigationLink {
                        IOSHeartRateSensorScreen()
                    } label: {
                        Label("Heart rate sensor", systemImage: "heart")
                    }

                    NavigationLink {
                        IOSProfileScreen()
                    } label: {
                        Label("Profile", systemImage: "person.crop.circle")
                    }

                    if viewModel.isImporting {
                        ProgressView("Importing")
                    }

                    if let statusMessage = viewModel.statusMessage {
                        Text(statusMessage)
                            .foregroundStyle(.green)
                    }

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    }
                }

                Section("Imported tracks") {
                    if viewModel.tracks.isEmpty {
                        Text("No tracks imported yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.tracks, id: \.id) { track in
                            NavigationLink {
                                IOSTrackDetailScreen(track: track)
                            } label: {
                                ImportedTrackRow(track: track)
                            }
                        }
                        .onDelete { offsets in
                            viewModel.deleteTracks(at: offsets)
                        }
                    }
                }
            }
            .navigationTitle("GPXFixer")
            .fileImporterSheet(isPresented: $isShowingImporter) { url in
                viewModel.importTrack(from: url)
            }
            .onOpenURL { url in
                viewModel.importTrack(from: url)
            }
            .onAppear {
                viewModel.loadHistory()
                recordingSession.checkForUnfinishedRecording()
            }
            .alert(
                "Unfinished recording found",
                isPresented: Binding(
                    get: { recordingSession.recoveredRecording != nil },
                    set: { isPresented in
                        if !isPresented {
                            recordingSession.discardRecoveredRecording()
                        }
                    }
                ),
                presenting: recordingSession.recoveredRecording
            ) { _ in
                Button("Restore") {
                    recordingSession.restoreRecoveredRecording { message in
                        viewModel.statusMessage = message
                        viewModel.loadHistory()
                    }
                }
                Button("Discard", role: .destructive) {
                    recordingSession.discardRecoveredRecording()
                }
            } message: { recovered in
                Text(
                    "A recording was interrupted before it could be saved: "
                        + "\(recovered.stats.pointCount) points, "
                        + "\(formatDistance(recovered.stats.distanceMeters)). Restore it?"
                )
            }
        }
    }
}

private struct ImportedTrackRow: View {
    let track: ImportedTrack

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(track.displayName)
                .font(.headline)
            Text(track.originalFileName)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\(track.trackCount) tracks / \(track.pointCount) points")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Imported at \(track.importedAt)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
