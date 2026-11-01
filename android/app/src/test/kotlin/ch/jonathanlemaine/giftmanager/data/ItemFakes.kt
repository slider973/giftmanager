package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreview
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewService
import ch.jonathanlemaine.giftmanager.core.gifts.NewItem
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.core.model.PotParticipant
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.math.BigDecimal
import java.util.UUID

// Fakes en mémoire des dépôts de cadeaux, avec pannes injectables et journal des appels.

/** Cadeau de test : `status` vaut `available` par défaut (vue d'un membre) ; `null` pour la vue d'un parent. */
fun wishItem(
    n: Int,
    childN: Int = 1,
    title: String = "Cadeau $n",
    kind: WishKind = WishKind.WISH,
    status: ItemStatus? = ItemStatus.AVAILABLE,
    owned: Boolean = false,
    priority: Int = 0,
    position: Int = 0,
    eventId: UUID? = null,
    createdBy: UUID? = null,
    myReservation: ReservationState? = null,
    myContribution: BigDecimal? = null,
    reviewStatus: IdeaReview = IdeaReview.NONE,
) = WishItem(
    id = uuid(700 + n), childId = uuid(400 + childN), eventId = eventId, kind = kind, title = title, priority = priority,
    position = position, owned = owned, createdBy = createdBy, status = status, myReservation = myReservation,
    myContribution = myContribution, reviewStatus = reviewStatus,
)

fun itemLink(n: Int, itemN: Int, url: String = "https://www.galaxus.ch/p/$n", store: String? = "Galaxus", country: String? = "CH") =
    ItemLink(uuid(800 + n), uuid(700 + itemN), url, store, country, BigDecimal("10.00"), "CHF")

class FakeItemRepository : ItemRepository {
    val items = mutableListOf<WishItem>()
    val itemLinks = mutableListOf<ItemLink>()
    var failure: Throwable? = null
    var audience = 0
    var pending = listOf<PendingIdea>()

    /** Dernier paramètre d'événement reçu par [childItems] : `null` = aucun filtre. */
    var lastEventFilter: UUID? = null
    var childItemsCalls = 0
    var linksCalls = 0
    var added: NewItem? = null
    var addedLinks: List<DraftLink> = emptyList()
    var updated: List<Any?>? = null
    var replacedLinks: List<DraftLink>? = null
    val deleted = mutableListOf<UUID>()
    val priorities = mutableListOf<Pair<UUID, Boolean>>()
    val ownedCalls = mutableListOf<Pair<UUID, Boolean>>()
    var reordered: List<UUID>? = null
    var reorderPositions: Map<UUID, Int>? = null
    val reviews = mutableListOf<Triple<UUID, IdeaReview, String?>>()

    /** Écriture « serveur » : un rechargement ultérieur voit le changement. */
    private fun modify(id: UUID, change: (WishItem) -> WishItem) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = change(items[index])
    }

    private fun maybeFail() {
        failure?.let { throw it }
    }

    override suspend fun childItems(childId: UUID, eventId: UUID?, kind: WishKind?): List<WishItem> {
        childItemsCalls++
        lastEventFilter = eventId
        maybeFail()
        return items.filter { it.childId == childId && (kind == null || it.kind == kind) && (eventId == null || it.eventId == eventId || it.eventId == null) }
    }

    override suspend fun links(itemIds: List<UUID>): List<ItemLink> {
        linksCalls++
        maybeFail()
        return itemLinks.filter { it.itemId in itemIds }
    }

    override suspend fun locate(itemId: UUID): ItemLocator? {
        maybeFail()
        return items.firstOrNull { it.id == itemId }?.let { ItemLocator(it.id, it.childId, it.eventId) }
    }

    override suspend fun addItem(item: NewItem, links: List<DraftLink>): UUID {
        maybeFail()
        added = item
        addedLinks = links
        return uuid(900 + (items.size))
    }

    override suspend fun updateItem(id: UUID, title: String, notes: String?, imageUrl: String?, priority: Int, eventId: UUID?) {
        maybeFail()
        updated = listOf(id, title, notes, imageUrl, priority, eventId)
    }

    override suspend fun setPriority(itemId: UUID, favorite: Boolean) {
        maybeFail()
        priorities += itemId to favorite
        modify(itemId) { it.copy(priority = if (favorite) 1 else 0) }
    }

    override suspend fun setOwned(itemId: UUID, owned: Boolean) {
        maybeFail()
        ownedCalls += itemId to owned
        modify(itemId) { it.copy(owned = owned) }
    }

    override suspend fun deleteItem(itemId: UUID) {
        maybeFail()
        deleted += itemId
        items.removeAll { it.id == itemId }
    }

    override suspend fun reorder(itemIds: List<UUID>, currentPositions: Map<UUID, Int>) {
        maybeFail()
        reordered = itemIds
        reorderPositions = currentPositions
        itemIds.forEachIndexed { index, id -> modify(id) { it.copy(position = index) } }
    }

    override suspend fun replaceLinks(itemId: UUID, links: List<DraftLink>) {
        maybeFail()
        replacedLinks = links
    }

    override suspend fun pendingIdeas(): List<PendingIdea> {
        maybeFail()
        return pending
    }

    override suspend fun ideaAudience(childId: UUID): Int {
        maybeFail()
        return audience
    }

    override suspend fun reviewIdea(itemId: UUID, decision: IdeaReview, note: String?) {
        maybeFail()
        reviews += Triple(itemId, decision, note)
        // Écriture « serveur » : l'idée n'est plus en attente.
        pending = pending.filterNot { it.id == itemId }
    }
}

class FakeReservationRepository : ReservationRepository {
    val calls = mutableListOf<String>()
    var failure: Throwable? = null

    private fun record(name: String) {
        calls += name
        failure?.let { throw it }
    }

    override suspend fun reserve(itemId: UUID) = record("reserve")

    override suspend fun cancel(itemId: UUID) = record("cancel")

    override suspend fun setPurchased(itemId: UUID, purchased: Boolean) = record("setPurchased($purchased)")
}

class FakePotRepository : PotRepository {
    val calls = mutableListOf<String>()
    var participants = listOf<PotParticipant>()
    var failure: Throwable? = null

    /** Participations reçues : cadeau, montant, devise. */
    val joined = mutableListOf<Triple<UUID, BigDecimal, String>>()

    override suspend fun join(itemId: UUID, amount: BigDecimal, currency: String) {
        calls += "join"
        joined += Triple(itemId, amount, currency)
        failure?.let { throw it }
    }

    override suspend fun leave(itemId: UUID) {
        calls += "leave"
        failure?.let { throw it }
    }

    override suspend fun participants(itemId: UUID): List<PotParticipant> {
        calls += "participants"
        failure?.let { throw it }
        return participants
    }
}

class FakeImageUploader : ImageUploader {
    var uploads = 0
    var failure: Throwable? = null

    override suspend fun upload(bytes: ByteArray, userId: UUID): String {
        uploads++
        failure?.let { throw it }
        return "https://example.test/image.jpg"
    }
}

class FakeItemNotifier : ItemNotifier {
    val newItems = mutableListOf<List<UUID>>()
    val reservations = mutableListOf<UUID>()
    val reviews = mutableListOf<Pair<UUID, IdeaReview>>()

    override fun notifyNewItems(itemIds: List<UUID>) {
        newItems += itemIds
    }

    override fun notifyReservation(itemId: UUID) {
        reservations += itemId
    }

    override fun notifyIdeaReview(itemId: UUID, decision: IdeaReview) {
        reviews += itemId to decision
    }
}

class FakeLinkPreviewService(var result: LinkPreview? = null) : LinkPreviewService {
    val requests = mutableListOf<String>()

    override suspend fun preview(raw: String): LinkPreview? {
        requests += raw
        return result
    }
}
