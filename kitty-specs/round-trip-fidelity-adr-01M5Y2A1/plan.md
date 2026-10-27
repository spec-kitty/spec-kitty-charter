# Plan: Round-trip fidelity decision (ADR-0036)

**Mission**: `round-trip-fidelity-adr-01M5Y2A1` · **Branch**: `feat/round-trip-fidelity-adr` → `main`
**Inputs**: `spec.md`, `gap-analysis.md`, `research/grounding-squad.md`, operator rulings on #188.

## Divio allocation

| Area (spec.md) | Type | Where | Deferred |
|---|---|---|---|
| Round-trip equality per file kind | explanation + reference | ADR-0036 Decision 2 and its per-kind table | none |
| Unknown-field handling | explanation | ADR-0036 Decision 3; pointer in `architecture/descriptors.md` | Rewriting `descriptors.md` and the divergence register as current behaviour waits for the code change: they describe the code as it stands |
| DRG edge identity on round trip | explanation | ADR-0036 Decision 4; clarification on ADR-0027 and ADR-0025 | none |
| Filing pack inconsistencies | how-to (checklist inside the ADR) | ADR-0036 Decision 5 | A separate guide page: it would duplicate the checklist (spec C-002 scope) |
| Charter round-trip promise | explanation | ADR-0036 Decision 7 | The charter edit itself waits for operator question 2 |

Tutorials are out of scope (gap analysis: nobody learns a policy by following steps).

## Navigation and cross-links

- The ADR index (`docs/adr/README.md`) gets row 0036. The ADR's `related` frontmatter links ADR-0017,
  0024, 0025, 0027, 0028, 0035, `architecture/descriptors`, `architecture/logical-model` and
  `conformance/divergence-register`.
- Each superseded or clarified record gets a one-line dated pointer under its Status
  (docs/adr/README.md rules): ADR-0017 (R5/R13 superseded in part), ADR-0025 (D5 clarified),
  ADR-0027 (D2 clarified). Their index rows name it. `plans/open-questions.md` #29 notes the
  superseding decision. `architecture/descriptors.md` gets a **Planned** note (the page's own
  convention for decided-but-unimplemented behaviour).
- No navigation change: the ADR hub already lists every record.

## Design decisions (ADR-shaped)

1. **One ADR, not one per kind.** The rules are generic, and the per-kind differences are data (which
   lists are sets, their member identity). A per-kind table holds them, so #189's registry can carry
   the table as entry data. Alternative rejected: one ADR per kind would re-state the generic rules
   per kind and drift.
2. **Meaning is judged source against written.** Both files are read into one canonical form (the
   parsed YAML tree after the rules), not into the service's records. This catches a field the reader
   drops, which record-against-record cannot. Alternative rejected: record equality (today's corpus
   test) is blind to reader loss.
3. **The conservative list rule.** Order and repetition count in every list except the sets the ADR
   names (DRG edges and nodes). The writer keeps order, so this costs nothing, and no pack list
   silently loses its order. Alternative rejected: treating "obviously unordered" lists (`required_*`,
   tags) as sets needs a judgment per list and adds rules ruling 3 says not to add.
4. **Unknown fields reuse the schema-violation path.** No new code path or wire code. Alternative
   rejected: a dedicated `unknown_field` refusal would be special logic, and the existing path already
   renders "unknown key".
5. **No generator.** The docsite is hand-authored Markdown (ADR-0003).

## Generation slices

| Slice | Content | Files |
|---|---|---|
| G1 | ADR-0036 itself: rulings, generic rules, refusal, DRG canonical form, per-kind table, filing checklist, scope of schemaless kinds, charter reading, worked examples, consequences, alternatives | `docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md` |
| G2 | Index row, pointers on ADR-0017/0025/0027, open question #29, the Planned note in `descriptors.md`, changelog entry and index line, spelling words | `docs/adr/README.md`, `docs/adr/0017-…`, `0025-…`, `0027-…`, `docs/plans/open-questions.md`, `docs/architecture/descriptors.md`, `docs/changelog/2026-10-27-round-trip-fidelity-adr.md`, `docs/changelog/README.md`, `docs/_meta/spelling-words.txt` |

## Validation

- `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` and `npx cspell` on the changed files.
- An adversarial review squad on the ADR before it leaves Proposed (spec FR-012; charter: architecture
  changes get a squad). Cast: `reviewer-renata` (are the worked examples and rules non-fakeable and
  sufficient to build #187?), `architect-alphonso` (conflicts with ADR-0017/0024–0028/0035, the onion,
  the contract), `doctrine-daphne` (does it duplicate an existing authority, and are rulings and
  derived readings kept apart?). Each finding is dispositioned in `audit-report.md`.
- A fresh-context check: a delegate that has not seen the mission applies the ADR to the worked
  examples (spec SC-001).

## Risks

- The operator answers on #188 may change Decision 5 (expected failures) or Decision 7 (charter). The
  ADR stays **Proposed** until they do, and the PR hand-off lists them.
- #187 and #189 run in parallel and may read a draft. The hand-off names the decisions that are
  stable (rulings) and the ones that depend on an open answer.
