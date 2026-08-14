package io.javalin.plugin.graphql.helpers

/**
 * Plain data class: since graphql-kotlin 6 there is no marker interface to implement.
 * The instance is stored in the graphql-java `GraphQLContext` keyed by its class.
 */
data class ContextExample(val authorization: String? = null) {
    val isValid = authorization != null
}
