# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The major version
tracks Javalin's: 7.x targets Javalin 7.x. Releases up to 5.x predate this file.

## [Unreleased]

## [7.0.0] - unreleased

The first release for Javalin 7 and graphql-kotlin 10. Everything under *Changed* and *Removed*
is a breaking change; the README has a
[migration guide](.github/README.md#migrating-from-5x) and `docs/adr` has the reasoning.

### Added

- Subscriptions are served over the
  [graphql-transport-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md)
  protocol: `connection_init` / `connection_ack`, `ping` / `pong`, `subscribe` with an operation
  id, `next`, `error`, `complete`, and the close codes 4400, 4401, 4409 and 4429 (ADR-002).
- Several subscriptions can run concurrently on one connection, each cancellable by id with
  `complete`, and all of them are cancelled when the socket closes or the server stops.
- `disableGraphiQL()`, for an application that does not want to publish a schema explorer.
- `Automatic-Module-Name: io.javalin.community.graphql`, so a modular application can
  `requires` the plugin ([#5](https://github.com/javalin/javalin-graphql/issues/5), ADR-004).
- Architecture decision records under `docs/adr`.
- Continuous integration: build and test on JDK 17 and 21 across Linux, macOS and Windows,
  plus detekt.
- The published version is derived from the release tag.

### Changed

- Javalin 7.2.2, graphql-kotlin 10.2.0, JVM baseline 17.
- `config.plugins.register(...)` is now `config.registerPlugin(...)`.
- The context is graphql-java's map-like `GraphQLContext` instead of a type of yours, and a
  resolver reads it from the `DataFetchingEnvironment` rather than receiving it as a parameter
  (ADR-001). `GraphQLPluginBuilder` lost its context type parameter.
- The context factories are now optional constructor arguments of `GraphQLPluginBuilder`, and
  the constructor is annotated `@JvmOverloads` so Java can leave them out.
- `GraphQLPluginBuilder.add(...)` is now `addPackage(...)`, and at least one package is
  required.
- Subscription resolvers return a kotlinx `Flow` instead of a reactive-streams `Publisher`;
  Reactor is no longer a dependency.
- GraphiQL is pinned to 4.1.2 with React 18.3.1, both by version and by SRI hash, and its
  fetcher now points at the subscription WebSocket as well as the POST endpoint.
- Payloads are parsed without graphql-kotlin's sealed types, so the plugin works with whatever
  `JsonMapper` the application configured (ADR-003).
- The public API dependencies are exposed as `api` rather than `implementation`, so the
  examples in the README compile against the artifact.
- The Kotlin sources moved from `src/main/java` to `src/main/kotlin`.

### Removed

- `GraphQLOptions`, together with `GraphQLPluginBuilder.create(options)` and the
  `GraphQLPlugin(options)` constructor. `GraphQLPluginBuilder` is the only entry point.
- `middleHandler`, `wsMiddleHandler`, `setMiddleHandler`, `setWSMiddleHandler` and the
  `context` argument of `GraphQLOptions`: nothing ever read them, so a `middleHandler` set to
  authorize requests was silently dropped. Javalin's `before` and `beforeWs` do that job.
- `GraphQLRun` — use `GraphQLRequestHandler.executeSubscription`.
- `JavalinDataLoaderRegistryFactory` — use graphql-kotlin's `KotlinDataLoaderRegistryFactory`.
- The ad-hoc 5.x WebSocket exchange (a bare query frame answered with bare result data).

### Fixed

- A subscription no longer pins a Jetty thread for its whole lifetime, and stopping the server
  cancels the ones still running instead of leaking their coroutines.
- A malformed payload is answered with a 400 over HTTP and a 4400 close over WebSocket instead
  of a 500 or a dropped connection.
- A resolver that throws is reported as a protocol `error` frame for its operation instead of
  tearing down the whole connection.
- GraphiQL loaded unversioned scripts from a CDN, so it broke as soon as upstream released an
  incompatible major, and it requested a `renderExample.js` that does not exist.
- Building the plugin without registering a package now fails saying so, instead of scanning
  the package `kotlin.Unit`, which is a class and matches nothing.
- The published `javadoc` jar contained nothing but a manifest, because it was built from the
  `javadoc` task and every source file is Kotlin. It is now rendered by Dokka.

Set the date and add the release link below when the `v7.0.0` tag is cut.

[Unreleased]: https://github.com/javalin/javalin-graphql/commits/main/
