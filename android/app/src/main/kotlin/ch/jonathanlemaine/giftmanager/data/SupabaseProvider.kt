package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import ch.jonathanlemaine.giftmanager.core.session.DeepLinkRoute
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/** Construit le client Supabase unique de l'app avec les modules utilisés (comme sur iOS). */
object SupabaseProvider {
    fun create(config: AppConfig): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.supabasePublishableKey,
        ) {
            install(Auth) {
                // Connexion Apple web (#83) : Custom Tab, puis retour par giftmanager://login-callback (PKCE).
                scheme = DeepLinkRoute.AUTH_SCHEME
                host = DeepLinkRoute.AUTH_HOST
                flowType = FlowType.PKCE
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
}
