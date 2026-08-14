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
        const val anonymous_message = "anonymus"
    }

    fun counter(): Flow<Int> = flowOf(1)

    fun counterUser(environment: DataFetchingEnvironment): Flow<String> = flow {
        val context = environment.graphQlContext.get<ContextExample>(ContextExample::class)
        val user = if (context != null && context.isValid) context.authorization!! else anonymous_message
        repeat(5) {
            delay(200)
            emit("$user ~> 1")
        }
    }
}
