package io.javalin.plugin.graphql.helpers

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import io.javalin.plugin.graphql.graphql.QueryGraphql
import java.util.concurrent.CompletableFuture

/** Resolves through a data loader rather than directly, so the registry has to reach execution. */
class DataLoaderQueryExample : QueryGraphql {
    fun shout(word: String, environment: DataFetchingEnvironment): CompletableFuture<String> =
        environment.getValueFromDataLoader(UppercaseDataLoader.NAME, word)
}
