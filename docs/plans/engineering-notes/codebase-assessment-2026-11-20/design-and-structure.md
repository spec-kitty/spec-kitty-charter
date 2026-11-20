---
title: "Design and structure, 2026-11-20"
description: Under-designed and clunky surfaces in the charter's main code at 25e24c37, from god classes and text-matched fault identity to hexagonal gate gaps.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the large classes, duplicated concepts and hexagonal gaps, each with its file and line and a suggested direction.
---

# Design and structure, 2026-11-20

Lens L2 of the [codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/), pinned
at `25e24c37`. It looks for under-designed and clunky surfaces: large classes, long parameter lists, duplicated
concepts, hexagonal gaps and javadoc quality. It changes no code.

Read-only assessment of the repository at **25e24c372bae838d95a9328be57ee2063f6c05d1**.
Every `file:line` below is at that commit. Paths are under `service/<module>/src/main/java/ai/speckitty/charter/`
unless stated otherwise. The scripts are in `scripts/analysis/design/`; see [Scripts and outputs](#8-scripts-and-outputs).

## 0. Correctness, data or security defects

**None found.** Two places come closest, and both work as their javadoc describes:

- `PackCommitStore.requireNoNewViolation` (`packs/infra/storage/PackCommitStore.java:419-489`) treats a fault in the
  edited copy as already held when the live pack has a fault with the same code in the same file, first by wording,
  then by `(code, file)` alone. So a per-file write that fixes fault A and adds a different fault B, with the same code
  in the same file, is accepted. The javadoc (`:419-421`) says "no per-file path can store a pack that a whole-pack
  registration of it would refuse". That is true only for a live pack with no violations. The trade-off is documented,
  so this is a misleading claim and a design weakness (F1), not a bug.
- `AssemblyRules.sameFault` matches faults by suffix text (F1). If it matched wrongly, it would only drop a
  *duplicate* store fault when the reader already states a fault with the same code in the same file, so the verdict
  (pass or fail) is unchanged. Only the detail could be lost.

## 1. Method and toolchain notes

- **Lizard 1.17.13 does not understand Java records.** It reports a whole `record` body as a single function. For
  example, `ClosureView` comes out as one 1,091-line function with 12 parameters, and none of its 26 methods appear.
  `derecord.py` rewrites each record header to `class X` (line numbers kept, compact constructors given `()`) before
  lizard runs. All method numbers below come from that corrected run (`lizard-main-derec.csv`). The raw run
  (`lizard-main.csv`, over the unmodified sources) shows the problem.
- **The [ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) complexity gate is now on** (`config/checkstyle/checkstyle.xml:74-86`: CyclomaticComplexity 15,
  NPath 1000, MethodLength 60, NestedIfDepth 1; `suppressions.xml` has 0 entries; commit `6451dc59`, #371). The
  11-12 hotspots were long *methods*. The debt has moved into *class* size, parameter lists and duplicated concepts,
  which no gate measures (there is no FileLength, ParameterNumber or ClassFanOutComplexity module).
- Main code: 948 files, 121,302 physical lines, about 69,165 code lines and 41,906 comment lines
  (comment-to-code ratio 0.61, `filestats.py`).

Reproduce:

Reproduce, from the repository root (outputs go to `build/analysis/design/` and are not committed):

```bash
D=scripts/analysis/design
OUT=build/analysis/design
mkdir -p "$OUT"
find service -path '*/src/main/java/*.java' -not -path '*/build/*' > "$OUT/main-files.txt"
python3 -I "$D/derecord.py" . "$OUT/main-files.txt" "$OUT/derec"
(cd "$OUT/derec" && uvx --from lizard==1.17.13 lizard -l java --csv $(cat ../main-files.txt) > ../lizard-main-derec.csv)
python3 -I "$D/lizard_report.py" "$OUT/lizard-main-derec.csv" > "$OUT/lizard-report.txt"  # long/complex methods, params>=6, per-file CCN
python3 -I "$D/methods_of.py" "$OUT/lizard-main-derec.csv" ClosureView.java   # methods of one class, in line order
python3 -I "$D/filestats.py" . "$OUT/main-files.txt" 750 > "$OUT/filestats.md"  # class-size table
STRIP_COMMENTS=1 python3 -I "$D/import_graph.py" service > "$OUT/import-graph-code-only.txt"  # package/module graph, cycles
python3 -I "$D/import_graph.py" service > "$OUT/import-graph.txt"                             # same, javadoc refs included
python3 -I "$D/test_only_public.py" service > "$OUT/test-only-public.txt"  # heuristic: test-only publics (slow, ~3 min)
python3 -I "$D/sample_javadoc.py" "$OUT/main-files.txt" . 22 1112 > "$OUT/javadoc-sample.txt"
```

## 2. Inventory

### 2.1 Main classes over about 750 lines (`wc -l`; Code and Comment from `filestats.py`; function totals from lizard, corrected for records)

| File (module) | Lines | Code | Comment | Fns | Sum CCN | Max CCN | Nested types | Constructors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| packs/infra/storage/FilesystemPackStore (packs) | 1,627 | 946 | 587 | 73 | 219 | 12 | 7 | 5 |
| packs/domain/PackCompositionResolver (packs) | 1,480 | 941 | 452 | 62 | 195 | 12 | 12 | 1 |
| packs/infra/storage/FileSystemPackInspector (packs) | 1,438 | 987 | 365 | 67 | 295 | 19* | 4 | 5 |
| activations/application/EffectiveReadService (activations) | 1,435 | 781 | 583 | 42 | 88 | 6 | 16 (14 public records) | 1 |
| packs/application/RegistrationJobs (packs) | 1,212 | 744 | 353 | 62 | 145 | 10 | 19 | 1 |
| packs/application/ClosureView (packs, a 12-component record) | 1,171 | 574 | 526 | 53 | n/a | n/a | 9 | 3 + canonical |
| packs/infra/storage/PackCommitStore (packs) | 1,099 | 710 | 308 | 60 | 139 | 9 | 8 | 4 |
| packs/infra/storage/DomainRoundTrip (packs) | 912 | 664 | 184 | 41 | 166 | 14 | 3 | 3 |
| packs/application/ActionContextWalk (packs) | 901 | 499 | 347 | 35 | 87 | 11 | 10 | |
| packs/infra/storage/WholePackStore (packs) | 856 | 573 | 232 | 42 | 121 | 10 | 3 | 2 |
| packs/application/PackRegistrationService (packs) | 834 | 521 | 250 | 44 | 88 | 7 | 9 | |
| profiles/domain/AgentProfile (profiles) | 815 | 392 | 345 | 67 | 75 | 5 | 2 | |
| activations/infra/packs/ClosureViewSession (activations) | 779 | 542 | 171 | 60 | 96 | 7 | 1 | |
| packvalidation/roundtrip/WholePackExpectation (pack-validation) | 761 | 495 | 214 | 38 | 114 | 19* | 9 | |
| packs/infra/storage/DomainRepositoryService (packs; candidate) | 680 | | | 55 | | | 0 | 1 (implements 14 ports) |

\* Lizard's CCN. It counts pattern-matching `instanceof` and `&&` chains more heavily than Checkstyle does, and
Checkstyle passes these methods at max 15.

Compared with the 11-12 Sonar "lines of code" (a similar but not identical measure): FilesystemPackStore 799 → about
946, FileSystemPackInspector 591 → about 987 (it is now *the* one reader), WholePackStore 742 → about 573, and
PackCompositionResolver 670 → about 941. `PackPolicy` (889) is gone.

### 2.2 Methods over 60 lines (corrected lizard run): two, both at the gate's edge

| Method | Length | NLOC | CCN | Params |
|---|---:|---:|---:|---:|
| `StoreRead.read` (`packs/infra/storage/StoreRead.java:158`) | 62 | 62 | 3 | 6 |
| `DomainRoundTrip.roundTrip` (`packs/infra/storage/DomainRoundTrip.java:184`) | 62 | 56 | 10 | 3 |

Methods with lizard CCN ≥ 15 (each below Checkstyle's 15 as Checkstyle counts):
`MeaningComparator.differences` (`pack-validation/.../roundtrip/MeaningComparator.java:155`, 27),
`ParsedYaml.problem` (`:131`, 20), `FileSystemPackInspector.parseAndValidate` (`:1162`, 19),
`WholePackExpectation.entries` (`:711`, 19), `GraphDocumentReader.raw` and `siblingFacts` (`:62`, `:153`, 16),
`MeaningComparator.value` (`:347`, 15), `StyleguideYaml.read` (15), `LifecycleYaml.read` (15),
`PresetMapper.setterOf` (15), and `FileSystemPackInspector.FullRead.routingCatalogFactsOf` (`:1069`, 15).

### 2.3 Long parameter lists (≥ 9; constructors and records included)

| Where | Params | Note |
|---|---:|---|
| `AgentProfileDso` record | 26 | DSO that mirrors YAML; acceptable |
| `PackContents.Assembly.Assembled` (`packs/domain/PackContents.java:192-208`) | 16 | data clump: `int assembled` is an index into `violations`, `Optional<String>` faults, and `PackElementViolation` and `Reason` lists side by side |
| `SkillDso` 16, `DomainRoundTrip.Rewrite` 15, `SkillView` 14 | | |
| `ClosureView` record (`packs/application/ClosureView.java:81-93`) | 12 | plus three telescoping constructors (11, 10 and 9 parameters, `:468-560`) that only tests call |
| `ArtifactReadService(...)` (`packs/application/ArtifactReadService.java:360`) | 12 | takes all six per-kind read services (F4) |
| `StyleguideController.assemble` (`packs/infra/api/StyleguideController.java:126`) | 12 | 7 are `String` or nullable `String` (primitive obsession) |
| `ActivationController(...)` (`activations/infra/api/ActivationController.java:109`) | 11 | 4 services, 4 mappers and 2 tension collaborators |
| `PackCompositionFacts` (`packs/domain/PackCompositionFacts.java:42`) | 10 | **7 constructors** (10 down to 3 parameters). Main code calls only the canonical one (5 sites); tests call the others 40 times |
| `RegistrationJobs.Dependencies` | 7 | includes `JobGate`, a test seam (F3) |
| `PackRegistrationController` (`packs/infra/api/PackRegistrationController.java:83`) | 7 | was 6 on 11-12. Two of its collaborators are other controllers (`PackRemovalController`, `PackDocumentController`) |

Boolean flag parameters are rare (a handful, for example `TensionFinding.of(…, boolean, boolean)` at
`activations/domain/TensionFinding.java:70` and `StepEnhancementFacts.Companion.read(path, boolean, boolean, text)` at
`packs/domain/StepEnhancementFacts.java:167`). They are not a theme.

## 3. Candidates, verified

| Class | Responsibilities found | Cohesion | Smells, with evidence |
|---|---|---|---|
| **FilesystemPackStore** | five readers (kinds, presets, templates, graphs, descriptor), atomic writes (`writeAtomically :1578`), companion planning (`planCompanions`, 52 NLOC, 7 params; `withCompanions`, 8 params), containment (`realPathContained`, `namedFileProblem`, `bodyPathEscapes`), UTF-8 decoding, fault wording (`violationsOf`, `reasonOf`, `schemaReason :1522-1570`) and two global counters (`:1336-1360`) | low: four concerns | 5 telescoping constructors for test seams (`:289-343`); the `Reason`→`PackElementViolation` conversion strips a `"<file>: "` *text* prefix (`reasonOf :1534-1537`); `schemaReason` still produces the registration's `keyword violated at /x` form, which R2 retires, through `Companions.java:332` |
| **DomainRepositoryService** | implements **14 ports** (`:63-77`): 6 per-kind repositories, PackArtifact, Edge, Catalog, Descriptor, Pack, MutabilityPolicy, CompanionFiles and Document | low: a god adapter | `MutabilityPolicyRepository` is implemented only for a test seam (F6); each per-kind repository has one caller (F4) |
| **EffectiveReadService** | 13 public queries over activation, list, skipped, graph, action context, prompt, profile selection (2), model recommendation, step offer, artifact, held winners and glossary term; 14 public nested records serve as the edge contract | medium | four edges each use 1 to 3 methods (routing-edge uses `modelRecommendation` and `stepOffer`; profiles-edge uses `profileSelection` and `stepProfileSelection`): an interface-segregation problem and a shotgun-surgery hub (F5) |
| **WholePackStore** | strict load, staged load, manifest stamping, **a third, lenient "salvage" reader** (`salvageStored :410-430`, ADR-0065), and writes | medium | `Salvage` is a mutable accumulator class (`:390-398`); a third pack-tree reader (F2) |
| **RegistrationJobs** | job queue and slots, upload sessions (`OpenUpload` claim/adopt/discard), watchdog and expiry, admission and eviction, status retention, close and audit; 19 nested types | low: three lifecycles in one class (upload, job, retention) | `JobGate` is a test seam, but deployable wires it as a production bean (`deployable/RegistrationWiring.java:83-84`); `retained()` (`:810`) is test-only |
| **ClosureView** | merge inputs, effective prompt, merged graph, tensions, action context, governance, profile chain, step place, role and model tier, held winners, presets and locks: 26 public methods on a 12-component record | low | three telescoping constructors for tests; the 11-argument one sets `closureFingerprint = fingerprint` (`:468-488`), a default that is wrong for any closure smaller than the configured set; `fingerprint` and `closureFingerprint` are both `String` (swappable) |
| **PackCompositionResolver** (domain) | resolve, compose, reference and qualified-reference checks, delegations, sibling and preset contests, name conflicts, dependencies, levels, topological order and cycles, labels | low | its own fault vocabulary (`Refusal`, `Failure :166`), bridged to kernel `ReasonCode` by **token string equality** in `CompositionRules.codeOf` (`packs/domain/rules/CompositionRules.java:156-162`); package cycle with `packs.domain.rules` (F7) |
| **PackCommitStore** | transactional directory swap (aside, live, hide phases), version history, locks, interrupted-swap healing, copy and move of trees, **and a full revalidation of the staged copy on every per-file write, plus the live pack when the copy has a violation** (`requireNoNewViolation :429-474`, which constructs `new FileSystemPackInspector(strategy)` inline) | low | fault identity by wording, then by place (F1); persistence mixed with validation policy |
| **PackPolicy** | **deleted** in #554 (`60ed8cbe`, 2026-11-17). Not split per #367; it was dissolved into `packs.domain.rules` and `Wording` | n/a | `PackPolicy.handles` survives as `Wording.handles` (`packs/infra/storage/Wording.java:64`), still test-only |
| **FileSystemPackInspector** | the one reader: `readDirectory :340-385` runs the store half (`StoreRead`) and then `FullRead`, a 240-line stateful inner class; prechecks, mime checks, the manifest, the reserved shard, the census, and validate-mode duplicates of store rules (`parseAndValidate :1162-1214`: the preset stem rule is "checked here too", `:1181-1199`) | medium | 5 constructors; `kind` is stringly typed (`"graph-shard".equals(kind)` at `:1163`); the two parses (F2) |

Rough hotspots from L1 (largest or most complex files, not verified in depth):

- `DomainRoundTrip` (912 lines, 14 static fields, `Rewrite` has 15 components, `artefact(...)` has 9 parameters,
  and the file says "artefact" where the rest of the code says "artifact").
- `MeaningComparator` (the highest single-method CCN in the codebase).
- `Wording` (sum CCN 111 over 22 functions: a table that turns schema keywords into English).
- `ActionContextWalk` (901 lines).

None shows a defect. All four belong to the same pattern: one class per pipeline stage, grown by accretion.

## 4. ADR-0075 known limits, verified in code

The limits that [ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/) records, checked against the code.

| Limit | Status | Evidence |
|---|---|---|
| **Two parses (S7)** | open, confirmed | `FileSystemPackInspector.readDirectory`: `storeRead(...)` (`:358`, which reaches `StoreRead.read :158` and then `FilesystemPackStore` DSO parses), then `new FullRead(...).readAll()` (`:359-361`), which parses every mapped file again (`readMapped :914-915` → `parseAndValidate :1162`, `ParsedYaml.parse` and schema validation). `pack.yaml` is parsed twice (`PackDescriptorReader::read :344`, and the store's `descriptorOf`, `FilesystemPackStore.java:864`). |
| **AA1: "held" resolved twice** | open, confirmed | `CensusEntries.companions(...)` (`packs/infra/storage/CensusEntries.java:100-113`, called from `FileSystemPackInspector.java:373`) parses the owners again with the reader's bindings, while `PackFileSet.digested` (`PackFileSet.java:204-208, 322`) uses the hard-wired `KindBindings.kinds()`. |
| **`AssemblyRules.sameFault` text matching** | open; not an ADR-0075 limit, found by this lens | `packs/infra/storage/rules/AssemblyRules.java:171-191`: the final clause is `reason.detail().endsWith(violation.reason())`. `statesMissionTypeRefusal :200-212` matches `"step '"` and `"' stands twice across steps and on_demand_steps"`. `namesMissing :231-243` matches `"missing"`, `"does not exist"` and `"more than once"`. `why`, `declaredOf`, `namesCompanion` and `companionOf` (`:246-293`) recover the companion *path* by splitting the human reason at `": "`; a declared path with a space is treated as "names no companion" (`:278-279`). |
| **Reason ↔ PackElementViolation round trip (AA2)** | open, confirmed | Reason → PEV: `FilesystemPackStore.violationsOf :1522-1531` and `reasonOf :1534-1537` (strips the file prefix from the text). PEV → Reason: `AssemblyRules.held :133-141` → `fault(...)`, which uses `Findings.at(code, file, List.of(), detail)` (`:296`). The pointer and the severity are lost on the way. `PackContents.Assembled` (`packs/domain/PackContents.java:199-203`) carries `List<PackElementViolation>` and `List<Reason>` side by side. 29 `new PackElementViolation(` sites in main (FilesystemPackStore 14, StoreRead 8, PackAssembly 7). |
| **Global read counters (AA5)** | open, and there are more than ADR-0075 lists | `FilesystemPackStore.java:1336-1339` (`LOOKUP_PARSES`, `READS`), `PackFileSet.java:241` (`WALKS`), `StoreRead.java:70` (`RUNS`), plus `InForceResolution.java:45-46` (`RESOLUTIONS`, `WALK_VISITS`, public getters at `:56`, `:65`). That makes 6 JVM-wide static counters, read by `OneReadTest`, `ReadCounters`, `PackReadingTest`, `WholePackStore*Test` and `EffectiveReadOperationBudgetTest`. No `junit-platform.properties` enables parallel tests, so the miscount is latent. `InForceResolution`'s javadoc says "The function is pure" (`:40-43`), yet it sits in `packs.domain` with mutable static state, which `PACKS_DOMAIN_IS_PURE` cannot see (that rule checks dependencies, not state). |

## 5. Hexagonal health

### 5.1 Rules documented but not enforced, or documented wrongly

| # | Documented (where) | Code reality |
|---|---|---|
| H1 | "Arrows point inward only" (`docs/architecture/hexagonal-service.md`, The rule) | no `beFreeOfCycles` rule anywhere (`grep -rn beFreeOfCycles service/*/src/test` returns nothing). **Two real package cycles** (comments stripped): `packs.domain ⇄ packs.domain.rules` (`PackCompositionResolver.java:17` imports `rules.DescriptorChecks`; `ArtifactRules`, `AssetRules`, `CompositionRules` and `FragmentRules` import `packs.domain`), and `packs.infra.storage ⇄ packs.infra.storage.markdown` (`markdown/MarkdownCodec.java:8` imports `storage.FrontmatterYaml`) |
| H2 | the domain knows only itself and the kernel | two **javadoc-only** outward references, which ArchUnit cannot see: `packs/domain/ComposeResolver.java:17-18` links to `packs.application.GraphWalk` and `EdgeIndex`, and `activations/domain/EffectiveSet.java:314` links to `activations.application.PacksSession#inForce` |
| H3 | the application ring has `read` and `write` sub-packages; "the read and write use cases … do not depend on each other" | no `application/read` or `application/write` package exists in any module (the only subpackage is `config`), so that rule is neither true by structure nor enforced |
| H4 | module table lists `artifacts` ("same four layers as packages") and `charter-conformance` | neither module exists (`ls service`); the Status note ("skeleton … kernel, packs, profiles, charter-api, deployable") is stale |
| H5 | ADR-0006 `:72` and `:174` name `NO_INFRA_ANNOTATIONS_IN_DOMAIN` | the rule is `NO_FRAMEWORK_ANNOTATIONS_IN_DOMAIN` (`deployable/src/test/.../HexagonalArchitectureTest.java:200`); same intent, different name |
| H6 | "two outbound adapters of the same slice naming each other" (the 11-12 gap) | **still unenforced.** Only packs has `FETCH_ADAPTER_*` (`:688`, `:695`) and `ARCHIVE_ADAPTER_*` (`:720`, `:727`); there is no generic rule over `SLICES`. The import graph finds **no** current `<slice>.infra.X → <slice>.infra.Y` edge, so the gap is open but not exploited |
| H7 | missions-edge and profiles-edge depend on `web-transport` (doc table and `build.gradle.kts`) | declared `implementation(project(":web-transport"))`, but 0 main references (`grep -rl ai.speckitty.charter.web service/{missions,profiles}-edge/src/main` is empty) |
| H8 | "a slice's domain … kept pure by ArchUnit" | purity is checked as dependencies only. Mutable static state in `packs.domain` (`InForceResolution`) and test seams wired as beans (`JobGate`) pass. `noTestSeamInMain` (`HexagonalArchitectureTest.java:2381-2400`) catches only `..bdd..`, `*Drifting` and `*Scenario`. Main javadoc mentions a test seam or test hook 35 times in 16 files |

All 21 named rules in `hexagonal-service.md` and ADR-0075 have a test (`for r in …; grep -rl "$r" service --include=*.java`).
The `SLICES` list (`:90`) is packs, profiles, routing, missions and activations. The `-edge` modules live in their
slice's package (`ai.speckitty.charter.missions.*` and so on), so the `SLICE_RINGS` rules cover them, and the
artifacts edge has its own rules (`:1766-1810`). `ServiceClasses` imports every main package from deployable's test
classpath, including `pack-validation` and `pack-check`. **Every module is covered**; `pack-check` has no main
sources yet.

### 5.2 Module graph (main code, comments stripped)

```text
kernel        -> (JDK)
missions, profiles, routing -> kernel
web-transport -> kernel
packs         -> kernel, missions, profiles, routing, web-transport   (api(...) leaks missions/profiles/routing transitively)
activations   -> kernel, packs, web-transport
pack-validation -> kernel, packs
missions-edge -> activations, kernel, missions, packs                 (web-transport declared, unused)
profiles-edge -> activations, kernel, packs, profiles                 (web-transport declared, unused)
routing-edge  -> activations, kernel, routing, web-transport
artifacts-edge-> activations, kernel, packs, routing, web-transport
deployable    -> activations, kernel, packs (+ edges through Spring configuration)
```

No module cycle. No `-edge` module names another `-edge` module. Each edge names `packs` or `activations` only from
its `infra.packs` or `infra.activations` package, plus `artifacts.infra.packs → routing.domain` (6 refs, documented).
Full list: `build/analysis/design/import-graph-code-only.txt`, section "edges out of *-edge modules" (not committed;
regenerate with the commands in §1).

## 6. Readability and leftovers

| Item (11-12 reference) | Now | Status |
|---|---|---|
| `DoctrineGraph` / `DrgNode` / `DrgEdge` references (23 / 32 / 10) | 24 / 31 / 9 lines (26 / 31 / 9 occurrences), plus `packDrg` ×15; files `kernel/domain/DoctrineGraph.java`, `kernel/domain/DrgNode.java`, `packs/infra/storage/DrgEdge.java`; no exception recorded in `docs/context/domain.md:157-162` | **still open** (unchanged) |
| `PackPolicy.handles` test-only (`PackPolicy.java:428`) | moved to `Wording.handles` (`packs/infra/storage/Wording.java:64`), still called only from `SchemaKeywordCoverageTest` | **still open** (moved) |
| `RegistrationJobs.retained` (`:801`) | `RegistrationJobs.java:810`, test-only | **still open** |
| `ClosureQueries.allConfigured` (`:184`) | `ClosureQueries.java:239`, test-only | **still open** |
| ActivationFileCodec version 1 reader (`:32-38,102-126`) | `activations/infra/storage/ActivationFileCodec.java:28-40, 49-52, 75-80, 103-130` | **still open**, no ruling recorded |
| Unversioned descriptor branch (`PackPolicy :331,347`, `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED`) | moved: `packs/domain/rules/PromptSlotRules.java:37-38, 119-123` (`PROMPT_SLOT_NOT_EMPTY_WARNING` plus `UNVERSIONED_WARNING`); `kernel/domain/PackDescriptor.java:23, 79` (unversioned builder); carried to the read side at `activations/domain/EffectivePrompt.java:111` | **still open** (moved, renamed token) |
| kernel `Relation` vs `RelationType` | `kernel/domain/Relation.java` deleted; only `RelationType` remains (the hypermedia `packs/infra/api/Relation` is a different concept) | **resolved** |
| Code-point ordering ×5 | **9 implementations**: `ActivationDocument.java:320`, `TabooProjection.java:43`, missions-edge `CodePoints.java:17`, `EffectivePromptQuery.java:303`, `ArtifactPosition.java:40`, `ClosureView.java:1065`, `MissionTypeHoldingRead.java:174`, `FileSystemPackInspector.java:1313`, `InForceResolution.java:119`; the kernel has none | **worse** |
| `RefusalCode.notFound` vs the RefusalHandler list | `kernel/domain/RefusalCode.java:297-314` (17 codes) vs `web/RefusalHandler.java:371-389` (18 codes, adds `NO_PROFILE_MATCHED`); `notFound()` has no main caller | **still open** (unchanged) |
| `LimitedStream` copy | `packs/infra/api/ArchiveBodyAdvice.java:149` and `PackSourceBodyAdvice.java:73`, 36 lines each, differing only in the limit constant; a third bounded stream is `packs/infra/archive/LimitedInputStream.java:12` | **still open** (unchanged) |
| InForceResolution static counters (`:56,65`) | still present (`:45-46, 56, 65`), and 4 more static counters exist in storage (AA5) | **still open; worse in count** |
| Two outbound adapters of one slice naming each other | unenforced; no violation | **still open** |
| Complexity gate off (#367, #371) | on since `6451dc59` (#371) | **resolved** |
| `PackPolicy` god class | deleted (#554) | **resolved** |
| `PackCompositionResolver.refuseContests` (`:613`) | gone at method level (max CCN 12); the class is 1,480 lines | **method smell resolved; class still large** |
| `PackRegistrationController` with 6 collaborators, mocks | 7 collaborators, 2 of them controllers (`PackRegistrationController.java:83-90`) | **slightly worse** |

**Javadoc.**

- **Boilerplate.** There are about 320 "Checks the fields" or "Copies the lists" javadocs (`grep -rhE` count, for
  example "Checks the fields." ×198), and 845 of 8,380 `@param` lines say only `@param x the x`.
- **Process trace.** 1,405 parenthetical citations of issues, requirements, tasks and work packages (`(#554, T028)`,
  `(FR-041)`, `(IC-20)`, `(WP12 review MINOR-4)`). 30 of them cite review cycles or squads ("WP12 review cycle 2,
  MAJOR-A", "squad finding C-11").
- **Sample** (`javadoc-sample.txt`, 22 blocks, seed 1112):
  - About 9 restate the code: `GitVersionCheck.enabled`, `Pack.directives`, `SerializationStrategy.readDescriptor`,
    `MissionRelation.SELF`, a Spring no-arg constructor, `OnIndeterminate.fromToken`,
    `AgentProfile.Builder.selfReviewStep`, `TermStatus.DRAFT`, and `SourceRefusal.COMMIT_MISMATCH`, which repeats its
    own string literal.
  - About 13 explain intent or a constraint: `WholePackStore.Salvage.unreadable`, `RefusalCode.NOT_FOUND`,
    `StorageCodes.UNREADABLE`, `PackCompositionFacts.holds`, `GuardRefusals.record`, among others.
- **Javadocs that are wrong or that admit a copy:**
  - `InForceResolution` says "pure", yet holds static counters (`:40-43`).
  - `PackCommitStore :419-421` overstates the guarantee (§0).
  - `ParadigmReadService.java:17` says it is "a faithful copy of `DirectiveReadService`".
  - `DirectiveReadService` refers to "the prompt's sanctioned list path", an agent-prompt leftover.

**Naming.** "artefact" ×51 against "artifact" ×3,027 (`DomainRoundTrip`, `PackCommitStore`, `WholePackStore`,
`PackLayout`, `DomainRepositoryService`). The adapter is still named `FileSystemPackInspector` after the port it
implements became `PackReading` (ADR-0075 S6).

## 7. Findings

Severity follows the brief: HIGH = correctness, data or security risk, or a charter rule broken with no ruling;
MEDIUM = design debt that makes change risky; LOW = tidy-up. Cost: S < 1 day, M = a few days or one mission,
L = multi-mission.

### F1. Fault identity is decided by matching human-readable text (MEDIUM, M)

**Evidence.**

- `AssemblyRules.java:171-293`: `sameFault` matches by suffix, `statesMissionTypeRefusal` by `"' stands twice across
  steps and on_demand_steps"`, `namesMissing` by `"missing"`, `"does not exist"` and `"more than once"`, and
  `companionOf`, `why` and `namesCompanion` split the reason at `": "` to recover a *path*.
- `FilesystemPackStore.reasonOf :1534-1537` strips a text prefix.
- `PackCommitStore.requireNoNewViolation :429-489` matches by `(code, file, pointer, detail)`, then by `(code, file)`.
- `CompositionRules.codeOf :156-162` maps `RefusalCode` to `ReasonCode` by token string.
- The lossy `PackElementViolation(file, ReasonCode, String)` (`packs/domain/PackElementViolation.java`) sits in the
  middle.

**Risk.** A rewording in one place silently changes which faults are de-duplicated or tolerated. The commit-store
guard admits a new fault that has the same code and file as a fault the live pack already holds, and its javadoc
claims more than that.

**Direction.** Give each store fault a typed identity: a kernel `Reason` with a `Location` that has a pointer, and the
companion path as a field, not text. Delete `PackElementViolation` (AA2), and compare faults structurally. Correct the
`PackCommitStore` javadoc, or match on `(code, file, pointer)`.

### F2. The "one reader" is still several reads (MEDIUM, L)

**Evidence.**

- Two parses per file, and a second parse of `pack.yaml`, in `FileSystemPackInspector.readDirectory :340-385`, `:914`
  and `:1162`.
- A second companion resolution: `CensusEntries.companions` vs `PackFileSet.digested`, `:204-208` (AA1).
- A third, lenient salvage reader: `WholePackStore.salvageStored :410-430`.
- The store's rules duplicated in validate mode: the preset stem rule "checked here too", `:1181-1199`.
- Every per-file write re-reads the staged copy in full, and the live pack too when the copy has a violation: `PackCommitStore :429-474` (`:430-435`).

**Direction.** Do the deferred single-parse unification: derive the reader's facts from the DSOs, give "held" one
resolution, and let the commit store judge the staged `TreeRead` it already has.

### F3. Test hooks and global mutable state in main code (MEDIUM, S)

**Evidence.**

- Six static counters (§4, AA5), one of them in `packs.domain`.
- `JobGate` wired as a production bean: `RegistrationJobs.java:215-233`, `deployable/RegistrationWiring.java:83-84`.
- Telescoping constructors whose only purpose is a test seam:
  - `FilesystemPackStore :289-343` (5);
  - `FileSystemPackInspector` (5);
  - `PackCommitStore` (4);
  - `ClosureView :468-560` (3, one with a wrong default);
  - `PackCompositionFacts` (7: main calls 1, tests call the other 6 forty times);
  - `PackMutabilityPolicy :148-191` (4).

**Direction.**

- Move the counters to an injected per-instance probe or Micrometer counter.
- Move the convenience constructors to `testFixtures` builders, which the charter's "canonical shape only in tests"
  rule favours.
- Add an ArchUnit rule against `static` non-final or atomic fields in `..domain..`.

### F4. A per-kind read path duplicates the kind-keyed one (MEDIUM, M)

**Evidence.**

- Six `*ReadService` classes (87-120 lines each; a normalized diff of Tactic against Directive differs only in
  javadoc). Six per-kind repository ports, each with exactly one caller.
- `ArtifactReadService(...)` takes all six (`:360-372`), and `DomainRepositoryService` implements 14 ports
  (`:63-77`).
- The list methods `directivesOf`, `paradigmsOf`, `styleguidesOf` and `toolguidesOf` have no main caller
  (`test-only-public.txt`).
- `ParadigmReadService.java:17` calls itself "a faithful copy".

**Risk.** Shotgun surgery: a new kind, or a change to the consistent-read protocol, touches 6 or more classes.

**Direction.** One kind-keyed read over `KindBinding` ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)). Delete the per-kind ports and services.

### F5. The effective-read concept is threaded through four mirror layers (MEDIUM, M)

**Evidence.**

- The chain is `ClosureView` (26 public methods) → `activations.application.PacksSession` (293 lines) →
  `ClosureViewSession` (779 lines, 60 functions, mostly pass-through) → `EffectiveReadService` (1,435 lines, 14 public
  records) → four edge adapters.
- `stepModelTier` and `stepRole` each appear in 6 files; `heldWinners` appears in 7.

**Risk.** Each edge depends on the whole surface (interface segregation).

**Direction.** Split `EffectiveReadService` and `PacksSession` by consumer: profile, routing, mission and artifact
query ports. Keep one shared session. Return closure facts as values rather than adding methods.

### F6. Legacy readers and a dead port beside the canonical model (MEDIUM, S each, ruling first; charter rule of 2026-11-10)

**Evidence.**

- The activation codec's version 1 reader: `ActivationFileCodec.java:28-130`.
- The unversioned-descriptor warning branch: `PromptSlotRules.java:119-123`, `PackDescriptor.java:23, 79`,
  `EffectivePrompt.java:111`.
- **New:** `MutabilityPolicyRepository` is kept only as a test seam (`PackMutabilityPolicy.java:153-163`: "Production
  reads no policy through the port; it stays for this seam, and DomainRepositoryService still implements it").

**Direction.** Remove each, as the charter requires, or record an exception. The 11-12 questions 1 and 2 are still
unanswered: no ruling is in the docs.

### F7. Hexagonal gate gaps and documentation drift (MEDIUM, S)

**Evidence.** H1 to H8 in §5.1: no cycle rule and 2 real package cycles; the adapter-to-adapter gap from 11-12 is
still unenforced; `read`/`write` sub-packages and two modules are documented but absent; the rule-name drift; the
unused `web-transport` dependencies.

**Direction.**

- Add `slices().matching("ai.speckitty.charter.(*).(**)").should().beFreeOfCycles()`, or a per-module package-cycle
  rule. Break the `MarkdownCodec → FrontmatterYaml` and `PackCompositionResolver → DescriptorChecks` back edges.
- Add one `ADAPTERS_NAME_NO_OTHER_ADAPTER` over `SLICES`.
- Fix `hexagonal-service.md` and ADR-0006.
- Drop the unused `web-transport` dependencies.

### F8. God classes now at class level (MEDIUM, L)

**Evidence.** §2.1 and §3. Seven classes are over 1,000 lines:

- `FilesystemPackStore`: reading, writing, containment and wording;
- `PackCompositionResolver`: 12 nested types and its own fault vocabulary;
- `FileSystemPackInspector`;
- `EffectiveReadService`;
- `RegistrationJobs`: upload, job and retention lifecycles;
- `ClosureView`;
- `PackCommitStore`: swap engine, history, locks and revalidation.

The method gate pushed complexity into class size, which no gate measures.

**Direction, per class:**

- `FilesystemPackStore` → `PackFileReader`, `AtomicPackWriter`, `CompanionPlanner`, `StoreWording`.
- `RegistrationJobs` → `UploadSessions`, `JobQueue`, `JobRetention`.
- `PackCommitStore` → `DirectorySwap` plus `PackHistory`, with revalidation moved to the application ring.
- `PackCompositionResolver` → resolution, contests, and topology and labels.

Consider a Checkstyle `FileLength` or class fan-out ceiling to stop regrowth.

### F9. Data clumps and primitive obsession on the read path (LOW, S–M)

**Evidence.**

- `PackContents.Assembly.Assembled` has 16 components (`PackContents.java:192-208`): `int assembled`, and the
  `Optional<String>` fields `policyFault` and `factsRefusal`. `StoreRead.read :160-216` fills it through out-parameter
  lists (`List<StoreRead> refusal`, `List<String> policyFault`, `List<String> factsRefusal` used as `Optional`, and
  `trailing`, `storeOnly`, `references`).
- `StyleguideController.assemble` takes 12 parameters (7 `String`).
- `ClosureView` has two `String` fingerprints.
- `RelationshipEndpointRule.Refusal(String code, …)` is stringly coded (`packs/domain/RelationshipEndpointRule.java:74`).
- Checkstyle has no `ParameterNumber` module.

**Direction.** Return small result records instead of filling out-parameters; give the fingerprints a type
(`ClosureDigest` vs `ConfiguredDigest`); add a `ParameterNumber` limit with a records exemption.

### F10. Duplicated knowledge: 11-12 R6 not done, one item worse (LOW, S)

**Evidence.**

- Code-point ordering is implemented 9 times (it was 5).
- `RefusalCode.NOT_FOUND` (17) and `RefusalHandler.NOT_FOUND_CODES` (18) still differ.
- `LimitedStream` still appears twice, beside `LimitedInputStream`.
- Three identically shaped `Breach` records: `FallbackProfileRule.java:28`, `RolePinRule.java:29`,
  `ProfileEnhancementRule.java:39`. Two of them were added after ADR-0075 set out to unify the fault vocabulary.

**Direction.**

- One kernel `CodePointOrder`.
- `RefusalHandler` reads `RefusalCode.notFound()`, which needs a ruling on `NO_PROFILE_MATCHED`.
- One shared bounded stream.
- One `RuleBreach`, or kernel `Reason`s.

### F11. Retired `Doctrine` and `Drg` vocabulary in type names (LOW, M)

**Evidence.** `DoctrineGraph`, `DrgNode` and `DrgEdge` appear on 24, 31 and 9 lines; `packDrg` appears 15 times. No
glossary exception is recorded. "artefact" appears 51 times.

**Direction.** A bulk-edit mission (spk-practice-bulk-edit), as 11-12 question 3 proposed.

### F12. Dead or test-only public API (LOW, S)

**Evidence.**

- `ClosureQueries.allConfigured :239`, `RegistrationJobs.retained :810` and `Wording.handles :64`.
- The kernel `Relationship.requires`, `suggests`, `tension`, `requiresStep` and `annotated` factories
  (`kernel/domain/Relationship.java:231-434`).
- `CompositionFactsCache.hits` and `loads` (`:139`, `:148`) and `InForceResolution.walkVisits :65`.
- The per-kind list methods (F4).
- The full heuristic list is `test-only-public.txt`: 66 hits, about half of them framework callbacks.

**Direction.** Delete, or move to test fixtures.

### F13. The javadoc carries process archaeology and some false claims (LOW, S)

**Evidence.** §6 Javadoc: a comment-to-code ratio of 0.61; 1,405 tracker or task citations and 30 review-cycle
citations; about 320 boilerplate "checks the fields" javadocs and 845 tautological `@param` lines. Wrong or misleading:
`InForceResolution :40-43` ("pure"), `PackCommitStore :419-421`, `ParadigmReadService :17` ("faithful copy").

**Direction.** Keep intent and constraints in the javadoc, and move provenance to the ADRs and commits. Fix the three
wrong claims when the code they describe is next touched.

## 8. Scripts and outputs

The scripts are in `scripts/analysis/design/`:

- `derecord.py`: the record-header rewrite that works around the lizard bug.
- `lizard_report.py`: long and complex methods, parameter counts, and the per-file CCN table.
- `methods_of.py`: the methods of one class, in line order.
- `filestats.py`: the class-size table (lines, code, comment, nested types, constructors).
- `import_graph.py`: the package and module graph, cycles, edges out of the `-edge` modules, and adapter-to-adapter
  edges (`STRIP_COMMENTS=1` leaves out javadoc references).
- `test_only_public.py`: a heuristic list of public methods that only tests call.
- `sample_javadoc.py`: a seeded javadoc sample.

The outputs go to `build/analysis/design/` and are not committed; regenerate them with the commands in §1:

- `lizard-main.csv` (a raw lizard run over the unmodified sources, which shows the record problem) and
  `lizard-main-derec.csv` (the corrected run);
- `lizard-report*.txt`;
- `filestats.md`;
- `import-graph.txt` (javadoc references included) and `import-graph-code-only.txt`;
- `test-only-public.txt`;
- `javadoc-sample.txt`.
