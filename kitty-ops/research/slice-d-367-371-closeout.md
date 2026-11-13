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
