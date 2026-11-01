package ch.jonathanlemaine.giftmanager.ui.components

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiftStatusTest {
    private fun item(name: String): WishItem = Fixtures.decode(name)

    @Test fun potFixtureIsPot() = assertEquals(GiftStatus.POT, GiftStatus.of(item("wish_item_pot.json")))

    // Mode surprise : status null côté serveur, même si le cadeau est déjà possédé.
    @Test fun parentFixtureHasNoBadge() {
        val parent = item("wish_item_parent.json")
        assertEquals(true, parent.owned)
        assertNull(GiftStatus.of(parent))
    }

    @Test fun everyItemStatusMapsToItsBadge() {
        val base = item("wish_item_pot.json")
        val expected = mapOf(
            ItemStatus.AVAILABLE to GiftStatus.AVAILABLE,
            ItemStatus.TAKEN to GiftStatus.TAKEN,
            ItemStatus.MINE to GiftStatus.MINE,
            ItemStatus.OWNED to GiftStatus.OWNED,
            ItemStatus.POT to GiftStatus.POT,
        )
        ItemStatus.entries.forEach { assertEquals(expected[it], GiftStatus.of(base.copy(status = it))) }
        assertNull(GiftStatus.of(base.copy(status = null)))
    }

    @Test fun labelsMatchIos() {
        assertEquals(
            listOf("Disponible", "Déjà pris", "Je l'offre", "Possède déjà", "Cagnotte"),
            GiftStatus.entries.map { it.label },
        )
    }
}
