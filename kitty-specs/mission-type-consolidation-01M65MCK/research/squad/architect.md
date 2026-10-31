# #194 PR-B: the mission tier as `mission_type` + `mission_step`, a responsibility map

Profile: architect-alphonso (design, evaluate, decide; no implementation code). Read-only research.
Repo `spec-kitty-charter` at `feeff0a`; open packs at `5df92a5d` (`gradle.properties:9`); core at `/opt/spec-kitty` (reference only).

---

## 0. Ground truth: the mission tier as pinned (open-packs `5df92a5d`)

| Pack | Files (by shape) |
|---|---|
| built-in | `missions/mission_types/*.yaml` (4, 4 lines each: `schema_version: 1`, `id`, `display_name`, `requires_substantive_spec`); `missions/<t>/mission.yaml` (4, about 100 lines: workflow phases, artifacts, paths, validation, mcp_tools, agent_context, task_metadata, commands); `missions/<t>/mission-runtime.yaml` (4); `missions/<t>/governance-profile.yaml` (4, all `selected_*: []`); `missions/<t>/expected-artifacts.yaml` (4); `missions/<t>/actions/<a>/index.yaml` (24: per-kind doctrine id lists); `missions/mission-steps/<t>/<s>/step.yaml` (30), `prompt.md` (30), `guidelines.md` (17); `missions/<t>/templates/**` (33); `missions/built_in_step_contracts/*.step-contract.yaml` (21, already bound) |
| qa | `mission_types/qa.yaml`, `mission_types/qa/governance-profile.yaml`, `mission-steps/qa/*/step.yaml` (7, **no prompt.md** although each names `prompt_template: prompt.md`), `missions/qa/{mission,mission-runtime,expected-artifacts}.yaml`, `missions/qa/command-templates/*.md` (7), `missions/qa/templates/**` (12), `mission_step_contracts/*.step-contract.yaml` (7) |
| cave-kitty | `mission_types/cave-dev.yaml`, `mission_types/cave-dev/governance-profile.yaml`, `missions/cave-dev/{mission,mission-runtime,expected-artifacts}.yaml`, `command-templates/*.md` (8) |

Inconsistencies a consolidation has to resolve (each is a ruling, not a reader rule; ADR-0036 d5):
- **Three layouts.** built-in nests under `missions/`; qa and cave-kitty use pack-root `mission_types/` and `mission-steps/`. The governance profile sits at `missions/<t>/` in built-in and at `mission_types/<t>/` elsewhere.
- **Three step lists that disagree.** For software-dev, `mission-runtime.yaml` has `discovery … accept`, `mission-steps/` has `research, charter, tasks-outline/-packages/-finalize, analyze …`, and `actions/` has `retrospect`, which has no step. For research, the runtime has `accept`, which has no step.
- **Sequence stated three times:** runtime order plus `depends_on`, the step's `sequence_index` + `in_action_sequence`, and the `requires` edges `mission_type → action` in `mission_type.graph.yaml`. Core also has `MissionType.action_sequence` (`/opt/spec-kitty/src/charter/offering/missions/models.py:248-288`).
- **Doctrine scope stated twice:** `actions/<a>/index.yaml` lists against the `scope` edges `action:<t>/<a> → …` in `action.graph.yaml` (163 scope edges), and the empty `governance-profile.yaml` lists against the `scope` edges `mission_type:<t> → …` in `mission_type.graph.yaml` (38).
- **Steps name profiles.** `agent_profile:` is null in built-in, and qa pins one per step (`qa/mission-steps/qa/implement/step.yaml`: `agent_profile: qa-test-executor-elena`). ADR-0032 d1 forbids this.
- **Fragments point into retired files.** `qa/drg/fragment.yaml:96-98` and `cave-kitty/drg/fragment.yaml:16-18` have a `mission_types` node whose `body_path` is `…/governance-profile.yaml`.

Core's shapes, for reference: `MissionStep` (`models.py:87-146`: `id, display_name, step_type, prompt_template, agent_profile, guidance, delegates_to, depends_on, sequence_index, in_action_sequence, recommended_model_tier, template{artifact_key, template_file}`), `MissionType` (`models.py:229-307`), `ActionIndex` (`action_index.py:23-34`), `MissionTypeProfile` (`charter/activation/mission_type_profiles.py:124-166`).

---

## 1. Responsibility map: what a new bound kind touches (from the procedure and toolguide-guide precedent)

PR-A precedent commits: `61469e6` (schema + entity + binding + DSO + YAML + fixtures), `812a755` (Markdown companion + files rule + `GuideMarkdown`), `e6fe0f8` (companion content form, store/gate pairing), `73b7abb` (id grammar, stem rule, validate mode for companions).

### 1a. Contracts (`contracts/packs/`)
| Path | Responsibility | PR-B change |
|---|---|---|
| `contracts/packs/schemas/<name>.schema.json` | Closed Draft 2020-12 schema per stored kind. Canonical here (ADR-0015 d1), edited in place (R20). | New `mission-type.schema.json` and `mission-step.schema.json`. Keep the kebab names: `FileSystemPackInspector.artifactKinds()` maps a schema to a token by `-`→`_` (`FileSystemPackInspector.java:105`; ADR-0037 amendment #191, lines 240-242), so `mission-type`→`mission_type` and `mission-step`→`mission_step` line up with no hook. |
| `contracts/packs/schemas/mission-step-prompt.schema.json`, `mission-step-guidelines.schema.json` (new) | Structure schemas for Markdown companions, like `toolguide-guide.schema.json` (`812a755`). | Only if the companions are `markdown` (see §5). |
| `contracts/packs/file-kinds.yaml` | The single vocabulary authority: `node_kinds` (l.51), `artifact_kinds` (l.68), `fragment_kinds` (l.83), `files` (l.100), `exclusions` (l.147), `payloads` (l.197). | Add the new tokens to all three kind lists. Add `files` rules `**/*.mission-type.yaml`→`mission-type` and `**/*.mission-step.yaml`→`mission-step`, plus Markdown companion rules (`content_form: markdown`). Re-reason or narrow the exclusions at l.148-173: issue decision 5 lets #194 remove its own. |
| `contracts/packs/schemas/graph-shard.schema.json` | `DRGNode.kind` enum, the `urn`/`source`/`target` alternations and one `if/then` URN-prefix branch per node kind. | Add `mission_step` if it becomes a node kind. `mission_type` is already there. |
| `contracts/packs/schemas/org-fragment.schema.json` | `_OrgDRGNode.kind` plural enum. | Add `mission_steps`. **Hazard:** core's canonical plural `mission_steps` means *step contracts* (DIV-011, `docs/conformance/divergence-register.md:247-256`), so this changes what DIV-011 says. |
| `contracts/packs/schemas/{directive,paradigm,mission-step-contract}.schema.json` `$defs.ArtifactKind` | A local copy of the artifact-kind enum, used by typed references. | Needs a decision: either extend each copy (consistent) or leave them as they are (a reference to a mission type is not a doctrine reference). The S14 reference `type` enum of the new schemas has to name the kinds a step or type may reference. |
| `contracts/packs/schemas/preset.schema.json:51-75,157` | `activated_kinds` (no `mission_types` entry) and `activated_mission_types` (already present, ADR-0042). | Nothing needs to change. `Preset.ACTIVATION_KINDS` (`Preset.java:48-57`) keeps mission types in their own slot. |
| `contracts/packs/README.md` | States the schema count ("18 in all"). | Update the count. |

### 1b. Kernel (`service/kernel/.../kernel/domain/`)
| Class | Responsibility | PR-B change |
|---|---|---|
| `ArtifactKind.java:10-36` | The artifact kinds and their facts (activatable, orgRequirable, selectionOverlayable, inForceWhenAbsent), which mirror core and are checked by the conformance corpus. | Needs `MISSION_TYPE` and `MISSION_STEP`, because every binding, `CharterArtifact.kind()` and the `Pack` map are keyed by `ArtifactKind` (see §5, decision K). Core refuses mission types as an artifact kind (`MissionTypeNotAnArtifactKind`), so this needs a divergence entry. Issue #148 is the target. |
| `NodeKind.java:6-91` | The DRG node kinds: artifact kinds plus `ACTION`, `MISSION_TYPE`. `of(ArtifactKind)` is `valueOf(name)` (l.73-75); `fromPlural` special-cases `mission_types` and `actions` (l.84-90). | `MISSION_TYPE` moves from the special case to an ArtifactKind-backed constant. Add `MISSION_STEP`. Keep `ACTION`, since the shards still use it. |
| `Urn.java:43-61` | `Urn.action(type, action)` gives `action:<t>/<a>`; `Urn.missionType(t)`; the id may contain `/`. | Possibly `Urn.missionStep(t, s)`, giving `mission_step:<t>/<s>`. |
| `CharterArtifact.java` | `kind()`, `localId()`, `urn() = new Urn(NodeKind.of(kind()), localId())`. | The new entities implement it (ADR-0035 d4: "Every artifact kind implements it, mission types … included"). |
| `RefusalCode.java:9-10,85` | `MISSION_TYPE_NOT_FOUND`, `ACTION_NOT_FOUND` (404, `raised: false`). | No change. |

### 1c. Domain (`packs.domain` or a new `missions` slice, see §5 decision S)
| Class (new) | Responsibility | Precedent |
|---|---|---|
| `MissionType`, `MissionTypeIdentifier` | The entity: id = file stem, title, description, action sequence, path conventions, recipe ingredients (references kept, never minted). `relationships()` is empty, as for `Procedure` (ADR-0043 d2). | `Procedure.java`, `ProcedureIdentifier.java` |
| `MissionStep`, `MissionStepIdentifier` | The entity: owning type, step id, title, description, role, depends_on, prompt and guidelines companions, references, outputs. | `Procedure`, `Toolguide` (companion value `Guide`) |
| Value types: `StepReference` (or reuse a shared `{type,id,when?,reason?}`), `ExpectedArtifact`, `StepPrompt`/`StepGuidelines` (Markdown values) | Shapes S12 and S14. | `ProcedureReference.java`, `Guide.java`/`GuideBlock`/`GuideSection` |
| `Pack.java` (packs.domain) | Kind-keyed map with generic invariants (ADR-0037 d1). | No change for the map. A cross-artifact rule ("a step's type exists; `action_sequence` ids are steps of that type") is a new invariant or read violation, and needs a decision. |

### 1d. Storage (`service/packs/.../packs/infra/storage/`)
| Class | Responsibility | PR-B change |
|---|---|---|
| `KindBinding.java:22-252` | The kind↔DSO↔entity mapping: `kind()`, `fileToken()` (l.108), `schemaName()` (l.118), `idKey()`, `fileName(id)` (l.138, `<id>.<token>.yaml` where a registration creates a file), `companions/withCompanions/companionStructures/checkCompanion` (l.195-241), `idIsFileStem()` (l.249). | New `MissionTypeBinding` and `MissionStepBinding` (enums, `INSTANCE`). Override `fileName` for a nested default layout. Return `idIsFileStem() = true`. Declare companions. |
| `KindBindings.java:19-30` | The one registry. Its order is the load and digest order, and new kinds are appended. `COMPANION_STRUCTURES` is derived (l.36-38). | Append the two bindings. |
| `YamlFormats.java` | One `YamlFormat` per kind, in the same order. | Append `MissionTypeYaml` and `MissionStepYaml`. |
| `<Kind>Dso.java`, `<Kind>Yaml.java` | The persistence record and the YAML codec (key order, S16 omission of empty lists). | New. Precedent: `ProcedureDso`/`ProcedureYaml` (`61469e6`), `ToolguideDso.withGuide` (`812a755`). |
| `Companions.java:17-36,154-192` | The one companion path rule: relative to the artifact file, inside the pack, never `.yaml`, never a path another rule classifies except its own `files` rule (`claimedByRule`, l.171-192). | No code change. The companion rule globs must be the companions' own. |
| `CompanionContent.java`, `ContentForm.java` | Text or Markdown companion content. | No change. |
| `GuideMarkdown.java`, `MarkdownGuideFormat.java` | Map a Markdown document to the structured `Guide` (derived H1, fixed blocks). | Analogue `StepPromptMarkdown` (or a plain document if the prompt is held as a model with no further structure). |
| `PackFileSet.java` | Classifies files once: `loaded` (l.311), `markdownHolder` (l.352: a Markdown file is held only when its rule is a bound companion structure), `companions` (l.277), `digested` (l.182-252). | No change if the rules are added. A classified Markdown file that no artifact names fails loudly, so the rule globs must not catch pinned old-layout prompts (§5). |
| `FilesystemPackStore.java` | `read(id, kinds)` (l.297), `readKind` (l.507), `withCompanions` (l.540), the upsert with `planCompanions` (l.654) and `removeDropped` (l.758). | No change; it goes through the bindings. |
| `WholePackStore.java` | `load` (l.147), `companionFiles` (l.231-253), `unmodelled` (l.255), `write` (l.279), `putArtifact` (l.430). | No change. |
| `FileSystemPackInspector.java` | Validate mode: `artifactKinds()` (l.105), `parseAndValidate` (l.316), `companion` (l.292, which reads companions by their rule, the F3 fold in `73b7abb`), `misnamedArtifact` (l.366, the stem rule). | No change for schema-mapped files. Check the stem rule for nested steps. |
| `FileStems.java:24-46` | The id = file stem check, shared by the load and validate mode. | Used as is. |
| `PackLayout.java:45-57` | Where a new artifact is written: the recorded path, else `KindBindings.of(kind).fileName(localId)`. | A step's `localId` that contains `/` makes the default path nested, which is either convenient or a hazard (§5). |
| `DomainRepositoryService.java:254-290` | Generic `writeArtifact` and `assembly`. | No change, unless a repository port is wanted. |
| `PackSchemas.java` | Loads the bundled schemas. | Picks up the new ones. |
| `PresetFiles.java`, `PresetYaml.java` | `activated_mission_types` (l.30, 80-109). | A load-time check that preset mission-type ids exist becomes possible (optional, a later step). |

### 1e. Application and HTTP
| Class | Responsibility | PR-B change |
|---|---|---|
| `application/CompositeComponentResolver.java:99-101` | `default -> Optional.empty()` for `action`, `mission_type`, `procedure`, and every other unwired kind (D6). | None. |
| `application/RelationshipRegistrationService.java:86-91,114-116` | `from` must be a URN of an artifact in the pack (`urnsOf(pack)`). | **Behaviour change with no schema change:** once mission types and steps are artifacts, `POST /packs/{id}/relationships` with `from: mission_type:…` or `mission_step:…` succeeds where it was a 409. |
| `infra/api/RelationshipWire.java:74-81` | `Urn.parse` accepts any `NodeKind`. | A new node kind widens the URNs it accepts. |

---

## 2. Existing handling of `mission_step_contract`, `action` and `mission_type`

- **`mission_step_contract`** is a bound ArtifactKind (`ArtifactKind.java:26`). It is bound through `MissionStepContractBinding` (file token `step-contract`, schema `mission-step-contract`, l.27-33), `MissionStepContractDso/Yaml`, the domain `MissionStepContract`, `ContractStep`, `StepDelegation`, `StepInput`, `GateBinding`, and the files rule `**/*.step-contract.yaml` (file-kinds l.123). Its schema is still core's legacy shape and calls itself legacy: its description points to `MissionStep` as "the canonical surface for new mission-step authoring (FR-011)". The open follow-ups are rows 6, 7 and 9 in `docs/development/epic-186-sanitized-shape-follow-ups.md:47-52` (row 9: ids are ad hoc while `mission`/`action` hold the key). Graph URNs are `mission_step_contract:<t>/<a>`, while the file ids are `documentation-accept` or bare `implement`. These two id systems disagree.
- **`action`** is only a `NodeKind` (`NodeKind.java:34-35`), `Urn.action` (`Urn.java:49`), and fragment plural `actions`. Its nodes live in `packs/built-in/action.graph.yaml` (24 nodes; `scope` ×163 to doctrine, `instantiates` ×11 to templates). No entity, binding or schema exists. `actions/*/index.yaml` is excluded (file-kinds l.152).
- **`mission_type`** is only a `NodeKind` (l.36-37) with the fragment plural `mission_types` (`NodeKind.fromPlural`, l.86; pinned by `NodeKindTest:18-26` and `ArtifactKindTest:48`, which asserts `fromPlural("mission_types")` is empty). Its nodes are in `mission_type.graph.yaml` (4 nodes; `requires` ×21 to `action:` gives the sequence, `scope` ×38 to doctrine). Presets carry `activated_mission_types` (ADR-0042). `mission_types/*.yaml` and `governance-profile.yaml` are excluded (file-kinds l.166-173).
- **The whole-pack gate** lists `other:missions` as expected-red under #194 (`service/packs/src/test/resources/whole-pack/expected-red.yaml`). `WholePackRoundTripTest.missionTemplatesAreNotTemplates` (l.156-170) pins mission templates as `other:missions`.
- **Markdown:** `markdown/MarkdownCorpusTest` already parses and canonically re-writes all 47 pinned step prompts and guidelines (`glob:packs/*/missions/mission-steps/**.md`, `PINNED_COUNT = 72`). The `markdown` form holds them losslessly today, so a Markdown companion is proven at no cost.

### HTTP (`contracts/charter/`)
- **No operation exposes a mission type, step or action.** `openapi.yaml` has only `/info`, `/openapi.yaml`, `/packs/{packId}/{tactics,directives,styleguides,paradigms,toolguides}[/{id}]` and `/packs/{packId}/relationships`. The old `/mission-types/{missionType}/actions/{action}/context` operation survives only in closure and lint fixtures (`contracts/src/test/resources/closure/*.yaml`, `contracts/lint/fixtures/*`).
- `schemas/NodeKind.yaml` enumerates `action` and `mission_type`, but only `_index.yaml` references it; no operation does. Its `x-source` points at the dead path `service/charter-domain/...`, and no test syncs it with Java `NodeKind`. `CharterRefusalCode.yaml` keeps `mission_type_not_found` and `action_not_found` as not raised. `responses/ActionNotFound.yaml` exists.
- **Would PR-B change the HTTP contract?** Not as a schema change, if PR-B stays "read and write only" (issue title). The observable effects:
  1. `/relationships`: a `from` URN of a mission type or step now resolves (409 becomes 2xx).
  2. The `ETag` and `Charter-Source-Fingerprint` of packs with mission content change once (as ADR-0043 Negative notes for any newly bound kind).
  3. If `mission_step` becomes a node kind, `NodeKind.yaml` gains an additive enum value. Record it under CHANGELOG `Added`, and add a scenario line if any.

  Mission-type endpoints (the glossary says "Modelled as its own concept, with its own domain model and endpoints", `docs/glossary/charter/README.md:254`) are a separate decision under ADR-0009 additive sets. **Recommendation: no new operation in PR-B.**

---

## 3. Governing ADRs and invariants (exact citations)

| Constraint | Citation |
|---|---|
| Sanitized shape wins. A pinned file that breaks it is fixed in open-packs, with no tolerance logic. | ADR-0036 d5 (`docs/adr/0036-…md:253-281`); ADR-0043 d1 (l.107-110); issue #194 operator decision 1 (2026-10-28) |
| S1 id = file stem; S2 `schema_version: "1.0"`; S3 one name per concept; S4 closed objects; S5 no repeated, variant or always-null fields; S6 one spelling; S7 explicit types; S8 derived data declared and recomputed | ADR-0042 d1 table (`0042-…md:101-108`) |
| S9 snake_case; S10 `title`; S11 `description`; S12 `content`; S13 `<role>_path` names a file, and every companion is named by a field; S14 references `{type,id,when?,reason?}`, never minted until edge authority is ruled (#189); S15 `role` names who acts (ADR-0032), absent means any role; S16 an optional list has `minItems: 1` and is never written `[]` | ADR-0043 d1 (`0043-…md:96-105`) |
| Steps name roles, never profiles. A mission type does not name profiles. Roles are open and extended by packs. `operator` is a role. | ADR-0032 d1, d2, d5 (`0032-…md:45-49,67-68`); also d6 "Mission types and profiles are their own concepts … loosely coupled by role or by id" (l.69-70) |
| Canonical schemas live here; consumers adapt; the content stays with its owners | ADR-0015 d1, d2, d5 (`0015-…md:49-66`) |
| The registry: one binding per kind, appended to `KindBindings`/`YamlFormats`; entity, DSO, binding, format and schema per kind; a binding names its file token and schema (amendment #190/#191); "kinds that are not files of their own" are not bindable | ADR-0037 d2-d4 (`0037-…md:138-164`), amendment #191 (l.213-242) |
| **ADR-0037 d4** (requested): the registry does not cover non-YAML content beyond companions, or kinds without their own file. A mission step therefore needs a file of its own; it cannot stay a sub-document of a mission-type file if it is to be a bound kind. | `0037-…md:145-164` |
| ADR-0037 d5: "where a file sits is how a pack is authored, not what the artifact means". Identity (the owning mission type) must therefore be in the YAML, not derived from the directory. | `0037-…md:165-175` |
| ADR-0037 d6 reversed: a kind of another slice is bound in `packs.infra.storage`; the store depends inward on that slice's domain | `0037-…md:58-81` |
| Every artifact kind implements `CharterArtifact`, mission types included, "each from its own slice and domain model" | ADR-0035 d4 (`0035-…md:48-49`); Context l.32-38 |
| Companions: named by a field; text or Markdown with a structure schema; never YAML; one owner; a companion may not claim a path another rule classifies except its own `files` rule | ADR-0039 d1-d7 (`0039-…md:114-174`) and the 2026-10-29 amendment (l.65-97) |
| A content form is declared by the rule; `markdown` is built; `text` is declared but not built for `files` rules (it is fine for companions) | ADR-0040 and its 2026-10-29 amendment (`0040-…md:75-130`); `file-kinds.yaml` header l.18-38 |
| Every covered file is a `files` rule with a closed schema, a `payloads` rule, or an exclusion with a reason; a YAML file is never held without a schema | ADR-0042 d6 (`0042-…md:253-269`) |
| Validate mode agrees with the load (schema plus stem) | ADR-0042 d7 (`0042-…md:271-286`); ADR-0043 d4 last paragraph (l.197-199) |
| Migrate with the service's own writer and re-pin, rather than list the refusals as known failures (a refused owner leaves its companions unowned and red) | ADR-0043 d6 and Alternatives (`0043-…md:266-289,374-375`) |
| Open-packs and core follow afterwards, outside this mission | Issue #194 operator decision 2 (2026-10-28) |
| One name per concept for fragment plurals (R14) | DIV-011 (`docs/conformance/divergence-register.md:247-256`) |
| Mission step pointers are `prompt_path`, an output `path`, and `guidelines_path` | Follow-up row 13 (`docs/development/epic-186-sanitized-shape-follow-ups.md:56`) |
| Recipe ingredients: a mission type names the guidance it calls for, by id, and names no profiles. This replaces the mission-type profile. | `docs/context/conceptual-model.md:226-229`; glossary `docs/glossary/charter/README.md:342-344` |
| Candidate layout `missions/<type>/mission.yaml`, `missions/<type>/steps/<step>/`, `…/template.md` (undecided) | `docs/architecture/logical-model.md:458-461` |
| PR-B is expected to write its ADR first ("ADR-0042 … written first" in the issue text; ADR-0042 is taken, so the new record is ADR-0044) | Issue #194 body, "Planned slicing" |

---

## 4. Test seams

`service/packs/src/test/java/ai/speckitty/charter/packs/`:
- **Domain:** `domain/<Kind>EntityTest.java` (precedent `ProcedureEntityTest`, `ToolguideEntityTest`, `GuideTest`, `MissionStepContractTest`).
- **Schema:** `infra/storage/<Kind>SchemaTest.java` with fixtures in `src/test/resources/schema-fixtures/<kind>/accepted.*.yaml` and `refused-*.yaml`, one refused file per retired field (precedent `ProcedureSchemaTest` and `schema-fixtures/procedure/`).
- **YAML and DSO:** `<Kind>YamlTest`, `<Kind>DsoRoundTripTest` (`ProcedureYamlTest`, `ToolguideDsoRoundTripTest`).
- **Companions:** `MarkdownCompanionTest`, `CompanionFilesTest`, `CompanionDigestTest` (pairs each companion with its owner), and `<Kind>GuideTest`-style tests (`ToolguideGuideTest`, 512 lines, is the template for structure rules and refusals).
- **Registry and vocabulary:**
  - `KindBindingsTest`: every binding's file name and schema against `file-kinds.yaml`; `hasCompanions` iff companions are declared.
  - `VocabularyEqualityTest` (l.66-148): node kinds, fragment plurals, artifact kinds, the inspector's schema kinds (l.151) and relations, all equal across Java and the schemas.
  - `PackFileKindTest`: sample paths, overlap, counts `nodeKinds().hasSize(15)` (l.214) and `fileRules().hasSize(19)` (l.219), and mission sample paths (l.53-61, 154, 177, 188).
  - `PackSchemasTest`: `bundledKinds().hasSize(19)` (l.63).
  - `SchemaKeywordCoverageTest`; `RetiredFieldNamesTest` (assert `display_name`, `agent_profile`, `prompt_template`, `sequence_index`, `in_action_sequence`, `delegates_to`, `template_file` are refused).
- **Store:** `DomainRoundTripTest`; `PackFileSetExactnessTest` (one line per kind); `PackFileSetTest`; `FileSystemPackInspectorTest` (validate mode, stem rule, companions); `GeneratedPackTree` (a per-kind generator `case` at l.665 and l.684. Its javadoc at l.65-67 keeps `mission_types`, `missions` and `actions` out of the alphabet because they are both a rule and an exclusion; revisit that). Use `DoctrineValidationParityTest` for load/validate parity.
- **Round-trip fixtures:** `src/test/resources/roundtrip-fixtures/<kind>/` (precedent `procedure/{full,minimal}.procedure.yaml`, `edges-pack/`, `retired-pack/`, `misnamed-pack/`; `toolguide/` with a guide).
- **Whole-pack gate:**
  - `WholePackRoundTripTest`: `REQUIRED_KINDS` (l.59-73) gains `mission-type` and `mission-step`, and `missionTemplatesAreNotTemplates` (l.156) is revisited.
  - `WholePackCensus`: companions take their owner's gate kind (l.94-105).
  - `src/test/resources/whole-pack/expected-red.yaml`: the `other:missions` entry, under #194, is narrowed or re-reasoned.
  - `known-failures.yaml`; `exclusions.yaml`.
  - The open-packs corpus tests are tagged `open-packs` and run with `:packs:openPacksRoundTrip`: `WholePackOpenPacksRoundTripTest`, `ProcedureAndGuideCorpusTest`, `MissionStepContractCorpusTest` and `markdown/MarkdownCorpusTest`, which already covers the 47 prompts and guidelines.
- **Divergences:** `DivergenceRegisterTest` against `docs/conformance/divergences.yaml` and `divergence-register.md`. Add a DIV-5xx block, as #194 PR-A added DIV-401..403.
- **Kernel:** `service/kernel/src/test/.../ArtifactKindTest.java` (facts; l.48 `fromPlural("mission_types")` isEmpty flips), `NodeKindTest.java:18-42`, `UrnTest.java`.
- **ArchUnit:** `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`.
  - `SLICES = List.of("packs","profiles")` (l.63) and `EVERY_SLICE_IS_REGISTERED` (l.497) need updating for a new slice.
  - Also relevant: `KERNEL_IS_PURE` (l.130), `PACKS_DOMAIN_IS_PURE` (l.137), `PROFILES_DOMAIN_IS_PURE` (l.145, the template for `MISSIONS_DOMAIN_IS_PURE`), `PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE` (l.256), `STORAGE_RETURNS_DOMAIN` (l.291) and `ENTRYPOINT_STAYS_IN_ITS_SLICE` (l.381).
  - Module wiring precedent: `service/profiles/build.gradle.kts`, `service/packs/build.gradle.kts:10-11` (`api(project(":profiles"))`) and `settings.gradle.kts` (the module list).

---

## 5. Design options and recommendation

### Decision K: how the two kinds enter the registry
- **K1 (recommended): promote both to `ArtifactKind`.** Add `MISSION_TYPE` (activatable, but through `activated_mission_types`, not `activated_kinds`; not org-requirable; not selection-overlayable) and `MISSION_STEP` (not activatable on its own; it comes with its type). `NodeKind.MISSION_TYPE` becomes artifact-backed and `NodeKind.MISSION_STEP` is new.
  - *For:* `KindBinding`, `CharterArtifact`, `Pack` and `PackLayout` all key on `ArtifactKind`, so there is no refactor. It matches #148's target, the conceptual model ("activated like any other artifact") and ADR-0035 d4.
  - *Against:* it diverges from core's `MissionTypeNotAnArtifactKind` (needs a DIV entry). It touches every kind-vocabulary place (file-kinds, the graph-shard and org-fragment enums, the local `ArtifactKind` `$defs`, kernel tests).
- **K2: generalise `KindBinding` to `NodeKind`.** *Against:* it rewrites the ADR-0037 registry contract, `CharterArtifact.kind()` and the `Pack` invariants. That is too large for PR-B.
- **K3: one DSO (steps inline in `mission_type`).** *Against:* the operator named two DSOs. A companion per step would hang off a sub-document, and ADR-0037 d4 does not cover "kinds that are not files of their own".

### Decision S: which slice holds the domain
- **S-a: a new `missions` slice** (`service/missions`, `ai.speckitty.charter.missions.domain`), bound by the pack store like `AgentProfile` (ADR-0037 amendment option (c)). *For:* ADR-0035 Context and d4, ADR-0032 d6, the glossary ("own domain model") and the logical model all say mission types are their own concept. *Cost:* a Gradle module, `SLICES` + a purity rule, and no typed `Pack` view (`artifacts(MISSION_TYPE, MissionType.class)`).
- **S-b: `packs.domain`**, like `MissionStepContract` and `Procedure`. Cheaper, but it contradicts the stated target and moves later.
- **Lean: S-a**, since the profiles precedent makes it mechanical. If PR-B must stay small, S-b plus a recorded follow-up is acceptable. This is an operator call.

### Decision L: layout and identity
- **L1 (recommended), nested:**
  ```
  <pack>/missions/<type>/<type>.mission-type.yaml
  <pack>/missions/<type>/steps/<step>/<step>.mission-step.yaml
  <pack>/missions/<type>/steps/<step>/prompt.md        (prompt_path, required)
  <pack>/missions/<type>/steps/<step>/guidelines.md    (guidelines_path, optional)
  <pack>/missions/<type>/templates/…                    (unchanged, still other:missions)
  ```
  - `id` = file stem (S1, `idIsFileStem`). The step carries `mission_type: <type>` explicitly (ADR-0037 d5: location is not meaning). The step's `localId` and URN are `<type>/<step>`, giving `mission_step:software-dev/implement`. That matches the existing `action:` and `mission_step_contract:` URN forms and keeps ids unique per pack. Override `fileName` in the bindings so a registration lands in this layout.
  - Rule globs: `**/*.mission-type.yaml`, `**/*.mission-step.yaml`, `**/missions/*/steps/*/prompt.md`, `**/missions/*/steps/*/guidelines.md`.
  - *For:* it follows the logical-model candidate (l.458-461) and core's #148 "nested" fork. One directory per type keeps prompts beside their step, and none of these globs match the pinned old layout (`missions/mission-steps/<t>/<s>/prompt.md` has no `/steps/` segment). Pinned prompts are therefore not classified, not claimed, and not "unowned" (which would turn the gate red).
  - *Against:* the stem rule has to tolerate a composite `localId` (stem = the step part). `PackLayout`'s default `fileName` must be overridden. It needs a check that `mission_type` equals the enclosing directory, or that check is left out entirely (keep meaning in YAML only).
- **L2, flat:** `mission_types/<type>.mission-type.yaml` and `mission_steps/<type>-<step>.mission-step.yaml`, with companions `mission_steps/<type>-<step>.prompt.md`.
  - *For:* it is the kind-plural convention (`built_in_dir`). The default `fileName` works and the stem is the id.
  - *Against:* it overlaps the `**/mission_types/*.yaml` exclusion (file-kinds l.170), which must be narrowed or removed. The id is synthetic (`software-dev-implement`) and does not line up with `action:software-dev/implement`. Companion names need a rule like `**/mission_steps/*.prompt.md`.

### Decision C: the step companions
- **C1 (recommended): two Markdown companions** with their own `files` rules and structure schemas (`mission-step-prompt`, `mission-step-guidelines`), named by `prompt_path` (required) and `guidelines_path` (optional) per follow-up row 13. `MarkdownCorpusTest` already proves the model round-trips all 47 pinned files byte for byte.
  - The prompt schema must allow optional frontmatter and an optional H1: 28 of 47 pinned prompts carry frontmatter (`description:`), and 39 carry an H1.
  - Rule on whether prompt frontmatter `description` duplicates the step's `description` (S5/S6). Recommend refusing frontmatter in a stored prompt, deriving it (S8), or dropping it in the migration.
- **C2: text companions** (byte for byte, like the skill body). Cheaper, with no structure schema or rule. But validate mode does not read them, and they give no structure. Acceptable if the operator wants minimal scope.

### Decision F: folding the retired files
| Retired source | Fold into | Notes |
|---|---|---|
| `mission_types/<t>.yaml` | `mission_type` | `display_name`→`title` (S10); `requires_substantive_spec` kept (boolean); `schema_version: 1`→`"1.0"` (S2) |
| `governance-profile.yaml` | `mission_type.references` (recipe ingredients, S14, kept and never minted, the procedure precedent) | Drop `selected_agent_profiles` (ADR-0032 d1; conceptual model Appendix A l.401-405), `template_set` (core retired it, `models.py:268-277`), `available_tools` and `activations` (charter-level, not the type's). `selected_mission_step_contracts` becomes a step-level reference. `id == mission_type` collapses into one `id` (S5). The fragments' `body_path` at governance-profile becomes an open-packs issue. |
| `actions/<a>/index.yaml` | **`mission_step.references`** of the step whose id is the action | Every list becomes `{type, id}` entries in authored order. Orphans with no step (software-dev `retrospect`) are a pack inconsistency for open-packs, not a reader rule. |
| `mission-runtime.yaml` | `mission_type.action_sequence` (ordered step ids, one home; replaces the runtime order, step `sequence_index` and `in_action_sequence`, S5) plus `mission_step.depends_on` (DAG) and `mission_step.description` | `mission.version` and runtime `prompt_template: research.md` go (S5). The `discovery`/`research` and `accept` mismatches are open-packs issues. |
| `expected-artifacts.yaml` | `required_by_step` becomes `mission_step.outputs: [{key, class, path, blocking}]`; `required_always`/`optional_always` become `mission_type.artifacts` | Rename `artifact_key/artifact_class/path_pattern` per S3/S13 (`key`, `class`, `path`). `manifest_version` is sync identity and is dropped (S5). |
| `step.yaml` | `mission_step` | `agent_profile`→`role` (S15/ADR-0032); `display_name`→`title`; `prompt_template`→`prompt_path`; `template{artifact_key,template_file}`→`template{key, path}` (S13); `guidance: null` and `delegates_to: []` dropped (S5/S16); `step_type` kept (enum, maybe renamed `kind`?) |
| `mission.yaml` | **Not in the operator's retirement list.** `paths` overlaps core `path_conventions`, and `artifacts` overlaps expected-artifacts. | Needs an operator ruling: fold `paths`→`mission_type.path_conventions` (closed keys `VALID_PATH_KEYS`) and `description`, and retire the rest (`workflow.phases`, `validation`, `mcp_tools`, `agent_context`, `task_metadata`, `commands`), or keep it excluded. |

### Gate strategy (the key trade-off)
Operator decision 2 puts the open-packs migration *outside* this mission, so the pinned files keep their old names. The new globs match nothing pinned, which means the following:
- The gate stays green: the old files stay excluded (re-reasoned "retired shape, superseded by ADR-0044; open-packs#NN") and `other:missions` stays expected-red for mission templates.
- But **no pinned mission content is exercised by the round trip**. Coverage comes only from fixtures and `GeneratedPackTree`.

The alternative is the ADR-0043 d6 route: migrate with the service's writer, land it in open-packs, re-pin. It gives real coverage but contradicts decision 2 unless the operator re-rules. Recommend asking explicitly.

### Hazards to raise
1. The fragment plural `mission_steps` means *step contracts* in core (DIV-011). Adding it for `mission_step` flips its meaning. DIV-011 needs updating, or the step needs no fragment plural (fragments then could not declare steps).
2. `mission_step` vs `mission_step_contract` overlap. The glossary calls the contract "the detailed version of a mission step" (`docs/glossary/charter/README.md:428-430`). PR-B should keep the contract kind as is and let a step reference it by `{type: mission_step_contract, id}`. Merging the two is a later decision (follow-up row 9).
3. Keep the `action` node kind: shards and fragments use it. Mapping `action:<t>/<s>` to `mission_step:<t>/<s>` waits on edge authority (#189).
4. The pinned qa steps name a missing `prompt.md`, which will be refused once migrated.
5. `GeneratedPackTree` excludes `missions`, `mission_types` and `actions` from its alphabet because they are both a rule and an exclusion. The new rules change that analysis.
