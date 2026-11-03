---
work_package_id: WP02
title: The pack-qualified reference grammar
dependencies:
- WP01
requirement_refs:
- FR-013
- FR-014
- FR-015
- FR-017
planning_base_branch: issue-145-pack-composition
merge_target_branch: issue-145-pack-composition
branch_strategy: Planning artifacts for this mission were generated on issue-145-pack-composition. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-145-pack-composition unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T008
- T009
- T010
phase: Phase 2 - Implementation
history:
- at: '2026-11-03T00:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackQualifiedReference.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackQualifiedReferenceTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackQualifiedReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackQualifiedReferenceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – The pack-qualified reference grammar

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
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history WP02 --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `<div>`, `<script>`
Use language identifiers in code blocks: `java`, `bash`

---

## Objectives & Success Criteria

Make the dependency visible in the artifact itself: a reference from one pack to an artifact of another
is written `<pack-name>::<urn>` and resolves; a reference inside one pack stays bare; a bare reference
that only resolves in another pack is refused with `pack_reference_unqualified` naming the holding pack;
a qualified reference to a pack outside the closure is refused with `pack_reference_pack_missing` naming
the missing pack (FR-013, FR-014, FR-015, FR-017; ADR-0048 decision 10 as amended 2026-11-02; ADR-0053
decision 7).

Success criteria:

- `<pack-name>::<urn>` parses and resolves against the compose closure; the artifact read answers.
- A bare reference resolves only within the referencing pack; a bare id that exists only in another
  pack refuses, naming that pack.
- A qualified reference whose pack is in the descriptor's `depends_on` but not in the project's closure
  refuses, naming the missing pack.
- Both grammar outcomes are proven on one shared fixture (SC-004).
- `:service:packs:test` is green.

## Context & Constraints

- **Already on this branch** (do not re-implement): the compose resolution (`PackCompositionResolver`,
  wired into `OfferingReadService` by `25b6d702`) and the reserved refusal codes
  `PACK_REFERENCE_UNQUALIFIED` and `PACK_REFERENCE_PACK_MISSING`, both mapped to 422 in
  `RefusalHandler`. Duplicate pack names in a compose are already refused
  (`PACK_NAME_CONFLICT`), so a qualified reference is never ambiguous.
- **Deciding records**: ADR-0048 decision 10 as amended (a reference that crosses a pack boundary is
  written `<pack-name>::<urn>`, e.g. `foundation::directive:DIRECTIVE_001`; in-pack references stay
  bare; pack names are unique within a compose), ADR-0053 decision 7.
- **Mission docs**: spec.md User Story 3 and its edge cases; data-model.md "Qualified reference";
  research.md "Reference grammar and the ruling".
- **C-005 (hard constraint)**: ADR-0053 open question 3 was ruled (multi-word URN kind tokens move to
  kebab case directly), but this mission writes **no URN-parsing code**. The `<urn>` part after `::` is
  an opaque string: no splitting on `:`, no kind-token parsing, no re-spelling. Only the
  `<pack-name>::` prefix is grammar.
- **Existing seam**: `CompositeComponentResolver` (application) resolves artifact URNs inside one pack
  over the `ComponentResolver` port; it has no pack graph. The qualified reference extends this seam;
  the two graphs (component resolution and pack composition) must not be merged (research.md).
- **Java 25**: `export JAVA_HOME=/usr/lib/jvm/temurin-25`; `java` is not on `PATH`.

## Branch Strategy

- **Strategy**: worktree-per-lane (allocated from `lanes.json` by `spec-kitty agent action implement`)
- **Planning base branch**: `issue-145-pack-composition`
- **Merge target branch**: `issue-145-pack-composition`

> These fields are populated automatically by `spec-kitty agent mission finalize-tasks`.
> Do NOT change them manually unless you are certain the branch topology has changed.

## Subtasks & Detailed Guidance

### Subtask T006 – Parse `<pack-name>::<urn>` with the URN part opaque

- **Purpose**: The qualifier is the only new grammar; parsing it must not leak URN semantics.
- **Steps**:
  1. Create `PackQualifiedReference` as an application value object: parse a reference string into
     `name` (the pack name before the first `::`) and `urn` (everything after, verbatim). A string
     with no `::` is not qualified — represent that as an explicit absence, not a null urn.
  2. Validate the name shape against what a pack `name` may be (see the descriptor schema); reject an
     empty name or a name containing `::`.
  3. Keep the URN untouched: no case folding, no token splitting, no validation beyond non-empty.
  4. Unit-test the parser: qualified, bare, empty, `::` at the start, doubled `::`, and a URN
     containing further colons (must survive verbatim).
- **Files**: `PackQualifiedReference.java` (new), `PackQualifiedReferenceTest.java` (new).
- **Parallel?**: T006 can be written first; T007 consumes it.
- **Notes**: `@NullMarked`, ArchUnit layer rules, and Error Prone's StringSplitter apply — prefer
  `indexOf` over `split` (see how `Pep440Range` handled the same pressure).

### Subtask T007 – Resolve a qualified reference against the compose closure

- **Purpose**: FR-013: the dependency is visible and checkable; resolution goes through the pack name
  to the pack, then the URN inside that pack.
- **Steps**:
  1. Extend the reference-resolution path (`CompositeComponentResolver` and, where a read resolves
     references, `ArtifactReadService`) to accept a qualified reference.
  2. Resolve the pack name against the compose closure the offering already resolved: name → pack,
     then resolve the opaque urn inside that pack through the existing `ComponentResolver` port.
  3. A name that matches no pack in the closure is T009's refusal, not a fallback to the referencing
     pack.
  4. A qualified reference inside the referencing pack's own closure is the normal case; the pack's
     own artifacts are reachable both bare (in-pack, T008) and qualified.
- **Files**: `CompositeComponentResolver.java`, `ArtifactReadService.java`.
- **Parallel?**: No.
- **Notes**: Consume the closure read-only (call `PackCompositionResolver`/`OfferingReadService`);
  this WP does not own those files. If a minimal, well-justified out-of-map touch is unavoidable,
  record it with a one-line rationale in the WP history.

### Subtask T008 – Refuse a bare reference that only resolves in another pack

- **Purpose**: FR-015: an accidental cross-pack coupling must not resolve by load order — the
  accidental coupling is the bug the named-packs split exists to catch.
- **Steps**:
  1. A bare reference resolves only within the referencing pack (today's behaviour for one pack;
     now bounded to it).
  2. When a bare id does not resolve in-pack but does exist in another pack of the closure, refuse
     with `PACK_REFERENCE_UNQUALIFIED`, naming the pack that holds the id and the reference's pack.
  3. When a bare id resolves nowhere, keep today's not-found refusal — the unqualified code is
     specifically the "it exists, but not here" case.
- **Files**: `CompositeComponentResolver.java`, `ArtifactReadService.java`.
- **Parallel?**: No.
- **Notes**: The refusal message must let a reader fix the reference by adding the qualifier — name
  the holding pack.

### Subtask T009 – Refuse a qualified reference to a pack outside the closure

- **Purpose**: FR-017: the closure check names the pack to add, so the operator knows what to
  configure.
- **Steps**:
  1. A qualified reference whose pack name is not in the compose closure refuses with
     `PACK_REFERENCE_PACK_MISSING`, naming the missing pack.
  2. The spec's edge case: the pack is in the descriptor's `depends_on` but not in the project's
     closure — same refusal, and the message names the pack missing from the closure.
  3. Do not distinguish "not in closure" from "unknown name" beyond the message; one code covers
     both (FR-017 has one code).
- **Files**: `CompositeComponentResolver.java`, `ArtifactReadService.java`.
- **Parallel?**: No.
- **Notes**: Both reference codes are already wired to 422; raise them as `CharterRefusal` through
  the same path the other compose refusals travel.

### Subtask T010 – Test both grammar outcomes on one shared fixture

- **Purpose**: SC-004 requires the qualified-resolves and bare-refused assertions on one shared
  fixture, so the two behaviours cannot drift apart.
- **Steps**:
  1. Build one two-pack fixture (A and B in one closure) where a directive in A references
     `B::directive:<urn>`: the read resolves.
  2. On the same fixture, write the reference bare: the read refuses with
     `pack_reference_unqualified` naming B.
  3. On the same fixture, reference `C::directive:<urn>` with C outside the closure: the read refuses
     with `pack_reference_pack_missing` naming C.
  4. A bare in-pack reference on the same fixture still resolves (FR-014).
- **Files**: `CompositeComponentResolverTest.java`, `ArtifactReadServiceTest.java`,
  `PackQualifiedReferenceTest.java`.
- **Parallel?**: No.
- **Notes**: Follow the existing fixture style in those test files. Each refusal also gets its
  negative control here; WP03 lifts the same fixtures into BDD scenarios at the contract level.

## Test Strategy

- `./gradlew --no-daemon :service:packs:test` (JAVA_HOME at `/usr/lib/jvm/temurin-25`).
- This WP owns typed sources; the Gradle build is the configured compiler check and must finish
  without diagnostics — a green test runner does not replace it.

## Risks & Mitigations

- **URN parsing creep** → the opaque rule is absolute (C-005); if a change seems to need URN
  internals, stop and record the finding instead of parsing.
- **Load-order resolution sneaks back in** → the bare-reference refusal is the guard; the shared
  fixture asserts both directions.
- **Ambiguity if duplicate names were possible** → already refused upstream by
  `PACK_NAME_CONFLICT`; do not re-check it here.

## Review Guidance

- **Reviewer profile**: `reviewer-renata` (load via `/spk-charter-profile-load` before reviewing).
- Verify: the URN part is untouched after `::`; in-pack bare references behave exactly as before; the
  two refusals name the packs; the shared fixture carries both SC-004 assertions; the compiler check
  passed.
- Revisit ADR-0048 decision 10 (amended) and spec.md User Story 3 before approving.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this
prompt file. Do **not** hand-edit a history section here.

- **Record a progress note**: `spec-kitty agent tasks add-history WP02 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP02 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`

## Implementation Command

```bash
spec-kitty agent action implement WP02 --agent opencode --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541
```
