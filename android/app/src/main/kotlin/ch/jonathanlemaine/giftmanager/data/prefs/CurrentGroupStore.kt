package ch.jonathanlemaine.giftmanager.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Famille consultée, mémorisée entre deux lancements (iOS : `UserDefaults` « currentGroupId »). */
interface CurrentGroupStore {
    suspend fun get(): UUID?

    suspend fun set(groupId: UUID)
}

class DataStoreCurrentGroupStore(private val dataStore: DataStore<Preferences>) : CurrentGroupStore {
    override suspend fun get(): UUID? =
        dataStore.data.first()[KEY]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    override suspend fun set(groupId: UUID) {
        dataStore.edit { it[KEY] = groupId.toString() }
    }

    companion object {
        val KEY = stringPreferencesKey("current_group_id")
    }
}
