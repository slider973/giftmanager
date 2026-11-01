package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakeImageUploader
import ch.jonathanlemaine.giftmanager.data.FakeItemNotifier
import ch.jonathanlemaine.giftmanager.data.FakeItemRepository
import ch.jonathanlemaine.giftmanager.data.FakeLinkPreviewService
import ch.jonathanlemaine.giftmanager.data.FakePotRepository
import ch.jonathanlemaine.giftmanager.data.FakeReservationRepository
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/**
 * Décor commun des tests de cadeaux : l'utilisateur 1 est parent de Léa (foyer 1) ; Zoé (foyer 2) est l'enfant d'un
 * autre foyer de la famille.
 */
class GiftsEnv(val f: SessionFixture) {
    val items = FakeItemRepository()
    val reservations = FakeReservationRepository()
    val pots = FakePotRepository()
    val images = FakeImageUploader()
    val notifier = FakeItemNotifier()
    val previews = FakeLinkPreviewService()

    val session get() = f.session
    val lea = testChild(1, name = "Léa")
    val zoe = testChild(3, householdN = 2, name = "Zoé")
    val christmas = testEvent(1, "2026-12-25", kind = GiftEventKind.CHRISTMAS)
    val past = testEvent(2, "2026-09-01")

    /** Révision du bus de changement : incrémentée à chaque `itemsChanged()`. */
    val revision get() = session.itemsRevision.value
}

suspend fun TestScope.giftsEnv(setup: GiftsEnv.() -> Unit = {}): GiftsEnv {
    val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
    f.seedReady()
    val env = GiftsEnv(f)
    f.childRepo.all += listOf(env.lea, env.zoe)
    f.eventRepo.all += listOf(env.christmas, env.past)
    env.setup()
    f.session.start()
    runCurrent()
    return env
}
