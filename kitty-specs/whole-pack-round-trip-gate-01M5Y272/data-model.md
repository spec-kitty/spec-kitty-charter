# Data Model: Whole-pack round-trip gate

All types are test-scope, package `ai.speckitty.charter.packs.infra.storage`.

## Gate kind

String. The schema name from `file-kinds.yaml` for a mapped file, else `other:<top-level dir>` / `other:<root
file>`. Modelled gate kinds today: `directive`, `tactic`, `styleguide`, `toolguide`, `paradigm`, `pack-descriptor`,
`graph-shard`.

## CensusEntry

| Field | Type | Rule |
|---|---|---|
| path | String | pack-relative, `/`-separated; unique |
| kind | String | gate kind |

## Outcome (from the seam)

`Written(String writtenPath)` | `Refused(String reason)`. Only for files of a modelled gate kind.

## FileResult

| Field | Type | Rule |
|---|---|---|
| path | String | from the census |
| kind | String | from the census |
| status | `GREEN` \| `RED` \| `EXPECTED_RED` \| `EXCLUDED` | exactly one per file |
| detail | String | first difference, refusal reason, baseline reason + issue, or exclusion reason; empty for green |

State rules: a modelled file is GREEN when written, read back and meaning-equal, else RED; then the expectation
re-labels a RED that is baselined as EXPECTED_RED. An unmodelled file is RED ("no adapter") unless baselined by kind
or excluded. EXCLUDED takes precedence only for files of unmodelled kinds (an exclusion over a modelled kind is an
error).

## Difference

| Field | Type |
|---|---|
| path | String (e.g. `steps[2].title`, `edges{source=…,target=…,relation=…}`) |
| source | rendered source value or `<absent>` |
| written | rendered written value or `<absent>` |

## Expectation (`expected-red.yaml`)

```yaml
kinds:            # a whole gate kind with no adapter yet
  - kind: other:missions
    reason: Mission types, steps and actions are not modelled.
    issue: "#194"
files:            # a single file of a modelled kind that is known to differ
  - path: tactics/moscow-scoping-lens.tactic.yaml
    reason: The writer leaves out the explicit empty list applies_to_languages.
    issue: "#187"
```

## Exclusions (`exclusions.yaml`)

```yaml
exclusions:
  - match: "**/README.md"
    reason: Human orientation notes for pack authors; not pack content.
```

## Ratchet problems (fail the gate)

`UNEXPECTED_RED(path)`, `UNEXPECTED_GREEN(entry)`, `STALE_BASELINE_PATH(path)`, `STALE_BASELINE_KIND(kind)`,
`STALE_EXCLUSION(match)`, `EXCLUDED_MODELLED(path)`, `EMPTY_MODELLED_KIND(kind)`.
