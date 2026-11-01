package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.Fixtures
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEncodingTest {
    private fun encoded(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test fun childEncodesOptionalsAsExplicitNull() {
        val child = Child(UUID.randomUUID(), UUID.randomUUID(), "Mamie", birthdate = null, isAdult = true)
        val obj = encoded(GiftJson.write.encodeToString(child))
        assertEquals(JsonNull, obj["birthdate"])
        assertEquals(JsonNull, obj["avatar_emoji"])
        assertEquals(JsonNull, obj["avatar_color"])
        assertEquals(JsonNull, obj["avatar_url"])
        assertTrue(obj["is_adult"]!!.jsonPrimitive.boolean)
        assertEquals("Mamie", obj["first_name"]!!.jsonPrimitive.content)
    }

    @Test fun childRoundTrip() {
        val child = Fixtures.decode<Child>("child.json")
        assertEquals(child, GiftJson.read.decodeFromString<Child>(GiftJson.write.encodeToString(child)))
    }

    @Test fun giftEventUpsertHasNullChildButNeverCreatedBy() {
        val event = Fixtures.decode<GiftEvent>("gift_event_christmas_timestamp.json")
        val obj = encoded(GiftJson.write.encodeToString(event.toUpsert()))
        assertEquals(JsonNull, obj["child_id"])
        assertFalse("created_by" in obj)
        assertEquals("christmas", obj["kind"]!!.jsonPrimitive.content)
        assertEquals("2026-12-25", obj["event_date"]!!.jsonPrimitive.content)
    }

    @Test fun giftEventUpsertEncodesNullGroupExplicitlyAndDropsCreatedBy() {
        val event = Fixtures.decode<GiftEvent>("gift_event.json")
        val obj = encoded(GiftJson.write.encodeToString(event.toUpsert()))
        assertEquals(JsonNull, obj["group_id"])
        assertFalse("created_by" in obj)
        assertEquals("birthday", obj["kind"]!!.jsonPrimitive.content)
    }

    @Test fun dayDateRoundTrip() {
        val day = DayDate(LocalDate.of(2026, 2, 9))
        val text = GiftJson.write.encodeToString(day)
        assertEquals("\"2026-02-09\"", text)
        assertEquals(day, GiftJson.read.decodeFromString<DayDate>(text))
    }

    @Test fun enumsEncodeToSqlValues() {
        assertEquals("\"owned_already\"", GiftJson.write.encodeToString(IdeaReview.OWNED_ALREADY))
        assertEquals("\"pot\"", GiftJson.write.encodeToString(ItemStatus.POT))
        assertEquals("\"purchased\"", GiftJson.write.encodeToString(ReservationState.PURCHASED))
    }

    @Test fun decimalEncodesAsJsonNumber() {
        val participant = PotParticipant("Mamie", BigDecimal("12.50"), "CHF", false)
        val amount = encoded(GiftJson.write.encodeToString(participant))["amount"]!!.jsonPrimitive
        assertEquals("12.50", amount.content)
        assertFalse(amount.isString)
    }
}
