package ch.jonathanlemaine.giftmanager.core.santa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SantaScriptTests.swift` : le script est la source de vérité de ce que l'enfant entendra. Ces tests verrouillent le
 * contrat entre le JSON livré (partagé avec iOS) et le code qui le lit.
 */
class SantaScriptTest {
    @Test fun decodesTheExpectedShape() {
        val script = SantaScript.parse(
            """
            {
              "_commentaire": ["ignoré"],
              "voix": { "nom": "Sylvestre", "voice_id": "2vBkYF6XwnA9E7Osnt2m",
                        "model_id": "eleven_multilingual_v2", "reglages": { "stability": 0.35 } },
              "repliques": [
                { "id": "accueil", "texte": "Ho ho ho !", "attend_reponse": false },
                { "id": "demande_prenom", "texte": "Comment t'appelles-tu ?",
                  "attend_reponse": true, "champ": "prenom", "aide_parent": "Saisis le prénom." },
                { "id": "question_activite", "texte": "Que préfères-tu ?",
                  "attend_reponse": true, "champ": "activite",
                  "choix": [ { "cle": "construire", "libelle": "Construire" } ] }
              ]
            }
            """,
        )
        assertEquals("Sylvestre", script.voix.nom)
        assertEquals("eleven_multilingual_v2", script.voix.modelId)
        assertEquals(3, script.repliques.size)
        assertFalse(script.repliques[0].attendReponse)
        assertEquals("prenom", script.repliques[1].champ)
        assertEquals("construire", script.repliques[2].choix?.first()?.cle)
    }

    @Test fun missingOptionalFieldsDoNotBreakDecoding() {
        // Régression : un champ obligatoire de plus casserait les scripts déjà livrés.
        val script = SantaScript.parse(
            """{ "voix": { "nom": "V", "voice_id": "x", "model_id": "m" }, "repliques": [ { "id": "a", "texte": "Bonjour" } ] }""",
        )
        val line = script.repliques[0]
        assertFalse(line.facultatif)
        assertFalse(line.attendReponse)
        assertNull(line.choix)
    }

    @Test fun eachLineNamesItsAudioAsset() {
        val script = SantaScript.parse(
            """{ "voix": { "nom": "V", "voice_id": "x", "model_id": "m" }, "repliques": [ { "id": "cloture", "texte": "Au revoir" } ] }""",
        )
        assertEquals("audio/cloture.mp3", script.repliques[0].audioAsset)
    }

    @Test fun emptyScriptHasNoLine() {
        assertTrue(SantaScript.EMPTY.repliques.isEmpty())
    }

    @Test fun theShippedFileIsValid() {
        // Lit le vrai JSON partagé avec iOS (ios/GiftManager/Resources/SantaScript), pas une copie.
        val loader = checkNotNull(javaClass.classLoader)
        val text = checkNotNull(loader.getResourceAsStream(SantaScript.ASSET_NAME)) { "script introuvable" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val script = SantaScript.parse(text)

        assertTrue(script.repliques.size >= 5)
        assertEquals("la voix retenue est Sylvestre", "2vBkYF6XwnA9E7Osnt2m", script.voix.voiceId)

        // Les identifiants servent de noms de fichiers audio : ils doivent être uniques.
        val ids = script.repliques.map { it.id }
        assertEquals("identifiants de répliques dupliqués", ids.size, ids.toSet().size)

        // Toute question qui attend une réponse doit nommer son champ, sinon la réponse n'a nulle part où aller.
        script.repliques.filter { it.attendReponse }.forEach {
            assertNotNull("la réplique ${it.id} attend une réponse sans champ", it.champ)
        }

        // Sans accents, le modèle multilingue bascule sur une prononciation anglaise.
        script.repliques.filter { it.texte.length > 40 }.forEach { line ->
            assertTrue("la réplique ${line.id} n'a aucun accent", line.texte.any { it in "éèêëàâäîïôöùûüç" })
        }

        // Chaque réplique a son fichier audio livré.
        script.repliques.forEach { line ->
            assertNotNull("audio manquant pour ${line.id}", loader.getResource(line.audioAsset))
        }
    }
}

class SantaWishesTest {
    @Test fun aSessionWithoutMainWishIsNotUsable() {
        var wishes = SantaWishes()
        assertFalse(wishes.estExploitable)
        wishes = wishes.copy(prenom = "Lina", activites = setOf("construire"))
        assertFalse("un prénom seul ne suffit pas", wishes.estExploitable)
        assertTrue(wishes.copy(enviePrincipale = "une trottinette").estExploitable)
    }

    @Test fun blankWishIsNotAWish() {
        assertFalse(SantaWishes(enviePrincipale = "   \n  ").estExploitable)
    }

    @Test fun preferencesRefineTheMainQuery() {
        val wishes = SantaWishes(enviePrincipale = "trottinette", preferences = "rouge", envieSecondaire = "jeu de société")
        assertEquals(listOf("trottinette rouge", "trottinette", "jeu de société"), wishes.requetes)
    }

    @Test fun withoutPreferenceTheQueryStaysRaw() {
        assertEquals(listOf("Lego pompiers"), SantaWishes(enviePrincipale = "Lego pompiers").requetes)
    }
}
