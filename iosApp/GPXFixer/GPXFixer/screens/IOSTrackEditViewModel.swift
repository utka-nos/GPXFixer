import Combine
import SwiftUI
import shared

@MainActor
final class IOSTrackEditViewModel: ObservableObject {
    @Published var document: GpxDocument
    @Published var selectedPointIndex: Int?
    @Published var errorMessage: String?
    @Published private(set) var hasChanges = false

    private let detail: TrackDetail
    private let importFacade = IosImportFacade()

    init(detail: TrackDetail) {
        self.detail = detail
        self.document = detail.document
    }

    func deleteSelectedPoint() {
        guard let selectedPointIndex else { return }

        let result = importFacade.deleteTrackPoint(
            document: document,
            pointIndex: Int32(selectedPointIndex)
        )

        if let failure = result as? DeleteGpxTrackPointResultFailure {
            errorMessage = failure.error.message
        } else if let success = result as? DeleteGpxTrackPointResultSuccess {
            document = success.document
            self.selectedPointIndex = nil
            errorMessage = nil
            hasChanges = true
        } else {
            errorMessage = "Failed to delete track point."
        }
    }

    func saveEditedTrack() -> ImportedTrack {
        let importedTrack = importFacade.overwriteTrack(
            track: detail.importedTrack,
            document: document
        )

        errorMessage = nil
        return importedTrack
    }
}
