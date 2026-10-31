# Consolidate the mission tier into one mission-type file per type

Filed as spec-kitty/spec-kitty-open-packs#95. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0044.

## What

The charter service consolidates the mission tier of a Charter Pack into one sanitized file per mission type
(ADR-0044):
- `missions/<type>/<type>.mission-type.yaml` holds the type and two step lists: its steps in run order (`steps`) and
  the steps invoked on demand (`on_demand_steps`). Each step carries its role, one-line description (at most 160
  characters), expected outputs (postconditions), output templates and references.
- `missions/<type>/steps/<step>.prompt.md` holds each step's prompt. A prompt keeps its frontmatter `description` as
  its detailed description.
- `missions/<type>/steps/<step>.guidelines.md` holds each step's working notes.

This PR migrates the built-in, cave-kitty and qa mission tiers to that shape. The charter service's own writer
produced it, through a throwaway harness. An independent verifier re-derives every field from the files at
`5df92a5d`. Its result:
- 6 mission types, with 40 sequenced and 6 on-demand steps;
- 43 prompts and 17 guidelines, each byte-equal to its pinned source;
- every reference id resolves;
- every pinned key is carried or is a listed drop, and each repeated value is checked equal before it is dropped;
- only retired files are deleted, only the files named below are added or modified, and each changes only the lines
  named;
- 13 output templates resolve, 17 dropped step titles, 4 descriptions taken from prompt frontmatter, and 1
  uncarried action index, each counted independently;
- zero problems.

A self-test plants one defect of each class and shows the verifier catches all 14. The charter service reads every
mission type of this tree into its domain and writes each of the 66 mission-type files and step companions back
through its production writer byte for byte (`MissionTypeCorpusTest`). Its whole-pack gate
(`:packs:wholePackRoundTrip`) also round-trips the built-in pack, comparing YAML by meaning and Markdown by model.

## Retired and dropped

The PR retires 153 files:
- `mission_types/*.yaml`
- `missions/*/mission.yaml`
- `mission-runtime.yaml`
- `governance-profile.yaml`
- `expected-artifacts.yaml`
- `actions/*/index.yaml`
- `mission-steps/**`
- `missions/*/command-templates/*`

The following field classes have no place in the sanitized shape and are dropped (ADR-0044 §3, charter
`divergence.md` §4):
- `mission.yaml`'s `name`, `version`, `domain`, `workflow` (and its `phases`), `artifacts`, `validation`,
  `mcp_tools`, `task_metadata`, `commands`, and qa's state machine (`mission`, `initial`, `states`, `transitions`);
- the runtime's `name`, `title` and `version`, where these conflict with the type's own;
- the steps' `depends_on`, which `steps` order replaces;
- `step.yaml`'s `step_type`, `prompt_template`, `guidance`, `delegates_to`, `sequence_index` and
  `in_action_sequence`, and its `display_name` where the runtime has a title (listed in
  `spec-kitty/spec-kitty-open-packs#96`);
- the governance profile's `template_set`, `selected_agent_profiles` (see `spec-kitty/spec-kitty-open-packs#97`),
  `selected_mission_step_contracts`, `available_tools` and `activations`;
- the expected-artifacts `schema_version` and `manifest_version`;
- plan's `runtime` block's `step_transition` and `prompt_template_dir`.

Values that only repeated another value are checked equal and dropped:
- the runtime `mission.key`;
- plan's nested `mission.steps` copy, and its `runtime` block's `loop_type` and `terminal_step`;
- the governance profile's `id` and `mission_type`;
- expected-artifacts' `mission_type`;
- `step.yaml`'s `id`;
- the action index's `action`.

**YAML comments are not carried.** The retired files hold about 500 comment lines. The largest are qa's
`expected-artifacts.yaml` (63 lines) and documentation's `governance-profile.yaml` (59). Two of qa's comments carry
rationale the mission-type file now loses:
- "implement: deliberately empty … do not re-add", on why qa's implement step has no blocking traceability output.
  This bears on the defect-log move in `spec-kitty/spec-kitty-open-packs#99`.
- "Decision 17", on why `qa-coverage.json` is declared twice under two keys.

Carry that rationale into the qa PROVENANCE.md or the step prompts if it should stay visible.

## Other edits in this PR

- The qa and cave-kitty DRG fragments' `body_path` now name the type files.
- `packs/built-in.source.yaml` carries the re-seeded `tree_digest`. It is core's `snapshot_sha256`, which reproduces
  the old digest on the pinned tree.
- `docs/packs/` is regenerated (`gen_pack_reference.py`).
- `scripts/ci/check_vocabulary.py` drops its provenance row for the retired documentation governance profile. The
  `common-docs-curation was folded into` comment went with that file. Its unit test now picks the qa row by path
  instead of by index.

## Consequences

- With spec-kitty 4.0.0rc8, `scripts/ci/validate-packs.sh` and `scripts/ci/check-built-in.sh` report exactly the same
  errors on this tree as on the pin (both fail there already). The vocabulary guard, its tests, the release tests and
  `gen_pack_reference.py --check` pass.
- Core still reads the retired files. Core's runtime, at CI's pinned commit, may fail on this tree until core moves to
  the charter's model. Per the charter's operator ruling (epic #186: the rewrite is designed without regard to core),
  this needs the same operator waiver as PR-A's procedure migration (#91).
- Docs, a tactic and qa asset docstrings still describe the retired layout: `spec-kitty/spec-kitty-open-packs#100`.
- `pack-manifest.yaml` is unchanged: it lists no mission file, and core's manifest has no mission-type kind.

## Patch

`kitty-specs/mission-type-consolidation-01M65MCK/research/migration/patch/0001-*.patch` in the charter branch
`issue-194-mission-type-consolidation`, against `5df92a5d`. The decisions the mapping made, one per line, are in
`research/migration/migration-report.md`.
