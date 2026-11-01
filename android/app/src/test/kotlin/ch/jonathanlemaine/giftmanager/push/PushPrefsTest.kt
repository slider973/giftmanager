package ch.jonathanlemaine.giftmanager.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class PushPrefsTest {
    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File.createTempFile("push", ".preferences_pb").also { it.delete() } }

    @Test fun registeredTokenRoundTripsAndClears() = runTest {
        val prefs = DataStorePushPrefs(store())
        assertNull(prefs.registeredToken())
        val entry = RegisteredToken(UUID.randomUUID(), "token-a")
        prefs.setRegisteredToken(entry)
        assertEquals(entry, prefs.registeredToken())
        prefs.setRegisteredToken(null)
        assertNull(prefs.registeredToken())
    }

    @Test fun corruptedUserIsIgnored() = runTest {
        val ds = store()
        ds.updateData { it.toMutablePreferences().apply { set(DataStorePushPrefs.USER, "pas-un-uuid"); set(DataStorePushPrefs.TOKEN, "t") } }
        assertNull(DataStorePushPrefs(ds).registeredToken())
    }

    @Test fun flagsDefaultToFalseAndPersist() = runTest {
        val prefs = DataStorePushPrefs(store())
        assertFalse(prefs.permissionAsked())
        assertFalse(prefs.promptShown())
        prefs.setPermissionAsked()
        prefs.setPromptShown()
        assertTrue(prefs.permissionAsked())
        assertTrue(prefs.promptShown())
    }
}
