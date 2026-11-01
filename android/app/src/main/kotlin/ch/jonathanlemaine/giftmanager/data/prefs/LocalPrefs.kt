package ch.jonathanlemaine.giftmanager.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Préférences propres à l'appareil, sans équivalent côté serveur. */
interface LocalPrefs {
    /**
     * « Nouvelles envies dans la famille » (iOS : `NotificationService.Kind.familyNews`). Sans effet tant
     * que les notifications push ne sont pas branchées (#76).
     */
    val notifyFamilyNews: Flow<Boolean>

    suspend fun setNotifyFamilyNews(enabled: Boolean)
}

class DataStoreLocalPrefs(private val dataStore: DataStore<Preferences>) : LocalPrefs {
    override val notifyFamilyNews: Flow<Boolean> = dataStore.data.map { it[FAMILY_NEWS] ?: true }

    override suspend fun setNotifyFamilyNews(enabled: Boolean) {
        dataStore.edit { it[FAMILY_NEWS] = enabled }
    }

    companion object {
        val FAMILY_NEWS = booleanPreferencesKey("notify_family_news")
    }
}
