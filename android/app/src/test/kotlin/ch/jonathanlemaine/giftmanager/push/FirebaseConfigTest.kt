package ch.jonathanlemaine.giftmanager.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseConfigTest {
    private fun config(
        apiKey: String = "AIzaSyExample-key",
        appId: String = "1:123456789:android:abcdef",
        projectId: String = "giftmanager-prod",
        senderId: String = "123456789",
    ) = FirebaseConfig(apiKey, appId, projectId, senderId)

    @Test fun realValuesAreConfigured() {
        assertTrue(config().isConfigured)
    }

    @Test fun dummyDefaultsAreNotConfigured() {
        assertFalse(FirebaseConfig("dummy", "dummy", "dummy", "dummy").isConfigured)
    }

    @Test fun oneMissingValueDisablesPush() {
        assertFalse(config(apiKey = "").isConfigured)
        assertFalse(config(appId = "  ").isConfigured)
        assertFalse(config(projectId = "A_RENSEIGNER").isConfigured)
        assertFalse(config(senderId = "ci-dummy").isConfigured)
    }
}
