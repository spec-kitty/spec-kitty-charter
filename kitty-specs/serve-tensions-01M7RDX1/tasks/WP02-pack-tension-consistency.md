---
work_package_id: WP02
title: Pack tension consistency
dependencies:
- WP01
requirement_refs:
- FR-004
subtasks:
- T009
- T010
- T011
- T012
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- service/packs/src/test/resources/load-fixtures/shard-tension-duplicated/**
- service/packs/src/test/resources/load-fixtures/shard-tension-orientation-reversed/**
- service/packs/src/test/resources/load-fixtures/shard-tension-source-not-owned/**
- service/packs/src/test/resources/load-fixtures/shard-tension-self/**
- service/packs/src/test/resources/load-fixtures/shard-tension-canonical/**
- service/pack-validation/src/test/resources/golden/validate.json
- service/packs/src/testFixtures/resources/planted/tension-duplicated/**
create_intent:
- service/packs/src/test/resources/load-fixtures/shard-tension-duplicated/expected.json
- service/packs/src/test/resources/load-fixtures/shard-tension-orientation-reversed/expected.json
- service/packs/src/test/resources/load-fixtures/shard-tension-source-not-owned/expected.json
- service/packs/src/test/resources/load-fixtures/shard-tension-self/expected.json
- service/packs/src/test/resources/load-fixtures/shard-tension-canonical/expected.json
- service/packs/src/testFixtures/resources/planted/tension-duplicated/expected.json
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

# Work Package Prompt: WP02 - Pack tension consistency

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
- Start with `spec-kitty agent action implement WP02 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

A pack whose tensions are inconsistent is refused at load, at validation and at registration, and the reader never
corrects it (ADR-0045 decisions 1 and 6, ruling G1; spec FR-004, US3 AS2 and AS3; plan IC-02 and the Revisions
section). Within one pack, across every root `*.graph.yaml` shard and `drg/fragment.yaml`:

| Code | Fault | Reported at |
| --- | --- | --- |
| `tension_duplicated` | a second tension on the same unordered bare pair | the second edge (`/edges/<n>` of its file), naming the first's file and index |
| `tension_orientation` | the pack owns one side and the `source` is the other; or the pack owns both sides, or neither, and `source` is not the smaller bare URN | the edge |
| `tension_self` | both sides have the same bare URN (for example `directive:A` and `other::directive:A`) | the edge |

All three are `VIOLATION`. Across packs nothing is a fault: the merge collapses (WP04).

## Context

- `PackRulesets.CONTENT` (`packs/domain/rules/PackRulesets.java:20-29`) runs `GraphRules.RULES` first.
- `GraphRules` (:37-49) has `UNBOUND` and `ENDPOINT_NOT_ALLOWED` (:61-92). A pack-wide rule iterates `read(contents)`
  (:95-104) over the shards and the fragment, and reports with `Findings.at(code, file, List.of("edges", i), ...)`.
- Codes live in `PackCodes` (:35-42); the `CONTENT` list (:93-107) must include any new code. `PackCodesTest` checks
  the token form and uniqueness.
- These codes reach the wire as violations of the existing refusals (`pack_unreadable` on load, `validation_failed` on
  registration), so `CharterRefusalCode.yaml` does not change.
- "Owned by the pack": the bare URN names an artifact the pack itself holds (a source with no pack qualifier, or one
  qualified with the pack's own name, resolves to the pack's artifact). Use the pack contents the rule already sees;
  do not call the store.
- WP01 already refuses a self-tension in `Relationship`'s constructor. If that makes the binder fault first with
  `drg_schema_error`, decide which code the reader answers and keep one: the consistency rule's `tension_self` is the
  pack-level code the spec names. Record the choice in the activity log.

## Subtasks

### T009: The codes

Add `TENSION_DUPLICATED`, `TENSION_ORIENTATION`, `TENSION_SELF` to `PackCodes` (kernel `ReasonCode`, `VIOLATION`) with
their wording functions, and to `CONTENT`. `PackCodesTest` covers them.

### T010: The rule

A third `.must(...)` in `GraphRules`: `TENSION_CONSISTENCY`.
1. Collect every tension edge of the pack in reading order (shards in file-name order, then the fragment; document
   order within a file), with its file and index.
2. For each: bare source and target (strip the qualifier). Self → `tension_self`. Else if exactly one side is owned
   and it is not the source → `tension_orientation`. Else (both owned, or neither) if `bare(source) > bare(target)` →
   `tension_orientation`. A tension between two artifacts the pack owns neither of is allowed (operator ruling of
   2026-11-19, extending G1).
3. Key on the unordered bare pair; a second edge on a seen key → `tension_duplicated`, naming the first.
4. The wording names the pair in canonical order, and for orientation the expected source.

### T011: Fixtures and goldens

- Load fixtures (same layout as `shard-tension-binds-as-tension`), one per fault, plus `shard-tension-canonical` as
  the positive control: a tension `directive:A tension directive:B` beside `directive:B requires directive:A`, which
  loads clean.
- `shard-tension-duplicated` puts the two tensions in two different shards (the rule is pack-wide).
- `shard-tension-source-not-owned`: the pack owns `directive:B`, and the source is `other::directive:A`, qualified to
  another pack (the owned side must be the source).
- Positive control `shard-tension-canonical` also holds a fully foreign tension, `base::directive:X tension
  base::directive:Y`, smaller first, which loads clean; its reversed twin is refused.
- Update `golden/validate.json` if validate mode enumerates the codes; add planted cases under
  `testFixtures/resources/planted/tension-*` if the planted suite is how validate mode is proven (follow the existing
  planted pattern and `core-verdicts.json` only if a core verdict exists; otherwise mark it charter-only as the
  neighbours do).

### T012: Tests

`GraphRulesTest`: one test per fault with its exact code, file and pointer, each beside a positive twin that differs
only in the faulty field (the same pair canonical; the same tension once; two different URNs). A pack with a
`requires` and a tension on one pair is clean. Two tensions on the same pair in two different packs are not judged
here (one pack per run).

## Definition of Done

- `./gradlew :packs:test :pack-validation:test --max-workers=1` pass; the round trip over the example packs passes.
- Registration of a pack with any of the three faults is refused (the registration path already runs `CONTENT`;
  confirm with one test or scenario row, and record which).

## Risks and reviewer guidance

- Judge on bare URNs; a qualified source to the pack's own name counts as owned.
- Reviewers: run each fixture against its twin; a rule that refuses the canonical fixture is a defect.
