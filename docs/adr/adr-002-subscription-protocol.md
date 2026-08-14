# ADR-002: Serve subscriptions over graphql-transport-ws

- **Status:** Accepted
- **Date:** 2026-08-09
- **Deciders:** @7agustibm
- **Related Components:** `GraphQLHandler`, `GraphQLPlugin` WebSocket route

---

## Context and Problem Statement

The 5.x WebSocket endpoint spoke an exchange invented for this plugin: the client sent a frame
containing a GraphQL query, and the server sent back bare result data. It carried no operation
id, had no handshake, no completion signal and no error frame.

That matched no standard, so no off-the-shelf GraphQL client could use it — a subscriber had to
be written by hand against this plugin. It also made two things impossible by construction:
running more than one subscription per connection, and cancelling one.

Separately, the implementation ran `runBlocking` around the collector inside `onMessage`, so a
long-running subscription pinned a Jetty thread for its whole lifetime.

---

## Decision

Implement [graphql-transport-ws](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md):
`connection_init` / `connection_ack`, `ping` / `pong`, `subscribe` carrying an operation id,
`next`, `error`, `complete`, and the protocol's close codes (4400, 4401, 4409, 4429).

Message types are reused from graphql-kotlin (`com.expediagroup.graphql.server.types`), which
ships them in the server module independently of the Ktor and Spring integrations. Incoming
frames are dispatched on their `type` field rather than deserialized into the sealed
`GraphQLSubscriptionMessage` — see ADR-003 for why.

Each subscription runs in its own coroutine, registered under its operation id, and is
cancelled by the client's `complete` message or when the connection closes.

---

## Alternatives Considered

- **Keep the ad-hoc exchange.**
  Pros: no change for the handful of users who wrote a client against it; no test rewrite.
  Cons: keeps the module unusable from any standard client, and keeps multiplexing and
  cancellation off the table. Preserves compatibility with something that arguably should not
  have existed.

- **Implement the legacy `subscriptions-transport-ws` (Apollo) protocol.**
  Pros: still widely deployed in older clients.
  Cons: deprecated by its own author in favour of graphql-transport-ws; would be adopting a
  dead protocol in a major version whose whole point is to catch up.

- **Support both protocols, selected by subprotocol.**
  Pros: widest client compatibility.
  Cons: Javalin exposes no subprotocol negotiation (see Consequences), so there is no reliable
  signal to select on; it would double the protocol surface of a module that currently has no
  maintainer.

---

## Consequences

- **Positive**
    - Standard clients such as `graphql-ws` work against the endpoint without custom code.
    - Multiple concurrent subscriptions per connection, each cancellable by id.
    - The `runBlocking` thread-pinning defect disappears: subscriptions are cancelled rather
      than left to run.
    - Errors reach the client as protocol `error` frames instead of tearing down the socket.

- **Negative**
    - Breaking change for anyone who wrote a client against the 5.x exchange.
    - The plugin now owns a protocol state machine — connection acknowledgement and per-operation
      bookkeeping — which is more code to maintain than the previous handler.
    - Javalin exposes no WebSocket subprotocol negotiation, and the Jetty handshake beneath
      echoes back whichever subprotocol the client asked for first, without validating it. A
      `graphql-transport-ws` client connects correctly, but a client requesting a protocol this
      handler does not speak is told yes and then receives messages it cannot understand.
      Rejecting those requires a change in Javalin.

---

## References

- https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
- `com.expediagroup.graphql.server.types.GraphQLSubscriptionMessage`
- Commit `5319344`, correction in `973fe58`
