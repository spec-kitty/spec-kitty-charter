# Tasks: Whole-pack round trip closeout (epic #186)

**Mission**: `whole-pack-closeout-01M6P994` · **Planning branch**: `epic-186-closeout` · **Merge target**: `epic-186-closeout`
(the branch then goes to `main` by one pull request, which the operator merges).

Three work packages, one commit each. WP01 and WP02 are independent; WP03 depends on both, because the operator's
ruling closes the epic only after #299 and #319 land with the gate change.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Move `MarkdownScanner` (and its test) to `kernel.markdown`; add `MarkdownLines` | WP01 | |
| T002 | `MarkdownDocument` delegates; codec and package-info files repointed | WP01 | |
| T003 | `StepDocument` / `StepSection` check the line rules when built | WP01 | |
| T004 | Kernel rule tests and the `:packs` agreement test over shared tables | WP01 | |
| T005 | ADR-0040 and ADR-0044 dated notes; kernel row in `hexagonal-service.md` | WP01 | |
| T006 | Red-first validate-mode test over four root spellings (+ missing-template control, load parity) | WP02 | [P] |
| T007 | Normalize the root once in `FileSystemPackInspector.inspect` | WP02 | [P] |
| T008 | Linked-root mission-type variant; `missingNamedFile` precondition javadoc | WP02 | [P] |
| T009 | `whole-pack.yml` becomes callable; paths corrected; concurrency form | WP03 | |
| T010 | `ci.yml`: `wholepack` group, job, and the gate's needs/env/echo/loop | WP03 | |
| T011 | Code comments: `service/packs/build.gradle.kts`, `WholePackRoundTripTest` javadoc | WP03 | |
| T012 | Docs: service-build, contributing, ADR-0023 note, plans, roadmap, milestones, charter | WP03 | |
| T013 | Changelog entry; docs checks | WP03 | |

## WP01 — #299: the Markdown line rules move to the kernel; step texts refuse when built

**Goal**: `StepDocument` and `StepSection` refuse, when built, every text the line rules refuse (FR-001, FR-002), with
the pack documents unchanged (FR-003). **Priority**: P1. **Prompt**: `tasks/WP01-kernel-markdown-line-rules.md`.

T001 Move `MarkdownScanner` (and its test) to `kernel.markdown`; add `MarkdownLines` (WP01)
T002 `MarkdownDocument` delegates; codec and package-info files repointed (WP01)
T003 `StepDocument` / `StepSection` check the line rules when built (WP01)
T004 Kernel rule tests and the `:packs` agreement test over shared tables (WP01)
T005 ADR-0040 and ADR-0044 dated notes; kernel row in `hexagonal-service.md` (WP01)

**Independent test**: `./gradlew :kernel:test :missions:test :packs:test :deployable:test`.
**Dependencies**: none. **Risks**: an existing test's message order (`MissionValuesTest` blank title).

## WP02 — #319: validate mode normalizes the pack root once

**Goal**: validate mode over `<dir>/x/../pack` gives the plain spelling's verdict (FR-004), and a missing template is
still reported (FR-005). **Priority**: P1. **Prompt**: `tasks/WP02-validate-mode-normalized-root.md`.

T006 Red-first validate-mode test over four root spellings (+ missing-template control, load parity) (WP02)
T007 Normalize the root once in `FileSystemPackInspector.inspect` (WP02)
T008 Linked-root mission-type variant; `missingNamedFile` precondition javadoc (WP02)

**Independent test**: `./gradlew :packs:test --tests '*MissionTypeBindingTest*' --tests '*FileSystemPackInspectorTest*' -PrequireSymlinks=true`.
**Dependencies**: none (parallel with WP01). **Risks**: a fix that patches only `startsWith` (caught by trailing `.`/`..`).

## WP03 — #186 closeout: the whole-pack round trip under the required gate

**Goal**: `CI gate / gate` runs and requires the whole-pack round trip when its paths change (FR-006); docs and
changelog say so (FR-007); the PR closes #299, #319 and #186 (FR-008). **Priority**: P2.
**Prompt**: `tasks/WP03-whole-pack-required-gate.md`.

T009 `whole-pack.yml` becomes callable; paths corrected; concurrency form (WP03)
T010 `ci.yml`: `wholepack` group, job, and the gate's needs/env/echo/loop (WP03)
T011 Code comments: `service/packs/build.gradle.kts`, `WholePackRoundTripTest` javadoc (WP03)
T012 Docs: service-build, contributing, ADR-0023 note, plans, roadmap, milestones, charter (WP03)
T013 Changelog entry; docs checks (WP03)

**Independent test**: the workflow diff, `check_docs.py all`, and this PR's own CI (the gate shows the
`Whole-pack round trip` group). **Dependencies**: WP01, WP02. **Risks**: a regex that skips the group on a PR that
breaks it (covered: `contracts/`, `gradlew`).

## Parallelization and MVP

WP01 and WP02 touch disjoint files and can run in parallel; WP03 last. MVP: WP01 + WP02 (the two fixes the ruling
requires first).
