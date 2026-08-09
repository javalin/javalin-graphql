# ADR-001: Context model follows graphql-java instead of a plugin type

- **Status:** Accepted
- **Date:** 2026-08-09
- **Deciders:** @7agustibm
- **Related Components:** `GraphQLPluginBuilder`, `context/*ContextFactory`, resolvers

---

## Context and Problem Statement

Up to 5.x the plugin let an application define its own context class implementing
graphql-kotlin's `GraphQLContext` marker interface. The builder carried that class as a type
parameter (`GraphQLPluginBuilder<out T : GraphQLContext>`), and a resolver received the
context simply by declaring a parameter of that type.

graphql-kotlin 6 deleted the marker interface in favour of graphql-java's `GraphQLContext`, a
map keyed by arbitrary objects. In graphql-kotlin 10, `FunctionDataFetcher.mapParameterToValue`
resolves only two kinds of resolver parameter: a `DataFetchingEnvironment`, or a GraphQL
argument. There is no context annotation and no context injection left.

So the plugin cannot keep its old context model even if it wanted to: the machinery it rested
on is gone upstream.

---

## Decision

Follow upstream rather than rebuild the old model on top of it.

- A context factory is `GraphQLContextFactory<Request>` and returns a `graphql.GraphQLContext`,
  built from a map. The convention this project documents is to key entries by class:
  `mapOf(MyContext::class to MyContext(...)).toGraphQLContext()`.
- `GraphQLPluginBuilder` loses its context type parameter.
- A resolver that needs the context takes a `DataFetchingEnvironment` and reads
  `environment.graphQlContext.get<MyContext>(MyContext::class)`.

---

## Alternatives Considered

- **Reimplement context injection in the plugin**, with a custom
  `KotlinDataFetcherFactoryProvider` that injects a registered context type into resolver
  parameters.
  Pros: existing users' resolvers keep compiling; the nicer API survives.
  Cons: the plugin would own a fork of graphql-kotlin's parameter resolution and have to track
  it across releases; the behaviour would diverge from every other graphql-kotlin server, so
  examples and documentation from upstream would be wrong here.

- **Keep a plugin-owned context wrapper** stored under a fixed key, so users still write their
  own class but the plugin hides the map.
  Pros: a smaller diff for users.
  Cons: hides the real model without removing it — a resolver still cannot receive the context
  as a parameter, so the leaky part stays and the wrapper only adds a layer to explain.

- **Follow upstream** (chosen).
  Pros: no forked machinery; upstream documentation applies directly; the type parameter that
  existed only to carry the context disappears.
  Cons: a breaking change for every existing user, in two places (factory and resolvers).

---

## Consequences

- **Positive**
    - No plugin-specific reflection or parameter-resolution code to maintain.
    - `GraphQLPluginBuilder` is simpler: one fewer type parameter and no variance to reason about.
    - Context now works the same way here as in the Ktor and Spring integrations.

- **Negative**
    - Breaking change: both context factories and every resolver reading context must change.
    - The context is no longer type-checked at the resolver boundary — a wrong key returns null
      rather than failing to compile.
    - Keying by class is a convention this project documents, not something the type system
      enforces.

---

## References

- `com.expediagroup.graphql.generator.execution.FunctionDataFetcher` (graphql-kotlin 10)
- Migration section of the README
- Commit `731cb53`
