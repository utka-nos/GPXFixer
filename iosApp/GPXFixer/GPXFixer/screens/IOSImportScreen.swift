import SwiftUI
import shared

struct IOSImportScreen: View {
    @StateObject private var viewModel = IOSImportViewModel()
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
