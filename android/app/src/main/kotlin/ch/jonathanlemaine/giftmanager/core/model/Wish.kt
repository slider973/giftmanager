@file:UseSerializers(UuidSerializer::class, DecimalSerializer::class)

package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.format.Money
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Cadeaux, idées et réservations — ios/GiftManager/Core/Models.swift.

@Serializable
enum class WishKind {
    @SerialName("wish") WISH,
    @SerialName("idea") IDEA,
}

/** Statut public d'un cadeau, tel que renvoyé par `child_items`. `null` = mode surprise (parent). */
@Serializable
enum class ItemStatus {
    @SerialName("available") AVAILABLE,
    @SerialName("taken") TAKEN,
    @SerialName("mine") MINE,
    @SerialName("owned") OWNED,

    /** Cadeau financé à plusieurs (#35). */
    @SerialName("pot") POT,
}

@Serializable
enum class ReservationState {
    @SerialName("reserved") RESERVED,
    @SerialName("purchased") PURCHASED,
}

/**
 * Validation d'une idée par les parents (#57).
 *
 * Le parent est le seul à savoir si un cadeau convient (âge, doublon, règles de la maison) :
 * l'écarter ne protégeait rien, puisque le secret porte sur la réservation, pas sur l'objet.
 */
@Serializable
enum class IdeaReview(val label: String) {
    /** Idée non soumise : invisible des parents, comme avant. */
    @SerialName("none") NONE("Surprise totale"),
    @SerialName("pending") PENDING("En attente"),
    @SerialName("accepted") ACCEPTED("Acceptée"),
    @SerialName("rejected") REJECTED("Refusée"),
    @SerialName("owned_already") OWNED_ALREADY("Déjà possédé");

    val isSubmitted: Boolean get() = this != NONE
}

/** Idée soumise par un membre, en attente du verdict d'un parent (#57). */
@Serializable
data class PendingIdea(
    val id: UUID,
    @SerialName("child_id") val childId: UUID,
    @SerialName("child_name") val childName: String,
    val title: String,
    val notes: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_by") val createdBy: UUID? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant,
) {
    val author: String get() = authorName?.takeIf { it.isNotEmpty() } ?: "Quelqu'un de la famille"
}

/**
 * Décodage tolérant : les champs de validation (#57) et de cagnotte (#35) sont absents des réponses
 * plus anciennes et des charges utiles réduites (widget, extension de partage).
 */
@Serializable
data class WishItem(
    val id: UUID,
    @SerialName("child_id") val childId: UUID,
    @SerialName("event_id") val eventId: UUID? = null,
    val kind: WishKind,
    val title: String,
    val notes: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val priority: Int = 0,
    val position: Int = 0,
    val owned: Boolean = false,
    @SerialName("created_by") val createdBy: UUID? = null,
    val status: ItemStatus? = null,
    @SerialName("my_reservation") val myReservation: ReservationState? = null,
    /** Cagnotte (#35) : jamais renseignée pour les parents. */
    @SerialName("pot_total") val potTotal: BigDecimal? = null,
    @SerialName("pot_currency") val potCurrency: String? = null,
    @SerialName("pot_count") val potCount: Int? = null,
    /** Ma part dans la cagnotte, si je participe. */
    @SerialName("my_contribution") val myContribution: BigDecimal? = null,
    /** Validation parentale d'une idée (#57). */
    @SerialName("review_status") val reviewStatus: IdeaReview = IdeaReview.NONE,
    @SerialName("review_note") val reviewNote: String? = null,
) {
    val isFavorite: Boolean get() = priority > 0

    /** Je participe à la cagnotte de ce cadeau. */
    val isInMyPot: Boolean get() = (myContribution ?: BigDecimal.ZERO).signum() > 0
}

@Serializable
data class ItemLink(
    val id: UUID,
    @SerialName("item_id") val itemId: UUID,
    val url: String,
    val store: String? = null,
    val country: String? = null,
    val price: BigDecimal? = null,
    val currency: String? = null,
) {
    val priceText: String? get() = Money.format(price, currency)
}

@Serializable
data class MyReservation(
    @SerialName("item_id") val itemId: UUID,
    val title: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: ReservationState,
    val owned: Boolean,
    @SerialName("child_id") val childId: UUID,
    @SerialName("child_name") val childName: String,
    @SerialName("event_id") val eventId: UUID? = null,
    @SerialName("event_title") val eventTitle: String? = null,
    @SerialName("event_date") val eventDate: DayDate? = null,
) {
    val id: UUID get() = itemId
}

/** Lien saisi avant enregistrement (formulaire d'ajout de cadeau). */
data class DraftLink(
    val id: UUID = UUID.randomUUID(),
    val url: String,
    val store: String? = null,
    val country: String? = null,
    val price: BigDecimal? = null,
    val currency: String? = null,
)
