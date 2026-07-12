import SwiftUI
import Combine
import shared

@MainActor
final class IOSHeartRateSensorViewModel: ObservableObject {
    @Published var devices: [HeartRateSensorDevice] = []
    @Published var selected: SelectedHeartRateSensor?
    @Published var isScanning = false
    @Published var isConnecting = false
    @Published var errorMessage: String?

    private let facade = IosHeartRateSensorFacade()

    init() {
        selected = facade.selectedSensor()
    }

    func startScan() {
        isScanning = true
        errorMessage = nil
        facade.startScan { [weak self] device in
            guard let self else { return }
            self.devices.removeAll { $0.id == device.id }
            self.devices.append(device)
            self.devices.sort { $0.rssi > $1.rssi }
        } onError: { [weak self] message in
            self?.errorMessage = message
            self?.isScanning = false
        }
    }

    func select(_ device: HeartRateSensorDevice) {
        isConnecting = true
        facade.select(device: device) { [weak self] connected in
            guard let self else { return }
            self.isConnecting = false
            // facade.select stops the scan either way; keep the UI flag in sync.
            self.isScanning = false
            if connected.boolValue {
                self.selected = SelectedHeartRateSensor(id: device.id, name: device.name)
            } else {
                self.errorMessage = "Could not connect to sensor"
            }
        }
    }

    func forget() {
        facade.forget()
        selected = nil
        devices = []
    }

    func stopScan() {
        facade.closeScreen()
        isScanning = false
    }
}

struct IOSHeartRateSensorScreen: View {
    @StateObject private var viewModel = IOSHeartRateSensorViewModel()

    var body: some View {
        List {
            if let selected = viewModel.selected {
                Section("Selected sensor") {
                    Text(selected.name ?? selected.id)
                    Button("Forget sensor", role: .destructive) { viewModel.forget() }
                }
            }

            Section("Nearby heart rate sensors") {
                if viewModel.isScanning || viewModel.isConnecting {
                    ProgressView(viewModel.isConnecting ? "Connecting…" : "Scanning…")
                }
                if let error = viewModel.errorMessage {
                    Text(error).foregroundStyle(.red)
                }
                ForEach(viewModel.devices, id: \.id) { device in
                    Button { viewModel.select(device) } label: {
                        VStack(alignment: .leading) {
                            Text(device.name ?? "Heart rate sensor")
                            Text("RSSI \(device.rssi) dBm")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(viewModel.isConnecting)
                }
            }
        }
        .navigationTitle("Heart rate sensor")
        .onAppear { viewModel.startScan() }
        .onDisappear { viewModel.stopScan() }
        .toolbar {
            Button("Scan") { viewModel.startScan() }
                .disabled(viewModel.isScanning)
        }
    }
}
