<!--
  Canonical source: missions/qa/templates/test-strategy-template.md (this pack).
  Interim consumption path: copy this file by hand to
  .kittify/overrides/templates/test-strategy-template.md in a consuming project. No installer
  exists yet for this step (documented, not automated - see this pack's plan.md
  Complexity Tracking for the mission that owns building one).
  Removal condition: once the resolver-convergence mission's Step 0 lands (both
  resolver forks gain a mission-scoped override tier) and its later step adds an
  org-pack tier to both resolution chains, this file moves to the location that tier
  resolves directly and the interim per-project copy step above is no longer needed.
-->

# Test Strategy Template

Adopted from the ISO/IEC/IEEE 29119-3 test-strategy section structure, for section structure
only. This template produces a project's `spec.md`.

The five sections below are a floor, not a ceiling: they are the minimum this template must
declare. An author may add further sections beyond this list, provided each addition carries
its own one-line stated-purpose sentence in the same style as the sections below; omitting a
section listed here is non-compliant regardless of what else is added.

## 1. SUT / Test-Item Scope

*State the system-under-test's boundaries and what is explicitly out of scope.*

## 2. Risk Register

*List product/quality risks and their likelihood/impact.*

## 3. Quality Criteria

*State the measurable bar the SUT must clear.*

## 4. Entry/Exit Criteria

*State what must be true before testing starts and before it is declared complete.*

## 5. Requirement Register

*Declare one row per requirement, keyed by its `FR-`/`NFR-`/`C-` id, in the table-row or
bullet-form shape shown below.*

This section and `test-report-template.md`'s requirement-coverage-summary section govern the
same content — the FR/NFR/C-id-keyed requirement set — under two deliberately different names:
this section is the **authoritative requirement register**; the report's section is a
**cross-referencing requirement-coverage-summary** that restates each id's outcome without
duplicating this register. A reader who opens only this file still has everything needed to
declare requirements correctly; a reader who opens only `test-report-template.md` still knows
where the authoritative source lives.

**Id shape and its consequence.** Requirement ids in this register must be `FR-NNN`, `NFR-NNN`,
or `C-NNN` — uppercase, exactly three digits; a four-digit or lowercase id is accepted by the
requirement mapper and the retrospective coverage detector but rejected by the
substantive-content gate, so `spec.md`/`plan.md` silently stops auto-committing. Of the two files
in this template pair, this register is the one whose row shape is mechanically checked today:
it feeds `spec.md` directly, and a register whose only row uses a malformed id is read by that
gate as zero populated rows — the file loses auto-commit while the mapper and the retrospective
detector both continue to report it as fine. Get the shape right the first time; nothing else
will warn you.

Compliant table-row form:

```markdown
| FR-001 | Requirement title | Description of what this requirement demands. | High | Open |
```

Compliant bullet-form alternative:

```markdown
- **FR-001**: Description of what this requirement demands.
```

Either shape is acceptable — pick one deliberately for this document rather than improvising a
third shape partway through.
