import Foundation

enum IOSImportError: LocalizedError {
    case invalidGpx(String)

    var errorDescription: String? {
        switch self {
        case .invalidGpx(let message):
            return message
        }
    }
}
