import Combine
import SwiftUI
import shared

@MainActor
final class IOSTrackEditViewModel: ObservableObject {
    @Published var document: GpxDocument
    @Published var selectedPointIndex: Int?
    @Published var movingPointIndex: Int?
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
            movingPointIndex = nil
            errorMessage = nil
            hasChanges = true
        } else {
            errorMessage = "Failed to delete track point."
        }
    }

    func beginMovingSelectedPoint() {
        guard let selectedPointIndex else { return }

        movingPointIndex = selectedPointIndex
        errorMessage = nil
    }

    func moveSelectedPoint(
        latitude: Double,
        longitude: Double
    ) {
        guard let movingPointIndex else { return }

        let result = importFacade.moveTrackPoint(
            document: document,
            pointIndex: Int32(movingPointIndex),
            latitude: latitude,
            longitude: longitude
        )

        if let failure = result as? MoveGpxTrackPointResultFailure {
            errorMessage = failure.error.message
        } else if let success = result as? MoveGpxTrackPointResultSuccess {
            document = success.document
            selectedPointIndex = Int(success.movedPointIndex)
            self.movingPointIndex = nil
            errorMessage = nil
            hasChanges = true
        } else {
            errorMessage = "Failed to move track point."
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
