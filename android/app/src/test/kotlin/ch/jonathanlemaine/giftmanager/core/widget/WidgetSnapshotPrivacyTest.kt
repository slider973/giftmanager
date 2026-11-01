package ch.jonathanlemaine.giftmanager.core.widget

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le widget est visible de tous : rien d'autre que ces champs ne doit y figurer (pas de réservations, prix, identifiants). */
class WidgetSnapshotPrivacyTest {
    @Test fun serializedSnapshotOnlyHoldsPublicFields() {
        val snapshot = WidgetSnapshot(true, listOf(WidgetEvent(GiftEventKind.CHRISTMAS, "Noël", DayDate.parse("2026-12-25"))))
        val root = Json.parseToJsonElement(Json.encodeToString(WidgetSnapshot.serializer(), snapshot)).jsonObject
        assertEquals(setOf("signedIn", "events"), root.keys)
        val event = root.getValue("events").jsonArray.single().jsonObject
        assertEquals(setOf("kind", "title", "date"), event.keys)
        assertEquals("christmas", event.getValue("kind").jsonPrimitive.content)
        assertEquals("2026-12-25", event.getValue("date").jsonPrimitive.content)
    }

    @Test fun emptySnapshotStillOnlyHoldsPublicFields() {
        val json = Json { encodeDefaults = true }.encodeToString(WidgetSnapshot.serializer(), WidgetSnapshot())
        assertEquals("""{"signedIn":false,"events":[]}""", json)
    }
}
