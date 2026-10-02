import Foundation
import Supabase

/// Point d'accès unique au client Supabase.
/// La session est rangée dans un groupe de trousseau partagé avec l'extension de partage et le widget.
enum SupabaseService {
    static let client: SupabaseClient = {
        guard let group = AppConfig.sharedKeychainGroup else {
            return SupabaseClient(supabaseURL: AppConfig.supabaseURL, supabaseKey: AppConfig.supabasePublishableKey)
        }
        let options = SupabaseClientOptions(
            auth: .init(storage: SharedSessionStorage.live(sharedGroup: group, legacyGroup: AppConfig.legacyKeychainGroup))
        )
        return SupabaseClient(supabaseURL: AppConfig.supabaseURL, supabaseKey: AppConfig.supabasePublishableKey, options: options)
    }()
}

/// Session dans le trousseau partagé, avec reprise de l'ancienne session (trousseau propre à l'app)
/// pour ne pas déconnecter les utilisateurs existants lors de la mise à jour.
/// Chaque trousseau est épinglé à son groupe : supprimer l'ancien ne touche jamais la copie partagée.
struct SharedSessionStorage: AuthLocalStorage {
    let shared: any AuthLocalStorage
    let legacy: (any AuthLocalStorage)?

    static func live(sharedGroup: String, legacyGroup: String?) -> SharedSessionStorage {
        SharedSessionStorage(
            shared: KeychainLocalStorage(service: "supabase.gotrue.swift", accessGroup: sharedGroup),
            legacy: legacyGroup.map { KeychainLocalStorage(service: "supabase.gotrue.swift", accessGroup: $0) }
        )
    }

    func store(key: String, value: Data) throws {
        try shared.store(key: key, value: value)
    }

    func retrieve(key: String) throws -> Data? {
        if let data = try? shared.retrieve(key: key) { return data }
        guard let legacy, let data = try? legacy.retrieve(key: key) else { return nil }
        try shared.store(key: key, value: data)
        try? legacy.remove(key: key)
        return data
    }

    /// Les deux suppressions sont toujours tentées : un reste dans l'ancien trousseau serait re-migré
    /// au lancement suivant et reconnecterait l'utilisateur.
    func remove(key: String) throws {
        try? shared.remove(key: key)
        try? legacy?.remove(key: key)
    }
}
