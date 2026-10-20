# Milestone 2 roadmap

Order of work for the [M2 MVP](milestone2-mvp.md). Ticket text is in [`tickets-m2.csv`](tickets-m2.csv). Decisions are D1-D24; architecture is [ADR 0007](decisions/0007-client-side-platform-access.md). Each ticket is sized for 1 to 2 days. `opus-plan` tickets are planned with `/model opusplan`.

Rule for API changes: server first (schema, SDL), then the app back end, then views, then tests.

## Order

| Step | Tickets | Result |
|---|---|---|
| 0. Prep | M2-0, M2-0a, M2-0b, M2-0c, M2-1 | Scope, agents, skills and this roadmap (done except M2-1) |
| 1. Facts and data model | M2-2 (spike, opus-plan), M2-3 (opus-plan) | MAL API tested; server stores IDs only |
| 2. Account | M2-4 (opus-plan), M2-5, M2-6 (opus-plan), M2-7, M2-8 | Optional Google sign-in, per-user sync, merge, logout, deletion |
| 3. Library | M2-13, M2-14 | Search, filter, sort; five statuses and score |
| 4. MAL client | M2-9 (opus-plan), M2-10, M2-11, M2-12 | MAL login, search, details, suggestions |
| 5. Import, export, MAL sync | M2-15, M2-16, M2-18 (design, opus-plan), M2-17, M2-19 (opus-plan), M2-20 (opus-plan), M2-21 (opus-plan) | Files, API import, two-way sync, conflicts, several devices |
| 6. Release | M2-22, M2-23 (spike), M2-24, M2-25, M2-26 | Hardened server online, privacy policy, Play test track |
| 7. Checkpoint | M2-27 | End-to-end result note |

Steps 2, 3 and 4 only depend on step 1 where the table below says so, so they can run in any order.

## Dependencies

| Ticket | Depends on |
|---|---|
| M2-3 | M2-2 |
| M2-4 | M2-3 |
| M2-5 | M2-4 |
| M2-6 | M2-4 |
| M2-7 | M2-5, M2-6 |
| M2-8 | M2-5, M2-6 |
| M2-9 | M2-2 |
| M2-10 | M2-2 |
| M2-11 | M2-10, M2-14 |
| M2-12 | M2-9, M2-10 |
| M2-15 | M2-14 |
| M2-16 | M2-14 |
| M2-17 | M2-9, M2-14, M2-18 |
| M2-18 | M2-2 |
| M2-19 | M2-9, M2-14 |
| M2-20 | M2-18, M2-19 |
| M2-21 | M2-5, M2-9, M2-20 |
| M2-22 | M2-5 |
| M2-24 | M2-22, M2-23 |
| M2-25 | M2-8 |
| M2-26 | M2-24, M2-25 |
| M2-27 | all |

M2-2 needs a MAL client ID registered by the user before it starts.

## Decision coverage

| Decision | Tickets |
|---|---|
| D1 | M2-6, M2-9 |
| D2 | M2-3, M2-5 |
| D3 | M2-9 |
| D4 | M2-9 |
| D5 | M2-2, M2-10 |
| D6 | M2-14 |
| D7 | M2-14 |
| D8 | No work: a constraint (anime only, no manga) checked in M2-27 |
| D9 | M2-13 |
| D10 | M2-2, M2-10, M2-11 |
| D11 | M2-2, M2-12 |
| D12 | M2-14 |
| D13 | M2-2, M2-19 |
| D14 | M2-2, M2-20 |
| D15 | M2-18, M2-20, M2-21 |
| D16 | M2-15, M2-16, M2-17 |
| D17 | M2-19, M2-21 |
| D18 | M2-7 |
| D19 | M2-3, M2-21 |
| D20 | M2-7 |
| D21 | M2-26 |
| D22 | M2-8, M2-22, M2-23, M2-24, M2-25, M2-26 |
| D23 | M2-16 |
| D24 | M2-4, M2-5, M2-6 |

## Open points

- M2-2 may change the later tickets: if MAL search needs a user login, M2-10 and M2-11 depend on M2-9; if MAL offers no app type for PKCE without a secret, M2-9 needs a new plan.
- M2-15 keeps the local entry on a conflict until M2-20 exists; revisit the order if import is wanted earlier.
- Account deletion (M2-8) and the privacy policy (M2-25) are required by Play, so M2-26 cannot start without them.
