# QA Result Ingestion (JUnit-XML Producer)

## Overview

`qa_result_ingest.py` is the producer, not the validator: it reads `spec.md`'s Requirement
Register, every `tasks/WP*.md`'s frontmatter (including the `automation_ref` field), and
JUnit-XML result file(s), then writes or refreshes `qa-traceability.yaml` in the exact
`qa-traceability/v1` schema `qa_carrier_lint.py` already validates. This guide documents the
script's invocation contract only: how to resolve it, its flag surface, its exit codes, and the
shape of the carrier it writes. It does not restate the carrier schema itself in full — that lives
in this mission's own `data-model.md`.

## Setup

Two different ids are in play here and they are not interchangeable:

- **This toolguide's own id is `qa-result-ingest-guide`.** It is not a resolvable asset id — it
  names this toolguide document, nothing else.
- **The script ships as an asset sidecar whose own id is the different string
  `qa-result-ingest`.** Resolve the script's real filesystem path with:

  ```
  spec-kitty charter pack asset path qa-result-ingest
  ```

  Never pass this toolguide's own id (`qa-result-ingest-guide`) to `asset path` — it will not
  resolve, because it is not an asset id. (This is the same two-distinct-ids caution
  `toolguides/qa-carrier-lint.guide.md` already carries for its own `qa-traceability-lint`
  asset/`qa-carrier-lint` toolguide split.)

**Anchor asymmetry.** The sidecar's own manifest `path:` value is `qa_result_ingest.py`, with no
`assets/` prefix. Org-tier asset blobs (this pack's own tier) anchor *at* the pack's `assets/`
directory, while only *built-in* blobs anchor one level above their own `assets/` directory and
therefore carry a leading `assets/` segment in their own `path:` value
(`doctrine/assets/repository.py::_anchor_for`, its own documented "Anchor asymmetry (A-2)", first
established for org-tier blobs by `pack-structured-carriers-01M0ME39`). `spec-kitty charter pack asset
path qa-result-ingest` is the resolution path a caller should use regardless of this detail.

**The script carries no shebang and is never made executable.** Invoke it with `python` in front
of the resolved path — a bare resolved-path invocation fails "Permission denied," not a Python
traceback.

## Usage

```
qa_result_ingest.py --results PATH [--results PATH ...] --strategy path/to/spec.md
                     --tasks-dir tasks/ --since TIMESTAMP --mission-dir PATH [--json]
qa_result_ingest.py --self-test
```

| Flag | Meaning |
|---|---|
| `--results PATH` | Path to a JUnit-XML result file. Repeatable — pass it once per result file when a mission run produces more than one. **Required only when at least one `tasks/WP*.md` case declares a non-empty `automation_ref` anywhere in the mission** — a legitimately exploratory/manual-only mission has none, and invokes the ingester with `--results` omitted entirely (see Edge Cases in `spec.md`). When at least one `automation_ref` is declared and `--results` is omitted anyway, that is exit 2 (`error`): declared automated evidence with nothing supplied to correlate against is not well-formed input at all. |
| `--strategy PATH` | Path to the mission's `spec.md`, the source of the Requirement Register (FR-009). Required unless `--self-test` is passed. A missing file at this path is exit 2 (`error`, FR-019) — a missing Requirement Register is unusable input, never a valid empty one. |
| `--tasks-dir PATH` | Path to the mission's `tasks/` directory, whose `WP*.md` frontmatter (including `automation_ref`) the ingester reads. Required unless `--self-test` is passed. |
| `--since TIMESTAMP` | The boundary a `<testsuite>` result's effective timestamp is checked against for staleness (FR-006). Required unless `--self-test` is passed. |
| `--mission-dir PATH` | The mission-dir root `qa-traceability.yaml` is written (or refreshed) into. Required unless `--self-test` is passed. |
| `--json` | Additionally echo a six-key ingestion record (`status`, `exit_code`, `carrier_path`, `requirements_total`, `cases_total`, `unmapped_testcases`) -- not the carrier document's own structure to stdout, alongside writing `qa-traceability.yaml` unconditionally to `<mission-dir>/qa-traceability.yaml` regardless of this flag. |
| `--self-test` | Runs the script's own built-in fixture/assertion pairs — one fixture that trips each named guard, one compliant fixture that does not — printing its own derived pair count (the tool derives and prints the count itself, so this guide does not restate it), mirroring `qa_carrier_lint.py --self-test`'s own convention. Makes every other flag optional — when `--self-test` is passed, none of them are read. |

## Scope: what this ingester does not read

Not ingested in v1, named explicitly rather than left as a vague "other formats": **TAP**, **TRX**
(MSTest/VSTest's own native XML schema), **Cucumber JSON**, **Robot Framework**'s native XML, and
**CTRF**. Within JUnit-XML itself, only the outcome-as-child-element dialect
(`<failure>`/`<error>`/`<skipped/>`) is supported — the outcome-as-`status`-attribute dialect some
emitters use (e.g. `<testcase status="failed">` with no `<failure>` child) is explicitly
unsupported, and is detected and refused rather than silently mis-read as passing (see Exit codes
below).

## Exit codes

| Exit code | `status` | Meaning |
|---|---|---|
| `0` | `ingested` | The carrier was produced or refreshed from real, current-run evidence; no guard fired. |
| `1` | `blocked` | A specific, named guard fired on well-formed, interpretable input, and the carrier was deliberately not written or refreshed rather than written with a hole in it — for example a supplied JUnit-XML file that parses to zero `<testcase>` elements, results considered stale against `--since`, or a WP-declared `automation_ref` that resolves to no enumerated testcase. This is a measured negative, not an error. |
| `2` | `error` | Input this tool cannot use at all, in either of two senses named by `spec.md`'s Decision 1: **(a)** syntactically unparseable or entirely absent — the XML does not parse, the Requirement Register is unreadable, `spec.md` itself does not exist, or `--results` was never supplied despite a declared `automation_ref`; or **(b)** well-formed but semantically un-interpretable — every individual file parses cleanly, but the tool cannot extract outcomes from the combined input at all, such as the unsupported `status=`-attribute JUnit dialect, or two different WPs' cases declaring the identical `automation_ref` value. |

`spec.md`'s Decisions Log (Decision 1, and its Amendment/Second Amendment) is the authoritative
home for the full guard-by-guard taxonomy; this table names the three-state shape, not every
individual guard's own FR number.

## `qa-traceability.yaml` — the produced/refreshed carrier

Written (or refreshed) at `<mission-dir>/qa-traceability.yaml` in the same `qa-traceability/v1`
schema `qa_carrier_lint.py` already validates — the carrier's producer and its validator agree on
one schema by construction, never two independently-maintained ones. Each `cases[]` entry the
ingester resolves against real JUnit-XML evidence carries `type: automated` with its literal
`evidence.junit` key populated (the carrier schema's own field name, quoted verbatim here since
neutralising a direct quotation of a field name would misdescribe the schema); a case with no
`automation_ref` for the current run is left as session-evidence-backed or unmeasured, never
guessed into an automated correlation it was not declared for. The full field-by-field shape lives
in this mission's own `data-model.md`.

## Known limitations

No runtime hook compels running this ingester or blocks on a `blocked`/`error` exit today:
`GateBinding.handler_kind: "asset"` remains inert pending upstream issue #2599 (open), and
`expected-artifacts.yaml`'s `blocking:` flag is likewise not enforced by `accept` — the same gap
this pack's own `qa-carrier-lint` toolguide already discloses for its sibling validator applies
here too. This ingester's output is mechanically checkable — a real exit code, a real
`qa-traceability.yaml` on disk — but it is agent-invoked, never machine-enforced, until both of
those conditions close.
