# Milestone 2 roadmap

Order of work for the [M2 MVP](milestone2-mvp.md). Ticket text is in [`tickets-m2.csv`](tickets-m2.csv) and the GitHub issues. Decisions are D1-D27; architecture is [ADR 0007](decisions/0007-client-side-platform-access.md). Each ticket is sized for 1 to 2 days. `opus-plan` tickets are planned with `/model opusplan`.

Rules (reworked 2026-10-26, see `ticket-workflow` skill):
- Ticket numbers follow the order of work, and a ticket depends only on lower numbers.
- Every feature ticket ends with something the user can reach in the app. The API and the view that shows it are one ticket, built in parallel against the ticket's Contract.
- Every ticket has a Decisions section, so it starts without open questions.
- Inside a ticket: server and SDL first, then app back end and views in parallel, then tests.

M2-11 to M2-27 were rewritten on 2026-10-26 (old issues #63-#79 closed, new issues #109-#126). The old numbers in closed issues and merged PRs keep their old meaning.

## Done

| Step | Tickets | Result |
|---|---|---|
| Prep | M2-0, M2-0a, M2-0b, M2-0c, M2-1 | Scope, agents, skills, roadmap |
| Facts and data model | M2-2, M2-3 | MAL API tested; server stores IDs only |
| Account | M2-4, M2-5, M2-6, M2-6a, M2-7, M2-8, M2-29 | Optional Google sign-in, per-user sync, merge, logout, deletion, design system |
| MAL client | M2-9, M2-10 | MAL login and search/details in `shared` (their screens come in M2-11, M2-12, M2-15) |
| Dev server | M2-28, M2-30 | Server runs and deploys on the mini PC |
| Ticket rework | M2-10a (#127) | The tickets below, in work order, with decisions made up front |

## Order

| ID | Issue | Ticket | What the user gets | Depends on |
|---|---|---|---|---|
| M2-11 | #109 | Add anime from MAL search | Bottom bar; Search tab; add to library; title language setting | – |
| M2-12 | #110 | Anime details screen | Tap any anime to see its details | M2-11 |
| M2-12a | #131 | Check DI graph and view models | Koin graph test; build fails if a view model takes a repository | M2-12 |
| M2-12b | #132 | Test Settings and For you screens | View tests for the two remaining screens | M2-12 |
| M2-12c | #133 | Move texts to string resources | No visible change; texts ready for translation | M2-12 |
| M2-12d | #135 | Clean up the Claude setup | No visible change; rules in one place, tickets run as parallel agents | M2-12c |
| M2-13 | #111 | Track status, episodes and score | Edit an entry; +1 on library rows | M2-12 |
| M2-14 | #112 | Search, filter and sort your library | Find entries in the library, offline | M2-13 |
| M2-15 | #113 | Connect your MAL account | MyAnimeList section in Settings | – |
| M2-16 | #114 | Suggestions from MAL | For you tab | M2-12, M2-15 |
| M2-17 | #115 | Export your library to a MAL file | Settings › Export | M2-13 |
| M2-18 | #116 | Import a MAL export file | Settings › Import | M2-17 |
| M2-19 | #117 | Choose which version to keep | Conflicts screen | M2-18 |
| M2-20 | #118 | Import your list from MAL | Import on connect, or from Settings | M2-15, M2-19 |
| M2-21 | #119 | Send your changes to MAL | Automatic upload, status in Settings | M2-13, M2-15 |
| M2-22 | #120 | Get changes made on MAL | Automatic download, Sync now | M2-20, M2-21 |
| M2-23 | #121 | MAL on a second device | Prompt on devices without MAL | M2-22 |
| M2-24 | #122 | Server limits and input validation | Server only | – |
| M2-25 | #123 | Put the server online | Release server with HTTPS (host chosen here) | M2-24 |
| M2-26 | #124 | Privacy policy | Settings › Privacy policy | – |
| M2-27 | #125 | Publish to Google Play internal testing | The app on a Play test track | M2-25, M2-26 |
| M2-31 | #126 | End-to-end check of the MVP | Result note | all |

M2-15, M2-24 and M2-26 have no open dependencies and can be done at any point. M2-28 to M2-30 are taken by done tickets, so the checkpoint is M2-31.

## Decision coverage

| Decision | Tickets |
|---|---|
| D1 | M2-6, M2-9, M2-11 |
| D2 | M2-3, M2-5 |
| D3 | M2-9, M2-15 |
| D4 | M2-9 |
| D5 | M2-2, M2-10 |
| D6 | M2-13 |
| D7 | M2-13 |
| D8 | No work: a constraint checked in M2-31 |
| D9 | M2-14 |
| D10 | M2-11 |
| D11 | M2-16 |
| D12 | M2-13, M2-21 |
| D13 | M2-21 |
| D14 | M2-22 |
| D15 | M2-19, M2-20, M2-22, M2-23 |
| D16 | M2-17, M2-18, M2-20 |
| D17 | M2-21, M2-23 (strict: changes from devices without MAL never reach MAL) |
| D18 | M2-7 |
| D19 | M2-3, M2-23 |
| D20 | M2-7 |
| D21 | M2-27 |
| D22 | M2-8, M2-24, M2-25, M2-26, M2-27 |
| D23 | M2-17 |
| D24 | M2-4, M2-5, M2-6 |
| D25 | M2-11 |
| D26 | M2-13 |
| D27 | M2-15, M2-20, M2-21 |

## Open points

- M2-26 needs a dedicated contact address from the user before it starts.
- M2-25 chooses between the mini PC and paid hosts. The user picks in that ticket's ADR.
