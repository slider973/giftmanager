package ch.jonathanlemaine.giftmanager.core.santa

import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.BlockReason
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `SantaSessionModelTests.swift` : le déroulé pur (le ViewModel couvre l'audio et le réseau). */
class SantaSessionTest {
    private val script = SantaScript.parse(
        """
        {
          "voix": { "nom": "Sylvestre", "voice_id": "2vBkYF6XwnA9E7Osnt2m", "model_id": "eleven_multilingual_v2" },
          "repliques": [
            { "id": "accueil", "texte": "Ho ho ho !", "attend_reponse": false },
            { "id": "demande_prenom", "texte": "Ton prénom ?", "attend_reponse": true, "champ": "prenom" },
            { "id": "question_envie", "texte": "Ton envie ?", "attend_reponse": true, "champ": "envie_principale" },
            { "id": "cloture", "texte": "Au revoir", "attend_reponse": false }
          ]
        }
        """,
    )

    private fun session(country: String? = "CH", childMode: Boolean = false) = SantaSession(script, country, childMode)

    /** Déroule jusqu'à la revue des suggestions avec le prénom et l'envie donnés. */
    private fun SantaSession.runThrough(name: String = "Lina", wish: String? = "trottinette") {
        start()
        advanceAfterSpeaking(); advanceAfterSpeaking()
        submitAnswer(name)
        advanceAfterSpeaking()
        if (wish != null) submitAnswer(wish) else skipAnswer()
        advanceAfterSpeaking()
    }

    // region Le garde-fou qui compte

    @Test fun childModeBlocksTheSession() {
        val s = session(childMode = true)
        s.start()
        assertEquals(Stage.Blocked(BlockReason.ChildMode), s.stage)
    }

    @Test fun aParentCanStart() {
        val s = session()
        s.start()
        assertEquals(Stage.Speaking(0), s.stage)
        assertEquals("accueil", s.currentLine?.id)
    }

    @Test fun anEmptyScriptBlocks() {
        val s = SantaSession(SantaScript.EMPTY, "CH", childModeActive = false)
        s.start()
        assertEquals(Stage.Blocked(BlockReason.EmptyScript), s.stage)
    }

    // endregion

    // region Déroulé

    @Test fun flowChainsSpeechAndAnswersUpToSuggestions() {
        val s = session()
        s.start()
        assertEquals("accueil", s.currentLine?.id)

        // Une réplique sans question enchaîne directement.
        s.advanceAfterSpeaking()
        assertEquals(Stage.Speaking(1), s.stage)

        // Une réplique avec question attend la saisie du parent.
        s.advanceAfterSpeaking()
        assertEquals(Stage.AwaitingAnswer(1), s.stage)

        s.submitAnswer("Lina")
        assertEquals("Lina", s.wishes.prenom)
        assertEquals(Stage.Speaking(2), s.stage)

        s.advanceAfterSpeaking()
        s.submitAnswer("une trottinette")
        assertEquals("une trottinette", s.wishes.enviePrincipale)

        assertEquals(Stage.Speaking(3), s.stage)
        s.advanceAfterSpeaking()
        assertEquals(Stage.ReviewingSuggestions, s.stage)
    }

    @Test fun answersAreTrimmed() {
        val s = session()
        s.start()
        s.advanceAfterSpeaking(); s.advanceAfterSpeaking()
        s.submitAnswer("  Lina  ")
        assertEquals("Lina", s.wishes.prenom)
    }

    @Test fun anOptionalQuestionCanBeSkipped() {
        val s = session()
        s.start()
        s.advanceAfterSpeaking(); s.advanceAfterSpeaking()
        assertEquals(Stage.AwaitingAnswer(1), s.stage)
        s.skipAnswer()
        assertTrue(s.wishes.prenom.isEmpty())
        assertEquals(Stage.Speaking(2), s.stage)
    }

    @Test fun activitiesAccumulateAndCanBeRemoved() {
        val s = session()
        s.toggleActivity("construire")
        s.toggleActivity("dehors")
        assertEquals(setOf("construire", "dehors"), s.wishes.activites)
        s.toggleActivity("dehors")
        assertEquals(setOf("construire"), s.wishes.activites)
    }

    // endregion

    // region Suggestions filtrées par pays du foyer

    @Test fun aSwissHouseholdOnlyGetsSwissSuggestions() {
        val s = session("CH")
        s.runThrough()
        assertTrue(s.suggestions.isNotEmpty())
        assertTrue(s.suggestions.all { it.store.country == "CH" })
        assertTrue(s.suggestions.all { it.url.contains(".ch") })
    }

    @Test fun aFrenchHouseholdOnlyGetsFrenchSuggestions() {
        val s = session("FR")
        s.runThrough("Arthur", "Lego pompiers")
        assertTrue(s.suggestions.isNotEmpty())
        assertTrue(s.suggestions.all { it.store.country == "FR" })
        assertFalse("une boutique suisse a fuité dans un foyer FR", s.suggestions.any { it.store.domain == "galaxus.ch" })
    }

    @Test fun suggestionsCrossQueriesWithTheFirstThreeStores() {
        val s = session("CH")
        s.runThrough()
        assertEquals(3, s.suggestions.size)
        assertEquals(listOf("franz-carl-weber.ch", "galaxus.ch", "fnac.ch"), s.suggestions.map { it.store.domain })
    }

    @Test fun withoutHouseholdCountryNothingIsInvented() {
        val s = session(country = null)
        s.runThrough()
        assertEquals(Stage.Blocked(BlockReason.NoStore), s.stage)
        assertTrue(s.suggestions.isEmpty())
    }

    @Test fun withoutAWishThereIsNoSuggestion() {
        val s = session()
        s.runThrough(wish = null)
        assertEquals(Stage.Blocked(BlockReason.NoWish), s.stage)
    }

    // endregion

    // region Validation parentale

    @Test fun nothingIsSavedWithoutParentValidation() {
        val s = session()
        s.runThrough()
        assertTrue(s.suggestions.isNotEmpty())
        assertEquals(0, s.savedCount)
    }

    @Test fun aSavedSuggestionLeavesTheList() {
        val s = session()
        s.runThrough()
        val first = s.suggestions.first()
        val before = s.suggestions.size
        s.markSaved(first)
        assertEquals(1, s.savedCount)
        assertEquals(before - 1, s.suggestions.size)
    }

    @Test fun aRejectedSuggestionSavesNothing() {
        val s = session()
        s.runThrough()
        val first = s.suggestions.first()
        s.reject(first)
        assertEquals(0, s.savedCount)
        assertFalse(first in s.suggestions)
    }

    @Test fun theSessionEndsWhenTheLastSuggestionIsHandled() {
        val s = session()
        s.runThrough()
        s.suggestions.toList().forEach { s.reject(it) }
        assertEquals(Stage.Done, s.stage)
    }

    @Test fun finishingDropsUnvalidatedSuggestions() {
        val s = session()
        s.runThrough()
        assertNotNull(s.suggestions.firstOrNull())
        s.finish()
        assertEquals(Stage.Done, s.stage)
        assertTrue(s.suggestions.isEmpty())
        assertEquals(0, s.savedCount)
    }

    // endregion
}
