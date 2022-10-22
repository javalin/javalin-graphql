package io.javalin.examples

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import io.javalin.Javalin
import io.javalin.plugin.graphql.GraphQLOptions
import io.javalin.plugin.graphql.GraphQLPlugin
import io.javalin.plugin.graphql.graphql.QueryGraphql
import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import reactor.core.publisher.Flux

// More documentation: https://expediagroup.github.io/graphql-kotlin/docs/getting-started
@GraphQLDescription("awesome data")
data class DemoData(
    @GraphQLDescription("key is mandatory")
    val key: String,
    @GraphQLDescription("The widget's value that can be `null`")
    val value: String?
)

@GraphQLDescription("Query Example")
class QueryExample : QueryGraphql {
    fun hello(): String = "Hello world"

    fun demoData(@GraphQLDescription("awesome input") data: DemoData): DemoData = data
}

@GraphQLDescription("Subscriber Example")
class SubscriberExample : SubscriptionGraphql {
    fun number() = Flux.just((1..10).random())
}


fun main() {
    val app = Javalin.create { config ->
        val graphQLOption = GraphQLOptions("/graphql")
            .addPackage("io.javalin.examples")
            .register(QueryExample())
            .register(SubscriberExample())
        config.plugins.register(GraphQLPlugin(graphQLOption))
    }

    app.start()
}
