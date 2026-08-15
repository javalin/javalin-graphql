package io.javalin.plugin.graphql.helpers

import graphql.schema.DataFetchingEnvironment
import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * graphql-kotlin 6+ drives subscriptions with kotlinx [Flow] rather than a reactive-streams
 * `Publisher`, so these resolvers no longer return reactor `Flux`.
 */
class SubscriptionExample : SubscriptionGraphql {

    companion object {
        const val ANONYMOUS_MESSAGE = "anonymous"
    }

    fun counter(): Flow<Int> = flowOf(1)

    /** Never completes on its own: proves that a client `complete` really stops a subscription. */
    fun infinite(): Flow<Int> = flow {
        var value = 0
        while (true) {
            emit(++value)
            delay(SLOW_TICK_MS)
        }
    }

    /**
     * Emits [size] values with a small gap, so two of these on one connection genuinely overlap
     * and both write to the same Jetty session from `Dispatchers.Default`.
     */
    fun burst(size: Int): Flow<Int> = flow {
        repeat(size) {
            emit(it)
            delay(FAST_TICK_MS)
        }
    }

    fun counterUser(environment: DataFetchingEnvironment): Flow<String> = flow {
        val context = environment.graphQlContext.get<ContextExample>(ContextExample::class)
        val user = if (context != null && context.isValid) context.authorization!! else ANONYMOUS_MESSAGE
        repeat(5) {
            delay(TICK_MS)
            emit("$user ~> 1")
        }
    }
}

private const val TICK_MS = 200L
private const val SLOW_TICK_MS = 50L
private const val FAST_TICK_MS = 1L
