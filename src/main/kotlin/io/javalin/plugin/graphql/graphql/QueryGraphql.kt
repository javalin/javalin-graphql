package io.javalin.plugin.graphql.graphql

/**
 * Marks a class as holding top-level query resolvers, so that
 * `GraphQLPluginBuilder.register` can be one name for three kinds of top-level object: after
 * erasure the three overloads would otherwise be the same signature. graphql-kotlin itself
 * needs nothing of the sort — see ADR-005 for why this stays anyway.
 */
interface QueryGraphql
