# Implementation Plan: Mission-type consolidation into the sanitized model

**Branch**: `issue-194-mission-type-consolidation` | **Date**: 2026-10-30 (revised after the post-plan squad) |
**Spec**: [spec.md](spec.md)
**Input**: `kitty-specs/mission-type-consolidation-01M65MCK/spec.md`, ADR-0044, decisions D1 to D6 and D1a under
`decisions/`, the post-plan squad dispositions in [research.md](research.md#post-plan-squad-dispositions-2026-10-30).

## Summary

The mission tier becomes one sanitized artifact kind, `mission_type`, stored as one file per type at
`missions/<id>/<id>.mission-type.yaml`, its steps as sub-items of `steps` and `on_demand_steps`, each step's prompt and
guidelines as Markdown companions. The domain lives in a new `:missions` module; the pack store binds it. Retired file
families are classified `Retired` and refused by name. A throwaway harness migrates the pinned mission tiers with the
service's own writer; the orchestrator lands the result in `spec-kitty-open-packs`; the charter re-pins in one commit
that also removes the old exclusions, adds the `retired` rules and deletes the harness.

## Technical Context

**Language/Version**: Java 25, Gradle 9.8 (Kotlin DSL convention plugins in `build-logic/`).
**Primary Dependencies**: existing only. `gradle/verification-metadata.xml` unchanged.
**Storage**: `FilesystemPackStore` / `WholePackStore`; companions per ADR-0039 as amended by ADR-0043.
**Testing**: JUnit 5 + AssertJ. `:missions` and `:packs` unit tests; `open-packs`-tagged corpus tests in
`:packs:openPacksRoundTrip` (part of `check`); the `whole-pack` gate in `:packs:wholePackRoundTrip`; ArchUnit in
`:deployable`. Each WP runs its module's tests and `:packs:openPacksRoundTrip`; `./gradlew verifyAll` before review
and in CI.
**Constraints**: ADR-0036, S1 to S16, no tolerance logic, zero new suppressions, complexity at most 15 (measured
once in review), no HTTP schema or operation change, no core change, shared-file discipline with #260.
**Scale** (pinned, verified by the harness): 6 mission types (built-in 4, cave-kitty 1, qa 1); 40 sequenced steps
(documentation 7, plan 4, research 6, software-dev 8, cave-dev 8, qa 7); 6 on-demand steps; 43 prompts; 17 guidelines;
153 retired files (built-in 121, cave-kitty 13, qa 19).

## Charter Check

| Charter item | Status | Note |
|---|---|---|
| Divergence from the charter's own model | Pass | The model is the charter's own (ADR-0044); core follows later (epic #186). |
| Breaking the OpenAPI contract | Pass | No schema or operation change; `/relationships` accepts a `mission_type:` source where it refused 409 (additive, CHANGELOG line and scenario). |
| Open packs round-trip without loss of meaning | Pass by design | Independent verifier; every drop listed with a reason; the gate holds equality at the new pin. |
| No unverified dependency | Pass | None added. |
| Adversarial squad at plan point and before merge | Done / planned | Post-plan squad folded (research.md); pre-PR squad before the PR. |
| `verifyAll` on Java 25, style, docs checks | Planned | Before review and in CI. |
| BDD / specification by example | Planned | Accepted/refused fixture pairs; committed positive controls; corpus tests with pinned counts. |

## Design

### IC-01 `:missions` module and domain (FR-005, FR-006)

`service/missions`, package `ai.speckitty.charter.missions.domain` (`package-info` `@NullMarked`),
`charter.domain-conventions` + `charter.hand-written`, `api(project(":kernel"))`. `:packs` gains
`api(project(":missions"))`. Wiring: `settings.gradle.kts`; `charter-reports` `serviceModules`;
`HexagonalArchitectureTest` `SLICES`, `DOMAIN_RINGS`, a `MISSIONS_DOMAIN_IS_PURE` rule with a known-bad fixture in
`deployable` test sources.

Types ([data-model.md](data-model.md)): `MissionType` (`CharterArtifact`, kind `MISSION_TYPE`,
`relationships()` empty), `MissionTypeIdentifier`, `MissionStep`, `StepDocument`/`StepSection`, `ExpectedArtifact`
with `ArtifactClass`, `MissionReference` (kernel `ArtifactKind`, restricted to the six guidance kinds by an
invariant), `PathConventions`. No file paths in the domain. Guards through `ModularRuleset`, as `Procedure`.

### IC-02 Kernel and vocabulary (FR-004)

`ArtifactKind.MISSION_TYPE` with its facts in the enum's real shape; `NodeKind.MISSION_TYPE` artifact-backed;
`fromPlural("mission_types")` resolves both. `ArtifactKindTest`, `NodeKindTest`, `VocabularyEqualityTest` (including
the inspector's hand list of artifact schema kinds) and the pack-manifest constituent `kind` enum (widened, with an
equality assertion). Divergence entry: core refuses mission types as an artifact kind.

### IC-03 Contracts (FR-001, FR-002, FR-003)

- `mission-type.schema.json` (ADR-0044 §3), `mission-step-prompt.schema.json`, `mission-step-guidelines.schema.json`
  (frontmatter forbidden, optional title; superseded by the refined ruling Q1 of 2026-10-30: a prompt's frontmatter
  holds only its detailed `description`, see ADR-0044 §5).
- `file-kinds.yaml`, one block, added with the binding (WP03): `artifact_kinds` gains `mission_type`; `files` rules
  `**/*.mission-type.yaml`, `*/missions/*/steps/*.prompt.md`, `*/missions/*/steps/*.guidelines.md`. None matches a
  path of the old layout. The seven old exclusions stay until the re-pin.
- At the re-pin (WP06): the seven exclusions removed; `*/missions/**/README.md` excluded; a `retired` section with one
  rule per family: `**/mission_types/*.yaml`, `**/missions/*/mission.yaml`, `**/missions/*/mission-runtime.yaml`,
  `**/governance-profile.yaml`, `**/missions/*/expected-artifacts.yaml`, `**/actions/*/index.yaml`,
  `**/mission-steps/**/*` (step.yaml, prompt.md, guidelines.md) and `**/missions/*/command-templates/*`.
- `PackFileKind.parse` refuses an unknown top-level key (WP04).

### IC-04 Binding and store (FR-007)

`MissionTypeBinding`, `MissionTypeDso`, `MissionTypeYaml` in `packs/infra/storage`, appended last to `KindBindings`
and `YamlFormats` (digest order of existing kinds unchanged).

- `fileToken`/`schemaName` `mission-type`; `idIsFileStem` true; the stem check generalized in `FileStems` to a
  path check for this kind: `missions/<id>/<id>.mission-type.yaml`.
- `fileName(id)` returns that path; `FilesystemPackStore.newFile` admits a binding's nested file name below the pack,
  creating its directories, after the id grammar has refused `/` and `..`; link and traversal refusals stay.
- Companions per step, in order (`steps` then `on_demand_steps`; prompt then guidelines), `markdown` form with their
  structure schemas; `checkCompanion` refuses frontmatter (guidelines only, after the refined ruling Q1). `toEntity` refuses a `prompt_path`/`guidelines_path` that
  is not the derived one, and a `template_path` that leaves the pack or names no file. `toDso` writes the derived
  paths.
- `MissionTypeYaml` writes keys in schema order, omitting empty optional lists (S16); split per sub-item (step,
  expected artifact, reference) to keep each method small.
- The unowned-companion case is reported by the gate as red, as for toolguide guides.

### IC-05 Retired files (FR-008)

`PackFileKind.Classification` gains `Retired(match, reason)`; `PackFileSet.Listing.retired` collects them once.
`FilesystemPackStore.read` reports each as a `PackElementViolation` (`retired`, the reason); validate mode reports
each, YAML and Markdown alike, from the same listing; `Companions.claimedByRule` refuses a retired path; the gate
census buckets them as `retired:`; `PackFileSet.digested` appends them in the tail after the manifest, in path order,
so a pack without any keeps its digest. Built and tested against fixture tables (`PackFileKind(String)`) and fixture
packs; the bundled table gains its `retired` rules only at the re-pin.

### IC-06 Gates and corpus (FR-009)

- `expected-red.yaml`: only `other:missions`, narrowed at the re-pin to what stays outside the model (mission
  templates, `plan-field-declaration.yaml`, `__init__.py`), with that reason.
- `REQUIRED_KINDS` gains `mission-type`; `WholePackRoundTripTest.missionTemplatesAreNotTemplates` stays.
- `MissionTypeCorpusTest` (tag `open-packs`): per-pack counts above; no retired file in any pinned or vendored pack;
  every `template_path` resolves.
- `MarkdownCorpusTest`: glob moved to `packs/*/missions/*/steps/*.md`, count pinned.
- `GeneratedPackTree`: a mission-type shape (loaded, with companions) and a retired-file shape (refused).
- Committed positive controls through the production write path: a step title changed, two steps swapped, an
  on-demand step dropped, a prompt section edited, a reference reordered — each reported red at its path.
- A source scan over `:missions` and `:packs` main sources forbids the retired field names outside `file-kinds.yaml`.

### IC-07 Migration harness (FR-010) — throwaway

A `migration` source set in `:packs` (not on the test runtime classpath), task `:packs:packMigration
-PmigrationSource=<pinned checkout> -PmigrationTarget=<copy>`, refusing to write into the pinned checkout.

- `LegacyMissionTierReader`: every retired family of one pack as plain maps; fails on an unmapped key.
- `MissionTierMapper` (ADR-0044 §3, in precedence order): the `discovery`/`research` merge; runtime order to
  `steps`; remaining step directories to `on_demand_steps` (retrospect with no prompt); profiles to primary roles;
  directive stems to ids; step description by Q1 (runtime text, else the prompt's); inputs and outputs by Q2, templates on outputs; command templates to
  prompts; fragment `body_path` rewritten.
- Writes with `MissionTypeYaml` and the Markdown codec; deletes only retired-glob files and asserts every other file
  unchanged.
- `FieldMappingVerifier`, independent: reads the output as raw YAML, checks it against its own expectation table, every
  source key path to one target path or a listed drop (equality checked for every drop labelled a repeat), pinned
  counts, every reference id resolves in the pinned packs, and load-then-write byte-identical. A self-test plants
  mapper defects and expects it to fail.
- Output under `research/migration/`: the verifier report, per-class diffs, the patch bundle (`git format-patch`
  against `5df92a5d`); issue drafts under `research/open-packs-issues/` keyed `open-packs#TBD-<slug>`. Sent to the
  orchestrator.

### IC-08 Re-pin (FR-011), one commit

On the orchestrator's sha: `openPacksRef`; re-vendor `packs/qa` and `LOCAL_DELTA`, `PROVENANCE.md`; the `file-kinds`
change of IC-03; `expected-red` narrowed; corpus counts; the `migration` source set, task and harness deleted;
`theMigrationHarnessIsGone` extended to the build file. Then `:packs:test :packs:openPacksRoundTrip
:packs:wholePackRoundTrip :deployable:test` and `verifyAll`.

### IC-09 Docs (FR-012)

ADR-0044 (first), ADR index; divergence register (mission type as an artifact kind; retired families;
`/relationships`); contract CHANGELOG line and a `/relationships` scenario; `docs/architecture/descriptors.md`,
`descriptor-schemas.md`; `contracts/packs/README.md` schema count; glossary and `docs/context/domain.md` (mission
step, on-demand step, action, step contract, step guidelines); `epic-186-sanitized-shape-follow-ups.md` (mission
templates; step-contract keys); a changelog entry and index line.

## Sequencing

| WP | Scope | Red/green |
|---|---|---|
| WP01 | `:missions` module, domain, ArchUnit wiring (IC-01) | green |
| WP02 | Kernel kind, manifest enum, vocabulary tests, `mission-type.schema.json` (IC-02) | green |
| WP03 | Binding, DSO, YAML, companion schemas and `files` rules, nested create, placement, positive controls, `GeneratedPackTree` shape (IC-03 part, IC-04) | green: no pinned path matches the new rules |
| WP04 | `Retired` classification, store/validate/census/digest, unknown-key refusal, source scan (IC-05) on fixture tables | green: the bundled table has no `retired` rule yet |
| WP05 | Migration harness, run, verifier, bundle and issue drafts to the orchestrator (IC-07) | green: separate source set |
| WP06 | Re-pin commit (IC-03 rest, IC-06, IC-08) | green at the new pin |
| WP07 | Docs (IC-09) | docs checks green |

## Risks

- WP06 waits on the orchestrator landing the open-packs change.
- #260 lands first or second: whoever is second rebases `file-kinds.yaml`, `expected-red.yaml`, the census sample,
  `REQUIRED_KINDS` and the indexes.
- Open-packs' own checks (`tree_digest`, manifest hashes) need commits upstream; core's validate check fails on the
  retired files by the operator's ruling. The bundle says so.
- Q1 and Q2 are ruled (2026-10-30); the shape follows them.

## Complexity Tracking

None.
