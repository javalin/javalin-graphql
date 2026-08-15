package io.javalin.plugin.graphql.context

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import io.javalin.http.Context

/**
 * Default context factory for HTTP requests: produces an empty [GraphQLContext].
 *
 * Since graphql-kotlin 6 the context is no longer a user-defined type implementing a marker
 * interface, but graphql-java's map-like [GraphQLContext]. Implement your own factory and put
 * values in the map keyed by class, then read them in a resolver through the
 * `DataFetchingEnvironment`.
 */
class EmptyGraphQLContextFactory : GraphQLContextFactory<Context> {
    override suspend fun generateContext(request: Context): GraphQLContext =
        emptyMap<Any, Any>().toGraphQLContext()
}
