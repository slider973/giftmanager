package ch.jonathanlemaine.giftmanager.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class NotificationRouterTest {
    private val item = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val event = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val child = UUID.fromString("33333333-3333-4333-8333-333333333333")

    @Test fun priceOpensTheGift() {
        assertEquals(NotificationTarget.GiftDetail(item), NotificationRouter.parse(mapOf("type" to "price", "item_id" to item.toString())))
    }

    @Test fun purchaseOpensMyPurchases() {
        assertEquals(NotificationTarget.MyPurchases, NotificationRouter.parse(mapOf("type" to "purchase", "event_id" to event.toString())))
    }

    @Test fun birthdayOpensTheEvent() {
        assertEquals(NotificationTarget.Event(event), NotificationRouter.parse(mapOf("type" to "birthday", "event_id" to event.toString())))
    }

    @Test fun newItemsOpensTheChildListOrHome() {
        assertEquals(NotificationTarget.ChildList(child), NotificationRouter.parse(mapOf("type" to "new-items", "child_id" to child.toString())))
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "new-items")))
    }

    @Test fun thanksOpensTheThanksInbox() {
        assertEquals(NotificationTarget.ThanksInbox, NotificationRouter.parse(mapOf("type" to "thanks", "item_id" to item.toString())))
        assertEquals(NotificationTarget.ThanksInbox, NotificationRouter.parse(mapOf("type" to "thanks")))
    }

    @Test fun ideaReviewOpensTheGiftOrHome() {
        assertEquals(NotificationTarget.GiftDetail(item), NotificationRouter.parse(mapOf("type" to "idea-review", "item_id" to item.toString())))
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "idea-review")))
    }

    @Test fun reservationIsAnonymousAndOpensHome() {
        // Même si un item_id traînait dans le message, la réservation n'ouvre jamais le cadeau.
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "reservation", "item_id" to item.toString())))
    }

    @Test fun invalidUuidFallsBackToHome() {
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "price", "item_id" to "pas-un-uuid")))
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "birthday", "event_id" to "")))
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "new-items", "child_id" to "42")))
    }

    @Test fun unknownOrMissingTypeFallsBackToHome() {
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(mapOf("type" to "inconnu", "item_id" to item.toString())))
        assertEquals(NotificationTarget.Home, NotificationRouter.parse(emptyMap()))
    }

    @Test fun extrasRoundTrip() {
        listOf(
            NotificationTarget.GiftDetail(item), NotificationTarget.MyPurchases, NotificationTarget.ThanksInbox,
            NotificationTarget.Event(event), NotificationTarget.ChildList(child), NotificationTarget.Home,
        ).forEach { assertEquals(it, NotificationRouter.fromExtras(it.toExtras())) }
    }

    @Test fun extrasWithoutTargetAreNotANotification() {
        assertNull(NotificationRouter.fromExtras(emptyMap()))
        assertNull(NotificationRouter.fromExtras(mapOf("autre" to "x")))
    }

    @Test fun corruptedExtrasFallBackToHome() {
        assertEquals(NotificationTarget.Home, NotificationRouter.fromExtras(mapOf(NotificationRouter.EXTRA_TARGET to "gift")))
        assertEquals(
            NotificationTarget.Home,
            NotificationRouter.fromExtras(mapOf(NotificationRouter.EXTRA_TARGET to "event", NotificationRouter.EXTRA_ID to "nope")),
        )
        assertEquals(NotificationTarget.Home, NotificationRouter.fromExtras(mapOf(NotificationRouter.EXTRA_TARGET to "futur")))
    }
}
