package ch.jonathanlemaine.giftmanager.core.session

import java.net.URI

/**
 * Destination d'un lien reçu par `MainActivity` : retour de la connexion Apple dans le navigateur
 * (`giftmanager://login-callback?code=…`), invitation (`famillecadeaux://join/CODE`) ou rien d'autre.
 * Le partage (ACTION_SEND) n'a pas d'URI : il est traité à part.
 */
sealed interface DeepLinkRoute {
    /** Retour du flux OAuth web (#83) : le code d'autorisation est échangé contre une session. */
    data object AuthCallback : DeepLinkRoute

    data class Invite(val code: String) : DeepLinkRoute

    data object None : DeepLinkRoute

    companion object {
        /** Schéma dédié au retour d'authentification, distinct de celui des invitations. */
        const val AUTH_SCHEME = "giftmanager"
        const val AUTH_HOST = "login-callback"
        const val AUTH_REDIRECT = "$AUTH_SCHEME://$AUTH_HOST"

        fun parse(uri: String?): DeepLinkRoute {
            if (isAuthCallback(uri)) return AuthCallback
            return InviteLink.parse(uri)?.let(::Invite) ?: None
        }

        private fun isAuthCallback(uri: String?): Boolean {
            val parsed = try {
                URI(uri ?: return false)
            } catch (_: Exception) {
                return false
            }
            return parsed.scheme.equals(AUTH_SCHEME, ignoreCase = true) && parsed.host.equals(AUTH_HOST, ignoreCase = true)
        }
    }
}
