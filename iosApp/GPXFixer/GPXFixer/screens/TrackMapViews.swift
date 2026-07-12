import MapKit
import SwiftUI
import shared

struct TrackMapSection: View {
    let document: ActivityDocument

    var body: some View {
        if let geometry = TrackMapGeometry(document: document) {
            Section("Map") {
                NavigationLink {
                    TrackMapFullScreen(document: document)
                } label: {
                    TrackMapPreview(geometry: geometry)
                        .frame(height: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
                .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
            }
        }
    }
}

private struct TrackMapFullScreen: View {
    let document: ActivityDocument

    var body: some View {
        if let geometry = TrackMapGeometry(document: document) {
            TrackMapPreview(
                geometry: geometry,
                isInteractive: true
            )
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle("Map")
            .navigationBarTitleDisplayMode(.inline)
        } else {
            ContentUnavailableView("No track geometry", systemImage: "map")
        }
    }
}

private struct TrackMapPreview: UIViewRepresentable {
    let geometry: TrackMapGeometry
    var isInteractive = false

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isUserInteractionEnabled = isInteractive
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = isInteractive
        mapView.showsScale = isInteractive
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        mapView.isUserInteractionEnabled = isInteractive
        mapView.showsCompass = isInteractive
        mapView.showsScale = isInteractive
        mapView.removeOverlays(mapView.overlays)

        let overlays = geometry.polylines.map { coordinates in
            MKPolyline(coordinates: coordinates, count: coordinates.count)
        }
        mapView.addOverlays(overlays)
        mapView.setVisibleMapRect(
            geometry.visibleMapRect,
            edgePadding: UIEdgeInsets(top: 28, left: 28, bottom: 28, right: 28),
            animated: false
        )

        context.coordinator.allPoints = geometry.allCoordinates
        context.coordinator.showsPointMarkers = isInteractive
        context.coordinator.syncPointAnnotations(on: mapView)
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        // Zoom level at which individual track points become visible. At zoom
        // 16 the screen spans a few hundred meters, so a typical 1 point/sec
        // track yields at most a few hundred on-screen points — cheap to
        // render and still readable.
        private static let pointMarkersMinZoom: Double = 16
        private static let maxPointMarkers = 500
        private static let pointMarkerDiameter: CGFloat = 8
        private static let pointMarkerReuseIdentifier = "trackPointMarker"

        var allPoints: [CLLocationCoordinate2D] = []
        var showsPointMarkers = false
        private var pointAnnotations: [TrackPointAnnotation] = []

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
            syncPointAnnotations(on: mapView)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard annotation is TrackPointAnnotation else {
                return nil
            }

            let view = mapView.dequeueReusableAnnotationView(
                withIdentifier: Self.pointMarkerReuseIdentifier
            ) ?? MKAnnotationView(
                annotation: annotation,
                reuseIdentifier: Self.pointMarkerReuseIdentifier
            )
            view.annotation = annotation
            view.bounds = CGRect(
                x: 0,
                y: 0,
                width: Self.pointMarkerDiameter,
                height: Self.pointMarkerDiameter
            )
            view.layer.cornerRadius = Self.pointMarkerDiameter / 2
            view.backgroundColor = .systemBlue
            view.layer.borderColor = UIColor.white.cgColor
            view.layer.borderWidth = 1.5
            view.displayPriority = .required
            return view
        }

        func syncPointAnnotations(on mapView: MKMapView) {
            let coordinates = visiblePointCoordinates(on: mapView)
            let unchanged = coordinates.count == pointAnnotations.count
                && zip(coordinates, pointAnnotations).allSatisfy { coordinate, annotation in
                    coordinate.latitude == annotation.coordinate.latitude
                        && coordinate.longitude == annotation.coordinate.longitude
                }
            guard !unchanged else {
                return
            }

            mapView.removeAnnotations(pointAnnotations)
            pointAnnotations = coordinates.map(TrackPointAnnotation.init)
            mapView.addAnnotations(pointAnnotations)
        }

        private func visiblePointCoordinates(on mapView: MKMapView) -> [CLLocationCoordinate2D] {
            guard showsPointMarkers, zoomLevel(of: mapView) >= Self.pointMarkersMinZoom else {
                return []
            }

            let visibleRect = mapView.visibleMapRect
            return allPoints
                .filter { visibleRect.contains(MKMapPoint($0)) }
                .subsampled(to: Self.maxPointMarkers)
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

private final class TrackPointAnnotation: NSObject, MKAnnotation {
    let coordinate: CLLocationCoordinate2D

    init(coordinate: CLLocationCoordinate2D) {
        self.coordinate = coordinate
    }
}

private extension Array {
    func subsampled(to maxCount: Int) -> [Element] {
        guard count > maxCount else {
            return self
        }
        let step = Double(count) / Double(maxCount)
        return (0..<maxCount).map { self[Int(Double($0) * step)] }
    }
}

private struct TrackMapGeometry {
    let polylines: [[CLLocationCoordinate2D]]
    let allCoordinates: [CLLocationCoordinate2D]
    let visibleMapRect: MKMapRect

    init?(document: ActivityDocument) {
        let polylines = document.tracks
            .flatMap(\.segments)
            .map { segment in
                segment.points
                    .compactMap { point in
                        guard let latitude = point.latitude, let longitude = point.longitude else {
                            return nil
                        }
                        return CLLocationCoordinate2D(
                            latitude: latitude.doubleValue,
                            longitude: longitude.doubleValue
                        )
                    }
                    .filter(CLLocationCoordinate2DIsValid)
            }
            .filter { !$0.isEmpty }

        guard !polylines.isEmpty else {
            return nil
        }

        self.polylines = polylines
        allCoordinates = polylines.flatMap { $0 }

        let mapRect = allCoordinates
            .map { coordinate in
                let point = MKMapPoint(coordinate)
                return MKMapRect(x: point.x, y: point.y, width: 1, height: 1)
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
