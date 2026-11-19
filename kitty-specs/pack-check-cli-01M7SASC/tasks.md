# Tasks: pack-check CLI and its service release

**Mission**: `pack-check-cli-01M7SASC` (#478) | **Planning branch**: `feat/478-pack-check` | **Merge target**:
`feat/478-pack-check` (then `main` through PR #608)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/PackCheckReport.yaml](contracts/PackCheckReport.yaml), [quickstart.md](quickstart.md)
**Rulings**: D1 to D6 (spec.md, Rulings); plan-point squad dispositions S1 to S18 and J1 to J12 (research.md).

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | The walk lists no link as a regular file; the root link (#174) and a directory link inside still behave | WP01 | |
| T002 | `pack_entry_linked`: `StorageCodes`, `LinkRules` in `StorageRulesets.holding()`, coverage fixtures | WP01 | |
| T003 | The store half states `pack_entry_linked`, so the configured load refuses a linked pack | WP01 | |
| T004 | Planted link subjects, goldens and the existing link tests follow the rule; #597's registration cases | WP01 | |
| T005 | Contract: the code in `validation_failed`, a scenario, a `### Breaking` fragment; the R2 table | WP01 | |
| T006 | `PackReadout.Facts` (`Built` / `Unreadable`) filled by the one read | WP02 | [P] |
| T007 | `PackRulesets.closure(presetIdsOf)`: one definition of `CLOSURE` with the preset phase | WP02 | [P] |
| T008 | The Rationale→`CharterRefusal` mapping moves to `packs.domain`; `PackRefusals` delegates | WP02 | [P] |
| T009 | Registration cost of the facts measured; construction sites and tests updated | WP02 | |
| T010 | `PackCheck.check(root)` returns the readout and the verdict; `validate` delegates | WP03 | |
| T011 | `PackSetCheck` and `PackSetVerdict`: per pack, then `CLOSURE` over the set as given (D5), D3 skip | WP03 | |
| T012 | The round-trip port `RoundTripping` and its step in the set check | WP03 | |
| T013 | Set-check tests: valid set, dependent pack alone, cycle, unreadable pack branches, round trip | WP03 | |
| T014 | `Arguments`: `--config-root`, repeated `--pack dir[:level]`, `--round-trip`, usage faults | WP04 | [P] |
| T015 | `ConfigRoot`: `<root>/packs/<name>/` discovery; duplicate packs by real path | WP04 | [P] |
| T016 | `Problems`: verdicts and composition faults as contract problems (title, status, cap, packs) | WP04 | |
| T017 | `ReportWriter` and `PackCheckMain`: JSON on stdout, warnings on stderr, exit codes | WP04 | |
| T018 | Module gates: allowlist, ArchUnit exemption by FQCN with control, javadoc, mutation floor | WP04 | |
| T019 | `PackCheckReport.yaml` in the contract, `_index.yaml`, a self-checking `STANDALONE` exemption | WP05 | |
| T020 | The `### Added` fragment for the report schema | WP05 | |
| T021 | Deployable contract test: CLI output validates; problems equal `RefusalHandler`'s | WP05 | |
| T022 | NFR-003 parity: every planted golden subject through `PackCheckMain` vs `surfaces.json` | WP05 | |
| T023 | The jar configuration: excludes by module, consistent resolution, judged by the allowlist | WP06 | |
| T024 | `packCheckJar`: manifest, excludes, generated SLF4J service file, own directory, duplicates fail | WP06 | |
| T025 | Licence relocation per artifact, checked-in texts for the four jars without one | WP06 | |
| T026 | `jarTest` in `check`: child-JVM runs over every path, empty stderr, deny list, marker, reproducible | WP06 | |
| T027 | `service.yml`: build, run and time the jar, upload `pack-check-jar` | WP07 | |
| T028 | `service-release.yml` and `service-release-assets.sh`: 13 files, 6 sums, notes name both jars | WP07 | |
| T029 | `rehearse-service-release.sh`: the new asset set and a refusal without the pack-check jar | WP07 | |
| T030 | ADR-0046 amendment (asset set, core vendor evidence); ADR-0075 landing note | WP07 | [P] |
| T031 | `service-build.md` and a pack-author page for `pack-check` | WP07 | [P] |
| T032 | The open-packs draft issue for its Python mirror (`open-packs#TBD-pack-entry-linked`) | WP08 | [P] |
| T033 | The release follow-through runbook for `service-v0.1.0-rc.2` (FR-012) | WP08 | [P] |
| T034 | The closeout evidence list: issues to close (#478, #597), epic #549 to tick | WP08 | [P] |

## Phase 1 — Foundations in the packs slice (parallel)

### WP01 — Links are refused on every surface (D1, D6, #597)

- **Prompt**: [tasks/WP01-links-refused-everywhere.md](tasks/WP01-links-refused-everywhere.md)
- **Goal**: no read on any surface follows a link inside a pack root, and registration, validate mode and the
  configured load each refuse a link with `pack_entry_linked`.
- **Priority**: P1. **Independent test**: register a staged tree holding a file link, a directory link and an
  unmodelled link, and each is refused `validation_failed` with `pack_entry_linked`. A configured pack with a link does
  not compose.
- **Subtasks**:

  T001 The walk lists no link as a regular file (WP01)
  T002 `pack_entry_linked` in `holding()` (WP01)
  T003 The store half states it (WP01)
  T004 Planted subjects, goldens, existing tests, #597 cases (WP01)
  T005 Contract and R2 table (WP01)

- **Dependencies**: none.
- **Risks**:
  - Existing tests create links: `CompanionFilesTest`, `PackWriteSafetyTest`, `TacticSaveContractTest`,
    `GeneratedPackTree`, `SurfaceGoldenTest`, `PackRefusalsTest`, `PackIdRulesTest` and the commit store's root-link
    case. Each is adapted to the canonical rule, with the reason in the commit, or shown unaffected.

### WP02 — Facts on the one read, one CLOSURE, one refusal mapping

- **Prompt**: [tasks/WP02-facts-closure-mapping.md](tasks/WP02-facts-closure-mapping.md)
- **Goal**: the readout carries the pack's composition facts. `PackRulesets` holds the one `CLOSURE` with presets. The
  Rationale→`CharterRefusal` mapping is shared from `packs.domain`.
- **Priority**: P1. **Independent test**: the readout's facts of each example pack equal `facts/example-packs.json`. An
  unreadable graph yields `Unreadable(pack_unreadable)`. `closure(presetIdsOf)` refuses a preset contest.
  `PackRefusalsTest` is unchanged and green.
- **Subtasks**:

  T006 `PackReadout.Facts` (WP02)
  T007 `PackRulesets.closure(presetIdsOf)` (WP02)
  T008 Shared refusal mapping (WP02)
  T009 Registration cost and construction sites (WP02)

- **Dependencies**: none (parallel with WP01).
- **Risks**: registration computes facts it does not use. Measure it against the registration budget tests; a
  memoized supplier is the fallback.

## Phase 2 — The validation edge

### WP03 — The pack-set check

- **Prompt**: [tasks/WP03-pack-set-check.md](tasks/WP03-pack-set-check.md)
- **Goal**: `pack-validation` runs validate mode per pack, then `CLOSURE` over the set as given, skips it when a pack
  has no facts, and optionally runs the round trip through a port.
- **Priority**: P1. **Independent test**: the five example packs pass, `project` alone fails
  `pack_dependency_unknown`, and an unreadable pack skips `CLOSURE` and is reported once.
- **Subtasks**:

  T010 `PackCheck.check(root)` (WP03)
  T011 `PackSetCheck`, `PackSetVerdict` (WP03)
  T012 The `RoundTripping` port (WP03)
  T013 Set-check tests (WP03)

- **Dependencies**: WP02.

## Phase 3 — The CLI

### WP04 — The CLI module

- **Prompt**: [tasks/WP04-cli-module.md](tasks/WP04-cli-module.md)
- **Goal**: `PackCheckMain` composes the reader and the round trip, parses the arguments, writes the report and maps
  the exit codes.
- **Priority**: P1. **Independent test**: in-process runs of `PackCheckMain.run(args, out, err)` over example and
  planted packs give the documented report and exit code.
- **Subtasks**:

  T014 `Arguments` (WP04)
  T015 `ConfigRoot` (WP04)
  T016 `Problems` (WP04)
  T017 `ReportWriter`, `PackCheckMain` (WP04)
  T018 Module gates (WP04)

- **Dependencies**: WP03 (and WP01 for the link code's report, which only adds a case).

### WP05 — The report contract and its parity tests

- **Prompt**: [tasks/WP05-report-contract.md](tasks/WP05-report-contract.md)
- **Goal**: `PackCheckReport` is an additive contract schema, and real CLI output validates against it. Its problems
  equal what the API renders, and every planted subject gets registration's codes.
- **Priority**: P1. **Independent test**: the deployable contract test and the parity test.
- **Subtasks**:

  T019 The schema and its exemption (WP05)
  T020 The fragment (WP05)
  T021 The deployable contract test (WP05)
  T022 NFR-003 parity (WP05)

- **Dependencies**: WP04.

### WP06 — The Spring-free jar and its proof

- **Prompt**: [tasks/WP06-spring-free-jar.md](tasks/WP06-spring-free-jar.md)
- **Goal**: one runnable `pack-check-<version>.jar` with no Spring, proven on every path in a child JVM, inside
  `check`.
- **Priority**: P1. **Independent test**: `./gradlew :pack-check:jarTest`.
- **Subtasks**:

  T023 The jar configuration (WP06)
  T024 `packCheckJar` (WP06)
  T025 Licences (WP06)
  T026 `jarTest` (WP06)

- **Dependencies**: WP04 (parallel with WP05).

## Phase 4 — Release and records

### WP07 — Release wiring and the ADRs

- **Prompt**: [tasks/WP07-release-and-docs.md](tasks/WP07-release-and-docs.md)
- **Goal**: the service release assembles, checks and attests the pack-check jar. The ADRs and guides record it.
- **Priority**: P1. **Independent test**: `scripts/release/rehearse-service-release.sh` passes with 13 files and
  6 sums, and refuses a missing pack-check jar.
- **Subtasks**:

  T027 `service.yml` (WP07)
  T028 `service-release.yml`, assets script (WP07)
  T029 Rehearsal (WP07)
  T030 ADR-0046, ADR-0075 (WP07)
  T031 Guides (WP07)

- **Dependencies**: WP06.

### WP08 — Drafts, the release runbook and the closeout list (planning artifact)

- **Prompt**: [tasks/WP08-drafts-runbook-closeout.md](tasks/WP08-drafts-runbook-closeout.md)
- **Goal**: the open-packs draft issue, the exact post-merge release steps (FR-012), and the closeout evidence.
- **Priority**: P2. **Independent test**: a reviewer can follow the runbook end to end without other sources.
- **Subtasks**:

  T032 The open-packs draft (WP08)
  T033 The runbook (WP08)
  T034 The closeout list (WP08)

- **Dependencies**: WP01 (for the code's final name and wording).

## Parallelization

- WP01 and WP02 run in parallel.
- WP05 and WP06 run in parallel after WP04.
- WP08 runs any time after WP01.

**MVP**: WP01 to WP04 give a working CLI from a checkout. WP06 makes it a shippable jar; WP07 puts it on the release.
