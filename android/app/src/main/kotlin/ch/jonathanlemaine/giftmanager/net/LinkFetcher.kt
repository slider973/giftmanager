package ch.jonathanlemaine.giftmanager.net

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Page HTML relue : son texte (borné) et l'URL finale après redirections. */
data class FetchedPage(val html: String, val finalUrl: String)

/** Relit une page web. `null` : refus, échec réseau ou contenu inexploitable (jamais d'exception). */
interface LinkFetcher {
    suspend fun fetch(url: String): FetchedPage?
}

/** Ce que l'app accepte de joindre. Les valeurs par défaut sont celles de la production. */
data class FetchPolicy(
    val allowedPorts: Set<Int> = setOf(80, 443),
    val isBlockedHost: (String) -> Boolean = SafeNet::isBlockedHostname,
)

/** Résolution DNS qui écarte les adresses privées : un nom public pointant vers le réseau interne échoue. */
class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val isPrivate: (String) -> Boolean = SafeNet::isPrivateIp,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { isPrivate(it.hostAddress.orEmpty()) }) throw UnknownHostException("private address")
        return addresses
    }
}

/**
 * Relecture HTTP protégée contre le SSRF (miroir de `safe_fetch.ts`) : redirections suivies à la main avec
 * revalidation de chaque saut, ports 80/443, DNS filtré, lecture bornée, HTML seulement.
 */
class OkHttpLinkFetcher(
    client: OkHttpClient = defaultClient(),
    private val policy: FetchPolicy = FetchPolicy(),
    private val maxBytes: Long = MAX_BYTES,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LinkFetcher {
    // Quelle que soit l'origine du client, le suivi des redirections reste manuel.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun fetch(url: String): FetchedPage? {
        val start = url.toHttpUrlOrNull() ?: return null
        return withContext(dispatcher) {
            // http:// est d'abord tenté en https:// ; le http d'origine n'est qu'un recours si le réseau échoue.
            val first = fetchFrom(start, upgrade = start.scheme == "http")
            val outcome = if (first == Attempt.Failed && start.scheme == "http") fetchFrom(start, upgrade = false) else first
            (outcome as? Attempt.Page)?.page
        }
    }

    private sealed interface Attempt {
        data class Page(val page: FetchedPage) : Attempt

        /** Refus de la politique, contenu inexploitable ou trop de redirections : inutile de réessayer. */
        data object Refused : Attempt

        /** Échec réseau. */
        data object Failed : Attempt
    }

    private suspend fun fetchFrom(start: HttpUrl, upgrade: Boolean): Attempt {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            val target = if (upgrade) upgraded(current) else current
            if (!isAllowed(target)) return Attempt.Refused
            try {
                client.newCall(request(target)).await().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location") ?: return Attempt.Refused
                        current = target.resolve(location) ?: return Attempt.Refused
                    } else {
                        return read(response, target)?.let { Attempt.Page(it) } ?: Attempt.Refused
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return Attempt.Failed
            }
        }
        return Attempt.Refused // trop de redirections
    }

    private fun upgraded(url: HttpUrl): HttpUrl =
        if (url.scheme == "http" && url.port == 80) url.newBuilder().scheme("https").port(443).build() else url

    private fun isAllowed(url: HttpUrl): Boolean =
        (url.scheme == "http" || url.scheme == "https") &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.port in policy.allowedPorts &&
            !policy.isBlockedHost(url.host)

    private fun request(url: HttpUrl): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "fr-CH,fr;q=0.9,de;q=0.7,en;q=0.5")
        amazonCurrency(url.host)?.let { builder.header("Cookie", "i18n-prefs=$it") }
        return builder.build()
    }

    private fun read(response: Response, url: HttpUrl): FetchedPage? {
        if (!response.isSuccessful) return null
        val body = response.body
        if (!body.contentType().toString().lowercase(Locale.ROOT).contains("html")) return null
        if (body.contentLength() > maxBytes) return null
        val source = body.source()
        val buffer = okio.Buffer()
        while (buffer.size < maxBytes) {
            if (source.read(buffer, minOf(8192L, maxBytes - buffer.size)) == -1L) break
        }
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return FetchedPage(buffer.readString(charset), url.toString())
    }

    companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 1_500_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"

        /** Devise d'origine des sites Amazon : sans elle, Amazon convertit dans la devise du visiteur. */
        private val amazonCurrencies = mapOf(
            "amazon.fr" to "EUR", "amazon.de" to "EUR", "amazon.it" to "EUR", "amazon.es" to "EUR",
            "amazon.com.be" to "EUR", "amazon.nl" to "EUR", "amazon.co.uk" to "GBP",
            "amazon.com" to "USD", "amazon.ca" to "CAD",
        )

        internal fun amazonCurrency(host: String): String? =
            amazonCurrencies[host.lowercase(Locale.ROOT).removePrefix("www.")]

        fun defaultClient(base: OkHttpClient = OkHttpClient()): OkHttpClient = base.newBuilder()
            .dns(PublicOnlyDns())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

/** Attend les en-têtes de la réponse sans bloquer ; l'annulation de la coroutine annule l'appel. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        },
    )
}
