# Op 01M7FA7GY4NZ6AN3WBA0BQTBDN: the whole-pack round trip moves into main code (#555)

Profile: java-jenny. Kind: refactor and test quality. Size: M. Parent epic: #549 (ADR-0075, decision 8).

## The ruling it carries

Operator ruling 2 of 2026-11-15, recorded on #549, amends ADR-0075 decision 8. The round trip splits in two:

- **The production flow** (read, domain, write, read back; today `DomainRoundTrip`) moves into `packs` main code. It
  stays on the storage path (`DomainRepositoryService`), not on `POST /packs` registration admission.
- **The validation harness** moves into `pack-validation`, as a rule over the flow's result. It holds the per-file
  outcomes, equality of meaning (ADR-0036) and the expected baseline (today `WholePackExpectation` and the verdict
  logic in `WholePackRoundTripTest`).

Ruling 1 (the packs domain stays canonical) and ruling 4 (a code owns its severity) also apply.

## Grounding (main at `5decda0`)

- **All of it is test code today.** `service/packs/src/test/.../infra/storage` holds the flow, the harness and their
  helpers: `DomainRoundTrip` (891 lines), `WholePackRoundTrip` (the adapter interface), `WholePackCensus` (253),
  `MeaningComparator` (473), `WholePackExpectation` (739), `WholePackReport` (119) and `WholePackDrift` (56). The gate
  is `WholePackRoundTripTest` (1,064), and `observe` there is the verdict's input. The drift test is
  `src/driftTest/.../OpenPacksDriftTest`.
- **The flow names package-private codecs**: `Yaml`, `KindBindings`, `PackFileKind`, `ContentForm`, `PackManifests`,
  `DomainRepositoryService` and `markdown.MarkdownCodec`. So it moves into `packs.infra.storage`, beside them.
- **The comparator parses.** `MeaningComparator.compareTexts` calls `Yaml.load`, and `compareMarkdown` calls
  `MarkdownCodec.parse`. `PACK_VALIDATION_IS_FRAMEWORK_FREE`'s direct half lets `pack-validation` name only
  `packs.domain`, so the comparator cannot take texts there.
- **The comparator is a shared oracle.** 19 per-kind codec tests in `:packs` test use it (for example
  `AgentProfileYamlTest` and `PresetRoundTripTest`). They test package-private codecs and cannot move.
- **The expected-red lists belong to the example packs.** `whole-pack/{expected-red,exclusions,known-failures}.yaml`
  name files of the example packs, which live in `:packs` test fixtures (ADR-0070).
- **#547 (merged, PR #564).** The drift test judges every census file through `WholePackExpectation.none().evaluate`,
  so a census file of a kind the round trip does not model fails, naming its kind and path. The gate ties the modelled
  kinds to `file-kinds.yaml` (`schemaProblems`). The planted break (`withoutKind(org-fragment)`) proves both. This must
  keep holding.

## Design

- **`packs.domain.roundtrip`** (new) holds the flow's result: `PackRoundTripResult` (the census, the modelled kinds,
  one `FileOutcome` per modelled file, and every written file), `FileOutcome` (`Written` or `Refused`) and `Reading`
  (`Bytes`, `Text`, `Yaml` tree, `Markdown` model, `MarkdownRefused`). The flow reads both sides with the readers'
  own settings, so no rule parses a file again (ADR-0075's "no second derivation"). The manifest's stale derived
  fields (#195) are facts of the read, so the flow records them.
- **`packs.infra.storage`** gains `DomainRoundTrip`, `WholePackRoundTrip` and `WholePackCensus` (moved, still
  package-private) and the public entry `PackRoundTrip`.
- **`pack-validation`** gains `packvalidation.roundtrip`:
  - `MeaningComparator`, over trees and Markdown models;
  - `RoundTripObservation`, the old `observe`, now pure over the result;
  - `WholePackExpectation`, reading its lists with snakeyaml-engine, which the module allowlist admits;
  - `WholePackReport`;
  - `WholePackRoundTripRule`, a kernel `Rule<PackRoundTripResult>` with one `ReasonCode` per problem kind.
- **Where the tests and lists go.**
  - The gate and the drift test move to `:pack-validation` (`wholePackRoundTrip` task, `driftTest` source set).
  - The expected-red lists move to `:packs` test fixtures, beside the example packs.
  - The flow's own controls stay in `:packs` test. The end-to-end lossy controls run in `pack-validation` through a
    test-fixture factory in `packs.infra.storage` (`RoundTripControls`), so production code carries no test hook.
  - The 19 codec tests keep the oracle through `:packs` `testImplementation(project(":pack-validation"))`, a test-only
    edge. The main direction stays `pack-validation` to `packs`.

This shape was sent to the orchestrator as Q1 (option A, recommended). The alternative (B) keeps the comparator in
the flow.

## Squads

**Tracked issues.**

| Issue | Relation | Disposition |
| --- | --- | --- |
| #555 | this Op | delivered here |
| #549 | parent epic | ticked at landing; decision 8 text amended here |
| #547 | merged; its loud failure must hold | kept: the planted `org-fragment` and `glossary` breaks move with the gate and still fail both ways |
| #477 | `impacts` red | out of scope; slice F (#567) retires `impacts`, which makes it moot |
| #478 | `pack-check --round-trip` | out of scope; it composes `PackRoundTrip` and `WholePackRoundTripRule` through its main |
| #531 | PIT cost of `ProcedureAndGuideCorpusTest` | out of scope; the test keeps its assertions and calls the moved flow |
| #557 | removes the drift workflow and source set | out of scope; the source set moves module here, and #557 deletes it there |
| #554 | parallel, `PACK`/`CLOSURE` rulesets | no file overlap expected; both add to `pack-validation`, so whoever merges second rebases |

**Similar surfaces.**

| Surface | Disposition |
| --- | --- |
| `.github/workflows/whole-pack.yml` (`:packs:wholePackRoundTrip`, report paths) | updated to `:pack-validation` |
| `.github/workflows/open-packs-drift.yml` (`:packs:driftTest`) | updated to `:pack-validation` |
| `docs/guides/service-build.md`, `docs/plans/charter-example-packs.md` | task names and list paths updated |
| `build-logic` `charter.hand-written` (`src/driftTest/java/**`) | the glob is module-agnostic; checked |
| open-packs' required gate runs `:packs:driftTest` from its pin (ADR-0070 decision 4, as amended) | draft issue `open-packs#TBD-drift-task-moves` below; the orchestrator files it |
| ADR-0036, ADR-0070 | amended where they name the gate's home |
| engineering notes (`docs/plans/engineering-notes/*`) | dated records; not rewritten |

## Draft issue for spec-kitty-open-packs (`open-packs#TBD-drift-task-moves`)

> **Title:** The charter drift gate's task moves to `:pack-validation:driftTest`
>
> spec-kitty-charter #555 moves the whole-pack round-trip harness into `pack-validation` (ADR-0075 decision 8, as
> amended on 2026-11-15). From the charter commit that merges it, the drift gate runs as
> `./gradlew :pack-validation:driftTest -PopenPacksDir=<checkout>`, and its report is under
> `service/pack-validation/build/reports/tests/driftTest`. When the `charter-service` job bumps its charter pin past that
> commit, update the command and the upload path. The judgement is the same.

## Not done

- No rebase onto slice F (#567): F is still planning-only (see Q2 to the orchestrator).
- `pack-check --round-trip` is #478's.
