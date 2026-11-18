# Mission Specification: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Mission Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` (lands on `slice-5-554-pack-closure-rulesets`, then
on `main` by pull request)
**Created**: 2026-11-15
**Status**: Draft
**Input**: #554, slice 5 of epic #549 (ADR-0075 decisions 3, 4, 5 and 7, as amended on 2026-11-13). Kind `feature`,
size L, profile java-jenny. Grounding: [research.md](research.md).

## Intent Summary (operator, 2026-11-15)

The orchestrator sent the operator the summary below on 2026-11-15. The operator ruled on Q1 and raised no other
change. The 2026-11-15 rulings on #549 also bind it.

A pack author or an operator registers a pack, or asks validate mode for a verdict on one. Later, `pack-check` asks too
(#478). Today the same pack is judged by two pipelines kept equal by hand:

- validate mode (`PackPolicy`);
- the registration load (`WholePackStore` and `DomainRepositoryService`).

Converters glue the two, and each runs checks the other misses.

This Mission makes every pack check a rule of the kernel's rule vocabulary, over the `packs` domain objects:

- **Per kind.** One ruleset per pack file kind. They compose into one `PACK` ruleset.
- **Across packs.** One `CLOSURE` ruleset over the composition facts of a set of packs with levels.
- **One pipeline.** Validate mode and the registration judge one read of the pack with the same rules, each surface
  through a named composition of them (S1, and S3 for the storage rules); the compose read judges the store half of
  that one read (S7). The consistency rules
  live in the `packs` domain; the storage rules live in the storage edge, which registration and validate mode reach
  through one domain port (S6); validate mode is the validation edge in `pack-validation` (R3, as superseded in part
  by S1 to S3 and S6).

Each surface keeps the set of faults it reports today (R1), apart from what S2 adds to registration. Validate mode
gains the checks only the load runs.
One fault answers one code and one wording on every surface (R2), so where the two surfaces disagree today, one
of them changes, under `### Breaking`. The unmodelled-file ruling (Q1) changes the wire too.

Rulings that bind this Mission:

| Ruling | Effect |
| --- | --- |
| Operator ruling 1, 2026-11-15 (#549) | The `packs` domain stays canonical: no parallel model. `FileSystemPackInspector` stays. The registration load keeps its reader. Its "through a port that `packs.domain` declares" is withdrawn by R3. **Superseded by S1 (2026-11-16)** in part: the registration load's own reader goes. `FileSystemPackInspector` becomes the one reader, and the store's DSO read becomes its assembly half (`PackAssembly`). The canonical-model half stands. **S6 (2026-11-16)** brings back a port that `packs.domain` declares, for the read only: `PackReading`, which `FileSystemPackInspector` implements. |
| Operator ruling 3, 2026-11-15 (#549) | Kept compatible for #556: an external check that cannot decide fails closed later. This Mission adds no external check. |
| Operator ruling 4, 2026-11-15 (#549) | A code owns its severity. |
| `DM-01M7FARH7YMGS2X0BRV1HKH47B` (operator, Q1) | An unmodelled file is a `file_not_held` warning. Pack authors may add docs and reference files that do not affect the service: warn, then ignore. The file is not held and not round-tripped. |
| ADR-0075 amendment, 2026-11-13 | `pack-validation` depends on `:packs` and names only `packs.domain`. Nothing moves out of `packs`. **Superseded by S3 (2026-11-16)** in part: `PackValidation` moves out of `packs` into `pack-validation` (as `PackCheck`). S3's "`pack-validation` may also name `packs.infra.storage.rules`" is withdrawn by S6: `pack-validation` names only `packs.domain`, as this amendment says, and reaches the storage checks through the domain port `PackReading` (C-003). The dependency direction stands. |
| `DM-01M7FFDY3YP3VA3EA5VGRZF28V` (operator, P1) | Registration judges the inspector's `PackContents` of the staged tree. The store's reader only builds the aggregate, and a reader fault that `PACK` missed fails closed. **Superseded by S1 (2026-11-16)**: the store's second read and the fail-closed guard go. Registration judges the one read (`TreeRead`), and `PackAssembly` builds the aggregate from that read. |
| `DM-01M7G73STB9TJ62R73EB3NMYJR` (operator, R1 a) | One `PACK`, with a surface profile. Registration and the compose read report the faults they report today. Validate mode gains the load-only checks. **Superseded by S1 to S3 (2026-11-16)** in part: the surface profile goes, and each surface runs a named composition (FR-002). The fault sets stand, apart from S2's gains on registration and S4 and S5 on the compose read. |
| `DM-01M7G73VX4WDJ7B03FDS8NSAYD` (operator, R2 b) | Codes and wording are unified: one fault answers one code and one wording on every surface. Per fault, the specific code wins over a catch-all. A surface that changes gets a `### Breaking` fragment. |
| `DM-01M7G73XXVE0W5AGMZTWDNPTEJ` (operator, R3, and R3-b (i)) | The rules, `PACK`, `CLOSURE`, `PackCodes` and the mappers live in `packs.domain`. There is no judging port. `pack-validation` is the Spring-free check library that pack-check runs. ADR-0075 is amended ([design](research/design-r3.md)). The mutation-margin criterion is met by the nightly measurement of #578's code. **Superseded by S2 and S3 (2026-11-16)** in part: the mappers live at their edges (`PackRefusals` in `packs.application`, `PackVerdicts` in `pack-validation`), not in `packs.domain`; the storage rules and `StorageCodes` live in `packs.infra.storage.rules`; `pack-validation` is the validation edge (`PackCheck`, placement and `PlacementCodes`), not only a check library (design-r3 §8). **Superseded by S6 (2026-11-16)** in part: `pack-validation` names no storage package. The core's rules are plain rulesets with no judging port; S6 adds one reading port (`PackReading`) whose readout carries the storage edge's verdict. The margin criterion stands. |
| `DM-01M7G740QJPSFSE7HNQY1FK998` (operator, R4 b) | One pull request, with ordered work packages that are each green. |
| S1 (operator, 2026-11-16, [squad-shaping](research/squad-shaping.md)) | One reader on the registration path? **Yes.** One read produces the facts, and registration, the compose read and validate mode all judge them. The aggregate is assembled from that read. The second read and its fail-closed guard go. Supersedes P1's second read and guard, and R1's surface profile. **Realised by S7 (2026-11-17)** for the compose read: it runs only the store half of the one reader (`StoreRead`). |
| S2 (operator, 2026-11-16) | Where do the validate-only checks go? "api/dto checks are edge, core consistency is domain. prechecks/schema checks are edge." Recorded reading: the repeated asset id is a consistency rule in the domain, so registration gains it; the descriptor version format and the asset mime format are schema prechecks in the edge reader, so with one reader (S1) registration gains them too, under one `### Breaking` fragment; the level and org fragment are a deployment-config check, an edge extension in `pack-validation` only. |
| S3 (operator, 2026-11-16) | Who owns manifest membership, the reserved shard and the held set? "storage layer. the validation cli may choose to use them, or vica-versa." Recorded reading: the storage checks, the census of files held and not held, and the tokens they own (`file_not_held`, `reserved_shard`, `manifest_membership`) live in `packs.infra.storage`, as a kernel `ModularRuleset` over a storage fact (`packs.infra.storage.rules`). `pack-validation` may name that one package. Each token is built exactly once (`PackCodes` in the domain, `StorageCodes` in the storage edge). **Superseded by S6 (2026-11-16)** in part: nothing outside `packs.infra.storage` and `deployable` names that package; `pack-validation` and registration reach it through the domain port `PackReading`. |
| S4 (operator, 2026-11-16) | Does the compose read report S2's new reader prechecks (descriptor version format, asset mime format)? "Keep today's answers". The compose read keeps reporting only the faults it reports today; ignoring the new prechecks is its own mapping policy in the storage edge (`DomainRepositoryService`), not a profile in core: a filter over the one read's faults, never a second reader. Stored packs keep composing; new uploads are refused at registration. **Realised by S7 (2026-11-17):** the compose read runs only the store half (`StoreRead`), which never produces S2's prechecks, so no filter is built. |
| S5 (operator, 2026-11-16) | The compose read throws a 500 on a repeated artifact id. "Yes, fix the 500". Landed in WP04 through the store half (`StoreRead`, S7): the compose read answers `pack_unreadable` with a detail; the golden row changes once, recorded as a fix (R2 open case 3). |
| S6 (operator, 2026-11-16) | How do registration (`packs.application`) and the validation edge (`pack-validation`) reach the storage checks, when `ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`, `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` and `PACK_VALIDATION_IS_FRAMEWORK_FREE` forbid both to name `packs.infra.storage.rules`? "Port, no rule change". Recorded reading: `packs.domain` declares one port, `PackReading` (`PackReadout read(PackRoot root)`), that returns kernel and domain types only: the pack's `PackContents`, its classified files, its not-held files (`NotHeldFile`, a pack-relative path with its kind and reason) and one kernel `Rationale`, the storage layer's composed check (the read faults, `content()`, `holding()`, and the manifest gate when those are clean). `FileSystemPackInspector` implements it by composing the storage rules, which stay in `packs.infra.storage` (S3) and are named by nothing outside storage and `deployable`. Registration and `PackCheck` name only the port; `PackCheck` merges placement's `Rationale` with the kernel's `Rationale.and`. No ArchUnit rule is widened. `PackReading` replaces `PackInspector`; the `TreeReader` port is not built. |
| S7 (operator, 2026-11-17) | The compose read is a hot path. WP05 measured it on the full one read (`readTree`: the inspector's generic parse plus the store's DSO parse) at 3.5 to 4.8 times today's cost (built-in 35.4 ms to 161 ms, org-base 3.8 ms to 18.3 ms, project 1.1 ms to 3.8 ms), because every file is parsed twice. Ruling: the compose read runs only the store half of the one reader, `StoreRead`, the same implementation `readTree` runs over the same one walk; there is no second DSO reader. Its answers are unchanged: it answers from `StoreRead`'s assembly facts and faults (`PackContents.Assembly`) as today, the first failure as `pack_unreadable`, endpoint prefix kept, and runs no `content()` over the inspector's `PackContents` facts. No S4 filter is built: the store half never produces S2's prechecks, so S4 holds by construction. Registration and validate mode still judge the full one read. Known limit: single-parse unification (the inspector's facts derived from the DSOs, each file parsed once) is later work, outside this Mission; `loadStaged` and validate mode also parse twice, but they are not hot paths. |
| S8 (operator, 2026-11-17) | A pack whose `pack.yaml` is missing or unreadable: does registration keep today's single refusal ("The pack cannot be registered: <fault>") or answer what validate mode answers? **Every fault.** Once WP12 switches registration onto the verdict, it answers any graph faults, then the descriptor fault, then the pack's other faults, under "N fault(s), the first at X" (`PackRefusals`). One pipeline, no registration-only short-circuit. The 38 descriptor rows (R2 rows 22–24) are in NFR-002's diff list and a `### Breaking` fragment (WP12). |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One verdict, whichever way a pack is judged (Priority: P1)

A pack author runs validate mode on a pack, then registers the same pack. Every fault the registration refuses,
validate mode names too, with the same code and wording. Today validate mode misses the manifest-membership and
reserved-shard checks, and it names some faults with other codes. Registration keeps the set of faults it reports
today (R1), and gains only what S2 places in the domain or the edge reader: the repeated asset id, the descriptor
version format and the asset mime format. It does not gain validate mode's level and org-fragment checks, which are
the validation edge's placement extension (S2).

**Why this priority**: it is the point of the slice. One rule exists once, and parity by hand goes.

**Independent Test**: For each planted, example and scenario pack, the registration's faults are a subset of validate
mode's, with equal code and wording. The registration's faults also equal today's registration faults (both subset
tests).

**Acceptance Scenarios**:

1. **Given** a pack whose manifest lists a missing file, **When** it is validated and then registered, **Then** both
   answers name `manifest_membership` at the manifest, with one wording.
2. **Given** a valid example pack, **When** it is validated and registered, **Then** both answers are clean, apart
   from the warnings each answered before.

---

### User Story 2 - The wire does not move (Priority: P1)

A client reads the registration refusal and validate mode's verdict. The order of findings stays as it is today on
each surface. The codes and wording change only where the operator ruled: the unmodelled-file ruling (User Story 3),
and the per-fault unification of R2, listed in WP01's golden snapshot and in the `### Breaking` fragments.

**Why this priority**: the contract is a "must never go wrong" (charter), and open-packs' expected-code fixtures pin
the codes.

**Independent Test**: The golden snapshot of every surface, taken before any change, is reproduced byte for byte,
apart from the enumerated diff list of NFR-002, each entry citing its cause.

**Acceptance Scenarios**:

1. **Given** the registration scenario with two broken graph documents, **When** it runs, **Then** the
   `drg_schema_error` violations come first, in path order, as today.
2. **Given** a pack with another fault and a manifest mismatch, **When** it is registered, **Then** the manifest
   mismatch is not reported, because manifest membership is judged only when nothing else failed, as today.

---

### User Story 3 - An unmodelled file is warned about, then ignored (Priority: P1)

A pack author adds `notes.txt`, a `LICENSE` or a reference spreadsheet beside the pack content. No pack file kind
models it.

**Why this priority**: the operator's Q1 ruling. Packs carry author aids that do not affect the service.

**Independent Test**: Register a valid pack plus `notes.txt`. The registration succeeds with a `file_not_held` warning
naming `notes.txt`. The stored pack does not hold the file, and the round trip does not see it.

**Acceptance Scenarios**:

1. **Given** a valid pack with `notes.txt`, **When** it is registered, **Then** it is accepted with one `file_not_held`
   diagnostic at `notes.txt`. The stored pack and its revision are those of the pack without the file.
2. **Given** the same pack, **When** it is validated, **Then** the verdict is valid, with one `file_not_held` warning.
3. **Given** a retired `PROVENANCE.md`, or an orphan prompt fragment, **When** it is registered, **Then** it is still
   refused with its own code (`retired`, or the orphan's). The ruling covers only files that nothing models.
4. **Given** a valid pack plus a nested `drg/<kind>.graph.yaml` or a `charter.yaml` (open case 6), **When** it is
   validated and then registered, **Then** both answers carry one `file_not_held` warning at that file, and neither
   judges the file's contents: validate mode reports no `drg_schema_error` or `artifact_schema_error` there.

---

### User Story 4 - Cross-pack conflicts, every one at once (Priority: P2)

An operator composes a set of packs with levels: built-in, org and project. Each composition fault is reported, not
just the first.

**Why this priority**: `pack-check` (#478) runs `CLOSURE` without the graph walk. The service needs it in the same
shape.

**Independent Test**: A set with a dependency cycle and a name conflict reports both, each with its existing code.

**Acceptance Scenarios**:

1. **Given** packs with a cycle and an unknown dependency, **When** the set is composed, **Then** both faults are
   reported, with their codes unchanged.
2. **Given** a set whose only fault is an unresolved qualified reference, **When** `CLOSURE` runs, **Then** it reports
   nothing. Reference checks belong to #556's closure walk.

---

### Edge Cases

- **A typo hidden as an unmodelled file.** For example, `directives/x.yml` where `.yaml` is modelled. It becomes a
  warning and is ignored (Q1). The warning names the file, so the author sees it.
- **A pack whose only faults are unmodelled files.** It is now accepted. Scenarios that used `notes.txt` as a
  convenient second fault are rewritten to use a fault that still refuses.
- **The reserved shard.** Validate mode reports it as `drg_schema_error` today, and registration as `reserved_shard`.
  Under R2 it answers one code on both surfaces, as the R2 table decides (`reserved_shard`, the specific code).
  Validate mode's change goes under `### Breaking`.
- **A pack the inspector cannot read.** It is still `unreadable` or `pack_unreadable`, as today.
- **Slice F's endpoint check.** If F has landed, the graph rule calls F's endpoint bridge, and
  `relationship_endpoint_not_allowed` is unchanged.

## Domain Language

| Term | Meaning | Avoid |
| --- | --- | --- |
| **rule** | A kernel `Rule` that returns every `Reason` it finds | "validator", "check class" |
| **ruleset** | A `ModularRuleset`, itself a rule. Per kind, `PACK` and `CLOSURE` | "policy" for the new code |
| **finding** | One `Reason`: a code, its severity, a location and a detail | "breach", "error" |
| **validate mode** | Judging a pack without registering it: the validation edge's `PackCheck` in `pack-validation` | "dry run" |
| **composition** | A named `ModularRuleset` built with `must` (and `on` to lift a rule onto a larger subject) from smaller ones: `content()`, `holding()`, `registration()`, `closure()` | "surface profile", "mode flag" |
| **census** | The one storage-edge account of a pack tree's files: held, not held (with its kind and reason), the manifest and the reserved-shard stamp | "classification" for the held set |
| **not held** | A file the registration removes before storing, reported as `file_not_held` | "skipped", "dropped" |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Per-kind rulesets | As a maintainer, I want one ruleset for each pack file kind: the descriptor, graph, mission type, step, artifact, asset, fragment and prompt slot. Each expresses today's consistency checks for its kind, in the `packs` domain (R3), so that a rule exists once. The format prechecks are the one reader's (FR-017), and the storage rules are the storage edge's (FR-002, S3). The glossary has no content rule: its checks are the reader's prechecks and format rules, a retired shape, or `GraphBinding` faults (S2; WP06 D1). The asset escape is the reader's sidecar check alone, never a second domain rule (S2; WP06 D2). | High | Open | [build] | no |
| FR-002 | `PACK` as named compositions | As a maintainer, I want the per-kind rulesets composed into named compositions, with no surface profile and no mode flag, each one type-checking against the kernel (`ModularRuleset<T>.must(Rule<T>)`, `Rule<T>.on(Function<? super S, ? extends T>)`, `Rationale.and(Rationale)`): `PackRulesets.content()`, a `ModularRuleset<PackContents>` in `packs.domain.rules` (graph, endpoint, mission type, step enhancement, orphan fragments, prompt slot, glossary, asset escape and repeated asset id); `StorageRulesets.holding()`, a `ModularRuleset<TreeRead>` in `packs.infra.storage.rules` (not held, reserved shard, the manifest a regular file; S3); `StorageRulesets.registration()` = `holding().must(PackRulesets.content().on(TreeRead::contents))`, a `ModularRuleset<TreeRead>`. Manifest membership is a plain function, `StorageRulesets.manifestMembership(TreeRead)`, run only when the read faults and `registration()` found no `VIOLATION`, as `WholePackStore.loadStaged` does today; no kernel combinator expresses the gate (`must` is collect-all). `StorageRulesets.verdict(TreeRead)` joins them into one `Rationale`: the read faults, then `registration().check(read)`, then the gate's reasons when those pass, in registration's wire order. The domain port `PackReading` returns that `Rationale` in its `PackReadout` (S6, FR-016). The compose read runs only the store half of the one reader (`StoreRead`, S7) inside the storage edge and answers from its assembly facts and faults; registration takes the readout's `Rationale`; validate mode takes the same `Rationale` and appends the placement rules' with `Rationale.and` (FR-005). So registration's findings are a subset of validate mode's by construction (S1 to S3, S6). | High | Open | [build] | no |
| FR-003 | `CLOSURE` ruleset | As an operator, I want a `CLOSURE` ruleset over the composition facts of a set of packs with levels. It covers the duplicate id, the dependency cycle, the unknown and out-of-range dependency, the parent, the level, and the name, sibling and preset conflicts. It reports every fault and keeps today's codes. The offering keeps its first refusal in today's order, with its pack list. | High | Open | [build] | no: a valid set on the same fixture reports nothing |
| FR-004 | Plain functions stay plain | As a maintainer, I want classification, ordering and precedence, and the manifest set difference kept as plain functions that rules call, so that a rule adds no code where a function suffices. | Medium | Open | [ratchet] | yes: covered by FR-001 and FR-002's tests, not by its own |
| FR-005 | Validate mode is `pack-validation`'s edge | As a pack author, I want validate mode to be the validation edge in `pack-validation`: `PackCheck`, moved from `packs.domain.PackValidation`, runs the one read (FR-016) through the domain port `PackReading` its caller supplies (S6) and gets one `PackReadout`. It checks the placement rules, a `ModularRuleset<Placement>` over `Placement(PackReadout read, the configured level and org facts)`, and merges the two results with the kernel's `Rationale.and`: `read.rationale().and(placement().check(placement))`. The manifest gate already ran inside the readout's `Rationale` (FR-002), so placement's reasons never gate it. Placement (the configured level and the org fragment) is the edge's own extension (S2), with its tokens in `PlacementCodes`. `PackVerdicts` maps its findings. So validate mode finds what registration finds, and more. `PackCheck` names `packs.domain` and the kernel only (C-003). The service runs no `pack-validation` main code; the CLI entry stays with #478. | High | Open | [build] | no: the load-only fault is found in validate mode, where it was missed before |
| FR-006 | Registration judges the one read before assembly | As a maintainer, I want registration to read the staged tree once through the domain port `PackReading` (S6) and refuse through `PackRefusals` when the readout's `Rationale` (the read faults, `registration()` and the manifest gate, FR-002) fails, before `PackAssembly` builds the aggregate. The store's `Loader` then assembles from that readout's `PackContents` and removes its not-held files. `packs.application` names no storage type. The aggregate is built only from a read that passed, so there is no second read and no fail-closed guard (S1, FR-016). | High | Open | [build] | no: a reader-only fault is refused before assembly; one read |
| FR-007 | Converters deleted | As a maintainer, I want the converters between `PackFinding`, `PackElementViolation` and `RefusalViolation` deleted, with no replacement shim and no per-surface code table, so that one pipeline remains (R2). | High | Open | [build] | no: a search for the converter methods returns nothing |
| FR-008 | Two mappers, each at its edge, each keeping its order | As a client, I want one mapping from the findings to the API problem and its diagnostics, `PackRefusals` in `packs.application` (the API edge), and one to the validate-mode verdict, `PackVerdicts` in `pack-validation` (the validation edge). They differ only in shape and order, each orders its findings as its surface does today, and neither lives in `packs.domain`. | High | Open | [build] | no |
| FR-009 | Typed, unified codes | As a maintainer, I want every code to be a typed code that owns its severity and its wording: the string sub-codes and validate mode's `PackFindingCode`. One fault answers one code on every surface (R2). Per fault, the specific code wins over a catch-all (`domain`, `reference`, `invalid`, the aggregate `validation_failed`, the refusal at pointer `pack`, `schema` given once per violation). Where registration already answers a specific code, validate mode takes it. | High | Open | [build] | no |
| FR-010 | An unmodelled file is not held | As a pack author, I want a file that no pack file kind models to be reported as a `file_not_held` warning and not held. The registration accepts it, and neither the stored pack nor the round trip carries it (Q1). The `unmodelled` violation code goes. Validate mode reports the same `file_not_held` warning and judges nothing in a file that is not held (open case 6). The packs contract says what the store holds: `**/charter.yaml` and the nested `**/*.graph.yaml` are narrowed to the files the service models, with their classifier cases, under a packs `### Breaking` line. | High | Open | [build] | no: `notes.txt` was refused before and is accepted with a warning after; a nested shard was judged by validate mode before and is a warning after |
| FR-011 | Endpoint check rehomed | As a maintainer, I want the graph rule to call slice F's endpoint bridge, so that #515's check lives in `PACK` with its code `relationship_endpoint_not_allowed` unchanged. | Medium | Open | [build] | no |
| FR-012 | Tests per rule, per composition and per mapper | As a maintainer, I want the tests of `PackPolicy`, the inspector's verdicts, the resolver's refusals and the converters to become one test class per rule and one per mapper, plus `PACK` composition tests (one per gate, suppression or precedence interaction). The order and wording functions keep their whole tests. The tests live in the module of their subject. | Medium | Open | [build] | no |
| FR-013 | Records follow | As a maintainer, I want the records to say what the service does: the `### Breaking` fragments for Q1, for registration's R2 changes, for validate mode's R2 changes and for S2's registration gains; the packs `### Breaking` line for the narrowed globs; the rewritten scenarios; the ADR-0075 amendment (R3 as superseded by S1 to S3 and S6: the validation edge, the one reader, the storage rules, and the domain port `PackReading` that replaces `PackInspector` with no ArchUnit rule widened); the ADR-0062 and ADR-0065 annotations; #554's issue text amended; and #478's text amended with what it inherits (the CLI entry over `PackCheck`, the pack-set check, the `pack-check` module merge). | Medium | Open | [build] | no |
| FR-014 | Open-packs draft | As the orchestrator, I want a draft issue for `spec-kitty-open-packs` in the Mission research (`open-packs#309`). Its expected-code fixtures that expect `unmodelled` change, and its Python port of the rule follows. It also carries the narrowed `charter.yaml` and nested-shard classification (FR-010), and the codes registration gains under R2 and S2. | Medium | Open | [build] | no |
| FR-015 | Composition facts from pack contents | As the orchestrator of #478, I want a Spring-free function that builds each pack's composition facts from its inspected contents. A test checks the facts it builds over the example packs against WP01's golden composition answers (`golden/composition.json`, `CompositionGoldenTest`), not against a second builder, so that `CLOSURE` judges real facts outside the service. | Medium | Open | [build] | no |
| FR-016 | One reader and one held-set census in the storage edge | As a maintainer, I want one reader in `packs.infra.storage`, `FileSystemPackInspector`, that turns a pack tree into one `TreeRead` (`packs.infra.storage.rules`): `PackContents` (storage-agnostic facts, the aggregate's inputs among them), a `TreeCensus` (every file held or not held, each not-held file with an explicit kind and its reason, the manifest and the reserved-shard stamp) and the read faults (parse, schema and S2 precheck faults, as kernel `Reason`s whose code is a kernel `ReasonCode` and whose wording the reader owns). `TreeRead`, `TreeCensus`, the storage rules and `StorageCodes` are named by nothing outside `packs.infra.storage` and `deployable`, and the reader's method that returns a `TreeRead` is package-private. The reader implements the domain port `PackReading` (S6): `PackReadout read(PackRoot root)` returns the `PackContents`, the classified files, the not-held files (`NotHeldFile`) and one kernel `Rationale`, `StorageRulesets.verdict(read)`. A composition root supplies it: `deployable` to registration, and `PackCheck`'s callers to the validation edge. Registration and validate mode judge that one read, and the compose read its store half (`StoreRead`, S7); `PackAssembly` builds the aggregate from its `PackContents` without judging; the composition facts are built from it once (S1, S3). The second read on the registration path, the fail-closed guard and the parity tests that held two readers equal are gone, and there is one owner of "held". | High | Open | [build] | no: a test that counts reads of a staged tree sees one, and the census's not-held list equals the golden's `file_not_held` diagnostics (`golden/surfaces.json`) over the planted and example packs |
| FR-017 | S2: registration gains the content and format checks | As a pack author, I want the repeated asset id judged as a consistency rule in `content()`, and the descriptor version format (`requires_core`, `pack_version`, a `depends_on` range) and the asset mime format judged as prechecks of the one reader (FR-016), so that registration refuses them as validate mode does, with the codes of the R2 table, under one `### Breaking` fragment. The configured level and the org fragment stay validate-only, as the validation edge's placement extension. | High | Open | [build] | no: `requires-core-invalid` and `asset-repeated-id-*` were accepted by registration before and are refused after |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gates green | `./gradlew verifyAll` passes on Java 25, with strict dependency verification and no new dependency. It includes the whole-pack round trip, the contract checks, the style checks, the native build and the architecture gate. `PACK_VALIDATION_IS_FRAMEWORK_FREE`, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and the onion rules are unchanged (S6). The storage rules, which `FileSystemPackInspector` composes, are walked by `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and pinned framework-free by the added `STORAGE_RULES_ARE_FRAMEWORK_FREE`. The single-owner rules point to the new owners, with their bite tests kept. The docs checks pass. | Quality | High | Open |
| NFR-002 | Wire parity | The golden snapshot of every surface (codes, pointers, details, order and diagnostics), taken before any change, is reproduced byte for byte. The only exceptions are an enumerated diff list, each entry citing Q1, an R2 table row, open case 6 (FR-010), S2 (FR-017), S5 (the compose read's repeated artifact id: a 500 becomes `pack_unreadable`, recorded as a fix, R2 open case 3), WP02 review L2 (a retired file is no longer also listed as not held, so its duplicate `file_not_held` warning goes; WP11), open case 6's excluded files (validate mode warns `file_not_held` for an excluded file as registration does, because a file is warned about for not being held, whatever the reason: `example-packs/built-in` and `example-packs/org-base` gain one warning each; WP11 review cycle 1), open case 8 (a planted fixture's defect fixed, not a behaviour change), FR-009's severity spelling (validate mode spells a violation's severity `violation`, not `blocking`, as the wire does; WP04), S8 (registration answers every fault of a pack whose `pack.yaml` is missing or unreadable, not the single descriptor refusal: the 38 descriptor rows of R2 rows 22–24 change when WP12 switches registration), or open case 6's fixture moves (the `c4-shard-urn-prefix-differs-from-kind` and `c16-shard-edge-provenance` plants move their shard to the pack root so C4, C16 and P10 keep a plant; 10 rows change: validate is the old `drg_schema_error` at the root `tactic.graph.yaml`; registration of the `+pack.yaml` variants is refused with `drg_schema_error` (WP01: refused `unmodelled`); the compose read answers `pack_unreadable` "a graph document of qa cannot be read"; WP11 review cycle 1). The forward subset (registration's faults ⊆ validate mode's) holds by construction of the compositions and is still tested; the reverse subset (registration's faults equal today's, apart from the listed rows) is tested over every planted, example and scenario pack. | Quality | High | Open |
| NFR-003 | Mutation margins honest | The nightly run measures `pack-validation` (with #578's round-trip verdict and #554's validation edge: `PackCheck`, `PackVerdicts` and the placement rules, whose tests move with them) and `packs` (with `packs.domain.rules` and `packs.infra.storage.rules`) again, and ADR-0062's documented margins are re-set from it (R3-b (i)). No floor is lowered. Each work package reports a narrowed PIT run, before and after. | Quality | High | Open |
| NFR-004 | No second derivation | Rules read only the `TreeRead` the one reader produced (`PackContents`, `TreeCensus` and the read faults), and the edges read only the `PackReadout` the port returns for that read (S6). They never parse a file again. Review checks it, and a test proves that a rule run reads no file. | Maintainability | High | Open |
| NFR-005 | Every code covered | A test enumerates every `PackCodes`, `StorageCodes` and `PlacementCodes` constant and every `CLOSURE` code, and finds at least one failing and one passing fixture for each. The composition interactions of FR-012 are each covered. The test count is reported, not targeted. | Maintainability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model | The rules run over the `packs` domain objects. There is no parallel model, no legacy reader and no old-string mapper kept (charter; ruling 1). | Technical | High | Open |
| C-002 | Base | Implementation starts on `main` after slice F (#567), #555 (#578) and slice G. This branch rebases onto `main` before implementing, and again whenever `main` moves. | Process | High | Open |
| C-003 | Module direction | `pack-validation` depends on `:packs` and names only `packs.domain` and the kernel: it reaches the storage checks through the domain port `PackReading` (S6), and `PACK_VALIDATION_IS_FRAMEWORK_FREE` is unchanged. `packs.application` names no `packs.infra` type either (`ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`, unchanged). `:packs` main never depends on `pack-validation`, and #578's test edge serves the round-trip oracle. The service does not run `pack-validation` main code: validate mode is the validation edge (FR-005), and `deployable` keeps `pack-validation` on its test classpath only, so the `PackValidation` bean goes. | Technical | High | Open |
| C-004 | Out of scope | Out of scope: #556's external checks (the qualified references, the fallback profile, impact and mutability) and its `ERROR`-severity fail-closed codes; #552's identifier grammar; #555's round-trip move; #478's jar; #535, #518 and #358. | Business | High | Open |
| C-005 | Contract versioning | The 0.x line may break on a minor, with a `### Breaking` CHANGELOG entry (ADR-0022). | Technical | High | Open |
| C-006 | Process | Commits are signed, with no Co-Authored-By trailer and no model names. Integrate `main` by rebase only. Gradle runs in the foreground with `--max-workers=1`, one run at a time. | Process | High | Open |

### Key Entities

- **Pack contents**: the storage-agnostic facts the one reader reads from one pack: its domain objects (`packs.domain`). The read faults travel beside it, in `TreeRead`.
- **Tree census**: the storage edge's account of the same read: the files held and not held, each not-held file with its kind and reason, the manifest and the reserved-shard stamp (`packs.infra.storage.rules`).
- **Tree read**: the one read of a pack tree, `TreeRead(PackContents, TreeCensus, read faults)` (`packs.infra.storage.rules`), produced by `FileSystemPackInspector` inside the storage edge. The storage rules judge it; nothing outside `packs.infra.storage` and `deployable` names it.
- **Pack readout**: what the domain port `PackReading` returns for one read (`packs.domain`, S6): `PackReadout(PackContents, classified files, not-held files, Rationale)`, kernel and domain types only. Its `Rationale` is the storage layer's composed check. Registration and validate mode judge it.
- **Not-held file**: `NotHeldFile(path, kind, reason)` (`packs.domain`): a pack-relative path, its kind (`EXCLUDED`, `UNMODELLED`, or `UNNAMED` for a mapped file the pack does not name: an unnamed `policy.yaml` or an orphan companion; open case 9, WP05) and its reason. The census holds them; the readout carries them, so registration removes them without a storage type.
- **`PACK`**: the ruleset over one pack's contents.
- **`CLOSURE`**: the ruleset over the composition facts of a set of packs with levels.
- **Finding**: a kernel `Reason`, whose code owns its severity.
- **Surface composition**: the named composition a surface runs: the store half of the one reader (`StoreRead`: its assembly facts and faults, `PackContents.Assembly`) for the compose read (S7); `StorageRulesets.verdict(read)` (the read faults, `registration()` = `holding().must(content().on(TreeRead::contents))`, then the manifest gate, a plain function, when clean) for registration, returned through `PackReading`; that same `Rationale` `.and(...)` the placement rules' (a `ModularRuleset<Placement>`) for validate mode (S1 to S3, S6, S7; replaces R1's surface profile).
- **Composition facts**: the `PackUnderResolution` of each pack. A Spring-free function builds them from pack contents, so that `CLOSURE` runs outside the service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Over every planted, example and scenario pack, the registration's faults are a subset of validate mode's, with equal codes and wording, and equal to today's registration faults apart from the S2 rows. — [build] ·
  no-op passable: no
- **SC-002**: The golden snapshot holds, apart from the enumerated diff list. — [ratchet] · no-op passable: yes, so it is
  paired with SC-001, which a do-nothing change fails
- **SC-003**: A pack that differs from a valid one only by a file nothing models is accepted with one warning, and is
  stored identical to the valid one. — [build] · no-op passable: no
- **SC-004**: The three converter families, the string sub-codes, the `Surface` enum and the second read on the registration path are gone from main code. — [build] · no-op
  passable: no
- **SC-005**: #554 is closed with evidence, and epic #549 is ticked. #355's validate-mode half is referenced. —
  [build] · no-op passable: no

## Assumptions

- **An unmodelled file is any file that no rule models.** It is neither retired nor an orphan fragment, which keep
  their own codes. The Markdown fallback of 2026-11-11 (#451) becomes the general rule.
- **"Not round-tripped" is met by not holding the file.** The round trip runs over the stored pack. #555, which runs in
  parallel, is told through the orchestrator.
- **Slice F's shapes.** The relayed facts in research.md are checked against `main` once F merges.
