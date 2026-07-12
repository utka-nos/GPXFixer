import SwiftUI
import shared

struct IOSTrackDetailScreen: View {
    let track: ImportedTrack

    @StateObject private var viewModel = IOSTrackDetailViewModel()
    @State private var isRenameAlertPresented = false
    @State private var renameText = ""

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

                TrackChartSection(metric: .power, samples: detail.powerSamples)
                TrackChartSection(metric: .heartRate, samples: detail.heartRateSamples)
                TrackChartSection(metric: .speed, samples: detail.speedSamples)

                WarningsSection(warnings: detail.warnings)
                TrackSegmentsSection(segments: detail.segments)
            }
        }
        .navigationTitle(viewModel.detail?.importedTrack.displayName ?? track.displayName)
        .toolbar {
            if let detail = viewModel.detail {
                TrackActionsMenu(
                    detail: detail,
                    onRename: {
                        renameText = detail.importedTrack.displayName
                        isRenameAlertPresented = true
                    },
                    onTrimSave: { updatedTrack in
                        viewModel.load(track: updatedTrack, force: true)
                    },
                    onEditSave: { updatedTrack in
                        viewModel.load(track: updatedTrack, force: true)
                    },
                    onExportGpx: {
                        viewModel.exportTrack(asFit: false)
                    },
                    onExportFit: {
                        viewModel.exportTrack(asFit: true)
                    }
                )
            }
        }
        .onAppear {
            viewModel.load(track: track)
        }
        .alert("Rename track", isPresented: $isRenameAlertPresented) {
            TextField("Track name", text: $renameText)
            Button("Cancel", role: .cancel) {}
            Button("Rename") {
                viewModel.renameTrack(to: renameText)
            }
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
    let onRename: () -> Void
    let onTrimSave: (ImportedTrack) -> Void
    let onEditSave: (ImportedTrack) -> Void
    let onExportGpx: () -> Void
    let onExportFit: () -> Void

    var body: some View {
        Menu {
            Button(action: onRename) {
                Label("Rename", systemImage: "pencil.line")
            }

            NavigationLink {
                IOSTrackTrimScreen(
                    detail: detail,
                    onSave: onTrimSave
                )
            } label: {
                Label("Trim track", systemImage: "scissors")
            }

            NavigationLink {
                IOSTrackEditScreen(
                    detail: detail,
                    onSave: onEditSave
                )
            } label: {
                Label("Edit", systemImage: "pencil")
            }

            Button(action: onExportGpx) {
                Label("Export GPX", systemImage: "square.and.arrow.up")
            }

            Button(action: onExportFit) {
                Label("Export FIT", systemImage: "square.and.arrow.up")
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }
}
