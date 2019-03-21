package no.nav.syfo.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArgument
import no.nav.syfo.metrics.NETWORK_CALL_SUMMARY
import no.nav.syfo.log
import java.io.IOException
import kotlin.reflect.KClass

inline fun <reified T> CoroutineScope.retryAsync(
    callName: String,
    logKeys: String,
    logValues: Array<StructuredArgument>,
    vararg legalExceptions: KClass<out Throwable> = arrayOf(IOException::class),
    exceptionCausedByDepth: Int = 3,
    retryIntervals: Array<Long> = arrayOf(500, 1000, 3000, 5000, 10000),
    crossinline block: suspend () -> T
): Deferred<T> = async {
    for (interval in retryIntervals) {
        try {
            return@async timed(callName) { block() }
        } catch (e: Throwable) {
            if (!isCausedBy(e, exceptionCausedByDepth, legalExceptions)) {
                throw e
            }
            log.warn("Failed to execute call $callName, retrying in $interval ms $logKeys", *logValues, e)
        }
        delay(interval)
    }
    timed(callName) { block() }
}

suspend inline fun <reified T> timed(callName: String, crossinline block: suspend() -> T) = NETWORK_CALL_SUMMARY.labels(callName).startTimer().use {
    block()
}

fun isCausedBy(throwable: Throwable, depth: Int, legalExceptions: Array<out KClass<out Throwable>>): Boolean {
    var current: Throwable = throwable
    for (i in 0.until(depth)) {
        if (legalExceptions.any { it.isInstance(current) }) {
            return true
        }
        current = current.cause ?: break
    }
    return false
}
