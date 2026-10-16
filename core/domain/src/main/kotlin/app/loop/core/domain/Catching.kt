package app.loop.core.domain

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspend code: failures become a [Result], but coroutine cancellation is
 * rethrown so a stopped worker or a closed screen actually stops instead of carrying on.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
