package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.BuildConfig

/**
 * Configuration Firebase des notifications push (#76), injectée au build depuis 1Password (voir docs/SECRETS.md) :
 * pas de plugin google-services, [PushRuntime] initialise Firebase avec ces quatre valeurs publiques.
 */
data class FirebaseConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String,
) {
    /** Faux tant qu'une valeur est vide, factice (build local sans secrets, CI) ou à renseigner : le push est alors désactivé. */
    val isConfigured: Boolean
        get() = listOf(apiKey, appId, projectId, senderId).all { it.isUsable() }

    companion object {
        private const val PLACEHOLDER = "A_RENSEIGNER"

        private fun String.isUsable() = isNotBlank() && !contains("dummy") && this != PLACEHOLDER

        fun fromBuildConfig(): FirebaseConfig = FirebaseConfig(
            apiKey = BuildConfig.FIREBASE_API_KEY,
            appId = BuildConfig.FIREBASE_APP_ID,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            senderId = BuildConfig.FIREBASE_SENDER_ID,
        )
    }
}
