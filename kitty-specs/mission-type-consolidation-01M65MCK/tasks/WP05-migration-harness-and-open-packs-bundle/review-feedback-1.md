# WP05 review 1 (reviewer-renata): changes requested

Verified good: the mapping follows ADR-0044 §3–§5 on spot checks (qa, plan, research, software-dev). All 60 companions are byte-equal to their pinned sources once frontmatter is stripped. Every template_path resolves. The 153 deletions are all retired-glob files, and no retired file is left. The tree_digest equals core's snapshot_sha256, recomputed independently. A rerun of `:packs:packMigration` reproduces /home/user/op-migrate exactly; the only difference is the digest, which is re-seeded by hand. The patch is identical to `format-patch 1347514`. The verifier exits 0, and the 11 test classes b543946 touched pass (117 tests). There are no suppressions. WP06 deletes the harness.

## Issue 1 (high): the verifier is missing three checks that IC-07, R-B1 and R-M6 require
`research/migration/verify_migration.py` does not do these:
- **Equality for drops labelled a repeat** (ADR-0044 §3: "Each is checked equal to the value kept before it is dropped"). Neither the verifier nor the harness compares these with the values that are kept:
  - runtime `mission.key`
  - expected-artifacts `mission_type`
  - governance-profile `id` and `mission_type`
  - step.yaml `id`
  - action index `action`
  - plan runtime's nested `mission.steps` copy and its `runtime` block

  The harness only allow-lists these keys (MissionTierMigration.java:152, :208-210, :244-246, :408). I checked by hand and they are all equal today, so this is a gap in the checks, not wrong output. Add the comparisons to the verifier.
- **Every source key path maps to one target path or to a listed drop.** The verifier needs its own drop table, and it must fail on a pinned key that is in neither.
- **Load-then-write byte-identical.** Add the check, or record exactly which gate run proves it and where.

Also:
- The prompt and guideline counts (43 and 17) are computed but never asserted (verify_migration.py:251).
- Added files are never checked (verify_migration.py:238-247 ignores `A`). An extra file would pass.
- The `drg/fragment.yaml` and `built-in.source.yaml` changes are allowed without their content being checked. Assert that the only change is the body_path line, or the tree_digest line.
- Commit the script that plants the defects for the self-test, so verifier-self-test.txt can be reproduced.

## Issue 2 (medium): some dropped text is not listed
- **16 step.yaml `display_name` values that differ from the runtime title were dropped silently.** That is documentation 6, plan 2, research 5 and software-dev 3; for example, `Methodology` was dropped beside `Methodology Design`. This is the same class as the 24 descriptions. Report each pair (MissionTierMigration.java:280) and add them to `step-descriptions.md`, or to a sibling draft.
- **About 500 lines of YAML comments in the retired files are not carried, and nothing mentions this.** The largest are qa expected-artifacts.yaml (63 lines) and documentation governance-profile.yaml (59 lines). qa's comment "implement: deliberately empty … do not re-add" bears directly on the defect-log move in step-preconditions.md, and so does its "Decision 17" rationale for the double qa-coverage.json. Say in mission-type-consolidation.md that the comments are dropped, and point to the qa rationale.
- **mission-type-consolidation.md does not list the classes of fields it drops.** These are validation, mcp_tools, task_metadata, commands, workflow.phases, artifacts, qa's state machine, template_set, step_type, and the conflicting name and version values. List them, or link to ADR-0044 §3 and divergence.md §4.

## Issue 3 (high): the bundle turns open-packs' own CI red, and nothing says so
On /home/user/op-migrate:
- `python3 scripts/ci/check_vocabulary.py` fails with 1 finding. Its PROVENANCE_PHRASES row names the deleted `packs/built-in/missions/documentation/governance-profile.yaml`, at check_vocabulary.py:80.
- `scripts/docs/gen_pack_reference.py --check` reports "Drift detected in generated pack pages: built-in, cave-kitty, qa".

Fix both in the patch: drop or move the vocabulary row, and regenerate docs/packs. Otherwise list them in the consolidation draft's Consequences.

mission-docs.md also misses several stale references:
- qa/PROVENANCE.md (`mission-steps/` and command-templates)
- cave-kitty/PROVENANCE.md (`mission-runtime.yaml`)
- docs/artefacts/index.md
- tactic `reviewer-implementer-role-separation` (it names mission-runtime.yaml as the home of profile routing; that is now wrong)
- the qa toolguide texts that name expected-artifacts.yaml

## Issue 4 (low): template ids can collide (b543946)
`templates/plan/x.md` and `missions/plan/templates/x.md` both get the id `plan/x.md`. DomainRepositoryService.assembly adds both, and `places.put` overwrites one of them silently.

Refuse a duplicate template id as a violation, and add a test. Also add a unit test for the new `Template.missionType` invariant: the IllegalArgumentException when the id does not start with `<type>/`. The invariant has no direct test today.

## Issue 5 (low): the role fallback is not in ADR-0044 §3
Both tools fall back to the action index's `agent_profiles` for a step's role (MissionTierMigration.java:322 and :411-414; verify_migration.py:114-115). ADR-0044 §3 has no such rule, and no pinned index uses it. Remove the fallback from both tools, or refuse the key. Having the same unstated rule in both tools weakens the claim that they are independent.

## Coordination note (anti-pattern check, item 7)
b543946 edits files outside WP05's owned_files: file-kinds.yaml, ADR-0044, Template, TemplateBinding, PackFileSet, FilesystemPackStore, DomainRepositoryService, and 12 tests. WP06 must not add the `*/missions/**/README.md` exclusion again, because b543946 already adds it. WP06 starts from b543946's template counts (27 templates, and qa's 10).
