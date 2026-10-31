# WP06 review 1: response

The three blocking issues and all five low ones are fixed. `verifyAll`, `:packs:openPacksRoundTrip` and
`:packs:wholePackRoundTrip` are green against `86d9284`.

## Blocking issues

- **M1:** `mission-type` is in `WholePackRoundTripTest.REQUIRED_KINDS`.
- **M2:** `packs/qa/PROVENANCE.md` now:
  - names `86d9284` (spec-kitty/spec-kitty-open-packs#95, 2026-10-31);
  - describes the `missions/qa/` layout and the retired files;
  - notes that the port-time verification describes the old layout.

  `packs/internal/PROVENANCE.md` says the pin moved and that `packs/internal` did not change.
- **M3:** the `MissionTypeCorpusTest` filter now admits only `presets/` violations and pins them: 14 for built-in, 0
  for the others. The comment names OP-1 only.

## Low-severity issues

- **L1:** `ParseFailure` has a typed `Optional<String> retiredReason` component, set only by `ParseFailure.retired`.
  The two-argument constructor keeps every parse failure's call site unchanged. Nothing sniffs a prefix any more.
  - `PackPolicyTest.aRetiredFileReadsAsRetiredAndAParseFailureKeepsItsWording` covers a retired YAML file, a retired
    Markdown file, and a parse failure that keeps "is not valid YAML".
  - The Javadoc is reflowed.
- **L2:** stale pin references are fixed:
  - the `gradle.properties` comment;
  - `docs/guides/service-build.md`;
  - the `FileSystemPackInspectorTest` comment, which now says "386 at the previous pin, 5df92a5d".

  The unused `OpenPacksCorpus.YAML_FILE_COUNT` is removed.
- **L3:** `theCorpusCountsArePinned` resolves every `template_path` of every type and step. It asserts 13 and checks
  that each one is a regular file.
- **L4:** `noRetiredFileIsLeft` also walks the vendored `packs/` of this repository.
- **L5:** `*/missions/**/README.md` is split into three exclusions:
  - `*/missions/README.md`;
  - `*/missions/*/README.md`;
  - `*/missions/*/templates/**/README.md`.

  A README under `mission-steps/` or `command-templates/` is now retired, not excluded.
  `RetiredFilesTest.aReadmeUnderARetiredDirectoryIsRetired` covers both sides, and `PackFileKindTest` has a sample
  for each new exclusion.

## Nits

- `theRetiredRulesReplaceTheMissionExclusions` is kept. It is the one check that ties the removal of the seven
  exclusions to the retired samples.
- The broken `qa/README.md` link is upstream content and out of scope.
