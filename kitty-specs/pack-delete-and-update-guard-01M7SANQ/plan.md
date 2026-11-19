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
- **P2. The removal compose is a third `RegistrationImpact` entry point, on the caller's session.**
  `checkRemoval(Session, ConfiguredPack)` finds the closures that hold the pack on the stored view (`ClosureRoots.of`),
  drops the removed pack from each one's roots, and composes them again on `Session.without(pack)`, a new method beside
  `with`. It reuses `judge`, `viewBefore` and the `ForceDiff` notices. One `Session` is shared by the dependents, the
  inbound scan and this compose (NFR-001, squad M8). This entry point is the one place #556's `ImpactCheck` will also
  take over.
- **P3. The dependents come before the compose, all of them, and the check fails closed.**
  - `PackDependents` lists only direct dependents, in this order: packs (`depends_on`, then `parent_pack`, one
    violation per relation, sorted by configured name), then activations (sorted by id), then inbound referrers (in
    the order `InboundReferences` gives them).
  - A's `pack_id` is salvaged from its `pack.yaml` when A cannot be loaded. When A's own `pack.yaml` cannot be read,
    A is judged by its configured name and can still be deleted (operator ruling on I3, after review R4-1).
  - Any other configured pack whose descriptor cannot be read refuses the delete with `500 pack_unreadable`, naming
    it. A dependent that cannot be read never lets a delete through (squad M3).
  - This is a refusal of the delete's own, not a #556 rule (C-003).
- **P4. The dry run is a `Mode` argument, not a separate service.**
  - `register(..., Mode)` and `uninstall(..., Mode)` return their usual outcome, with `dryRun` set.
  - In dry-run mode `commit` and `removeWhole` are never called, the history is not written, `impact.committed` is not
    called, and nothing is audited.
  - A registration dry run still stages and judges the archive, and cleans up its staging as a refusal does.
- **P5. The PUT and POST dry runs stay asynchronous.**
  - The same `202`, job and status. The job's result `PackRegistration` carries `dryRun: true` and the outcome it
    would have had (`created`, `replaced` or `unchanged`); the outcome enum gains no `would_*` values.
  - A dry-run status has no `pack` link. The result's `_links.self` is the pack URI the write would hold; it answers
    404 after a would-be create (documented).
  - `previousVersion` and `dryRun` never appear on `getPack` (squad M7).
- **P6. The delete dry run answers `200 PackImpact` for a judgement, and an error status for anything else.**
  - A judgement is a body: `outcome: deleted` with notices, or `outcome: refused` with `problem` for
    `409 pack_has_dependents` or a `422` compose refusal.
  - Everything decided before the judgement, or by infrastructure, keeps its status: `503` (the flag, or the registry
    lock busy), `404`, `428`, `409 registration_conflict`, `412` and `500`.
  - The order of checks is `RemovalPrecondition`'s: the 503 flag, 404, 428, then under the locks 409 identity and 412
    (squad M9).
- **P7. The audit follows the registration audit filter.** `RegistrationPaths.classify` and `WorktreeGuard` learn
  `DELETE /packs/{packId}` as operation `deletePack`; every attempt is entered, refusals included, and
  `RevisionTrail` carries revisionBefore and revisionAfter. A request with `dryRun=true`, on any of the three
  operations, is never entered: the filter skips it and the job does not `record` it (squad H1, NFR-002).
- **P8. The store: resolve, then lock, then one rename to discard.**
  - The uninstall goes through the `PackRemovalStore` seam. Its `exclusively(PackIdentifier)` resolves a name or a
    `pack_id` under the registry lock and locks the resolved directory, the lock a listing's crash repair takes
    (squad H2).
  - The new `PackRemovalStore.removeWhole(PackIdentifier)` hides stale asides first, then renames the live directory
    straight to `.pack-discard-*` (atomic; nothing is left that a repair would restore) under the write-generation
    bracket, then deletes that tree. The generation counters are kept (squad H3).
- **P9. A linked pack is not removed.** A writable-root entry that is a link (#174) is refused with
  `409 registration_conflict`; the operator removes the link. The service never deletes outside its writable root
  (squad M1).
- **P10. The history is healed and labels compare by PEP 440.** Before the removal, the stored
  `(pack_version, revision)` pair is staged into the history when it is missing. `requireNotReused` matches labels by
  PEP 440 equality (`compareVersions == 0`), so `1.1`, `1.1.0` and `1.1.0+x` name one label (squad M2).
- **Open review items for the pre-merge reviewer:** P1, P5's self link, P6, P9, and M5. M5 is that an activation with
  no `packs` list may hold selections of the removed pack's artifacts, which read as stale and are dropped on the next
  write, as after an artifact `DELETE`. It is documented, with a scenario.

Squad findings and dispositions: [research/squad-plan.md](research/squad-plan.md).

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

- The `PackDependents` port and `ClosurePackDependents`, built over `ClosureRoots` (direct reverse `depends_on` and
  `parent_pack`), `ActivationImpact` (the activations whose `packs` list names the pack) and
  `InboundReferences.scanRemoval`. The scan's `StagedTree` carries the pack's descriptor and empty facts, and `removed`
  is the pack's defined artifacts, its digested artifacts and its terms (squad M4).
- `ActivationImpact`'s record change (P1), and its implementation in `activations.infra`.
- The refusal `PACK_HAS_DEPENDENTS` (409), shaped like `artifact_referenced`: a `packs` member (RefusalPack) for each
  dependent pack, and up to 100 violations. Each violation has an empty pointer, a code (`depends_on`, `parent_pack`,
  `activation` or `artifact_reference`) and the dependent named in its detail. When more than 100 are found, the count
  left out is named in the detail (squad M6).
- The check fails closed: when another pack cannot be read, the delete is refused `500 pack_unreadable` (P3).

### IC-04 — Uninstall (FR-001 to FR-007, P2, P7 to P10)

- `Session.without`, and `RegistrationImpact.checkRemoval(Session, ConfiguredPack)`.
- `PackRemovalStore.removeWhole`, implemented in `CommitStorePackWriteStore` and `PackCommitStore`, which:
  1. hides the stale asides;
  2. renames the live directory straight to `.pack-discard-*` under the generation bracket;
  3. deletes that tree;
  4. heals the stored pair into the history first (P10).
- `PackUninstallService.uninstall(packId, Preconditions, Mode)`, in this order:
  1. the 503 flag (in the controller);
  2. resolve the pack (404);
  3. `If-Match` present (428);
  4. under `store.exclusively(PackIdentifier)`: the identity (409 for a built-in pack, a read-only location or a
     linked pack), the stale check (412), the dependents (409), the compose (422);
  5. remove, unless this is a dry run.
- The controller: `DELETE /packs/{packId}` answers 204 (200 `PackImpact` on a dry run). The generated
  `RegistrationApi` gains `deletePack`, implemented by delegating to the uninstall controller.
- The audit (P7): `RegistrationPaths` and `WorktreeGuard` learn `deletePack`, and a dry run is never entered.
- `PackVersionHistory.requireNotReused` matches labels by PEP 440 equality (P10).

### IC-05 — Dry run (FR-011 to FR-013, P4 to P6)

- `Mode` in `PackRegistrationService`, `RegistrationJobs` and both registration controllers (the `dryRun` query
  parameter). A dry-run job neither records nor writes.
- The `PackImpact` mapping for the delete. The dry-run links on `PackRegistrationStatus` (P5).

### IC-06 — Scenarios, tests, docs (FR-015)

- `@contract` scenarios on charter's example packs, for every acceptance scenario of stories 1 to 4. Each case of
  stories 1 to 3 has a dry-run twin (SC-002). They include the M5 stale-selection case.
- Unit tests:
  - `PackUninstallServiceTest`, `ClosurePackDependentsTest`;
  - the version-rule and label-equality cases;
  - `PackCommitStoreTest`: `removeWhole`, its swap failure, and the crash/listing-repair that must not resurrect a
    deleted pack;
  - `RegistrationPathsTest`.
- The existing scenarios that step a version back (`whole-pack-registration.feature`) are rewritten to move forward.
- Remove the "label, not an order" wording from: `paths/packs_packId.yaml`, `PackVersionHistory`, `compose-packs.md`,
  `divergences.yaml` and `divergence-register.md`. Also update the `CharterRefusalCode` prose that lists which
  operations raise which codes.
- `./gradlew verifyAll`.

### Sequence

IC-01 → IC-02 → IC-03 → IC-04 → IC-05 → IC-06. Scenarios are written first within each concern (BDD), and IC-06
closes the gaps.
