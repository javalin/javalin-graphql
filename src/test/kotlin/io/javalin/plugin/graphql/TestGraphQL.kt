package io.javalin.plugin.graphql

import io.javalin.Javalin
import io.javalin.plugin.graphql.helpers.ContextFactoryExample
import io.javalin.plugin.graphql.helpers.ContextWsFactoryExample
import io.javalin.plugin.graphql.helpers.MutationExample
import io.javalin.plugin.graphql.helpers.QueryExample
import io.javalin.plugin.graphql.helpers.SubscriptionExample
import io.javalin.testtools.JavalinTest
import kong.unirest.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TestGraphQL {

    private val graphqlPath = "/graphql"
    private val message = "Hello World"
    private val newMessage = "hi"

    @Test
    fun query() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val body = client.post(graphqlPath, "{\"query\": \"{ hello }\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("hello"), message)
    }

    @Test
    fun mutation() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val mutation = "mutation { changeMessage(newMessage: \\\"$newMessage\\\") }"
        val body = client.post(graphqlPath, "{\"query\": \"$mutation\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("changeMessage"), newMessage)
    }

    @Test
    fun multiQuery() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val queries = "query X { hello } query Y { echo(message: \\\"$newMessage\\\") }"

        var body = client.post(graphqlPath, "{\"query\": \"$queries\", \"operationName\": \"X\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("hello"), message)
        assertFalse(JSONObject(body).getJSONObject("data").has("echo"))

        body = client.post(graphqlPath, "{\"query\": \"$queries\", \"operationName\": \"Y\"}").body?.string()
        assertFalse(JSONObject(body).getJSONObject("data").has("hello"))
        assertEquals(JSONObject(body).getJSONObject("data").getString("echo"), newMessage)
    }

    @Test
    fun mutation_with_variables() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val mutation = "mutation changeMessage(\$message: String!){changeMessage(newMessage: \$message)}"
        val variables = "{\"message\": \"$newMessage\"}"
        val body = client.post(graphqlPath, "{\"variables\": $variables, \"query\": \"$mutation\" }").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("changeMessage"), newMessage)
    }

    @Test
    fun contextWithoutAuthorized() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val body = client.post(graphqlPath, "{\"query\": \"{ isAuthorized }\"}").body?.string()
        assertFalse(JSONObject(body).getJSONObject("data").getBoolean("isAuthorized"))
    }

    @Test
    fun contextWithAuthorized() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val body = client.post(
            graphqlPath,
            "{\"query\": \"{ isAuthorized }\"}"
        ) { request -> request.header("Authorization", "Beare token") }.body?.string()
        assertTrue(JSONObject(body).getJSONObject("data").getBoolean("isAuthorized"))
    }

    // --- graphql-transport-ws ---------------------------------------------------------------

    @Test
    fun connectionInitIsAcknowledged() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { it.initAndAwaitAck() }
    }

    @Test
    fun pingIsAnsweredWithPong() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"ping"}""")
            assertEquals("pong", ws.awaitMessage { it.getString("type") == "pong" }.getString("type"))
        }
    }

    @Test
    fun subscribeBeforeConnectionInitIsUnauthorized() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { ws ->
            ws.connectBlocking()
            ws.subscribe("1", "subscription { counter }")
            assertEquals(4401, ws.awaitClose())
        }
    }

    @Test
    fun subscribe() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counter }")

            val next = ws.awaitMessage { it.getString("type") == "next" }
            assertEquals("1", next.getString("id"))
            assertEquals(1, next.getJSONObject("payload").getJSONObject("data").getInt("counter"))

            // the stream is finite, so the server must say so
            assertEquals("1", ws.awaitMessage { it.getString("type") == "complete" }.getString("id"))
        }
    }

    @Test
    fun subscribeWithoutContext() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counterUser }")

            val next = ws.awaitMessage { it.getString("type") == "next" }
            assertEquals(
                "${SubscriptionExample.anonymous_message} ~> 1",
                next.getJSONObject("payload").getJSONObject("data").getString("counterUser")
            )
        }
    }

    @Test
    fun subscribeWithContext() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        val tokenUser = "token"
        TestClient(server, graphqlPath, mapOf("Authorization" to "Beare $tokenUser")).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counterUser }")

            val next = ws.awaitMessage { it.getString("type") == "next" }
            assertEquals(
                "$tokenUser ~> 1",
                next.getJSONObject("payload").getJSONObject("data").getString("counterUser")
            )
        }
    }

    @Test
    fun reusingAnOperationIdIsRejected() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counterUser }")
            ws.subscribe("1", "subscription { counterUser }")
            assertEquals(4409, ws.awaitClose())
        }
    }

    /** Minimal graphql-transport-ws client, enough to drive the protocol from a test. */
    internal inner class TestClient(
        app: Javalin,
        path: String,
        headers: Map<String, String> = emptyMap()
    ) : WebSocketClient(URI.create("ws://localhost:" + app.port() + path), Draft_6455(), headers, 0) {

        private val received = LinkedBlockingQueue<JSONObject>()
        private val closeCodes = LinkedBlockingQueue<Int>()

        override fun onOpen(serverHandshake: ServerHandshake) {}
        override fun onClose(code: Int, reason: String, remote: Boolean) { closeCodes.offer(code) }
        override fun onError(e: Exception) {}
        override fun onMessage(s: String) { received.offer(JSONObject(s)) }

        fun sendJson(json: String) = send(json)

        fun initAndAwaitAck() {
            connectBlocking()
            sendJson("""{"type":"connection_init"}""")
            awaitMessage { it.getString("type") == "connection_ack" }
        }

        fun subscribe(id: String, query: String) =
            sendJson("""{"id":"$id","type":"subscribe","payload":{"query":"$query"}}""")

        /** Waits for the first message matching [predicate], failing the test on timeout. */
        fun awaitMessage(timeoutSeconds: Long = 5, predicate: (JSONObject) -> Boolean): JSONObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            val seen = mutableListOf<JSONObject>()
            while (System.nanoTime() < deadline) {
                val next = received.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(next)) return next
                seen.add(next)
            }
            return fail("No matching message within ${timeoutSeconds}s. Received: $seen")
        }

        fun awaitClose(timeoutSeconds: Long = 5): Int =
            closeCodes.poll(timeoutSeconds, TimeUnit.SECONDS) ?: fail("Connection was not closed")
    }

    /** Runs [block] against the client and always closes the socket afterwards. */
    private fun TestClient.session(block: (TestClient) -> Unit) {
        try {
            block(this)
        } finally {
            closeBlocking()
        }
    }

    private fun shortTimeoutServer(): Javalin {
        return Javalin.create { config ->
            val graphQLPluginBuilder =
                GraphQLPluginBuilder(graphqlPath, ContextFactoryExample(), ContextWsFactoryExample())
                    .addPackage("io.javalin.plugin.graphql")
                    .register(QueryExample(message))
                    .register(MutationExample(message))
                    .register(SubscriptionExample())

            config.registerPlugin(graphQLPluginBuilder.build())
        }
    }
}
