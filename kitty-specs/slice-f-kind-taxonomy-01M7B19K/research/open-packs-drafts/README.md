# Open-packs drafts for slice F

Slice F (spec-kitty-charter#537, ADR-0077) changes the model that `spec-kitty-open-packs` follows. This session cannot
write to open-packs (charter.md; issue-tracker.md, Rule 4), so each change is drafted here with an
`open-packs#TBD-<slug>` placeholder. The orchestrator files each draft in `spec-kitty/spec-kitty-open-packs` as
written and replaces the placeholder with the issue number, here and in the drafts' cross-links.

Every count below was taken against open-packs at `1692a3b` (the charter's `openPacksRef`), and every generated table
comes from `apply-drafts.py`.

| Placeholder | Draft | What it does | Size |
|---|---|---|---|
| `open-packs#TBD-fold-step-contracts` | [TBD-fold-step-contracts.md](TBD-fold-step-contracts.md) | Folds the 28 step contracts into the `substeps` of their steps, renames substep ids to the kernel grammar, qualifies cross-pack candidates, removes the files and the 2 graph fragments | 28 contracts → 149 substeps on 28 steps of 5 mission types; 115 id renames; 144 candidates, 34 qualified `foundation::`; 30 files removed |
| `open-packs#TBD-step-requires-edges` | [TBD-step-requires-edges.md](TBD-step-requires-edges.md) | Links every step and on-demand step from its mission type | 26 `requires` edges (cave-dev 8, qa 7, software-dev 8, research 2, documentation 1), 23 action nodes |
| `open-packs#TBD-delegation-scope-edges` | [TBD-delegation-scope-edges.md](TBD-delegation-scope-edges.md) | A `scope` edge from each step's action to each delegation candidate | 79 edges for 80 candidate uses (foundation 47, qa 18, writing 14) |
| `open-packs#TBD-pre-review-guard` | [TBD-pre-review-guard.md](TBD-pre-review-guard.md) | Foundation's work-package lifecycle and the `spec-kitty-pre-review` guard on `in_progress -> for_review` (ADR-0054 decision 12) | 2 new files, 1 manifest |
| `open-packs#TBD-validator-and-docs` | [TBD-validator-and-docs.md](TBD-validator-and-docs.md) | The validator's retired kind and file precedence, its tests, pack prose, docs, and the charter pin | 2 scripts, about 12 docs and prose files, `charter.lock.yaml` |

Retired relations (`impacts`, `replaces`, `delegates_to`): open-packs authors none, so no edge migrates. The rule
that a `replaces` edge is written `overrides` is recorded in draft TBD-validator-and-docs for content that appears
later.

## Ordering

**Open-packs raises its charter pin only after slice F is merged into the charter's `main` and released
(ADR-0070).** Open-packs proves its content against `charter.lock.yaml`, so the charter's `main` is not blocked
meanwhile; the drift watch shows the gap (#509).

- **Before the pin moves:** drafts TBD-step-requires-edges, TBD-delegation-scope-edges and TBD-pre-review-guard are
  additive and valid under the current pin (`17ce3f9`), so they may land first, with one dependency:
  - **TBD-delegation-scope-edges lands with or after TBD-step-requires-edges.** 18 of its edges start from
    `action:qa/<step>` nodes, and only TBD-step-requires-edges adds them. Alone, it fails `validate-packs.sh`
    (`--cross-pack`: the source resolves to no node).
  - **TBD-pre-review-guard is independent** and may land at any time.
- **By the pin move, TBD-step-requires-edges and TBD-delegation-scope-edges must be in the tree** (or in the same
  pull request): the new pin refuses an unlinked step (`mission_step_unlinked`) and an unscoped candidate
  (`substep_delegation_unscoped`).
- **With the pin move, in one pull request:** TBD-fold-step-contracts and TBD-validator-and-docs. The current pin's
  `mission-type.schema.json` refuses `substeps`, and the new pin refuses every step-contract file, so neither half
  passes CI alone.
- **Then, in the charter:** `openPacksRef` in `gradle.properties` moves to the open-packs commit that holds the fold.
  `scripts/usability/service-usability-run.sh` expects the 5 software-dev steps with substeps (specify, plan, tasks,
  implement, review) and checks out `openPacksRef`, so a live usability run passes only after this move.

## What was checked here (T051)

On a scratch copy of open-packs `1692a3b` with drafts 1 to 3 applied by `apply-drafts.py` (the real checkout was
not touched):

- `research/edge-census.py` (the mission's census, run with `OPEN_PACKS_DIR` set to the copy, from an export of the
  charter build through WP10) counted 1,436 authored open-packs edges (1,331 + 26 + 79).
- `census-check.py` judged that census and the tree against the slice F model: **0 refused edges, 0 unlinked steps,
  0 unresolved candidates, 0 unscoped candidates, 0 retired files**, and 0 schema violations of the 6 mission types,
  the graph shards and the fragments against the build's `mission-type`, `graph-shard` and `org-fragment` schemas.
  On the untouched checkout the same check reports 26 unlinked steps, 30 retired files and 42 schema violations
  (foundation 28, writing 14: the nodes of the 2 `mission_step_contract.graph.yaml` fragments); on a copy with one scope
  edge removed and a `procedure -requires-> action` edge added it reports 1 unscoped candidate and 1 refused edge.
- Open-packs' own `scripts/ci/validate-packs.sh` and `--cross-pack` passed against the build's `contracts/packs`.

`edge-census.py` itself only counts edges by relation and kind; the zero counts come from `census-check.py`, which
reads the census CSV and adds the step-link and delegation checks. The charter service job of open-packs (which
composes and registers every pack) was not run: it needs the built charter jar at the new pin.

## Scripts

- `apply-drafts.py <copy> [--drafts 1,2,3] [--tables <dir>]`: applies drafts 1 to 3 to a copy of open-packs, editing
  files textually so their formatting stays; `--tables` writes the generated tables the drafts quote.
- `census-check.py <tree> <edge_census.csv> [<charter contracts/packs>]`: the zero check above; exit 0 only when
  every count is 0.
