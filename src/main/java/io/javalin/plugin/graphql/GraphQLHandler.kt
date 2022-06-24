package io.javalin.plugin.graphql

import io.javalin.plugin.graphql.graphql.GraphQLRun
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking

class GraphQLHandler(private val graphQLBuilder: GraphQLPluginBuilder<*>) {

    fun execute(ctx: WsMessageContext) {
        val body = ctx.messageAsClass(Map::class.java)
        val query = body["query"].toString()
        val variables: Map<String, Any> = getVariables(body)
        val operationName = body["operationName"]?.toString()
        val context = runBlocking { graphQLBuilder.contextWsFactory.generateContext(ctx) }

        runBlocking {
            GraphQLRun(graphQLBuilder.getSchema())
                .withQuery(query)
                .withVariables(variables)
                .withOperationName(operationName)
                .withContext(context)
                .subscribe()
                .collect {
                    ctx.send(it.getData<Any>())
                }
        }
    }

    private fun getVariables(body: Map<*, *>) =
        if (body["variables"] == null) emptyMap() else body["variables"] as Map<String, Any>
}
