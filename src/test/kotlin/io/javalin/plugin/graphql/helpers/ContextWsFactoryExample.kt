package io.javalin.plugin.graphql.helpers

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import io.javalin.websocket.WsMessageContext

class ContextWsFactoryExample : GraphQLContextFactory<WsMessageContext> {
    override suspend fun generateContext(request: WsMessageContext): GraphQLContext =
        mapOf(ContextExample::class to ContextExample(request.header("Authorization")?.removePrefix("Beare ")))
            .toGraphQLContext()
}
