# Squad lens: similar surfaces (#554, ADR-0075 slice 5)

Read-only map of what exists today, as of `93f4b825`. Paths are under `service/` unless they say otherwise.
Short names: `kv` = `kernel/src/main/java/ai/speckitty/charter/kernel/validation`,
`pd` = `packs/src/main/java/ai/speckitty/charter/packs/domain`,
`ps` = `packs/src/main/java/ai/speckitty/charter/packs/infra/storage`,
`pa` = `packs/src/main/java/ai/speckitty/charter/packs/application`.

## 1. The kernel rule vocabulary, as merged

| Type | Where | What it gives |
|---|---|---|
| `Rule<T>` | `kv/Rule.java:20` | `apply(T) -> List<Reason>`; `check` -> `Rationale` (:37); `guard` throws on the first `VIOLATION` (:47); `on(focus)` (:62); `forEach(parts)` (:84); `when(applicable)` (:111); `locatedAt(where)` (:135) relocates failing reasons only. |
| `Reason` | `kv/Reason.java:16` | record `(rule, Evaluation result, ReasonCode code, Location location, detail)`; `severity()` is `code.severity()` (:69); `at(Location)` (:79); `isFailing(Severity)` (:107). `passed/failed` factories use `ReasonCode.INVARIANT`. |
| `ReasonCode` | `kv/ReasonCode.java:17` | record `(String value, Severity severity)`. **The code owns its severity.** `INVARIANT` = `("invariant", VIOLATION)` (:26); `of(value)` defaults to `VIOLATION` (:43). Equality is the pair, so two codes with one value and two severities are distinct. |
| `Severity` | `kv/Severity.java:7` | `VIOLATION, WARNING, NOTICE`. There is **no `ERROR`** and no `BLOCKING`. |
| `Evaluation` | `kv/Evaluation.java:4` | `PASS, FAIL`. |
| `Location` | `kv/Location.java:16` | record `(file, pointer)`; `NOWHERE`; `in(file)` (:43); `at(jsonPointer)` (:57); a pointer must start with `/` and needs a file. |
| `Constraint<T>` | `kv/Constraint.java:29` | a `Rule` from a predicate, with `code`, optional `location` and `detail` functions; `coded/locatedBy/detailedBy` (:81-101). One reason per subject, **a PASS reason too** (:61). |
| `ModularRuleset<T>` | `kv/ModularRuleset.java:14` | a `Rule`; `emptyRules()`, `of(list)`, `must(rule)`; `apply` concatenates in rule order. |
| `Rationale` | `kv/Rationale.java:12` | immutable; `isPassing` = no failing `VIOLATION` (:24); `failures/violations/warnings/notices`; `and(next)` (:79). `details` holds passes too. |
| `Constraints` | `kv/Constraints.java:6` | `of(description, predicate)`, `notBlank()`. |
| `Constrained<T>` | `kv/Constrained.java:10` | value + ruleset; `guard()`, `rationale()`. |
| `ConstraintBreachException` | `kv/ConstraintBreachException.java:6` | unchecked, carries the `Reason`. |
| Grammar rules | `kernel/.../kernel/domain/IdentifierGrammar.java:108` `slug(max)`, `:121` `letterFirstSlug(max)` | `Constraint<String>` factories (slice #552). |

Facts for the plan:
- There is no static `forEach` combinator: composition is `kindRuleset.forEach(PackContents::artifacts)` on the rule.
- No main code calls `check()` or reads a `Rationale` yet. #554 is its first consumer.
- A mapper must read `failures()`, never `details()`, because passes are in there.
- `PackFindingCode.Severity` (`pd/PackFindingCode.java:70`) is `WARNING, BLOCKING`. `BLOCKING` maps to `VIOLATION`. `NOTICE` is unused by packs today.

## 2. Every pack check today

### 2.1 Domain rules (`packs.domain`)

| Class | Lines | Role |
|---|--:|---|
| `PackPolicy` | 1,334 | validate-mode judge plus helpers the load borrows. |
| `PackFindingCode` | 128 | 19 codes, each with a fixed severity. |
| `PackFinding` | 59 | `(code, file, place, detail)`; `toRefusal()` (:46), `toDiagnostic()` (:56). |
| `PackVerdict` | 24 | `(files, findings)`. |
| `PackValidation` | 36 | `validate(root)` = `PackPolicy.judge(inspector.inspect(root, FULL))`. |
| `PackElementViolation` | 25 | `(file, String kind, reason)`, stored on the `Pack` aggregate (`pd/Pack.java:55, :343, :518`). |
| `StepEnhancementRules` | 225 | `judge(facts)` (:65); 13 `StepEnhancementFault` kinds. |
| `OverrideSourceRule` | 150 | `judge(edges)` (:90), `refusal(relationship)` (:110), `violationOf(fault)` (:128); `CODE = "overrides_source_not_activatable"` (:36). |
| `PackCompositionResolver` | 1,055 | closure checks; fail-fast `Refusal` exception. |
| `PerPackPolicy` | 77 | mutability level lookup by closure; **not a check**. |
| `PackInspector` | 24 | port: `inspect(root, Scope.LOAD_PATH or FULL)`. |
| `PackContents` | 556 | the facts the inspector reads (`:30`). |

`PackPolicy` rule methods (`pd/PackPolicy.java`):

| Rule group | Method | Codes |
|---|---|---|
| level | `level` :126, `declared` :142 | `PACK_LEVEL_MISSING` (W), `PACK_LEVEL_MISMATCH` |
| descriptor | `descriptorRules` :471, `descriptorFindings` :484 (public) | `REQUIRES_CORE_INVALID`, `PACK_VERSION_INVALID`, `DEPENDENCY_RANGE_INVALID` |
| org fragment | `orgFragmentRules` :430, `isOrgPack` :441 | `ORG_FRAGMENT_MISSING` |
| graph | `graph` :671, `registeredGraph` :684, `build` :688, edge/node helpers :745-946, `joinsItself` :924, `bodyPathEscape` :730 | `DRG_SCHEMA_ERROR` |
| unreadable / schema | `unreadable` :946, `schemaFindings` :991, `codeFor` :1041, detail vocabulary :1092-1228 | `PACK_UNREADABLE`, `DRG_SCHEMA_ERROR`, `ARTIFACT_SCHEMA_ERROR`, `PROMPT_FRAGMENT_INVALID` |
| step | `stepRules` :522 | `DUPLICATE_STEP_ID` |
| artifact | `artifactRules` :543 | `DUPLICATE_ARTIFACT_ID` |
| asset | `assetRules` :571, `mimeProblem` :598 | `ASSET_PATH_ESCAPE`, `ASSET_MIME_INVALID` |
| step enhancement | `stepEnhancementFaults` :220, `stepEnhancementFindings` :263, `stepEnhancementViolations` :276 | `PROMPT_FRAGMENT_INVALID` |
| prompt slot | `promptSlotFaults` :362, `promptSlotFindings` :387 | `PROMPT_SLOT_MALFORMED`, `PROMPT_SLOT_NOT_EMPTY`, `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` (W) |
| override source | `overrideSourceFindings` :308 | `OVERRIDES_SOURCE_NOT_ACTIVATABLE` |
| order (plain fn) | `PRECEDENCE` :109, `group` :609, `comparePaths` :1230, `comparePlaces` :1260 | — |
| entry | `judge` :171 | runs all of the above, then sorts by `PRECEDENCE` |

`PackFindingCode` (`pd/PackFindingCode.java:8-67`), 19 codes: `PACK_LEVEL_MISSING`(W), `PACK_LEVEL_MISMATCH`,
`PACK_UNREADABLE`, `REQUIRES_CORE_INVALID`, `PACK_VERSION_INVALID`, `DEPENDENCY_RANGE_INVALID`, `ORG_FRAGMENT_MISSING`,
`DRG_SCHEMA_ERROR`, `ARTIFACT_SCHEMA_ERROR`, `DUPLICATE_STEP_ID`, `DUPLICATE_ARTIFACT_ID`, `ASSET_PATH_ESCAPE`,
`ASSET_MIME_INVALID`, `PROMPT_FRAGMENT_INVALID`, `PROMPT_SLOT_MALFORMED`, `PROMPT_SLOT_NOT_EMPTY`,
`PROMPT_SLOT_NOT_EMPTY_UNVERSIONED`(W), `FILE_NOT_HELD`(W), `OVERRIDES_SOURCE_NOT_ACTIVATABLE`. Two projections:
`refusal()` :98 (5 codes fold into `pack_unreadable`) and `diagnostic()` :120 (3 warning codes to `Diagnostic.Code`,
`pd/Diagnostic.java:22-29`; `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` goes out as `prompt_slot_not_empty`).

`PackCompositionResolver.Failure` (`pd/PackCompositionResolver.java:151-173`), 11 kinds, each a `RefusalCode`:
`CYCLE`, `UNKNOWN_DEPENDENCY`, `OUT_OF_RANGE`, `PARENT_UNRESOLVED`, `LEVEL_VIOLATION`, `NAME_CONFLICT`,
`DUPLICATE_ID`, `SIBLING_CONFLICT` (`CLOSURE` scope); `REFERENCE_UNQUALIFIED`, `REFERENCE_PACK_MISSING`,
`REFERENCE_ARTIFACT_MISSING` (graph-dependent, #556 `ClosureWalk`). Throw sites: `indexById` :827, `indexByName` :842,
`refuseDisjointRanges` :576, `dependenciesOf` :861-893, `requireLevel` :914, `topologicalOrder` :977,
`refuseSiblingContests` :628/696, `refusePresetContests` :718 (preset = `SIBLING_CONFLICT`), `refuseCaseOnlyMatches`
:761, `checkReferences` :468. Every one **throws the first fault**. `Refusal.toCharterRefusal()` :254 is the only
projection. `resolve` :279 asserts `PackPolicy.descriptorFindings` is empty first (a descriptor check reused as a
precondition). Callers: `pa/OfferingReadService.java:359-377` (resolveRooted, refusePresetContests,
refuseCaseOnlyMatches), `:496-524` (checkReferences), `pa/EdgeIndex.java:547-553`,
`pa/CompositeComponentResolver.java:112-114`.

### 2.2 Converters (all go, per decision 4)

| Converter | Where | From -> to | Callers |
|---|---|---|---|
| `PackFinding.toRefusal()` | `pd/PackFinding.java:46` | finding -> `CharterRefusal` | **tests only** (`Judged.java:19`, `PackFindingTest`) |
| `PackFinding.toDiagnostic()` | `pd/PackFinding.java:56` | finding -> `Diagnostic` | `pa/PackRegistrationService.java:655` |
| `PackFindingCode.refusal()` / `diagnostic()` | `pd/PackFindingCode.java:98, :120` | code -> wire code | the two above |
| `PackPolicy.findingOf(packName, StepEnhancementFault.Finding)` | :235 | fault -> finding | `stepEnhancementFindings` |
| `PackPolicy.violationOf(StepEnhancementFault.Finding)` | :250 | fault -> `PackElementViolation` | `ps/DomainRepositoryService.java:965`, `stepEnhancementViolations` (`ps/WholePackStore.java:451`) |
| `PackPolicy.findingOf(file, PromptSlotFault)` | :406 | slot fault -> finding | `promptSlotFindings`; load warnings `ps/DomainRepositoryService.java:993` |
| `PackPolicy.violationOf(file, PromptSlotFault)` | :419 | slot fault -> `PackElementViolation` | `ps/DomainRepositoryService.java:991` |
| `PackPolicy.violationKind(PackFindingCode)` | :290 | finding code -> string kind | `:252, :420`, `ps/DomainRepositoryService.java:1058` |
| `OverrideSourceRule.violationOf(Fault)` | `pd/OverrideSourceRule.java:128` | fault -> `PackElementViolation` | `ps/FilesystemPackStore.java:434` |
| `WholePackStore.violationOf(pointer, code, detail)` | `ps/WholePackStore.java:755` | kind -> `RefusalViolation`; a non-snake kind becomes `"invalid"` | `loadStaged` :366-395, `graphViolations` :747, `loadRefusingAsInvalid` :739, `reservedShard` :762 |
| `WholePackStore.graphViolations` | `ps/WholePackStore.java:747` | `PackElementViolation` -> `drg_schema_error` `RefusalViolation` | `loadStaged`, `loadRefusingAsInvalid` |
| `DomainRepositoryService.judged` | `ps/DomainRepositoryService.java:1050` | remaps `companion` kind on enhancements to `prompt_fragment_invalid` | `assembly` :963 |
| `FilesystemPackStore.violationsOf(Unreadable)` | `ps/FilesystemPackStore.java:1380` | unreadable -> `unreadable`/`schema` violations | store and `WholePackStore.manifest` :849 |
| `PackCompositionResolver.Refusal.toCharterRefusal()` | `pd/PackCompositionResolver.java:254` | closure refusal -> `CharterRefusal` | 3 application callers above |
| `WholePackWriteStore.ExcludedFile.notHeld()` | `pa/WholePackWriteStore.java:49` | excluded file -> `FILE_NOT_HELD` finding | `ps/WholePackStore.java:796` |
| `EnhancementCompose…toCharterRefusal()` | `pd/EnhancementCompose.java:116` | compose fault -> refusal | not a pack check; leave |

### 2.3 Load-only checks and the untyped sub-codes

| Check | Where | Wire code today |
|---|---|---|
| unmodelled file (not modelled, not excluded) | `ps/WholePackStore.java:931` (list), `:372-381` (refuse) | violation `unmodelled` |
| excluded file (exclusion rule or `ignored` fallback) | `ps/WholePackStore.java:955-956`, `:796` | diagnostic `file_not_held` (W) |
| orphan fragment | `ps/WholePackStore.java:440` (via `PackPolicy.stepEnhancementViolations`) | violation `prompt_fragment_invalid` |
| already-reported file filter | `ps/WholePackStore.java:416` `unreportedFiles` | — (dedup rule) |
| reserved shard (`registered.graph.yaml` without stamp) | `ps/WholePackStore.java:762` | violation `reserved_shard`, always last |
| manifest not a regular file | `ps/WholePackStore.java:385-389` | violation `unreadable` |
| manifest membership | `ps/WholePackStore.java:390-396`, `ps/ManifestMembership.java:131` `mismatch`, `:46` `VIOLATION_CODE` | violation `manifest_membership`, **only when no other violation** |
| misplaced preset entry | `ps/WholePackStore.java:845` | `schema` |
| named policy file unreadable | `ps/WholePackStore.java:864` | `unreadable` |
| aggregate `build()` breach | `ps/WholePackStore.java:299` | refusal `validation_failed`, then violation at `pack` with that token (`:739`) |

String sub-codes written into `PackElementViolation.kind` (`new PackElementViolation(` sites: 12 in
`FilesystemPackStore`, 7 in `DomainRepositoryService`, 2 in `WholePackStore`, 2 in `PackPolicy`, 1 in
`OverrideSourceRule`):

| Token | Sites |
|---|---|
| `domain` | `ps/FilesystemPackStore.java:405`; `ps/DomainRepositoryService.java:893, :912, :921, :1116` |
| `schema` | `ps/FilesystemPackStore.java:469, :768, :1387`; `ps/WholePackStore.java:845` |
| `reference` | `ps/FilesystemPackStore.java:774`; `ps/DomainRepositoryService.java:1103, :1108` |
| `unreadable` | `ps/FilesystemPackStore.java:552, :1383`; `ps/WholePackStore.java:864` (+ `:388`) |
| `retired` | `ps/FilesystemPackStore.java:538` |
| `companion` | `ps/FilesystemPackStore.java:261` `COMPANION_KIND`, `:893` |
| artifact kind tokens (`asset`, `template`) | `ps/FilesystemPackStore.java:498-499, :520-521` |
| `drg_schema_error` | `ps/FilesystemPackStore.java:729` |
| `unmodelled`, `reserved_shard`, `manifest_membership`, `invalid` | `ps/WholePackStore.java:379, :777, ManifestMembership:46, :758` |

These tokens are **on the wire**: the contract scenarios pin `/problem/violations/N/code` to `schema` (3), `retired`
(3), `unmodelled` (2), `manifest_membership` (1), `asset` (1). "Typed codes" must keep each token byte for byte.

## 3. The two pipelines, end to end

Validate mode:
1. `deployable/.../CharterWiring.java:87-88` builds `new PackValidation(new FileSystemPackInspector())`. No main code
   injects it (only `CharterWiringTest` and packs tests).
2. `PackValidation.validate` -> `FileSystemPackInspector.inspect(root, FULL)` (`ps/FileSystemPackInspector.java:152`,
   903 lines) -> `PackContents`.
3. `PackPolicy.judge` (`pd/PackPolicy.java:171`) -> `PackVerdict`, sorted by `PRECEDENCE`.
4. `pack-validation/.../PackLoad.java` reads the same port (`contents(root)`), and judges nothing yet.

Load and registration:
1. `pa/PackRegistrationService.register` :257 -> `loader.loadStaged(staged)` :265 (port `pa/WholePackWriteStore.java:140`,
   in **packs.application**, not packs.domain). Also `pa/PackSourceStagers.java:73`.
2. `ps/WholePackStore.loadStaged` :360 -> `loadRefusingAsInvalid` :732 -> `load` :272:
   `FilesystemPackStore.read` (`ps/FilesystemPackStore.java:354-391`) and `readGraphs` (:687) ->
   `DomainRepositoryService.assembly` (`ps/DomainRepositoryService.java:955`, which runs step-enhancement and slot rules
   through `PackPolicy`) -> `Pack.Builder.build()`.
3. `loadStaged` collects `RefusalViolation`s in a fixed order: graph faults in path order, the pack's own violations in
   read order, unmodelled and orphan files in path order, the reserved shard, the manifest file check, then manifest
   membership only if nothing else. It throws `CharterRefusal.withViolations(VALIDATION_FAILED, ...)` (:398).
4. Warnings (`PackFinding`s) ride on `Contents` and become `Diagnostic`s at `pa/PackRegistrationService.java:655`.
5. `web-transport/.../RefusalHandler.java:233-260` turns `CharterRefusal` into the API problem
   (`charter.api.model.RefusalViolation`; the contract enum is `CharterRefusalCode`, mirrored by
   `kernel/.../RefusalCode.java`, 296 lines).

A third path, the compose read: `ps/DomainRepositoryService.java:790` `readable` refuses the **first** pack violation
as `pack_unreadable`; `:469, :531` refuse any graph fault the same way; `WholePackStore.load` :287-291 refuses the
first graph fault as `drg_schema_error`. Offering: `pa/OfferingReadService.java:540` takes the first
`descriptorFindings` as `pack_unreadable`.

Divergences today:

| Check | Validate | Load / registration |
|---|---|---|
| level missing / mismatch | yes | no (`PACK_LEVEL_MISMATCH` refusal is never thrown by main code) |
| org fragment missing | yes | no |
| descriptor PEP 440 | yes | offering and resolver only, first finding |
| duplicate step id / artifact id | yes | no rule; the aggregate guard (`Pack.guardKind`) instead |
| asset escape / mime | yes | sidecar rule `SidecarPayloads` (kind `asset`), different code |
| unmodelled | silently skipped (`ps/FileSystemPackInspector.java:367`) | `unmodelled` violation |
| excluded -> `file_not_held` | lists the file, no finding | warning |
| manifest membership | no | yes |
| reserved shard | `drg_schema_error` with its own text (`ps/FileSystemPackInspector.java:875`) | `reserved_shard` |
| orphan fragment | yes, via `StepEnhancementFacts` | yes, from the unmodelled list |
| edges-read gate for enhancement rules | `graphsRead` | `graphs.violations().isEmpty()` |
| order | `PRECEDENCE` (group, path, place) | the fixed sequence above |

## 4. pack-validation today, and its fences

- Contents: `PackLoad` (38 lines) and `package-info`; test `PackLoadTest` (3 tests). `pack-check` holds only
  `package-info`.
- `pack-validation/build.gradle.kts`: `ModuleDependencyAllowlist(projects = :kernel, :profiles, :missions, :packs; modules
  = snakeyaml-engine, json-schema-validator, jackson-databind, re2j, spring-boot-dependencies BOM)`. The class is
  `build-logic/src/main/kotlin/ModuleDependencyAllowlist.kt:20`.
- `deployable/build.gradle.kts:24` has `:pack-validation` as `testImplementation` only. Wiring `PACK` into the
  registration load needs it on `implementation`.
- ArchUnit, all in `deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`:
  - `ONION_*` :96-134 (`DOMAIN_RINGS` :80). `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` :112 forbids `packvalidation`
    from naming `packs.infra.storage`.
  - `OUTSIDE_THE_DOMAIN` :292 allows `packvalidation`, `packcheck`, `kernel`, `profiles.domain`, `missions.domain`,
    `packs.domain`.
  - `PACK_VALIDATION_IS_FRAMEWORK_FREE`: direct :330, transitive :373 (`REACHES_NO_FRAMEWORK` :344),
    `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` :387 (walks from `FileSystemPackInspector`); test :408; bite test :417 with
    `deployable/src/test/java/ai/speckitty/charter/packvalidation/badfixture/`.
- Mutation floor: `pack-validation/build.gradle.kts` `mutationFloors { group("pack-validation") { floor = 85 } }`;
  documented in `docs/adr/0062-nightly-mutation-floor.md:105` (table row, margin 0) and the #553 amendment
  (`:349`). Margin logic: `build-logic/src/main/kotlin/MutationFloors.kt:41, :137-144`. The `packs`
  `domain+application` group (floor 85, margin 126) mutates `packs.domain` too, so deleting `PackPolicy` shrinks
  that group's mutant count; moving rules adds mutants to `pack-validation`, where one survivor today fails.

## 5. Tests that pin codes and order, and tests #554 would collapse

Pinning wire codes and order (keep green, unchanged):

| Test | Tests | Pins |
|---|--:|---|
| `contracts/charter/scenarios/registration/whole-pack-registration.feature` | — | `unmodelled`, `retired`, `manifest_membership`, `file_not_held` x4, indexed order |
| `contracts/charter/scenarios/prompt-fragment-refusals.feature` | — | `prompt_fragment_invalid`, `schema`, order |
| all scenarios | 67 files | 63 `violations/N/code` assertions, 6 `diagnostics/N/code` |
| `packs/.../infra/storage/VocabularyEqualityTest` | 9 | every load-path wire code is in the contract enums |
| `packs/.../infra/api/WireTokenParityTest` | 6 | wire tokens |
| `deployable/.../ReservedRefusalCodesTest` | 10 | reserved codes |
| `web-transport/.../RefusalHandlerTest` | 20 | every code has a status |
| `packs/.../infra/storage/GlossaryPlantedRefusalsTest` | 5 | runs the 10 `planted/glossary-*` `expected.json` |
| `packs/src/test/resources/planted/*/expected.json` | 72 dirs | validate findings with codes; only the 10 glossary ones run in Java; the rest feed `scripts/packs/core_verdicts.py` |
| `ExcludedFilesAgreementTest` | 6 | classification, validate, load, census agree on excluded files |

Tests #554's "one per rule, one per mapper" would collapse or replace (counts are `@Test`/`@ParameterizedTest`):

| Test class | Tests | Lines | Fate |
|---|--:|--:|---|
| `PackPolicyRulesTest` | 43 | 748 | collapse to per-rule tests |
| `PackPolicyVocabularyTest` | 49 | 550 | keep for the detail vocabulary (plain fn), trim |
| `PackPolicyGraphTest` | 29 | 433 | graph ruleset |
| `PackPolicyOrderTest` | 22 | 428 | precedence stays a plain fn: keep, trim |
| `PackPolicyTest` | 18 | 363 | collapse |
| `PackPolicyStepEnhancementTest` | 5 | 265 | collapse |
| `PackFindingTest` | 8 | 111 | delete with the converters; becomes the mapper test |
| `PackValidationTest` | 5 | 174 | one pipeline test |
| `StepEnhancementRulesTest` | 34 | 536 | fragment/step-enhancement ruleset |
| `OverrideSourceRuleTest` | 12 | 194 | graph ruleset |
| `PackCompositionResolverTest` | 56 | 1,008 | `CLOSURE` part moves; reference part stays (#556) |
| `FileSystemPackInspectorTest` | 58 | 1,419 | inspector stays; its rule assertions move |
| `WholePackStoreTest` | 42 | 1,193 | load-only checks become rules |
| `DomainRepositoryServiceTest` | 37 | 942 | assembly loses the rule calls |
| `StepEnhancementLoadChecksTest` | 25 | 681 | parity by hand; collapse |
| `PromptSlotLoadChecksTest` | 18 | 507 | parity by hand; collapse |
| `ManifestMembershipTest` | 13 | 390 | set difference stays a plain fn |
| `Judged` (helper) | 0 | 35 | delete (uses `toRefusal`) |
| total | 474 | ~10,000 | |

Kernel vocabulary tests exist: `kernel/src/test/.../kernel/validation/{Rule,ModularRuleset,Rationale,Location,Constrained}Test`.

## 6. Sibling surfaces

| Surface | Where | Similar how | Scope |
|---|---|---|---|
| `GuardClosure` | `ps/GuardClosure.java:200` | catches `ConstraintBreachException` into faults | #556 (closure of guards) / leave |
| `GuideMarkdown` | `ps/GuideMarkdown.java:158, :167` | catches breaches into faults | leave; a codec, not a pack rule |
| `FilesystemPackStore`, `DomainRepositoryService` breach catches | `ps/FilesystemPackStore.java:404`; `ps/DomainRepositoryService.java:892, :910, :919, :1114, :1217` | catch breach -> `domain` violation | #554 (typed `domain` code; the breach already carries a `Reason`) |
| registration/controller breach catches | `pa/{Directive,Paradigm,Styleguide,Toolguide}RegistrationService`, 5 controllers, `ArtifactReadService:767` | single-artifact writes | OUT |
| `FallbackProfileRule.Breach` | `pa/FallbackProfileRule.java:27` | `(RefusalCode, detail)`, first fault | #556 (`ClosureWalk`) |
| `RegistrationImpact` / `ImpactRelations` | `pa/RegistrationImpact.java:217-500`; `pa/ImpactRelations.java:155-314` (`Violation`) | closure difference, first fault | #556 (`ImpactCheck`) |
| `PackMutabilityPolicy` | `pa/PackMutabilityPolicy.java:66, :216` | `governance_policy_violation` | #556 (`MutabilityCheck`) |
| `PerPackPolicy` | `pd/PerPackPolicy.java` | lookup only | OUT |
| resolver reference checks | `pd/PackCompositionResolver.java:468-540, :761` | qualified references | #556 |
| `RelationshipRegistrationService` | `pa/RelationshipRegistrationService.java:139-143` | reuses `OverrideSourceRule.refusal` at `/from` | reuse the graph rule's code; stays a single-relationship write |
| `ArchiveStaging`, `InboundReferences`, `LocalSourceReader` | `pa/ArchiveStaging.java:58`, `pa/InboundReferences.java:103` | build `RefusalViolation`s | OUT |
| activations `DocumentRules` etc. | `activations/.../domain/DocumentRules.java:266-303` | own `RefusalViolation`s | OUT |
| pack-id regex | `pa/PackRegistrationService.java:202`, `RegistrationAdmission` | duplicate grammar | #552, not #554 |
| `WholePackDrift` / round trip | test source set | — | #555 |

## Findings

1. **BLOCKER** — One `PACK` ruleset gives one order, but the two pipelines order differently today (`PRECEDENCE` vs
   the `loadStaged` sequence), and "order must not change". Disposition: IN scope. Ordering stays a plain function per
   mapper: `Rationale -> PackVerdict` sorts by `PRECEDENCE`; `Rationale -> problem` keeps the registration sequence.
   Say so in the spec.
2. **BLOCKER** — Decision 5 says an unmodelled file is `file_not_held` at `WARNING`, but registration refuses it today
   as the `unmodelled` violation (pinned by `whole-pack-registration.feature:533, :574`), while `file_not_held` is the
   excluded-file warning. Disposition: IN scope, needs an operator ruling: keep `unmodelled` (VIOLATION) and
   `file_not_held` (WARNING) as two typed codes, or change the wire. The wire rule says keep both.
3. **MAJOR** — Issue #554 says "delete `FileSystemPackInspector`"; the amendment and the 2026-11-15 ruling keep it.
   Disposition: IN scope; the spec cites the ruling, the issue text is stale.
4. **MAJOR** — The registration port `WholePackWriteStore` is in `packs.application`; the ruling wants a port that
   `packs.domain` declares and `deployable` wires, because `:packs` cannot depend on `:pack-validation` (cycle) and
   `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` forbids the reverse reach. `deployable` has `:pack-validation` as test
   only. Disposition: IN scope (new `packs.domain` port, e.g. a `PackRules`/`PackJudge`, plus `implementation` dep).
5. **MAJOR** — The string sub-codes are wire tokens (`schema`, `retired`, `unmodelled`, `manifest_membership`,
   `asset`, `reserved_shard`, `domain`, `reference`, `unreadable`, `companion`) plus the `invalid` fallback at
   `WholePackStore:758`. Disposition: IN scope; one typed `ReasonCode` constant per token, same value, and drop the
   `invalid` fallback once every code is typed.
6. **MAJOR** — Divergent checks: validate runs level, org fragment, duplicate ids, asset rules that the load never
   runs; the load runs unmodelled, manifest membership, reserved shard that validate never runs; the reserved shard is
   `drg_schema_error` in validate and `reserved_shard` at registration. Running one ruleset on both changes what the
   registration answers (e.g. a level mismatch would start refusing). Disposition: IN scope; the spec must list, per
   check, which wire answer each path keeps, and get a ruling where a new refusal appears at registration.
7. **MAJOR** — `CLOSURE` today is fail-fast (`throw new Refusal`) and names packs (`RefusalPack`); the offering
   answers one refusal with `packs`. A collecting ruleset must still give the offering the same first refusal and
   pack list. Disposition: IN scope; the offering maps the first `CLOSURE` violation; the reference checks stay in the
   resolver for #556.
8. **MEDIUM** — `ReasonCode` severity is per code. `PROMPT_SLOT_NOT_EMPTY` (VIOLATION) and
   `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` (WARNING) both go out as `prompt_slot_not_empty`. Disposition: IN scope; two
   `ReasonCode`s, the mapper keeps the diagnostic token.
9. **MEDIUM** — Kernel `Rationale.details` holds PASS reasons; `Constraint` emits one per subject. A mapper reading
   `details` would emit passes. Disposition: IN scope; mappers read `failures()`, one mapper test pins it.
10. **MEDIUM** — Mutation margin 0 on `pack-validation`, and `packs` `domain+application` loses `PackPolicy`'s mutants.
    Disposition: SEQUENCE; land per-rule tests with each ruleset, re-measure, amend the ADR-0062 table after.
11. **MEDIUM** — Only 10 of 72 planted packs' `expected.json` run in Java; the other 62 feed only `core_verdicts.py`.
    They are the cheapest per-rule fixtures. Disposition: IN scope as reuse (one parameterised test over all plants),
    or OUT with a note.
12. **MEDIUM** — `PackFinding.toRefusal()` and `PackFindingCode.refusal()` have no main caller; the
    `PACK_LEVEL_MISMATCH` refusal is never thrown in main. Disposition: IN scope; delete, do not port.
13. **MINOR** — `ps/DomainRepositoryService.java:1116` records a `domain` violation with an artifact id in the file
    field. Disposition: IN scope while typing the code (use `stored.file()`), or fold.
14. **MINOR** — `GuardClosure`, `GuideMarkdown`, `FallbackProfileRule.Breach`, `ImpactRelations.Violation`,
    `PackMutabilityPolicy` look like collectors but are closure or codec checks. Disposition: #556 / OUT; do not
    duplicate them into `PACK`.
15. **MINOR** — The pack-id regex at `PackRegistrationService.java:202` is still the old one. Disposition: OUT (#552).
