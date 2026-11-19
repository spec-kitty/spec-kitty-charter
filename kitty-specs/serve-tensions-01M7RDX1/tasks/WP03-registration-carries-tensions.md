---
work_package_id: WP03
title: Registration carries tensions
dependencies:
- WP01
- WP02
requirement_refs:
- FR-005
subtasks:
- T013
- T014
- T015
- T016
- T017
- T018
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipWire.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipDraft.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipMerge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRemovalController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipMergeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/InboundReferencesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RelationshipWireTest.java
- contracts/charter/paths/packs_packId_relationships.yaml
- contracts/charter/schemas/RelationshipRegistration.yaml
- contracts/charter/schemas/RelationshipRegistered.yaml
- contracts/charter/examples/RelationshipRegistration.tension.yaml
- contracts/charter/examples/RelationshipRegistered.tension.yaml
- contracts/charter/scenarios/relationships.feature
- contracts/charter/scenarios/pack-removal.feature
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipMergeTest.java
- contracts/charter/examples/RelationshipRegistration.tension.yaml
- contracts/charter/examples/RelationshipRegistered.tension.yaml
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

# Work Package Prompt: WP03 - Registration carries tensions

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
- Start with `spec-kitty agent action implement WP03 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

`putPackRelationship` accepts `type: tension`; the registered shard keeps tensions through every rewrite;
`deletePackRelationship` removes per pair class; removing an artifact takes its registered tensions with it (ADR-0045
decisions 1 and 5, ruling G1; spec FR-005, US3 AS5 and AS6; plan IC-03 and the Revisions section).

## Context (re-check each line)

- Wire: `RelationshipWire.fromWire` (:49-110): `RelationType.fromToken(type)` or `relationship_type_unknown` at `/type`
  (:92, :129-133); `edgeTargetOf` (:97); self-loop check (:100); `from` must not be qualified (:77-81).
- `RelationshipDraft.bind()` (:90) → `RelationshipRegistrationService.bound` (:151-169) → `writeStore.registeredRelationships`
  → `RelationshipMerge.upsert` → `PackWriteStore.Change.relationships` → `RelationshipShard.write` → `GraphShardWriter`.
- `RelationshipRegistrationService:133-138` authorizes on `relationship.from()` and answers it; `:135` `created =
  existing.stream().noneMatch(relationship::equals)`; `:234-237` a private `samePair` used by the step-enhancement
  guards (:218, :226).
- `RelationshipMerge.upsert` (:23-32) drops an existing edge that `equals` the incoming one: after WP01 that is the
  slot, which is what we want; confirm and document it.
- `PackRegistrationService.edgeKey` (:557-563) and `edgeWrites` (:607-615) key on `from`, ordered.
- Removal: `PackRemovalController.deleteRelationship(packId, from, to, ifMatch)` (:51) → `RelationshipRemovalService.remove`;
  `names()` (:214-227) is the unordered pair; `heldOutsideTheShard` (:230-237); order of checks: pack, If-Match, pair.
- `ArtifactRemovalService:156-158` drops registered edges whose `from` is removed; `:175` `!outgoing.contains(edge)`
  is pair-equality and also drops a reverse structural edge of another source today. `InboundReferences.referencesOf`
  (:315-319) counts an edge's target as referred to.

## Subtasks

### T013: The wire

1. `type: tension` resolves through `fromToken` (WP01). Keep `from` in the pack, `to` optionally qualified, except for
   a tension the pack owns neither side of: then `from` may be pack-qualified too (operator ruling of 2026-11-19).
   The contract's `RelationshipRegistration.from` and `RelationshipRegistered` widen to a `PackQualifiedReference`
   for that case, with an example.
2. A tension whose two sides have the same bare URN is refused as a self-loop (today's code and pointer), even when
   `to` is qualified.
3. Stored orientation (G1, extended): the owned side is the source; when both sides are in the pack, or neither, and
   `bare(from) > bare(to)`, store `to → from`.
4. The answer (`RelationshipRegistered.relationship`) echoes the request as written; its description says the stored
   orientation may differ for a tension and why. Add the two `.tension.yaml` examples.
5. `RelationshipWireTest`: `tension` accepted; `in_tension_with` → `relationship_type_unknown`; qualified self-tension
   refused; positive twin with two different URNs accepted.

### T014: Merge, created and duplicates

1. `RelationshipMerge.upsert` upserts on the slot (pair and class). Javadoc. New `RelationshipMergeTest`: a tension
   upserted onto a pair with `requires` keeps both; a tension in reverse orientation replaces the existing tension;
   a `suggests` replaces the `requires` and keeps the tension.
2. `created` (201) is per slot: "no edge of this pair class was registered on the pair before". A tension on a
   `requires` pair answers 201 and keeps the `requires`.
3. 422: when another graph document of the pack (not `registered.graph.yaml`) already joins the pair by a tension,
   refuse with `validation_failed` carrying one violation `tension_duplicated` (WP02's code) at that document, and
   write nothing. Find where the registration runs the pack's full read (the strict closure check); if the rewritten
   pack is already re-judged by `PackRulesets.CONTENT`, this falls out of WP02's rule; prove it with a test either way.
4. The governance gate judges a tension on **each side** (`policy.authorize` once per side), not on its source alone,
   so a fully foreign tension is judged by the policies of the artifacts it names. Tests: a tension to a foreign `to`
   whose level forbids relationship writes is refused; a fully foreign tension is gated on both sides; a structural
   relationship keeps today's single check on `from`.

### T015: Guards and keys per class

1. The step-enhancement guards (:218, :226) count only same-class edges: a tension on an enhancer pair never trips
   "would replace its enhances edge". Make the private helper use the kernel's `samePair` plus the class check.
2. `PackRegistrationService.edgeKey`/`edgeWrites`: ordered keys stay correct because storage is canonical per G1;
   add a test that a pack whose shard holds `A requires B` and `A tension B` diffs both as separate writes.

### T016: Removal per pair class

1. Contract (`paths/packs_packId_relationships.yaml`, DELETE): optional query parameter `relation` (`$ref
   ../schemas/Relation.yaml`). Describe: given, removes only the registered edge of that relation on the pair; absent,
   removes every registered edge of the pair. An unknown token is 400 Problem, judged before the pack (rest
   conventions). Checks keep the order pack, If-Match, pair.
2. `relation` given and no registered edge of that relation on the pair → 404 `relationship_not_found`, even when the
   other class exists; its detail names the relation.
3. 409 `relationship_not_registered` is judged per class: `relation=tension` on a pair with an authored `requires` and
   a registered tension removes the tension (204). Absent `relation` with an authored edge of either class on the pair
   and a registered edge → 409 whose detail tells the client to pass `relation`.
4. `RelationshipRemovalService.names` and `heldOutsideTheShard` filter by class. `PackRemovalController` passes the
   parameter. Tests in `RelationshipRemovalServiceTest` for each row of this table:

| Pair holds (R = registered, A = authored) | `relation` | Answer |
| --- | --- | --- |
| R requires, R tension | tension | 204, requires stays |
| R requires, R tension | requires | 204, tension stays |
| R requires, R tension | absent | 204, both go |
| A requires, R tension | tension | 204 |
| A requires, R tension | absent | 409 `relationship_not_registered`, detail names `relation` |
| R requires | tension | 404 `relationship_not_found` |
| any | `bogus` | 400 |

### T017: Artifact removal

1. `ArtifactRemovalService`: removing an artifact drops every registered tension where it is either side (a tension
   is bidirectional); replace `:175`'s `contains` with an identity filter so a reverse structural edge of another
   source is no longer dropped by accident (test the old defect).
2. `InboundReferences.referencesOf`: within the pack, a tension does not block removing one of its sides (the
   registered tension goes with it). A tension **in another pack** that names the artifact, as a source or a target,
   is a reference that blocks the removal, as any cross-pack reference does, so a pack never keeps a tension to a
   removed artifact (ruling of 2026-11-19). Count a qualified `fromReference` too. Tests: removing `B` of an in-pack
   `A tension B` succeeds and drops it; removing `B` while another pack holds `base::A tension base::B` is refused;
   removing `B` of `A requires B` is still refused (positive control).

### T018: Scenarios

`relationships.feature` and `pack-removal.feature` (the switch gate keeps `putPackRelationship` and
`deletePackRelationship` ready, so every new behaviour is asserted):
- register a tension on a pair with `requires`: 201, both in `registered.graph.yaml`; register again: 200;
- register two owned sides larger first: the stored shard has the smaller as `source` (read the file); the answer
  echoes the request;
- `in_tension_with`: 422 `relationship_type_unknown`;
- a pair already joined by a tension in a hand-written shard: 422 with `tension_duplicated`, nothing written;
- a later registration of another relationship keeps the tension in the shard;
- the removal table rows 1, 2, 5, 6, 7.

## Definition of Done

`./gradlew :packs:test --max-workers=1` and the relationships and pack-removal scenarios pass; `verifyAll` passes.

## Risks and reviewer guidance

- The 201/200 rule changes for every relation (per slot instead of per pair): check existing scenarios that register
  a second relation on a pair still assert what the operator ruled (a structural relation still replaces another).
- Reviewers: walk the removal table against the code, row by row.
