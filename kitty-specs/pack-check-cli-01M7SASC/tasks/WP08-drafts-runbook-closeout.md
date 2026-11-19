---
work_package_id: WP08
title: Drafts, the release runbook and the closeout list
dependencies:
- WP01
requirement_refs:
- FR-012
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
subtasks:
- T032
- T033
- T034
phase: Phase 4 - Release
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: kitty-specs/pack-check-cli-01M7SASC/
create_intent:
- kitty-specs/pack-check-cli-01M7SASC/research/issue-drafts/open-packs-pack-entry-linked.md
- kitty-specs/pack-check-cli-01M7SASC/release-runbook.md
- kitty-specs/pack-check-cli-01M7SASC/closeout.md
execution_mode: planning_artifact
owned_files:
- kitty-specs/pack-check-cli-01M7SASC/research/issue-drafts/**
- kitty-specs/pack-check-cli-01M7SASC/release-runbook.md
- kitty-specs/pack-check-cli-01M7SASC/closeout.md
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP08 — Drafts, the release runbook and the closeout list

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`

## Objective

The planning-side deliverables that no code WP may own (all under `kitty-specs/`):

- the open-packs draft issue (`pack_entry_linked` in its Python mirror);
- the exact runbook for the post-merge release follow-through, FR-012 (D4);
- the closeout evidence list.

## Context

- Spec: FR-012, rulings D4 and D6. charter.md "Orchestrating cloud sessions": a child session writes draft issues for
  a sibling repository into its mission research directory with an `open-packs#TBD-<slug>` placeholder, and the
  orchestrator files them. `docs/development/issue-tracker.md` rules 2 and 4.
- `docs/guides/service-build.md` "Release a service version", steps 1 to 5. The operator pushes tags; the session's
  proxy refuses tag pushes.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP08 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T032 — The open-packs draft

`kitty-specs/pack-check-cli-01M7SASC/research/issue-drafts/open-packs-pack-entry-linked.md`, with placeholder
`open-packs#TBD-pack-entry-linked`:

- the title;
- the problem: charter now refuses a link inside a pack with `pack_entry_linked`, on registration, at load and in
  pack-check. open-packs' Python classification and schema pre-check (`validate_pack_schemas.py`) does not know the
  code;
- the ask: either retire the mirror in favor of `pack-check` at the pin (open-packs#252), or teach it the code;
- the note that no open-packs pack holds a link today (the D6 check);
- the labels it should carry.

### T033 — The release runbook (FR-012)

`kitty-specs/pack-check-cli-01M7SASC/release-runbook.md`, the exact steps once PR #608 merges:

1. **Version PR:** set `version=0.1.0-rc.2` in `gradle.properties`. Run `./gradlew checkServiceReleaseTag
   -PreleaseTag=service-v0.1.0-rc.2` after `git fetch --tags origin`. The PR body says the bundled contract is
   unreleased (`0.2.0-SNAPSHOT`); a pre-release may bundle it.
2. **After it merges,** dispatch `service-native.yml` on `main` at the merge commit. Record the run URL.
3. **When that run is green,** tell the orchestrator the exact tag `service-v0.1.0-rc.2` and the merge commit SHA, with
   the command `git tag service-v0.1.0-rc.2 <sha> && git push origin service-v0.1.0-rc.2`. The operator pushes it.
4. **Watch `service-release.yml`.** The release must list 13 files, `SHA256SUMS` 6 lines, and attestations. Verify:
   - `sha256sum --check --ignore-missing SHA256SUMS`;
   - `gh attestation verify pack-check-0.1.0-rc.2.jar …` with the signer workflow and the source ref.
5. **Snapshot PR:** set `version` to the next development version (`0.1.0-SNAPSHOT` stays below `0.1.0`; follow the
   guide's step 5).
6. **Evidence:** the PR URLs, the run URLs and the release URL, posted on #478.

### T034 — The closeout list

`kitty-specs/pack-check-cli-01M7SASC/closeout.md`:

- the issues this mission delivers, with evidence. #478 (minus FR-012's release, which completes after merge: say so
  on #478 with `Refs` until the release exists) and #597 (`Closes #597`, with the test file and code);
- epic #549's checklist line to tick;
- the follow-ups filed (none expected), and the open-packs draft placeholder.

## Definition of Done

- The three files exist and are accurate against the merged code (the code name, file paths, test names).
- The runbook's commands were dry-read against `service-build.md` and the workflows.

## Risks and reviewer guidance

- **Never** instruct a tag push from a session. Never close #478 before the release exists, or say explicitly that
  only the release step remains.
