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
 * Builds the GraphQL schema and the pieces the plugin needs to serve it.
 *
 * Note that since graphql-kotlin 6 the context is graphql-java's map-like `GraphQLContext`,
 * so this class no longer carries a context type parameter.
 */
class GraphQLPluginBuilder(
    val path: String,
    val contextFactory: GraphQLContextFactory<Context>,
    val contextWsFactory: GraphQLContextFactory<WsMessageContext>
) {

    private var graphql: GraphQL? = null
    private var queries: MutableList<TopLevelObject> = mutableListOf()
    private var mutations: MutableList<TopLevelObject> = mutableListOf()
    private var subscriptions: MutableList<TopLevelObject> = mutableListOf()
    private var packages: MutableList<String> = mutableListOf("kotlin.Unit")
    private val dataLoaders: MutableList<KotlinDataLoader<*, *>> = mutableListOf()

    companion object {
        fun create(options: GraphQLOptions): GraphQLPluginBuilder {
            val graphQLPluginBuilder = GraphQLPluginBuilder(options.path, EmptyGraphQLContextFactory(), EmptyWsGraphQLContextFactory())
            graphQLPluginBuilder.queries = options.queries
            graphQLPluginBuilder.mutations = options.mutations
            graphQLPluginBuilder.subscriptions = options.subscriptions
            graphQLPluginBuilder.packages = options.packages
            return graphQLPluginBuilder
        }
    }

    fun add(aPackage: String) = apply { packages.add(aPackage) }

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

    internal fun getSchema(): GraphQL {
        if (graphql == null) {
            graphql = GraphQL.newGraphQL(
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
                .build()!!
        }

        return graphql!!
    }

    internal fun toKotlinDataLoaderRegistryFactory() = KotlinDataLoaderRegistryFactory(dataLoaders)

    /** Built once: a subscription message must not rebuild the handler on every frame. */
    internal val requestHandler: GraphQLRequestHandler by lazy {
        GraphQLRequestHandler(getSchema(), toKotlinDataLoaderRegistryFactory())
    }
}
