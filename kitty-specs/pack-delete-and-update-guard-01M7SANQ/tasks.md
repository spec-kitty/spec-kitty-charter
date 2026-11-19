# Tasks: Pack uninstall and update guard (#517)

**Mission**: `pack-delete-and-update-guard-01M7SANQ` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Branch**: `feat/517-pack-delete-and-update-guard` → `main`, as one pull request.

Five work packages on one branch, in dependency order. Each leaves `./gradlew verifyAll` green. Implementation profile:
`java-jenny`. Subtask completion is recorded with `spec-kitty agent tasks mark-status`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Contract: `deletePack` on `paths/packs_packId.yaml`, `parameters/DryRun.yaml` on PUT and POST, `schemas/PackImpact.yaml` and its examples | WP01 | |
| T002 | Contract: `PackRegistration` (`previousVersion`, `dryRun`, the version wording), `CharterRefusalCode` (`pack_has_dependents` and the raiser prose), `Problem` violations wording | WP01 | |
| T003 | The `### Breaking` change fragment and the contract README or CHANGELOG entries the checks require | WP01 | |
| T004 | ADR-0081, the amended-by note in ADR-0065 D2, D4 and D9, DIV-602 in `divergences.yaml` and `divergence-register.md`, and `compose-packs.md` | WP01 | |
| T005 | `Pep440Range.compareVersions`, tested by precedence (pre, post, dev, epoch, local) | WP02 | |
| T006 | `applyVersionRule`: refuse not-newer with other bytes `409 pack_version_conflict`, naming both versions; `RegistrationOutcome.previousVersion` and its mapping | WP02 | |
| T007 | `PackVersionHistory.requireNotReused` matches labels by PEP 440 equality; javadoc without "label, not an order" | WP02 | |
| T008 | `version-order.feature` `@contract` scenarios; rewrite the backward version steps in `whole-pack-registration.feature` | WP02 | |
| T009 | `ActivationImpact.activationRoots()` returns `List<ActivationRoots>` (id, packs); `ActivationImpactAdapter` and the callers move with it | WP03 | |
| T010 | The `PackDependents` port and `ClosurePackDependents`: direct `depends_on` and `parent_pack`, activations naming the pack, and inbound referrers; fails closed on a pack that cannot be read | WP03 | |
| T011 | `StagedPackViews.Session.without` and `RegistrationImpact.checkRemoval(Session, ConfiguredPack)`, on one session | WP03 | |
| T012 | `RefusalCode.PACK_HAS_DEPENDENTS` and the refusal shaped like `artifact_referenced` (packs and capped violations) | WP03 | |
| T013 | `PackRemovalStore.removeWhole`: hide stale asides, one rename to `.pack-discard-*` under the generation bracket, delete; heal the history; refuse a linked pack | WP04 | |
| T014 | `PackUninstallService.uninstall(packId, Preconditions, Mode)`: 404, 428, then under the locks identity 409, 412, dependents 409, compose 422, remove | WP04 | |
| T015 | The controller for `deletePack` (204, or 200 `PackImpact` on a dry run) behind the registration flag; the `RegistrationApi` delegation | WP04 | |
| T016 | The audit: `RegistrationPaths` and `WorktreeGuard` learn `deletePack`; a `dryRun=true` request is never entered | WP04 | |
| T017 | `pack-uninstall.feature` `@contract` scenarios with dry-run twins; `PackCommitStoreTest` crash/repair and swap-failure cases | WP04 | |
| T018 | `Mode` through `RegistrationJobs` and both registration entry points; a dry-run job neither commits, writes history nor records an audit entry | WP05 | |
| T019 | The result: `dryRun: true`, no `pack` link on the status; `previousVersion` and `dryRun` never on `getPack` | WP05 | |
| T020 | `registration-dry-run.feature` `@contract` scenarios: every story 3 case and its dry-run twin, with a byte comparison of the store | WP05 | |
| T021 | `./gradlew verifyAll` green; close the gaps the scenarios show | WP05 | |

## Work Packages

### WP01 — The contract, ADR-0081 and the records

Depends on: nothing. Requirements: FR-001, FR-003, FR-004, FR-008, FR-010, FR-011, FR-012, C-004.

Prompt: [tasks/WP01-contract-and-adr.md](tasks/WP01-contract-and-adr.md)

### WP02 — The version order, previousVersion and label equality

Depends on: WP01. Requirements: FR-008, FR-009, FR-010.

Prompt: [tasks/WP02-version-order.md](tasks/WP02-version-order.md)

### WP03 — PackDependents and the removal compose

Depends on: WP01. Requirements: FR-004, FR-005, FR-014, NFR-001, C-002, C-003.

Prompt: [tasks/WP03-dependents-and-removal-compose.md](tasks/WP03-dependents-and-removal-compose.md)

### WP04 — DELETE /packs/{packId}: the store, the service, the controller and the audit

Depends on: WP02, WP03. Requirements: FR-001, FR-002, FR-003, FR-006, FR-007, NFR-002.

Prompt: [tasks/WP04-uninstall.md](tasks/WP04-uninstall.md)

### WP05 — The registration dry run on PUT and POST

Depends on: WP02, WP04. Requirements: FR-012, FR-013, NFR-002, SC-002.

Prompt: [tasks/WP05-registration-dry-run.md](tasks/WP05-registration-dry-run.md)
