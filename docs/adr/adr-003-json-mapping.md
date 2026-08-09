# ADR-003: Parse GraphQL payloads without graphql-kotlin's sealed types

- **Status:** Accepted
- **Date:** 2026-08-09
- **Deciders:** @7agustibm
- **Related Components:** `JavalinGraphQLRequestParser`, `GraphQLHandler`

---

## Context and Problem Statement

graphql-kotlin models an incoming payload as a sealed class — `GraphQLServerRequest` over HTTP,
`GraphQLSubscriptionMessage` over WebSocket — with a custom deserializer that picks the concrete
subtype.

Those deserializers are declared with **Jackson 3** annotations (`tools.jackson.databind`) and
fastjson2. Javalin's default `JsonMapper` is **Jackson 2** (`com.fasterxml.jackson`). Jackson 2
cannot see a Jackson 3 deserializer, so asking Javalin's mapper for the sealed type fails: every
query and mutation returned an error until this was addressed.

A plugin sits inside somebody else's application. Whatever it does here, it must not dictate
which JSON library that application uses — Javalin deliberately lets the mapper be swapped.

---

## Decision

Never ask the mapper for the sealed type. Determine the concrete shape from the payload and ask
the mapper for that:

- HTTP: a body starting with `[` is a `GraphQLBatchRequest`, anything else a `GraphQLRequest`.
- WebSocket: read the `type` field and dispatch to the matching handler.

All deserialization still goes through `ctx.bodyAsClass` / `ctx.messageAsClass`, so it uses
whatever `JsonMapper` the application configured. Serialization is unaffected: the response and
message types carry Jackson 2 annotations and serialize correctly.

A malformed payload is logged at debug and answered with the protocol's own error — a 400 over
HTTP, a 4400 close over WebSocket — never a 500.

---

## Alternatives Considered

- **Depend on Jackson 3 and use it directly in the parser.**
  Pros: graphql-kotlin's deserializers work as intended; less code here.
  Cons: bypasses the application's configured mapper, so a request would be parsed by one
  library and the response written by another; adds a hard JSON dependency to a plugin whose
  host already has one.

- **Require applications to configure a Jackson 3 mapper in Javalin.**
  Pros: no dispatch code at all.
  Cons: imposes a JSON library choice on every user of the plugin for an internal reason, and
  Javalin's own defaults would stop working out of the box.

- **Wait for graphql-kotlin to ship Jackson 2 deserializers.**
  Pros: the problem disappears upstream.
  Cons: the module does not work at all in the meantime, and it depends on somebody else's
  roadmap.

---

## Consequences

- **Positive**
    - The plugin works with any `JsonMapper` the host application configures.
    - No JSON library is added to the dependency tree.
    - Failures at the parsing boundary produce protocol-correct errors instead of 500s.

- **Negative**
    - The plugin owns a small amount of dispatch logic that duplicates what upstream's
      deserializers do, and it has to follow upstream if the message set changes.
    - Batch detection is by first character rather than by parsing, which assumes the body is
      JSON — acceptable, since anything else fails immediately afterwards.
    - The broad `catch (Exception)` at both boundaries is deliberate and marked as such; it is
      suppressed in detekt with the reason written at the call site.

---

## References

- `com.expediagroup.graphql.server.types.GraphQLServerRequest` (Jackson 3 + fastjson2 annotations)
- `io.javalin.json.JavalinJackson`
- Commits `731cb53`, `5319344`
