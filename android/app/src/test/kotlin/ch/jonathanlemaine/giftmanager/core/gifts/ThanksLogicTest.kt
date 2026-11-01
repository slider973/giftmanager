package ch.jonathanlemaine.giftmanager.core.gifts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThanksLogicTest {
    @Test fun defaultMessageQuotesTheTitle() {
        assertEquals("Merci beaucoup pour « Lego » !", ThanksLogic.defaultMessage("Lego"))
    }

    @Test fun lengthIgnoresSurroundingWhitespace() {
        assertEquals(2, ThanksLogic.length("  Hi \n"))
        assertEquals(0, ThanksLogic.length(" \n "))
    }

    @Test fun anEmojiCountsForOne() {
        assertEquals(1, ThanksLogic.length("🎁"))
        assertEquals(3, ThanksLogic.length("a🎁b"))
    }

    @Test fun limitIsInCodePointsNotUtf16Units() {
        // 1000 émojis = 2000 unités UTF-16, mais 1000 points de code : accepté.
        assertTrue(ThanksLogic.canSend("🎁".repeat(ThanksLogic.MAX_LENGTH), isSending = false))
        assertFalse(ThanksLogic.canSend("🎁".repeat(ThanksLogic.MAX_LENGTH + 1), isSending = false))
        assertTrue(ThanksLogic.isTooLong("a".repeat(1001)))
        assertFalse(ThanksLogic.isTooLong("a".repeat(1000)))
    }

    @Test fun cannotSendEmptyOrWhileSending() {
        assertFalse(ThanksLogic.canSend("   ", isSending = false))
        assertFalse(ThanksLogic.canSend("Merci", isSending = true))
        assertTrue(ThanksLogic.canSend("Merci", isSending = false))
    }

    @Test fun donorNames() {
        assertEquals("", ThanksLogic.donorNames(emptyList()))
        assertEquals("Mamie", ThanksLogic.donorNames(listOf("Mamie")))
        assertEquals("Mamie et Paul", ThanksLogic.donorNames(listOf("Mamie", "Paul")))
        assertEquals("Mamie, Paul et Léa", ThanksLogic.donorNames(listOf("Mamie", "Paul", "Léa")))
    }
}
