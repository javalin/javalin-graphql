package io.javalin.examples

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import io.javalin.Javalin
import io.javalin.plugin.graphql.GraphQLPluginBuilder
import io.javalin.plugin.graphql.graphql.QueryGraphql
import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// More documentation: https://expediagroup.github.io/graphql-kotlin/docs/getting-started
@GraphQLDescription("awesome data")
data class DemoData(
    @GraphQLDescription("key is mandatory")
    val key: String,
    @GraphQLDescription("The widget's value that can be `null`")
    val value: String?
)

@GraphQLDescription("Query Example")
@Suppress("FunctionOnlyReturningConstant") // it is an example schema
class QueryExample : QueryGraphql {
    fun hello(): String = "Hello world"

    fun demoData(@GraphQLDescription("awesome input") data: DemoData): DemoData = data
}

@GraphQLDescription("Subscriber Example")
class SubscriberExample : SubscriptionGraphql {
    fun number(): Flow<Int> = flowOf((1..10).random())
}


/** Run this and open http://localhost:7070/graphql to try the schema in GraphiQL. */
fun main() {
    val plugin = GraphQLPluginBuilder("/graphql")
        .addPackage("io.javalin.examples")
        .register(QueryExample())
        .register(SubscriberExample())
        .build()

    Javalin.create { config -> config.registerPlugin(plugin) }.start(7070)
}
