# Mission Specification: pack-doctor coverage

**Mission Branch**: `kitty/mission-pack-doctor-coverage-01M7YMQK` (merges into `feat/pack-doctor-coverage`, PR to `main`)
**Created**: 2026-11-21
**Status**: Draft
**Input**: #629, #630, #632 and #614 (epic #549), with the operator's design rulings R-a to R-s
(`decisions/`) and the research in `research/rule-coverage.md`.

## Purpose

Open-packs still runs a Python copy of the charter's cross-pack rules. Some faults pass the standalone pack checker
but are refused by registration. Others pass both, though the operator now rules they must be refused. ADR-0075
decision 9 asks for one answer on every surface, and ADR-0082 asks that the domain own every rule and every walk.
This mission:

- gives registration and the pack checker the same verdict for the same pack;
- renames the checker to **pack-doctor**, with a `validate` and a `remediate` command;
- lets pack-doctor write a structured report and repair a pack's manifest and canonical form.

The aim is that open-packs can retire its Python rules.

## Domain Language

| Term | Meaning | Avoid |
|---|---|---|
| pack-doctor | The standalone CLI and jar, formerly pack-check. It has two commands, `validate` and `remediate`. | pack-check (retired name, no alias) |
| own closure | A declaring pack, plus every pack reached from it over `depends_on` and `parent_pack`. Its dependents are not part of it. | composed closure (the closure a read composes) |
| declared | An id of a kind exists in a closure as a graph node or as an artifact file. | defined |
| held | An id of a kind exists in a closure as an artifact file. | present |
| preset item | One entry of a preset's `activated_<kinds>` list. A `must_have` item is a required one. | activation token |
| full pass | `validate --full`: the default validation plus three domain checks that registration also runs, each per own closure: substep delegations, references and enhancement targets (ruling R-s). The `specializes_from` cycle, relation, edge-index and own-closure-walk checks are not in it; they join the factory with #636 slices 2-4 and 7. | deep check; "everything registration judges" |
| anti-pattern file | An anti-pattern authored as its own artifact, `anti_patterns/<id>.anti-pattern.yaml` (ADR-0052 decision 8, as amended by ADR-0053 decision 30). It is the only form of an `anti_pattern`, and what holds one. A graph node of the same URN in the same pack declares it and is a `rejects` edge's endpoint, but holds nothing; a node without the file is refused (ADR-0052 decision 14, ADR-0053 decision 31; ruling R-r). | inline anti-pattern (the retired `anti_patterns` entry of a styleguide or procedure, now refused as an unknown key); file-less node |
| `rejects` edge | The graph edge by which a guidance artifact (directive, tactic, styleguide, toolguide, paradigm, procedure) rejects an anti-pattern (ADR-0064 decision 12). Across packs, its target is pack-qualified and the target's pack is in the rejecting pack's `depends_on` (ADR-0052 decision 11). Activation never follows it (ADR-0052 decision 13). | rejects list, `references` entry of `type: anti_pattern` |

## User Scenarios & Testing

### User Story 1 - A preset that names what its closure lacks is refused everywhere (Priority: P1)

A pack author lists an id in a preset, and the pack's own closure does not hold or declare it. Registration and
pack-doctor refuse the pack with the same specific code, naming the preset, the item, its kind and the closure.

**Why this priority**: #629 and #632 item 1 are P1 critical-path gaps. Today both surfaces accept the pack.

**Independent Test**: Plant each case in a copy of the example packs. Run `pack-doctor validate` and a registration
over it, and compare the codes.

**Acceptance Scenarios**:

1. **Given** a preset whose `must_have` item no pack of the own closure holds as a file, **When** the pack is
   registered or validated, **Then** it is refused with `preset_item_not_held` at that item. The answer is the same
   whether the item is declared by no pack at all, or only by a pack outside the closure.
2. **Given** a preset item that no pack of the own closure declares as its kind, **When** the pack is registered or
   validated, **Then** it is refused with `preset_item_undeclared` at that item, and gets no second problem for the
   same item.
3. **Given** a preset item that differs from a held id only in letter case, **When** the pack is judged, **Then** the
   existing case-only answer stands, and it is the item's only problem.
4. **Given** a pack already stored whose preset now breaks the rule, **When** a composing read runs, **Then** it is
   not refused, and `requiredNotHeld` still reports the unheld item.

### User Story 2 - A stale manifest hash is refused everywhere (Priority: P1)

A pack's `pack-manifest.yaml` carries a `content_hash` or `manifest_hash` that does not match the pack's bytes.
`pack-doctor validate` refuses it without `--round-trip`, and whole-pack registration refuses it too.

**Why this priority**: #630, P1. Today only `--round-trip` notices it, and only under a generic code. Registration
silently restamps the hashes.

**Independent Test**: Append a line to a file that the manifest lists, then validate and register the pack.

**Acceptance Scenarios**:

1. **Given** a stale `content_hash`, **When** the pack is validated, **Then** the exit code is 1. There is one
   `manifest_stale` problem at `pack-manifest.yaml`, naming `constituents{<path>}.content_hash`.
2. **Given** the same pack validated with `--round-trip`, **Then** the cause still gives one problem: there is no
   `round_trip_red` beside the `manifest_stale`.
3. **Given** the same pack uploaded for whole-pack registration, **Then** it is refused with `manifest_stale`.
4. **Given** a pack with no manifest, or a per-artifact write, **Then** nothing changes: a write still stamps the
   manifest.

### User Story 3 - pack-doctor validate judges what registration judges (Priority: P1)

Open-packs CI runs `pack-doctor validate --full` over its packs. It gets every fault registration would refuse,
including the profile references, the enhancement target steps and the substep delegations that the checker misses
today (ruling R-s). The `specializes_from` cycle check is not part of `--full` yet: it needs #636 slices 2-4, and
registration keeps refusing it.

**Why this priority**: #632 item 3. Today ADR-0075 decision 9 is broken in the opposite direction.

**Independent Test**: Plant each of the three faults (a reference, an enhancement target, a delegation). Assert
`--full` exits 1 with the registration's code, and that the default run (without `--full`) does not report them.

**Acceptance Scenarios**:

1. **Given** a profile's bare reference held only by another pack, **When** `validate --full` runs, **Then** it
   reports `pack_reference_unqualified`, as registration does.
2. **Given** a step enhancement whose target step does not exist, **When** `validate --full` runs, **Then** it
   reports the registration's `pack_reference_*` code.
3. **Given** a substep delegation candidate that names a pack of the own closure that does not hold it, **When**
   `validate --full` runs, **Then** it reports the registration's `pack_unreadable` with the violation
   `substep_delegation_unresolved` at the mission type file.
4. **Given** a set with several such faults, **Then** every one is reported, not only the first.

### User Story 4 - The checker is pack-doctor, with a report file (Priority: P2)

A user runs `pack-doctor validate [--full] [--round-trip] [--report <file> [--format yaml|xml]]` and
`pack-doctor remediate <pack>...`. Nothing named `pack-check` remains as a live surface: no jar, option, module or
release asset.

**Acceptance Scenarios**:

1. **Given** `--report out.yaml`, **When** validation finishes, **Then** the file holds:
   - the tool version and the inputs;
   - the checks run and the checks skipped;
   - each pack's problems, each with its code, severity, file, pointer and detail;
   - a summary with counts and the exit code.

   Stdout keeps the problem JSON.
2. **Given** `--format xml`, **Then** the same report model is written as XML. Both formats are checked against a
   schema.
3. **Given** a release, **Then** it carries `pack-doctor-<version>.jar` and its checksum, and no `pack-check` jar.

### User Story 5 - remediate repairs the manifest and the canonical form (Priority: P2)

A user runs `pack-doctor remediate <pack-dir>`. It derives and stamps `pack-manifest.yaml` the way every service
write does, and rewrites each modelled file in the round trip's canonical form. A following `validate` then reports
no `manifest_membership`, no `manifest_stale` and no round-trip form finding.

**Acceptance Scenarios**:

1. **Given** a pack with a stale or incomplete manifest, **When** it is remediated, **Then** a following validation of
   the pack (with `--round-trip`) is clean of manifest and form findings, and the pack's meaning is unchanged
   (ADR-0036).
2. **Given** a pack with a fault that remediate does not repair (a domain fault), **When** it is remediated, **Then**
   the command refuses with exit 1, names the fault, and writes nothing.
3. **Given** a stray file the census does not hold, **Then** remediate leaves it untouched.

### Edge Cases

- A preset item that is declared but not held, and not `must_have`, is accepted.
- An item of one kind does not satisfy a preset entry of another kind.
- A preset item held only by a dependent pack, which is outside the own closure, is refused at the declaring pack.
- A `parent_pack` that holds the item satisfies it.
- An `anti_pattern` item counts as held only through its anti-pattern file. A graph node alone is refused before the
  preset rule runs (FR-014), so a `must_have` anti-pattern held nowhere in the own closure answers
  `preset_item_not_held`.
- An `anti_pattern` node in a graph shard or the fragment, with no `anti_patterns/<id>.anti-pattern.yaml` in the same
  pack, is refused `drg_schema_error` at `/nodes/<n>` of that document. A pack-qualified `rejects` target
  (`other::anti_pattern:x`) is no node of the rejecting pack, so it needs no file there; it needs the target's pack in
  `depends_on` (`pack_reference_pack_missing` otherwise) and the file in that pack (`pack_reference_artifact_missing`).
- An anti-pattern file with no node and no edge is valid: it is held and activatable.
- A styleguide or procedure file that still carries `anti_patterns` is refused `artifact_schema_error`, pointer the
  file root, detail "unknown key 'anti_patterns'", on every surface (registration, `pack-doctor validate`, a
  per-artifact write). Its request form (`StyleguideRegistration`) has no `antiPatterns`, so the request is refused by
  its closed schema.
- Activating a styleguide or procedure that rejects an anti-pattern does not activate the anti-pattern.
- When the round trip and the manifest gate both see the same stale field, there is one problem. A "written stale"
  red, where the writer itself is wrong, still reports.
- A `specializes_from` cycle is not judged by `--full` (R-s); registration still refuses it, and ADR-0083 records it
  as landing with #636.
- `remediate` on a path that is not a pack directory exits 2, as an I/O or usage failure.

## Requirements

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | #614, re-scoped as slice 1 of epic #636: `Origin` on `Reason`; one `ClosureFault.of(...)` factory; `CompositionFault` deleted outright from `CompositionRules`, `PackRulesets` and the checker's problem mapping; `ClosureVerdict` becomes a view over `Rationale` or is deleted. The wire codes and order of findings are unchanged. | Proposed | [build] | no: the type is deleted, and the pinning tests still pass |
| FR-002 | One domain rule judges every preset item against the declaring pack's own closure. A `must_have` item that no closure pack holds as a file answers `preset_item_not_held`. An item that no closure pack declares as its kind answers `preset_item_undeclared`. Each item gets exactly one fault, and the case-only answer wins when it applies. | Proposed | [build] | no: the planted cases fail first |
| FR-003 | FR-002 runs in the default validation (`CLOSURE`'s preset phase) and on every registration and write, through the same domain factory. It never runs on a composing read, where `requiredNotHeld` keeps reporting. | Proposed | [build] | no |
| FR-004 | A stale `content_hash` (of a constituent or a preset) or a stale `manifest_hash` answers `manifest_stale` at `pack-manifest.yaml`, naming the field. It is judged in the storage manifest gate beside `manifest_membership`, so validation and whole-pack registration refuse it. Files that membership already names are skipped. | Proposed | [build] | no |
| FR-005 | With `--round-trip`, the round trip no longer reports a source-stale manifest field, because FR-004 owns it. A written-stale field still reports. | Proposed | [build] | no |
| FR-006 | `validate --full` adds one domain phase to the default validation (ruling R-s): the substep delegation check (`unresolvedDelegations`), the profile and graph reference checks (`referenceFaults`) and the enhancement target check (`EnhancementCompose.targetFaults`), in registration's phase order. Each is an existing domain function, run per pack within its own closure, collecting every fault, with no new walk, and answers the codes registration answers. Registration and the composing read take the first fault of the same collect-all forms. The `specializes_from` cycle, relation, edge-index and own-closure-walk checks are out of this phase; ADR-0083 records them as landing in the factory with #636 slices 2-4 and 7. | Proposed | [build] | no |
| FR-007 | pack-check is renamed pack-doctor: the module, the jar, the main class, the release asset and checksum, the CI steps, the release scripts and the live docs. It has the commands `validate` (with `--config-root`/`--pack`, `--round-trip`, `--full`, `--report`, `--format`) and `remediate`. No alias or old name remains as a live surface. | Proposed | [build] | no |
| FR-008 | `validate --report <file> [--format yaml\|xml]` writes one report model in either format, with YAML as the default. Each format is checked against a schema in `contracts/`. | Proposed | [build] | no |
| FR-009 | `remediate <pack-dir>...` writes each pack's manifest through the service's own derivation and stamp, and rewrites each modelled file in the round trip's canonical form, through a public storage-edge entry point. It judges nothing new. It refuses, writing nothing, when the pack has a fault it does not repair. | Proposed | [build] | no |
| FR-010 | The example packs, the contract scenarios and the whole-pack gate carry the canonical shape, and fixtures are fixed, never exempted. `built-in` gains the anti-pattern artifact its default preset requires, as a file of the kind FR-013 builds, and every other inline anti-pattern of the example packs becomes a file with its `rejects` edge. `org-base` gains, or drops, the two tactics its `spdd` preset activates. Example manifests are restamped. | Proposed | [build] | no |
| FR-011 | The contract gains `preset_item_not_held`, `preset_item_undeclared` and `manifest_stale` with their statuses. The report schemas are added, and the registration scenarios that pinned the old behavior are rewritten. Each comes with a change fragment (`### Breaking` where a valid input is now refused). | Proposed | [build] | no |
| FR-012 | ADR-0083 records rulings R-a to R-s (R-r: all of #154 in this PR; R-s: the narrowed `--full`). It amends ADR-0075 (decisions 6, 7 and 9), ADR-0042 (the 2026-11-12 amendment), rules A10 and C31, and the patterns page's scope table. It records #535 and #52 item 3 as not the charter's to judge. | Proposed | [build] | no |
| FR-013 | The `anti_pattern` kind has a file form (rulings R-p, R-q and R-r; ADR-0052 decision 8 as amended by ADR-0053 decision 30): `anti_patterns/<id>.anti-pattern.yaml`, a closed shape with `schema_version`, `id` (the file stem), `title`, `description`, `severity` (`smell`, `severe` or `breaking`) and `bad_example` required, and `good_example` optional. There is no default severity (ADR-0053 decision 32). The packs contract gains its schema, its `files` rule and a classifier case. The store binds, reads and writes it in one canonical form. Registration and `pack-doctor validate` hold a valid file and refuse an invalid or misplaced one with the existing answers: a missing `severity`, a missing `bad_example`, an unknown severity or an unknown key is `artifact_schema_error` at the file and the breaching pointer; a stem or placement fault is the existing stem and placement answer. Every inline anti-pattern of the example packs moves to a file of `built-in` (18 entries, 18 files) with a node and a `rejects` edge from its owner, and its inline block is removed; `built-in` holds `indirection-for-repetition`, which its default preset requires. | Proposed | [build] | no: a planted file is `file_not_held` before, held after |
| FR-014 | A pack whose graph document (a shard or the fragment) declares an `anti_pattern` node that the same pack does not hold as an anti-pattern file is refused (ruling R-r; ADR-0052 decision 14, ADR-0053 decision 31): one `drg_schema_error` per such node, at `/nodes/<n>` of its document, naming the node and the file it lacks. It is one content rule of `GraphRules`, composed into `PackRulesets.content()`, so registration, every write and `pack-doctor validate` refuse alike. It reads the nodes and files of the one read and walks nothing. | Proposed | [build] | no: the planted node is accepted before |
| FR-015 | The procedure and styleguide shapes drop `anti_patterns` outright (ruling R-r; ADR-0052 decision 16): `procedure.schema.json` and `styleguide.schema.json` lose the property and its `$defs` entry; the domain (`Procedure.antiPatterns`, `Styleguide.antiPatterns`, `AntiPatternEntry`, `StyleguideAntiPattern`), the DSOs, YAML forms and bindings lose it; the charter contract's `Styleguide`, `StyleguideRegistration` and `Procedure` schemas lose `antiPatterns`, and `StyleguideAntiPattern.yaml` and `ProcedureAntiPattern.yaml` are deleted. A pack file that still carries the key is refused `artifact_schema_error` (unknown key) by the closed schema, on every surface. Contract examples, scenarios and test fixtures carry only the new shape. | Proposed | [build] | no: the planted inline block is accepted before |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|---|---|---|---|
| NFR-001 | Each behavior change has a test that is written first and fails before the change. | 100% of FR-001 to FR-006, FR-008, FR-009 and FR-013 to FR-015 | Proposed |
| NFR-002 | pack-doctor's run over the example packs keeps CI's existing time budget. | `validate --round-trip` within the NFR-002 step's 20 s, and `--full` within 30 s | Proposed |
| NFR-003 | No new walk over packs. The own closure comes from the domain's existing closure function. | 0 new `depends_on`/`parent_pack` traversals outside `packs.domain` (ArchUnit or review) | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | ADR-0082 applies: every rule lives in the domain factory (`PackRulesets`) or, for a storage format, at the storage edge. Ports only transform and interface, and no judging port is added. | Fixed |
| C-002 | Canonical model: no deprecation window, legacy reader or alias. | Fixed |
| C-003 | No PIT run in mission work. | Fixed |
| C-004 | Historic records (kitty-specs, kitty-ops, ADR history, changelog entries) keep the old name. The rename covers live surfaces only. | Fixed |
| C-005 | Out of scope: an id re-declared across levels (a layer override, ADR-0053 d6); a bare edge endpoint that no pack holds (DIV-007); a prompt slot outside an activation; #158 beyond `manifest_stale`. These stay open-packs house rules. | Fixed |
| C-006 | Open-packs consequences (the jar repin, the new refusals, `remediate`, retiring Python rules, the report format) are drafted as `open-packs#TBD-<slug>` issues in `research/`, not filed. | Fixed |

## Success Criteria

- SC-001: For every planted fault in this mission's test set, registration and `pack-doctor validate --full` give the
  same code. — [build] · no-op passable: no
- SC-002: Open-packs can retire its Python rules for undeclared preset ids, unheld must_haves, stale hashes, profile
  references, enhancement targets and manifest derivation: six of the rules this mission covers. Its
  `specializes_from` cycle rule stays until #636 lands it in the factory (R-s). — [build] · no-op passable: no
- SC-004: Every #154 refusal has a test and a contract code: a missing `severity` and a missing `bad_example`
  (`artifact_schema_error`), an inline `anti_patterns` block (`artifact_schema_error`, unknown key) and a file-less
  `anti_pattern` node (`drg_schema_error`). The example packs carry no inline anti-pattern and no file-less node.
  — [build] · no-op passable: no
- SC-003: The example packs and the whole-pack gate stay green. — [ratchet] · no-op passable: yes (positive control:
  the planted cases go red)

## Key Entities

- **Preset item fault**: the preset, the item, its kind, whether it is required, and the own closure judged.
- **Anti-pattern**: an artifact file of kind `anti_pattern` (id, title, description, severity, bad example, optional
  good example), rejected by guidance through `rejects` edges.
- **pack-doctor report**: the inputs, the checks run and skipped, each pack's problems, and a summary.

## Assumptions

- The own closure (R-k) is `PackCompositionResolver`'s composition closure rooted at the declaring pack.
- New refusal codes answer 422 at registration, like the other content faults.

## Issues

Closes #614, #629, #630 and #154. Refs #632: items 1 and 2 land (WP03, WP07) and item 3 lands narrowed (R-s); the
`specializes_from` cycle, relation, edge-index and own-closure-walk checks of item 3 remain, for #636 slices 2-4 and 7.
#52 item 3 and #535 are closed by ruling R-g. Parent epics: #549 (ticked for #614, #629, #630) and #142 (ticked for
#154). Kept in view: #158. #154 is delivered whole (ruling R-r): the `anti_pattern` file kind (FR-013), the file-less
node refusal (FR-014) and the inline drop (FR-015). Open-packs' styleguides and procedures with inline entries, and its
13 file-less nodes, are refused at its repin until curated (`open-packs#TBD-anti-pattern-files`, C-006).
