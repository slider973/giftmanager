package ch.jonathanlemaine.giftmanager.net

import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Relecture protégée : le serveur factice écoute en local, la politique de test l'autorise explicitement. */
class LinkFetcherTest {
    private lateinit var server: MockWebServer
    private val html = "<html><head><title>Produit</title></head></html>"

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    private val relaxed = FetchPolicy(allowedPorts = emptySet(), isBlockedHost = { false })

    private fun fetcher(policy: FetchPolicy = relaxed, maxBytes: Long = OkHttpLinkFetcher.MAX_BYTES) =
        OkHttpLinkFetcher(OkHttpClient(), policy, maxBytes)

    private fun url(path: String = "/") = server.url(path).toString()

    private fun policyFor(vararg ports: Int) = FetchPolicy(ports.toSet()) { false }

    private fun htmlResponse(body: String = html) =
        MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    @Test fun fetchesHtmlWithHeaders() = runTest {
        server.enqueue(htmlResponse())
        val page = fetcher(policyFor(server.port)).fetch(url("/p"))
        assertEquals(html, page?.html)
        val request = server.takeRequest()
        assertTrue(request.headers["User-Agent"]!!.contains("Mobile"))
        assertEquals("fr-CH,fr;q=0.9,de;q=0.7,en;q=0.5", request.headers["Accept-Language"])
        assertNull(request.headers["Cookie"])
    }

    @Test fun followsRedirectsManually() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/final").build())
        server.enqueue(htmlResponse())
        val page = fetcher(policyFor(server.port)).fetch(url("/start"))
        assertNotNull(page)
        assertTrue(page!!.finalUrl.endsWith("/final"))
        assertEquals(2, server.requestCount)
    }

    @Test fun stopsAfterFiveRedirects() = runTest {
        repeat(8) { server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/loop").build()) }
        assertNull(fetcher(policyFor(server.port)).fetch(url("/loop")))
        assertEquals(OkHttpLinkFetcher.MAX_REDIRECTS + 1, server.requestCount)
    }

    @Test fun redirectToPrivateIpIsRefused() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://169.254.169.254/latest").build())
        server.enqueue(htmlResponse())
        // Seul le port du serveur est permis ; la destination (nom bloqué, port 80) est refusée avant tout appel.
        val policy = FetchPolicy(setOf(server.port, 80)) { SafeNet.isBlockedHostname(it) && it != server.hostName }
        assertNull(fetcher(policy).fetch(url("/start")))
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectToForbiddenPortIsRefused() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://example.com:8080/x").build())
        assertNull(fetcher(policyFor(server.port)).fetch(url()))
        assertEquals(1, server.requestCount)
    }

    @Test fun productionPolicyRejectsLocalServer() = runTest {
        server.enqueue(htmlResponse())
        assertNull(OkHttpLinkFetcher(OkHttpClient()).fetch(url()))
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsCredentialsInUrl() = runTest {
        server.enqueue(htmlResponse())
        val withCredentials = url().replace("http://", "http://user:pw@")
        assertNull(fetcher(policyFor(server.port)).fetch(withCredentials))
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsNonHtmlContent() = runTest {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body("{}").build())
        assertNull(fetcher(policyFor(server.port)).fetch(url()))
    }

    @Test fun rejectsMissingContentType() = runTest {
        server.enqueue(MockResponse.Builder().body(html).build())
        assertNull(fetcher(policyFor(server.port)).fetch(url()))
    }

    @Test fun rejectsErrorStatus() = runTest {
        server.enqueue(MockResponse.Builder().code(404).addHeader("Content-Type", "text/html").body(html).build())
        assertNull(fetcher(policyFor(server.port)).fetch(url()))
    }

    @Test fun declaredOversizeIsRefused() = runTest {
        server.enqueue(htmlResponse("x".repeat(5000)))
        assertNull(fetcher(policyFor(server.port), maxBytes = 1000).fetch(url()))
    }

    @Test fun undeclaredOversizeIsTruncated() = runTest {
        val body = "a".repeat(5000)
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/html").chunkedBody(body, 512).build(),
        )
        val page = fetcher(policyFor(server.port), maxBytes = 1000).fetch(url())
        assertEquals(1000, page?.html?.length)
    }

    @Test fun amazonCurrencyCookieTable() {
        assertEquals("EUR", OkHttpLinkFetcher.amazonCurrency("www.amazon.fr"))
        assertEquals("GBP", OkHttpLinkFetcher.amazonCurrency("amazon.co.uk"))
        assertEquals("USD", OkHttpLinkFetcher.amazonCurrency("www.amazon.com"))
        assertEquals("CAD", OkHttpLinkFetcher.amazonCurrency("amazon.ca"))
        assertNull(OkHttpLinkFetcher.amazonCurrency("www.galaxus.ch"))
    }

    @Test fun publicOnlyDnsFiltersPrivateAddresses() {
        val private = Dns { listOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 5))) }
        val mixed = Dns {
            listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        }
        val public = Dns { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 4, 4))) }
        listOf(private, mixed).forEach {
            try {
                PublicOnlyDns(it).lookup("evil.example")
                throw AssertionError("adresse privée acceptée")
            } catch (_: UnknownHostException) {
            }
        }
        assertEquals(1, PublicOnlyDns(public).lookup("ok.example").size)
    }
}
