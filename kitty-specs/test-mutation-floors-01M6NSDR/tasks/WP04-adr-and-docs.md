---
work_package_id: WP04
title: ADR "Nightly mutation floor" and docs
dependencies: [WP02, WP03]
requirement_refs: [FR-005]
planning_base_branch: test-mutation-floors
merge_target_branch: test-mutation-floors
branch_strategy: Planning artifacts for this mission were generated on test-mutation-floors. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-mutation-floors unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-mutation-floors-01M6NSDR
base_commit: 3aeb9a239b3f67f497bdc85a6e2c1a8c3bfefcbf
created_at: '2026-11-05T16:46:59.384381+00:00'
subtasks: [T014, T015, T016]
phase: Phase 3 - Record
agent: claude
history:
- at: '2026-11-05T12:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: docs/
create_intent:
- docs/adr/0062-nightly-mutation-floor.md
- docs/changelog/2026-11-05-nightly-mutation-floors.md
execution_mode: code_change
owned_files:
- docs/adr/0062-nightly-mutation-floor.md
- docs/guides/service-build.md
- docs/changelog/2026-11-05-nightly-mutation-floors.md
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — ADR "Nightly mutation floor" and docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Record the decision and make the docs match what runs (FR-005).

## Branch Strategy

Planning base and merge target `test-mutation-floors`; depends on WP02 and WP03.

```bash
spec-kitty agent action implement WP04 --agent claude --mission test-mutation-floors-01M6NSDR
```

## Environment

`export JAVA_HOME=/usr/lib/jvm/temurin-25 SPEC_KITTY_NO_NAG=1 SPEC_KITTY_NO_UPGRADE_CHECK=1`; `./gradlew` only; run
`./gradlew syncPacks -PopenPacksDir=/home/user/spec-kitty-open-packs` once per worktree and pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs` to every run. Never `./gradlew --stop`. Never a model-bearing identity.

## Subtasks

- **T014 ADR.** Before writing, check the next free number against `docs/adr/` on `main` **and** every open PR
  (`git ls-remote`, then the PR branches). #376 holds 0059. If 0060 is taken, rename the file, and the owned path
  with it, and report.

  Follow the ADR template and frontmatter of a recent ADR (0057/0058). Status: Accepted, operator ruling
  2026-11-04/05. Record:
  - that the experiment status ends (DM-01M57RMMBAS7275PXGNSYJZBQY);
  - fixed floors per module, and the packs groups;
  - the raise rule (by PR, when the score is ≥ 5 above the floor) and the lower rule (only by ADR amendment);
  - that test strength is reported, not gated;
  - the triage order (kill by strengthening, equivalent with a reason, accepted with an owner; never a new test
    only for a mutant);
  - the nightly behaviour (`--continue`, the summary, one issue);
  - never on a PR or the required check;
  - the rejected alternatives: a survivor delta on changed classes, gating test strength, and running per PR.
- **T015 Docs.**
  - `docs/guides/service-build.md`: replace the "Not in verifyAll: mutation testing …" paragraph with how
    `./gradlew pitest` runs, which modules and groups, the floors and where they live, the floor-check message,
    `floors.json`, `pit-targets.init.gradle` and the nightly. Keep #368's sections intact; it edits other
    sections of this file.
  - A changelog entry `docs/changelog/2026-11-05-nightly-mutation-floors.md`, in the repo's frontmatter
    convention (description 50–180 characters).
  - The FR-009 trace lives in another mission's `kitty-specs`: do not edit it. Instead, the ADR says it supersedes
    the FR-009 "experiment" wording.
- **T016 Checks.** `python3 scripts/docs/check_docs.py all`, `npx --yes markdownlint-cli2@0.18.1 <files>`,
  `npx --yes cspell@9.2.0 --no-progress` (whole tree, as CI runs it). All clean. One commit with the two required
  lines.

## Definition of Done

- [ ] The ADR is accepted and numbered against main and open PRs; the guide and changelog match the build; docs
  checks are clean.
