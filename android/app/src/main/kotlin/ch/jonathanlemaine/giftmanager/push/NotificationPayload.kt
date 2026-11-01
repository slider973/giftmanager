package ch.jonathanlemaine.giftmanager.push

/** Canaux de notification Android (textes dans `strings.xml`, créés par [NotificationPresenter]). */
enum class PushChannel(val id: String) {
    /** Rappels d'achat et alertes de prix. */
    Purchases("purchases"),

    /** Rappels d'anniversaire. */
    Birthdays("birthdays"),

    /** Nouvelles envies, idées, remerciements, réservations. */
    Family("family"),
}

/** Valeurs de `data.type` envoyées par les fonctions Edge (`_shared/push.ts`). */
enum class PushType(val wire: String, val channel: PushChannel) {
    NewItems("new-items", PushChannel.Family),
    Birthday("birthday", PushChannel.Birthdays),
    Purchase("purchase", PushChannel.Purchases),
    Price("price", PushChannel.Purchases),
    Thanks("thanks", PushChannel.Family),
    Reservation("reservation", PushChannel.Family),
    IdeaReview("idea-review", PushChannel.Family),
    ;

    companion object {
        fun from(wire: String?): PushType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Notification à afficher, lue du `data` d'un message FCM « data-only ».
 * [key] regroupe les notifications d'un même sujet (même identifiant et même requestCode : la nouvelle remplace
 * l'ancienne), comme le `thread-id` d'iOS et la `collapse_key` du serveur.
 */
data class NotificationPayload(
    val type: PushType?,
    val title: String,
    val body: String,
    val channel: PushChannel,
    val target: NotificationTarget,
    val key: String,
)

object NotificationPayloadParser {
    private const val KEY_TITLE = "title"
    private const val KEY_BODY = "body"

    /** `null` si le message n'a rien à afficher (ni titre ni texte). Un type inconnu s'affiche sur le canal famille, vers l'Accueil. */
    fun parse(data: Map<String, String>): NotificationPayload? {
        val title = data[KEY_TITLE].orEmpty().trim()
        val body = data[KEY_BODY].orEmpty().trim()
        if (title.isEmpty() && body.isEmpty()) return null
        val type = PushType.from(data[NotificationRouter.KEY_TYPE])
        val subject = listOf("item_id", NotificationRouter.KEY_EVENT_ID, NotificationRouter.KEY_CHILD_ID)
            .firstNotNullOfOrNull { data[it]?.takeIf(String::isNotBlank) }
        return NotificationPayload(
            type = type,
            title = title,
            body = body,
            channel = type?.channel ?: PushChannel.Family,
            target = NotificationRouter.parse(data),
            key = listOfNotNull(type?.wire ?: "other", subject).joinToString("-"),
        )
    }

    /** Seules les « Nouvelles envies » se filtrent à l'affichage : le jeton reste enregistré, les autres types ne dépendent pas de ce réglage. */
    fun shouldDisplay(payload: NotificationPayload, familyNewsEnabled: Boolean): Boolean =
        payload.type != PushType.NewItems || familyNewsEnabled
}
