---
name: commit-split
description: |
  How to commit a finished ticket in anime-kmp - several commits grouped by layer instead of one, message format, pushing and the PR. Use this skill whenever the user says commit, push, "open the PR" or asks to split or redo commits. Trigger on phrases like "commit", "commit and push", "split the commits", "divide the commits", "open a PR", or "squash".
---

# Commit split

Runs only after the user explicitly says to commit. Never commit, push or open a PR before that (`CLAUDE.md`, `ticket-workflow`).

## Steps
1. `git status` and `git log --oneline -5`. The branch name must carry the ticket ID. Nothing unrelated may be staged.
2. Group the changed files by layer, in this order:
   1. `server/` and the committed GraphQL schema file
   2. `shared/` (data layer, sync, use cases, view models, Apollo operations)
   3. `composeApp/` (design system, screens, previews)
   4. docs and config (ADRs, skills, `CLAUDE.md`, README, ticket CSV, build files)
3. Tests go in the commit of the layer they cover, so every commit builds and passes lint and tests on its own. Skip a group with no files. Split a layer further by concern only if each part still compiles.
4. Unstage everything (`git reset`), then for each group `git add <paths>` and commit. No interactive flags (`-i`).
5. Message: `<TICKET>: <what changed>` as the subject, for example `M2-7: Merge and sign-out use cases`. Add a short body only when the why is not obvious. No attribution lines (no `Co-Authored-By`, no "Generated with Claude Code").
6. Check `git status` is clean and `git log --oneline` shows the groups. Run the build-runner checks once on the final state.
7. Push with `git push -u origin <branch>`. Open the PR only if the user asks, with the body layout of the previous PRs: `Closes #N`, What, Checked, Not checked, Follow-up.

## Redoing commits already made
- Ask first. Rewriting a pushed branch needs `git reset --soft <base>`, the steps above, and `git push --force-with-lease` on that branch only.
- Never force-push `main`, and never rewrite a branch that has an open PR with review comments.
