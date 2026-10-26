# 0014: The mini PC runs the server and deploys after passing tests

## Status
Accepted (M2-30)

## Context
The Mac runs the Android app, the emulator and Gradle builds. Running the server, PostgreSQL and Docker there as well added memory pressure (M2-28). Server tests already run against Docker on the mini PC through `server-tests`. Nothing deployed the server, and `scripts/dev-server.sh` started Postgres through a local `docker compose`.

## Decision
Use **the mini PC for everything except the apps**, and **deploy there automatically whenever the server tests pass**.

- `docker` and `docker compose` are denied for Claude Code on the Mac (`.claude/settings.json`).
- `server-deploy`, a script outside the repo, syncs the working tree to the mini PC over SSH and runs `docker compose up -d --build --wait` there. Flyway applies pending migrations when the server starts (ADR 0005). `--reset` drops the database volume first.
- `server-tests` runs `server-deploy` after a passing `:server:test` run. A failing run, or any other Gradle task, never deploys.
- No question is asked before a deploy. A failed deploy prints the server log tail and is not retried.
- The mini PC keeps its own `.env` (secrets, bind address); it is never synced. Host and address details stay in the scripts and out of the repo.

## Consequences
- Every passing server test run replaces the server on the mini PC with the working tree, including unreviewed work, and applies its migrations to the mini PC database.
- A changed squashed `V1` migration needs `server-deploy --reset`; the data is dev data.
- This is a dev deployment on a trusted LAN over plain HTTP. The production image, hosting choice and hardening stay in M2-22, M2-23 and M2-24.
