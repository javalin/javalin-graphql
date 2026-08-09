package io.javalin.plugin.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.javalin.http.Context
import org.slf4j.LoggerFactory

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
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Deliberately broad: this is a trust boundary. Any mapper can fail in its own way on
            // a malformed body, and none of those failures should reach the client as a 500.
            // Returning null makes GraphQLServer skip execution, which the plugin turns into a 400.
            log.debug("Could not parse the request body as a GraphQL request", e)
            null
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(JavalinGraphQLRequestParser::class.java)
    }
}
