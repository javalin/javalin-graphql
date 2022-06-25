[![Chat at https://discord.gg/sgak4e5NKv](https://img.shields.io/badge/chat-on%20Discord-%234cb697)](https://discord.gg/sgak4e5NKv)
[![Test all JDKs on all OSes](https://github.com/javalin/javalin-graphql/actions/workflows/main.yml/badge.svg)](https://github.com/javalin/javalin-graphql/actions/workflows/main.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven](https://img.shields.io/maven-central/v/io.javalin/javalin.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.javalin%22%20AND%20a%3A%22javalin%22)

# About Javalin

* [:heart: Sponsor Javalin](https://github.com/sponsors/tipsy)
* The main project webpage is [javalin.io](https://javalin.io)
* Chat on Discord: https://discord.gg/sgak4e5NKv
* License summary: https://tldrlegal.com/license/apache-license-2.0-(apache-2.0)

## Javalin GraphQL

This plugin allows implementing the [GraphQL specification](https://graphql.org)
with some easy steps.

### Getting Started

Add the dependencies:

<details>
    <summary>Gradle setup for Javalin 4.x</summary>

```groovy
implementation("io.javalin:javalin-redoc-plugin:$openapi")
```

</details>


<details>
    <summary>Maven setup for Javalin 4.x</summary>

```xml
<dependency>
    <groupId>io.javalin</groupId>
    <artifactId>javalin-graphql</artifactId>
    <version>4.6.3</version>
</dependency>
```

</details>

Register the plugin:

```kotlin
val app = Javalin.create {
    val graphQLOption = GraphQLOptions("/graphql", ContextExample())
            .addPackage("io.javalin.examples")
            .register(QueryExample(message))
            .register(MutationExample(message))
            .register(SubscriptionExample())
            .context()
    it.registerPlugin(GraphQLPlugin(graphQLOption))
}

app.start()
```

The GraphQL is now available under the `/graphql` endpoint.

### Create Query

This section contains an overview of all the available to create queries.

```kotlin
@GraphQLDescription("Query Example")
class QueryExample : QueryGraphql {
    fun hello(): String = "Hello world"

    fun demoData(@GraphQLDescription("awesome input") data: DemoData): DemoData = data
}
```

After creating this class is necessary to register the class at the start of the plugin.

### Create Command

This section contains an overview of all the available to create commands.

```kotlin
@GraphQLDescription("Command Example")
class CommandExample : CommandGraphql {
    fun hello(): String = "Hello world"

    fun demoData(@GraphQLDescription("awesome input") data: DemoData): DemoData = data
}
```

After creating this class is necessary to register the class at the start of the plugin.

### Create Subscription

This section contains an overview of all the available to create a subscription.

```kotlin
@GraphQLDescription("Subscription Example")
class SubscriptionExample: SubscriptionGraphql {
    fun counter(): Flux<Int> = Flux.interval(Duration.ofMillis(100)).map { 1 }
}
```

After creating this class is necessary to register the class at the start of the plugin.

### Pass context

Sometimes it is necessary to pass the context in the method. You can create this context with this class.

```kotlin
class ContextExample {
    val globalEnvironment = "globalEnvironment"
}
```

After creating this class is necessary to register the class at the start of the plugin.

Then is possible to access this context with this annotation @GraphQLContext.

```kotlin
class QueryExample() : QueryGraphql {
    fun context(@GraphQLContext context: ContextExample): ContextExample {
        return context
    }
}
```
