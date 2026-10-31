# Test Report Review

## Role in the QA lifecycle

This step is the `review.md`-equivalent stage: reviewing execution results for genuine,
no-fake-green outcomes and producing the test report. In spirit, the last step before a release
decision — but the actual release decision is made at `accept`.

## Bound agent-profile

`qa-reporter-rhea`.

## Directive citation

- `QA_003` (no-fake-green) — no-fake-green is this step's defining constraint; the test report
  must reflect genuine outcomes.

## Canonical artifact & template

- Canonical artifact: `test-report.md`.
- Template file: `test-report-template.md`.

## Evidence Ingestion

Before running the traceability carrier's validator (below), resolve and run this mission's
carrier producer, so the carrier the validator scores is one this mission's own machinery just
built from the current run's evidence, not a stale or hand-typed file:

1. Resolve the script's on-disk path: `spec-kitty charter pack asset path qa-result-ingest`.
2. Run it against this mission's own inputs. `--results` is a repeatable append flag —
   `--results PATH [--results PATH ...]`, one occurrence per result file, matching
   `assets/qa_result_ingest.py`'s own documented invocation shape (module docstring) — **never** a
   bare shell glob handed to a single `--results` occurrence: that only happens to run when the
   glob expands to exactly one file, and breaks with `unrecognized arguments` the moment a second
   result file exists. For two result files `a.xml` and `b.xml`: `--results evidence/results/a.xml
   --results evidence/results/b.xml --strategy spec.md --tasks-dir tasks/ --since <timestamp>
   --mission-dir . --json` (the full invocation is `python <resolved-path> --results
   evidence/results/a.xml --results evidence/results/b.xml --strategy spec.md --tasks-dir tasks/
   --since <timestamp> --mission-dir . --json`). To build this from however many files
   `evidence/results/*.xml` actually expands to, without typing each path by hand, turn the glob
   into repeated flags first (bash):

   ```
   shopt -s nullglob          # without this, an empty results/ passes the literal '*.xml'
   results_args=()
   for f in evidence/results/*.xml; do results_args+=(--results "$f"); done
   python <resolved-path> "${results_args[@]}" --strategy spec.md --tasks-dir tasks/ \
       --since <timestamp> --mission-dir . --json
   ```

   `--results` is required only when at least one `tasks/WP*.md` case declares a non-empty
   `automation_ref` anywhere in this mission; a legitimately exploratory/manual-only mission has
   none and omits `--results` entirely.
3. Source `<timestamp>` from this mission's own runtime state, never a fixed or guessed value: it
   is the wall-clock time `implement`'s own "Evidence Commit Discipline" step began. Concretely,
   read it off this mission's own event log — the `history:` entries every `tasks/WP*.md`
   frontmatter accumulates (`at:`/`actor:`/`action:`, appended by `spec-kitty agent tasks
   move-task`/`add-history`) — and take the earliest `at:` timestamp among this mission's
   test-execution work package(s) moving `--to doing`. That transition is this mission's own
   record of when evidence-producing execution started; use it verbatim as `--since`'s boundary.
4. The ingester's exit code communicates status, mirroring the validator's own three-state
   contract below: exit `0` (`ingested` — the carrier was produced or refreshed from real,
   current-run evidence, no guard fired), exit `1` (`blocked` — a specific, named guard fired on
   well-formed, interpretable input, e.g. zero parsed testcases or stale results, and the carrier
   was deliberately not written or refreshed), or exit `2` (`error` — input this tool cannot use
   at all, either syntactically unparseable/absent or well-formed but semantically
   un-interpretable, e.g. the unsupported `status=`-attribute JUnit-XML dialect below).

**Disclosed non-support (FR-003)**. This ingester does **not** support TAP, TRX (MSTest/VSTest's
own native XML schema), Cucumber JSON, Robot Framework's native XML, or CTRF in v1 — named
explicitly here, never left as a vague "other formats" catch-all. Within JUnit-XML itself, only
the outcome-as-child-element dialect (`<failure>`/`<error>`/`<skipped/>`) is supported; the
outcome-as-`status`-attribute dialect some emitters use (e.g. `<testcase status="failed">` with no
`<failure>` child) is explicitly unsupported and is detected and refused, never silently mis-read
as passing.

**Known limitation (FR-015)**. No runtime hook compels running this ingester or blocks on a
`blocked`/`error` exit today: `GateBinding.handler_kind: "asset"` remains inert pending upstream
issue #2599 (open), and `expected-artifacts.yaml`'s `blocking:` flag is likewise not enforced by
`accept` — the same gap this file's own "Evidence & Coverage Computation" section already
discloses (FR-016) for the validator side of this carrier applies here too, on the producer side.
Running this ingester today is mechanically checkable (a real exit code, a real
`qa-traceability.yaml` on disk) but agent-invoked, never machine-enforced — nothing upstream
blocks `review` or `accept` on it.

This disclosed-non-support and known-limitation text is mirrored from `assets/qa_result_ingest.py`'s
own module docstring — the primary, authoritative home for this prose — and must be reconciled
against that docstring's final, as-shipped wording at review time, not left to drift from it.

In practice, for this step: run the ingester before the validator below, and treat its exit code
as informing whether a genuine, current-run carrier exists at all for the validator to score —
this is agent-invoked discipline, not a mechanically enforced gate.

## Evidence & Coverage Computation

Before drafting `test-report.md`'s Coverage/Pass-Fail tables, resolve and run the traceability
carrier's validator:

1. Resolve the script's on-disk path: `spec-kitty charter pack asset path qa-traceability-lint`.
2. Run it against this mission's own carrier: `--carrier qa-traceability.yaml --mission-dir . --json`
   (the full invocation is `python <resolved-path> --carrier qa-traceability.yaml --mission-dir . --json`).
   It exits `0` when `status: measured, verdict: ready`, `1` when `status: measured,
   verdict: blocked`, or `2` when `status: not_measured` (no carrier) or `status: error` (carrier
   present but malformed).
3. The validator writes `qa-coverage.json` unconditionally. Source the Coverage/Pass-Fail tables'
   numbers only from that file's own fields — `requirements_covered` / `requirements_uncovered` /
   `requirements_blocked` for the requirement-level table, `passed` / `failed` / `skipped` /
   `overrun` for the case-level counts — never by hand-counting or re-deriving them from the
   carrier or the raw JUnit-XML/session evidence directly.
4. This is required by `QA_003` (no-fake-green): the test report's numbers must be sourced by name
   only from this project's machine-readable evidence carrier.

**Known limitation (`pack-structured-carriers-01M0ME39` FR-016)**. The sentence below is the
disclosure that sibling mission's own FR-016 mandates verbatim for the validator named in it; it
is reproduced here word for word rather than restated, because that requirement says verbatim.
(PMG-03 disambiguation: this citation is deliberately NOT this mission's — `feat-result-ingestion-
01M0YTZP`'s — own, unrelated FR-016, the JUnit-XML `status=`-attribute dialect guard; the two
numbers collide only because they belong to two different missions' Requirement Registers.)

> No runtime hook compels running this validator or blocks on its verdict today:
> `GateBinding.handler_kind: "asset"` remains inert pending upstream issue #2599 (open), and
> although spec-kitty gained a live per-type artifact-presence gate on 2026-08-21
> (`gather_artifact_presence`, closing #3597), it is wired only for the four built-in mission
> families' own step vocabularies, ignores `expected-artifacts.yaml`'s `blocking:` flag entirely,
> and has no entry for a custom type such as this pack's own `qa`, so `accept` still enforces
> nothing from that manifest.

In practice, for this step: following the steps above is agent-invoked discipline, not a
mechanically enforced gate. Run the validator anyway, and treat its verdict as binding on your own
report — nothing upstream will do it for you.
