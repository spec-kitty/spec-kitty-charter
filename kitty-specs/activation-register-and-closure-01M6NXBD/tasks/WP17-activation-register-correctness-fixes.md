---
work_package_id: WP17
title: Activation register correctness fixes (post-PR-A squad)
dependencies:
- WP16
requirement_refs:
- FR-001
- FR-003
- FR-006
- FR-007
- FR-019
- FR-022
- NFR-001
- NFR-004
- NFR-005
- NFR-007
- C-001
- C-002
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T105
- T106
- T107
- T108
- T109
- T110
- T111
- T112
- T113
- T114
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/UnknownRootException.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/ActivationDirectoryName.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationDirectoryNameTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryCaseFoldTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryLockTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryAtomicityTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceRaceTest.java
- contracts/charter/scenarios/activation-register-guards.feature
- contracts/charter/changes/2026-11-06-requiring-pack-via.md
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/UnknownRootException.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/ActivationDirectoryName.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationDirectoryNameTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryCaseFoldTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryLockTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryAtomicityTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceRaceTest.java
- contracts/charter/scenarios/activation-register-guards.feature
- contracts/charter/changes/2026-11-06-requiring-pack-via.md
- contracts/_shared/schemas/RequiringPack.yaml
role: implementer
tags: []
tracker_refs: []
---

# WP17: Activation register correctness fixes (post-PR-A squad)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Fix the correctness findings the post-PR-A squad (architect-alphonso, reviewer-renata) raised on lane-i, before PR B's
write WPs build on them. WP13 depends on this WP.

1. Preconditions are evaluated **before** content processing, so a stale validator answers `412` even with an invalid
   body.
2. The file store never aliases `ABC` and `abc` on a case-insensitive file system.
3. The per-id lock map is bounded.
4. `PacksAvailability` catches only a typed `UnknownRootException`.
5. `ClosureQueries` de-duplicates presets once, and builds both the presets map and `RequiredFloor` from that list.
6. The registration gate composes through `composedWithFacts(roots)`, a verified read with a shared policy source.
7. Tests:
   - service-level races (8 writers through `put`; create against create with `If-None-Match: *`);
   - an atomicity test that fails on a non-atomic write;
   - 401/403 scenarios for `/activations`.
8. `RequiringPack.via` is populated from the snapshot's extends chain, or documented as not populated yet.
9. Two of renata's NOTEs: an empty `violations` list, and the first unknown member only.

Implementation command:
`spec-kitty agent action implement WP17 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. PR A is WP01–WP10 plus WP16 and WP17.
- **Squad findings:**
  - alphonso: the MINORs PacksAvailability, presets split-brain and policy gate, and the NOTEs `via` and presets copy;
  - renata: the MINORs precondition order, case-fold, locks, `via`, PacksAvailability, and the fakeable test with
    the races, and the NOTEs empty violations, first unknown member and 401/403.

  Every finding's disposition is in `tasks.md` "PR A squad dispositions".
- **Rulings:** RFC 9110 §13.2.2 (preconditions are evaluated before the method's own processing); ADR-0020 D6 and D10;
  ADR-0053 d6 (the last holder in merge order wins); ADR-0012 D3 (the highest level wins).
- **Collisions:** rebase over #377, #379, #384, #390, #391, #392, #393 and #394; and over WP16's moves (the advices
  now live in `activations.infra.api`, the handlers in `ai.speckitty.charter.web`).
- **Grounding (lane-i, `.worktrees/activation-register-and-closure-01M6NXBD-lane-i`):**
  - `ActivationService.put` (~lines 122–131) calls `preconditionOf(...)` (header syntax only), then
    `checked(built)` (one `packs.snapshot` plus `DocumentRules`), and only then `matched(id, value)`, which compares
    the `If-Match` with the stored revision. `CreateOnly` learns that the id exists only inside
    `repository.create`. A stale `If-Match` with an invalid body therefore answers 422, and a closure snapshot is
    taken that a 412 never needs.
  - `FileActivationRepository.directory(id)` is `root.resolve(id.value())`. The `locks` field is a
    `ConcurrentMap<ActivationId, ReentrantLock>` filled by `computeIfAbsent` and never shrunk. `ActivationId`'s
    grammar `^[A-Za-z0-9][A-Za-z0-9-]{0,63}$` admits case variants.
  - `PacksAvailability` (~line 71) has `catch (IllegalArgumentException unknownRoot)`, which also swallows any other
    `IllegalArgumentException` from the compose.
  - `ClosureQueries.viewOf` builds `presetsByPack` (every preset of every closure pack) and passes it to both
    `RequiredFloor.of(presetsByPack)` (all copies) and `presetsOf(presetsByPack)` (the last holder wins). A preset id
    two packs hold therefore puts **both** copies' must-have items into the floor, while only one copy is reachable as
    a preset.
  - `PackMutabilityPolicy`'s production constructor composes with `offerings.composed(Set.of(root))`, an unverified
    read that reads the policy through `MutabilityPolicyRepository` separately.
  - `FileActivationRepositoryConcurrencyTest.readersRacingWritersOnlyEverSeeAWholeWrittenDocument` reads through
    `store.find`, which takes the same per-id lock as the writers. It would pass with a non-atomic write, so it is
    fakeable.
  - `DocumentRules` (~line 198) builds `new RequiringPack(pack, List.of())`, an always-empty `via`.
    `contracts/_shared/schemas/RequiringPack.yaml` describes `via` as "the extends chain … from the pack the
    activation names to packId".
  - `PackMutabilityPolicyTest` lives at `service/packs/src/test/java/ai/speckitty/charter/packs/application/` on
    lane-i, not at the `domain/` path WP03's map names.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR A).
- Lane worktree from `lanes.json`. It depends on WP16. WP13 (PR B) depends on this WP.
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

**Ordering rule with WP17 (A4, option b):** WP17 lands first on `ClosureQueries.java`, `ClosureView.java` and
`ClosureQueriesTest.java`. WP11's review waits until WP17 is approved; WP11 rebases its lane over WP17 before review;
and at replay WP11's commits on these files go only into PR B.

- `service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationService.java`, and its
  `ActivationServiceTest.java` and `FakeAvailablePacks.java` (owner WP09): T105, T111.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepository.java`, and
  `FileActivationRepositoryConcurrencyTest.java` and `FileActivationRepositoryTest.java` (owner WP07): T106, T107,
  T111.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java` (owner WP05),
  and `PacksAvailabilityTest.java` (owner WP09): T108, T113.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java`, `ClosureView.java` and
  `ClosureQueriesTest.java` (owner WP04): T108, T109, T113.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java` and
  `PackMutabilityPolicyTest.java` (owner WP03): T110.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java` (owner WP02): T108, only
  if the unknown-root check moves there.
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java`, `DocumentRules.java`
  and `DocumentRulesTest.java` (owner WP06): T113.
- `docs/architecture/offering-and-activation.md` (owner WP10, approved). Two changes:
  - the Mode 1 refusal list and the canonical-form paragraph state that preconditions are evaluated **first**: 412
    before 422 or 409. Rationale: T105 changes the observable order, and the architecture doc must not describe
    the old one;
  - the storage sentence gains the encoded directory name (T106).
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationProblems.java`,
  `ActivationBodyAdvice.java`, and their tests (owner WP16): T114.

## Subtask Guidance

### Subtask T105: Preconditions before content (renata MINOR)

**Steps (red first):**
1. In `ActivationServiceTest` (out-of-map), store a document. Then:
   - `put` with a stale `If-Match` and a body `DocumentRules` refuses (an unknown id) answers `PreconditionException(STALE)`
     (412), not `activation_invalid`;
   - assert `FakeAvailablePacks` recorded **zero** snapshots;
   - the same for `If-None-Match: *` on an existing id with an invalid body (412);
   - for `delete`, the existing order is already header-first; keep a test.
2. Add the scenario "A stale If-Match is refused before the body is judged" to `activation-register-guards.feature`
   (T112).
3. Implement:
   - `put` resolves the precondition **first**: `CreateOnly` checks `repository.find(id).isPresent()`, and `Matching`
     calls `matched(id, value)`;
   - only then does it run `checked(built)`;
   - the repository's compare-and-set still re-checks, so a writer that slips in between still gives 412 (T111
     proves it).

   Keep `put` short: extract `expectedOf(id, precondition)` returning the `Optional<Revision>` or the create-only
   marker.
4. Docs (out-of-map, owner WP10): in `docs/architecture/offering-and-activation.md`, the Mode 1 refusal list and the
   canonical-form paragraph state that a write evaluates its preconditions first. `428`, `412` and `400` (malformed
   validators) come before any `422 activation_invalid` or `409 artifact_required`/`artifact_locked`, and a refused
   precondition takes no closure snapshot. Run `python3 scripts/docs/check_docs.py all`.

### Subtask T106: Case-insensitive file systems (renata MINOR)

**Decision:** reversible encoding (preferred over case folding). The directory name is derived from the id so that two
ids that differ only in case get different names on any file system, and the name decodes back to the id.

**Steps (red first):**
1. `ActivationDirectoryNameTest`:
   - `encode("ABC") != encode("abc")`, even after `toLowerCase(Locale.ROOT)` of both. The name itself must be
     case-insensitive-unique;
   - `decode(encode(id)) == id` for generated ids;
   - every encoded name matches `^[a-z0-9-]+$`.

   Suggested scheme: the lower-cased id, a `-`, then a lowercase hex bitmap of which characters were upper case.
   `aBc` gives `abc-2`. It stays readable and is reversible.
2. `FileActivationRepositoryCaseFoldTest`:
   - store `ABC` and `abc` with different documents, and read both back distinct;
   - simulate a case-insensitive file system with a `@TempDir` plus a `Path` mapper, or assert on the encoded names on
     disk;
   - the id is also stored inside `activation.json`'s wrapper, or verified against the decoded directory name on read.
     Decide which, and record it. A mismatch is `ActivationStorageException`.
3. **Migration:** no released store exists (the activation API is new in PR A). Note in the Javadoc that the layout is
   `<root>/<encoded id>/activation.json`, and update the storage sentence of `docs/architecture/offering-and-activation.md`
   (an out-of-map edit; WP10 owns the file).

### Subtask T107: A bounded lock map (renata MINOR)

**Steps (red first, `FileActivationRepositoryLockTest`):**
1. After 10,000 writes to distinct ids, the lock structure's size stays bounded. Assert it through a package-private
   accessor: ≤ the stripe count, or 0 after release when locks are removed on release.
2. Two writes to one id are still serialised (reuse the CAS contract test).
3. Implement **striped locks**: a fixed array of `ReentrantLock` (for example 64), indexed by
   `Math.floorMod(encodedName.hashCode(), stripes)`. Use the T106 encoded name, so case variants never share a key by
   accident (they may share a stripe, which is harmless). Simpler and safer than remove-on-release, which races.
   Document it.

### Subtask T108: A typed `UnknownRootException` (alphonso and renata MINOR)

**Steps (red first):**
1. `ClosureQueriesTest` (out-of-map): `snapshot(Optional.of([unknown]))` throws `UnknownRootException` naming the id.
   Any other `IllegalArgumentException` from the compose propagates unchanged.
2. Create `packs/application/UnknownRootException.java`, a `RuntimeException` carrying the unknown configured ids.
   `ClosureQueries.snapshot` checks the roots against the catalog's configured ids **before** composing, inside the
   same `ConsistentRead` supplier, and throws it.
3. `PacksAvailability` catches **only** `UnknownRootException`. Delete the `IllegalArgumentException` catch.
   `PacksAvailabilityTest`: a compose that throws a plain `IllegalArgumentException` is **not** turned into
   `unknownPacks`.

### Subtask T109: One preset list for the presets map and the floor (alphonso MINOR, and NOTE presets copy)

**Steps (red first, `ClosureQueriesTest`):**
1. Two closure packs hold preset `team`. The earlier one marks `directive:A` must-have; the later one, which wins in
   merge order, marks `directive:B`. The floor holds B (from the later pack) and **not** A, and `presets().get("team")`
   is the later copy.
2. Implement:
   - de-duplicate once into a `List<HeldPreset>`, where the last holder in merge order wins;
   - build `presetsOf` from that list, and `RequiredFloor.of(...)` from the same list, regrouped by holder;
   - copy each `Preset` once: `ClosureView` holds the de-duplicated list, and the map is a view over it.
3. Record in the `ClosureView` Javadoc that the floor follows the preset that wins (ADR-0053 d6).

### Subtask T110: The registration gate reads through `composedWithFacts` (alphonso MINOR)

**Steps (red first, `PackMutabilityPolicyTest`, which lives in `application/` on lane-i):**
1. The gate's closure and the policies come from **one** `composedWithFacts(Set.of(root))` result inside
   `ConsistentRead`: `PackCompositionFacts.policy()` (WP03) per member. No separate
   `MutabilityPolicyRepository.policyOf` read.
2. A pack change between the compose and the policy read answers `stale_input`, never a mixed answer. Use a
   `PackSources` fake whose fingerprint moves.
3. Keep the test constructor `PackMutabilityPolicy(MutabilityPolicySource)`. Remove `MutabilityPolicyRepository` only
   if nothing else uses it; otherwise leave it and say why.

### Subtask T111: Races and a non-fakeable atomicity test (renata MINOR)

**Steps:**
1. `ActivationServiceRaceTest`, through `ActivationService` over `FileActivationRepository` on a `@TempDir`:
   - **8-writer `put`:** store a document, then have 8 threads `put` distinct valid documents with the same `If-Match`,
     released by a `CountDownLatch`. Exactly one succeeds and seven throw `PreconditionException(STALE)`. Run 100
     iterations, with a timeout and no `sleep`;
   - **create against create:** 2 threads `put` with `If-None-Match: *` on one new id. Exactly one gets
     `Written(created = true)`, and the other 412. Run 100 iterations.
2. Replace `readersRacingWritersOnlyEverSeeAWholeWrittenDocument` with a test in
   `FileActivationRepositoryAtomicityTest` that **would fail on a non-atomic write**. Choose one, and justify it in a
   comment:
   - (a) readers read `activation.json` **directly from disk** with `Files.readAllBytes`, **without** the
     repository's lock, while writers run, and every read parses to a whole document whose revision is one written;
   - (b) a `Rename` seam that records the target's bytes at the moment of the move and asserts the target was never
     written in place. Also plant a deliberately non-atomic `Rename` (copy then truncate) in the test, and show the
     assertion fails with it.
3. Delete the fakeable test from `FileActivationRepositoryConcurrencyTest` (out-of-map) in the same commit.

### Subtask T112: The guards feature (renata NOTE 401/403, plus T105's scenario)

**Steps:** create `contracts/charter/scenarios/activation-register-guards.feature`, tagged `@contract @activations`:
- `When I POST "/activations" without the worktree token` → 401, with `WWW-Authenticate: Bearer` (the
  `SecuritySteps` step exists);
- `When I GET "/activations/a-1" with the Authorization "Bearer not-the-worktree-token-…"` → 401;
- a browser request (`with the header "Origin": "https://evil.example"`) to `PUT /activations/a-1` → 403;
- T105: a stored document, then a `PUT` with a stale `If-Match` and an invalid body → 412, and a `GET` shows the
  revision unchanged;
- T105: `If-None-Match: *` on an existing id with an invalid body → 412.

Write the scenarios before the T105 fix, and see the two precondition ones fail with 422.

### Subtask T113: `RequiringPack.via` (alphonso NOTE, renata MINOR)

**Decision rule:** populate `via` when the snapshot can give the extends chain cheaply. It can:
`PackUnderResolution.descriptor().parentPack()` is already in the composed closure.

**Steps (red first, `DocumentRulesTest` and `PacksAvailabilityTest`):**
1. `ClosureView` exposes `parentOf(descriptorPackId)`, from the descriptors already read. The adapter maps it into
   `ClosureSnapshot` as `extendsChain(rootConfiguredId, requiringConfiguredId)`. That gives the configured ids from the
   pack the activation names, up the `parent_pack` chain, to the requiring pack, with both ends excluded per the
   schema text. Check the exact endpoint rule against `RequiringPack.yaml` and its examples, and follow them.
2. `DocumentRules` fills `via` for each requiring pack: empty when an activation root declares it itself, the chain
   when it is inherited over `parent_pack`. When it is reached over `depends_on` only (not an extends chain), `via` is
   `[]`.
3. `contracts/_shared/schemas/RequiringPack.yaml`: add one sentence to the `via` description, saying it is empty for
   a pack reached over `depends_on` and not `parent_pack`. Add the fragment
   `contracts/charter/changes/2026-11-06-requiring-pack-via.md` (`### Changed`): `via` is now populated for the 409
   refusals.
4. **If** the chain proves not cheap (for example, roots are not available to `DocumentRules`): keep `via: []`, say
   in the schema description that `via` is not populated yet, put that in the fragment instead, and record a
   follow-up issue in the review notes.

### Subtask T114: Renata's NOTEs on the 422 body

**Steps (red first, in the moved `ActivationProblemsTest` and `ActivationBodyAdviceTest`):**
1. **No empty `violations`.** An unbindable body that Jackson reports without a path (for example
   `HttpMessageNotReadableException` on `"packs": 5`) still answers one violation: pointer `""`, code `unreadable_body`.
   Never `violations: []` on a 422.
2. **Unknown members: report all.** The strict re-read stops at the first `UnrecognizedPropertyException`. Either
   collect every unknown member (walk the tree against the DTO's known property names per object level), or document
   "the first unknown member" in the `ActivationInvalid` response description. Collecting all is preferred, because a
   client then fixes the body in one round trip. Test a body with two unknown members, and expect two violations.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand (CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if
depth ≤ 1). No new suppression, no disabled test, and no `@WIP`.

## Gate commands

```bash
./gradlew :contracts:check :charter-api:compileJava
./gradlew :packs:test --tests '*ClosureQueriesTest*' --tests '*PackMutabilityPolicyTest*'
./gradlew :activations:check
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' \
  --tests '*ActivationRestartTest*' --tests '*ActivationRoundTripPropertyTest*' --tests '*ReservedRefusalCodesTest*'
```

## Definition of Done

- [ ] A stale `If-Match` and an existing id under `If-None-Match: *` answer 412 even with an invalid body, with no
      snapshot taken; the unit test and the scenarios pass.
- [ ] `ABC` and `abc` never alias on disk (reversible encoding); the lock structure is bounded (striped).
- [ ] `PacksAvailability` catches only `UnknownRootException`.
- [ ] The presets map and the floor come from one de-duplicated list; the two-copies test passes.
- [ ] The registration gate reads composition and policy in one verified read.
- [ ] The service-level races pass 100 of 100; the atomicity test fails against a planted non-atomic `Rename`.
- [ ] 401 and 403 scenarios exist for `/activations`.
- [ ] `via` is populated (or documented, with a fragment line); there is no empty `violations` on a 422; unknown
      members are all reported (or documented).
- [ ] No new suppression, and no disabled test.

## Risks

- **The layout change** (encoded directory names) after WP07: no released data exists, but the restart test and any
  fixture directory names must follow it.
- **`via` semantics** depend on the schema's endpoint rule; read the schema and its example before coding.
- **A double read in `put`:** reading the current revision for the precondition plus the CAS read costs one extra
  file read per conditional write. It is acceptable, and it keeps 412 cheap.

## Reviewer guidance (reviewer-renata)

- **Order:** read the new `put`. The precondition check must come **before** `checked(...)`, and the T105 test asserts
  zero snapshots.
- **Atomicity:** run the atomicity test against the planted non-atomic `Rename` and see it fail. A test that passes
  either way is the original finding again.
- **Races:** exactly 1 + 7 and exactly 1 + 1, over 100 iterations, through `ActivationService`, not the repository.
- **Case-fold:** on disk, the two directories for `ABC` and `abc` have names that differ when lower-cased.
- **Non-fakeable evidence:**
  - the failing run of the guard scenarios before T105, then green;
  - the planted-`Rename` failure output;
  - the floor test with two copies of `team`.
