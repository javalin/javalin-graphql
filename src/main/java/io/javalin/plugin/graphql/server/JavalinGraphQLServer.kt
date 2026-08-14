package io.javalin.plugin.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import io.javalin.http.Context
import io.javalin.plugin.graphql.GraphQLPluginBuilder

class JavalinGraphQLServer(
    contextFactory: GraphQLContextFactory<Context>,
    requestHandler: GraphQLRequestHandler
) : GraphQLServer<Context>(JavalinGraphQLRequestParser(), contextFactory, requestHandler) {

    companion object {
        fun create(builder: GraphQLPluginBuilder): JavalinGraphQLServer =
            JavalinGraphQLServer(builder.contextFactory, builder.requestHandler)
    }
}
