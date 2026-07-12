import SwiftUI
import Charts
import shared

/// Names and units of the metric a track chart displays.
struct TrackChartMetric {
    let title: String
    let unit: String
    /// Spelled-out unit for spoken accessibility text, e.g. "watts".
    let unitLong: String
    /// SF Symbol shown when there is not enough data to draw a chart.
    let emptyIcon: String

    static let power = TrackChartMetric(
        title: "Power",
        unit: "W",
        unitLong: "watts",
        emptyIcon: "bolt"
    )
}

/// Full-screen metric-over-time chart. One finger scrubs a selection crosshair;
/// pinching zooms the visible time window and two-finger drags pan it.
struct IOSTrackChartScreen: View {
    let metric: TrackChartMetric
    let samples: [TrackChartSample]

    @State private var window: TrackChartWindow?
    @State private var selectedSample: TrackChartSample?
    @State private var isMagnifying = false
    @State private var magnifyStartWindow: TrackChartWindow?
    @State private var magnifyFocusSeconds: Int64?
    @State private var lastPanX: CGFloat?

    private var presenter: TrackChartPresenter { TrackChartPresenter.shared }
    private var fullWindow: TrackChartWindow? { presenter.fullWindow(samples: samples) }

    var body: some View {
        Group {
            if let fullWindow {
                chartContent(fullWindow: fullWindow)
            } else {
                ContentUnavailableView(
                    "Not enough \(metric.title.lowercased()) data",
                    systemImage: metric.emptyIcon,
                    description: Text(
                        "This track does not have enough \(metric.title.lowercased()) samples to draw a chart."
                    )
                )
            }
        }
        .navigationTitle(metric.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func chartContent(fullWindow: TrackChartWindow) -> some View {
        let activeWindow = window ?? fullWindow
        let presentation = presenter.presentation(
            samples: samples,
            window: activeWindow,
            maxRenderPoints: 800
        )

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(summaryText(presentation))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                if activeWindow != fullWindow {
                    Button("Reset zoom") {
                        window = nil
                        selectedSample = nil
                    }
                    .font(.subheadline)
                }
            }

            chart(presentation: presentation, activeWindow: activeWindow)

            ChartPanSlider(
                samples: samples,
                fullWindow: fullWindow,
                window: activeWindow,
                onWindowChange: { newWindow in
                    window = newWindow
                    selectedSample = nil
                }
            )
        }
        .padding()
        .accessibilityElement(children: .contain)
    }

    private func chart(presentation: TrackChartPresentation, activeWindow: TrackChartWindow) -> some View {
        let timeLabels = Dictionary(
            uniqueKeysWithValues: presentation.timeTicks.map { ($0.elapsedSeconds, $0.label) }
        )
        let valueLabels = Dictionary(
            uniqueKeysWithValues: presentation.valueTicks.map { ($0.value, $0.label) }
        )

        return Chart {
            ForEach(Array(presentation.segments.enumerated()), id: \.offset) { segmentIndex, segment in
                ForEach(segment, id: \.elapsedSeconds) { sample in
                    LineMark(
                        x: .value("Time", Double(sample.elapsedSeconds)),
                        y: .value(metric.title, Int(sample.value)),
                        series: .value("Segment", segmentIndex)
                    )
                    AreaMark(
                        x: .value("Time", Double(sample.elapsedSeconds)),
                        y: .value(metric.title, Int(sample.value)),
                        series: .value("Segment", segmentIndex),
                        stacking: .unstacked
                    )
                    .foregroundStyle(.linearGradient(
                        colors: [.accentColor.opacity(0.25), .accentColor.opacity(0.02)],
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                }
            }

            if let selected = selectedSample,
               selected.elapsedSeconds >= activeWindow.startSeconds,
               selected.elapsedSeconds <= activeWindow.endSeconds {
                RuleMark(x: .value("Time", Double(selected.elapsedSeconds)))
                    .foregroundStyle(.secondary)
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .annotation(
                        position: .top,
                        spacing: 0,
                        overflowResolution: .init(x: .fit(to: .chart), y: .disabled)
                    ) {
                        Text(tooltipText(selected))
                            .font(.caption.bold())
                            .padding(6)
                            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 6))
                    }
                PointMark(
                    x: .value("Time", Double(selected.elapsedSeconds)),
                    y: .value(metric.title, Int(selected.value))
                )
            }
        }
        .chartXScale(domain: Double(activeWindow.startSeconds)...Double(activeWindow.endSeconds))
        .chartYScale(domain: 0...Double(presentation.axisMaxValue))
        .chartXAxis {
            AxisMarks(values: presentation.timeTicks.map { Double($0.elapsedSeconds) }) { value in
                AxisGridLine()
                AxisTick()
                AxisValueLabel {
                    if let seconds = value.as(Double.self),
                       let label = timeLabels[Int64(seconds.rounded())] {
                        Text(label)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(values: presentation.valueTicks.map { Int($0.value) }) { value in
                AxisGridLine()
                AxisTick()
                AxisValueLabel {
                    if let metricValue = value.as(Int.self),
                       let label = valueLabels[Int32(metricValue)] {
                        Text(label)
                    }
                }
            }
        }
        .chartXAxisLabel("Elapsed time")
        .chartYAxisLabel("\(metric.title), \(metric.unit)")
        .chartOverlay { proxy in
            GeometryReader { geometry in
                Rectangle()
                    .fill(Color.clear)
                    .contentShape(Rectangle())
                    .gesture(
                        scrubOrPanGesture(proxy: proxy, geometry: geometry)
                            .simultaneously(with: magnifyGesture(proxy: proxy, geometry: geometry))
                    )
            }
        }
        .accessibilityLabel(accessibilityText(presentation, activeWindow: activeWindow))
    }

    private func scrubOrPanGesture(proxy: ChartProxy, geometry: GeometryProxy) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if isMagnifying {
                    // Two fingers are down: horizontal movement pans the window.
                    if let lastX = lastPanX,
                       let activeWindow = window ?? fullWindow,
                       let plotWidth = plotWidth(proxy: proxy, geometry: geometry),
                       plotWidth > 0 {
                        let secondsPerPoint =
                            Double(truncating: activeWindow.durationSeconds as NSNumber) / plotWidth
                        let deltaSeconds = Int64((Double(lastX - value.location.x) * secondsPerPoint).rounded())
                        if deltaSeconds != 0 {
                            window = presenter.panned(
                                samples: samples,
                                window: activeWindow,
                                deltaSeconds: deltaSeconds
                            )
                        }
                    }
                    lastPanX = value.location.x
                    selectedSample = nil
                } else {
                    lastPanX = nil
                    selectedSample = sample(atX: value.location.x, proxy: proxy, geometry: geometry)
                }
            }
            .onEnded { _ in
                lastPanX = nil
            }
    }

    private func magnifyGesture(proxy: ChartProxy, geometry: GeometryProxy) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                isMagnifying = true
                selectedSample = nil
                if magnifyStartWindow == nil {
                    magnifyStartWindow = window ?? fullWindow
                    let anchorX = value.startAnchor.x * geometry.size.width
                    magnifyFocusSeconds = elapsedSeconds(atX: anchorX, proxy: proxy, geometry: geometry)
                }
                guard let startWindow = magnifyStartWindow,
                      let focusSeconds = magnifyFocusSeconds else { return }
                window = presenter.zoomed(
                    samples: samples,
                    window: startWindow,
                    factor: Double(value.magnification),
                    focusSeconds: focusSeconds
                )
            }
            .onEnded { _ in
                isMagnifying = false
                magnifyStartWindow = nil
                magnifyFocusSeconds = nil
            }
    }

    private func plotWidth(proxy: ChartProxy, geometry: GeometryProxy) -> Double? {
        guard let plotFrame = proxy.plotFrame else { return nil }
        return geometry[plotFrame].width
    }

    private func elapsedSeconds(atX x: CGFloat, proxy: ChartProxy, geometry: GeometryProxy) -> Int64? {
        guard let plotFrame = proxy.plotFrame else { return nil }
        let plotX = x - geometry[plotFrame].origin.x
        guard let seconds: Double = proxy.value(atX: plotX) else { return nil }
        return Int64(seconds.rounded())
    }

    private func sample(atX x: CGFloat, proxy: ChartProxy, geometry: GeometryProxy) -> TrackChartSample? {
        guard let seconds = elapsedSeconds(atX: x, proxy: proxy, geometry: geometry),
              let activeWindow = window ?? fullWindow else { return nil }
        let clamped = min(max(seconds, activeWindow.startSeconds), activeWindow.endSeconds)
        guard let nearest = presenter.nearestSample(samples: samples, elapsedSeconds: clamped),
              nearest.elapsedSeconds >= activeWindow.startSeconds,
              nearest.elapsedSeconds <= activeWindow.endSeconds else { return nil }
        return nearest
    }

    private func summaryText(_ presentation: TrackChartPresentation) -> String {
        var parts: [String] = []
        if let average = presentation.averageValue {
            parts.append("Avg \(average.intValue) \(metric.unit)")
        }
        if let maximum = presentation.maxValue {
            parts.append("Max \(maximum.intValue) \(metric.unit)")
        }
        guard !parts.isEmpty else { return "No samples in view" }
        return parts.joined(separator: " · ") + " (visible range)"
    }

    private func tooltipText(_ sample: TrackChartSample) -> String {
        let elapsed = presenter.formatElapsed(elapsedSeconds: sample.elapsedSeconds)
        return "\(elapsed) · \(sample.value) \(metric.unit)"
    }

    private func accessibilityText(
        _ presentation: TrackChartPresentation,
        activeWindow: TrackChartWindow
    ) -> String {
        var text = "\(metric.title) chart from "
            + presenter.formatElapsed(elapsedSeconds: activeWindow.startSeconds)
            + " to "
            + presenter.formatElapsed(elapsedSeconds: activeWindow.endSeconds)
        if let average = presentation.averageValue {
            text += ", average \(average.intValue) \(metric.unitLong)"
        }
        if let maximum = presentation.maxValue {
            text += ", maximum \(maximum.intValue) \(metric.unitLong)"
        }
        if let selected = selectedSample {
            text += ". Selected point at "
                + presenter.formatElapsed(elapsedSeconds: selected.elapsedSeconds)
                + ", \(selected.value) \(metric.unitLong)"
        }
        return text
    }
}

/// Scrollbar-style pan control under the chart: the track represents the whole
/// ride, the thumb the visible window. Dragging it pans the window at the
/// current zoom level.
private struct ChartPanSlider: View {
    let samples: [TrackChartSample]
    let fullWindow: TrackChartWindow
    let window: TrackChartWindow
    let onWindowChange: (TrackChartWindow) -> Void

    @State private var windowAtDragStart: TrackChartWindow?

    var body: some View {
        GeometryReader { geometry in
            let trackWidth = geometry.size.width
            let trackHeight = geometry.size.height
            let fullDuration = max(Double(fullWindow.durationSeconds), 1)
            let minThumbWidth = trackHeight * 2
            let thumbWidth = min(
                max(trackWidth * Double(window.durationSeconds) / fullDuration, minThumbWidth),
                trackWidth
            )
            let thumbOffset = min(
                max(
                    trackWidth * Double(window.startSeconds - fullWindow.startSeconds) / fullDuration,
                    0
                ),
                trackWidth - thumbWidth
            )

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color(.systemFill))
                Capsule()
                    .fill(Color.accentColor)
                    .frame(width: thumbWidth)
                    .offset(x: thumbOffset)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if windowAtDragStart == nil {
                            windowAtDragStart = window
                        }
                        guard let startWindow = windowAtDragStart, trackWidth > 0 else { return }
                        let deltaSeconds = Int64(
                            (Double(value.translation.width) / trackWidth * fullDuration).rounded()
                        )
                        onWindowChange(
                            TrackChartPresenter.shared.panned(
                                samples: samples,
                                window: startWindow,
                                deltaSeconds: deltaSeconds
                            )
                        )
                    }
                    .onEnded { _ in
                        windowAtDragStart = nil
                    }
            )
        }
        .frame(height: 14)
        .accessibilityLabel("Chart position")
        .accessibilityHint("Drag to move the visible range")
    }
}
