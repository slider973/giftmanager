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
            auth: .init(storage: SharedSessionStorage(accessGroup: group))
        )
        return SupabaseClient(supabaseURL: AppConfig.supabaseURL, supabaseKey: AppConfig.supabasePublishableKey, options: options)
    }()
}

/// Session dans le trousseau partagé, avec reprise de l'ancienne session (trousseau propre à l'app)
/// pour ne pas déconnecter les utilisateurs existants lors de la mise à jour.
struct SharedSessionStorage: AuthLocalStorage {
    private let shared: KeychainLocalStorage
    private let legacy = KeychainLocalStorage(service: "supabase.gotrue.swift")

    init(accessGroup: String) {
        shared = KeychainLocalStorage(service: "supabase.gotrue.swift", accessGroup: accessGroup)
    }

    func store(key: String, value: Data) throws {
        try shared.store(key: key, value: value)
    }

    func retrieve(key: String) throws -> Data? {
        if let data = try? shared.retrieve(key: key) { return data }
        guard let data = try? legacy.retrieve(key: key) else { return nil }
        try shared.store(key: key, value: data)
        try? legacy.remove(key: key)
        return data
    }

    func remove(key: String) throws {
        try shared.remove(key: key)
        try? legacy.remove(key: key)
    }
}
