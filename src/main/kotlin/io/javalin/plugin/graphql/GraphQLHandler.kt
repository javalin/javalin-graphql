package io.javalin.plugin.graphql

import com.expediagroup.graphql.server.types.GRAPHQL_WS_COMPLETE
import com.expediagroup.graphql.server.types.GRAPHQL_WS_CONNECTION_INIT
import com.expediagroup.graphql.server.types.GRAPHQL_WS_PING
import com.expediagroup.graphql.server.types.GRAPHQL_WS_PONG
import com.expediagroup.graphql.server.types.GRAPHQL_WS_SUBSCRIBE
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLServerError
import com.expediagroup.graphql.server.types.SubscriptionMessageComplete
import com.expediagroup.graphql.server.types.SubscriptionMessageConnectionAck
import com.expediagroup.graphql.server.types.SubscriptionMessageError
import com.expediagroup.graphql.server.types.SubscriptionMessageNext
import com.expediagroup.graphql.server.types.SubscriptionMessagePong
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves GraphQL subscriptions over the `graphql-transport-ws` protocol
 * (https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md).
 *
 * The message types come from graphql-kotlin, but they are annotated for Jackson 3 and
 * fastjson2, so incoming frames are dispatched on the `type` field by hand rather than
 * deserialized into the sealed `GraphQLSubscriptionMessage`. That keeps the plugin working
 * with whatever `JsonMapper` the application has configured.
 *
 * Note on subprotocol negotiation: Javalin exposes no API for it, and the Jetty handshake
 * underneath echoes back whichever subprotocol the client requested first, without checking
 * it. A `graphql-transport-ws` client therefore connects correctly, but a client asking for
 * something this handler does not speak — the legacy `graphql-ws` subprotocol, say — is told
 * yes and then receives messages it cannot understand. Rejecting those needs a Javalin change.
 */
class GraphQLHandler(private val graphQLBuilder: GraphQLPluginBuilder) {

    private companion object {
        // Close codes defined by the graphql-transport-ws protocol.
        const val BAD_REQUEST = 4400
        const val UNAUTHORIZED = 4401
        const val SUBSCRIBER_ALREADY_EXISTS = 4409
        const val TOO_MANY_INITIALISATION_REQUESTS = 4429
    }

    private val log = LoggerFactory.getLogger(GraphQLHandler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** connection id -> (operation id -> the coroutine draining that subscription) */
    private val operations = ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>()
    private val acknowledged = ConcurrentHashMap.newKeySet<String>()

    fun onMessage(ctx: WsMessageContext) {
        val message = try {
            ctx.messageAsClass(Map::class.java)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Trust boundary: a client can send anything, and no frame should kill the connection.
            log.debug("Could not read WebSocket frame as JSON", e)
            return ctx.closeSession(BAD_REQUEST, "Invalid message received")
        }

        when (message["type"] as? String) {
            GRAPHQL_WS_CONNECTION_INIT -> connectionInit(ctx)
            GRAPHQL_WS_PING -> ctx.send(SubscriptionMessagePong())
            GRAPHQL_WS_PONG -> Unit // nothing to do, the client is just answering our ping
            GRAPHQL_WS_SUBSCRIBE -> subscribe(ctx, message)
            GRAPHQL_WS_COMPLETE -> complete(ctx, message["id"] as? String)
            else -> ctx.closeSession(BAD_REQUEST, "Unknown message type")
        }
    }

    /** Cancels everything still running for a connection that went away. */
    fun onClose(ctx: WsCloseContext) {
        acknowledged.remove(ctx.sessionId())
        operations.remove(ctx.sessionId())?.values?.forEach { it.cancel() }
    }

    /**
     * Cancels every subscription still running and releases the scope. Called once, when the
     * server is stopping. The scope is not reusable afterwards, which is fine: a Javalin
     * instance cannot be restarted.
     */
    fun shutdown() {
        scope.cancel("Javalin is stopping")
        operations.clear()
        acknowledged.clear()
    }

    private fun connectionInit(ctx: WsMessageContext) {
        if (!acknowledged.add(ctx.sessionId())) {
            return ctx.closeSession(TOO_MANY_INITIALISATION_REQUESTS, "Too many initialisation requests")
        }
        ctx.send(SubscriptionMessageConnectionAck())
    }

    private fun subscribe(ctx: WsMessageContext, message: Map<*, *>) {
        val connectionId = ctx.sessionId()
        if (connectionId !in acknowledged) {
            return ctx.closeSession(UNAUTHORIZED, "Unauthorized")
        }
        val id = message["id"] as? String
            ?: return ctx.closeSession(BAD_REQUEST, "Missing operation id")
        val payload = message["payload"] as? Map<*, *>
            ?: return ctx.closeSession(BAD_REQUEST, "Missing subscribe payload")

        val running = operations.computeIfAbsent(connectionId) { ConcurrentHashMap() }
        if (running.containsKey(id)) {
            return ctx.closeSession(SUBSCRIBER_ALREADY_EXISTS, "Subscriber for $id already exists")
        }

        // Started lazily so the job is registered before it can finish and deregister itself.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val graphQLContext = graphQLBuilder.contextWsFactory.generateContext(ctx)
                graphQLBuilder.requestHandler
                    .executeSubscription(payload.toGraphQLRequest(), graphQLContext)
                    .collect { response -> ctx.send(SubscriptionMessageNext(id, response)) }
                ctx.send(SubscriptionMessageComplete(id))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // A resolver is user code and may throw anything; report it as a protocol
                // `error` for this operation instead of tearing down the whole connection.
                log.debug("GraphQL subscription {} failed", id, e)
                ctx.send(SubscriptionMessageError(id, listOf(GraphQLServerError(e.message ?: "Subscription failed"))))
            } finally {
                operations[connectionId]?.remove(id)
            }
        }
        running[id] = job
        job.start()
    }

    private fun complete(ctx: WsMessageContext, id: String?) {
        if (id == null) {
            return ctx.closeSession(BAD_REQUEST, "Missing operation id")
        }
        operations[ctx.sessionId()]?.remove(id)?.cancel()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.toGraphQLRequest() = GraphQLRequest(
        query = this["query"] as? String ?: "",
        operationName = this["operationName"] as? String,
        variables = this["variables"] as? Map<String, Any?>,
        extensions = this["extensions"] as? Map<String, Any?>
    )
}
