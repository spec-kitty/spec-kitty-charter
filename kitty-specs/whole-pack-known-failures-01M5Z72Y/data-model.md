# Data Model: Whole-pack gate known-failure list

## `whole-pack/known-failures.yaml`

```yaml
# header comment: what the list is for and the workflow (ADR-0036 decision 5)
known-failures: []
```

An entry (when one exists):

```yaml
known-failures:
  - path: directives/example.directive.yaml          # pack-relative, as in the census
    at: "refused: example.yaml is a multi-document file"  # every difference path as the report shows it; for a refusal, the whole red detail
    reason: The pack file holds two YAML documents.
    issue: spec-kitty/spec-kitty-open-packs#123
```

Load rules (each a load failure naming the entry): only the key `known-failures`; entries are maps with exactly
`path`, `at`, `reason`, `issue`, all non-blank text; `issue` matches `^spec-kitty/spec-kitty-open-packs#[1-9]\d*$`;
a path appears once. A missing resource fails `fromClasspath()`.

## `KnownFailure` (record)

| Field | Meaning |
|-------|---------|
| `path` | pack-relative path |
| `at` | expected difference paths, joined by ` ; ` (`atOf(red)`), or the whole red detail when the file is refused |
| `reason` | one line |
| `issue` | the open-packs issue |

## `Status.KNOWN_FAILURE`

Appended after `RED`. Label `known failure`. A file gets it when it is listed, of a modelled kind, not excluded, red,
and its red detail matches the entry (`atOf(red).equals(at)`, or `red.equals(at)` when `atOf(red)` is `refused`).
Detail: `<red> — known failure: <reason> (<issue>)`.

## Judgement (per census file)

| Situation | Status | Problem |
|-----------|--------|---------|
| listed, red, matches `at` | `KNOWN_FAILURE` | none |
| listed, red, differences changed | `RED` | `CHANGED_RED <path> (<kind>): expected the first difference at '<at>', now: <red>` |
| listed, green | `GREEN` | `UNEXPECTEDLY_GREEN <path>: it round-trips now; remove it from whole-pack/known-failures.yaml` |
| not listed anywhere, red | `RED` | `UNEXPECTED_RED …` (unchanged) |

## Whole-list checks

| Situation | Problem |
|-----------|---------|
| entry path not in the census | `STALE_KNOWN_FAILURE <path>: no such file; remove it from whole-pack/known-failures.yaml` |
| entry path excluded | `STALE_KNOWN_FAILURE <path>: the file is excluded; remove it from whole-pack/known-failures.yaml` |
| entry path of an unmodelled kind | `KNOWN_FAILURE_UNMODELLED <path> (<kind>): no adapter models this kind, so the entry can never expire` |
| path also in `expected-red.yaml` `files:` | `AMBIGUOUS_ENTRY <path>: listed in both whole-pack/expected-red.yaml and whole-pack/known-failures.yaml` |

An ambiguous path is judged by neither list (status `RED`, detail = red detail) so it cannot pass by accident.

## Report

- Totals line: `- Files: N, green a, expected red b, excluded c, red d, known failure e` (loops over `Status.values()`).
- Per-kind table header: `| kind | files | green | expected red | excluded | red | known failure |`.
