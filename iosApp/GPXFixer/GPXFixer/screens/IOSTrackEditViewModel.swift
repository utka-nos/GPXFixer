import Combine
import SwiftUI
import shared

@MainActor
final class IOSTrackEditViewModel: ObservableObject {
    @Published var document: ActivityDocument
    @Published var selectedPointIndex: Int?
    @Published var movingPointIndex: Int?
    @Published var errorMessage: String?
    @Published private(set) var hasChanges = false
    @Published private(set) var canUndoDelete = false

    private let detail: TrackDetail
    private let importFacade = IosImportFacade()
    private var deletedPointSnapshots: [(document: ActivityDocument, pointIndex: Int)] = []

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
            deletedPointSnapshots.append((document: document, pointIndex: selectedPointIndex))
            canUndoDelete = true
            document = success.document
            self.selectedPointIndex = nil
            movingPointIndex = nil
            errorMessage = nil
            hasChanges = true
        } else {
            errorMessage = "Failed to delete track point."
        }
    }

    func undoLastDeletedPoint() {
        guard let snapshot = deletedPointSnapshots.popLast() else { return }

        document = snapshot.document
        selectedPointIndex = snapshot.pointIndex
        movingPointIndex = nil
        errorMessage = nil
        canUndoDelete = !deletedPointSnapshots.isEmpty
        hasChanges = document != detail.document
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
            // Undo restores a full pre-delete snapshot, which would silently
            // revert this move too — drop the history instead.
            deletedPointSnapshots = []
            canUndoDelete = false
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
