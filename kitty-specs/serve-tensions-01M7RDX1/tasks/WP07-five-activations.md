---
work_package_id: WP07
title: The five example activations
dependencies:
- WP03
- WP05
- WP06
requirement_refs:
- SC-001
- SC-003
subtasks:
- T035
- T036
- T037
authoritative_surface: contracts/charter/scenarios/tensions.feature
owned_files:
- contracts/charter/scenarios/tensions.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveGraphSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/TensionSteps.java
- contracts/charter/scenarios/README.md
create_intent:
- contracts/charter/scenarios/tensions.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/TensionSteps.java
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge
  back into feat/602-tensions unless the human explicitly redirects the landing branch.
agent_profile: java-jenny
role: implementer
agent: claude
execution_mode: code_change
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
tracker_refs:
- '#602'
tags: []
---

# Work Package Prompt: WP07 - The five example activations

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/serve-tensions-01M7RDX1/`: `spec.md`, `plan.md` (its last section, "Revisions after the
plan-point squad and rulings G1 to G3", wins over the concerns above it), `data-model.md`, `research.md`,
`research/squad-plan.md`; and `docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md`
(accepted, with rulings G1 to G3). Read `.kittify/charter/charter.md` and `AGENTS.md`.

Charter rules that bind every subtask:

- The charter's model is canonical: no reader of `in_tension_with`, no shim, no deprecation window.
- Tests and examples carry only the canonical shape, except error-handling fixtures. Never exclude a field from a
  parity or round-trip check to make a test pass.
- Tests are held to the production bar: outcome assertions, a positive control beside every refusal, no fake green.

## Branch Strategy

- Planning base and final merge target: `feat/602-tensions` (PR #606 to `main`). The mission branch is
  `kitty/mission-serve-tensions-01M7RDX1`.
- The execution worktree is allocated per computed lane from `lanes.json`.
- Start with `spec-kitty agent action implement WP07 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

Prove #602's five cases end to end over WP04's `tensions` example pack, through the three reads, as `@contract`
scenarios that pass the ADR-0059 D1 gate (spec FR-012, SC-001, SC-003, US1 and US2; plan IC-05 to IC-07 and the
Revisions section). Activations are scenario configuration, not pack content: the five activations are inline
documents in `tensions.feature`.

## Context

- Pack steps: `Given the example pack "<name>" of the charter` (`CharterSteps.java:126`, `ScenarioPacks.addExamplePack`
  :164-185). Activation steps: `the activation {string} holds:` (`ActivationSteps.java:85`).
- Graph steps: `EffectiveGraphSteps` (:34-93), "the graph holds exactly the edges:" prints only source, relation and
  target, so it is blind to the words (squad T5).
- A registration in a scenario: the `composition.feature:1597` pattern (remember the ETag from a GET, then PUT with
  If-Match). Read a stored file with `ScenarioPacks.fileOf`.
- Step vocabulary: `contracts/charter/scenarios/README.md`; add any new step there.

## Subtasks

### T035: Steps

`TensionSteps` (or new methods in `EffectiveGraphSteps`):
- "the graph's tension edges are exactly:" with one line per edge, `<source> tension <target> reconciledBy [<urns>] when <w|-> reason <r|->`;
- "the context's tensions are exactly:" in the same form;
- "the activation's tensions are exactly:" and "the activation's findings are exactly:" with
  `<source> <target> resolutions [<list>] links [<relations>]`;
- "the edge <source> <relation> <target> has no tension words".

### T036: `tensions.feature`

One activation per case, selecting the case's artifacts (and `RECONCILE_*` where the case has one). For each: the
effective graph, one action context that lists both sides (pick an action and mission type the pack's built-in
dependency offers, so directives are listed project-wide), and the tensions read.

| Case | Expect |
| --- | --- |
| 1 reconciled | graph edge with `reconciledBy [RECONCILE_REC]`; context lists it with the same; tensions read lists it; no finding |
| 2 unreconciled | `reconciledBy []` (the one-sided `RECONCILE_HALF` is not a reconciler); one finding, `resolutions [reconcile, deactivate_one_side]`, links `exclude-source`, `exclude-target`, `effective-source`, `effective-target` |
| 3 one side out | B excluded: no tension edge, no context tension, no tension, no finding; positive twin: the same activation without the exclusion has it |
| 4 reverse order | PUT `{"from": "directive:TENSION_REG_B", "to": "directive:TENSION_REG_A", "type": "tension"}` → 201, the answer echoes it; the stored `registered.graph.yaml` has `source: directive:TENSION_REG_A`; one tension and one finding, keyed `A`, `B` |
| 5 requires and tension | the graph has the `requires` edge with no words and the tension edge; the tensions read lists the tension; the finding's `exclude` link for the required side is absent (G3) |

Also: the context of an activation where a side is only a suggestion, at depth 0 and depth 1 (no tension in either;
twin: case 1's context has it); a mission type not in force answers `tensions: []` while the tensions read of the same
activation lists the tension.

### T037: Follow-through

- Case 2: PUT the `exclude-source` link with If-Match = the tensions answer's revision → success; read the tensions
  again → no finding, no tension.
- A locked side (if the pack's policy or the activation can lock one; otherwise use the required side of case 5):
  no exclude link for it, and when neither side can be excluded, `resolutions [reconcile]`.

## Definition of Done

`./gradlew :deployable:test --max-workers=1` passes with the new feature; `verifyAll` passes (switch gate, link checks,
schema checks over every exchange).

## Risks and reviewer guidance

- Each case must fail before WP04 to WP06 (check one by reverting a WP locally, or by reasoning recorded in the
  activity log): no vacuous scenario.
