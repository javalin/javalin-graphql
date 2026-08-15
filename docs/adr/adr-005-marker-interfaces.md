# ADR-005: Keep the QueryGraphql / MutationGraphql / SubscriptionGraphql markers

- **Status:** Accepted
- **Date:** 2026-08-15
- **Deciders:** @7agustibm
- **Related Components:** `graphql/QueryGraphql`, `graphql/MutationGraphql`,
  `graphql/SubscriptionGraphql`, `GraphQLPluginBuilder.register`

---

## Context and Problem Statement

The plugin ships three empty interfaces — `QueryGraphql`, `MutationGraphql` and
`SubscriptionGraphql` — that a resolver class has to implement before it can be registered.
They declare no members at all; each file is three lines.

graphql-kotlin does not need them. `toSchema` takes `TopLevelObject(anyObject)`, and everything
that describes a schema is an annotation on the functions, not on the class. What the three
interfaces actually do is make the builder's overloads resolvable: on the JVM,

```kotlin
fun register(vararg queries: Any)
fun register(vararg mutations: Any)      // same signature after erasure — does not compile
```

is one method three times over. A distinct element type per overload is what allows a single
`register` name.

7.0 already breaks the context model (ADR-001), the subscription protocol (ADR-002) and the
plugin's entry point, so if this concept is ever going to be dropped, this is the release where
it would cost users the least. graphql-kotlin also has its own equivalents in
`com.expediagroup.graphql.server.operations` — `Query`, `Mutation`, `Subscription` — which are
likewise empty markers, so adopting those is a third option. The question is which of the three
to take, and to stop rediscovering it at every review.

---

## Decision

Keep the three interfaces as they are, in `io.javalin.plugin.graphql.graphql`, with no
deprecation attached in 7.0.

They are documented as what they are: the discriminator that lets `register` be one name for
three kinds of top-level object, and a compile-time check that a class registered as a query
was written to be one.

---

## Alternatives Considered

- **Drop the markers and name the methods instead** — `registerQuery(vararg Any)`,
  `registerMutation(vararg Any)`, `registerSubscription(vararg Any)`.
  Pros: no plugin-specific type leaks into user code; any object can be a resolver, including
  one shared with a service built on another GraphQL server; the ambiguity of a class
  implementing two markers disappears.
  Cons: every resolver class *and* every registration call site changes, on top of the context
  and subscription migrations users already face in 7.0, for what is a naming preference;
  `registerQuery(vararg Any)` accepts literally anything, so registering the wrong object
  compiles and fails later during schema generation instead of at the call site.

- **Re-use graphql-kotlin's `Query` / `Mutation` / `Subscription`.**
  Pros: one fewer plugin-specific concept; upstream examples apply verbatim; identical
  mechanics, since those are empty markers too.
  Cons: the same breaking change to every resolver class, and it buys only a rename — the
  interfaces are equally empty. It also pins this plugin's public API to types that live in
  graphql-kotlin's *server* module, so an upstream reshuffle would break our users rather than
  just our internals.

- **Deprecate now, remove in 8.x.**
  Pros: signals a direction without breaking 7.0.
  Cons: a deprecation warning with no chosen replacement is noise on every resolver class in
  every user's build for a whole major version.

---

## Consequences

- **Positive**
    - Existing resolver classes compile unchanged, keeping the 7.0 migration to context,
      subscriptions and plugin registration.
    - `register` keeps one name, and passing a mutation object where a query belongs is a
      compile error rather than a schema-generation failure at start-up.
    - No part of the public API is tied to a graphql-kotlin package that upstream may
      reorganise.

- **Negative**
    - The plugin keeps three empty interfaces that exist because of JVM erasure, not because
      they mean anything in the domain. Every reader has to be told this, which is why this
      record exists.
    - Resolvers written for another graphql-kotlin server cannot be reused here without adding
      an `implements`, and vice versa.
    - A class implementing two markers makes `register(...)` ambiguous. The library reports
      nothing; the error appears at the call site and points at overload resolution rather than
      at the real cause.
    - The decision is one rename away from being reversed, so it will come back the next time
      the API is opened. This ADR is the reason it should not come back cheaply.

---

## References

- `GraphQLPluginBuilder.register` overloads
- `com.expediagroup.graphql.server.operations.Query` (graphql-kotlin 10)
- ADR-001, for the type parameter that *was* removed from the same class
