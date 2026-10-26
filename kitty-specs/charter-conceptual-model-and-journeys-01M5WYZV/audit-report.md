# Audit report: charter conceptual model, journeys and scenarios

Validate step, 2026-10-26. Content gated against `spec.md` and `plan.md`.

**Verdict: ready-to-publish**, with one gate deferred to CI (contract suite run, see G7).

## Gates

| # | Gate | Evidence | Result |
|---|---|---|---|
| G1 | Docs checks (`check_docs.py all`: frontmatter, related, freshness, adr, changelog, terms, orphans, links) | run on the branch head | pass, 0 findings |
| G2 | Docs tooling tests (`pytest scripts/docs/tests`) | 24 passed | pass |
| G3 | Spelling (cspell 9.2.0, CI pin) on changed docs | 0 issues in `docs/` | pass |
| G4 | markdownlint-cli2 0.18.1 (CI pin, advisory) on changed docs | 0 findings after fixing two list-spacing issues in ADR-0024 | pass |
| G5 | NFR-002: no field, file or class names in the conceptual model body | fidelity reviewer grep: none outside the pointer section and appendices | pass |
| G6 | SC-002: every concept in Appendix B has a glossary entry | fidelity reviewer: all present; three entries corrected in fold-in | pass |
| G7 | Contract suite with the journey scenarios (NFR-003) | not runnable here (JDK 25 unavailable, download blocked); every non-`@WIP` step statically matched to a step definition, 0 unmatched; `@ExcludeTags("WIP")` checked against the JUnit Platform Suite version | **deferred to CI** on PR #199 |
| G8 | SC-001: 11 journeys × page, diagram, scenario file naming the journey | journeys reviewer: all 11 complete, links both ways | pass |
| G9 | SC-004: a cold reader answers five questions from the page alone | fresh-context reviewer: high confidence on four, medium-high on upgrades; the gaps it found were folded in | pass |
| G10 | ADR conventions (immutable decisions, inline annotations, index status) | architecture reviewer: only `updated:` lines removed from older records; ADR-0017 annotation added in fold-in | pass |
| G11 | Divio adherence | context pages `kind: Explanation`, hubs `kind: Hub`, ADRs `kind: ADR`; glossary pages generated | pass |
| G12 | Generator reproducibility (glossary) | pages regenerated from `site/.contextive/definitions.yaml` with the doc-kitty glossary generator source (no glossary change between the pinned 0.4.0 and 0.5.0); second run produces no diff | pass |

## Review squad (validate)

Four reviewers: fidelity to the operator's rulings, architecture (architect-alphonso), journeys and BDD
(reviewer-renata), and a cold reader (SC-004). No blockers. All findings folded in (19 items), except
two declined with rationale:

| Finding | Disposition |
|---|---|
| ADR-0028 decision 3 (tension alongside one structural relationship) decides what was open | `deferred_with_rationale`: the operator approved it in the mission scope (spec FR-010) |
| The Brain Kitty glossary entry contradicts the page | `changed`: the entry (governance product line) is right; the page now says "the commercial part of the Brain Kitty line" |

## Risks (pre-mortem)

| Risk | Disposition |
|---|---|
| A runnable journey scenario fails on the real suite (not run locally) | mitigate: CI runs the suite on PR #199; fix before ready-for-review |
| The branch conflicts with `main` (doc-kitty 0.5.0 upgrade, #119/#45 landing) | mitigate: rebase in closeout, regenerate the glossary with the pinned 0.5.0 |
| Readings made by agents, not ruled by the operator (role pin before selection; removal reported as skipped; `reconciles_tension` target; removal not "loosening") | accept: each is marked as an open question or a reading in its ADR or page, and listed for the operator in the PR |
| Core still holds the old glossary and ADR `2026-10-03-1` | accept: out of scope by constraint C-001; recorded in ADR-0029, ADR-0034 and open question #31 |
