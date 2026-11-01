package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.Fixtures
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDecodingTest {
    private val childId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val itemId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private inline fun <reified T> decode(json: String): T = GiftJson.read.decodeFromString<T>(json)

    private fun assertDecimal(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    // region Profil, foyer

    @Test fun profileDecodesAllFieldsAndIgnoresUnknownOnes() {
        val profile = Fixtures.decode<Profile>("profile.json")
        assertEquals("Jonathan", profile.displayName)
        assertEquals("CHF", profile.currency)
        assertTrue(profile.onboarded)
        assertFalse(profile.notifyBirthdayReminders)
        assertTrue(profile.notifyPurchaseReminders)
    }

    @Test fun profileDefaultsReminderFlagsToTrueWhenAbsentOrNull() {
        val profile = Fixtures.decode<Profile>("profile_legacy.json")
        assertTrue(profile.notifyBirthdayReminders)
        assertTrue(profile.notifyPurchaseReminders)
        assertFalse(profile.onboarded)
    }

    @Test fun householdDecoding() {
        val household = Fixtures.decode<Household>("household.json")
        assertEquals("Les Lemaine", household.name)
        assertEquals("H0USE123", household.inviteCode)
        assertNull(decode<Household>("""{"id":"$itemId","name":"A","country":"FR"}""").inviteCode)
    }

    @Test fun membershipRowsDecode() {
        val hg = decode<HouseholdGroup>("""{"household_id":"$childId","group_id":"$itemId"}""")
        assertEquals(childId, hg.householdId)
        assertEquals(itemId, hg.groupId)
        assertEquals(itemId, decode<HouseholdMember>("""{"household_id":"$childId","user_id":"$itemId"}""").userId)
        assertEquals(itemId, decode<GroupMember>("""{"group_id":"$childId","user_id":"$itemId"}""").userId)
        val group = decode<FamilyGroup>("""{"id":"$childId","name":"Famille","invite_code":"ABC","created_by":null}""")
        assertNull(group.createdBy)
    }

    // endregion

    // region Enfants

    @Test fun childDecodesEveryField() {
        val child = Fixtures.decode<Child>("child.json")
        assertEquals(childId, child.id)
        assertEquals("Emma", child.firstName)
        assertEquals(DayDate(LocalDate.of(2018, 3, 12)), child.birthdate)
        assertEquals("🦄", child.avatarEmoji)
        assertEquals("pastelPink", child.avatarColor)
        assertFalse(child.isAdult)
    }

    @Test fun minimalChildDefaultsToChildWithoutAvatar() {
        val child = Fixtures.decode<Child>("child_minimal.json")
        assertFalse(child.isAdult)
        assertNull(child.avatarEmoji)
        assertNull(child.avatarColor)
        assertNull(child.avatarUrl)
    }

    @Test fun adultChildWithExplicitNulls() {
        val adult = Fixtures.decode<Child>("child_adult.json")
        assertTrue(adult.isAdult)
        assertNull(adult.birthdate)
        assertNull(adult.age(Fixtures.clock))
    }

    // endregion

    // region Événements

    @Test fun giftEventDecoding() {
        val event = Fixtures.decode<GiftEvent>("gift_event.json")
        assertEquals(GiftEventKind.BIRTHDAY, event.kind)
        assertEquals(DayDate(LocalDate.of(2026, 12, 25)), event.eventDate)
        assertEquals(childId, event.childId)
        assertNull(event.groupId)
        assertEquals(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), event.createdBy)
    }

    @Test fun eventDateToleratesTimeSuffix() {
        val event = Fixtures.decode<GiftEvent>("gift_event_christmas_timestamp.json")
        assertEquals(GiftEventKind.CHRISTMAS, event.kind)
        assertEquals(DayDate(LocalDate.of(2026, 12, 25)), event.eventDate)
        assertNull(event.childId)
        assertNull(event.createdBy)
    }

    @Test fun invalidDateFails() {
        val json = Fixtures.load("gift_event.json").replace("2026-12-25", "pas-une-date")
        assertThrows(SerializationException::class.java) { decode<GiftEvent>(json) }
    }

    @Test fun unknownEnumValueFails() {
        val json = Fixtures.load("gift_event.json").replace("birthday", "inconnu")
        assertThrows(SerializationException::class.java) { decode<GiftEvent>(json) }
    }

    // endregion

    // region Cadeaux

    @Test fun potWishItemDecodesDecimalsAsNumbers() {
        val item = Fixtures.decode<WishItem>("wish_item_pot.json")
        assertEquals(ItemStatus.POT, item.status)
        assertDecimal("120.5", item.potTotal)
        assertEquals("CHF", item.potCurrency)
        assertEquals(3, item.potCount)
        assertDecimal("40", item.myContribution)
        assertTrue(item.isInMyPot)
        assertEquals(WishKind.WISH, item.kind)
        assertEquals(IdeaReview.NONE, item.reviewStatus)
    }

    @Test fun legacyWishItemFallsBackToDefaults() {
        val item = Fixtures.decode<WishItem>("wish_item_legacy.json")
        assertEquals(WishKind.IDEA, item.kind)
        assertEquals(0, item.priority)
        assertEquals(0, item.position)
        assertFalse(item.owned)
        assertNull(item.status)
        assertNull(item.potTotal)
        assertFalse(item.isInMyPot)
        assertFalse(item.isFavorite)
        assertEquals(IdeaReview.NONE, item.reviewStatus)
    }

    @Test fun parentWishItemWithNullsAndReview() {
        val item = Fixtures.decode<WishItem>("wish_item_parent.json")
        assertNull(item.status)
        assertEquals(ReservationState.PURCHASED, item.myReservation)
        assertNull(item.potTotal)
        assertNull(item.potCount)
        assertEquals(IdeaReview.OWNED_ALREADY, item.reviewStatus)
        assertEquals("Déjà reçu à Noël", item.reviewNote)
        assertTrue(item.isFavorite)
        assertTrue(item.owned)
    }

    @Test fun nullReviewStatusFallsBackToNone() {
        val json = Fixtures.load("wish_item_legacy.json").replace("\"kind\"", "\"review_status\": null, \"kind\"")
        assertEquals(IdeaReview.NONE, decode<WishItem>(json).reviewStatus)
    }

    @Test fun parentNeverGetsPotProgress() {
        val item = Fixtures.decode<WishItem>("wish_item_parent.json")
        assertNull(PotProgress.make(item, emptyList()))
    }

    @Test fun ideaReviewLabelsAndSubmission() {
        assertFalse(IdeaReview.NONE.isSubmitted)
        assertTrue(IdeaReview.PENDING.isSubmitted)
        assertEquals("Déjà possédé", IdeaReview.OWNED_ALREADY.label)
        assertEquals("Surprise totale", IdeaReview.NONE.label)
    }

    @Test fun pendingIdeaDecodesTimestampWithMicroseconds() {
        val idea = Fixtures.decode<PendingIdea>("pending_idea.json")
        assertEquals("Lego", idea.title)
        assertEquals(Instant.parse("2026-10-03T08:30:15.123456Z"), idea.createdAt)
        // Auteur vide : libellé de repli.
        assertEquals("Quelqu'un de la famille", idea.author)
    }

    @Test fun pendingIdeaAuthorWhenKnown() {
        val json = Fixtures.load("pending_idea.json").replace("\"author_name\": \"\"", "\"author_name\": \"Mamie\"")
        assertEquals("Mamie", decode<PendingIdea>(json).author)
    }

    @Test fun itemLinkAcceptsPriceAsString() {
        val link = Fixtures.decode<ItemLink>("item_link.json")
        assertDecimal("230", link.price)
        assertEquals("EUR", link.currency)
        assertEquals("Magasin", link.store)
    }

    @Test fun itemLinkAcceptsPriceAsNumberOrNull() {
        val base = Fixtures.load("item_link.json")
        assertDecimal("19.9", decode<ItemLink>(base.replace("\"230.00\"", "19.9")).price)
        assertNull(decode<ItemLink>(base.replace("\"230.00\"", "null")).price)
    }

    @Test fun priceHistoryDecoding() {
        val checks = Fixtures.decode<List<PriceCheck>>("price_history.json")
        assertEquals(2, checks.size)
        assertDecimal("169.00", checks[0].price)
        assertEquals("CHF", checks[0].currency)
        assertEquals(true, checks[0].inStock)
        assertNull(checks[1].price)
        assertNull(checks[1].inStock)
        assertEquals(Instant.parse("2026-10-03T06:00:00Z"), checks[1].checkedAt)
    }

    @Test fun myReservationDecoding() {
        val reservation = Fixtures.decode<MyReservation>("my_reservation.json")
        assertEquals(ReservationState.RESERVED, reservation.status)
        assertEquals(itemId, reservation.id)
        assertEquals(DayDate(LocalDate.of(2026, 12, 25)), reservation.eventDate)
        assertEquals("Noël 2026", reservation.eventTitle)
    }

    // endregion

    // region Cagnotte, équilibre, remerciements

    @Test fun potParticipantsDecoding() {
        val rows = Fixtures.decode<List<PotParticipant>>("pot_participants.json")
        assertEquals(2, rows.size)
        assertDecimal("25.5", rows[1].amount)
        assertTrue(rows[1].isMe)
        assertFalse(rows[0].isMe)
    }

    @Test fun myContributionDecoding() {
        val row = Fixtures.decode<MyContribution>("my_contribution.json")
        assertDecimal("40", row.amount)
        assertEquals(3, row.potCount)
        assertDecimal("120", row.potTotal)
        assertEquals(DayDate(LocalDate.of(2026, 12, 25)), row.eventDate)
        assertNull(row.eventId)
    }

    @Test fun reservationCountsDecoding() {
        val rows = Fixtures.decode<List<ReservationCounts>>("reservation_counts.json")
        assertEquals(4, rows[0].total)
        assertEquals(0, rows[1].total)
    }

    @Test fun thanksNoteDecodingAndTimestamp() {
        val note = Fixtures.decode<ThanksNote>("thanks_note.json")
        assertEquals("Maman", note.senderName)
        assertTrue(note.revealed)
        assertNull(note.photoUrl)
        assertEquals(Instant.parse("2026-12-26T09:00:00.5Z"), note.createdDate)
    }

    @Test fun itemDonorDecoding() {
        assertEquals("Mamie", Fixtures.decode<ItemDonor>("item_donor.json").displayName)
    }

    // endregion

    // region Horodatages

    @Test fun timestampWithAndWithoutFractionalSeconds() {
        val expected = Instant.parse("2026-10-02T10:00:00Z")
        assertEquals(expected, Timestamp.parse("2026-10-02T10:00:00+00:00"))
        assertEquals(expected, Timestamp.parse("2026-10-02T10:00:00Z"))
        assertEquals(expected.plusMillis(120), Timestamp.parse("2026-10-02T10:00:00.12+00:00"))
        assertEquals(Instant.parse("2026-10-02T08:00:00Z"), Timestamp.parse("2026-10-02T10:00:00+02:00"))
    }

    @Test fun timestampDropsOverlongFractionsInsteadOfFailing() {
        assertEquals(
            Instant.parse("2026-10-02T10:00:00Z"),
            Timestamp.parse("2026-10-02T10:00:00.1234567890123+00:00"),
        )
    }

    @Test fun invalidTimestampIsNull() {
        assertNull(Timestamp.parse("hier"))
    }

    // endregion

    @Test fun budgetDecoding() {
        val budget = Fixtures.decode<ch.jonathanlemaine.giftmanager.core.budgets.Budget>("budget.json")
        assertEquals("Noël 2026", budget.scopeTitle)
        assertNull(budget.childId)
        assertDecimal("180.5", budget.spent)
        assertDecimal("200", budget.amount)
        assertEquals("CHF", budget.currency)
    }
}
