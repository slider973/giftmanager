package ch.jonathanlemaine.giftmanager.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Dernier taux connu, conservé sur l'appareil (`ExchangeRateCache` iOS). Seuls des taux publics y figurent. */
interface ExchangeRateCache {
    suspend fun load(): ExchangeRates?

    suspend fun save(rates: ExchangeRates)
}

class DataStoreExchangeRateCache(private val dataStore: DataStore<Preferences>) : ExchangeRateCache {
    /** JSON corrompu ou d'un format inconnu : traité comme un cache vide. */
    override suspend fun load(): ExchangeRates? {
        val raw = dataStore.data.first()[KEY] ?: return null
        return try {
            GiftJson.read.decodeFromString<ExchangeRates>(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(rates: ExchangeRates) {
        val raw = GiftJson.write.encodeToString(rates)
        dataStore.edit { it[KEY] = raw }
    }

    companion object {
        val KEY = stringPreferencesKey("exchange_rates_v1")
    }
}
