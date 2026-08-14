[![Chat at https://discord.gg/sgak4e5NKv](https://img.shields.io/badge/chat-on%20Discord-%234cb697)](https://discord.gg/sgak4e5NKv)
[![Build](https://github.com/javalin/javalin-graphql/actions/workflows/build.yml/badge.svg)](https://github.com/javalin/javalin-graphql/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# About Javalin

* [:heart: Sponsor Javalin](https://github.com/sponsors/tipsy)
* The main project webpage is [javalin.io](https://javalin.io)
* Chat on Discord: https://discord.gg/sgak4e5NKv
* License summary: https://tldrlegal.com/license/apache-license-2.0-(apache-2.0)

## Javalin GraphQL

Serve a [GraphQL](https://graphql.org) schema, GraphiQL and subscriptions from a Javalin
application. Schemas are generated from Kotlin (or Java) classes by
[graphql-kotlin](https://github.com/ExpediaGroup/graphql-kotlin).

| | |
|---|---|
| Javalin | 7.x |
| graphql-kotlin | 10.x |
| JVM | 17+ |

> **Upgrading from 5.x?** The public API changed. See [Migrating from 5.x](#migrating-from-5x).

### Getting Started

Add the dependency:

<details>
    <summary>Gradle</summary>

```kotlin
dependencies {
    implementation("io.javalin.community.graphql:javalin-graphql:7.0.0")
}
```

</details>

<details>
    <summary>Maven</summary>

```xml
<dependency>
    <groupId>io.javalin.community.graphql</groupId>
    <artifactId>javalin-graphql</artifactId>
    <version>7.0.0</version>
</dependency>
```

</details>

Javalin 7 does not bundle an object mapper, and this plugin uses the one your application
configures. If you have not set one up, add `jackson-databind` (plus `jackson-module-kotlin`
if your schema classes are written in Kotlin).

Register the plugin:

```kotlin
val app = Javalin.create { config ->
    val options = GraphQLOptions("/graphql")
        .addPackage("com.example.schema")
        .register(QueryExample())
        .register(MutationExample())
        .register(SubscriptionExample())

    config.registerPlugin(GraphQLPlugin(options))
}

app.start(7070)
```

GraphQL is now served under `/graphql`: `GET` returns GraphiQL, `POST` executes queries and
mutations, and the WebSocket on the same path serves subscriptions.

### Queries

```kotlin
@GraphQLDescription("Query Example")
class QueryExample : QueryGraphql {
    fun hello(): String = "Hello world"

    fun demoData(@GraphQLDescription("awesome input") data: DemoData): DemoData = data
}
```

### Mutations

```kotlin
@GraphQLDescription("Mutation Example")
class MutationExample(private var message: String) : MutationGraphql {
    fun changeMessage(newMessage: String): String {
        message = newMessage
        return message
    }
}
```

### Subscriptions

A subscription resolver returns a kotlinx [`Flow`](https://kotlinlang.org/docs/flow.html):

```kotlin
@GraphQLDescription("Subscription Example")
class SubscriptionExample : SubscriptionGraphql {
    fun counter(): Flow<Int> = flow {
        while (true) {
            delay(100)
            emit(1)
        }
    }
}
```

Every class has to be registered when the plugin is built.

Subscriptions are served over the
[graphql-transport-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md)
protocol, so a standard client such as [graphql-ws](https://github.com/enisdenjo/graphql-ws)
can talk to the endpoint directly. The connection is acknowledged with `connection_init` /
`connection_ack`, each operation carries an id, and a subscription is cancelled by the
client's `complete` message or when the socket closes.

> Javalin exposes no API for WebSocket subprotocol negotiation, and the Jetty handshake
> underneath echoes back whichever subprotocol the client requested first, without checking
> it. A `graphql-transport-ws` client connects correctly; a client asking for a protocol this
> plugin does not speak — the legacy `graphql-ws` subprotocol, say — is told yes and then
> receives messages it cannot understand.

### Context

Build a context by putting values into graphql-java's `GraphQLContext`, keyed by class:

```kotlin
data class MyContext(val authorization: String?) {
    val isValid = authorization != null
}

class MyContextFactory : GraphQLContextFactory<Context> {
    override suspend fun generateContext(request: Context): GraphQLContext =
        mapOf(MyContext::class to MyContext(request.header("Authorization")))
            .toGraphQLContext()
}
```

Read it in a resolver through the `DataFetchingEnvironment`:

```kotlin
class QueryExample : QueryGraphql {
    fun isAuthorized(environment: DataFetchingEnvironment): Boolean =
        environment.graphQlContext.get<MyContext>(MyContext::class)?.isValid == true
}
```

Context factories are passed to the builder, one for HTTP and one for subscriptions:

```kotlin
val plugin = GraphQLPluginBuilder("/graphql", MyContextFactory(), MyWsContextFactory())
    .add("com.example.schema")
    .register(QueryExample())
    .build()

config.registerPlugin(plugin)
```

### JPMS

The artifact declares `Automatic-Module-Name: io.javalin.community.graphql`, so a modular
application can depend on it:

```java
requires io.javalin.community.graphql;
```

It does not ship a `module-info.java` yet. The graphql-kotlin artifacts declare neither a
module descriptor nor an `Automatic-Module-Name`, so they resolve as automatic modules named
after their file names; requiring those would freeze unstable names into the descriptor. See
[#5](https://github.com/javalin/javalin-graphql/issues/5).

### Design decisions

The reasoning behind the 7.0 design is recorded as ADRs in [`docs/adr`](../docs/adr):

| | |
|---|---|
| [ADR-001](../docs/adr/adr-001-context-model.md) | Context model follows graphql-java instead of a plugin type |
| [ADR-002](../docs/adr/adr-002-subscription-protocol.md) | Serve subscriptions over graphql-transport-ws |
| [ADR-003](../docs/adr/adr-003-json-mapping.md) | Parse GraphQL payloads without graphql-kotlin's sealed types |
| [ADR-004](../docs/adr/adr-004-jpms.md) | Declare an automatic module name instead of shipping module-info |

### Migrating from 5.x

**Registering the plugin.** `config.plugins.register(...)` became `config.registerPlugin(...)`.

**Context is no longer a type of yours.** graphql-kotlin removed its `GraphQLContext` marker
interface in favour of graphql-java's map-like `GraphQLContext`.

```diff
- data class MyContext(val authorization: String?) : GraphQLContext
+ data class MyContext(val authorization: String?)

- class MyContextFactory : GraphQLContextFactory<MyContext, Context> {
-     override suspend fun generateContext(request: Context): MyContext =
-         MyContext(request.header("Authorization"))
+ class MyContextFactory : GraphQLContextFactory<Context> {
+     override suspend fun generateContext(request: Context): GraphQLContext =
+         mapOf(MyContext::class to MyContext(request.header("Authorization")))
+             .toGraphQLContext()
  }
```

`GraphQLPluginBuilder` lost its context type parameter as a result.

**Context is no longer injected into resolvers.** A resolver parameter typed as your context
class is now treated as a GraphQL argument. Take a `DataFetchingEnvironment` instead:

```diff
- fun isAuthorized(context: MyContext?): Boolean = context?.isValid == true
+ fun isAuthorized(environment: DataFetchingEnvironment): Boolean =
+     environment.graphQlContext.get<MyContext>(MyContext::class)?.isValid == true
```

**Subscriptions return `Flow`, not `Publisher`.** Reactor is no longer a dependency.

```diff
- fun counter(): Flux<Int> = Flux.interval(Duration.ofMillis(100)).map { 1 }
+ fun counter(): Flow<Int> = flow { while (true) { delay(100); emit(1) } }
```

**Subscriptions speak graphql-transport-ws.** 5.x used an ad-hoc exchange — send a query
frame, receive bare result data — which matched no standard. A client now has to send
`connection_init` and `subscribe` messages. Off-the-shelf GraphQL clients do this for you.

**Removed:** `GraphQLRun` (use `GraphQLRequestHandler.executeSubscription`) and
`JavalinDataLoaderRegistryFactory` (use graphql-kotlin's `KotlinDataLoaderRegistryFactory`).
