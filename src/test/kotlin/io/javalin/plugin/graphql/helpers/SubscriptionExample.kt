package io.javalin.plugin.graphql.helpers

import io.javalin.plugin.graphql.graphql.SubscriptionGraphql
import reactor.core.publisher.Flux
import java.time.Duration

class SubscriptionExample : SubscriptionGraphql {

    companion object {
        const val anonymous_message = "anonymus"
    }

    fun counter(): Flux<Int> = Flux.interval(Duration.ofSeconds(1)).map { 1 }

    fun counterUser(contextExample: ContextExample?) = Flux.interval(Duration.ofSeconds(1))
        .map {
            val user =
                if (contextExample != null && contextExample.isValid) contextExample.authorization!! else anonymous_message
            "$user ~> 1"
        }
}
