package ch.jonathanlemaine.giftmanager.core.purchases

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Calculs de l'écran « Mes achats », vérifiés sans réseau ni interface (port de `MyPurchasesModelTests`). */
class PurchasesLogicTest {
    private fun dec(text: String) = BigDecimal(text)

    private fun reservation(
        title: String,
        child: String,
        status: ReservationState = ReservationState.RESERVED,
        eventId: UUID? = null,
        eventTitle: String? = null,
        eventDate: String? = null,
        itemId: UUID = UUID.randomUUID(),
        childId: UUID = UUID.nameUUIDFromBytes(child.toByteArray()),
    ) = MyReservation(
        itemId, title, null, status, false, childId, child, eventId, eventTitle, eventDate?.let(DayDate::parse),
    )

    private fun contribution(amount: String, currency: String, itemId: UUID = UUID.randomUUID()) =
        MyContribution(itemId, "Vélo", null, dec(amount), currency, false, null, null, uuid(1), "Epril")

    private fun link(itemId: UUID, price: String?, currency: String?, country: String? = null, store: String = "Boutique") =
        ItemLink(UUID.randomUUID(), itemId, "https://example.test/p", store, country, price?.let(::dec), currency)

    private fun totals(
        reservations: List<MyReservation> = emptyList(),
        contributions: List<MyContribution> = emptyList(),
        links: List<ItemLink> = emptyList(),
        country: String? = "CH",
    ) = PurchasesLogic.totals(reservations, contributions, links.groupBy { it.itemId }, country)

    // region Totaux

    @Test fun totalAddsPricesOfTheSameCurrency() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("Vélo", "Epril", itemId = a), reservation("Livre", "Elliot", itemId = b)),
            links = listOf(link(a, "120", "CHF"), link(b, "30", "CHF")),
        )
        assertEquals(1, result.size)
        assertEquals(0, dec("150").compareTo(result.first().amount))
        assertEquals("CHF", result.first().currency)
    }

    @Test fun currenciesAreNeverMixed() {
        // Additionner CHF et EUR donnerait un montant faux : deux lignes distinctes, triées par devise.
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("Vélo", "Epril", itemId = a), reservation("Livre", "Elliot", itemId = b)),
            links = listOf(link(a, "100", "CHF"), link(b, "40", "EUR")),
        )
        assertEquals(listOf("CHF", "EUR"), result.map { it.currency })
        assertEquals(0, dec("100").compareTo(result[0].amount))
        assertEquals(0, dec("40").compareTo(result[1].amount))
    }

    @Test fun onlyMyPotShareCounts() {
        // Le cadeau vaut peut-être 300, mais mon budget ne supporte que ma part.
        val result = totals(contributions = listOf(contribution("50", "CHF").copy(potTotal = dec("300"))))
        assertEquals(0, dec("50").compareTo(result.first().amount))
    }

    @Test fun anItemWithoutPriceIsIgnored() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("Vélo", "Epril", itemId = a), reservation("Surprise", "Elliot", itemId = b)),
            links = listOf(link(a, "60", "CHF"), link(b, null, "CHF")),
        )
        assertEquals(1, result.size)
        assertEquals(0, dec("60").compareTo(result.first().amount))
    }

    @Test fun reservationsAndPotsAddUpInTheSameCurrency() {
        val a = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("Vélo", "Epril", itemId = a)),
            contributions = listOf(contribution("25", "CHF")),
            links = listOf(link(a, "100", "CHF")),
        )
        assertEquals(1, result.size)
        assertEquals(0, dec("125").compareTo(result.first().amount))
    }

    @Test fun linkWithoutCurrencyCountsAsEuroAndCaseIsIgnored() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("A", "Epril", itemId = a), reservation("B", "Epril", itemId = b)),
            contributions = listOf(contribution("5", "eur")),
            links = listOf(link(a, "10", null), link(b, "20", "EUR")),
        )
        assertEquals(listOf("EUR"), result.map { it.currency })
        assertEquals(0, dec("35").compareTo(result.first().amount))
    }

    @Test fun theBestLinkPricesTheItemNotTheSumOfAllLinks() {
        val a = UUID.randomUUID()
        val result = totals(
            reservations = listOf(reservation("Vélo", "Epril", itemId = a)),
            links = listOf(link(a, "130", "EUR", country = "FR", store = "Amazon"), link(a, "120", "CHF", country = "CH", store = "Galaxus")),
            country = "CH",
        )
        assertEquals(listOf("CHF"), result.map { it.currency })
        assertEquals(0, dec("120").compareTo(result.first().amount))
    }

    @Test fun bestLinkPrefersTheReadersCountry() {
        val a = UUID.randomUUID()
        val fr = link(a, "1", "EUR", country = "FR", store = "A")
        val ch = link(a, "1", "CHF", country = "CH", store = "Z")
        val links = mapOf(a to listOf(fr, ch))
        assertEquals(ch, PurchasesLogic.bestLink(a, links, "CH"))
        assertEquals(fr, PurchasesLogic.bestLink(a, links, "FR"))
        assertNull(PurchasesLogic.bestLink(UUID.randomUUID(), links, "CH"))
    }

    // endregion

    // region Regroupement

    @Test fun closestEventsFirst() {
        val groups = PurchasesLogic.groups(
            listOf(
                reservation("Vélo", "Epril", eventId = uuid(1), eventTitle = "Noël", eventDate = "2033-05-18"),
                reservation("Livre", "Elliot", eventId = uuid(2), eventTitle = "Anniversaire", eventDate = "2030-03-17"),
            ),
        )
        assertEquals(listOf("Anniversaire", "Noël"), groups.map { it.title })
    }

    @Test fun itemsWithoutEventComeLast() {
        val groups = PurchasesLogic.groups(
            listOf(
                reservation("Surprise", "Molly"),
                reservation("Vélo", "Epril", eventId = uuid(1), eventTitle = "Noël", eventDate = "2030-03-17"),
            ),
        )
        assertEquals("Noël", groups.first().title)
        assertEquals("Sans événement", groups.last().title)
    }

    @Test fun childrenAreSortedAndSoAreTheirItems() {
        val groups = PurchasesLogic.groups(
            listOf(
                reservation("Vélo", "Molly", eventId = uuid(1), eventTitle = "Noël"),
                reservation("Ballon", "Elliot", eventId = uuid(1), eventTitle = "Noël"),
                reservation("Album", "Elliot", eventId = uuid(1), eventTitle = "Noël"),
            ),
        )
        val children = groups.first().children
        assertEquals(listOf("Elliot", "Molly"), children.map { it.name })
        assertEquals(listOf("Album", "Ballon"), children.first().items.map { it.title })
    }

    @Test fun twoChildrenWithTheSameFirstNameStayApart() {
        val groups = PurchasesLogic.groups(
            listOf(
                reservation("A", "Léa", eventId = uuid(1), eventTitle = "Noël", childId = uuid(10)),
                reservation("B", "Léa", eventId = uuid(1), eventTitle = "Noël", childId = uuid(11)),
            ),
        )
        assertEquals(2, groups.single().children.size)
    }

    @Test fun orderIsDeterministicWhenDatesAreEqual() {
        val reservations = listOf(
            reservation("A", "Epril", eventId = uuid(2), eventTitle = "Zèbre", eventDate = "2030-03-17"),
            reservation("B", "Epril", eventId = uuid(1), eventTitle = "Anniversaire", eventDate = "2030-03-17"),
            reservation("C", "Epril"),
            reservation("D", "Epril", eventId = uuid(3), eventTitle = "Sans date"),
        )
        val expected = listOf("Anniversaire", "Zèbre", "Sans date", "Sans événement")
        assertEquals(expected, PurchasesLogic.groups(reservations).map { it.title })
        assertEquals(expected, PurchasesLogic.groups(reservations.reversed()).map { it.title })
    }

    // endregion

    // region Résumé

    @Test fun summaryOnlyCountsWhatRemainsToBuy() {
        val reservations = listOf(
            reservation("Vélo", "Epril", ReservationState.RESERVED),
            reservation("Livre", "Elliot", ReservationState.PURCHASED),
        )
        assertEquals(1, PurchasesLogic.remainingCount(reservations))
        assertEquals("cadeau à acheter", PurchasesLogic.summaryCountLabel(reservations, emptyList()))
        val two = reservations + reservation("Ballon", "Epril")
        assertEquals("cadeaux à acheter", PurchasesLogic.summaryCountLabel(two, emptyList()))
    }

    @Test fun withoutReservationsTheSummaryTalksAboutPots() {
        val pots = listOf(contribution("20", "CHF"), contribution("30", "CHF"))
        assertEquals("cagnottes", PurchasesLogic.summaryCountLabel(emptyList(), pots))
        assertEquals("cagnotte", PurchasesLogic.summaryCountLabel(emptyList(), pots.take(1)))
    }

    // endregion
}
