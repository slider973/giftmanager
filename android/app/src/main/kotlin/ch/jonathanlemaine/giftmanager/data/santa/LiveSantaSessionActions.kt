package ch.jonathanlemaine.giftmanager.data.santa

import ch.jonathanlemaine.giftmanager.core.gifts.NewItem
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.santa.SantaSessionActions
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.util.UUID

/**
 * Branche la session du Père Noël sur le dépôt réel — `LiveSantaSessionActions.swift`. C'est ici, et seulement ici,
 * que les suggestions validées par le parent deviennent des idées de cadeau en base.
 */
class LiveSantaSessionActions(
    private val items: ItemRepository,
    private val session: AppSession,
) : SantaSessionActions {
    override suspend fun createGiftIdea(childId: UUID, title: String, link: String?) {
        // Pas de repli sur l'enfant : une idée n'est jamais attribuée à quelqu'un d'autre que l'utilisateur connecté.
        val userId = session.state.value.userId ?: error("NOT_AUTHENTICATED")
        // `reviewStatus` reste à sa valeur par défaut : une suggestion retenue par le parent est déjà validée.
        val item = NewItem(
            childId = childId,
            eventId = null,
            kind = WishKind.WISH,
            title = title,
            notes = NOTES,
            imageUrl = null,
            priority = 0,
            owned = false,
            createdBy = userId,
        )
        val links = listOfNotNull(
            link?.let {
                val store = StoreCatalog.store(it)
                DraftLink(url = it, store = store?.name, country = store?.country, price = null, currency = store?.currency)
            },
        )
        items.addItem(item, links)
        session.itemsChanged()
    }

    companion object {
        const val NOTES = "Recueilli avec le Père Noël"
    }
}
