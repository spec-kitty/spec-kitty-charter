---
work_package_id: WP02
title: packs rooted compose and public closure
dependencies: []
requirement_refs:
- FR-009
- FR-020
- SC-004
- NFR-001
- C-002
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: 2a33cb9fdc330dada00255c1e363633652f1f30c
created_at: '2026-11-05T19:20:31.270484+00:00'
subtasks:
- T008
- T009
- T010
- T011
- T012
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP02: packs rooted compose and public closure

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Root the pack compose at a set of packs (#360) so an activation composes only what it builds on:

- `PackCompositionResolver.resolveRooted(available, roots)` runs the existing checks over the closure of the roots
  only;
- a public `closureOf` follows `depends_on` **and** `parent_pack`, and tolerates a target that no available pack
  declares (the resolver, not the closure walk, refuses it);
- `OfferingReadService.composed(roots)` reads descriptors tolerantly and reads composition facts **only for packs
  in the closure**, so an unreadable, duplicate-id or duplicate-name pack outside the closure is never judged;
- `/offering` calls it with every configured pack as a root, so its answer and every refusal stay byte-identical
  (FR-020, SC-004).

Implementation command:
`spec-kitty agent action implement WP02 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-02. **Research:** R2, squad finding A3 (the rooted compose must not read every pack).
- **Requirements:** FR-009 (the closure read rests on this), FR-020 (offering unchanged, a ratchet), SC-004.
- **Rulings:** ADR-0048 D1 and ADR-0053 D4 (`depends_on`), the composition mission's FR-011 (the activated packs
  are the closure's roots, operator ruling of 2026-11-03).
- **Collisions:** PR #377 touches `DomainRepositoryService` (not this WP). Base is `main` (with #368 and #376). Nothing in this WP
  collides directly; rebase before review.
- **Grounding (verified in the code):**
  - `PackCompositionResolver.closureOf(String, Map)` at line ~441 is `private` and follows `depends_on` **only**,
    not `parent_pack`. The plan's public `closureOf` must add `parent_pack`. The private use in `checkReferences`
    must keep `depends_on`-only semantics (a qualified reference may name only packs in the writer's `depends_on`
    closure, FR-015). Keep two named functions, for example `dependsOnClosure` (the current private one) and
    `compositionClosure` (public, `depends_on` + `parent_pack`), rather than one function with a flag.
  - `resolve(packs)` indexes **all** packs by id and by name before anything else (`indexById`, `indexByName`), so a
    duplicate anywhere refuses. `resolveRooted` must select the closure **first**, then run `resolve` over that
    subset.
  - Roots are **configured ids** (`ConfiguredPack.id()`, the contract's `PackPin.packId`), while `depends_on` and
    `parent_pack` name **descriptor `pack_id`s** (ULIDs). The closure walk bridges the two: a root is found by
    configured id, and each edge by descriptor `pack_id`.
  - `OfferingReadService.composed()` today reads every configured pack's descriptor (`descriptorOf`, which throws
    `pack_unreadable`) and facts (`packArtifacts.compositionFacts`), then resolves.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. No dependency; can run in parallel with WP01.
- Rebase onto the lane base before review.

## Subtask Guidance

### Subtask T008: Red tests for the rooted closure

**Purpose:** pin the behaviour before writing it.

**Steps:** in `service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java`, add
a nested class `Rooted` with the fixture alpha → beta (`depends_on`), and gamma unrelated:
1. `resolveRooted(all, {alpha})` answers [beta, alpha], and gamma is absent.
2. `resolveRooted(all, {alpha, gamma})` answers the same order rule as `resolve` gives today for the three: the
   stable order, levels contiguous.
3. A pack delta whose `parent_pack` is beta: `resolveRooted(all, {delta})` holds beta (`parent_pack` is in the
   composition closure).
4. Two packs **outside** the closure that share a `pack_id` (or a `name`): `resolveRooted(all, {alpha})` succeeds;
   `resolve(all)` still refuses `DUPLICATE_ID` / `NAME_CONFLICT` (ratchet).
5. A root whose `depends_on` target is absent from `available`: the refusal is `UNKNOWN_DEPENDENCY` naming the root,
   exactly as `resolve` refuses it today.
6. A duplicate `pack_id` **inside** the closure (beta declared twice): refused `DUPLICATE_ID`.
7. `compositionClosure` on a cycle (alpha ↔ beta) terminates and returns both; the cycle refusal comes from
   `resolveRooted`, as today.
8. A root id that names no available pack: an `IllegalArgumentException` from the domain. The application maps
   it to `activation_invalid` later (WP06); the resolver never invents a refusal code for it.

Run them and see them fail (`./gradlew :packs:test --tests '*PackCompositionResolverTest*'`).

### Subtask T009: Public `compositionClosure`

**Purpose:** one closure rule, shared by the resolver and (later) `ClosureQueries` (WP04).

**Steps:**
1. Add `public static Set<String> compositionClosure(Collection<String> rootPackIds, Map<String,
   PackUnderResolution> byPackId)`. It returns descriptor `pack_id`s reached over `depends_on` and `parent_pack`,
   in first-seen order, and **tolerates** a missing target: it records the id, but does not descend from it.
2. Rename the existing private `closureOf` to `dependsOnClosure` and keep its callers (`checkReferences`) on it.
   Do not change its semantics.
3. Keep both under the limits: the loop body is short. Extract `successorsOf(pack)` (deps + optional parent).

**Validation:** the T008 cases on closure membership pass. Checkstyle and Error Prone are clean.

### Subtask T010: `resolveRooted(available, roots)`

**Purpose:** run every existing check over the closure only.

**Steps:**
1. Signature: `public static List<PackUnderResolution> resolveRooted(List<PackUnderResolution> available,
   Collection<PackIdentifier> roots)`. Roots are configured ids.
2. Map roots to their `PackUnderResolution` by `configured().id()`. An unknown root throws
   `IllegalArgumentException("no available pack is configured as …")`.
3. Build a **lenient** `pack_id → pack` index of `available` that keeps the first pack per id and remembers the
   duplicate ids. Do not refuse yet: a duplicate outside the closure must not be judged.
4. Compute `compositionClosure` from the roots' descriptor `pack_id`s, then select `available` entries whose
   `pack_id` is in the closure, **including every duplicate of an id in the closure** (so `resolve` can refuse
   `DUPLICATE_ID` naming both).
5. Return `resolve(selected)`. Every refusal (cycle, out of range, unknown dependency, level, sibling contest, name
   conflict among closure members) is the existing one.
6. `resolve(packs)` stays as it is. Prove that the offering path is unchanged in T012.

**Edge cases:**
- A missing dependency target stays missing in `selected`, and `resolve` refuses `UNKNOWN_DEPENDENCY` (the
  `dependenciesOf` path). Do not pre-empt it.
- A `parent_pack` target outside `available` is `PARENT_UNRESOLVED` from `resolve`.

### Subtask T011: `OfferingReadService.composed(roots)` and the rooted closure read

**Purpose:** read facts only for the closure (A3), and give WP04 a reusable rooted compose.

**Steps (red first in `OfferingReadServiceTest`):**
1. Tests, with the existing fakes for `PackCatalogRepository`, `PackDescriptorRepository` and
   `PackArtifactRepository`:
   - with alpha → beta and gamma, `composed({alpha})` calls `compositionFacts` for alpha and beta only (assert the
     fake's call log);
   - gamma's descriptor is missing (`find` empty) or invalid (a finding from `PackPolicy.descriptorFindings`):
     `composed({alpha})` succeeds, and `composed(all)` refuses `pack_unreadable` exactly as today;
   - gamma's facts would throw `pack_unreadable`: `composed({alpha})` succeeds (never read);
   - a **root** with no readable descriptor refuses `pack_unreadable` naming it;
   - a closure member reached by `pack_id` that only an unreadable pack could declare: refuse `pack_unreadable`
     naming the unreadable pack(s) when the closure has a missing `depends_on` target **and** some descriptor was
     unreadable; otherwise `pack_dependency_unknown`. Document this rule in the Javadoc. It keeps the answer
     actionable: we cannot know the unreadable pack's id.
2. Implement:
   - `composed()` becomes `composed(allConfiguredIds())`;
   - `composed(Collection<PackIdentifier> roots)` reads each configured descriptor tolerantly into
     `Map<ConfiguredPack, Optional<PackDescriptor>>` (a private `tryDescriptor` that catches the `pack_unreadable`
     of `descriptorOf` and records it), builds descriptor-only `PackUnderResolution`s for the readable ones, and
     computes the closure with `PackCompositionResolver.compositionClosure`;
   - **duplicates in the tolerant read (J7):** keep **every** readable descriptor, duplicates of a `pack_id` or a
     `name` included. Never collapse them to the first in the tolerant index that feeds the closure walk. When a
     duplicated id is in the closure, pass all of its holders to `resolveRooted`, so `indexById` (and `indexByName`)
     still refuse `pack_duplicate_id` / `pack_name_conflict` naming both. Add the test: two packs declaring beta's
     `pack_id`, alpha depending on it, `composed({alpha})` refuses `pack_duplicate_id` naming both;
   - the closure check: a root unreadable → refuse; a missing target → the rule above;
   - read facts only for closure members, build the full `PackUnderResolution`s, call `resolveRooted`, then run the
     existing `checkReferences` loop over the result.
3. Add `public ConsistentRead.Verified<List<OfferingEntry>> closure(Collection<PackIdentifier> roots)`, the verified
   rooted counterpart of `offering()`, reusing `entries(...)` with the closure. WP04 builds `ClosureQueries` over the
   unverified `composed(roots)` inside its own single `ConsistentRead`, so keep both public.
4. Keep `composed` short: extract `readDescriptors`, `closureMembers` and `withFacts`. Each method stays ≤ 60 lines.

**Validation:** `./gradlew :packs:test --tests '*OfferingReadServiceTest*' --tests '*PackCompositionResolverTest*'`.

### Subtask T012: The offering ratchet

**Purpose:** FR-020 and SC-004: `/offering` is unchanged.

**Steps:**
1. Run every offering and composition scenario: `./gradlew :deployable:test --tests '*CharterContractSuite*'`.
   `offering.feature` and the non-`@WIP` scenarios of `composition.feature` pass untouched.
2. Add one `OfferingReadServiceTest` case: for a fixture of five packs with a sibling contest settled by `overrides`,
   `composed()` and `composed(allConfiguredIds())` return equal lists, and `offering()` refuses alike for each of the
   eleven composition codes you can provoke with the existing fixtures (reuse `SinglePackCompose` and the
   resolver-test builders; do not invent new fixture packs).
3. Do **not** touch the `@WIP` scenario in `composition.feature`. WP08 rewrites it against the closure read.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand.

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines per method, nested-if depth ≤ 1. The limits are not enforced on `main` yet, so
hold to them by construction. `composed` and `resolveRooted` are the risk; extract helpers. No new suppression, and
no disabled test.

## Gate commands

```bash
./gradlew :packs:test --tests '*PackCompositionResolverTest*' --tests '*OfferingReadServiceTest*'
./gradlew :packs:check -x openPacksRoundTrip
./gradlew :deployable:test --tests '*CharterContractSuite*'
```

## Definition of Done

- [ ] `resolveRooted` and public `compositionClosure` exist; `checkReferences` still uses the `depends_on`-only
      closure.
- [ ] `composed(roots)` reads facts only for closure packs (asserted by a call log), and tolerates unreadable,
      duplicate or conflicting packs outside the closure.
- [ ] `composed()` equals `composed(all)`; `offering.feature` and `composition.feature` pass unchanged.
- [ ] The T008/T011 tests were seen red, then green (note it in the review evidence).
- [ ] No new suppression, and no disabled test.

## Risks

- **The parent edge in the compose.** Adding `parent_pack` to the closure could pull a parent the offering never
  ordered before. `resolve` already requires the parent present (`PARENT_UNRESOLVED`), so the parent is in the
  offering today; the ratchet test proves the order did not move.
- **Error-message drift.** A refusal reached through `resolveRooted` must carry the same `detail` and `packs` as
  through `resolve`. Compare whole `Refusal` values in the tests, not only codes.

## Reviewer guidance (reviewer-renata)

- **Read the call-log assertion.** A test that only checks the answer passes even if gamma's facts are read and
  swallowed. Demand the fake's call list.
- **Check two closure functions:** grep `dependsOnClosure` in `checkReferences`. A merged function with a boolean
  flag is a red flag.
- **Non-fakeable evidence:** the red-run output of T008 (failing names), then green; the full
  `CharterContractSuite` summary line (same scenario count as before the WP).
- **Check the unknown-root path** throws `IllegalArgumentException`, not a `CharterRefusal` with a made-up code.
