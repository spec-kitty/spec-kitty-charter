---
work_package_id: WP02
title: The glossary kind replaces glossary_pack (atomic)
dependencies:
- WP01
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-020
- NFR-002
- C-001
- C-003
- SC-003
- NFR-004
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T005
- T006
- T007
- T008
- T009
- T010
- T011
- T012
- T013
- T014
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- contracts/packs/schemas/glossary.schema.json
- contracts/charter/changes/2026-11-14-glossary-replaces-glossary-pack.md
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryContext.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GlossaryContextIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TermIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TermStatus.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AppliesTo.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GlossaryYaml.java
- service/packs/src/testFixtures/resources/example-packs/built-in/glossaries/spec-kitty-core.glossary.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/glossary.graph.yaml
execution_mode: code_change
owned_files:
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- contracts/packs/schemas/glossary-pack.schema.json
- contracts/packs/schemas/glossary.schema.json
- contracts/packs/schemas/graph-shard.schema.json
- contracts/packs/schemas/org-fragment.schema.json
- contracts/packs/schemas/pack-manifest.schema.json
- contracts/packs/schemas/preset.schema.json
- contracts/packs/schemas/charter.schema.json
- contracts/packs/schemas/org-charter.schema.json
- contracts/packs/schemas/policy.schema.json
- contracts/packs/schemas/mission-step-contract.schema.json
- contracts/charter/schemas/NodeKind.yaml
- contracts/charter/schemas/ActivatableKind.yaml
- contracts/charter/schemas/CascadeKind.yaml
- contracts/charter/schemas/SelectedArtifacts.yaml
- contracts/charter/schemas/DeactivatedArtifacts.yaml
- contracts/charter/schemas/Preset.yaml
- contracts/charter/paths/activations_activationId_presets_presetId.yaml
- contracts/charter/scenarios/effective-reads.feature
- contracts/charter/changes/2026-11-14-glossary-replaces-glossary-pack.md
- contracts/offering/schemas/ActivationConfig.schema.json
- contracts/offering/corpus/ActivationConfig/valid/06-every-selected-kind.json
- contracts/offering/corpus/ActivationConfig/valid/07-every-deactivated-kind.json
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relation.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/ArtifactKindTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Glossary*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TermIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TermStatus.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AppliesTo.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Glossary*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/Glossary*Test.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/Glossary*Test.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProjectCharterYamlSchemaTest.java
- service/packs/src/testFixtures/resources/example-packs/built-in/**
- service/packs/src/test/resources/digest-baseline/**
- service/packs/src/test/resources/pack-level/presets-pack/**
- scripts/usability/charter-to-activation.py
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - The glossary kind replaces glossary_pack (atomic)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md` (FR-001 to FR-006, the edge cases)
- `plan.md`, IC-02 and IC-03
- `research.md`, R1, R2, R4, R9, R10, R11, R14 and R15
- `data-model.md`
- `occurrence_map.yaml`

Then read [ADR-0049](../../../docs/adr/0049-glossary-contexts-and-terms.md), decisions 1 to 6, and
[ADR-0053](../../../docs/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions.md), decisions 11 to 14.

## Objective

Replace the kind `glossary_pack` outright with `glossary` in ADR-0049's shape, in one change that compiles and passes
`verifyAll`. Nothing reads the old shape afterwards: no alias, no fallback and no upgrade (charter, "the charter's model
is canonical"; C-001).

This WP is large on purpose. The kernel enum, the generated DTOs, the domain, storage and fixtures do not compile or
pass apart (squad, Java lens H4). Keep its commits small and ordered inside the WP: one commit per subtask where the
tree compiles, otherwise squash the non-compiling steps together.

## Context: what exists

| Area | Files |
| --- | --- |
| Kernel | `ArtifactKind.GLOSSARY_PACK("glossary_pack", "glossary_packs", true)`; `NodeKind.GLOSSARY_PACK` (made explicit by WP01); `Relation.VOCABULARY` and `RelationType.VOCABULARY` |
| Domain | `GlossaryPack.java` `{id, packId, provenance, description?, terms}`, at least one term, unique `surface`; `GlossaryTerm.java` (core's shape, keyed by `surface`); `GlossaryPackIdentifier.java` (any non-blank text) |
| Storage | `GlossaryPackBinding` (token `glossary-pack`, schema `glossary-pack`), `GlossaryPackDso`, `GlossaryPackYaml`, `KindBindings.java:27`, `YamlFormats.java:19`; `PresetYaml` derives `activated_<plural>` from `ArtifactKind.plural()`; `PresetMapper.java:102`, `case GLOSSARY_PACK -> setActivatedGlossaryPacks` |
| Packs contract | `glossary-pack.schema.json`; `file-kinds.yaml` (`node_kinds`, `artifact_kinds`, `fragment_kinds` `glossary_packs: glossary_pack`, the `**/*.glossary-pack.yaml` rule); `graph-shard.schema.json` (URN alternations :12, :17, :67; node kind enum :81; per-kind branch :286-296); `org-fragment`, `pack-manifest`, `preset` (`activated_glossary_packs`), `charter` (`selected_glossary_packs`, `activated_glossary_packs`), `org-charter` (`required_glossary_packs`), `policy` (:53, :88), `mission-step-contract` (:18) |
| Charter contract | `NodeKind.yaml`, `ActivatableKind.yaml`, `CascadeKind.yaml`, `SelectedArtifacts.yaml`, `DeactivatedArtifacts.yaml`, `Preset.yaml` (`activatedGlossaryPacks`); the 400 detail at `effective-reads.feature:~3297` lists every activatable token; the prose of `paths/activations_activationId_presets_presetId.yaml` |
| Offering contract | `ActivationConfig.schema.json` (:129, :283); corpus `valid/06`, `valid/07` |
| Fixtures | `example-packs/built-in/glossary_packs/spec-kitty-core.glossary-pack.yaml` (139 terms), `glossary_pack.graph.yaml`, `pack-manifest.yaml`, `policy.yaml`, `presets/default.yaml`; `digest-baseline/fixture/glossary_packs/eta.glossary-pack.yaml`; `pack-level/presets-pack/presets/governing.yaml` |

Leave these alone (occurrence map):

- `packs/**`, `kitty-specs/**`, `kitty-ops/**`, `docs/changelog/**` and `.kittify/**`;
- `site/.contextive/**`;
- earlier `contracts/charter/changes/**` fragments and earlier `CHANGELOG.md` entries;
- the planted `r15-glossary-urn-*` cases, which refuse core's `glossary:` URN and keep doing so.

## Subtasks

### T005: The packs contract

- **`contracts/packs/schemas/glossary.schema.json`** replaces `glossary-pack.schema.json` (delete the old file). It
  has the closed shape of `data-model.md`:
  - `schema_version` const `"1.0"`;
  - `id` with pattern `^(?!.*\n)[a-z][a-z0-9]*(-[a-z0-9]+)*$` and `maxLength` 64, exactly as `skill.schema.json`;
  - `name` and `description`, non-blank;
  - `tags`: `minItems` 1, `uniqueItems`;
  - `applies_to`: `{paths, languages, stacks}`, each with `minItems` 1;
  - `terms`: `minItems` 1.
  - A term is closed: `id` (the same pattern), `name`, `definition`, `examples`, `aliases`, `avoid`, `status`
    (enum `draft`, `active`, `deprecated`) and `see_also` (items of pattern
    `^term:[a-z][a-z0-9]*(-[a-z0-9]+)*/[a-z][a-z0-9]*(-[a-z0-9]+)*$`).
  - Uniqueness of term ids is a domain rule; JSON Schema cannot say it.
- **`file-kinds.yaml`**:
  - `node_kinds`: `glossary_pack` becomes `glossary_context`, and `term` is added at the position `NodeKind`'s
    constant order gives (decide the order once, and use it in `NodeKind`, `file-kinds.yaml` and `NodeKind.yaml`).
  - `artifact_kinds`: `glossary`.
  - `fragment_kinds`: `glossaries: glossary_context`.
  - The file rule is `**/*.glossary.yaml` → schema `glossary`.
  - Update the header comment where it says `node_kinds` is the artifact kinds plus `action`.
- **`graph-shard.schema.json`**:
  - The node URN alternation and the `DRGNode` `kind` enum gain `glossary_context` (declarable).
  - The edge source and target alternations gain `glossary_context` and `term` (edge targets).
  - The per-kind branch becomes `const glossary_context` with `^glossary_context:`.
  - `term` gets no node branch.
- **The other packs schemas**: rename the token and plural.
  - `preset.schema.json`: `activated_glossaries`.
  - `charter.schema.json`: `selected_glossaries`, `activated_glossaries`.
  - `org-charter.schema.json`: `required_glossaries`.
  - `org-fragment.schema.json`: URN patterns `glossary_context`, fragment plural `glossaries`.
  - `pack-manifest`, `policy` and `mission-step-contract`: the reference `type` enums use `glossary`, the artifact
    kind.
- **`contracts/packs/README.md`**: rename the mention.
- **Coordination**: if PR #568 (#559) has landed `contracts/packs/CHANGELOG.md`, add a `### Breaking` entry there too.
  Ask the orchestrator if unsure.

### T006: Kernel

- `ArtifactKind.GLOSSARY("glossary", "glossaries", true)` replaces `GLOSSARY_PACK`.
- `NodeKind`, using the WP01 table:
  - `GLOSSARY_CONTEXT("glossary_context", ArtifactKind.GLOSSARY, "glossaries", declarable true, edgeTarget true)`
    replaces `GLOSSARY_PACK`;
  - add `TERM("term", null, null, declarable false, edgeTarget true)`.
- `Relation.VOCABULARY`: if targets are declared anywhere in the kernel or in `PackPolicy`'s relation endpoint table,
  they become `glossary_context` and `term`. If ADR-0064 decision 13's typed endpoints have landed (slice F), follow
  their mechanism.
- Javadoc says what each constant stands for, and why the URN token differs from the kind token (ADR-0053 d13).
- Fix every exhaustive `switch` the compiler flags. Never add a `default` branch only to silence it.
- `ArtifactKindTest` and the WP01 `NodeKindTest` rows are updated with literal values.

### T007: Domain

- **`GlossaryContext`** (replaces `GlossaryPack`): a `CharterArtifact` of kind `GLOSSARY`.
  - Fields: `id` (`GlossaryContextIdentifier`), `packId`, `name`, `description`, `tags`, `appliesTo` (`AppliesTo`) and
    `terms` (`List<GlossaryTerm>`, unmodifiable, in authored order).
  - Rules, each refused with the existing invalid-content exception and a message naming the field: at least one
    term; unique term ids; non-blank `name` and `description`; non-empty `tags` when present.
  - `label()` returns `name`, `urn()` returns `glossary_context:<id>`, and `term(TermIdentifier)` returns
    `Optional<GlossaryTerm>`.
  - Use a builder as `GlossaryPack` does today.
- **`GlossaryTerm`** (reshaped): `id` (`TermIdentifier`), `name`, `definition`, `examples`, `aliases`, `avoid`,
  `status` (`TermStatus`) and `seeAlso` (`List<Urn>`, each of kind `TERM`). `urn(GlossaryContextIdentifier)` returns
  `term:<ctx>/<id>`.
- **`GlossaryContextIdentifier`** and **`TermIdentifier`**: records over `String`, checked with
  `IdentifierGrammar.isLetterFirstSlug` (research R9; ruling `DM-01M7B1QF8FQ38JNYVARN95AQ9F`). Copy the
  `SkillIdentifier` pattern exactly.
- **`TermStatus`**: an enum of `DRAFT`, `ACTIVE` and `DEPRECATED`, with `token()` and `fromToken()`.
- **`AppliesTo`**: `paths`, `languages` and `stacks`, each an unmodifiable `List<String>`; an absent value is empty.
- Delete `GlossaryPack`, `GlossaryPackIdentifier` and the old `GlossaryTerm` shape: `confidence`,
  `introducedInMission`, `synonymsToAvoid` and `bannedSynonyms` are gone (ADR-0049 d5).
- Tests: `GlossaryContextTest` and `GlossaryTermTest` cover every rule with a refusal case and its positive control
  on the same fixture (the non-vacuity tactic).

### T008: Storage and presets

- `GlossaryBinding` (replaces `GlossaryPackBinding`): `kind()` `GLOSSARY`, `fileToken()` and `schemaName()`
  `glossary`. The layout is `glossaries/<id>.glossary.yaml`, the id equals the file stem, and a new file is created at
  that path.
  - Read how `SkillBinding` and `AssetBinding` declare `idIsFileStem` / `placedAtItsFileName`, and do the same. The
    rule is ADR-0049 decision 2: "id: the file stem".
- `GlossaryDso` mirrors the YAML, snake case. `GlossaryYaml` encodes and decodes, keeping key order:
  `schema_version, id, name, description, tags, applies_to, terms`, and per term
  `id, name, definition, examples, aliases, avoid, status, see_also`.
- `KindBindings` and `YamlFormats` register the new binding.
- `PresetMapper`: `case GLOSSARY -> setActivatedGlossaries`. The generated setter comes from T009. `PresetYaml`
  needs no change: it derives `activated_glossaries` from the plural. Verify it with a test.
- **Old shape**: a file `glossary_packs/x.glossary-pack.yaml` is no longer a known file kind.
  - Check what the loader does with an unknown file under the pack root. If it is silently unclassified (see the
    `file-kinds.yaml` header: "a file that matches nothing is left unclassified, not refused"), the old shape would
    be *ignored*, not refused, which breaks FR-005 and story 1 scenario 2.
  - Make it refused: the smallest correct rule is an `ignored`-style entry in `file-kinds.yaml` that maps
    `**/*.glossary-pack.yaml` to a refusal with a named reason ("the glossary_pack shape is replaced by
    glossaries/<id>.glossary.yaml, ADR-0049").
  - If epic #549 has already landed `file_not_held` at `WARNING` (ADR-0075), coordinate through the orchestrator
    first, because a warning is not a refusal.
  - WP03 writes the planted refusal case; this subtask makes the behaviour.

### T009: Charter and offering contracts

- `NodeKind.yaml`: `glossary_pack` becomes `glossary_context` and `term`, in `NodeKind` order. The contract enum is
  the wire's set of URN kinds (WP01 T002).
- `ActivatableKind.yaml` and `CascadeKind.yaml`: `glossary`.
- `SelectedArtifacts.yaml` and `DeactivatedArtifacts.yaml`: the property `glossary` (context ids; items with the id
  pattern).
- `Preset.yaml`: `activatedGlossaries`.
- `ActivationConfig.schema.json` (:129, :283) and the corpus `valid/06`, `valid/07`: `glossary`. `schemaVersion` stays
  2 (research R2).
- `effective-reads.feature`: the 400 detail that lists the activatable tokens.
- The prose of `paths/activations_activationId_presets_presetId.yaml`.
- `ArtifactKindToken` and the `Artifact` `oneOf` are **not** changed here. WP04 adds `glossary` to them with the
  pack reads, because `ArtifactKindToken` is pinned to `ArtifactReadService.LISTED`.
- Regenerate, then compile. Every generated-DTO use in `:activations`, `:packs` and the edges follows.

### T010: Fixtures

- `example-packs/built-in`:
  - `glossary_packs/spec-kitty-core.glossary-pack.yaml` → `glossaries/spec-kitty-core.glossary.yaml`, by a one-off
    script in your scratch space (not committed). The script:
    - adds `schema_version: "1.0"` and `name: Spec Kitty core`;
    - keeps `description`, and drops `provenance`, `confidence` and `introduced_in_mission`;
    - per term: `surface` → `name`, and `id` = the surface lower-cased, with each run of non-`[a-z0-9]` characters
      replaced by `-`, then trimmed (research R4). All 139 ids are distinct and valid, but assert that in the script;
    - `synonyms_to_avoid` + `banned_synonyms` → `avoid` (deduplicated, order kept);
    - `see_also` surfaces → `term:spec-kitty-core/<id>`;
    - `status` and `definition` are kept;
    - the YAML is emitted in the codec's key order.
  - Remove the "GENERATED (migrated) … Do not hand-edit" header; the file is canonical content now. Replace it with a
    one-line comment naming ADR-0049.
  - `glossary_pack.graph.yaml` → `glossary.graph.yaml`, with node `glossary_context:spec-kitty-core`, kind
    `glossary_context`. Check how the fixture names its shard files. If shards are named per kind token, the file is
    `glossary.graph.yaml`.
  - `pack-manifest.yaml` (the file list), `policy.yaml` and `presets/default.yaml` (`activated_glossaries`).
- `digest-baseline/fixture/glossary_packs/eta.glossary-pack.yaml` → `glossaries/eta.glossary.yaml`, same rule.
- `pack-level/presets-pack/presets/governing.yaml`: `activated_glossaries`.
- **Re-recorded pins**: `PackDigestFixtureTest`, `PackFileSetExactnessTest`, `PackManifestRoundTripTest` and any
  golden digest. Re-record each from the new files. The commit message names each pin, its old and new value, and
  "fixture moved to the glossary shape (ADR-0049)".
- Keep the surface-to-id map the script printed. Paste it into the WP's review notes. WP08 writes it to
  `research/term-id-map.md`.

### T011: Tests

- **Update every test** that names the old kind, using the canonical shape only. The list (from `git grep`):
  - `ActivationPresetServiceTest`, `PresetRulesTest` (activations);
  - `ActionContextWalkOwnershipTest`, `ActionContextWalkTest`, `ClosureViewGovernanceTest`, `GovernanceWalkTest`,
    `InboundReferencesTest`, `InForceResolutionTest`, `PresetTest`;
  - `GeneratedPackTree`, `GlossaryPackAndSkillCorpusTest` and `GlossaryPackAndSkillSchemaTest` (rename them to
    `GlossaryAndSkill…`), `GlossaryPackYamlTest` → `GlossaryYamlTest`;
  - `PackDigestFixtureTest`, `PackFileKindTest`, `PackFileSetExactnessTest`, `PackLevelSchemasTest`,
    `PackManifestRoundTripTest`, `PresetRoundTripTest`, `PresetYamlTest`, `VocabularyEqualityTest`;
  - `planted/core-verdicts.json` (only rows that are *this* kind, not the r15 `glossary:` rows);
  - `EffectiveProfileReadTest`, `PacksProfileSourceTest` (profiles-edge);
  - `WireTokenParityTest`: its "unknown future kind" example uses `glossary`, so pick a token no kind uses
    (squad, Java lens).
  - Record each test file outside `owned_files` as an out-of-map edit: one line per file in the commit message.
- **`ProjectCharterYamlSchemaTest`** (ruling `DM-01M7CEZ19K8QRA3KX68KRWX22G`; research R14):
  - It stops validating `.kittify/charter/charter.yaml`, which is core's file; do not edit that file.
  - Move whatever `charter.schema.json` coverage it gave to a canonical example charter file under
    `service/packs/src/test/resources/`. It carries `selected_glossaries` and `activated_glossaries`.
  - Keep the Gradle wiring (`service/packs/build.gradle.kts:215`) pointing at the new resource. That is an out-of-map
    edit; record it.

### T012: The usability script

In `scripts/usability/charter-to-activation.py:53`, the left side is core's charter key (`activated_glossary_packs`)
and stays. The right side becomes the activation kind `glossary`. Add a one-line comment saying so.

### T013: The change fragment

Write `contracts/charter/changes/2026-11-14-glossary-replaces-glossary-pack.md` with `### Breaking` (ADR-0022). Copy
the shape of `changes/2026-11-07-a0-one-kind-model.md`. It names:

- every renamed token and member: `ActivatableKind`, `CascadeKind`, `SelectedArtifacts`, `DeactivatedArtifacts`,
  `ActivationConfig`, `Preset.activatedGlossaries`, `NodeKind` (`glossary_context`, `term`) and the packs schemas and
  file family;
- that a write sending `selected.glossary_pack` is `422 validation_failed` (`unknown_member`);
- that a stored activation holding it is refused, with no upgrade (ruling `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT`);
- what a generated-client caller changes;
- that open-packs content follows (draft issue `open-packs#TBD-glossary-contexts`).

Run the changelog-fragment check.

### T014: The NFR-002 sweep

```bash
git grep -n -E 'glossary_pack|glossary-pack|glossary_packs|GlossaryPack' -- contracts service scripts
```

Every remaining hit must be in a path the occurrence map classifies as an exception: refusal tests, CHANGELOG and
change entries, the r15 planted cases, and the left side of the usability mapping. Paste the filtered output into the
review notes.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check
./gradlew --max-workers=1 :kernel:test :packs:test :activations:test :profiles-edge:test :artifacts-edge:test :missions-edge:test
./gradlew --max-workers=1 :packs:wholePackRoundTrip
./gradlew --max-workers=1 verifyAll
python3 scripts/docs/check_docs.py all
```

Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- `verifyAll` is green.
- No test was removed, skipped or loosened, except the narrowed `ProjectCharterYamlSchemaTest`, which follows the
  operator ruling, with its coverage moved.
- The NFR-002 sweep shows only occurrence-map exceptions.
- The example pack holds 139 terms with minted ids, and round-trips.
- A pack in the old shape is refused, not ignored (the planted case lands in WP03; prove it here with a unit test).
- The change fragment is written.
- New code meets the complexity gate and the packs and activations mutation floors (85; research R15).

## Risks and reviewer guidance

- **Reviewer**: diff the contract changes against the occurrence map, path by path.
- **Reviewer**: check that each digest re-record is named in its commit.
- **Reviewer**: check that no `default ->` branch was added to silence a switch.
- **Slice F (#537)** and **PR #568 (#559)** touch the same surfaces. Rebase onto `main` first. Tell the orchestrator
  before starting.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP02 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase only. Force-push only your own rebased branch, with a lease.
