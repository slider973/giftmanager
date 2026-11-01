@file:UseSerializers(UuidSerializer::class, DecimalSerializer::class)

package ch.jonathanlemaine.giftmanager.core.budgets

import ch.jonathanlemaine.giftmanager.core.model.DecimalSerializer
import ch.jonathanlemaine.giftmanager.core.model.UuidSerializer
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Budget privé d'un utilisateur pour un enfant et/ou un événement, dans une devise (#40) — `Budget`
 * (`GiftRepository+Budgets.swift`). Un budget n'est lu et modifié que par son auteur (RLS).
 */
@Serializable
data class Budget(
    val id: UUID,
    @SerialName("child_id") val childId: UUID? = null,
    /** `null` si l'enfant n'est plus visible (groupe quitté). */
    @SerialName("child_name") val childName: String? = null,
    @SerialName("event_id") val eventId: UUID? = null,
    @SerialName("event_title") val eventTitle: String? = null,
    val amount: BigDecimal,
    val currency: String,
    /** Déjà engagé dans cette devise : cadeaux réservés ou achetés + parts de cagnotte. */
    val spent: BigDecimal,
) {
    /** « Léo · Noël 2026 », « Léo », « Noël 2026 ». */
    val scopeTitle: String
        get() {
            val parts = listOfNotNull(
                if (childId == null) null else childName ?: "Enfant",
                if (eventId == null) null else eventTitle ?: "Événement",
            )
            return parts.joinToString(" · ").ifEmpty { "Budget" }
        }

    val remaining: BigDecimal get() = amount - spent

    val isOver: Boolean get() = spent > amount

    /** Part consommée, plafonnée à 1 pour la jauge. */
    val progress: Double
        get() {
            if (amount.signum() <= 0) return 0.0
            return spent.divide(amount, PROGRESS_SCALE, RoundingMode.HALF_EVEN).toDouble().coerceIn(0.0, 1.0)
        }

    // Le companion d'une classe @Serializable porte `serializer()` : il ne doit pas être privé, sinon le
    // décodage depuis un autre fichier lève IllegalAccessError à l'exécution sur Android.
    companion object {
        private const val PROGRESS_SCALE = 10
    }
}
