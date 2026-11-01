package ch.jonathanlemaine.giftmanager.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    private fun config(googleId: String) = AppConfig("https://x.supabase.co", "sb_publishable_x", googleId)

    @Test fun realWebClientIdIsConfigured() {
        assertTrue(config("123-abc.apps.googleusercontent.com").isGoogleConfigured)
    }

    @Test fun blankIsNotConfigured() {
        assertFalse(config("").isGoogleConfigured)
        assertFalse(config("   ").isGoogleConfigured)
    }

    @Test fun dummyValuesAreNotConfigured() {
        assertFalse(config("dummy.apps.googleusercontent.com").isGoogleConfigured)
        assertFalse(config("ci-dummy.apps.googleusercontent.com").isGoogleConfigured)
        assertFalse(config("A_RENSEIGNER").isGoogleConfigured)
    }

    @Test fun appleSignInIsOffByDefaultAndOptIn() {
        assertFalse(AppConfig("https://x.supabase.co", "k").appleSignInEnabled)
        assertTrue(AppConfig("https://x.supabase.co", "k", appleSignInEnabled = true).appleSignInEnabled)
    }

    @Test fun defaultIsNotConfigured() {
        assertFalse(AppConfig("https://x.supabase.co", "k").isGoogleConfigured)
    }
}
