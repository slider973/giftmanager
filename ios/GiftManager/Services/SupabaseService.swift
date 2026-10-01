import Foundation
import Supabase

/// Point d'accès unique au client Supabase.
enum SupabaseService {
    static let client = SupabaseClient(
        supabaseURL: AppConfig.supabaseURL,
        supabaseKey: AppConfig.supabasePublishableKey
    )
}
