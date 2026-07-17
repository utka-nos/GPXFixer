import SwiftUI
import Combine
import shared

@MainActor
final class IOSProfileViewModel: ObservableObject {
    enum Field: Hashable {
        case weight
        case birthYear
        case maxHeartRate
        case heartRateZones
        case ftp
        case powerZones
    }

    enum SexChoice: String, CaseIterable, Identifiable {
        case notSet = "Not set"
        case male = "Male"
        case female = "Female"

        var id: String { rawValue }

        var shared: Sex? {
            switch self {
            case .notSet: return nil
            case .male: return .male
            case .female: return .female
            }
        }

        static func from(_ sex: Sex?) -> SexChoice {
            switch sex {
            case .male: return .male
            case .female: return .female
            default: return .notSet
            }
        }
    }

    @Published var weightText = ""
    @Published var sexChoice = SexChoice.notSet
    @Published var birthYearText = ""
    @Published var maxHeartRateText = ""
    @Published var heartRateBoundTexts = Array(repeating: "", count: 4)
    @Published var ftpText = ""
    @Published var powerBoundTexts = Array(repeating: "", count: 6)
    @Published var errors: [Field: String] = [:]
    @Published var statusMessage: String?

    private let facade = IosUserProfileFacade()
    private let currentYear = Calendar.current.component(.year, from: Date())

    init() {
        let profile = facade.profile()
        if let weight = profile.weightKg?.doubleValue {
            weightText = weight.truncatingRemainder(dividingBy: 1) == 0
                ? String(Int(weight))
                : String(weight)
        }
        sexChoice = SexChoice.from(profile.sex)
        if let birthYear = profile.birthYear?.intValue {
            birthYearText = String(birthYear)
        }
        if let zones = profile.heartRateZones {
            heartRateBoundTexts = zones.upperBoundsBpm.map { String($0.intValue) }
        }
        if let ftp = profile.ftpWatts?.intValue {
            ftpText = String(ftp)
        }
        if let zones = profile.powerZones {
            powerBoundTexts = zones.upperBoundsWatts.map { String($0.intValue) }
        }
    }

    var canEstimateMaxHeartRate: Bool {
        guard let birthYear = Int32(birthYearText.trimmingCharacters(in: .whitespaces)) else { return false }
        return ProfileValidation.shared.isValidBirthYear(birthYear: birthYear, currentYear: Int32(currentYear))
    }

    var canFillHeartRateZones: Bool {
        guard let maxHeartRate = Int32(maxHeartRateText.trimmingCharacters(in: .whitespaces)) else { return false }
        return ProfileValidation.shared.isValidMaxHeartRate(maxHeartRateBpm: maxHeartRate)
    }

    var canDerivePowerZones: Bool {
        guard let ftp = Int32(ftpText.trimmingCharacters(in: .whitespaces)) else { return false }
        return ProfileValidation.shared.isValidFtp(ftpWatts: ftp)
    }

    func estimateMaxHeartRate() {
        guard let birthYear = Int32(birthYearText.trimmingCharacters(in: .whitespaces)) else { return }
        let estimated = ZoneDefaults.shared.estimatedMaxHeartRate(
            birthYear: birthYear,
            currentYear: Int32(currentYear)
        )
        maxHeartRateText = String(estimated)
    }

    func fillHeartRateZonesFromMax() {
        guard let maxHeartRate = Int32(maxHeartRateText.trimmingCharacters(in: .whitespaces)) else { return }
        let zones = ZoneDefaults.shared.heartRateZonesFromMax(maxHeartRateBpm: maxHeartRate)
        heartRateBoundTexts = zones.upperBoundsBpm.map { String($0.intValue) }
    }

    func derivePowerZonesFromFtp() {
        guard let ftp = Int32(ftpText.trimmingCharacters(in: .whitespaces)) else { return }
        let zones = ZoneDefaults.shared.powerZonesFromFtp(ftpWatts: ftp)
        powerBoundTexts = zones.upperBoundsWatts.map { String($0.intValue) }
    }

    func clearHeartRateZones() {
        heartRateBoundTexts = Array(repeating: "", count: heartRateBoundTexts.count)
    }

    func clearPowerZones() {
        powerBoundTexts = Array(repeating: "", count: powerBoundTexts.count)
    }

    func save() {
        var newErrors: [Field: String] = [:]

        var weightKg: KotlinDouble?
        let weightTrimmed = weightText
            .replacingOccurrences(of: ",", with: ".")
            .trimmingCharacters(in: .whitespaces)
        if !weightTrimmed.isEmpty {
            if let parsed = Double(weightTrimmed), ProfileValidation.shared.isValidWeight(weightKg: parsed) {
                weightKg = KotlinDouble(value: parsed)
            } else {
                newErrors[.weight] = "Enter a weight between 20 and 400 kg"
            }
        }

        var birthYear: KotlinInt?
        let birthYearTrimmed = birthYearText.trimmingCharacters(in: .whitespaces)
        if !birthYearTrimmed.isEmpty {
            if let parsed = Int32(birthYearTrimmed),
               ProfileValidation.shared.isValidBirthYear(birthYear: parsed, currentYear: Int32(currentYear)) {
                birthYear = KotlinInt(value: parsed)
            } else {
                newErrors[.birthYear] = "Enter a birth year between 1900 and \(currentYear)"
            }
        }

        let heartRateBounds = parseBounds(
            heartRateBoundTexts,
            field: .heartRateZones,
            rangeDescription: "40–250 bpm",
            errors: &newErrors
        ) { ProfileValidation.shared.validateHeartRateBounds(upperBoundsBpm: $0) }

        var ftpWatts: KotlinInt?
        let ftpTrimmed = ftpText.trimmingCharacters(in: .whitespaces)
        if !ftpTrimmed.isEmpty {
            if let parsed = Int32(ftpTrimmed), ProfileValidation.shared.isValidFtp(ftpWatts: parsed) {
                ftpWatts = KotlinInt(value: parsed)
            } else {
                newErrors[.ftp] = "Enter an FTP between 30 and 2000 W"
            }
        }

        let powerBounds = parseBounds(
            powerBoundTexts,
            field: .powerZones,
            rangeDescription: "1–3000 W",
            errors: &newErrors
        ) { ProfileValidation.shared.validatePowerBounds(upperBoundsWatts: $0) }

        errors = newErrors
        guard newErrors.isEmpty else {
            statusMessage = nil
            return
        }

        facade.save(
            profile: UserProfile(
                weightKg: weightKg,
                sex: sexChoice.shared,
                birthYear: birthYear,
                heartRateZones: heartRateBounds.map { HeartRateZones(upperBoundsBpm: $0) },
                ftpWatts: ftpWatts,
                powerZones: powerBounds.map { PowerZones(upperBoundsWatts: $0) }
            )
        )
        statusMessage = "Profile saved"
    }

    private func parseBounds(
        _ texts: [String],
        field: Field,
        rangeDescription: String,
        errors: inout [Field: String],
        validate: ([KotlinInt]) -> ZoneBoundsError?
    ) -> [KotlinInt]? {
        let trimmed = texts.map { $0.trimmingCharacters(in: .whitespaces) }
        if trimmed.allSatisfy(\.isEmpty) { return nil }
        if trimmed.contains(where: \.isEmpty) {
            errors[field] = "Fill in all \(texts.count) boundaries or leave them all empty"
            return nil
        }

        let parsed = trimmed.compactMap { Int32($0) }
        guard parsed.count == trimmed.count else {
            errors[field] = "Boundaries must be whole numbers"
            return nil
        }

        let bounds = parsed.map { KotlinInt(value: $0) }
        switch validate(bounds) {
        case .outOfRange:
            errors[field] = "Boundaries must be within \(rangeDescription)"
            return nil
        case .notAscending:
            errors[field] = "Each boundary must be higher than the previous one"
            return nil
        default:
            return bounds
        }
    }
}

struct IOSProfileScreen: View {
    @StateObject private var viewModel = IOSProfileViewModel()

    var body: some View {
        Form {
            Section("Basics") {
                LabeledContent("Weight (kg)") {
                    TextField("Not set", text: $viewModel.weightText)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                }
                fieldError(.weight)

                Picker("Sex", selection: $viewModel.sexChoice) {
                    ForEach(IOSProfileViewModel.SexChoice.allCases) { choice in
                        Text(choice.rawValue).tag(choice)
                    }
                }

                LabeledContent("Birth year") {
                    TextField("Not set", text: $viewModel.birthYearText)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
                fieldError(.birthYear)
            }

            Section {
                LabeledContent("Max heart rate (bpm)") {
                    TextField("Not set", text: $viewModel.maxHeartRateText)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
                fieldError(.maxHeartRate)
                Button("Estimate (220 − age)") { viewModel.estimateMaxHeartRate() }
                    .disabled(!viewModel.canEstimateMaxHeartRate)
                Button("Fill zones from max HR") { viewModel.fillHeartRateZonesFromMax() }
                    .disabled(!viewModel.canFillHeartRateZones)

                ForEach(viewModel.heartRateBoundTexts.indices, id: \.self) { index in
                    LabeledContent("Z\(index + 1) upper bound (bpm)") {
                        TextField("Not set", text: $viewModel.heartRateBoundTexts[index])
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
                fieldError(.heartRateZones)
                Button("Clear heart rate zones", role: .destructive) { viewModel.clearHeartRateZones() }
            } header: {
                Text("Heart rate zones")
            } footer: {
                Text("Five zones (Z1–Z5) split by four boundaries; Z5 has no upper limit. "
                    + "Leave all boundaries empty to keep zones unset.")
            }

            Section {
                LabeledContent("FTP (W)") {
                    TextField("Not set", text: $viewModel.ftpText)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
                fieldError(.ftp)
                Button("Derive zones from FTP") { viewModel.derivePowerZonesFromFtp() }
                    .disabled(!viewModel.canDerivePowerZones)

                ForEach(viewModel.powerBoundTexts.indices, id: \.self) { index in
                    LabeledContent("Z\(index + 1) upper bound (W)") {
                        TextField("Not set", text: $viewModel.powerBoundTexts[index])
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
                fieldError(.powerZones)
                Button("Clear power zones", role: .destructive) { viewModel.clearPowerZones() }
            } header: {
                Text("Power zones")
            } footer: {
                Text("Seven Coggan zones (Z1–Z7) split by six boundaries; Z7 has no upper limit. "
                    + "Leave all boundaries empty to keep zones unset.")
            }

            Section {
                Button("Save") { viewModel.save() }
                if let statusMessage = viewModel.statusMessage {
                    Text(statusMessage).foregroundStyle(.green)
                }
            }
        }
        .navigationTitle("Profile")
    }

    @ViewBuilder
    private func fieldError(_ field: IOSProfileViewModel.Field) -> some View {
        if let message = viewModel.errors[field] {
            Text(message)
                .font(.footnote)
                .foregroundStyle(.red)
        }
    }
}
