---
work_package_id: WP03
title: '#186 closeout: the whole-pack round trip under the required gate'
dependencies:
- WP01
- WP02
requirement_refs:
- FR-006
- FR-007
- FR-008
- NFR-001
- NFR-002
- C-004
- C-005
- C-006
- SC-003
- SC-004
planning_base_branch: epic-186-closeout
merge_target_branch: epic-186-closeout
branch_strategy: Planning artifacts for this mission were generated on epic-186-closeout. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into epic-186-closeout unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-closeout-01M6P994
base_commit: fa6bcf169363a84349e9617cfb3342530a53d1ae
created_at: '2026-11-05T17:04:21.166082+00:00'
subtasks:
- T009
- T010
- T011
- T012
- T013
phase: Phase 2 - Closeout
history:
- at: '2026-11-05T17:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: .github/workflows/
create_intent:
- docs/changelog/2026-11-05-whole-pack-required.md
execution_mode: code_change
model: ''
owned_files:
- .github/workflows/whole-pack.yml
- .github/workflows/ci.yml
- service/packs/build.gradle.kts
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- docs/guides/service-build.md
- docs/guides/contributing.md
- docs/adr/0023-ci-gate-required-check.md
- docs/plans/roadmap.md
- docs/plans/milestones.md
- docs/plans/triage-2026-11-02.md
- docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md
- docs/changelog/2026-11-05-whole-pack-required.md
- .kittify/charter/charter.md
role: implementer
tags: []
task_type: implement
tracker_refs:
- '#186'
---

# Work Package Prompt: WP03 – #186 closeout: the whole-pack round trip under the required gate

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Operator ruling (2026-11-05, #186): `whole-pack` is wired into `ci.yml`'s `gate` job, so every PR has to keep packs
round-tripping; docs updated. Design: plan D5, D7; research R3 and the squad table.

## Branch Strategy

Planning branch and merge target: `epic-186-closeout`. Implement with
`spec-kitty agent action implement WP03 --agent claude --mission whole-pack-closeout-01M6P994` once WP01 and WP02
are approved.

## Subtasks

### T009 — `whole-pack.yml`

- Header: replace "Not a required check yet …" with: pull requests enter through `ci.yml`, which calls this
  workflow (`workflow_call`) for the `wholepack` group, so it feeds `CI gate / gate` (ADR-0023); epic #186.
- Triggers: drop `pull_request`; keep `push` to `main` and `workflow_dispatch`; add bare `workflow_call:`. `push`
  paths become `contracts/**` (was `contracts/packs/**`), `service/**`, `build-logic/**`, `gradle/**`,
  `*.gradle.kts`, `gradle.properties`, `gradlew`, `.github/workflows/whole-pack.yml`.
- Concurrency: `group: whole-pack-${{ github.workflow }}-${{ github.ref }}`,
  `cancel-in-progress: ${{ github.workflow == 'whole-pack' }}`, with `service.yml`'s three-line comment adapted.

### T010 — `ci.yml`

- Header L8: list `whole-pack` among the called workflows. Comment ~L86: mirror whole-pack.yml too.
- `changes` output `wholepack`; regex
  `wholepack_re='^(contracts/|service/|build-logic/|gradle/|[^/]+\.gradle\.kts$|gradle\.properties$|gradlew$|\.github/workflows/(whole-pack|ci)\.yml$)'`
  and its echo line.
- Job `wholepack`: `name: Whole-pack round trip`, `needs: changes`,
  `if: needs.changes.outputs.wholepack == 'true'`, `uses: ./.github/workflows/whole-pack.yml`.
- `gate`: `needs: [changes, service, native, docs, wholepack]`; env `WHOLEPACK: ${{ needs.wholepack.result }}`; the
  echo and the loop pair `"wholepack:$WHOLEPACK"`.

### T011 — Code comments

- `service/packs/build.gradle.kts` (~L135-142): keep "not part of check"; CI runs it under `CI gate / gate` through
  `whole-pack.yml` (required, #186).
- `WholePackRoundTripTest` javadoc (~L51): outside `check`; CI requires it through `CI gate / gate`.

### T012 — Docs

- `docs/guides/service-build.md`: L208-210 → required through `CI gate / gate` when its paths change; L87 keeps
  "not in `verifyAll`" (drop only the stale "yet" if it implies joining verifyAll).
- `docs/guides/contributing.md` (~L74-76): the gate also runs the whole-pack round trip.
- `docs/adr/0023-ci-gate-required-check.md`: dated note (2026-11-05, #186): a fourth group, `wholepack`, calls
  `whole-pack.yml`; the required check is unchanged.
- `docs/plans/triage-2026-11-02.md` L359 (#186 closed by this PR) and L361 (#197: whole-pack now in a group), as dated
  notes; `docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md` L130-131: a `*Clarified 2026-11-05:*`
  note; `docs/plans/roadmap.md` (#186 track done); `docs/plans/milestones.md` L88 (#186 row: closed).
- `.kittify/charter/charter.md` "Gates before merge": add the whole-pack round trip, required through `CI gate / gate`
  (operator ruling, 2026-11-05). Keep `charter.yaml` untouched unless its gate list names gates (check).
- Update each doc's `updated:` frontmatter.

### T013 — Changelog and checks

- `docs/changelog/2026-11-05-whole-pack-required.md`, shaped like `2026-11-04-cpu-time-budgets-report-only.md`:
  the whole-pack round trip is required; step texts refuse when built (#299); validate mode accepts a root with `..`
  (#319); epic #186 closed. Description 50-180 characters. Do not edit the generated `docs/changelog/README.md`.
- Run `python3 scripts/docs/check_docs.py all`, the frontmatter check, markdownlint-cli2 and cspell on changed docs.

## Definition of Done

- The gate's `needs`, env, echo and loop all list `wholepack`; `whole-pack.yml` has no `pull_request` trigger.
- No doc says the whole-pack round trip is not required.
- Docs checks green.

## Reviewer guidance

Diff the `wholepack_re` against whole-pack.yml's `push` paths (they must match, plus `ci.yml`). Check the gate fails
closed on a `failure`/`cancelled` wholepack result and passes on `skipped`.
