package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Le cadeau n'existe plus, ou n'est pas visible : le code est celui du serveur (`GiftError.NotFound`). */
class ItemNotFoundException : Exception("ITEM_NOT_FOUND")

/** Un cadeau chargé par son seul identifiant, avec son contexte. */
data class LoadedItem(
    val item: WishItem,
    val links: List<ItemLink>,
    /** Événement du cadeau, ou à défaut celui de la liste d'où l'on vient. */
    val eventId: UUID?,
    val eventDate: DayDate?,
    /** L'événement est passé : fiche archivée, ni modification ni réservation. */
    val readOnly: Boolean,
)

/**
 * Charge un cadeau à partir de son seul identifiant (notification, lien) : on retrouve d'abord son enfant, puis la
 * liste de l'enfant (c'est `child_items` qui applique le secret) et les liens du cadeau, en parallèle.
 */
class ItemLoader(private val items: ItemRepository, private val clock: Clock = Clock.systemDefaultZone()) {
    /**
     * @param events événements connus de la session (la date et le statut passé en dérivent)
     * @param contextEventId événement de la liste d'où l'on vient, si le cadeau n'en porte pas
     * @throws ItemNotFoundException cadeau absent, ou masqué à l'appelant
     */
    suspend fun load(itemId: UUID, events: List<GiftEvent>, contextEventId: UUID? = null): LoadedItem = coroutineScope {
        val locator = items.locate(itemId) ?: throw ItemNotFoundException()
        val listed = async { items.childItems(locator.childId) }
        val links = async { items.links(listOf(itemId)) }
        // Le serveur ne renvoie pas un cadeau qu'on n'a pas le droit de voir : il est alors introuvable.
        val item = listed.await().firstOrNull { it.id == itemId } ?: throw ItemNotFoundException()
        val eventId = locator.eventId ?: contextEventId
        val event = events.firstOrNull { it.id == eventId }
        LoadedItem(item, links.await(), eventId, event?.eventDate, readOnly = event?.isPast(clock) ?: false)
    }
}
