package io.javalin.plugin.graphql

import io.javalin.Javalin
import io.javalin.plugin.graphql.helpers.*
import io.javalin.testtools.JavalinTest
import kong.unirest.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException

class TestGraphQL {

    private val graphqlPath = "/graphql"
    private val message = "Hello World"
    private val newMessage = "hi"

    data class TestLogger(val log: ArrayList<String>)

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
        ) { request -> request.addHeader("Authorization", "Beare token") }.body?.string()
        assertTrue(JSONObject(body).getJSONObject("data").getBoolean("isAuthorized"))
    }

    @Test
    fun subscribe() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath)
            .connectSendAndDisconnect("{\"query\": \"subscription { counter }\"}")
        assertThat(server.logger().log).containsAnyOf("{\"counter\":1}")
    }

    @Test
    fun subscribeWithoutContext() = JavalinTest.test(shortTimeoutServer()) { server, client ->
        TestClient(server, graphqlPath)
            .connectSendAndDisconnect("{\"query\": \"subscription { counterUser }\"}")
        assertThat(server.logger().log).containsAnyOf("{\"counterUser\":\"${SubscriptionExample.anonymous_message} ~> 1\"}")
    }

    @Test
    fun subscribeWithContext() = JavalinTest.test(shortTimeoutServer()) { server, httpUtil ->
        val tokenUser = "token"
        TestClient(server, graphqlPath, mapOf("Authorization" to "Beare $tokenUser"))
            .connectSendAndDisconnect("{\"query\": \"subscription { counterUser }\"}")
        assertThat(server.logger().log).containsAnyOf("{\"counterUser\":\"${SubscriptionExample.anonymous_message} ~> 1\"}")
        assertThat(server.logger().log).containsAnyOf("{\"counterUser\":\"$tokenUser ~> 1\"}")
    }

    internal open inner class TestClient(
        var app: Javalin,
        path: String,
        headers: Map<String, String> = emptyMap(),
        val onOpen: (TestClient) -> Unit = {},
        var onMessage: ((String) -> Unit)? = null
    ) :
        WebSocketClient(URI.create("ws://localhost:" + app.port() + path), Draft_6455(), headers, 0) {

        override fun onOpen(serverHandshake: ServerHandshake) = onOpen(this)
        override fun onClose(i: Int, s: String, b: Boolean) {}
        override fun onError(e: Exception) {}
        override fun onMessage(s: String) {
            onMessage?.invoke(message)
            app.logger().log.add(s)
        }

        fun connectSendAndDisconnect(message: String) {
            connectBlocking()
            doBlocking(
                {
                    send(message)
                },
                {
                    app.logger().log.size == 0
                },
                Duration.ofSeconds(5)
            )
        }

        private fun doBlocking(
            slowFunction: () -> Unit,
            conditionFunction: () -> Boolean,
            timeout: Duration = Duration.ofSeconds(1)
        ) {
            val startTime = System.currentTimeMillis()
            val limitTime = startTime + timeout.toMillis()
            slowFunction.invoke()
            while (conditionFunction.invoke()) {
                if (System.currentTimeMillis() > limitTime) {
                    throw TimeoutException("Wait for condition has timed out")
                }
                Thread.sleep(25)
            }
        }
    }

    private fun Javalin.logger(): TestLogger {
        if (this.attribute<TestLogger>(TestLogger::class.java.name) == null) {
            this.attribute(TestLogger::class.java.name, TestLogger(ArrayList()))
        }
        return this.attribute(TestLogger::class.java.name)
    }

    private fun shortTimeoutServer(): Javalin {
        return Javalin.create {
            val graphQLPluginBuilder =
                GraphQLPluginBuilder(graphqlPath, ContextFactoryExample(), ContextWsFactoryExample())
                    .add("io.javalin.plugin.graphql")
                    .add("io.javalin.plugin.graphql.helpers")
                    .register(QueryExample(message))
                    .register(MutationExample(message))
                    .register(SubscriptionExample())

            it.registerPlugin(graphQLPluginBuilder.build())
        }
    }
}
