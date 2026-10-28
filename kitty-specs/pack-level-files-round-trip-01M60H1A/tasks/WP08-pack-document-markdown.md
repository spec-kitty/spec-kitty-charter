---
work_package_id: WP08
title: "pack.md through #194's markdown content form"
dependencies:
- WP06
requirement_refs:
- FR-005
- SC-001b
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T030
- T031
- T032
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocument
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentRoundTripTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentRoundTripTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP08 — pack.md through #194's markdown content form

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Hold and write `pack.md` through the `markdown` content form that sibling #194 builds in its PR-A (spec FR-005,
SC-001b; decision `DM-01M60HM8`). **Blocked until #194's PR-A has merged into main.** Rebase onto it first and follow
its design for how a markdown file is held. If PR-A has not merged at hand-off, this WP stays open, `other:pack.md`
stays expected-red, and the hand-off says so.

## Subtask T030 — Rule and domain slot

- Add a `file-kinds.yaml` rule for `*/pack.md` in the shape #194 defines for markdown files.
- Add the `Pack` document slot, typed as #194's markdown value.
- Read it in the store, write it in `WholePackStore`, and append it to the fingerprint tail between the presets and
  the manifest.

## Subtask T031 — Gate

- `DomainRoundTrip` models `pack-document`.
- `expected-red.yaml` loses `other:pack.md`.
- A lossy body writer goes red at `pack.md`, and the faithful run is green.
- `withPayloads` keeps the document.

## Subtask T032 — Tests and docs

- `PackDocumentRoundTripTest`: `pack.md` with and without frontmatter, CRLF, and no final newline, each judged by
  #194's rule.
- ADR-0043 decision 8 is marked done.

## Definition of Done

- The gate is green, with built-in `pack.md` green.
- `./gradlew :packs:test :packs:wholePackRoundTrip` is green.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP08 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
