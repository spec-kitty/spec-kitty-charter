# WP05 kernel survivor triage

The triage of every `kernel.domain` mutant that survived `:kernel:pitest` (#239, ADR-0062 decision 7). It records each
verdict, so a later run can tell a known equivalent from a new gap. Main code is unchanged; no test method was added.
The new cases are rows in existing parameterised tables (`Pep440RangeTest`) or assertions in existing tests
(`PackQualifiedReferenceTest`).

## Before and after

`:kernel:pitest`, whole module:

| | Detected / mutants | Score | Test strength | Survived |
|---|--:|--:|--:|---|
| Before | 274 / 378 | 72 % | 91 % | 28 (25 `kernel.domain`, 3 `kernel.validation`) |
| After | 289 / 378 | 76 % | 95 % | 14 (11 `kernel.domain` equivalents, 3 `kernel.validation`) |

The three `kernel.validation` survivors are outside this triage: the test gaps of
[#384](https://github.com/spec-kitty/spec-kitty-charter/pull/384) cover them.

**Equivalent mutants still count as SURVIVED in PIT's score.** Stock PIT, as configured here, cannot mark a mutant
equivalent: the `EQUIVALENT` status comes only from PIT extensions. So the 11 equivalents below stay in the denominator of the kernel score, and in the survivor count of every run. This
file is where their verdicts are recorded.

## Verdicts

Classes are `Pep440Range` unless named; line numbers are those of the triaged code.

- **killed**: killed by a strengthened existing test.
- **equivalent (analytical)**: no input can tell the mutant from the original, for the reason given.
- **equivalent (empirical)**: equivalent (empirical: differential fuzz, about 2.1 M and 4.38 M range pairs; no
  distinguishing input found). This is evidence, not a proof; see
  [#386](https://github.com/spec-kitty/spec-kitty-charter/issues/386), the `Pep440Range.candidates` defect that the
  same triage found.

| Location | Mutant | Verdict | Killed by, or reason |
|---|---|---|---|
| `admitsOne:154` | return false | killed | `aRangeAdmitsOrRefusesAVersion` row `===1.0, 1.0, true` |
| `admitsOne:154` | return true | killed | `aRangeAdmitsOrRefusesAVersion` row `===1.0, 1.0.0, false` |
| `candidates:197` | empty list | killed | `twoRangesOverlap...` row `===1.0\|>=1\|true` |
| `candidates:231` | drop the next-dev candidate | killed | `twoRangesOverlap...` row `>1.0.dev0\|<1.0a1\|true` |
| `canonical:265` | negated epoch check | killed | `twoRangesOverlap...` row `>=1!1\|<1!2\|true` |
| `canonical:272` | drop the post segment | killed | `twoRangesOverlap...` rows `==1.0.post0\|>=1.0\|true`, `>1.0.post0\|<1.0.post2\|true` |
| `canonical:273` | drop the dev segment | killed | `twoRangesOverlap...` row `==1.0.dev1\|<=1.0\|true` |
| `compare:312` | pre number returns 0 | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0a1, 1.0a2, false` |
| `compare:323` | negated left dev check | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0.dev1, 1.0.dev2, false` |
| `compare:323` | negated right dev check | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0.dev1, 1.0.dev2, false` |
| `compare:324` | dev presence returns 0 | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0a1, 1.0a1.dev1, false` |
| `compare:326` | dev number returns 0 | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0.dev1, 1.0.dev2, false` |
| `Parsed.number:406` | negated null check | killed | `aRangeAdmitsOrRefusesAVersion` row `==1.0a1, 1.0a2, false` |
| `PackQualifiedReference.isPackName:55` | return true | killed | `aPackNameOutsideTheGrammarIsRefused` asserts `isPackName("Writing")` is false |
| `candidates:228` | post-bump lambda returns null (newly covered by the post rows, then survived) | killed | `twoRangesOverlap...` row `>1.0.post0\|<1.0.post2\|true` |
| `candidates:200` | depth lambda returns 0 | equivalent (empirical) | as for line 202: the padding depth does not change which cells are witnessed |
| `candidates:202` | depth `+1` becomes `-1` | equivalent (empirical) | the padded candidate is redundant: the operand's `post0` and the next operand's `.dev0` candidates witness the same cells |
| `candidates:209` | negated epoch check on `N!0.dev0` | equivalent (empirical) | a set confined to epoch N needs a lower operand in epoch N, whose own candidates witness that region |
| `candidates:214` | padding boundary `<` becomes `<=` | equivalent (empirical) | one deeper padding names a version in the same cell |
| `candidates:214` | padding loop negated (no padding) | equivalent (empirical) | `X.1` lies in the same cell as `X.0..0.1`; other candidates cover any operand between |
| `candidates:221` | drop the next-prerelease candidate | equivalent (empirical) | the operand's prerelease `.post0` candidate lies in the same cell, just past the prerelease |
| `compareRelease:331` | `i < size` becomes `i <= size` | equivalent (analytical) | the extra iteration compares ZERO with ZERO and continues |
| `Parsed.parse:381` | `from <= length` becomes `<` | equivalent (analytical) | the dotted release never ends in `.`, so `from` never equals `length` |
| `Parsed.parse:383` | `dot < 0` becomes `dot <= 0` | equivalent (analytical) | the release starts with a digit, so `indexOf('.')` from a segment start is never 0 |
| `Pep440.parse:91` | `start <= length` becomes `<` | equivalent (analytical) | at `start == length` the clause is empty and skipped, so both forms return the same clauses |
| `Urn.parseBare:48` | `colon <= 0` becomes `< 0` | equivalent (analytical) | a leading `:` leaves an empty kind token, which `NodeKind.fromToken` already refuses |

The rows that kill `canonical:272` and `canonical:273` were first spelled `==1.0.post0|>1.0` and `==1.0.dev1|<1.0`.
Those relied on the class's documented deviation from PEP 440's exclusive-comparison rule, where `packaging` finds no
overlap, so they were respelled with the inclusive operators `>=1.0` and `<=1.0`, which overlap under both and still
kill the two mutants.

## Amendment, 2026-11-05: line numbers after #368

PR #368 (merged before this PR) moved the body of `Pep440Range.candidates`'s per-operand loop, unchanged, into a new
method `addCandidateTexts` (commit `be7c17d1`, an extract-method refactor to meet the complexity limit). The verdicts
above still hold: the same code yields the same 11 survivors in a full run on the rebased branch (kernel 290 of 379
detected, 14 survived: 11 `kernel.domain`, 3 `kernel.validation`). Only the locations moved:

| Above | After #368 |
|---|---|
| `candidates:200`, `candidates:202` | `candidates:200`, `candidates:202` (unchanged) |
| `candidates:209` (negated epoch check) | `addCandidateTexts:224` |
| `candidates:214` (boundary and negated) | `addCandidateTexts:229` (boundary and negated) |
| `candidates:221` (next-prerelease candidate) | `addCandidateTexts:236` |
| `compareRelease:331` | `compareRelease:339` |
| `Parsed.parse:381`, `Parsed.parse:383` | `Parsed.parse:389`, `Parsed.parse:391` |
| `Pep440.parse:91`, `Urn.parseBare:48` | unchanged |
