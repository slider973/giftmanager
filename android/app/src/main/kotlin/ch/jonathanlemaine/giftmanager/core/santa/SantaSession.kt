package ch.jonathanlemaine.giftmanager.core.santa

import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import java.util.UUID

/**
 * Déroulé d'une session du Père Noël, séparé de son affichage — `SantaSessionModel.swift`. Pur : ni coroutines, ni
 * Android ; le ViewModel enchaîne l'audio et les appels réseau.
 *
 * Trois invariants portés ici plutôt que dans l'écran :
 *  1. **Seul un parent déclenche la session.** [childModeActive] la bloque : en mode enfant, elle refuse de démarrer.
 *  2. **Les boutiques viennent du pays du foyer** (`households.country`), jamais de la locale de l'appareil.
 *  3. **Rien n'est enregistré sans validation parentale** : les suggestions restent en mémoire jusqu'à ce que le
 *     parent en retienne une ([markSaved]).
 */
class SantaSession(
    val script: SantaScript,
    /** Pays du foyer — source unique des boutiques proposées. */
    private val householdCountry: String?,
    private val childModeActive: Boolean,
) {
    sealed interface Stage {
        data object NotStarted : Stage
        data class Speaking(val lineIndex: Int) : Stage
        data class AwaitingAnswer(val lineIndex: Int) : Stage
        data object ReviewingSuggestions : Stage
        data object Done : Stage

        /** La session ne peut pas démarrer (mode enfant, pays absent…). */
        data class Blocked(val reason: BlockReason) : Stage
    }

    /** Pourquoi la session est bloquée ; l'écran en tire le message. */
    enum class BlockReason { ChildMode, EmptyScript, NoStore, NoWish }

    /** Une piste de cadeau à soumettre au parent. */
    data class Suggestion(
        val id: UUID = UUID.randomUUID(),
        val query: String,
        val store: StoreCatalog.SearchableStore,
        val url: String,
    ) {
        val storeName: String get() = store.name
    }

    var stage: Stage = Stage.NotStarted
        private set
    var wishes = SantaWishes()
        private set
    var suggestions: List<Suggestion> = emptyList()
        private set
    var savedCount = 0
        private set

    // region Déroulé

    val currentLine: SantaScript.Line?
        get() = when (val s = stage) {
            is Stage.Speaking -> script.repliques.getOrNull(s.lineIndex)
            is Stage.AwaitingAnswer -> script.repliques.getOrNull(s.lineIndex)
            else -> null
        }

    /** Démarre la session. Refuse si l'appareil est en mode enfant. */
    fun start() {
        // Garde-fou central : un enfant ne doit pas pouvoir lancer le Père Noël seul. L'absence de bouton dans
        // l'interface enfant ne suffit pas — la règle vit ici, où elle est testable.
        if (childModeActive) {
            stage = Stage.Blocked(BlockReason.ChildMode)
            return
        }
        if (script.repliques.isEmpty()) {
            stage = Stage.Blocked(BlockReason.EmptyScript)
            return
        }
        advance(0)
    }

    /** Passe à la réplique suivante, ou à la revue des suggestions à la fin. */
    fun advanceAfterSpeaking() {
        val s = stage as? Stage.Speaking ?: return
        val line = script.repliques[s.lineIndex]
        if (line.attendReponse) stage = Stage.AwaitingAnswer(s.lineIndex) else advance(s.lineIndex + 1)
    }

    /** Enregistre la réponse saisie par le parent et avance. */
    fun submitAnswer(value: String) {
        val s = stage as? Stage.AwaitingAnswer ?: return
        script.repliques[s.lineIndex].champ?.let { apply(value, it) }
        advance(s.lineIndex + 1)
    }

    /** Bascule un choix d'activité (plusieurs réponses possibles). */
    fun toggleActivity(key: String) {
        wishes = wishes.copy(activites = if (key in wishes.activites) wishes.activites - key else wishes.activites + key)
    }

    /** Passe une question facultative sans rien enregistrer. */
    fun skipAnswer() {
        val s = stage as? Stage.AwaitingAnswer ?: return
        advance(s.lineIndex + 1)
    }

    private fun advance(index: Int) {
        if (index !in script.repliques.indices) {
            buildSuggestions()
            return
        }
        stage = Stage.Speaking(index)
    }

    private fun apply(value: String, champ: String) {
        val clean = value.trim()
        wishes = when (champ) {
            "prenom" -> wishes.copy(prenom = clean)
            "envie_principale" -> wishes.copy(enviePrincipale = clean)
            "preferences" -> wishes.copy(preferences = clean)
            "envie_secondaire" -> wishes.copy(envieSecondaire = clean)
            else -> wishes // « activite » : géré par toggleActivity
        }
    }

    // endregion

    // region Suggestions

    /** Construit les pistes de cadeaux dans les boutiques du pays du foyer. */
    fun buildSuggestions() {
        val stores = StoreCatalog.searchableStores(householdCountry)
        if (stores.isEmpty()) {
            stage = Stage.Blocked(BlockReason.NoStore)
            return
        }
        if (!wishes.estExploitable) {
            stage = Stage.Blocked(BlockReason.NoWish)
            return
        }

        // Les requêtes vont de la plus précise à la plus large ; on croise avec les trois premières boutiques du
        // pays pour rester lisible à l'écran.
        suggestions = wishes.requetes.flatMap { query ->
            stores.take(MAX_STORES).mapNotNull { store ->
                StoreCatalog.searchUrl(query, store)?.let { Suggestion(query = query, store = store, url = it) }
            }
        }
        stage = Stage.ReviewingSuggestions
    }

    /** Le parent a retenu [suggestion] et l'idée est enregistrée : elle quitte la liste. */
    fun markSaved(suggestion: Suggestion) {
        savedCount += 1
        remove(suggestion)
    }

    /** Le parent écarte une suggestion sans rien enregistrer. */
    fun reject(suggestion: Suggestion) = remove(suggestion)

    private fun remove(suggestion: Suggestion) {
        suggestions = suggestions.filterNot { it.id == suggestion.id }
        if (suggestions.isEmpty()) stage = Stage.Done
    }

    /** Clôt la session en laissant tomber ce qui n'a pas été validé. */
    fun finish() {
        suggestions = emptyList()
        stage = Stage.Done
    }

    // endregion

    private companion object {
        const val MAX_STORES = 3
    }
}
