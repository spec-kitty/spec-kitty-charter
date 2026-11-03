---
work_package_id: WP01
title: Sibling refinement refusal and the overrides edge
dependencies: []
requirement_refs:
- FR-012
planning_base_branch: issue-145-pack-composition
merge_target_branch: issue-145-pack-composition
branch_strategy: Planning artifacts for this mission were generated on issue-145-pack-composition. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-145-pack-composition unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 2 - Implementation
history:
- at: '2026-11-03T00:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackOverride.java
execution_mode: code_change
model: ''
owned_files:
- contracts/packs/schemas/pack-descriptor.schema.json
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackOverride.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- contracts/charter/scenarios/errors.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Sibling refinement refusal and the overrides edge

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
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history WP01 --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `<div>`, `<script>`
Use language identifiers in code blocks: `java`, `bash`

---

## Objectives & Success Criteria

Complete the last graph-level refusal the mission owes: a compose in which two sibling packs change the
same artifact is refused with `pack_sibling_refinement_conflict` (HTTP 409) naming the packs, unless the
distribution pack declares its own `overrides` edge settling which member wins (FR-012; ADR-0048 open
question 5 as ruled 2026-11-02; ADR-0053 decision 5).

Success criteria:

- A descriptor may declare an `overrides` edge; the closed schema, the kernel facts, the storage reader,
  the DSO and the YAML writer round-trip it.
- Two siblings changing one artifact with no settling `overrides` edge refuse the compose with
  `PACK_SIBLING_REFINEMENT_CONFLICT`, and the refusal names both packs.
- The same compose with the distribution pack's settling `overrides` edge resolves.
- The layer merge across levels (project over org over built-in) is unchanged — siblings are refused
  within one closure, levels still merge.
- `:service:packs:test` and `:service:kernel:test` are green.

## Context & Constraints

- **Already on this branch** (do not re-implement): the `depends_on` descriptor item, `Pep440Range`,
  `PackCompositionResolver` (order, dedupe, cycle, unknown, out-of-range, parent, level rule, name
  conflict, duplicate id — commits `50b1330e`, `3fc77ea4`, `3c35227c`, `25b6d702`), and the reserved
  refusal code `PACK_SIBLING_REFINEMENT_CONFLICT` already mapped to 409 in `RefusalHandler` and present
  in `CharterRefusalCode.yaml`.
- **Deciding records**: ADR-0048 (decisions 6, 9, 10 as amended 2026-11-02), ADR-0053 decision 5 — the
  distribution pack's own `overrides` edge on the contested artifact names the member whose version
  wins; no new edge type. Read `docs/adr/0048-pack-composition-and-the-named-packs-split.md` and
  `docs/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions.md` before designing the shape.
- **Mission docs**: `kitty-specs/pack-composition-depends-on-and-the-dependency-resolver-01M6F541/`
  (spec.md FR-012, data-model.md, research.md "What the codebase map settles", quickstart.md).
- **Closed-world schema**: a key the schema does not declare makes `pack.yaml` unreadable
  (ADR-0036 decision 3). `PackSchemasTest` enumerates `contracts/packs/schemas`, so the schema edit
  forces the schema suites to run.
- **Java 25**: `export JAVA_HOME=/usr/lib/jvm/temurin-25`; `java` is not on `PATH`. The build reads a
  sibling `spec-kitty-open-packs` checkout pinned by `openPacksRef` in `gradle.properties`.
- **Constraints**: C-005 (no URN kind-token work), C-002 (contract rules — the wire code already
  exists, so no new contract change is needed here), `@NullMarked` and ArchUnit layer rules apply to
  every new class.

## Branch Strategy

- **Strategy**: worktree-per-lane (allocated from `lanes.json` by `spec-kitty agent action implement`)
- **Planning base branch**: `issue-145-pack-composition`
- **Merge target branch**: `issue-145-pack-composition`

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask T001 – Add the `overrides` edge to the pack-descriptor schema

- **Purpose**: Let a distribution pack declare, on a contested artifact, which member's version wins
  (ADR-0053 decision 5). Without an authored edge there is no way to settle a sibling contest.
- **Steps**:
  1. Read `contracts/packs/schemas/pack-descriptor.schema.json` and the `depends_on` item
     `50b1330e` added, as the pattern to mirror.
  2. Add an `overrides` property: a list of closed items. Each item carries the fields the ruled edge
     needs to name the contested artifact and the winning member — derive the exact minimal field set
     from ADR-0053 decision 5 and the data model; keep every id a ULID-shaped string where it names a
     pack, and the artifact reference opaque (C-005: no URN parsing or re-spelling).
  3. Keep the schema closed (`additionalProperties: false` on the item and the descriptor) and the
     property optional (a descriptor with no `overrides` is valid — it is the common case).
  4. Do **not** couple `overrides` to `pack_level` the way `depends_on` is coupled; the rulings impose
     no such condition.
- **Files**: `contracts/packs/schemas/pack-descriptor.schema.json`
- **Parallel?**: No — T002 consumes the shape.
- **Notes**: A schema edit forces `PackSchemasTest` and the reader/round-trip suites to run
  (`:packs:processResources` copies the schema onto the test classpath). An invalid `overrides` value
  is a schema error at read time, exactly as an unparsable `range` is.

### Subtask T002 – Carry `overrides` through the kernel descriptor and storage

- **Purpose**: The kernel reads the authored edge as facts; storage round-trips it so a written
  `pack.yaml` reads back identically.
- **Steps**:
  1. Add `PackOverride` as a kernel value object beside `PackDependency`
     (`service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackOverride.java`): an
     immutable record of the fields T001 settled, `@NullMarked`, validated in a compact constructor.
  2. Extend `PackDescriptor` with `List<PackOverride> overrides()` defaulting to empty, without
     breaking the existing constructors (mirror how `50b1330e` added `dependencies()`).
  3. Map `overrides` in `PackDescriptorReader` (YAML → descriptor), mirror the field in
     `PackDescriptorDso` in both directions, and emit it in `YamlSerializationStrategy` in the
     documented key order.
  4. Extend `PackDescriptorReaderTest` and `PackRoundTripTest` for the new field, including the
     empty case and a malformed item.
- **Files**: `PackOverride.java` (new), `PackDescriptor.java`, `PackDescriptorReader.java`,
  `PackDescriptorDso.java`, `YamlSerializationStrategy.java`, the two storage test files.
- **Parallel?**: No.
- **Notes**: Research records ~35 test constructions of `PackDescriptor`; a defaulted accessor avoids
  touching them. Keep the DSO mirror faithful in both directions or the round-trip test fails.

### Subtask T003 – Refuse an unsettled sibling refinement in the compose resolution

- **Purpose**: FR-012: a conflict must be a decision, not load order. ADR-0048 decision 9 refuses the
  "last loaded wins" behaviour; the refusal lands with the compose resolution.
- **Steps**:
  1. In `PackCompositionResolver`, add a `SIBLING_CONFLICT` failure to the `Failure` enum.
  2. Detect the contest: two packs in one compose that both change the same artifact (an `enhances`
     or `overrides` relation on the same artifact id) with no distribution pack whose authored
     `overrides` edge settles that artifact. Use the artifact-relationship facts the resolver's inputs
     already carry; if the current `PackUnderResolution` inputs do not carry them, extend the input
     record rather than reaching into repositories (the resolver stays a pure function).
  3. Refuse with the pack ids of both siblings named in the detail, the contested artifact named, and
     the settling edge's absence stated.
  4. Map the failure in `OfferingReadService.codeOf` to `RefusalCode.PACK_SIBLING_REFINEMENT_CONFLICT`
     (already 409 in `RefusalHandler`).
  5. A settled contest (the distribution pack declares its `overrides` edge naming the winning member)
     resolves normally — no warning, no diagnostic.
- **Files**: `PackCompositionResolver.java`, `OfferingReadService.java`.
- **Parallel?**: No.
- **Notes**: Siblings are packs in one closure at one level; the layer merge across levels is a
  separate idea (ADR-0048 decision 6) and must keep working. A small, well-justified out-of-map touch
  (e.g. a relationship source you must read) is acceptable when recorded with a one-line rationale in
  the WP history.

### Subtask T004 – Test the sibling refusal, the settled compose and the layer merge

- **Purpose**: NFR-005 requires every refusal code to have a scenario and a negative control; here
  that means the refusal and its settled counterpart on the same fixture shape.
- **Steps**:
  1. In `PackCompositionResolverTest`: two siblings changing one artifact → refused, both ids named;
     the same graph with the settling `overrides` edge → resolves, in order.
  2. Siblings changing different artifacts → resolves (negative control for the contest detection).
  3. In `OfferingReadServiceTest`: the refusal travels as `PACK_SIBLING_REFINEMENT_CONFLICT` and the
     offering is not served partially.
  4. One test proving the cross-level merge still composes project over org over built-in with no
     `depends_on` and no contest (FR-011's seam stays intact).
- **Files**: `PackCompositionResolverTest.java`, `OfferingReadServiceTest.java`.
- **Parallel?**: No.
- **Notes**: Follow the existing test style in those files (AssertJ, fixture builders). Do not weaken
  an assertion to make a test pass — delete-the-assertion-not-the-test is forbidden; fix the code.

### Subtask T005 – Keep the refusal parity surfaces green

- **Purpose**: Adding a raised code must keep the five-part refusal change complete (research.md
  "What the codebase map settles").
- **Steps**:
  1. Confirm `contracts/charter/scenarios/errors.feature` lists `pack_sibling_refinement_conflict`
     in the raised table; add the row if `25b6d702` left it out.
  2. Run `WireTokenParityTest`, `ReservedRefusalCodesTest`, `RefusalHandlerTest` and
     `PackSchemasTest` via `./gradlew --no-daemon :service:packs:test :service:kernel:test`.
  3. Record the suites you ran in the WP history.
- **Files**: `contracts/charter/scenarios/errors.feature` (only if the row is missing).
- **Parallel?**: No.
- **Notes**: The wire code, the kernel enum constant and the 409 mapping already exist from
  `25b6d702`; this subtask verifies the parity, it does not re-add the code.

## Test Strategy

- Unit suites: `./gradlew --no-daemon :service:packs:test :service:kernel:test` (JAVA_HOME at
  `/usr/lib/jvm/temurin-25`).
- The full gate (`./gradlew --no-daemon verifyAll`) runs before review; the sibling openPacksRef
  checkout must be at the pinned commit or tasks that read it refuse.
- This WP owns typed sources; a passing test runner does not replace compilation — the Gradle build is
  the configured compiler check and must finish without diagnostics.

## Risks & Mitigations

- **Contest detection needs artifact-relationship data the resolver inputs lack** → extend the pure
  input record; never let the resolver touch a repository (its purity is what keeps it testable).
- **`overrides` shape drift from the ruling** → derive the fields from ADR-0053 decision 5 only; do
  not invent a second edge type.
- **Wide edit blast radius on `PackDescriptor` constructions** → default the accessor to empty, as
  `50b1330e` did for `dependencies()`.

## Review Guidance

- **Reviewer profile**: `reviewer-renata` (load via `/spk-charter-profile-load` before reviewing).
- Verify: the `overrides` item is closed and authored; the refusal names both siblings and the
  contested artifact; the settled compose resolves; the layer merge is untouched; the resolver is
  still a pure function; parity suites ran green; compiler diagnostics passed (Gradle build clean).
- Revisit ADR-0053 decision 5 and spec.md FR-012's acceptance scenarios before approving.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this
prompt file. Do **not** hand-edit a history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history WP01 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP01 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

## Implementation Command

```bash
spec-kitty agent action implement WP01 --agent opencode --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541
```
