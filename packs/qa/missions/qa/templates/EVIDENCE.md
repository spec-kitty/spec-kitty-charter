# Evidence: Cross-file Sweeps and Structural Self-Check (WP05)

This file records the result of WP05's three verification subtasks (T014-T016) over the eight
files this mission authored: the seven templates at `missions/qa/templates/` plus
`missions/qa/templates/README.md`. WP05 authors no template content; it verifies and records.

**No schema or loader validates any of this mission's template content.** Confirmed directly this
session, restating spec.md Decision 8 / Constraint C-004: `pack_validator.py` contains zero
occurrences of the string `template`, and this mission's constraints (C-003) forbid authoring a
gate to close that gap. **Every check recorded below is therefore a named manual step** — a direct
file read, a `grep` invocation, or a one-off script-assisted parse run by this WP this session —
never an automated CI gate. Where a check could not be completed in full, that incompleteness is
recorded as the result for that check, not treated as an implicit pass.

## File inventory (8 files in scope)

1. `missions/qa/templates/test-strategy-template.md`
2. `missions/qa/templates/test-plan-template.md`
3. `missions/qa/templates/test-suite-template.md`
4. `missions/qa/templates/test-case-template.md`
5. `missions/qa/templates/exploratory-charter-template.md`
6. `missions/qa/templates/test-report-template.md`
7. `missions/qa/templates/defect-log-template.md`
8. `missions/qa/templates/README.md`

All eight were present in this WP's lane at the time of this review (verified by directory
listing this session), so every sweep below runs over the full set unless a subtask's own scope
deliberately narrows it (stated explicitly where that happens).

---

## T014 — Cross-file term-pair consistency sweep (FR-015)

**Method**: `grep -ni` for each of the six individual terms (severity, priority, defect, failure,
error, smoke, sanity, regression) across all seven template files. The README is excluded from
this specific count — it is not a template and carries no requirement or QA-vocabulary content of
its own (it is a mapping table plus install-path prose). **Files checked: 7 of 7 template files.**

| Term pair | Owning file (research.md Decision 6) | Definition stated in owning file? | Competing definition found elsewhere? | Result |
|---|---|---|---|---|
| severity vs. priority | `defect-log-template.md` | Yes — Sections 5-6 state both fields independently, forbid collapsing them, and give an operational example of legitimate divergence. | None. `test-case-template.md` (frontmatter field `priority: P1`) and `test-suite-template.md` (table column `Priority`, prose "priority/risk ordering") both use `priority` as a mandated field/column — a use, not a redefinition. | **0 competing definitions** |
| defect vs. failure vs. error | `test-report-template.md` | Yes — Section 1 states all three, in one sentence each, immediately after the section's own purpose line. | None. `defect-log-template.md` uses "defect" 8+ times in prose and section titles but states no competing definition of the term itself — consistent use. | **0 competing definitions** |
| smoke vs. sanity vs. regression | `test-plan-template.md` | Yes — Section 4 (Suite Breakdown) states all three as a bulleted list, prefaced "states, once... no other file in this template set restates or re-derives it." | None. `test-suite-template.md` uses "smoke" exactly once, inside the example suite name `Login smoke suite` in its worked table row — an incidental use inside an example, not a definition. | **0 competing definitions** |

FR-015's forward-looking citation-readiness half — checking these term pairs against a
not-yet-existing ISTQB-citing glossary pack — is out of this mission's scope (spec.md Decision 13)
and is **not evaluated here**. The three-pair, seven-file internal-consistency check above is the
whole of what this subtask verifies.

### WP04-deferred check: leading comment-block cross-file consistency (FR-013)

WP04 reported honestly that lane isolation left only two of the seven templates in its worktree,
so it could check comment-block consistency for only that subset and carried the remainder to
WP05. All eight files are now present in this lane; this WP ran the full check.

**Method**: read the leading `<!-- ... -->` comment block (the first 11 lines) of all seven
template files and compared them side by side, normalizing only the substituted filename.
**Files checked: 7 of 7 template files** (the README is excluded from this comment-block check —
it carries the same three facts as prose in its own "Interim install path" section rather than as
a leading comment block, a carve-out already reviewed and approved for this mission).

**Result**: all seven blocks state the same three facts, worded identically apart from the
filename substitution:

1. **Canonical source** — `missions/qa/templates/<filename>` (this pack).
2. **Interim consumption path** — manual copy to `.kittify/overrides/templates/<filename>` in a
   consuming project; no installer exists yet for this step.
3. **Removal condition** — once the resolver-convergence mission's Step 0 lands (both resolver
   forks gain a mission-scoped override tier) and its later step adds an org-pack tier to both
   resolution chains, the file moves and the interim copy step is no longer needed.

**Zero competing or inconsistent statements of these three facts found across the seven files.**

One immaterial variance noted for the record, not recorded as a defect: `exploratory-charter-template.md`'s
block wraps the sentence "No installer exists yet for this step" one word earlier than the other
six files' blocks do (its line reads "No" / "installer exists..." where the other six read
"No installer" / "exists..."), because its filename is longer than the other six, shifting the
wrap point. The three facts' wording is otherwise identical across all seven files; this is a
line-wrap artifact of filename length, not a missing, restated, or contradicted fact.

---

## T015 — Structural self-check evidence, recorded as a manual step (FR-016, NFR-005)

### Per-file section/field presence against each file's governing FR row (data-model.md Section C)

| File | Governing requirement | Required sections/fields | Present? | Result |
|---|---|---|---|---|
| `test-strategy-template.md` | FR-004 | 5/5: SUT/test-item scope, risk register, quality criteria, entry/exit criteria, requirement register | 5/5 present, each with a one-line italicized purpose | PASS |
| `test-plan-template.md` | FR-005 | 8/8: test items, environments, test-data strategy, suite breakdown, tooling, schedule, responsibilities, traceability-matrix skeleton | 8/8 present, each with a one-line stated purpose | PASS |
| `test-suite-template.md` | FR-006, FR-012 | Frontmatter `requirement_refs:` convention + 1 body section (suite manifest, literal per-row FR-id shape) | Both present; manifest row uses the literal parser-expected shape (`FR-001, FR-003` in a table row), not a prose paraphrase | PASS |
| `test-case-template.md` | FR-007, FR-012, FR-014 | Frontmatter fields `id`, `requirement_refs`, `priority`, `type`, `preconditions` (5/5) + Gherkin scenario section citing `styleguide:given-when-then-authoring` and `toolguide:gherkin`, one added rule, no restated dialect | 5/5 frontmatter fields present; body cites both artifacts by name and states exactly one added rule (independently-observable `Then`), with a compliant and a rejected worked example; no file-layout or runner-binding content restated | PASS |
| `exploratory-charter-template.md` | FR-008, FR-012 | Frontmatter `requirement_refs:` convention + 6/6 body sections: charter statement, timebox, areas/coverage, session notes, bugs/issues found, debrief outcome | Both present; debrief-outcome section explicitly requires recording a timebox overrun rather than treating it as on-schedule | PASS |
| `test-report-template.md` | FR-009, FR-011 | 6/6: test-execution summary, coverage table, pass/fail table, requirement-coverage-summary, residual-risk statement, go/no-go recommendation | 6/6 present; coverage/pass-fail tables state the computed-number discipline; FR-011's id-shape-and-consequence sentence is present verbatim-equivalent to spec.md Decision 12's supplied sentence | PASS |
| `defect-log-template.md` | FR-010 | 7/7 independently-set fields: reproduction steps, expected result, actual result, environment, severity, priority, attached evidence | 7/7 present; severity/priority independence and non-collapse rule stated explicitly with a worked example of legitimate divergence | PASS |
| `README.md` | FR-002, FR-013 | Mapping table binding all 7 filenames exactly once (6 distinct canonical artifact names, `tasks/WP*.md` bound twice — the one sanctioned double-binding) + interim install path and removal condition, as prose | Mapping table: 7/7 template rows present, 0 unmapped, 0 unintended duplicates (the one `tasks/WP*.md` double-binding matches SC-002's stated exception exactly); install path and removal condition both stated in the "Interim install path" section | PASS |

**Files reviewed against a named requirement: 8 of 8. No file was skipped and no partial review is
presented as a full one.**

### YAML frontmatter parse check (three WP-shaped templates)

**Method**: extracted each file's frontmatter block (the content between its `---` delimiters) and
ran it through a standard YAML parser (`yaml.safe_load`) this session. **Files checked: 3 of 3.**

| File | Parse result |
|---|---|
| `test-suite-template.md` | Parses cleanly: `{'requirement_refs': []}` |
| `test-case-template.md` | Parses cleanly: `{'id': 'TC-001', 'requirement_refs': [], 'priority': 'P1', 'type': 'functional', 'preconditions': []}` |
| `exploratory-charter-template.md` | Parses cleanly: `{'requirement_refs': []}` |

This confirms each block is **well-formed YAML** — a weaker guarantee than schema validation. It
does not confirm the field set is semantically complete or correct beyond what the per-file section
check above already established by direct read; no `Template` pydantic-equivalent model exists to
check that mechanically (spec.md Decision 8, data-model.md Section A).

### UTF-8 Markdown validity check

**Method**: read each file's raw bytes this session and decoded as UTF-8. **Files checked: 8 of 8.**
All eight decoded without error:

| File | Byte count | Result |
|---|---|---|
| `test-strategy-template.md` | 3,496 | valid UTF-8 |
| `test-plan-template.md` | 2,872 | valid UTF-8 |
| `test-suite-template.md` | 2,503 | valid UTF-8 |
| `test-case-template.md` | 2,656 | valid UTF-8 |
| `exploratory-charter-template.md` | 2,381 | valid UTF-8 |
| `test-report-template.md` | 4,224 | valid UTF-8 |
| `defect-log-template.md` | 2,843 | valid UTF-8 |
| `README.md` | 3,370 | valid UTF-8 |

No encoding or rendering anomaly found in any of the eight files.

### M-01 naming-seam closure (research.md Decision 5)

**Files checked: 2 of 2** (the only two files this check applies to). Confirmed:

- `test-strategy-template.md` Section 5 states, in one explicit sentence, that its requirement
  register and `test-report-template.md`'s requirement-coverage-summary section govern the same
  content under two deliberately different names, and states which of the two is authoritative.
- `test-report-template.md` Section 4 states the mirror-image sentence, identifying itself as the
  cross-referencing, non-duplicating coverage-summary form.

**Both files close the seam explicitly. The seam is not left implicit.**

---

## T016 — Hygiene and neutrality sweep over all eight authored files (NFR-001, NFR-002)

### Scope note

This sweep runs over the eight authored files listed in the File Inventory above. It does **not**
sweep `reviews/`. This is a scoping decision recorded honestly, not a silent narrowing: this
mission's own `analysis-report.md` (finding A1) already identified that NFR-001's scope sentence
in `spec.md` enumerates the seven templates plus `plan.md`, `research.md`, `data-model.md`, and
`checklists/`, and does not name `reviews/` — and recommended treating `reviews/` as out of scope
for the same reason `spec.md` already exempts two of its own self-references: a review report that
names a leak pattern or a banned term in order to describe what it scanned for is describing the
check, not committing a leak. This WP applies that documented reasoning. **For the record**: as
read in this WP's lane, `spec.md`'s NFR-001 row did not yet carry an explicit "`reviews/` excluded"
sentence, so the exclusion was recorded here as this WP's own scoping decision grounded in
`analysis-report.md` finding A1, rather than presented as a quotation.

**Resolved after authoring**: the amendment *had* in fact been made — `spec.md`'s NFR-001 row on the
mission branch now states the exclusion and its reason explicitly. This lane was allocated before
that commit landed, so the copy read here was a stale snapshot. The scoping applied is therefore the
same as the spec's, not a divergence from it. The original observation is left standing above rather
than overwritten, because a lane reading a stale artifact and saying so is the behaviour this
evidence file exists to encourage.

### Hygiene sweep (NFR-001)

**Method**: `grep` across all eight files for each of the five leak-pattern categories NFR-001
names, using the same three regex shapes this pack's CI gate 12 (`scripts/pack_ci.py`'s
`C003_LEAK_PATTERNS`) compiles for four of the five categories, plus a manual literal check for the
fifth (this pack's prior-local-research-directory name), since no compiled pattern for that
category exists anywhere in `pack_ci.py`. **Files checked: 8 of 8.**

| # | Category | Raw match count (8 files) | Named exception | Residual |
|---|---|---|---|---|
| 1 | Linux-home-rooted absolute path | 0 | none | 0 |
| 2 | macOS-home-rooted absolute path | 0 | none | 0 |
| 3 | Home-directory environment-variable reference | 0 | none | 0 |
| 4 | This workspace's internal checkout-root name (literal) | 0 | none | 0 |
| 5 | Reference to this pack's prior-local-research directory by name (manual-only category) | 0 | none | 0 |

**Raw totals minus named exceptions: 0 - 0 = 0 for every category. Residual: 0. Files checked: 8/8.**

### Neutrality sweep (NFR-002)

**Method**: `grep` across all eight files for each of the pack's 4-entry banned-terms list
(confirmed this session against the installed CLI's own `banned_terms.yaml`: `PY-001` literal
"pytest", `PY-002` literal "junit", `PY-003` regex `\bpip install\b`, `PY-004` regex
`\bpython -m\b`). **Files checked: 8 of 8.**

| # | Pattern | Raw match count (8 files) | Named exception | Residual |
|---|---|---|---|---|
| PY-001 | literal "pytest" (case-insensitive check run) | 0 | none | 0 |
| PY-002 | literal "junit" (case-sensitive per the scanner's default) | 0 | none | 0 |
| PY-003 | regex `\bpip install\b` | 0 | none | 0 |
| PY-004 | regex `\bpython -m\b` | 0 | none | 0 |

**Raw totals minus named exceptions: 0 - 0 = 0 for every pattern. Residual: 0. Files checked: 8/8.**

**JUnit-XML phrasing check**: none of the eight files mentions "JUnit-XML result format" or any
lowercase "junit" variant at all. The title-case-required phrasing rule therefore has nothing to
check against in this file set today — recorded here as not-applicable, not as a silent pass.

---

## Summary

- **T014**: 7 of 7 template files checked for the three term pairs — 0 competing definitions found.
  The WP04-deferred comment-block cross-file check ran over 7 of 7 template files — 0 inconsistent
  statements of the three FR-013 facts found (one immaterial line-wrap variance noted, not a
  defect).
- **T015**: 8 of 8 authored files checked against their governing FR row's required
  sections/fields — 8/8 PASS. 3 of 3 WP-shaped frontmatter blocks parse as valid YAML. 8 of 8 files
  confirmed valid UTF-8 Markdown. The M-01 naming seam is closed in both of the 2 files it applies
  to.
- **T016**: 8 of 8 authored files checked for hygiene (5 categories) and neutrality (4 patterns),
  scoped to exclude `reviews/` per the documented, non-narrowing reason stated above — raw match
  counts are 0 for every category and pattern in both sweeps; residual is 0 for both.
- **No defect was found in any of the seven templates or the README during this WP's sweeps.**
- Every result above was produced by a named manual step (a direct file read, a `grep` invocation,
  or a one-off script-assisted YAML/UTF-8 parse run this session) — no automated CI gate performs
  any part of T014, T015, or T016 today (spec.md Decision 8 / Constraint C-004). Nothing in this
  file should be read as "an automated gate confirmed this."
