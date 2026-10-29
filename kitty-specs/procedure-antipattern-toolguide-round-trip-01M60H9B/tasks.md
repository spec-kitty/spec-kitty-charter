# Tasks: Procedure, anti-pattern and toolguide guide round trip

**Mission**: `procedure-antipattern-toolguide-round-trip-01M60H9B` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning branch / merge target**: `feat/194a-procedure-antipattern-toolguide` (two PRs to `main`, issue #194)

Eleven work packages in two delivery slices.

- **PR-A1** (lands first; #195 rebases on it): WP01 (Markdown document model and codec) → WP02 (`markdown` for
  `files` rules: vocabulary, structure schema, gate comparison, ADR-0040 amendment, A1 changelog).
- **PR-A2**: WP03 (procedure domain and sanitized schema) and WP08 (guarded guide fetcher) start once PR-A1's WPs
  are approved and run in parallel with WP05 (Markdown companions). WP04 (procedure storage) needs WP03. WP06
  (structured toolguide guide) needs WP05. WP07 (guide over HTTP) needs WP06 and WP08. WP09 (migration harness and
  open-packs PR) needs WP04 and WP06. WP10 (re-pin and re-vendor) needs WP09 and the open-packs PR merged. WP11
  (ADR-0042 and docs) needs WP04–WP08.

Every WP leaves `:packs:test` green (and `:deployable:test` for WP07). `:packs:openPacksRoundTrip` (part of `check`)
and `:packs:wholePackRoundTrip` stay green through PR-A1; from WP04 (procedures registered) and WP06 (sanitized
`guide_path`) until WP10 moves the pin they are **red at the old pin by design** (plan IC-06). PR-A2 opens only after
WP10. Reviewers of WP04–WP09 verify those WPs against fixtures, not the pinned corpus.

Shared files: each has one owning WP; a later WP that must append to a file it does not own records a one-line
rationale in its prompt (the dependency order prevents parallel collisions): `contracts/packs/file-kinds.yaml` (WP02;
WP06 appends the guide rule), `WholePackCensus.java` / `MeaningComparator.java` (WP02; WP05 adds companion pairing),
`docs/changelog/README.md` (WP02; WP11 appends), `KindBindings.java` / `YamlFormats.java` (WP04).

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `MarkdownDocument` model with construction invariants | WP01 | |
| T002 | Line scanner: frontmatter, H1/H2, CommonMark fences, refusals | WP01 | |
| T003 | Canonical writer; write-time `parse(write(d)) == d` check | WP01 | |
| T004 | Unit matrix: accepted/refused neighbours, three structural non-vacuity pairs | WP01 | [P] |
| T005 | Corpus check: 72/72 pinned Markdown files byte-equal | WP01 | [P] |
| T006 | `ContentForm.MARKDOWN` built; `PackFileKind` admits it for `files` rules | WP02 | |
| T007 | `PackSchemas`: structure schemas for Markdown rules (2020-12 keywords only) | WP02 | |
| T008 | Gate comparison by content form: `WholePackCensus`, `MeaningComparator` exhaustive dispatch | WP02 | |
| T009 | Fixture pack with a `markdown` rule; positive control | WP02 | |
| T010 | `file-kinds.yaml` header, `contracts/packs/README.md`; count tests | WP02 | [P] |
| T011 | ADR-0040 amendment (decisions 1 and 3); A1 changelog entry | WP02 | [P] |
| T012 | Rewrite `procedure.schema.json` (sanitized) | WP03 | |
| T013 | `ProcedureIdentifier`, `Procedure` entity, invariants | WP03 | |
| T014 | `ProcedureStep` with optional `role`; `AntiPatternEntry`; reference value | WP03 | [P] |
| T015 | Domain tests and schema fixture tests (accepted/refused twins) | WP03 | |
| T016 | `ProcedureDso`, `ProcedureYaml` (read/write) | WP04 | |
| T017 | `ProcedureBinding`; append to `KindBindings` and `YamlFormats` | WP04 | |
| T018 | `ArtifactEdges` exhaustive per kind; procedures mint nothing; tactic positive control | WP04 | |
| T019 | Fixture round-trip tests; registry, file-kind and schema count tests | WP04 | |
| T020 | Retired-name source scan (SC-005); `expected-red.yaml` comment | WP04 | [P] |
| T021 | `CompanionContent.Markdown`; `Companion` declares a form and structure schema | WP05 | |
| T022 | `Companions`: refuse any path a `files` or exclusions rule classifies | WP05 | |
| T023 | Store read/write/validate companions by form (`FilesystemPackStore`, `WholePackStore`) | WP05 | |
| T024 | Gate: classify, pair and compare companions by form (`DomainRoundTrip`, census) | WP05 | |
| T025 | Companion conflicts as client errors; skill companion unchanged; tests | WP05 | |
| T026 | `Guide`, `GuideBlock`, `GuideSection` domain values and rules | WP06 | |
| T027 | Guide ↔ `MarkdownDocument` mapping; H1 rule | WP06 | |
| T028 | `toolguide.schema.json` (relative `guide_path`), new `toolguide-guide.schema.json` | WP06 | [P] |
| T029 | `ToolguideDso` / `ToolguideYaml` / `ToolguideBinding` companion; `Toolguide` holds `Guide` | WP06 | |
| T030 | Guide `files` rule (`**/toolguides/*.guide.md`, markdown) appended to `file-kinds.yaml` | WP06 | |
| T031 | Guide tests: fixtures, refusals, fingerprint on guide-only change | WP06 | |
| T032 | `GuideFetcher` port and typed fetch refusals | WP08 | |
| T033 | `GlobalAddressResolverProvider` (JEP 418) + service registration | WP08 | |
| T034 | `HttpsGuideFetcher`: no proxy, manual redirects, single deadline, caps, encoding | WP08 | |
| T035 | `StorageProperties`: allowlist and limits under `charter.` | WP08 | [P] |
| T036 | Offline fetch matrix (resolver and transport seams) | WP08 | |
| T037 | ArchUnit rule: no `java.net` in domain/application, with bad fixture | WP08 | [P] |
| T038 | Contract: `ToolguideRegistration` (guide \| guideUri), `Toolguide` (guide), paths, CHANGELOG | WP07 | |
| T039 | `ToolguideController` exactly-one check, 1 MiB cap, DTO mapping | WP07 | |
| T040 | `ToolguideRegistrationService`: fetch before the write lock, store guide | WP07 | |
| T041 | Refusal codes (reuse or add; `ReservedRefusalCodesTest`) | WP07 | |
| T042 | Rewrite `toolguides.feature`, journey-05; stub `GuideFetcher` bean | WP07 | |
| T043 | Rewrite Java tests that use `guidePath` | WP07 | [P] |
| T044 | Harness: old-shape reader → new domain → production writer (test scope, `migration` tag) | WP09 | |
| T045 | Old-to-new field-mapping verifier, counts, idempotence | WP09 | |
| T046 | Guide restructuring per the content-squad mapping; manual-decision log | WP09 | |
| T047 | Run over built-in, internal, qa; per-class diff artifacts | WP09 | |
| T048 | Open-packs issues per class of change; open-packs PR | WP09 | |
| T049 | Move `openPacksRef`; re-vendor `packs/internal`, `packs/qa`; recompute `LOCAL_DELTA` | WP10 | |
| T050 | `expected-red.yaml`: remove `procedure`, `other:toolguides`; refresh changed `at` texts | WP10 | |
| T051 | Corpus tests for procedures and guides; pinned counts in `WholePackOpenPacksRoundTripTest` | WP10 | |
| T052 | Delete the harness; prove its absence | WP10 | |
| T053 | Full gate and `verifyAll` at the new pin | WP10 | |
| T054 | ADR-0042 (sanitized shapes, guide, fetch posture, residuals) | WP11 | |
| T055 | ADR-0039 amendment; ADR index | WP11 | [P] |
| T056 | `docs/guides/service-build.md`; glossary definitions | WP11 | [P] |
| T057 | A2 changelog entry | WP11 | [P] |
| T058 | Epic #186 follow-up list draft | WP11 | [P] |

## PR-A1 — Markdown content form

### WP01 — Markdown document model and codec

Prompt: [tasks/WP01-markdown-document-model-and-codec.md](tasks/WP01-markdown-document-model-and-codec.md)
Goal: a parsed Markdown model and a canonical codec with exact refusals. Priority P1. Independent test: unit matrix
and the 72/72 corpus check. Depends on: none.

T001 `MarkdownDocument` model (WP01)
T002 Line scanner (WP01)
T003 Canonical writer (WP01)
T004 Unit matrix (WP01)
T005 Corpus byte check (WP01)

### WP02 — `markdown` for `files` rules

Prompt: [tasks/WP02-markdown-form-for-files-rules.md](tasks/WP02-markdown-form-for-files-rules.md)
Goal: admit `markdown` in the file-kind table, apply a rule's structure schema, compare by form in the gate.
Priority P1. Independent test: fixture pack positive control; gate unchanged at the pin. Depends on: WP01.

T006 `ContentForm` / `PackFileKind` (WP02)
T007 `PackSchemas` structure schemas (WP02)
T008 Gate comparison by form (WP02)
T009 Fixture pack (WP02)
T010 Table header, README, count tests (WP02)
T011 ADR-0040 amendment, A1 changelog (WP02)

## PR-A2 — Procedures, guides, HTTP, migration

### WP03 — Procedure domain and sanitized schema

Prompt: [tasks/WP03-procedure-domain-and-sanitized-schema.md](tasks/WP03-procedure-domain-and-sanitized-schema.md)
Depends on: WP02. Parallel with WP05, WP08.

T012 `procedure.schema.json` (WP03)
T013 `Procedure` entity (WP03)
T014 Steps, anti-pattern entries, references (WP03)
T015 Domain and schema tests (WP03)

### WP04 — Procedure storage binding

Prompt: [tasks/WP04-procedure-storage-binding.md](tasks/WP04-procedure-storage-binding.md)
Depends on: WP03.

T016 DSO and YAML (WP04)
T017 Binding and registration (WP04)
T018 No minting from procedure references (WP04)
T019 Fixture and registry tests (WP04)
T020 Retired-name scan (WP04)

### WP05 — Markdown companions in store and gate

Prompt: [tasks/WP05-markdown-companions-in-store-and-gate.md](tasks/WP05-markdown-companions-in-store-and-gate.md)
Depends on: WP02.

T021 Companion form (WP05)
T022 Ownership rule (WP05)
T023 Store by form (WP05)
T024 Gate pairing (WP05)
T025 Conflicts and tests (WP05)

### WP06 — Structured toolguide guide

Prompt: [tasks/WP06-structured-toolguide-guide.md](tasks/WP06-structured-toolguide-guide.md)
Depends on: WP05.

T026 `Guide` values (WP06)
T027 Mapping and H1 rule (WP06)
T028 Schemas (WP06)
T029 Toolguide binding (WP06)
T030 Guide file rule (WP06)
T031 Guide tests (WP06)

### WP08 — Guarded guide fetcher

Prompt: [tasks/WP08-guarded-guide-fetcher.md](tasks/WP08-guarded-guide-fetcher.md)
Depends on: WP02. Parallel with WP03–WP06.

T032 Port and refusals (WP08)
T033 Resolver guard (WP08)
T034 Fetcher (WP08)
T035 Properties (WP08)
T036 Offline matrix (WP08)
T037 ArchUnit rule (WP08)

### WP07 — Guide over HTTP

Prompt: [tasks/WP07-guide-over-http.md](tasks/WP07-guide-over-http.md)
Depends on: WP06, WP08.

T038 Contract (WP07)
T039 Controller (WP07)
T040 Service (WP07)
T041 Refusal codes (WP07)
T042 Scenarios (WP07)
T043 Java tests (WP07)

### WP09 — Migration harness and open-packs PR

Prompt: [tasks/WP09-migration-harness-and-open-packs-pr.md](tasks/WP09-migration-harness-and-open-packs-pr.md)
Depends on: WP04, WP06.

T044 Harness (WP09)
T045 Mapping verifier (WP09)
T046 Guide restructuring (WP09)
T047 Run and artifacts (WP09)
T048 Issues and PR (WP09)

### WP10 — Re-pin and re-vendor

Prompt: [tasks/WP10-re-pin-and-re-vendor.md](tasks/WP10-re-pin-and-re-vendor.md)
Depends on: WP09 (and the open-packs PR merged), WP07.

T049 Pin and vendored packs (WP10)
T050 Baseline (WP10)
T051 Corpus tests and counts (WP10)
T052 Harness deleted (WP10)
T053 Full gate (WP10)

### WP11 — ADR-0042 and living docs

Prompt: [tasks/WP11-adr-0042-and-living-docs.md](tasks/WP11-adr-0042-and-living-docs.md)
Depends on: WP04, WP05, WP06, WP07, WP08.

T054 ADR-0042 (WP11)
T055 ADR-0039 amendment, index (WP11)
T056 Service-build guide, glossary (WP11)
T057 A2 changelog (WP11)
T058 #186 follow-up list (WP11)
