# Data Model: Verified source fingerprint on every read

No persisted data changes. The types this mission adds or reshapes:

## LoadedKind (infra.storage, package-private enum in PackFileSet)

| Constant | token | File-kinds glob (classified by `PackFileKind`) |
|---|---|---|
| TACTIC | `tactic` | `<packdir>/**/*.tactic.yaml` |
| DIRECTIVE | `directive` | `<packdir>/**/*.directive.yaml` |
| STYLEGUIDE | `styleguide` | `<packdir>/**/*.styleguide.yaml` |
| PARADIGM | `paradigm` | `<packdir>/**/*.paradigm.yaml` |

Invariant: the store enumerates files only for a `LoadedKind`; the digest covers every
`LoadedKind`. Adding a kind means adding a constant, which digests it.

## Digested file list of one pack (in order)

1. `pack.yaml` (when a regular file)
2. for each `LoadedKind` in declaration order: its files, `String` order of the relative path
3. root `*.graph.yaml` regular files, by name
4. `drg/fragment.yaml` (when present)

Each entry is digested as the label `<configured pack name>/<relative path with '/'>` and the
file's bytes (length-prefixed fields, `Fingerprint.add`). Packs are digested in configured order;
`ProjectConfigSources` then wraps the result with the project config files (unchanged).

## ConsistentRead.Verified<T> (application)

| Field | Type | Meaning |
|---|---|---|
| value | T | what the load answered |
| fingerprint | String | `sha256:` + 64 hex: the digest taken before the load and confirmed after it |

## Refusal outcomes of a verified read

| First digest | Load | Second digest | Outcome |
|---|---|---|---|
| refused | (not run) | (not run) | the first digest's refusal (`pack_unreadable`) |
| d1 | value | d1 | 200 with `Verified(value, d1)` |
| d1 | value | d2 != d1, or unreadable | `stale_input` |
| d1 | refused (e.g. 404) | d1 | the load's refusal |
| d1 | refused | d2 != d1, or unreadable | `stale_input` (the load's refusal kept as the cause) |
| d1 | throws a non-refusal exception | d1 | that exception, unchanged |
| d1 | any | throws a non-refusal exception | that exception propagates (not `stale_input`) |
