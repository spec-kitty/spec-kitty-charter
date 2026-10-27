# Research: Whole-pack gate known-failure list

## Pre-spec squad (2026-10-27)

Point-cut: pre-spec. Question: what is the right design seam for #247, and which acceptance criteria could pass
fakely? Lenses: `architect-alphonso` and `reviewer-renata`, read from their profile YAML (the CLI was not installed
when the squad ran; degraded fallback per the procedure). Verdicts: architect "go", reviewer "not ready as written"
until the overlap with the existing ratchet was defined; they converge, so no second opinion was taken.

### Findings and dispositions

| # | Lens | Severity | Finding | Disposition |
|---|------|----------|---------|-------------|
| 1 | reviewer | HIGH | The `files:` section of `expected-red.yaml` already gives three of the four #247 cases (listed red passes, listed green fails, unlisted red fails, missing issue refused); tests against it would be green on day one | accepted: a separate list, a distinct status and an open-packs-only issue format (spec FR-001, FR-004; data-model.md) |
| 2 | reviewer, architect | HIGH, MED | Issue validation only checks non-blank; `#12` (a charter issue) or `TODO` would pass | accepted: `^spec-kitty/spec-kitty-open-packs#[1-9]\d*$`, refused at load (FR-004; decision `01M5Z73CH2336MGVKF64EJ444G`) |
| 3 | reviewer | HIGH | `verifyAll` does not run the whole-pack gate, so "verifyAll green" proves nothing about the real list | accepted: an untagged test loads the checked-in list (NFR-002); evidence also names `:packs:wholePackRoundTrip` (SC-002) |
| 4 | architect | HIGH | A path-only entry would hide any new loss in that file; `atOf` reduces every refusal to `refused` | accepted: `at` is required; for a refused known failure the whole message is compared (FR-006) |
| 5 | architect | HIGH | A section in `expected-red.yaml` collides with four sibling sessions and mixes two lifecycles (mission landing vs pin bump) | accepted: separate `known-failures.yaml` (FR-001, C-001) |
| 6 | architect, reviewer | MED | Interactions unspecified: path not in census, excluded, unmodelled kind, also in expected red, duplicates | accepted: `STALE_KNOWN_FAILURE`, `KNOWN_FAILURE_UNMODELLED`, `AMBIGUOUS_ENTRY`, load-time duplicate refusal (FR-007) |
| 7 | architect, reviewer | MED | Report: header loops over `Status.values()` but `kindTable` hard-codes columns; the report has no dedicated test | accepted: column appended last (decision `01M5Z7T5QET0XX0GDP5GE1ZNSH`), text assertions on the header and the row (FR-008) |
| 8 | reviewer | MED | "Self-expiring" undefined | changed: #247 defines it as "a listed file that now passes fails the gate"; no clock or date |
| 9 | architect | LOW | ADR-0036 says "keyed by issue and file"; one entry per path loses the second issue of a doubly broken file | deferred_with_rationale: one entry per file is simpler and keeps one pinned difference per file; confirmed by the operator in decision `01M5Z73CH2336MGVKF64EJ444G` |
| 10 | architect | INFO | The two current `files:` entries (#235, #236) are service defects | accepted: they stay expected red; the list ships empty (spec Assumptions) |

## Decisions

- **Separate resource, not a section.** Decision: `whole-pack/known-failures.yaml`. Rationale: no collision with siblings, one lifecycle per file. Alternatives: a `known-failures:` key in `expected-red.yaml` (rejected: shared file, mixed lifecycles).
- **Exact issue format.** Decision: `spec-kitty/spec-kitty-open-packs#N` only. Rationale: one canonical surface; a bare `#N` reads as a charter issue. Alternatives: full URL too (rejected: two spellings).
- **Status appended last.** Decision: `KNOWN_FAILURE` after `RED`; report column after `red`. Rationale: existing ordering, columns and assertions stay byte-stable. Alternatives: beside expected red (rejected: edits existing rows).
- **2-arg `load` delegates.** Decision: keep `load(red, exclusions)` and add `load(red, exclusions, known)`. Rationale: the existing tests stay untouched. Alternatives: change every caller (rejected: collision surface).
