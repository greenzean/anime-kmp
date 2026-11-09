# 0024: The GraphQL endpoint limits query size, request size, push batches and request rate, and hides introspection in production

## Status
Accepted (M2-24)

## Context
`POST /graphql` is public. Until now it had no query limits, no body limit, no rate limiting, and GraphiQL and introspection were on in every environment. Versions: Ktor 3.6.0 (`ktor-server-rate-limit`, `ktor-server-body-limit`), graphql-kotlin 11.0.0, checked on 2026-11-09 against Maven Central and the official docs. The status code the body-limit plugin returns was not confirmed in the docs and is covered by a test.

## Decision
Use **fixed limits in `Limits`, Ktor plugins for size and rate, and graphql-java instrumentations for the query, with a cost per field**.

- Query depth at most 10 and complexity at most 1000. Complexity is 1 per field plus a weight (`QueryCost`): `pushChanges` its number of changes, `changesSince` a fifth of its `limit`, each sign-in and delete-account operation 250, each account operation 50. This stops aliases (`a: signInNonce b: signInNonce ...`) from multiplying the work of one request. Over the limit, the response is a GraphQL error.
- A JSON array of operations on `POST /graphql` is rejected with 400 (`SingleRequestParser`). The app never batches, and each element would otherwise get its own complexity budget.
- Request body at most 256 KB (`RequestBodyLimit`), 413 when over, also for a chunked body without `Content-Length`.
- `pushChanges` takes at most 500 changes per call, the size of a pull page. The app pushes in batches of 500.
- Rate limit with Ktor `RateLimit`, applied to every `/graphql` request: 120 per minute per IP and 60 per minute per user. A request with a valid access token is counted under its user in both limiters, so anonymous traffic from a shared address cannot lock out a signed-in user. A request without one is counted under its address with the per-IP limit of 120 (the per-user limit of 60 applies only to signed-in users), and IPv6 addresses are grouped by their /64 prefix. Over the limit, the response is 429 with `Retry-After`.
- Introspection and GraphiQL follow `GRAPHQL_INTROSPECTION`, which defaults to on when `APP_ENV=dev` and off when `APP_ENV=prod`.
- Input validation: enums and `UUID`/`Instant` scalars by the schema; per change `malId` 1..1,000,000, `progress`, `score`, timestamps from 2000-01-01 up to 5 minutes ahead (`REJECTED_INVALID`); at most 20,000 entries per user, live and tombstones; token strings at most 4096 characters; `cursor` at most 32 characters; `limit` clamped to 1..1000.
- Errors that are not `GraphqlErrorException` or `IllegalArgumentException` reach the client as "Internal server error"; the text goes only to the log (`SafeExceptionHandler`).
- An index on `sign_in_nonce (expires_at)` (in V1, edited in place because nothing is deployed) keeps the cleanup in `issueNonce` cheap.

## Consequences
- The IP is the connection's remote address. Behind a reverse proxy, or with Docker's port publishing, every client may look like one IP; M2-25 (hosting) must check that the source IP survives and decide whether to trust a forwarded header. It must also set `APP_ENV=prod`, because the default is dev, which serves GraphiQL.
- Limiter state is in memory and per server process, and resets on restart. Whether Ktor evicts idle keys was not verified.
- Someone with many valid accounts can avoid the per-IP limit; each account still has its own 60 per minute.
- The app does not handle 429 specially: it counts as a network error, so a very large first sync (more than about 30 push and pull pages in a minute) stops partway and resumes on the next sync.
- Google key fetches (10 per minute, shared) can still be used up by sign-in calls with made-up key ids. The cost weights make this a few requests per second at most, but it is not closed.
- Introspection is off in production, so a client cannot discover the schema; the committed SDL file is the reference.
- This closes the open points on rate limiting, GraphQL limits and the exception handler in [ADR 0010](0010-server-sign-in.md) and [ADR 0013](0013-account-deletion.md), and lowers the push batch in [ADR 0006](0006-sync-protocol.md) from 1000 to 500.
