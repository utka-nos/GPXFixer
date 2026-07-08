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

    func deleteTracks(at offsets: IndexSet) {
        statusMessage = nil
        errorMessage = nil

        for index in offsets {
            let track = tracks[index]
            importFacade.deleteTrack(track: track)
            statusMessage = "Deleted \(track.displayName)"
        }

        tracks = importFacade.getImportedTracks()
    }

    func importTrack(from url: URL) {
        isImporting = true
        statusMessage = nil
        errorMessage = nil

        Task {
            do {
                let importedTrack = try decodeTrack(from: url)
                tracks = importFacade.getImportedTracks()
                statusMessage = "Imported \(importedTrack.displayName)"
            } catch {
                errorMessage = error.localizedDescription
            }

            isImporting = false
        }
    }

    private func decodeTrack(from url: URL) throws -> ImportedTrack {
        let shouldStopAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if shouldStopAccessing {
                url.stopAccessingSecurityScopedResource()
            }
        }

        if url.pathExtension.lowercased() == "fit" {
            return try importFit(from: url)
        }
        return try importGpx(from: url)
    }

    private func importGpx(from url: URL) throws -> ImportedTrack {
        let content = try String(contentsOf: url, encoding: .utf8)
        let result = importFacade.importGpx(
            originalFileName: url.lastPathComponent,
            content: content
        )

        if let failure = result as? ImportGpxTrackResultFailure {
            throw IOSImportError.invalidTrack(failure.error.message)
        }

        if let success = result as? ImportGpxTrackResultSuccess {
            return success.importedTrack
        }

        throw IOSImportError.invalidTrack("Failed to import GPX file")
    }

    private func importFit(from url: URL) throws -> ImportedTrack {
        let data = try Data(contentsOf: url)
        let result = importFacade.importFit(
            originalFileName: url.lastPathComponent,
            content: data
        )

        if let failure = result as? ImportFitTrackResultFailure {
            throw IOSImportError.invalidTrack(failure.error.message)
        }

        if let success = result as? ImportFitTrackResultSuccess {
            return success.importedTrack
        }

        throw IOSImportError.invalidTrack("Failed to import FIT file")
    }
}
