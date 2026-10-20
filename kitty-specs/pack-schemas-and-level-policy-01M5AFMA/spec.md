# Mission Specification: Pack schemas and pack-level policy in the domain

**Mission Branch**: `feat/pack-schemas-and-level-policy`
**Created**: 2026-10-19
**Status**: Draft
**Input**: Issues [#48](https://github.com/spec-kitty/spec-kitty-charter/issues/48) ("Move the pack-level policy into the domain") and [#114](https://github.com/spec-kitty/spec-kitty-charter/issues/114) ("Import the pack schemas as canonical and validate packs against them in the YAML adapter"), with [#115](https://github.com/spec-kitty/spec-kitty-charter/issues/115) prepared but not implemented. Decision records: [ADR-0017](../../docs/adr/0017-pack-schemas-imported-as-canonical-json-schema.md), ADR-0015, ADR-0016, ADR-0012, ADR-0006, ADR-0008.

## Intent Summary (confirmed)

### How intent was confirmed

This mission started from the operator's written dispatch brief (2026-10-19, mission runner B).
A profile-loaded research squad (architect, implementer, reviewer) grounded both issues on `main`
at `61d0980`, on core at `9554e7b2` and on open-packs. A post-spec squad (reviewer, doctrine)
reviewed the first draft. The operator then answered every open call live (2026-10-19, see
[Operator rulings](#operator-rulings-this-spec-rests-on)). This spec is the confirmed result. The
calls still made without an operator ruling are listed under
[Open review items](#open-review-items-calls-made-without-review), as the charter requires.

### What we are building

Each Charter Pack file kind gets one published schema, owned by this repository (ADR-0015 decision
1, ADR-0017). A pack author, and the charter service on their behalf, validates against it. The
schemas are imported from core's pydantic models, so verdicts on real open-packs content start in
parity with core. Where this repository deliberately differs, the difference is recorded. The
service validates the pack files it reads against these schemas.

Four further changes come with it:

- **Pack-level policy.** It moves wholly into the domain (#48). Today the YAML adapter builds the
  `pack_level_missing` diagnostic and its text, and the domain builds the `pack_level_mismatch`
  refusal.
- **Domain rules.** The rules a schema cannot express live in the domain, as collected findings:
  the PEP 440 grammar of `requires_core`, URN prefix equals kind, unique step ids and the
  cross-file rules.
- **Edge endpoints are URNs everywhere.** A bare id is refused in graph shards and org fragments
  alike, so that resolving an endpoint never depends on what a document happens to declare. The
  one open-packs file that uses bare ids is migrated upstream in the same change.
- **A validate mode collects every finding for a pack.** It does not stop at the first. It is the
  seam #115 (pack doctor parity) builds on. Loading stays fail-fast, as a projection of the
  load-path findings.

### Actors, trigger and outcome

**Primary actors.**
- A pack author, or core's `charter pack validate`, whose pack files must be judged by a published
  schema.
- The charter service loading the configured packs.
- The #115 doctor parity harness, which consumes the collected findings.

**Trigger.** The service loads the configured packs, or a validator is asked to judge a pack root.

**Outcome.**
- Every YAML file of open-packs at the pinned commit is classified: mapped to exactly one schema,
  or on a reviewed exclusion list.
- Every mapped file validates.
- Planted invalid files fail with the expected finding.
- The service's answers change only where an operator ruling below changes them, and each such
  change has a contract scenario.

**Invariants.**
- The pack-level outcomes are decided only in the domain.
- Each structural rule has exactly one authority: the schema or the domain.
- Pinned detail texts change only where a ruling changes the behavior.
- No unverified dependency enters the build.

## Operator rulings this spec rests on

| # | Ruling | Source | Effect here |
|---|--------|--------|-------------|
| R1 | This repository is canonical for the Charter Pack schemas: stay close to the source, deviate for good reason, record every change | ADR-0015 decisions 1, 3, 4 | `contracts/packs/schemas/` plus `VENDORED.md` |
| R2 | Import core's models as JSON Schema; networknt in `adapter-out-yaml` only; domain records hand-written; no generated records | ADR-0017 decisions 1-5 | FR-004 to FR-010, C-002, C-003 |
| R3 | `requires_core` stays in `pack.yaml` and is optional. It is a version-compatibility field, so its value is held strictly to PEP 440 specifier grammar, and a malformed value is a blocking refusal at load (core parity) | ADR-0017 and ADR-0005 decision 9 amended; operator 2026-10-19 | FR-009, FR-013 |
| R4 | A missing `pack_level` is a diagnostic, not a refusal. `pack_version` is required at **every** level (Q13). ADR-0017 decision 2 ("a built-in pack declares `pack_version`") gets a dated amendment citing Q13 | open-questions Q13 (2026-10-18); operator 2026-10-19 | FR-001, FR-005, FR-015 |
| R5 | Unknown keys in `pack.yaml` are **tolerated, with a warning** when the pack is registered: a new diagnostic code on the pack listing (contract change) | operator 2026-10-19 | FR-008, FR-013 |
| R6 | Edge endpoints are **URNs everywhere**, shards and fragments, local references included, refused now. A non-URN endpoint is refused with `drg_schema_error` by the schema's URN pattern; `unresolved_edge_endpoint` is no longer raised (it stays in the enum) | operator 2026-10-19 | FR-005, FR-013, FR-017 |
| R7 | The org-fragment header fields core overwrites from the operator's configuration (`pack_name`, `source_kind`, `source_ref`, `layer_index`, `provenance_marker`) are not part of the authored fragment schema. A fragment carrying them is refused (unknown key), and open-packs drops them | operator 2026-10-19, after evidence (`org_pack_loader.py:648-655`: "the pack-side fragment.yaml's copies are advisory") | FR-005, FR-017 |
| R8 | `generated_by` on graph shards is **optional** here, though core requires it (`drg/models.py:441`). This deliberate laxity is recorded in the register | operator 2026-10-19 | FR-005, FR-014 |
| R9 | All lenient conversions are dropped. These are ADR-0017's two (scalar `role` to `roles`, `schema_version: "1"`) plus role lowercasing and pydantic's lax string-to-integer coercion. The ADR-0017 amendment lists them | operator 2026-10-19 | FR-005, FR-015 |
| R10 | The divergence register lives at `docs/conformance/`. It is the single register ADR-0016 decision 4 means, seeded with this mission's entries and with the three Provisional items of `contracts/charter/CHANGELOG.md` (linked, not moved) | operator 2026-10-19 | FR-014 |
| R11 | Open the open-packs migration PR myself on open-packs `main`. Catch the charter up to `main`'s layout (`built-in/` moved under `packs/built-in/`), re-vendor the packs this repository carries, and pin `openPacksRef` in `gradle.properties` (plan P6) to the PR head until the operator merges it, then to the merge commit | operator 2026-10-19 | FR-011, FR-017, C-004 |
| R12 | Contract scenarios keep inline fixtures, and every scenario change gets a line in the contract `CHANGELOG.md` | open-questions Q15 | FR-013 |
| R13 | Retired `pack.yaml` fields (core's `RETIRED_PACK_FIELD`, such as `accompanies_doctrine_pack`) are treated like any unknown key: a warning, not a refusal. Recorded as laxer than core | operator 2026-10-19 | FR-005, FR-014 |
| R14 | One name per concept in the org-fragment node-kind vocabulary, with no aliases. The kinds are the artifact-kind plurals the service models, plus `mission_types`, `actions` and `anti_patterns`, which have distinct meanings and stay (core refuses the last two). `mission_step_contracts` is the one name for the concept core calls `mission_steps`: in core `mission_steps` is the canonical fragment kind name and `mission_step_contracts` its back-compat alias, so the service's canonical choice is the reverse and it refuses core's canonical `mission_steps` and core's `mission_type` alias. The glossary records the canonical names, and a PlantUML class diagram documents the resulting write model (the authored pack files) | operator 2026-10-19 | FR-005, FR-014, FR-015 |
| R15 | The URN pattern accepts the service's 15 node kinds. Core's extra `glossary` and `glossary_scope` are refused, recorded as a divergence | operator 2026-10-19 | FR-005, FR-014 |
| R16 | Beyond the core domain rules, this mission adds three of core's single-pack rules: duplicate artifact ids within a kind (blocking); the asset checks, path escape and MIME type (blocking); and "an org pack ships `drg/fragment.yaml`" (blocking at load, which changes load behavior for an org pack without one). Org-charter advisories are a #115 follow-up | operator 2026-10-19 | FR-009, FR-013 |
| R17 | A graph shard (`*.graph.yaml`) that is not valid YAML is refused with `drg_schema_error`, as core does; it was `pack_unreadable`. The parse-failure code follows the file kind: `pack.yaml` keeps `pack_unreadable`, a fragment follows the DRG, an artifact is `artifact_schema_error`. Detail: [research R11](research.md#r11-implementation-time-operator-rulings) | operator 2026-10-19, during implementation (WP05 review) | FR-003, FR-008, FR-013 |
| R18 | YAML `~`, `Null` and `NULL` are null, as in core (YAML 1.2 core schema); the reader had read them as strings. No divergence entry. Detail: [research R11](research.md#r11-implementation-time-operator-rulings) | operator 2026-10-20, during implementation (after WP08's core-verdict comparison) | FR-008, FR-012 |
| R19 | An explicit `null` in a `pack.yaml` field typed as a plain string (`pack_id`, `pack_version`, `name`, `pack_level`) is refused with `pack.yaml of <n>: <key> must be a string` (`pack_unreadable`), as core's model refuses it. An absent key keeps "has no <key>" and, for `pack_level`, the `pack_level_missing` diagnostic. `parent_pack`, `requires_core`, `active_charter` and `pack_drg` still accept `null`. Detail: [research R11](research.md#r11-implementation-time-operator-rulings) | operator 2026-10-20, at acceptance | FR-003, FR-008, FR-013 |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The domain alone decides a pack's level (Priority: P1)

The service loads a pack in one of three states: its `pack.yaml` is absent, it has no
`pack_level`, or it declares a level other than the one configured. The answer is the same as
today:
- a `pack_level_missing` diagnostic with today's text for the first two cases;
- a `pack_level_mismatch` refusal for the third.

Only the domain decides it. An adapter only reports what the file says.

**Why this priority**: Issue #48. Otherwise a second adapter would have to re-implement half the
rule. An imported schema that required `pack_level` would refuse the pack before the domain could
decide.

**Independent Test**:
- A domain unit test covers declared-equals-configured, declared-differs and absent, asserting the
  code and the detail text.
- An architecture rule fails if either code is constructed outside the domain. It is red on
  today's code, which builds the diagnostic in two adapter classes.

**Acceptance Scenarios**:

1. **Given** an org pack with no `pack.yaml`, **When** the packs are listed, **Then** the pack
   carries `pack_level_missing` with the detail "org pack qa has no pack.yaml; the configured
   level org is used" (`packs.feature:43`, unchanged).
2. **Given** an org pack whose `pack.yaml` has no `pack_level`, **When** the packs are listed,
   **Then** the diagnostic detail is "pack.yaml of internal has no pack_level; the configured
   level org is used" (`packs.feature:62`, unchanged).
3. **Given** an org pack that declares `pack_level: built-in`, **When** the packs are listed,
   **Then** the answer is the `pack_level_mismatch` refusal (`packs.feature:80`, unchanged).
4. **Given** the production code, **When** the architecture gate runs, **Then** no class outside
   the domain constructs the `pack_level_missing` diagnostic or a `pack_level_mismatch` refusal.

---

### User Story 2 - Every pack file kind has one canonical, published schema (Priority: P1)

A pack author opens `contracts/packs/schemas/` and finds one JSON Schema (Draft 2020-12) per file
kind core models. Each has a `$schema` and a unique `$id`. `VENDORED.md` names the origin: core's
pydantic models at `9554e7b2`, exported with `model_json_schema(mode="validation")`. It also lists
every change made on import, with its reason and the ruling behind it.

**Why this priority**: Issue #114 and ADR-0017 decisions 1 and 2. Every other story validates
against these schemas.

**Independent Test**:
- A build check confirms that every schema is valid Draft 2020-12, every `$id` is unique, every
  `$ref` resolves to a bundled resource, and every file is listed in `VENDORED.md`.
- Regenerating the raw export from core and replaying the listed changes reproduces the committed
  files.

**Acceptance Scenarios**:

1. **Given** the committed schemas, **When** the schema check runs, **Then** all 14 files pass the
   metaschema, and their `$id`s are distinct.
2. **Given** a schema with a dangling `$ref`, an unknown keyword or a missing `$schema`, **When**
   the check runs, **Then** it fails and names the file.

---

### User Story 3 - The service validates the pack files it reads (Priority: P1)

The service loads `pack.yaml`, the graph shards and the org fragment, and validates each against
its schema before mapping it. A file the schema refuses is refused with the existing codes:
`pack_unreadable` for `pack.yaml`, and `drg_schema_error` for shards and fragments. The detail text
keeps today's format, so every contract scenario and adapter test that pins a text keeps it,
except where a ruling changes the behavior.

**Why this priority**: Issue #114, validation in the YAML adapter. Without it the schemas are
documentation that the code can disagree with, the alternative ADR-0017 rejected.

**Independent Test**:
- Planted invalid files, one per rule encoded on import, are refused with the expected code and
  detail.
- Every contract scenario that no ruling changes, every pinned adapter text, and the open-packs
  round-trip pass unchanged.

**Acceptance Scenarios**:

1. **Given** a graph shard with the unknown key `surprise`, **When** the packs are listed,
   **Then** the refusal is `drg_schema_error` with "built-in/directive.graph.yaml: unknown key
   'surprise'" (`packs.feature:115`, unchanged).
2. **Given** a `pack.yaml` with a key the schema does not declare, **When** the packs are listed,
   **Then** the pack loads and carries the new unknown-key diagnostic, which names the key (R5).
3. **Given** an edge whose endpoint is a bare id, in a shard or a fragment, **When** the packs are
   listed, **Then** the refusal is `drg_schema_error` naming the endpoint (R6). The
   `packs.feature:83-99` scenario changes to this.
4. **Given** a `pack.yaml` with `requires_core: banana`, **When** the packs are listed, **Then**
   the refusal is `pack_unreadable` naming `requires_core` (R3).
5. **Given** an org fragment that carries `pack_name`, **When** the packs are listed, **Then** the
   refusal is `drg_schema_error` with "unknown key 'pack_name'" (R7).

---

### User Story 4 - A pack can be judged in full, every finding at once (Priority: P2)

A caller asks for the validation of a pack root. It receives the complete list of findings:
- schema findings for every mapped file;
- domain-rule findings;
- the classification of every YAML file (mapped to a schema, or excluded).

It does not stop at the first finding. Each finding carries a stable code, a severity (blocking or
warning), the file and the place in the file.

Loading a pack runs the same pipeline restricted to the **load-path files**: `pack.yaml`, the graph
shards and the org fragment. The first blocking finding becomes the existing refusal, and the
warnings become diagnostics. Findings on files the load path does not read (artifact files, step
contracts) are reported by validate mode only. They never change a load's answer.

**Why this priority**: It prepares #115 (pack doctor parity) and gives the domain rules one home.
The doctor itself, its CLI and its comparison with core are out of scope.

**Independent Test**:
- A pack with three independent defects across load-path and non-load-path files yields three
  findings in validate mode.
- Loading it yields only the load-path outcome.
- On open-packs, validate mode yields zero blocking findings over all mapped files.

**Acceptance Scenarios**:

1. **Given** a pack with an unknown key in `pack.yaml`, a step contract with a duplicate step id,
   and an edge with a bare endpoint, **When** the pack is validated, **Then** three findings are
   returned: one warning and two blocking, each with its own code, file and place.
2. **Given** the same pack, **When** it is loaded, **Then** the load is refused with the
   bare-endpoint finding's code. The duplicate step id does not affect the load.

---

### User Story 5 - Open-packs proves the schemas, without a vacuous pass (Priority: P1)

Run against the pinned open-packs commit (R11; layout `packs/<pack>/`, built-in included), the
acceptance check classifies every YAML file under `packs/`:

- **409 mapped files** across 13 schemas: pack descriptor 2, graph shard 14, org fragment 4, org
  charter 4, directive 55, tactic 137, styleguide 34, toolguide 24, paradigm 15, procedure 41,
  agent profile 31, mission step contract 28, asset 20.
- **107 excluded files**, each class on a reviewed exclusion list with its reason (no model exists
  in core for the class).

Every mapped file validates. The check fails on any of these:
- a file that is neither mapped nor excluded;
- a change in a per-kind count;
- a checkout at a commit other than the pinned one.

These counts were measured on open-packs `main` at `3fb9a7f`. The migration PR (FR-017) changes
no file count. The check is re-measured at the pinned commit.

**Why this priority**: It is #114's acceptance criterion. Without exact counts and the pin
assertion, it could pass by matching nothing.

**Independent Test**: The check asserts the exact per-kind counts and the pinned commit. Removing
an exclusion entry, or adding an unmapped file class to a copy of the corpus, makes it fail.

**Acceptance Scenarios**:

1. **Given** open-packs at the pinned commit, **When** the acceptance check runs, **Then** 409
   files are mapped with the per-kind counts above, 107 are excluded, 0 are unclassified, and 0
   mapped files fail.
2. **Given** a planted invalid file for each change made on import and each domain rule, **When**
   it is validated, **Then** it fails with the expected finding.
3. **Given** a planted file where core has the same rule, **When** core's validator judges it,
   **Then** core also rejects it, and that cross-check is recorded with the planted files. Where
   the rule is a recorded divergence, core's differing verdict is recorded instead.

### Edge Cases

- **A pack with no `pack.yaml`**: no descriptor schema applies. The domain emits the "has no
  pack.yaml" diagnostic, unchanged.
- **`pack_level`**: it is optional in the authored-file schema, because core injects it before
  validating. When present, it must be one of the three tokens. The domain decides absence and
  mismatch. An unknown token keeps today's `pack_unreadable` text ("declares an unknown
  pack_level"), pinned by a test before it moves.
- **YAML the loader types differently from core**:
  - An unquoted `schema_version: 1.0` is a number in both, and must fail the schema.
  - An unquoted date stays a string here.
  - A duplicate key is refused by the parser before any schema runs.
  - A `NaN` or infinite `impacts` strength stays refused, by a `PackPolicy` rule; `DrgEdge`
    keeps its check as a programming-error guard. It is never left to the validator's number handling.
- **Several schema errors in one file**: the reported error is chosen deterministically, by
  instance path and then keyword, so the same file always gives the same refusal text.
- **A nested tactic under `packs/built-in/tactics/<sub>/`**: core's `validate_pack` does not scan
  it, but its runtime loads it. Here it is validated like any tactic. The difference is recorded.
- **`charter.yaml`**: it is a project file, not a pack file. It has no positive control in
  open-packs. Its schema is imported, with a planted valid and a planted invalid fixture. Whether
  this repository's own `.kittify/charter/charter.yaml` validates is measured and recorded, and is
  not required (the raw export refuses its `activated_glossary_packs`).

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | The domain owns both pack-level outcomes | As a service maintainer, I want the pack descriptor to carry the declared level as an optional value, absent when the file or its `pack_level` is absent. The domain decides the missing diagnostic and the mismatch refusal and builds their detail text, so that no adapter re-implements the policy. | High | Open | [build] | no — FR-002's rule is red on today's adapters |
| FR-002 | Architecture rule confines the pack-level codes | As a service maintainer, I want an architecture rule that fails when a production class outside the domain constructs a `Diagnostic` with `pack_level_missing` or a `CharterRefusal` with `pack_level_mismatch`. The rule checks construction, not mere reference, because the REST adapter's title mapping may still name the code. A negative fixture proves the rule bites, so that the policy cannot leak back out. | High | Open | [build] | no — red on today's code (two adapter construction sites) |
| FR-003 | Pinned texts unchanged unless a ruling changes them | As a contract consumer, I want every pinned detail text to pass unchanged, except those changed by R6 or R7. These are: the three pack-level texts (`packs.feature:43/62/80`); `packs.feature:115`; the open-packs round-trip and production-wiring tests; and the adapter tests that pin structural texts (`GraphDocumentReaderTest` "expected a mapping", "has an empty id", "'impacts' must be a number", "is outside [-1, 1]", "an impacts edge is missing 'impacts'", "a scope edge has no 'impacts'"; and the `PackDescriptorReader` texts "has no <key>", "<key> must be a string", "declares an unknown pack_level", "is not a mapping"). Texts not yet pinned are pinned by a test before the code they cover moves. Domain tests cover declared-equals-configured, declared-differs and absent, so that the refactor is proven to change nothing it should not. | High | Open | [ratchet] | yes — paired with FR-002 and FR-012 on the same load path |
| FR-004 | Canonical schemas imported with provenance | As a pack author, I want 14 JSON Schema (Draft 2020-12) files under `contracts/packs/schemas/`, one per file kind core models, each with `$schema` and a unique `$id`. A `VENDORED.md` names core's models, the commit `9554e7b2`, the export command and every change with its reason and ruling, so that the schemas are owned and traceable here. | High | Open | [build] | no — absent today |
| FR-005 | Changes made on import | As a pack author, I want the imported schemas changed as follows, each change listed in `VENDORED.md`: (a) close every nested section the export leaves open, so that only the free-form maps core types as free-form stay open. (b) Encode the URN grammar, the per-kind URN prefix where a pattern can, and URN-only edge endpoints in shards and fragments (R6). (c) Encode the fragment kind vocabulary. (d) Require `pack_version` at every level (R4). (e) Drop every lenient conversion (R9). (f) Encode the lost validators a schema can express: `routing_priority` 0-100, `max_concurrent_tasks` > 0, and lenient adherence requires `explicit_allowances`. (g) Make `pack_level` optional (core injects it) and keep `requires_core` optional (R3). (h) Remove the fragment header fields core overwrites (R7). (i) Make shard `generated_by` optional (R8). (j) Leave the `pack.yaml` top level open to unknown keys, retired fields included, which the adapter reports (R5, R13). (k) Encode the org-fragment node-kind vocabulary with one name per concept (R14), and the URN pattern over the service's 15 node kinds (R15). (l) Add the `impacts` relation and an optional numeric edge `impacts` strength, which core lacks; ADR-0009 D1 rules that the contract leads (plan P11). (m) Leave edge `provenance`, a merge-time marker in core, out of the authored edge schema (plan P10). | High | Open | [build] | no — each change has a planted file that the raw export judges differently (FR-012) |
| FR-006 | Schema self-check in the build | As a contract maintainer, I want a build check of the schemas: every schema is valid Draft 2020-12; `$id`s are unique; every `$ref` resolves to a bundled resource; unknown keywords are refused (networknt only warns by default, so it is configured to fail); and `VENDORED.md` lists every file. A broken or silently lax schema then fails the build. | Medium | Open | [build] | no — red against a planted broken schema |
| FR-007 | Validator dependency pinned and confined | As a maintainer, I want the JSON Schema validator added as a catalog entry with an exact version and SHA-256 verification metadata generated by Gradle. Only the YAML adapter uses it, and an architecture rule confines it there, so that no unverified dependency enters the build and the domain stays pure. | High | Open | [build] | no — the architecture rule is red against a fixture that uses it outside the adapter |
| FR-008 | Load path validates against the schemas | As a service operator, I want `pack.yaml`, the graph shards and the org fragment validated against their schemas before they are mapped. Failures keep the existing refusal codes and today's detail format, through one mapper keyed on keyword and instance path, never on the validator's message. Keys in `pack.yaml` that the schema does not declare yield the new unknown-key warning diagnostic (R5), built in the domain from the keys the adapter reports. The hand-coded structural checks the schemas now cover are removed, so that each rule has one authority. | High | Open | [build] | no — the planted unknown-key `pack.yaml` gets no diagnostic today and does after |
| FR-009 | Domain rules a schema cannot express | As a pack author, I want the domain to check these rules: the PEP 440 specifier grammar of `requires_core` (blocking, R3); URN prefix equals kind where a pattern cannot; unique step ids in a mission step contract; unique artifact ids within a kind directory; the asset rules (an asset path never escapes the pack, and its MIME type is allowed); an org pack ships `drg/fragment.yaml` (R16); and any other single-pack rules core enforces when it loads a pack, enumerated in the plan from core's source with file:line evidence. No rule is invented here: core at `9554e7b2` deliberately keeps a pack's first declaration of a URN it declares twice. Each rule yields a finding with a stable code, so that these rules live in one place and are testable without YAML. | High | Open | [build] | no — each rule has a domain test that is red before it lands |
| FR-010 | Validate mode collects every finding | As the #115 doctor harness, I want a validate entry point (a pure domain service that #115 composes into a use case) that validates a pack root and returns every finding (code, severity, file, place, detail) plus the classification of every YAML file. The load path stays fail-fast as a projection of the load-path findings only: the first blocking finding becomes the refusal, and warnings become diagnostics. The doctor parity work then has a seam to build on. | Medium | Open | [build] | no — a three-defect pack yields three findings |
| FR-011 | Open-packs classification and validation | As a maintainer, I want an open-packs check, run in `verifyAll` and never skipped, that asserts the checkout's HEAD equals the pinned commit and walks the new layout (`packs/<pack>/`, built-in included). It classifies every YAML file as mapped to exactly one schema or listed on the reviewed exclusion list with a reason. It asserts the exact per-kind counts and that every mapped file validates with zero blocking findings, so that acceptance cannot pass vacuously. | High | Open | [build] | no — exact counts, pinned HEAD; an unclassified file fails |
| FR-012 | Planted invalid files | As a maintainer, I want one planted invalid file for each change made on import and each domain rule. Each is asserted to fail with its expected code, and cross-checked against core's verdict where core has the rule (or recorded as a divergence where it differs), so that every encoded rule is proven to bite. | High | Open | [build] | no — each plant must fail |
| FR-013 | Contract changes, scenarios first | As a contract consumer, I want each wire-visible change written as a contract scenario before its code, with a line in the contract `CHANGELOG.md` (R12). The changes: (a) a new `DiagnosticCode` value for an unknown `pack.yaml` key, with its scenario (R5). (b) The bare-endpoint scenario (`packs.feature:83-99`) changed to `drg_schema_error` (R6), and the `unresolved_edge_endpoint` description in `CharterRefusalCode` marked as no longer raised. (c) A scenario for a malformed `requires_core` refused with `pack_unreadable` (R3). (d) The listing scenario's fragment fixture rewritten to URN endpoints and without `pack_name` (R6, R7). (e) A scenario for an org pack without `drg/fragment.yaml`, refused with `pack_unreadable` (R16). Every org-pack fixture that lacks a fragment is given one. Each change passes the repository's breaking-change check, or the operator is asked before it lands. | High | Open | [build] | no — each new or changed scenario is red before its code |
| FR-014 | Divergence register | As the conformance gate (#72), I want one reviewed register under `docs/conformance/` (R10). It has a human-readable page and a machine-readable file, and each entry gives the surface, core's behavior, the service's behavior, the rationale, the ruling and a status (open, aligned or retired). It is seeded with the three Provisional items of the contract `CHANGELOG.md` (linked) and with this mission's entries: `pack_version` at every level; unknown `pack.yaml` keys warned rather than refused; URN-only endpoints; the dropped fragment header fields; optional `generated_by`; nested tactics validated; nested sections closed; the dropped coercions; retired `pack.yaml` fields warned (R13); the fragment kind vocabulary (R14); the 15 URN kinds (R15); the `impacts` relation and strength (ADR-0009, P11); edge `provenance` not authored (P10). `VENDORED.md` cross-links it, so that #72 and #115 can consult it. | Medium | Open | [build] | no — absent today |
| FR-015 | Docs and decision records follow | As a pack author, I want the following updates, so that the docs and decision records name one authority each: (a) A dated amendment to ADR-0017 that cites Q13 for `pack_version` at every level, extends the dropped-conversion list (R9), records the dropped fragment header fields (R7) and the URN-only endpoints (R6), and corrects its corpus numbers and its list of lost validators. (b) `docs/architecture/descriptors.md` (fix `pack_version` "(built-in)" at line 61; the JSON Schemas win for structure). (c) `docs/architecture/descriptor-schemas.md` (re-source it from `contracts/packs/schemas/`; fix its precedence clause). (d) `docs/architecture/logical-model.md` (link the schemas; endpoint rule). (e) `docs/context/system-context.md` (the pack schemas row). (f) Glossary rows in `docs/context/domain.md` and the full Glossary for **Pack finding**, **Declared level**, **Configured level** and the canonical node-kind names of R14 (**Mission step contract**, **Action**, **Anti-pattern**, **Mission type**), with the refused aliases marked as synonyms to avoid. (g) A PlantUML class diagram of the write model under `docs/architecture/diagrams/`, linked from the descriptor docs (R14). The write model is the authored pack files: pack descriptor, graph shard, org fragment, nodes, edges and artifact files. (h) A follow-up for the per-response refusal-code narrowing from #48. | Medium | Open | [build] | no — absent today |
| FR-016 | #115 parity test design recorded | As the next mission, I want the plan to record the doctor parity test design without implementing it: inputs, the mapping of core's findings onto this repository's codes and severities, and comparison on file, kind, severity and code, never on message text. #115 then starts from a settled seam. | Low | Open | [build] | no — absent today |
| FR-017 | Open-packs migrated and re-pinned | As a maintainer, I want an open-packs PR that migrates every bare edge endpoint of `packs/internal/drg/fragment.yaml` (84 endpoints) to URNs and removes the fragment header fields from the four org fragments (R6, R7), checked against core's own validator. The charter then catches up to open-packs `main`'s layout, re-vendors the packs it carries (`packs/internal`, `packs/qa`), and pins `openPacksRef` in `gradle.properties` (plan P6) to that PR's head until the operator merges it, then to the merge commit (R11). The pinned corpus then satisfies the stricter schemas. | High | Open | [build] | no — at today's pin the URN-only rule refuses the internal fragment |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Full gate green | `./gradlew verifyAll` on Java 25 passes with 0 failures. That includes strict SHA-256 dependency verification, the ArchUnit gate, the contract checks and scenarios, the open-packs round-trip and the new open-packs classification check. | Reliability | High | Open |
| NFR-002 | Validation cost bounded | Validate mode completes over all 409 mapped open-packs files in under 15 seconds on a CI runner. Loading the configured open-packs packs takes no more than 2 seconds longer than before this mission. Both are measured and reported in the PR. | Performance | Medium | Open |
| NFR-003 | Domain rules mutation-tested | PIT, run by this mission on the classes holding the new domain rules (the build runs it nightly only), reports a mutation score of at least 80%. The report is recorded in the mission evidence. | Quality | Medium | Open |
| NFR-004 | Docs gates green | `check_docs.py all`, markdownlint-cli2 0.18.1 and cspell 10.4.0 report 0 errors. | Quality | Medium | Open |
| NFR-005 | No network at validation time | Schema resolution, the Draft 2020-12 metaschema included, reads only bundled resources: 0 network requests during validation, enforced by refusing any `$ref` or `$id` that is not bundled. | Security | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract changes only by ruling | The only contract changes are those FR-013 lists: one new `DiagnosticCode` value, one refusal-code description, the changed or added `packs.feature` scenarios with their `CHANGELOG.md` lines, and fixtures in other scenario files only where the new rules require them. No operation, response shape or other pinned text changes. | Technical | High | Open |
| C-002 | Validator only in the YAML adapter | The JSON Schema validator and its Jackson bridge are used only by `adapter-out-yaml`. The domain and the application ring stay free of them (ADR-0006, ADR-0017 decision 3). | Technical | High | Open |
| C-003 | Hand-written domain records | No records or classes are generated from the schemas (ADR-0017 decision 5, ADR-0008). | Technical | High | Open |
| C-004 | Pinned origins | Schemas are exported from core at `9554e7b2`. Acceptance runs against open-packs at the commit R11 pins. | Technical | High | Open |
| C-005 | Parallel runner A | The REST and read-app layers and the action context belong to the parallel mission (#95, #113). This mission's edits under `contracts/charter/` are the FR-013 ones only, kept minimal, and this branch rebases onto A. | Business | High | Open |
| C-006 | Supply-chain rules | The dependency is added only through the version catalog with an exact pin. Its verification metadata is generated by Gradle (`--write-verification-metadata sha256`) and never edited by hand to get past a fetch error. | Regulatory | High | Open |
| C-007 | Test first | Every FR lands red first. The failing test, rule or scenario is shown failing before the change that turns it green, and the red runs are kept as mission evidence. | Technical | High | Open |

### Key Entities

- **Charter Pack schema**: the JSON Schema for one pack file kind, owned here (ADR-0015).
- **Pack file kind**: the class of a YAML file, decided by its file name. The kinds are the pack
  descriptor, graph shard, org fragment, org charter, nine of the 13 artifact kinds, and
  `charter.yaml`.
- **Declared level**: the `pack_level` a `pack.yaml` states. It is absent when the file or the key
  is absent.
- **Configured level**: the level `config.yaml` assigns a pack.
- **Pack finding**: one judgement on a pack file: code, severity (blocking or warning), file,
  place, detail. A blocking finding on a load-path file becomes a refusal, and a warning becomes a
  diagnostic.
- **Exclusion list**: the reviewed list of YAML file classes that have no schema, each with a
  reason.
- **Divergence register**: the reviewed record of deliberate differences from core (ADR-0016
  decision 4), under `docs/conformance/`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the YAML files of open-packs at the pinned commit are classified (409 mapped, 107 excluded, 0 unclassified), and 100% of the mapped files validate. — [build] · no-op passable: no
- **SC-002**: Every change made on import and every domain rule has a planted invalid file, and 100% of the planted files fail with their expected code. — [build] · no-op passable: no
- **SC-003**: 0 constructions of the two pack-level codes outside the domain. The rule is red before the move and green after. — [build] · no-op passable: no
- **SC-004**: 100% of the pinned texts not changed by a ruling pass unchanged. — [ratchet] · no-op passable: yes — paired with SC-002 and SC-003 on the same load path
- **SC-005**: A pack with N independent defects yields N findings in validate mode (N = 3 in the acceptance case), and only its load-path finding on load. — [build] · no-op passable: no
- **SC-006**: The full gate and the docs gates report 0 failures. — [ratchet] · no-op passable: yes — paired with SC-001 to SC-003

## Open review items (calls made without review)

The plan-point squad reviews each item. Those still open are handed to the operator in the PR.

1. **The closure of nested sections makes the service stricter than core.** Core's pydantic
   default on nested models is `extra="ignore"`. This affects 8 agent-profile sections and 13
   `charter.yaml` sections. No open-packs file is affected, which the probe re-checks on the
   changed schemas. Recorded in the register.
2. **Nested tactics are validated.** Core's `validate_pack` skips them; its runtime loads them.
3. **Schema failures reuse `pack_unreadable` and `drg_schema_error`.** No new refusal code is
   added.
4. **The 107 excluded files have no schema in this mission.** The excluded classes are step,
   index, mission, mission-runtime, governance-profile, expected-artifacts, skill, glossary_pack,
   presets, mission types, pack manifests and `built-in.source.yaml`. Each is a candidate
   follow-up, recorded with its reason.
5. **The name of the new diagnostic code** (proposed: `pack_unknown_key`) is settled in the plan.
6. **Edge `provenance` is not authored** (plan P10). Core stamps it at merge time. Refusing it in
   authored files applies R7's principle. No open-packs DRG edge carries it.
7. **The `impacts` relation and strength are added to the imported schemas** (plan P11). Core
   lacks both. This applies ADR-0009 D1 and ADR-0014 D-2 rather than making a new call.
8. **FR-010's entry point is a pure domain service, `PackValidation`** (plan P12). The
   application-ring use case is left to #115, because the read-app belongs to runner A (C-005).

## Assumptions

- The core venv used for the export carries core at `9554e7b2`, verified by the squad: `charter`
  matches that commit, with pydantic 2.14.1.
- networknt json-schema-validator 3.x targets Jackson 3, which the service already runs (Spring
  Boot 4), so no second JSON stack is added. Its API is confirmed against the jar in a red test
  before the seam is written.
- Core accepts URN edge endpoints in org fragments (`merge.py` resolves a URN directly), so the
  open-packs migration keeps core's verdicts.
- Maven Central intermittently rate-limits this environment. Dependency resolution is retried, and
  verification metadata is never edited to get past a fetch error.
