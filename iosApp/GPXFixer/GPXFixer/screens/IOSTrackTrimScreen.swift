import SwiftUI
import shared

struct IOSTrackTrimScreen: View {
    let detail: TrackDetail
    let onSave: (ImportedTrack) -> Void

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: IOSTrackTrimViewModel

    init(
        detail: TrackDetail,
        onSave: @escaping (ImportedTrack) -> Void
    ) {
        self.detail = detail
        self.onSave = onSave
        _viewModel = StateObject(
            wrappedValue: IOSTrackTrimViewModel(detail: detail)
        )
    }

    var body: some View {
        List {
            TrackMapSection(document: viewModel.previewDocument ?? detail.document)

            TrackTrimControlsSection(viewModel: viewModel)

            SummarySection(
                title: "Preview summary",
                importedAt: nil,
                summary: viewModel.previewSummary ?? detail.summary
            )
        }
        .navigationTitle("Trim track")
        .toolbar {
            Button("Save") {
                if let track = viewModel.saveTrimmedTrack() {
                    onSave(track)
                    dismiss()
                }
            }
            .disabled(!viewModel.canSave)
        }
        .onAppear {
            viewModel.applyTrim()
        }
    }
}

private struct TrackTrimControlsSection: View {
    @ObservedObject var viewModel: IOSTrackTrimViewModel

    var body: some View {
        Section("Trim") {
            if viewModel.originalPointCount > 0 {
                TrimPointStepper(
                    title: "Start point",
                    value: Binding(
                        get: { viewModel.trimStartIndex },
                        set: { viewModel.setTrimStartIndex($0) }
                    ),
                    range: 0...viewModel.lastPointIndex
                )

                TrimPointStepper(
                    title: "End point",
                    value: Binding(
                        get: { viewModel.trimEndIndex },
                        set: { viewModel.setTrimEndIndex($0) }
                    ),
                    range: 0...viewModel.lastPointIndex
                )

                TrimPointSlider(
                    title: "Start slider",
                    value: Binding(
                        get: { viewModel.trimStartIndex },
                        set: { viewModel.setTrimStartIndex($0) }
                    ),
                    lastPointIndex: viewModel.lastPointIndex
                )

                TrimPointSlider(
                    title: "End slider",
                    value: Binding(
                        get: { viewModel.trimEndIndex },
                        set: { viewModel.setTrimEndIndex($0) }
                    ),
                    lastPointIndex: viewModel.lastPointIndex
                )

                DetailRow(
                    label: "Kept points",
                    value: "\(viewModel.selectedPointCount) of \(viewModel.originalPointCount)"
                )

                if viewModel.removedPointCount > 0 {
                    DetailRow(label: "Removed points", value: "\(viewModel.removedPointCount)")
                }

                Button("Reset trim") {
                    viewModel.resetTrimRange()
                }
            } else {
                Text("No track points found.")
                    .foregroundStyle(.secondary)
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
            }

            if let statusMessage = viewModel.statusMessage {
                Text(statusMessage)
                    .foregroundStyle(.green)
            }
        }
    }
}

private struct TrimPointStepper: View {
    let title: String
    @Binding var value: Int
    let range: ClosedRange<Int>

    var body: some View {
        Stepper(
            value: $value,
            in: range
        ) {
            DetailRow(label: title, value: "\(value + 1)")
        }
    }
}

private struct TrimPointSlider: View {
    let title: String
    @Binding var value: Int
    let lastPointIndex: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            DetailRow(label: title, value: "\(value + 1)")
            Slider(
                value: Binding(
                    get: { Double(value) },
                    set: { value = Int($0.rounded()) }
                ),
                in: 0...Double(lastPointIndex),
                step: 1
            )
        }
    }
}
