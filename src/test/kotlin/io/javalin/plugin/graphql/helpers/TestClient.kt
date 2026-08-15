package io.javalin.plugin.graphql.helpers

import io.javalin.Javalin
import kong.unirest.json.JSONObject
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.Assertions.fail
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Minimal graphql-transport-ws client, enough to drive the protocol from a test. */
internal class TestClient(
    app: Javalin,
    path: String = GRAPHQL_PATH,
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

    fun complete(id: String) = sendJson("""{"id":"$id","type":"complete"}""")

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

    /** Collects every message, in arrival order, until [until] holds. */
    fun awaitMessages(timeoutSeconds: Long = 20, until: (List<JSONObject>) -> Boolean): List<JSONObject> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        val collected = mutableListOf<JSONObject>()
        while (System.nanoTime() < deadline) {
            if (until(collected)) return collected
            received.poll(200, TimeUnit.MILLISECONDS)?.let { collected.add(it) }
        }
        return fail("Condition not met within ${timeoutSeconds}s. Received: $collected")
    }

    /** Waits [millis] and returns whatever arrived meanwhile. Used to assert silence. */
    fun collectFor(millis: Long): List<JSONObject> {
        Thread.sleep(millis)
        return mutableListOf<JSONObject>().also { received.drainTo(it) }
    }

    fun awaitClose(timeoutSeconds: Long = 5): Int =
        closeCodes.poll(timeoutSeconds, TimeUnit.SECONDS) ?: fail("Connection was not closed")
}

/** Runs [block] against the client and always closes the socket afterwards. */
internal fun TestClient.session(block: (TestClient) -> Unit) {
    try {
        block(this)
    } finally {
        closeBlocking()
    }
}
