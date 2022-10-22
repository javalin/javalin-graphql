package io.javalin.plugin.graphql

import io.javalin.Javalin
import io.javalin.plugin.Plugin
import io.javalin.plugin.PluginLifecycleInit
import io.javalin.plugin.graphql.server.JavalinGraphQLServer
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.http.HttpStatus

class GraphQLPlugin(private val builder: GraphQLPluginBuilder<*>) : Plugin, PluginLifecycleInit {

    constructor(options: GraphQLOptions) : this(GraphQLPluginBuilder.create(options)) {
        GraphQLPluginBuilder
    }

    private val graphQLHandler: GraphQLHandler = GraphQLHandler(builder)

    override fun apply(app: Javalin) {
        val server = JavalinGraphQLServer.create(builder)

        app.get(builder.path) {
            it.contentType("text/html; charset=UTF-8")
                .result(
                    GraphQLPlugin::class.java.getResourceAsStream("graphqli/index.html")
                )
        }
        app.post(builder.path) { ctx ->
            val response = runBlocking { server.execute(ctx) }
            if (response != null) {
                ctx.json(response)
            } else {
                ctx.status(HttpStatus.BAD_REQUEST_400).json(mapOf("error" to "Invalid request"))
            }
        }
        app.ws(builder.path) { ws ->
            ws.onMessage { ctx -> graphQLHandler.execute(ctx) }
            ws.onError { ctx ->
                error("GraphQL Error WebSocket -> ${ctx.error()?.message}")
                error(ctx.error()?.stackTrace as Any)
            }
        }
    }

    override fun init(app: Javalin) {}
}
