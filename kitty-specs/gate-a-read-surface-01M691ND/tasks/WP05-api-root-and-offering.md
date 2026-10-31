---
work_package_id: WP05
title: API root and offering
dependencies:
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-016
- C-003
- C-004
- SC-001
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T022
- T023
- T024
- T025
- T026
phase: Phase 2 - The resource tree
agent: claude
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- contracts/charter/scenarios/root.feature
- contracts/charter/scenarios/offering.feature
- contracts/charter/paths/root.yaml
- contracts/charter/paths/offering.yaml
- contracts/charter/schemas/ApiRoot.yaml
- contracts/charter/schemas/Offering.yaml
- contracts/charter/schemas/OfferingPack.yaml
- contracts/charter/headers/ServiceDescLink.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCatalogRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ConfiguredPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/OfferingController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/OfferingControllerTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/scenarios/**
- contracts/charter/paths/root.yaml
- contracts/charter/paths/offering.yaml
- contracts/charter/paths/info.yaml
- contracts/charter/schemas/**
- contracts/charter/headers/**
- contracts/charter/examples/**
- contracts/charter/CHANGELOG.md
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCatalogRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ConfiguredPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDescriptorRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/**
- service/deployable/src/main/java/ai/speckitty/charter/deployable/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – API root and offering

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Add `GET /` (`getApiRoot`) and `GET /offering` (`getOffering`). Give `ServiceInfo` its `_links` and the
`service-desc` link and header (#208 hand-over).

Every link target now exists:

- `artifacts` came in WP03;
- `/openapi.yaml` came with #209;
- `/info` and `/offering` come in this WP.

## Context

- Spec US1, US4, FR-001, FR-002, FR-016; data-model.md; research R-07, R-09, R-11, R-18; squad findings 8 and 24.
- **Ruling 3** — the smallest Gate-A set: no action context, graph, mission types, kinds or
  `GET /packs/{packId}` (C-003). No #125 operation and no link to one (C-004).
- **Pack levels.**
  - Storage's level is `PackLocation.level`, the configured level. `StorageRootLocations.all()`
    (`infra/storage/StorageRootLocations.java:68-98`) lists `<config-root>/packs/*` and sorts built-in
    first, then by name.
  - `ScenarioPacks` sets the configured level from the step (`Given the project pack "p"`), with no
    `pack.yaml` needed (ScenarioPacks.java:91-106).
  - `PackPolicy` reports a mismatch between the declared and the configured level (PackPolicy.java:130-136).
- **Descriptor.** `PackDescriptorRepository.get` and `FilesystemPackStore.descriptorOf` (:561-566) refuse
  `PACK_UNREADABLE` when `pack.yaml` is absent or invalid. The offering must tell the two apart: an absent
  `pack.yaml` leaves name and version absent, while an invalid one is a 500.
- **Ports.** ArchUnit keys its port rules on the `*Repository`/`*Gateway` suffix (HexagonalArchitectureTest
  :259-284), so the new port is named `PackCatalogRepository`.
- **Root path.** The contract path `/` with the server `/api/v1` is `/api/v1/`. ScenarioHttp reaches it as
  `BASE + "/"`.
- **Fingerprint hook.** `CharterSteps.fingerprintHeaderIsTheVerifiedDigest` (:80-110) requires exactly one
  fingerprint on every GET 200. The root carries none (R-08 revised), so the hook's own rule becomes:
  GET, 200, a JSON body, and a path other than `/`.

## Subtasks

### T022 — Red scenarios

1. **`root.feature`.**
   - `GET /` answers 200 with an exact body: `_links` holding `self` `/api/v1/`, `info` `/api/v1/info`,
     `offering` `/api/v1/offering`, `artifacts` `/api/v1/packs/{packId}/artifacts{?kind}` (templated) and
     `service-desc` `/api/v1/openapi.yaml`. There is no fingerprint.
   - Follow `info`, `offering` and `service-desc`, and expand `artifacts` with a configured pack, expecting
     200 each.
   - 401 without a token.
   - Pin what `GET` with no trailing slash (`/api/v1`) answers today, and document it in the README. Do not
     add a second mapping.
2. **`offering.feature`** (R-18).
   - Built-in, org and project packs configured out of order, two per level, ids `Zeta` and `alpha`: the
     order is by configured level, then by code point.
   - A pack whose declared `pack_level` differs from its configured level: the configured level wins.
   - A pack without `pack.yaml`: listed, with no `name` and no `version`.
   - A pack with an invalid `pack.yaml`: 500 `pack_unreadable`, no fingerprint.
   - A pack directory that is missing: 500 `pack_unreadable`.
   - Every entry's `_links.artifacts` follows to a 200.
   - The answer carries `_links.self` and the fingerprint.
   - `stale_input` when inputs change during the read.
3. **`service.feature`.** `/info`'s body gains `_links` (`self` and `service-desc`), and the answer carries
   the header `Link: </api/v1/openapi.yaml>; rel="service-desc"`. No other response carries `Link`; assert
   this on `/` and `/offering`.
4. Run them and see them fail. Record this.

### T023 — Contract

1. **`paths/root.yaml`.**
   - `getApiRoot`, tag `Service`.
   - The 200 answers `ApiRoot` with the two always-on Charter headers and no fingerprint (WP01's rule
     already excludes `/`).
   - 401, 403, and `default`. No 500: the root reads nothing that can fail. If the guard or filter can
     still produce a 500, keep `OfferingUnreadable` off and let `default` cover it.
2. **`paths/offering.yaml`.**
   - `getOffering`, new tag `Offering`.
   - The 200 answers `Offering` with the fingerprint required.
   - 401, 403, 500 `OfferingUnreadable`, and `default`.
   - The description states the order rule, and that merging is not applied yet (#10, #11).
3. **Schemas.**
   - `ApiRoot`: `_links`.
   - `Offering`: `packs`, `_links`.
   - `OfferingPack`: `packId`, `level` (`$ref PackLevel.yaml`), optional `name` and `version`, and
     `_links` (`artifacts`).
   - `ServiceInfo` gains a required `_links`.
   - Everything is cited and described. Also fix the stale `x-source` path of `PackLevel.yaml` if you
     touch it (it names `service/charter-domain/...`).
4. **`headers/ServiceDescLink.yaml`.** The `Link` header, required on the info 200, with a pattern
   `^</api/v1/openapi\.yaml>; rel="service-desc"$`. Add it to `headers/_index.yaml`, and reference it from
   `paths/info.yaml`'s 200 only.
5. **Tags and examples.** Add the `Offering` tag to `openapi.yaml`. Give each new 200 an example with
   `_links`, using only the relations `self`, `info`, `offering`, `artifacts` and `service-desc`.
6. `:contracts:check` is green.

### T024 — `PackCatalogRepository` and descriptor lookup

1. **`packs.domain.ConfiguredPack`**: a record `(PackIdentifier id, PackLevel level)`.
   **`packs.domain.PackCatalogRepository`**: `List<ConfiguredPack> configured()`.
2. **`PackDescriptorRepository`**: add `Optional<PackDescriptor> find(PackIdentifier)`.
   - Empty when the pack has no `pack.yaml`.
   - Refuses `PACK_UNREADABLE` when the file is present but unreadable or invalid, or when the pack
     directory is missing.
   - Keep `get` as it is.
3. **`DomainRepositoryService`** implements both:
   - `configured()` maps `StorageRootLocations.all()` to `ConfiguredPack` using the location's configured
     level;
   - `find` delegates to the store, telling an absent file from an invalid one.
4. Unit tests in the existing storage test classes:
   - absent vs invalid;
   - the configured level is used;
   - a linked pack is listed. Use the `SymbolicLinks` helper if #289 has landed; otherwise follow the
     existing link-test pattern.

### T025 — `OfferingReadService`

1. In `application`. `Verified<List<OfferingEntry>> offering()`, as **one** `consistentRead.read`:
   - `configured()`;
   - `find` for each pack;
   - sort by `level.ordinal()`, then by `id.value()` in code-point order.

   Each entry is `(id, level, Optional name, Optional version)`.
2. Unit tests with fakes:
   - the order across levels and within a level (`Zeta`/`alpha`);
   - an absent descriptor;
   - an invalid descriptor raises `pack_unreadable`;
   - `stale_input` comes through `ConsistentRead`.

### T026 — Controllers, links, header, hook

1. **`OfferingController implements OfferingApi`**: maps entries, builds links with `Hypermedia`
   (`self`, per-entry `artifacts`), and sets the fingerprint.
2. **`InfoController`**.
   - Implement `getApiRoot`, with links built from `Relation`: `SELF`, `INFO`, `OFFERING`, `ARTIFACTS`
     (templated `…/packs/{packId}/artifacts{?kind}`) and `SERVICE_DESC`. No fingerprint.
   - `getServiceInfo` adds `_links` (`self`, `service-desc`) and the `Link` header. Keep its existing
     fingerprint behaviour.
3. **`CharterSteps` fingerprint hook**: change the rule to "GET, 200, JSON body, path ≠ `/`" — exactly
   one fingerprint, equal to the one `/info` gives. Everything else carries none, except writes, which stay
   exempt. Do not derive it from the contract (R-08 revised).
4. Controller tests: `OfferingControllerTest`, and new `InfoController` cases.

## Definition of Done

- [ ] The root, offering and service scenarios pass, and they failed first (logged).
- [ ] Every link in the root and offering answers resolves to a 200 in a scenario.
- [ ] `verifyAll` is green. ArchUnit is unchanged and green; `PackCatalogRepository` is under the port rules.
- [ ] No new suppressions. Complexity ≤ 15.
- [ ] The CHANGELOG mission block lists `getApiRoot`, `getOffering`, the `ServiceInfo._links`, the `Link`
  header and the scenarios.

## Risks and notes

- Keep `StorageRootLocations` behaviour unchanged. Only read from it.
- Linked-pack hardening (#289) may land first. If it does, rebase, and use its `SymbolicLinks` helper in
  tests.

## Reviewer guidance

- Check the offering scenario that configures packs out of order. It must fail if the service used storage's
  own name order.
- Check that the root's `artifacts` link is templated and that `templated: true` is set.

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP05 --agent claude --mission gate-a-read-surface-01M691ND`.
