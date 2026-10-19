---
work_package_id: WP03
title: Consistent reads and the refusal pins
dependencies:
- WP02
requirement_refs:
- FR-014
- FR-015
- FR-016
- FR-019
- NFR-001
- NFR-005
- C-001
- SC-002
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-action-context-reserved-surface-01M5AFEK
base_commit: fb190ec630f8ed540dd22f1cc56f97f140d90f16
created_at: '2026-10-19T17:37:22.173394+00:00'
subtasks:
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - The reserved surface
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-read-app/src/main/java/ai/speckitty/charter/read/ConsistentLoad.java
create_intent:
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ConsistentLoad.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/ConsistentLoadTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ReadConsistencySteps.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/DriftingSources.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/ReservedRefusalCodesTest.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/PackListing.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/scenarios/errors.feature
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/responses/OfferingUnreadable.yaml
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ConsistentLoad.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/ConsistentLoadTest.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/PackListingQuery.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/PackListing.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/PackListingQueryTest.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/CharterHeaders.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/PacksController.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ReadConsistencySteps.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/DriftingSources.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractDescriptionsTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/HexagonalArchitectureTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/ReservedRefusalCodesTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03: Consistent reads and the refusal pins

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP03 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Issue #113 and decision D11 (`research.md` §3), with plan-point findings S3 to S6, O6 and O7
(§8).

1. **`stale_input` is raised for real** by every read that loads the offering:
   - **the reads:** the context read and `/packs`; WP02 did `/info`.
   - **the check:** the read digests its inputs, loads, then digests again. A difference is
     `stale_input`.
   - **on a failed load:** if the load throws a `CharterRefusal` and a re-digest differs from
     the first digest, the read answers `stale_input` instead. A half-written file is not a
     permanent `pack_unreadable`.
2. **Every 200 carries exactly one `Charter-Source-Fingerprint`:**
   - its value is the digest that read verified;
   - the read sets it once;
   - no refusal carries one;
   - `CharterHeaders` no longer digests at all.
3. **`pack_unreadable`** is pinned by a real input: a `*.graph.yaml` shard that is not valid YAML.
   The `service.feature` scenario that plants `internal_error` is retitled for what it tests:
   "An unexpected failure while the packs are read is refused with internal_error".
4. **`governance_policy_violation`** is pinned in two places:
   - a `@contract @reserved` scenario asserts its declaration (in the enum, 500, not
     retryable);
   - a unit test enforces that no main class except the enum and the title mapping references
     it.

   It is reported as a declared pin, not behavior.
5. **ArchUnit:** no test seam in main. Contract text: `stale_input` is raised, and
   `OfferingUnreadable` covers it.

**Done when** the scenarios and tests were seen red on assertions, then green, and
`verifyAll` is green.

## Context & Constraints

**Read:**

- `spec.md`: User Story 4, FR-014 to FR-016, FR-019;
- `research.md`: D11, and §8 findings S3 to S6, O6, O7.

**Constraints:**

- **Runner B boundary:** do not edit `FileSystemCharterPackRepository`,
  `PackDescriptorReader`, `CharterPack*` or `contracts/packs/**`. The `pack_unreadable`
  scenario uses a graph shard, which `GraphDocumentReader:67` refuses. **Check that file is
  not runner B's:** it is not on their list.
- **The test seam must test detection, not comparison (S6).** `DriftingSources`, in test
  sources:
  - wraps the **real** adapters;
  - **rewrites a real pack file on disk** between the first digest and the load (or during the
    load);
  - production code computes both digests.

  The negative control is the same seam armed to change nothing, which answers 200 with the
  verified digest.
- **Expected out-of-map edits** (record each with `add-history`):
  - `ActionContextQuery` and `ContextController` (WP01's) switch to `ConsistentLoad` and set the
    header;
  - `CharterWiring` (WP02's) wires `ConsistentLoad`;
  - `CucumberSpringConfiguration` (WP02's) registers the seam;
  - `service.feature` (WP02's) retitles the planted-failure scenario;
  - `CharterSteps` (WP04's) gains a step counting header values, if the steps need it.

  Keep each edit minimal.
- **CHANGELOG:** do not edit it. Record contract changes in history.
- **`ContractDescriptionsTest`** pins four sentences of the `CharterRefusalCode` description.
  Keep them, or update the test **only** where a sentence's truth changed: the reserved-code
  sentence does change for `stale_input`, which is now raised. Say so in history.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`

```bash
spec-kitty agent action implement WP03 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

**Environment:** `JAVA_HOME=/opt/jdk-25`; open-packs at `/home/user/spec-kitty-open-packs`.

## Subtasks & Detailed Guidance

### Subtask T012: Red scenarios

**In `errors.feature`** (keep `@contract` on every scenario, because the suite runs only
`@contract`):

1. **"A pack whose graph shard is not valid YAML is refused with pack_unreadable".** The shard
   content is, for example, `nodes: [` unterminated. GET `/packs` and the context read → 500
   `pack_unreadable`, and an exact body. Check `GraphDocumentReader:67`'s detail wording
   first. The response has no `Charter-Source-Fingerprint` header.
2. **"A read whose packs change while it is answered is refused with stale_input".** It uses the
   new step `Given a pack file changes while the next read is answered`, which arms
   `DriftingSources` to rewrite a pack file (append a node) once. Run it for the context read
   and `/packs`. Expect 500, `stale_input`, the exact title/detail, and no fingerprint header.
3. **"A pack file rewritten while it is parsed is stale, not unreadable".** The seam rewrites a
   shard to invalid YAML before the load and **leaves it rewritten**, so the re-digest after the
   failed load differs from the first digest. The read answers `stale_input`. A restore before
   the re-digest would be the ABA window D11 documents as unseen. Do not test that.
4. **Negative control:** the seam armed with "no change" → 200. The fingerprint header equals
   `/info`'s `sourceFingerprint` and appears exactly once (a new step,
   `the response header {string} appears once`).
5. **`@contract @reserved` "governance_policy_violation is reserved".**
   - **First, the contract (post-tasks finding 8).** `CharterRefusalCode.yaml` states status and
     retryability only in prose. Add a machine-readable `x-refusal` map, keyed by code, each
     `{status: 404|500, retryable: bool, raised: bool}`, for **all nine** codes. Clients and
     the steps read it.
     - `raised: false` for `governance_policy_violation`;
     - `raised: true` for `stale_input` (now);
     - `retryable: true` for `internal_error` and `stale_input` only.
     - Check `contracts/lint/ruleset.yaml` accepts the extension, and add a lint rule fixture if
       one is needed.
   - **Steps:**
     - `Then the contract declares the refusal code "governance_policy_violation"`;
     - `And the contract says "governance_policy_violation" is answered with status 500`;
     - `And the contract says "governance_policy_violation" may not be retried`;
     - `And the contract says "governance_policy_violation" is raised by no operation`.

     Implement them in `ReadConsistencySteps` by reading `x-refusal` from the test classpath
     under `contract/`. They are red before the `x-refusal` map exists.
   - The description must state this as "a declaration pin, not behavior".
   - Also add one `@contract` scenario that checks `x-refusal` against behavior: every code a
     scenario in this suite raises has `raised: true`. Keep it simple, for example a step table
     of the codes this suite raises.

**`scenarios/README.md`** (WP02's, out-of-map): add the new steps to "Step vocabulary", and add the `@reserved` rule:

- a reserved scenario pins a declaration, never behavior;
- it must also carry `@contract`;
- each one names the unit test that enforces "raised by nothing" and the issue that will raise
  the code (#48).

**Red run:** capture the assertion failures and the scenario count in history, then commit.

### Subtask T013: Consistent reads

**`ConsistentLoad`** (read-app, plain Java):

- **Constructor:** `(CharterPackRepository packs, PackSources sources)`.
- **Method:** `Loaded load()` returns `record Loaded(CharterOffering offering, String sourceFingerprint)`.
- **Algorithm:**
  1. `first = sources.fingerprint()`. When it throws, propagate it: an unreadable file is
     `pack_unreadable`.
  2. `try { offering = packs.load(); } catch (CharterRefusal refused) { if (!first.equals(digestOrNull())) throw stale(); throw refused; }`.
     `digestOrNull` catches a refusal from the re-digest and treats it as "changed".
  3. `second = sources.fingerprint()`. When it differs from `first`, `throw stale()`.
  4. Return `new Loaded(offering, second)`.
- **The refusal:** `stale()` is
  `new CharterRefusal(STALE_INPUT, "The packs or the project configuration changed while the read was answered; read again.")`.
  It names no file and no host path.
- **Javadoc:** the residual window. A change after the second digest, or one reverted between
  the two (ABA), is not seen.

**Unit tests (`ConsistentLoadTest`):**

- equal digests → loaded with the digest;
- different → stale;
- the load throws and the digest changed → stale;
- the load throws and the digest is unchanged → the original refusal;
- the first digest throws → propagated.

**Queries and controllers:**

- `ActionContextQuery` (out-of-map) and `PackListingQuery` take a `ConsistentLoad` instead of
  calling `packs.load()`.
- Their results carry the verified `sourceFingerprint`, a read-model field the controller maps
  to the header, never into the body.
  - `ActionContext.java` (WP01's, out-of-map) gains the field.
  - `PackListingQuery` returns a new `PackListing(List<PackSummary> packs, String sourceFingerprint)`
    record instead of a bare list.
- `ContextController` (out-of-map) and `PacksController` return a `ResponseEntity` whose
  `Charter-Source-Fingerprint` is set with `set`, not `add`.
- Update `ActionContextQueryTest` and `PackListingQueryTest` for the new constructor. That
  edit to WP01's test is out-of-map: a constructor change only.

**`CharterWiring`** (out-of-map) wires `ConsistentLoad` with the composite `PackSources` from
WP02. It must be the **same** `PackSources` the `/info` read uses, so the 200 header of any
read equals `/info`'s `sourceFingerprint` for unchanged inputs.

### Subtask T014: `CharterHeaders` no longer digests

- Remove the `info.answer()` call. The filter sets `Charter-Contract-Version` and
  `Charter-Worktree` only.
- Rewrite its Javadoc: the fingerprint is set by each read on its 200 answer.
- **Contract:** in `headers/CharterSourceFingerprint.yaml` (out-of-map, WP02's file), the
  "which responses" sentence becomes "on every 200 answer, the digest that answer's read
  verified; never on a refusal or a failure". It stays `required: false` and declared on every
  response (lint `charter-headers-on-every-response`).
- **Existing scenarios** that assert a fingerprint header on a refusal: find them with
  `grep -n "Charter-Source-Fingerprint" contracts/charter/scenarios/*.feature`. They change in
  the red commit, as the new rule's red evidence.

### Subtask T015: The reserved pin and its enforcing test

**`ReservedRefusalCodesTest`** (charter-service tests, ArchUnit or plain reflection over main
classes):

- No class in `ai.speckitty.charter..` except `RefusalCode` and `RefusalHandler` (its title
  mapping) accesses `RefusalCode.GOVERNANCE_POLICY_VIOLATION`.
- With ArchUnit, import main classes only (`ImportOption.DoNotIncludeTests`), and exclude by
  **name pattern**: `noClasses().that().haveNameNotMatching("ai\\.speckitty\\.charter\\.domain\\.RefusalCode(\\$.*)?").and().haveNameNotMatching("ai\\.speckitty\\.charter\\.rest\\.RefusalHandler(\\$.*)?").should().accessField(RefusalCode.class, "GOVERNANCE_POLICY_VIOLATION")`.
  javac compiles the enum `switch` in `RefusalHandler.title()` into a synthetic
  `RefusalHandler$1` (`$SwitchMap`) that reads the field. A simple-name exclusion fails on
  correct code (post-tasks finding 1).
- **Proof that the rule passes on unchanged code:** run it before the planted throw, and record
  both runs.
- Its Javadoc names #48 as the change that deletes this test when it raises the code.

**Red-first proof:**

- In a scratch commit **that you do not keep**, add a throw site (for example in
  `ConsistentLoad`).
- Run the test and record the failure in history.
- Revert the throw site.

### Subtask T016: ArchUnit and contract text

**`HexagonalArchitectureTest`:** a rule that no main class depends on a class in a `..bdd..`
package or on any class whose name ends in `Drifting`/`Scenario`. A test seam never lives in
main.

**`CharterRefusalCode.yaml`:**

- `stale_input` is raised: "the inputs changed while the answer was computed, so the answer
  and its fingerprint might disagree". It may be retried.
- `governance_policy_violation` stays reserved (#48).
- Keep the dispatch paragraph.

**`OfferingUnreadable.yaml`:** the description covers `stale_input`.

**Final run:**

```bash
./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs
```

## Red-first evidence (required)

- the T012 red run (assertion failures, scenario count);
- the T015 planted-throw failure;
- the green run;
- the commit hashes.

## Definition of Done

- [ ] `stale_input` raised for real on the context read and `/packs`, and on a failed load whose
  inputs changed.
- [ ] Exactly one fingerprint header per 200; none on refusals.
- [ ] `pack_unreadable` pinned by a real input; the `internal_error` scenario retitled.
- [ ] `governance_policy_violation` pinned by declaration and enforced by a unit test; the score
  is reported as 8 behavioral plus 1 declared.
- [ ] `verifyAll` green; no runner B file touched; no CHANGELOG edit; out-of-map edits recorded.

## Risks

- **Double digest cost:** each read digests twice. Note the measured scenario-suite time in
  history before and after. No new timeout is allowed.
- **The seam timing:** arm it to act inside `packs.load()` or between the first digest and the
  load, deterministically. No sleeps, no threads.

## Reviewer guidance

Check that:

- the seam rewrites a real file;
- production code computes both digests;
- the negative control exists;
- no fingerprint header appears on any refusal;
- one header appears on each 200;
- the reserved scenario carries `@contract`;
- the enforcing test has red evidence.
