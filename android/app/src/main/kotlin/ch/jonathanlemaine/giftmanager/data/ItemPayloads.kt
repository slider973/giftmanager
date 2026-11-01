@file:OptIn(ExperimentalSerializationApi::class)

package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.gifts.NewItem
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Corps JSON des écritures sur les cadeaux. Construits à la main : un optionnel absent d'un `update` ne
 * l'efface pas, il faut un `null` explicite (un `UPDATE` ne touche que les colonnes envoyées).
 */
internal object ItemPayloads {
    /** Valeur transportée d'une énumération (`@SerialName`) : « wish », « owned_already »… */
    fun wire(kind: WishKind): String = GiftJson.write.encodeToJsonElement(WishKind.serializer(), kind).jsonPrimitive.content

    fun wire(review: IdeaReview): String = GiftJson.write.encodeToJsonElement(IdeaReview.serializer(), review).jsonPrimitive.content

    private fun uuid(value: UUID?): JsonElement = value?.let { JsonPrimitive(it.toString()) } ?: JsonNull

    /** Montant décimal écrit tel quel (« 12.50 »), sans passer par un flottant. */
    private fun decimal(value: BigDecimal?): JsonElement = value?.let { JsonUnquotedLiteral(it.toPlainString()) } ?: JsonNull

    fun newItem(item: NewItem): JsonObject = buildJsonObject {
        put("child_id", item.childId.toString())
        put("event_id", uuid(item.eventId))
        put("kind", wire(item.kind))
        put("title", item.title)
        put("notes", item.notes?.let(::JsonPrimitive) ?: JsonNull)
        put("image_url", item.imageUrl?.let(::JsonPrimitive) ?: JsonNull)
        put("priority", item.priority)
        put("owned", item.owned)
        put("created_by", item.createdBy.toString())
        put("review_status", wire(item.reviewStatus))
    }

    fun itemPatch(title: String, notes: String?, imageUrl: String?, priority: Int, eventId: UUID?): JsonObject = buildJsonObject {
        put("title", title)
        put("notes", notes?.let(::JsonPrimitive) ?: JsonNull)
        put("image_url", imageUrl?.let(::JsonPrimitive) ?: JsonNull)
        put("priority", priority)
        put("event_id", uuid(eventId))
    }

    fun priorityPatch(favorite: Boolean): JsonObject = buildJsonObject { put("priority", if (favorite) 1 else 0) }

    fun ownedPatch(owned: Boolean): JsonObject = buildJsonObject { put("owned", owned) }

    fun positionPatch(position: Int): JsonObject = buildJsonObject { put("position", position) }

    fun linkRow(itemId: UUID, link: DraftLink): JsonObject = buildJsonObject {
        put("item_id", itemId.toString())
        put("url", link.url)
        put("store", link.store?.let(::JsonPrimitive) ?: JsonNull)
        put("country", link.country?.let(::JsonPrimitive) ?: JsonNull)
        put("price", decimal(link.price))
        put("currency", link.currency?.let(::JsonPrimitive) ?: JsonNull)
    }

    // region Paramètres de RPC

    /** Les filtres absents sont omis : les paramètres SQL valent `null` par défaut. */
    fun childItemsParams(childId: UUID, eventId: UUID?, kind: WishKind?): JsonObject = buildJsonObject {
        put(Rpc.P_CHILD, childId.toString())
        eventId?.let { put(Rpc.P_EVENT, it.toString()) }
        kind?.let { put(Rpc.P_KIND, wire(it)) }
    }

    fun itemParams(itemId: UUID): JsonObject = buildJsonObject { put(Rpc.P_ITEM, itemId.toString()) }

    fun purchasedParams(itemId: UUID, purchased: Boolean): JsonObject = buildJsonObject {
        put(Rpc.P_ITEM, itemId.toString())
        put(Rpc.P_PURCHASED, purchased)
    }

    fun joinPotParams(itemId: UUID, amount: BigDecimal, currency: String): JsonObject = buildJsonObject {
        put(Rpc.P_ITEM, itemId.toString())
        put(Rpc.P_AMOUNT, decimal(amount))
        put(Rpc.P_CURRENCY, currency.uppercase())
    }

    fun reviewIdeaParams(itemId: UUID, decision: IdeaReview, note: String?): JsonObject = buildJsonObject {
        put(Rpc.P_ITEM, itemId.toString())
        put(Rpc.P_DECISION, wire(decision))
        put(Rpc.P_NOTE, note?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun audienceParams(childId: UUID): JsonObject = buildJsonObject { put(Rpc.P_CHILD, childId.toString()) }

    // endregion
}
