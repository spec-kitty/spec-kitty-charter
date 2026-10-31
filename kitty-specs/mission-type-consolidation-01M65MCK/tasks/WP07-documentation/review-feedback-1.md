# WP07 review 1: Documentation

**Verdict: approve.** No high or medium issue. Three low issues and two nits are below; each is a
one-line fix, and folding them before the pull request opens is recommended but not required.

Reviewed at `50fd379` on `issue-194-mission-type-consolidation`: the WP07 commit `75d7e30`, the
issue-naming commit `4d23cb3` and the docs part of the re-pin commit `396bb2a`. Source of truth:
ADR-0044, `service/missions`, `MissionTypeBinding`, `mission-type.schema.json`, the two step
structure schemas, `contracts/packs/file-kinds.yaml`, `relationships.feature`, and the open-packs
checkout at `86d9284` (read only).

## Gates

| Check | Result |
|---|---|
| `python3 scripts/docs/check_docs.py all` | green (frontmatter, related, freshness, adr, changelog, terms, orphans, links) |
| `./gradlew -PopenPacksDir=… :packs:test --tests '*DivergenceRegister*' :deployable:test --tests '*CharterContractSuite*' :contracts:checkContractCompatibility` | BUILD SUCCESSFUL; `DivergenceRegisterTest` 3/3; `relationships.feature` 2/2 scenarios pass |
| markdownlint-cli2 0.18.1 (repo config) | 0 errors, 172 files |
| cspell 10 (repo config, `docs/**/*.md`) | 0 issues, 172 files. The changed non-Markdown files report only words on pre-existing lines (`AFMA`, `unioned`, `antipattern` and others), none on a WP07 line |

## Verified good

- **Counts against the pinned data (`86d9284`).** 6 mission types (built-in 4, cave-kitty 1, qa 1);
  40 steps plus 6 on demand (software-dev `charter`, `tasks-finalize`, `tasks-outline`,
  `tasks-packages`; documentation and research `retrospect`), so the changelog's "46 steps" is right;
  43 prompts, 17 guidelines; 153 files deleted between `5df92a5d` and `86d9284` (built-in 121,
  cave-kitty 13, qa 19), matching DIV-502 on both forms and the changelog. 22 schemas in
  `contracts/packs/schemas/` (12 artifact kinds with a schema, 7 pack-level and project schemas, 3
  structure schemas), matching `contracts/packs/README.md` and `descriptor-schemas.md`; "twelve
  artifact kinds that have a schema" is right (template and anti-pattern have none).
- **Field names and bounds.** `descriptors.md` and `descriptor-schemas.md` match the schema at every
  level: required `schema_version`, `id`, `title`, `description`, `steps`; the step, input, output and
  reference shapes; `requires_substantive_spec` and `blocking` const `true`; `path_conventions` keys;
  the guidance-kind enum; `artifact_class` values; `template_path` under `templates/`. The 160-character
  bound matches `maxLength: 160` and `MissionStep.DESCRIPTION_MAX_LENGTH` (code points). The ADR's
  44 step descriptions and 28 prompt frontmatters check out against the data.
- **Prompt frontmatter (refined ruling Q1).** Every changed doc says a prompt's frontmatter holds only
  `description`, the detailed description, and is kept; guidelines hold none. This matches
  `mission-step-prompt.schema.json`, `mission-step-guidelines.schema.json`,
  `MissionTypeBinding.detailedDescription` and the data (the only frontmatter key in the 28 prompts is
  `description`; no guidelines file has frontmatter). No doc says the frontmatter is dropped.
- **`retired`.** The eight families, "refused by name in the load and validate mode, never a companion,
  bucketed `retired:`, digested in the fingerprint tail" match `file-kinds.yaml`'s `retired` section and
  header, `RetiredFilesTest` and `PackFileSet` (retired files after the manifest in the digest).
- **Template id `<type>/<path>`.** Matches `PackFileSet.templateId` and `TemplateBinding` and the
  `*/missions/*/templates/**/*` payload rule; the README and hidden-file exclusions are described right.
- **`/relationships`.** The contract CHANGELOG, DIV-503 and the changelog entry name the operation as
  `PUT /packs/{packId}/relationships` (`putPackRelationship`), as the path file does; the `NodeKind`
  description and enum are indeed unchanged; the scenario pins 201 for a held mission type and 409
  `registration_conflict` for one the pack does not hold, and both pass.
- **Divergence register.** DIV-501..503 are on the page and in `divergences.yaml` with the same ids,
  surfaces, statuses, rulings and `since`; the block lists in both headers name the new block. The cited
  tests and classes (`ArtifactKindTest`, `NodeKindTest`, `VocabularyEqualityTest`, `MissionTypeSchemaTest`,
  `MissionTypeBindingTest`, `RetiredFilesTest`, `RelationshipRegistrationService`) exist. DIV-501's kind
  facts match `ArtifactKind.MISSION_TYPE` (activatable, not org-requirable, not selection-overlayable,
  `ALL`), as does the new row of the logical model's kind table.
- **Glossary and domain.** The `definitions.yaml` entries (Mission step, Mission type, On-demand step,
  Recipe ingredients, Step contract, Step guidelines, Step template, Template (pack)) agree with the
  ADR. The domain module list in `logical-model.md` matches `service/missions/.../domain`.
- **Re-pin state.** Nothing describes the re-pin as pending: the changelog names `86d92846`, which is
  `openPacksRef` in `gradle.properties`; follow-up 13 is done; the residuals row for mission templates
  was updated by the re-pin commit. No doc still says 18 schemas, "no rule declares markdown", or that
  mission templates are unmodelled content.
- **Style.** Plain and concrete, in the voice of the neighbouring pages; no marketing tone.

## Issues

### L1 (low): `domain.md` defines a mission step as a sub-item of `steps` only

`docs/context/domain.md:120`: "A sub-item of its mission type's `steps`, in run order". An on-demand
step is a mission step too (ADR-0044 decision 2; the next row of the same table; the glossary's
"Mission step", which says "in `steps` (the run order) or `on_demand_steps`").

**Fix:** "A sub-item of its mission type's `steps` (in run order) or `on_demand_steps`: a role, …".

### L2 (low): the YAML form of DIV-501 and DIV-502 lost the open-packs issue, and neither form names the pin

`docs/conformance/divergences.yaml:697` and `:717`: `4d23cb3` added
`(spec-kitty/spec-kitty-open-packs#95)` to the corpus cases on the page
(`divergence-register.md:526`, `:538`) but not to the YAML, so the two forms of one register differ.
The earlier blocks name the pin of their corpus case (`openPacksRef 5df92a5d`); these do not.

**Fix:** on both forms, end the two corpus cases with "(spec-kitty/spec-kitty-open-packs#95), at
`openPacksRef` `86d92846`".

### L3 (low): ADR-0044 names the relationships operation with the wrong method

`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md:336`: "`POST
/packs/{id}/relationships`". The operation is `PUT /packs/{packId}/relationships`
(`contracts/charter/paths/packs_packId_relationships.yaml`), as every WP07 doc and the scenario say.
The ADR is the source of truth the docs cite, so it should agree.

**Fix:** "`PUT /packs/{packId}/relationships` with a `mission_type:` source …".

### N1 (nit): the pack schema README still says three classification steps

`contracts/packs/README.md:26-33`: "classifies a pack file in three steps … `payloads` rules, consulted
only when neither matches". With `retired`, the order is `files`, `exclusions`, `retired`, then
`payloads` (`file-kinds.yaml:22`), and a payload rule is consulted only when none of the three matches.

**Fix:** "in four steps", and say the `retired` rules are consulted after `exclusions` and before
`payloads`.

### N2 (nit): the logical model speaks of the deleted migration in the present, and lists five of eight retired families

`docs/architecture/logical-model.md:467-468`: "the migration maps each profile to its primary role".
The harness was deleted by `396bb2a`. `:481-482` lists `mission_types/`, `mission.yaml`,
`mission-runtime.yaml`, `mission-steps/` and action indexes as refused, leaving out
`governance-profile.yaml`, `expected-artifacts.yaml` and `command-templates/`.

**Fix:** "the migration mapped each profile to its primary role"; and either list all eight families or
write "The older layout's files are refused by name (DIV-502)".

## Observations (no action in this WP)

- `docs/glossary/charter/README.md` is generator-owned and was last written on 2026-10-27, so it still
  shows the old Mission step, Recipe ingredients, Step contract and Step template definitions and has no
  On-demand step or Step guidelines entry. The docsite build rewrites it from `definitions.yaml`
  (`docs/guides/docsite.md`), and earlier missions left it the same way. For the same reason
  `domain.md`'s On-demand step and Step guidelines rows cannot link a glossary anchor yet.
- The core paths and line numbers of DIV-501..503 (spec-kitty `c678037c`) could not be checked: there is
  no core checkout in this environment.
- `docs/development/epic-186-sanitized-shape-follow-ups.md:4`: the description names ADR-0042 and
  ADR-0043 only, while the page now also holds the ADR-0044 residuals. Optional.
