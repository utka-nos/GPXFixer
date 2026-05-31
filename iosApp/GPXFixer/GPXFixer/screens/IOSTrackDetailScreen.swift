import SwiftUI
import shared

struct IOSTrackDetailScreen: View {
    let track: ImportedTrack

    @StateObject private var viewModel = IOSTrackDetailViewModel()
    @State private var isEditPlaceholderPresented = false

    var body: some View {
        List {
            if viewModel.isLoading {
                ProgressView("Loading")
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
            }

            if let detail = viewModel.detail {
                Section {
                    Text(detail.importedTrack.originalFileName)
                        .foregroundStyle(.secondary)
                }

                TrackMapSection(document: detail.document)

                SummarySection(
                    title: "Summary",
                    importedAt: detail.importedTrack.importedAt,
                    summary: detail.summary
                )

                WarningsSection(warnings: detail.warnings)
                TrackSegmentsSection(segments: detail.segments)
            }
        }
        .navigationTitle(track.displayName)
        .toolbar {
            if let detail = viewModel.detail {
                TrackActionsMenu(
                    detail: detail,
                    onTrimSave: { updatedTrack in
                        viewModel.load(track: updatedTrack, force: true)
                    },
                    onEdit: {
                        isEditPlaceholderPresented = true
                    },
                    onExport: {
                        viewModel.exportTrack()
                    }
                )
            }
        }
        .onAppear {
            viewModel.load(track: track)
        }
        .alert("Editing is not available yet.", isPresented: $isEditPlaceholderPresented) {
            Button("OK", role: .cancel) {}
        }
        .sheet(isPresented: $viewModel.isShareSheetPresented) {
            if let url = viewModel.exportURL {
                ActivityView(activityItems: [url])
            }
        }
    }
}

private struct TrackActionsMenu: View {
    let detail: TrackDetail
    let onTrimSave: (ImportedTrack) -> Void
    let onEdit: () -> Void
    let onExport: () -> Void

    var body: some View {
        Menu {
            NavigationLink {
                IOSTrackTrimScreen(
                    detail: detail,
                    onSave: onTrimSave
                )
            } label: {
                Label("Trim track", systemImage: "scissors")
            }

            Button(action: onEdit) {
                Label("Edit", systemImage: "pencil")
            }

            Button(action: onExport) {
                Label("Export GPX", systemImage: "square.and.arrow.up")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }
}
