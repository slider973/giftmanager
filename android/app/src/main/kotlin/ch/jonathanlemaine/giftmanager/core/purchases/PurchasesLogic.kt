package ch.jonathanlemaine.giftmanager.core.purchases

import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import java.math.BigDecimal
import java.util.UUID

/**
 * Calculs de l'écran « Mes achats », séparés de son affichage (iOS : `MyPurchasesModel`).
 *
 * Le total par devise est le point sensible : il mélange prix des liens et parts de cagnotte, et une erreur y
 * fausserait le budget sans rien casser visiblement.
 */
object PurchasesLogic {
    /** Titre d'un événement absent. */
    const val NO_EVENT_TITLE = "Sans événement"

    private const val NO_EVENT_KEY = "none"

    /** Les cadeaux réservés pour un enfant (regroupé par identifiant : deux enfants peuvent avoir le même prénom). */
    data class ChildGroup(val childId: UUID, val name: String, val items: List<MyReservation>)

    /** Un événement, ses enfants, et les cadeaux réservés pour chacun. */
    data class EventGroup(val id: String, val title: String, val date: DayDate?, val children: List<ChildGroup>)

    data class CurrencyTotal(val currency: String, val amount: BigDecimal)

    private val byName = String.CASE_INSENSITIVE_ORDER

    /**
     * Réservations groupées par événement puis par enfant, les deux triés. Les événements les plus proches
     * d'abord ; ceux sans date passent en dernier. L'ordre est déterministe (titre puis identifiant à égalité).
     */
    fun groups(reservations: List<MyReservation>): List<EventGroup> =
        reservations.groupBy { it.eventId?.toString() ?: NO_EVENT_KEY }
            .map { (key, items) ->
                val first = items.first()
                val children = items.groupBy { it.childId }
                    .map { (childId, mine) ->
                        ChildGroup(
                            childId,
                            mine.first().childName,
                            mine.sortedWith(compareBy<MyReservation, String>(byName) { it.title }.thenBy { it.itemId }),
                        )
                    }
                    .sortedWith(compareBy<ChildGroup, String>(byName) { it.name }.thenBy { it.childId })
                EventGroup(key, first.eventTitle ?: NO_EVENT_TITLE, first.eventDate, children)
            }
            .sortedWith(
                compareBy<EventGroup, DayDate?>(nullsLast()) { it.date }
                    .thenBy(byName) { it.title }
                    .thenBy { it.id },
            )

    /** Cadeaux réservés qu'il reste à acheter. */
    fun remainingCount(reservations: List<MyReservation>): Int = reservations.count { it.status == ReservationState.RESERVED }

    /** Sans réservation, le résumé compte les cagnottes. */
    fun summaryCountLabel(reservations: List<MyReservation>, contributions: List<MyContribution>): String {
        if (reservations.isEmpty()) return if (contributions.size > 1) "cagnottes" else "cagnotte"
        return if (remainingCount(reservations) > 1) "cadeaux à acheter" else "cadeau à acheter"
    }

    /** Lien le plus pertinent pour ce cadeau (boutique du pays de l'utilisateur en priorité). */
    fun bestLink(itemId: UUID, links: Map<UUID, List<ItemLink>>, preferredCountry: String?): ItemLink? =
        StoreCatalog.sorted(links[itemId].orEmpty(), preferredCountry).firstOrNull()

    /**
     * Total par devise : prix du meilleur lien de chaque réservation, plus ma part de cagnotte.
     *
     * Les devises ne sont jamais converties ici — additionner des CHF et des EUR donnerait un montant faux. Un
     * cadeau sans prix connu est simplement ignoré ; pour une cagnotte, seule ma part compte, pas le total du cadeau.
     */
    fun totals(
        reservations: List<MyReservation>,
        contributions: List<MyContribution>,
        links: Map<UUID, List<ItemLink>>,
        preferredCountry: String?,
    ): List<CurrencyTotal> {
        val sums = sortedMapOf<String, BigDecimal>()
        for (reservation in reservations) {
            val link = bestLink(reservation.itemId, links, preferredCountry) ?: continue
            val price = link.price ?: continue
            val currency = (link.currency ?: "EUR").uppercase()
            sums[currency] = (sums[currency] ?: BigDecimal.ZERO) + price
        }
        for (contribution in contributions) {
            val currency = contribution.currency.uppercase()
            sums[currency] = (sums[currency] ?: BigDecimal.ZERO) + contribution.amount
        }
        return sums.map { CurrencyTotal(it.key, it.value) }
    }
}
