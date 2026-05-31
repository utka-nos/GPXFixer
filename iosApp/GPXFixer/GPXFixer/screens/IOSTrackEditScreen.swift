import MapKit
import SwiftUI
import shared

struct IOSTrackEditScreen: View {
    let detail: TrackDetail
    let onSave: (ImportedTrack) -> Void

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: IOSTrackEditViewModel

    init(
        detail: TrackDetail,
        onSave: @escaping (ImportedTrack) -> Void
    ) {
        self.detail = detail
        self.onSave = onSave
        _viewModel = StateObject(
            wrappedValue: IOSTrackEditViewModel(detail: detail)
        )
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            if EditableTrackMapGeometry(document: viewModel.document) != nil {
                EditableTrackMap(
                    document: viewModel.document,
                    selectedPointIndex: $viewModel.selectedPointIndex
                )
                .ignoresSafeArea(edges: .bottom)
            } else {
                ContentUnavailableView("No track geometry", systemImage: "map")
            }

            if let selectedPointIndex = viewModel.selectedPointIndex {
                SelectedPointMenu(
                    pointIndex: selectedPointIndex,
                    onDelete: {
                        viewModel.deleteSelectedPoint()
                    }
                )
                .padding(16)
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .padding(12)
                    .frame(maxWidth: .infinity)
                    .background(.regularMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding(16)
            }
        }
        .navigationTitle("Edit track")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Save") {
                let updatedTrack = viewModel.saveEditedTrack()
                onSave(updatedTrack)
                dismiss()
            }
            .disabled(!viewModel.hasChanges)
        }
    }
}

private struct SelectedPointMenu: View {
    let pointIndex: Int
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Point \(pointIndex + 1)")
                .font(.headline)

            Text("Index: \(pointIndex)")
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Button(role: .destructive, action: onDelete) {
                    Text("Delete")
                }
                .buttonStyle(.borderedProminent)

                Button("Move") {}
                    .buttonStyle(.bordered)
                    .disabled(true)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct EditableTrackMap: UIViewRepresentable {
    let document: GpxDocument
    @Binding var selectedPointIndex: Int?

    func makeCoordinator() -> Coordinator {
        Coordinator(selectedPointIndex: $selectedPointIndex)
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = true
        mapView.showsScale = true
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        context.coordinator.selectedPointIndex = $selectedPointIndex

        guard let geometry = EditableTrackMapGeometry(document: document) else {
            mapView.removeOverlays(mapView.overlays)
            mapView.removeAnnotations(mapView.annotations)
            context.coordinator.didSetInitialVisibleMapRect = false
            return
        }

        mapView.removeOverlays(mapView.overlays)
        mapView.removeAnnotations(mapView.annotations)

        let overlays = geometry.polylines.map { coordinates in
            MKPolyline(coordinates: coordinates, count: coordinates.count)
        }
        mapView.addOverlays(overlays)
        mapView.addAnnotations(
            geometry.points.map { point in
                EditableTrackPointAnnotation(
                    pointIndex: point.index,
                    coordinate: point.coordinate
                )
            }
        )

        if !context.coordinator.didSetInitialVisibleMapRect {
            mapView.setVisibleMapRect(
                geometry.visibleMapRect,
                edgePadding: UIEdgeInsets(top: 72, left: 28, bottom: 160, right: 28),
                animated: false
            )
            context.coordinator.didSetInitialVisibleMapRect = true
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var selectedPointIndex: Binding<Int?>
        var didSetInitialVisibleMapRect = false

        init(selectedPointIndex: Binding<Int?>) {
            self.selectedPointIndex = selectedPointIndex
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            guard let polyline = overlay as? MKPolyline else {
                return MKOverlayRenderer(overlay: overlay)
            }

            let renderer = MKPolylineRenderer(polyline: polyline)
            renderer.strokeColor = UIColor.systemBlue
            renderer.lineWidth = 4
            renderer.lineJoin = .round
            renderer.lineCap = .round
            return renderer
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard let annotation = annotation as? EditableTrackPointAnnotation else {
                return nil
            }

            let identifier = "track-point"
            let view = mapView.dequeueReusableAnnotationView(
                withIdentifier: identifier
            ) as? MKMarkerAnnotationView ?? MKMarkerAnnotationView(
                annotation: annotation,
                reuseIdentifier: identifier
            )
            view.annotation = annotation
            view.canShowCallout = false
            if annotation.pointIndex == selectedPointIndex.wrappedValue {
                view.markerTintColor = UIColor.systemOrange
            } else {
                view.markerTintColor = UIColor.systemCyan
            }
            view.glyphText = "\(annotation.pointIndex + 1)"
            return view
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let annotation = view.annotation as? EditableTrackPointAnnotation else {
                return
            }

            selectedPointIndex.wrappedValue = annotation.pointIndex
            mapView.deselectAnnotation(annotation, animated: false)
        }
    }
}

private final class EditableTrackPointAnnotation: NSObject, MKAnnotation {
    let pointIndex: Int
    let coordinate: CLLocationCoordinate2D

    init(
        pointIndex: Int,
        coordinate: CLLocationCoordinate2D
    ) {
        self.pointIndex = pointIndex
        self.coordinate = coordinate
    }
}

private struct EditableTrackMapGeometry {
    let polylines: [[CLLocationCoordinate2D]]
    let points: [EditableTrackPoint]
    let visibleMapRect: MKMapRect

    init?(document: GpxDocument) {
        var globalPointIndex = 0
        var pointMarkers: [EditableTrackPoint] = []
        let polylines = document.tracks
            .flatMap(\.segments)
            .map { segment in
                var coordinates: [CLLocationCoordinate2D] = []

                for point in segment.points {
                    let pointIndex = globalPointIndex
                    globalPointIndex += 1
                    let coordinate = CLLocationCoordinate2D(
                        latitude: point.latitude,
                        longitude: point.longitude
                    )

                    if CLLocationCoordinate2DIsValid(coordinate) {
                        pointMarkers.append(
                            EditableTrackPoint(
                                index: pointIndex,
                                coordinate: coordinate
                            )
                        )
                        coordinates.append(coordinate)
                    }
                }

                return coordinates
            }
            .filter { !$0.isEmpty }

        guard !polylines.isEmpty, !pointMarkers.isEmpty else {
            return nil
        }

        self.polylines = polylines
        self.points = pointMarkers

        let mapRect = pointMarkers
            .map { point in
                let mapPoint = MKMapPoint(point.coordinate)
                return MKMapRect(x: mapPoint.x, y: mapPoint.y, width: 1, height: 1)
            }
            .reduce(MKMapRect.null) { partialResult, rect in
                partialResult.union(rect)
            }

        visibleMapRect = mapRect.insetBy(
            dx: -max(mapRect.width * 0.15, 1_000),
            dy: -max(mapRect.height * 0.15, 1_000)
        )
    }
}

private struct EditableTrackPoint {
    let index: Int
    let coordinate: CLLocationCoordinate2D
}
