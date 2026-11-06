---
work_package_id: WP01
title: One replica per deployment (ADR)
dependencies: []
requirement_refs:
- FR-019
- SC-004
- C-006
planning_base_branch: feat/container-guard-and-actuator
merge_target_branch: feat/container-guard-and-actuator
branch_strategy: Planning artifacts for this mission were generated on feat/container-guard-and-actuator. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/container-guard-and-actuator unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
history: []
agent_profile: scribe-sally
authoritative_surface: docs/adr/
create_intent:
- docs/adr/0060-one-replica-per-deployment.md
- docs/changelog/2026-11-05-one-replica-per-deployment.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/adr/0060-one-replica-per-deployment.md
- docs/changelog/2026-11-05-one-replica-per-deployment.md
- docs/guides/run-in-a-container.md
role: implementer
tags: []
tracker_refs: []
---
# WP01: One replica per deployment (ADR)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Branch Strategy

Planning branch and final merge target: `feat/container-guard-and-actuator`. Execution worktrees are allocated per computed lane from
`lanes.json`; do not pick a base by hand.

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission container-guard-and-actuator-01M6P99A`

## Objective

Record the operator's ruling of 2026-11-05 on #233 as an accepted ADR. **A deployment runs exactly one replica**
for now, because the service keeps per-project state in memory:
- the write-through copy of the active charter (ADR-0033);
- the ETags (ADR-0018).

Multi-replica operation is out of scope until a hosting option is chosen. Closes #233 in its own pull request.

## Subtasks

### T001 — Write the ADR

- **Number.** Choose it at writing time: the next number free on `origin/main` *and* in every open PR. ADR-0059
  is taken by PR #376, so 0060 is expected. Recheck with `git fetch origin main`, list `docs/adr`, and scan the
  open PRs' files. If 0060 is taken, rename the file and fix the `owned_files` mapping in the PR body (the
  mission records the intent, not the number).
- **Template.** `docs/_meta/templates/adr.template`. The frontmatter is OKF: `title`, a `description` of 50 to
  180 characters, `doc_status: active`, `updated`, `decision_status: Accepted`, `decision_date: '2026-11-05'`,
  `type`/`kind: ADR`, `related` (real slugs only: `adr/0018-...`, `adr/0033-...`, `adr/0034-...`, `adr/0041-...`,
  `guides/run-in-a-container`), and `audience`.
- **Content:**
  - **Status.** Accepted 2026-11-05 by the operator (ruling on #233), under epic #226.
  - **Context.**
    - The two pieces of in-memory state, keyed per project. One instance may serve many projects (#226,
      2026-10-27).
    - #233's six concerns.
    - The hosting platform is not chosen (ADR-0041: a platform-neutral container).
  - **Decision.**
    - One replica per deployment.
    - The image documentation (#234, when written) and the container guide say "single replica".
    - A second replica, or a second process over the same configuration root, is unsupported. Point to the
      existing "one container per configuration root" limit (#349), which is a different but related rule.
  - **What breaks with two replicas.**
    - Divergent activation copies: a write on A is unseen by B.
    - ETag mismatches: a conditional write can pass on a stale replica.
    - The source fingerprint and cache coherence.
  - **Revisit when.** A hosting option is chosen. Then shared storage or sticky routing is decided, along with
    probes, draining, and auth/session state.
  - **Follow-ups.** #233's remaining scope items, listed as deferred.
  - **Consequences.** Health probes (#232) are single-instance liveness only, and there is no readiness gate
    for draining.
  - **Alternatives.** Sticky routing now; shared storage now. Both were rejected because no platform has been
    chosen.
- **Style.** Follow the existing ADRs' style: short sentences, British spelling where the repository uses it.
  Check the neighbours, e.g. 0041.

### T002 — Container guide

In `docs/guides/run-in-a-container.md`, under "Limits for now", add a **Single replica** bullet that links the
ADR. Do not edit any other part of the guide here; WP07 owns the rest.

### T003 — Changelog entry

Add `docs/changelog/2026-11-05-one-replica-per-deployment.md`, copying the frontmatter shape of a recent entry,
e.g. `docs/changelog/2026-10-29-container-properties-record.md`. Keep it under 3,000 characters. Never edit
`docs/changelog/README.md` or `docs/adr/README.md`: their indexes are generated (ADR-0047).

### T004 — Checks

Run:

```bash
python3 scripts/docs/check_docs.py all
npx markdownlint-cli2@0.18.1
npx cspell@9.2.0 --no-progress
```

Also run the site build if Node is available (`docs/guides/docsite.md`), or check that every `related:` slug
maps to a file. New words go in `docs/_meta/spelling-words.txt`.

## Definition of Done

- The ADR is accepted, numbered without a collision, and its description is at most 180 characters.
- The guide says "single replica".
- The docs checks pass.
- The PR closes #233.

## Reviewer guidance

Check that the ADR does not over-decide: no storage or routing choice, only "one replica" and its rationale.
