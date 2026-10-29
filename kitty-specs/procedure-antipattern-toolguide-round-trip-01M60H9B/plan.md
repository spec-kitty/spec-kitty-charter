# Implementation Plan: Procedure, anti-pattern and toolguide guide round trip

**Branch**: `feat/194a-procedure-antipattern-toolguide` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/spec.md`

The planning questions are answered and recorded as Decision Moments under `decisions/`; the Engineering Alignment
was confirmed by the operator on 2026-10-29 and revised after the post-plan review squad
(`research/post-plan-squad/`, dispositions in [research.md](research.md#post-plan-squad-dispositions)).

## Summary

Bring procedures, procedure anti-patterns and toolguide guides into the whole-pack round trip with sanitized,
deliberately designed storage shapes, and build ADR-0040's `markdown` content form.

- The `markdown` form parses a Markdown document into a structural model (frontmatter, title, preamble, H2
  sections), writes it canonically and compares by the model. It serves `files` rules (with a structure schema,
  for #195's `pack.md`) and companions.
- A toolguide's guide becomes a structured `Guide` stored as a Markdown companion `<id>.guide.md` beside the
  toolguide.
- Procedures get a sanitized schema, entity and binding, following Tactic; references are kept, never minted.
- The HTTP toolguide registration carries guide content or a URI (fetched directly, guarded); the response carries
  the guide content; `guidePath` leaves the API.
- A throwaway harness migrates the pinned and vendored packs with this service's writer, verified by an old-to-new
  field mapping; the result lands in spec-kitty-open-packs and `openPacksRef` moves to it.
- ADR-0042 records the sanitized-shape rules and these shapes; ADR-0039 and ADR-0040 are amended.

Delivery: **PR-A1** = the `markdown` form, its `files`-rule schema application, gate comparison by form for `files`
rules, the ADR-0040 amendment; lands first (#195 rebases on it). **PR-A2** = everything else.

## Technical Context

**Language/Version**: Java 25 (CI authority; Temurin 25 locally), Gradle 9.8 with the Kotlin DSL convention plugins in
`build-logic/`
**Primary Dependencies**: Spring Boot, snakeyaml-engine, the JSON Schema validator behind `PackSchemas` (all
existing); the JDK `java.net.http.HttpClient` and the JDK `java.net.spi.InetAddressResolverProvider` SPI (JEP 418)
for the guide fetch. No new artifact; `gradle/verification-metadata.xml` unchanged (DIRECTIVE_051: no dependency
change; any need for one is a plan deviation for the operator).
**Markdown parsing**: a purpose-built line scanner in `packs/infra/storage` with CommonMark fence rules (backtick and
tilde fences, a closing fence of the same character at least as long as the opener, fences at any indentation hide
headings); H1/H2 recognized at column 0 only. No CommonMark library.
**Storage**: pack files via `FilesystemPackStore` / `WholePackStore`; companions per ADR-0039 (amended).
**Testing**: JUnit 5 + AssertJ in `service/packs/src/test`; corpus tests are tagged `open-packs` and run through
`:packs:openPacksRoundTrip` (part of `check`); the whole-pack gate is tagged `whole-pack` and runs through
`:packs:wholePackRoundTrip` (not part of `check`, run explicitly); contract scenarios are Cucumber in `service/deployable` (`CharterContractSuite`);
ArchUnit gate with its bad-fixture control. Per `NO_FULL_HEAVY_SUITES_IN_MISSION`, work packages run the affected
module's tests; `./gradlew verifyAll` runs once before each PR's review and in CI.
**Target Platform**: the charter service (Linux container), Spring Boot deployable.
**Project Type**: single multi-module Gradle project (`service/*`, `contracts`, `build-logic`).
**Performance Goals**: guide fetch bounded (10 s deadline, 1 MiB); the fetch runs outside the pack write lock.
**Constraints**: ADR-0036 equality of meaning, unknown fields refused, no tolerance logic; ADR-0038 one configuration
root (`charter.` properties for the fetch allowlist and limits); C-002 shared-file rule; Java style check (Spotless
palantir, Checkstyle, Error Prone, NullAway, doclint).
**Scale/Scope**: 41 procedures, 24 toolguides and guides across three packs (18 procedures and 6 toolguides also
vendored under `packs/`); 2 contract schemas and their scenarios; ADR-0042 plus two amendments.

## Charter Check

*GATE: passes before Phase 0; re-checked after Phase 1 (below).*

| Charter item | Status | Note |
|---|---|---|
| Divergence from the oracle (core must not break) | Justified deviation | Operator ruling on epic #186: operate as if core does not exist. |
| Breaking the OpenAPI contract | Pass, authorized | Contract `0.1.0-SNAPSHOT`, no released tag; 0.x may break (ADR-0022). Registration and response change authorized on #194; `### Breaking` CHANGELOG entry, scenarios. |
| Open-packs round-trips without loss of meaning | Pass by design | Migration verified by an old-to-new field mapping; the gate holds equality at the new pin. |
| No unverified dependency | Pass | JDK only. |
| Adversarial squad at plan point and before merge | Done / planned | Pre-spec, content and post-plan squads ran; a pre-PR squad runs before each PR. |
| `verifyAll` on Java 25, style, docs checks | Planned | Once before each PR's review. |
| BDD / specification by example | Planned | Contract scenarios; example fixtures with accepted/refused pairs. |

## Project Structure

### Documentation (this mission)

```
kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/                    # HTTP delta + pack schema notes
├── decisions/                    # Decision Moments
├── research/squad/               # pre-spec squad
├── research/content-squad/       # toolguide guide shape squad
├── research/post-plan-squad/     # post-plan review squad
└── tasks.md                      # /spec-kitty.tasks
```

### Source Code (repository root)

```
contracts/
├── charter/schemas/ToolguideRegistration.yaml, Toolguide.yaml   # guide | guideUri in, guide out (A2)
├── charter/paths/packs_packId_toolguides*.yaml                    # response codes (A2)
├── charter/scenarios/ (toolguides.feature, journey-05)            # rewritten (A2)
├── charter/CHANGELOG.md                                           # ### Breaking (A2)
└── packs/file-kinds.yaml, README.md, schemas/
    ├── procedure.schema.json (rewritten), toolguide.schema.json (A2)
    └── toolguide-guide.schema.json (new, A2)
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/ Procedure*, AntiPatternEntry, Guide*, Toolguide (A2)
├── application/ ToolguideRegistrationService, GuideFetcher port (A2)
└── infra/
    ├── storage/ ContentForm, PackFileKind, MarkdownDocument, MarkdownCodec, PackSchemas (A1);
    │            CompanionContent, KindBinding, Companions, WholePackStore, Procedure*/Toolguide* DSO/Yaml/Binding,
    │            KindBindings, YamlFormats (A2)
    ├── fetch/ HttpsGuideFetcher, GlobalAddressResolverProvider (+ META-INF/services) (A2)
    └── api/ ToolguideController (A2)
service/packs/src/test/... WholePackCensus, MeaningComparator, DomainRoundTrip (A1 for files rules, A2 for
                           companions), corpus tests, fetcher matrix, migration harness (A2, deleted before review)
packs/internal, packs/qa                                           # re-vendored at the new pin (A2)
gradle.properties (openPacksRef, A2)
docs/adr/0042-*.md, ADR-0039/0040 amendments, docs/changelog/, docs/guides/service-build.md,
site/.contextive/definitions.yaml
```

**Structure Decision**: existing hexagonal layout of `service/packs` and the ADR-0037 registry; no new module. The
fetcher is an application port with an infra adapter; an ArchUnit rule (with a bad fixture) keeps `java.net` out of
domain and application.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Shapes and the toolguide API diverge from core | Operator ruling on #186; sanitized shapes win | Matching core reintroduces the fields ruled out |
| Breaking contract change (registration and response) | API must not assume a shared filesystem | A bare `guidePath` leaves nothing to store or read back |
| First outbound HTTP call, with a JVM-wide resolver provider | Operator chose fetch-and-store, direct with a resolver guard | URI-only storage and proxied fetch were offered and declined |

## Implementation Concern Map

> Concerns are not work packages; `/spec-kitty.tasks` decomposes them.

### IC-01 — Markdown content form (PR-A1)

- **Purpose**: build `MarkdownDocument` / `MarkdownCodec` (parse, canonical write, model equality, refusals) and admit
  `markdown` for `files` rules, including applying a rule's structure schema (a JSON Schema over the parsed model,
  standard 2020-12 keywords only, registered with `PackSchemas`).
- **Relevant requirements**: FR-005, SC-006, NFR-002, C-006
- **Affected surfaces**: `ContentForm`, `PackFileKind`, `PackSchemas`, new `MarkdownDocument` / `MarkdownCodec`,
  test-side `WholePackCensus` / `MeaningComparator` / `DomainRoundTrip` (exhaustive dispatch by `ContentForm` for
  `files` rules), `contracts/packs/file-kinds.yaml` header, ADR-0040 amendment (decisions 1 and 3), docs changelog
  entry for A1.
- **Sequencing/depends-on**: none.
- **Risks**: model invariants at construction (no leading or trailing blank lines in content, titles single-line and
  non-blank, no H1/H2 inside content) so `parse(write(d)) == d` cannot fail late; thematic breaks are not setext
  underlines; the indented-heading refusal covers H1 and H2 only (indented H3 in lists exists in the corpus);
  `pack.md` has an H1 and no H2. Tests: unit matrix with accepted/refused neighbours for each refusal, the three
  structural non-vacuity pairs (fenced heading, in-section change, blank-line change), a fixture pack with a
  `markdown` rule through `WholePackStore`, 72/72 byte-equal corpus check. No gate outcome changes at the pin.

### IC-02 — Markdown companions in store and gate (PR-A2)

- **Purpose**: a companion declares `text` or `markdown` (with a structure schema); the store reads, writes and
  validates it by form; the gate classifies, pairs and compares companions by form.
- **Relevant requirements**: FR-005a, FR-009, NFR-002
- **Affected surfaces**: `KindBinding.Companion`, `CompanionContent` (Markdown case), `Companions` (refuse any path a
  `files` or exclusions rule classifies, not only schema-less payloads), `WholePackStore`, a `files` rule for
  `**/toolguides/*.guide.md` with `content_form: markdown`, test-side census/pairing (`MODELLED`/required kinds,
  companion outcomes), companion conflicts mapped to client errors, ADR-0039 amendment.
- **Sequencing/depends-on**: IC-01.
- **Risks**: the skill companion stays `text` with unchanged behaviour; a Markdown companion must never reach
  `Yaml.load`.

### IC-03 — Structured toolguide guide (PR-A2)

- **Purpose**: the `Guide` value (fixed blocks, freeform sections) at `<id>.guide.md` relative to the toolguide;
  H1 written from `title`, incoming H1 optional but refused when different; preamble and frontmatter refused.
- **Relevant requirements**: FR-004, FR-011
- **Affected surfaces**: `Toolguide`, `Guide` / `GuideBlock` / `GuideSection`, `ToolguideDso`, `ToolguideYaml`,
  `ToolguideBinding` (companions), `toolguide.schema.json`, `toolguide-guide.schema.json`, `expected-red.yaml`
  (`other:toolguides` removed at the re-pin).
- **Sequencing/depends-on**: IC-02.
- **Risks**: fixed-block headings are canonical (`## Overview`, `## When to use`, `## Setup`, `## Usage`,
  `## Cautions`, `## Known limitations`, `## References`); a reserved heading in another case is refused; freeform
  sections sit between `usage` and `cautions`; guide prose links are never relationships.

### IC-04 — Sanitized procedure (PR-A2)

- **Purpose**: rewrite `procedure.schema.json`; bind procedures (entity, identifier, steps with optional `role`,
  anti-pattern entries, references kept but never minted).
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-003a, FR-007, FR-008, SC-002, SC-004, SC-005, C-005
- **Affected surfaces**: `procedure.schema.json`, domain `Procedure*` and `AntiPatternEntry`, `ProcedureDso` /
  `ProcedureYaml` / `ProcedureBinding`, `KindBindings` and `YamlFormats` (append), `ArtifactEdges` (exhaustive
  per-kind decision; procedure mints nothing), `KindBindingsTest`, `PackFileKindTest` / `PackSchemasTest` counts,
  `expected-red.yaml` (`kind: procedure` removed at the re-pin), a retired-name source scan.
- **Sequencing/depends-on**: none (parallel to IC-01..03).
- **Risks**: edge-minting negative control paired with a tactic positive control in one fixture;
  `anti_pattern.graph.yaml` stays red for tags only.

### IC-05 — Guide over HTTP (PR-A2)

- **Purpose**: registration carries exactly one of `guide` or `guideUri`; the response carries `guide`; `guidePath`
  leaves the API; a URI is fetched by a guarded adapter before the pack write lock.
- **Relevant requirements**: FR-010, FR-011, NFR-004, NFR-005, C-001
- **Affected surfaces**: `ToolguideRegistration.yaml`, `Toolguide.yaml`, toolguide paths (response codes),
  `toolguides.feature` and journey-05 scenarios, `CHANGELOG.md`, `ToolguideController` (exactly-one check: null,
  empty, both), `ToolguideRegistrationService`, `GuideFetcher` port, `HttpsGuideFetcher`
  (`HttpClient.newBuilder().proxy(NO_PROXY).followRedirects(NEVER)`, manual redirects with per-hop scheme, exact-host
  allowlist and address re-checks; one deadline over DNS, every hop and the body via `sendAsync` + counting body
  subscriber + `orTimeout` + cancel; identity encoding and UTF-8 only; no credentials), `GlobalAddressResolverProvider`
  (JEP 418: for allowlisted hosts, refuses any answer that is not global unicast, covering IPv4-mapped and
  compatible IPv6, unique-local, CGNAT, 0.0.0.0, NAT64/6to4), `StorageProperties` (allowlist, limits), refusal codes
  (existing where they fit; new ones listed in the CHANGELOG and `ReservedRefusalCodesTest`), Java tests using
  `guidePath`.
- **Sequencing/depends-on**: IC-03.
- **Risks**: the `oneOf` keyword is the contract's first composition — spike what the generator emits, and enforce
  exactly-one in the controller regardless; the resolver provider is JVM-wide, so it acts only for allowlisted hosts
  and delegates otherwise; the offline test matrix uses a resolver seam and a transport seam that records the
  connect address (optionally a JDK HttpServer stub), plus a stub `GuideFetcher` bean for the BDD scenarios.

### IC-06 — Pack migration and re-pin (PR-A2)

- **Purpose**: a test-scope harness reads the pinned and vendored procedures, toolguides and guides in their current
  form, converts them (actor `human`→`operator`, `agent`/`system`→no role; `name`→`title`; `purpose`→`description`;
  anti-pattern `name`/`description`→`title`/`content`; guides renamed to `<id>.guide.md` and restructured per the
  content-squad mapping; `guide_path` relative), and writes them with the production writer into an open-packs
  working copy; an old-to-new field-mapping verifier fails on any unmapped key; counts in equal counts out
  (41/41, 24/24); conversion is idempotent.
- **Relevant requirements**: FR-006, NFR-001, NFR-002, SC-001..SC-004, C-002, C-007
- **Affected surfaces**: harness class in the test source set under an excluded `migration` tag, guarded against
  writing into the pinned checkout; open-packs branch, PR and issues; `gradle.properties` (`openPacksRef`);
  `packs/internal`, `packs/qa` (re-vendored) and `VendoredPacksTest.LOCAL_DELTA` (recomputed); `known-failures.yaml`
  (residuals only); `WholePackOpenPacksRoundTripTest` (add pinned procedure and guide counts); other entries whose
  `at` text the re-pin changes.
- **Sequencing/depends-on**: IC-03, IC-04. Order: PR-A2 code proven on fixtures → harness output → open-packs PR
  (human review of per-class diffs) → merge → one commit moves the pin, removes the baseline entries, re-vendors,
  recomputes `LOCAL_DELTA` and deletes the harness. Between the code and that commit the corpus tests (part of `check`) and the
  gate are red at the old pin by design; the PR is opened only after it.
- **Risks**: content decisions the converter cannot make mechanically follow the content-squad mapping table and
  are listed in the open-packs PR for review; #195 rebases onto the new pin (parent session informed).

### IC-07 — Decision record and docs

- **Purpose**: ADR-0042 (sanitized-shape rules, the procedure, anti-pattern entry and guide shapes, the `content`
  rule, the fetch posture and its residuals); ADR-0039 and ADR-0040 amendments; docs changelog entries (A1, A2);
  `docs/guides/service-build.md`; glossary definitions (append); `contracts/packs/README.md`; the #186 follow-up list.
- **Relevant requirements**: C-003, C-001, C-006
- **Affected surfaces**: `docs/adr/`, `docs/adr/README.md` (append), `docs/changelog/`, `site/.contextive/definitions.yaml`.
- **Sequencing/depends-on**: the ADR-0040 amendment with IC-01 (A1, which does not cite ADR-0042); the rest with A2.
- **Risks**: ADR numbering reserved (0042 is #194's, 0043 #195's).

## Exit criteria (frozen)

- **E1 (PR-A1)**: Markdown unit matrix green with the three structural non-vacuity pairs and accepted/refused
  neighbours for each refusal; 72/72 pinned Markdown files parse and write byte-equal; the fixture pack with a
  `markdown` rule round-trips and its one-character positive control fails; `PackFileKindTest` / `PackSchemasTest`
  updated; no gate outcome changes at the current pin; `verifyAll` and docs checks green.
- **E2 (PR-A2)**: `:packs:wholePackRoundTrip` green at the new pin with `procedure` and `other:toolguides` gone and the
  `anti_pattern.graph.yaml` entry intact; lossy-writer positive controls (YAML, Markdown, procedure) red;
  `VendoredPacksTest` green; edge-minting negative and positive controls green; fetcher matrix green offline;
  contract suite green in `:deployable`; ArchUnit rule and its bad fixture green; retired-name source scan green;
  harness absent from the tree; migration verification artifacts attached to the open-packs PR; `verifyAll` and docs
  checks green.

## Post-design Charter re-check

Unchanged: the only deviations are the operator-authorized ones in Complexity Tracking; no dependency is added;
network code stays out of domain and application; no reader tolerates a retired field.
