package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreview
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Règles d'enregistrement d'un cadeau, vérifiées sans réseau ni formulaire (`AddGiftModelTests.swift`). Trois sont
 * invisibles à l'écran : une idée soumise aux parents part en `pending` (#57), un cadeau déjà possédé n'est rattaché
 * à aucun événement, et la recherche de doublon balaie toute la liste de l'enfant (#61).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddGiftViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val zoe = uuid(403)
    private val event = uuid(501)

    private fun TestScope.vm(
        env: GiftsEnv,
        kind: WishKind = WishKind.WISH,
        owned: Boolean = false,
        eventId: UUID? = null,
        itemId: UUID? = null,
        initialUrl: String? = null,
    ) = AddGiftViewModel(zoe, kind, owned, eventId, itemId, env.session, env.items, env.images, env.notifier, env.previews, initialUrl = initialUrl)
        .also { runCurrent() }

    private fun AddGiftViewModel.fill(title: String = "Vélo rouge") = onTitleChange(title)

    private val AddGiftViewModel.state get() = uiState.value

    // region Validation parentale des idées (#57)

    @Test fun anIdeaSubmittedToParentsIsPending() = runTest {
        val env = giftsEnv()
        val vm = vm(env, WishKind.IDEA).apply { fill() }
        vm.save()
        assertEquals(IdeaReview.PENDING, env.items.added?.reviewStatus)
        assertTrue(vm.state.finished)
    }

    @Test fun anIdeaNotSubmittedStaysInvisible() = runTest {
        val env = giftsEnv()
        val vm = vm(env, WishKind.IDEA).apply { fill(); onSubmitToParentsChange(false) }
        vm.save()
        assertEquals(IdeaReview.NONE, env.items.added?.reviewStatus)
    }

    @Test fun aWishIsNeverSubmittedForReview() = runTest {
        val env = giftsEnv()
        val vm = vm(env, WishKind.WISH).apply { fill() }
        vm.save()
        assertEquals(IdeaReview.NONE, env.items.added?.reviewStatus)
    }

    // endregion

    // region Cadeau déjà possédé

    @Test fun anOwnedGiftBelongsToNoEvent() = runTest {
        val env = giftsEnv()
        val vm = vm(env, owned = true, eventId = event).apply { fill() }
        vm.save()
        assertNull("sinon il réapparaît dans la liste de Noël", env.items.added?.eventId)
        assertEquals(true, env.items.added?.owned)
    }

    @Test fun anOwnedGiftTriggersNoNotification() = runTest {
        val env = giftsEnv()
        val vm = vm(env, owned = true).apply { fill() }
        vm.save()
        assertTrue(env.notifier.newItems.isEmpty())
    }

    @Test fun aNormalGiftKeepsItsEventAndNotifiesTheFamily() = runTest {
        val env = giftsEnv()
        val vm = vm(env, eventId = event).apply { fill() }
        vm.save()
        assertEquals(event, env.items.added?.eventId)
        assertEquals(listOf(uuid(900)), env.notifier.newItems.single())
    }

    @Test fun savingTellsTheListsToReload() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill() }
        val before = env.revision
        vm.save()
        assertEquals(before + 1, env.revision)
    }

    // endregion

    // region Doublons (#61)

    @Test fun duplicateSearchSweepsTheWholeList() = runTest {
        val env = giftsEnv()
        val vm = vm(env, eventId = event).apply { fill() }
        vm.saveChecked()
        assertNull("aucun filtre d'événement ne doit être transmis", env.items.lastEventFilter)
        assertEquals(1, env.items.childItemsCalls)
    }

    @Test fun savingStopsWhenADuplicateExists() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 3, title = "  VÉLO rouge ") }
        val vm = vm(env).apply { fill() }
        vm.saveChecked()
        assertNotNull(vm.state.duplicate)
        assertFalse(vm.state.finished)
        assertNull("rien n'est envoyé tant que l'utilisateur n'a pas tranché", env.items.added)
        assertFalse(vm.state.isSaving)
    }

    @Test fun confirmingTheDuplicateSaves() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 3, title = "Vélo rouge") }
        val vm = vm(env).apply { fill() }
        vm.saveChecked()
        vm.save()
        assertNotNull(env.items.added)
        assertNull(vm.state.duplicate)
        assertTrue(vm.state.finished)
    }

    @Test fun dismissingTheDuplicateKeepsTheForm() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 3, title = "Vélo rouge") }
        val vm = vm(env).apply { fill() }
        vm.saveChecked()
        vm.dismissDuplicate()
        assertNull(vm.state.duplicate)
        assertEquals("Vélo rouge", vm.state.title)
    }

    @Test fun duplicateDetectedByTheMainLink() = runTest {
        val env = giftsEnv {
            items.items += wishItem(1, childN = 3, title = "Autre nom")
            items.itemLinks += itemLink(1, 1, url = "https://www.galaxus.ch/p/42?utm=x")
        }
        val vm = vm(env).apply { fill(); onUrlChange("https://galaxus.ch/p/42") }
        vm.saveChecked()
        assertEquals(uuid(701), vm.state.duplicate?.id)
    }

    @Test fun aNetworkFailureNeverPreventsSaving() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill() }
        env.items.failure = IOException("réseau")
        vm.saveChecked()
        // La détection échoue en silence, mais l'enregistrement lui-même échoue aussi : erreur affichée, rien de bloqué.
        assertEquals(GiftError.Network, vm.state.error)
        env.items.failure = null
        vm.dismissError()
        vm.saveChecked()
        assertTrue(vm.state.finished)
    }

    @Test fun aBlankTitleCannotBeSaved() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill("   ") }
        assertFalse(vm.state.canSave)
        vm.saveChecked()
        assertEquals(0, env.items.childItemsCalls)
        assertNull(env.items.added)
    }

    // endregion

    // region Modification

    @Test fun editingDoesNotSearchForDuplicates() = runTest {
        // Sinon modifier un cadeau le détecterait comme son propre doublon.
        val existing = wishItem(1, childN = 3, title = "Vélo rouge", eventId = event, priority = 1)
        val env = giftsEnv {
            items.items += existing
            items.itemLinks += itemLink(1, 1, url = "https://www.galaxus.ch/p/1")
            items.itemLinks += itemLink(2, 1, url = "https://www.amazon.fr/dp/2", store = "Amazon", country = "FR")
        }
        val vm = vm(env, itemId = existing.id)
        assertFalse(vm.state.isLoadingExisting)
        assertEquals("Vélo rouge", vm.state.title)
        assertEquals("https://www.galaxus.ch/p/1", vm.state.urlText)
        assertEquals("10.00", vm.state.priceText)
        assertEquals(1, vm.state.otherLinks.size)
        assertTrue(vm.state.isFavorite)
        assertEquals(event, vm.state.eventId)

        vm.saveChecked()
        assertNull(vm.state.duplicate)
        assertTrue(vm.state.finished)
        assertEquals("seule la charge initiale a lu la liste", 1, env.items.childItemsCalls)
        assertEquals(listOf<Any?>(existing.id, "Vélo rouge", null, null, 1, event), env.items.updated)
        assertEquals(2, env.items.replacedLinks?.size)
        assertNull("une modification n'annonce rien à la famille", env.items.added)
        assertTrue(env.notifier.newItems.isEmpty())
    }

    @Test fun editingAMissingGiftClosesTheForm() = runTest {
        val env = giftsEnv()
        val vm = vm(env, itemId = uuid(799))
        assertTrue(vm.state.finished)
        assertEquals(GiftError.NotFound, vm.state.error)
    }

    // endregion

    // region Image et erreurs

    @Test fun theImageIsUploadedOnlyIfOneWasPicked() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill() }
        vm.save()
        assertEquals(0, env.images.uploads)
        assertNull(env.items.added?.imageUrl)

        val withImage = vm(env).apply { fill(); onImagePicked(byteArrayOf(1, 2, 3)) }
        withImage.save()
        assertEquals(1, env.images.uploads)
        assertEquals("https://example.test/image.jpg", env.items.added?.imageUrl)
    }

    @Test fun aSavingFailureSurfacesTheErrorAndClosesNothing() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill() }
        env.items.failure = IllegalStateException("boom")
        vm.save()
        assertFalse(vm.state.finished)
        assertEquals(GiftError.Other, vm.state.error)
        assertFalse(vm.state.isSaving)
        assertTrue(env.notifier.newItems.isEmpty())
    }

    @Test fun anUploadFailureStopsBeforeCreatingTheGift() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill(); onImagePicked(byteArrayOf(1)) }
        env.images.failure = IOException("réseau")
        vm.save()
        assertNull(env.items.added)
        assertFalse(vm.state.finished)
    }

    // endregion

    // region Audience d'une idée

    @Test fun audienceIsLoadedForAnIdeaOnly() = runTest {
        val wishEnv = giftsEnv { items.audience = 3 }
        assertNull(vm(wishEnv, WishKind.WISH).state.audience)
        val ideaEnv = giftsEnv { items.audience = 3 }
        val idea = vm(ideaEnv, WishKind.IDEA)
        assertEquals(3, idea.state.audience)
        idea.onSubmitToParentsChange(false)
        assertFalse(idea.state.audienceIsEmpty)
    }

    @Test fun anIdeaNobodyWouldSeeIsFlagged() = runTest {
        val env = giftsEnv { items.audience = 0 }
        val vm = vm(env, WishKind.IDEA)
        assertFalse("soumise aux parents, l'idée atteint quelqu'un", vm.state.audienceIsEmpty)
        vm.onSubmitToParentsChange(false)
        assertTrue(vm.state.audienceIsEmpty)
    }

    // endregion

    // region Aperçu du lien

    private val preview = LinkPreview("LEGO Technic", "https://img.test/a.jpg", BigDecimal("108"), "CHF")

    @Test fun previewRunsSevenHundredMsAfterTheLastKeystroke() = runTest {
        val env = giftsEnv { previews.result = preview }
        val vm = vm(env)
        vm.onUrlChange("https://www.galaxus.ch/p")
        vm.onUrlChange("https://www.galaxus.ch/p/1")
        advanceTimeBy(699)
        runCurrent()
        assertTrue("trop tôt", env.previews.requests.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals("un seul aperçu, pour le dernier lien", listOf("https://www.galaxus.ch/p/1"), env.previews.requests)
        assertEquals("LEGO Technic", vm.state.title)
        assertEquals("108.00", vm.state.priceText)
        assertEquals("CHF", vm.state.currency)
        assertEquals("https://img.test/a.jpg", vm.state.imageUrl)
        assertEquals("Galaxus", vm.state.store?.name)
        assertFalse(vm.state.isFetching)
        assertFalse(vm.state.previewFailed)
    }

    @Test fun submittingFetchesAtOnce() = runTest {
        val env = giftsEnv { previews.result = preview }
        val vm = vm(env)
        vm.onUrlChange("amazon.fr/dp/X")
        vm.onUrlSubmit()
        runCurrent()
        assertEquals(listOf("https://amazon.fr/dp/X"), env.previews.requests)
        assertEquals("la devise de l'aperçu l'emporte sur celle de la boutique", "CHF", vm.state.currency)
    }

    @Test fun failedPreviewAsksForManualEntry() = runTest {
        val env = giftsEnv { previews.result = null }
        val vm = vm(env)
        vm.onUrlChange("https://www.galaxus.ch/p/1")
        advanceTimeBy(701)
        runCurrent()
        assertTrue(vm.state.previewFailed)
        assertFalse(vm.state.isFetching)
        assertEquals("", vm.state.title)
    }

    @Test fun aNewLinkClearsThePreviousPriceAndTakesTheStoreCurrency() = runTest {
        val env = giftsEnv { previews.result = null }
        val vm = vm(env).apply { onPriceChange("50") }
        vm.onUrlChange("https://www.amazon.fr/dp/1")
        advanceTimeBy(701)
        runCurrent()
        assertEquals("", vm.state.priceText)
        assertEquals("EUR", vm.state.currency)
    }

    @Test fun initialUrlFillsTheFormAndStartsThePreview() = runTest {
        val env = giftsEnv { previews.result = preview }
        val vm = vm(env, initialUrl = "https://www.galaxus.ch/p/1")
        assertEquals("https://www.galaxus.ch/p/1", vm.state.urlText)
        advanceTimeBy(699)
        runCurrent()
        assertTrue("même délai que la saisie", env.previews.requests.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("https://www.galaxus.ch/p/1"), env.previews.requests)
        assertEquals("LEGO Technic", vm.state.title)
    }

    @Test fun initialUrlIsIgnoredWhenEditing() = runTest {
        val existing = wishItem(1, childN = 3, title = "Mon titre")
        val env = giftsEnv {
            items.items += existing
            items.itemLinks += itemLink(1, 1, url = "https://www.galaxus.ch/p/1")
            previews.result = preview
        }
        val vm = vm(env, itemId = existing.id, initialUrl = "https://shop.example/autre")
        advanceTimeBy(701)
        runCurrent()
        assertEquals("https://www.galaxus.ch/p/1", vm.state.urlText)
        assertTrue(env.previews.requests.isEmpty())
    }

    @Test fun textWithoutLinkStartsNoPreview() = runTest {
        val env = giftsEnv { previews.result = preview }
        val vm = vm(env)
        vm.onUrlChange("un vélo")
        advanceTimeBy(701)
        runCurrent()
        assertTrue(env.previews.requests.isEmpty())
    }

    @Test fun editingDoesNotRefetchTheOriginalLinkNorOverwriteTheTitle() = runTest {
        val existing = wishItem(1, childN = 3, title = "Mon titre")
        val env = giftsEnv {
            items.items += existing
            items.itemLinks += itemLink(1, 1, url = "https://www.galaxus.ch/p/1")
            previews.result = preview
        }
        val vm = vm(env, itemId = existing.id)
        vm.onUrlChange("https://www.galaxus.ch/p/1")
        advanceTimeBy(701)
        runCurrent()
        assertTrue(env.previews.requests.isEmpty())

        vm.onUrlChange("https://www.galaxus.ch/p/2")
        advanceTimeBy(701)
        runCurrent()
        assertEquals(1, env.previews.requests.size)
        assertEquals("Mon titre", vm.state.title)
        assertEquals("108.00", vm.state.priceText)
    }

    @Test fun otherLinksCanBeAddedAndRemoved() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill() }
        val link = DraftLink(url = "https://www.amazon.fr/dp/1", store = "Amazon", country = "FR")
        vm.addLink(link)
        vm.save()
        assertEquals(listOf(link), env.items.addedLinks)

        val other = vm(env).apply { fill(); addLink(link); removeLink(link.id) }
        assertTrue(other.state.otherLinks.isEmpty())
    }

    @Test fun mainLinkIsSavedFirst() = runTest {
        val env = giftsEnv()
        val vm = vm(env).apply { fill(); onUrlChange("https://www.galaxus.ch/p/1"); onPriceChange("19,90"); addLink(DraftLink(url = "https://x.ch/a")) }
        vm.save()
        assertEquals(listOf("https://www.galaxus.ch/p/1", "https://x.ch/a"), env.items.addedLinks.map { it.url })
        assertEquals("Galaxus", env.items.addedLinks.first().store)
        assertEquals(0, BigDecimal("19.90").compareTo(env.items.addedLinks.first().price))
    }

    // endregion

    @Test fun parentNamesComeFromTheChildsHousehold() = runTest {
        val env = giftsEnv()
        assertEquals(emptyList<String>(), vm(env).state.parentNames) // foyer 2 absent des données de la fixture
        assertEquals(env.zoe, vm(env).state.child)
    }
}
