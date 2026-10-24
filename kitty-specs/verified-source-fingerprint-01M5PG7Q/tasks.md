# Work Packages: Verified source fingerprint on every read

**Mission**: `verified-source-fingerprint-01M5PG7Q` · **Branch**: `fix/verified-source-fingerprint` (planning base and merge target)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)

Three work packages, one per ring of the design. WP01 records the design (docs, ADR-0018, contract
prose) and is independent. WP02 builds the one file-set authority and the exact digest. WP03
builds the verified reads and the header, and needs WP02 because its tactic-file stale scenario is
red until the digest covers tactic files.

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | ADR-0018 dated amendment (D8, D11, Risks) | WP01 | [P] |
| T002 | Freshness section of `service-lifecycle.md` | WP01 | [P] |
| T003 | Built surface and headers in `service-contract.md` | WP01 | [P] |
| T004 | Contract prose: header, `ServiceInfo`, refusal codes, `TacticNotFound`, tactic paths | WP01 | |
| T005 | Contract CHANGELOG entry and docs and contract checks | WP01 | |
| T006 | Red first: exactness test (generator, oracle) and the "follows a tactic file" scenario, run against unchanged code | WP02 | |
| T007 | `PackFileSet` with `LoadedKind`, `PACK_YAML`, normalized walk, `files`, `digested` | WP02 | |
| T008 | `FilesystemPackStore` reads only declared kinds through `PackFileSet` | WP02 | |
| T009 | `FileSystemPackSources` digests through `PackFileSet` (production seam), inspector shares shard listing | WP02 | |
| T010 | Planted-omission mutants, `PackFileSet` unit tests, javadoc of the port and the sources | WP02 | |
| T011 | Red first: `tactics.feature` scenarios and the tactic-file drift step, run against unchanged code | WP03 | |
| T012 | `ConsistentRead` and its outcome-table tests | WP03 | |
| T013 | `ServiceInfoQuery` through `ConsistentRead`; production wiring; guard and wiring tests | WP03 | |
| T014 | Verified tactic reads and the header on both 200s; service, controller and wired tests | WP03 | |
| T015 | Scenario wiring (`@Primary ConsistentRead` over `DriftingSources`), drift javadoc, scenarios README, `verifyAll` | WP03 | |

## Phase 1 — Record the design

### WP01 — Record the verified fingerprint in the docs and the contract prose

**Prompt**: [tasks/WP01-record-the-design.md](tasks/WP01-record-the-design.md) · **Priority**: P1 · **Dependencies**: none

**Goal**: The docs, ADR-0018 and the contract's prose say what the service will do: one
definition of the source fingerprint (loaded files plus declared graph inputs plus project
config), every artifact read verified twice and sending the header once, `stale_input` beating a
refusal, a missing pack directory answering 500 `pack_unreadable`.

**Independent test**: `python3 scripts/docs/check_docs.py all`, markdownlint and cspell clean;
`./gradlew :contracts:check` (lint, compatibility) green; no schema, operation or declaration
change in `git diff contracts/`.

Included subtasks:

T001 ADR-0018 dated amendment (WP01)
T002 Freshness section of `service-lifecycle.md` (WP01)
T003 Built surface and headers in `service-contract.md` (WP01)
T004 Contract prose (WP01)
T005 Contract CHANGELOG entry and checks (WP01)

**Risks**: Prose edits that change a contract's shape (keep to `description` text); docs lint.

## Phase 2 — Foundational: one file-set authority

### WP02 — One pack file set for the load and the fingerprint

**Prompt**: [tasks/WP02-one-pack-file-set.md](tasks/WP02-one-pack-file-set.md) · **Priority**: P1 · **Dependencies**: none

**Goal**: `PackFileSet` declares the loaded kinds and the graph inputs; the store reads only
declared kinds and the fingerprint digests exactly `pack.yaml`, the loaded files and the graph
inputs. A generated-tree test observes both sides independently and a planted omission on the
production path fails it.

**Independent test**: `PackFileSetExactnessTest` red on unchanged code (tactic files undigested),
green after; mutants dropping each kind fail it; the "follows a tactic file" scenario red then
green.

Included subtasks:

T006 Red first: exactness test and scenario (WP02)
T007 `PackFileSet` (WP02)
T008 Store through `PackFileSet` (WP02)
T009 Sources through `PackFileSet` (WP02)
T010 Planted omission, unit tests, javadoc (WP02)

**Risks**: Circular oracle (never compute expected sets with `PackFileKind` or `PackFileSet`);
`/info`'s value must not change for packs without loaded-kind files.

## Phase 3 — User stories 1 and 2: verified reads and the header

### WP03 — Verified tactic reads with the source fingerprint

**Prompt**: [tasks/WP03-verified-tactic-reads.md](tasks/WP03-verified-tactic-reads.md) · **Priority**: P1 · **Dependencies**: WP02

**Goal**: `ConsistentRead` verifies every read; `/info` and both tactic reads go through it; the
tactic 200s carry the verified fingerprint once; a changed tactic file mid-read is `stale_input`;
stale beats a 404; a missing pack directory is 500 `pack_unreadable`.

**Independent test**: `tactics.feature` red on unchanged code for the right reason (header
missing; 200 where `stale_input` is expected), green after; `ConsistentReadTest` covers every
outcome row.

Included subtasks:

T011 Red first: `tactics.feature` and drift step (WP03)
T012 `ConsistentRead` (WP03)
T013 `ServiceInfoQuery` and wiring (WP03)
T014 Verified tactic reads and header (WP03)
T015 Scenario wiring and `verifyAll` (WP03)

**Risks**: Bean ambiguity (never inject `PackSources` by type into a use case); `/info`'s pinned
stale body must not change; the drift step must edit a `*.tactic.yaml`, never a shard.

## Parallelism and MVP

- WP01 and WP02 can run in parallel; WP03 follows WP02.
- MVP: WP02 + WP03 (they close both issues); WP01 is the record the operator brief requires
  before code and lands first in the history.
