# ADR-004: Declare an automatic module name instead of shipping module-info

- **Status:** Accepted
- **Date:** 2026-08-09
- **Deciders:** @7agustibm
- **Related Components:** `build.gradle.kts` jar manifest

---

## Context and Problem Statement

Issue [#5](https://github.com/javalin/javalin-graphql/issues/5) asks whether the plugin can be
used from a Java project using JPMS. PR [#6](https://github.com/javalin/javalin-graphql/pull/6)
answered with a `module-info.java` placed at the repository root, where no build step compiles
it; it also required `com.expediagroup.graphql.generator.annotations`, which is not a module
name that exists. It was never merged, and it would not have worked if it had been.

The real obstacle is upstream. Of the dependencies this module needs:

| Dependency | Module name | Kind |
|---|---|---|
| `io.javalin:javalin` | `io.javalin` | proper `module-info` |
| `kotlin-stdlib`, `kotlinx-coroutines-core`, `slf4j-api` | declared | proper `module-info` |
| `graphql-java` | `com.graphqljava` | automatic, vendor-declared |
| `graphql-kotlin-server`, `-schema-generator`, `-dataloader` | **none** | automatic, **derived from file name** |

The three graphql-kotlin artifacts ship neither a module descriptor nor an
`Automatic-Module-Name`, so the JVM derives `graphql.kotlin.server` and friends from the jar
file names. Requiring those in our descriptor would write unstable names into a published
contract: if ExpediaGroup later declares proper names, every consumer breaks.

This is not theoretical. Gradle refuses to place such jars on the module path at all, so a
`module-info.java` requiring them does not even compile — verified.

---

## Decision

Declare `Automatic-Module-Name: io.javalin.community.graphql` in the jar manifest and ship no
`module-info.java`.

A modular application can `requires io.javalin.community.graphql` today, and the name is under
this project's control, so it stays stable when a full descriptor becomes possible.

Revisit when graphql-kotlin declares module names.

---

## Alternatives Considered

- **Ship a full `module-info.java` requiring the automatic modules.**
  Pros: a genuine named module; enables `jlink` for consumers.
  Cons: freezes filename-derived names into a published artifact, breaking consumers when
  upstream changes; does not compile under Gradle without forcing jars onto the module path by
  hand.

- **Do nothing, as today.**
  Pros: no work.
  Cons: leaves #5 unanswered when a useful partial answer exists; without a declared name the
  module name would be derived from *our* file name, which changes with the artifact id.

- **Vendor or shade the graphql-kotlin classes to control their module names.**
  Pros: full modularity possible.
  Cons: wildly disproportionate — the module would own a copy of its main dependency to satisfy
  a packaging concern.

---

## Consequences

- **Positive**
    - Modular applications can depend on the plugin now, with a stable name.
    - No unstable third-party names are baked into a published artifact.
    - #5 and #6 get an answer with a reason, and a named condition for revisiting.

- **Negative**
    - The module is an automatic module, so it reads the unnamed module and cannot be used with
      `jlink`.
    - Resolution of the issue depends on an upstream project, on no known schedule.

---

## References

- https://github.com/javalin/javalin-graphql/issues/5
- https://github.com/javalin/javalin-graphql/pull/6
- Commit `51f06cc`
