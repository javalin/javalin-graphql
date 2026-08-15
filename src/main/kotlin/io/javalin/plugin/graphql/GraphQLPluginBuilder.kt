package io.javalin.plugin.graphql

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import graphql.GraphQL
import io.javalin.http.Context
import io.javalin.plugin.graphql.context.EmptyGraphQLContextFactory
import io.javalin.plugin.graphql.context.EmptyWsGraphQLContextFactory
import io.javalin.plugin.graphql.graphql.MutationGraphql
import io.javalin.plugin.graphql.graphql.QueryGraphql
import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import io.javalin.websocket.WsMessageContext

/**
 * Builds the GraphQL schema and the pieces the plugin needs to serve it. This is the only way
 * to configure the plugin:
 *
 * ```
 * config.registerPlugin(
 *     GraphQLPluginBuilder("/graphql")
 *         .addPackage("com.example.schema")
 *         .register(QueryExample())
 *         .build()
 * )
 * ```
 *
 * Both context factories are optional and default to an empty `GraphQLContext`. The
 * `@JvmOverloads` is what makes `new GraphQLPluginBuilder("/graphql")` compile from Java,
 * where Kotlin's default arguments do not exist. Java callers realistically use the defaults:
 * `GraphQLContextFactory.generateContext` is a `suspend` function, which is not implementable
 * from Java in practice.
 *
 * Note that since graphql-kotlin 6 the context is graphql-java's map-like `GraphQLContext`,
 * so this class no longer carries a context type parameter (ADR-001).
 */
class GraphQLPluginBuilder @JvmOverloads constructor(
    val path: String,
    val contextFactory: GraphQLContextFactory<Context> = EmptyGraphQLContextFactory(),
    val contextWsFactory: GraphQLContextFactory<WsMessageContext> = EmptyWsGraphQLContextFactory()
) {

    private val queries: MutableList<TopLevelObject> = mutableListOf()
    private val mutations: MutableList<TopLevelObject> = mutableListOf()
    private val subscriptions: MutableList<TopLevelObject> = mutableListOf()
    private val packages: MutableList<String> = mutableListOf()
    private val dataLoaders: MutableList<KotlinDataLoader<*, *>> = mutableListOf()

    /** Whether `GET <path>` serves GraphiQL. See [disableGraphiQL]. */
    internal var graphiQLEnabled: Boolean = true
        private set

    /**
     * Adds a package for graphql-kotlin to scan when it resolves interface and union subtypes.
     * Sub-packages are included. At least one package is required.
     */
    fun addPackage(aPackage: String) = apply { packages.add(aPackage) }

    /**
     * Stops the plugin from serving GraphiQL, leaving only the `POST` endpoint and the
     * subscription WebSocket. An application that does not want to publish a schema explorer
     * has no other way to say so, since the routes are registered by the plugin itself.
     */
    fun disableGraphiQL() = apply { graphiQLEnabled = false }

    fun register(vararg dataLoaders: KotlinDataLoader<*, *>) = apply { this.dataLoaders.addAll(dataLoaders) }

    fun register(vararg queries: QueryGraphql) = apply {
        this.queries.addAll(queries.map { TopLevelObject(it) })
    }

    fun register(vararg mutations: MutationGraphql) = apply {
        this.mutations.addAll(mutations.map { TopLevelObject(it) })
    }

    fun register(vararg subscriptions: SubscriptionGraphql) = apply {
        this.subscriptions.addAll(subscriptions.map { TopLevelObject(it) })
    }

    fun build() = GraphQLPlugin(this)

    /**
     * Built once, on first use: generating the schema scans the classpath, and neither a
     * request nor a subscription frame may pay for that more than once.
     */
    internal val schema: GraphQL by lazy {
        // graphql-kotlin fails an empty scan with `InvalidPackagesException`, which names a
        // package the caller never wrote. Say what is actually missing instead.
        check(packages.isNotEmpty()) {
            "No package to scan. Call addPackage(\"your.schema.package\") before building the plugin."
        }
        GraphQL.newGraphQL(
            toSchema(
                // The Flow hooks are what let a subscription resolver return a kotlinx Flow.
                config = SchemaGeneratorConfig(
                    supportedPackages = packages,
                    hooks = FlowSubscriptionSchemaGeneratorHooks()
                ),
                queries = queries,
                mutations = mutations,
                subscriptions = subscriptions
            )
        )
            .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy())
            .build()
    }

    /** Built once: a subscription message must not rebuild the handler on every frame. */
    internal val requestHandler: GraphQLRequestHandler by lazy {
        GraphQLRequestHandler(schema, KotlinDataLoaderRegistryFactory(dataLoaders))
    }
}
