package ch.jonathanlemaine.giftmanager.core.gifts

/** Règles du message de remerciement (iOS : `ThanksComposerView`). Fonctions pures. */
object ThanksLogic {
    /** Longueur maximale du message, en points de code (un émoji compte pour un). */
    const val MAX_LENGTH = 1000

    /** Message proposé au départ, que le parent peut réécrire. */
    fun defaultMessage(title: String): String = "Merci beaucoup pour « $title » !"

    /** Message tel qu'il est envoyé : sans espaces autour. */
    fun trimmed(message: String): String = message.trim()

    /** Longueur du message nettoyé, en points de code. */
    fun length(message: String): Int = trimmed(message).let { it.codePointCount(0, it.length) }

    fun isTooLong(message: String): Boolean = length(message) > MAX_LENGTH

    /** Non vide, pas trop long, et aucun envoi en cours. */
    fun canSend(message: String, isSending: Boolean): Boolean {
        val length = length(message)
        return length in 1..MAX_LENGTH && !isSending
    }

    /** « Mamie », « Mamie et Paul », « Mamie, Paul et Léa ». */
    fun donorNames(names: List<String>): String {
        if (names.size <= 1) return names.firstOrNull().orEmpty()
        return names.dropLast(1).joinToString(", ") + " et " + names.last()
    }
}
