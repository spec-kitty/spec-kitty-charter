# Tasks: Pack schemas and pack-level policy in the domain

**Mission**: `pack-schemas-and-level-policy-01M5AFMA` · **Branch**: `feat/pack-schemas-and-level-policy`

**Inputs**:
- [spec.md](spec.md) (rulings R1-R16)
- [plan.md](plan.md) (concerns IC-01 to IC-08, calls P1-P12)
- [data-model.md](data-model.md)
- [research.md](research.md) (squad dispositions R8-R10)
- [contracts/](contracts/)
- [quickstart.md](quickstart.md)

## Mission-wide rules (every WP)

1. **Red first, as an assertion failure** (C-007). Create the new type or method as a stub that
   throws `UnsupportedOperationException`, or that returns an empty result. Write the test, run it,
   and record the failing **assertion** output, not a compile error. Then implement. Record the red
   output with `spec-kitty agent tracer-append`, or in the review handoff.
2. **Every commit compiles and keeps its lane green.** Never commit a red scenario or test. A
   wire-visible change lands in the same WP as its scenario.
3. **Gradle invocation.**
   - Always pass an absolute `-PopenPacksDir`. The default `../spec-kitty-open-packs` resolves
     wrongly inside `.worktrees/<lane>/`.
     - WP01 uses the old-layout clone at `7f063ae`: `/home/user/spec-kitty/spec-kitty-open-packs`.
     - Every WP from WP03 on uses the new-layout pin: `/home/user/spec-kitty-open-packs`.
   - Use `--max-workers=2`.
   - Use JDK 25: `export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH`.
   - On HTTP 429 from Maven Central, wait and retry. Never edit the verification metadata to get
     past a fetch error.
   - `-PcontractBaseline=none` is allowed only when the clone is shallow **and**
     `git tag --list 'contract-charter-v*'` is empty after `git fetch --tags`. Say so in the
     evidence.
4. **Style gates** (`verifyAll`):
   - palantir-java-format: run `spotlessApply`.
   - Checkstyle. Records nested in interfaces take no `public`/`static` modifiers. An all-static
     class is `final` with a private constructor.
   - Error Prone and NullAway, with `-Werror`.
   - doclint `all`, which needs `@param` for every record component and Javadoc on every public
     element.
   - jspecify `@Nullable` only. No other annotations in the domain.
5. **Commit trailer.** Every commit ends with
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
6. **Model routing.** Implementers run on sonnet and reviewers on opus. The charter
   (`.kittify/charter/charter.md`) wins over any prompt.
7. **External gate (K1).** The charter PR must not merge before open-packs PR #52 is merged and
   `openPacksRef` points at its merge commit. That merge and re-pin is an operator-owned step;
   WP08 T041 records the gate.

## Streams and dependencies

```text
Stream A (domain → adapter; one lane: shares PackPolicy, readers, repository, packs.feature)
  WP01 ─► WP05 ─► WP06 ─► WP07 ─► WP08
Stream B (schemas)            WP02 ─┬────────────► WP04 ─► WP07
Stream C (open-packs, build)  WP03 ─┘ (WP04 also needs WP01)
Docs                          WP02 ─► WP09 ─► WP08 (register ids)
                              WP07 + WP09 ─► WP10
```

- WP01, WP02 and WP03 start in parallel.
- WP05 depends only on WP01, so the domain chain runs alongside the dependency work (WP04).
- WP07 needs WP06, WP04 and WP03: the pinned corpus must already be URN-only (open-packs #52).
- WP09 is the early docs piece (the register and the ADR amendment). WP08 needs its DIV ids.
  WP10 follows WP07, so the docs describe what landed.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Pin the pack-level texts red-first (stubs; adapter ratchets) | WP01 | |
| T002 | Finding model types and the `PackInspector` port | WP01 | |
| T003 | `PackPolicy` (static) with the level rules; `CharterPack` guard | WP01 | |
| T004 | The adapter reports the declared level as a fact; repository delegates | WP01 | |
| T005 | ArchUnit: pack-level constants confined (prefix exemptions); networknt confined | WP01 | |
| T006 | Test-shape fixes, repository-port Javadoc, gate run | WP01 | |
| T007 | `sync_schemas.py`: export the 14 models from core's venv | WP02 | [P] |
| T008 | `contracts/packs/file-kinds.yaml` | WP02 | [P] |
| T009 | Transform: changes C1-C16 on import | WP02 | |
| T010 | `VENDORED.md` and `README.md` | WP02 | |
| T011 | Corpus probe (own classifier) and reproducibility check | WP02 | |
| T012 | Pin plumbing; `OpenPacksCorpus` (including the DIAGNOSTICS table) | WP03 | |
| T013 | `OpenPacksRoundTripTest` on the new layout (red-first) | WP03 | |
| T014 | CI reads the pin from `gradle.properties` | WP03 | [P] |
| T015 | Re-vendor `packs/internal` and `packs/qa`; local governance check | WP03 | [P] |
| T016 | Catalog entry and build file: networknt 3.0.8, exclusions, BOM | WP04 | |
| T017 | Verification metadata and `dependencyInsight` evidence | WP04 | |
| T018 | Bundle `contracts/packs`; `PackSchemas` and `PackFileKind` | WP04 | |
| T019 | `PackSchemasTest` and `PackFileKindTest` | WP04 | |
| T020 | `NodeKind.fromPlural` (R14) | WP05 | |
| T021 | Detail vocabulary: `SchemaViolation` to text | WP05 | |
| T022 | Graph building from `RawGraph`; the `load(contents)` overload | WP05 | |
| T023 | Precedence ordering; multi-defect test | WP05 | |
| T024 | Vocabulary equality and schema keyword-coverage tests | WP07 | |
| T025 | `Pep440.isValidSpecifierSet` with a provenance corpus | WP06 | |
| T026 | Rules: `requires_core_invalid`, `pack_unknown_key` (code; projection in WP07) | WP06 | |
| T027 | Rules: `duplicate_step_id`, `duplicate_artifact_id` | WP06 | [P] |
| T028 | Rules: `asset_path_escape`, `asset_mime_invalid`; MIME table | WP06 | [P] |
| T029 | PIT at 80% or more, with survivors explained | WP06 | |
| T030 | Contract deltas D1-D7, `org_fragment_missing`, `Diagnostic.Code.PACK_UNKNOWN_KEY`, scenarios (red) | WP07 | |
| T031 | `GraphDocumentReader` reports facts | WP07 | |
| T032 | `PackDescriptorReader` reports facts | WP07 | |
| T033 | `FileSystemPackInspector` (LOAD_PATH); repository delegates; old overload deleted | WP07 | |
| T034 | `LoadOutcomeTest` (independent oracle), precedence and pinned texts | WP07 | |
| T035 | Fixture updates; full gate | WP07 | |
| T036 | `PackValidation`, FULL scope, three-defect test | WP08 | |
| T037 | `OpenPacksAcceptanceTest`: pinned clean HEAD, exact counts, all valid | WP08 | |
| T038 | Planted files for C1-C16, every rule, R15, P2, P7 and the positive controls | WP08 | |
| T039 | `core-verdicts.json` with derived agreement | WP08 | |
| T040 | `ScopeParityTest` (LOAD_PATH against FULL); NFR-002 thresholds | WP08 | |
| T041 | `charter.yaml` measurement; pre-merge pin gate (K1); final gate | WP08 | |
| T042 | Divergence register, DIV-001 to DIV-019 | WP09 | |
| T043 | ADR-0017 dated amendment | WP09 | |
| T044 | Architecture docs, `service-build.md`, open-questions | WP10 | |
| T045 | Glossary (`domain.md`, `terminology.yaml`); the generated page untouched | WP10 | |
| T046 | PlantUML write-model diagram | WP10 | |
| T047 | Follow-ups, WP09's wordlist terms, docs gates | WP10 | |

## Phase 1 — Foundations (parallel)

### WP01 — Domain finding model and the pack-level policy (#48)

**Prompt**: [tasks/WP01-domain-finding-model-and-pack-level-policy.md](tasks/WP01-domain-finding-model-and-pack-level-policy.md) · **Priority**: P1 · **Depends on**: none

**Goal**: The domain alone decides the missing and mismatch outcomes and their texts, owns the
finding model, and ArchUnit keeps the constants confined.

**Independent test**: Rule A is red on today's code and green after. The pinned texts are
unchanged.

T001 Pin the pack-level texts red-first (WP01)
T002 Finding model types and the PackInspector port (WP01)
T003 PackPolicy with the level rules (WP01)
T004 Adapter reports the declared level as a fact (WP01)
T005 ArchUnit confinement rules (WP01)
T006 Test-shape fixes, Javadoc, gate run (WP01)

### WP02 — Canonical pack schemas imported with provenance

**Prompt**: [tasks/WP02-canonical-pack-schemas.md](tasks/WP02-canonical-pack-schemas.md) · **Priority**: P1 · **Depends on**: none

**Goal**: 14 schemas, reproducible from core `9554e7b2`, with C1-C16 listed in `VENDORED.md`.
`file-kinds.yaml` is the single data authority.

**Independent test**: `--check` shows no diff. The probe finds 409 files mapped and valid, and 107
excluded.

T007 sync_schemas.py export (WP02)
T008 file-kinds.yaml (WP02)
T009 Transform C1-C16 (WP02)
T010 VENDORED.md and README.md (WP02)
T011 Corpus probe and reproducibility (WP02)

### WP03 — Open-packs layout catch-up, pin and re-vendor

**Prompt**: [tasks/WP03-open-packs-catch-up-and-pin.md](tasks/WP03-open-packs-catch-up-and-pin.md) · **Priority**: P1 · **Depends on**: none

**Goal**: Read the `packs/built-in` layout, pinned to #52's head, with one source of truth for the
pin, and refresh the vendored packs.

**Independent test**: The round-trip goes from red to green on the new layout. CI fails on a blank
pin.

T012 Pin plumbing (WP03)
T013 Round-trip on the new layout (WP03)
T014 CI pin step (WP03)
T015 Re-vendor and local governance check (WP03)

## Phase 2 — Dependency, domain rules and early docs

### WP04 — Validator dependency and schema loading

**Prompt**: [tasks/WP04-validator-dependency-and-schema-loading.md](tasks/WP04-validator-dependency-and-schema-loading.md) · **Priority**: P1 · **Depends on**: WP01, WP02, WP03

T016 Catalog and build file (WP04)
T017 Verification metadata and dependency evidence (WP04)
T018 Bundle schemas; PackSchemas, PackFileKind (WP04)
T019 PackSchemasTest, PackFileKindTest (WP04)

### WP05 — Domain vocabulary and graph rules

**Prompt**: [tasks/WP05-domain-vocabulary-and-graph-rules.md](tasks/WP05-domain-vocabulary-and-graph-rules.md) · **Priority**: P1 · **Depends on**: WP01

T020 NodeKind.fromPlural (WP05)
T021 Detail vocabulary (WP05)
T022 Graph building and the load(contents) overload (WP05)
T023 Precedence ordering (WP05)

### WP06 — Validate-mode domain rules

**Prompt**: [tasks/WP06-validate-mode-domain-rules.md](tasks/WP06-validate-mode-domain-rules.md) · **Priority**: P2 · **Depends on**: WP05

T025 Pep440 (WP06)
T026 Descriptor rules (WP06)
T027 Duplicate id rules (WP06)
T028 Asset rules (WP06)
T029 PIT (WP06)

### WP09 — Divergence register and the ADR-0017 amendment

**Prompt**: [tasks/WP09-divergence-register-and-adr-amendment.md](tasks/WP09-divergence-register-and-adr-amendment.md) · **Priority**: P1 · **Depends on**: WP02

T042 Divergence register (WP09)
T043 ADR-0017 amendment (WP09)

## Phase 3 — Integration

### WP07 — Adapter load path on the schemas, and the contract changes scenarios-first

**Prompt**: [tasks/WP07-adapter-load-path-and-contract-changes.md](tasks/WP07-adapter-load-path-and-contract-changes.md) · **Priority**: P1 · **Depends on**: WP06, WP03, WP04

**Risks**: K2 (runner A), K3 and K4.

T024 Vocabulary equality and keyword coverage (WP07)
T030 Contract deltas, org fragment rule, scenarios first (WP07)
T031 GraphDocumentReader facts (WP07)
T032 PackDescriptorReader facts (WP07)
T033 Inspector LOAD_PATH; repository (WP07)
T034 LoadOutcomeTest, precedence, pinned texts (WP07)
T035 Fixture updates; full gate (WP07)

### WP08 — Validate mode and non-vacuous open-packs acceptance

**Prompt**: [tasks/WP08-validate-mode-and-open-packs-acceptance.md](tasks/WP08-validate-mode-and-open-packs-acceptance.md) · **Priority**: P1 · **Depends on**: WP07, WP09

T036 PackValidation, FULL scope (WP08)
T037 OpenPacksAcceptanceTest (WP08)
T038 Planted files (WP08)
T039 core-verdicts.json (WP08)
T040 ScopeParityTest; NFR-002 (WP08)
T041 charter.yaml; pin gate; final gate (WP08)

## Phase 4 — Documentation

### WP10 — Architecture docs, glossary and the write-model diagram

**Prompt**: [tasks/WP10-architecture-docs-glossary-write-model.md](tasks/WP10-architecture-docs-glossary-write-model.md) · **Priority**: P2 · **Depends on**: WP07, WP09

T044 Architecture docs (WP10)
T045 Glossary (WP10)
T046 PlantUML write model (WP10)
T047 Follow-ups, WP09 wordlist terms, docs gates (WP10)

## MVP

- **#48** is resolved by WP01 alone.
- **#114's load-path validation** needs WP01 to WP07.
- **#114's acceptance** is completed by WP08, which also prepares #115.
