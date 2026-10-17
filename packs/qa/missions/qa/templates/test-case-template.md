<!--
  Canonical source: missions/qa/templates/test-case-template.md (this pack).
  Interim consumption path: copy this file by hand to
  .kittify/overrides/templates/test-case-template.md in a consuming project. No installer
  exists yet for this step (documented, not automated - see this pack's plan.md
  Complexity Tracking for the mission that owns building one).
  Removal condition: once the resolver-convergence mission's Step 0 lands (both
  resolver forks gain a mission-scoped override tier) and its later step adds an
  org-pack tier to both resolution chains, this file moves to the location that tier
  resolves directly and the interim per-project copy step above is no longer needed.
-->

# Test Case Template

This template produces a single test case **inside the body of** a project's `tasks/WP*.md`:
a fenced YAML block plus one Gherkin-notation scenario.

> **This block is NOT the work-package file's own frontmatter.** A `tasks/WP*.md` file has
> exactly one frontmatter section, at the top of the file, and it belongs to the work package —
> not to a test case. Its schema is `WPMetadata` (`specify_cli/status/wp_metadata.py`), which
> **requires `work_package_id`** and is declared `extra="forbid"`, so the test-case keys below
> (`id`, `type`, `preconditions`) are rejected there.
>
> Putting these keys in the WP file's frontmatter **fails silently**. `read_wp_frontmatter`
> raises, and `specify_cli/status/bootstrap.py` catches it, emits only a
> `logger.warning("Skipping %s: malformed frontmatter")`, records the file as
> `skipped_malformed`, and continues with a successful exit code. The work package is dropped
> from the mission with no error surfaced to the operator.
>
> Write test-case blocks in the WP **body**, below the frontmatter, as fenced ```yaml blocks.

## Test-case block fields

*Fill in every field: `id`, `requirement_refs`, `priority`, `type`, `preconditions`.* `automation_ref`
is optional — populate it only when this case has a corresponding automated JUnit-XML
implementation; leave it unset otherwise.

`requirement_refs:` is populated by `spec-kitty agent tasks map-requirements`; this template does
not redefine or re-implement that command. `assets/qa_carrier_lint.py`'s own bridge invariant
checks this same field (this pack's shipped `qa-traceability/v1` validator), and
`assets/qa_result_ingest.py` (this pack's shipped `qa-traceability/v1` producer) reads it directly
when correlating a case's declared `automation_ref` to the requirement(s) it covers.

`automation_ref` is an optional string naming the exact JUnit `classname::name` pair this case's
automated implementation resolves to (e.g.
`"TestCarrierSchema::test_schema_rejects_unknown_key"`). **Its condition is its own presence — not
the `type:` field.** A non-empty `automation_ref` declares that this case has real, automated
JUnit evidence to correlate for the current run; the result-ingestion tooling (a later mission's
asset) resolves it against ingested JUnit-XML and, on success, writes this case's carrier entry as
`type: automated` with `evidence.junit` populated. An absent or empty `automation_ref` means this
case has no automated evidence to correlate for this run; no JUnit correlation is attempted for
it, and its carrier-side classification (`exploratory`/`manual`, `evidence.session`) follows the
pre-existing session-evidence-pointer mechanism, unchanged and untouched by this field.

```yaml
id: TC-001
requirement_refs: []
priority: P1
type: functional
preconditions: []
```

The two examples below use `text` fences on purpose: `qa-result-ingest` reads every ```yaml block
in a work-package body as a case, so a work package that keeps these examples would ingest them.
Write your own cases as ```yaml blocks, like the `TC-001` block above.

### Example: automated case

```text
id: TC-014
requirement_refs: [FR-007]
priority: P1
type: functional
preconditions: []
automation_ref: "IngestPipelineTests::test_zero_testcase_guard_blocks_carrier_write"
```

When a case has a corresponding automated implementation, as here, `automation_ref` names it: this
case has a real, automated JUnit-XML implementation identified by the given `classname::name` pair,
and an ingester resolving this file's evidence looks for exactly that pair in the supplied
JUnit-XML results.

### Example: exploratory/manual case, no automated evidence

```text
id: TC-015
requirement_refs: [FR-006]
priority: P2
type: exploratory
preconditions: []
```

This case carries no `automation_ref` key, so the ingester attempts no JUnit correlation for it;
its evidence, if any, is a session-evidence pointer this case's frontmatter already carries by the
pre-existing mechanism — untouched by this field.

**Backward compatibility.** `automation_ref` is a purely additive frontmatter field. Every case
authored against today's template before this change — carrying no `automation_ref` key at all —
is read identically to the "absent" example above: no schema-validation failure, no retroactive
edit required, no correlation attempted. The pre-existing `type:` field and its vocabulary
(including the shipped `type: functional` example above) are untouched by this edit.

## 1. Scenario

*Write the scenario in Given/When/Then form.*

The notation, the dialect, and any runner-binding rules live in
`styleguide:given-when-then-authoring` and `toolguide:gherkin` — this template cites both by name
as delegation targets and does not restate, narrow, or contradict either one's own content.

This template adds exactly one rule on top of that cited doctrine: each scenario's `Then` must be
independently observable, so a result row can be emitted per case.

**Operational test.** A `Then` clause is independently observable when it names a concrete,
checkable value or state transition — not a summary-judgment word ("correctly", "as expected",
"works") — that can be evaluated for that one scenario alone, without running any other scenario
first and without inspecting internal implementation state.

Compliant — a concrete, checkable `Then`, evaluable for this scenario alone:

```gherkin
Scenario: Order ships successfully
  Given an order with status "processing"
  When the warehouse marks the order as shipped
  Then the order's status field reads "shipped"
```

Rejected by this rule — a summary judgment naming no checkable value at all:

```gherkin
Scenario: Order ships successfully
  Given an order with status "processing"
  When the warehouse marks the order as shipped
  Then the system behaves correctly
```
