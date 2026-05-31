import Foundation
import Combine
import SwiftUI
import shared

@MainActor
final class IOSTrackDetailViewModel: ObservableObject {
    @Published var detail: TrackDetail?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var exportURL: URL?
    @Published var isShareSheetPresented = false

    private let importFacade = IosImportFacade()
    private var loadedTrackId: String?

    func load(
        track: ImportedTrack,
        force: Bool = false
    ) {
        guard force || loadedTrackId != track.id else { return }

        loadedTrackId = track.id
        isLoading = true
        errorMessage = nil

        let result = importFacade.getTrackDetail(track: track)
        if let failure = result as? TrackDetailResultFailure {
            detail = nil
            errorMessage = failure.error.message
        } else if let success = result as? TrackDetailResultSuccess {
            detail = success.detail
        } else {
            detail = nil
            errorMessage = "Failed to open imported track"
        }

        isLoading = false
    }

    func exportTrack() {
        guard let detail else { return }

        let content = importFacade.serializeGpx(document: detail.document)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(exportFileName(for: detail.importedTrack.displayName))

        do {
            try content.write(to: url, atomically: true, encoding: .utf8)
            exportURL = url
            isShareSheetPresented = true
            errorMessage = nil
        } catch {
            errorMessage = "Failed to export GPX file."
        }
    }

    private func exportFileName(for displayName: String) -> String {
        "\(sanitizedFileBaseName(for: displayName)).gpx"
    }
}
