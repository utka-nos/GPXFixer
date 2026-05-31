import Combine
import Foundation
import MapKit
import SwiftUI
import shared

struct IOSTrackDetailScreen: View {
    let track: ImportedTrack

    @StateObject private var viewModel = IOSTrackDetailViewModel()
    @State private var isShowingFullScreenMap = false

    var body: some View {
        List {
            if viewModel.isLoading {
                ProgressView("Loading")
            }

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
            }

            if let detail = viewModel.detail {
                Section {
                    Text(detail.importedTrack.originalFileName)
                        .foregroundStyle(.secondary)
                }

                TrackMapSection(detail: detail) {
                    isShowingFullScreenMap = true
                }

                SummarySection(detail: detail)

                if !detail.warnings.isEmpty {
                    Section("Warnings") {
                        ForEach(detail.warnings, id: \.self) { warning in
                            Text(warning)
                                .foregroundStyle(.red)
                        }
                    }
                }

                Section("Segments") {
                    if detail.segments.isEmpty {
                        Text("No segments found.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(detail.segments, id: \.index) { segment in
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Segment \(segment.index)")
                                    .font(.headline)
                                Text("\(segment.pointCount) points / \(formatDistance(segment.distanceMeters))")
                                    .foregroundStyle(.secondary)
                                Text(formatTimeRange(segment.startTime, segment.endTime))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
        }
        .navigationTitle(track.displayName)
        .onAppear {
            viewModel.load(track: track)
        }
        .fullScreenCover(isPresented: $isShowingFullScreenMap) {
            if let detail = viewModel.detail {
                TrackMapFullScreen(detail: detail) {
                    isShowingFullScreenMap = false
                }
            }
        }
    }
}

private struct TrackMapSection: View {
    let detail: TrackDetail
    let onTap: () -> Void

    var body: some View {
        if let geometry = TrackMapGeometry(document: detail.document) {
            Section("Map") {
                ZStack {
                    TrackMapView(
                        geometry: geometry,
                        isUserInteractionEnabled: false,
                        edgePadding: UIEdgeInsets(top: 28, left: 28, bottom: 28, right: 28)
                    )
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture(perform: onTap)
                }
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
            }
        }
    }
}

private struct TrackMapFullScreen: View {
    let detail: TrackDetail
    let onDismiss: () -> Void

    var body: some View {
        if let geometry = TrackMapGeometry(document: detail.document) {
            ZStack(alignment: .topLeading) {
                TrackMapView(
                    geometry: geometry,
                    isUserInteractionEnabled: true,
                    edgePadding: UIEdgeInsets(top: 80, left: 48, bottom: 80, right: 48)
                )
                .ignoresSafeArea()

                Button("Back", action: onDismiss)
                    .buttonStyle(.borderedProminent)
                    .padding()
            }
        } else {
            NavigationStack {
                ContentUnavailableView("No map data", systemImage: "map")
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Back", action: onDismiss)
                        }
                    }
            }
        }
    }
}

private struct TrackMapView: UIViewRepresentable {
    let geometry: TrackMapGeometry
    let isUserInteractionEnabled: Bool
    let edgePadding: UIEdgeInsets

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.pointOfInterestFilter = .excludingAll
        mapView.showsCompass = isUserInteractionEnabled
        mapView.showsScale = false
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        mapView.isUserInteractionEnabled = isUserInteractionEnabled
        mapView.showsCompass = isUserInteractionEnabled
        mapView.removeOverlays(mapView.overlays)

        let overlays = geometry.polylines.map { coordinates in
            MKPolyline(coordinates: coordinates, count: coordinates.count)
        }
        mapView.addOverlays(overlays)
        mapView.setVisibleMapRect(
            geometry.visibleMapRect,
            edgePadding: edgePadding,
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

private struct TrackMapGeometry {
    let polylines: [[CLLocationCoordinate2D]]
    let visibleMapRect: MKMapRect

    init?(document: GpxDocument) {
        let polylines = document.tracks
            .flatMap(\.segments)
            .map { segment in
                segment.points
                    .map { point in
                        CLLocationCoordinate2D(
                            latitude: point.latitude,
                            longitude: point.longitude
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

private struct SummarySection: View {
    let detail: TrackDetail

    var body: some View {
        let summary = detail.summary

        Section("Summary") {
            DetailRow(label: "Imported", value: detail.importedTrack.importedAt)
            DetailRow(label: "Tracks", value: "\(summary.trackCount)")
            DetailRow(label: "Segments", value: "\(summary.segmentCount)")
            DetailRow(label: "Points", value: "\(summary.pointCount)")
            DetailRow(label: "Distance", value: formatDistance(summary.distanceMeters))
            DetailRow(label: "Elevation gain", value: formatElevation(summary.totalAscentMeters))
            DetailRow(label: "Elevation loss", value: formatElevation(summary.totalDescentMeters))
            DetailRow(
                label: "Elevation range",
                value: formatElevationRange(summary.minElevationMeters, summary.maxElevationMeters)
            )
            DetailRow(label: "Time range", value: formatTimeRange(summary.startTime, summary.endTime))
            DetailRow(label: "Start", value: formatCoordinate(summary.startCoordinate))
            DetailRow(label: "Finish", value: formatCoordinate(summary.endCoordinate))
        }
    }
}

private struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
        }
    }
}

@MainActor
private final class IOSTrackDetailViewModel: ObservableObject {
    @Published var detail: TrackDetail?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let importFacade = IosImportFacade()
    private var loadedTrackId: String?

    func load(track: ImportedTrack) {
        guard loadedTrackId != track.id else { return }

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
}

private func formatDistance(_ meters: Double) -> String {
    if meters >= 1_000.0 {
        return String(format: "%.2f km", meters / 1_000.0)
    }

    return String(format: "%.0f m", meters)
}

private func formatElevation(_ meters: KotlinDouble?) -> String {
    guard let meters else {
        return "No data"
    }

    return String(format: "%.0f m", meters.doubleValue)
}

private func formatElevationRange(
    _ minMeters: KotlinDouble?,
    _ maxMeters: KotlinDouble?
) -> String {
    guard let minMeters, let maxMeters else {
        return "No data"
    }

    return "\(formatElevation(minMeters)) to \(formatElevation(maxMeters))"
}

private func formatTimeRange(
    _ startTime: String?,
    _ endTime: String?
) -> String {
    switch (startTime, endTime) {
    case (nil, nil):
        return "No data"
    case let (start?, nil):
        return start
    case let (nil, end?):
        return end
    case let (start?, end?) where start == end:
        return start
    case let (start?, end?):
        return "\(start) to \(end)"
    }
}

private func formatCoordinate(_ coordinate: GpxCoordinate?) -> String {
    guard let coordinate else {
        return "No data"
    }

    return String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude)
}
