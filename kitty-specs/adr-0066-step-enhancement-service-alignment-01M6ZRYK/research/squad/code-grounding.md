# Phase A: code grounding for #424, #428, #429 and #430

Role: java-jenny (implementer lens, read-only). Repo `/home/user/spec-kitty-charter` at `391ed51b` (main, holds #425 and #427).
open-packs pinned at `gradle.properties:10` `openPacksRef=22c14b2d…`. There is no `.codegraph/` directory, so this report uses grep and file reads only.
Notation: `P=service/packs/src/main/java/ai/speckitty/charter/packs`, `A=service/activations/src/main/java/ai/speckitty/charter/activations`, `K=service/kernel/src/main/java/ai/speckitty/charter/kernel/domain`, `D=service/deployable/src/test/java/ai/speckitty/charter/deployable`.

## 1. Sizes, 90-day churn and responsibility map

Churn is `git log --since=90.days --oneline -- <path> | wc -l` on a full clone (`is-shallow=false`). Caveat: the count does not follow renames, so files moved out of the old `service/charter-domain` module may be undercounted.

| File | Lines | 90d commits | Responsibility |
|---|---|---|---|
| `P/infra/storage/PackFileSet.java` | 594 | 26 | The one declaration of which files the store loads and which the fingerprint digests. `loaded()` :376-403 classifies each file once. `yamlHolder()` :408-418 returns `Optional.empty()` for a YAML file whose schema has no binding, which is how `step-enhancement` YAML is skipped. `markdownHolder()` :428-440 returns empty for `isDeferredCompanionStructure` (`prompt-fragment`). `digested()` :265-305 iterates only `KindBindings.all()`. |
| `P/infra/storage/KindBinding.java` | 295 | 9 | Interface `KindBinding<D, E extends CharterArtifact>` :93-283: kind, DSO type, `toEntity`/`toDso`, companions (`companions`, `withCompanions`, `companionStructures`, `checkCompanion`), `idIsFileStem`, `placedAtItsFileName`, `namedFiles`. |
| `P/infra/storage/KindBindings.java` | 126 | 12 | The registry `ALL` :19-33 (14 bindings: tactic … mission_type, guard, lifecycle). It has no step_enhancement. `DEFERRED_COMPANION_STRUCTURES = Set.of("prompt-fragment")` :49. The order is the load and digest order ("New kinds are appended" :14). |
| `P/infra/storage/FileSystemPackInspector.java` | 654 | 21 | Validate mode (`Scope.FULL`). It reads every mapped YAML through `parseAndValidate` :420-466, which is schema-only for step-enhancement. `misnamedArtifact` :470 uses `KindBindings.bySchema`, so it runs no stem check for step-enhancement. It reads deferred Markdown companions through `readCompanion` :260-261 → `companion()` :396-417. That runs the structure schema only: `byCompanionStructure("prompt-fragment")` is empty, so `checkCompanion` never runs (:407-409). |
| `P/infra/storage/YamlSerializationStrategy.java` | 136 | 16 | YAML read/write through `ParsedYaml`, `PackSchemas` and one `YamlFormat` per bound kind (`YamlFormats`). |
| `K/PackDescriptor.java` | 207 | 6 | Kernel descriptor. `schemaVersion` is an `Optional<String>`, empty for a legacy descriptor (:23, :35, :160-173). This is the version gate #424 needs. |
| `P/infra/storage/PackDescriptorDso.java` / `PackDescriptorReader.java` | 120 / 119 | 8 / 7 | Persistence shape and reader. `schemaVersion` is carried through (:31, :45, :99). |
| `P/infra/storage/WholePackStore.java` | 909 | 23 | Whole-pack load and write. `load()` :183-230 builds the `modelled` set; every other file is `unmodelled()` :653-683. `loadStaged` refuses unmodelled files as `validation_failed` (javadoc :231-235). Today a step-enhancement file and its fragment are therefore unmodelled: the load tolerates them, the write drops them, and a registration refuses them. |
| `P/infra/storage/DomainRepositoryService.java` | 867 | 46 (hottest) | The domain repositories over the store. `compositionFacts()` :379-417 builds `defined` from `assembled.artifacts()` plus `declaredWithoutFile` :580-597. That second source covers only `ANTI_PATTERN` and `ACTION` nodes, so a `step_enhancement:` URN is never in `defined`. `missionTypeOf` :424-428 binds mission_step_contract only. |
| `P/domain/LayerMerge.java` | 404 | 4 | `merge()` :56. Winners come from `holdersByUrn` :288 over the composition facts, so a kind absent from the facts is never a winner. |
| `P/domain/InForceResolution.java` | 540 | 4 | `resolve(MergedClosure, Edges, InForceRequest)` :105. `requiredNotInForce` :398-406 takes the pruned URNs that are required or locked. `enhancedTargets` :409-417 collects the targets of each enhancer with reason ENHANCED_TARGET_NOT_IN_FORCE. A step enhancer's targets are `action:` URNs. |
| `P/domain/InForceStates.java` | 237 | 2 | Value object: states, stale, denyNotApplied, overrideNotApplied, requiredNotHeld, requiredNotInForce, enhancedTargets (:25-35). |
| `A/infra/packs/ClosureViewSession.java` | 310 | 4 | The adapter from packs to activations. Every URN passes through `activatable()` :229-231. See §6. |
| `A/domain/EffectiveSet.java` | 406 | 1 | Builds the effective set. `skippedOf` :368-397 builds `EnhancerWithoutTarget(ref, enhancedTargets…)` :375-376 and `requiredNotInForce` :380. |
| `A/domain/EffectiveSkippedReport.java` | 264 | 1 | `record EnhancerWithoutTarget(ArtifactRef ref, List<ArtifactRef> targets)` :94. |
| `A/infra/api/EffectiveReadMapper.java` | 192 | 1 | Wire mapping. `EnhancerWithoutTarget.KindEnum.fromValue` and `ActivationChangeMapper.references(targets)` :166-172. |
| `P/infra/storage/MissionTypeBinding.java` | 364 | 4 | Mission type plus its `steps/<step>.prompt.md` and `.guidelines.md` companions (`PROMPT_SCHEMA="mission-step-prompt"` :36). It parses a prompt into `StepDocument(title, description, preamble, sections)` (:142, :242-282). |
| `P/infra/storage/MissionTypeChecks.java` | 115 | 1 | Validate-mode checks on mission-type files. |
| `P/infra/storage/markdown/MarkdownCodec.java` | 245 | 4 | Markdown parse and render. An HTML comment is plain text (:39-42), so slot markers survive as text inside the preamble and section content. |
| `P/domain/Diagnostic.java` | 52 | 3 | `Diagnostic.Code {PACK_LEVEL_MISSING, PROMPT_SLOT_NOT_EMPTY}` :17-24. |
| `K/NodeKind.java` | 117 | 6 | 18 constants (:7-43), including GUARD and LIFECYCLE. |
| `K/ArtifactKind.java` | 125 | 8 | `STEP_ENHANCEMENT(…, activatable=true)` :63. Javadoc :58-62 says "no store binds the kind yet (#428)". |
| `K/RefusalCode.java` | 237 | 15 | `PROMPT_SLOT_MALFORMED`, `PROMPT_SLOT_NOT_EMPTY`, `PROMPT_FRAGMENT_INVALID` and `PROMPT_SLOT_MISSING` at :189-195. |
| `service/web-transport/.../RefusalHandler.java` | 388 | 6 | The status map. The four prompt codes are at :365-368. |
| `service/packs/src/test/.../infra/api/WireTokenParityTest.java` | 207 | 4 | See §4 and §5. |
| `contracts/charter/schemas/CharterRefusalCode.yaml` | 732 | 43 | Codes enum :219-222. `x-refusal` :710-729 gives all four prompt codes `status: 500, raised: false`. The description is at :161-167. |
| `contracts/charter/schemas/DiagnosticCode.yaml` | 21 | 5 | See §5. |
| `contracts/charter/schemas/NodeKind.yaml` / `CascadeKind.yaml` | 25 / 24 | 3 / 3 | See §5. |
| `contracts/packs/schemas/step-enhancement.schema.json` | 53 | 2 | `schema_version` const "1.0", `id`, `prompt_fragments[{slot,path}]`. It has no guard or emit fields; its description says "(#428)". |
| `contracts/packs/file-kinds.yaml` | 294 | 16 | Rules :190-194: `*/missions/*/enhancements/*.step-enhancement.yaml` uses `step-enhancement`, and `*.prompt-fragment.md` uses `prompt-fragment`, content_form markdown. |
| `.../storage/WholePackRoundTripTest.java` | 821 | 14 | The whole-pack gate. See §4. |
| `D/ReservedRefusalCodesTest.java` | 362 | 11 | The raised/unraised ArchUnit gate. See §4. |

### Where step_enhancement and prompt-fragment handling lives today (after #425)
- **Classification.** `file-kinds.yaml:81,100,118,190-194`. `ArtifactKind.STEP_ENHANCEMENT` and `NodeKind.STEP_ENHANCEMENT` exist.
- **Validate mode.** The `step-enhancement` YAML is checked against its JSON schema only (`FileSystemPackInspector.readMapped` :339 → `parseAndValidate` :420). The `prompt-fragment` Markdown is checked against its structure schema only (`companion()` :396). That schema refuses frontmatter and a blank body; MarkdownCodec refuses the frontmatter cases.
- **Load.** `PackFileSet.yamlHolder` :414-417 maps the YAML to no list, and `markdownHolder` :430-433 maps the fragment to no list. No store holds either file, and neither enters the source fingerprint (`digested` :272-286 iterates bindings only).
- **The semantic checks the schema description promises are not in Java.** grep finds no `step-enhancement` or `prompt-fragment` string in any main class except `KindBindings`. Those checks are: id equals the file stem, each slot at most once, the named companion exists beside the file, no slot marker in a fragment, and the target is a step. They are #424 and #428 work.
- **Tests.** `KindBindingsTest:198-210` asserts `prompt-fragment` is deferred and not a companion structure; these assertions flip when the kind is bound. `PromptFragmentContractsTest` (285 lines) pins the schema behaviour and the reserved-code wording (:279-283). `FileSystemPackInspectorTest:375-416` covers frontmatter and blank fragments in validate mode.
- **The packs ship no step enhancement.** open-packs at 22c14b2 has no `*.step-enhancement.yaml` and no `*.prompt-fragment.md`. The five built-in software-dev prompts (`specify`, `plan`, `tasks`, `implement`, `review`) still hold text between `<!-- spdd:reasons-block:start/end -->`. `packs/built-in/pack.yaml` has no `schema_version`, so it is a legacy descriptor and the slot finding is a warning, not a refusal.

### Mission-step prompt read path, and whether an effective-prompt endpoint exists
- Prompts are read only in the store: `MissionTypeBinding` loads the companion and `MarkdownCodec` parses it into `missions/domain/StepDocument` and `MissionStep.prompt()` (`MissionStep.java:22,32`).
- **No HTTP operation serves a mission type, a step or a prompt.** `contracts/charter/paths/` holds no mission-type read. `ArtifactReadService.SERVED` covers tactic, directive, styleguide, paradigm and toolguide only (WireTokenParityTest:109; `CharterRefusalCode.yaml:11-13`).
- No contract path or schema mentions "prompt" except the refusal codes and `SelectedArtifacts`.
- The only adjacent operation is the reserved `getEffectiveActionContext` (`paths/activations_activationId_missionTypes_missionType_actions_action_context.yaml:6,16`: `x-reserved`, contractSet activation, #125). It answers 501.
- ADR-0066 decision 9 (lines 141-148) says the effective-prompt read "belongs with the mission-type read (R5, #99) and the activation read (R9, #103)" and is additive. **So #424 needs a new contract operation, or must build a reserved one. Either choice is an architect decision.**
- **Risk (my inference, not confirmed).** The store holds the parsed `StepDocument`, not the raw bytes. "Base prompt text unchanged" (ADR-0066:141) therefore means either the canonical render (meaning-equal per ADR-0036) or a new raw-text holding.

### Where the refusal and diagnostic codes are defined
- **Contract.** `CharterRefusalCode.yaml` enum :219-222, description :161-167, `x-refusal` :710-729 (all `raised: false`). `DiagnosticCode.yaml` :15-18.
- **Java.** `K/RefusalCode.java:189-195`, `RefusalHandler.java:365-368` (status 500), `P/domain/Diagnostic.java:17-24`.
- **The wire does not carry `DiagnosticCode` today.** `Diagnostic.yaml` is listed only in `schemas/_index.yaml` and no path references it. Only the domain uses `Diagnostic` (`PackFinding`, `PackFindingCode.diagnostic()` :84-86).
- **Stale `x-source` paths.** `NodeKind.yaml:24`, `Diagnostic.yaml:15` and `DiagnosticCode.yaml:20` point at `service/charter-domain/...`, which no longer exists. The real files are `K/NodeKind.java` and `P/domain/Diagnostic.java`.

## 2. Test seams and their cost

All tests below are plain JUnit 5 in the module's `test` task, with no Spring, unless stated. `TacticReadWiredTest` is the only Spring test in packs or activations.

| Surface | Existing tests (lines) | Seam |
|---|---|---|
| PackFileSet | `PackFileSetTest` (365), `PackFileSetExactnessTest` (492, the generated-tree exactness check), `PackFileSetBudgetTest` (114, tag `timing`, outside check) | `digested(packDir, Set<ArtifactKind>)` and `digested(packDir, PackFileKind table)` are test seams for planting an omission. |
| KindBindings | `KindBindingsTest` (235) | Static registry. Assertions :105, :198-210 must flip for #428. |
| Inspector | `FileSystemPackInspectorTest` (875), `PromptFragmentContractsTest` (285), `PackFileKindTest` (:47, :233, :249) | Temp-dir packs. Cheap. |
| PackDescriptor | `PackDescriptorReaderTest` (415) | Cheap. |
| WholePackStore | `WholePackStoreTest` (883) | Temp-dir packs. |
| DomainRepositoryService | `DomainRepositoryServiceTest` (929) | Built directly with a locations supplier. No Spring. |
| LayerMerge / InForceResolution | `LayerMergeTest` (611), `InForceResolutionTest` (957), `InForceResolutionPropertyTest` (556, property-based) | Pure domain, value inputs. The cheapest place for #429's packs side. |
| ClosureViewSession | `ClosureViewSessionDepthTest` (45), `ClosureViewSessionStepViaTest` (34) | Package-private static mappers (`inForceOf` :169, `mergedOf` :95, `reachOf` :259) are directly unit-testable with `InForceStates` values. This is the right seam for #429. |
| EffectiveSet | `EffectiveSetTest` (584) | Pure domain. |
| Mission types | `MissionTypeBindingTest` (797), `MissionTypeSchemaTest` (166), `MissionTypeCorpusTest` (225, open-packs), `MissionTypeStepsHeldTest` (123), `missions/.../MissionTypeTest` (314) | Prompt parsing lives here. This is where #424's slot checks get their unit tests. |
| Vocabulary and wire | `WireTokenParityTest` (207), `VocabularyEqualityTest` (304), `PackPolicyRulesTest:184-190` | Read contract YAML through the `charter.contractSchemas` system property (`service/packs/build.gradle.kts:238`). |
| Contract scenarios | `contracts/charter/scenarios/*.feature` (32 entries, including `effective-reads.feature`, `layer-merge.feature`, `composition.feature`). Glue in `D/bdd/*Steps.java`. | Bound by `D/bdd/CharterContractSuite` (Cucumber on JUnit Platform, `@IncludeTags("contract")`, `@ExcludeTags("WIP")`, glue `ai.speckitty.charter.deployable.bdd`, `UndefinedStepGuard` fails undefined steps). This is a Spring Boot deployable: the costliest seam. The step vocabulary is in `scenarios/README.md`, and adding a step is discouraged. Untagged or `@WIP` features are not run. |

## 3. Churn hotspots and conflict risk
- `DomainRepositoryService` (46), `CharterRefusalCode.yaml` (43), `PackFileSet` (26), `WholePackStore` (23) and `FileSystemPackInspector` (21) are the hot files. #424 and #428 both touch `PackFileSet`, `KindBindings`, the inspector and the refusal YAML, so their work packages need serializing or a careful split.
- `ClosureViewSession`, `EffectiveSet` and `InForceResolution` are cold (1-4 commits), so #429 has low conflict risk.

## 4. Gates that name these files or kinds, and how to keep each green honestly

1. **ArchUnit `D/HexagonalArchitectureTest.java` (1315 lines).**
   - The packs domain is pure (`PACKS_DOMAIN_IS_PURE`, `domainIsPure(slice)`).
   - Port implementations stay in `infra.storage`, and storage returns domain types only (`STORAGE_RETURNS_DOMAIN`).
   - Activations may name packs only from `activations.infra.packs` (`ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER`, ~:986). ClosureViewSession is already there.
   - `RESERVED_CONTROLLER_READS_NOTHING` and `RESERVED_METHOD_READS_NOTHING`: a reserved operation must reach no use case. Building an effective-prompt operation means removing its reservation, not wiring a reserved method.
   - Effect: a `StepEnhancement` entity goes in the domain (it must implement `K/CharterArtifact`, which is a plain interface, not sealed), and its DSO and binding go in `infra.storage`.
   - No allowlist exists: `config/checkstyle/suppressions.xml` holds 0 entries.
2. **`D/ReservedRefusalCodesTest`.**
   - Rule: a main class may name an unraised code only from the enum and its mappings (`statusOf`, `title`). Exceptions: `WholePackStore` may name `DRG_SCHEMA_ERROR`, and `PackFindingCode` may name `PACK_LEVEL_MISMATCH` and `DRG_SCHEMA_ERROR` (`OFF_WIRE` :70-72).
   - A raised code must be named by some main class.
   - :342-345 lists the four prompt codes as unraised.
   - #424 must flip `raised: true` in `CharterRefusalCode.yaml:710-729`, update the description :161-167 and update this test's list together.
3. **`checkRefusalExamples`** (`contracts/build.gradle.kts:886`, `contracts/src/main/.../refusal/RefusalExamples.java`): examples and descriptions may name raised codes only.
4. **`D/bdd/ContractDescriptionsTest`.** It pins description wording. Commit 2fa116d5 pinned "the two reads that stay reserved". Any change to the refusal description must update it.
5. **Contract checks in `check`** (`contracts/build.gradle.kts`): `bundleContract`, `lintContract` (vacuum ruleset, including `enum-snake-case`, `x-source-shape`, `reserved-operation-declares-501` and `built-operation-declares-no-501`), `checkClosedResponseObjects`, `checkContractCompatibility` (:837; oasdiff against the released baseline, which needs a `### Breaking` CHANGELOG entry when breaking) and `checkContractChangelogFragments` (:922). Every contract change needs a dated fragment in `contracts/charter/changes/` (the latest is `2026-11-08-prompt-fragment-contracts.md`).
   - **Risk for #429.** Widening `ArtifactReference.kind` or `EnhancerWithoutTarget.targets` beyond `ActivatableKind` changes a response schema. Adding a response enum value is flagged by oasdiff and by the client's tolerant handling. Check the severity in `contracts/compat/severity-levels.txt`.
6. **Scenario trace gates.**
   - `D/bdd/OperationCoverage(Test)`: every 2xx of every operation is exercised by some scenario, so a new effective-prompt operation needs a scenario.
   - `D/bdd/ReservedOperationCoverageTest`: reserved operations and their 501 pins match one to one per contract set.
   - `UndefinedStepGuard`.
7. **`WireTokenParityTest`.** Pairs today: ActivatableKind ×4 inlined copies, SelectedArtifacts members, CharterRefusalCode, ArtifactKindToken (read from YAML, :120-130) and PackLevel. A positive control fails each skewed pair (:191-205). It has no NodeKind, CascadeKind or DiagnosticCode pair; that is #430's work.
8. **`VocabularyEqualityTest`.**
   - :77-112 holds NodeKind against `file-kinds.yaml` node_kinds in order.
   - :274-292: every projected load-path code (warning diagnostics plus load-path refusals) must be in `DiagnosticCode` ∪ `CharterRefusalCode`. This is containment only, not parity.
   - Plus `ActivationConfigSchemaParityTest:142` (13 activatable kinds, step_enhancement among them).
9. **Whole-pack round trip** (`:packs:wholePackRoundTrip`, `service/packs/build.gradle.kts:148-171`, tag `whole-pack`, outside `check`, required by `.github/workflows/whole-pack.yml:76` through the `CI gate / gate` check).
   - **What it iterates.** It takes the census of every file of pinned `packs/built-in` (`WholePackCensus`), runs each through `DomainRoundTrip` (read, write, read back, compare by meaning), and evaluates against `src/test/resources/whole-pack/{expected-red,known-failures,exclusions}.yaml`.
   - `modelledKinds` must equal `{descriptor, graph-shard, asset-payload, template, preset, pack-manifest, pack-document}` ∪ `KindBindings.all().schemaName()` (`WholePackRoundTripTest:113-124`).
   - **A newly bound `step-enhancement` enters `modelledKinds` automatically.** Built-in has no file of it, so the evaluation reports `EMPTY_MODELLED_KIND step-enhancement` (`WholePackExpectation.java:~398-401`) unless an `empty_kinds` entry is added to `expected-red.yaml`, as skill, guard and lifecycle have (expected-red.yaml:9-26), with issue #428.
   - That entry goes stale (`STALE_BASELINE`) the day open-packs ships its spdd enhancements, which is honest by construction.
   - A companion is attributed to its owner's kind (`WholePackCensus:26-28,107-113`), so `prompt-fragment` files would count as `step-enhancement`.
   - The round-trip proof of the new kind must therefore come from unit or corpus tests (as `GuardAndLifecycleYamlTest` does for guards) until a pack ships one.
   - A sibling task, `openPacksRoundTrip` (tag `open-packs`, build.gradle.kts:~115-140), runs `MissionTypeCorpusTest` and others.
10. **Source fingerprint.** Binding the kind adds its files to `digested()`. KindBindings are appended (:14), so packs without step enhancements keep their digest list (NFR-002). The tests to watch are `PackFileSetExactnessTest` and `PackFileSetTest`.

## 5. #430: contract enums against Java enums

| Contract enum | Contract values | Java source | Java values | Drift |
|---|---|---|---|---|
| `NodeKind.yaml:6-22` (16 values) | directive, tactic, styleguide, toolguide, paradigm, procedure, agent_profile, mission_step_contract, glossary_pack, skill, anti_pattern, asset, template, action, mission_type, step_enhancement | `K/NodeKind.java:7-43` (18 values, `token()` = lower-case name) | the same 16 **plus `guard` and `lifecycle`** | Java has 2 extra. NodeKind reaches the wire only through `EffectiveGraphNode` in the **reserved** `getEffectiveGraph` (`paths/activations_activationId_graph.yaml`). `x-source` is stale. Decide: add guard and lifecycle to the contract (an additive response enum value) or exempt them in the parity test with a reason. |
| `CascadeKind.yaml:7-21` (14 values) | the 13 activatable kinds + `all` | `A/domain/CascadeScope.java:23` (`ALL="all"`), `CASCADE_KINDS` = `ArtifactKind.activatable` :28-31 | 13 activatable tokens + "all" | **None.** No Java enum exists; the pair is `ArtifactKind.activatable` ∪ {`CascadeScope.ALL`}. A generated `CascadeKind` exists in client-java (`CascadeDepthRoundTripTest:8`). |
| `DiagnosticCode.yaml:15-18` (3 values) | pack_level_missing, **pack_unknown_key**, prompt_slot_not_empty | `P/domain/Diagnostic.java:17-24` (`Code.token()`) | pack_level_missing, prompt_slot_not_empty | **The pack_unknown_key drift, below.** |

**The exact pack_unknown_key drift.**
- The contract keeps `pack_unknown_key` in the enum (`DiagnosticCode.yaml:17`). Its description says "retired, never raised … The value stays in the enum so a client built against an earlier draft still reads it" (:7-11; CHANGELOG.md:481).
- Java removed the constant: `git log -S PACK_UNKNOWN_KEY` shows the removal in b6dc899b (#195), per ADR-0042:243-244.
- `PackPolicyRulesTest:186-190` actively asserts that `Diagnostic.Code` and `PackFindingCode` do NOT contain `PACK_UNKNOWN_KEY`.
- So a strict parity pair fails on `pack_unknown_key`. The honest fix is an explicit, named "retired contract-only values" exemption in the parity test, cited to ADR-0036 decision 3 and ADR-0042. Re-adding the Java constant would contradict `PackPolicyRulesTest`.
- Note: `Diagnostic` and `DiagnosticCode` are not referenced by any path, so the generator may not emit a model for them. The parity test should read `DiagnosticCode.yaml` directly, as the `ArtifactKindToken` pair does (WireTokenParityTest:120-130). Not verified by a build. NodeKind and CascadeKind do reach generated models, through `EffectiveGraphNode` and `SelectionRequest`/`ExclusionRequest` → `CascadeScope`.

## 6. #429: the exact lines in ClosureViewSession

`A/infra/packs/ClosureViewSession.java`:
- :228-231. Every URN is filtered here, and `ArtifactRef.of` (`A/domain/ArtifactRef.java:40-41`) returns empty for `NodeKind.ACTION`, because `artifactKind()` is empty:
  ```java
  private static Optional<ArtifactRef> activatable(Urn urn) {
      return ArtifactRef.of(urn).filter(ref -> ref.kind().activatable());
  }
  ```
- :173-174. Each enhancer's target list goes through `listOf` (:233-235), which drops every `action:<type>/<step>` target, so a step enhancer's targets come back `[]`:
  ```java
  states.enhancedTargets()
          .forEach((urn, list) -> activatable(urn).ifPresent(ref -> targets.put(ref, listOf(list))));
  ```
- :218-226 `requiredNotInForce`. Each entry is kept only if `activatable(entry.urn())` holds (:221), so a pruned required or locked `action:` URN disappears from the skipped report. The packs side does produce it (`InForceResolution.java:398-406`).
- Also filtered: `satisfiedBy` and `overriddenBy` :191-192, `denyNotApplied` :199, `overrideNotApplied` :211-212, and states :171. The way a step is reached is already re-homed to its mission type (`writer()` :289-297).

**Contract shapes.** `EnhancerWithoutTarget.yaml` is `{kind: ActivatableKind, artifactId: ArtifactId, targets: ArtifactReference[]}`, required all three, `additionalProperties: false`. `ArtifactReference.yaml` is `{kind: ActivatableKind, artifactId: ArtifactId}`, closed. `RequiredNotInForce.yaml` is `{kind: ActivatableKind, artifactId, reason ∈ {enhanced_target_not_in_force, mission_type_not_in_force}}`. `ActivatableKind.yaml` has no `action` value.
- So a step target cannot be represented on the wire without a contract change: a new step-reference shape, or a widened kind. That change is a response-schema change, subject to `checkContractCompatibility` and the `ArtifactReference` pairs of WireTokenParityTest :75-79 (one of the `ActivatableKind` inlined copies).
- The domain records also need widening: `EffectiveSkippedReport.EnhancerWithoutTarget(ArtifactRef, List<ArtifactRef>)` :94 and `MergedSnapshot.InForceAnswer.enhancedTargets` (`Map<ArtifactRef, List<ArtifactRef>>`, MergedSnapshot.java:237).
- The wire mapping is at `EffectiveReadMapper.java:166-172`.

## Unverified points and risks
- Nothing was built or run; generated-model presence is inferred from the contract references and the generator config (`build-logic/src/main/kotlin/charter.contract-api.gradle.kts`).
- #424's effective-prompt read has no contract home yet. Building it changes the contract and is an architect decision.
- The order in which ADR-0066 applies its version gate interacts with the five built-in prompts. Those prompts must stay warnings (unversioned descriptor), or `:packs:wholePackRoundTrip` and `openPacksRoundTrip` will refuse built-in.
- `Diagnostic` warnings have no wire surface, so `prompt_slot_not_empty` as a warning can be observed only in domain or validate tests.
