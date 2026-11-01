@file:UseSerializers(UuidSerializer::class)

package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.gifts.NewItem
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.core.model.UuidSerializer
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** Où se range un cadeau : suffit à ouvrir sa fiche depuis son seul identifiant (notification, lien). */
@Serializable
data class ItemLocator(
    val id: UUID,
    @SerialName("child_id") val childId: UUID,
    @SerialName("event_id") val eventId: UUID? = null,
)

@Serializable
internal data class InsertedId(val id: UUID)

/**
 * Cadeaux d'un enfant, leurs liens et la validation des idées (#9, #57, #61). Les règles d'accès et d'anonymat
 * sont appliquées par le serveur (RLS + RPC) : `child_items` renvoie `status = null` aux parents.
 */
interface ItemRepository {
    /** Paramètres `null` omis : tous les événements, souhaits et idées. */
    suspend fun childItems(childId: UUID, eventId: UUID? = null, kind: WishKind? = null): List<WishItem>

    /** Liens des cadeaux, par ordre de création. Vide sans requête quand [itemIds] est vide. */
    suspend fun links(itemIds: List<UUID>): List<ItemLink>

    /** Enfant et événement d'un cadeau, ou `null` s'il n'existe plus ou n'est pas visible. */
    suspend fun locate(itemId: UUID): ItemLocator?

    /** Crée le cadeau puis ses liens ; si les liens échouent, le cadeau est supprimé et l'erreur relancée. */
    suspend fun addItem(item: NewItem, links: List<DraftLink>): UUID

    suspend fun updateItem(id: UUID, title: String, notes: String?, imageUrl: String?, priority: Int, eventId: UUID?)

    suspend fun setPriority(itemId: UUID, favorite: Boolean)

    suspend fun setOwned(itemId: UUID, owned: Boolean)

    suspend fun deleteItem(itemId: UUID)

    /**
     * Enregistre l'ordre des souhaits : [itemIds] dans l'ordre voulu. Seules les positions qui diffèrent de
     * [currentPositions] sont écrites (toutes si l'on ne les connaît pas). Appels sérialisés.
     */
    suspend fun reorder(itemIds: List<UUID>, currentPositions: Map<UUID, Int> = emptyMap())

    /** Remplace les liens : les nouveaux sont insérés avant de supprimer les anciens (un échec ne perd rien). */
    suspend fun replaceLinks(itemId: UUID, links: List<DraftLink>)

    /** Idées soumises en attente d'un verdict, pour mes enfants. */
    suspend fun pendingIdeas(): List<PendingIdea>

    /** Nombre de personnes qui verraient une idée non soumise : 0 ⇒ elle n'atteindrait personne. */
    suspend fun ideaAudience(childId: UUID): Int

    /** Verdict du parent sur une idée : `accepted`, `rejected` ou `owned_already`. */
    suspend fun reviewIdea(itemId: UUID, decision: IdeaReview, note: String? = null)
}

class SupabaseItemRepository(private val client: SupabaseClient) : ItemRepository {
    private val reorderLock = Mutex()

    override suspend fun childItems(childId: UUID, eventId: UUID?, kind: WishKind?): List<WishItem> =
        client.postgrest.rpc(Rpc.CHILD_ITEMS, ItemPayloads.childItemsParams(childId, eventId, kind)).decodeTolerantList()

    override suspend fun links(itemIds: List<UUID>): List<ItemLink> {
        if (itemIds.isEmpty()) return emptyList()
        // Paquets : l'URL d'une requête GET a une longueur limitée.
        return itemIds.chunked(LINKS_CHUNK).flatMap { chunk ->
            client.from("item_links").select(Columns.ALL) {
                filter { isIn("item_id", chunk.map(UUID::toString)) }
                order("created_at", Order.ASCENDING)
            }.decodeTolerantList<ItemLink>()
        }
    }

    override suspend fun locate(itemId: UUID): ItemLocator? =
        client.from("wish_items").select(Columns.raw(LOCATOR_COLUMNS)) {
            filter { eq("id", itemId.toString()) }
        }.decodeTolerantList<ItemLocator>().firstOrNull()

    override suspend fun addItem(item: NewItem, links: List<DraftLink>): UUID {
        val inserted = client.from("wish_items").insert(ItemPayloads.newItem(item)) {
            select(Columns.raw("id"))
        }.decodeTolerantList<InsertedId>().first().id
        try {
            replaceLinks(inserted, links)
        } catch (e: Exception) {
            // Sans ses liens le cadeau serait à moitié créé : on le retire (même si l'on est annulé) puis on relance.
            withContext(NonCancellable) { runCatching { deleteItem(inserted) } }
            throw e
        }
        return inserted
    }

    override suspend fun updateItem(id: UUID, title: String, notes: String?, imageUrl: String?, priority: Int, eventId: UUID?) {
        client.from("wish_items").update(ItemPayloads.itemPatch(title, notes, imageUrl, priority, eventId)) {
            filter { eq("id", id.toString()) }
        }
    }

    override suspend fun setPriority(itemId: UUID, favorite: Boolean) {
        client.from("wish_items").update(ItemPayloads.priorityPatch(favorite)) { filter { eq("id", itemId.toString()) } }
    }

    override suspend fun setOwned(itemId: UUID, owned: Boolean) {
        client.from("wish_items").update(ItemPayloads.ownedPatch(owned)) { filter { eq("id", itemId.toString()) } }
    }

    override suspend fun deleteItem(itemId: UUID) {
        client.from("wish_items").delete { filter { eq("id", itemId.toString()) } }
    }

    override suspend fun reorder(itemIds: List<UUID>, currentPositions: Map<UUID, Int>) {
        reorderLock.withLock {
            itemIds.forEachIndexed { index, id ->
                if (currentPositions[id] == index) return@forEachIndexed
                client.from("wish_items").update(ItemPayloads.positionPatch(index)) { filter { eq("id", id.toString()) } }
            }
        }
    }

    override suspend fun replaceLinks(itemId: UUID, links: List<DraftLink>) {
        val oldIds = client.from("item_links").select(Columns.raw("id")) {
            filter { eq("item_id", itemId.toString()) }
        }.decodeTolerantList<InsertedId>().map { it.id }
        val rows = links.filter { it.url.isNotEmpty() }.map { ItemPayloads.linkRow(itemId, it) }
        if (rows.isNotEmpty()) client.from("item_links").insert(rows)
        if (oldIds.isNotEmpty()) {
            client.from("item_links").delete { filter { isIn("id", oldIds.map(UUID::toString)) } }
        }
    }

    override suspend fun pendingIdeas(): List<PendingIdea> = client.postgrest.rpc(Rpc.PENDING_IDEAS).decodeTolerantList()

    override suspend fun ideaAudience(childId: UUID): Int =
        client.postgrest.rpc(Rpc.IDEA_AUDIENCE, ItemPayloads.audienceParams(childId)).rpcInt()

    override suspend fun reviewIdea(itemId: UUID, decision: IdeaReview, note: String?) {
        client.postgrest.rpc(Rpc.REVIEW_IDEA, ItemPayloads.reviewIdeaParams(itemId, decision, note))
    }

    internal companion object {
        const val LINKS_CHUNK = 100
        const val LOCATOR_COLUMNS = "id, child_id, event_id"
    }
}
