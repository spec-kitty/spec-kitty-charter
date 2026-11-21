# Tasks: pack-doctor coverage

**Mission**: `pack-doctor-coverage-01M7YMQK` · **Planning base / merge target**: `feat/pack-doctor-coverage` (PR to
`main`) · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) (D1-D14)

Execution order (one PR, R-f): WP01 → WP02 → {WP10 → {WP03, WP11, WP12}, WP04} → WP05 → WP06 → WP07 → WP08 → WP09.
WP10 (the `anti_pattern` file kind and the example anti-patterns as files, rulings R-p, R-q and R-r, plan D12) comes
before WP03, which needs the built-in anti-pattern held as a file, and before WP11 (the file-less node refusal, D13)
and WP12 (the inline drop, D14), which need example packs already in the canonical shape. WP03 does not wait for WP11
or WP12. WP05 is no longer held: ruling R-s narrows `--full` to delegations, references and enhancement targets
(plan D5).

Every behavior change is written red-first (NFR-001). No PIT (C-003). Rewrite a test that pins replaced behavior;
never delete or skip it. Fixtures are fixed, never exempted (C-002).

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | `Origin` on `Reason` (kernel), red-first | WP01 | |
| T002 | One `ClosureFault.of(...)`; delete `CompositionFault`; CLOSURE emits `ClosureFault` | WP01 | |
| T003 | `ClosureVerdict` as a `Rationale` view, or deleted | WP01 | |
| T004 | `PackSetVerdict`, `PackSetCheck` and `Problems` carry `ClosureFault`; a deletion guard test | WP01 | |
| T005 | Pinning tests stay green; fixtures and contract examples assert `Origin` | WP01 | |
| T006 | Structural red test: no live `pack-check` surface; the `validate`/`remediate` command words | WP02 | |
| T007 | Module, package, main class and jar renamed (`service/pack-doctor`) | WP02 | |
| T008 | build-logic, settings, the charter-reports allowlist, deployable wiring and ArchUnit names | WP02 | |
| T009 | CI workflows, release scripts and rehearsal; two timed CI steps (NFR-002) | WP02 | |
| T010 | The contract schema renamed `PackDoctorReport`; live docs renamed; a change fragment | WP02 | |
| T011 | `held`/`declared` replace `defined` in `PackCompositionFacts`, the builder and `WriteFacts` | WP03 | |
| T012 | New refusal codes `preset_item_not_held` and `preset_item_undeclared` (kernel, PackCodes, contract, status) | WP03 | |
| T013 | `PresetItemRules` own-closure phase with case-only first; exposed by `PackRulesets` | WP03 | |
| T014 | Wired into CLOSURE (`closure(presets)`) and `ClosureImpactCheck.walkFaults`; never the read | WP03 | |
| T015 | Example packs fixed with positive controls; planted variants | WP03 | |
| T016 | Scenarios: a test-only store seed for A10 and the other `must_have` reads; count guard; new refusal scenarios | WP03 | |
| T017 | Census carries stale fields; `manifest_stale` in `ManifestRules`; `mismatch` returns paths | WP04 | [P] |
| T018 | Round trip drops source-stale and keeps written-stale | WP04 | [P] |
| T019 | Rewrite the tests pinning source-stale and `round_trip_red`; equality assertion | WP04 | |
| T020 | `whole-pack-registration.feature:528-584` rewritten, with a control scenario | WP04 | |
| T021 | Example manifests restamped through `ManifestMembership.stamp` | WP04 | |
| T022 | One body per check: delegations, references, enhancement targets; first-fault callers read the collect-all forms | WP05 | |
| T023 | `validate --full` runs the three checks per own closure in registration order; the check value; the timed CI step | WP05 | |
| T024 | Parameterized parity table: registration vs `validate --full` (SC-001), with the deferred cycle row | WP05 | |
| T025 | Report tree = stdout problem tree plus `tool`, `inputs`, `summary` | WP06 | |
| T026 | YAML and XML serializers; dependency check recorded (D6) | WP06 | |
| T027 | `--report`/`--format` options; the schema gains the new keys; contract test parses XML back | WP06 | |
| T028 | `PackRemediation` facade: canonical rewrite, then stamp, via staged swap | WP07 | |
| T029 | Precondition as one `StorageRulesets` function | WP07 | |
| T030 | `remediate` command wiring; the ArchUnit main rules name a third adapter | WP07 | |
| T031 | Remediation tests: red before, green after, meaning kept, stray file untouched, nothing written on refusal | WP07 | |
| T032 | ADR-0083 (rulings R-a to R-n, the post-plan design, #535/#52.3 rulings) | WP08 | |
| T033 | Amendments: ADR-0075 (d6, d7, d9), ADR-0042, rules A10/C31, the patterns page scope rows | WP08 | |
| T034 | Guides and changelog: `check-packs.md`, docs changelog entry | WP08 | |
| T035 | open-packs#TBD issue drafts (repin, refusals, remediate, retire Python rules, report) | WP09 | |
| T036 | Issue-closure evidence list for the PR body | WP09 | |
| T037 | Packs contract: `anti-pattern.schema.json`, the `files` rule, a classifier case, CHANGELOG and README | WP10 | |
| T038 | Domain `AntiPattern`, `AntiPatternIdentifier`, `AntiPatternSeverity`; `Pack` accessor; `ArtifactKind` fact | WP10 | |
| T039 | `AntiPatternBinding`/`Dso`/`Yaml` appended last; canonical round trip; stem and placement; digest fixture | WP10 | |
| T040 | Built-in `indirection-for-repetition.anti-pattern.yaml`; node kept; manifest stamped; facts re-recorded | WP10 | |
| T041 | Registration and `validate` hold or refuse the file (missing severity, missing bad_example); scenario; `### Breaking` fragment; docs and glossary | WP10 | |
| T042 | The other 17 example inline anti-patterns become files with nodes and `rejects` edges; inline blocks removed | WP10 | |
| T043 | `GraphRules.ANTI_PATTERN_WITHOUT_FILE`: a file-less `anti_pattern` node is `drg_schema_error`, red first | WP11 | |
| T044 | Tests that relied on a file-less node become canonical or refusal tests | WP11 | |
| T045 | Registration and `validate` parity; cross-pack `rejects` and non-cascade pinned; `### Breaking` fragment | WP11 | |
| T046 | The inline-block refusal red first on every surface (scenario, validate, request, schema fixture) | WP12 | |
| T047 | `procedure.schema.json` and `styleguide.schema.json` drop `anti_patterns`; packs CHANGELOG | WP12 | [P] |
| T048 | Domain, storage and API drop the field; tests and fixtures rewritten | WP12 | |
| T049 | The charter contract drops `antiPatterns`; two schemas deleted; examples and scenarios; `### Breaking` fragment | WP12 | |
| T050 | Live docs: descriptors, descriptor schemas, the procedure guide, the conformance register | WP12 | |

## Phase 1 — Foundation

### WP01 — #614: one closure fault type (slice 1 of #636)
Prompt: [tasks/WP01-one-closure-fault-type.md](tasks/WP01-one-closure-fault-type.md) · Priority P1 · Depends on: none

T001 `Origin` on `Reason` (kernel), red-first (WP01)
T002 One `ClosureFault.of(...)`; delete `CompositionFault`; CLOSURE emits `ClosureFault` (WP01)
T003 `ClosureVerdict` as a `Rationale` view, or deleted (WP01)
T004 `PackSetVerdict`, `PackSetCheck` and `Problems` carry `ClosureFault`; a deletion guard test (WP01)
T005 Pinning tests stay green; fixtures and contract examples assert `Origin` (WP01)

Independent test: `:packs:test :pack-validation:test :pack-check:test :deployable:test` green with no
expected-code or order edit, and the deletion guard is red before T002. Risks: the order of findings.

### WP02 — pack-check becomes pack-doctor (the rename, no behavior change)
Prompt: [tasks/WP02-pack-check-becomes-pack-doctor.md](tasks/WP02-pack-check-becomes-pack-doctor.md) · P1 · Depends on: WP01

T006 Structural red test: no live `pack-check` surface; the `validate`/`remediate` command words (WP02)
T007 Module, package, main class and jar renamed (`service/pack-doctor`) (WP02)
T008 build-logic, settings, the charter-reports allowlist, deployable wiring and ArchUnit names (WP02)
T009 CI workflows, release scripts and rehearsal; two timed CI steps (NFR-002) (WP02)
T010 The contract schema renamed `PackDoctorReport`; live docs renamed; a change fragment (WP02)

Independent test: the T006 scan is red before and green after; `verifyAll` and the release rehearsal pass. Risks:
release workflow, asset script and rehearsal must change together.

### WP10 — The `anti_pattern` file kind and the example anti-patterns as files (R-p, R-q, R-r; #154)
Prompt: [tasks/WP10-anti-pattern-file-kind.md](tasks/WP10-anti-pattern-file-kind.md) · P1 · Depends on: WP02

T037 Packs contract: schema, `files` rule, classifier case, CHANGELOG and README (WP10)
T038 Domain entity, identifier and severity; `Pack` accessor; `ArtifactKind` fact (WP10)
T039 Storage binding, DSO and YAML form appended last; round trip; stem and placement; digest fixture (WP10)
T040 Built-in anti-pattern file; node kept; manifest stamped; facts re-recorded (WP10)
T041 Registration and `validate` parity, including the missing-severity and missing-bad_example refusals; scenario; `### Breaking` fragment; docs and glossary (WP10)
T042 The other 17 example inline anti-patterns become files with nodes and `rejects` edges; inline blocks removed (WP10)

Independent test: a planted `anti_patterns/x.anti-pattern.yaml` is `file_not_held` before and held after; one
without `severity` or without `bad_example` is refused `artifact_schema_error` by registration and `pack-doctor
validate` alike; no example styleguide or procedure carries `anti_patterns`; the whole-pack gate stays green. Risks:
the built-in manifest (WP04's) and three styleguides (WP03's glob) are justified out-of-map edits.

### WP11 — A file-less `anti_pattern` node is refused (R-r; #154)
Prompt: [tasks/WP11-anti-pattern-node-refusal.md](tasks/WP11-anti-pattern-node-refusal.md) · P1 · Depends on: WP10

T043 The `GraphRules` rule, red first (WP11)
T044 Tests that relied on a file-less node (WP11)
T045 Every surface; cross-pack `rejects` and non-cascade pinned; fragment (WP11)

Independent test: a planted file-less `anti_pattern` node is accepted before and refused `drg_schema_error` at
`/nodes/<n>` after, by registration and `pack-doctor validate` alike; with its file it registers.

### WP12 — The procedure and styleguide shapes drop `anti_patterns` (R-r; closes #154)
Prompt: [tasks/WP12-inline-anti-patterns-dropped.md](tasks/WP12-inline-anti-patterns-dropped.md) · P1 · Depends on: WP10

T046 The refusal, red first, on every surface (WP12)
T047 The packs schemas (WP12)
T048 Domain, storage and API (WP12)
T049 The charter contract (WP12)
T050 Live docs (WP12)

Independent test: a planted `anti_patterns` block in a styleguide or procedure is accepted before and refused
`artifact_schema_error` (unknown key) after, by registration and `pack-doctor validate` alike; the charter contract
and the service carry no `antiPatterns`.

## Phase 2 — The rules

### WP03 — The preset-item rule (#629, #632 item 1)
Prompt: [tasks/WP03-preset-item-rule.md](tasks/WP03-preset-item-rule.md) · P1 · Depends on: WP02, WP10

T011 `held`/`declared` replace `defined` (WP03)
T012 New refusal codes (WP03)
T013 `PresetItemRules` own-closure phase with case-only first (WP03)
T014 Wired into CLOSURE and `ClosureImpactCheck.walkFaults`; never the read (WP03)
T015 Example packs fixed with positive controls; planted variants (WP03)
T016 Scenarios: a test-only seed, the count guard, new refusal scenarios (WP03)

Independent test: planted unheld `must_have` and undeclared item refused with the same code by `pack-doctor validate`
and registration; reads still report `requiredNotHeld`.

### WP04 — `manifest_stale` (#630)
Prompt: [tasks/WP04-manifest-stale.md](tasks/WP04-manifest-stale.md) · P1 · Depends on: WP02

T017 Census carries stale fields; `manifest_stale` in `ManifestRules` (WP04)
T018 Round trip drops source-stale (WP04)
T019 Rewrite the pinning tests; equality assertion (WP04)
T020 `whole-pack-registration.feature:528-584` rewritten, with a control (WP04)
T021 Example manifests restamped (WP04)

Independent test: a stale hash gives exit 1 with exactly `["manifest_stale"]` with and without `--round-trip`; upload
refused; the whole-pack gate green.

### WP05 — `validate --full`: delegations, references, enhancement targets (#632 item 3, ruling R-s)
Prompt: [tasks/WP05-validate-full.md](tasks/WP05-validate-full.md) · P2 · Depends on: WP03, WP11, WP12

T022 One body per check (WP05)
T023 `validate --full` (WP05)
T024 Parity table (WP05)

Independent test: each planted delegation, reference and target fault gives the registration's code under `--full`
and nothing without it; a `specializes_from` cycle is refused by registration and not yet reported by `--full`.

## Phase 3 — The CLI surfaces

### WP06 — The report file
Prompt: [tasks/WP06-report-file.md](tasks/WP06-report-file.md) · P2 · Depends on: WP03, WP04

T025 Report tree (WP06)
T026 YAML and XML serializers (WP06)
T027 `--report`/`--format`; schema and contract test (WP06)

### WP07 — `remediate`
Prompt: [tasks/WP07-remediate.md](tasks/WP07-remediate.md) · P2 · Depends on: WP04, WP06

T028 `PackRemediation` facade (WP07)
T029 Precondition function (WP07)
T030 Command wiring and ArchUnit (WP07)
T031 Remediation tests (WP07)

## Phase 4 — Records

### WP08 — ADR-0083, amendments and docs
Prompt: [tasks/WP08-adr-and-docs.md](tasks/WP08-adr-and-docs.md) · P2 · Depends on: WP03, WP04, WP05, WP06, WP07, WP11, WP12

T032 ADR-0083 (WP08)
T033 Amendments (WP08)
T034 Guides and changelog (WP08)

### WP09 — Open-packs drafts and closure evidence
Prompt: [tasks/WP09-open-packs-drafts.md](tasks/WP09-open-packs-drafts.md) · P3 · Depends on: WP08

T035 open-packs#TBD drafts (WP09)
T036 Issue-closure evidence (WP09)
