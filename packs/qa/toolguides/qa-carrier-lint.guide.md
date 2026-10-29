# QA Traceability Carrier Lint (Coverage Validator)

## Overview

`qa_carrier_lint.py` validates a `qa-traceability/v1` carrier (`qa-traceability.yaml`) and
computes `qa-coverage.json`, this pack's own machine-readable coverage record. This guide
documents the script's invocation contract only: how to resolve it, its flag surface, its exit
codes, and the shape of the record it writes. It does not restate the carrier schema itself
(`qa-traceability.yaml`'s own `requirements[]`/`cases[]`/`defects[]` shape) — that lives in this
mission's own `data-model.md`, Section A.

## Setup

Two different ids are in play here and they are not interchangeable:

- **This toolguide's own id is `qa-carrier-lint`.** It is not a resolvable asset id — it names
  this toolguide document, nothing else.
- **The script ships as an asset sidecar whose own id is the different string
  `qa-traceability-lint`.** Resolve the script's real filesystem path with:

  ```
  spec-kitty charter pack asset path qa-traceability-lint
  ```

  Never pass this toolguide's own id (`qa-carrier-lint`) to `asset path` — it will not resolve,
  because it is not an asset id.

**Anchor asymmetry, so a copied path does not silently break.** The sidecar's own manifest
`path:` value is `qa_carrier_lint.py`, with no `assets/` prefix. Org-tier asset blobs (this
pack's own tier) anchor *at* the pack's `assets/` directory, while only *built-in* blobs anchor
one level above their own `assets/` directory and therefore carry a leading `assets/` (or
equivalent) segment in their own `path:` value (`doctrine/assets/repository.py::_anchor_for`, its
own documented "Anchor asymmetry (A-2)"). Copying a built-in sidecar's `path:` shape verbatim
into an org-tier sidecar — i.e. prefixing it with `assets/` — resolves to a location one level too
deep and does not exist. `spec-kitty charter pack asset path qa-traceability-lint` is the resolution
path a caller should use regardless of this detail; the detail is documented here only so a reader
who inspects the sidecar manifest directly does not misread its unprefixed `path:` value as a
mistake.

## Usage

```
qa_carrier_lint.py --carrier PATH --mission-dir PATH [--json]
qa_carrier_lint.py --self-test
```

| Flag | Meaning |
|---|---|
| `--carrier PATH` | Path to `qa-traceability.yaml`, the carrier to validate. Required unless `--self-test` is passed. |
| `--mission-dir PATH` | The mission-dir root that evidence paths (JUnit-XML files, `*.session.yaml` files) resolve under, and the directory `qa-coverage.json` is written into. Required unless `--self-test` is passed. |
| `--json` | Additionally echo the identical `qa-coverage.json` structure to stdout. `qa-coverage.json` is written to `<mission-dir>/qa-coverage.json` unconditionally on every run regardless of this flag — `--json` only adds the stdout echo, it does not gate whether the file itself is written. |
| `--self-test` | Runs the script's own own built-in fixture/assertion pairs (the tool derives and prints the count itself, so this guide does not restate it) (`data-model.md` Section D) and exits `0` only if every pair passes. Makes `--carrier`/`--mission-dir` optional — when `--self-test` is passed neither is read. |

Invoking the script with neither `--self-test` nor both of `--carrier`/`--mission-dir` is itself
an error: it prints a usage message to stderr and exits `2`.

## Exit codes

| Exit code | `status` | Meaning |
|---|---|---|
| `0` | `measured`, `verdict: ready` | Every requirement with non-empty `case_ids` has every referenced case's evidence resolved to a passing outcome, and the bridge check (below) found no violation. |
| `1` | `measured`, `verdict: blocked` | The carrier is well-formed and every reference resolves, but at least one requirement's covering evidence did not resolve to a passing outcome, or `requirements_uncovered` is non-empty. This is a measured **negative**, not an error. |
| `2` | `not_measured` (carrier absent) **or** `error` (carrier present but malformed) | The two `2`-exit states are distinguished by `qa-coverage.json`'s own `status` field, never conflated. `not_measured` means no carrier file was found at `--carrier`'s path. `error` means a carrier file was found but is invalid: a schema-version mismatch, an unrecognised top-level/entry/evidence key (a closed-schema violation), non-zero `results_missing`, or a non-empty `bridge_violations` / `duplicate_case_ids` / `dangling_case_refs` / `evidence_unparseable` list. Any one of these conditions alone forces `error`; more than one may fire on the same run. |

(`data-model.md`, Section B, the exit-code-to-`status` table.)

## `qa-coverage.json` — the computed-output record

Written unconditionally to `<mission-dir>/qa-coverage.json` on every run; `--json` only
additionally echoes the identical structure to stdout. The full field-by-field table, including
every type and every cross-field interaction, lives in `data-model.md` Section B — this section
summarizes the top-level shape at a level a reader can act on without re-deriving it.

- **Identity and outcome**: `schema` (`"qa-coverage/v1"`), `status`
  (`measured`/`not_measured`/`error`), `verdict` (`ready`/`blocked`, populated only when
  `status == measured`), `reason` (populated only when `status` is `not_measured`/`error`).
- **Requirement coverage**: `requirements_total`, `requirements_covered` (a count),
  `requirements_uncovered` (a list of requirement ids with empty `case_ids`, each with its own
  rationale in `reasons`), `requirements_blocked` (a list of requirement ids whose `case_ids` are
  non-empty but did not resolve to a fully-passing outcome — the state distinct from both
  "covered" and "uncovered").
- **Case/evidence resolution**: `cases_total`, `results_parsed` (cases whose evidence actually
  resolved and was classified), `passed`, `failed`, `skipped`, `overrun` (JUnit-XML and
  session-evidence outcome buckets — see below), `results_missing` (a JUnit-XML `testcase`
  reference that did not match any `<testcase>` in its named file — forces `status: error`),
  `evidence_unparseable` (a list of `{case_id, file, evidence_kind, reason}` objects for evidence
  files that exist but fail to parse — forces `status: error`), `evidence_file_missing` (a count
  of evidence files that simply do not exist yet on disk — a benign, non-error state, never
  forcing `status: error`).
- **Referential-integrity errors**: `duplicate_case_ids` (a list of `{id, count}` objects — forces
  `status: error`), `dangling_case_refs` (a list of `{requirement_id, case_id}` objects for a
  `requirements[].case_ids` entry with no matching `cases[].id` — forces `status: error`).
- **Bookkeeping**: `defects_total`, `reasons` (an id-keyed map carrying every
  `requirements_uncovered` rationale plus every `results_missing` resolution-failure detail, plus
  the top-level `reason` duplicated under `_carrier` when `status != measured`).
- **Bridge check (FR-011)**: `bridge_violations` (a list of `{requirement_id, case_id, wp_file}`
  objects — a requirement whose covering `tasks/WP*.md` file(s) do not list that requirement id;
  forces `status: error`), `bridge_not_measured` (a list of `{requirement_id, case_id, wp_file,
  reason}` objects for a covering WP file that does not exist on disk yet — a normal authoring
  state, never forcing `status: error`).
- **CTRF (reserved, not implemented)**: `ctrf_pending`, a list of `{case_id, reason}` objects for
  every case using the reserved `evidence.ctrf` key; each is individually `not_measured`, with
  `reason` always the fixed string `"CTRF not implemented in v1"`.

**JUnit-XML and session-evidence classification, briefly**: a JUnit-XML outcome classifies as
`passed`, `failed` (failure and error folded into one bucket), or `skipped` — `skipped` is never
folded into `passed`. A session-typed case (`*.session.yaml` evidence) counts as `passed` only
when `status: completed` **and** `overrun: false`; `status: completed` with `overrun: true` counts
in its own dedicated `overrun` bucket, never `passed` or `failed`; `status: aborted` counts as
`failed`. Full mechanism and the count-reconciliation identities live in `data-model.md` Section B.

## Known limitations

No runtime hook compels running this validator or blocks on its verdict today:
`GateBinding.handler_kind: "asset"` remains inert pending upstream issue #2599 (open), and
although spec-kitty gained a live per-type artifact-presence gate on 2026-08-21
(`gather_artifact_presence`, closing #3597), it is wired only for the four built-in mission
families' own step vocabularies, ignores `expected-artifacts.yaml`'s `blocking:` flag entirely,
and has no entry for a custom type such as this pack's own `qa`, so `accept` still enforces
nothing from that manifest. This validator's
output is mechanically checkable — a real exit code, a real `qa-coverage.json` on disk — but it is
agent-invoked, never machine-enforced, until both of those conditions close.
