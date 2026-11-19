# Implementation Plan: Pack uninstall and update guard (#517)

**Branch**: `feat/517-pack-delete-and-update-guard` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)

## Summary

Three service operations for the external org-pack lifecycle, built on what the registration already has:

- **Uninstall.** `DELETE /packs/{packId}`: a new `PackUninstallService` that finds dependents through a new
  `PackDependents` port, composes the remaining closures strictly through `RegistrationImpact`, and asks a new store
  operation, `WholePackWriteStore.removeWhole`, to move the pack's directory aside and delete it in one swap.
- **Update guard.** `PackRegistrationService.applyVersionRule` refuses an older-or-equal `pack_version` with other bytes
  (`409 pack_version_conflict`), ordered by a new public `Pep440Range.compareVersions`. The outcome reports
  `previousVersion`.
- **Dry run.** A `Mode` (`COMMIT` or `DRY_RUN`) passed from the controllers to the two services. In dry-run mode the
  services stop at the point where `beforeCommit` runs today, and answer what they would have done.

## Decisions

The operator's rulings D1 to D10 are in [spec.md](spec.md) and `decisions/`. The plan adds these design calls. They
are logged as open review items for the plan-point squad:

- **P1. Activation ids reach `packs` by changing the port, not by adding to it.**
  - `ActivationImpact.activationRoots()` becomes `List<ActivationRoots>`, a record of the activation id and its
    optional `packs` list. The old shape is replaced (C-001); its one implementation, in `activations.infra`, and the
    tests move with it.
- **P2. The removal compose is a third `RegistrationImpact` entry point.**
  - `checkRemoval(ConfiguredPack)` finds the closures that hold the pack on the stored view (`ClosureRoots.of`). It
    composes each one again on `Session.without(pack)`, a new method beside `with`, after dropping the removed pack
    from its roots.
  - It reuses `judge`, `viewBefore` and the `ForceDiff` notices, so no compose rule is copied.
  - This is the one place #556's `ImpactCheck` will also have to take over. FR-014's seam covers the dependents;
    this entry point stays on `RegistrationImpact` with it.
- **P3. The dependents come before the compose, and every dependent is collected.**
  - `PackDependents` returns all of them, in this order: packs (`depends_on`, then `parent_pack`, each sorted by
    configured name), then activations (sorted by id), then inbound referrers (as `InboundReferences` orders them).
  - It does not stop at the first. This is a refusal of the delete's own, not a #556 rule (C-003).
- **P4. The dry run is a `Mode` argument, not a separate service.**
  - `register(..., Mode)` and `uninstall(..., Mode)` return the same outcome record, with `dryRun` set.
  - In dry-run mode `commit` is never called, the version history is not written, and `impact.committed` is not
    called.
  - A registration dry run still stages and judges the archive. The staging area is cleaned up as on a refusal.
- **P5. The PUT and POST dry runs stay asynchronous.** Same `202`, same job and same status. The job's result
  `PackRegistration` carries `dryRun: true` and the outcome it would have had (`created`, `replaced` or `unchanged`).
  It adds no `would_*` values to the `outcome` enum.
- **P6. The delete dry run answers `200 PackImpact`.**
  - When the delete would succeed: `outcome: deleted`, its notices, `dependents: []`.
  - When it would be refused with `pack_has_dependents`, or with a `422`: `outcome: refused` and `problem`, the same
    Problem the delete answers, so the refusal is a body and not an error status.
  - `428`, `412`, `404` and `503` stay error statuses, as they are answered before anything is judged.
  - *Open review item:* the alternative is to answer refusals with their own status and a `dryRun` flag in the Problem.
- **P7. The audit log.** A committed uninstall writes an audit entry (`AuditLog`, action `pack_removed`). A dry run
  writes none.

## Technical Context

**Language/Version**: Java 25 (Spring Boot), Gradle `verifyAll`
**Primary Dependencies**: no new dependency
**Storage**: the pack directories under the writable root, and the version history `.history/<packId>.json`
**Testing**: JUnit 5 + AssertJ (unit), `@contract` Cucumber scenarios (`contracts/charter/scenarios/registration/`),
ArchUnit, the whole-pack round trip
**Target Platform**: the charter service
**Project Type**: a single Gradle multi-module project (`service/*`, `contracts/charter`)
**Constraints**: the ADR-0065 D12 lock order (registry, then pack); the contract's 0.x Breaking rule (ADR-0022); the
ArchUnit layering (`packs` declares ports, `activations.infra` implements them)

## Charter Check

- **Divergence from the charter's model**: none. The canonical shape replaces the old one (P1, D2), and DIV-602 is
  rewritten.
- **The contract**: a breaking 0.x change, recorded in a `### Breaking` fragment
  (`contracts/charter/changes/2026-11-19-pack-uninstall-and-version-order.md`).
- **Adversarial review squad**: at this plan point (contract, architecture and domain), and before merge, as the
  independent reviewer session.
- **Round trip**: there is no new pack shape. The example packs only gain dependents for the scenarios, through the
  existing `depends_on`.
- **Calls made without review**: P1 to P7 are logged above as open review items.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-delete-and-update-guard-01M7SANQ/
├── spec.md, plan.md, tasks.md, decisions/, research/ (squad findings)
docs/adr/0081-pack-uninstall-and-the-version-order.md    (new)
docs/adr/0065-pack-registration-replaces-the-whole-pack.md (amended-by note on D2, D4, D9)
docs/conformance/divergences.yaml + divergence-register.md (DIV-602 rewritten)
docs/guides/compose-packs.md                               (the version rule, the uninstall)
```

### Source Code (repository root)

```
contracts/charter/
├── paths/packs_packId.yaml          + delete deletePack; + dryRun on put
├── paths/packs.yaml                 + dryRun on post
├── parameters/DryRun.yaml           (new)
├── schemas/PackImpact.yaml          (new) + examples
├── schemas/PackRegistration.yaml    + previousVersion, dryRun
├── schemas/CharterRefusalCode.yaml  + pack_has_dependents; pack_version_conflict reworded
├── changes/2026-11-19-…md           (### Breaking)
└── scenarios/registration/pack-uninstall.feature, version-order.feature, dry-run.feature (new)
service/kernel/…/Pep440Range.java    + compareVersions
service/kernel/…/RefusalCode.java    + PACK_HAS_DEPENDENTS
service/packs/…/application/
├── PackUninstallService.java        (new)
├── PackDependents.java              (new port) + ClosurePackDependents.java (implementation)
├── ActivationImpact.java            activationRoots → List<ActivationRoots> (P1)
├── RegistrationImpact.java          + checkRemoval (P2)
├── StagedPackViews.java             Session.without
├── WholePackWriteStore.java         + removeWhole
├── PackRegistrationService.java     Mode, version order, previousVersion
└── RegistrationJobs.java            the Mode is carried to the job
service/packs/…/infra/storage/PackCommitStore.java + removeWhole; the StagedPackViews implementation + without
service/packs/…/infra/api/           PackUninstallController (or on PackRegistrationController), the mappers
service/activations/…/infra/         the ActivationImpact implementation (P1)
```

## Complexity Tracking

None. Every new piece reuses an existing mechanism: the swap, the closure finder, the compose, the inbound scan and
the job.

## Implementation Concern Map

### IC-01 — Contract and ADR

- The contract changes listed above.
- ADR-0081, the ADR-0065 note, DIV-602 and the Breaking fragment.
- The contract checks (`check_docs`, contract validation and generation) pass. Generated DTOs are rebuilt.

### IC-02 — The version order (FR-008 to FR-010)

- `Pep440Range.compareVersions`, with unit tests of the precedence: a pre-release, a post-release, an epoch and a
  local segment ignored.
- `applyVersionRule`: same revision → `unchanged`; stored version ≥ the upload's version (PEP 440) → `409
  pack_version_conflict`, with detail naming both versions; then the history label-reuse check, as today.
- `RegistrationOutcome.previousVersion` and its mapping.
- Today's tests that went back to an older version are rewritten to move forward (C-005).

### IC-03 — Dependents (FR-004, FR-014, P1, P3)

- The `PackDependents` port and `ClosurePackDependents` over `ClosureRoots`, `ActivationImpact` and
  `InboundReferences.scanRemoval` (every artifact and term of the pack counts as removed).
- The `ActivationImpact` record change, and its implementation in `activations.infra`.
- The refusal `PACK_HAS_DEPENDENTS` (409), with one violation per dependent: `pointer` `/dependents/<i>`, a code per
  kind (`depends_on`, `parent_pack`, `activation`, `artifact_reference`), and the dependent named in the detail.

### IC-04 — Uninstall (FR-001 to FR-007, P2, P7)

- `Session.without`; `RegistrationImpact.checkRemoval`.
- `WholePackWriteStore.removeWhole` and its implementation in `PackCommitStore`: under the generation bracket, move the
  live directory aside, then hide it and delete it. A failure restores it. The history is kept.
- `PackUninstallService.uninstall(packId, Preconditions, Mode)`, under `store.exclusively`:
  1. resolve the pack (404, or 409 when it is built-in or read-only);
  2. check `If-Match`;
  3. find the dependents (409);
  4. compose the closures without the pack (422);
  5. remove, unless dry run;
  6. write the audit entry.
- The controller: `DELETE /packs/{packId}` (204 or 200 on a dry run), behind the registration flag (503), with
  `If-Match` required (428).

### IC-05 — Dry run (FR-011 to FR-013, P4 to P6)

- `Mode` in `PackRegistrationService`, `RegistrationJobs` and both registration controllers (`dryRun` query
  parameter).
- `PackImpact` mapping for the delete.

### IC-06 — Scenarios and tests (FR-015)

- `@contract` scenarios on charter's example packs, for every acceptance scenario of stories 1 to 4. Each story 1–3
  case has a dry-run twin (SC-002).
- Unit tests: `PackUninstallServiceTest`, `ClosurePackDependentsTest`, the version-rule cases in
  `PackRegistrationServiceTest`, and the `PackCommitStoreTest` swap failure for `removeWhole`.
- `./gradlew verifyAll`.

### Sequence

IC-01 → IC-02 → IC-03 → IC-04 → IC-05 → IC-06. Scenarios are written first within each concern (BDD), and IC-06
closes the gaps.
