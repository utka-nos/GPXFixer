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
                        ForEach(viewModel.tracks, id: \.id) { track in
                            NavigationLink {
                                IOSTrackDetailScreen(track: track)
                            } label: {
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

private enum IOSImportError: LocalizedError {
    case invalidGpx(String)

    var errorDescription: String? {
        switch self {
        case .invalidGpx(let message):
            return message
        }
    }
}
