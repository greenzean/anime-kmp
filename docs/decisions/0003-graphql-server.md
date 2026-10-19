# 0003: GraphQL server library

## Status
Accepted (M0-7)

## Context
The Ktor server (Ktor 3.6.0, Kotlin 2.4.20, Netty) needs a GraphQL library. Candidates were checked on 2026-10-19. Hello-world queries (`{ hello }`) were run in `testApplication` for graphql-kotlin and graphql-java. KGraphQL and Apollo Kotlin Execution were evaluated from docs and release data only.

| | graphql-kotlin (Expedia) | graphql-java | KGraphQL (stuebingerb fork) | Apollo Kotlin Execution |
|---|---|---|---|---|
| Version | 11.0.0 (2026-10-07) | 26.1 | 0.42.0 (2026-07-13) | 0.1.2 (2025-12-08), experimental |
| Maintenance | Active (stable plus alphas Aug to Oct 2026) | Active | Active fork; the original aPureBase repo is abandoned | Low activity, 9 stars |
| Ktor 3 | Dedicated `graphql-kotlin-ktor-server`, built against Ktor 3.4.1 | No coupling, hand-written route | Built against Ktor 3.5.1 and Kotlin 2.4.0; Ktor artifact name not verified | Docs use Ktor 3.0.0; needs KSP matching Kotlin 2.4.20, not checked |
| Schema | Code-first (from Kotlin classes) | Schema-first (SDL plus wiring) | Code-first DSL | Generated from annotated code via KSP plugin |
| Subscriptions | Yes, `Flow` over Ktor WebSockets (`graphql-transport-ws`) | Reactive Streams `Publisher`; transport is hand-written | Not verified | Yes |
| Hello-world | Passed | Passed (needs hand-written route and JSON handling) | Not run | Not run |

## Decision
Use **graphql-kotlin** (`com.expediagroup:graphql-kotlin-ktor-server`).

- It is the only candidate with a maintained Ktor 3 module, ready-made routes, and `Flow`-based subscriptions.
- The hello-world needed about 10 lines and returned `{"data":{"hello":"world"}}`.
- It runs on graphql-java underneath, so dropping to graphql-java stays possible.

## Consequences
- The schema is generated from Kotlin types, so the SDL is an output, not the source. M0-8 decides whether to export that SDL for the Apollo client codegen.
- 11.0.0 builds against Ktor 3.4.1 and Kotlin 2.3.0. Compatibility with Ktor 3.6.0 and Kotlin 2.4.20 was exercised only by the hello-world test; a full-feature check is part of the implementation ticket.
- Subscriptions are supported but were not prototyped.
- Fallback if graphql-kotlin stalls: graphql-java with a hand-written Ktor route.
