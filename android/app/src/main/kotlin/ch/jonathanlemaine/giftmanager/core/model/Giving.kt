@file:UseSerializers(UuidSerializer::class, DecimalSerializer::class)

package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.format.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Modèles v2 : cagnotte (#35), équilibre (#41), remerciements (#42) — ios/GiftManager/Core/GivingModels.swift.
// Règle d'anonymat : ces types ne portent jamais l'identité d'un acheteur, sauf
// les participants d'une cagnotte entre eux et les donateurs qui se sont dévoilés.

// region Cagnotte

/** Participant d'une cagnotte, tel que renvoyé par `pot_participants` (réservé aux participants). */
@Serializable
data class PotParticipant(
    @SerialName("display_name") val displayName: String,
    val amount: BigDecimal,
    val currency: String,
    @SerialName("is_me") val isMe: Boolean,
) {
    val amountText: String get() = Money.format(amount, currency).orEmpty()
}

/** Une de mes participations, telle que renvoyée par `my_contributions`. */
@Serializable
data class MyContribution(
    @SerialName("item_id") val itemId: UUID,
    val title: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val amount: BigDecimal,
    val currency: String,
    val owned: Boolean,
    @SerialName("pot_total") val potTotal: BigDecimal? = null,
    @SerialName("pot_count") val potCount: Int? = null,
    @SerialName("child_id") val childId: UUID,
    @SerialName("child_name") val childName: String,
    @SerialName("event_id") val eventId: UUID? = null,
    @SerialName("event_title") val eventTitle: String? = null,
    @SerialName("event_date") val eventDate: DayDate? = null,
) {
    val id: UUID get() = itemId
}

/** Progression d'une cagnotte : montant réuni face au prix du cadeau dans la même devise. */
data class PotProgress(
    val collected: BigDecimal,
    val currency: String,
    /** Prix du cadeau dans la devise de la cagnotte ; `null` si aucun lien ne le donne. */
    val target: BigDecimal?,
    val participants: Int,
) {
    /** Part réunie, entre 0 et 1 ; `null` sans prix de référence. */
    val fraction: Double?
        get() {
            val target = target?.takeIf { it.signum() > 0 } ?: return null
            val ratio = collected.divide(target, MATH_SCALE, RoundingMode.HALF_EVEN).toDouble()
            return ratio.coerceIn(0.0, 1.0)
        }

    /** Reste à réunir (jamais négatif) ; `null` sans prix de référence. */
    val remaining: BigDecimal? get() = target?.let { (it - collected).max(BigDecimal.ZERO) }

    val isComplete: Boolean get() = remaining?.signum() == 0

    val collectedText: String get() = Money.format(collected, currency).orEmpty()
    val targetText: String? get() = Money.format(target, currency)
    val remainingText: String? get() = Money.format(remaining, currency)

    val participantsText: String
        get() = if (participants > 1) "$participants participants" else "$participants participant"

    companion object {
        private const val MATH_SCALE = 10

        /** Prix de référence : premier lien (dans l'ordre de préférence) affiché dans la devise de la cagnotte. */
        fun target(currency: String, links: List<ItemLink>): BigDecimal? =
            links.firstOrNull {
                it.currency?.uppercase() == currency.uppercase() && (it.price?.signum() ?: 0) > 0
            }?.price

        /** Progression d'un cadeau en cagnotte ; `null` s'il n'y a pas de cagnotte (ou pour un parent). */
        fun make(item: WishItem, links: List<ItemLink>): PotProgress? {
            val total = item.potTotal ?: return null
            val currency = item.potCurrency ?: return null
            val count = item.potCount?.takeIf { it > 0 } ?: return null
            return PotProgress(total, currency, target(currency, links), count)
        }
    }
}

/** Saisie d'un montant de participation (« 25 », « 12,50 », « 12.5 »). */
object PotAmount {
    val maximum: BigDecimal = BigDecimal(99_999_999)

    fun parse(text: String): BigDecimal? {
        val cleaned = text.trim()
            .replace("’", "")
            .replace("'", "")
            .replace(" ", "")
            .replace(" ", "")
            .replace(",", ".")
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) {
            return null
        }
        val rounded = cleaned.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP) ?: return null
        return rounded.takeIf { it.signum() > 0 && it <= maximum }
    }

    /** Texte éditable d'un montant : on garde la forme courte (« 12,5 »). */
    fun editableText(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString().replace('.', ',')

    /** Montants proposés en un geste : des montants ronds, et le reste à réunir s'il est connu. */
    fun suggestions(remaining: BigDecimal?): List<BigDecimal> {
        var values = listOf(BigDecimal(10), BigDecimal(20), BigDecimal(50))
        if (remaining != null && remaining.signum() > 0) {
            values = values.filter { it < remaining }
            if (values.none { it.compareTo(remaining) == 0 }) values = values + remaining
        }
        return values
    }
}

// endregion

// region Équilibre entre enfants

/**
 * Compteurs anonymes d'un enfant (`children_reservation_counts`). Le serveur n'en renvoie
 * jamais pour mes propres enfants : l'absence de compteur ne s'affiche pas.
 */
@Serializable
data class ReservationCounts(
    @SerialName("child_id") val childId: UUID,
    @SerialName("reserved_count") val reservedCount: Int,
    @SerialName("purchased_count") val purchasedCount: Int,
    @SerialName("pot_count") val potCount: Int,
) {
    val total: Int get() = reservedCount + purchasedCount + potCount

    /** « 2 réservés · 1 acheté · 1 cagnotte », ou une invitation douce quand rien n'est prévu. */
    val summaryText: String
        get() {
            if (total == 0) return "Rien de prévu pour l'instant"
            val parts = buildList {
                if (reservedCount > 0) add("$reservedCount réservé${plural(reservedCount)}")
                if (purchasedCount > 0) add("$purchasedCount acheté${plural(purchasedCount)}")
                if (potCount > 0) add("$potCount cagnotte${plural(potCount)}")
            }
            return parts.joinToString(" · ")
        }

    /** Lecture TalkBack, sans abréviation. */
    val accessibilityText: String
        get() {
            if (total == 0) return "Aucun cadeau prévu pour l'instant"
            val parts = buildList {
                if (reservedCount > 0) add("$reservedCount cadeau${plural(reservedCount, "x")} réservé${plural(reservedCount)}")
                if (purchasedCount > 0) add("$purchasedCount acheté${plural(purchasedCount)}")
                if (potCount > 0) add("$potCount en cagnotte")
            }
            return parts.joinToString(", ")
        }

    private fun plural(count: Int, suffix: String = "s") = if (count > 1) suffix else ""
}

// endregion

// region Remerciements

/** Remerciement reçu pour un cadeau que j'ai offert (`my_thanks`). */
@Serializable
data class ThanksNote(
    val id: UUID,
    @SerialName("item_id") val itemId: UUID,
    val title: String,
    @SerialName("child_name") val childName: String,
    /** Parent qui a écrit le message (son identité n'est pas protégée). */
    @SerialName("sender_name") val senderName: String? = null,
    val message: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    /** Je me suis fait connaître des parents pour ce cadeau. */
    val revealed: Boolean,
) {
    val createdDate: java.time.Instant? get() = Timestamp.parse(createdAt)
}

/** Donateur qui s'est fait connaître (`item_donors`, réservé aux parents). */
@Serializable
data class ItemDonor(
    @SerialName("display_name") val displayName: String,
)

/** Règles d'affichage du « Reçu ! » et des remerciements (#42). */
object ReceiptRules {
    /** L'événement a commencé : le jour même ou après. */
    fun hasStarted(eventDate: DayDate?, clock: Clock = Clock.systemDefaultZone()): Boolean =
        eventDate?.hasStarted(clock) ?: false

    /** « Reçu ! » : un parent, sur un souhait pas encore possédé, une fois la fête arrivée. */
    fun canMarkReceived(
        isParent: Boolean,
        kind: WishKind,
        owned: Boolean,
        eventDate: DayDate?,
        clock: Clock = Clock.systemDefaultZone(),
    ): Boolean = isParent && kind == WishKind.WISH && !owned && hasStarted(eventDate, clock)

    /** Remercier : un parent, sur un souhait reçu, une fois la fête arrivée. */
    fun canSendThanks(
        isParent: Boolean,
        kind: WishKind,
        owned: Boolean,
        eventDate: DayDate?,
        clock: Clock = Clock.systemDefaultZone(),
    ): Boolean = isParent && kind == WishKind.WISH && owned && hasStarted(eventDate, clock)

    /**
     * Côté donateur : le cadeau est noté possédé après la fête, il a donc été reçu
     * (avant la fête, c'est une alerte « déjà possédé »).
     */
    fun isReceived(owned: Boolean, eventDate: DayDate?, clock: Clock = Clock.systemDefaultZone()): Boolean =
        owned && hasStarted(eventDate, clock)
}

// endregion
