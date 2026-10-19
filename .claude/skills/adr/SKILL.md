---
name: adr
description: |
  Architecture decision records for anime-kmp - when to write one, the template in `docs/decisions/template.md`, numbering, status, superseding, and keeping the index and README in sync. Use this skill whenever a ticket makes a library, tool or architecture choice, a spike produces its answer, or an existing decision changes. Trigger on phrases like "ADR", "decision record", "record the decision", "write up the spike", "spike result", "supersede", or "docs/decisions".
---

# ADRs

ADRs live in `docs/decisions/`. The process and the index are in `docs/decisions/README.md`.

## When
- Choosing a library, framework or tool.
- A spike's answer. The ADR is the spike's deliverable.
- A convention that crosses modules (contract, module layout, sync rules).

Do not write one for choices local to one file or for things that are already in a skill.

## Writing one
1. Copy `docs/decisions/template.md` to `docs/decisions/NNNN-kebab-title.md`, using the next free number.
2. Context: the problem, constraints, and versions with the date they were checked (always verified with a search, never from memory) plus sources. If options were compared, add a table with one column per option, and say what was prototyped and what was judged from docs only.
3. Decision: the chosen option in bold, then the rules that follow from it.
4. Consequences: follow-ups, risks, and what was not verified, with the ticket that will verify it.
5. Status: `Proposed` while the plan is open, `Accepted (<ticket>)` once the user approves it.
6. Add a row to the index in `docs/decisions/README.md` and a link under **Architecture → Decisions** in the root `README.md`.

The `docs` agent can format the ADR from notes once the decision is made. It does not make the decision.

## Changing a decision
Do not rewrite an accepted ADR. Write a new one that references the old one, set the old one's status to `Superseded by NNNN`, and update both indexes. Fixing a typo or a broken link is fine.

## Style
Short sentences, no filler. Prefer tables for comparisons and bullets for rules. Follow ADRs 0003 and 0004 as examples.
