package io.javalin.plugin.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.javalin.http.Context

/**
 * Parses the request body into a [GraphQLServerRequest].
 *
 * We deliberately do *not* ask the mapper to deserialize the sealed [GraphQLServerRequest]
 * directly: graphql-kotlin annotates it for Jackson 3 (`tools.jackson`) and fastjson2, while
 * Javalin's default mapper is Jackson 2, so the sealed type has no deserializer it can see.
 * Picking the concrete subtype here keeps the plugin working with whatever `JsonMapper` the
 * application has configured instead of forcing a JSON library on it.
 */
class JavalinGraphQLRequestParser : GraphQLRequestParser<Context> {

    override suspend fun parseRequest(request: Context): GraphQLServerRequest? {
        val body = request.body().trimStart()
        if (body.isEmpty()) return null

        return try {
            when {
                // A batch request is a JSON array of requests; anything else is a single one.
                body.startsWith("[") ->
                    GraphQLBatchRequest(request.bodyAsClass(Array<GraphQLRequest>::class.java).toList())
                else -> request.bodyAsClass(GraphQLRequest::class.java)
            }
        } catch (e: Exception) {
            // Returning null makes GraphQLServer skip execution, which the plugin turns into a 400.
            null
        }
    }
}
