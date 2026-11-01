package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.ui.components.GiftStatus
import java.util.UUID

/** Une liste chargée pour la recherche : son enfant, ses cadeaux et leurs liens. */
data class SearchList(
    val child: Child,
    /** Liste d'un de mes enfants (mode surprise) : aucun statut n'existe côté serveur, aucun badge n'est affiché. */
    val isParentList: Boolean,
    val items: List<WishItem>,
    val links: Map<UUID, List<ItemLink>>,
)

/** Filtres de la recherche (iOS : `SearchView`). */
data class SearchFilters(
    val query: String = "",
    /** « Disponibles » : exclut les listes de parents et tout statut autre que disponible. */
    val onlyAvailable: Boolean = false,
    /** Code pays d'une boutique : ne garde que les cadeaux qui ont un lien de ce pays. */
    val country: String? = null,
)

/** Un cadeau trouvé : [link] est le meilleur lien (celui du pays du lecteur d'abord), [badge] est `null` pour une liste de parent. */
data class SearchResult(val item: WishItem, val link: ItemLink?, val badge: GiftStatus?)

/** Résultats d'un enfant. */
data class SearchGroup(val child: Child, val isParentList: Boolean, val results: List<SearchResult>)

/** Règles de la recherche globale (iOS : `SearchView`). Fonctions pures : aucune requête à la frappe. */
object SearchLogic {
    /**
     * Résultats regroupés par enfant, dans l'ordre de [lists] (celui de la famille) ; un enfant sans résultat est omis.
     * Un cadeau déjà possédé n'apparaît jamais.
     */
    fun groups(lists: List<SearchList>, filters: SearchFilters, preferredCountry: String? = null): List<SearchGroup> {
        val text = filters.query.trim().lowercase()
        return lists.mapNotNull { list ->
            val results = list.items.mapNotNull { item ->
                val links = list.links[item.id].orEmpty()
                if (!matches(item, list, links, filters, text)) return@mapNotNull null
                SearchResult(
                    item = item,
                    link = StoreCatalog.sorted(links, preferredCountry).firstOrNull(),
                    badge = GiftActionReducer.badge(item, list.isParentList),
                )
            }
            if (results.isEmpty()) null else SearchGroup(list.child, list.isParentList, results)
        }
    }

    private fun matches(item: WishItem, list: SearchList, links: List<ItemLink>, filters: SearchFilters, text: String): Boolean {
        if (item.owned) return false
        if (text.isNotEmpty() && !item.title.lowercase().contains(text) && !list.child.firstName.lowercase().contains(text)) return false
        if (filters.onlyAvailable && (list.isParentList || item.status != ItemStatus.AVAILABLE)) return false
        if (filters.country != null && links.none { it.country == filters.country }) return false
        return true
    }

    /** Pays des boutiques présents dans les listes chargées, sans doublon, triés (les puces de filtre). */
    fun countries(lists: List<SearchList>): List<String> =
        lists.flatMap { list -> list.links.values.flatten() }.mapNotNull { it.country }.distinct().sorted()
}
