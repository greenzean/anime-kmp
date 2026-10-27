---
name: graphql-schema
description: |
  GraphQL contract conventions for anime-kmp - graphql-kotlin code-first schema in `server/`, the committed SDL file, the schema export/drift test, Apollo Kotlin operations in `shared/`, and how an API change is split between the server, the app back end and the views. Use this skill whenever adding or changing a query, mutation, subscription, GraphQL type, scalar, or Apollo operation. Trigger on phrases like "GraphQL", "schema", "SDL", "add a query", "add a mutation", "resolver", "Apollo operation", ".graphql file", "schema.graphqls", or "regenerate schema".
---

# GraphQL schema conventions

Decisions: ADR 0003 (graphql-kotlin) and ADR 0004 (schema as contract). The schema is code-first on the server, and the committed SDL is the contract the client compiles against. `server` never depends on `shared`.

## Where things live

```
server/src/main/kotlin/com/greenzean/animekmp/server/graphql/
  <Feature>Query.kt / <Feature>Mutation.kt
  model/
server/src/test/.../graphql/SchemaExportTest.kt

shared/src/commonMain/graphql/
  schema.graphqls
  <Name>Query.graphql
  <Name>Mutation.graphql
```

`Query` and `Mutation` classes implement the graphql-kotlin interfaces, and `model/` holds the GraphQL output and input types (server-owned). `SchemaExportTest` exports the SDL and checks for drift. `schema.graphqls` is written in the plan as part of the Contract and checked by the drift test; the `.graphql` files are the client operations, one per file.

- Apollo generates client models into `com.greenzean.animekmp.shared.graphql`. Map them to domain/DB models in the data layer and never expose them to the UI.
- GraphQL types are separate from DB rows and from MAL DTOs. Map between them in the server.

## Changing the API

The SDL change is part of the ticket's Contract, so the parts run in parallel:

1. Plan: write the SDL change into `schema.graphqls`. It is the one time the file is edited by hand.
2. Server (`server` agent): types plus `Query`/`Mutation` classes whose printed schema equals the committed SDL.
3. App back end (`shared-kmp` agent): `.graphql` operations in `shared`, then the repository/sync code, built against the committed SDL.
4. Views and tests (`compose-ui`, `tester`) build from the Contract.
5. After all the agents finish, the main session runs `server-tests :server:test`, which includes the drift test. If it fails, run `server-tests -PupdateSchema :server:test` (it does not deploy, because the first argument is not `:server:test`) and compare the new `schema.graphqls` with the Contract: accept it only when the difference is ordering or formatting, otherwise fix the server code.

The drift test in `server-tests :server:test` fails if `schema.graphqls` no longer matches the server code. Decision: ADR 0015.

## Rules

- Only additive changes once a client has shipped: deprecate before removing anything (graphql-kotlin maps Kotlin `@Deprecated` to `@deprecated`).
- Input validation happens in resolvers. The client is untrusted.
- Custom scalars need a graphql-kotlin `SchemaGeneratorHooks` entry and a matching Apollo scalar mapping. The export test must use the same hooks as the runtime.

## Export and drift check (M1-2)

- `GraphQLSchemaDefinition` (`server/.../graphql/`) is the single definition of the schema (packages, queries, later hooks). The runtime `install(GraphQL)` and `SchemaExportTest` both read it; add new `Query`/`Mutation` objects there.
- `SchemaExportTest` prints the schema with graphql-java's `SchemaPrinter` and compares it with `schema.graphqls`. With `-PupdateSchema` it rewrites the file instead. `server/build.gradle.kts` passes `updateSchema` and `schemaFile` to the test JVM as system properties and forces the test to rerun when `-PupdateSchema` is set.
- `graphQLPostRoute()` installs its own Jackson `ContentNegotiation`, so the kotlinx one is scoped to the non-GraphQL routes in `module()`.

## Custom scalars (ADR 0006)

| GraphQL | Server (graphql-kotlin hook in `SyncSchemaHooks`) | Client (`mapScalar` in `shared/build.gradle.kts`) |
|---|---|---|
| `Instant` | `java.time.Instant`, ISO-8601 UTC string | `kotlin.time.Instant` via `InstantAdapter` |
| `UUID` | `java.util.UUID`, string | `kotlin.String` |
| `Long` | `kotlin.Long`, JSON integer | `kotlin.Long` |

graphql-kotlin ships none of these. Add a scalar in three places: the hook, the `mapScalar` line, and a round-trip test. Nullable resolver arguments need a Kotlin default (`= null`) or omitting them fails.

## TODO

- Query depth/complexity limits.
