package ch.jonathanlemaine.giftmanager.core.config

import ch.jonathanlemaine.giftmanager.BuildConfig

/**
 * Configuration injectée au build (BuildConfig), elle-même alimentée depuis 1Password
 * (voir docs/SECRETS.md). Valeurs publiques uniquement : jamais la secret_key.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    /** ID client OAuth « Web » Google (serverClientId de Credential Manager). */
    val googleWebClientId: String = "",
) {
    /** Faux tant que l'ID client Google est vide ou factice (build local sans secrets, CI). */
    val isGoogleConfigured: Boolean
        get() = googleWebClientId.isNotBlank() &&
            !googleWebClientId.contains("dummy") &&
            googleWebClientId != PLACEHOLDER

    companion object {
        private const val PLACEHOLDER = "A_RENSEIGNER"

        fun fromBuildConfig(): AppConfig = AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
    }
}
