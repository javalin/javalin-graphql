package io.javalin.plugin.graphql;

import io.javalin.Javalin;
import io.javalin.examples.QueryExampleJVM;
import io.javalin.plugin.graphql.context.EmptyGraphQLContextFactory;
import io.javalin.plugin.graphql.context.EmptyWsGraphQLContextFactory;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Java-facing API. Kotlin's default arguments do not exist in Java, so the one-argument
 * constructor compiles only because of {@code @JvmOverloads}: this test is what keeps it there.
 */
class JavaApiTest {

    private static final String PATH = "/graphql";
    private static final String QUERY = "{\"query\": \"{ hello }\"}";

    @Test
    void builds_the_plugin_with_the_default_context_factories() {
        GraphQLPlugin plugin = new GraphQLPluginBuilder(PATH)
            .addPackage("io.javalin.examples")
            .register(new QueryExampleJVM())
            .build();

        JavalinTest.test(Javalin.create(config -> config.registerPlugin(plugin)), (server, client) ->
            assertThat(client.post(PATH, QUERY).body().string()).contains("Hello world")
        );
    }

    @Test
    void builds_the_plugin_with_explicit_context_factories() {
        GraphQLPlugin plugin = new GraphQLPluginBuilder(
            PATH, new EmptyGraphQLContextFactory(), new EmptyWsGraphQLContextFactory()
        )
            .addPackage("io.javalin.examples")
            .register(new QueryExampleJVM())
            .build();

        JavalinTest.test(Javalin.create(config -> config.registerPlugin(plugin)), (server, client) ->
            assertThat(client.post(PATH, QUERY).body().string()).contains("Hello world")
        );
    }
}
