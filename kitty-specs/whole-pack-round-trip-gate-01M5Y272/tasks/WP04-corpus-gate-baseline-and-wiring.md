---
work_package_id: WP04
title: Corpus gate, baseline, build, CI and docs
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-001
- FR-012
- NFR-001
- SC-001
- SC-002
- SC-003
- SC-004
planning_base_branch: feat/whole-pack-round-trip-gate
merge_target_branch: feat/whole-pack-round-trip-gate
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-round-trip-gate. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-round-trip-gate unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-round-trip-gate-01M5Y272
base_commit: a22ab233313aa9cb657ace551b86b92e5e8c9233
created_at: '2026-10-27T07:48:06.888282+00:00'
subtasks:
- T014
- T015
- T016
- T017
- T018
phase: Phase 2 - Gate assembly
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/test/resources/whole-pack/exclusions.yaml
- .github/workflows/whole-pack.yml
- docs/changelog/2026-10-27-whole-pack-round-trip-gate.md
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/resources/whole-pack/**
- service/packs/build.gradle.kts
- .github/workflows/whole-pack.yml
- docs/guides/service-build.md
- docs/changelog/README.md
- docs/changelog/2026-10-27-whole-pack-round-trip-gate.md
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Corpus gate, baseline, build, CI and docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Assemble the gate over the pinned built-in pack, give it its own Gradle task outside `check`, seed the expected-red
baseline from a triaged first run, run it in a non-required CI workflow, and document it (spec FR-001, FR-012,
C-002; research R-8).

## Branch Strategy

Planning base and final merge target: `feat/whole-pack-round-trip-gate`; lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP04 --agent claude --mission whole-pack-round-trip-gate-01M5Y272
```

## Subtask T014 — `WholePackRoundTripTest`

`@Tag("whole-pack")`, one `@Test` (plus `@TempDir`):

1. `OpenPacksCorpus.assertPinned()`; `Path pack = OpenPacksCorpus.packsDirectory().resolve("built-in")`.
2. `census = WholePackCensus.of(pack)`; `outcomes = new DomainRoundTrip(new YamlSerializationStrategy())
   .roundTrip(pack, temp.resolve("built-in"), census)`.
3. For each modelled path: `Written(w)` → `MeaningComparator.provisional().compareTexts(kind, source, written)` →
   green or `"<path>: source <x>, written <y>"`; `Refused(r)` → red `"refused: r"`.
4. `evaluation = WholePackExpectation.fromClasspath().evaluate(census, roundTrip.modelledKinds(), observed)`.
5. Write `WholePackReport.render(...)` to `System.getProperty("charter.wholePackReport",
   "build/reports/whole-pack/report.md")`; print the kind table to stdout.
6. Assert `evaluation.problems()` is empty, with the full problem list as the failure message.

## Subtask T015 — Gradle

In `service/packs/build.gradle.kts`: register `wholePackRoundTrip` (group verification) modelled on
`openPacksRoundTrip`: same `openPacksDir` input + `charter.openPacksDir`, the pin validated in `doFirst` and passed
as `charter.openPacksRef`, `useJUnitPlatform { includeTags("whole-pack") }`, report path system property and
`outputs.file`. Add `excludeTags("whole-pack")` to `tasks.test` and to `openPacksRoundTrip`. **Do not** add it to
`check`. Correct the stale comment above `openPacksDir` ("every YAML file … must survive") to say what the task
does today and point to the whole-pack gate.

Verify: `./gradlew :packs:test` does not run the gate; `./gradlew :packs:check --dry-run` does not list it.

## Subtask T016 — Baseline and exclusions

Run the gate once with an empty baseline; triage **every** red:

- Unmodelled kinds → one `kinds` entry each, issue by owner: `agent-profile` #190; `mission-step-contract` #191;
  glossary/skill (`other:glossary_packs`) #192; `asset`, `other:assets`, `other:templates` #193; `procedure`,
  `other:missions` #194; `other:presets`, `other:pack.md`, `other:pack-manifest.yaml` #195; toolguide companion
  `other:toolguides` → the toolguide-companion owner (ask on #186 if unclear; use #186 meanwhile).
- Reds in modelled kinds → one `files` entry each with the **specific** first difference as reason and #187 as
  issue for comparator-policy questions (e.g. `[]` dropped, operator Q2) or the upstream issue for a pack fix.
- Exclusions (append-only, reason each): `**/README.md`, `skills/.gitkeep`, `**/__init__.py` — only for files of
  unmodelled kinds; never exclude a modelled file.

## Subtask T017 — CI

New `.github/workflows/whole-pack.yml` (**not** called from `ci.yml`, so not part of `CI gate / gate`): on
`pull_request` and `push` to main, paths `service/**`, `contracts/packs/**`, `gradle.properties`, the workflow
itself; same pinned actions as `service.yml` (checkout with the pin, setup-java 25, setup-gradle); run
`./gradlew :packs:wholePackRoundTrip -PopenPacksDir=...`; upload `service/packs/build/reports/whole-pack` with
`if: always()`. Least-privilege `permissions: contents: read`.

## Subtask T018 — Docs

- `docs/guides/service-build.md`: a short section "The whole-pack round-trip gate": what it checks, how to run it,
  where the report is, how a Wave 1 mission flips its kind (the ratchet message), and that it is not part of
  `verifyAll` yet.
- `docs/changelog/2026-10-27-whole-pack-round-trip-gate.md` (frontmatter like the 2026-10-27 entry) + newest-first
  link in `docs/changelog/README.md`.
- Run `python3 scripts/docs/check_docs.py all`, markdownlint and cspell on the changed docs.

## Definition of Done

- `./gradlew :packs:wholePackRoundTrip` green on the pinned checkout; report written; every baseline entry has a
  specific reason and an issue.
- `./gradlew verifyAll` green and does not run the gate.
- Docs checks clean.

## Reviewer guidance

Grep the build for `whole-pack` — it must appear only in the new task and the two `excludeTags`. Spot-check five
baseline `files` entries against the report. Confirm the workflow is not referenced by `ci.yml`.
