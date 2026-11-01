package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddTarget
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddUiState
import java.net.URI

/** Que faire d'un lien partagé selon les listes disponibles. */
sealed interface ShareResolution {
    /** Aucun enfant : rien où ajouter. */
    data object NoList : ShareResolution

    /** Une seule liste : le formulaire s'ouvre directement dessus. */
    data class Single(val target: QuickAddTarget) : ShareResolution

    /** Plusieurs listes : l'utilisateur choisit l'enfant. */
    data object Choose : ShareResolution
}

object ShareTargets {
    /** Même règle que la feuille « + » : envie dans mon foyer, idée ailleurs (déjà portée par [QuickAddTarget.kind]). */
    fun resolve(state: QuickAddUiState): ShareResolution {
        val all = state.myLists + state.otherLists
        return when (all.size) {
            0 -> ShareResolution.NoList
            1 -> ShareResolution.Single(all.single())
            else -> ShareResolution.Choose
        }
    }

    /** Domaine affiché sous le titre de la feuille (« amazon.fr »), sans `www.`. */
    fun domain(url: String): String? = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
}
