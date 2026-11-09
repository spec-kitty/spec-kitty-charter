---
work_package_id: WP03
title: Pack schemas cap id length (Breaking)
dependencies: []
requirement_refs:
- FR-005
- FR-006
- FR-007
- C-002
- C-004
- SC-002
planning_base_branch: fix/regex-bounds-and-sonar-cleanup
merge_target_branch: fix/regex-bounds-and-sonar-cleanup
branch_strategy: Planning artifacts for this mission were generated on fix/regex-bounds-and-sonar-cleanup. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/regex-bounds-and-sonar-cleanup unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regex-bounds-and-sonar-cleanup-01M706DF
base_commit: e94a9b6d7b8676fe7c2bebd7b0a3c9b7380e45c4
created_at: '2026-11-09T13:20:12.080390+00:00'
subtasks:
- T009
- T010
- T011
- T012
history:
- timestamp: '2026-11-09T13:20:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/schemas/
create_intent:
- contracts/charter/changes/2026-11-09-pack-id-length-caps.md
- contracts/charter/scenarios/pack-id-length.feature
execution_mode: code_change
model: ''
owned_files:
- contracts/packs/schemas/guard.schema.json
- contracts/packs/schemas/lifecycle.schema.json
- contracts/packs/schemas/procedure.schema.json
- contracts/packs/schemas/toolguide.schema.json
- contracts/packs/schemas/mission-type.schema.json
- contracts/packs/schemas/step-enhancement.schema.json
- contracts/packs/schemas/styleguide.schema.json
- contracts/packs/schemas/tactic.schema.json
- contracts/packs/schemas/paradigm.schema.json
- contracts/packs/schemas/directive.schema.json
- contracts/packs/schemas/pack-descriptor.schema.json
- contracts/packs/schemas/asset.schema.json
- contracts/charter/changes/2026-11-09-pack-id-length-caps.md
- contracts/charter/scenarios/pack-id-length.feature
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, then load
`spec-kitty charter context --action implement`.

## Objective

The pack schemas refuse an over-cap id as `pack_unreadable` before the domain sees it, and the narrowing is announced
as Breaking. FR-005, FR-006, FR-007; operator decisions `01M706DW5XHBA4781BYHVDFNT5`,
`01M706DYNJRHY13DDN5C269JTT` and `01M706E15NPBQMX2AGC88F65NF`.

## Context

- Schema validation uses networknt with `Re2jRegularExpressionFactory`, which is linear-time, so a long id cannot
  overflow in validation and `maxLength` refuses it cleanly. Schema patterns stay ECMA-compatible: **no possessive
  quantifiers in the JSON schemas.**
- The longest real pack id in open-packs `22c14b2` is 55 characters. Manifest file paths reach 89, so path fields are
  **not** given 64.

## Subtasks

### T009: maxLength

Add `"maxLength": 64` to these fields:

- the `id` of `guard`, `lifecycle`, `procedure`, `toolguide`, `mission-type`, `step-enhancement`, `styleguide`,
  `tactic`, `paradigm` and `directive`;
- `mission-type` `$defs/Step/properties/id`;
- `lifecycle` `$defs/StateId`;
- `pack-descriptor` `properties/name`.

Add `"maxLength": 255` to:

- `lifecycle` `$defs/GuardEntry/properties/ref` (composite `pack::guard:name`);
- `asset` `$defs/Executable/properties/entry`;
- `mission-type` `artifact_key` in both `ExpectedInputArtifact` and `ExpectedOutputArtifact`.

`preset` and `skill` already have 64. Leave hashes, ULIDs, schema versions, fixed-shape file paths and graph-shard
URNs alone. Place `maxLength` next to `pattern`, matching the existing files' key order (see
`skill.schema.json:21`). If any of these schemas is mirrored elsewhere (embedded copies or a generated index), find it
with `grep -rl '<schema file name>' contracts service build-logic` and keep it in step.

### T010: Breaking fragment

Create `contracts/charter/changes/2026-11-09-pack-id-length-caps.md`, following the format of an existing fragment
(e.g. `contracts/charter/changes/2026-11-07-a0-ruling-breaking.md`, and the ADR-0047 fragment rules). It goes under
`### Breaking`. It lists the capped fields and the caps, and says a pack whose id exceeds them is refused as
`500 pack_unreadable` on read. It also says no real pack in open-packs `22c14b2` is affected (longest id 55), and
cites #440.

### T011: Contract scenario

Create `contracts/charter/scenarios/pack-id-length.feature`, modelled on the preset-id-grammar outline in
`activation-register.feature` (around line 304), which plants a pack file and expects `500 pack_unreadable`. A
Scenario Outline with:

- a guard (or procedure) id of 65 characters → `500 pack_unreadable` on `GET /offering`;
- the same file with a 64-character id → readable (positive control on the same fixture);
- an executable entry of 256 characters → `500 pack_unreadable`.

Reuse existing step glue only. Run it via the scenario task (find it:
`grep -rn 'scenarios' service/*/build.gradle.kts | head`), e.g. `./gradlew :deployable:test --tests '*Scenario*'`
or the module's cucumber runner. If a new step is needed, stop and report: step glue is outside this WP.

### T012: Real packs within the caps

Write a small check (in the scenario or a test, or record a one-off script result in the commit body). It confirms
every id field you capped, across the pinned open-packs checkout
(`/tmp/claude-1000/-home-stijn-Documents-code-kitty-spec-kitty-charter/f3c3a71b-9c1f-4c43-aa89-d9ff383fbce6/scratchpad/open-packs-pin`)
and `packs/`, is within its cap. Then run `./gradlew :packs:wholePackRoundTrip
-PopenPacksDir=<that path>` as the positive control. **If any real id exceeds a cap, stop and report**; this is an
operator decision.

## Definition of Done

- `./gradlew :contracts:check` green (contract lint and generation).
- New scenario green; the whole-pack round trip green.
- Fragment present under `### Breaking`.

Implement command: `spec-kitty agent action implement WP03 --agent claude --mission regex-bounds-and-sonar-cleanup-01M706DF`
