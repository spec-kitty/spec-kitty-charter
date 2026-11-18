# open-packs#TBD-unmodelled-not-held: a file no rule models is warned about, then ignored

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `pack-closure-rulesets-01M7FA90` (WP02).
> The orchestrator files it and replaces `TBD-unmodelled-not-held` with the issue number.

## Title

Follow charter #554 (ruling Q1): an unmodelled file is a `file_not_held` warning, not an `unmodelled` refusal

## Why

The charter operator ruled (#554, Q1): "warning. we can expect pack authors to add docs and reference info to their
pack that doesn't impact our service (warn, then ignore. do not roundtrip these)".

From charter #554 WP02 on, the charter service answers a file that no `file-kinds.yaml` rule takes (no `files`,
`exclusions`, `retired`, `payloads` or `ignored` rule, and no companion claim) like an excluded file:

- the registration accepts the pack, does not hold the file, and reports one `file_not_held` diagnostic (severity
  warning) whose detail is `<file>: not held (no pack file kind models this file)`;
- the stored pack and its revision are those of the pack without the file, and the round trip never sees it;
- the violation code `unmodelled` is no longer answered. A retired file is still refused as `retired`, and an orphan
  prompt fragment as `prompt_fragment_invalid`;
- with that fault gone, a pack whose only other fault was an unmodelled file is now judged on its manifest
  (`manifest_membership`).

The classifier outcome `unmodelled` in `contracts/packs/conformance/classifier-cases.yaml` does not change: it is a
classification, not a verdict. Only its wording changes ("warned as `file_not_held` and not held").

Charter #554 WP11 settles open case 6 in the same packs-contract version. `file-kinds.yaml` narrows the
`graph-shard` rule from `**/*.graph.yaml` to `*/*.graph.yaml` (a shard at the pack root) and removes the
`**/charter.yaml` rule. A shard below the root (`drg/<kind>.graph.yaml`, `graph/a.graph.yaml`) and a `charter.yaml`
anywhere in a pack are therefore `unmodelled`: warned as `file_not_held` with `no pack file kind models this file`, not
held, and not judged. A `*.graph.yaml` under `assets/` or `templates/` is now that place's payload. The classifier
cases `core/graph/a.graph.yaml` and `project/charter.yaml` move from `mapped` to `unmodelled`, `core/a.graph.yaml` is
the `graph-shard` case, and `core/drg/tactic.graph.yaml` (unmodelled) and `core/assets/a.graph.yaml` (asset-payload)
are added. `schemas/charter.schema.json` stays as the project charter's schema, outside any pack.

## What

Check each item against the open-packs pin first; the charter session had no open-packs checkout.

### `scripts/ci/validate_pack_schemas.py`

1. The verdict for a file `classify()` answers `unmodelled` for: report it as a `file_not_held` warning naming the
   file and `no pack file kind models this file`, never as an error, and do not fail the run on it.
2. Do not read, digest or validate the file further; it is ignored after the warning.
3. Keep `retired` and the orphan-fragment check as errors with their own codes.

### `tests/ci/test_validate_pack_schemas.py` and the expected-code fixtures

1. Every fixture or test that expects the code `unmodelled` for a file nothing models changes to expect one
   `file_not_held` warning at that file, and a passing verdict when the file was the only fault.
2. A fixture that used an unmodelled file as a convenient second fault is rewritten on a fault that still refuses
   (a retired `PROVENANCE.md`, as the charter's scenarios now do), so its count of errors stays as it was.
3. New: a pack plus `notes.txt` passes with exactly one `file_not_held` warning naming `notes.txt`.

### Nested shards and charter.yaml

1. `classify()` follows the vendored table: a nested `*.graph.yaml` and any `charter.yaml` are `unmodelled`, so they
   get one `file_not_held` warning and are not validated against `graph-shard` or `charter`.
2. Fixtures that expect `drg_schema_error` or `artifact_schema_error` at a nested shard or a `charter.yaml` expect one
   `file_not_held` warning at that file instead. A fixture that needs a graph fault moves its shard to the pack root.

### Docs

1. Wherever the docs say a file no rule models "is refused as `unmodelled`", say it is warned as `file_not_held` and
   not held.

### Pin

Move the charter pin to the commit that carries #554 WP02, so the vendored `file-kinds.yaml` wording, the
`DiagnosticCode` text and the classifier-case header match.

## Acceptance

- A pack with an extra `notes.txt`, a `LICENSE` or a spreadsheet passes `validate_pack_schemas.py` with one
  `file_not_held` warning per such file.
- No test or fixture expects `unmodelled` as an error code.
- A pack with `drg/tactic.graph.yaml` or `charter.yaml`, however invalid, passes with one `file_not_held` warning
  naming that file.
- The open-packs drift gate (charter `OpenPacksDriftTest`) still names every unmodelled file of a pack, so the pack
  set stays free of them.

## The other expected-code changes of charter #554 (WP10, the final list)

Q1 and open case 6 are above. Charter #554 also changes what validate mode and the registration answer under R2 (one
fault, one code and one wording on every surface; the specific code wins over a catch-all), S2 (the registration gains
the validate-only consistency and format checks) and S8 (the registration answers every fault of a pack with no
readable `pack.yaml`). Each row is the charter's R2 table row (`docs/development/notes/pack-code-unification.md`) and
its `### Breaking` fragment. Change every fixture that expects the old answer.

### Validate mode (fragments `2026-11-16-validate-mode-speaks-the-wire-codes.md` and `2026-11-17-validate-mode-judges-the-one-read.md`)

| R2 row | Fault | Was | Now |
| --- | --- | --- | --- |
| 1 | Artifact not YAML or not UTF-8 | `artifact_schema_error` | `unreadable`, `<file>: <why>` |
| 11 | Retired file | `artifact_schema_error` | `retired`, `<file>: <why>` |
| 12 | Asset sidecar's payload missing | `artifact_schema_error` | `asset` |
| 13 | Asset path escapes `assets/` | two findings, `artifact_schema_error` and `asset_path_escape` | one `asset` |
| 19 | A companion that does not parse, is not UTF-8 or breaks its kind's rule | `artifact_schema_error` | `companion` (`prompt_fragment_invalid` for a prompt fragment) |
| 21 | Filled slot under an unversioned descriptor | warning `prompt_slot_not_empty_unversioned` | warning `prompt_slot_not_empty` |
| 24 | No `pack.yaml` | not reported | `pack_unreadable` at `pack.yaml`, `pack <name> has no pack.yaml` (charter DIV-028: core reports nothing) |
| 28 | Unstamped `registered.graph.yaml` | `drg_schema_error` | `reserved_shard`, in the registration's words |
| 29, 30 | Manifest membership; a manifest that is not a regular file | not judged | `manifest_membership` (only when nothing else failed); `unreadable` |
| 32 | The policy file `mutability_policy` names cannot be read | not judged | `unreadable`, `<file>: named by mutability_policy, ...` |
| 34 | An excluded file | listed, no finding | `file_not_held` warning, as the registration answers it |
| all | Severity of a violation | `blocking` | `violation` |

Every validate-mode detail loses its `<pack>/` prefix: `<file>: <reason>`, as the registration words it.

### Registration (fragments `2026-11-16-registration-names-the-fault.md` and `2026-11-17-registration-answers-every-fault.md`)

| R2 row | Fault | Was | Now |
| --- | --- | --- | --- |
| 2, 3, 31 | Schema breach of an artifact or the manifest | `schema` | `artifact_schema_error`, `<file>: '<key>' does not match its pattern` (and so on) |
| 4 | Graph document breaks its schema | `drg_schema_error`, `pattern violated at ...` | `drg_schema_error`, `<file>: edge endpoint 'X' is not a URN` (and so on) |
| 10, 16 | Two files (or templates) of one kind declare one id | aggregate `validation_failed` at `pack`; `template` | `duplicate_artifact_id` at the second file |
| 17 | Value-object breach | `domain` | `artifact_schema_error` at the file |
| 18 | Unresolved template or reference | `reference` | `artifact_schema_error` |
| 19 | A companion that cannot be read | at the artifact, `<artifact>: <companion>: <why>` | at the companion file, `<companion>: <why>` |
| 22, 23 | `pack.yaml` breaks its schema or is not YAML | refusal at pointer `pack` | `pack_unreadable` at pointer `pack.yaml`, validate mode's wording |
| 22 to 24 (S8) | No readable `pack.yaml` | the one refusal `The pack cannot be registered: <fault>` | every fault: the graph faults, then the descriptor's, then the rest, under `N fault(s), the first at <file>.` |
| 27 | A kind token that is not snake case | `invalid` | the kind's own code |

### The registration gains (S2, fragment `2026-11-16-registration-judges-content-and-format.md`)

| R2 row | Fault | Was | Now |
| --- | --- | --- | --- |
| 14 | Asset mime malformed or not matching the extension | accepted | `asset_mime_invalid` |
| 15 | Two asset sidecars declare one id | each sidecar's own fault only | `duplicate_artifact_id` at the second sidecar |
| 38 | `requires_core`, `pack_version` or a `depends_on` range not PEP 440 | accepted | `requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid` |

The configured level (`pack_level_missing`, `pack_level_mismatch`) and the org fragment (`org_fragment_missing`) stay
validate-only.

### Acceptance, for these

- No fixture expects `schema`, `domain`, `reference`, `invalid`, `template`, `unmodelled`, `asset_path_escape` or
  `prompt_slot_not_empty_unversioned`, nor `blocking` as a severity.
- A fixture of a pack with no `pack.yaml` expects `pack_unreadable` at `pack.yaml` from validate mode.
