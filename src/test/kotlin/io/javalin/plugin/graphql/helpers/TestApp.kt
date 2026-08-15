package io.javalin.plugin.graphql.helpers

import io.javalin.Javalin
import io.javalin.plugin.graphql.GraphQLPluginBuilder

internal const val GRAPHQL_PATH = "/graphql"
internal const val MESSAGE = "Hello World"
internal const val NEW_MESSAGE = "hi"

/**
 * A Javalin app serving the example schema in this package. Built per test rather than shared:
 * [MutationExample] is stateful.
 *
 * One package is enough — ClassGraph accepts sub-packages, so `...graphql.helpers` is covered.
 */
internal fun graphQLApp(): Javalin = Javalin.create { config ->
    config.registerPlugin(
        GraphQLPluginBuilder(GRAPHQL_PATH, ContextFactoryExample(), ContextWsFactoryExample())
            .addPackage("io.javalin.plugin.graphql")
            .register(QueryExample(MESSAGE))
            .register(MutationExample(MESSAGE))
            .register(SubscriptionExample())
            .build()
    )
}
