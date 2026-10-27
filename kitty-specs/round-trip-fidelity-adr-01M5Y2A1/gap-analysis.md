# Gap Analysis: Round-trip fidelity decision (ADR-0036)

**Analysis Date**: 2026-10-27
**Documentation Framework**: doc-kitty Markdown docsite (`docs/`)
**Iteration Mode**: `mission_specific`. The areas are the round-trip fidelity decision and the
pages that state today's fidelity behaviour, not the whole docsite.
**Evidence**: `research/grounding-squad.md` (squad findings and probes on `main` `0b2503b` and
open-packs `01edb4e`). Classification rule: frontmatter `type`/`kind`. Every row below was classified
from frontmatter, so confidence is high unless marked otherwise.

## Coverage matrix

| Area | tutorial | how-to | reference | explanation |
|---|---|---|---|---|
| Round-trip equality per file kind | n/a | n/a | missing | missing |
| Unknown-field handling | n/a | n/a | present-but-stale (`architecture/descriptors.md:84-89`) | present-but-stale (ADR-0017 R5/R13; `conformance/divergence-register.md` DIV-005/006; `plans/open-questions.md` #29) |
| DRG edge identity on round trip | n/a | n/a | present (`architecture/logical-model.md`) | present-but-stale (ADR-0027 D2 "lossless"; ADR-0025 D5) |
| Filing pack inconsistencies upstream | n/a | missing | present (`packs/internal/PROVENANCE.md`, vendoring only) | missing |
| Charter round-trip promise | n/a | n/a | n/a | present-but-stale (`.kittify/charter/charter.md:29`; `charter.yaml` PROJECT_002) |

Tutorial cells are `n/a`: nobody learns a fidelity policy by following steps. The audience
(contributors, pack authors, core maintainers; `spec.md`) needs the decision and a reference table.
The filing how-to fits inside the ADR as a checklist; a separate guide page would duplicate it.

## Drift findings (high priority: wrong is worse than missing)

| # | Docs path | States | Contradicting source of truth |
|---|---|---|---|
| D1 | `docs/architecture/descriptors.md:86-89`, `docs/conformance/divergence-register.md` DIV-005/006, `docs/conformance/divergences.yaml`, `docs/plans/open-questions.md:56` | An unknown `pack.yaml` key is tolerated with `pack_unknown_key` | Operator ruling 1 on #188 (2026-10-27): unknown fields are refused. These pages still describe the code correctly, so they are annotated as planned to change; the divergence entries close when the code changes. |
| D2 | `docs/adr/0017-…:57-58` (R5, R13) | Unknown keys are reported, never refused | Ruling 1. Superseded in part by ADR-0036. |
| D3 | `docs/adr/0027-…:59-62`, `docs/adr/0025-…` D5 | The stored form round-trips "losslessly"; a second edge is never dropped silently | Rulings 2 and 4: meaning, not bytes, and edges are compared canonically. Clarified by ADR-0036: the second edge on a pair is still not dropped, it is an inconsistency fixed in the pack. |
| D4 | `.kittify/charter/charter.md:29`, `charter.yaml` PROJECT_002 | "Round-trips exactly", "without loss or rewrite" | Ruling 2. The charter is changed only through the charter flow, so this is operator question 2 on #188. |
| D5 | `contracts/charter/schemas/DiagnosticCode.yaml:7-12`, contract scenario "A pack descriptor with an undeclared key loads" | `pack_unknown_key` is never refused | Ruling 1. This is a contract change; ADR-0036 names it as follow-up work (C-001: no contract or code edits in this mission). |
| D6 | `service/packs/build.gradle.kts:88-90` comment, `PackRoundTripTest.java:20` | "Every YAML file must survive unchanged"; a byte-level test that no longer exists | The tests compare records, not bytes. This is code (C-001), so it is noted for #187. |

## Gaps, ranked by user impact

| Rank | Gap | Impact | Audience | Closed by |
|---|---|---|---|---|
| 1 | No definition of "same meaning" per file kind | Blocks a task: #187 cannot be built | contributors | ADR-0036, Decision 2 and the per-kind table |
| 2 | No canonical DRG edge form | Blocks a task: #187 and #189 | contributors, pack authors | ADR-0036, Decision 4 |
| 3 | Unknown-field refusal path undefined; contract says the opposite | Blocks a task, and is drift | contributors, core maintainers | ADR-0036, Decision 3, plus pointers (D1, D2) |
| 4 | No filing procedure for pack inconsistencies | Blocks a task: the epic will find some (55 multi-edge pairs) | contributors, open-packs maintainers | ADR-0036, Decision 5 |
| 5 | Kinds without a schema have no strictness story | Blocks discoverability | contributors | ADR-0036, Decision 6 |
| 6 | Charter wording drift | Drift | everyone | Operator question 2; ADR-0036 states the reading |

## Ungoverned content

None found in the areas in scope: every page above carries frontmatter. The stale code comments
(D6) are not documentation pages.
