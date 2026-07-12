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
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
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
    }
}

/// Map for the recording screen: draws the route recorded so far (one polyline
/// per pause/resume segment) and keeps the camera on the latest position. The
/// camera only moves when a new point arrives, so it stays still while paused.
struct LiveRecordingMapView: UIViewRepresentable {
    let segments: [[CLLocationCoordinate2D]]

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isUserInteractionEnabled = false
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = false
        mapView.showsScale = false
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        mapView.removeOverlays(mapView.overlays)
        let overlays = segments
            .filter { !$0.isEmpty }
            .map { MKPolyline(coordinates: $0, count: $0.count) }
        mapView.addOverlays(overlays)

        guard let currentPosition = segments.last?.last else { return }

        let coordinator = context.coordinator
        if let previous = coordinator.followedPosition,
           previous.latitude == currentPosition.latitude,
           previous.longitude == currentPosition.longitude {
            return
        }

        if coordinator.followedPosition == nil {
            mapView.setRegion(
                MKCoordinateRegion(
                    center: currentPosition,
                    latitudinalMeters: 1_000,
                    longitudinalMeters: 1_000
                ),
                animated: false
            )
        } else {
            mapView.setCenter(currentPosition, animated: true)
        }
        coordinator.followedPosition = currentPosition
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var followedPosition: CLLocationCoordinate2D?

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
    }
}

private struct TrackMapGeometry {
    let polylines: [[CLLocationCoordinate2D]]
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

        let mapRect = polylines
            .flatMap { $0 }
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
