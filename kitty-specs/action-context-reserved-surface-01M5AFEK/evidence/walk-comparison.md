# Walk evidence against core (WP06, SC-001)

Evidence, not a gate. The gate is the corpus harness (#9) over core's CLI.

## Commits used

| What | Commit |
|---|---|
| core (oracle, read-only) | `9554e7b26afe7a61519f7744d0147346ab36f197` |
| open-packs (the commit `OPEN_PACKS_REF` pins in `.github/workflows/service.yml`) | `7f063aecbdede29d05cc4d5fc9a5b13f9c7434d7` |
| service (lane-a worktree, WP01 as merged there) | `5b6a4ec410d6bc3b3fcccd242dd6377fc1d95bf1` |

## Method

1. `core_walk.py` loads open-packs `built-in/` with core's `load_graph_or_dir` and calls
   `resolve_context(graph, action, depth=d)` for every `action:` node (24 actions, 4 mission types)
   at d = 0, 1, 2. Output: `core-walk.json` (artifacts and glossary_scopes, sorted).
2. `service_walk.py` GETs `/api/v1/mission-types/<mt>/actions/<a>/context?depth=<d>` for the same
   72 cells from the real HTTP service (`bootRun` over open-packs `built-in`, option (a)).
   Output: `service-walk.json` (compact). All 72 answers were HTTP 200.
3. `compare.py` compares URN sets per cell. Charter set S = guidance entries with a relation other
   than `vocabulary`; charter vocabulary = entries with relation `vocabulary`, compared with core's
   `glossary_scopes` (DIV-106). Charter-minus-core is classified against a reference model that
   re-runs core's own `walk_edges`: the transitive `requires` of the artifacts core lists as
   suggested (research.md D2 steps 3 and 4), minus what core already lists. A cell is DIV-110 only
   when the extras equal that model exactly; any other extra or missing URN is UNREGISTERED.
   It also checks the via/hops invariant over every real answer.

Reproduce (from a directory outside open-packs; `EV` is this directory):

```bash
EV=/home/user/spec-kitty-charter/kitty-specs/action-context-reserved-surface-01M5AFEK/evidence
OP=/home/user/spec-kitty-open-packs   # at 7f063ae
LANE=/home/user/spec-kitty-charter/.worktrees/action-context-reserved-surface-01M5AFEK-lane-a  # at 5b6a4ec
cd /tmp
/root/.venv-sk/bin/python -I $EV/core_walk.py $OP > $EV/core-walk.json

export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH
(cd $LANE && ./gradlew :charter-service:bootRun --args='--charter.packs[0].name=built-in --charter.packs[0].level=built-in --charter.packs[0].path='$OP'/built-in --server.port=18080') &
# wait for "Started CharterServiceApplication", then:
python3 -I $EV/service_walk.py $EV/core-walk.json http://localhost:18080 > $EV/service-walk.json
# stop the service, then:
/root/.venv-sk/bin/python -I $EV/compare.py $OP $EV/core-walk.json $EV/service-walk.json
```

(`service-walk.json` in this directory was minified with `json.dump(..., separators=(',', ':'))`
to stay under the size budget; contents are unchanged.)

## Result

- 24 actions x 3 depths = 72 cells; 65 identical, 7 differ, all 7 by DIV-110.
- **core minus charter is empty in all 72 cells.** The charter loses nothing core lists.
- Totals of |S| over all actions:

| depth | core | charter | core - charter | charter - core |
|---|---|---|---|---|
| 0 | 296 | 296 | 0 | 0 |
| 1 | 397 | 434 | 0 | 37 |
| 2 | 467 | 497 | 0 | 30 |

The depth-1 charter total, 434, is the figure research.md D2 quotes.

## Differences, classified

| Cell | charter - core | Relation of the extras | Register |
|---|---|---|---|
| documentation/design d2 | 1 (`directive:DIRECTIVE_031`, via `DIRECTIVE_032`, hops 4) | requires | DIV-110 |
| software-dev/implement d1 | 16 (1 styleguide, 4 procedure, 8 tactic, 3 directive) | requires | DIV-110 |
| software-dev/implement d2 | 11 (2 directive, 6 tactic, 3 procedure) | requires | DIV-110 |
| software-dev/plan d1 | 15 (11 tactic, 4 directive) | requires | DIV-110 |
| software-dev/plan d2 | 14 (11 tactic, 3 directive) | requires | DIV-110 |
| software-dev/review d1 | 6 (1 styleguide, 4 tactic, 1 procedure) | requires | DIV-110 |
| software-dev/review d2 | 4 (2 tactic, 1 procedure, 1 directive) | requires | DIV-110 |

In every one of the 7 cells the extras are exactly the transitive `requires` of the suggested
artifacts that core lists no other way. Each has relation `requires`. None is `suggests` or
`scope`, which also confirms step 4 does not walk `suggests` from suggested artifacts. The depth-2
extras are fewer than the depth-1 ones because a depth-2 `suggests` hop lists some of these
artifacts as suggested, and core then lists them too.

Other register ids:

- **DIV-102 (undeclared scope target):** not exercised. Core's results contain no URN that is not a
  graph node (`core-walk.json` `undeclared` is empty in all 72 cells), so there is no
  core-lists-it, charter-refuses-it case in open-packs. The scenario fixture and the corpus case
  `undeclared-scope-target` pin it, not this content.
- **DIV-106 (vocabulary):** none. Open-packs has 0 `vocabulary` edges; core's `glossary_scopes` and
  the charter's `vocabulary` entries are both empty in all 72 cells. The vocabulary walk is pinned
  by scenario fixtures only.
- **DIV-105 (ordered answer, `relation`/`via`/`hops`):** by construction; the sets were compared
  as URN sets. The service's answers are sorted by URN in every cell.
- **DIV-101, 003, 007, 008, 009:** not in scope for a bare-walk comparison (no action-not-a-node,
  no load error, no activation, no tension annotation, no delivery slots were compared).
- `plan/*` (4 actions, 12 cells): both answer empty. Mission type `plan` has no action scope edges.

**UNREGISTERED differences: none.** No WP01 defect found: no depth off-by-one, no missing BFS
expansion (core minus charter = 0 and the charter extras match the reference model exactly in
every cell).

## via/hops invariant

Over all 1,227 entries (72 answers), 0 violations of:

- `via` is the action node (then `hops` = 1 and `relation` = `scope`) or another entry of the
  same answer, and `hops` = `hops(via)` + 1;
- each entry is a real graph edge of its own `relation` from its `via`;
- no duplicate URN, the action itself is never listed, entries are sorted by URN, `depth` is echoed.

## SC-001 verdict

**SC-001 holds.** Over open-packs 7f063ae, for all 24 actions at depths 0, 1 and 2, the service's
answer equals core's `resolve_context` at 9554e7b2 except for the registered DIV-110 extras
(7 cells; 37 extra entries at depth 1 and 30 at depth 2, summed over actions, all `requires` of
suggested artifacts), with core minus charter empty everywhere and no unregistered difference.

## Per-action, per-depth table

Columns: `core S` and `charter S` are the URN-set sizes; `core glossary` is core's
`glossary_scopes`; `charter vocab` is the number of `relation: vocabulary` entries.

| action | depth | core S | charter S | core-charter | charter-core | core glossary | charter vocab | register ids |
|---|---|---|---|---|---|---|---|---|
| action:documentation/accept | 0 | 5 | 5 | 0 | 0 | 0 | 0 | - |
| action:documentation/accept | 1 | 5 | 5 | 0 | 0 | 0 | 0 | - |
| action:documentation/accept | 2 | 5 | 5 | 0 | 0 | 0 | 0 | - |
| action:documentation/audit | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:documentation/audit | 1 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:documentation/audit | 2 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:documentation/design | 0 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:documentation/design | 1 | 9 | 9 | 0 | 0 | 0 | 0 | - |
| action:documentation/design | 2 | 14 | 15 | 0 | 1 | 0 | 0 | DIV-110 |
| action:documentation/discover | 0 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:documentation/discover | 1 | 10 | 10 | 0 | 0 | 0 | 0 | - |
| action:documentation/discover | 2 | 10 | 10 | 0 | 0 | 0 | 0 | - |
| action:documentation/generate | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:documentation/generate | 1 | 10 | 10 | 0 | 0 | 0 | 0 | - |
| action:documentation/generate | 2 | 10 | 10 | 0 | 0 | 0 | 0 | - |
| action:documentation/publish | 0 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:documentation/publish | 1 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:documentation/publish | 2 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:documentation/retrospect | 0 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:documentation/retrospect | 1 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:documentation/retrospect | 2 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:documentation/validate | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:documentation/validate | 1 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:documentation/validate | 2 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:plan/plan | 0 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/plan | 1 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/plan | 2 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/research | 0 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/research | 1 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/research | 2 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/review | 0 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/review | 1 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/review | 2 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/specify | 0 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/specify | 1 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:plan/specify | 2 | 0 | 0 | 0 | 0 | 0 | 0 | - |
| action:research/gathering | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/gathering | 1 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/gathering | 2 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/methodology | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/methodology | 1 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:research/methodology | 2 | 8 | 8 | 0 | 0 | 0 | 0 | - |
| action:research/output | 0 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:research/output | 1 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:research/output | 2 | 4 | 4 | 0 | 0 | 0 | 0 | - |
| action:research/retrospect | 0 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:research/retrospect | 1 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:research/retrospect | 2 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:research/scoping | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/scoping | 1 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:research/scoping | 2 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:research/synthesis | 0 | 6 | 6 | 0 | 0 | 0 | 0 | - |
| action:research/synthesis | 1 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:research/synthesis | 2 | 7 | 7 | 0 | 0 | 0 | 0 | - |
| action:software-dev/implement | 0 | 63 | 63 | 0 | 0 | 0 | 0 | - |
| action:software-dev/implement | 1 | 101 | 117 | 0 | 16 | 0 | 0 | DIV-110 |
| action:software-dev/implement | 2 | 129 | 140 | 0 | 11 | 0 | 0 | DIV-110 |
| action:software-dev/plan | 0 | 21 | 21 | 0 | 0 | 0 | 0 | - |
| action:software-dev/plan | 1 | 31 | 46 | 0 | 15 | 0 | 0 | DIV-110 |
| action:software-dev/plan | 2 | 39 | 53 | 0 | 14 | 0 | 0 | DIV-110 |
| action:software-dev/retrospect | 0 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:software-dev/retrospect | 1 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:software-dev/retrospect | 2 | 11 | 11 | 0 | 0 | 0 | 0 | - |
| action:software-dev/review | 0 | 71 | 71 | 0 | 0 | 0 | 0 | - |
| action:software-dev/review | 1 | 103 | 109 | 0 | 6 | 0 | 0 | DIV-110 |
| action:software-dev/review | 2 | 128 | 132 | 0 | 4 | 0 | 0 | DIV-110 |
| action:software-dev/specify | 0 | 19 | 19 | 0 | 0 | 0 | 0 | - |
| action:software-dev/specify | 1 | 19 | 19 | 0 | 0 | 0 | 0 | - |
| action:software-dev/specify | 2 | 19 | 19 | 0 | 0 | 0 | 0 | - |
| action:software-dev/tasks | 0 | 20 | 20 | 0 | 0 | 0 | 0 | - |
| action:software-dev/tasks | 1 | 28 | 28 | 0 | 0 | 0 | 0 | - |
| action:software-dev/tasks | 2 | 31 | 31 | 0 | 0 | 0 | 0 | - |

Totals per depth (core, charter, core-charter, charter-core): {'0': [296, 296, 0, 0], '1': [397, 434, 0, 37], '2': [467, 497, 0, 30]}
Actions: 24 cells: 72
Cells by register ids: {'-': 65, 'DIV-110': 7}
Undeclared nodes in core results: []
Invariant: {'entries': 1227} violations: 0
Findings (unregistered): 0
