---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T19:36:42Z'
reviewer_agent: claude
wp_id: WP06
---

# WP06 review, cycle 1: changes requested (reviewer-renata)

Reviewed commit `7799b09` only. Most of the work is solid and needs no change:
- The six rules and their pinned texts match `contracts/pack-finding-codes.md`.
- The PEP 440 corpus regenerates byte-identical with packaging 26.3. My 35 extra adversarial strings agree with `packaging`.
- `AssetMimeTable` regenerates identically from Python 3.13.16. 50 probe paths agree with `MimeTypes(filenames=()).guess_type`, including `.tgz`, `.json.gz`, `.json.GZ`, `.svgz` and `x.Tgz.gz`.
- The red evidence is stub-based.
- The gate is green.

Four required changes follow. Fix them all in one pass.

## Issue 1 (blocking, production fragility): `Pep440.isValidSpecifierSet` throws `StackOverflowError` on long input

Java's regex engine recurses once per repetition of a quantified group. `(?:\.[0-9]+)*` (release) and `(?:[-_.][a-z0-9]+)*` (local) therefore overflow the stack on a long but well-formed value:

| Input | Python `SpecifierSet` | `Pep440` |
|---|---|---|
| `">=1" + ".1".repeat(1000)` | valid | `true` (correct) |
| `">=1" + ".1".repeat(10000)` (20 KB) | valid | `java.lang.StackOverflowError` |
| `"==1.0+a" + ".a".repeat(10000)` | valid | `java.lang.StackOverflowError` |

`requires_core` has no `maxLength` in `pack-descriptor.schema.json`, and `PackPolicy.load` runs `judge`. Once WP07 fills `requiresCore`, one pack.yaml can make `repository.load` throw an `Error` instead of a `CharterRefusal`. The governance tactic `secure-regex-catastrophic-backtracking` applies here.

**Fix:**
- Make the match stack-safe for any input length. Two options:
  - Pre-validate the release and local segments with a non-recursive scan, or with a single character class such as `[0-9]+(?:\.[0-9]+)*` rewritten as `[0-9][0-9.]*` plus a check that no segment is empty.
  - Tokenise the clause by hand.
- Do **not** fix it with an arbitrary length cap that returns `false`. That would diverge from `packaging`, which accepts these values.
- Add a regression test with a very long valid and a very long invalid input, for both the release and the local part, asserting the `packaging` verdict. Also add one such row to the corpus generator. Python decides it fine.

## Issue 2 (blocking, parity): `asset_path_escape` misses the Windows forms that core refuses

Core's `resolve_relative_path_within_root` (`org_pack_config.py:156-167`) checks both `PurePosixPath` and `PureWindowsPath`, both for `is_absolute()` and for `..` in `.parts`. I verified with Python 3.13 that core flags each of these:

| Path | Core | `PackPolicy` today |
|---|---|---|
| `C:\x.png`, `C:/x.png` | absolute | not absolute |
| `\\srv\share\x.png` | absolute | not absolute |
| `a\..\b.png` | `..` component | no `..` (split on `/` only) |

On Linux, the adapter's `containedInAssetsRoot` (WP08) cannot catch these, because `\` and `C:` are ordinary filename characters there. The contract's core mapping says `asset_path_escape` → "same / blocking". The only divergence it records is the MIME table (P8).

**Fix:**
- Mirror core:
  - absolute = POSIX absolute (leading `/`), or Windows absolute (a drive letter followed by `\` or `/`, or a UNC `\\host\share` or `//host/share` prefix);
  - `..` = a `..` segment after splitting on both `/` and `\`.
- Note what is *not* absolute in core: `C:x.png` and `\x.png`. Keep those passing.
- Add parameterized cases for every row above, and for the two non-absolute forms.

## Issue 3 (blocking, parity): `duplicate_artifact_id` picks the owner by string order, but core uses `Path` order

Core walks `sorted(directory.rglob(glob))` for `styleguides` and `assets` (`pack_validator.py:291-295`). Python orders `Path` objects part by part, so a directory sorts before a sibling that extends its name. `PackPolicy.artifactRules` sorts with `Comparator.comparing(ArtifactFacts::file)`, which is plain `String` order. `'-'` and `'.'` sort below `'/'`, so the two orders disagree:

| Files with the same id | Core owner | `PackPolicy` owner |
|---|---|---|
| `styleguides/python/x.yaml`, `styleguides/python-legacy/y.yaml` | `python/x.yaml` | `python-legacy/y.yaml` |
| `styleguides/a.yaml`, `styleguides/a/b.yaml` | `a/b.yaml` | `a.yaml` |

So the finding lands on the other file and names the other `<first-file>`. #115 compares on `(file, code, severity)`, so this shows up as a parity difference.

**Fix:**
- Order by the `/`-separated segments, compared segment by segment, with a shorter prefix first. That is Python's `PurePosixPath` order on Linux.
- Add a test for each row above.
- Consider using the same comparator for the group-5 "path order" in `PRECEDENCE`, so that "path order" means one thing in this class. That part is optional.

## Issue 4 (DoD, NFR-003): the survivor rationales and the FR-009 note are not recorded anywhere

The DoD asks for every surviving mutant to be listed, with a rationale, in the WP evidence. The prompt also says to record that FR-009's "URN prefix equals kind" is fully expressed by the schema (R4, WP02 C4).

Neither appears anywhere durable:
- The commit message is one line.
- The for_review note says only "PIT 97% (264 mutants)", and "757 rows" there is off (the corpus has 752).
- `tasks/WP06-validate-mode-domain-rules/` is empty.

PIT on the current tree has these 7 survivors and 2 no-coverage mutants:
- `comparePlaces:776`
- `group:270`
- `group:275`
- `isIndex:789`
- `schemaFindings` lambda `:574`
- `PRECEDENCE` lambda `:65`
- `Pep440.isValidSpecifierSet:62` (boundary `<=` to `<`: equivalent, because the empty last clause is valid anyway)
- no coverage: the `detail` lambdas `:674` and `:677`

**Fix:**
- Record each survivor with its equivalence or acceptability rationale where the next reviewer and the mission review can find it (the commit body, or the for_review evidence or note).
- Record the FR-009 / R4 note in the same place.
- Re-run PIT after issues 1–3 and update the numbers.

## Non-blocking notes (no change required in WP06)

- **Python quirks in `Pep440`.** Copying `packaging`'s quirks (Unicode folding for `~=`, Python `isspace`) is right, not over-fitting. #115 parity is decided by `packaging`, and the corpus pins the behaviour. Keep the corpus regeneration command in the header, as it is now.
- **`duplicate_step_id`.** One finding per repeated id, at its second occurrence, is a reasonable refinement of core's single per-contract error. #115 must de-duplicate the charter side by `(file, code)` as well, or the counts differ. Note this for WP10 / #115.
- **Duplicate asset ids.** Core runs the asset checks only on the *first* manifest of an id (`setdefault`). A later manifest that repeats the id gets `duplicate_id` only. `assetRules` judges every `AssetFacts`. Either WP08 passes only first-owner and schema-valid asset facts, or the domain skips assets whose `(assets, id)` is already owned. Decide in WP08.
- **DIV-016.** The fixed table differs from this host's platform table on real extensions. Examples: `.xml` is `text/xml` (fixed) against `application/xml` (Debian). `.yaml`, `.yml`, `.ttf` and `.woff2` are unknown in the fixed table but typed on Debian. This is already recorded as DIV-016. WP09 may want to cite `.xml` as the concrete example.
- **Within-file order of unknown keys.** `pack_unknown_key` sorts by `String`. `PRECEDENCE` then re-sorts unknown keys by the same `String` order, except all-digit keys, which it compares numerically. This is harmless.
- **`PackFindingTest` edit.** The edit to this test outside the owned files is minimal and necessary, because the new enum constant breaks the old `containsExactly`. It is accepted. `pit-targets.init.gradle` is in `owned_files` and is appropriate.
