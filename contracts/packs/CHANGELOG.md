# Changelog

All notable changes to the packs contract are recorded here, newest first. The packs contract is
everything under `contracts/packs/` that a reader of packs depends on: `file-kinds.yaml`, the JSON
Schemas under `schemas/` (the pack-document structure schema among them) and the classifier
conformance cases under `conformance/`. It is versioned on its own changes, apart from the charter
contract (`../charter/CHANGELOG.md`), and the top heading below is its version: there is no other
version carrier. A change to any of those files adds a line to the top section in the same pull
request.

The rules are those of the charter contract (ADR-0022): an unreleased version carries `-SNAPSHOT`;
while the major is 0 a minor may break and a patch may not, and a minor bump over a release holds a
`### Breaking` subsection that lists each break. A release is tagged `contract-packs-v<version>`;
`contracts/packs/README.md` ("Releasing the packs contract") has the procedure, and
`docs/guides/pin-the-packs-contract.md` how a consumer pins and checks a release.

## 0.1.0-SNAPSHOT

The work toward the next packs release, after the release candidate `0.1.0-rc.1`. That release is
`0.1.0-rc.2` or the final `0.1.0`. The changes below break `0.1.0-rc.1`: a consumer pinned to
`contract-packs-v0.1.0-rc.1` breaks when it moves its pin to that release, and must apply the
`### Breaking` entries below in the same change.

### Breaking

- The file kinds say what the service holds (#554, open case 6): the `graph-shard` rule narrows from
  `**/*.graph.yaml` to `*/*.graph.yaml`, a shard at the pack root, and the `**/charter.yaml` rule is removed, so no
  pack file kind maps a `charter.yaml`. A shard below the root (`drg/<kind>.graph.yaml`, `graph/a.graph.yaml`) and a
  `charter.yaml` anywhere in a pack are `unmodelled`: warned as `file_not_held`, not held and not judged, where they
  were validated against `graph-shard` and `charter`. A `*.graph.yaml` under `assets/` or `templates/` is now that
  place's payload. The classifier cases `core/graph/a.graph.yaml` and `project/charter.yaml` move from `mapped` to
  `unmodelled`, and `core/a.graph.yaml` is the `graph-shard` case. `schemas/charter.schema.json` stays the schema of a
  project's `charter.yaml`, outside any pack.

### Changed

- A file no rule takes (#554, operator ruling Q1): the "Matching" header of `file-kinds.yaml`, `README.md` and the
  classifier cases' `unmodelled` outcome say it is warned as `file_not_held` and not held, as an excluded file is; they
  said it is refused as `unmodelled`. The outcome stays `unmodelled`, a classification and not a verdict, and no case
  changes.

## 0.1.0-rc.1

The first packs contract version, a release candidate (operator ruling R1, 2026-11-16; #559).

### Added

- Role pins (slice G, #42; ADR-0030, ADR-0078 decision 3): `policy.schema.json` gains `role_pins`, a map from a
  role token (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, never `generic`, at most 64 characters) to a profile id
  (`^[a-z0-9][a-z0-9_-]*$`, 1 to 64 characters), in authored order. A pin is tighten-only along the closure; a roles
  key (role locks) stays reserved and is refused as undeclared.
- The packs contract as a versioned line of its own, published as a release
  (`contract-packs-v<version>`) with a checksum for every file.
- Classifier conformance cases (`conformance/classifier-cases.yaml`): a pack file path and the
  outcome an implementation of `file-kinds.yaml` must give it (`mapped` with its kind, schema and
  content form, `excluded`, `retired` or `unmodelled`), one case for every rule at least. The
  charter service runs them against its own classifier in CI.
- `packs-contract/RELEASE.json` in the release archive: the version, the tag and the charter commit.

### Changed

- Identifiers on the shared grammar (ADR-0075 decision 2, #552): a role (`agent-profile.schema.json` `role` and
  `alt-roles`, `mission-type.schema.json` step `role`, `procedure.schema.json` step `role`, now also at most 64
  characters, and the `role_pins` keys of `policy.schema.json`), a paradigm `id`, a styleguide `id`, a tactic `id` and each part of a
  step enhancement's `slot` (now `maxLength: 129`; the 64-character cap of each part is the service's) are letter-first kebab slugs, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, so a doubled, leading or
  trailing hyphen is invalid where `^[a-z][a-z0-9-]*$` accepted it. No spec-kitty-open-packs id breaks (138 tactic ids among them) (census at
  `openPacksRef` and at open-packs `main`).
- The agent profile's performer (slice G, #512; ADR-0078 decision 4): `agent-profile.schema.json` replaces
  `sentinel` (boolean) with `performer` (`agent` or `human`, optional, no default). The role `operator` is reserved for
  a human-performed profile: a profile whose `role` or `alt-roles` take it and that is not `performer: human` is
  invalid, reported at `/role` or `/alt-roles/<n>`. There is no reader of `sentinel`: a profile that carries it is
  refused as any undeclared key is.
- A mission step's role (slice G, #446; ADR-0068 decision 4, ADR-0078): in `mission-type.schema.json` a step's
  `role` is at most 64 characters and never the reserved `generic`, reported at `/steps/<n>/role`.
- The artifact kind `glossary_pack` is replaced by `glossary` (#106; ADR-0049, ADR-0053): `glossary.schema.json`
  replaces `glossary-pack.schema.json`, and a glossary context lives at `glossaries/<id>.glossary.yaml`. A
  `*.glossary-pack.yaml` file is refused by name as retired. `file-kinds.yaml` gains the node kinds
  `glossary_context` and `term`, the artifact kind `glossary` and the fragment plural `glossaries`; the graph-shard,
  org-fragment, preset (`activated_glossaries`), charter (`selected_glossaries`, `activated_glossaries`),
  pack-manifest, policy and mission-step-contract schemas follow. A term is an edge endpoint only as the target of a
  `vocabulary` edge: the graph-shard and org-fragment edge schemas refuse it as a source and as the target of any
  other relation. The classifier cases map
  `core/glossaries/a.glossary.yaml` and refuse `core/glossary/a.glossary-pack.yaml` as retired. There is no reader
  of the old shape.
- The "Matching" header of `file-kinds.yaml` states the order the sections are consulted in, and
  that a file no rule takes is refused as `unmodelled`; it said such a file was left unclassified.
- Precedence (slice F, #537; ADR-0077): a `retired` rule beats a `files` rule that also matches, unless an
  `exclusions` rule matches too. Step 1 of the classifier cases says so, and the case
  `core/mission_step_contract.graph.yaml` is `retired`, not a graph shard.
- The schemas slice F changes (#537; ADR-0064 decision 13, ADR-0077): `mission-type.schema.json` carries each step's
  `substeps` (`id`, `description`, optional `command`, `inputs`, `delegates_to` and `guidance`), the structure the
  step contract held; `graph-shard.schema.json` and `org-fragment.schema.json` drop the node kind
  `mission_step_contract`, the fragment plural `mission_step_contracts`, the edge field `impacts` and the relations
  `impacts`, `replaces` and `delegates_to`; `charter.schema.json` drops `selected_mission_step_contracts`,
  `activated_mission_step_contracts` and `template_set`; `preset.schema.json` drops
  `activated_mission_step_contracts`; `pack-manifest.schema.json` and `policy.schema.json` drop the kind
  `mission_step_contract`. `file-kinds.yaml` drops the node and artifact kind `mission_step_contract` and its
  `files` rule. There is no reader of the old shapes.

### Removed

- `mission-step-contract.schema.json` (slice F, #514, #537; ADR-0077): a step's substeps are authored in the mission
  type. A `*.step-contract.yaml` file and a `mission_step_contract.graph.yaml` fragment are refused by name as
  retired, wherever they stand; the classifier case `core/step-contracts/a.step-contract.yaml` is `retired`.
