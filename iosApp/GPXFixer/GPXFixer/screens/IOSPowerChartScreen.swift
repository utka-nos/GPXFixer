import SwiftUI
import Charts
import shared

/// Full-screen power-over-time chart. One finger scrubs a selection crosshair;
/// pinching zooms the visible time window and two-finger drags pan it.
struct IOSPowerChartScreen: View {
    let samples: [PowerChartSample]

    @State private var window: PowerChartWindow?
    @State private var selectedSample: PowerChartSample?
    @State private var isMagnifying = false
    @State private var magnifyStartWindow: PowerChartWindow?
    @State private var magnifyFocusSeconds: Int64?
    @State private var lastPanX: CGFloat?

    private var presenter: PowerChartPresenter { PowerChartPresenter.shared }
    private var fullWindow: PowerChartWindow? { presenter.fullWindow(samples: samples) }

    var body: some View {
        Group {
            if let fullWindow {
                chartContent(fullWindow: fullWindow)
            } else {
                ContentUnavailableView(
                    "Not enough power data",
                    systemImage: "bolt",
                    description: Text("This track does not have enough power samples to draw a chart.")
                )
            }
        }
        .navigationTitle("Power")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func chartContent(fullWindow: PowerChartWindow) -> some View {
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
        }
        .padding()
        .accessibilityElement(children: .contain)
    }

    private func chart(presentation: PowerChartPresentation, activeWindow: PowerChartWindow) -> some View {
        let timeLabels = Dictionary(
            uniqueKeysWithValues: presentation.timeTicks.map { ($0.elapsedSeconds, $0.label) }
        )
        let powerLabels = Dictionary(
            uniqueKeysWithValues: presentation.powerTicks.map { ($0.powerWatts, $0.label) }
        )

        return Chart {
            ForEach(Array(presentation.segments.enumerated()), id: \.offset) { segmentIndex, segment in
                ForEach(segment, id: \.elapsedSeconds) { sample in
                    LineMark(
                        x: .value("Time", Double(sample.elapsedSeconds)),
                        y: .value("Power", Int(sample.powerWatts)),
                        series: .value("Segment", segmentIndex)
                    )
                    AreaMark(
                        x: .value("Time", Double(sample.elapsedSeconds)),
                        y: .value("Power", Int(sample.powerWatts)),
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
                    y: .value("Power", Int(selected.powerWatts))
                )
            }
        }
        .chartXScale(domain: Double(activeWindow.startSeconds)...Double(activeWindow.endSeconds))
        .chartYScale(domain: 0...Double(presentation.axisMaxWatts))
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
            AxisMarks(values: presentation.powerTicks.map { Int($0.powerWatts) }) { value in
                AxisGridLine()
                AxisTick()
                AxisValueLabel {
                    if let watts = value.as(Int.self),
                       let label = powerLabels[Int32(watts)] {
                        Text(label)
                    }
                }
            }
        }
        .chartXAxisLabel("Elapsed time")
        .chartYAxisLabel("Power, W")
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

    private func sample(atX x: CGFloat, proxy: ChartProxy, geometry: GeometryProxy) -> PowerChartSample? {
        guard let seconds = elapsedSeconds(atX: x, proxy: proxy, geometry: geometry),
              let activeWindow = window ?? fullWindow else { return nil }
        let clamped = min(max(seconds, activeWindow.startSeconds), activeWindow.endSeconds)
        guard let nearest = presenter.nearestSample(samples: samples, elapsedSeconds: clamped),
              nearest.elapsedSeconds >= activeWindow.startSeconds,
              nearest.elapsedSeconds <= activeWindow.endSeconds else { return nil }
        return nearest
    }

    private func summaryText(_ presentation: PowerChartPresentation) -> String {
        var parts: [String] = []
        if let average = presentation.averagePowerWatts {
            parts.append("Avg \(average.intValue) W")
        }
        if let maximum = presentation.maxPowerWatts {
            parts.append("Max \(maximum.intValue) W")
        }
        guard !parts.isEmpty else { return "No samples in view" }
        return parts.joined(separator: " · ") + " (visible range)"
    }

    private func tooltipText(_ sample: PowerChartSample) -> String {
        let elapsed = presenter.formatElapsed(elapsedSeconds: sample.elapsedSeconds)
        return "\(elapsed) · \(sample.powerWatts) W"
    }

    private func accessibilityText(
        _ presentation: PowerChartPresentation,
        activeWindow: PowerChartWindow
    ) -> String {
        var text = "Power chart from "
            + presenter.formatElapsed(elapsedSeconds: activeWindow.startSeconds)
            + " to "
            + presenter.formatElapsed(elapsedSeconds: activeWindow.endSeconds)
        if let average = presentation.averagePowerWatts {
            text += ", average \(average.intValue) watts"
        }
        if let maximum = presentation.maxPowerWatts {
            text += ", maximum \(maximum.intValue) watts"
        }
        if let selected = selectedSample {
            text += ". Selected point at "
                + presenter.formatElapsed(elapsedSeconds: selected.elapsedSeconds)
                + ", \(selected.powerWatts) watts"
        }
        return text
    }
}
