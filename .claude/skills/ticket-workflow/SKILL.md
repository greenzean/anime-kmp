---
name: ticket-workflow
description: Ticket template, states, Definition of Done, branch naming, plan format and the no-commit rule for this project. Use when planning, starting, reviewing or finishing any ticket (GitHub issue) in anime-kmp.
---

# Ticket workflow

Tickets are GitHub issues in `greenzean/anime-kmp`, one project per phase, one milestone per M-number.

## Types and sizing
Feature, Platform, Chore, Spike (time-boxed, produces a written answer), Bug. Each ticket is sized for 1 to 2 days.

## Template
- **Title:** what the user can do, at most 6 words.
- **Goal:** one sentence, from the user's point of view.
- **Where in the app:** the entry point (button, tab, Settings row). Server-only and release tickets say "nowhere".
- **Contract:** view model State, Action and Event, plus the SDL change when the server changes. Fixed before work starts so back end and views run in parallel.
- **Acceptance:** short bullets.
- **Decisions:** answers to every question the ticket raises, settled when the ticket is written.
- **Depends on:** lower ticket numbers only.
- **Out of scope.**

Design criteria are in the Definition of Done, not repeated in each ticket.

## States
Backlog, Ready, In Progress, In Review, Done. A ticket is not Done until it works on every platform it claims.

## Planning model
- Labeled `opus-plan` (architecture-heavy: sync engine, auth, MAL OAuth, GraphQL schema): start the session with `/model opusplan` and plan in plan mode.
- Everything else: plan and execute on Sonnet.
- Execution is always Sonnet.

## Flow
1. Read the issue (`gh issue view <n>`).
2. Plan: files to touch, the skills to load (`kmp-presentation-mvi`, `kmp-compose-ui`, `kmp-navigation`, `kmp-di-koin`, `kmp-data-layer`, `kmp-testing` as the ticket needs), steps, tests, how it will be verified. Take the Decisions from the ticket; ask only if the code contradicts them. Wait for the user's approval.
3. Implement:
   - Server and SDL first, when the server changes.
   - Then, for a ticket with both `shared` and `composeApp`, run `shared-kmp` (API, data, view model) and `compose-ui` (screens, previews from fake states) in parallel against the Contract.
   - The main session wires DI and navigation and integrates. Use `build-runner` for builds and tests.
4. `reviewer` only if the user asks.
5. `/clear` before the next ticket.
6. The user decides when to commit, push and open the PR. When they do, commit as in the `commit-split` skill.

## No-commit rule
Never commit, push, or open a PR (including via `gh pr`) until the user explicitly says so. Permission rules prompt for these as a safety net.

## Branch naming
Include the ticket ID: `m0-1-create-monorepo`, `m1-5-push-pull-sync`.

## Definition of Done
- Builds in CI for all active targets.
- Tests pass; shared logic is covered in `commonTest`.
- View models depend on use cases only, never on repositories or data sources (`kmp-presentation-mvi` skill).
- Every `composeApp` screen has `runComposeUiTest` tests for all its states, actions, accessibility labels and UX rules (`kmp-testing` skill, View Tests).
- Checked manually on a device or emulator.
- Reachable from the app: the user can get to the feature from a button, a tab or a Settings row.
- Views: built on the design system from M2-6a (#86), with no new hard-coded colors, sizes or text styles. A Compose preview for every state in light and dark. Touch targets of at least 48dp, text contrast checked, `contentDescription` or semantics on every control. Emulator screenshots in the PR.
- Tickets that touch `server/`: `server-tests` passed and the deploy to the mini PC is healthy.
- Reviewed and merged, with the ticket linked to the PR.

## Decisions
Architecture decisions get an ADR in `docs/decisions/`. Process: `adr` skill.
