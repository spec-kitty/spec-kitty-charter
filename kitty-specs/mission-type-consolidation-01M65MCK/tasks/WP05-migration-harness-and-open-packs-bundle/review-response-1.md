# WP05 review 1: response

The bundle was also regenerated for the operator's refined ruling Q1 (2026-10-30). That ruling has three parts:
- prompt frontmatter is kept as the prompt's detailed description;
- a step `description` is at most 160 characters;
- the harness and verifier changed to match.

The migrated commit is now `12b0e553` (was `1347514`).

## Issue 1: verifier checks (fixed)

`verify_migration.py` now covers each point raised:
- **Repeats.** It checks every repeat the review listed against the value kept:
  - the runtime `mission.key`;
  - plan's nested `mission.steps` copy (id, name, description, order, depends_on);
  - plan's `runtime` block (loop_type, terminal_step);
  - the governance profile's `id` and `mission_type`;
  - expected-artifacts' `mission_type`;
  - `step.yaml`'s `id`;
  - the action index's `action`.

  The harness checks the same repeats on its own and stops on any difference.
- **Key coverage.** Its own `KEY_TABLE` lists every pinned key per source file as map, repeat or drop. It fails on a key
  in none of those.
- **Byte equality.** Prompts and guidelines are now compared byte for byte with their pinned files, frontmatter
  included. All 43 prompts and 17 guidelines are equal.
- **Load-then-write.** The proof is in `research/migration/gate-evidence.md`: `:packs:wholePackRoundTrip` on the
  migrated tree is green, 7/7.
- **Counts.** The prompt, guideline, added-file and companion counts are asserted.
- **Added files.** Every added file, including untracked ones, must match the mission-type or step-companion pattern.
- **Modified files.** Each must be in `EXPECTED_EDITS`, and every removed or added line must match that entry's pattern.
  This covers the fragments, `built-in.source.yaml`, the vocabulary script and its test. `docs/packs/` is checked by
  running the generator's own `--check`.
- **Self-test.** `self_test.py` is committed. It plants 10 defect classes in throwaway worktrees and asserts that each is
  reported. Result: 10 of 10 detected (`verifier-self-test.txt`).

## Issue 2: dropped text (fixed)

- The harness reports each `step.yaml` `display_name` that differs from the runtime title: 17 steps. The review counted
  16; software-dev's `discovery` makes 17, because its directory is `research`. `step-descriptions.md` lists all 17
  in a table.
- `mission-type-consolidation.md` now:
  - lists the dropped field classes;
  - lists the repeats;
  - says YAML comments are not carried;
  - quotes the two qa rationales (the deliberately empty implement entry and Decision 17).

## Issue 3: open-packs CI (fixed in the patch)

- `check_vocabulary.py` drops the stale provenance row. Its unit test now picks the qa row by path, not by index.
- `docs/packs/` is regenerated. `gen_pack_reference.py --check` shows no drift.
- With spec-kitty 4.0.0rc8, `validate-packs.sh` and `check-built-in.sh` give the same error lines as on the pin
  (checked with `diff`).
- `mission-docs.md` now lists every remaining stale reference:
  - the two PROVENANCE files;
  - `docs/artefacts/index.md` and the authoring guide;
  - the implement skill;
  - the tactics `reviewer-implementer-role-separation`, flagged as wrong and not only stale, and
    `ownership-map-leeway`;
  - the qa toolguides and asset docstrings.

## Issue 4: template id collision (fixed, d6e5d13)

- The store refuses a second template file with an id it already read. The violation is on the later file and names
  both: `template plan/x.md is already read from missions/plan/templates/x.md`.
- `TemplateStoreTest` covers the collision. `TemplateEntityTest` covers the `Template.missionType` invariant: a
  wrong prefix, a type with '/', a blank type, and a top-level template accepting any id.

## Issue 5: role fallback (kept, now stated)

The finding says no pinned index uses `agent_profiles`, but three do: the `retrospect` action indexes of
documentation, research and software-dev name `retrospective-facilitator`. software-dev's belongs to no step and is
not carried; the other two steps' `step.yaml` has `agent_profile: null`. Removing the fallback would silently drop the
`facilitator` role from those two steps (corrected in round 2; this answer first said three steps).
Refusing the key would block the migration. So the rule is now stated in ADR-0044 §3, in the role row and the
paragraph under the table, and both tools keep it.
