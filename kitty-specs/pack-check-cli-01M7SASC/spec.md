# Mission Specification: pack-check CLI and its service release

**Mission Branch**: `kitty/mission-pack-check-cli-01M7SASC` (target `feat/478-pack-check`, then `main` by PR)
**Created**: 2026-11-19
**Status**: Ready for plan. Decisions D1 to D4 were ruled by the operator through the orchestrator on 2026-11-19, each
as recommended (see Rulings).
**Input**: Issue #478 (slice 8 of epic #549), as the operator ruled it on 2026-11-13 and as #554 (delivered by #594)
handed it the CLI entry, the pack-set check, the module merge, the builder's reachability and the linked descriptors.
Governing records: ADR-0075 decision 9 and its amendments, ADR-0046.

## Intent Summary

A pack author, and spec-kitty-open-packs' CI, need to know whether a set of packs is valid **by the charter's own
checks**, without building or starting the charter service. Today the only way is to build the service from source,
start it and register each pack over HTTP (about 450 lines of harness and six minutes of CI on the consumer side), or
to trust a Python mirror of the classification that drifts.

This mission ships `pack-check`: a standalone jar that runs the same code the service runs (`pack-validation` over the
packs domain), with no Spring on its runtime classpath.

- **Primary actor:** a pack author on their machine, or a consumer's CI job (open-packs gates on it, open-packs#252).
- **Trigger:** `java -jar pack-check-<version>.jar --config-root <dir>` or `--pack <dir>[:<level>]` (repeated), with
  `--round-trip` to add the round trip.
- **Outcome:** one report in the API's problem format and codes, and an exit code: `0` valid (warnings allowed), `1`
  a violation, `2` a usage or I/O failure.
- **Invariant:** one fault answers one code and one wording on every surface. The CLI holds no check of its own; a
  pack the registration refuses is refused by `pack-check` with the same code.
- **Release:** the jar ships with its checksum and provenance on the `service-v*` line (ADR-0046, as this mission
  amends it).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Check one pack on my machine (Priority: P1)

A pack author runs `pack-check --pack ./my-pack:org` (with the packs it depends on, as further `--pack`) and learns
whether the registration would accept the pack.

**Why this priority**: it is the smallest useful slice, and every other story builds on the same entry.

**Independent Test**: run the jar against a charter example pack (valid) and against each golden subject of validate
mode (invalid), and compare codes with the registration's.

**Acceptance Scenarios**:

1. **Given** a charter example pack, **When** I run `pack-check --pack <dir>:<its level>`, **Then** the exit code is
   `0`, the report lists `PACK` among the checks run, and it holds no problem.
2. **Given** a pack with a duplicate artifact id, **When** I run it, **Then** the exit code is `1` and the report holds
   one `validation_failed` problem whose violation is `duplicate_artifact_id` at the file, in the registration's
   wording.
3. **Given** a pack with a file no schema models, **When** I run it, **Then** the exit code is `0` and the warning
   `file_not_held` is written to standard error, not as a problem.
4. **Given** `--pack` names a directory that does not exist, **When** I run it, **Then** the exit code is `2` and
   standard error names the path.

### User Story 2 - Check a set of packs, as a deployment configures them (Priority: P1)

open-packs' CI runs `pack-check --config-root <dir>` over the packs it publishes, so a dependency cycle, an unknown or
out-of-range dependency, a missing parent, a level violation or a name, sibling or preset conflict fails its gate
before merge.

**Why this priority**: it is what open-packs gates on (ADR-0070 as amended; open-packs#252).

**Independent Test**: a config root of charter's example packs passes; planted sets each fail with the code the
service's composition refuses them with.

**Acceptance Scenarios**:

1. **Given** a config root holding charter's example packs under `packs/`, **When** I run `--config-root`, **Then** the
   exit code is `0` and the report lists `PACK` and `CLOSURE`.
2. **Given** two packs that depend on each other, **When** I run them as a set, **Then** the exit code is `1` and the
   report holds a `pack_dependency_cycle` problem whose `packs` name both.
3. **Given** the same set passed as repeated `--pack <dir>:<level>`, **When** I run it, **Then** the report is the same
   as with the config root.
4. **Given** a set where one pack cannot be read into composition facts, **When** I run it, **Then** the exit code is
   `1`, that pack's problem is reported, and `CLOSURE` is not among the checks run (D3).

### User Story 3 - Prove the round trip at my pin (Priority: P2)

open-packs adds `--round-trip` so that a pack whose YAML would lose meaning through the charter's model fails its own
gate, instead of failing charter's nightly drift watch after the merge.

**Independent Test**: an example pack passes with `--round-trip`; a planted lossy file fails with `round_trip_red`.

**Acceptance Scenarios**:

1. **Given** a pack every file of which round-trips by meaning, **When** I add `--round-trip`, **Then** the exit code
   is `0` and `ROUND_TRIP` is among the checks run.
2. **Given** a pack with a file that loses meaning, **When** I add `--round-trip`, **Then** the exit code is `1` with a
   `round_trip_red` violation at that file. No expected-red baseline applies outside charter's own example packs.

### User Story 4 - Download a verified pack-check (Priority: P1)

open-packs pins a `service-v*` tag and downloads `pack-check-<version>.jar`, checking it against `SHA256SUMS` and its
provenance attestation, instead of building charter from source.

**Independent Test**: a release rehearsal (`workflow_dispatch`, and `rehearse-service-release.sh` in CI) assembles the
jar, its `.sha256` and its `SHA256SUMS` line, and the jar starts and checks a fixture pack on a JVM with no Spring on
the classpath.

**Acceptance Scenarios**:

1. **Given** a rehearsal of the release, **When** the assets are assembled, **Then** `pack-check-<version>.jar` and
   `pack-check-<version>.jar.sha256` are among them and `SHA256SUMS` lists the jar.
2. **Given** the assembled jar, **When** it is run with `java -jar` over a fixture pack, **Then** it answers, and no
   `org.springframework` class is in the jar or on its classpath.

### User Story 5 - A linked file cannot smuggle content into a pack (Priority: P2)

A local pack directory whose `pack.yaml`, shard or fragment is a symbolic link is refused, by `pack-check` and by the
registration alike (D1; folds #597).

**Acceptance Scenarios**:

1. **Given** a pack whose `pack.yaml` is a link to a file outside the pack, **When** I run `pack-check`, **Then** the
   exit code is `1` with the link violation at `pack.yaml`.
2. **Given** a staged registration tree holding a link, **When** it is registered, **Then** it is refused
   `validation_failed` with the same violation code.
3. **Given** a config root whose `packs/<name>` directory is itself a link, **When** I run `--config-root`, **Then** the
   pack is read: the rule judges entries inside a pack root, not the root.

### Edge Cases

- No `--pack` and no `--config-root`, both together, an unknown option or a malformed level: exit `2`, usage on
  standard error, nothing on standard output.
- A config root with no `packs/` directory, or an empty one: exit `2` (nothing to check is a usage fault, not a pass).
- More than 100 violations in one pack: the problem lists the first 100 and its detail counts the rest, as the API does.
- `--pack <dir>` with no level: the level the pack's `pack.yaml` declares, as `--config-root` uses.
- A pack that cannot be read is a `pack_unreadable` violation (exit `1`); exit `2` is for failures outside a pack read
  (writing the output, the round trip's temporary directory), never a partial report with exit `0`.
- A dependent pack checked without its dependencies: `pack_dependency_unknown` (exit `1`); pass the dependencies (D5).
- `--pack <dir>` with no level and no readable declared level: the pack's entry has no `level`, and its fault is
  reported.
- Two `--pack` entries resolving to the same directory: exit `2`.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Issue | Status | Delivery | No-op passable? |
|----|-------------|-------|--------|----------|-----------------|
| FR-001 | `service/pack-check` has a `main` that parses `--config-root <dir>`, a repeated `--pack <dir>[:<level>]` and `--round-trip`, with no CLI framework, and is the composition root that supplies the file-system reader | #478 | Proposed | build | no |
| FR-002 | Per pack, the CLI runs validate mode (`PackCheck.validate`) over the one reader, so its findings are the registration's plus placement, in the same codes and wording | #478 | Proposed | build | no |
| FR-003 | The pack-set check builds each pack's composition facts through a public adapter entry over `CompositionFactsBuilder.underResolution` (outside `packs.domain`) and runs `PackRulesets.closure()` over them | #478 | Proposed | build | no |
| FR-004 | `--round-trip` runs the production round trip (`PackRoundTrip`) and `WholePackRoundTripRule` per pack, with no expected-red baseline | #478 | Proposed | build | no |
| FR-005 | The output is one report naming the checks that ran and the packs checked, whose problems each validate against the contract's `Problem` schema with the API's codes (D2) | #478 | Proposed | build | no |
| FR-006 | Exit codes: `0` valid (warnings allowed), `1` any violation, `2` usage or I/O failure; warnings go to standard error | #478 | Proposed | build | no |
| FR-007 | When a pack cannot be read into composition facts, `CLOSURE` does not run and the report says so (D3) | #478 | Proposed | build | no |
| FR-008 | A symbolic link inside a pack root is refused by the one reader with one violation code, so `pack-check` and registration refuse it alike (D1) | #478, #597 | Ruled (D1) | build | no |
| FR-009 | Whatever `pack-validation` holds for the CLI alone lives in `pack-check`; the jar carries no second copy of a check (verified by inventory: no `pack-validation` type is CLI-only) | #478 | Proposed | folded | yes |
| FR-010 | The release assembles `pack-check-<version>.jar` with its `.sha256`, lists it in `SHA256SUMS` and attests it; the rehearsal script and the asset-set check expect it | #478 | Proposed | build | no |
| FR-011 | ADR-0046 is amended (the asset set gains the pack-check jar) and ADR-0075 records #478's landing; the service-build guide and a pack-author page document the CLI | #478 | Proposed | build | no |
| FR-012 | Post-merge follow-through (D4): the `0.1.0-rc.2` version PR, the `service-native` dispatch at its merge commit, the tag and commit handed to the operator, then the next `-SNAPSHOT` PR; evidence is the PR and run URLs | #478 | Proposed | build | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|----|-------------|-----------|--------|
| NFR-001 | Spring-free runtime | The jar and its runtime classpath hold zero `org.springframework`, `org.apache.tomcat` or `jakarta.servlet` classes; a test runs the built jar on that classpath | Proposed |
| NFR-002 | Speed on a pack author's machine | Checking all of charter's example packs as a set, with `--round-trip`, completes in under 20 s wall time on a CI runner (the service route takes about 6 min including the build) | Proposed |
| NFR-003 | Parity with registration | For every planted golden subject, the codes in `PackCheckMain`'s report equal the registration's codes recorded in `surfaces.json`: 100 % of subjects | Proposed |
| NFR-004 | Jar size | The jar is under 40 MB | Proposed |
| NFR-005 | Gates | `./gradlew verifyAll` on Java 25 passes, with the ArchUnit gate, the mutation floors of the touched modules and the docs checks | Proposed |

### Constraints

| ID | Constraint | Status |
|----|-----------|--------|
| C-001 | No change to composition or registration semantics, except the link rule of D1 | Active |
| C-002 | `pack-check` names `pack-validation`, the packs domain and the kernel; its `main` alone may name the file-system adapter and the facts adapter (`PACK_VALIDATION_IS_FRAMEWORK_FREE` and `ONION_DEPENDS_INWARD`, each with a known-good control). No ArchUnit rule is widened otherwise | Active |
| C-003 | The charter's model is canonical: tests and examples use the canonical shape, except error-handling tests | Active |
| C-004 | No new build plugin or unverified dependency (strict SHA-256 verification) | Active |
| C-005 | The service-v* asset names of ADR-0046 do not change; the jar is additive | Active |
| C-006 | The graph walk, impact, mutability, fallback-profile and required-floor checks stay in the service (operator ruling, 2026-11-13); #556 can extend `CLOSURE` without a CLI change | Active |
| C-007 | No tag is pushed from this session; the operator pushes it | Active |

## Success Criteria *(mandatory)*

- SC-001: A pack author checks a set of packs with one downloaded file and one command, with no service build or start
  — build · no-op passable: no
- SC-002: Every invalid golden subject gets the same codes from the jar as from the in-process check (100 %)
  — ratchet · no-op passable: no
- SC-003: The published jar verifies with `sha256sum --check --ignore-missing SHA256SUMS` and `gh attestation verify`
  — build · no-op passable: no
- SC-004: A link inside a pack is refused with one code by `pack-check`, by a registration of a staged tree and by the
  service's load of a configured pack; real uploads are refused earlier by their stagers — build · no-op passable: no

## Key Entities

- **Pack check report**: the checks that ran (`PACK`, `CLOSURE`, `ROUND_TRIP`), the packs checked (directory, level),
  the problems.
- **Problem**: the contract's `Problem` (RFC 9457), with its code, violations or packs.
- **Pack under resolution**: what `CLOSURE` judges of one pack of a set: its descriptor, level, changed and defined
  artifacts, held terms.

## Domain Language

- **validate mode** — `PackCheck.validate`, the per-pack check; not "lint".
- **pack set** — the packs checked together, as a config root or repeated `--pack`; not "workspace".
- **CLOSURE** — the descriptor-level composition rules; not the closure walk, which pack-check does not run.

## Rulings (operator, through the orchestrator, 2026-11-19)

- **D1 (A):** every symbolic link inside a pack root is refused with one new violation code in the one reader, so
  registration refuses it too; #597 folds in and closes. The pack root itself may be a link. The contract change carries
  a `### Breaking` CHANGELOG note (ADR-0022).
- **D2 (A):** a `PackCheckReport` envelope, an additive contract schema; its problems are the contract's `Problem`.
  Warnings go to standard error.
- **D3 (A):** when a pack cannot be read into composition facts, `CLOSURE` is skipped and the report says so; the
  unreadable pack is a violation (exit 1).
- **D4 (A):** the first release with the jar is `service-v0.1.0-rc.2`, through a small release PR after the mission;
  the orchestrator is told the tag and commit once the native run is green.

- **D5 (A, ruled 2026-11-19):** `CLOSURE` runs over the set as given; a
  pack is checked with the packs it depends on.
- **D6 (A, ruled 2026-11-19):** no read follows a link on any surface, and the
  store half states `pack_entry_linked`, so the service's load of a configured pack refuses it too.

## Assumptions

- `--config-root` follows the service's layout: packs under `<root>/packs/<name>/`, each at the level its `pack.yaml`
  declares.
- Core's service pin reads `SHA256SUMS` with `--ignore-missing`, so an added line does not disturb it (ADR-0046: adding
  an asset is additive).
- #556 is not waited for; `CLOSURE` is already collect-all.
