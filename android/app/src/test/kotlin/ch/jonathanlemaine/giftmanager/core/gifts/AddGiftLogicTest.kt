package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakeItemRepository
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Règles d'enregistrement d'un cadeau (`AddGiftModelTests.swift`, partie pure). */
class AddGiftLogicTest {
    private val childId = uuid(401)
    private val userId = uuid(1)
    private val event = uuid(501)

    private fun draft(
        title: String = "Vélo rouge",
        eventId: java.util.UUID? = null,
        submit: Boolean = true,
        links: List<DraftLink> = emptyList(),
    ) = GiftDraft(title = title, notes = "  des notes  ", isFavorite = true, eventId = eventId, submitToParents = submit, links = links)

    @Test fun ideaSubmittedToParentsIsPending() {
        assertEquals(IdeaReview.PENDING, AddGiftLogic.reviewStatus(WishKind.IDEA, submitToParents = true))
        assertEquals(IdeaReview.NONE, AddGiftLogic.reviewStatus(WishKind.IDEA, submitToParents = false))
    }

    @Test fun aWishIsNeverSubmittedForReview() {
        assertEquals(IdeaReview.NONE, AddGiftLogic.reviewStatus(WishKind.WISH, submitToParents = true))
    }

    @Test fun ownedGiftBelongsToNoEvent() {
        val item = AddGiftLogic.newItem(childId, WishKind.WISH, owned = true, draft(eventId = event), null, userId)
        assertNull(item.eventId)
        assertEquals(true, item.owned)
        assertEquals(event, AddGiftLogic.newItem(childId, WishKind.WISH, owned = false, draft(eventId = event), null, userId).eventId)
    }

    @Test fun newItemTrimsAndCarriesTheFields() {
        val item = AddGiftLogic.newItem(childId, WishKind.WISH, owned = false, draft(title = "  Vélo  "), "https://x/y.jpg", userId)
        assertEquals("Vélo", item.title)
        assertEquals("des notes", item.notes)
        assertEquals(1, item.priority)
        assertEquals("https://x/y.jpg", item.imageUrl)
        assertEquals(userId, item.createdBy)
        assertEquals(childId, item.childId)
    }

    @Test fun blankNotesBecomeNull() {
        assertNull(AddGiftLogic.trimmedNotes("   "))
    }

    @Test fun mainLinkDerivesStoreCountryAndPriceFromTheUrl() {
        val link = AddGiftLogic.mainLink("Regarde https://www.galaxus.ch/fr/p/1 !", null, "108,50", "EUR")!!
        assertEquals("https://www.galaxus.ch/fr/p/1", link.url)
        assertEquals("Galaxus", link.store)
        assertEquals("CH", link.country)
        assertEquals(0, BigDecimal("108.50").compareTo(link.price))
        assertEquals("EUR", link.currency)
    }

    @Test fun mainLinkPrefersTheDetectedStoreAndHandlesNoUrl() {
        val store = StoreCatalog.Store("Autre", "FR", "EUR")
        assertEquals("Autre", AddGiftLogic.mainLink("exemple.ch/a", store, "", "CHF")!!.store)
        assertNull(AddGiftLogic.mainLink("pas de lien", null, "10", "CHF"))
        assertNull(AddGiftLogic.mainLink("exemple.ch/a", null, "", "CHF")!!.price)
    }

    @Test fun priceInputTextHasTwoDecimals() {
        assertEquals("108.00", AddGiftLogic.priceInputText(BigDecimal("108")))
        assertEquals("19.99", AddGiftLogic.priceInputText(BigDecimal("19.99")))
        assertEquals("0.13", AddGiftLogic.priceInputText(BigDecimal("0.125")))
    }

    // region Doublons

    @Test fun duplicateSearchSweepsTheWholeList() = runTest {
        val items = FakeItemRepository()
        AddGiftLogic.findDuplicate(items, childId, "Vélo", emptyList())
        assertNull("aucun filtre d'événement ne doit être transmis", items.lastEventFilter)
        assertEquals(1, items.childItemsCalls)
    }

    @Test fun duplicateByTitleWhateverTheCase() = runTest {
        val items = FakeItemRepository().apply { this.items += wishItem(1, title = "  VÉLO Rouge ") }
        assertNotNull(AddGiftLogic.findDuplicate(items, childId, "vélo rouge", emptyList()))
    }

    @Test fun noDuplicateOnADifferentTitle() = runTest {
        val items = FakeItemRepository().apply { this.items += wishItem(1, title = "Trottinette") }
        assertNull(AddGiftLogic.findDuplicate(items, childId, "Vélo rouge", emptyList()))
    }

    @Test fun duplicateByPurchaseLink() = runTest {
        val items = FakeItemRepository().apply {
            this.items += wishItem(1, title = "Autre nom")
            itemLinks += itemLink(1, 1, url = "https://www.galaxus.ch/p/42?utm=x")
        }
        val link = DraftLink(url = "https://galaxus.ch/p/42")
        assertEquals(uuid(701), AddGiftLogic.findDuplicate(items, childId, "Vélo rouge", listOf(link))?.id)
    }

    @Test fun blankTitleTriggersNoSearch() = runTest {
        val items = FakeItemRepository()
        assertNull(AddGiftLogic.findDuplicate(items, childId, "   ", emptyList()))
        assertEquals(0, items.childItemsCalls)
    }

    @Test fun networkFailureNeverBlocksSaving() = runTest {
        val items = FakeItemRepository().apply { failure = IOException("réseau") }
        assertNull(AddGiftLogic.findDuplicate(items, childId, "Vélo rouge", emptyList()))
    }

    // endregion
}
