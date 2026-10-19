---
name: graphql-schema
description: |
  GraphQL contract conventions for anime-kmp - graphql-kotlin code-first schema in `server/`, the committed SDL file, the schema export/drift test, Apollo Kotlin operations in `shared/`, and the server-first routing order for API changes. Use this skill whenever adding or changing a query, mutation, subscription, GraphQL type, scalar, or Apollo operation. Trigger on phrases like "GraphQL", "schema", "SDL", "add a query", "add a mutation", "resolver", "Apollo operation", ".graphql file", "schema.graphqls", or "regenerate schema".
---

# GraphQL schema conventions

Decisions: ADR 0003 (graphql-kotlin) and ADR 0004 (schema as contract). The schema is code-first on the server, and the committed SDL is the contract the client compiles against. `server` never depends on `shared`.

## Where things live

```
server/src/main/kotlin/com/greenzean/animekmp/server/graphql/
  <Feature>Query.kt / <Feature>Mutation.kt   # implement graphql-kotlin Query / Mutation
  model/                                     # GraphQL output and input types (server-owned)
server/src/test/.../graphql/SchemaExportTest.kt  # SDL export + drift check

shared/src/commonMain/graphql/
  schema.graphqls          # generated from the server, committed, never hand-edited
  <Name>Query.graphql      # client operations, one per file
  <Name>Mutation.graphql
```

- Apollo generates client models into `com.greenzean.animekmp.shared.graphql`. Map them to domain/DB models in the data layer and never expose them to the UI.
- GraphQL types are separate from DB rows and from MAL DTOs. Map between them in the server.

## Changing the API (always in this order)

1. Server: types plus `Query`/`Mutation` classes, with resolver tests.
2. Export: `./gradlew :server:test -PupdateSchema`, then review and commit the `schema.graphqls` diff.
3. Client: add or adjust `.graphql` operations in `shared`, then the repository/sync code.
4. Views.

A plain `./gradlew :server:test` fails if `schema.graphqls` no longer matches the server code. Regenerate the file; do not edit it by hand.

## Rules

- Only additive changes once a client has shipped: deprecate before removing anything (graphql-kotlin maps Kotlin `@Deprecated` to `@deprecated`).
- Input validation happens in resolvers. The client is untrusted.
- Custom scalars need a graphql-kotlin `SchemaGeneratorHooks` entry and a matching Apollo scalar mapping. The export test must use the same hooks as the runtime.

## Export and drift check (M1-2)

- `GraphQLSchemaDefinition` (`server/.../graphql/`) is the single definition of the schema (packages, queries, later hooks). The runtime `install(GraphQL)` and `SchemaExportTest` both read it; add new `Query`/`Mutation` objects there.
- `SchemaExportTest` prints the schema with graphql-java's `SchemaPrinter` and compares it with `schema.graphqls`. With `-PupdateSchema` it rewrites the file instead. `server/build.gradle.kts` passes `updateSchema` and `schemaFile` to the test JVM as system properties and forces the test to rerun when `-PupdateSchema` is set.
- `graphQLPostRoute()` installs its own Jackson `ContentNegotiation`, so the kotlinx one is scoped to the non-GraphQL routes in `module()`.

## TODO (fill in with M1-5)

- Scalar mapping table (`Instant`, IDs).
- Query depth/complexity limits.
