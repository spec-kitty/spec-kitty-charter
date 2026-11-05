---
work_package_id: WP15
title: Documentation and divergences for PR B
dependencies:
- WP10
- WP14
requirement_refs:
- C-006
- FR-013
- FR-014
- FR-015
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T090
- T091
- T092
- T093
agent: claude
history: []
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-05-activation-writes-and-cascade.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/changelog/2026-11-05-activation-writes-and-cascade.md
role: implementer
tags: []
tracker_refs: []
---

# WP15: Documentation and divergences for PR B

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make the docs say what PR B ships, and record its divergences from core (C-006):

- divergence entries for:
  - the lineage pass-through (`specializes_from`, `requires` and `scope` only, never in force);
  - the `includeDeactivated` `enhances` clearing, and `enhances` never written into `selected`;
- the architecture docs for the cascade: what a write walks, what it reports, and what is left to the read-time pull;
- a docs changelog fragment for PR B;
- the #360 and #291 notes for the PR B description, delivered in the review notes.

Docs only: this WP changes no code, contract or test.

Implementation command:
`spec-kitty agent action implement WP15 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** B (its last WP). **Concern:** IC-10 (B half).
- **Rulings:** ADR-0059 D2 as revised on 2026-11-05 (D2.1 the five relations; D2.2 `enhances` at read only, with the
  two write-time exceptions; D2.4 lineage), ADR-0059 D6 (presets), ADR-0013 D4, ADR-0020 D6.
- **Superseded:** the 13:15 discovery decisions `DM-01M6NXERTNKMWK4MTDTX48358B` (write-time `enhances`) and
  `DM-01M6NXKAHRQNR4RK14FFJN2TVM` (lineage over every relation). The docs describe the 2026-11-05 rulings, not those.
- **Grounding:** WP10 added DIV-203 to DIV-205 and renamed the block. The next free ids are DIV-206 onwards. The two
  register forms are kept equal by `DivergenceRegisterTest`.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR B).
- Lane worktree from `lanes.json`. It depends on WP14, so the docs describe merged behaviour, and on WP10, whose files
  it edits out of map (A2).
- Rebase onto the lane base before review. At closeout this WP's commits go only into PR B; PR A replays WP01–WP10, WP16 and WP17.

## Out-of-map edits (sequenced by dependency)

P1 (the PR cut): this WP touches **no** file WP10 owns except the edits listed here. The PR A replay takes WP01–WP10, WP16 and WP17's
commits only, so an unlisted edit to a WP10 file would leak PR B content into PR A's history.


- `docs/conformance/divergence-register.md` and `docs/conformance/divergences.yaml` (owner WP10): add DIV-206 and
  DIV-207.
- `docs/architecture/offering-and-activation.md` and `docs/architecture/resolution-model.md` (owner WP10): the cascade
  section and the per-artifact writes.
- `docs/adr/0013-persistent-deactivation.md` (owner WP10): only if the cascade text there contradicts the built
  behaviour; prefer a link to ADR-0059 D2 over new prose.

## What the built behaviour is (the docs must say exactly this)

Check each statement against the scenarios in `activation-selections.feature` and `activation-presets.feature`
before writing it down:

| Topic | Built behaviour | Scenario |
|---|---|---|
| Relations a write walks | `requires`, `suggests`, `refines`, `scope`, `instantiates`, forward, unbounded, each node once | US3 AS1, the cycle case |
| Kind scope | `cascade` kinds, or exactly `["all"]`; a mix answers 422 at `/cascade`; out-of-scope reached artifacts are in `skippedByScope` | US3 AS2, AS8 |
| Deny | the walk stops at a denied artifact: `skippedDeactivated` with `viaRequires` and `requiredBy` (artifacts) | US3 AS1 |
| Behind a deny | `unreachedBehindDeactivated` | US3 AS4 |
| Lineage | through every `specializes_from` ancestor, `requires` and `scope` only; no ancestor in force; a denied ancestor stops it | US3 AS4 |
| `includeDeactivated` | takes denied artifacts back in and clears their deny entries, plus every deny entry the `enhances` pull would reach from what the walk selects (both ways, transitive over `enhances`); nothing pulled is selected | US3 AS3 |
| Deactivate | only the named id is denied; with a cascade, what only it reached leaves `selected`; shared items stay in `skippedShared` (active = selected ∪ floor; active enhancers count) | US4 AS1, AS2 |
| Floor | deactivating a required or locked artifact answers 409 and changes nothing | US4 AS3 |
| Presets | each listed key is replaced, `activatedKinds` and `missionTypes` included; `force` clears matching deny entries | US5 |
| Not built | the read-time `enhances` pull, the effective reads, the layer merge | (none: still `501`) |

## Subtask Guidance

### Subtask T090: Divergence entries DIV-206 and DIV-207

**Steps:**
1. **DIV-206, lineage pass-through:** a cascade passes through every `specializes_from` ancestor of a profile it
   reaches, following only `requires` and `scope` from each ancestor, without putting any ancestor in force; a denied
   ancestor stops it and is reported. Core's cascade does not follow `specializes_from`. Ruling: ADR-0059 D2.4.
   Corpus case: `cascade-lineage-pass-through`.
2. **DIV-207, `enhances` at write:** a write never adds an enhancer to `selected`; with `includeDeactivated`, it clears
   every deny entry the read-time `enhances` pull would reach from what the walk selects, in both directions and
   transitively over `enhances`; a cascading deactivate keeps an
   artifact an active enhancer references (`skippedShared`). Core does not follow `enhances` in its cascade. Ruling:
   ADR-0059 D2.2. Corpus case: `cascade-enhances-clearing`.
3. Add both to both forms with the same ids and status as DIV-203 to DIV-205; run
   `./gradlew :packs:test --tests '*DivergenceRegister*'`.

### Subtask T091: Architecture docs for the cascade

**Steps:**
1. `offering-and-activation.md`: a "Cascade" section covering:
   - the five relations, forward and unbounded, with the visited guard;
   - the stop at a deny entry, and the four report buckets with what each means;
   - the lineage pass-through;
   - `includeDeactivated`, and the deactivate shared check.

   It names `packs` as the owner of the traversal (`Reachability` on `GraphWalk`) and `activations` as the owner of
   the policy (`CascadeRules`), with one verified closure view per request. Add a short mermaid sequence (controller →
   service → session → `ClosureQueries` → reach → rules → repository) if it replaces prose.
2. State the read-time `enhances` pull explicitly as **not built here** (the next mission, the effective reads). The
   effective reads and the layer merge remain `501`. Only the four effective reads stay reserved.
3. `resolution-model.md`: point to the cascade section; the per-artifact writes are now served.

### Subtask T092: The docs changelog fragment

**Steps:**
1. Create `docs/changelog/2026-11-05-activation-writes-and-cascade.md` (date it the day you write it, and update the
   path in this WP's `owned_files` only if the date changes) with the `okf_version` frontmatter, as WP10's fragment
   has.
2. Body: the five writes are served; the cascade rules; `enhances` is not written at write time; presets replace each
   listed key; four operations stay reserved; DIV-206 and DIV-207.

### Subtask T093: PR description notes (#360, #291) and docs gates

**Steps:**
1. Write in the review notes (not a file) the PR B description paragraphs:
   - **#291:** the nine operations #125 reserved (eight writes and `getActivation`) are built across PR A (CRUD) and PR B (select, unselect, deactivate,
     reactivate, preset). List the operations and their scenario files. PR B closes #291 if the effective reads are
     tracked elsewhere (#103); say so.
   - **#360:** confirm the acceptance re-point made in PR A (the closure read), and that the writes see only the
     closure (FR-010).
   - **Open review items:** the closure-scoped floor (DIV-205), pins refused rather than served side by side,
     ADR-0059 open questions 1 and 4, and WP14's "listed key" decision.
2. Docs gates:
   ```bash
   python3 scripts/docs/check_docs.py all
   npx cspell@9.2.0 --no-progress
   ./gradlew :packs:test --tests '*DivergenceRegister*'
   ```

## Definition of Done

- [ ] DIV-206 and DIV-207 are in both register forms; `DivergenceRegisterTest` passes.
- [ ] `offering-and-activation.md` has the cascade section, matching ADR-0059 D2 as revised; the read-time pull is
      marked "next mission".
- [ ] The PR B changelog fragment exists with valid frontmatter.
- [ ] The #291 and #360 PR-description notes and the open review items are in the review notes.
- [ ] The docs checks and spelling pass.

## Risks

- **Describing the superseded 13:15 decisions.** Check every cascade sentence against ADR-0059 D2.2 and D2.4.
- **Overclaiming the effective reads.** They are `501`.

## Reviewer guidance (reviewer-renata)

- **Check the lineage text says "`requires` and `scope` only"**, and the `enhances` text says "never written into
  `selected`".
- **Check both register forms** carry DIV-206 and DIV-207; run `DivergenceRegisterTest`.
- **Non-fakeable evidence:** the `check_docs.py all` output, the cspell summary, and the PR-description notes pasted in
  the review.
