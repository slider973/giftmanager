package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import java.net.URI
import java.util.Locale

/** Lien d'invitation `famillecadeaux://join/CODE` (ou `famillecadeaux://join?code=CODE`). */
object InviteLink {
    const val SCHEME = "famillecadeaux"
    const val HOST = "join"

    /** Code d'invitation en majuscules contenu dans [uri], ou `null` si ce n'est pas un lien d'invitation. */
    fun parse(uri: String?): String? {
        val parsed = try {
            URI(uri ?: return null)
        } catch (_: Exception) {
            return null
        }
        if (!parsed.scheme.equals(SCHEME, ignoreCase = true) || !parsed.host.equals(HOST, ignoreCase = true)) return null
        val fromPath = parsed.path.orEmpty().split('/').firstOrNull { it.isNotEmpty() }
        val fromQuery = parsed.query.orEmpty().split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it[0] == "code" && it.size == 2 }?.get(1)
        return (fromPath ?: fromQuery)?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
    }

    /**
     * Texte partagé pour inviter dans [group] (iOS : `AppState.inviteMessage`). Identique sur les deux
     * plateformes : c'est un contenu envoyé à des tiers, pas un texte d'interface.
     */
    fun message(group: FamilyGroup): String =
        "Rejoins « ${group.name} » sur Gift Manager pour coordonner les cadeaux des enfants 🎁\n" +
            "Code d'invitation : ${group.inviteCode}\n" +
            "$SCHEME://$HOST/${group.inviteCode}"
}
