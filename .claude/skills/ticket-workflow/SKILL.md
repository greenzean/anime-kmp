---
name: ticket-workflow
description: Ticket template, states, Definition of Done, branch naming, plan format and the no-commit rule for this project. Use when planning, starting, reviewing or finishing any ticket (GitHub issue) in anime-kmp.
---

# Ticket workflow

Tickets are GitHub issues in `greenzean/anime-kmp`, one project per phase, one milestone per M-number.

## Types and sizing
Feature, Platform, Chore, Spike (time-boxed, produces a written answer), Bug. Each ticket is sized for 1 to 2 days.

## Template
Goal, Scope (module and platforms), Acceptance criteria, Out of scope.

## States
Backlog, Ready, In Progress, In Review, Done. A ticket is not Done until it works on every platform it claims.

## Planning model
- Labeled `opus-plan` (architecture-heavy: sync engine, auth, MAL OAuth, GraphQL schema): start the session with `/model opusplan` and plan in plan mode.
- Everything else: plan and execute on Sonnet.
- Execution is always Sonnet.

## Flow
1. Read the issue (`gh issue view <n>`).
2. Plan: files to touch, steps, tests, how it will be verified. Wait for the user's approval.
3. Main session implements. Use `build-runner` for builds and tests.
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
- Checked manually on a device or emulator.
- Reviewed and merged, with the ticket linked to the PR.

## Decisions
Architecture decisions get an ADR in `docs/decisions/`. Process: `adr` skill.
