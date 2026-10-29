# Implementation Plan: Procedure, anti-pattern and toolguide guide round trip

**Branch**: `feat/194a-procedure-antipattern-toolguide` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/spec.md`

The planning questions are answered and recorded as Decision Moments under `decisions/`; the Engineering Alignment
was confirmed by the operator on 2026-10-29. Grounding: `research/squad/` (pre-spec squad) and
`research/content-squad/` (toolguide guide shape), consolidated in [research.md](research.md).

## Summary

Bring procedures, procedure anti-patterns and toolguide guides into the whole-pack round trip with sanitized,
deliberately designed storage shapes, and build ADR-0040's `markdown` content form.

- The `markdown` content form parses a Markdown document into a structural model (frontmatter, title, preamble, H2
  sections) and compares documents by that model. It is admitted for `files` rules (ADR-0040 decision 1 amended, for
  sibling #195's `pack.md`) and for companions (ADR-0039 widened).
- A toolguide's guide becomes a structured `Guide` (fixed blocks plus freeform sections), stored as a Markdown
  companion at a `guide_path` relative to the toolguide's file.
- Procedures get a sanitized schema (rewritten in place), a domain entity and a binding, following Tactic.
- The HTTP toolguide registration carries the guide's content or a URI the service fetches (authorized contract
  change); the API never assumes a shared filesystem.
- A throwaway harness rewrites the pinned packs into the new shapes with this service's own writer; the result lands
  in spec-kitty-open-packs and `openPacksRef` moves to it.
- ADR-0042 records the sanitized-shape rules and these shapes.

Delivery is two pull requests (operator- and parent-session agreed): **PR-A1** the `markdown` content form and the
ADR-0040 amendment, small, landing first so #195 can rebase; **PR-A2** everything else.

## Technical Context

**Language/Version**: Java 25 (CI authority; Temurin 25 locally), Gradle 9.8 with the Kotlin DSL convention plugins in
`build-logic/`
**Primary Dependencies**: Spring Boot (existing), snakeyaml-engine (existing YAML reader/writer), the bundled JSON
Schema validator behind `PackSchemas` (existing), the JDK `java.net.http.HttpClient` for the guide fetch (JDK, no new
artifact). No new dependency; no change to `gradle/verification-metadata.xml` is expected (DIRECTIVE_051: nothing to
verify; if an implementer finds one is needed, it is a plan deviation and goes back to the operator).
**Markdown parsing**: a small purpose-built line scanner in `packs/infra/storage` (H1/H2 at column 0, fence tracking at
any indentation, frontmatter delimiters). No CommonMark library: the model needs only document structure, and a full
parser would add a dependency and its own normalization.
**Storage**: pack files on disk via `FilesystemPackStore` / `WholePackStore`; companions per ADR-0039.
**Testing**: JUnit 5 + AssertJ in `service/packs/src/test` — unit tests for the Markdown model, corpus round-trip tests
over the pinned packs (built-in, internal, qa), the whole-pack gate (`WholePackOpenPacksRoundTripTest`), contract
scenarios under `contracts/charter/scenarios/` for the registration change, ArchUnit gate. Per
`NO_FULL_HEAVY_SUITES_IN_MISSION`, work packages run the affected module's tests; `./gradlew verifyAll` runs before
review and in CI.
**Target Platform**: the charter service (Linux container), Spring Boot deployable.
**Project Type**: single multi-module Gradle project (`service/*`, `contracts`, `build-logic`).
**Performance Goals**: guide fetch bounded (10 s total, 1 MiB); pack load time grows only by the Markdown scan of
companions (24 guides at the pin), no measurable target beyond the existing gate runtime.
**Constraints**: ADR-0036 equality of meaning, unknown fields refused, no tolerance logic; one configuration root
(ADR-0038: the fetch allowlist lives under `charter.` in the packs domain's properties); append-only edits to shared
files except this mission's own exclusions and baseline entries; Java style check (Spotless palantir, Checkstyle,
Error Prone, NullAway, doclint).
**Scale/Scope**: 41 procedure files, 24 toolguides and guides across three packs; 1 contract schema change; 1 new ADR
(0042) and amendments to ADR-0039 and ADR-0040.

## Charter Check

*GATE: passes before Phase 0; re-checked after Phase 1 (below).*

| Charter item | Status | Note |
|---|---|---|
| Divergence from the oracle (core must not break) | Justified deviation | Operator ruling on epic #186 (2026-10-28): during the rewrite missions operate as if core does not exist; core changes when the service is incorporated. Recorded in Complexity Tracking. |
| Breaking the OpenAPI contract | Pass, with authorized change | The contract is `0.1.0-SNAPSHOT` with no released tag; under ADR-0022 a 0.x line may break. The registration change is operator-authorized (#194 comment of 2026-10-28), gets a CHANGELOG `### Breaking` entry and a scenario. No core consumer exists yet (see row above). |
| Open-packs content round-trips without loss of meaning | Pass by design | The migration changes pack content upstream through a reviewed open-packs PR; the gate then holds equality of meaning at the new pin. |
| No unverified dependency | Pass | No new artifact (JDK HttpClient). |
| Adversarial review squad at plan point and before merge | Done / planned | Pre-spec squad and content squad ran; a post-plan squad runs before tasks; a pre-PR review squad runs before each PR. |
| `verifyAll` on Java 25, style check, docs checks | Planned | Each PR runs them before review. |
| BDD / specification by example | Planned | Contract scenario for registration; example-driven corpus fixtures. |

## Project Structure

### Documentation (this mission)

```
kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/
├── spec.md
├── plan.md               # this file
├── research.md           # Phase 0: decisions, rationale, alternatives
├── research/squad/       # pre-spec squad reports + synthesis
├── research/content-squad/  # toolguide guide squad reports + synthesis
├── data-model.md         # Phase 1: entities, shapes, invariants
├── contracts/            # Phase 1: HTTP registration delta, pack schema sketches
├── quickstart.md         # Phase 1: how to exercise the change
├── decisions/            # Decision Moments
└── tasks.md              # /spec-kitty.tasks (not created here)
```

### Source Code (repository root)

```
contracts/
├── charter/schemas/ToolguideRegistration.yaml   # guide content | guideUri (PR-A2)
├── charter/scenarios/                            # registration scenarios (PR-A2)
├── charter/CHANGELOG.md                          # ### Breaking entry (PR-A2)
└── packs/
    ├── file-kinds.yaml                           # markdown admitted (A1); own exclusions removed (A2)
    ├── README.md
    └── schemas/
        ├── procedure.schema.json                 # rewritten, sanitized (A2)
        ├── toolguide.schema.json                 # guide_path relative (A2)
        └── toolguide-guide.schema.json           # structure of the parsed guide (A2)
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/
│   ├── Procedure.java, ProcedureIdentifier.java, ProcedureStep.java, AntiPatternEntry.java  (A2)
│   ├── Toolguide.java (+ Guide), Guide.java, GuideBlock.java, GuideSection.java             (A2)
├── application/
│   ├── ToolguideRegistrationService.java         # guide content or fetched URI (A2)
│   └── GuideFetcher.java (port)                  (A2)
└── infra/
    ├── storage/
    │   ├── ContentForm.java, PackFileKind.java   # MARKDOWN built (A1)
    │   ├── MarkdownDocument.java, MarkdownCodec.java  (A1)
    │   ├── CompanionContent.java                 # Markdown case (A2)
    │   ├── KindBinding.java / Companions.java    # companion form (A2)
    │   ├── ProcedureDso/Yaml/Binding.java        (A2)
    │   ├── ToolguideDso/Yaml/Binding.java        # guide companion (A2)
    │   ├── KindBindings.java, YamlFormats.java   # append procedure (A2)
    │   └── PackSchemas.java                      # companion structure schemas (A2)
    ├── fetch/HttpsGuideFetcher.java              # guarded JDK HttpClient (A2)
    └── api/ToolguideController.java              # DTO mapping (A2)
service/packs/src/test/...                        # unit, corpus, gate, migration harness (throwaway)
service/packs/src/test/resources/whole-pack/expected-red.yaml, known-failures.yaml
docs/adr/0042-sanitized-pack-shapes.md (A2); docs/adr/0039, 0040 amendments; docs/changelog; site/.contextive/definitions.yaml
gradle.properties                                 # openPacksRef (A2, after the open-packs PR merges)
```

**Structure Decision**: existing hexagonal layout of `service/packs` (domain / application / infra), ADR-0037
kind-keyed registry; no new module. The guide fetcher is an application port with an infra adapter so the domain and
application layers stay free of network code (ArchUnit gate).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Charter "divergence from the oracle" — shapes and the registration body diverge from core | Operator ruling on epic #186: operate as if core does not exist; sanitized shapes win (#194) | Matching core's formats would reintroduce the legacy and variant fields the operator ruled out |
| Breaking contract change (registration body) | API must not assume a shared filesystem (operator) | Keeping a bare `guidePath` makes a registered toolguide unwritable (no content to store) |
| First outbound HTTP call from the service | Operator chose fetch-and-store for guide URIs | Storing the URI only was offered and declined |

## Implementation Concern Map

> Concerns are not work packages; `/spec-kitty.tasks` decomposes them.

### IC-01 — Markdown content form

- **Purpose**: Build ADR-0040's declared `markdown` form: parse a Markdown document into `MarkdownDocument`
  (optional frontmatter, optional H1 title, preamble, H2 sections of title and content), write it back canonically,
  and compare documents by model equality.
- **Relevant requirements**: FR-005, NFR-002, NFR-003
- **Affected surfaces**: `ContentForm`, `PackFileKind` (admit `markdown` for `files` rules), new `MarkdownDocument` /
  `MarkdownCodec` in `infra/storage`, `MeaningComparator` and `WholePackCensus` (test-side comparison by form),
  `contracts/packs/file-kinds.yaml` header docs, ADR-0040 amendment (decision 1).
- **Sequencing/depends-on**: none (ships as PR-A1).
- **Risks**: refusal rules must be exact (unclosed fence, CR or BOM, misplaced H1, indented heading 1–3 spaces,
  setext underline) and `parse(write(d)) == d` must hold on write; headings inside fences at any indentation never
  count. Collision with #195: this PR owns the form, #195 only adds its rule.

### IC-02 — Companions carry a content form

- **Purpose**: Let a companion declare `text` or `markdown` (with a structure schema), read and compare it by its form,
  and register companion structure schemas with `PackSchemas`.
- **Relevant requirements**: FR-004, FR-005
- **Affected surfaces**: `KindBinding.Companion`, `CompanionContent` (Markdown case), `Companions`, `WholePackStore`,
  `PackSchemas` (registration path for schemas not named by a `files` rule), gate comparison of companions,
  ADR-0039 amendment ("content is text today").
- **Sequencing/depends-on**: IC-01
- **Risks**: the gate currently compares companions as YAML (headings become comments) — must change in the same
  step; the skill companion stays `text` and must not change behaviour.

### IC-03 — Structured toolguide guide

- **Purpose**: Model the guide as a `Guide` value (fixed blocks `overview` (required), `when_to_use`, `setup`, `usage`,
  `cautions`, `known_limitations`, `references`, plus freeform `sections` of `title` and `content`), stored as a
  Markdown companion at `guide_path` relative to the toolguide's file; the H1 is written from the toolguide `title`.
- **Relevant requirements**: FR-004, FR-009
- **Affected surfaces**: `Toolguide`, new `Guide` / `GuideBlock` / `GuideSection`, `ToolguideDso`, `ToolguideYaml`,
  `ToolguideBinding` (companions), `toolguide.schema.json`, new `toolguide-guide.schema.json`, `file-kinds.yaml`
  (remove this mission's own guide exclusion), `expected-red.yaml` (`other:toolguides` removed when green).
- **Sequencing/depends-on**: IC-02
- **Risks**: fixed-block headings are canonical text (`## Overview`, `## When to use`, `## Setup`, `## Usage`,
  `## Cautions`, `## Known limitations`, `## References`); an H1 different from `title` or a reserved block out of
  order is refused; guide prose links are content, never relationships.

### IC-04 — Sanitized procedure

- **Purpose**: Rewrite `procedure.schema.json` to the sanitized shape and bind procedures: entity, identifier, steps
  with `role`, anti-pattern entries `{title, content, bad_example?, good_example?}`, references in the shared
  `Reference` shape kept on the entity but never minted as relationships (#189).
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-007, FR-008
- **Affected surfaces**: `contracts/packs/schemas/procedure.schema.json`, new domain `Procedure*` and
  `AntiPatternEntry`, `ProcedureDso` / `ProcedureYaml` / `ProcedureBinding`, `KindBindings` and `YamlFormats` (append),
  `ArtifactEdges` (ensure no minting for procedure), registry and exactness tests, `expected-red.yaml`
  (`kind: procedure` removed).
- **Sequencing/depends-on**: none (parallel to IC-01..03); its anti-pattern entry shape is shared with the guide's
  `cautions` only by name, not by type.
- **Risks**: `ArtifactEdges` mints typed references as SUGGESTS today — procedure references must be excluded
  explicitly and tested; `anti_pattern.graph.yaml` must stay red for tags only (#236).

### IC-05 — Guide over HTTP

- **Purpose**: Change the toolguide registration to carry `guide` (the guide's Markdown) or `guideUri`, exactly one;
  fetch a URI through a guarded fetcher and store the content as the guide companion; parse and validate the guide
  before storing.
- **Relevant requirements**: FR-010, NFR-004, C-001
- **Affected surfaces**: `contracts/charter/schemas/ToolguideRegistration.yaml` (and the read DTO if it exposes
  `guidePath`), contract scenarios, `contracts/charter/CHANGELOG.md` (`### Breaking`), `ToolguideController`,
  `ToolguideRegistrationService`, `GuideFetcher` port, `HttpsGuideFetcher` adapter, `StorageProperties` (allowlist,
  limits under `charter.`), refusal codes (reuse existing ones where they fit; any new code is a contract addition).
- **Sequencing/depends-on**: IC-03
- **Risks**: SSRF — resolve the host and refuse private, loopback, link-local and unique-local addresses before
  connecting and on every redirect; pin the resolved address for the connection; HTTPS only; 3 redirects; 10 s; 1 MiB
  streamed cap; no credentials forwarded; tests use a local stub server through an injectable resolver, never the
  network.

### IC-06 — Pack migration and re-pin

- **Purpose**: A throwaway test-scope harness reads the pinned procedures, toolguides and guides in their current
  form, converts them to the sanitized shapes, and writes them with the production writer into an open-packs
  working copy; the result is reviewed and landed in spec-kitty-open-packs (one issue per class of change), then
  `openPacksRef` moves to the merged commit.
- **Relevant requirements**: FR-006, NFR-001, NFR-002, SC-001..SC-004
- **Affected surfaces**: a test-scope harness class (deleted before PR-A2 is reviewed, or kept out of the main source
  set and excluded from the build), open-packs branch + PR + issues, `gradle.properties` (`openPacksRef`),
  `known-failures.yaml` (only residual divergences, if any), `WholePackOpenPacksRoundTripTest` counts.
- **Sequencing/depends-on**: IC-03, IC-04
- **Risks**: the harness is the only place that reads the old shapes — it must never ship in `src/main`; content
  decisions the converter cannot make mechanically (heading-to-block mapping, summary/overview overlap, stray notes)
  follow the content-squad mapping table and are listed in the open-packs PR for human review; #195 must rebase onto
  the new pin (parent session informed).

### IC-07 — Decision record and docs

- **Purpose**: ADR-0042 (sanitized-shape rules R1–R13 with the shapes of procedure, anti-pattern entry and toolguide
  guide; the `content` naming rule), ADR-0039 and ADR-0040 amendments, docs changelog, `docs/guides/service-build.md`,
  glossary definitions; list the merged-schema follow-ups on epic #186.
- **Relevant requirements**: C-003, C-002
- **Affected surfaces**: `docs/adr/`, `docs/adr/README.md` index (append), `docs/changelog/`, `site/.contextive/definitions.yaml`
  (append), `contracts/packs/README.md`.
- **Sequencing/depends-on**: IC-01 (amendment for A1); others with A2.
- **Risks**: ADR numbering reserved (0042 is #194's, 0043 is #195's).

## Post-design Charter re-check

Unchanged from the table above: the only deviations are the operator-authorized ones in Complexity Tracking; the design
adds no dependency, keeps network code out of domain/application, and keeps tolerance logic out of every reader.
