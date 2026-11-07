# Tasks: Container guard, actuator and single-replica ruling

**Mission**: `container-guard-and-actuator-01M6P99A`. **Planning and merge branch**:
`feat/container-guard-and-actuator`. Spec: [spec.md](spec.md). Plan: [plan.md](plan.md).

Delivery: two pull requests to `main`:

- **Docs pull request:** WP01 (closes #233).
- **Code pull request:** WP02 to WP07 (closes #268, #231 and #232).

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Pick the ADR number against `main` and the open PRs; write the one-replica ADR | WP01 | |
| T002 | Add "single replica" to the container guide's limits | WP01 | [P] |
| T003 | Changelog entry for the ADR | WP01 | [P] |
| T004 | Run the docs checks, the frontmatter description length, and the site's `related:` slugs | WP01 | |
| T005 | `GuardPolicy` value type with factories, normalization, and the exempt-GET rule | WP02 | |
| T006 | `WorktreeGuard` takes a policy: configured hosts, required `Host`, exempt path | WP02 | |
| T007 | `GuardPolicyTest` and the `WorktreeGuardTest` cases for the named policy | WP02 | |
| T008 | Health exemption near-miss tests at the guard (both policies) | WP02 | |
| T009 | `ContainerProperties` element pattern for allowed-host entries | WP03 | |
| T010 | `GuardWiring` passes the policy from `ContainerWiring` | WP03 | |
| T011 | `ContainerPropertiesTest` and `GuardWiringTest` cases for both profiles | WP03 | |
| T012 | Contract 403 description and changelog fragment | WP03 | [P] |
| T013 | `ExposureStartupCheck`: local loopback bind | WP04 | |
| T014 | `ExposureStartupCheck`: management and request-detail rules | WP04 | |
| T015 | `ExposureStartupCheckTest` | WP04 | |
| T016 | Shipped-configuration tests: container never default; missing-secret start | WP04 | [P] |
| T017 | Add `spring-boot-starter-actuator` with verification metadata | WP05 | |
| T018 | Management and JMX settings in `application.yaml`; forwarded headers off under container | WP05 | |
| T019 | `ContainerHealthTest` end to end, both profiles | WP05 | |
| T020 | Token never logged across the new paths, debug logging on | WP05 | |
| T021 | Shared `ConstraintViolationException` → 400 in `ProblemHandler` | WP06 | |
| T022 | Remove the controller-local handler; move and extend its tests | WP06 | |
| T023 | Container guide: allowed hosts, health check, probe `Host`; limits lifted | WP07 | |
| T024 | Compose example: health check and allowed hosts | WP07 | |
| T025 | Changelog entry for the code pull request | WP07 | |

## WP01 – One replica per deployment (ADR) → [tasks/WP01-one-replica-adr.md](tasks/WP01-one-replica-adr.md)

- **Goal:** the operator's ruling of 2026-11-05 on #233 recorded as an accepted ADR. Priority P2.
- **Independent test:** the docs checks and the site build pass, and the ADR states the rule and its reason.
- **Dependencies:** none.

Subtasks:

T001 Pick the ADR number against `main` and the open PRs; write the one-replica ADR (WP01)
T002 Add "single replica" to the container guide's limits (WP01)
T003 Changelog entry for the ADR (WP01)
T004 Run the docs checks, the frontmatter description length, and the site's `related:` slugs (WP01)

## WP02 – Guard policy in the request guard → [tasks/WP02-guard-policy.md](tasks/WP02-guard-policy.md)

- **Goal:** the guard judges hosts against a passed-in policy and exempts one exact `GET` path. P1.
- **Independent test:** packs unit tests with a loopback policy (unchanged) and a named policy.
- **Dependencies:** none.

Subtasks:

T005 `GuardPolicy` value type with factories, normalization, and the exempt-GET rule (WP02)
T006 `WorktreeGuard` takes a policy: configured hosts, required `Host`, exempt path (WP02)
T007 `GuardPolicyTest` and the `WorktreeGuardTest` cases for the named policy (WP02)
T008 Health exemption near-miss tests at the guard (both policies) (WP02)

## WP03 – Container composition, host entries and the 403 text → [tasks/WP03-container-composition.md](tasks/WP03-container-composition.md)

- **Goal:** under `container` the guard gets `allowed-hosts` and the health exemption. Bad entries fail the
  start. The contract's 403 text covers the configured list. P1.
- **Dependencies:** WP02.

Subtasks:

T009 `ContainerProperties` element pattern for allowed-host entries (WP03)
T010 `GuardWiring` passes the policy from `ContainerWiring` (WP03)
T011 `ContainerPropertiesTest` and `GuardWiringTest` cases for both profiles (WP03)
T012 Contract 403 description and changelog fragment (WP03)

## WP04 – Exposure start-up check and profile guard tests → [tasks/WP04-exposure-startup-check.md](tasks/WP04-exposure-startup-check.md)

- **Goal:** the local bind is loopback or the start fails. The management exposure cannot be widened. The
  container profile is never a default. A missing secret fails the start. P2.
- **Dependencies:** none.

Subtasks:

T013 `ExposureStartupCheck`: local loopback bind (WP04)
T014 `ExposureStartupCheck`: management and request-detail rules (WP04)
T015 `ExposureStartupCheckTest` (WP04)
T016 Shipped-configuration tests: container never default; missing-secret start (WP04)

## WP05 – Actuator health, end to end → [tasks/WP05-actuator-health.md](tasks/WP05-actuator-health.md)

- **Goal:** `GET /actuator/health` answers `{"status":"UP"}`, without a token only under `container`, and
  nothing else is exposed. P1.
- **Dependencies:** WP03, WP04.

Subtasks:

T017 Add `spring-boot-starter-actuator` with verification metadata (WP05)
T018 Management and JMX settings in `application.yaml`; forwarded headers off under container (WP05)
T019 `ContainerHealthTest` end to end, both profiles (WP05)
T020 Token never logged across the new paths, debug logging on (WP05)

## WP06 – Shared 400 for method-validation violations → [tasks/WP06-shared-validation-400.md](tasks/WP06-shared-validation-400.md)

- **Goal:** any controller's parameter violation is a plain 400, and any other violation stays a 500. P3.
- **Dependencies:** none.

Subtasks:

T021 Shared `ConstraintViolationException` → 400 in `ProblemHandler` (WP06)
T022 Remove the controller-local handler; move and extend its tests (WP06)

## WP07 – Container guide, compose and changelog → [tasks/WP07-guide-and-compose.md](tasks/WP07-guide-and-compose.md)

- **Goal:** the guide and the compose example match the shipped behaviour. P2.
- **Dependencies:** WP01, WP05.

Subtasks:

T023 Container guide: allowed hosts, health check, probe `Host`; limits lifted (WP07)
T024 Compose example: health check and allowed hosts (WP07)
T025 Changelog entry for the code pull request (WP07)

## Parallelism

- WP01, WP02, WP04 and WP06 are independent.
- WP03 follows WP02. WP05 follows WP03 and WP04. WP07 closes.
