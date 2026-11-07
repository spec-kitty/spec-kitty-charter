---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-06T15:48:36Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

**Issue 1 (blocking): the kernel's new test dependency on `:charter-api` breaks the innermost module's boundary.**

`service/kernel/build.gradle.kts` (cb8bb5b1) adds `testImplementation(project(":charter-api"))` and the Spring Boot BOM. It passes the gates only because neither gate looks at test scope:
- `charter.domain-conventions` checks only the `api` and `implementation` configurations;
- `HexagonalArchitectureTest` imports with `DO_NOT_INCLUDE_TESTS` and runs in `deployable`, so `kernel` test classes are never scanned (`GENERATED_API_NOT_IN_DOMAIN`, `DOMAIN_IS_PURE`).

It contradicts the documented rule:
- `docs/architecture/hexagonal-service.md`: "kernel | nothing outside the JDK"; "charter-api ... referenced only from each slice's `infra.api` and the shared `web-transport`"; "the kernel is held pure both at build time and by ArchUnit".
- The kernel build file's own header: "Pure Java by construction".
- The WP's risk note: "The registry is kernel: no Spring".

Effects:
- The kernel's test classpath now carries spring-web, spring-context, Jackson and Jakarta.
- `:kernel:check` now depends on OpenAPI generation (`:charter-api:jar`), so the innermost module's tests build after the generated adapter contract.
- No other `domain-conventions` module (`profiles`, `missions`) has a dependency like this.

Fix:
- Revert the `dependencies` block in `service/kernel/build.gradle.kts`.
- Move `theActivatableKindsAreTheContractsActivatableKindInItsOrder` to the module that already owns kernel-to-contract parity: `service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java`. It already pairs `RefusalCode` and `ArtifactKind` with generated enums, and no WP owns it. Add the ArtifactReference, EffectiveArtifact, SharedSkip and SkippedDeactivated `KindEnum` pairs and the `SelectedArtifacts` member check there.
- Record it as an out-of-map edit and gate it with `:packs:check`.
- Keep the kernel-side tests (registry shape, token parsing, plurals) where they are.

The DoD exit check ("the new parity test fails if a kind is added to one list but not the registry") still holds, because the test still fails on drift; it just runs in an adapter ring.

**Note (non-blocking):** the parity test passed at the red commit, because WP01 had already added `mission_type` to the contract. Only `theRegistryHoldsOnlyTheFactsItsConsumersRead` was red; I re-ran it at cb8bb5b1 and it fails with an AssertionError, 1 of 73 failed. Say this in the hand-off note rather than claiming the parity test was seen red.

**Everything else checked out and needs no change:**
- The dead flags are deleted, and nothing in `service/` reads them.
- `MISSION_TYPE` is activatable, and its javadoc is updated.
- `NodeKind.plural()` is routed through `artifactKind()`.
- `fromToken` has exact, case-sensitive tests.
- `:kernel:check` exits 0.
- `HexagonalArchitectureTest` passes 59 of 59.
- The 109 `CharterContractSuite` failures are in the activation and preset features (activatedKinds and missionTypes), which WP03, WP04, WP06 and WP08 own. WP02's change does not alter behaviour.
