package ch.jonathanlemaine.giftmanager.data.auth

/** Échecs de connexion, sans jamais embarquer de jeton ni de nonce dans le message. */
sealed class AuthError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** L'utilisateur a fermé la fenêtre de choix du compte. */
    class Cancelled(cause: Throwable? = null) : AuthError("Connexion annulée", cause)

    /** Aucun compte Google sur l'appareil. */
    class NoGoogleAccount(cause: Throwable? = null) : AuthError("Aucun compte Google", cause)

    /** Services Google absents ou inutilisables. */
    class GoogleUnavailable(cause: Throwable? = null) : AuthError("Services Google indisponibles", cause)

    /** ID client Google absent de cette version de l'app. */
    class NotConfigured : AuthError("Connexion Google non configurée")

    /** Connexion Apple (web) pas encore activée : Services ID absent côté Apple ou Supabase. */
    class AppleNotConfigured : AuthError("Connexion Apple non configurée")

    class Network(cause: Throwable? = null) : AuthError("Réseau indisponible", cause)

    class Failed(cause: Throwable? = null) : AuthError("Connexion échouée", cause)
}
