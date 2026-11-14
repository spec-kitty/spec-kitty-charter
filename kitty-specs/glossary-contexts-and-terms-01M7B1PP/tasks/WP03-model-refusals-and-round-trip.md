---
work_package_id: WP03
title: Model refusals and round-trip proofs
dependencies:
- WP02
requirement_refs:
- FR-003
- FR-005
- C-001
- NFR-001
- SC-003
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T015
- T016
- T017
- T018
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/resources/planted/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPlantedRefusalsTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationGlossaryPackRefusalTest.java
- contracts/offering/corpus/ActivationConfig/invalid/19-glossary-pack-member.json
- contracts/charter/scenarios/activation-glossary-pack-refused.feature
execution_mode: code_change
owned_files:
- service/packs/src/test/resources/planted/glossary-*/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPlantedRefusalsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/IdentifierBoundsTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationGlossaryPackRefusalTest.java
- contracts/offering/corpus/ActivationConfig/invalid/19-glossary-pack-member.json
- contracts/charter/scenarios/activation-glossary-pack-refused.feature
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 - Model refusals and round-trip proofs

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`: story 1 and the edge cases
- `research.md`: R2, R9 and R10
- `data-model.md`

Also read WP02's prompt, and the review notes on WP02.

## Objective

Prove, with refusal cases and their positive controls, that:

- the new model's invariants hold;
- the old shape is refused, not ignored (FR-003, FR-005, C-001);
- the whole-pack round trip covers the new file kind (#547).

The old shape appears **only** here, in error-handling tests (charter: tests carry the canonical shape except
refusal tests).

## Context

- **Planted cases**: `service/packs/src/test/resources/planted/<case>/files/**` holds a pack, and
  `planted/<case>/expected.json` holds `{covers, configuredLevel, findings: [{code, severity, file, place}], load}`.
  - See `planted/asset-id-not-kebab/` and its test, `AssetIdNotKebabTest`.
  - Find how the planted corpus is iterated (`WholePackRoundTripTest` and others), and whether a new case is picked up
    automatically or has to be registered.
- **`core-verdicts.json`** records core's CLI verdict per plant. A new plant either gets a row (if the harness needs
  one) or is marked charter-only. Follow how `asset-id-not-kebab` is handled.
- **Stored activations**: `ActivationFileCodec.kind()` (:148-150) already throws for an unknown token, for version 1
  and version 2 documents (squad, Java lens). Only a test is needed.
- The write-side 422 comes from the closed generated DTO.

## Subtasks

### T015: Planted refusals

Add these planted cases, each with `expected.json` naming the code, severity, file and place. Use the exact codes the
loader raises; run the case first to read the code, and do not guess it.

| Case | Content | Expected |
| --- | --- | --- |
| `glossary-pack-old-shape` | `glossary_packs/x.glossary-pack.yaml` in core's old shape | refused at load (WP02 T008's rule), naming the file |
| `glossary-context-id-not-kebab` | context `id: Core` | `artifact_schema_error` at `id` |
| `glossary-term-id-double-hyphen` | a term `id: a--b` | refused at `terms[0].id` |
| `glossary-term-id-digit-first` | a term `id: 4-eyes` | refused |
| `glossary-term-id-duplicate` | two terms with `id: lane` | refused by the domain rule, naming `lane` |
| `glossary-see-also-not-a-urn` | `see_also: [lane]` | refused at `see_also[0]` |
| `glossary-context-id-not-the-stem` | file `glossaries/a.glossary.yaml` with `id: b` | refused (the id is the file stem) |

- **Positive control**: one passing case, `glossary-context-valid`, with the same pack minus the fault, loads. A
  do-nothing implementation must fail at least one row.
- `GlossaryPlantedRefusalsTest` runs these if the corpus is not iterated automatically. Otherwise it is not needed:
  remove it from `owned_files` in the review notes.

### T016: Identifier bounds

`IdentifierBoundsTest` gains one row for `GlossaryContextIdentifier` and one for `TermIdentifier` (ruling: this
Mission owns its rows). Rows: the 64-character limit (64 accepted, 65 refused), `a--b`, digit-first, uppercase, a
trailing hyphen, empty and blank. Follow the existing row shape exactly. #552 may land rows of its own, so rebase and
keep both.

### T017: Stored activation and write refusals

- `ActivationGlossaryPackRefusalTest` (activations, storage):
  - a stored v2 document holding `selected.glossary_pack: [x]` is refused with `ActivationStorageException`, naming the
    id;
  - the same for `deactivated.glossary_pack`;
  - positive control: the same document with `selected.glossary: [x]` loads.
- `contracts/offering/corpus/ActivationConfig/invalid/19-glossary-pack-member.json` (check the next free number) is
  refused by the schema. The corpus test picks it up.
- `contracts/charter/scenarios/activation-glossary-pack-refused.feature` (`@contract`):
  - a `createActivation` body with `selected.glossary_pack` is `422 validation_failed`, with an `unknown_member`
    violation at `/selected/glossary_pack`;
  - beside it, the same body with `selected.glossary` is accepted.
  - Check how `activation-kind-model.feature` words the A0 removed-member scenarios, and match its steps.

### T018: Round trip and references

- **Round trip**: confirm that `:packs:wholePackRoundTrip` and `WholePackRoundTripTest` classify
  `glossaries/*.glossary.yaml` as a modelled file kind and compare it.
  - Add an assertion, or a test row, that fails if the glossary file is reported as unmodelled or skipped (#547's
    failure mode).
  - Use the example pack `built-in`.
- **Broken references**: a `see_also` URN, and a `vocabulary` edge target `term:<ctx>/<term>`, that no held context
  resolves are each reported as a broken reference, as other unresolved references are (data model).
  - Find the existing broken-reference check (`InboundReferences`, the closure walk) and add one test per case, with
    the resolving case as its positive control.
  - If the existing check cannot see `term:` targets, extend it in the class that owns reference resolution, and record
    that as an out-of-map edit.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :packs:test :activations:test :contracts:check
./gradlew --max-workers=1 :packs:wholePackRoundTrip
./gradlew --max-workers=1 :deployable:test
```

## Definition of Done

- Every planted case fails or loads exactly as its `expected.json` says.
- The positive controls load.
- The identifier rows pass.
- The stored and write refusals are pinned.
- The round trip demonstrably covers the glossary file.

## Risks and reviewer guidance

- **Reviewer**: check each refusal has a positive control on the same fixture.
- **Reviewer**: check that no refusal code was invented: each must exist in `RefusalCode` or the loader's finding
  codes.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP03 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
