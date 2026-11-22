---
affected_files: []
cycle_number: 1
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-21T23:41:13Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP10
---

# WP10 review feedback, cycle 1 (reviewer-renata)

Verdict: **CHANGES REQUESTED**. Reviewed commit `a80f3e4a7` (range `b9429eeb10ea..a80f3e4a71e9`). Workspace HEAD
`6119f99f6` differs from it only in kitty-specs planning files.

The code, schema, storage, fixtures and tests are sound. Fresh gates are green (`--rerun-tasks`, counts from the
test XML): kernel 3118/0 failed; packs 7197/0 (6 skipped); pack-validation 471/0 (1 skipped); wholePackRoundTrip
17/0; pack-doctor 263 + jarTest 15 + surfaceTest 4, all passing; contracts 314/0; deployable 2382/0, including all 6
`anti-pattern-files.feature` scenarios; spotlessCheck passed. One merge-breaking gap and one FR gap remain.

## Findings

1. [HIGH] `docs/architecture/pack-schemas/` (missing `anti-pattern.md`): the new schema has no page, so the docs
   site's `prebuild` fails. `site/scripts/stage-docs-inputs.mjs:39-44` exits 1 with
   `schemas with no page in docs/architecture/pack-schemas/: anti-pattern.schema.json` (reproduced locally). The
   docs-site CI breaks at merge, and no WP in this mission plans the page.
   **Fix:** add `docs/architecture/pack-schemas/anti-pattern.md` with `schematic_path: schemas/anti-pattern.schema.json`,
   following `routing-catalog.md`'s front matter, and add a link to it in `docs/architecture/pack-schemas/README.md`'s
   list. This is a justified out-of-map edit; list it in the review notes.

2. [MEDIUM] `contracts/charter/scenarios/registration/anti-pattern-files.feature:85-88` and
   `service/pack-validation/.../antipattern/AntiPatternFileCheckTest.java`: FR-013 (and data-model.md:52) lists
   **an unknown key** among the refusals that answer `artifact_schema_error`, and the change fragment
   `contracts/charter/changes/2026-11-21-anti-pattern-file-kind.md` claims it. No test plants an unknown key in an
   anti-pattern file, and no test pins the schema's `additionalProperties: false`. A future edit could open the
   schema and every test would stay green.
   **Fix:** add an unknown-key case, such as `rejects: []` or `when: x`, to the scenarios (a separate scenario
   is fine) and to `AntiPatternFileCheckTest`. Assert the same code, file, pointer and detail on both surfaces
   (`unknown key '<k>'`, `Wording.additionalPropertiesDetail`).

3. [MEDIUM] Planning artifacts (orchestrator, not the implementer):
   `tasks/WP12-inline-anti-patterns-dropped.md:108-109` ("WP10 has landed in your base: no example styleguide or
   procedure carries `anti_patterns` ... if one does, stop") and `:217` (stop condition "An example pack still carries
   an inline block: WP10 is incomplete in your base; stop") contradict the re-slice (T047a). A WP12 implementer who
   follows them stops at once. WP10's own Definition of Done (`tasks/WP10-anti-pattern-file-kind.md`, "no example
   styleguide or procedure carries `anti_patterns`") is stale in the same way.
   **Fix:** reword those WP12 lines to "the 18 files, nodes and edges exist, and the inline blocks are still present
   (T047a removes them)". Amend WP10's DoD line to match the re-slice note.

4. [LOW] The for_review note lists no out-of-map edits, though the WP asked for them: `built-in/pack-manifest.yaml`
   (WP04's) was restamped, `PackSchemasTest` (26 to 27) and `VocabularyEqualityTest` were updated, and
   `VocabularyEqualityTest` did go red, as the derived inspector list now includes `anti-pattern`.
   **Fix:** list them, and the docs page from finding 1, in the next for_review note.

## Checked and fine

- **Schema:** closed, with fields, required set, severity enum and key order exactly as ADR-0052 d8, ADR-0053 d30
  and d32 rule them. The id pattern and maxLength 64 match paradigm, tactic, styleguide and mission-type, and
  `IdentifierGrammar.letterFirstSlug`. The files rule, the classifier case, CHANGELOG `### Added`, the README count
  (28 files; 27 bundled in `PackSchemasTest`) and the file-rule count (27) are all correct.
- **Domain:** `AntiPattern` is modelled like `Paradigm`, with one static ruleset, identity equality and no
  relationship fields. Severity is required with no default (builder NPE, tested), and
  `AntiPatternSeverity.fromToken` is a closed mapping. `Pack.antiPatterns()` and `Builder.antiPattern` are present.
- **Storage:** the binding is appended last to `KindBindings` and `YamlFormats`. `idIsFileStem` and
  `placedAtItsFileName` hold. The round trip is byte-equal, and an absent `good_example` stays absent. An unknown
  severity is refused, by the schema at load and by the binding defensively. The writer's key order is the schema's.
  The digest fixture `nu.anti-pattern.yaml` was added, both constants recaptured, and the javadoc "Recaptures" line
  written.
- **The 18 files:** all are verbatim from the inline entries; I checked every one. The 11 written `bad_example`s are
  concrete and grounded in each entry's own content. The severities are sensible: 3 breaking/severe/smell all
  covered, and `silently-dropped-finding` is `breaking`. All 18 nodes name a held file. The `rejects` edges (source
  owner, relation `rejects`, a reason naming the owner) sit in the right shard.
- **Fixtures:** `facts/example-packs.json` changed only built-in `defined` (65 to 82: 17 new URNs, and
  `indirection-for-repetition` moved into ArtifactKind order). The manifest gained exactly 18 `anti_pattern`
  constituents. The rest is the stamp's reordering with no content-hash change, and presets are unchanged.
- **Re-slice:** sound. With the schema still declaring `anti_patterns`, removing every inline block makes
  `everySchemaPropertyHasAnExample` red, and the only alternatives are an exclusion or keeping a token inline entry.
  The inline entries mint no URNs, and WP12 removes them in the same PR. The glossary and docs describe the model at
  merge (WP11 and WP12 land in the same PR), which is correct.
- `contracts/charter/scenarios/README.md` "Features" does not list the new feature. It also omits
  `closure-faults`, `registration-dry-run`, `version-order` and `pack-uninstall`, so the convention is not applied
  consistently; folded, no action.
