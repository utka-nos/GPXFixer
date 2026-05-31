import Foundation
import Combine
import SwiftUI
import shared

@MainActor
final class IOSImportViewModel: ObservableObject {
    @Published var tracks: [ImportedTrack] = []
    @Published var isImporting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let importFacade = IosImportFacade()

    func loadHistory() {
        tracks = importFacade.getImportedTracks()
    }

    func importGpx(from url: URL) {
        isImporting = true
        statusMessage = nil
        errorMessage = nil

        Task {
            do {
                let importedTrack = try importTrack(from: url)
                tracks = importFacade.getImportedTracks()
                statusMessage = "Imported \(importedTrack.displayName)"
            } catch {
                errorMessage = error.localizedDescription
            }

            isImporting = false
        }
    }

    private func importTrack(from url: URL) throws -> ImportedTrack {
        let shouldStopAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if shouldStopAccessing {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let content = try String(contentsOf: url, encoding: .utf8)
        let result = importFacade.importGpx(
            originalFileName: url.lastPathComponent,
            content: content
        )

        if let failure = result as? ImportGpxTrackResultFailure {
            throw IOSImportError.invalidGpx(failure.error.message)
        }

        if let success = result as? ImportGpxTrackResultSuccess {
            return success.importedTrack
        }

        throw IOSImportError.invalidGpx("Failed to import GPX file")
    }
}
