---
work_package_id: WP03
title: Verified tactic reads with the source fingerprint
dependencies:
- WP02
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-010
- NFR-002
- NFR-003
- SC-001
- SC-004
planning_base_branch: fix/verified-source-fingerprint
merge_target_branch: fix/verified-source-fingerprint
branch_strategy: Planning artifacts for this mission were generated on fix/verified-source-fingerprint. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/verified-source-fingerprint unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-verified-source-fingerprint-01M5PG7Q
base_commit: 0a67b35f0872ab8c86e2377443941839e3c721c6
created_at: '2026-10-24T09:26:00.533866+00:00'
subtasks:
- T011
- T012
- T013
- T014
- T015
phase: Phase 3 - User stories 1 and 2
history:
- at: '2026-10-24T09:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ConsistentReadTest.java
- contracts/charter/scenarios/tactics.feature
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ServiceInfoQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/TacticReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/TacticController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ConsistentReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ServiceInfoQueryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TacticReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/TacticReadWiredTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/TacticControllerTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterWiring.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WorktreeGuardTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/GuardWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProductionWiringTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CucumberSpringConfiguration.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/DriftingSources.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReadConsistencySteps.java
- contracts/charter/scenarios/tactics.feature
- contracts/charter/scenarios/README.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 — Verified tactic reads with the source fingerprint

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Close issue #173 and finish the freshness rule: every built artifact read digests its inputs
before and after loading, answers 200 with the digest it verified as `Charter-Source-Fingerprint`
(once), and refuses with `stale_input` when the inputs changed, including when the load itself
refused.

Read first: [spec.md](../spec.md) (US1, US2, Edge Cases, FR-001..FR-005, FR-010, NFR-002),
[plan.md](../plan.md) ("ConsistentRead", "TacticReadService", "TacticController", "Testing
design", "Global digest consequence"), [data-model.md](../data-model.md) (refusal outcomes, all 7
rows), [research.md](../research.md) (R1, R3, R7). Then the code: `ServiceInfoQuery`,
`TacticReadService`, `TacticController`, `InfoController`, `CharterHeaders`, `RefusalHandler`,
`CharterWiring`, `PackPersistenceWiring`, the slice `config` packages, and the scenario glue in
`service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/` (`CharterSteps` —
especially the `@After` fingerprint check around lines 84-110 —, `ScenarioHttp`,
`DriftingSources`, `ReadConsistencySteps`, `CucumberSpringConfiguration`, `ScenarioPacks`), and
`contracts/charter/scenarios/errors.feature` (the `/info` stale scenarios, whose body is pinned).

WP02 is merged into your base: the fingerprint now covers tactic files.

Success:

- `tactics.feature` is **red on the code before this WP** for the right reasons (recorded) and green after.
- `ConsistentReadTest` has one test per outcome row (7 rows) plus event-order assertions.
- `/info`'s existing scenarios and pinned bodies are unchanged and green.
- `./gradlew verifyAll -PopenPacksDir=...` green on Java 25.

## Context & Constraints

- **Rings**: `ConsistentRead` in `application` depends only on the `domain` port `PackSources`,
  kernel and `java.*` (`APPLICATION_IS_EDGE`). The controller depends on `application` only.
  Nothing threads a fingerprint through a domain port (`STORAGE_RETURNS_DOMAIN`).
- **No stereotype on `ConsistentRead`** (`@Service`/`@Component` forbidden): production holds two
  `PackSources` beans and the scenario suite three. It is created by `@Bean` methods only, and use
  cases receive a `ConsistentRead`, never a `PackSources`.
- **Pinned body**: `/info`'s stale detail stays exactly
  `The inputs changed while the service info was read; read it again.`
- **Style, commits, Java, Gradle**: as WP02 (palantir, Checkstyle, Error Prone, NullAway,
  doclint, complexity ≤ 15, every commit green, red-first recorded not committed;
  `JAVA_HOME=/usr/lib/jvm/temurin-25`, `-PopenPacksDir=/home/user/spec-kitty/spec-kitty-open-packs`,
  `--max-workers=2` after a 429, never touch verification metadata).

## Branch Strategy

- Planning/base branch: `fix/verified-source-fingerprint`; final merge target:
  `fix/verified-source-fingerprint`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Run
  `spec-kitty agent action implement WP03 --agent claude --mission verified-source-fingerprint-01M5PG7Q`
  (it refuses until WP02 is approved) and work only in the worktree it gives you.

## Subtasks

### T011 — Red first: `tactics.feature` and the tactic-file drift step

**Purpose**: Pin US1 and US2 at the contract level before changing production code.

**Steps**:
1. Add to `ReadConsistencySteps` the step
   `@Given("a tactic file changes while the next read is answered")`: it arms `drift` with a
   change that finds the `*.tactic.yaml` files under `packs.locations()` roots (strict suffix
   match on the file name; sorted walk), requires **exactly one**
   (`IllegalStateException` otherwise; never fall back to a shard — the Background of other
   features writes shards named like `tactic.graph.yaml`), rewrites its `name:` line (the file
   stays a valid tactic with the same id) and asserts the bytes changed. Reuse the existing
   `rewrite(...)` helper if its shape fits.
2. Create `contracts/charter/scenarios/tactics.feature`, tagged `@contract`, with a Background
   that writes the tactic:

   ```gherkin
   @contract
   Feature: The tactic reads answer with the source fingerprint they verified
     Every tactic read digests its inputs before and after it loads them (ADR-0018 D11). A 200
     carries that digest once as Charter-Source-Fingerprint, equal to what /info answers for the
     same inputs; inputs that change while the read is answered refuse it with stale_input, and
     no refusal carries the fingerprint.

     Background:
       Given the org pack "qa" with the file "rehearse.tactic.yaml":
         """
         id: rehearse
         schema_version: '1.0'
         name: Rehearse
         steps:
           - title: Only step
         """
   ```

   Scenarios (check every step phrase against the glue; reuse, do not duplicate). The `@After`
   harness check already asserts, for every exchange, exactly one fingerprint on a 200 equal to
   `/info`'s right after, and none on a refusal; the explicit header steps make the intent
   readable:
   - **A tactic list answers with the source fingerprint it verified**: `GET /packs/qa/tactics`
     → 200; `the response header "Charter-Source-Fingerprint" appears once`; the body holds the
     tactic `rehearse` (use the body/field step that exists; if none fits a JSON array, add one
     small step that asserts an element with `id` equal to a value).
   - **One tactic answers with the source fingerprint it verified**:
     `GET /packs/qa/tactics/rehearse` → 200; header appears once; field `id` is `rehearse`.
   - **A tactic list whose tactic file changes while it is answered is refused with stale_input**:
     `And a tactic file changes while the next read is answered`; `GET /packs/qa/tactics` → 500,
     body exactly
     `{"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the tactics were read; read them again."}`,
     no fingerprint header; then `GET /packs/qa/tactics` → 200.
   - **One tactic whose file changes while it is answered is refused with stale_input**: same on
     `/packs/qa/tactics/rehearse`, detail
     `The inputs changed while the tactic was read; read it again.`, then 200 on retry.
   - **A read of an absent tactic whose inputs change is stale, not not-found**:
     `And a tactic file changes while the next read is answered` (the tactic-file step; this
     feature has no graph shard, so "a pack file changes ..." would throw);
     `GET /packs/qa/tactics/no-such-tactic` → 500 `stale_input`; retry → 404 `tactic_not_found`.
   - **A tactic read that watches for a change and sees none answers with the digest it
     verified** (negative control): `And no pack file changes while the next read is answered`;
     `GET /packs/qa/tactics` → 200; header appears once.
   - Two missing-directory scenarios. Add to `ReadConsistencySteps` a step
     `Given the directory of the pack {string} is missing`: find the pack's root in
     `packs.locations()` (package-visible; matches by configured name) and delete it
     recursively; `ScenarioPacks.delete()` already tolerates a root that is gone. To configure a
     sibling, write any file to it first with the existing "the {word} pack ... with the file"
     step, then remove its directory.
     - **A tactic read of a pack whose directory is missing is refused with pack_unreadable**:
       remove `qa`'s directory; `GET /packs/qa/tactics` → 500 `pack_unreadable`, no fingerprint.
       Red reason on the base: 404 `pack_not_found`.
     - **A missing sibling pack directory refuses a tactic read of a healthy pack**: configure org
       pack `other` (one file), remove its directory; `GET /packs/qa/tactics` → 500
       `pack_unreadable`; `GET /info` → 500 `pack_unreadable`. Red reason on the base: 200.
3. **Run red and record** (`./gradlew :deployable:test --tests '*CharterContractSuite*'`). The
   expected reasons, which the record must show:
   - the 200 scenarios fail in the `@After` check on the missing `Charter-Source-Fingerprint`
     (not on body conformance; if body conformance fails first, fix the fixture);
   - the stale scenarios fail on "expected 500, got 200" (on this base the armed change fires
     during the harness's `/info` probe, because the tactic read takes no digest yet);
   - stale beats not-found fails with 404 instead of 500;
   - the missing own directory fails with 404 `pack_not_found` instead of 500, and the missing
     sibling with 200 instead of 500.
   Keep this record for the review request. Do not commit a red state.

### T012 — `ConsistentRead`

**Steps**:
1. `application/ConsistentRead.java`, `public final class`, no stereotype:
   ```java
   public ConsistentRead(PackSources sources)
   public <T> Verified<T> read(String staleDetail, Supplier<T> load)
   public record Verified<T>(T value, String fingerprint) {}
   ```
   Semantics (data-model.md table): first digest (a refusal propagates, load not run); load; on a
   `RuntimeException` from the load, take the second digest with `digestOrNull` (only a
   `CharterRefusal` becomes `null`): differ or `null` → `stale_input` with `staleDetail` and the
   load's exception as cause; equal → rethrow the load's exception unchanged. On success, second
   digest: differ or `null` → `stale_input`; equal → `Verified(value, first)`. A non-refusal
   exception from either digest propagates.
2. `ConsistentReadTest` with a scripted, event-logging fake `PackSources` (records `digest` and,
   through the supplier, `load`): one test per row:
   1. first digest refuses → same refusal, load never runs, one digest;
   2. d1, value, d1 → `Verified(value, d1)`, events exactly `[digest, load, digest]`;
   3a. d1, value, d2 → `stale_input` with message exactly `staleDetail`;
   3b. d1, value, second refuses → `stale_input`;
   4. d1, `TACTIC_NOT_FOUND` refusal, d1 → the same refusal instance (`isSameAs`), two digests;
   5a. d1, refusal, d2 → `stale_input`, cause is the load's refusal;
   5b. d1, refusal, second refuses → `stale_input`;
   6. d1, `IllegalStateException`, d1 → rethrown unchanged;
   7. second digest throws `IllegalStateException` → propagates, not `stale_input`.

### T013 — `ServiceInfoQuery` and production wiring

**Steps**:
1. `ServiceInfoQuery(String worktree, ConsistentRead read)`; `answer()` returns
   `read.read("The inputs changed while the service info was read; read it again.", () -> worktree)`
   mapped to `new ServiceInfo(worktree, verified.fingerprint())`. Its private `digestOrNull` goes.
2. `CharterWiring`: `@Bean ConsistentRead consistentRead(ProjectConfigSources sources)`; the
   `serviceInfoQuery` bean takes `ConsistentRead`.
3. Update `ServiceInfoQueryTest` (wrap each fake in `new ConsistentRead(...)`; keep every
   assertion, including the verbatim stale detail), `WorktreeGuardTest`, `GuardWiringTest` (they
   construct `ServiceInfoQuery`: `service/packs/.../infra/api/WorktreeGuardTest.java`,
   `service/deployable/.../GuardWiringTest.java`), and
   `CharterWiringTest` if it asserts the bean graph (one `ConsistentRead` over `ProjectConfigSources`).

### T014 — Verified tactic reads and the header

**Steps**:
1. `TacticReadService(PackRepository packs, TacticRepository tactics, ConsistentRead read)`
   (still `@Service`, `@Autowired`): `tacticsOf` returns
   `ConsistentRead.Verified<List<Tactic>>` through
   `read.read("The inputs changed while the tactics were read; read them again.", () -> { try { return packs.tacticsOf(pack); } catch (CharterRefusal r) { throw packRefusal(pack, r); } })`;
   `tactic` returns `Verified<Tactic>` with the not-found mapping **inside** the supplier and
   detail `The inputs changed while the tactic was read; read it again.`. Update the javadoc: a
   missing configured pack directory or an unreadable pack file is refused by the first digest
   with `pack_unreadable` (500); an identifier that names no configured pack is `pack_not_found`.
2. `TacticController`: both handlers
   `ResponseEntity.ok().headers(CharterHeaders.fingerprint(v.fingerprint())).body(...)`, as
   `InfoController` does.
3. Tests:
   - `TacticReadServiceTest`: a counting `PackSources` fake; two digests on the list, the single
     read and the 404 path; an absent pack and an absent tactic with digests d1 then d2 answer
     `stale_input` (not `pack_not_found`/`tactic_not_found`); `.value()` where the old tests read
     the list/tactic.
   - `TacticControllerTest`: header present with exactly one value equal to the fake's digest on
     both 200s; `header().doesNotExist("Charter-Source-Fingerprint")` on the 404s.
   - `TacticReadWiredTest`: its context (`StorageConfiguration` + `ApplicationConfiguration`) has
     no `ConsistentRead`; its `Root` config supplies
     `new ConsistentRead(new FileSystemPackSources(() -> locations))` over the same locations;
     assert `verified.fingerprint()` equals that sources' `fingerprint()`.

### T015 — Scenario wiring, docs of the seam, full gate

**Steps**:
1. `CucumberSpringConfiguration`: replace the `@Primary ServiceInfoQuery` override with a
   `@Bean @Primary ConsistentRead driftingConsistentRead(DriftingSources sources)`, so `/info` and
   the tactic reads both see an armed change. Keep `DriftingSources` over the real
   `ProjectConfigSources`.
2. `DriftingSources` javadoc: name every reader of the seam (the shared `ConsistentRead`), and
   note that an armed change fires on the next digest of any read, including the harness's `/info`
   probe after a 200.
3. `contracts/charter/scenarios/README.md`: add the new step(s) to the step list and mention
   `tactics.feature` where features are listed.
4. Run `./gradlew spotlessApply` and `./gradlew verifyAll -PopenPacksDir=...`. Green, including
   `HexagonalArchitectureTest` and all three existing features unchanged.
5. `contracts/charter/CHANGELOG.md` (owned by WP01) lists the scenario titles of T011. If a
   title changed, correct that list in this WP with a one-line out-of-map rationale in the commit
   message.

## Definition of Done

- [ ] Red run recorded with the four expected reasons.
- [ ] `ConsistentRead` has no stereotype; 7 outcome rows tested; `[digest, load, digest]` pinned.
- [ ] `/info` and both tactic reads go through `ConsistentRead`; not-found mapping inside the supplier.
- [ ] Tactic 200s carry the header once (controller test and scenarios); 404s carry none.
- [ ] Scenario suite uses one `@Primary ConsistentRead` over `DriftingSources`; the drift step edits exactly one `*.tactic.yaml`.
- [ ] `verifyAll` green on Java 25; spotless clean; `/info`'s pinned bodies unchanged.

## Risks

- **Bean ambiguity** in `ProductionWiringTest`, `CharterWiringTest`, the scenario context and
  `TacticReadWiredTest`. Run them all.
- **False green in the stale scenarios**: if the drift step touched a shard, the scenario would
  pass even without tactic coverage. Reviewers will read the step.
- **Header from a fresh digest** instead of the verified one: the controller must use
  `Verified.fingerprint()` only.

## Reviewer Guidance

- Read the red record: each scenario failed for the stated reason.
- Check `ConsistentRead` imports (domain port, kernel, java only) and that it is not scanned.
- Check the not-found mapping sits inside the supplier in both reads.
- Check the drift step's file selection.
