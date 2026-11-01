package ch.jonathanlemaine.giftmanager.push

/**
 * État de l'autorisation d'afficher des notifications. Avant Android 13 il n'y a pas de permission à demander
 * ([NotRequired]) ; à partir d'Android 13 (API 33) c'est `POST_NOTIFICATIONS`.
 */
enum class NotificationPermission {
    /** API < 33 : aucune demande à l'exécution. */
    NotRequired,

    /** Autorisée par l'utilisateur. */
    Granted,

    /** Pas encore accordée, mais le système affichera encore sa boîte de dialogue. */
    CanAsk,

    /** Refusée sans retour possible depuis l'app : seuls les réglages du système peuvent la rétablir. */
    PermanentlyDenied,
    ;

    /** Le jeton de l'appareil peut être enregistré côté serveur (jamais avant l'autorisation, API 33+). */
    val canRegister: Boolean get() = this == NotRequired || this == Granted

    companion object {
        /** Première version d'Android qui demande `POST_NOTIFICATIONS` à l'exécution. */
        const val RUNTIME_PERMISSION_SDK = 33

        /**
         * [shouldShowRationale] : le système indique que l'utilisateur a déjà refusé une fois (la demande est encore possible).
         * [alreadyAsked] : l'app a déjà affiché la boîte de dialogue du système. Sans refus mémorisé, « jamais demandée »
         * et « refusée définitivement » se confondraient, car Android répond `false` dans les deux cas.
         */
        fun resolve(sdk: Int, granted: Boolean, shouldShowRationale: Boolean, alreadyAsked: Boolean): NotificationPermission =
            when {
                sdk < RUNTIME_PERMISSION_SDK -> NotRequired
                granted -> Granted
                shouldShowRationale || !alreadyAsked -> CanAsk
                else -> PermanentlyDenied
            }

        /** La feuille d'explication s'affiche une seule fois, à la première arrivée sur l'écran principal. */
        fun shouldPrompt(permission: NotificationPermission, alreadyPrompted: Boolean): Boolean =
            permission == CanAsk && !alreadyPrompted
    }
}
