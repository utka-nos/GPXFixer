import MapKit
import SwiftUI
import shared

struct IOSTrackEditScreen: View {
    let detail: TrackDetail
    let onSave: (ImportedTrack) -> Void

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: IOSTrackEditViewModel
    @State private var mapProxy = EditableTrackMapProxy()

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
                    selectedPointIndex: $viewModel.selectedPointIndex,
                    isMovingPoint: viewModel.movingPointIndex != nil,
                    onMapTap: { coordinate in
                        viewModel.moveSelectedPoint(
                            latitude: coordinate.latitude,
                            longitude: coordinate.longitude
                        )
                    },
                    mapProxy: mapProxy
                )
                .ignoresSafeArea(edges: .bottom)
            } else {
                ContentUnavailableView("No track geometry", systemImage: "map")
            }

            if viewModel.movingPointIndex != nil {
                MovePointControls(
                    onNudge: { latitudeSign, longitudeSign in
                        let step = nudgeStep()
                        viewModel.nudgeMovingPoint(
                            latitudeDelta: Double(latitudeSign) * step.latitude,
                            longitudeDelta: Double(longitudeSign) * step.longitude
                        )
                    },
                    onSave: {
                        viewModel.finishMovingPoint()
                    }
                )
                .padding(16)
            } else if let selectedPointIndex = viewModel.selectedPointIndex {
                let neighbors = neighborPointIndices(of: selectedPointIndex)
                SelectedPointMenu(
                    pointIndex: selectedPointIndex,
                    pointTime: pointTime(at: selectedPointIndex),
                    previousPointIndex: neighbors.previous,
                    nextPointIndex: neighbors.next,
                    onSelectPoint: { pointIndex in
                        viewModel.selectedPointIndex = pointIndex
                    },
                    onDelete: {
                        viewModel.deleteSelectedPoint()
                    },
                    onMove: {
                        viewModel.beginMovingSelectedPoint()
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
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    viewModel.undoLastDeletedPoint()
                } label: {
                    Label("Undo delete", systemImage: "arrow.uturn.backward")
                }
                .disabled(!viewModel.canUndoDelete)

                Button("Save") {
                    let updatedTrack = viewModel.saveEditedTrack()
                    onSave(updatedTrack)
                    dismiss()
                }
                .disabled(!viewModel.hasChanges)
            }
        }
    }

    private func neighborPointIndices(of index: Int) -> (previous: Int?, next: Int?) {
        guard let geometry = EditableTrackMapGeometry(document: viewModel.document),
              let position = geometry.points.firstIndex(where: { $0.index == index }) else {
            return (nil, nil)
        }

        let previous = position > 0 ? geometry.points[position - 1].index : nil
        let next = position + 1 < geometry.points.count ? geometry.points[position + 1].index : nil
        return (previous, next)
    }

    // One nudge moves the point by 1% of the visible map span, so the step
    // scales with the current zoom level.
    private func nudgeStep(
        spanFraction: Double = 0.01,
        fallbackDegrees: Double = 0.00005
    ) -> (latitude: Double, longitude: Double) {
        guard let span = mapProxy.mapView?.region.span,
              span.latitudeDelta > 0,
              span.longitudeDelta > 0 else {
            return (fallbackDegrees, fallbackDegrees)
        }

        return (span.latitudeDelta * spanFraction, span.longitudeDelta * spanFraction)
    }

    private func pointTime(at index: Int) -> String? {
        var globalPointIndex = 0
        for track in viewModel.document.tracks {
            for segment in track.segments {
                for point in segment.points {
                    if globalPointIndex == index {
                        return point.time
                    }
                    globalPointIndex += 1
                }
            }
        }
        return nil
    }
}

private struct MovePointControls: View {
    let onNudge: (_ latitudeSign: Int, _ longitudeSign: Int) -> Void
    let onSave: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("Tap the map or use the arrows to move the point")
                .font(.body)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                onNudge(1, 0)
            } label: {
                Image(systemName: "arrow.up")
            }
            .buttonStyle(.bordered)

            HStack(spacing: 12) {
                Button {
                    onNudge(0, -1)
                } label: {
                    Image(systemName: "arrow.left")
                }
                .buttonStyle(.bordered)

                Button {
                    onNudge(-1, 0)
                } label: {
                    Image(systemName: "arrow.down")
                }
                .buttonStyle(.bordered)

                Button {
                    onNudge(0, 1)
                } label: {
                    Image(systemName: "arrow.right")
                }
                .buttonStyle(.bordered)
            }

            Button("Save position", action: onSave)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

final class EditableTrackMapProxy {
    weak var mapView: MKMapView?
}

private struct SelectedPointMenu: View {
    let pointIndex: Int
    let pointTime: String?
    let previousPointIndex: Int?
    let nextPointIndex: Int?
    let onSelectPoint: (Int) -> Void
    let onDelete: () -> Void
    let onMove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Text("Point \(pointIndex + 1)")
                    .font(.headline)

                Spacer()

                Button {
                    if let previousPointIndex {
                        onSelectPoint(previousPointIndex)
                    }
                } label: {
                    Image(systemName: "chevron.left")
                }
                .buttonStyle(.bordered)
                .disabled(previousPointIndex == nil)

                Button {
                    if let nextPointIndex {
                        onSelectPoint(nextPointIndex)
                    }
                } label: {
                    Image(systemName: "chevron.right")
                }
                .buttonStyle(.bordered)
                .disabled(nextPointIndex == nil)
            }

            Text("Index: \(pointIndex)")
                .foregroundStyle(.secondary)

            Text("Time: \(pointTime ?? "No data")")
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Button(role: .destructive, action: onDelete) {
                    Text("Delete")
                }
                .buttonStyle(.borderedProminent)

                Button("Move", action: onMove)
                    .buttonStyle(.bordered)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct EditableTrackMap: UIViewRepresentable {
    let document: ActivityDocument
    @Binding var selectedPointIndex: Int?
    let isMovingPoint: Bool
    let onMapTap: (CLLocationCoordinate2D) -> Void
    let mapProxy: EditableTrackMapProxy

    func makeCoordinator() -> Coordinator {
        Coordinator(
            selectedPointIndex: $selectedPointIndex,
            isMovingPoint: isMovingPoint,
            onMapTap: onMapTap
        )
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapProxy.mapView = mapView
        mapView.delegate = context.coordinator
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = true
        mapView.showsScale = true

        let tapRecognizer = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleMapTap(_:))
        )
        tapRecognizer.cancelsTouchesInView = false
        tapRecognizer.delegate = context.coordinator
        mapView.addGestureRecognizer(tapRecognizer)

        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        context.coordinator.selectedPointIndex = $selectedPointIndex
        context.coordinator.isMovingPoint = isMovingPoint
        context.coordinator.onMapTap = onMapTap

        guard let geometry = EditableTrackMapGeometry(document: document) else {
            mapView.removeOverlays(mapView.overlays)
            mapView.removeAnnotations(mapView.annotations)
            context.coordinator.points = []
            context.coordinator.didSetInitialVisibleMapRect = false
            return
        }
        context.coordinator.points = geometry.points

        mapView.removeOverlays(mapView.overlays)
        mapView.removeAnnotations(
            mapView.annotations.filter { $0 is EditableTrackPointAnnotation }
        )

        let overlays = geometry.polylines.map { coordinates in
            MKPolyline(coordinates: coordinates, count: coordinates.count)
        }
        mapView.addOverlays(overlays)
        if let selectedPointIndex,
           let selectedPoint = geometry.points.first(where: { $0.index == selectedPointIndex }) {
            mapView.addAnnotation(
                EditableTrackPointAnnotation(
                    pointIndex: selectedPoint.index,
                    coordinate: selectedPoint.coordinate
                )
            )
        }

        if !context.coordinator.didSetInitialVisibleMapRect {
            mapView.setVisibleMapRect(
                geometry.visibleMapRect,
                edgePadding: UIEdgeInsets(top: 72, left: 28, bottom: 160, right: 28),
                animated: false
            )
            context.coordinator.didSetInitialVisibleMapRect = true
        }
        context.coordinator.syncPointDotAnnotations(on: mapView)
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        private static let pointDotsMinZoom: Double = 16
        private static let maxVisiblePointDots = 50
        private static let pointDotDiameter: CGFloat = 8
        private static let pointDotReuseIdentifier = "editable-track-point-dot"

        var selectedPointIndex: Binding<Int?>
        var isMovingPoint: Bool
        var onMapTap: (CLLocationCoordinate2D) -> Void
        var points: [EditableTrackPoint] = []
        var didSetInitialVisibleMapRect = false
        private var pointDotAnnotations: [EditableTrackPointDotAnnotation] = []

        init(
            selectedPointIndex: Binding<Int?>,
            isMovingPoint: Bool,
            onMapTap: @escaping (CLLocationCoordinate2D) -> Void
        ) {
            self.selectedPointIndex = selectedPointIndex
            self.isMovingPoint = isMovingPoint
            self.onMapTap = onMapTap
        }

        @objc func handleMapTap(_ recognizer: UITapGestureRecognizer) {
            guard let mapView = recognizer.view as? MKMapView else {
                return
            }

            let point = recognizer.location(in: mapView)
            let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
            if isMovingPoint {
                onMapTap(coordinate)
            } else if let nearestPoint = nearestPoint(to: point, in: mapView) {
                selectedPointIndex.wrappedValue = nearestPoint.index
            }
        }

        private func nearestPoint(
            to tapPoint: CGPoint,
            in mapView: MKMapView,
            maxDistance: CGFloat = 48
        ) -> EditableTrackPoint? {
            points
                .map { point in
                    let screenPoint = mapView.convert(point.coordinate, toPointTo: mapView)
                    let distance = hypot(screenPoint.x - tapPoint.x, screenPoint.y - tapPoint.y)
                    return (point: point, distance: distance)
                }
                .filter { candidate in
                    candidate.distance <= maxDistance
                }
                .min { lhs, rhs in
                    lhs.distance < rhs.distance
                }?
                .point
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

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            syncPointDotAnnotations(on: mapView)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if let annotation = annotation as? EditableTrackPointDotAnnotation {
                let view = mapView.dequeueReusableAnnotationView(
                    withIdentifier: Self.pointDotReuseIdentifier
                ) ?? MKAnnotationView(
                    annotation: annotation,
                    reuseIdentifier: Self.pointDotReuseIdentifier
                )
                view.annotation = annotation
                view.bounds = CGRect(
                    x: 0,
                    y: 0,
                    width: Self.pointDotDiameter,
                    height: Self.pointDotDiameter
                )
                view.layer.cornerRadius = Self.pointDotDiameter / 2
                view.backgroundColor = .systemBlue
                view.layer.borderColor = UIColor.white.cgColor
                view.layer.borderWidth = 1.5
                view.displayPriority = .required
                return view
            }

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
            guard !isMovingPoint else { return }

            guard let annotation = view.annotation as? EditableTrackPointIdentifying else {
                return
            }

            selectedPointIndex.wrappedValue = annotation.pointIndex
            mapView.deselectAnnotation(annotation, animated: false)
        }

        func syncPointDotAnnotations(on mapView: MKMapView) {
            let visiblePoints = visiblePointsForDots(on: mapView)
            let unchanged = visiblePoints.count == pointDotAnnotations.count
                && zip(visiblePoints, pointDotAnnotations).allSatisfy { point, annotation in
                    point.index == annotation.pointIndex
                        && point.coordinate.latitude == annotation.coordinate.latitude
                        && point.coordinate.longitude == annotation.coordinate.longitude
                }
            guard !unchanged else { return }

            mapView.removeAnnotations(pointDotAnnotations)
            pointDotAnnotations = visiblePoints.map {
                EditableTrackPointDotAnnotation(
                    pointIndex: $0.index,
                    coordinate: $0.coordinate
                )
            }
            mapView.addAnnotations(pointDotAnnotations)
        }

        private func visiblePointsForDots(on mapView: MKMapView) -> [EditableTrackPoint] {
            guard !isMovingPoint, zoomLevel(of: mapView) >= Self.pointDotsMinZoom else {
                return []
            }

            let selectedPointIndex = selectedPointIndex.wrappedValue
            let visibleRect = mapView.visibleMapRect
            let candidates = points.lazy
                .filter { $0.index != selectedPointIndex }
                .filter { visibleRect.contains(MKMapPoint($0.coordinate)) }
                .prefix(Self.maxVisiblePointDots + 1)
            guard candidates.count <= Self.maxVisiblePointDots else {
                return []
            }
            return Array(candidates)
        }

        private func zoomLevel(of mapView: MKMapView) -> Double {
            let width = mapView.bounds.width
            guard width > 0, mapView.region.span.longitudeDelta > 0 else {
                return 0
            }
            return log2(360 * (width / 256) / mapView.region.span.longitudeDelta)
        }
    }
}

extension EditableTrackMap.Coordinator: UIGestureRecognizerDelegate {
    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        return true
    }
}

private protocol EditableTrackPointIdentifying: MKAnnotation {
    var pointIndex: Int { get }
}

private final class EditableTrackPointAnnotation: NSObject, EditableTrackPointIdentifying {
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

private final class EditableTrackPointDotAnnotation: NSObject, EditableTrackPointIdentifying {
    let pointIndex: Int
    let coordinate: CLLocationCoordinate2D

    init(pointIndex: Int, coordinate: CLLocationCoordinate2D) {
        self.pointIndex = pointIndex
        self.coordinate = coordinate
    }
}

private struct EditableTrackMapGeometry {
    let polylines: [[CLLocationCoordinate2D]]
    let points: [EditableTrackPoint]
    let visibleMapRect: MKMapRect

    init?(document: ActivityDocument) {
        var globalPointIndex = 0
        var pointMarkers: [EditableTrackPoint] = []
        let polylines = document.tracks
            .flatMap(\.segments)
            .map { segment in
                var coordinates: [CLLocationCoordinate2D] = []

                for point in segment.points {
                    let pointIndex = globalPointIndex
                    globalPointIndex += 1
                    guard let latitude = point.latitude, let longitude = point.longitude else {
                        continue
                    }
                    let coordinate = CLLocationCoordinate2D(
                        latitude: latitude.doubleValue,
                        longitude: longitude.doubleValue
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
