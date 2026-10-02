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

    /// Préfixe d'identifiant d'app (« TEAMID. »). `nil` dans les builds non signés (tests CI).
    static let appIdentifierPrefix: String? = {
        guard let prefix = Bundle.main.object(forInfoDictionaryKey: "AppIdentifierPrefix") as? String,
              prefix.hasSuffix("."), prefix.count > 1 else { return nil }
        return prefix
    }()

    /// Groupe de trousseau commun à l'app et à ses extensions (« TEAMID.ch.jonathanlemaine.giftmanager.shared »).
    /// `nil` dans les builds non signés : stockage par défaut.
    static let sharedKeychainGroup: String? = appIdentifierPrefix.map { $0 + "ch.jonathanlemaine.giftmanager.shared" }

    /// Groupe de trousseau propre à l'app (« TEAMID.<bundle id> »), où était rangée la session avant le partage.
    /// `nil` dans les extensions : elles n'ont jamais eu de session à reprendre.
    static let legacyKeychainGroup: String? = {
        guard let prefix = appIdentifierPrefix, !Bundle.main.bundlePath.hasSuffix(".appex"),
              let bundleId = Bundle.main.bundleIdentifier else { return nil }
        return prefix + bundleId
    }()
}
