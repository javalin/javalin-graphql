package io.javalin.plugin.graphql

import io.javalin.config.JavalinState
import io.javalin.http.HttpStatus
import io.javalin.plugin.Plugin
import io.javalin.plugin.graphql.server.JavalinGraphQLServer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * The plugin is configured through [GraphQLPluginBuilder], which the caller builds eagerly
 * before registering the plugin, so there is no `Consumer<CONFIG>` config to apply here —
 * hence [Void]. Prefer [GraphQLPluginBuilder.build] over calling this constructor directly.
 */
class GraphQLPlugin(private val builder: GraphQLPluginBuilder) : Plugin<Void>() {

    private val log = LoggerFactory.getLogger(GraphQLPlugin::class.java)
    private val graphQLHandler: GraphQLHandler = GraphQLHandler(builder)

    override fun onStart(state: JavalinState) {
        val server = JavalinGraphQLServer.create(builder)

        if (builder.graphiQLEnabled) {
            state.routes.get(builder.path) { ctx ->
                ctx.contentType("text/html; charset=UTF-8")
                    .result(GraphQLPlugin::class.java.getResourceAsStream("graphqli/index.html")!!)
            }
        }
        state.routes.post(builder.path) { ctx ->
            val response = runBlocking { server.execute(ctx) }
            if (response != null) {
                ctx.json(response)
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(mapOf("error" to "Invalid request"))
            }
        }
        state.routes.ws(builder.path) { ws ->
            ws.onMessage { ctx -> graphQLHandler.onMessage(ctx) }
            ws.onClose { ctx -> graphQLHandler.onClose(ctx) }
            ws.onError { ctx -> log.error("GraphQL WebSocket error", ctx.error()) }
        }
    }
}
