package io.javalin.plugin.graphql.helpers

import graphql.schema.DataFetchingEnvironment
import io.javalin.plugin.graphql.graphql.QueryGraphql

class QueryExample(val message: String) : QueryGraphql {
    fun hello(): String = message

    fun echo(message: String): String = message

    /**
     * graphql-kotlin no longer injects a custom context type as a resolver parameter;
     * the context is read from the `DataFetchingEnvironment`.
     */
    fun isAuthorized(environment: DataFetchingEnvironment): Boolean {
        val context = environment.graphQlContext.get<ContextExample>(ContextExample::class)
        return context != null && context.isValid
    }
}
