import Foundation

/// Configuration lue dans l'Info.plist (alimentée par les xcconfig, eux-mêmes générés depuis 1Password).
enum AppConfig {
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              let url = URL(string: raw) else {
            fatalError("SupabaseURL manquant dans Info.plist")
        }
        return url
    }()

    static let supabasePublishableKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String,
              !key.isEmpty else {
            fatalError("SupabasePublishableKey manquant dans Info.plist")
        }
        return key
    }()
}
