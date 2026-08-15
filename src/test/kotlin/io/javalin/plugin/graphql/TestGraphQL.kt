package io.javalin.plugin.graphql

import io.javalin.Javalin
import io.javalin.plugin.graphql.helpers.DataLoaderQueryExample
import io.javalin.plugin.graphql.helpers.GRAPHQL_PATH
import io.javalin.plugin.graphql.helpers.MESSAGE
import io.javalin.plugin.graphql.helpers.NEW_MESSAGE
import io.javalin.plugin.graphql.helpers.QueryExample
import io.javalin.plugin.graphql.helpers.UppercaseDataLoader
import io.javalin.plugin.graphql.helpers.graphQLApp
import io.javalin.testtools.JavalinTest
import kong.unirest.json.JSONArray
import kong.unirest.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The HTTP side of the endpoint. The WebSocket protocol lives in [TestGraphQLSubscriptions]. */
class TestGraphQL {

    @Test
    fun query() = JavalinTest.test(graphQLApp()) { _, client ->
        val body = client.post(GRAPHQL_PATH, "{\"query\": \"{ hello }\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("hello"), MESSAGE)
    }

    @Test
    fun mutation() = JavalinTest.test(graphQLApp()) { _, client ->
        val mutation = "mutation { changeMessage(newMessage: \\\"$NEW_MESSAGE\\\") }"
        val body = client.post(GRAPHQL_PATH, "{\"query\": \"$mutation\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("changeMessage"), NEW_MESSAGE)
    }

    @Test
    fun multiQuery() = JavalinTest.test(graphQLApp()) { _, client ->
        val queries = "query X { hello } query Y { echo(message: \\\"$NEW_MESSAGE\\\") }"

        var body = client.post(GRAPHQL_PATH, "{\"query\": \"$queries\", \"operationName\": \"X\"}").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("hello"), MESSAGE)
        assertFalse(JSONObject(body).getJSONObject("data").has("echo"))

        body = client.post(GRAPHQL_PATH, "{\"query\": \"$queries\", \"operationName\": \"Y\"}").body?.string()
        assertFalse(JSONObject(body).getJSONObject("data").has("hello"))
        assertEquals(JSONObject(body).getJSONObject("data").getString("echo"), NEW_MESSAGE)
    }

    @Test
    fun mutation_with_variables() = JavalinTest.test(graphQLApp()) { _, client ->
        val mutation = "mutation changeMessage(\$message: String!){changeMessage(newMessage: \$message)}"
        val variables = "{\"message\": \"$NEW_MESSAGE\"}"
        val body = client.post(GRAPHQL_PATH, "{\"variables\": $variables, \"query\": \"$mutation\" }").body?.string()
        assertEquals(JSONObject(body).getJSONObject("data").getString("changeMessage"), NEW_MESSAGE)
    }

    @Test
    fun contextWithoutAuthorized() = JavalinTest.test(graphQLApp()) { _, client ->
        val body = client.post(GRAPHQL_PATH, "{\"query\": \"{ isAuthorized }\"}").body?.string()
        assertFalse(JSONObject(body).getJSONObject("data").getBoolean("isAuthorized"))
    }

    @Test
    fun contextWithAuthorized() = JavalinTest.test(graphQLApp()) { _, client ->
        val body = client.post(
            GRAPHQL_PATH,
            "{\"query\": \"{ isAuthorized }\"}"
        ) { request -> request.header("Authorization", "Beare token") }.body?.string()
        assertTrue(JSONObject(body).getJSONObject("data").getBoolean("isAuthorized"))
    }

    @Test
    fun batchRequest() = JavalinTest.test(graphQLApp()) { _, client ->
        val batch = """[{"query": "{ hello }"}, {"query": "{ echo(message: \"$NEW_MESSAGE\") }"}]"""
        val response = client.post(GRAPHQL_PATH, batch)

        assertEquals(200, response.code)
        val responses = JSONArray(response.body?.string())
        assertEquals(2, responses.length())
        assertEquals(MESSAGE, responses.getJSONObject(0).getJSONObject("data").getString("hello"))
        assertEquals(NEW_MESSAGE, responses.getJSONObject(1).getJSONObject("data").getString("echo"))
    }

    @Test
    fun malformedBodyIsRejected() = JavalinTest.test(graphQLApp()) { _, client ->
        val response = client.post(GRAPHQL_PATH, "{ this is not json")

        assertEquals(400, response.code)
        assertEquals("Invalid request", JSONObject(response.body?.string()).getString("error"))
    }

    @Test
    fun emptyBodyIsRejected() = JavalinTest.test(graphQLApp()) { _, client ->
        assertEquals(400, client.post(GRAPHQL_PATH, "").code)
        assertEquals(400, client.post(GRAPHQL_PATH).code)
    }

    @Test
    fun getServesGraphiql() = JavalinTest.test(graphQLApp()) { _, client ->
        val response = client.get(GRAPHQL_PATH)

        assertEquals(200, response.code)
        assertThat(response.headers().get("Content-Type")?.first()).startsWith("text/html")
        assertThat(response.body?.string()).contains("id=\"graphiql\"")
    }

    @Test
    fun graphiqlCanBeTurnedOff() {
        val app = Javalin.create { config ->
            config.registerPlugin(
                GraphQLPluginBuilder(GRAPHQL_PATH)
                    .addPackage("io.javalin.plugin.graphql.helpers")
                    .register(QueryExample(MESSAGE))
                    .disableGraphiQL()
                    .build()
            )
        }

        JavalinTest.test(app) { _, client ->
            assertEquals(404, client.get(GRAPHQL_PATH).code)
            // the endpoint itself is untouched
            val body = client.post(GRAPHQL_PATH, "{\"query\": \"{ hello }\"}").body?.string()
            assertEquals(MESSAGE, JSONObject(body).getJSONObject("data").getString("hello"))
        }
    }

    @Test
    fun dataLoadersAreRegisteredAndBatched() {
        val dataLoader = UppercaseDataLoader()
        val app = Javalin.create { config ->
            config.registerPlugin(
                GraphQLPluginBuilder(GRAPHQL_PATH)
                    .addPackage("io.javalin.plugin.graphql.helpers")
                    .register(DataLoaderQueryExample())
                    .register(dataLoader)
                    .build()
            )
        }

        JavalinTest.test(app) { _, client ->
            val query = """{"query": "{ a: shout(word: \"one\") b: shout(word: \"two\") }"}"""
            val data = JSONObject(client.post(GRAPHQL_PATH, query).body?.string()).getJSONObject("data")

            assertEquals("ONE", data.getString("a"))
            assertEquals("TWO", data.getString("b"))
            // Both fields sit at the same level, so graphql-java dispatches one batch of two keys.
            assertEquals(1, dataLoader.batchCalls.get())
            assertEquals(listOf("one", "two"), dataLoader.lastKeys)
        }
    }

    /**
     * The default used to be the package `kotlin.Unit`, which is a class and matched nothing, so
     * the failure named a package the caller had never written. Javalin runs `Plugin.onStart`
     * from `Javalin.create`, so this fails while the app is being configured.
     */
    @Test
    fun aBuilderWithoutPackagesSaysSo() {
        assertThatThrownBy {
            Javalin.create { config ->
                config.registerPlugin(
                    GraphQLPluginBuilder(GRAPHQL_PATH).register(QueryExample(MESSAGE)).build()
                )
            }
        }.hasStackTraceContaining("addPackage")
    }
}
