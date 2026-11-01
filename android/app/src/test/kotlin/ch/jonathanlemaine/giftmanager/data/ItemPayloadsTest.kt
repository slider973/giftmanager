package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.gifts.NewItem
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corps JSON des écritures : un champ effacé doit partir à `null`, jamais être omis. */
class ItemPayloadsTest {
    private val child = uuid(401)
    private val user = uuid(1)

    private fun newItem(eventId: UUID? = null, notes: String? = null, image: String? = null, review: IdeaReview = IdeaReview.NONE) =
        NewItem(child, eventId, WishKind.IDEA, "Vélo", notes, image, 1, false, user, review)

    @Test fun newItemSendsExplicitNulls() {
        val json = ItemPayloads.newItem(newItem())
        assertEquals(JsonNull, json["event_id"])
        assertEquals(JsonNull, json["notes"])
        assertEquals(JsonNull, json["image_url"])
        assertEquals(JsonPrimitive("idea"), json["kind"])
        assertEquals(JsonPrimitive("none"), json["review_status"])
        assertEquals(JsonPrimitive(user.toString()), json["created_by"])
        assertEquals(JsonPrimitive(1), json["priority"])
        assertEquals(JsonPrimitive(false), json["owned"])
        assertEquals(
            setOf("child_id", "event_id", "kind", "title", "notes", "image_url", "priority", "owned", "created_by", "review_status"),
            json.keys,
        )
    }

    @Test fun reviewStatusUsesTheDatabaseValue() {
        assertEquals(JsonPrimitive("pending"), ItemPayloads.newItem(newItem(review = IdeaReview.PENDING))["review_status"])
        assertEquals("owned_already", ItemPayloads.wire(IdeaReview.OWNED_ALREADY))
        assertEquals("wish", ItemPayloads.wire(WishKind.WISH))
    }

    @Test fun updatePatchClearsWithExplicitNulls() {
        val json = ItemPayloads.itemPatch("Vélo", null, null, 0, null)
        assertEquals(JsonNull, json["notes"])
        assertEquals(JsonNull, json["image_url"])
        assertEquals(JsonNull, json["event_id"])
        assertEquals(JsonPrimitive(0), json["priority"])
        val filled = ItemPayloads.itemPatch("Vélo", "n", "u", 1, uuid(501))
        assertEquals(JsonPrimitive(uuid(501).toString()), filled["event_id"])
    }

    @Test fun smallPatches() {
        assertEquals(JsonPrimitive(1), ItemPayloads.priorityPatch(true)["priority"])
        assertEquals(JsonPrimitive(0), ItemPayloads.priorityPatch(false)["priority"])
        assertEquals(JsonPrimitive(true), ItemPayloads.ownedPatch(true)["owned"])
        assertEquals(JsonPrimitive(3), ItemPayloads.positionPatch(3)["position"])
    }

    @Test fun linkRowKeepsNullsAndWritesThePriceAsANumber() {
        val row = ItemPayloads.linkRow(uuid(700), DraftLink(url = "https://x.ch/a", price = BigDecimal("12.50")))
        assertEquals(JsonNull, row["store"])
        assertEquals(JsonNull, row["country"])
        assertEquals(JsonNull, row["currency"])
        assertEquals("12.50", (row["price"] as JsonPrimitive).content)
        assertFalse((row["price"] as JsonPrimitive).isString)
        assertEquals(JsonNull, ItemPayloads.linkRow(uuid(700), DraftLink(url = "u"))["price"])
    }

    @Test fun childItemsOmitsAbsentFilters() {
        val bare = ItemPayloads.childItemsParams(child, null, null)
        assertEquals(setOf(Rpc.P_CHILD), bare.keys)
        val full = ItemPayloads.childItemsParams(child, uuid(501), WishKind.IDEA)
        assertEquals(JsonPrimitive("idea"), full[Rpc.P_KIND])
        assertEquals(JsonPrimitive(uuid(501).toString()), full[Rpc.P_EVENT])
    }

    @Test fun joinPotWritesAnExactAmountAndUppercaseCurrency() {
        val json: JsonObject = ItemPayloads.joinPotParams(uuid(700), BigDecimal("12.50"), "chf")
        assertEquals("12.50", (json[Rpc.P_AMOUNT] as JsonPrimitive).content)
        assertFalse((json[Rpc.P_AMOUNT] as JsonPrimitive).isString)
        assertEquals(JsonPrimitive("CHF"), json[Rpc.P_CURRENCY])
        assertEquals(setOf(Rpc.P_ITEM, Rpc.P_AMOUNT, Rpc.P_CURRENCY), json.keys)
    }

    @Test fun purchasedAndReviewParams() {
        assertEquals(JsonPrimitive(true), ItemPayloads.purchasedParams(uuid(700), true)[Rpc.P_PURCHASED])
        val review = ItemPayloads.reviewIdeaParams(uuid(700), IdeaReview.REJECTED, null)
        assertEquals(JsonPrimitive("rejected"), review[Rpc.P_DECISION])
        assertEquals(JsonNull, review[Rpc.P_NOTE])
        assertEquals(JsonPrimitive("trop cher"), ItemPayloads.reviewIdeaParams(uuid(700), IdeaReview.REJECTED, "trop cher")[Rpc.P_NOTE])
        assertEquals(setOf(Rpc.P_CHILD), ItemPayloads.audienceParams(child).keys)
        assertEquals(setOf(Rpc.P_ITEM), ItemPayloads.itemParams(uuid(700)).keys)
    }

    // region Notifications et images

    @Test fun notificationBodiesMatchTheEdgeFunction() {
        val id = uuid(700)
        assertEquals("""{"item_ids":["$id"]}""", SupabaseItemNotifier.newItemsBody(listOf(id)).toString())
        assertEquals("""{"type":"reservation","item_id":"$id"}""", SupabaseItemNotifier.reservationBody(id).toString())
        assertEquals(
            """{"type":"idea_review","item_id":"$id","decision":"owned_already"}""",
            SupabaseItemNotifier.ideaReviewBody(id, IdeaReview.OWNED_ALREADY).toString(),
        )
        assertEquals("notify-new-items", SupabaseItemNotifier.FUNCTION)
    }

    @Test fun edgeFunctionStillReadsTheseFields() {
        val source = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "supabase/functions/notify-new-items/index.ts") }.first { it.isFile }.readText()
        listOf("item_ids", "payload.type === \"reservation\"", "payload.type === \"idea_review\"", "payload.item_id", "payload.decision")
            .forEach { assertTrue(it, it in source) }
    }

    @Test fun imagePathIsLowercaseUnderTheUsersFolder() {
        val user = UUID.fromString("ABCDEF00-0000-4000-8000-0000000000AB")
        val image = UUID.fromString("12345678-ABCD-4000-8000-0000000000CD")
        assertEquals(
            "abcdef00-0000-4000-8000-0000000000ab/12345678-abcd-4000-8000-0000000000cd.jpg",
            SupabaseImageUploader.path(user, image),
        )
        assertEquals("images", SupabaseImageUploader.BUCKET)
    }

    @Test fun repositoryConstants() {
        assertEquals(100, SupabaseItemRepository.LINKS_CHUNK)
        assertEquals("id, child_id, event_id", SupabaseItemRepository.LOCATOR_COLUMNS)
    }

    // endregion
}
