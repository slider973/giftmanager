package ch.jonathanlemaine.giftmanager.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NotificationPayloadParserTest {
    private val item = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val event = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val child = UUID.fromString("33333333-3333-4333-8333-333333333333")

    private fun data(type: String?, vararg extra: Pair<String, String>) =
        buildMap {
            if (type != null) put("type", type)
            put("title", "Titre")
            put("body", "Texte")
            putAll(extra)
        }

    @Test fun titleAndBodyAreRead() {
        val payload = NotificationPayloadParser.parse(data("price", "item_id" to item.toString()))!!
        assertEquals("Titre", payload.title)
        assertEquals("Texte", payload.body)
        assertEquals(NotificationTarget.GiftDetail(item), payload.target)
    }

    @Test fun channelFollowsTheType() {
        fun channel(type: String) = NotificationPayloadParser.parse(data(type))!!.channel
        assertEquals(PushChannel.Purchases, channel("purchase"))
        assertEquals(PushChannel.Purchases, channel("price"))
        assertEquals(PushChannel.Birthdays, channel("birthday"))
        assertEquals(PushChannel.Family, channel("new-items"))
        assertEquals(PushChannel.Family, channel("idea-review"))
        assertEquals(PushChannel.Family, channel("thanks"))
        assertEquals(PushChannel.Family, channel("reservation"))
    }

    @Test fun channelIdsAreStable() {
        assertEquals(listOf("purchases", "birthdays", "family"), PushChannel.entries.map { it.id })
    }

    @Test fun unknownTypeStillShowsOnTheFamilyChannel() {
        val payload = NotificationPayloadParser.parse(data("futur"))!!
        assertNull(payload.type)
        assertEquals(PushChannel.Family, payload.channel)
        assertEquals(NotificationTarget.Home, payload.target)
        assertEquals(PushChannel.Family, NotificationPayloadParser.parse(data(null))!!.channel)
    }

    @Test fun messageWithoutTextIsIgnored() {
        assertNull(NotificationPayloadParser.parse(mapOf("type" to "price")))
        assertNull(NotificationPayloadParser.parse(mapOf("type" to "price", "title" to " ", "body" to "")))
    }

    @Test fun titleAloneOrBodyAloneIsEnough() {
        assertEquals("", NotificationPayloadParser.parse(mapOf("type" to "birthday", "title" to "Anniversaire"))!!.body)
        assertEquals("", NotificationPayloadParser.parse(mapOf("type" to "birthday", "body" to "Bientôt"))!!.title)
    }

    @Test fun keyGroupsNotificationsOfTheSameSubject() {
        val first = NotificationPayloadParser.parse(data("birthday", "event_id" to event.toString()))!!
        val again = NotificationPayloadParser.parse(data("birthday", "event_id" to event.toString()))!!
        val other = NotificationPayloadParser.parse(data("birthday", "event_id" to UUID.randomUUID().toString()))!!
        assertEquals("birthday-$event", first.key)
        assertEquals(first.key, again.key)
        assertNotEquals(first.key, other.key)
        assertEquals("new-items-$child", NotificationPayloadParser.parse(data("new-items", "child_id" to child.toString()))!!.key)
        assertEquals("reservation", NotificationPayloadParser.parse(data("reservation"))!!.key)
    }

    @Test fun onlyNewItemsDependOnTheFamilyNewsPreference() {
        PushType.entries.forEach { type ->
            val payload = NotificationPayloadParser.parse(data(type.wire))!!
            assertTrue(type.wire, NotificationPayloadParser.shouldDisplay(payload, familyNewsEnabled = true))
            assertEquals(type.wire, type != PushType.NewItems, NotificationPayloadParser.shouldDisplay(payload, familyNewsEnabled = false))
        }
        assertTrue(NotificationPayloadParser.shouldDisplay(NotificationPayloadParser.parse(data("futur"))!!, familyNewsEnabled = false))
        assertFalse(NotificationPayloadParser.shouldDisplay(NotificationPayloadParser.parse(data("new-items"))!!, familyNewsEnabled = false))
    }
}
