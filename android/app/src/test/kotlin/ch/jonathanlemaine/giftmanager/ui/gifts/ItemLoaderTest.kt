package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.data.FakeItemRepository
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemLoaderTest {
    private val repo = FakeItemRepository()
    private val loader = ItemLoader(repo, Fixtures.clock)
    private val future = testEvent(1, "2026-12-25")
    private val past = testEvent(2, "2026-09-01")

    @Test fun loadsAnItemByItsIdAlone() = runTest {
        repo.items += listOf(wishItem(1, childN = 3), wishItem(2, childN = 3))
        repo.itemLinks += itemLink(1, 1)
        repo.itemLinks += itemLink(2, 2)
        val loaded = loader.load(uuid(701), emptyList())
        assertEquals(uuid(701), loaded.item.id)
        assertEquals(listOf(uuid(801)), loaded.links.map { it.id })
        assertNull("aucun filtre d'événement : toute la liste de l'enfant", repo.lastEventFilter)
    }

    @Test fun unknownItemIsNotFound() = runTest {
        val failure = runCatching { loader.load(uuid(701), emptyList()) }.exceptionOrNull()
        assertTrue(failure is ItemNotFoundException)
        assertEquals(GiftError.NotFound, GiftError.from(failure!!))
    }

    @Test fun anItemTheServerWillNotListIsNotFound() = runTest {
        // locate() le voit, mais child_items ne le renvoie pas à cet utilisateur (idée d'un autre).
        repo.items += wishItem(1)
        val hiding = object : ch.jonathanlemaine.giftmanager.data.ItemRepository by repo {
            override suspend fun childItems(childId: java.util.UUID, eventId: java.util.UUID?, kind: ch.jonathanlemaine.giftmanager.core.model.WishKind?) =
                emptyList<ch.jonathanlemaine.giftmanager.core.model.WishItem>()
        }
        assertTrue(runCatching { ItemLoader(hiding).load(uuid(701), emptyList()) }.exceptionOrNull() is ItemNotFoundException)
    }

    @Test fun readOnlyComesFromThePastEvent() = runTest {
        repo.items += wishItem(1, eventId = past.id)
        val loaded = loader.load(uuid(701), listOf(past, future))
        assertTrue(loaded.readOnly)
        assertEquals(past.eventDate, loaded.eventDate)
    }

    @Test fun futureEventIsNotReadOnly() = runTest {
        repo.items += wishItem(1, eventId = future.id)
        assertFalse(loader.load(uuid(701), listOf(past, future)).readOnly)
    }

    @Test fun contextEventAppliesWhenTheGiftHasNone() = runTest {
        repo.items += wishItem(1, eventId = null)
        val loaded = loader.load(uuid(701), listOf(past, future), contextEventId = past.id)
        assertTrue(loaded.readOnly)
        assertEquals(past.id, loaded.eventId)
        assertFalse(loader.load(uuid(701), listOf(past, future)).readOnly)
    }
}
