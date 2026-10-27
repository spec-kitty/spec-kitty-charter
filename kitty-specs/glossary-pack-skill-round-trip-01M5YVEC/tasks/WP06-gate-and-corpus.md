---
work_package_id: WP06
title: Gate and corpus
dependencies:
- WP03
- WP05
requirement_refs:
- FR-008
- FR-009
- C-002
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-glossary-pack-skill-round-trip-01M5YVEC
base_commit: 90ddefea2b974eb4a31092c99090dac5a9a383b9
created_at: '2026-10-27T17:08:21.837985+00:00'
subtasks:
- T020
- T021
- T022
- T023
phase: Phase 3 - Evidence and record
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/resources/whole-pack/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillCorpusTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackAndSkillCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP06 — Gate and corpus

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Turn `other:glossary_packs` green in the #187 gate, account honestly for the modelled `skill` kind that the pinned
built-in pack has no file of, and prove every pinned file of both kinds round-trips (spec US1, US4, FR-008, FR-009;
plan IC-04; post-plan R1, R5). Flip **only** this mission's kinds.

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP06 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Subtasks

### T020 — `empty_kinds` ratchet

In `WholePackExpectation`:

- Accept a third top-level key in `expected-red.yaml`, `empty_kinds`, entries `{kind, reason, issue}` (same
  validation as `kinds`: reason and issue required, no unknown keys, no repeats).
- `EMPTY_MODELLED_KIND` is not raised for a kind listed in `empty_kinds`.
- New problems: `STALE_BASELINE empty kind <k>: the pack has a file of it now; remove it from …` when the census has
  the kind; `STALE_BASELINE empty kind <k>: not a modelled kind; remove it …` when it is not modelled.
- The report (`WholePackReport`) needs no new row; if it lists problems or kinds, check the empty kind still reads
  sensibly.
- `WholePackExpectationTest`: positive controls — an unlisted empty modelled kind still fails; a listed one passes;
  a listed kind with a file fails; a listed kind that is not modelled fails; an `empty_kinds` entry without a reason
  or issue is refused at load.

### T021 — `expected-red.yaml`

- Remove the `other:glossary_packs` kind entry.
- Add:

  ```yaml
  empty_kinds:
    - kind: skill
      reason: >-
        The pinned built-in pack has no skill (skills/ holds only .gitkeep). Skills round-trip in
        GlossaryPackAndSkillCorpusTest and the open-packs seam test. The gate does not yet pair a companion
        (body_path) with its artifact; when built-in gains a skill this entry goes stale and that pairing is due.
      issue: "#192"
  ```

- Update the header comment to describe `empty_kinds` in one sentence.
- If `WholePackRoundTripTest` asserts the modelled kinds equal the bound schemas, it now includes `glossary-pack`
  and `skill` through the registry; change nothing there unless a hard-coded list exists (append only).

### T022 — Corpus test

`GlossaryPackAndSkillCorpusTest` (tag `open-packs`, mirroring `MissionStepContractCorpusTest`):

- Glossary: every `*.glossary-pack.yaml` under `OpenPacksCorpus.packsDirectory()` reads through the strategy,
  maps to the entity and back, writes, re-reads, and compares equal with `MeaningComparator.adr0036()`
  (`"glossary-pack"`). Assert the covered list is exactly 4 files (built-in, event-driven, internal, qa).
- Skills: every `*.skill.yaml` is read through `FilesystemPackStore` (or the binding plus the companion resolution)
  so its body is filled; round-tripped through entity and DSO; written with its body into a temp directory;
  compared by meaning (`"skill"`) and the body byte for byte. Assert exactly 5.
- Coverage: at least one term with each of `see_also`, `synonyms_to_avoid`, `introduced_in_mission`, `aliases`;
  at least one multi-line definition; every skill has `invocation.side_effects`.
- Positive control: a writer that drops every `see_also` fails at exactly those term paths; a writer that drops one
  character of a body fails the byte comparison.

### T023 — Gate and seam runs

Run on Java 25 and record the output summary in the WP review notes:

- `./gradlew :packs:wholePackRoundTrip` → green; the report shows `glossary-pack` 1 green and no
  `other:glossary_packs` row.
- `./gradlew :packs:openPacksRoundTrip` → green (seam test, corpus test, step-contract corpus).
- `./gradlew :packs:test :packs:check`.

## Definition of Done

- The three runs above green; the report row and the corpus counts quoted in the review notes.
- No other kind's expected-red entry touched.

## Risks / reviewer guidance

- The `empty_kinds` mechanism must not let any other empty modelled kind pass: the positive controls are the
  evidence.
- The corpus counts are exact (`hasSize`), so a vanished file fails.
