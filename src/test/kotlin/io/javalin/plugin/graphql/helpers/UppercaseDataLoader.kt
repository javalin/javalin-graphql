package io.javalin.plugin.graphql.helpers

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/** Counts its batch calls, so a test can tell "registered" from "registered and batching". */
internal class UppercaseDataLoader : KotlinDataLoader<String, String> {

    companion object {
        const val NAME = "UppercaseDataLoader"
    }

    val batchCalls = AtomicInteger()

    @Volatile
    var lastKeys: List<String> = emptyList()

    override val dataLoaderName: String = NAME

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, String> =
        DataLoaderFactory.newDataLoader(
            // Explicit SAM type: the lambda alone is ambiguous with BatchLoaderWithContext.
            BatchLoader<String, String> { keys ->
                batchCalls.incrementAndGet()
                lastKeys = keys.toList()
                CompletableFuture.completedFuture(keys.map { it.uppercase() })
            }
        )
}
