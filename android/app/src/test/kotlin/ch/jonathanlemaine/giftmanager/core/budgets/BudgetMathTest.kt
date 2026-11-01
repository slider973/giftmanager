package ch.jonathanlemaine.giftmanager.core.budgets

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetMathTest {
    private val leo = uuid(1)
    private val emma = uuid(2)
    private val noel = uuid(3)
    private val birthday = uuid(4)

    private fun dec(text: String) = BigDecimal(text)

    private fun budget(
        child: UUID? = null,
        event: UUID? = null,
        spent: String = "0",
        amount: String = "200",
        currency: String = "CHF",
        id: UUID = UUID.randomUUID(),
        childName: String? = if (child == null) null else "Léo",
    ) = Budget(id, child, childName, event, if (event == null) null else "Noël 2026", dec(amount), currency, dec(spent))

    /** Les formats français utilisent des espaces insécables : on les normalise pour comparer. */
    private fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    @Test fun gaugeText() {
        assertEquals("180 / 200 CHF", plain(BudgetMath.gaugeText(dec("180"), dec("200"), "CHF")))
        assertEquals("49,90 / 150 €", plain(BudgetMath.gaugeText(dec("49.9"), dec("150"), "EUR")))
        assertEquals("1 200 CHF", plain(BudgetMath.amountText(dec("1200"), "CHF")))
    }

    @Test fun numberUsesFixedSeparators() {
        assertEquals("1 200", BudgetMath.number(dec("1200")))
        assertEquals("1 234,50", BudgetMath.number(dec("1234.5")))
        assertEquals("180", BudgetMath.number(dec("180.00")))
        assertEquals("199,90", BudgetMath.number(dec("199.9")))
        assertEquals("0", BudgetMath.number(BigDecimal.ZERO))
    }

    @Test fun numberRoundsHalfEven() {
        // Deux décimales : l'égalité exacte est arrondie au pair, comme NumberFormatter.
        assertEquals("0,12", BudgetMath.number(dec("0.125")))
        assertEquals("0,14", BudgetMath.number(dec("0.135")))
        assertEquals("0,13", BudgetMath.number(dec("0.1251")))
    }

    @Test fun symbols() {
        assertEquals("€", BudgetMath.symbol("eur"))
        assertEquals("£", BudgetMath.symbol("GBP"))
        assertEquals("$", BudgetMath.symbol("USD"))
        assertEquals("$ CA", BudgetMath.symbol("CAD"))
        assertEquals("CHF", BudgetMath.symbol("chf"))
    }

    @Test fun levels() {
        assertEquals(BudgetMath.Level.COMFORTABLE, BudgetMath.level(budget(spent = "100")))
        assertEquals(BudgetMath.Level.NEARLY_REACHED, BudgetMath.level(budget(spent = "180")))
        assertEquals(BudgetMath.Level.NEARLY_REACHED, BudgetMath.level(budget(spent = "200")))
        assertEquals(BudgetMath.Level.OVER, BudgetMath.level(budget(spent = "201")))
        assertEquals(0.85, BudgetMath.WARNING_RATIO, 0.0)
    }

    @Test fun progressIsCapped() {
        assertEquals(0.25, budget(spent = "50").progress, 0.0001)
        assertEquals(1.0, budget(spent = "400").progress, 0.0)
        assertEquals(0, dec("-200").compareTo(budget(spent = "400").remaining))
        assertEquals(0.0, budget(spent = "50", amount = "0").progress, 0.0)
    }

    @Test fun overIsComparedByValueNotScale() {
        assertFalse(budget(spent = "200.00", amount = "200").isOver)
        assertTrue(budget(spent = "200.01", amount = "200").isOver)
    }

    @Test fun scopeTitle() {
        assertEquals("Léo", budget(child = leo).scopeTitle)
        assertEquals("Noël 2026", budget(event = noel).scopeTitle)
        assertEquals("Léo · Noël 2026", budget(child = leo, event = noel).scopeTitle)
        assertEquals("Enfant", budget(child = leo, childName = null).scopeTitle)
        assertEquals("Budget", budget().scopeTitle)
    }

    @Test fun overlappingBudgetsAreNotSummed() {
        // Deux enfants différents : disjoints.
        assertTrue(BudgetMath.areDisjoint(listOf(budget(child = leo), budget(child = emma, currency = "EUR"))))
        // Deux événements différents : disjoints.
        assertTrue(BudgetMath.areDisjoint(listOf(budget(event = noel), budget(event = birthday))))
        // Léo (tous événements) et Noël (tous enfants) comptent les cadeaux de Léo pour Noël.
        assertFalse(BudgetMath.areDisjoint(listOf(budget(child = leo), budget(event = noel))))
        // Léo et Léo à Noël se recoupent.
        assertFalse(BudgetMath.areDisjoint(listOf(budget(child = leo), budget(child = leo, event = noel))))
        // Léo à Noël et Emma tous événements : disjoints.
        assertTrue(BudgetMath.areDisjoint(listOf(budget(child = leo, event = noel), budget(child = emma))))
    }

    @Test fun sortedByEventDateThenNameThenCurrencyThenId() {
        val soon = budget(event = noel, id = uuid(10), childName = null)
        val later = budget(event = birthday, id = uuid(11), childName = null)
        val undatedZoe = budget(child = emma, id = uuid(12), childName = "Zoé")
        val undatedAmelie = budget(child = leo, id = uuid(13), childName = "Amélie", currency = "EUR")
        val undatedAmelieChf = budget(child = leo, id = uuid(14), childName = "Amélie", currency = "CHF")
        val twinB = budget(child = leo, id = uuid(16), childName = "Amélie", currency = "CHF")
        val dates = mapOf(noel to DayDate.parse("2026-12-25"), birthday to DayDate.parse("2027-02-01"))
        val result = BudgetMath.sorted(
            listOf(undatedZoe, twinB, later, undatedAmelie, undatedAmelieChf, soon),
            eventDate = { it.eventId?.let(dates::get) },
        )
        assertEquals(
            listOf(soon, later, undatedAmelieChf, twinB, undatedAmelie, undatedZoe).map { it.id },
            result.map { it.id },
        )
    }

    @Test fun sortedNamesCanComeFromTheCaller() {
        val a = budget(child = leo, id = uuid(1), childName = null)
        val b = budget(child = emma, id = uuid(2), childName = null)
        val names = mapOf(leo to "Zoé", emma to "Alice")
        val result = BudgetMath.sorted(listOf(a, b), eventDate = { null }, childName = { it.childId?.let(names::get) })
        assertEquals(listOf(b.id, a.id), result.map { it.id })
    }
}
