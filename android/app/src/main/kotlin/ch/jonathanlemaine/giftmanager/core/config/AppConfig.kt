package ch.jonathanlemaine.giftmanager.core.config

import ch.jonathanlemaine.giftmanager.BuildConfig

/**
 * Configuration injectée au build (BuildConfig), elle-même alimentée depuis 1Password
 * (voir docs/SECRETS.md). Valeurs publiques uniquement : jamais la secret_key.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
) {
    companion object {
        fun fromBuildConfig(): AppConfig = AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}
