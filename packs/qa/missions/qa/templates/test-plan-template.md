<!--
  Canonical source: missions/qa/templates/test-plan-template.md (this pack).
  Interim consumption path: copy this file by hand to
  .kittify/overrides/templates/test-plan-template.md in a consuming project. No installer
  exists yet for this step (documented, not automated - see this pack's plan.md
  Complexity Tracking for the mission that owns building one).
  Removal condition: once the resolver-convergence mission's Step 0 lands (both
  resolver forks gain a mission-scoped override tier) and its later step adds an
  org-pack tier to both resolution chains, this file moves to the location that tier
  resolves directly and the interim per-project copy step above is no longer needed.
-->

# Test Plan Template

Adopted from the ISO/IEC/IEEE 29119-3 test-plan section structure, for section structure only.
This template produces a project's `plan.md`.

The eight sections below are a floor, not a ceiling: they are the minimum this template must
declare. An author may add further sections beyond this list, provided each addition carries
its own one-line stated-purpose sentence in the same style as the sections below; omitting a
section listed here is non-compliant regardless of what else is added.

## 1. Test Items

*State what is being tested, and at what scope, in this plan.*

## 2. Environments

*State where tests run — staging environment, hardware profile, data-isolation requirements, and
any other environment property this plan depends on.*

## 3. Test-Data Strategy

*State how test data for this plan is sourced, seeded, and reset between runs.*

## 4. Suite Breakdown

*Categorize this plan's test suites, and state each suite's scope and the requirement ids it
targets.*

This section states, once, the smoke/sanity/regression distinction this mission's vocabulary
uses; no other file in this template set restates or re-derives it:

- **Smoke** — a fast, shallow check that the build is stable enough to test further.
- **Sanity** — a narrow, targeted check, after a small change, that the affected area still
  works.
- **Regression** — a broader check that previously-working behavior has not broken.

## 5. Tooling

*State what supports test execution for this plan, described generically — do not name a
specific test runner or framework here.*

## 6. Schedule

*State when test activities occur relative to the system-under-test's own development schedule.*

## 7. Responsibilities

*State who owns each testing activity named in this plan.*

## 8. Traceability-Matrix Skeleton

*List each test item from Section 1 against the requirement id(s) it exercises, by id only — do
not restate requirement descriptions here; the authoritative register lives in
`test-strategy-template.md`.*

Skeleton shape:

```markdown
| Test Item | Requirement ID(s) | Coverage Status |
|---|---|---|
| Login flow | FR-001 | Planned |
```
