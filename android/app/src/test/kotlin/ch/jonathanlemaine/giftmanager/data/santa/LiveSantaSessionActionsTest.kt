package ch.jonathanlemaine.giftmanager.data.santa

import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSantaSessionActionsTest {
    @Test fun createsAWishWithTheStoreLinkAndSignalsTheChange() = runTest {
        val env = giftsEnv()
        val before = env.revision

        LiveSantaSessionActions(env.items, env.session)
            .createGiftIdea(env.lea.id, "trottinette", "https://www.galaxus.ch/fr/search?q=trottinette")

        val item = checkNotNull(env.items.added)
        assertEquals(env.lea.id, item.childId)
        assertEquals(WishKind.WISH, item.kind)
        assertEquals("trottinette", item.title)
        assertEquals("Recueilli avec le Père Noël", item.notes)
        assertFalse(item.owned)
        assertEquals(0, item.priority)
        assertNull(item.eventId)
        assertEquals(uuid(1), item.createdBy)

        val link = env.items.addedLinks.single()
        assertEquals("https://www.galaxus.ch/fr/search?q=trottinette", link.url)
        assertEquals("Galaxus", link.store)
        assertEquals("CH", link.country)
        assertEquals("CHF", link.currency)
        assertNull(link.price)
        assertEquals(before + 1, env.revision)
    }

    @Test fun failureLeavesTheChangeUnsignalled() = runTest {
        val env = giftsEnv()
        env.items.failure = java.io.IOException("réseau")
        val before = env.revision
        try {
            LiveSantaSessionActions(env.items, env.session).createGiftIdea(env.lea.id, "x", null)
            error("aurait dû échouer")
        } catch (_: java.io.IOException) {
        }
        assertEquals(before, env.revision)
    }
}
