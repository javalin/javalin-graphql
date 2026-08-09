package io.javalin.plugin.graphql

import com.expediagroup.graphql.server.types.GraphQLRequest
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.runBlocking

class GraphQLHandler(private val graphQLBuilder: GraphQLPluginBuilder) {

    fun execute(ctx: WsMessageContext) {
        val body = ctx.messageAsClass(Map::class.java)
        val query = body["query"].toString()
        val variables: Map<String, Any?> = getVariables(body)
        val operationName = body["operationName"]?.toString()

        runBlocking {
            val graphQLContext = graphQLBuilder.contextWsFactory.generateContext(ctx)
            val request = GraphQLRequest(query = query, operationName = operationName, variables = variables)

            graphQLBuilder.requestHandler
                .executeSubscription(request, graphQLContext)
                .collect { response ->
                    // Send just the data when there is any, so a subscriber receives {"field":value};
                    // fall back to the whole response so errors are not swallowed.
                    ctx.send(response.data ?: response)
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getVariables(body: Map<*, *>): Map<String, Any?> =
        if (body["variables"] == null) emptyMap() else body["variables"] as Map<String, Any?>
}
