package io.javalin.plugin.graphql.helpers

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import io.javalin.http.Context

class ContextFactoryExample : GraphQLContextFactory<Context> {
    override suspend fun generateContext(request: Context): GraphQLContext =
        mapOf(ContextExample::class to ContextExample(request.header("Authorization")?.removePrefix("Beare ")))
            .toGraphQLContext()
}
