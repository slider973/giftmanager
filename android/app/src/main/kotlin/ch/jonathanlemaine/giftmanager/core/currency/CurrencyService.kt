package ch.jonathanlemaine.giftmanager.core.currency

import ch.jonathanlemaine.giftmanager.data.ExchangeRateSource
import ch.jonathanlemaine.giftmanager.data.prefs.ExchangeRateCache
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Taux courants pour toute l'app (`CurrencyService` iOS). Lit le cache une fois, puis rafraîchit au plus une fois
 * toutes les 6 heures ; en cas d'échec réseau, garde le dernier taux connu. Les échecs sont silencieux : une
 * conversion indicative manquante ne mérite pas d'alerte.
 *
 * Le rafraîchissement tourne sur [scope] : il n'est pas annulé quand l'appelant l'est (changement d'écran), et les
 * appels simultanés partagent la même requête.
 */
class CurrencyService(
    private val source: ExchangeRateSource,
    private val cache: ExchangeRateCache,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _rates = MutableStateFlow<ExchangeRates?>(null)
    val rates: StateFlow<ExchangeRates?> = _rates.asStateFlow()

    private val cacheLock = Mutex()
    private var cacheLoaded = false

    private val refreshLock = Any()
    private var inFlight: Deferred<Unit>? = null

    val isStale: Boolean
        get() {
            val current = _rates.value ?: return true
            return Duration.between(current.fetchedAt, Instant.now(clock)) > MAX_AGE
        }

    /** Charge le cache (une seule fois), puis rafraîchit si les taux manquent ou sont périmés. */
    suspend fun refreshIfNeeded() {
        loadCacheOnce()
        if (isStale) refresh()
    }

    /** Récupère les taux du jour. Les appels simultanés partagent la même requête. */
    suspend fun refresh() {
        val job = synchronized(refreshLock) {
            inFlight?.takeIf { it.isActive } ?: scope.async { fetch() }.also { inFlight = it }
        }
        job.await()
    }

    private suspend fun loadCacheOnce() = cacheLock.withLock {
        if (cacheLoaded) return@withLock
        cacheLoaded = true
        val stored = try {
            cache.load()
        } catch (e: CancellationException) {
            cacheLoaded = false
            throw e
        } catch (_: Exception) {
            null
        }
        if (stored != null && _rates.value == null) _rates.value = stored
    }

    private suspend fun fetch() {
        try {
            val fresh = source.latest().copy(fetchedAt = Instant.now(clock))
            _rates.value = fresh
            // Un cache illisible ou plein n'empêche pas d'utiliser les taux obtenus.
            try {
                cache.save(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Hors ligne ou service indisponible : on garde le dernier taux connu.
        }
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(6)
    }
}
