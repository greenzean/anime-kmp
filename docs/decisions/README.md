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
| 0004 | [Client/server contract](0004-shared-contract.md) | Accepted; routing order superseded by 0015 | M0-8 |
| 0005 | [Postgres access, migrations and test database](0005-postgres-access.md) | Accepted | M1-3 |
| 0006 | [Sync protocol](0006-sync-protocol.md) | Accepted; push batch changed by 0024 | M1-5 |
| 0007 | [Client-side platform access](0007-client-side-platform-access.md) | Accepted | M2 MVP brief |
| 0008 | [MAL API behaviour and how the app uses it](0008-mal-api.md) | Accepted | M2-2 |
| 0009 | [Anime references: a server-side ID map keyed by platform IDs](0009-anime-id-map.md) | Accepted | M2-3 |
| 0010 | [Server sign-in and session tokens](0010-server-sign-in.md) | Accepted | M2-4 |
| 0011 | [Sign-out and who owns the library on the device](0011-sign-out-and-library-ownership.md) | Accepted | M2-7 |
| 0012 | [Sign-out revokes the refresh token](0012-sign-out-revokes-refresh-token.md) | Accepted | M2-29 |
| 0013 | [Account deletion](0013-account-deletion.md) | Accepted | M2-8 |
| 0014 | [The mini PC runs the server and deploys after passing tests](0014-mini-pc-runs-server-and-deploys.md) | Accepted | M2-30 |
| 0015 | [Parts are built in parallel against a Contract](0015-parallel-parts-against-a-contract.md) | Accepted | M2-12d |
| 0016 | [App logging through a small AppLogger interface](0016-app-logging.md) | Accepted | M2-32 |
| 0017 | [Reading MAL export files with xmlutil and okio, and filling imported anime images](0017-mal-export-reading-with-xmlutil-and-okio.md) | Accepted | M2-18 |
| 0018 | [Import conflicts are stored locally and the user picks the version](0018-conflicts-stored-locally-user-picks-version.md) | Accepted; partly superseded by 0023 | M2-19 |
| 0019 | [MAL list import reads the account's list page by page and resumes after a failure](0019-mal-list-import.md) | Accepted; partly superseded by 0023 | M2-20 |
| 0020 | [Changes made on this device go to MAL through a local outbox, one row per anime](0020-send-changes-to-mal.md) | Accepted | M2-21 |
| 0021 | [Changes made on MAL are pulled by the app and applied by MAL's update time](0021-get-changes-from-mal.md) | Accepted | M2-22 |
| 0022 | [MAL on a second device: the server keeps only a "MAL linked" flag, and a device without MAL never sends to MAL](0022-mal-on-a-second-device.md) | Accepted | M2-23 |
| 0023 | [When the MAL list is imported, MAL's update time settles differences instead of asking the user](0023-mal-connect-import-by-update-time.md) | Accepted | M2-23 |
| 0024 | [The GraphQL endpoint limits query size, request size, push batches and request rate, and hides introspection in production](0024-server-limits.md) | Accepted | M2-24 |
| 0025 | [The server is hosted on the mini PC and published through Tailscale Funnel](0025-server-hosting.md) | Accepted | M2-25 |
