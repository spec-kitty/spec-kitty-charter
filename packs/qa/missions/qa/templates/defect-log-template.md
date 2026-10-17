<!--
  Canonical source: missions/qa/templates/defect-log-template.md (this pack).
  Interim consumption path: copy this file by hand to
  .kittify/overrides/templates/defect-log-template.md in a consuming project. No installer
  exists yet for this step (documented, not automated - see this pack's plan.md
  Complexity Tracking for the mission that owns building one).
  Removal condition: once the resolver-convergence mission's Step 0 lands (both
  resolver forks gain a mission-scoped override tier) and its later step adds an
  org-pack tier to both resolution chains, this file moves to the location that tier
  resolves directly and the interim per-project copy step above is no longer needed.
-->

# Defect Log Template

This template produces a project's `defect-log.md`. It matches the intent of this pack's
not-yet-authored defect-quality directive, cited here by id only, not implemented here.

The seven fields below are a floor, not a ceiling: they are the minimum this template must
require, independently set, on every logged entry. An author may add further fields beyond this
list, provided each addition carries its own one-line stated-purpose sentence in the same style
as the fields below; omitting a field listed here is non-compliant regardless of what else is
added.

## 1. Reproduction Steps

*List the concrete sequence of steps that reproduces the defect.*

## 2. Expected Result

*State what should have happened.*

## 3. Actual Result

*State what happened instead.*

## 4. Environment

*State where the defect was observed — build, platform, and data state.*

## 5. Severity

*State the technical impact of this defect — how badly it breaks the system.*

## 6. Priority

*State the order in which this defect should be fixed, set independently of its severity.*

Severity and priority are two independent fields on every entry, and must never be collapsed
into one. **Severity** is a statement about how badly a defect breaks the system (illustrative
scale: blocker, critical, major, minor, trivial — this template does not mandate a specific
scale, only that the field exists and is set independently of priority). **Priority** is a
statement about fix urgency — the order in which a defect should be addressed (illustrative
scale: P1–P4), independent of severity. The two can, and legitimately do, diverge: a
cosmetic-severity defect can carry the highest priority for a stated reason (for example, a
visible typo on a login page ahead of a demo tomorrow), and a high-severity defect can carry a
lower priority if nothing depends on fixing it immediately. Every logged entry must set both
fields; recording a single merged "severity/priority" value, or setting only one of the two, is
non-compliant.

## 7. Attached Evidence

*Reference logs, screenshots, or other artifacts supporting this report.*
