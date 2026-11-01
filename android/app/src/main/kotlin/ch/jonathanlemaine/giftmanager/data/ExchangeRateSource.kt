package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.net.await
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** D'où viennent les taux de change (`ExchangeRateSource` iOS). Lève une exception en cas d'échec. */
interface ExchangeRateSource {
    suspend fun latest(): ExchangeRates
}

/** Taux de référence de la BCE, via Frankfurter (gratuit, sans clé, publiés chaque jour ouvré). */
class FrankfurterRateSource(
    client: OkHttpClient,
    private val url: String = DEFAULT_URL,
    private val clock: Clock = Clock.systemUTC(),
) : ExchangeRateSource {
    private val client = client.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun latest(): ExchangeRates = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parse(response.body.string(), Instant.now(clock))
        }
    }

    companion object {
        const val DEFAULT_URL = "https://api.frankfurter.dev/v1/latest?base=EUR"
        const val TIMEOUT_SECONDS = 10L

        /**
         * Les taux sont lus tels qu'écrits (« 0.9279 » reste 0.9279, jamais un flottant). Codes en majuscules ;
         * un taux nul, négatif ou illisible est ignoré ; une table vide est refusée (rien à convertir).
         */
        fun parse(body: String, fetchedAt: Instant): ExchangeRates {
            val root = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Taux illisibles", e)
            }
            val base = root.text("base") ?: throw SerializationException("Devise de base absente")
            val date = root.text("date") ?: throw SerializationException("Date des taux absente")
            val table = root["rates"] as? JsonObject ?: throw SerializationException("Table des taux absente")
            val rates = buildMap {
                for ((code, value) in table) {
                    val rate = (value as? JsonPrimitive)?.content?.toBigDecimalOrNull() ?: continue
                    if (rate.signum() > 0) put(code.uppercase(), rate)
                }
            }
            if (rates.isEmpty()) throw SerializationException("Aucun taux")
            return ExchangeRates(base.uppercase(), date, rates, fetchedAt)
        }

        private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }
}
