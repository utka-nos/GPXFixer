import SwiftUI
import Charts
import shared

/// Live power-over-time chart on the recording screen. Follows the newest
/// samples with a sliding window; dragging pans back over the ride, and the
/// Live button (or panning forward to the newest sample) resumes following.
/// Renders nothing until there is enough power data to draw a line.
struct LivePowerChartView: View {
    let samples: [TrackChartSample]

    @State private var pannedWindow: TrackChartWindow?
    @State private var windowAtDragStart: TrackChartWindow?

    private var presenter: TrackChartPresenter { TrackChartPresenter.shared }

    var body: some View {
        if let liveWindow = LiveChartWindow.shared.liveWindow(samples: samples) {
            let window = pannedWindow ?? liveWindow
            let presentation = presenter.presentation(
                samples: samples,
                window: window,
                maxRenderPoints: 600
            )

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Power, W")
                        .font(.subheadline.weight(.medium))
                    Spacer()
                    if pannedWindow != nil {
                        Button("Live") {
                            pannedWindow = nil
                        }
                        .font(.subheadline)
                    }
                }

                chart(presentation: presentation, window: window)
                    .frame(height: 160)
            }
        }
    }

    private func chart(presentation: TrackChartPresentation, window: TrackChartWindow) -> some View {
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
                        y: .value("Power", Int(sample.value)),
                        series: .value("Segment", segmentIndex)
                    )
                    AreaMark(
                        x: .value("Time", Double(sample.elapsedSeconds)),
                        y: .value("Power", Int(sample.value)),
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
        }
        .chartXScale(domain: Double(window.startSeconds)...Double(window.endSeconds))
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
                    if let watts = value.as(Int.self),
                       let label = valueLabels[Int32(watts)] {
                        Text(label)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geometry in
                Rectangle()
                    .fill(Color.clear)
                    .contentShape(Rectangle())
                    .gesture(panGesture(proxy: proxy, geometry: geometry, window: window))
            }
        }
        .accessibilityLabel(accessibilityText(window: window))
    }

    private func panGesture(
        proxy: ChartProxy,
        geometry: GeometryProxy,
        window: TrackChartWindow
    ) -> some Gesture {
        // The minimum distance keeps vertical scrolling of the surrounding
        // list working; a mostly horizontal drag pans the chart window.
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                if windowAtDragStart == nil {
                    windowAtDragStart = window
                }
                guard let startWindow = windowAtDragStart,
                      let plotFrame = proxy.plotFrame else { return }
                let plotWidth = geometry[plotFrame].width
                guard plotWidth > 0 else { return }
                let secondsPerPoint =
                    Double(truncating: startWindow.durationSeconds as NSNumber) / plotWidth
                let deltaSeconds = Int64((-value.translation.width * secondsPerPoint).rounded())
                let panned = LiveChartWindow.shared.panned(
                    samples: samples,
                    window: startWindow,
                    deltaSeconds: deltaSeconds
                )
                pannedWindow = LiveChartWindow.shared.isAtLiveEdge(samples: samples, window: panned)
                    ? nil
                    : panned
            }
            .onEnded { _ in
                windowAtDragStart = nil
            }
    }

    private func accessibilityText(window: TrackChartWindow) -> String {
        return "Live power chart from "
            + presenter.formatElapsed(elapsedSeconds: window.startSeconds)
            + " to "
            + presenter.formatElapsed(elapsedSeconds: window.endSeconds)
            + ". Drag to look back over the ride"
    }
}
