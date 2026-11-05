---
work_package_id: WP04
title: packs ClosureQueries, one verified closure snapshot
dependencies:
- WP02
- WP03
requirement_refs:
- FR-007
- FR-009
- FR-010
- FR-023
- NFR-007
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RequiredFloor.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureQueriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/RequiredFloorTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RequiredFloor.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureQueriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/RequiredFloorTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP04: packs ClosureQueries, one verified closure snapshot

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Give the activation slice everything it may ask about pack content in **one verified read** (NFR-007, squad A2):
`ClosureQueries.snapshot(roots)` runs one `ConsistentRead` and answers a `ClosureView` holding:

- the closure facts in the offering's pack shape (`OfferingEntry`, which the API maps to `OfferingPack` and
  `ResolvedDependency`), in dependency order;
- the held URNs per artifact kind, and the holder of each;
- the mission types the closure holds;
- the required floor: the must-have items of every governing preset in the closure, each with the requiring pack
  (FR-023);
- the lock level of any URN, with the locking pack (via WP03's `PerPackPolicy`);
- the presets by id, with the holding pack;
- the verified source fingerprint.

A pack change during the read answers `stale_input`; it never gives a mixed answer.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-03b. **Research:** R1, R5; squad A1, A2, B2.
- **Requirements:** FR-007 and FR-023 (the floor and locks feed the 409 refusals), FR-009 (the closure read's
  facts), FR-010 (writes see only the closure), NFR-007.
- **Rulings:** ADR-0031 and ADR-0042 (requirement is authored as a preset; must-have items), ADR-0012 D7 (locked
  implies required), planning decision `DM-01M6PCWSA65497T9A7J80K6V56` (the closure-scoped floor, an assumed default
  flagged to the operator).
- **Collisions:** PR #377 edits `DomainRepositoryService` (this WP edits it out of map, see below). Rebase after it.
- **Grounding:**
  - `ConsistentRead.read(staleDetail, Supplier<T>)` returns `Verified<T>(value, fingerprint)`. The supplier must do
    every read, so `composed(roots)` (WP02) runs **inside** it, never a nested `offering()`.
  - `OfferingReadService` builds `OfferingEntry` from the resolved closure (`entries()`, `dependenciesOf`). Reuse that
    mapping. Make the entry-building helper package-visible or public in WP02's shape; do not copy it.
  - `PackUnderResolution.definedArtifacts()` is the held set (bare URNs, every graph-authored kind) and
    `PackCompositionResolver.holderOf` answers which pack holds a reference.
  - Presets are read today only through the whole-pack assembly (`Pack.presets()`, read in
    `DomainRepositoryService` at line ~496). `compositionFacts` already assembles the `Pack`
    (`Pack assembled = readable(pack)`), so the presets ride on `PackCompositionFacts` at no extra read cost.
  - `Preset.required()` (per kind) and `Preset.requiredMissionTypes()` are the must-have items, and
    `Preset.governing()` says whether a preset binds.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP02 and WP03 (the facts record WP03 extended).
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java` (owner WP03): add
  `List<Preset> presets`. WP03 has already reshaped the record (the policy component); this adds one more.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java` (owner WP03):
  fill `presets` from the assembled `Pack` in `compositionFacts`, which already loads it.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java` (owner WP02): expose
  the entry mapping (`entriesOf(List<PackUnderResolution>)`) and the facts read with the closure
  (`composedWithFacts(roots)`), so `ClosureQueries` does not re-read. Keep `/offering` untouched.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java` (owner
  WP03): one case for `presets` on the facts.
- `service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java` (owner WP02): one
  case pinning that `composedWithFacts(roots)` returns the facts it read, once per closure pack.

## Subtask Guidance

### Subtask T020: Presets on the composition facts

**Steps (red first):**
1. In `DomainRepositoryServiceTest`, a pack with a governing preset: `compositionFacts(pack).presets()` holds it. A
   pack with none gives `[]`. (This test file is WP03's; add the case as part of the out-of-map edit.)
2. Add `List<Preset> presets` to `PackCompositionFacts`, defaulting to `List.of()` in the shorter constructors so
   that the application-test fakes compile.
3. Fill it from `assembled.presets()` in `compositionFacts`. A preset that failed the domain (a
   `PackElementViolation`) is **not** in `presets()`. Decide whether such a pack is unreadable for the compose:
   today a violation makes `readable(pack)` throw `pack_unreadable`. Check `readable` and keep that rule.

### Subtask T021: `RequiredFloor` (pure domain)

**Purpose:** the floor rule in one place, testable without packs on disk (FR-023).

**Steps (red first, `RequiredFloorTest`):**
1. Two packs: org holds the governing preset P with must-have `directive:D1` and a convenience item `directive:D2`;
   built-in holds the convenience preset C. `RequiredFloor.of(closure)` maps `directive:D1` to [org], and D2 is
   absent.
2. Two governing presets in two packs both requiring D1: both packs listed, in closure order.
3. `requiredMissionTypes` gives `mission_type:<id>` entries the same way.
4. An empty closure gives an empty floor.

**Implementation:** `public record RequiredFloor(Map<Urn, List<String /*descriptorPackId*/>> requiredBy)` with
`static RequiredFloor of(List<Map.Entry<String, List<Preset>>> presetsByPackInOrder)`, `isRequired(Urn)` and
`requiringPacks(Urn)`. Pure: no Spring, no infra (`PACKS_DOMAIN_IS_PURE`).

### Subtask T022: `ClosureView`, the answer

**Purpose:** a read-only value the activations adapter (WP09) maps into its own `ClosureSnapshot`. Activations never
holds packs types beyond its adapter.

**Shape** (`packs.application.ClosureView`, a record):
- `List<OfferingReadService.OfferingEntry> packs`: dependency order;
- `Map<ArtifactKind, Set<String>> heldIds`: declared ids per kind, from `definedArtifacts`;
- `Map<Urn, String> holderOf`: URN to descriptor `pack_id` (first holder in merge order, as `holderOf` resolves a
  bare reference);
- `Set<String> missionTypes`: the held mission types (they are graph-authored; `mission_type:<id>` in `defined`);
- `RequiredFloor floor`;
- `PerPackPolicy policy` and `List<String> closurePackIds`, with a method `lockOf(Urn)` returning WP03's
  `(level, setBy)` record;
- `Map<String, HeldPreset> presets`: preset id to `(Preset preset, String descriptorPackId)`. Two packs holding one
  preset id: the last in merge order wins. Record the choice in the Javadoc; it follows the merge order ADR-0053 d6
  uses for layer overrides;
- `String fingerprint`.

Plus convenience methods `holds(ArtifactKind, String id)`, `isLocked(Urn)` (level `LOCKED`) and
`isRequired(Urn)` (floor, or locked: ADR-0012 D7, locked implies required).

### Subtask T023: `ClosureQueries.snapshot(roots)`

**Steps (red first, `ClosureQueriesTest`, with the fakes `OfferingReadServiceTest` uses):**
1. Roots {alpha}, with alpha → beta and gamma unrelated: `snapshot` lists [beta, alpha], and alpha's entry carries
   its `ResolvedEdge` to beta. `heldIds` has only alpha's and beta's artifacts.
2. Roots empty or absent: the caller passes every configured pack (`ClosureQueries.allConfigured()`, or an
   `Optional` roots parameter where empty means all, per FR-008's "absent packs means every configured pack").
3. A closure that cannot compose: the composition refusal propagates (`pack_dependency_out_of_range` naming the
   pack), unchanged (US2 AS4).
4. `lockOf(directive:D1)` is `LOCKED` by org when org's facts carry a policy locking it.

**Implementation:**
- `@Service public final class ClosureQueries`, with constructor injection of `OfferingReadService` and
  `ConsistentRead`.
- `public ClosureView snapshot(Optional<List<PackIdentifier>> roots)`: `read.read(STALE, () -> build(roots))`, where
  `build` calls WP02's `composed(roots)` variant that also returns the facts it read (expose it in the out-of-map
  edit: `composedWithFacts(roots)`), then builds the view.
- Keep `build` under 60 lines: extract `heldOf`, `presetsOf` and `policyOf`.

### Subtask T024: One read per request (NFR-007)

**Steps (red first):**
1. With `DriftingSources`-style fakes (see `service/deployable/src/test/.../bdd/DriftingSources.java` for the pattern;
   in the packs test, use a `PackSources` fake whose fingerprint changes between the two digests), `snapshot` throws
   `CharterRefusal(STALE_INPUT)`.
2. Assert that `compositionFacts` is called once per closure pack per `snapshot`, so no hidden second compose.

### Subtask T025: Wiring and gates

**Steps:**
1. `ClosureQueries` is picked up by `ApplicationConfiguration`'s scan (`packs.application`). Confirm with
   `:deployable:test --tests '*ProductionWiringTest*' --tests '*WiringRoundTripTest*'`. **Run them, do not edit them
   (P5):** both have one owner, WP07. If either needs a change because of `ClosureQueries`, record it in the review
   notes. WP07 depends on WP04 and makes the edit.
2. Run the gate commands. The offering scenarios must be unchanged (FR-020).

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1 (by construction; not yet enforced on `main`; the reviewer measures by hand). No new
suppression, and no disabled test.

## Gate commands

```bash
./gradlew :packs:test --tests '*ClosureQueriesTest*' --tests '*RequiredFloorTest*' --tests '*DomainRepositoryServiceTest*'
./gradlew :packs:check -x openPacksRoundTrip
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' --tests '*WiringRoundTripTest*'
```

## Definition of Done

- [ ] `PackCompositionFacts.presets` is filled from the already-assembled pack.
- [ ] `RequiredFloor` is pure domain and covers governing, convenience, shared and mission-type cases.
- [ ] `ClosureQueries.snapshot` runs exactly one `ConsistentRead`; the stale case answers `stale_input`.
- [ ] `ClosureView` answers the packs, held ids, holders, mission types, floor, locks, presets and fingerprint.
- [ ] `/offering` is unchanged; the architecture test is green.
- [ ] No new suppression, and no disabled test.

## Risks

- **Preset id collisions across packs.** The merge-order rule is a choice; record it, and flag it in the PR
  description as following ADR-0053 d6.
- **The floor is closure-scoped** (an assumed default). Keep it in one function (`RequiredFloor.of`) so a later
  ruling changes one place.
- **Cost.** One snapshot per request composes the closure once. The closure is small; do not add caching in this
  mission.

## Reviewer guidance (reviewer-renata)

- **One read:** demand the call-count assertion of T024. A snapshot that calls `offering()` inside `snapshot()`
  nests two `ConsistentRead`s; grep for it.
- **Floor correctness:** the convenience-preset case must be in `RequiredFloorTest`. Check that `requiredBy` lists
  packs (descriptor `pack_id`s), not artifacts.
- **Locked implies required:** `isRequired` must return `true` for a locked URN with no preset.
- **Non-fakeable evidence:** the test names and the `ProductionWiringTest` output.
