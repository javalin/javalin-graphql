package io.javalin.plugin.graphql.helpers

import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.CountDownLatch

/**
 * A subscription that never ends and reports when it is torn down, so a test can prove that
 * stopping the server actually cancels running subscriptions rather than leaking them.
 */
class CancellationProbe : SubscriptionGraphql {

    val started = CountDownLatch(1)
    val cancelled = CountDownLatch(1)

    fun endless(): Flow<Int> = flow {
        var value = 0
        while (true) {
            emit(++value)
            started.countDown()
            delay(TICK_MS)
        }
    }.onCompletion { cancelled.countDown() }

    private companion object {
        const val TICK_MS = 20L
    }
}
