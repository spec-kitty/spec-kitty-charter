<!--
  Canonical source: missions/qa/templates/test-report-template.md (this pack).
  Interim consumption path: copy this file by hand to
  .kittify/overrides/templates/test-report-template.md in a consuming project. No installer
  exists yet for this step (documented, not automated - see this pack's plan.md
  Complexity Tracking for the mission that owns building one).
  Removal condition: once the resolver-convergence mission's Step 0 lands (both
  resolver forks gain a mission-scoped override tier) and its later step adds an
  org-pack tier to both resolution chains, this file moves to the location that tier
  resolves directly and the interim per-project copy step above is no longer needed.
-->

# Test Report Template

Adopted from the ISO/IEC/IEEE 29119-3 test-completion/summary-report section structure, for
section structure only. This template produces a project's `test-report.md`.

The six sections below are a floor, not a ceiling: they are the minimum this template must
declare. An author may add further sections beyond this list, provided each addition carries
its own one-line stated-purpose sentence in the same style as the sections below; omitting a
section listed here is non-compliant regardless of what else is added.

## 1. Test-Execution Summary

*Summarize what ran, when, and against what build/environment.*

This report uses three related terms precisely, and no other file in this template pair
redefines them: a **defect** is a flaw in the product; a **failure** is an observed deviation
from expected behavior during test execution; an **error** is the human or process mistake that
produced the defect.

## 2. Coverage Table

*State the computed coverage figures, sourced by name only from this project's machine-readable
evidence carrier; if a figure cannot be computed yet, say so explicitly rather than estimating a
prose substitute.*

## 3. Pass/Fail Table

*State the computed pass/fail counts, with the same computed-number discipline as the coverage
table above — never a prose estimate standing in for a number that has not been computed yet.*

## 4. Requirement-Coverage-Summary

*Restate each requirement id's pass/fail outcome, keyed by its `FR-`/`NFR-`/`C-` id, in the
table-row or bullet-form shape shown below.*

This section and `test-strategy-template.md`'s requirement-register section govern the same
content — the FR/NFR/C-id-keyed requirement set — under two deliberately different names: the
strategy's section is the **authoritative requirement register**; this section is a
**cross-referencing requirement-coverage-summary** that restates each id's pass/fail outcome
without duplicating that register. A reader who opens only this file still knows what this
section is and where the authoritative source lives; a reader who opens only
`test-strategy-template.md` still has everything needed to declare requirements correctly.

**Id shape and its consequence.** Requirement ids in this table must be `FR-NNN`, `NFR-NNN`, or
`C-NNN` — uppercase, exactly three digits; a four-digit or lowercase id is accepted by the
requirement mapper and the retrospective coverage detector but rejected by the
substantive-content gate, so `spec.md`/`plan.md` silently stops auto-committing. This file's rows
use the identical shape for readability and consistency with the strategy register above; no live
auto-commit gate inspects this file directly today — the discipline matters here for cross-file
consistency with the authoritative register, not because this file itself is gated. Author to the
same strict shape anyway: a reader should get the same discipline from either file.

Compliant table-row form:

```markdown
| FR-001 | Requirement title | Pass/fail outcome and one-line evidence pointer. | High | Pass |
```

Compliant bullet-form alternative:

```markdown
- **FR-001**: Pass/fail outcome and one-line evidence pointer.
```

Either shape is acceptable — match whichever shape `test-strategy-template.md`'s register used
for this project, so the two files read consistently side by side.

## 5. Residual-Risk Statement

*State what risk remains open at report time.*

## 6. Go/No-Go Recommendation

*State the report's bottom-line recommendation.*
