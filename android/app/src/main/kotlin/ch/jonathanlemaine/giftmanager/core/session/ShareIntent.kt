package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewParser

/**
 * Lecture pure d'un partage reçu d'une autre app (iOS : `GiftManagerShare`). Ne touche ni à `Intent` ni aux logs :
 * le lien partagé peut contenir des jetons de suivi ou de session.
 */
object ShareIntent {
    const val ACTION_SEND = "android.intent.action.SEND"
    private const val MIME_TEXT = "text/plain"

    /** Partage de texte (`ACTION_SEND` + `text/plain`), avec ou sans lien dedans. */
    fun isTextShare(action: String?, mimeType: String?): Boolean =
        action == ACTION_SEND && mimeType?.substringBefore(';')?.trim().equals(MIME_TEXT, ignoreCase = true)

    /**
     * Premier lien http(s) du texte partagé, sinon du sujet (certaines apps y mettent l'URL). `null` pour tout
     * autre partage ou sans lien exploitable ; jamais de schéma autre que http et https.
     */
    fun extractLink(action: String?, mimeType: String?, text: String?, subject: String?): String? {
        if (!isTextShare(action, mimeType)) return null
        return listOfNotNull(text, subject).firstNotNullOfOrNull(::httpLink)
    }

    private fun httpLink(raw: String): String? = LinkPreviewParser.normalizedUrl(raw)?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
}
