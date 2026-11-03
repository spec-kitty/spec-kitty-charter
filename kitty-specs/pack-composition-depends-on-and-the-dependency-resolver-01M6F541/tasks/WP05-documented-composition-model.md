---
work_package_id: WP05
title: The documented composition model
dependencies:
- WP03
requirement_refs:
- FR-021
- FR-022
- FR-023
planning_base_branch: issue-145-pack-composition
merge_target_branch: issue-145-pack-composition
branch_strategy: Planning artifacts for this mission were generated on issue-145-pack-composition. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-145-pack-composition unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
phase: Phase 2 - Implementation
history:
- at: '2026-11-03T00:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- docs/architecture/logical-model.md
- docs/architecture/descriptors.md
- docs/architecture/pack-registration-distribution.md
- docs/glossary/charter/README.md
- site/.contextive/definitions.yaml
- docs/plans/pack-ecosystem-work-breakdown.md
role: scribe
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – The documented composition model

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `scribe`
- **Agent/tool**: `opencode`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history WP05 --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `<div>`, `<script>`
Use language identifiers in code blocks: `markdown`, `bash`

---

## Objectives & Success Criteria

Make the docs describe the pack model the code now has: the logical model and the architecture pages
cover `depends_on`, the compose and the resolver's refusals (FR-021); the glossary and the contextive
definitions carry Compose, Closure, Dependency edge and Qualified reference in one vocabulary with the
specification and the code (FR-022); the work-breakdown items 2 and 3 are marked done (FR-023); every
page that describes the pack model either states the new shape or links to the page that does, and the
docs checks pass with zero findings (SC-006).

Success criteria:

- No page still says the resolver is not built, or defers the compose boundary to an open question.
- The four terms are defined in the glossary and the contextive source, in the ruled shape.
- Items 2 and 3 in `docs/plans/pack-ecosystem-work-breakdown.md` are struck through and dated, as
  item 1 is.
- `python3 scripts/docs/check_docs.py all` (plus markdownlint and cspell where the repo runs them)
  passes with zero findings.

## Context & Constraints

- **Already on this branch**: the descriptor `depends_on` (with its first docs pass in `50b1330e`:
  `descriptors.md` documents `depends_on` as accepted, `logical-model.md` still says "the resolver is
  not built yet", the contextive source gained Closure and Dependency Edge), the resolver and its
  wiring, and WP03's contract change set (the fragment `2026-11-03-pack-composition-refusals-and-
  dependency-facts.md` is the changelog entry FR-023 asks for — this package verifies it reads where
  readers look, it does not duplicate it).
- **Deciding records**: ADR-0048 (as amended 2026-11-02), ADR-0053 (decisions 4, 5, 6, 7 — the
  one-closure-at-one-level compose, the settling `overrides` edge, the `<pack-name>::` qualifier).
- **Mission docs**: spec.md FR-021 to FR-023, SC-006; data-model.md "Our own terms" is the canonical
  wording for the four terms.
- **Stale spots found in the survey** (verify each before editing; the branch may have moved):
  - `docs/architecture/logical-model.md` around line 167: "the resolver is not built yet".
  - `docs/glossary/charter/README.md` `{#compose}`: defers the boundary to "ADR-0048 open question 6",
    settled by the 2026-11-02 rulings.
  - `docs/glossary/governance/README.md` (Pack Level) and `docs/glossary/charter/README.md` (Charter
    Pack): both end with "Decided, not built yet: ADR-0048 composes packs through `depends_on`" — now
    built.
  - `docs/plans/pack-ecosystem-work-breakdown.md`: items 2 and 3 unmarked.
- **Constraints**: C-004 (the open-packs split — renaming `built-in`, re-homing artifacts — is out of
  scope; do not document it as done), C-005 (no URN kind-token spelling decisions leak into docs
  beyond citing the ruling). Documentation-policy style guide applies; the glossary's canonical
  spellings win over prose preferences.

## Branch Strategy

- **Strategy**: worktree-per-lane (allocated from `lanes.json` by `spec-kitty agent action implement`)
- **Planning base branch**: `issue-145-pack-composition`
- **Merge target branch**: `issue-145-pack-composition`

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask T020 – Describe composition in the logical model and architecture pages

- **Purpose**: FR-021: the docs must not describe a pack model the code no longer has.
- **Steps**:
  1. In `docs/architecture/logical-model.md`: replace the "resolver is not built yet" note with the
     delivered shape — the descriptor's `depends_on` (PEP 440 ranges), the compose as one closure at
     one level, the resolver's ordering and its refusal codes, the settling `overrides` edge, and the
     `<pack-name>::<urn>` qualifier with bare in-pack references. Keep the page's existing structure
     and depth; link to the ADRs rather than restating their rationale.
  2. In `docs/architecture/descriptors.md`: complete the `depends_on` documentation from "accepted and
     read" to "enforced" — the conditional `pack_level` requirement, the range grammar shared with
     `requires_core`, the `overrides` edge, and the ULID rule on `pack_id`/`parent_pack`/dependency
     targets (FR-003's reader-facing statement).
  3. In `docs/architecture/pack-registration-distribution.md`: state where composition sits in the
     read path (catalog → descriptors → resolver → offering), that a refused compose refuses the
     offering rather than serving part of it, and the compatibility statement for the single
     `built-in` tree (FR-018).
  4. Cite the delivered commits' behaviour, not their hashes: describe what the code does.
- **Files**: `logical-model.md`, `descriptors.md`, `pack-registration-distribution.md`.
- **Parallel?**: Independent of T021–T023 (different files).
- **Notes**: Every architectural claim must be checkable against the code or the contract; where a
  page only needs the reader to know composition exists, link to `logical-model.md` instead of
  duplicating (SC-006 allows either, prefers the shorter page).

### Subtask T021 – Carry the four terms in the glossary and the contextive definitions

- **Purpose**: FR-022: Compose, Closure, Dependency edge and Qualified reference defined in the
  glossary and the contextive definitions, so specification and code use one vocabulary.
- **Steps**:
  1. In `docs/glossary/charter/README.md`: update the `{#compose}` entry to the ruled boundary (one
     `depends_on` closure at one level; ADR-0053 decision 6 — no longer an open question); add or
     complete entries for **Closure** (the set reachable from the configured packs through
     `depends_on`, each id once), **Dependency edge** (one declared `depends_on` item: target id plus
     PEP 440 range) and **Qualified reference** (`<pack-name>::<urn>`, URN opaque); update the Charter
     Pack and Pack Level entries' "Decided, not built yet" tails to the built state.
  2. In `site/.contextive/definitions.yaml`: align the contextive terms with the glossary wording
     (the `50b1330e` pass already added Closure and Dependency Edge — verify and complete, including
     Qualified reference).
  3. Use data-model.md "Our own terms" as the canonical phrasing; do not coin variants.
- **Files**: `docs/glossary/charter/README.md`, `site/.contextive/definitions.yaml`.
- **Parallel?**: Independent of the other subtasks.
- **Notes**: The glossary's cross-reference style (`{#anchor}`, ADR links) must be preserved; the docs
  checker validates anchors and links.

### Subtask T022 – Mark work-breakdown items 2 and 3 done

- **Purpose**: FR-023: the split's plan reflects what landed.
- **Steps**:
  1. In `docs/plans/pack-ecosystem-work-breakdown.md`, strike through items 2 and 3 in the ordered
     work table with a done note and date (2026-11-03), mirroring how item 1 is marked.
  2. Item 2: "`depends_on` and `parent_pack` checks in the pack-descriptor schema; the range grammar"
     — done by this mission (descriptor schema, `Pep440Range`).
  3. Item 3: "The dependency resolver: ordering, cycles, ranges, dedupe by `pack_id`, sibling
     refusal; a compatibility shim keeping the single `built-in` tree loading" — done by this mission
     (resolver core plus WP01's sibling refusal and WP04's ratchet).
  4. Do not touch items 4 to 14; their status is not this mission's to change (C-004 keeps the split
     itself out of scope).
- **Files**: `docs/plans/pack-ecosystem-work-breakdown.md`.
- **Parallel?**: Independent of the other subtasks.
- **Notes**: Verify WP03's changelog fragment exists and reads where readers look
  (`contracts/charter/changes/`); if it does not, record that in the WP history rather than writing a
  duplicate.

### Subtask T023 – Sweep every pack-model page and pass the docs checks

- **Purpose**: SC-006: every page that describes the pack model either describes `depends_on` and the
  compose or links to the page that does; zero docs findings.
- **Steps**:
  1. Grep the docs tree for pack-model descriptions: `grep -rln "pack" docs/architecture/
     docs/glossary/ docs/development/` and read the hits; sweep for stale phrasings ("single tree
     only", "no dependencies", "load order", "not built yet", "open question 6").
  2. For each page that describes the pack model, either state the new shape or link to
     `logical-model.md` — choose the shorter edit that keeps the page honest.
  3. Run `python3 scripts/docs/check_docs.py all` from the repo root; fix every finding in the owned
     files. Findings in files this WP does not own are recorded in the WP history and reported, not
     silently edited (a one-line, well-justified out-of-map fix for a broken link this mission caused
     is acceptable with a rationale).
  4. Run the markdown and spelling checks the repo uses (markdownlint, cspell) if configured.
- **Files**: the four owned docs files plus the contextive source; out-of-map touches only with a
  recorded rationale.
- **Parallel?**: Runs after T020–T022 so the sweep sees the final text.
- **Notes**: The mission files under `kitty-specs/…/` are not this WP's surface; they are the mission
  record, not the documented model.

## Test Strategy

- Docs are the artefact: `python3 scripts/docs/check_docs.py all` must exit zero. Where the repo
  configures markdownlint/cspell, run them the way CI does.
- No compiler applies to this WP; the docs checks are the gate.

## Risks & Mitigations

- **Docs drift from code** → cite the delivered behaviour (resolver, refusal codes, contract fields)
  and the ADRs; avoid restating mechanics that will change with the split (C-004).
- **Glossary wording variants** → data-model.md's terms are canonical; one spelling each.
- **Anchor/link rot from the sweep** → the docs checker catches it; run it before declaring done.

## Review Guidance

- **Reviewer profile**: `reviewer-renata` (load via `/spk-charter-profile-load` before reviewing).
- Verify: no "not built yet"/"open question 6" phrasings remain for composition; the four terms are
  defined once, consistently; items 2 and 3 are marked done and items 4+ untouched;
  `check_docs.py all` exits zero; every out-of-map edit carries a one-line rationale in the WP
  history.
- Revisit spec.md FR-021 to FR-023 and SC-006 before approving.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this
prompt file. Do **not** hand-edit a history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history WP05 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP05 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

## Implementation Command

```bash
spec-kitty agent action implement WP05 --agent opencode --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541
```
