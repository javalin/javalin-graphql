package io.javalin.plugin.graphql.context

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import io.javalin.websocket.WsMessageContext

/**
 * Default context factory for subscription messages: produces an empty [GraphQLContext].
 *
 * See [EmptyGraphQLContextFactory] for how the context model changed in graphql-kotlin 6+.
 */
class EmptyWsGraphQLContextFactory : GraphQLContextFactory<WsMessageContext> {
    override suspend fun generateContext(request: WsMessageContext): GraphQLContext =
        emptyMap<Any, Any>().toGraphQLContext()
}
