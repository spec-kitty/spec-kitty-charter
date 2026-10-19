---
work_package_id: WP05
title: Decision records, docs and CHANGELOG
dependencies:
- WP04
- WP06
requirement_refs:
- FR-001
- FR-007
- FR-008
- FR-013
- FR-017
- NFR-002
- C-002
- C-004
- SC-003
- SC-004
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-action-context-reserved-surface-01M5AFEK
base_commit: cd430ee571f13a8143a57f2fb6a84e14f6bad6e6
created_at: '2026-10-19T18:49:14.006834+00:00'
subtasks:
- T022
- T023
- T024
- T025
- T026
phase: Phase 3 - Records
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/adr/
create_intent:
- docs/adr/0018-action-context-read-and-seam-access-surface.md
- docs/conformance/divergence-register.md
execution_mode: code_change
model: sonnet
owned_files:
- docs/adr/**
- docs/api/**
- docs/architecture/**
- docs/plans/**
- docs/context/**
- docs/guides/**
- contracts/charter/CHANGELOG.md
- cspell.json
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05: Decision records, docs and CHANGELOG

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP05 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Record decisions D1 to D14 (`research.md` §3) and bring every parallel authority into line
(`research.md` §4). Then the contract, the decision records and the docs say the same thing
about each of these (SC-003):

- `depth`;
- the walk;
- the fingerprint inputs;
- the token;
- refresh;
- the read-only rule;
- the reserved codes.

**Done when:**

- each item exists;
- the docs checks are clean: `python3 scripts/docs/check_docs.py all`,
  `npx --yes markdownlint-cli2@0.18.1` and `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`;
- `verifyAll` is still green (the CHANGELOG is in the contract tree).

## Context & Constraints

**Read first:**

- `docs/adr/0008` to `0017`, especially the 0009, 0011 and 0016 status conventions: "a later
  record supersedes it rather than editing it";
- `docs/adr/README.md` (index, numbering);
- `docs/_meta/` (the front-matter schema `check_docs.py` enforces);
- `research.md` §3 to §8;
- the WP01 to WP04 history notes, which list every contract change and out-of-map edit;
- the WP06 evidence (`evidence/walk-comparison.md`).

**Style:** follow the repository's docs style. Short sentences, plain words, and front matter
with `title`, `description`, `doc_status`, `updated: 2026-10-19`, `type`, `kind`, `related` and
`audience`, as the existing ADRs have.

**Runner B** is writing in parallel and may also add an ADR numbered 0018. Use 0018. The landing
rebase renumbers it if B lands first. Do not touch B's pages.

**Do not add the dated `docs/changelog/` entry.** The closeout adds it after the rebase.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`

```bash
spec-kitty agent action implement WP05 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

## Subtasks & Detailed Guidance

### Subtask T022: ADRs

**`docs/adr/0018-action-context-read-and-seam-access-surface.md`** (new):

- **Status:** "Accepted (provisional)", 2026-10-19. The runner decided under the operator's
  steer; "confirmation pending in the PR handover".
- **Context:**
  - #95 and #113;
  - the scope audit (lens C);
  - the release window (no tag; after 1.0 every closed-shape change is breaking).
- **Decision:** D1 to D14 as numbered decisions, each concise, with the walk table, the dedup
  rule, `depth` with the seam mapping, and the token and refresh shapes.
- **Operator rulings (research §10):** record each as decided by the operator on 2026-10-19:
  DIV-110 kept; R9 gates cutover stage 3; refresh as `POST /packs/refresh`; tensions to the
  creed set. Status stays "Accepted (provisional)", with these four marked operator-confirmed.
- **Supersedes, explicitly:**
  - ADR-0009 D3 bullet 1 (`depth` as scope hops, maximum 1);
  - ADR-0011 D2's "read operations only", reworded per D10;
  - ADR-0011 D4's "every answer carries" the fingerprint (now 200 only);
  - ADR-0011 D4's fingerprint input list (now pack files plus `config.yaml` and `charter.yaml`
    under the configured directory).
- **Not superseded** (stays #73's work): ADR-0011's built graph and reading pack locations from
  `config.yaml`.
- **Consequences, risks, alternatives:** take them from research §3 and §8. Name the residual
  risks:
  - the ABA window;
  - the port squatter (#70);
  - the guessable derived worktree id (#70);
  - tensions not reproducible until the creed set;
  - R9 as a recommended cutover gate.
- **Revisit when:**
  - R9 lands;
  - the creed set lands;
  - #73 adds a built graph;
  - a consumer needs a suggested artifact's `requires` closure;
  - core migrates its `in_tension_with` edges.
- **Requests to core:**
  - **the seam's mapping:** first load → depth 2, a recorded load → depth 1, the public bundle
    read → depth 0; drop `vocabulary` entries from delivery;
  - **the launcher (#70):** pass the token by environment, set `charter.project-config-dir`,
    always set `charter.worktree-id`;
  - **the seam:** treat 401 and 403 as transport failures.

**ADR-0009, ADR-0011 and ADR-0016:** add a dated `## Amendment (2026-10-19)` section at the end
of each, pointing to ADR-0018 and saying what it supersedes. Bump `updated:`. ADR-0016's §6 "at
both depths" becomes "at depths 0, 1 and 2", by amendment note.

**`docs/adr/README.md`:** list ADR-0018.

### Subtask T023: The service contract page and the divergence register

**`docs/api/service-contract.md`:**

- The #95 decision with its rationale, as #95's acceptance requires.
- The operation table gains `POST /packs/refresh`.
- `ServiceInfo` gains `serviceVersion`.
- The error model: 401 and 403 are transport failures; `stale_input` is raised;
  `governance_policy_violation` is reserved (#48).
- The "Planned" table loses the items now done, and gains R9's effective-context operation and
  the creed set's tension operation.

**`docs/conformance/divergence-register.md`** (new):

- Front matter like its siblings.
- A short intro: ADR-0016 D4, the format belongs to #72, and every entry has a stable id.
- The table from research §5: DIV-101 to DIV-103 and DIV-105 to DIV-109, with each one's
  corpus case. DIV-104 is listed as withdrawn, with the reason.
- The corpus cases named for #9.
- The two-sided comparison for activated cases (D5).
- Link it from `docs/api/README.md` (or the API index) and from ADR-0016's amendment.

### Subtask T024: Architecture and plan pages

Bring every statement listed in research §4 into line. Point to the contract file rather than
restating rules where possible.

| Page | What changes |
|---|---|
| `docs/architecture/resolution-model.md` (around :38, :66, :91-95) | the walk, `depth`, the reserved codes |
| `docs/architecture/service-lifecycle.md` (:50, :52, :55, :67-68, :81) | version, fingerprint inputs, token, refresh, `stale_input` |
| `docs/architecture/contract-and-dtos.md` (:104, :134-135) | `openapi.yaml` now holds `components` and `security`; reserved codes; depth |
| `docs/plans/open-questions.md` | Q14 (context resolution) answered by ADR-0018; the token question (:34) |
| `docs/plans/roadmap.md` (:76-80) | the action-context status |
| Glossary or context pages under `docs/context/` | define `hops` and the divergence register if they are domain terms; grep `docs/context/` first |

### Subtask T025: The contract CHANGELOG

**`contracts/charter/CHANGELOG.md`, under `## 1.0.0-SNAPSHOT`.** Follow its existing subsection
style and record each change from the WP01 to WP04 history notes:

- `depth`;
- the walk;
- `hops`;
- `diagnostics`;
- `serviceVersion`;
- the fingerprint inputs and header rule;
- `securitySchemes` and 401/403;
- refresh;
- `stale_input` raised;
- the scenarios;
- the compat fixture.

**The `### Provisional` section:** keep only what remains provisional (the `x-provisional`
context operation, pending the corpus), and move the three divergence paragraphs to the
register, with a link.

### Subtask T026: Docs checks

```bash
python3 scripts/docs/check_docs.py all
npx --yes markdownlint-cli2@0.18.1
npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"
```

- All three must be clean.
- Add genuine new words to `cspell.json`. Never disable a check.
- If `check_docs.py` has an index writer, use it for the ADR index rather than editing by hand.
  Check `scripts/docs/`.

## Definition of Done

- [ ] ADR-0018 written; the three amendments added; the index updated.
- [ ] The service contract page records the #95 decision; the divergence register exists and is
  linked.
- [ ] Every parallel authority in research §4 is in line (SC-003).
- [ ] The CHANGELOG records every change.
- [ ] All docs checks are clean, and `verifyAll` is green.

## Reviewer guidance

- Grep the docs for `depth 1`, `maximum 1`, `read operations only`, "every answer carries" and
  `scope edges` to find leftovers.
- Check that ADR-0018's supersession list matches research D14.
- Check that the register matches research §5.
