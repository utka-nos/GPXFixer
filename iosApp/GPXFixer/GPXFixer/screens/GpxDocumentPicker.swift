import SwiftUI
import UIKit
import UniformTypeIdentifiers

extension View {
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
        let fitType = UTType(filenameExtension: "fit") ?? .data
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [gpxType, fitType, .xml, .data],
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
