import Foundation
import shared

func sanitizedFileBaseName(for displayName: String) -> String {
    let allowedCharacters = CharacterSet.alphanumerics
        .union(CharacterSet(charactersIn: "-_ "))
    let sanitized = displayName
        .unicodeScalars
        .map { allowedCharacters.contains($0) ? Character($0) : "-" }
    let baseName = String(sanitized)
        .trimmingCharacters(in: .whitespacesAndNewlines)

    return baseName.isEmpty ? "track" : baseName
}

func formatDistance(_ meters: Double) -> String {
    if meters >= 1_000.0 {
        return String(format: "%.2f km", meters / 1_000.0)
    }

    return String(format: "%.0f m", meters)
}

func formatDuration(_ millis: Int64) -> String {
    let totalSeconds = millis / 1_000
    let hours = totalSeconds / 3_600
    let minutes = (totalSeconds % 3_600) / 60
    let seconds = totalSeconds % 60
    return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
}

func formatSpeed(_ metersPerSecond: KotlinDouble?) -> String {
    guard let metersPerSecond else {
        return "—"
    }

    return String(format: "%.1f km/h", metersPerSecond.doubleValue * 3.6)
}

func formatElevation(_ meters: KotlinDouble?) -> String {
    guard let meters else {
        return "No data"
    }

    return String(format: "%.0f m", meters.doubleValue)
}

func formatElevationRange(
    _ minMeters: KotlinDouble?,
    _ maxMeters: KotlinDouble?
) -> String {
    guard let minMeters, let maxMeters else {
        return "No data"
    }

    return "\(formatElevation(minMeters)) to \(formatElevation(maxMeters))"
}

func formatTimeRange(
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

func formatCoordinate(_ coordinate: GpxCoordinate?) -> String {
    guard let coordinate else {
        return "No data"
    }

    return String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude)
}
