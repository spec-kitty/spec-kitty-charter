---
work_package_id: WP03
title: 'The contract change set: dependency facts and resolver scenarios'
dependencies:
- WP01
- WP02
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-016
- FR-019
- FR-020
- FR-023
- NFR-002
planning_base_branch: issue-145-pack-composition
merge_target_branch: issue-145-pack-composition
branch_strategy: Planning artifacts for this mission were generated on issue-145-pack-composition. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-145-pack-composition unless the human explicitly redirects the landing branch.
subtasks:
- T011
- T012
- T013
- T014
- T015
phase: Phase 2 - Implementation
history:
- at: '2026-11-03T00:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ResolvedEdge.java
- contracts/charter/scenarios/composition.feature
- contracts/charter/changes/2026-11-03-pack-composition-refusals-and-dependency-facts.md
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ResolvedEdge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/OfferingController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ActivationJson.java
- contracts/charter/schemas/PackPin.yaml
- contracts/charter/schemas/OfferingPack.yaml
- contracts/charter/openapi.yaml
- contracts/charter/scenarios/composition.feature
- contracts/charter/changes/2026-11-03-pack-composition-refusals-and-dependency-facts.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – The contract change set: dependency facts and resolver scenarios

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
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history WP03 --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `<div>`, `<script>`
Use language identifiers in code blocks: `java`, `gherkin`, `yaml`

---

## Objectives & Success Criteria

Land the mission's one contract change set (C-006): the pack pin and offering reads expose what a
compose resolved (FR-020); every resolver refusal code is proven by a BDD scenario with a negative
control (FR-019 completion, NFR-005); the change is recorded with a `### Breaking` fragment and a
changelog entry under ADR-0022 (FR-023's changelog half, SC-005).

Success criteria:

- The offering read for a compose exposes, per pack, each dependency it declared (target id and range)
  and the version that satisfied it; the pack pin carries the same facts.
- Each of the ten refusal codes (cycle, unknown dependency, out-of-range, unresolved parent, level
  violation, name conflict, duplicate id, sibling conflict, unqualified reference, missing pack) has
  at least one scenario and one negative control in the contract suite.
- `info.version` in `contracts/charter/openapi.yaml` moves (0.x minor bump) and the changes fragment
  carries a `### Breaking` entry where the 0.x line breaks.
- The contract suite, refusal parity tests and compatibility check run green.

## Context & Constraints

- **Already on this branch** (do not re-implement): the resolver, its wiring into the offering read,
  and the ten codes in `CharterRefusalCode.yaml`, `RefusalCode` and `RefusalHandler` (`25b6d702`).
  What is missing is the *exposure* and the *proof*: no BDD scenario drives a compose today, and the
  reads carry no dependency facts.
- **Deciding records**: ADR-0022 (0.x contract rules — a breaking change takes a minor bump and a
  `### Breaking` entry; no released contract breaks incompatibly), ADR-0048, ADR-0053. C-002 and C-006
  bind this package.
- **Mission docs**: spec.md FR-019/FR-020/FR-023, SC-004/SC-005, NFR-002 and NFR-005; data-model.md
  (`ResolvedEdge`, `ResolvedPack`, `ResolvedCompose`); research.md "What the codebase map settles"
  (the additive-response `ERR` grading and the five-part refusal change).
- **Compatibility grading**: an additive response property is graded `ERR` by
  `contracts/compat/severity-levels.txt`, so the `info.version` move and the `### Breaking` fragment
  are part of the change (research.md) — this is a contract decision, not a free addition.
- **BDD mechanics**: scenarios under `contracts/charter/scenarios/*.feature` are picked up
  automatically by `CharterContractSuite`; research.md suggests `offering.feature` or a new file —
  this package creates `composition.feature` so the resolver's scenarios stay one family.
- **Java 25**: `export JAVA_HOME=/usr/lib/jvm/temurin-25`; `java` is not on `PATH`.

## Branch Strategy

- **Strategy**: worktree-per-lane (allocated from `lanes.json` by `spec-kitty agent action implement`)
- **Planning base branch**: `issue-145-pack-composition`
- **Merge target branch**: `issue-145-pack-composition`

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask T011 – Derive the resolved edges in the offering read

- **Purpose**: FR-020 needs the declared range and the satisfying version per dependency; the data
  model names the shape (`ResolvedEdge`: from id, to id, declared range, satisfying version; the pair
  appears once).
- **Steps**:
  1. Create `ResolvedEdge` in the application layer beside the resolver's output
     (`service/packs/src/main/java/ai/speckitty/charter/packs/application/ResolvedEdge.java`).
  2. Derive the edges in `OfferingReadService` from the ordered closure the resolver already returns:
     for each pack, each declared `PackDependency` (target id, range) paired with the target's held
     `pack_version` from the closure. Prefer this derivation over reshaping
     `PackCompositionResolver` (owned by WP01); if the resolver genuinely must change, keep the touch
     minimal and record it with a one-line rationale in the WP history.
  3. Extend `OfferingEntry` (or a view beside it) so the offering read carries each pack's resolved
     edges.
  4. Unit-test: a diamond compose exposes four edges, `(from, to)` once each, ranges and satisfying
     versions as declared.
- **Files**: `ResolvedEdge.java` (new), `OfferingReadService.java`, `OfferingReadServiceTest.java`.
- **Parallel?**: No.
- **Notes**: The edges are derived, never authored — nothing here writes `pack.yaml`.

### Subtask T012 – Add the dependency facts to `PackPin.yaml` and `OfferingPack.yaml`, and move `info.version`

- **Purpose**: The contract is where consumers read what a compose resolved; the facts must be
  additive but recorded (ADR-0022).
- **Steps**:
  1. Extend `contracts/charter/schemas/OfferingPack.yaml` with the resolved-dependency view (per
     entry: the declared `depends_on` target id, the range, the satisfying version).
  2. Extend `contracts/charter/schemas/PackPin.yaml` with the same facts for the pack an activation
     builds on (data-model.md: the pin gains what the resolved dependency contributed).
  3. Keep both schemas closed and the new properties optional-with-default where the existing style
    does so; a pack with no dependencies answers an empty list.
  4. Move `info.version` in `contracts/charter/openapi.yaml` one minor step on the 0.x line
     (currently `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT`).
  5. Update the generated-DTO consumption in `OfferingController` and `ActivationJson` so the new
     fields are populated from T011's edges; import of generated DTOs stays inside `infra.api`.
- **Files**: `OfferingPack.yaml`, `PackPin.yaml`, `openapi.yaml`, `OfferingController.java`,
  `ActivationJson.java`.
- **Parallel?**: No.
- **Notes**: No hand-written DTO is added (plan.md structure decision). Examples under
  `contracts/charter/examples/` that pin these schemas update in step with them.

### Subtask T013 – Write the `### Breaking` changelog fragment

- **Purpose**: SC-005: the change set is recorded where readers look, with a `### Breaking` entry
  where the 0.x line breaks; FR-023's changelog half.
- **Steps**:
  1. Create `contracts/charter/changes/2026-11-03-pack-composition-refusals-and-dependency-facts.md`
     following the existing fragments' structure (see `2026-11-01-refusal-code-raised-map.md`).
  2. Record: the ten refusal codes (with statuses — 422 and the sibling 409), the additive dependency
     facts on `OfferingPack` and `PackPin`, the `info.version` move, and the `### Breaking` entry for
     the additive response properties the compatibility check grades `ERR`.
  3. State that no released contract is broken incompatibly (C-002) and that the change set is one
     family (C-006).
- **Files**: `contracts/charter/changes/2026-11-03-pack-composition-refusals-and-dependency-facts.md` (new).
- **Parallel?**: No.
- **Notes**: The fragment is the changelog entry FR-023 asks for; WP05 verifies it reads where
  readers look and marks the work-breakdown items.

### Subtask T014 – Author one BDD scenario and one negative control per refusal code

- **Purpose**: NFR-005: every refusal code has at least one BDD scenario and one negative control on
  the same fixture, so a no-op implementation cannot pass. This is also the end-to-end proof for the
  already-delivered FR-001 to FR-010 and FR-016.
- **Steps**:
  1. Create `contracts/charter/scenarios/composition.feature` (`@contract @composition`) following
     the Gherkin style of `offering.feature` (pack fixtures written as `pack.yaml` documents).
  2. One scenario per code, each with the offending pack named in the refusal:
     - `pack_dependency_cycle` — A→B→A (spec User Story 2, scenario 1).
     - `pack_dependency_unknown` — A depends on an unconfigured id.
     - `pack_dependency_out_of_range` — A declares `>=2.0`, the held B is 1.9.0.
     - `pack_parent_unresolved` — `parent_pack` names no declared id.
     - `pack_level_violation` — an `org` pack depending on a `project` pack (a lower tier may not
       lean on a higher one; a pack may depend only on its own level or a lower one). Note a tension
       to carry, not resolve here: spec.md User Story 2 scenario 5 also refuses a `project` pack
       depending on another `project` pack, while data-model.md invariant 3 and the delivered
       resolver admit same-level edges — follow the ADR-0048/data-model rule (own level or lower) and
       report the spec's first clause to the operator as a spec-consistency finding.
     - `pack_name_conflict` — two packs, one name.
     - `pack_duplicate_id` — two configured packs, one `pack_id`.
     - `pack_sibling_refinement_conflict` — WP01's contest, unsettled.
     - `pack_reference_unqualified` / `pack_reference_pack_missing` — WP02's shared fixture lifted to
       the contract (SC-004: qualified resolves, bare refuses, on one fixture).
  3. One negative control per code on the same fixture shape: the happy path that would break if the
     refusal fired wrongly (e.g. the settled sibling compose, the in-range dependency, the
     three-pack diamond with B and C both depending on A — A once, before B and C).
  4. Cover the positive stories too: the diamond resolves (User Story 1), a descriptor with no
     `depends_on` resolves exactly as today, the single `built-in` tree still loads (FR-018's scenario
     level; the byte-level ratchet is WP04's).
- **Files**: `contracts/charter/scenarios/composition.feature` (new).
- **Parallel?**: No.
- **Notes**: Scenarios are picked up automatically by `CharterContractSuite`. Keep the spec's edge
  cases in view: an exact range on an unheld version refuses as out-of-range, not unknown; `<2` and
  `>=2` touching at one version do not overlap.

### Subtask T015 – Run the contract suite, parity tests and golden examples green

- **Purpose**: The change set is only real when the suites that pin it pass.
- **Steps**:
  1. Run the contract suite and the module tests:
     `./gradlew --no-daemon :service:packs:test :service:kernel:test` plus the suite that runs
     `contracts/charter/scenarios` (`CharterContractSuite`).
  2. Run the refusal parity tests (`WireTokenParityTest`, `ReservedRefusalCodesTest`,
     `RefusalHandlerTest`) and the compatibility check that reads
     `contracts/compat/severity-levels.txt`.
  3. Update golden examples under `contracts/charter/examples/` for the new additive fields.
  4. Record the suites and their results in the WP history.
- **Files**: as touched by failures only; examples under `contracts/charter/examples/`.
- **Parallel?**: No.
- **Notes**: The sibling `spec-kitty-open-packs` checkout must be at the pinned `openPacksRef` commit
  or the tasks that read it refuse (quickstart.md).

## Test Strategy

- `./gradlew --no-daemon :service:packs:test :service:kernel:test` and the contract suite
  (`CharterContractSuite` picks up `composition.feature` automatically).
- Full gate before review: `./gradlew --no-daemon verifyAll`.
- This WP owns typed sources; the Gradle build is the configured compiler check and must finish
  without diagnostics.

## Risks & Mitigations

- **Another contract PR interleaves** → C-006: rebase rather than merging two change sets; keep every
  `contracts/` touch inside this WP's owned files.
- **Additive fields graded `ERR` without a version move** → T012 moves `info.version` and T013 records
  the `### Breaking` entry; run the compatibility check, do not reason about it.
- **Scenario sprawl** → one file, one fixture vocabulary, mirroring `offering.feature`'s style.

## Review Guidance

- **Reviewer profile**: `reviewer-renata` (load via `/spk-charter-profile-load` before reviewing).
- Verify: every one of the ten codes has a scenario **and** a negative control; the dependency facts
  appear on both `OfferingPack` and `PackPin`; `info.version` moved; the fragment carries
  `### Breaking`; the resolver was not reshaped (WP01 owns it — any touch must carry a rationale);
  the compiler check passed.
- Revisit ADR-0022, spec.md NFR-005 and SC-004/SC-005 before approving.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this
prompt file. Do **not** hand-edit a history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history WP03 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP03 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

## Implementation Command

```bash
spec-kitty agent action implement WP03 --agent opencode --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541
```
