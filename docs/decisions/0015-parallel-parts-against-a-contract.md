# 0015: Parts are built in parallel against a Contract

## Status
Accepted (M2-12d)

## Context
ADR 0004 set the routing order for any API change: server first, then the SDL export, then the client, then the views. The reason was that the client cannot compile an operation until the SDL contains its fields.

A ticket now has one agent per touched part (server, app back end, views, build, docs) plus a tester. Running them one after another in that order leaves most of them waiting, although the parts only need to agree on a few shared names.

## Decision
**Write the Contract first, then build all the parts at the same time.**

- The Contract is: the SDL change, the view model State, Action and Event, the domain models and the use case and repository interfaces, the screen signatures, the string resource keys and test tags of every control, and new dependencies. The plan lists it; the agents do not change it, they report a needed change.
- When the plan is approved, the main session writes the Contract to disk before starting any agent. The SDL change goes into `schema.graphqls` by hand (the only time the file is edited by hand), and the Kotlin parts go into their modules with `TODO()` bodies.
- One agent per touched part, plus `tester`, all start in one batch in the same working tree and each touches only its own part. There are no worktrees: the no-commit rule means a worktree would not see the uncommitted Contract files.
- The agents run no Gradle tasks while they work, because the others are still writing and any build, lint or test run would fail on half-written files. The main session lints, builds and tests everything once all of them have finished.
- The `server` agent makes the code-first schema print exactly the Contract's SDL. The drift test checks it after all the agents finish, with `server-tests :server:test`.
- If the drift test fails, the main session runs `server-tests -PupdateSchema :server:test` (the `server-tests` script deploys only when `:server:test` is its first argument) and compares the new file with the Contract. It accepts the file only when the difference is ordering or formatting; otherwise the server code is fixed.
- The main session then wires DI and navigation, runs the full build and tests, and fixes the seams.

## Consequences
- This replaces only the routing order in ADR 0004. The rest of ADR 0004 stands: the server owns the schema, the SDL is committed in `shared`, and the drift test guards it.
- A mistake in the Contract shows up at integration, after the agents are done. The fix is to change the Contract and send the affected agents back.
- `tester` writes tests before the code exists, so its tests may fail at integration for reasons that are not bugs in the code.
- Not verified: how often a Contract needs rework. Review it after the first few feature tickets.
