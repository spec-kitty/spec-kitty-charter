---
work_package_id: WP04
title: The compatibility ratchet and resolver qualities
dependencies:
- WP03
requirement_refs:
- FR-004
- FR-005
- FR-011
- FR-018
planning_base_branch: issue-145-pack-composition
merge_target_branch: issue-145-pack-composition
branch_strategy: Planning artifacts for this mission were generated on issue-145-pack-composition. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-145-pack-composition unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
phase: Phase 2 - Implementation
history:
- at: '2026-11-03T00:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SingleTreeCompatRatchetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ResolverDeterminismTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ResolverCostBoundTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ComposeLevelSeparationTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SingleTreeCompatRatchetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ResolverDeterminismTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ResolverCostBoundTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ComposeLevelSeparationTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – The compatibility ratchet and resolver qualities

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `opencode`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history WP04 --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `<div>`, `<script>`
Use language identifiers in code blocks: `java`, `bash`

---

## Objectives & Success Criteria

Prove the qualities the delivered resolver owes: today's single `built-in` tree answers byte-identical
except additive fields (FR-018, NFR-004, SC-003 — a ratchet, not a claim); resolution is deterministic
across directory listing order and repeated runs (NFR-001); a 50-pack compose with 200 edges resolves
within 250 ms at p95 (NFR-003); one compose is one closure at one level with dependencies before
dependents and a shared dependency loaded once (FR-011, FR-004, FR-005, SC-001).

Success criteria:

- Four new test files in `:service:packs:test`, all green, with **no production code change** — if a
  quality fails, the fix belongs to the owning WP and is escalated, not patched here.
- The ratchet tolerates exactly the additive fields WP03 introduced and nothing else.

## Context & Constraints

- **Already on this branch** (do not re-implement): the resolver, its wiring, and WP03's contract
  change set (dependency facts, scenarios). This package only measures and pins.
- **Mission docs**: spec.md NFR-001, NFR-003, NFR-004, FR-004, FR-005, FR-011, FR-018, SC-001,
  SC-003; research.md "What this means for the work packages" (the shim is a property of the offering
  read, tested by pinning today's single-tree answers); data-model.md invariants 1, 4 and 5.
- **Frozen-baseline discipline**: the ratchet is shrink-only — a pinned answer may not change; a new
  difference is a finding, not an expectation update.
- **Flaky discipline**: quarantine a flaky timing case rather than deleting or weakening it
  (qa-flaky-test-quarantine); never delete an assertion to make a test pass.
- **Java 25**: `export JAVA_HOME=/usr/lib/jvm/temurin-25`; `java` is not on `PATH`. Module tests that
  do not read the sibling `spec-kitty-open-packs` checkout still compile and run even when it is not
  at the pinned commit.

## Branch Strategy

- **Strategy**: worktree-per-lane (allocated from `lanes.json` by `spec-kitty agent action implement`)
- **Planning base branch**: `issue-145-pack-composition`
- **Merge target branch**: `issue-145-pack-composition`

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask T016 – Pin the single-tree offering and pack answers (ratchet)

- **Purpose**: FR-018 / NFR-004 / SC-003: a compose built only from today's packs produces offering
  and pack responses with zero differences other than additive fields, across the existing fixture
  set. The shim is proven by pinning, not by argument.
- **Steps**:
  1. Create `SingleTreeCompatRatchetTest` beside the resolver tests.
  2. Build the fixture of today's packs (the single `built-in` tree, no `depends_on`, no qualified
     references) exactly as the existing offering fixtures do.
  3. Pin the offering read's answer (ids, levels, names, versions, order) and the pack reads' answers
     as golden values in the test.
  4. Assert equality allowing only the additive dependency-fact fields WP03 introduced (empty lists /
     absent properties for a dependency-free fixture); any other difference fails.
  5. Run it 100 times in one test method loop — the answer must not vary (this doubles as an NFR-001
     pin for the single-tree shape).
- **Files**: `SingleTreeCompatRatchetTest.java` (new).
- **Parallel?**: Independent of T017–T019 (different files).
- **Notes**: If a difference beyond the additive fields appears, do not update the golden — record
  the finding in the WP history and escalate; the fix belongs to the offering read or WP03's exposure,
  not to this ratchet.

### Subtask T017 – Prove deterministic resolution across shuffled inputs and repeated runs

- **Purpose**: NFR-001: the same configured packs and descriptor contents produce the same resolved
  order across 100 repeated runs, independent of directory listing order.
- **Steps**:
  1. Create `ResolverDeterminismTest`.
  2. Build one compose fixture with enough shape to expose ordering (≥ 8 packs, ≥ 2 levels, a diamond,
     siblings sharing a dependency).
  3. For 100 iterations: shuffle the input list (and, where the fixture supports it, the configured
     order), resolve, and assert the identical output sequence.
  4. Assert the tie-break is the stable order the resolver documents (level rank, then pack id by
     code point) — assert the sequence, not just its multiset.
- **Files**: `ResolverDeterminismTest.java` (new).
- **Parallel?**: Independent of the other subtasks.
- **Notes**: The resolver is a pure function; feed it shuffled lists directly rather than spinning up
  storage. Keep the runtime bounded (a 100-iteration unit test must stay well inside a minute).

### Subtask T018 – Bound the resolution cost of a 50-pack, 200-edge compose

- **Purpose**: NFR-003: resolving a compose of 50 packs with 200 dependency edges completes within
  250 ms at p95 on the reference build.
- **Steps**:
  1. Create `ResolverCostBoundTest`.
  2. Generate a 50-pack, 200-edge fixture programmatically (levels mixed, ranges satisfied, a few
     diamonds) — no filesystem, pure resolver inputs.
  3. Warm up (a handful of unmeasured resolutions, so JIT noise does not own the number), then
     measure ≥ 30 resolutions and compute p95.
  4. Assert p95 < 250 ms, with headroom chosen so the bound holds on the reference build without
     flaking (the NFR is the contract; the assertion may be generous relative to observed cost but
     must remain below 250 ms at p95).
- **Files**: `ResolverCostBoundTest.java` (new).
- **Parallel?**: Independent of the other subtasks.
- **Notes**: This is a bounded smoke, not a benchmark harness. If it flakes in CI, quarantine and
  record rather than deleting (qa-flaky-test-quarantine).

### Subtask T019 – Prove one closure at one level and the diamond order

- **Purpose**: FR-011 (one compose is one closure at one level; composition and layer merge stay
  separate ideas), FR-004 (dependencies resolve before dependents), FR-005 (a shared dependency loads
  once) — SC-001's five-pack diamond.
- **Steps**:
  1. Create `ComposeLevelSeparationTest`.
  2. Five-pack diamond: B and C depend on A; D depends on B and C; E depends on A. Assert A precedes
     B and C, B and C precede D, A appears exactly once, and every dependency precedes its dependents
     (SC-001).
  3. Level separation: packs configured at `built-in`, `org` and `project` in one offering resolve as
     one closure per level with the merge order across levels preserved (project over org over
     built-in) — the compose does not mix levels into one merged closure, and cross-level edges still
     follow the own-level-or-lower rule.
  4. Negative control: an edge that would cross the level boundary upward is refused
     (`pack_level_violation`), proving the separation is enforced, not incidental.
- **Files**: `ComposeLevelSeparationTest.java` (new).
- **Parallel?**: Independent of the other subtasks.
- **Notes**: If the delivered resolver conflates the per-level closure with the cross-level merge,
  record the finding and escalate — do not reshape `PackCompositionResolver` from this WP (WP01 owns
  it; a fix goes back through that lane with a rationale).

## Test Strategy

- `./gradlew --no-daemon :service:packs:test` (JAVA_HOME at `/usr/lib/jvm/temurin-25`).
- This WP owns typed sources (tests are typed sources); the Gradle build is the configured compiler
  check and must finish without diagnostics.
- Full gate before review: `./gradlew --no-daemon verifyAll`.

## Risks & Mitigations

- **Timing flake** → generous-but-bounded assertion, warm-up, and quarantine on repeat failure.
- **Ratchet golden rot** → shrink-only: differences are findings, never expectation updates.
- **Hidden production fix needed** → escalate through the owning WP; this package changes no
  production code.

## Review Guidance

- **Reviewer profile**: `reviewer-renata` (load via `/spk-charter-profile-load` before reviewing).
- Verify: four new test files, zero production diffs; the ratchet allows only WP03's additive fields;
  the determinism test asserts a sequence, not a multiset; the cost bound asserts p95 below 250 ms;
  the compiler check passed.
- Revisit spec.md NFR-001/NFR-003/NFR-004 and SC-001/SC-003 before approving.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this
prompt file. Do **not** hand-edit a history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history WP04 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP04 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

## Implementation Command

```bash
spec-kitty agent action implement WP04 --agent opencode --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541
```
