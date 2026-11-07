---
work_package_id: WP01
title: Decision record
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-013
- C-005
- SC-004
planning_base_branch: claude/240-drop-edge-fields
merge_target_branch: claude/240-drop-edge-fields
branch_strategy: Planning artifacts for this mission were generated on claude/240-drop-edge-fields. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/240-drop-edge-fields unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
phase: Phase 1 - Decision
history:
- at: '2026-11-05T17:40:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/adr/
create_intent:
- docs/adr/0060-graph-documents-are-the-only-source-of-relationships.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/adr/**
role: architect
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Decision record

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `architect`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history <WPID> --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `` `<div>` ``, `` `<script>` ``
Use language identifiers in code blocks: ````java`, ````bash`

---

## Objectives & Success Criteria

- A new ADR records the operator ruling of 2026-11-05 on #240, field by field. It says that
  `putPackRelationship` is the only operation that writes an edge, and that a pack's graph documents
  are the only place one is authored.
- Every ADR the ruling changes carries a dated **Amendment 2026-11-05** under its Status, and an
  inline note wherever a changed decision stands.
- No ADR still says edge authority waits on #189 or #167. Historical mentions stay, but they point on
  to the new ADR.
- The docs checks pass: `scripts/docs/check_docs.py` (all eight), markdownlint-cli2, cspell, and the
  CI frontmatter check (description 50 to 180 characters).

## Context & Constraints

- Read first: [spec.md](../spec.md), [plan.md](../plan.md) (design decisions P1 to P6, and IC-01),
  and [research.md](../research.md) (R1 is the per-field evidence table; R8 holds the squad
  dispositions).
- The decision is the operator's. The three deferred questions (D1 `procedures`, D2 reference scope,
  D3 read shapes) are recorded as the mission's working answers and asked on #240
  (issuecomment-6663603068). The ADR states each answer and says it was asked. It does not present a
  deferred answer as an operator ruling.
- Template: `docs/_meta/templates/adr.template`. Frontmatter: `title` ("ADR-0060: …"),
  `description` (50 to 180 characters), `doc_status: active`, `updated`, `decision_status`,
  `decision_date`, `type: ADR`, `kind: ADR`, `related:` (slugs of real pages only; `adr/README` is
  not a page), and `audience`.
- Amendment precedent: ADR-0053 amending ADR-0052. The amended record gets
  `decision_status: 'Accepted, with Amendment 2026-11-05 (ADR-0060)'`, a bumped `updated`, a
  `### Amendment 2026-11-05` section under `## Status` listing each changed decision, and an inline
  `*Amended 2026-11-05 ([ADR-0060](/adr/0060-…/) decision N): …*` after each changed decision.
- **Do not edit `docs/adr/README.md`.** Its index is generated (ADR-0047).
- **Number:** re-check `git ls-tree origin/main docs/adr/` and the open PRs immediately before
  writing. PR #376 holds 0059. If 0060 is taken, use the next free number and rename everywhere.
- Designing as if core did not exist: the ADR does not cite core's extractor as an authority.

## Branch Strategy

- Planning base and merge target: `claude/240-drop-edge-fields`. The PR to `main` is opened from it.
- Execution worktrees are allocated per computed lane from `lanes.json`. Run
  `spec-kitty agent action implement WP01 --agent <name> --mission graph-documents-sole-edge-source-01M6P9D0`.

## Subtasks & Detailed Guidance

### Subtask T001 – Write ADR-0060

**Purpose**: the single record of the per-field decision.

**Title**: "ADR-0060: Relationships are authored only in a pack's graph documents".
**File**: `docs/adr/0060-graph-documents-are-the-only-source-of-relationships.md`.

**Status**: "**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator). The operator ruled on
#240 on 2026-11-05 …". Name the three questions that are still open with the operator, and say
that this record holds the working answer to each.

**Context**: the two homes of an edge. ADR-0024 and ADR-0025 mint edges from fields. Graph
documents (ADR-0037, ADR-0036) also carry edges. Registration bodies carry `relationships` (ADR-0026),
and the read shape derives its own copy from the fields, so a GET never shows body-registered edges
(research R3). The #167 interim mapping and the #189 deferrals follow from this.

**Decisions**:
1. An edge is authored only in a pack's graph documents: root `*.graph.yaml` shards, including the
   registered shard `registered.graph.yaml`, and `drg/fragment.yaml`.
2. Each field below is dropped. List each one with its kind, its field, what it minted, and where
   its edges now live:
   - a directive's typed `references`;
   - a paradigm's `references` and `directive_refs`;
   - a tactic's top-level `references` and its steps' `references`;
   - a procedure's `references`;
   - a mission type's `references` and its steps' `references`;
   - a styleguide's and a toolguide's citation `references`.
3. A directive's `procedures` stays as prose steps and mints nothing (D1 working answer). Explain
   why: the 192 entries are sentences, not ids, and the edge they minted could not be stored.
4. The directive and paradigm registration bodies carry no `relationships`.
   `putPackRelationship` is the only operation that writes an edge.
5. A registration body is closed. An unknown member is refused with 422 `validation_failed`, naming
   the member. A pack file is closed by its schema.
6. Read shapes carry no `relationships` (D3 working answer). The references read
   (`listArtifactReferences`) answers the artifact's outgoing edges from this pack's graph
   documents, each with its relation, followed by its derived relations.
7. Derived relations: `CharterArtifact.relationships()` becomes `derivedRelationships()`. A guard's
   `IMPLEMENTED_BY` to an in-pack asset is the only one. It is runtime data, refused on write, and
   never compared by the round trip. It is not a second authoring source.
8. The interim mapping (`ArtifactEdges`, #167) and its pinned relation values are retired.
9. The round-trip gate compares edges from the graph documents only (amends ADR-0036 decision 4).
   An org fragment's edge gains `when`, so a fragment edge can hold what a shard edge holds.
10. Identity is unchanged (ADR-0027 decision 1 and ADR-0028). With one source, two edges on a pair
    can no longer come from two homes. The #167 provenance divergence is gone.
11. **Kept as data, not relationship authoring** (table):
    - guard `implemented_by`;
    - agent-profile `directive-references` and its tactic, toolguide and styleguide references
      (selection data: they say which doctrine the profile is handed, not how two artifacts relate);
    - skill `enhances` and `overrides`;
    - pack `depends_on`;
    - `activated_*` and `selected_*`;
    - glossary `see_also`;
    - mission-step-contract delegations;
    - lifecycle guards;
    - per-value `impacts`.

    Q4 asks the operator to confirm this table.
12. Every "until #189 rules" deferral resolves here.

**Consequences**:
- Positive: one source, the read and the round trip agree, and the contract is smaller.
- Negative: the open-packs migration, a contract break on 0.2.0-SNAPSHOT, and 26 non-artifact
  citations dropped (research R7).
- Risks: the deferred D1 to D3 answers may change, and the plan says how each one reverses.

**Alternatives considered**: keep the fields as a derived view; keep them as authoring sugar with
an agreement check; use a known-failure list instead of migrating first (rejected, plan P5).

**Interaction with ADR-0059 (PR #376)**: its cascade (D2) follows `requires` and `suggests` edges, so
after this change it reaches only graph edges. Its open question 4 (the edges of a whole-artifact
override) loses its premise, because an override never carried edges.

**References**: #240, #167, #189, ADR-0022, 0024 to 0028, 0036, 0037, 0043, 0044, 0050, 0052, 0053
and 0059.

**Validation**: the description is 50 to 180 characters, and every `related:` slug is a real file
under `docs/`.

### Subtask T002 – Amend ADR-0024, 0025, 0026, 0027, 0036 d4 and 0053 d22

Use the exact precedent form (see Context). The points to annotate:

- **0024**:
  - L80-82 (the common `edges`);
  - L92-94 (D1, the spine's `edges`);
  - L130-134 (the risk that #167 must resolve; closed by ADR-0060).
- **0025**:
  - D1 L61-62 (`when` carried by `ArtifactEdges`);
  - D3 L79-84 ("owns" becomes "exposes derived relations"; `relationships()` becomes
    `derivedRelationships()`);
  - D4 L87-88 (graph documents are now the only source);
  - neutral L131-134 (the interim mapping is retired).
- **0026**:
  - D1.2 L62-63 (the body carries relationships);
  - D1.3 L78-79 (a relationship riding inside a body). Both are dropped.
- **0027**:
  - D2 L63-65 ("source" now means graph documents);
  - neutral L99-102 (the provenance divergence dissolves).
- **0036**: decision 4 (L252-270): edges are compared from graph documents only.
- **0053**: decision 22 (L133-134): a step enhancement's additions lose `references`.

Line numbers are from `main` at plan time. Re-locate each one by its text.

### Subtask T003 – Repoint the citations

Where #189 is cited for edge authority, add "decided by [ADR-0060](…) (#240)". Keep the historical
link. Do the same where #167 is linked.

- ADR-0043: L72-73, L105, L126-127, L358-359, L366-367
- ADR-0044: L163, L243-244, L401
- ADR-0050: L55-56
- ADR-0052: d9, L141-147, where a `rejects` entry minting `Relation.rejects` from a field is retired
- ADR-0037: L226
- ADR-0036: L469
- ADR-0024: L132-133
- ADR-0025: L133
- ADR-0027: L101

Each of these records also gets the amendment section of T002, if one of its decisions changes. A
citation that only moves gets an inline note alone.

### Subtask T004 – Docs checks

Run `python3 scripts/docs/check_docs.py`, markdownlint-cli2 and cspell (see `docs/guides` for the
exact invocations, or `.github/workflows`). Also check every description length (≤180). Add new words
to `cspell.json` only if they are genuine terms.

## Test Strategy

The docs checks, plus `grep -rn "189\|167" docs/adr`. Review the result by hand: every remaining hit
must be historical, and each one must point on to ADR-0060.

## Risks & Mitigations

- **An ADR number collision**: re-check immediately before writing.
- **Overstating a deferred answer as a ruling**: the Status names the open questions.

## Review Guidance

- Is each field in research R1 named with a decision?
- Does each amended decision carry an inline note?
- Is no generated index edited?

## Activity Log

- 2026-11-05T17:40:00Z – system – Prompt created.
