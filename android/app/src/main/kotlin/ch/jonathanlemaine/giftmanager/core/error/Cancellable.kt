package ch.jonathanlemaine.giftmanager.core.error

import kotlinx.coroutines.CancellationException

/** Comme [runCatching], mais une annulation de coroutine n'est jamais avalée. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
