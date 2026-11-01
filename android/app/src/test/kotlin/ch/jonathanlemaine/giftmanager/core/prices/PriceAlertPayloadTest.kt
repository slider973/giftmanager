package ch.jonathanlemaine.giftmanager.core.prices

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceAlertPayloadTest {
    @Test fun readsTheItemId() {
        val item = UUID.randomUUID()
        assertEquals(item, PriceAlertPayload.itemId(mapOf("item_id" to item.toString(), "kind" to "price_drop")))
    }

    @Test fun missingKeyGivesNull() {
        assertNull(PriceAlertPayload.itemId(emptyMap()))
        assertNull(PriceAlertPayload.itemId(mapOf("link_id" to UUID.randomUUID().toString())))
    }

    @Test fun invalidValueGivesNullWithoutThrowing() {
        assertNull(PriceAlertPayload.itemId(mapOf("item_id" to "pas-un-uuid")))
        assertNull(PriceAlertPayload.itemId(mapOf("item_id" to "")))
    }
}
