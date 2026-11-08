# Research: Whole-pack round trip closeout (epic #186)

A grounding squad of three read-only agents (one per seam) ran on 2026-11-05 against `main` at `588ff58a`.

## R1 — #299: where the Markdown rules live, and the kernel move

- **Decision**: move `MarkdownScanner` and the three line-level checks (`headingProblem`, `blockProblem`,
  `openingProblem`) to a new kernel package `ai.speckitty.charter.kernel.markdown`; keep `MarkdownDocument`
  (structure, `differences()`) in `packs.domain.markdown`, delegating its statics.
- **Rationale**: the package depends only on `java.util` and JSpecify, so it meets `KERNEL_IS_PURE`.
  `:missions` and `:packs` already depend on `:kernel` with `api`, so no Gradle change; `KERNEL` in the ArchUnit
  test covers any kernel subpackage, so no ArchUnit change. About 10 files.
- **Alternatives considered**: (a) move the whole package (about 27 importing files; the structural model has no
  second user); (b) record the gap in ADR-0044 as accepted (the brief prefers the move when it stays small).
- **Evidence**: `StepDocument` today refuses only a blank title or description; the codec refuses at write in
  `MissionTypeBinding.markdownOf`. The read path builds `StepDocument` from an already valid `MarkdownDocument`, so
  constructor checks cannot regress reads. No test builds an invalid step and expects only the write to fail.

## R2 — #319: the cause

- **Decision**: normalize in `FileSystemPackInspector.inspect` (`Path.of(root.locator()).toAbsolutePath().normalize()`).
- **Rationale**: the issue's guess (Spellings returning `Absent`) is close but not exact. Validate mode keeps the root
  as configured; `FilesystemPackStore.missingNamedFile` normalizes the target (`base.resolve(path).normalize()`),
  which also collapses the root's own `..`, so `target.startsWith(directory)` is false and the check answers
  "leaves the pack" before `Spellings.of` is reached. Had it been reached, `relativize` between the raw and the
  normalized path would give `..` segments and `Absent`. The load path normalizes its root already
  (`FilesystemPackStore` constructor), which is why only validate mode fails.
- **Alternatives considered**: `toRealPath` (rejected: renames a linked root to its target's name, which
  classification reads, #284; throws on a missing root); patching `Spellings` or `missingNamedFile` (rejected by the
  issue: fix the root once where it is resolved).
- **Same hazard**: a `.` segment; covered by the same fix.

## R3 — Closeout: wiring and docs

- `whole-pack.yml` has no `workflow_call`; `ci.yml` wires groups by hand (`changes` outputs, a job per group, the
  `gate` job's `needs`, env, echo and loop). No script or test asserts the needs list, so all four places change
  together in one edit.
- Whole-pack's paths all fall inside `service_re`, but a separate group keeps its result visible and its path filter
  exact. The merge queue inherits the event (ADR-0047), so whole-pack runs on queue entries too.
- Docs that say "not required": `docs/guides/service-build.md` (L87, L208-210), `whole-pack.yml` header,
  `service/packs/build.gradle.kts` comment, `docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md`
  (L130-131), `docs/plans/triage-2026-11-02.md` (L359, L361). Also to update: `docs/guides/contributing.md` (what
  the gate runs), ADR-0023 (dated note: a fourth group), roadmap and milestones (#186 done), the charter's
  "Gates before merge".
- Whole-pack runs green on `main` (run 179 on `588ff58a`), so requiring it blocks nothing today.

## Squad findings and dispositions

Post-plan squad, 2026-11-05: architect-alphonso, qa-test-designer-tessa, reviewer-renata (profiles loaded with
`spec-kitty agent profile show`). All three: APPROVE WITH CHANGES. Every finding accepted and folded:

| Lens | Finding | Disposition |
|---|---|---|
| architect | D2 said `null`; the API returns `Optional<String>` | Accepted: D2 corrected |
| architect | The form's rules belong to ADR-0040, not ADR-0044 | Accepted: dated note on ADR-0040, one line on ADR-0044 |
| architect | Kernel docs row, javadocs, package-info files | Accepted: D7 |
| architect | Scanner visibility | Accepted: only the codec-facing types public |
| architect | "Refuse exactly" overclaims (YAML guards) | Accepted: spec FR-002/SC-001 narrowed; builds-means-writes test |
| architect | `missingNamedFile` assumes a normalized root | Accepted: javadoc precondition, no code change |
| tessa | `x` must exist, or the red test fails for another reason | Accepted: D6 |
| tessa | Trailing `.`/`..` spellings, exact `: missing` negative control, load parity | Accepted: D6 |
| tessa | Linked-root mission-type variant | Accepted: D6 |
| tessa | #299 agreement test in `:packs` over shared tables, field by field | Accepted: D6 |
| tessa | `MissionValuesTest` blank-title message order | Accepted: keep `TITLE_RULES` first |
| renata | Regex must cover `contracts/` and `gradlew` | Accepted: D5 corrected |
| renata | Job id `wholepack`, display name | Accepted: D5 |
| renata | Five places in `ci.yml`; `WholePackRoundTripTest` javadoc | Accepted: D7 |
| renata | Keep `push` to main, drop `pull_request`; concurrency form correct | Confirmed as planned |
| tessa/architect | `<dir>/link/../p` resolves lexically | Accepted as a known cost (D4), same as the load path |


## Pre-PR squad, 2026-11-05

Lenses: architect-alphonso and reviewer-renata over the whole diff; the per-WP reviews (reviewer-renata) ran before
the merge. Verdicts: approve, and approve with minor fixes. `verifyAll` passed locally on Java 25; the whole-pack round
trip passed locally at `openPacksRef` (417 of 437 files green, 20 excluded, 0 red).

| Lens | Finding | Disposition |
|---|---|---|
| WP01 review | BOM literal written as a raw character | Folded: `'﻿'` |
| WP01 review | `MarkdownDocument` javadoc line and wording | Folded |
| WP01 review | Kernel test covers only a sample of the rows | Folded: tab, open fence, setext and indented rows added |
| WP02 review | Linked-root test does not catch a switch to the real path | Folded: javadoc and commit text say so; the comment at the fix and review guard it |
| WP03 review | ADR-0023 note order; assessment's superseded phrase | Folded |
| architect | `hexagonal-service.md` module table is stale beyond the kernel row | Kept its old `updated:` date; the table is a follow-up (out of scope) |
| architect | Triage rows for #299 and #319 contradicted the #186 row | Folded: dated notes |
| architect | Scanner javadoc, changelog "as the pack's documents do", ADR-0023 `decision_status`, service-build wording | Folded |
| correctness | A `..` after a linked folder resolves by text | Accepted (plan D4, as in the load); stated in the changelog |
| correctness | `wholepack_re` omits `config/`, `deploy/`, `scripts/packs/`, the lockfile | Confirmed correct: the gate task reads none of them |
