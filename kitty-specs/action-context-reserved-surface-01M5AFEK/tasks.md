---
description: "Work packages for the action-context read and the reserved 1.0 surface"
---

# Work Packages: Action-context read and the reserved 1.0 surface

**Inputs**: `kitty-specs/action-context-reserved-surface-01M5AFEK/`. That is spec.md,
plan.md, research.md (decisions D1 to D14, squad dispositions §7 and §8), data-model.md,
contracts/contract-delta.md and quickstart.md.

**Tests**: required by the charter. Scenarios come first, test first, with no fake green.
Every behavior work package records a red run, an **assertion failure** in a scenario or
test, before the change that makes it pass. It also records the suite's scenario count
rising.

Subtasks are reference rows. Record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Red scenarios for the full walk (requires, suggests at depth 0/1/2, diamond, non-following, vocabulary, dedup, `hops`, diagnostics, no mission-type walk) | WP01 | |
| T002 | Contract: `Depth.yaml`, `ActionContext.yaml` (`depth` without maximum, `diagnostics`), `GuidanceEntry.yaml` (`hops`), context path description, example | WP01 | |
| T003 | Compat fixtures (compatible request-max raise; breaking response-max raise if ERR) and the no-maximum `ContractShapeTest` | WP01 | [P] |
| T004 | `DoctrineGraph` outgoing index | WP01 | |
| T005 | `ActionContextQuery`: breadth-first walks, provenance, dedup, diagnostics; unit tests | WP01 | |
| T006 | `ContextController` mapping (`hops`, `diagnostics`, depth range) | WP01 | |
| T007 | Shared `ScenarioHttp` seam (refactor, green first); red scenarios: `serviceVersion`, fingerprint follows `config.yaml`/`charter.yaml` | WP02 | |
| T008 | Contract: `ServiceInfo.serviceVersion` (semver pattern), fingerprint inputs text, example | WP02 | |
| T009 | Service version from `gradle.properties` through a generated resource | WP02 | |
| T010 | `ProjectConfigSources` composite fingerprint; `charter.project-config-dir`; startup check | WP02 | |
| T011 | `ServiceInfoQuery` two-digest check; `InfoController` sets the verified header | WP02 | |
| T012 | Red scenarios: `pack_unreadable` (graph shard), `stale_input` (real file change), negative control, one fingerprint header, no fingerprint on refusals; retitle the `internal_error` scenario | WP03 | |
| T013 | Consistent reads: digest, load, digest; re-digest on a failed load; verified fingerprint carried to the response | WP03 | |
| T014 | `CharterHeaders` no longer digests; header set once by reads | WP03 | |
| T015 | `x-refusal` map on all codes; `governance_policy_violation` `@contract @reserved` scenario; enforcing unit test (name-pattern exclusion); README rule | WP03 | |
| T016 | ArchUnit: no test seam in main; contract text for `stale_input`, `OfferingUnreadable` | WP03 | |
| T017 | Red scenarios: 401 (none, wrong, malformed), 403 (`Origin`, `Sec-Fetch-*`, non-loopback `Host`), 401 before 404 | WP04 | |
| T018 | Contract: `securitySchemes`, root `security`, `Unauthorized`/`Forbidden` responses on every operation | WP04 | |
| T019 | `WorktreeGuard` at the highest precedence; token holder; startup refusals | WP04 | |
| T020 | Steps send the token; suite configures one; production wiring test | WP04 | |
| T021 | Refresh: red scenarios, `paths/refresh.yaml`, `RefreshController`, POST in steps and conformance | WP04 | |
| T022 | ADR-0018; ADR-0009, ADR-0011, ADR-0016 amendments; ADR index | WP05 | |
| T023 | `docs/api/service-contract.md` and the new `docs/conformance/divergence-register.md` | WP05 | |
| T024 | Architecture and plan pages brought into line (resolution model, service lifecycle, contract and DTOs, open questions, roadmap) | WP05 | |
| T025 | Contract `CHANGELOG.md` under `1.0.0-SNAPSHOT`, with the Provisional section moved to the register | WP05 | |
| T026 | Docs checks clean | WP05 | |
| T027 | Evidence script: core's `resolve_context` over open-packs for every action at depths 0, 1, 2 | WP06 | |
| T028 | The service's answers for the same cases | WP06 | |
| T029 | Classify every difference against the register; write `evidence/walk-comparison.md` | WP06 | |

## Phase 1: The read (#95)

### WP01: The action-context walk

- **Goal:** `getActionContext` answers the offering's full walk, as decided in D1 to D4 and D13.
- **Priority:** P1.
- **Prompt:** [tasks/WP01-action-context-walk.md](tasks/WP01-action-context-walk.md)
- **Independent test:** `action_context.feature` scenarios at depths 0, 1 and 2 over planted
  graphs.
- **Dependencies:** none.

T001 Red scenarios for the full walk (WP01)
T002 Contract reshape for the context read (WP01)
T003 Compat fixture `compatible-depth-max-raised` (WP01)
T004 `DoctrineGraph` outgoing index (WP01)
T005 `ActionContextQuery` walk and unit tests (WP01)
T006 `ContextController` mapping (WP01)

**Risks:**

- Rewriting, not deleting, the scenarios that promise depth 1 or walk the mission-type node.
- The response `depth` has no maximum.

### WP06: Walk evidence against core

- **Goal:** SC-001's recorded evidence run.
- **Priority:** P2.
- **Prompt:** [tasks/WP06-walk-evidence.md](tasks/WP06-walk-evidence.md)
- **Dependencies:** WP01.

T027 Evidence script over core's library (WP06)
T028 The service's answers for the same cases (WP06)
T029 Classification and write-up (WP06)

## Phase 2: The reserved surface (#113)

### WP02: Service identity and fingerprint inputs

- **Goal:** `serviceVersion` (D7) and the configuration-aware fingerprint (D8). `/info` checks its
  digest twice.
- **Priority:** P1.
- **Prompt:** [tasks/WP02-service-identity-and-fingerprint.md](tasks/WP02-service-identity-and-fingerprint.md)
- **Dependencies:** WP01, which shares the serial lane.

T007 Red scenarios (WP02)
T008 Contract: `ServiceInfo` and fingerprint text (WP02)
T009 Service version resource (WP02)
T010 `ProjectConfigSources` and startup check (WP02)
T011 Two-digest `/info` (WP02)

### WP03: Consistent reads and the refusal pins

- **Goal:**
  - `stale_input` raised for real (D11);
  - `pack_unreadable` pinned by a real input;
  - one verified fingerprint header per 200;
  - `governance_policy_violation` pinned and enforced.
- **Priority:** P1.
- **Prompt:** [tasks/WP03-consistent-reads-and-refusal-pins.md](tasks/WP03-consistent-reads-and-refusal-pins.md)
- **Dependencies:** WP02.

T012 Red scenarios (WP03)
T013 Consistent reads (WP03)
T014 Header set once (WP03)
T015 Reserved pin and enforcing test (WP03)
T016 ArchUnit and contract text (WP03)

### WP04: The worktree guard and refresh

- **Goal:** the required token, the browser refusal and the startup refusals (D9), and
  `POST /packs/refresh` (D10, operator ruling).
- **Priority:** P1.
- **Prompt:** [tasks/WP04-worktree-guard-and-refresh.md](tasks/WP04-worktree-guard-and-refresh.md)
- **Dependencies:** WP03.

T017 Red security scenarios (WP04)
T018 Contract security surface (WP04)
T019 `WorktreeGuard` and startup refusals (WP04)
T020 Steps and suite token (WP04)
T021 Refresh operation (WP04)

## Phase 3: Records

### WP05: Decision records, docs and CHANGELOG

- **Goal:** every decision recorded (D14), and every parallel authority brought into line
  (research §4).
- **Priority:** P1.
- **Prompt:** [tasks/WP05-records-and-docs.md](tasks/WP05-records-and-docs.md)
- **Dependencies:** WP04 and WP06.

T022 ADRs (WP05)
T023 Service contract page and divergence register (WP05)
T024 Architecture and plan pages (WP05)
T025 Contract CHANGELOG (WP05)
T026 Docs checks (WP05)

## Dependency graph

```text
WP01 ─┬─> WP02 ─> WP03 ─> WP04 ─┬─> WP05
      └─> WP06 ─────────────────┘
```

WP02 to WP04 are serial. They share the scenario steps and the Spring wiring, and each owns a
disjoint set of files. A small out-of-map edit is recorded with `add-history`. The
**MVP is WP01**: the #95 decision, shaped and pinned.

## Closeout (after accept and consolidate; not a WP)

- The dated `docs/changelog/YYYY-MM-DD-<slug>.md` entry and its index line (FR-017). This is
  written after the rebase, so the index merges cleanly.
- File the follow-up issues named in research §8 and §9 (for example S6's "loaded set =
  digested set" property test on runner B's class), and cite their numbers in the handover.
