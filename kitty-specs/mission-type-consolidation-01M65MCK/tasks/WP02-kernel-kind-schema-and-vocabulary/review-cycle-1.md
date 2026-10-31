---
affected_files: []
cycle_number: 1
mission_slug: mission-type-consolidation-01M65MCK
reproduction_command:
reviewed_at: '2026-10-30T06:29:07Z'
reviewer_agent: claude-reviewer
wp_id: WP02
---

# WP02 review feedback (cycle 1), reviewer-renata

`./gradlew :kernel:test :packs:test` is green, there are no suppressions, Javadoc is present, and the vocabulary tests were widened rather than loosened. Two findings block approval. The others are notes.

## Blocking

**Issue 1 (MEDIUM): `path_conventions` does not carry the ADR's "at least one" rule.**
`contracts/packs/schemas/mission-type.schema.json:166`. ADR-0044 §3 says `path_conventions` is a "closed object, at least one of `workspace`, `tests`, `deliverables`, `documentation`, `data`", and data-model.md:51 says the same. The schema leaves this to "the domain's invariant", so `path_conventions: {}` validates. That is an empty field, which S16 forbids, and the WP asks for the schema "exactly per ADR-0044 §3".
Fix: add `"minProperties": 1` to `$defs/PathConventions`. Add a twin to `MissionTypeSchemaTest`, for example `'path_conventions:\n  workspace: research/\n  data: data/\n' >> 'path_conventions: {}\n'`, expecting `minProperties /path_conventions -`.

**Issue 2 (LOW-MEDIUM): the "empty steps" twin changes two things.**
`MissionTypeSchemaTest.java:106`. The replacement `'steps:\n- id: scoping' >> 'steps: []\nretired:\n- id: scoping'` empties `steps` and also adds an unknown top-level key, `retired`. It expects two violations. The class Javadoc promises that each refusal "can only come from that one change", and this twin breaks that promise.
Fix: give this twin exactly one cause. For example, build it from the smallest valid document (`theSmallestMissionTypeValidates`) with `steps: []`, expecting only `minItems /steps -`. Or anchor on the whole `steps:` block so nothing is left over.

## Non-blocking notes

- **Note A (`activatable` fact):** `ArtifactKind.java:42` sets `activatable = true`. Research K1 supports this ("activatable, but through `activated_mission_types`, not `activated_kinds`"), and so do the `activatable()` Javadoc and `Preset.ACTIVATION_KINDS`. But post-plan-squad architect finding 11 proposed `false`, and logical-model.md's fact table has no row for the kind. Keep `true`, but say why in the commit or a code comment. WP07 should add the `mission_type` row (activatable yes, org-requirable no, overlayable no) and the core divergence entry.
- **Note B (coverage, optional):** no twin covers `guidelines_path`'s pattern, `Reference` closedness, or `minItems` on step `references` and `expected_artifacts`. Each would be a one-line twin.
- **Note C (shared-file coordination):** the commit also edits `PackFileKindTest.java`, `PackSchemasTest.java`, `PresetTest.java` and the new `MissionTypeSchemaTest.java`, which are outside WP02's `owned_files`. It also moves the `**/*.mission-type.yaml` files rule from WP03 into WP02, on purpose: `PackSchemas` only loads schemas that a files rule names. These are counts and samples that follow from that move. Record them in the move-task note so WP03 (which owns `PackFileKindTest` samples, T012) does not re-add the rule or the sample.

## Anti-pattern checklist
1 Dead code: N/A (enum constant, schema). 2 Synthetic fixture: PASS (the twins run the real `PackSchemas`). 3 Silent empty return: N/A. 4 FR coverage: PASS (FR-004 has kernel tests; FR-001 has the schema tests). 5 Frozen surface: PASS. 6 Locked decision: PASS (no `mission_step` kind or plural, `fromPlural("mission_steps")` is empty). 7 Shared-file ownership: FAIL until Note C is recorded. 8 Production fragility: N/A.
