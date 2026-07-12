import SwiftUI
import Charts
import shared

struct PowerChartSection: View {
    let samples: [PowerChartSample]

    var body: some View {
        if samples.count >= 2 {
            Section("Power") {
                Chart(Array(samples.enumerated()), id: \.offset) { _, sample in
                    LineMark(
                        x: .value("Time", Double(sample.elapsedSeconds) / 60.0),
                        y: .value("Power", Int(sample.powerWatts))
                    )
                    AreaMark(
                        x: .value("Time", Double(sample.elapsedSeconds) / 60.0),
                        y: .value("Power", Int(sample.powerWatts))
                    )
                    .foregroundStyle(.linearGradient(
                        colors: [.accentColor.opacity(0.25), .accentColor.opacity(0.02)],
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                }
                .chartXAxisLabel("Time, min")
                .chartYAxisLabel("Power, W")
                .frame(height: 180)
                .padding(.vertical, 8)
            }
        }
    }
}

struct WarningsSection: View {
    let warnings: [String]

    var body: some View {
        if !warnings.isEmpty {
            Section("Warnings") {
                ForEach(warnings, id: \.self) { warning in
                    Text(warning)
                        .foregroundStyle(.red)
                }
            }
        }
    }
}

struct TrackSegmentsSection: View {
    let segments: [TrackSegmentSummary]

    var body: some View {
        Section("Segments") {
            if segments.isEmpty {
                Text("No segments found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(segments, id: \.index) { segment in
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

struct SummarySection: View {
    let title: String
    let importedAt: String?
    let summary: TrackSummary

    var body: some View {
        Section(title) {
            if let importedAt {
                DetailRow(label: "Imported", value: importedAt)
            }
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
            if let averagePower = summary.averagePowerWatts {
                DetailRow(label: "Average power", value: "\(Int(averagePower.doubleValue)) W")
            }
            if let maxPower = summary.maxPowerWatts {
                DetailRow(label: "Maximum power", value: "\(maxPower.intValue) W")
            }
            if let averageCadence = summary.averageCadenceRpm {
                DetailRow(label: "Average cadence", value: "\(Int(averageCadence.doubleValue)) rpm")
            }
            DetailRow(label: "Time range", value: formatTimeRange(summary.startTime, summary.endTime))
            DetailRow(label: "Start", value: formatCoordinate(summary.startCoordinate))
            DetailRow(label: "Finish", value: formatCoordinate(summary.endCoordinate))
        }
    }
}

struct DetailRow: View {
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
