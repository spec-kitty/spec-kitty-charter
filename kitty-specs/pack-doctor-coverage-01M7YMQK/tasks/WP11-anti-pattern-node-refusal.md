---
work_package_id: WP11
title: A file-less anti_pattern node is refused (R-r;
dependencies:
- WP10
requirement_refs:
- FR-014
- NFR-001
- NFR-003
- C-001
- SC-004
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
subtasks:
- T043
- T044
- T045
phase: Phase 1 - Foundation
history:
- at: '2026-11-21T22:30:00Z'
  actor: planner-priti
  action: Prompt generated for ruling R-r (all of #154 in this PR) - the file-less node refusal, ADR-0052 d14 and ADR-0053 d31
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/RejectsIsNotCascadedTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/AntiPatternNodeCheckTest.java
- contracts/charter/scenarios/registration/anti-pattern-nodes.feature
- contracts/charter/changes/2026-11-21-anti-pattern-node-refusal.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/RejectsIsNotCascadedTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/antipattern/AntiPatternNodeCheckTest.java
- contracts/charter/scenarios/registration/anti-pattern-nodes.feature
- contracts/charter/changes/2026-11-21-anti-pattern-node-refusal.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP11 - A file-less anti_pattern node is refused (R-r; #154)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, ADR-0052 decisions 11, 13 and 14, ADR-0053 decision 31, ADR-0064
decision 12, the mission `spec.md` (FR-014 and the edge cases), `plan.md` (**D13, read it whole**, and D12's
"What already exists"), `data-model.md` ("File-less `anti_pattern` node") and ruling R-r in `decisions/`
(`DM-01M801H54DXR0447M3F2ZQWFPZ`).

## Objective

Refuse a pack whose graph document declares an `anti_pattern` node that the same pack does not hold as an
anti-pattern file (FR-014). One content rule, on every surface, with an existing code. Pin the two #154 behaviors that
already exist (a cross-pack `rejects` needs `depends_on`; activation does not follow `rejects`), so #154 closes with a
test per bullet.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP11 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- WP10 has landed in your base: `built-in/anti_patterns/` holds 18 files and every `anti_pattern` node of the example
  packs has its file. Check this first (`ExamplePackCoverageTest`'s node-has-file guard is green); if not, stop.
- **The rule's home** is `GraphRules` (a content rule set composed by `PackRulesets.content()`, `PackRulesets.java:24-34`).
  `GraphRules.tensions` already builds the pack's held set from `contents.artifacts()` (`GraphRules.java:116-118`) and
  walks the documents the read read (`GraphRules.read`). Do the same; write no traversal (NFR-003, ADR-0082).
- **The code is `drg_schema_error`**, as for every other node a pack may not declare (a guard, lifecycle or routing
  catalog node: `GraphBinding.notAuthored`, `GraphBinding.java:33-51`; the finding at `/nodes/<n>`, `:154-155`).
  `drg_schema_error` is a contract enum value (`CharterRefusalCode.yaml:263, 348`). Do not mint a code.
- **Only the pack's own nodes.** A shard node URN carries no pack (`graph-shard.schema.json:84`); a cross-pack
  rejection is a pack-qualified **edge endpoint**, which the reference check judges (`pack_reference_pack_missing`,
  `PackCompositionResolver.java:189-190, 582-600`), never this rule.
- `action` nodes stay graph-only (a mission type's step). A file-less node of another kind (a `tactic`, say) is
  accepted today (`CompositionFactsTest:94`, `tactic:declared-only`) and stays so: ADR-0052 d14 rules this kind only.

## Subtasks

### T043 — The rule, red first
- `GraphRulesTest`, red first:
  - a shard node `anti_pattern:x` with no `anti_patterns/x.anti-pattern.yaml` gives exactly one `drg_schema_error` at
    the shard and `/nodes/<n>`, with the detail
    `<origin>: anti_pattern:x is declared with no file; an anti-pattern is held by anti_patterns/x.anti-pattern.yaml in the same pack`;
  - a fragment node `{id: x, kind: anti_patterns}` with no file gives the same at `drg/fragment.yaml`;
  - the same node with its file gives nothing;
  - an `action` node and a file-less `tactic` node give nothing;
  - an edge whose target is `other::anti_pattern:x` gives nothing from this rule;
  - two file-less nodes in two documents give two findings, shards before the fragment, in document order, after the
    tension findings of the same pack.
- `GraphRules.ANTI_PATTERN_WITHOUT_FILE`, a `Rule<PackContents>` appended to `GraphRules.RULES` after
  `TENSION_CONSISTENCY`, with a Javadoc naming ADR-0052 d14 and ADR-0053 d31, and the class Javadoc's list updated.

### T044 — Tests that relied on a file-less node
- `CompositionFactsTest.theNodesAGraphDeclaresForAKindWithNoFileAreDefined` (`:83-104`): give `copy-paste` and
  `shotgun-surgery` their anti-pattern files (canonical), keep the `action` and `tactic` assertions, and rename the
  test after what it now shows. WP03 renames its `defined` reads later (an out-of-map edit of its own).
- `DomainRepositoryServiceTest` (`:593-630`, "An anti_pattern is never a file"): add the file beside the node; the
  qualified-form and bare-form assertions stay; rewrite the Javadoc (the file holds it, the node declares it).
- Census, then fix: `rg -n "kind: anti_pattern\b|kind: anti_patterns|urn: '?anti_pattern:" service/*/src/test service/*/src/testFixtures contracts`.
  Each hit that registers or validates a pack becomes canonical (a file beside the node) or an explicit refusal test.
  `GraphDocumentReaderTest:88-106` reads a document only, runs no content rule, and stays. A hit in a file you do not
  own is a justified out-of-map edit; list it.

### T045 — Every surface, and the two existing behaviors pinned
- New `contracts/charter/scenarios/registration/anti-pattern-nodes.feature`, with the existing whole-pack steps:
  - a pack whose `styleguide.graph.yaml` declares `anti_pattern:x` with no file is refused 422 `validation_failed`,
    violation `drg_schema_error` at the shard, with the detail above;
  - the same from a fragment node;
  - control: the pack with `anti_patterns/x.anti-pattern.yaml` registers;
  - a pack whose `rejects` edge targets `other::anti_pattern:x` where `other` is not in its `depends_on` is refused
    `pack_reference_pack_missing` (ADR-0052 d11: the existing check, now pinned for the kind). If the steps cannot stage
    two packs, pin it in `AntiPatternNodeCheckTest` through `PackSetCheck` instead and say so in the review notes.
- `AntiPatternNodeCheckTest` (`pack-validation`, validate mode over a copied example pack): the same planted node
  gives the same code, pointer and detail as registration (ADR-0075 d9).
- `RejectsIsNotCascadedTest` (beside `TensionIsNotCascadedTest`): selecting a styleguide that rejects an anti-pattern
  does not activate the anti-pattern, and selecting the anti-pattern itself does (it is activatable, ADR-0052 d13).
  This pins existing behavior (`CascadeRules.CASCADE_RELATIONS`, `CascadeRules.java:64-70`): it is green on arrival.
  Show it can fail: add `REJECTS` to the set locally, see it red, revert, and say so in the review notes.
- Change fragment `2026-11-21-anti-pattern-node-refusal.md` under `### Breaking`: a pack with a file-less
  `anti_pattern` node, which registered before, is refused `drg_schema_error`; no schema of the charter contract
  changes, so core regenerates nothing.

## Test commands

```bash
./gradlew :packs:test :pack-validation:test :activations:test :pack-doctor:test
./gradlew :deployable:test     # the contract scenarios
./gradlew :pack-validation:wholePackRoundTrip
./gradlew spotlessCheck
```

## Definition of Done
- [ ] One rule in `GraphRules`, composed by `PackRulesets.content()`; no new walk; no new code.
- [ ] Registration and `pack-doctor validate` refuse a file-less node with the same code, pointer and detail.
- [ ] No test or fixture keeps a graph-only `anti_pattern` node except as an explicit refusal test.
- [ ] The cross-pack `rejects` refusal and the non-cascade are pinned by tests.
- [ ] A `### Breaking` fragment. Every behavior change was red first (NFR-001).

## Stop conditions
- An example pack goes red under the rule: WP10 is incomplete in your base. Stop and report; do not edit WP10's files.
- The rule needs facts the one read does not carry (a second read, a store call): stop. D13 rests on
  `contents.artifacts()` and the read graphs.
- A test can only pass by excluding a field or exempting a fixture: stop and report (charter).

## Risks and reviewer guidance
- A reviewer checks the rule's place (after the tension rule), the code (`drg_schema_error`, existing) and that only
  `anti_pattern` nodes are judged.
- Comments that say an anti-pattern is "held only as a graph node" remain in `PackCompositionResolverTest` (WP05's)
  and `CompositionGoldenTest` (WP03's); their owners refresh them. They are comments over facts built in memory, not
  packs, so no test changes.
- Open-packs has 13 file-less nodes (`packs/architecture/drg/fragment.yaml` 6, `packs/refactoring/drg/fragment.yaml`
  7): refused at its repin; drafted in `research/open-packs-TBD-anti-pattern-files.md`.
