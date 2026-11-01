package ch.jonathanlemaine.giftmanager.ui.onboarding

import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPagesTest {
    @Test fun fourPagesInIosOrder() {
        assertEquals(
            listOf(Illustration.MASCOT_FAMILY, Illustration.ILLUSTRATION_TRAVEL, Illustration.MASCOT_GIFT, Illustration.MASCOT_SURPRISE),
            OnboardingPages.all.map { it.illustration },
        )
    }

    @Test fun lastPageIsTheSignInPage() {
        assertEquals(R.string.sign_in_title, OnboardingPages.all.last().title)
        assertEquals(R.string.sign_in_message, OnboardingPages.all.last().message)
    }

    @Test fun nextIsClampedAndLastIsDetected() {
        assertFalse(OnboardingPages.isLast(0))
        assertEquals(1, OnboardingPages.next(0))
        assertTrue(OnboardingPages.isLast(3))
        assertEquals(3, OnboardingPages.next(3))
    }
}
