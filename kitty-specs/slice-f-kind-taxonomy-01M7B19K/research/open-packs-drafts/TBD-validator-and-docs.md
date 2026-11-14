# open-packs#TBD-validator-and-docs: the validator, its tests and the docs follow slice F

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `slice-f-kind-taxonomy-01M7B19K` (WP11).
> The orchestrator files it and replaces `TBD-validator-and-docs` with the issue number.

## Title

Follow charter slice F in `validate_pack_schemas.py`, its tests and the docs, and move the charter pin

## Why

Charter slice F (spec-kitty-charter#537, ADR-0077) changes the pack contract that open-packs validates against:

- the `mission_step_contract` kind, its schema and its file kinds are retired; `*.step-contract.yaml` and
  `mission_step_contract.graph.yaml` are `retired:` rules in `file-kinds.yaml`;
- `impacts`, `replaces` and `delegates_to` leave the `Relation` enum of `graph-shard` and `org-fragment`; a
  `replaces` edge is written `overrides` instead (open-packs has none at `1692a3b`);
- `template_set` leaves `charter.schema.json`, and `action_sequence` and `template_set` are refused by name in a
  mission type;
- each relation names the source and target kinds it may join (the endpoint table, ADR-0077 decision 5);
- a step carries `substeps`, and each delegation candidate needs a `scope` edge from the step's action.

The validator still names the retired kind in three constants and its tests build step-contract fixtures. It also
classifies files in the order `files`, `exclusions`, `retired`, while the charter lets a `retired` rule beat a
`files` rule that also matches (unless an exclusion matches too; charter WP08, pinned by its `RetiredFilesTest`).
Under the new contract the open-packs validator therefore reads a leftover `mission_step_contract.graph.yaml` as a
graph shard and reports schema errors, where the charter refuses it by name as retired. The docs describe the old
layout.

## What

### `scripts/ci/validate_pack_schemas.py`

1. `classify()`: consult `exclusions`, then `retired`, then `files`, then `payloads`, so that a retired rule beats a
   `files` rule that also matches and an exclusion still beats both, as `contracts/packs/file-kinds.yaml` states.
   Reproduced here: with the slice F contract, `packs/writing/mission_step_contract.graph.yaml` reports 14 graph-shard
   schema errors instead of one `retired layout` violation.
2. `KNOWN_KIND_TOKENS`: drop `step-contract`.
3. `MANIFEST_EXEMPT_NODE_KINDS`: drop `mission_step_contract`, and drop "step contracts" from the two comments above
   it.
4. `ARTIFACT_SCHEMAS`: drop `mission-step-contract`; rewrite the docstring example (`qa-plan.step-contract.yaml
   declares mission_step_contract:qa-plan`) with a live kind, for example `qa-001.directive.yaml declares
   directive:QA_001`.
5. No relation list lives in the validator; the retired relations are refused through the contract's schemas. Check
   that no other code path names `impacts`, `replaces` or `delegates_to` (none does at `1692a3b`).

### `tests/ci/test_validate_pack_schemas.py`

1. `test_exempt_graph_node_kinds_need_no_constituent_but_an_unknown_kind_does`: drop `mission_step_contracts` from the
   exempt list, and assert that a `mission_step_contracts` node is now an unknown kind (it fails like `widgets`).
2. `test_only_file_backed_doctrine_kinds_are_constituents`: drop `mission-step-contract` from the exempt tuple.
3. The cross-pack test that builds `steps/qa-plan.step-contract.yaml` files (the `filed`, `wrong` and `other`
   packs, around lines 425 to 455): rebuild it on a live file-backed kind (for example `*.procedure.yaml` with
   `activated_procedures`), keeping each assertion's meaning: a file-only id satisfies a preset only when the contract
   classifies files, a file and a node of one pack are one declaration, the right id of the wrong kind does not
   satisfy a preset, and a file-only id shared by two packs of different levels collides.
4. `CompositionChecks.CONTRACT`: drop the `("step-contract", "mission-step-contract")` rule.
5. New: a pack holding `x.step-contract.yaml` and `mission_step_contract.graph.yaml` under a contract with the two
   `retired` rules and a `**/*.graph.yaml` files rule reports exactly two `retired layout` violations and no schema
   error (the precedence of item 1).

### Pack prose that names a retired shape

- `packs/pack-authoring/agent_profiles/doctrine-daphne.agent.yaml` (around line 177): "step contracts under
  `missions/built_in_step_contracts/`" is no artifact home any more; say that a step's substeps live in its
  mission-type file.
- `packs/cave-kitty/missions/cave-dev/steps/{specify,plan,tasks}.prompt.md`: "`template_set: null`" names a retired
  field; say that cave-dev ships and pins no templates.
- `packs/foundation/missions/research/steps/output.guidelines.md`: see draft
  [TBD-fold-step-contracts](TBD-fold-step-contracts.md), item 10 (if not done there).
- `packs/foundation/missions/research/steps/gathering.guidelines.md` (lines 27 and 51) and `gathering.prompt.md`
  (line 90): "the step contract requires at least three sources" and "the minimum-source threshold from the step
  contract" name the retired kind; the threshold is the research type's `gathering` step (its substeps).
- `packs/foundation/README.md` (line 5, which `docs/packs/foundation.md` line 31 is generated from): "mission types
  with their step contracts and templates" becomes "mission types, with their steps' substeps, and templates".
- `packs/qa/README.md` (lines 10, 33 and 155; `docs/packs/qa.md` line 36 is generated from it): drop "step contracts"
  from the inventories, and "Step-contract bootstrap commands" becomes "The `bootstrap` substeps' commands".
- `packs/foundation/missions/README.md` (line 35): "(mission-type files, prompts, templates, step contracts)" drops
  "step contracts".
- `packs/cave-kitty/README.md` (lines 11 and 118): "re-states the built-in `software-dev` step contract as prose"
  becomes "re-states the built-in `software-dev` steps' substeps as prose".

### Docs

- `docs/guides/author-a-charter-pack.md`: remove the `mission_step_contracts/` line of the layout tree and the
  "Mission step contracts" row of the id table; add a short section on a step's `substeps` (fields, the id grammar,
  `[<pack>::]<id>` candidates, the `scope` edge each candidate needs, the `mission_type -requires-> action` edge every
  step needs); add the `guards/` and `lifecycles/` directories (draft
  [TBD-pre-review-guard](TBD-pre-review-guard.md)); state the endpoint table or link the charter's ADR-0077.
- `docs/contracts/index.md` (lines 165 to 167): the relation list drops `delegates_to` and `replaces`, and says that
  a former `replaces` edge is written `overrides` and that each relation has allowed endpoint kinds.
- `docs/artefacts/index.md`: remove the "Mission step contract" row (line 29) and the "Mission step contract" section
  (lines 106 to 108); mention substeps on the mission-type row.
- `docs/guides/validate-a-pack.md` (line 100): the manifest-exempt list drops "step contracts".
- `docs/architecture/adr/2026-11-11-1-split-built-in-into-foundation-writing-and-opt-in-bundles.md` (line 99): add a
  dated amendment note: "Step-contract `delegates_to` candidates stay bare" no longer holds; the step contracts are
  folded into substeps, and a candidate held by another pack is written `<pack>::<id>` (charter ADR-0077 decision 2).
- `docs/packs/catalog-proposal.md` (line 319): "mission-type/step-contract machinery" becomes "mission-type machinery".
- `docs/packs/{foundation,writing,qa,cave-kitty}.md`: regenerate with `python3 scripts/docs/gen_pack_reference.py`
  after drafts 1 to 4 (the step-contract inventories go; the page drift gate needs it).
- `docs/changelog/CHANGELOG.md`: one entry for the migration, naming drafts 1 to 5 and the charter pin.

### The charter pin

- `charter.lock.yaml`: move `commit` to the charter commit that merges slice F into `main`, and set `release_tag` and
  `jar_sha256` once the charter publishes that release (ADR-0070). This lands in the same pull request as draft
  TBD-fold-step-contracts and this draft: the fold is refused by the current pin's schema (`'substeps' was
  unexpected`), and the current tree is refused by the new one (28 `retired layout` violations, plus schema errors on the 2
  fragments until item 1 lands).

Not changed: `.kittify/charter/charter.yaml` (`selected_mission_step_contracts: []`, and `catalog.template_set` at
line 40) is generated by core's CLI and follows core (ADR-0077, Risks).

## Acceptance

- `python3 -m unittest tests.ci.test_validate_pack_schemas` passes, with the new retired-precedence test.
- `scripts/ci/validate-packs.sh` and `scripts/ci/check-pack-digests.sh` pass at the new pin.
- `scripts/docs/gen_pack_reference.py --check` passes.
- `git grep -n -i -e step-contract -e step_contract -e 'step contract' -- scripts tests packs docs ':!docs/changelog'`
  (which also matches `mission_step_contract` and "Step-contract") finds only the amendment notes and the new
  retired-file test.
- The charter service job registers every pack at the new pin.

## Files

- `scripts/ci/validate_pack_schemas.py`, `tests/ci/test_validate_pack_schemas.py`
- `packs/pack-authoring/agent_profiles/doctrine-daphne.agent.yaml`,
  `packs/cave-kitty/missions/cave-dev/steps/{specify,plan,tasks}.prompt.md`, `packs/cave-kitty/README.md`,
  `packs/foundation/README.md`, `packs/foundation/missions/README.md`,
  `packs/foundation/missions/research/steps/gathering.{guidelines,prompt}.md`, `packs/qa/README.md`,
  `packs/{pack-authoring,cave-kitty,foundation,qa}.source.yaml` (digests)
- `docs/guides/author-a-charter-pack.md`, `docs/guides/validate-a-pack.md`, `docs/contracts/index.md`, `docs/artefacts/index.md`,
  `docs/architecture/adr/2026-11-11-1-split-built-in-into-foundation-writing-and-opt-in-bundles.md`,
  `docs/packs/catalog-proposal.md`, `docs/packs/{foundation,writing,qa,cave-kitty,pack-authoring}.md` (regenerated),
  `docs/changelog/CHANGELOG.md`
- `charter.lock.yaml`

## Checked here

In a scratch copy of open-packs with drafts 1 to 3 applied, against the charter build's `contracts/packs` (lane
through WP10): `validate-packs.sh` passed, `--cross-pack` over the 19 packs reported 0 violations, and the 36 tests of
`tests.ci.test_validate_pack_schemas` passed unchanged (they build their own contracts, so they pass while still naming
the retired kind; items above remove those names). `check-pack-digests.sh` failed only on the four changed packs' digests
(foundation, writing, qa, cave-kitty), as expected before the restamp, and `gen_pack_reference.py` rewrote the four pages.
