import Combine
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import shared

struct IOSImportScreen: View {
    @StateObject private var viewModel = IOSImportViewModel()
    @State private var isShowingImporter = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        isShowingImporter = true
                    } label: {
                        Label("Import GPX", systemImage: "square.and.arrow.down")
                    }
                    .disabled(viewModel.isImporting)

                    if viewModel.isImporting {
                        ProgressView("Importing")
                    }

                    if let statusMessage = viewModel.statusMessage {
                        Text(statusMessage)
                            .foregroundStyle(.green)
                    }

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    }
                }

                Section("Imported tracks") {
                    if viewModel.tracks.isEmpty {
                        Text("No GPX tracks imported yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.tracks) { track in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(track.displayName)
                                    .font(.headline)
                                Text(track.originalFileName)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Text("\(track.trackCount) tracks / \(track.pointCount) points")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text("Imported at \(track.importedAt)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            .navigationTitle("GPXFixer")
            .fileImporterSheet(isPresented: $isShowingImporter) { url in
                viewModel.importGpx(from: url)
            }
            .onOpenURL { url in
                viewModel.importGpx(from: url)
            }
            .onAppear {
                viewModel.loadHistory()
            }
        }
    }
}

private extension View {
    func fileImporterSheet(
        isPresented: Binding<Bool>,
        onPick: @escaping (URL) -> Void
    ) -> some View {
        sheet(isPresented: isPresented) {
            GpxDocumentPicker(onPick: onPick)
        }
    }
}

private struct GpxDocumentPicker: UIViewControllerRepresentable {
    let onPick: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let gpxType = UTType(filenameExtension: "gpx") ?? .xml
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [gpxType, .xml, .data],
            asCopy: true
        )
        picker.allowsMultipleSelection = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {
    }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        private let onPick: (URL) -> Void

        init(onPick: @escaping (URL) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            onPick(url)
        }
    }
}

@MainActor
private final class IOSImportViewModel: ObservableObject {
    @Published var tracks: [ImportedTrackMetadata] = []
    @Published var isImporting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let store = IOSImportedTrackStore()
    private let fileStorage = IOSGpxTrackFileStorage()

    func loadHistory() {
        do {
            tracks = try store.getAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func importGpx(from url: URL) {
        isImporting = true
        statusMessage = nil
        errorMessage = nil

        Task {
            do {
                let importedTrack = try importTrack(from: url)
                tracks = try store.getAll()
                statusMessage = "Imported \(importedTrack.displayName)"
            } catch {
                errorMessage = error.localizedDescription
            }

            isImporting = false
        }
    }

    private func importTrack(from url: URL) throws -> ImportedTrackMetadata {
        let shouldStopAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if shouldStopAccessing {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let content = try String(contentsOf: url, encoding: .utf8)
        let parseResult = GpxParser.shared.parse(xml: content)

        if let failure = parseResult as? GpxParseResultFailure {
            throw IOSImportError.invalidGpx(failure.error.message)
        }

        guard let success = parseResult as? GpxParseResultSuccess else {
            throw IOSImportError.invalidGpx("Failed to parse GPX file")
        }

        let id = UUID().uuidString
        let storageKey = "tracks/\(id).gpx"
        let importedTrack = ImportedTrackMetadata(
            id: id,
            displayName: displayName(
                originalFileName: url.lastPathComponent,
                document: success.document
            ),
            originalFileName: url.lastPathComponent,
            importedAt: ISO8601DateFormatter().string(from: Date()),
            storageKey: storageKey,
            trackCount: Int(success.document.tracks.count),
            pointCount: Int(success.document.pointCount)
        )

        try fileStorage.save(storageKey: storageKey, content: content)
        do {
            try store.add(importedTrack)
        } catch {
            try? fileStorage.delete(storageKey: storageKey)
            throw error
        }

        return importedTrack
    }

    private func displayName(originalFileName: String, document: GpxDocument) -> String {
        if let metadataName = document.metadata?.name?.trimmingCharacters(in: .whitespacesAndNewlines),
           !metadataName.isEmpty {
            return metadataName
        }

        if let trackName = document.tracks.compactMap({ $0.name?.trimmingCharacters(in: .whitespacesAndNewlines) }).first(where: { !$0.isEmpty }) {
            return trackName
        }

        let fileName = originalFileName.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameWithoutExtension = (fileName as NSString).deletingPathExtension
        return nameWithoutExtension.isEmpty ? "Untitled track" : nameWithoutExtension
    }
}

private struct ImportedTrackMetadata: Codable, Identifiable, Equatable {
    let id: String
    let displayName: String
    let originalFileName: String
    let importedAt: String
    let storageKey: String
    let trackCount: Int
    let pointCount: Int
}

private final class IOSGpxTrackFileStorage {
    private let rootDirectory: URL

    init(fileManager: FileManager = .default) {
        rootDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    func save(storageKey: String, content: String) throws {
        let url = try urlFor(storageKey: storageKey)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try content.write(to: url, atomically: true, encoding: .utf8)
    }

    func read(storageKey: String) throws -> String {
        try String(contentsOf: try urlFor(storageKey: storageKey), encoding: .utf8)
    }

    func delete(storageKey: String) throws {
        let url = try urlFor(storageKey: storageKey)
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }

    private func urlFor(storageKey: String) throws -> URL {
        let url = rootDirectory.appendingPathComponent(storageKey)
        let rootPath = rootDirectory.standardizedFileURL.path
        let filePath = url.standardizedFileURL.path

        guard filePath == rootPath || filePath.hasPrefix(rootPath + "/") else {
            throw IOSImportError.invalidStorageKey
        }

        return url
    }
}

private final class IOSImportedTrackStore {
    private let metadataURL: URL
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init(fileManager: FileManager = .default) {
        metadataURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("imported_tracks.json")
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    }

    func getAll() throws -> [ImportedTrackMetadata] {
        guard FileManager.default.fileExists(atPath: metadataURL.path) else {
            return []
        }

        let data = try Data(contentsOf: metadataURL)
        guard !data.isEmpty else {
            return []
        }

        return try decoder.decode([ImportedTrackMetadata].self, from: data)
    }

    func add(_ track: ImportedTrackMetadata) throws {
        var tracks = try getAll().filter { $0.id != track.id }
        tracks.insert(track, at: 0)
        try writeAll(tracks)
    }

    func remove(id: String) throws {
        try writeAll(getAll().filter { $0.id != id })
    }

    private func writeAll(_ tracks: [ImportedTrackMetadata]) throws {
        try FileManager.default.createDirectory(
            at: metadataURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try encoder.encode(tracks).write(to: metadataURL, options: .atomic)
    }
}

private enum IOSImportError: LocalizedError {
    case invalidGpx(String)
    case invalidStorageKey

    var errorDescription: String? {
        switch self {
        case .invalidGpx(let message):
            return message
        case .invalidStorageKey:
            return "Storage key points outside app storage"
        }
    }
}
