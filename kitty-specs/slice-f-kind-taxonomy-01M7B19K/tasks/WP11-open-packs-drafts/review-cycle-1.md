---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T19:10:33Z'
reviewer_agent: claude-reviewer
wp_id: WP11
---

# WP11 review, cycle 1: changes requested (claude-reviewer)

Reviewed commit 2db03010a on `slice-f-537-kind-taxonomy`. The commit is signed (SSH ed25519, verified), has no
Co-Authored-By trailer and names no model, and touches only the research paths. The counts, the rename map, the
census re-run and the closeout evidence all hold (see "Verified" below). One MEDIUM finding blocks approval.

## Findings

**Issue 1 (MEDIUM): draft 3 depends on draft 2, but the ordering note says drafts 2 to 4 may land in any order.**

- Where: `research/open-packs-drafts/README.md:29-30` ("They may land first, in any order") and
  `TBD-delegation-scope-edges.md:51-52` ("valid under the current charter pin too ... independent of the fold").
- Why: 18 of draft 3's 79 edges have `action:qa/<step>` as their source, and qa has no `action` node at `1692a3b`.
  Only draft 2 adds those 7 nodes. Re-run on a scratch copy of `1692a3b` with only `apply-drafts.py --drafts 3`, against
  the current pin's contract (`17ce3f9`): `validate-packs.sh` exits 1 with
  `cross-pack: qa/drg/fragment.yaml: edge source 'action:qa/accept' resolves to no node of any pack` (and the same for
  the other qa actions). Drafts 2+3 together pass, and so does draft 2 alone.
- Fix: in the README, say that draft 3 lands with draft 2 or after it. Also say that drafts 2 and 3 must be in the
  tree by the pin-move pull request, because the service job refuses the fold without them (draft 1 already says
  this). In draft 3, add the dependency on draft 2 to "What" or "Acceptance", and correct the "independent" sentence.

**Issue 2 (LOW): wrong count.** `TBD-delegation-scope-edges.md:36-37` says writing's `action.graph.yaml` already uses
the `foundation::` form for "its 20 existing ones". At `1692a3b` it has 32 edges with a `foundation::` target, and 2
more with a `foundation::` source.

**Issue 3 (LOW): draft 5's file list misses prose that names the retired kind with a space.** The acceptance grep in
`TBD-validator-and-docs.md:107` cannot catch these, because it searches only for `step-contract`, `step_contract` and
`mission_step_contract`:

- `docs/artefacts/index.md:106-108`: the whole "## Mission step contract" section. The draft removes only the row at
  line 29.
- `docs/guides/validate-a-pack.md:100`: "except actions, ..., mission types and step contracts".
- `packs/foundation/README.md:5` (source of `docs/packs/foundation.md:31`) and the qa text behind
  `docs/packs/qa.md:36`: "with their step contracts".
- `packs/foundation/missions/README.md:35`.
- `packs/cave-kitty/README.md:11,118`.
- `packs/foundation/missions/research/steps/gathering.guidelines.md:27,51` and `gathering.prompt.md:90`: "the step
  contract requires ...".

Also, the "Not changed" note (line 99) does not mention `.kittify/charter/charter.yaml:40` `catalog.template_set`.
That file is core-generated, as ADR-0077's Risks says of charter's own copy; say so in one line.

**Issue 4 (LOW): the closeout pull request text.** At `closeout-evidence.md:14`, `Closes #112, #514, #520, #515, #533,
#350.` lets GitHub close only #112, because a comma list links only its first issue. Write `Closes #N` once per
issue. At `closeout-evidence.md:90-91`, the bare `#TBD-step-requires-edges` placeholders and the others lack the
`open-packs` prefix, so a mechanical placeholder replacement can miss them. Spell each one in full.

**Issue 5 (LOW): an acceptance criterion that cannot be met.** `TBD-pre-review-guard.md:158`: "No
`handler_kind: mission_step_contract` gate remains". Line 149 and the README let draft 4 land before the fold, so this
cannot hold when draft 4 lands. Move the criterion to draft 1, or make it conditional.

**Issue 6 (LOW): incomplete control description.** `README.md:48`: on the untouched checkout, `census-check.py` also
reports 42 schema violations: the 2 `mission_step_contract.graph.yaml` fragments against the slice F graph-shard
schema (foundation 28, writing 14). Mention them, so a reader of the control is not surprised.

## Verified (no change needed)

- Every count matches `1692a3b`: 28 contracts, 149 substeps, 115 renames, 144 candidates (34 `foundation::`), 26
  `requires` edges plus 23 action nodes (cave-dev 8, qa 7, software-dev 8/7, research 2/1, documentation 1/0), and 79
  scope edges for 80 uses (foundation 47, qa 18, writing 14). The candidate kinds are 86 directive, 50 tactic,
  6 procedure, 1 toolguide and 1 paradigm. There are 47 steps, with 21 existing `requires` edges. The four generated
  tables in the drafts are byte-identical to the output of `apply-drafts.py --tables`.
- Every renamed substep id matches `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (at most 64 characters), with no collision within a
  step. The step contracts carry no field outside id, description, command, delegates_to and guidance, and no literal
  block scalars, so `clean()` loses no meaning.
- Census re-run on a fresh scratch copy (drafts 1 to 3 applied; lane-j exported for `edge-census.py`): 1,436
  open-packs edges, with 0 refused edges, 0 unlinked steps, 0 unresolved candidates, 0 unscoped candidates, 0 retired
  files and 0 schema violations. The census-check endpoint table equals `KindTaxonomy.model.yaml` except
  `implemented_by`, which is derived and never authored.
- The controls behave as reported. The untouched copy shows 26 unlinked steps and 30 retired files. With one qa scope
  edge removed and a `procedure -requires-> action` edge added, the check shows 1 unscoped candidate and a refused
  edge (counted once in the census and once in the tree), with exit 1.
- The ordering claims for drafts 1 and 5 hold. The pin's schema refuses `substeps` (28 errors). The new contract gives
  28 `retired layout` violations and 14 schema errors on writing's fragment. `validate-packs.sh` passes with drafts 1
  to 3 under the lane-j contract, and the open-packs unit tests pass (36). The digest check fails only on cave-kitty,
  foundation, qa and writing, before the restamp.
- Draft 4's guard and lifecycle validate against both schemas, and `validate-packs.sh` passes at the pin once the
  manifest entries are added. The 27 transitions are exactly `specify_cli.status.transitions.ALLOWED_TRANSITIONS`
  among the nine lanes.
- Closeout: every named scenario, test class, test method, planted pack, example, path, schema, change fragment and
  ADR amendment exists in lane-j and shows the behavior. The issue-to-FR mapping matches the spec. The #513 comment
  matches C-008. The #94 and #537 ticks are right.

## Orchestrator items (not WP11 findings)

- The example pack wrongly makes git-flow and execution-lanes paradigms. The drafts correctly keep toolguide and
  procedure.
- The "80 scope edges" wording elsewhere (spec FR-020, WP prompt) should read "80 candidate uses, 79 edges".
- `r31-edge-overrides-across-kinds/expected.json` covers `#514` where it should cover `#515`.
