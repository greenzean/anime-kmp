# Architecture decision records

Each significant decision is recorded as a short ADR in this folder.

## When to write one
- Choosing a library, framework or tool.
- The result of a spike.
- A convention that crosses modules (contract, module layout, sync rules).

## How
1. Copy `template.md` to `NNNN-kebab-title.md`, using the next free number. Numbers are never reused or changed.
2. Fill in Context, Decision and Consequences. Versions are checked with a search, and the check date goes in Context.
3. Set Status to `Accepted (<ticket>)` once the ticket's plan is approved.
4. Add a row to the index below and a link in the Decisions list of the root `README.md`.

Accepted ADRs are not rewritten. A changed decision gets a new ADR, and the old one's status changes to `Superseded by NNNN`.

## Index

| # | Title | Status | Ticket |
|---|---|---|---|
| 0001 | [AGP 9 module layout](0001-agp9-module-layout.md) | Accepted | M0-2 |
| 0002 | [Lint and format tooling](0002-lint-tooling.md) | Accepted | M0-3 |
| 0003 | [GraphQL server library](0003-graphql-server.md) | Accepted | M0-7 |
| 0004 | [Client/server contract](0004-shared-contract.md) | Accepted | M0-8 |
| 0005 | [Postgres access, migrations and test database](0005-postgres-access.md) | Accepted | M1-3 |
| 0006 | [Sync protocol](0006-sync-protocol.md) | Accepted | M1-5 |
| 0007 | [Client-side platform access](0007-client-side-platform-access.md) | Accepted | M2 MVP brief |
| 0008 | [MAL API behaviour and how the app uses it](0008-mal-api.md) | Accepted | M2-2 |
