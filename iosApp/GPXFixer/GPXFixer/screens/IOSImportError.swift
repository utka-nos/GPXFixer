import Foundation

enum IOSImportError: LocalizedError {
    case invalidTrack(String)

    var errorDescription: String? {
        switch self {
        case .invalidTrack(let message):
            return message
        }
    }
}
