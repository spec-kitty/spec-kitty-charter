---
work_package_id: WP01
title: The contract, ADR-0081 and the records
dependencies: []
requirement_refs:
- FR-001
- FR-003
- FR-004
- FR-008
- FR-010
- FR-011
- FR-012
- C-004
planning_base_branch: feat/517-pack-delete-and-update-guard
merge_target_branch: feat/517-pack-delete-and-update-guard
branch_strategy: Planning artifacts for this mission were generated on feat/517-pack-delete-and-update-guard. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/517-pack-delete-and-update-guard unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
history:
- at: '2026-11-19T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/paths/packs_packId.yaml
create_intent:
- contracts/charter/parameters/DryRun.yaml
- contracts/charter/schemas/PackImpact.yaml
- contracts/charter/changes/2026-11-19-pack-uninstall-and-version-order.md
- docs/adr/0081-pack-uninstall-and-the-version-order.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/packs_packId.yaml
- contracts/charter/paths/packs.yaml
- contracts/charter/parameters/DryRun.yaml
- contracts/charter/schemas/PackImpact.yaml
- contracts/charter/schemas/PackRegistration.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/changes/2026-11-19-pack-uninstall-and-version-order.md
- docs/adr/0081-pack-uninstall-and-the-version-order.md
- docs/adr/0065-pack-registration-replaces-the-whole-pack.md
- docs/conformance/divergences.yaml
- docs/conformance/divergence-register.md
- docs/guides/compose-packs.md
tags: []
tracker_refs: []
---

# WP01 — The contract, ADR-0081 and the records

Plan: [plan.md](../plan.md). Squad dispositions: [research/squad-plan.md](../research/squad-plan.md).

## Subtasks

- **T001** Contract: `deletePack` on `paths/packs_packId.yaml`, `parameters/DryRun.yaml` on PUT and POST, `schemas/PackImpact.yaml` and its examples
- **T002** Contract: `PackRegistration` (`previousVersion`, `dryRun`, the version wording), `CharterRefusalCode` (`pack_has_dependents` and the raiser prose), `Problem` violations wording
- **T003** The `### Breaking` change fragment and the contract README or CHANGELOG entries the checks require
- **T004** ADR-0081, the amended-by note in ADR-0065 D2, D4 and D9, DIV-602 in `divergences.yaml` and `divergence-register.md`, and `compose-packs.md`

## Done when

- Each subtask is recorded with `spec-kitty agent tasks mark-status`.
- `./gradlew verifyAll` is green on Java 25.
- Tests carry only the canonical shape (charter companion), except error-handling cases.
