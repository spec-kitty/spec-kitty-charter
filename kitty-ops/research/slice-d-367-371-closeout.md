# Slice D close-out: #367's last methods and the #371 gate — grounding note

Op `01M7B88K7A7RQWJFECBFT4FFPT` (java-jenny, tidy-first). Written 2026-11-13 against `main` at
`cdc62e1c`, on branch `ops/slice-d-367-371-closeout`.

## Sources read

- #367, its body and both comments (2026-11-12 08:21 re-measure at `fd20faa`; 2026-11-12 19:54 after
  #541: five methods left in slice C files).
- #371: the four modules and limits, no grandfathering, `suppressions.xml` gains nothing, the build
  guide lists the rules.
- #468: slice D; #367 and #371 are its two open code items.
- ADR-0058, `.kittify/charter/charter.md`, `docs/development/issue-tracker.md`.

## The gate branch and the rebase

`ops/complexity-gate-371-activation` held 7 commits on `fd20faac`, 211 behind `main`:

| Commit | Content | On `main`? |
|---|---|---|
| `6ebe5d94` | Op record `01M77A79` | yes (`kitty-ops/01M77A794666NZ43REZRYMFJ17.jsonl`) |
| `942d5f09` | `AddressPolicy` IPv4 table | yes, as `999c2eea` (#525) |
| `12b8cd8e` | release gate split | yes, as `d7754fee` |
| `634fdaa8` | `MarkdownScanner` heading | yes, as `a2d4f63c` |
| `19ab1fe6` | packs test splits | yes, as `a2ec750a` |
| `9e0f6b6f` | kernel/missions/profiles/activations test splits | yes, as `c38368d3` |
| `797fb10e` | **the activation**: four modules in `checkstyle.xml`, ADR status, build guide, changelog | no |

A copy was rebased onto `main` as this branch (the existing branch is not force-pushed). Git dropped
the six applied commits; the activation applied cleanly, as `c699b4b0`, reworded to drop the
out-of-date "waits on slices B and C" note.

## Measurement on this branch (gate on, every hand-written source set)

All 31 Checkstyle tasks, `--continue`, `--max-workers=1`. Only Checkstyle tasks failed (7 of them).
**8 violations in 8 methods**:

| # | File | Method | Measured | In #367? |
|---|---|---|---|---|
| 1 | `kernel/.../RefusalCode.java:270` | `notFound` | CC 17 | yes |
| 2 | `packs/.../PackPolicy.java:1078` | `detail` | CC 25 | yes |
| 3 | `web-transport/.../RefusalHandler.java:350` | `statusOf(RefusalCode)` | 66 lines | yes |
| 4 | `deployable/.../ReservedRefusalCodesTest.java:289` | `NamesEveryCode.codes` | 67 lines | yes |
| 5 | `packs/.../PackPolicyStepEnhancementTest.java:47` | `failing` | 127 lines | yes |
| 6 | `activations/.../ActivationWireTokenParityTest.java:65` | `pairs` | 62 lines | **no** |
| 7 | `activations/.../EffectiveReadMapperTest.java:723` | `theContextIsMappedWithItsArtifactsSuggestionEnhancementsAndSkippedReport` | 66 lines | **no** |
| 8 | `artifacts-edge/.../EffectiveSkillReadsTest.java:37` | `theItemMapsTheKernelArtifactTheActivationReadThroughThePacksPort` | 96 lines | **no** |

Rows 6 to 8 entered `main` on 2026-11-13 (#439, #532 and the effective-step mission squash), after
#541 measured `17ce3f9`. They are outside #367's list, so they are reported to the operator before
any change (no silent widening).

## Similar surfaces

- The same over-limit shapes, by construction: the gate measures every hand-written source set, so the
  table above *is* the sweep. No `suppressions.xml` entry exists for any of the four rules.
- Open PRs: #565 edits `docs/guides/service-build.md` (a different section from the gate's); #567
  (slice F) is spec-only today, but its implementation will meet the gate once it lands.

## Tracked issues

| Issue | Relation | Disposition |
|---|---|---|
| #367 | the methods | Closes with this PR for the methods; its class-split criterion (`FilesystemPackStore`, `PackPolicy`) is not in this Op's brief and goes to the operator |
| #371 | the gate | Closes with this PR |
| #468 | parent epic | Tick #367 and #371 |
| — | open issues on checkstyle/complexity | none besides the three above |

## Refactor plan (behaviour-preserving, DIRECTIVE_025)

Move code first, then adjust callers; no logic edits while moving; tests unchanged.

1. `RefusalCode.notFound`: the `||` chain becomes an `EnumSet` membership test, the same 17 codes.
2. `PackPolicy.detail`: each `case` arm that holds logic moves into its own method beside
   `typeDetail`, `patternDetail` and `enumDetail`.
3. `RefusalHandler.statusOf(RefusalCode)`: the exhaustive switch is split by status family. The
   compiler's exhaustiveness check over every code must survive; see the operator question.
4. `ReservedRefusalCodesTest.NamesEveryCode.codes`: the array splits into two methods of the same
   probe class, so ArchUnit still sees every field access from `NamesEveryCode`.
5. `PackPolicyStepEnhancementTest.failing`: the long arms move into one fixture method each.

## Operator rulings (2026-11-14)

1. The three methods outside #367's list (rows 6 to 8) are in scope: refactored here.
2. `RefusalHandler.statusOf`: a table. The families are `EnumSet`s folded into an `EnumMap` in a nested
   `StatusByCode` class, checked at class load for a missing or doubled code. It is nested so that
   `ReservedRefusalCodesTest` keeps leaving the mapping out of its "raised codes are named" check, as it
   left out the switch. In bytecode, the outer class still names 16 codes, all from code this change does
   not touch, and the nested class names all 63.
3. #367's class-split criterion: a research squad corroborates or refutes the recommendation, below.

## Research squad on #367's class-split criterion (ad-hoc decision point-cut)

Question: may #367 close with this PR, recording why `FilesystemPackStore` and `PackPolicy` are not split
here? Three profile-loaded, read-only delegates, run in parallel:

- architect-alphonso (opus);
- planner-priti (sonnet);
- researcher-robbie (sonnet).

All three returned **AMEND**. Synthesis, adjudicated from the source:

- **The split is already decided.** ADR-0075 is Accepted. Its decision 3 moves `PackPolicy`'s rules,
  ordering and precedence into the new `pack-validation` module, as one ruleset per file kind. It keeps
  the `packs` Spring beans as thin adapters over the module's load. Its Negative section lists
  `FilesystemPackStore` as touched. #554 (open, Infra & Enablers) delivers it. So the recommendation's
  "a separate design note" is wrong: the design note exists (architect, HIGH; verified at ADR-0075
  decision 3, and "Large, refactor-heavy migration" under Negative).
- **Slice F explains only part of it.** Slice F's plan edits `PackPolicy` (`endpoints`, `violationKind`,
  `PackContents.MissionTypeFacts`). It names `FilesystemPackStore` only as the existing store, with no
  code change (researcher, HIGH; architect, MEDIUM). So `FilesystemPackStore`'s reason is ADR-0075 and
  #554, not slice F.
- **Sizes grew.** Both classes are larger than #367's 2026-11-04 table:
  - `FilesystemPackStore`: 1,201 → 1,379 non-blank lines;
  - `PackPolicy`: 930 → 1,215 non-blank lines.

  The record states the current numbers (researcher, HIGH).
- **Criterion wording** (planner, HIGH). "The reason to keep them whole" does not literally fit a
  split delivered elsewhere. The closing comment on #367 says the criterion is answered by ADR-0075
  decision 3 and #554, and the operator is told. No edit to the issue's history is needed.
- **No new issue** (all three). The split is tracked by #554 under ADR-0075. A size split is not a
  correctness, data or security risk (issue-tracker Rule 1).

| Finding | Disposition |
|---|---|
| Architect HIGH: ADR-0075 / #554 already decide the split | Accepted: the reason cites them, with no new note |
| Architect MEDIUM / researcher HIGH: slice F touches `PackPolicy`, not the store's code | Accepted: a separate reason for each class |
| Researcher HIGH: sizes grew | Accepted: current sizes in the closing comment |
| Researcher MEDIUM: seams exist (store read/write/IO; policy rules/graph/wording/ordering) | Deferred to #554, which moves those regions |
| Planner HIGH: criterion wording vs a deferral | Changed: the closing comment says the criterion is answered by ADR-0075 and #554 |
| Planner HIGH: close only after the ruling | Accepted: the operator asked for this squad, and the PR says `Closes #367` |
| Planner MEDIUM: no plan page names the split | Accepted: #554 is the tracker; no plan-page line |
| Planner/architect: file no new issue | Accepted |
| Planner LOW: tick #468 | Accepted at closeout |
