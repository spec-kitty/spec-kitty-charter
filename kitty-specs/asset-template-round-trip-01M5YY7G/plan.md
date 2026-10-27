# Implementation Plan: Asset and template round trip

**Branch**: `feat/asset-template-round-trip` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/asset-template-round-trip-01M5YY7G/spec.md`

## Summary

Model the two payload-bearing kinds of a Charter Pack — **asset** (a `*.asset.yaml` sidecar plus the payload file it
names) and **template** (a file under the pack's top-level `templates/`) — and read and write them through the ADR-0037
registry, so the whole-pack gate (#187) turns `asset`, `asset-payload` and `template` green. The pack vocabulary
(`contracts/packs/file-kinds.yaml`) gains the **content form** of a file (`yaml` default, `markdown`, `text`, `bytes`)
and a `payloads` section for schema-less files, consulted after `files` and `exclusions`. This mission builds only the
`bytes` form. ADR-0040 records the decision; ADR-0036's extension-based rows are revised in place.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; local fallback `-Pcharter.javaVersion=21`, CI always 25)
**Primary Dependencies**: Spring Boot (existing), SnakeYAML via the service's `Yaml` wrapper, networknt JSON Schema
validator (existing `PackSchemas`); no new dependency
**Storage**: Files — pack directories under the configuration root (ADR-0038); payload files written byte for byte
**Testing**: JUnit 5 + AssertJ; whole-pack gate (`:packs:wholePackRoundTrip`, tag `whole-pack`), open-packs round trip
(`:packs:openPacksRoundTrip`, tag `open-packs`), ArchUnit (`HexagonalArchitectureTest`), `./gradlew verifyAll`
**Target Platform**: JVM service (Linux CI, developer machines)
**Project Type**: single (Gradle multi-module service: `service/kernel`, `service/packs`, `service/deployable`, …)
**Performance Goals**: whole-pack load of the internal pack (~0.6 MB of payloads) grows by under 1 s (NFR-004)
**Constraints**: HTTP contract frozen (C-001); append-only edits to shared files (C-002); no pack-specific logic (C-003);
bytes form only (C-004); file mode not meaning (C-005)
**Scale/Scope**: 20 sidecars, 23 asset payload files (4 binary, ≤156 KB), 2 top-level templates across the open packs

### Supply chain (DIRECTIVE_051)

No dependency is added, upgraded or removed. Strict SHA-256 dependency verification is unchanged. Nothing to check.

## Charter Check

| Charter rule | How this plan meets it |
|---|---|
| Divergence from the oracle must not break core | Core treats asset payloads and templates as opaque files located by path; byte-for-byte round trip matches. Core's built-in path anchoring is not copied (C-003); the six built-in sidecars are refused and pinned, fix filed in open-packs. |
| No incompatible OpenAPI change | No endpoint, schema or refusal code of `contracts/charter/openapi.yaml` changes (C-001). `Charter-Source-Fingerprint` values change once for packs holding assets or templates (a value change, ADR-0037 negative consequence). |
| Open-packs YAML round-trips exactly | Sidecars compare by meaning (ADR-0036); payloads and templates compare by bytes, stricter than meaning. |
| No unverified dependency | None added. |
| Adversarial squad at plan point for contract/architecture/domain changes | Post-plan squad (architect, researcher, reducer lenses) runs before tasks; findings dispositioned in `research.md`. |
| BDD / specification by example; test first | Each concern starts from the spec's acceptance scenarios as failing tests; refusal tests paired with same-fixture positive controls (tactic acceptance-criteria-non-vacuity). |
| DDD in core's vocabulary | `Asset`, `Template` implement `CharterArtifact` (ADR-0035) in `packs.domain`; "content form" added to the glossary. |
| Decision documentation (DIRECTIVE_003) | ADR-0040; ADR-0036 revised in place; ADR index appended. |
| Living documentation (DIRECTIVE_037) | `file-kinds.yaml` header, `contracts/packs/README.md`, `docs/architecture/logical-model.md` assets/templates section, a `docs/changelog/` entry, glossary updated in the same PR. |
| Boy-scout vs smallest diff | Only what our kinds need; `OPEN_MAPS` duplication in the comparator noted, not refactored. |

Gate result: pass, no violations to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/asset-template-round-trip-01M5YY7G/
├── spec.md
├── plan.md              # this file
├── research.md          # decisions, squad findings and dispositions
├── data-model.md        # Asset, AssetFile, Template, Payload, ContentForm
├── quickstart.md        # how to see it work
├── contracts/
│   └── file-kinds-content-form.md   # the file-kinds.yaml shape delta
├── checklists/requirements.md
└── decisions/
```

### Source Code (repository root)

```
contracts/packs/
├── file-kinds.yaml                  # + content_form on rules, + payloads section, − **/*.config.yaml exclusion
└── README.md                        # vocabulary doc
service/kernel/                      # unchanged (ArtifactKind.ASSET/TEMPLATE exist)
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/
│   ├── Asset.java, AssetIdentifier.java, AssetFile.java, Payload.java       # new
│   ├── Template.java, TemplateIdentifier.java                               # new
│   ├── AssetRepository.java, TemplateRepository.java                        # new ports
│   └── Pack.java                    # + assetFiles, + assets()/templates() views, + payload invariant
└── infra/storage/
    ├── ContentForm.java                                                     # new
    ├── AssetBinding.java, AssetDso.java, AssetYaml.java                     # new (YAML kind)
    ├── FileBinding.java, FileBindings.java, TemplateBinding.java            # new (bytes kinds)
    ├── KindBindings.java, YamlFormats.java                                  # append ASSET
    ├── PackFileKind.java            # parse content_form + payloads section
    ├── PackFileSet.java             # walk all files; digest payload kinds after YAML kinds
    ├── FilesystemPackStore.java     # read/write bytes; asset payload presence check
    ├── DomainRepositoryService.java # AssetRepository, TemplateRepository; assemble payloads
    └── WholePackStore.java          # write bytes; layout for assets/templates
service/packs/src/test/…/infra/storage/
├── DomainRoundTrip.java, WholePackRoundTripTest.java, WholePackCensus.java  # append our kinds; bytes comparison
├── WholePackOpenPacksRoundTripTest.java + resources/whole-pack/expected-violations.yaml   # pinned six
└── resources/whole-pack/expected-red.yaml                                   # − 3 kinds, + 6 files
docs/adr/0040-content-form-and-payload-kinds.md, docs/adr/0036-…, docs/adr/README.md
docs/architecture/logical-model.md, docs/changelog/2026-10-27-asset-and-template-round-trip.md, site/.contextive/definitions.yaml (glossary)
```

**Structure Decision**: Existing single Gradle service; all code in the `packs` slice (ADR-0006), domain types in
`packs.domain`, storage in `packs.infra.storage`. No new module.

## Complexity Tracking

None.

## Key design decisions (detail in research.md)

- **D1 Content form lives on the file-kind rule.** `files` rules gain optional `content_form` (default `yaml`). A new
  `payloads` section holds schema-less rules (`match`, `kind`, `content_form`), consulted only when no `files` or
  `exclusions` rule matches. Extension and `mime` never choose a form.
- **D2 Asset = sidecar metadata; the bytes are an `AssetFile` the pack holds.** Every file the `*/assets/**` payload
  rule classifies is an `AssetFile(path under assets/, Payload)` held by the `Pack`; an `Asset` names one by `path`.
  This gives the sidecar-less config file (R1) a home without an invented id, and each byte lives once.
- **D3 Missing payload is refused at assembly, guarded in the domain.** The store refuses a sidecar whose payload
  file is absent as a `PackElementViolation` (R3); the `Pack` invariant "every asset names a held asset file" then
  holds for every pack the store builds and breaches loudly for a hand-built one.
- **D4 Template is a bytes artifact.** `Template(TemplateIdentifier = path under templates/, Payload)`, bound by a
  `FileBinding` (not a `KindBinding`, which is YAML-shaped and schema-named).
- **D5 Fingerprint covers the new files.** Sidecars join through `KindBindings` (appended); asset files and templates are digested
  last (after `drg/fragment.yaml`), so packs without them keep their exact digest (NFR-002).
- **D6 The gate keeps `DomainRoundTrip`.** Append-only blocks: save assets, asset files and templates; compare files
  of a `bytes` kind by bytes. The adapter switch to `WholePackStore` stays out of scope (shared with siblings).
- **D7 Pinned expected violations.** `WholePackOpenPacksRoundTripTest` reads
  `whole-pack/expected-violations.yaml` (pack, file, reason, issue) and ratchets both ways (R10).

## Implementation Concern Map

### IC-01 — Content form in the pack vocabulary

- **Purpose**: Let `file-kinds.yaml` say how each file is read and compared, and classify schema-less payload files.
- **Relevant requirements**: FR-001, FR-004, FR-008, FR-013
- **Affected surfaces**: `contracts/packs/file-kinds.yaml`, `contracts/packs/README.md`, `PackFileKind`,
  `PackFileKindTest`, `VocabularyEqualityTest`, `FileSystemPackInspector`/`PackSchemas` (they iterate `files` rules —
  unaffected as `payloads` rules carry no schema), `ContentForm`
- **Sequencing/depends-on**: none
- **Risks**: shared file with #192 (adds `markdown` rows): keep our rows in one block; retiring the `**/*.config.yaml`
  exclusion is the one non-append edit, justified by R1 (its only match is our payload).

### IC-02 — Asset and template domain model

- **Purpose**: `Asset`, `AssetFile`, `Payload`, `Template` and their identifiers implement the domain with invariants.
- **Relevant requirements**: FR-002, FR-003, FR-004, FR-007, FR-012
- **Affected surfaces**: `packs.domain` (new types, `Pack` views and invariant, repository ports),
  `HexagonalArchitectureTest` must stay green
- **Sequencing/depends-on**: none
- **Risks**: `Pack` gains a second collection beside the kind map; keep it a value map with one invariant.

### IC-03 — Storage: read, write and fingerprint payload kinds

- **Purpose**: Bind assets (YAML) and templates (bytes), read and write payload bytes, refuse missing payloads, digest
  the new files.
- **Relevant requirements**: FR-002–FR-007, FR-011
- **Affected surfaces**: `KindBindings`, `YamlFormats`, `FileBinding(s)`, `TemplateBinding`, `AssetBinding/Dso/Yaml`,
  `PackFileSet`, `FilesystemPackStore`, `DomainRepositoryService`, `WholePackStore`, `PackLayout`
- **Sequencing/depends-on**: IC-01, IC-02
- **Risks**: `KindBindings`/`YamlFormats` are append-only hotspots shared with #190/#191/#192; the walk widening must
  keep the YAML kinds' order and digests unchanged.

### IC-04 — Relationship endpoints

- **Purpose**: `asset:` and `template:` URNs become valid relationship endpoints once held (R6).
- **Relevant requirements**: FR-012
- **Affected surfaces**: `RelationshipRegistrationService` test (behaviour follows from `pack.artifacts()`)
- **Sequencing/depends-on**: IC-03
- **Risks**: a served behaviour change with no contract shape change: recorded in ADR-0040 and the changelog entry.

### IC-05 — Gate and open-packs ratchets

- **Purpose**: Turn our three gate kinds green; pin the six built-in refusals in both gates.
- **Relevant requirements**: FR-009, FR-010, SC-001–SC-003, NFR-001
- **Affected surfaces**: `DomainRoundTrip`, `WholePackRoundTripTest` (`REQUIRED_KINDS`, bytes comparison, lossy-byte
  control), `WholePackCensus`, `expected-red.yaml`, `WholePackOpenPacksRoundTripTest` + `expected-violations.yaml`
- **Sequencing/depends-on**: IC-03
- **Risks**: `REQUIRED_KINDS`/`ARTEFACT_KINDS`/`expected-red.yaml` are edited by every sibling: rebase after any
  sibling merge and keep edits to our lines.

### IC-06 — Decision records and living docs

- **Purpose**: Record content form and the non-YAML widening; revise ADR-0036 rows; update glossary, logical model,
  changelog entry; prepare the open-packs issue text.
- **Relevant requirements**: FR-014
- **Affected surfaces**: `docs/adr/0040-*.md`, `docs/adr/0036-*.md`, `docs/adr/README.md`,
  `docs/architecture/logical-model.md`, glossary `site/.contextive/definitions.yaml`, `docs/changelog/2026-10-27-asset-and-template-round-trip.md` (a new file; no shared changelog)
- **Sequencing/depends-on**: IC-01 (decision content), lands with IC-05
- **Risks**: docs checks (`check_docs.py`, markdownlint, cspell, lychee) must stay green.
