# QA Templates

This file is the canonical-artifact mapping table for this pack's seven QA templates, plus a
summary of the interim install path those templates share and the condition under which that
interim path goes away. It is documentation about the templates, not a template itself: it is not
resolver-facing content, and the naming rules the mapping and install-path sections below describe
apply to the seven template files, not to this file's own name.

## Canonical-artifact mapping

| Template file | Canonical artifact | Requirements |
|---|---|---|
| `test-strategy-template.md` | `spec.md` | FR-004, FR-011 |
| `test-plan-template.md` | `plan.md` | FR-005 |
| `test-suite-template.md` | `tasks.md` | FR-006, FR-012 |
| `test-case-template.md` | `tasks/WP*.md` | FR-007, FR-012, FR-014 |
| `exploratory-charter-template.md` | `tasks/WP*.md` | FR-008, FR-012 |
| `test-report-template.md` | `test-report.md` | FR-009, FR-011 |
| `defect-log-template.md` | `defect-log.md` | FR-010 |

`tasks/WP*.md` is bound twice, by design: once for `test-case-template.md`, once for
`exploratory-charter-template.md`. That double-binding is the one sanctioned exception to "exactly
once" in this table — every other canonical artifact is bound to exactly one template, this one is
bound to exactly two, and neither binding is an error to resolve down to one row.

`test-report.md` and `defect-log.md` are honestly-named extras: spec-kitty does not treat either
name as a canonical mission artifact the way it treats `spec.md`, `plan.md`, or `tasks.md`, but
both are real files a filled-in template produces, named for what they are rather than mapped onto
an artifact they are not.

## Interim install path

The mission-scoped override tier does not exist on the production resolver path today: both
`mission create` and plan setup import a resolver fork that carries no mission-scoped override
check at all, only a non-mission-scoped one. That means the only override location either resolver
fork actually honours right now is the non-mission-scoped override directory inside a consuming
project's own `.kittify/` tree — `.kittify/overrides/templates/<name>`, using the same filename the
template ships under in this pack.

Getting a template in front of that resolver today is a manual step, not an installer: a consuming
project copies the desired file by hand to that location, under its own name. No copy/symlink
installer exists yet for this step; building one is a later, separate mission's scope, not this
pack's.

That install target is not scoped to a mission type — it is a single flat directory shared by every
mission type a consuming project might use. This is exactly why every one of the seven template
filenames must be globally unique: a name collision there would silently override a built-in
template for every mission type in that project, not just this pack's `qa` content, the moment the
interim copy is installed. A filename that looks generic and harmless in isolation is a resolution
hazard the moment it collides with a name already in use elsewhere.

**Removal condition**: once both resolver forks gain a mission-scoped override tier, and an
org-pack tier is added to both resolution chains, these seven files move to the location that tier
resolves directly, and the interim per-project copy step described above is no longer needed.
