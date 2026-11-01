package ch.jonathanlemaine.giftmanager.ui.santa

import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.santa.SantaScript
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.BlockReason
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.Stage
import ch.jonathanlemaine.giftmanager.core.santa.SantaSessionActions
import ch.jonathanlemaine.giftmanager.core.santa.SantaVoicePlayer
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class SantaViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Lecteur factice : garde le rappel de fin ; [failing] reproduit un audio manquant (fin immédiate). */
    private class FakePlayer(var failing: Boolean = false) : SantaVoicePlayer {
        val played = mutableListOf<String>()
        var stops = 0
        var released = false
        private var callback: (() -> Unit)? = null
        override var isPlaying = false
        override var audioUnavailable = false
        override var onStateChanged: (() -> Unit)? = null

        override fun play(assetPath: String, onFinish: () -> Unit) {
            played += assetPath
            if (failing) {
                audioUnavailable = true
                onFinish()
                return
            }
            isPlaying = true
            callback = onFinish
        }

        override fun stop() {
            stops++
            isPlaying = false
            callback = null
        }

        override fun release() {
            released = true
        }

        /** La réplique en cours se termine. */
        fun finish() {
            val cb = callback ?: return
            callback = null
            isPlaying = false
            cb()
        }
    }

    private class FakeActions : SantaSessionActions {
        data class Created(val childId: UUID, val title: String, val link: String?)

        val created = mutableListOf<Created>()
        var failure: Throwable? = null

        override suspend fun createGiftIdea(childId: UUID, title: String, link: String?) {
            failure?.let { throw it }
            created += Created(childId, title, link)
        }
    }

    private val script = SantaScript.parse(
        """
        {
          "voix": { "nom": "Sylvestre", "voice_id": "v", "model_id": "m" },
          "repliques": [
            { "id": "accueil", "texte": "Ho ho ho !" },
            { "id": "demande_prenom", "texte": "Ton prénom ?", "attend_reponse": true, "champ": "prenom" },
            { "id": "question_envie", "texte": "Ton envie ?", "attend_reponse": true, "champ": "envie_principale" },
            { "id": "cloture", "texte": "Au revoir" }
          ]
        }
        """,
    )

    private class Setup(val env: GiftsEnv, val player: FakePlayer, val actions: FakeActions, val lock: ChildModeLock, val vm: SantaViewModel)

    private suspend fun TestScope.setup(
        country: String? = null,
        childMode: Boolean = false,
        player: FakePlayer = FakePlayer(),
        script: SantaScript = this@SantaViewModelTest.script,
    ): Setup {
        val env = giftsEnv {
            if (country != null) f.households.all.replaceAll { it.copy(country = country) }
        }
        val actions = FakeActions()
        val lock = ChildModeLock().also { if (childMode) it.activate() }
        val vm = SantaViewModel(env.lea.id, env.session, actions, { script }, player, lock).also { runCurrent() }
        return Setup(env, player, actions, lock, vm)
    }

    private val Setup.state get() = vm.uiState.value

    /** Joue tout le dialogue jusqu'à la revue des suggestions. */
    private fun Setup.runThrough(name: String = "Lina", wish: String = "trottinette") {
        player.finish() // accueil
        player.finish() // demande_prenom
        vm.submitAnswer(name)
        player.finish() // question_envie
        vm.submitAnswer(wish)
        player.finish() // cloture
    }

    // region Garde-fous

    @Test fun childModeBlocksTheSessionAndNothingIsPlayed() = runTest {
        val s = setup(childMode = true)
        assertEquals(Stage.Blocked(BlockReason.ChildMode), s.state.stage)
        assertTrue(s.player.played.isEmpty())
    }

    @Test fun storesComeFromTheChildsHouseholdCountry() = runTest {
        val s = setup(country = "FR")
        s.runThrough()
        assertEquals(Stage.ReviewingSuggestions, s.state.stage)
        assertTrue(s.state.suggestions.all { it.store.country == "FR" })
    }

    @Test fun swissHouseholdGetsSwissStores() = runTest {
        val s = setup()
        s.runThrough()
        assertTrue(s.state.suggestions.isNotEmpty())
        assertTrue(s.state.suggestions.all { it.store.country == "CH" })
    }

    @Test fun unknownChildLeavesNoCountryAndBlocksAtTheEnd() = runTest {
        val env = giftsEnv()
        val player = FakePlayer()
        val vm = SantaViewModel(UUID(0, 999), env.session, FakeActions(), { script }, player, ChildModeLock())
        player.finish(); player.finish()
        vm.submitAnswer("Lina")
        player.finish()
        vm.submitAnswer("trottinette")
        player.finish()
        assertEquals(Stage.Blocked(BlockReason.NoStore), vm.uiState.value.stage)
    }

    // endregion

    // region Voix

    @Test fun eachSpokenLineIsPlayedThenTheSessionAdvances() = runTest {
        val s = setup()
        assertEquals(listOf("audio/accueil.mp3"), s.player.played)
        assertEquals(Stage.Speaking(0), s.state.stage)

        s.player.finish()
        assertEquals(listOf("audio/accueil.mp3", "audio/demande_prenom.mp3"), s.player.played)
        assertEquals(Stage.Speaking(1), s.state.stage)

        s.player.finish()
        assertEquals(Stage.AwaitingAnswer(1), s.state.stage)
        assertEquals("demande_prenom", s.state.currentLine?.id)
    }

    @Test fun aFailingPlayerNeverBlocksTheSession() = runTest {
        val s = setup(player = FakePlayer(failing = true))
        // Tout l'audio échoue : la session s'arrête sur la première question, texte seul.
        assertEquals(Stage.AwaitingAnswer(1), s.state.stage)
        assertTrue(s.state.audioUnavailable)

        s.vm.submitAnswer("Lina")
        // La question suivante est atteinte sans intervention de l'audio.
        assertEquals(Stage.AwaitingAnswer(2), s.state.stage)
        s.vm.submitAnswer("trottinette")
        assertEquals(Stage.ReviewingSuggestions, s.state.stage)
    }

    @Test fun skippingTheVoiceMovesOnToTheNextLine() = runTest {
        val s = setup()
        s.vm.skipSpeaking()
        assertEquals(Stage.Speaking(1), s.state.stage)
        assertEquals("audio/demande_prenom.mp3", s.player.played.last())
        // Une seule réplique est jouée à la fois : l'ancien rappel a été retiré par stop().
        assertEquals(2, s.player.played.size)
    }

    @Test fun stoppingTheScreenStopsTheVoiceAndStartingResumesTheLine() = runTest {
        val s = setup()
        s.vm.onScreenStopped()
        assertFalse(s.player.isPlaying)
        assertEquals(Stage.Speaking(0), s.state.stage)

        s.vm.onScreenStarted()
        assertEquals(listOf("audio/accueil.mp3", "audio/accueil.mp3"), s.player.played)
    }

    @Test fun rotationNeitherStopsNorRestartsTheVoice() = runTest {
        val s = setup()
        val stopsBefore = s.player.stops
        s.vm.onScreenStopped(changingConfigurations = true)
        s.vm.onScreenStarted()
        assertEquals(stopsBefore, s.player.stops)
        assertTrue(s.player.isPlaying)
        assertEquals(listOf("audio/accueil.mp3"), s.player.played)

        // La réplique en cours se termine normalement après la rotation.
        s.player.finish()
        assertEquals(Stage.Speaking(1), s.state.stage)
    }

    @Test fun pausedPlayerIsReflectedAndTheLineCanStillBeSkipped() = runTest {
        val s = setup()
        assertTrue(s.state.isPlaying)
        // Perte de focus audio : le lecteur se met en pause sans finir la réplique.
        s.player.isPlaying = false
        s.player.onStateChanged?.invoke()
        assertFalse(s.state.isPlaying)
        assertEquals(Stage.Speaking(0), s.state.stage)

        s.vm.skipSpeaking()
        assertEquals(Stage.Speaking(1), s.state.stage)
    }

    @Test fun startingAgainWhileStillPlayingDoesNotRestartTheLine() = runTest {
        val s = setup()
        s.vm.onScreenStarted()
        assertEquals(1, s.player.played.size)
    }

    @Test fun answeringStopsTheVoiceAndReplayDoesNotAdvance() = runTest {
        val s = setup()
        s.player.finish()
        s.player.finish()
        s.vm.replay()
        assertEquals("audio/demande_prenom.mp3", s.player.played.last())
        s.player.finish()
        assertEquals(Stage.AwaitingAnswer(1), s.state.stage)
    }

    @Test fun clearedViewModelReleasesThePlayer() = runTest {
        val s = setup()
        val onCleared = SantaViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(s.vm)
        assertTrue(s.player.released)
    }

    // endregion

    // region Validation parentale

    @Test fun nothingIsCreatedWithoutApproval() = runTest {
        val s = setup()
        s.runThrough()
        assertTrue(s.state.suggestions.isNotEmpty())
        assertTrue(s.actions.created.isEmpty())
    }

    @Test fun approvingCreatesTheIdeaAndRemovesTheSuggestion() = runTest {
        val s = setup()
        s.runThrough()
        val first = s.state.suggestions.first()
        val before = s.state.suggestions.size

        s.vm.approve(first)

        assertEquals(listOf(FakeActions.Created(s.env.lea.id, "trottinette", first.url)), s.actions.created)
        assertEquals(1, s.state.savedCount)
        assertEquals(before - 1, s.state.suggestions.size)
        assertNull(s.state.savingId)
    }

    @Test fun approvingTheLastSuggestionEndsTheSession() = runTest {
        val s = setup()
        s.runThrough()
        s.state.suggestions.toList().forEach { s.vm.approve(it) }
        assertEquals(Stage.Done, s.state.stage)
        assertEquals(3, s.state.savedCount)
    }

    @Test fun aFailedApprovalKeepsTheSuggestionAndReportsTheError() = runTest {
        val s = setup()
        s.runThrough()
        val first = s.state.suggestions.first()
        s.actions.failure = IOException("réseau")

        s.vm.approve(first)

        assertEquals(GiftError.Network, s.state.error)
        assertEquals(0, s.state.savedCount)
        assertTrue(first in s.state.suggestions)
        assertNull(s.state.savingId)

        // Le parent peut réessayer.
        s.actions.failure = null
        s.vm.dismissError()
        s.vm.approve(first)
        assertNull(s.state.error)
        assertEquals(1, s.state.savedCount)
    }

    @Test fun rejectingCreatesNothing() = runTest {
        val s = setup()
        s.runThrough()
        val first = s.state.suggestions.first()
        s.vm.reject(first)
        assertTrue(s.actions.created.isEmpty())
        assertFalse(first in s.state.suggestions)
        assertEquals(0, s.state.savedCount)
    }

    @Test fun finishingDropsWhatWasNotApproved() = runTest {
        val s = setup()
        s.runThrough()
        s.vm.finish()
        assertEquals(Stage.Done, s.state.stage)
        assertTrue(s.state.suggestions.isEmpty())
        assertTrue(s.actions.created.isEmpty())
        assertNotNull(s.state.childName)
    }

    // endregion
}
