package io.javalin.plugin.graphql

import io.javalin.Javalin
import io.javalin.plugin.graphql.helpers.CancellationProbe
import io.javalin.plugin.graphql.helpers.GRAPHQL_PATH
import io.javalin.plugin.graphql.helpers.MESSAGE
import io.javalin.plugin.graphql.helpers.QueryExample
import io.javalin.plugin.graphql.helpers.SubscriptionExample
import io.javalin.plugin.graphql.helpers.TestClient
import io.javalin.plugin.graphql.helpers.graphQLApp
import io.javalin.plugin.graphql.helpers.session
import io.javalin.testtools.JavalinTest
import kong.unirest.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * The graphql-transport-ws side of the endpoint
 * (https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md).
 */
class TestGraphQLSubscriptions {

    @Test
    fun connectionInitIsAcknowledged() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { it.initAndAwaitAck() }
    }

    @Test
    fun pingIsAnsweredWithPong() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"ping"}""")
            assertEquals("pong", ws.awaitMessage { it.getString("type") == "pong" }.getString("type"))
        }
    }

    @Test
    fun subscribeBeforeConnectionInitIsUnauthorized() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.connectBlocking()
            ws.subscribe("1", "subscription { counter }")
            assertEquals(UNAUTHORIZED, ws.awaitClose())
        }
    }

    @Test
    fun subscribe() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
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
    fun subscribeWithoutContext() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counterUser }")

            val next = ws.awaitMessage { it.getString("type") == "next" }
            assertEquals(
                "${SubscriptionExample.ANONYMOUS_MESSAGE} ~> 1",
                next.getJSONObject("payload").getJSONObject("data").getString("counterUser")
            )
        }
    }

    @Test
    fun subscribeWithContext() = JavalinTest.test(graphQLApp()) { server, _ ->
        val tokenUser = "token"
        TestClient(server, GRAPHQL_PATH, mapOf("Authorization" to "Beare $tokenUser")).session { ws ->
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
    fun reusingAnOperationIdIsRejected() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { counterUser }")
            ws.subscribe("1", "subscription { counterUser }")
            assertEquals(SUBSCRIBER_ALREADY_EXISTS, ws.awaitClose())
        }
    }

    @Test
    fun invalidJsonClosesWithBadRequest() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.connectBlocking()
            ws.sendJson("this is not json")
            assertEquals(BAD_REQUEST, ws.awaitClose())
        }
    }

    @Test
    fun unknownMessageTypeClosesWithBadRequest() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"teleport"}""")
            assertEquals(BAD_REQUEST, ws.awaitClose())
        }
    }

    @Test
    fun subscribeWithoutIdClosesWithBadRequest() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"subscribe","payload":{"query":"subscription { counter }"}}""")
            assertEquals(BAD_REQUEST, ws.awaitClose())
        }
    }

    @Test
    fun subscribeWithoutPayloadClosesWithBadRequest() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"id":"1","type":"subscribe"}""")
            assertEquals(BAD_REQUEST, ws.awaitClose())
        }
    }

    @Test
    fun completeWithoutIdClosesWithBadRequest() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"complete"}""")
            assertEquals(BAD_REQUEST, ws.awaitClose())
        }
    }

    @Test
    fun aSecondConnectionInitIsRejected() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.sendJson("""{"type":"connection_init"}""")
            assertEquals(TOO_MANY_INITIALISATION_REQUESTS, ws.awaitClose())
        }
    }

    @Test
    fun completeStopsAnEndlessSubscription() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { infinite }")
            ws.awaitMessage { it.getString("type") == "next" }

            ws.complete("1")
            ws.collectFor(SETTLE_MS) // drain whatever was already in flight

            // The protocol has the server stay silent after a client `complete`, and the flow
            // never ends on its own, so anything arriving now means it was not cancelled.
            assertEquals(emptyList<JSONObject>(), ws.collectFor(SILENCE_MS))
        }
    }

    /**
     * Two subscriptions multiplexed on one connection: both coroutines run on
     * `Dispatchers.Default` and call `ctx.send()` on the same Jetty session. Were concurrent
     * writes rejected or interleaved, frames would be lost, reordered or unparseable.
     */
    @Test
    fun twoConcurrentSubscriptionsShareOneConnection() = JavalinTest.test(graphQLApp()) { server, _ ->
        TestClient(server).session { ws ->
            ws.initAndAwaitAck()
            ws.subscribe("1", "subscription { burst(size: $BURST_SIZE) }")
            ws.subscribe("2", "subscription { burst(size: $BURST_SIZE) }")

            val messages = ws.awaitMessages { collected ->
                collected.count { it.getString("type") == "complete" } == 2
            }

            assertEquals(emptyList<JSONObject>(), messages.filter { it.getString("type") == "error" })
            listOf("1", "2").forEach { id ->
                val values = messages
                    .filter { it.getString("type") == "next" && it.getString("id") == id }
                    .map { it.getJSONObject("payload").getJSONObject("data").getInt("burst") }
                assertEquals((0 until BURST_SIZE).toList(), values, "subscription $id lost or reordered frames")
            }
        }
    }

    /**
     * Javalin 7's `Plugin` has no stop hook, so the subscription scope is cancelled from the
     * `serverStopping` event. Built by hand rather than with `JavalinTest.test`, which stops the
     * app on the way out and would leave nothing to assert on.
     */
    @Test
    fun stoppingTheServerCancelsRunningSubscriptions() {
        val probe = CancellationProbe()
        val app = Javalin.create { config ->
            config.registerPlugin(
                GraphQLPluginBuilder(GRAPHQL_PATH)
                    .addPackage("io.javalin.plugin.graphql.helpers")
                    .register(QueryExample(MESSAGE))
                    .register(probe)
                    .build()
            )
        }.start(0)

        try {
            TestClient(app).session { ws ->
                ws.initAndAwaitAck()
                ws.subscribe("1", "subscription { endless }")
                assertTrue(probe.started.await(AWAIT_SECONDS, TimeUnit.SECONDS), "subscription never started")

                app.stop()
                assertTrue(probe.cancelled.await(AWAIT_SECONDS, TimeUnit.SECONDS), "subscription was not cancelled")
            }
        } finally {
            app.stop()
        }
    }

    private companion object {
        // Close codes defined by the graphql-transport-ws protocol.
        const val BAD_REQUEST = 4400
        const val UNAUTHORIZED = 4401
        const val SUBSCRIBER_ALREADY_EXISTS = 4409
        const val TOO_MANY_INITIALISATION_REQUESTS = 4429

        const val BURST_SIZE = 100
        const val SETTLE_MS = 300L
        const val SILENCE_MS = 500L
        const val AWAIT_SECONDS = 5L
    }
}
