package ch.jonathanlemaine.giftmanager.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreCurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreLocalPrefs
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {
    private fun kotlinx.coroutines.test.TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File.createTempFile("prefs", ".preferences_pb").also { it.delete() } }

    @Test fun currentGroupRoundTrips() = runTest {
        val store = DataStoreCurrentGroupStore(store())
        assertNull(store.get())
        val id = UUID.randomUUID()
        store.set(id)
        assertEquals(id, store.get())
    }

    @Test fun currentGroupKeyName() {
        assertEquals("current_group_id", DataStoreCurrentGroupStore.KEY.name)
    }

    @Test fun corruptedValueIsIgnored() = runTest {
        val ds = store()
        ds.updateData { it.toMutablePreferences().apply { set(DataStoreCurrentGroupStore.KEY, "pas-un-uuid") } }
        assertNull(DataStoreCurrentGroupStore(ds).get())
    }

    @Test fun familyNewsDefaultsToTrueAndPersists() = runTest {
        val prefs = DataStoreLocalPrefs(store())
        assertTrue(prefs.notifyFamilyNews.first())
        prefs.setNotifyFamilyNews(false)
        assertFalse(prefs.notifyFamilyNews.first())
        assertEquals("notify_family_news", DataStoreLocalPrefs.FAMILY_NEWS.name)
    }
}
