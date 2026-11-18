# Implementation Plan: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` → `slice-5-554-pack-closure-rulesets` → `main` (one pull
request, R4) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-closure-rulesets-01M7FA90/spec.md`

**Base.** Rebased onto `main` at `97985727a`, which holds slice F (#567), #555 (#578) and slice G (#573). The branch
rebases again whenever `main` moves (C-002).

## Summary

Every pack check becomes a kernel rule, placed by what it knows (R3, as superseded in part by S1 to S3 and S6 on
2026-11-16, [squad-shaping](research/squad-shaping.md)):

- **Core consistency (`packs.domain.rules`).** Per-kind rules over storage-agnostic facts, composed as
  `PackRulesets.content()` (graph, endpoint, mission type, step enhancement, orphan fragments, prompt slot and
  repeated asset id; no glossary rule and no asset-escape rule, both edge checks per S2, WP06 D1 and D2) and `PackRulesets.closure()` (collect-all composition checks). `PackCodes`
  holds the domain tokens. There is no `Surface` enum, no `Path`, no bytes, and no YAML or JSON-Schema vocabulary.
- **Storage edge (`packs.infra.storage`).** One reader, `FileSystemPackInspector`, turns a tree into one `TreeRead`:
  `PackContents`, a `TreeCensus` and the read faults (kernel `Reason`s with a kernel `ReasonCode`), the parse and
  schema prechecks among them (S2: the descriptor version format and the asset mime format), with the wording it owns.
  `PackAssembly` builds the aggregate from the read's `PackContents` without judging. `packs.infra.storage.rules`
  holds `TreeRead`, `TreeCensus`, `StorageRulesets.holding()` (not held, reserved shard, the manifest a regular file),
  `StorageRulesets.registration()` (`holding().must(PackRulesets.content().on(TreeRead::contents))`), the
  manifest-membership function, `StorageRulesets.verdict()` (the read faults, `registration()`, and the gate when
  those pass, as one `Rationale` in registration's wire order) and `StorageCodes` (S3). Nothing outside
  `packs.infra.storage` and `deployable` names them (S6).
- **The port (`packs.domain`, S6).** `PackReading` (`PackReadout read(PackRoot root)`) replaces `PackInspector`.
  `FileSystemPackInspector` implements it: one walk, then `StorageRulesets.verdict`. `PackReadout` carries the
  `PackContents`, the classified files, the not-held files (`NotHeldFile`: path, kind, reason) and that one kernel
  `Rationale`: kernel and domain types only.
- **API edge (`packs.application`).** `PackRegistrationService` reads once through `PackReading`, refuses through
  `PackRefusals` when the readout's `Rationale` fails, then has the store's `Loader` assemble from the readout's
  `PackContents`, remove its not-held files and stamp (S1, S6). The compose read runs only the store half of
  the one reader (`StoreRead`, S7) inside the storage edge and keeps its own first-`pack_unreadable` mapping over its
  assembly facts and faults; the store half never produces S2's prechecks, so S4 holds with no filter.
- **Validation edge (`pack-validation`).** `PackCheck`, moved from `packs.domain.PackValidation`, reads through the
  `PackReading` its caller supplies, checks the placement rules (a `ModularRuleset<Placement>` over
  `Placement(PackReadout, ...)`) and merges the two with the kernel's `Rationale.and`:
  `read.rationale().and(placement().check(placement))`. The manifest gate ran inside the readout, so placement never
  gates it. Placement (the configured level and the org fragment) is its own extension (S2), with its tokens in
  `PlacementCodes` (Q7). `PackVerdicts` maps its findings. `PackCheck` names `packs.domain` and the kernel only. The
  CLI entry stays with #478.
- **One code table per owner.** Each token is built exactly once, in `PackCodes`, `StorageCodes` or
  `PlacementCodes`, and one fault gives one code and one wording on every surface (R2).

Registration's findings are the readout's `Rationale`, and validate mode's are that `Rationale` followed by
placement's, so the forward subset holds by construction. The second read on the registration path, its fail-closed
guard and the parity tests between two readers are not built. No ArchUnit rule is widened (S6).

**What is deleted:** the converters, `PackFinding`, `PackFindingCode`, `PackPolicy`, `PackValidation` (moved), the
`PackInspector` port (`PackReading` replaces it, S6), `WholePackWriteStore.ExcludedFile` (`NotHeldFile` replaces it),
the string kinds and `loadStaged`'s judging.

**What changes on the wire:**
- an unmodelled file becomes a `file_not_held` warning (Q1);
- validate mode warns `file_not_held` for a nested shard or a `charter.yaml` and judges nothing in it, and the packs
  contract narrows those globs (open case 6, a packs `### Breaking` line);
- the per-fault code unification (R2), each change under `### Breaking`;
- registration gains the repeated asset id, the descriptor version format and the asset mime format (S2), under one
  `### Breaking` fragment;
- the compose read answers `pack_unreadable` for a repeated artifact id instead of a 500 (S5), recorded as a fix;
- a retired file is no longer also listed as not held (WP02 review L2, WP11).
- validate mode warns `file_not_held` for an excluded file as registration does (open case 6, WP11 review cycle 1).

`pack-validation` becomes the validation edge: #554 adds `PackCheck`, `PackVerdicts` and the placement rules to it.
ADR-0075 is amended.

## Decisions

| ID | Decision | Source |
| --- | --- | --- |
| Q1 | An unmodelled file is a `file_not_held` warning, and it is not held. | `DM-01M7FARH…` |
| P1 | Registration judges the inspector's `PackContents` of the staged tree. The store's reader only builds the aggregate, and a reader fault `PACK` missed fails closed. **Superseded by S1**: one read, no guard. | `DM-01M7FFDY…` |
| R1 | One `PACK`, with a surface profile. Registration and the compose read keep the faults they report today. Validate mode gains the load-only checks. **The profile is superseded by S1 to S3** (named compositions); the fault sets hold, apart from S2's gains. | `DM-01M7G73S…` |
| R2 | One fault answers one code and one wording. The specific code wins over a catch-all, and each surface that changes gets `### Breaking`. | `DM-01M7G73V…` |
| R3 | The consistency rules live in `packs.domain`, with no judging port. `pack-validation` is the **validation edge**: it wraps the core rules for validate mode and pack-check, and adds its own extensions (the round trip, placement, and the pack set from #478). ADR-0075 is amended ([design](research/design-r3.md), with its [challenge](research/design-r3-challenge.md); §8 lists what S1 to S3 and S6 superseded). R3-b (i): the margins are re-set from the nightly run. | `DM-01M7G73X…`, amended 2026-11-16 |
| R4 | One pull request, with ordered work packages, each green. | `DM-01M7G740…` |
| S1 | One reader on the registration path. One read produces the facts, and registration, the compose read and validate mode all judge them. The aggregate is assembled from that read. The second read and its fail-closed guard go. | operator, 2026-11-16 ([squad-shaping](research/squad-shaping.md)) |
| S2 | "api/dto checks are edge, core consistency is domain. prechecks/schema checks are edge." Read as: the repeated asset id is a domain rule (registration gains it); the descriptor version format and the asset mime format are prechecks of the one reader (registration gains them, one `### Breaking` fragment); the level and org fragment are `pack-validation`'s placement extension. | operator, 2026-11-16 |
| S3 | "storage layer. the validation cli may choose to use them, or vica-versa." Read as: the census, manifest membership, the reserved shard and their tokens live in `packs.infra.storage.rules`, which `pack-validation` may name; each token is built once. **Superseded by S6** in part: nothing outside storage and `deployable` names that package. | operator, 2026-11-16 |
| S4 | "Keep today's answers": the compose read ignores S2's new reader prechecks. Its mapping (a storage-edge policy in `DomainRepositoryService`) skips the S2 precheck faults (`requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid`, `asset_mime_invalid`) and otherwise reports what it reports today: a filter over the one read's faults, never a second reader. **Realised by S7**: the compose read runs only the store half (`StoreRead`), which never produces the S2 prechecks, so no filter is built. | operator, 2026-11-16 |
| S5 | "Yes, fix the 500": a repeated artifact id answers `pack_unreadable` on the compose read. The golden row changes once, recorded as a fix (R2 open case 3). Landed in WP04 through the store half (`StoreRead`, S7). | operator, 2026-11-16 |
| S6 | "Port, no rule change": registration and the validation edge reach the storage checks through one port that `packs.domain` declares, `PackReading` (`PackReadout read(PackRoot root)`), returning kernel and domain types only: `PackContents`, the classified files, the not-held files (`NotHeldFile`) and one kernel `Rationale`, the storage layer's composed check (read faults, `content()`, `holding()`, then the manifest gate when clean). `FileSystemPackInspector` implements it by composing the storage rules, which stay in storage (S3) and are named by nothing outside storage and `deployable`. `PackCheck` merges placement with `Rationale.and`. No ArchUnit rule is widened. `PackReading` replaces `PackInspector`; no `TreeReader` port is built. Supersedes S3's "`pack-validation` may name `packs.infra.storage.rules`". On R3's "no judging port": The core's rules are plain rulesets with no judging port; S6 adds one reading port (`PackReading`) whose readout carries the storage edge's verdict. | operator, 2026-11-16 |
| S7 | The compose read runs only the store half of the one reader: `StoreRead`, the same implementation `readTree` runs over the same one walk, with no second DSO reader. On the full one read (two parses of every file) the compose read, a hot path, cost 3.5 to 4.8 times today's (WP05: built-in 35.4 ms to 161 ms, org-base 3.8 ms to 18.3 ms, project 1.1 ms to 3.8 ms). Its answers are unchanged: from `StoreRead`'s assembly facts and faults (`PackContents.Assembly`), the first failure as `pack_unreadable`, endpoint prefix kept; no `content()` over the inspector's facts. No S4 filter is built: the store half never produces S2's prechecks. Registration and validate mode still judge the full one read. Single-parse unification is later work, outside this Mission. | operator, 2026-11-17 |
| S8 | Registration answers every fault of a pack without a readable `pack.yaml`, as validate mode does; no short-circuit (R2 rows 22–24, 38 golden rows, WP12). | operator, 2026-11-17 |
| Q2–Q12 | The orchestrator's defaults, consistent with S1 to S3 ([squad-shaping](research/squad-shaping.md)). Among them: Q5, the wire mapper joins the existing package-private `packs.application.PackRefusals`; Q6, `packs.infra.storage.rules` joins `:packs`' domain+application PIT group; Q7, the placement tokens are built once, in `pack-validation`'s `PlacementCodes`. | orchestrator, 2026-11-16 |
| P2–P7 (2026-11-15 plan) | **Superseded** by R1 to R3: the `PackRules` port, the `ClosureFacts` type and the per-surface code tables are gone. | — |

S1 to S6 are recorded in [squad-shaping](research/squad-shaping.md) and in the rulings tables of this plan and the spec.
S7 is recorded in the rulings tables of this plan and the spec.
The decision records of P1, R1 and R3 carry a dated note that S1 to S3 supersede them in part; those of P1 and R3
also carry a dated S6 note.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain). OpenAPI 3.1 contract in `contracts/charter/`, and the packs contract
in `contracts/packs/`.
**Primary Dependencies**: no new dependency.
- The vendored `kernel.validation` (`Rule`, `ModularRuleset`, `Rationale`, `Reason`, `ReasonCode`, `Location`).
- The `packs` slice, unchanged in its modules.
- `deployable` keeps `:pack-validation` as `testImplementation`, so the `PackValidation` bean goes (C-003).

**Storage**: pack files on disk, read once by the storage edge's one reader (S1), assembled by `PackAssembly` and
committed through `FilesystemPackStore` and `WholePackStore`. The files the census marks not held (excluded or
unmodelled) are removed before the commit, as #451 does.
**Testing**: the tests live in the module of their subject (ADR-0062).
- **Rule, composition, `PackRefusals`, code-coverage and subset tests** (SC-001, NFR-005) in `:packs:test`, with
  `planted/` moved to `:packs` testFixtures.
- **`PackCheck`, `PackVerdicts`, the placement rules and the validate golden** in `:pack-validation:test`, with
  their subject (Pr5).
- **The golden snapshot** of every surface (NFR-002), in `:packs:test`, `:pack-validation:test` and
  `:deployable:test`.
- **The contract scenarios**, through `:deployable:test`.
- **ArchUnit** (`HexagonalArchitectureTest`).
- **The whole-pack gate**, `:pack-validation:wholePackRoundTrip`, as #578 set it.
- **A narrowed PIT run per work package**, and the nightly floors.
- **`./gradlew verifyAll --max-workers=1`**, in the foreground, one run at a time.

**Target Platform**: the charter service (a Linux container, and the native image), and later the `pack-check` jar
(#478).
**Project Type**: multi-module service (`service/*`).
**Performance Goals**:
- Registration reads the staged tree once (S1); today it reads it twice, so this is a saving.
- The compose read does not read a pack again: it runs only the store half of the one reader (`StoreRead`, S7), so
  it does not pay the full one read's 3.5 to 4.8 times today's cost (WP05).
- Known limit: the full one read parses every file twice (the inspector's generic parse and the store's DSO parse).
  `loadStaged` and validate mode pay it, but they are not hot paths. Single-parse unification is later work, outside
  this Mission (S7).
- No new budget is set, and the registration scenarios keep their timeouts.

**Constraints**: the canonical model, with no legacy reader (C-001); the module direction (C-003); the wire changes
only as the diff list of NFR-002 allows; signed commits, rebase only.
**Scale/Scope**:
- Per-kind content rules (rule constants over the existing pure functions), `PackRulesets`, `CompositionRules` and
  `PackCodes` in `packs.domain`.
- The one reader, `PackAssembly`, a manifest derivation and the composition-facts builder in `packs.infra.storage`;
  `TreeCensus`, `StorageRulesets` and `StorageCodes` in `packs.infra.storage.rules`.
- `PackRefusals` in `packs.application`; `PackCheck`, `PackVerdicts` and the placement rules in `pack-validation`.
- Deletes `PackPolicy`, `PackFinding`, `PackFindingCode`, `PackValidation`, the string kinds and about 15 converters.
- About 82 test files are touched, spread over WP12 and WP13 (Pr7).
- About 4 scenarios are rewritten for Q1, plus the rows R2 and S2 change.

## Charter Check

| Charter rule | Status |
| --- | --- |
| The charter's model is canonical; no legacy reader | Pass. The rules run over `packs.domain` objects in the `packs` domain. No old-string mapper or per-surface table is kept. |
| Tests and examples carry only the canonical shape | Pass. Invalid packs appear only as error-path fixtures. `planted/*/expected.json` moves to the new tokens and severities, and `core_verdicts.py` follows. |
| Contract: 0.x may break on a minor, with `### Breaking` | Pass. There are dated fragments for Q1, for registration's R2 rows, for validate mode's R2 rows and for S2's registration gains, and a packs `### Breaking` line for the narrowed globs. |
| No unverified dependency | Pass. No new external dependency. |
| The whole-pack round trip over the example packs | Pass. A file that is not held is not round-tripped. |
| Adversarial squad at the plan point and before merge | Plan point: done ([squad-plan.md](research/squad-plan.md), the R3 design squad with its challenge, and the shaping squad, [squad-shaping.md](research/squad-shaping.md)). Before merge: one squad over the diff, with the open review items below. |
| Issues stay small | Pass. Nothing new is filed. `Closes #554`, `Refs #355`. What #478 inherits (the CLI entry, the pack-set check, the `pack-check` merge) goes into #478's text. |

No violations.

### Open review items for the pre-merge squad

- **The compose read runs the store half only (analysis L5, 2026-11-16; S7, 2026-11-17).** S7 makes L5's question
  (is S4 only a filter) moot: no filter is built. Check that the compose read (`DomainRepositoryService`) runs
  `StoreRead` over the one walk, with no second reader, parser or profile in core, and that it keeps serving stored
  packs whose descriptor version or asset mime registration now refuses.

### S6: the storage checks reach the edges through a domain port (2026-11-16)

The S3 placement met two `ONION_DEPENDS_INWARD` rules: `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY` forbids
`packs.application` to name `packs.infra.storage.rules`, and `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` (with
`PACK_VALIDATION_IS_FRAMEWORK_FREE`) forbids `pack-validation` to. The operator ruled "Port, no rule change" (S6):

- **The port.** `packs.domain` declares `PackReading` (`PackReadout read(PackRoot root)`). `PackReadout`,
  `NotHeldFile` and `ClassifiedFile` are domain records; the `Rationale` is the kernel's. The port names only
  `packs.domain`, the kernel and `java`, so `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES` and `PACKS_DOMAIN_IS_PURE` hold.
- **Inside storage.** `TreeRead`, `TreeCensus`, `StorageRulesets`, `StorageCodes` and the reader's `TreeRead` method
  stay in `packs.infra.storage`. `FileSystemPackInspector` implements `PackReading`; its method that returns a
  `TreeRead` is package-private, so `STORAGE_RETURNS_DOMAIN` holds for its public methods. `deployable` supplies it
  to registration and the compose read.
- **The kernel check.** The composition type-checks against `kernel.validation` as it is: `registration()` is
  `holding().must(content().on(TreeRead::contents))` (`Rule<PackContents>.on` gives a `Rule<TreeRead>`), and
  `verdict(read)` is `new Rationale(read.readFaults()).and(registration().check(read))`, followed by
  `.and(new Rationale(manifestMembership(read)))` when that is passing. `PackCheck` merges placement with the same
  `Rationale.and` ("this rationale followed by another; neither is changed"). No kernel change is needed.
- **ArchUnit.** Every onion, purity, port and framework rule stays as it is, with no allowlist entry added. WP09
  widens nothing; WP13 adds rules (`PACK_RULES_READ_NO_FILE`,
  `STORAGE_RULES_ARE_FRAMEWORK_FREE`) and re-points the single-owner rules whose subjects it deletes, but widens none.

Two consequences, stated in the work packages:

- **The aggregate's inputs travel in `PackContents`.** Registration (`packs.application`) cannot hand a storage-side
  assembly input back to the store, so `PackAssembly` builds the aggregate from the readout's `PackContents` alone,
  through the store's `Loader` (WP05, WP12). Whatever the aggregate needs that is not yet a domain fact of
  `PackContents` (the per-artifact digest, the bound graphs) becomes one in WP05; no storage type crosses.
- **Registration's wire order is built with the composition.** `PackRefusals` cannot name `StorageCodes`, so it cannot
  place the reserved shard and manifest membership by code. `StorageRulesets.verdict` returns its reasons in
  registration's wire order (WP07), and `PackRefusals` keeps that order, naming no storage code. `PackVerdicts`
  re-sorts with `Precedence.comparator(PlacementCodes.ALL)`. `Precedence` (`packs.domain.rules`) is typed on kernel
  `Reason` and `ReasonCode` only and takes the pack-wide group as a parameter, so no `:packs` class names
  `PlacementCodes` (C-003); `PackVerdict.findings` is a `List<Reason>`. WP09 retypes both (out-of-map on WP03's
  `Precedence` and WP05's `PackVerdict`), and the validate golden pins the order (NFR-002).

## Project Structure

### Documentation (this mission)

```text
kitty-specs/pack-closure-rulesets-01M7FA90/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── research/ (squad-*.md, design-r3.md, design-r3-challenge.md, golden/, open-packs-drafts/)
├── decisions/
└── tasks/
```

### Source Code (repository root)

```text
service/packs/src/main/java/ai/speckitty/charter/packs/domain/
    PackCodes.java                    # domain tokens: token, severity, wording (replaces PackFindingCode + string kinds)
    PackCompositionResolver.java      # collect-all composition checks; the offering's first refusal in today's order
    PackContents.java                 # storage-agnostic facts of the one read, with neutral read faults
    rules/
        PackRulesets.java             # content(), closure(): named compositions, no Surface enum
        {Step,Artifact,Asset,MissionType,Graph,Fragment,PromptSlot}Rules.java   # content rules
        CompositionRules.java         # CLOSURE over PackUnderResolution
        GraphBinding.java, Paths.java, DescriptorChecks.java, Wording.java   # plain functions
        Precedence.java               # comparator(Set<ReasonCode> firstGroup) -> Comparator<Reason>: kernel types only,
                                      # the pack-wide group injected by its caller (WP09 retypes it, N2)
    PackPolicy.java, PackFinding.java, PackFindingCode.java, PackValidation.java   # deleted (PackValidation moved)
    PackReading.java                  # the port of the one read (S6): PackReadout read(PackRoot); replaces PackInspector
    PackReadout.java                  # PackContents + classified files + NotHeldFile list + one kernel Rationale
    NotHeldFile.java                  # path, kind (EXCLUDED, UNMODELLED), reason; replaces WholePackWriteStore.ExcludedFile
    ClassifiedFile.java               # every file's classification, moved out of PackContents; PackVerdict carries it
    PackInspector.java                # deleted with its Scope: PackReading replaces it (WP09)
    PackVerdict.java                  # classified files + List<Reason> findings (WP09 retypes it from PackFinding)
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
    FileSystemPackInspector.java      # the one reader, implements PackReading; its TreeRead method is package-private (S1, S2, S6)
    PackAssembly.java, PackManifestDerivation.java, CompositionFactsBuilder.java   # Spring-free, not beans (F2, P7)
    WholePackStore.java               # loadStaged judges nothing; removeNotHeld takes the census
    DomainRepositoryService.java      # compose read: the store half (StoreRead) only; first pack_unreadable (S7)
    rules/
        TreeRead.java                 # PackContents + TreeCensus + read faults: the storage rules' one input type
        TreeCensus.java               # held and not held (NotHeldFile), classified files, manifest, reserved-shard stamp
        StorageRulesets.java          # holding(); registration() = holding().must(content().on(TreeRead::contents));
                                      # manifestMembership; verdict(read): one Rationale in registration's wire order
        StorageCodes.java             # file_not_held, reserved_shard, manifest_membership, the read-fault tokens
        {NotHeld,ReservedShard,Manifest}Rules.java
service/packs/src/main/java/ai/speckitty/charter/packs/application/
    PackRegistrationService.java      # PackReading.read once -> PackRefusals if the Rationale fails; Loader assembles; stamp
    PackRefusals.java                 # the API edge's mapper (wire problem + diagnostics), beside today's read mapping;
                                      # keeps the Rationale's order, names no storage code
    OfferingReadService.java          # first composition refusal, today's order
service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/
    PackCheck.java                    # the validation edge: PackReading.read, then rationale().and(placement().check(...))
    PackVerdicts.java                 # Rationale -> PackVerdict, Precedence.comparator(PlacementCodes.ALL) order
    placement/Placement.java, placement/PlacementCodes.java      # Placement(PackReadout, level, org facts); its tokens (Q7)
    placement/LevelRules.java, placement/OrgFragmentRules.java   # S2: the edge's placement extension
service/deployable/src/test/.../HexagonalArchitectureTest.java   # A–C re-pointed; tokens built once; rules read no file;
                                                                  # storage rules framework-free; onion and
                                                                  # PACK_VALIDATION rules unchanged (S6)
docs/adr/0075-*.md                    # amendment 2026-11-16 (R3 as superseded by S1 to S3 and S6)
contracts/charter/changes/2026-11-16-*.md   # ### Breaking: Q1; registration R2; validate R2; S2
contracts/packs/CHANGELOG.md          # ### Breaking: the narrowed charter.yaml and nested-shard globs (open case 6)
```

**Structure Decision**: the existing modules. No module is added, and no main dependency changes. Two packages are
added: `packs.domain.rules` and `packs.infra.storage.rules`. One domain port, `PackReading`, replaces `PackInspector`
(S6).

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Golden snapshot and the R2 code table

- **Purpose**: freeze what every surface answers today, and decide each fault's one code and wording, before anything
  changes.
- **Relevant requirements**: NFR-002, FR-009, SC-002.
- **Affected surfaces**:
  - a characterization test over every planted, example and scenario pack: validate verdict, registration problem
    and diagnostics, compose-read 500 detail, offering refusal;
  - `planted/` moved to `:packs` testFixtures;
  - the R2 table in `data-model.md`, checked against the snapshot.
- **Sequencing/depends-on**: none. It comes first.
- **Risks**: the snapshot must be taken on today's code. Any later diff must cite Q1 or an R2 row.

### IC-02 — Q1 on today's pipeline

- **Purpose**: an unmodelled file becomes a `file_not_held` warning and is not held, before the pipeline moves.
- **Relevant requirements**: FR-010, FR-013, FR-014; SC-003.
- **Affected surfaces**:
  - `WholePackStore` (`notHeld()` replaces `unmodelled()`), and its callers in #578's harness and the drift test;
  - `ExcludedFilesAgreementTest`;
  - the scenarios (C-10's list);
  - the unreleased 2026-11-11 fragment;
  - `DiagnosticCode.yaml`, `packs_packId.yaml` and the packs contract wording;
  - one `### Breaking` fragment;
  - the open-packs draft.
- **Sequencing/depends-on**: IC-01.
- **Risks**: after Q1, a manifest mismatch beside `notes.txt` is now reported. The fragment says so.

### IC-03 — PackPolicy's residue and the descriptor function

- **Purpose**: move the members other classes use into named `packs.domain` functions, so that `PackPolicy` can go.
- **Relevant requirements**: FR-004; A-M5.
- **Affected surfaces**: `TENSION`, `joinsItself`, `bodyPathEscape`, `assetPathEscape`, `registeredGraph` and
  `descriptorFindings`, and their roughly 20 callers. `DescriptorChecks` is a pure format function: the one reader
  calls it as a precheck (S2, IC-05), and the offering and the resolver's precondition keep calling it.
- **Sequencing/depends-on**: IC-02.
- **Risks**: none beyond mechanics.

### IC-04 — PackCodes, StorageCodes and the unified codes

- **Purpose**: give one typed code table per owner, and make every surface speak it (R2, S3).
- **Relevant requirements**: FR-009, FR-013; ruling 4.
- **Affected surfaces**:
  - `PackCodes` (domain tokens) and `StorageCodes` in the new `packs.infra.storage.rules` (storage and read-fault
    tokens); a token is built in exactly one of them. The placement tokens are not built here: WP09 builds them in
    `PlacementCodes` (Q7), and until then `PackFindingCode` stays as a thin view for `PackPolicy`, staging inside the
    single pull request (R4 b), never shipped;
  - `PackElementViolation.kind` and `PackFinding.code` become `ReasonCode`;
  - the `invalid` fallback goes;
  - the R2 table's changes on each surface, with two `### Breaking` fragments;
  - `planted/*/expected.json` and `core_verdicts.py`.
- **Sequencing/depends-on**: IC-03, IC-10.
- **Risks**: the two `prompt_slot_not_empty` codes. The mappers switch on the constant, and a token-grammar test is
  added.

### IC-05 — The one reader, the census, the Spring-free assembly and the composition facts

- **Purpose**: one read of a pack tree produces every fact a rule needs (S1), and one census owns "held" (S3). The
  aggregate and the composition facts are built from that read, without Spring (F2, F5, P7).
- **Relevant requirements**: FR-015, FR-016, NFR-004; S2's prechecks (FR-017) are read faults here.
- **Affected surfaces**:
  - `FileSystemPackInspector`, the one reader, with a package-private method that returns one `TreeRead`
    (`PackContents`, storage-agnostic and holding the aggregate's inputs; `TreeCensus`; the read faults, typed with
    kernel `ReasonCode`);
  - the domain port's types (S6): `PackReading`, `PackReadout`, `NotHeldFile` and `ClassifiedFile` in `packs.domain`
    (the reader implements the port in WP07, once `StorageRulesets.verdict` exists);
  - `TreeCensus` in `packs.infra.storage.rules`, built from WP11's narrowed table and explicit not-held kind;
  - `PackAssembly` and `PackManifestDerivation` in `infra.storage` (F2), shared with `DomainRepositoryService` and
    the manifest stamp;
  - one composition-facts builder over the read; `DomainRepositoryService` delegates (P7);
  - the store's load consumes the one read, and the compose read its store half (`StoreRead`, S7) over the same one
    walk, behaviour-preserving; no S4 filter is built, since the store half never produces S2's prechecks;
  - open case 8: the clean control gets its `assets/doc.txt`.
- **Sequencing/depends-on**: IC-04.
- **Acceptance**:
  - the A-M1 checklist, with one fixture per row, each fact produced by the one reader;
  - `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` stays green;
  - a read-count test sees one read per surface call;
  - no symlinks are followed on the registration path (F4).

### IC-06 — Content rules, the compositions and `PackRefusals`

- **Purpose**: express each consistency check once, in the kernel vocabulary, in `packs.domain.rules`; express each
  storage check once in `packs.infra.storage.rules`; compose them by name, with no `Surface` enum (S1 to S3).
- **Relevant requirements**: FR-001, FR-002, FR-008 (`PackRefusals`), FR-011, FR-012; FR-017 (repeated asset id).
- **Affected surfaces**:
  - `packs.domain.rules`, carved from `PackPolicy`, `StepEnhancementRules` and `RelationshipEndpointRule`, as rule
    constants over the existing pure functions;
  - `PackRulesets.content()` (`ModularRuleset<PackContents>`), `StorageRulesets.holding()` and
    `registration()` (`ModularRuleset<TreeRead>`, `content()` lifted with `on(TreeRead::contents)`), and the manifest
    gate as a plain function run only when the read faults and `registration()` pass;
  - `StorageRulesets.verdict(read)`, one `Rationale` in registration's wire order, and `FileSystemPackInspector`
    implementing `PackReading` with it (S6);
  - `PackRefusals`, the existing package-private class in `packs.application` (Q5), mapping the readout's `Rationale`
    in the order given, naming no storage type (C-9, S6).
- **Sequencing/depends-on**: IC-05.
- **Risks**: the composition interactions (M-7). Each gets a composition test, and they are listed in the tasks.

### IC-07 — CLOSURE

- **Purpose**: collect every composition fault, and keep the offering's first refusal.
- **Relevant requirements**: FR-003.
- **Affected surfaces**:
  - `PackCompositionResolver`, whose throws become collect-all functions;
  - `CompositionRules`, exposed as `PackRulesets.closure()`;
  - `OfferingReadService`, which takes the first refusal in today's interleaved order, with its `RefusalPack` list.
- **Out of scope**: the reference and case-only checks, which go to #556.
- **Sequencing/depends-on**: IC-05.
- **Risks**: the first refusal must stay byte-equal. Multi-fault fixtures check it against the snapshot.

### IC-08 — The validation edge

- **Purpose**: validate mode becomes `pack-validation`'s edge, taking the core and storage compositions' one
  `Rationale` through `PackReading` and adding placement (R3 as amended, S2, S6).
- **Relevant requirements**: FR-005, FR-008 (`PackVerdicts`), FR-010 (validate half kept), C-003; NFR-003.
- **Affected surfaces**:
  - `PackCheck` in `pack-validation`, moved from `packs.domain.PackValidation`;
  - `PackVerdicts`, `Placement`, the placement rules (`LevelRules`, `OrgFragmentRules`) and `PlacementCodes` (Q7) in
    `pack-validation`; `PackCheck` reads through the `PackReading` its caller supplies and merges placement with
    `Rationale.and` (S6);
  - the validate golden harness and the validate-mode tests, moved with their subject (Pr5);
  - `Precedence` retyped to `Comparator<Reason>` with the group injected, and `PackVerdict.findings` to
    `List<Reason>` (out-of-map on WP03's and WP05's files); the validate golden proves the order did not move;
  - the domain `PackInspector` port and its `Scope` deleted, once `PackCheck` and `PackLoad` no longer need them
    (N6); its test callers repointed to `readTree(root)` or `read(root)`;
  - the `PackValidation` bean in `CharterWiring` removed; `PACK_VALIDATION_IS_FRAMEWORK_FREE` unchanged (S6).
- **Sequencing/depends-on**: IC-06, IC-07.
- **Risks**: the validate golden must be reproduced by `PackCheck`, apart from the listed rows.

### IC-09 — Tests, floors and records

- **Purpose**: collapse the old tests, cover every code, re-set the margins honestly and amend the records.
- **Relevant requirements**: FR-012, FR-013; NFR-003, NFR-005; SC-005.
- **Affected surfaces**:
  - the collapsing test classes;
  - the code-coverage test over `PackCodes`, `StorageCodes` and (in `:pack-validation:test`) `PlacementCodes`;
  - the narrowed and nightly PIT runs, with the margins in `pack-validation/build.gradle.kts`, `packs/build.gradle.kts`
    and ADR-0062 re-set from measurement (R3-b (i));
  - the ADR-0075 amendment (R3 as superseded by S1 to S3 and S6) and the ADR-0065 annotation;
  - #554's issue text, #478's text, and the closeout.
- **Sequencing/depends-on**: IC-12.
- **Risks**: the `packs` group gains the rules' mutants. A narrowed PIT run per work package catches a dip early.

### IC-10 — Open case 6 on today's pipeline

- **Purpose**: validate mode and registration agree on what is held before the pipeline moves: validate mode warns
  `file_not_held` and judges nothing in a file the store does not hold (Q1, open case 6).
- **Relevant requirements**: FR-010, FR-013, FR-014; US3 scenario 4.
- **Affected surfaces**:
  - `file-kinds.yaml`'s `**/charter.yaml` and nested `**/*.graph.yaml`, narrowed, with their classifier cases and a
    packs `### Breaking` line;
  - the inspector's classification and `PackPolicy`'s judging of not-held files;
  - an explicit not-held kind in place of the reason compare, and retired files left out of the not-held list (WP02
    review LOWs);
  - one held-set agreement test; the golden.
- **Sequencing/depends-on**: IC-02.
- **Risks**: narrowing the table re-kinds the census gate (Pr6). Check the whole-pack baseline and the drift test.

### IC-11 — Registration and the compose read on the one read

- **Purpose**: registration reads once through `PackReading`, refuses through `PackRefusals` when the readout's
  `Rationale` (`registration()` and the manifest gate) fails, then assembles and stamps (S6); the compose read runs
  only the store half of the one reader (`StoreRead`, S7) and answers as today, with `pack_unreadable` for a repeated
  artifact id (S5, landed in WP04).
- **Relevant requirements**: FR-002, FR-016, FR-017; SC-001; NFR-002.
- **Affected surfaces**:
  - `PackRegistrationService` on `PackReading` (S6), `PackSourceStagers` through registration, `loadStaged`
    assembling from the readout's `PackContents` and judging nothing, `removeNotHeld` over the readout's
    `NotHeldFile`s;
  - `DomainRepositoryService`'s compose read on `StoreRead` (S7): no filter, since the store half never produces S2's
    prechecks (S4), and never a second reader; the S5 golden row (a 500 becomes `pack_unreadable`, recorded as a fix;
    landed in WP04);
  - S2's `### Breaking` fragment and the scenarios that pinned the old acceptance;
  - the two subset tests.
- **Sequencing/depends-on**: IC-08.
- **Risks**: the 63 `new WholePackStore(` test sites that assert refusals move to registration and rule tests.
  `PackRegistrationService` names only `PackReading` and `PackReadout`, so the onion rules hold unchanged (S6).

### IC-12 — The deletions and ArchUnit

- **Purpose**: the glue goes, and the shape is pinned.
- **Relevant requirements**: FR-007; SC-004; NFR-001.
- **Affected surfaces**:
  - deletes `PackPolicy`, `PackFinding`, `PackFindingCode`, the converters and the parity-by-hand tests (the
    `PackInspector` port went in IC-08, WP09);
  - ArchUnit: rules A to C re-pointed to `PlacementCodes` and the placement rules; `PACK_CODES_HAVE_ONE_OWNER`
    reworded to "each token is built once" (`PackCodes`, `StorageCodes`, `PlacementCodes`);
    `PACK_RULES_READ_NO_FILE`; `STORAGE_RULES_ARE_FRAMEWORK_FREE`; bite tests. The onion rules,
    `PACK_VALIDATION_IS_FRAMEWORK_FREE`, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and `STORAGE_RETURNS_DOMAIN` are
    unchanged (S6).
- **Sequencing/depends-on**: IC-11.
- **Risks**: deleting coverage. Each deleted test's cases are first found in a rule, composition or mapper test.

### Sequence

```text
WP01✓ ─ WP02✓ ─┬─ WP11 ─┐
               └─ WP03 ─┴─ WP04 ─ WP05 ─┬─ WP06 ─ WP07 ─┐
                                        └─ WP08 ────────┴─ WP09 ─ WP12 ─ WP13 ─ WP10
```

| IC | WP |
| --- | --- |
| IC-01 | WP01 |
| IC-02 | WP02 |
| IC-03 | WP03 |
| IC-04 | WP04 |
| IC-05 | WP05 |
| IC-06 | WP06, WP07 |
| IC-07 | WP08 |
| IC-08 | WP09 |
| IC-09 | WP10 |
| IC-10 | WP11 |
| IC-11 | WP12 |
| IC-12 | WP13 |
