import Combine
import SwiftUI
import shared

@MainActor
final class IOSTrackTrimViewModel: ObservableObject {
    @Published var trimStartIndex = 0
    @Published var trimEndIndex = 0
    @Published var previewDocument: ActivityDocument?
    @Published var previewSummary: TrackSummary?
    @Published var removedPointCount = 0
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let detail: TrackDetail
    private let importFacade = IosImportFacade()

    init(detail: TrackDetail) {
        self.detail = detail
        trimEndIndex = max(Int(detail.summary.pointCount) - 1, 0)
    }

    var originalPointCount: Int {
        Int(detail.summary.pointCount)
    }

    var lastPointIndex: Int {
        max(originalPointCount - 1, 0)
    }

    var selectedPointCount: Int {
        guard originalPointCount > 0 else { return 0 }
        return max(trimEndIndex - trimStartIndex + 1, 0)
    }

    var canSave: Bool {
        previewDocument != nil && removedPointCount > 0
    }

    func setTrimStartIndex(_ value: Int) {
        trimStartIndex = min(max(value, 0), lastPointIndex)
        if trimStartIndex > trimEndIndex {
            trimEndIndex = trimStartIndex
        }
        applyTrim()
    }

    func setTrimEndIndex(_ value: Int) {
        trimEndIndex = min(max(value, 0), lastPointIndex)
        if trimEndIndex < trimStartIndex {
            trimStartIndex = trimEndIndex
        }
        applyTrim()
    }

    func resetTrimRange() {
        trimStartIndex = 0
        trimEndIndex = lastPointIndex
        applyTrim()
    }

    func saveTrimmedTrack() -> ImportedTrack? {
        guard let previewDocument else { return nil }

        let importedTrack = importFacade.overwriteTrack(
            track: detail.importedTrack,
            document: previewDocument
        )

        errorMessage = nil
        statusMessage = "Saved \(importedTrack.displayName)"
        return importedTrack
    }

    func applyTrim() {
        guard originalPointCount > 0 else {
            previewDocument = detail.document
            previewSummary = detail.summary
            removedPointCount = 0
            return
        }

        let result = importFacade.trimTrack(
            document: detail.document,
            startPointIndex: Int32(trimStartIndex),
            endPointIndexInclusive: Int32(trimEndIndex)
        )

        if let failure = result as? TrimGpxTrackResultFailure {
            previewDocument = nil
            previewSummary = nil
            removedPointCount = 0
            errorMessage = failure.error.message
            statusMessage = nil
        } else if let success = result as? TrimGpxTrackResultSuccess {
            previewDocument = success.document
            previewSummary = success.summary
            removedPointCount = Int(success.removedPointCount)
            errorMessage = nil
            statusMessage = nil
        } else {
            previewDocument = nil
            previewSummary = nil
            removedPointCount = 0
            errorMessage = "Failed to trim track."
            statusMessage = nil
        }
    }
}
