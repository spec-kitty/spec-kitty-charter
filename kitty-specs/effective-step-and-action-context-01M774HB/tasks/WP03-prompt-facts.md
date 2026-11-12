---
work_package_id: WP03
title: Prompt facts in the composition facts
dependencies: []
requirement_refs:
- FR-001
- FR-003
- C-002
- C-006
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-effective-step-and-action-context-01M774HB
base_commit: 2bd9d0840418b07bd9ba525df8aeba45d0e308d5
created_at: '2026-11-12T11:53:41.486679+00:00'
subtasks:
- T011
- T012
- T013
- T014
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptFacts.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptFacts.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PromptFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServicePromptFactsTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PromptFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WriteFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PromptFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/WriteFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositionFactsCacheTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServicePromptFactsTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

Make the compose's one verified, fingerprint-cached and staged-aware input — `PackCompositionFacts` — carry what the
slot check (WP05) and the prompt read (WP06) need, so nothing is read again outside the verified read (research R9,
revised after squad A1):

- per held step `action:M/s`: the slot ids its prompt companion declares, in declaration order, and whether each holds
  text (legacy), parsed from the canonical render exactly as the load rule parses it (A5);
- per held step enhancement: its entries `(slot id, canonical fragment text)` in authored order.

## Context

- Spec: FR-001, FR-003, FR-005, C-002, C-006. Research R8, R9. Squad A1, A9.
- Read first: `domain/PackCompositionFacts.java` (its constructors and `NONE`), `domain/PackContents.java`
  (`PromptSlotFacts`, `stepEnhancements`), `infra/storage/DomainRepositoryService.java` (`compositionFacts` ~l.503,
  `recordPromptSlotFaults` ~l.908, `stepEnhancementFacts` ~l.943), `application/WriteFacts.java`,
  `application/CompositionFactsCache.java`, `infra/storage/StagedPacks.compositionFacts` (it delegates to the same
  builder), `kernel/markdown/PromptSlots`, `StepEnhancementFacts.Companion.canonicalText`.

## ⛔ Coordination gate for T013

`DomainRepositoryService` is slice B's file (PR #501). **T013 starts only after #501 has merged into `main` and the
mission branch has been rebased onto it** (C-006). If it has not, finish T011, T012 and T014's pure parts, mark T013
blocked, and tell the run lead. The hunk is additive: one call that fills the new member.

## Subtasks

### T011 — `PromptFacts` and the new member

- `packs.domain.PromptFacts` (record): `Map<Urn /*action:M/s*/, List<DeclaredSlot>> declared` and
  `Map<Urn /*step_enhancement:id*/, List<Fill>> fills`, with `DeclaredSlot(SlotId id, boolean holdsText)` and
  `Fill(SlotId slot, String text)`; `PromptFacts.NONE`; immutable copies; deterministic map order (insertion order of
  sorted keys).
- `PackCompositionFacts` gains `PromptFacts prompts` as its last component; every existing constructor/factory keeps
  its meaning with `PromptFacts.NONE`, so no caller outside this WP changes. Javadoc in the record's style.
- `PromptFactsTest`: copies are defensive; order is stable.

### T012 — `WriteFacts` keeps the member

- Each transform in `WriteFacts` (l.47, l.93, l.162) that rebuilds facts for a per-artifact or relationship write must
  carry `prompts` through unchanged (those writes cannot change a step prompt or an enhancement's entries; if one can,
  recompute — check `StepEnhancement` writes, which are refused today by R-REL and the load). `WriteFactsTest`: a
  transform keeps the member.

### T013 — Fill it where the facts are built (after the rebase)

- In `DomainRepositoryService.compositionFacts(PackIdentifier)`: from the same read that fills
  `PackContents.promptSlots` and `stepEnhancements`, build `PromptFacts`: map each prompt file
  `missions/<M>/steps/<s>.prompt.md` to `action:M/s` (the binding already knows the path; do not parse paths by hand if
  a helper exists), take `PromptSlots.Parse.slots()` (id, empty ⇒ `holdsText = !empty`), and each enhancement's
  entries with `MarkdownCodec.write(fragment body)` as text — the same canonical text the load rule judges.
- A pack that failed its load keeps today's behaviour (the refusal comes first).

### T014 — Equality and cache tests

- `DomainRepositoryServicePromptFactsTest` over the example packs (`testFixtures/resources/example-packs`): built-in
  declares `spdd:reasons-block` on `action:software-dev/{specify,plan,tasks,implement,review}` and nothing on the
  others; org-base's five enhancements fill it with their fragment text; the facts' slot ids equal
  `PackContents.promptSlots` for the same pack (squad A1's "canonical render must equal the load rule's").
- `CompositionFactsCacheTest`: the member is cached with the other facts, computed once per fingerprint.

## Project constraints (every subtask)

- **Test first (C-004)**: the scenario and a failing unit test come before the code; commit the red test first when
  the behaviour is new. Every refusal has a failing fixture and a passing twin that differ only in the faulty part.
- **Canonical shape only (C-005)**: fixtures, examples and scenarios use the canonical shape; an invalid shape appears
  only to exercise a refusal. Never exclude a field from a parity or round-trip check.
- **Stay out of slice B (C-006)**: do not edit `PackCommitStore`, `PackLocator`, `PackDirectories`,
  `CommitStorePackWriteStore`, `FileSystemPackSources`, `Loader`/`LoadedPack`, `CharterWiring`, `file-kinds.yaml`, or
  the example packs' `pack.md`/`README.md`/`org-charter.yaml`. `DomainRepositoryService` only in WP03's one hunk.
- **Hexagonal**: `HexagonalArchitectureTest` must stay green: only `activations.infra.packs` names packs, and only
  `packs.application`/`packs.domain`; generated `api` types only in `*.infra.api` and `web`.
- **Quality gates**: Checkstyle, palantir-java-format (Spotless), Error Prone, NullAway and doclint are on; no
  suppression, no disabled test, no new dependency.
- **Gradle**: `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 <module>:test` (or
  `:contracts:...`, `:service:deployable:test --tests ...`), foreground, one run at a time, never `--stop`, never
  `verifyAll` locally.
- **Commits**: signed, conventional (`feat(...)`, `test(...)`, `docs(...)`), no AI model name and no
  `Co-Authored-By` trailer; an edit to a file another WP owns is recorded in the message as
  `out-of-map: <file> — <why>`.
- **Contract changes** each carry a dated fragment under `contracts/charter/changes/2026-11-12-*.md` with the class
  `contracts/README.md` of the mission names (Added, Changed wording only, Breaking).

## Branch Strategy

- Planning branch: `kitty/mission-effective-step-and-action-context-01M774HB`; final merge target: `slice-c/effective-step-context` (then a pull request into `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.
- Implementation command: `spec-kitty agent action implement WP03 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- `./gradlew :service:packs:test` green; the round trip (`:packs:wholePackRoundTrip`) unaffected (C-002).
- No other slice-B file touched; the `DomainRepositoryService` hunk recorded as `out-of-map`-free (it is owned here) and
  announced in the PR body.

## Risks and reviewer guidance

- Canonical text: compare with the load rule, never re-implement the parse.
- Memory: fragments are small; do not store prompt bodies (the read takes them from the stored companion, WP06).
