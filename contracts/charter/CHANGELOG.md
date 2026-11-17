# Changelog

All notable changes to the `charter` contract are recorded here, newest first. The module
version is the `info.version` of `openapi.yaml`; the canonical JSON Schemas of `../offering/schemas/`
(`ActivationConfig.schema.json`) are versioned with it. The contract scenarios under `scenarios/` are
half of the contract, and an OpenAPI diff cannot see them, so every scenario change gets a line
here too.

A contract change does not edit this file: it adds a fragment under `changes/`, which the release
pull request assembles into the top version section (`changes/README.md`, ADR-0047).

## 0.2.0-SNAPSHOT

The work toward the next candidate (`0.2.0-rc.3`) or the final `0.2.0`, after the release candidate
`0.2.0-rc.2`. What changes is recorded in the fragments under `changes/`, assembled here at
release; the `### Breaking` of `0.2.0-rc.1` already lists what the 0.2 line breaks.

## 0.2.0-rc.2

The second release candidate, cut together with the first service release candidate, `0.1.0-rc.1`, so
that the service bundles a published contract (operator ruling, 2026-11-17). The contract is
unchanged since `0.2.0-rc.1`: no path, schema or scenario differs, only `info.version`.

## 0.2.0-rc.1

The first published contract, a release candidate (operator ruling R1, 2026-11-16; ADR-0022). It
opens the 0.2 line: while the major is 0, a minor version may break. No release was cut under
`0.1.0-SNAPSHOT`, so nothing released breaks incompatibly; `Breaking` lists what changed for core
against the unreleased draft it has seen. The never-released `0.1.0-SNAPSHOT` is folded in at the
end of this section.

### Breaking

- A mission step's `role` is never the reserved `generic` and is at most 64 characters (ADR-0068 decision 4, #446),
  in the pack schema `mission-type.schema.json` (`not: {const: generic}`, `maxLength: 64`, reported at
  `/steps/<n>/role`), in the step's domain rules, and in `MissionStep.role` of the contract. A step never asks for the
  role that marks a fallback profile, so the step-keyed selection never reads it.

- `AgentProfile.sentinel` (boolean) is replaced by `performer: agent | human` (ruling D4, #512), on the wire and in the
  pack file (`agent-profile.schema.json`), with no reader of the old key: a profile file that carries `sentinel` is now
  refused as invalid content, as any undeclared key of the closed schema is. `performer` is omitted when the file
  declares none, and selection reads an absent performer as `agent`. The role `operator` is reserved for a
  human-performed profile. Core must regenerate its `AgentProfile` DTO and write `performer` in place of `sentinel`
  in the profiles it holds.
- `getEffectiveAgentProfile` now answers the effective profile (R8, #102, #380; rulings D5, D6 and D7 of ADR-0078),
  not the stored winner: `agentProfile` is the winner merged with its in-force enhancers and folded root to leaf under
  its in-force `specializes_from` ancestors, each merged with its own enhancers first. An enhancer's identity fields
  (`profileId`, `name`, `purpose`, `role`, `altRoles`, `performer`, `routingPriority`) are never merged, and identity
  fields are never inherited; every other top-level field a descendant or an enhancer declares replaces the value so
  far, and a field a descendant leaves absent is inherited from its nearest ancestor that declares it. The answer
  gains the required `lineage`, an array of `LineageLink` (`id`, `pack`, `depth`), the in-force ancestors nearest
  first, empty for a root. A profile that enhances another agent profile answers its own stored body and links the
  read of what it enhances as `_links.enhances` (a new relation). `_links.artifact` still reads the stored profile in
  its pack (`getPackAgentProfile`), which is no longer the same body when an enhancer or an ancestor contributes. An
  in-force enhancement stored before the rule that would drop part of a profile of the chain is now refused with
  `500 profile_enhancement_drops_field`, its `packs` naming the enhancing pack first. The `ETag` keeps its inputs,
  which already cover the packs of every enhancer and ancestor of the closure; the tag version stays `v3`, since no
  release served the earlier body under it. Core's seam: core may read a profile's effective form and its lineage
  from this read instead of walking `specializes_from` and `enhances` itself, and must regenerate its
  `EffectiveAgentProfileRead` DTO for `lineage`.

- Every identifier is on the shared kernel grammar (ADR-0075 decision 2, operator ruling "everything strict", #552):
  a doubled hyphen (`a--b`), a trailing hyphen (`a-`) and a leading hyphen are refused where they were accepted.
  - **The pack id** is a kebab slug of at most 63 characters, `^[a-z0-9]+(-[a-z0-9]+)*$` with `maxLength: 63`, a
    digit first still allowed: the parameter `PackId`, the `packId` path parameters of `packs_packId` and
    `packs_packId_doc`, and `PackSource.packId`. A registration under such an id answers `400` without a code before
    the body is read (`whole-pack-registration.feature` gains the `acme--x` and `acme-` rows); a
    `registerPackFromSource` body that names one breaks the schema of `PackSource.packId`.
  - **A role** is a letter-first kebab slug of at most 64 characters, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`:
    `AgentProfile.role` and `altRoles`, `ProcedureStep.role` (now also `maxLength: 64`), and `MissionStep.role`,
    `ProfileSelectionRead.role` and the `role` path parameter of `selectAgentProfile`, whose patterns still refuse
    the reserved `generic` without a lookahead.
  - **A slot id** is two letter-first kebab slugs joined by a colon, each at most 64 characters: `SlotId` (now with
    `maxLength: 129`). The pattern cannot cap each part without a lookahead, so the 64-character cap of a part is
    the service's: a step enhancement whose slot part is longer passes the pack schema and is refused by the step
    enhancement's rule.
    `prompt-fragment-refusals.feature` gains a slot part with a doubled hyphen, refused as a `schema` violation.
  - **A paradigm, styleguide or tactic id** is a letter-first kebab slug of at most 64 characters, in the domain and in
    the pack schemas.
  - The pack schemas follow (packs contract `0.1.0-SNAPSHOT`, `### Changed`).
  - Core's seam: regenerate the DTOs; a client that sent or stored an id, a role or a slot with a doubled, leading or
    trailing hyphen must rename it. No refusal code is added.

- A mission type's step authors `substeps`: a flat, ordered list, one level deep, of `{id, description, command,
  inputs, delegates_to {kind, candidates}, guidance}`, the content a step contract's step held (operator ruling
  DM-01M7B1YH1GF15RD0G4ZPSA8KW9, FR-004). A substep id is a letter-first kebab slug, unique within its step; an input's
  `optional` is written only as `true`; `delegates_to.kind` is a guidance kind, a `scope` target of an action,
  never `agent_profile` (a mission type names roles, not profiles: ADR-0032 decision 1, operator ruling
  DM-01M7DTN8JWR4G1P35NXGCMS65M), and each candidate is written `[<pack>::]<id>`, each once. `getPackMissionStep` and
  `getEffectiveMissionStep` answer `substeps` in authored order (`MissionSubstep`), each delegation candidate linked to
  its read when the scope holds it: `artifact` in a pack for a candidate written bare or with the pack's own name,
  `effective-artifact` under an activation (examples `MissionStep.specify`, `MissionStep.activation-implement`).
  `MissionStepDelegation` and `MissionStepDelegationCandidate` are renamed `MissionSubstepDelegation` and
  `MissionSubstepDelegationCandidate`.
- Every candidate resolves to an artifact of its kind and the step's action has a `scope` edge to it (FR-005). A pack
  that breaks either is refused: a registration answers `422 validation_failed` with one violation per candidate at
  the mission type file, code `substep_delegation_unresolved` or `substep_delegation_unscoped` (examples
  `PackRegistrationStatus.substep-delegation-unresolved` and `.substep-delegation-unscoped`), and a composing read
  answers `500 pack_unreadable`. A candidate written with another pack's name is resolved when the packs compose; one
  the named pack of the closure does not hold refuses with `substep_delegation_unresolved` before its scope edge's
  reference is judged. `deletePackRelationship` of the last scope edge a candidate needs answers `422
  validation_failed` with `substep_delegation_unscoped` at `/to`. Validate mode reports `duplicate_step_id` for a
  substep id repeated within a step. Core's seam: a client that reads the violations of `validation_failed` meets two
  more codes; no `CharterRefusalCode` is added.

- Every step and on-demand step of a mission type is linked by an authored `mission_type:<type> requires
  action:<type>/<step>` edge, and no such edge names an action that is not one of the type's steps (operator ruling
  DM-01M7B1YQR5ZKQPDW6DX64KPNRN). A registration answers `422 validation_failed` with one violation per unlinked
  step at the mission type file and one per stray edge at its graph document, code `mission_step_unlinked` (example
  `PackRegistrationStatus.mission-step-unlinked`). A composing read answers `500 pack_unreadable`, also when another
  pack writes a `requires` edge at the mission type in force that names no step of the type. `putPackRelationship` of
  a stray edge, and `deletePackRelationship` of the last edge that links a step, answer `422 validation_failed` with
  the violation at `/to`. A mission type that authors `action_sequence`
  or `template_set` is refused as an unknown field by name, and the project charter schema no longer has
  `template_set` (#520): a `charter.yaml` whose catalog carries it is refused. Its activation set is proven through
  the activation document it registers: a `createActivation` or `replaceActivation` body that carries `template_set`,
  or selects the retired step-contract kind, answers `422 activation_invalid` with one `unknown_member` at the key's
  pointer (`activation-retired-keys.feature`). `deletePackArtifact` of a mission type
  drops the type's own step links, and the `scope` edges from its own step actions (`action:<type>/<step>`, bare or
  written with the pack's own name), from the author-written graph documents that hold them (operator rulings
  DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ and DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6), the one case where a removal rewrites such a
  document, and the same edges from `registered.graph.yaml` (operator ruling DM-01M7F1ARDGCHPSB62QNY0EZW7Y); so a type whose substeps delegate is removed, and any other edge that names it or its actions still
  answers `409`. Core's seam: a client that reads the
  violations of `validation_failed` meets one more code; no `CharterRefusalCode` is added.

- `mission_step_contract` is retired outright (ADR-0077 decision 3, operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9;
  #514, #533). A step's substeps are authored in its mission type (see the substeps entry of this version), and the
  `review` gate is the guard `spec-kitty-pre-review` on the work-package lifecycle's `in_progress -> for_review`
  transition (ADR-0054 decision 12). This retires what earlier entries of this version added for the kind. Gone from
  the contract: the operation `getEffectiveMissionStepContract` and its path
  `/activations/{activationId}/artifacts/mission_step_contract/{contractId}`, with its switch-gate key; the parameter
  `StepContractId`; the response `ActivationMissionStepContractNotFound`; the example
  `Artifact.activation-mission-step-contract`; the schemas `MissionStepContract`, `MissionStepContractGate`,
  `MissionStepContractInput` and `MissionStepContractStep`; the value `mission_step_contract` of `ArtifactKindToken`,
  `ActivatableKind`, `CascadeKind` and `NodeKind`; the refusal code `mission_step_contract_not_found`; the members
  `Artifact.missionStepContract`, `SelectedArtifacts.mission_step_contract`,
  `DeactivatedArtifacts.mission_step_contract` and `Preset.activatedMissionStepContracts`; and a step's
  `step-contract` link, with the `step` link relation a step contract's links carried (`contracts/lint/ruleset.yaml`
  and the hypermedia relation table). The offering schema `ActivationConfig.schema.json` drops the kind from
  `selected` and `deactivated`. Every retired route answers as an unknown kind: `404 kind_not_found` on the item and
  `/references` routes in both trees, and a `400` Problem without a code on `?kind=mission_step_contract`
  (`removed-routes.feature`). Core's seam: regenerate the DTOs, drop the step-contract read, the kind token and the
  refusal code, and read a step's `substeps` from `getPackMissionStep` and `getEffectiveMissionStep`.
- The pack schemas change with the kind (ADR-0077): `mission-step-contract.schema.json` is removed, and the kind
  leaves `graph-shard`, `org-fragment` (the fragment plural `mission_step_contracts`), `pack-manifest`, `policy`,
  `preset` (`activated_mission_step_contracts`) and `charter` (`selected_mission_step_contracts`). A
  `*.step-contract.yaml` file or a `mission_step_contract.graph.yaml` fragment is refused by name as a retired file
  kind (`file-kinds.yaml`, `retired:`): `422 validation_failed` at registration and a blocking finding in validate
  mode. A consumer of `contracts/packs` (#559) meets the same break; spec-kitty-open-packs folds its 28 step
  contracts into their steps.
- The relations `impacts`, `replaces` and `delegates_to` are retired (ADR-0077 decision 6, operator ruling
  DM-01M7B1YXJHQCBSYJ145NJC77V5). They leave `Relation` (its `x-source` is now `RelationType`) and the relation enum
  of `graph-shard.schema.json` and `org-fragment.schema.json`, and an edge's `impacts` strength leaves both schemas,
  whose edge is closed. A graph document whose edge uses one is refused as an unknown relation, and an edge that
  writes `impacts` as an unknown member, as any unknown relation or member is (`drg_schema_error`), so a pack that
  registered with one before is refused now. `putPackRelationship` with one as its `type`, or with any type that
  names no relation, answers `422 validation_failed` with one violation `relationship_type_unknown` at `/type`
  (example `CharterRefusal.relationship-type-unknown`). `replaces` was
  sometimes written to mean `overrides`: rewrite such an edge as `overrides`. Core's seam: map no relation to the
  three, drop the edge strength, and read one more violation code of `validation_failed`.
- The other refusals of this change are entries of their own in this version: the endpoint table
  (`relationship_endpoint_not_allowed`, and the same-kind rule of `overrides`), the step links and `template_set`
  (`mission_step_unlinked`), and the substeps (`substep_delegation_unresolved`, `substep_delegation_unscoped`, and the
  renamed `MissionSubstepDelegation` and `MissionSubstepDelegationCandidate`).

- The kind `glossary_pack` is replaced outright by `glossary`, in the shape of ADR-0049 (decisions 1 to 6, amended by
  ADR-0053 decisions 11 to 14; #106). There is no alias, no reader of the old shape and no upgrade (the charter's model
  is canonical). Allowed on the 0.x line (ADR-0022).
  - Renamed members and values: `ActivatableKind` and `CascadeKind` replace `glossary_pack` with `glossary`;
    `SelectedArtifacts` and `DeactivatedArtifacts` replace the member `glossary_pack` with `glossary` (glossary context
    ids), and `ActivationConfig.schema.json` and its corpus (`valid/06`, `valid/07`) follow; `Preset.activatedGlossaryPacks`
    becomes `Preset.activatedGlossaries`. `schemaVersion` stays `2`.
  - `NodeKind` replaces `glossary_pack` with two URN kinds: `glossary_context`, the URN token of a context
    (`glossary_context:<id>`), which differs from the kind token `glossary` on purpose (ADR-0053 decision 13), and
    `term` (`term:<context-id>/<term-id>`), an edge endpoint only as the target of a `vocabulary` edge, never a declared node
    (operator ruling 2026-11-14): a graph shard, an org fragment or a registered relationship that names a term as a
    source, or as the target of any other relation, is refused. The `vocabulary` relation targets both.
  - The packs contract: `glossary.schema.json` replaces `glossary-pack.schema.json`, closed at every level
    (`schema_version`, `id`, `name`, `description`, `tags?`, `applies_to?`, `terms`; a term is `id`, `name`,
    `definition`, `examples?`, `aliases?`, `avoid?`, `status`, `see_also?`). Context and term ids are letter-first
    kebab slugs of at most 64 characters; `status` is `draft`, `active` or `deprecated`; a `see_also` entry is a term reference, bare
    (`term:<context-id>/<term-id>`, a term of the same pack) or pack-qualified
    (`<pack>::term:<context-id>/<term-id>`, a term another pack holds), judged by the one reference rule: a bare entry
    that only another pack holds is `pack_reference_unqualified`, and a qualified one the named pack does not hold is
    `pack_reference_artifact_missing` (operator ruling 2026-11-14).
    `confidence`, `introduced_in_mission` and `provenance` are gone, `surface` is renamed `name`, and
    `synonyms_to_avoid` and `banned_synonyms` fold into `avoid`. The file family is `glossaries/<id>.glossary.yaml` (fragment plural
    `glossaries`), the id is the file stem, and a context elsewhere is refused by placement. `file-kinds.yaml`, the
    graph shard and org fragment URN alternations, and the `preset` (`activated_glossaries`), `charter`
    (`selected_glossaries`, `activated_glossaries`), `pack-manifest`, `policy` (`kind_defaults.glossary`; an
    `artifacts` key is `glossary_context:<id>`) and `mission-step-contract` schemas follow.
  - A write (`createActivation`, `replaceActivation`) that sends `selected.glossary_pack` or
    `deactivated.glossary_pack` is `422 activation_invalid` with an `unknown_member` violation at its pointer, as for
    any undeclared member.
  - A stored activation that holds `selected.glossary_pack` or `deactivated.glossary_pack` is refused as an invalid
    stored document, with no upgrade (ruling `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT`).
  - A pack file in the old shape (`*.glossary-pack.yaml`) is refused by name as a retired file, in the load, in
    validate mode and at registration, with the reason that the shape is replaced by `glossaries/<id>.glossary.yaml`.
    A preset that names `activated_glossary_packs` makes its pack unreadable.
  - A generated-client caller regenerates: `ActivatableKind.GLOSSARY_PACK` becomes `GLOSSARY`, the
    `glossaryPack` members of `SelectedArtifacts` and `DeactivatedArtifacts` become `glossary`,
    `Preset.getActivatedGlossaryPacks()` becomes `getActivatedGlossaries()`, and `NodeKind` gains `GLOSSARY_CONTEXT`
    and `TERM` in place of `GLOSSARY_PACK`. A client that selects glossary packs selects glossary contexts by id under
    `selected.glossary`.
  - Pack content follows in `spec-kitty-open-packs`, which migrates its glossaries to contexts with the minted term
    ids. Until then a pack in the old shape is refused.

- `glossary` becomes a served kind (#106; ADR-0022). `ArtifactKindToken`, the `?kind=` filter, lists `glossary` after
  `skill` and before `agent_profile`, and the `400` detail of an unknown `?kind=` names it in that order. `Artifact`
  gains the member `glossary` (`GlossaryContext`, with `GlossaryContextTerm` and `GlossaryAppliesTo`) in its `oneOf`,
  which the literal context read answers. Core must regenerate its DTOs (`ArtifactKindToken`, `Artifact` and the new
  glossary schemas) and handle the kind token and the member before it relies on an exhaustive switch over either.

- An edge whose relation does not allow its source and target kinds is refused (ADR-0064 decision 13, #515, #350).
  The endpoint table names, per relation, the kinds each side may hold: a procedure no longer requires a step's
  action, and an `overrides` or `enhances` edge joins two artifacts of one kind. A registration answers
  `422 validation_failed` with one violation per such edge, code `relationship_endpoint_not_allowed`, at its
  pack-relative graph document (example `PackRegistrationStatus.relationship-endpoint-not-allowed`).
  `putPackRelationship` answers `422 validation_failed` with the violation at `/type` and writes nothing (example
  `CharterRefusal.relationship-endpoint-not-allowed`). A composing read of a closure that holds such a pack answers
  `500 pack_unreadable`, naming the graph document and then the code: `the file procedure.graph.yaml of main cannot
  be read: relationship_endpoint_not_allowed: a requires edge may not connect procedure to action`. An `overrides` edge from a kind that is not activatable keeps
  `overrides_source_not_activatable`, and an `enhances` or `overrides` edge from a step enhancement the pack holds
  keeps `prompt_fragment_invalid`. Core's seam: a client that reads the violations of `validation_failed` meets one
  more code; no `CharterRefusalCode` is added.

- Two compose checks judge every step enhancement, a `step_enhancement` whose one `enhances` edge names an
  `action:<type>/<step>` (ADR-0066 decision 8, phase 2; FR-002; #438). A compose, a registration or a write that
  answered before is now refused (`step-enhancement-compose.feature`):
  - **The target held (C37).** The step must be held (it defines the mission type and declares the step, OD-9) by the
    pack the edge names: the enhancing pack for a bare target, or the pack of the qualifier's name, which must be the
    enhancing pack or one of its `depends_on` closure. A qualifier naming a pack outside the closure is
    `pack_reference_pack_missing`, and a named pack that does not hold the step is `pack_reference_artifact_missing`,
    where an `action:` reference was never found missing before. The reads (`getOffering` and every composing read)
    and the activation writes answer `500`; a registration (`getPackRegistration`) and the impact-checked writes
    (`putPackRelationship` and the per-artifact registrations) answer `422`, as for every composition code.
  - **The inherited lock (OD-2 (A)).** A step takes its mission type's mutability level, judged along the closure
    without the enhancing pack's own policy. An enhancement of a step whose mission type is `locked` is refused `422
    override_not_permitted` at registration and on the impact-checked writes, naming the enhancing pack and then the
    pack that locks the mission type, and `500 pack_unreadable` on the activation reads and writes whatever the
    activation selects, as a forbidden layer override is (C21). An `extendable` mission type takes the enhancement.

  No enum value is added. Core's seam must expect these refusals where the same request succeeded; a pack that
  enhances an unheld step, or a step of a mission type a pack of its closure locks, must be fixed before it composes.

- `prompt_slot_missing` is raised (`CharterRefusalCode`, `raised: true`; ADR-0066 decision 8, phase 2; FR-001, FR-006;
  #438). The compose check C38 refuses a step enhancement in force that fills a slot the prompt in force of its step
  does not declare: the prompt at the step's place, the one `getEffectiveStepPrompt` serves (the mission type's version
  in force when it declares the step, else the merged winner of `action:<type>/<step>` whose version does; operator
  ruling Q3), so a layer override of the mission type whose prompt drops the slot is caught, and only the winning copy
  of the enhancement is judged. An
  enhancement that is not in force never refuses (OD-1 (B)). Requests that answered before are now refused
  (`step-enhancement-slots.feature`):
  - **Composing reads, `500`**: every read of an activation that puts such an enhancement in force
    (`listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext` and the
    other effective reads), after the composition codes and the merge's `pack_unreadable` (F6).
  - **Registration and the impact-checked pack writes, `422`**: `getPackRegistration` (a refused registration),
    `registerPackDirective`, `putPackDirective`, `registerPackParadigm`, `putPackParadigm`, `registerPackStyleguide`,
    `putPackStyleguide`, `registerPackToolguide`, `putPackToolguide`, `putPackRelationship`, `deletePackArtifact` and
    `deletePackRelationship`, judged for every stored activation whose closure the write joins, the activation named in
    the detail, on the closure as the write leaves it: a write that repairs a closure is let through only when the
    closure composes after it, and one that leaves it broken is refused (G8, G21).
  - **Activation writes, `422`**, and nothing is stored: `createActivation`, `replaceActivation`,
    `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
    `reactivateActivationArtifact` and `applyActivationPreset`.

  The code's `operations` map lists every `422` operation, and the shared responses say so: `OfferingUnreadable` (the
  `500` of the composing reads) names `prompt_slot_missing` with an example, beside the two new `pack_unreadable` causes
  (C37 and #447); `ActivationInvalid` and the `422` of each impact-checked pack write name it too. No enum value is
  added. Core: nothing while it is not a consumer (epic #186); a pack whose override drops a slot that an enhancement
  in force fills must declare it again.

- An `overrides` edge whose source is not of an activatable kind (a template, an asset, a guard or a lifecycle) is
  refused (FR-014, operator ruling of 2026-11-12 on #447, option 1). Such an artifact has no activation state, so the
  read dropped it as an overrider while a lock could still put it in force, and the deactivation then found no
  satisfier. A registration answers `422 validation_failed` with one violation per such edge, code
  `overrides_source_not_activatable`, at its pack-relative graph document (example
  `PackRegistrationStatus.overrides-source-not-activatable`). `putPackRelationship` answers `422 validation_failed` with
  the violation at `/from` and writes nothing (example `CharterRefusal.overrides-source-not-activatable`). A composing
  read of a closure that holds such a pack answers `500 pack_unreadable`, naming the graph document
  (`override-source-kind.feature`). Registrations, relationship writes and reads that answered before are refused now.
  An `overrides` edge from an activatable kind is unchanged. spec-kitty-open-packs authors no `overrides` edge, so no
  pack content changes. Core's seam: a client that reads the violations of `validation_failed` meets one more code;
  no `CharterRefusalCode` is added.

- `CharterRefusalCode` gains the value `mission_type_not_in_force` (409, `raised: true`; operator ruling OD-5 of
  2026-11-12, Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`; research R5). `getEffectiveStepPrompt` alone raises it, for
  a mission type the activation's merged closure holds and the activation does not put in force, after
  `activation_not_found`, `mission_type_not_found` and `action_not_found` and before the closure's `500` codes and any
  `304`. The action context of the same action answers `200` with no artifacts, and the two reads differ on purpose:
  the context describes what governs the action, which for a mission type not in force is nothing, while the prompt
  read hands a renderer the text of a running step, and such a mission type has none. The enum is closed, so a
  consumer that validates a refusal's `code` against the earlier enum refuses the new value. Core: nothing while it is
  not a consumer (epic #186).

- A whole-pack registration derives `pack-manifest.yaml`'s lists and refuses an uploaded manifest whose lists differ
  (#110, ADR-0042 amendment of 2026-11-12, ADR-0074 decision 7). The manifest lists one constituent per artifact the
  pack holds (its kind token, id and file) and one entry per preset, each list in path order. A registration whose
  uploaded manifest lists an entry the pack does not hold, or leaves out one it holds, answers `422 validation_failed`
  with one violation per entry, code `manifest_membership`, at `pack-manifest.yaml`, for example
  `pack-manifest.yaml: leaves out constituent directive:B at directives/B.directive.yaml, which the pack holds`. The
  order of the entries and every derived hash are not judged. Every write then stores a manifest with the derived lists
  and fresh hashes: a whole-pack registration, a per-artifact `PUT` and a `DELETE`, and a pack that held none gets one.
  So the stored pack, its revision and its `ETag` include a manifest the upload may not have carried. A
  `pack-manifest.yaml` that is a directory or a link is refused (`unreadable`). Core's seam: a pack whose manifest lists
  a subset of its artifacts, which registered before, is refused until its lists are complete; a client that wrote the
  manifest by hand can leave it out and let the service generate it. A pack stored before this change gets a new
  revision on its next write, so re-registering it unchanged at the same `pack_version`, which answered `unchanged`,
  now answers `409` (same version, another revision): send a new `pack_version`. Pack authors: regenerate each
  `pack-manifest.yaml` from the derived lists, or drop it and let the service generate it. open-packs's `built-in`, the
  one pack there that holds a manifest, differs from its derived lists and is refused until it does (spec-kitty-open-packs#250).

- `satisfiedBy` and `overriddenBy` name every overrider in force, where they named the one with the smallest URN
  (operator ruling of 2026-11-12 on #439, option (b); rules E5, E9 and E14). Each is now an array of
  `ArtifactReference` (`minItems: 1`), sorted by the level of the pack that holds the version in force of each overrider
  (built-in, then org, then project), then by full URN in Unicode code point order. `satisfiedBy` stays absent when no
  overrider satisfies the artifact. The state and the reason of every artifact are unchanged.
- The schemas that change: `EffectiveArtifact.satisfiedBy` and its copies `EffectiveArtifactRead`,
  `EffectiveContextArtifact`, `EffectiveGovernanceEntry`, `EffectiveAgentProfileRead` and `EffectiveSkillRead`; and
  `OverriddenArtifact.overriddenBy`, the item of the skipped report's `overridden` and `overrideNotApplied`. An entry of
  `overrideNotApplied` now names its locked target once, with every overrider in force whose edge was not applied,
  where it was one entry per edge, so `ActivationSummary.skipped` counts such a target once. The schemas are closed
  (ADR-0022 D4), so a consumer that reads either member as an object refuses the array.
- The operations whose answer changes shape: `listEffectiveArtifacts`, `getActivationSkippedReport`,
  `getEffectiveActionContext` (its items and its `skipped`), `getEffectiveArtifact`, `getEffectiveSkill` and
  `getEffectiveAgentProfile` (the answer and its governance entries). The effective mission-type reads carry neither
  member. A write's `ActivationChange.skipped` answers `overridden` and `overrideNotApplied` as `[]`, as before.
- Core: nothing while it is not a consumer (epic #186).

- `getEffectiveActionContext` applies the ownership rule (operator ruling of 2026-11-12, Decision Moment
  `01M774HQ8N5EVYDRTP16MBJCN3`; ADR-0018 D5 and rule E16 as amended). Beside what the action's walk reaches, `artifacts`
  now lists the directives, tactics and paradigms in force that the action's mission type scopes, and those that no
  action and no mission type scopes, each with what ADR-0018's steps 2 to 5 reach from it. One scoped only by other
  actions or other mission types is still absent. An answer that was the action's walk alone now lists more for the
  same activation, revision and packs: the listing's meaning changes (ADR-0022, 0.x line).
- The items of `EffectiveActionContext.artifacts` are `EffectiveContextArtifact` (new), where they were
  `EffectiveArtifact`: the same members, copied, plus a required `appliesThrough` (`action`, `mission_type`,
  `project`), the part of the rule that lists the item; a suggestion carries the value of the entry whose `suggests` edge
  reached it. The schema is closed (ADR-0022 D4), so a consumer that validates an item against `EffectiveArtifact`
  refuses the new member. `listEffectiveArtifacts` and the other copies of `EffectiveArtifact` do not change.
- Every effective read answers a new `ETag` once: rule E17's recipe moves to `v3` (`listEffectiveArtifacts`,
  `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`, `getEffectiveArtifact`,
  `getEffectiveAgentProfile` and `getActivation`), because the context changes meaning under an unchanged revision and
  closure fingerprint. A tag kept from before never matches, so the first conditional read after the upgrade answers
  `200`, not `304`.
- Core: nothing while it is not a consumer (epic #186).

- `asset` and `skill` become served kinds (#105, #107; ADR-0022). `ArtifactKindToken`, the `?kind=` filter, lists
  `asset` and `skill` after `procedure` and before `agent_profile`, and the `400` detail of an unknown `?kind=` names
  them in that order. `Artifact` gains the members `asset` (`Asset`, with `AssetExecutable`) and `skill` (`Skill`,
  with `SkillParameter`, `SkillInvocation` and `SkillExpansion`) in its `oneOf`, which the literal asset and skill
  reads answer. Core must regenerate its DTOs (`ArtifactKindToken`, `Artifact` and the new asset and skill schemas)
  and handle the two kind tokens and the two members before it relies on an exhaustive switch over either.
- An asset id is now one lowercase kebab-case segment of at most 64 characters, the pattern a skill id has
  (`asset.schema.json`). A pack with any other asset id that registered before is now refused as invalid content:
  `artifact_schema_error` in validate mode and `422 validation_failed` at registration. Core's seam must write asset
  ids as kebab slugs, and a pack it holds with another asset id must be renamed before it registers. Every asset of
  the example packs already matches it.

- A sidecar whose asset `path` leaves `assets/` is refused as an escape (#382; ADR-0040 decision 6, amended
  2026-11-11). The path is judged as written: absolute in POSIX or Windows form, or with a `..` part. The detail reads
  `asset path '<path>' escapes the pack's assets/ root`, whether or not a file is at the target, where it read
  `payload assets/<path> does not exist`. A missing payload under `assets/` still reads `does not exist`. The codes do
  not change: a composing read answers `500 pack_unreadable` (`PR-CHARTER-SHARING-004` pins the new detail).
- A refused registration lists every sidecar the store refuses (an escape, a missing payload, a payload another
  sidecar named) once, at its file; it no longer lists it a second time as `unmodelled`, so the violation count
  drops by one per such sidecar (example `PackRegistrationStatus.asset-path-escape`). Core's seam: nothing; a client
  that matched the old detail of an escaping path, or counted the duplicate violation, reads the new answer.

- Every id a request carries is capped in length (#443, after the pack-side caps of #440):
  - 64: a path's `packId` (`PackId`), and `ActivationId`, whose pattern already allowed no more and now says so as
    `maxLength`. `PackSource.packId` states the 63 its pattern allows.
  - 128, the cap `ArtifactId` already carries: a path's `artifactId` (`ArtifactId` parameter), a step contract's id
    (`StepContractId`), and the `id` of a directive, paradigm, styleguide or toolguide registration. A declared id
    over 64 is still refused by the domain, `422 validation_failed`.
  - 255, as written: a relationship registration's `from` and `to`, and the `from` and `to` of a relationship
    `DELETE`. The new `PackQualifiedReferenceWrite` carries the cap for the request side. `PackQualifiedReference`
    and `Urn` stay uncapped, because responses answer references that packs write, which the pack schemas do not cap.

  A request over a cap is a `400` Problem without a code, and nothing is read or written. The relationship mapping
  refuses a longer `from` or `to` too (`422 validation_failed`), for a caller that bypasses the HTTP binding. Core's
  seam: a client that sent a longer id got a not-found or had the id stored; no core call sends one.

- The pack document is `README.md` at the pack root, not `pack.md` (#451, ADR-0042 decision 8 as amended
  2026-11-11). `pack.md` and `PROVENANCE.md` at a pack's root are retired: a pack that ships either is refused at
  registration with violation code `retired`, naming the rule and its reason, and so is a root file that differs from a
  rule's file only in letter case (`Readme.md`, `Pack.md`, `PROVENANCE.MD`). The reason for `PROVENANCE.md` is that its
  content is folded into the README's provenance section. A pack already stored before this change with a `pack.md` or
  `PROVENANCE.md` at its root keeps it until it is registered again. Until then the reads of the pack's descriptor, revision, document and of one kind's files (`getPack`, `/doc`, the artifact
  listing, a directive's item) still answer; the reads that load its whole aggregate do not. The step contract item
  read answers `404 pack_not_found`, as for any pack whose aggregate cannot be read after its `pack.yaml` was read;
  a read that composes the pack with the others (`GET /offering`, the activation and effective reads) answers
  `500 pack_unreadable`, whichever pack it was asked about. No alias, no fallback reader. Core's seam: read the
  pack document from `README.md` (and, once the document read lands, from `/doc`), not from `pack.md`; a pack's
  `PROVENANCE.md` content moves into a `## Provenance` section of its README.

- `org-charter.yaml` is retired, and `pack.yaml` loses `active_charter` (#431, which also settles #381). A pack that
  ships `org-charter.yaml` at its root is refused at registration with `422 validation_failed` and violation code
  `retired` at `org-charter.yaml`, naming the rule and its reason (never `unmodelled`); so is a root file that differs
  from it only in letter case (`Org-Charter.yaml`). A pack already stored before this change with one keeps it until
  it is registered again. Until then the reads of the pack's descriptor, revision, document and of one kind's files (`getPack`, `/doc`, the artifact
  listing, a directive's item) still answer; the reads that load its whole aggregate do not. The step contract item
  read answers `404 pack_not_found`, as for any pack whose aggregate cannot be read after its `pack.yaml` was read;
  a read that composes the pack with the others (`GET /offering`, the activation and effective reads) answers
  `500 pack_unreadable`, whichever pack it was asked about. The descriptor is closed, so a `pack.yaml` that declares `active_charter` is refused as an unknown
  key: every read that reads the descriptor (`getPack`, `/doc`, the artifact listing and item reads) answers that pack
  `500 pack_unreadable`, and so does every composed read, and a registration refuses it with `422 validation_failed` and one
  `pack_unreadable` violation at `pack`; both details name the undeclared key. The `org-charter` schema, with its reserved `skill_namespace`, is gone. Org
  enforcement is a governing activation preset whose items are must-have, plus the levels of `policy.yaml`. No alias,
  no fallback reader. Core's seam: stop reading `org-charter.yaml` and `active_charter`, and apply the governing
  preset's required floor, which the service already serves.

- A `drg/fragment.yaml` node whose `body_path` leaves the pack root is refused (#254). The path is judged as written:
  absolute in POSIX or Windows form (a drive or UNC root), or with a `..` part. It is a graph fault of the fragment: a
  registration answers `422 validation_failed` with a `drg_schema_error` violation at `drg/fragment.yaml`, detail
  `node <id> body_path '<path>' escapes the pack root` (example `PackRegistrationStatus.fragment-body-path-escape`),
  and a composing read of a closure that holds the pack answers `500 pack_unreadable`. The description of
  `drg_schema_error` names the rule. A pack that carried such a path was accepted before and is refused now; no
  example pack does. Core's seam: nothing; no code is added.

- An agent profile declares `role` and optional `alt-roles`; the positional `roles` list is retired with no window
  (ADR-0069, #458). In the `agent-profile` pack schema `role` is required and `roles` is not a key, so a profile file
  that still carries `roles` is unreadable (`500 pack_unreadable`). A role token is kebab-case,
  `^[a-z][a-z0-9-]*$`, at most 64 characters; `alt-roles` holds at least one item when present, each once and never
  the `role` (a repeat is reported as `uniqueItems` at `/alt-roles/<n>`). `generic` is reserved for a fallback profile
  (ADR-0068).
- `AgentProfile` (the profile read) answers `role` and `altRoles` (`[]` when none) and no longer answers `roles`.
- An `excluding` entry that named `roles` names `alt-roles`.
- Core's seam: core reads profiles through this contract only after the cutover; it adopts `role` and `altRoles`
  through the generated client then. spec-kitty-open-packs migrates its profiles before its pin moves
  (open-packs#70).

- Paradigms and styleguides state named principles (ADR-0067, #456). One element, `Principle`
  (`{name, description}`, closed; name 1 to 64 characters, description 1 to 255, neither blank), is used by
  `Paradigm`, `ParadigmRegistration`, `Styleguide` and `StyleguideRegistration`, and by the `paradigm` and
  `styleguide` pack schemas:
  - a paradigm may state `principles` (optional; at least one item when sent; `[]` on a read when it states none);
  - a styleguide's `principles` changes from a list of strings to a list of `Principle` (still required, at least
    one). **Breaking**: a styleguide body or file with string principles is refused (`422 validation_failed` on a
    write, `500 pack_unreadable` on a read).
  - names are unique within their list, ignoring case and surrounding whitespace; a repeat is refused like a schema
    error, reported in a pack file as `uniqueItems` at `/principles/<n>/name`. The list has no count cap.
- Core's seam: nothing; core reads no styleguide or paradigm through this contract yet. spec-kitty-open-packs rewrites
  its styleguide principles before its pin moves (open-packs#66).

- Two packs at one level that define the same step enhancement id are now refused `500 pack_sibling_conflict` on every
  composing read (e.g. `getOffering`). Before, the service did not hold step enhancements and composed such packs,
  ignoring the files (#428). A descriptor `overrides` entry settles the contest as for any other kind, and a same-id
  enhancement at a higher level replaces the lower one by layer override (ADR-0048 d10).
- core: nothing while it is not a consumer (epic #186).

- A refused registration lists every graph fault, where it listed one (operator ruling D-1, "I want all errors in a
  single report"; Refs #424). The `422 validation_failed` of `registerPack`, `putPack` and the registration status
  (`getPackRegistration`) carries one `drg_schema_error` violation per fault of a graph document that cannot be read or breaks
  its schema, each `{pointer: <pack-relative graph file>, code: drg_schema_error}` with a `detail` naming the file and
  the reason, listed with the pack's other faults in the same report. Before, the first graph fault was the only
  violation, `{pointer: pack, code: drg_schema_error}`, and the pack's other faults were not listed. A client that
  reads that single violation at `pack` reads the file from `pointer` instead, and reads every violation of the list.
  The graph faults come first, in path order, then the pack's other violations in their order.
- The descriptions of `drg_schema_error` and of `raised` in `CharterRefusalCode` say it. The contract scenario "An
  archive with two graph documents that break their schema is refused listing both at their files"
  (`whole-pack-registration.feature`) pins it.
- core: nothing while it is not a consumer (epic #186).

- A step prompt's slots are checked (#424; Refs #428), so content that validated or registered before is now refused,
  naming the prompt file, at validate, at load, at registration and on a read: a slot marker that is malformed, repeated,
  nested or unpaired is refused with `prompt_slot_malformed` whatever the pack descriptor declares, and text between
  paired markers is refused with `prompt_slot_not_empty` in a pack whose descriptor declares `schema_version: "1.0"`.
  The pinned packs are unaffected: an unversioned descriptor admits the text with a warning.
- `PackRegistration` gains an optional `diagnostics` member (closed object), present in the result of a succeeded
  registration and listing load warnings, today only `prompt_slot_not_empty` for an unversioned descriptor; `getPack`
  carries none.
- A registration lists every such fault at once, each as a violation `{pointer: <pack-relative file>, code:
  prompt_slot_malformed | prompt_slot_not_empty}` of `422 validation_failed`. Every verified read of a closure holding
  such a pack answers `500 pack_unreadable`, naming the first file.
- core: nothing while it is not a consumer (epic #186).

- A step enhancement and its prompt fragments are checked (#428; Refs #424), so content that validated or registered
  before is now refused with `prompt_fragment_invalid`, naming the file, at validate, at load, at registration and on
  a read:
  - a fragment that is blank, carries frontmatter, a carriage return or a byte order mark, or holds a slot marker of
    any id or leaves a comment open (the enhancement that names a blank, frontmatter-bearing, carriage-return or
    byte-order-mark fragment is the pointer; a fragment with a marker is its own pointer);
  - an enhancement that names one slot twice, a fragment that does not exist, or a fragment another entry names; a
    fragment under `missions/<type>/enhancements/` that no entry names (an orphan);
  - an enhancement without exactly one `enhances` edge, to an `action:<type>/<step>`, with a `<type>` directory that
    differs from that edge's type, or an id repeated across type directories (the second file in path order is
    refused and withheld from the pack), or no `prompt_fragments` key;
  - a step enhancement that is the source of any `overrides` edge is refused with `prompt_fragment_invalid`; an
    enhancement only adds.
- `PUT /packs/{packId}/relationships` refuses, with `422 prompt_fragment_invalid` and nothing written, an edge that
  would give a held step enhancement an `overrides` edge or a second `enhances` edge, or replace its one `enhances`
  edge. Before, a step enhancement was not held, and every edge from one answered `409 registration_conflict`.
  `prompt_fragment_invalid` declares `operations: {putPackRelationship: {status: 422}}` for it.
- A registration lists every such fault at once, each as a violation `{pointer: <pack-relative file>, code:
  prompt_fragment_invalid}` of `422 validation_failed`; an id that differs from the file stem, an entry whose `slot`
  breaks `<namespace>:<name>` and `prompt_fragments: []` stay schema violations. A file reported as
  `prompt_fragment_invalid` is not also listed as `unmodelled`; nor is a refused enhancement, or the fragments beside
  it, listed again beside its schema violation.
- Every verified read of a closure holding such a pack answers `500 pack_unreadable`, naming the first file.
- core: nothing while it is not a consumer (epic #186).

- The pack schemas cap the length of these ids (#440; operator decisions `DM-01M706DW5XHBA4781BYHVDFNT5`,
  `DM-01M706DYNJRHY13DDN5C269JTT` and `DM-01M706E15NPBQMX2AGC88F65NF`). A pack file whose id is longer is a schema
  error, so the pack is refused as `500 pack_unreadable` on read, as for any other schema error. The caps:
  - **64 characters**: the `id` of a guard, lifecycle, procedure, toolguide, mission type, mission step, step
    enhancement, styleguide, tactic, paradigm and directive; a lifecycle state's `id`; and the pack descriptor's
    `name`. A preset id and a skill id were already capped at 64.
  - **136 characters**: a lifecycle guard entry's `ref` (`<pack>::guard:<id>`): two 64-character parts and the
    `::guard:` separator, the same ceiling the domain's guard reference applies.
  - **255 characters**: an executable's `entry` (asset.schema.json) and an expected input or output artifact's
    `artifact_key`.
- Not capped: a preset item id, a lifecycle transition `event`, the `role` of a step or procedure, an agent profile's
  `roles`, the URNs, and the ids of an asset, an agent profile, a glossary pack and a contract. A guard's
  `implemented_by` has no schema cap; the domain caps its `<pack>::` qualifier at 64 characters.
- The domain applies the same caps to the ids it has a type for, before it matches them: the directive, tactic,
  paradigm, styleguide, procedure, toolguide, guard, lifecycle, mission type and mission step ids, a guard reference,
  an executable's entry and an expected artifact's key. A lifecycle state's id, a step enhancement's id and the pack
  descriptor's `name` are capped by the schema only: the domain has no type that checks their length. Every pattern the domain matches with is possessive, so an id of any length is judged without
  overflowing the stack. Before this change, an id of about 10 000 characters threw `StackOverflowError`, which
  answered a bare `500` with no refusal code.
- A write whose artifact id is longer than the cap, such as a directive `PUT` whose path id is 65 characters (the
  `ArtifactId` path parameter allows 128), is refused with `422 validation_failed` and stores nothing. Before, the
  write was accepted and the pack then answered `500 pack_unreadable` on every read.
- Core's seam: nothing; core reads no pack file through this contract.
- No real pack is affected: the longest id in spec-kitty-open-packs at the pin `22c14b2` is 55 characters
  (`refactoring-replace-magic-number-with-symbolic-constant`).

- Mission types and mission step contracts become readable (#99, #148, #96; ADR-0022). `CharterRefusalCode` gains
  `mission_step_contract_not_found` (404, the not-found code of kind `mission_step_contract`) and `skeleton_not_found`
  (404, a skeleton name that does not decode to a skeleton of the mission type); the generic item and references reads,
  `deletePackArtifact` and `getEffectiveMissionStepContract` raise the first, and the skeleton reads raise the second. `Artifact` gains the members `missionType` (`MissionType`) and `missionStepContract`
  (`MissionStepContract`) in its `oneOf`, and `ArtifactKindToken`, the `?kind=` filter, gains `mission_type` and
  `mission_step_contract` after `agent_profile`. The new `MissionSkeleton.encoding` is a closed enum, `text` or
  `base64`. Core must regenerate its DTOs (`CharterRefusalCode`, `Artifact`, `ArtifactKindToken` and the new mission
  schemas) and handle the two codes, the two members and the two kind tokens before it relies on an exhaustive switch
  over any of them. For the five kinds already served the reads answer as before; one behavior changes: a
  `deletePackArtifact` now prunes the directories the removal leaves empty (operator ruling).

- `EnhancerWithoutTarget.targets` (`SkippedReport.enhancedTargetNotInForce`) is a list of `NodeReference` with
  `minItems: 1`, where it was a list of `ArtifactReference` (#429; operator ruling D15). Every carrier of `SkippedReport`
  changes: the response of `getActivationSkippedReport` (`report`), the response of `getEffectiveActionContext`
  (`skipped`), and the `ActivationChange.skipped` of the five writes that answer an `ActivationChange`
  (`selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
  `reactivateActivationArtifact` and `applyActivationPreset`). In each, an item of `enhancedTargetNotInForce[].targets`
  renames `artifactId` to `id`, and its `kind` widens from `ActivatableKind` to `NodeKind`, so a target may be a step of
  a mission type (`{kind: action, id: <missionType>/<step>}`), which the list names where it used to drop it. The
  enhancer's own `kind` and `artifactId` do not change, and neither does any other bucket: `ArtifactReference` stays
  the reference of an artifact an activation can select.
- A consumer that reads a target by `artifactId`, or that validates `kind` against `ActivatableKind`, has to read `id`
  and `NodeKind`; the strict DTOs of core have to be regenerated.

- ADR-0066 prompt-fragment contracts (#424; spec-kitty-open-packs#175). Pack descriptors gain optional
  `schema_version: "1.0"`, which switches on empty prompt slots; unversioned descriptors keep legacy non-empty slots
  with a `prompt_slot_not_empty` warning. Step enhancements gain `prompt_fragments: [{slot, path}]` and the
  `<id>.prompt-fragment.md` Markdown companion, with slot and graph vocabulary in the pack schemas and
  `activated_step_enhancements` in presets. `ActivatableKind`, selected and deactivated artifact maps, cascade kinds
  and node kinds gain `step_enhancement`, so core must regenerate its activation DTOs and handle the new kind before
  selecting or applying a preset that names one. `CharterRefusalCode` reserves `prompt_slot_malformed`,
  `prompt_slot_not_empty`, `prompt_fragment_invalid` and `prompt_slot_missing`, all `raised: false` until #424 builds
  the checks; `DiagnosticCode` reserves the legacy-warning spelling of `prompt_slot_not_empty`. As with other 0.x
  contract additions, core must regenerate its refusal-code DTOs; no existing operation raises the new codes.
  The service classifies and validates step-enhancement files but does not store or activate the kind until #424
  (#428): until then, selecting a `step_enhancement` answers `404 artifact_not_found`, and a preset's
  `activated_step_enhancements` ids are stale.

- The `422 activation_invalid` of a duplicate member (`duplicate_member`) and of a scalar of another type than the one declared (`unreadable_body`) on `createActivation` and `replaceActivation`, entered as `### Changed` in `2026-11-06-activation-body-strictness.md`, is breaking: a body that used to be accepted is refused. Read it as breaking; core's seam sends each member once and a JSON value of the declared type.

- The union floor of rule A1 holds in the writes as it holds in the reads (FR-014, FR-027): the requirement of a
  governing preset id is the union, across the levels (built-in, org, project), of the must-have items of the copy in
  force at each level. Within one level the copy of the pack last in closure order replaces the others, a child pack's
  copy its parent's, and a replaced copy requires nothing. An activation write that denies an artifact only the copy
  of an earlier level requires, which a later level's copy replaced, is refused `409` with `artifact_required`, naming
  the requiring pack, that earlier level's pack included; it answered `200` before, and the next read kept the
  artifact in force. The `floorAdded` and `floorRemoved` notices of a pack registration follow the same rule, so
  replacing a later level's copy no longer reports an earlier level's must-have item as removed. No operation or
  schema changes: the sub-resource writes already declare the `409`.

- A write that meets the pack registry lock for longer than its timeout answers `503` with `Retry-After` and a plain
  `Problem`, as the registration job's `refused` status does: a per-artifact registration (`registerPack*`, `putPack*`,
  `putPackRelationship`) answered `500 internal_error` before. A client that treated that `500` as a fault now sees a
  `503` it must retry: wait the seconds `Retry-After` names and send the same write again; the write changed nothing.
- The activation writes (`createActivation`, `replaceActivation`, `deleteActivation`, `selectActivationArtifact`,
  `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact` and
  `applyActivationPreset`) run under the pack registry's write lock, as the registrations do, so that a document is never
  judged against a pack set a registration is replacing (the WP15 hand-off). A write that meets the lock for longer than its
  timeout now answers `503` with `Retry-After` and a plain `Problem`, declared on each operation as the new
  `RegistryBusy` response; before, an activation write never met the lock. A client must retry such a write after
  `Retry-After`, the same as the per-artifact writes above.
- The `503` is declared on the nine per-artifact and relationship writes of a pack (`registerPackDirective`,
  `putPackDirective`, `registerPackParadigm`, `putPackParadigm`, `registerPackStyleguide`, `putPackStyleguide`,
  `registerPackToolguide`, `putPackToolguide`, `putPackRelationship`) with the `RegistryBusy` response, and on the two
  `DELETE`s of a pack (`deletePackArtifact`, `deletePackRelationship`), which are first built in this release, with
  `PackRemovalUnavailable`: that response also covers `503 pack_source_unavailable` while registration is switched off.

- `EffectiveGraph` gains the required `layerOverrides` (`[LayerOverride]`): a closed response of `getEffectiveGraph`
  gains a member, as in PR B (the operation answered `501` before; FR-018, IC-11). `EffectiveActionContext` gains the
  required `enhancements` (`[ActionEnhancement]`) and its `skipped` is the extended `SkippedReport` the other reads
  carry, so a strict consumer of either response has to know the new members. `getEffectiveGraph` and
  `getEffectiveActionContext` lose `x-reserved` and their `501`: a client that treated either as unbuilt now gets the
  answers below. The `NodeReference` change to `skipped` is `2026-11-09-c-node-reference-step-targets.md`.
- Core's seam: core regenerates its strict DTOs of `EffectiveGraph` and `EffectiveActionContext`; its in-process backend
  has no counterpart (DIV-244).

- A deny entry on an artifact a pack requires is accepted when an active override of it satisfies the requirement, so
  the write behaviour #399 shipped changes in two answers. First, `createActivation`, `replaceActivation` and
  `deactivateActivationArtifact` answered `409` with `artifact_required` for every deny entry they judged on a
  required artifact, and now answer `200` (or `201`) when an artifact that overrides it is in force in the document as
  it will be stored (selected or required, within the language filter and bound to a mission type in force, not
  denied and not hidden by an override itself; operator ruling Q9, research R12). Second, a write that judges such a
  deny entry reads the closure through the in-force resolution, so when the closure's layer merge refuses (a
  forbidden layer override) the write answers `500` with `pack_unreadable` where it answered `409`; the `500` of
  `OfferingUnreadable` is declared on these writes already, and the other per-artifact writes that apply anything
  reach the same refusal (DIV-230), as the effective reads do. An enhancement never satisfies, so a document with only
  an enhancer of the artifact is still `409 artifact_required`, and a locked artifact is still `409 artifact_locked`
  whatever overrides it. A create judges every deny entry of the body. A replace judges only the deny entries it adds.
  A deactivate always judges the named target, whether or not its deny entry is already stored, so a second
  deactivate of a locked artifact is `409 artifact_locked`, and of a required artifact whose override was unselected
  is `409 artifact_required`. A preset applied with `force` and a select with `includeDeactivated` add no deny entry
  and judge none (DIV-204). A later write that removes the override (an unselect of it) is not refused: the stored
  deny entry is then read as not applied, and `getActivationSkippedReport` lists it in `denyNotApplied` with the
  reason `required`. The `ArtifactPolicyRefused` response, the `artifact_required` text of `CharterRefusalCode` and
  the descriptions of the three writes say so. A client that relied on the documented `409
  artifact_required` of `createActivation`, `replaceActivation` or `deactivateActivationArtifact` now gets `200` (or
  `201`) when an active override satisfies the requirement, and the declared `500 pack_unreadable` (the `500` of
  `OfferingUnreadable`) when the closure's layer merge refuses; it must treat both as outcomes of those writes. Core's
  in-process backend still refuses the deny entry (DIV-204).

- `listEffectiveArtifacts` and `getActivationSkippedReport` are declared as built operations, and the documented
  responses of operations already on the contract change with them, the seven listed below (IC-08, IC-17, IC-20; FR-013 to FR-017, FR-022,
  FR-027, FR-028, FR-032). Allowed on the 0.x line (ADR-0022): no `contract-charter-v*` baseline exists yet.
  - `EffectiveArtifact` gains the required `packId` (the configured identifier of the pack that holds the version in
    force) and `level` (`PackLevel`), the winner's provenance after the layer merge (FR-013). It also reaches the
    `artifacts` of `EffectiveActionContext`, which uses the same schema. A client that builds the closed object from
    a response before regenerating fails on the new members.
  - `EffectiveArtifactReason` loses `kind_default` (FR-022: ADR-0053 d10 made it unreachable) and `kind_not_activated`
    (the kind gate is gone with IC-20). A client that switches on the closed enum and still lists either value no
    longer compiles after regeneration.
  - `SkippedReport` gains ten required buckets: `staleIds`, `denyNotApplied`, `languageFiltered`, `overridden`,
    `missionTypeNotInForce`, `overrideNotApplied`, `enhancedTargetNotInForce`, `pinNotHeld`, `requiredNotHeld` and
    `requiredNotInForce`. Every answer that carries the schema now carries them: a write's `ActivationChange.skipped`
    and the `skipped` of `EffectiveActionContext` answer them as `[]`. `skippedByScope` stays: it is the write report
    of the `cascade` scope and is always empty at read (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`).
  - `ActivationChange` gains the required `warnings` (`[{code, artifact}]`, `[]` when none), FR-032 (B6/B7): each
    artifact the write applied that a read will not put in force.
  - Core's seam: core must regenerate its strict DTOs from this contract before it calls a server with this change,
    and must stop reading `kind_default` or `kind_not_activated` as a reason. Nothing is edited in core by this change
    (C-003).
  - `Activation` gains the optional `denyNotApplied` (`[Urn]`, B4, FR-027) and `PackRegistrationNotices` the optional `activationsAffected` (`[ActivationId]`, B4, FR-027). Both objects are closed responses of operations already on the contract (`getActivation`, `createActivation`, `replaceActivation`, a succeeded registration), so a new member is breaking (`contracts/lint/README.md`; the A0 precedent `Activation.dropped`): a strict client must regenerate.
  - The `ETag` of `getActivation` is no longer the bare quoted revision (decision `01M6Y11E55YP5TG1EJ1MDKCFRP`): it covers the revision and the `governanceFingerprint` of the packs of the closure, because `denyNotApplied` is judged against those packs. It changes when a pack of the closure changes, and not when a pack outside it does; `getActivation` can now also answer `500` with a composition code or `pack_unreadable`.
  - What a write's `If-Match` takes (decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`): `replaceActivation`, `deleteActivation` and the sub-resource writes accept the quoted revision or the current `getActivation` tag, so a client may send what it read; a closure tag that no longer matches is `412`. A write's answer keeps the revision as its `ETag` and carries no `denyNotApplied`. A client that compared the `getActivation` `ETag` to `revision` must stop doing so. ADR-0020 D6 and ADR-0059 D5 are amended to say so.

- `replaceActivation` drops instead of refusing what the packs no longer hold in the document it replaces (A4,
  decision `DM-01M6RA7XC5HC8915GZT2R3H83W`): an id of the body the closure no longer holds that the replaced document
  named is dropped and listed in the answer's new `dropped`, where it was `422 activation_invalid`
  (`unknown_artifact`); an unknown id the replaced document did not name is still `422`. Its description no longer
  says that the write never drops an id the client sent.
- `replaceActivation` drops a version pin no pack of the closure holds and lists it in the answer's new
  `droppedPins`, where it was `422 version_not_held` (B1, decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`; the per-artifact
  writes and `applyActivationPreset` report theirs in `ActivationChange.droppedPins`, as
  `2026-11-07-a0-one-kind-model.md` says). `createActivation` keeps `422 version_not_held` for a pin in its own body.
- `Activation` gains two optional members, on a `replaceActivation` answer (`200` or `201`) only and only when the
  write dropped something: `dropped` (`ArtifactReference[]`, sorted) and `droppedPins` (`PackPin[]`, each as the body
  wrote it). A read never carries them, so its bytes still depend on the stored document alone (ADR-0059 D5).
  `Activation` is closed, so a strict client must regenerate before it reads a replace answer that dropped something.
- A `replaceActivation` without `If-Match` can now answer `412`: it is judged again from a fresh read when another
  writer changes the activation in between, up to eight times, and answers `412` with nothing stored when every
  attempt loses (`replaceActivation` and `PreconditionFailed` say so). It was an unconditional compare-and-swap and
  never answered `412`. `ActivationServiceTest.aPutWithoutIfMatchThatLosesEveryAttemptIsAFailedPreconditionAndStoresNothing`
  pins the eight attempts. Core's seam must send `If-Match` or handle `412`.
- Core's seam: core regenerates its strict DTOs from this contract (as for the one kind model), reads `dropped` and
  `droppedPins` on a replace answer instead of handling a `422`, and treats `config` as the document stored. Nothing
  is edited in core by this change (C-003).

- One kind model on the activation document (IC-20, FR-040, FR-041; rulings A7 `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, Q1
  `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, Q2 `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`, Q3 `DM-01M6RCY40CQQTHC130RWF43W0D`, Q4
  `DM-01M6RCY8MYNGNPJ247M2Y4880N`). The document is `packs`, `selected`, `deactivated` and `languages`; mission types,
  steps and agent profiles are ordinary kinds of it. Allowed on the 0.x line (ADR-0022): no `contract-charter-v*`
  baseline exists yet.
  - `ActivationConfig` loses `activatedKinds` and `missionTypes`. The kind gate is gone: nothing is in force because
    its kind is "activated", and a select brings its artifact into force. Mission types are selected under
    `selected.mission_type` and denied under `deactivated.mission_type`.
  - A write (`createActivation`, `replaceActivation`) that sends `activatedKinds` or `missionTypes` is
    `422 validation_failed`, one violation per member at its pointer naming its replacement (`selected.mission_type`
    for `missionTypes`, `selected.<kind>` for `activatedKinds`). There is no deprecation window. One `removed_member`
    violation makes the whole refusal `validation_failed`, even with other violations such as `unknown_member` listed
    beside it (ruling Q4; `activation-kind-model.feature`, "activatedKinds in a create body is 422 validation_failed
    ...").
  - `ActivatableKind` gains `mission_type`, and `SelectableKind` is removed: `ArtifactReference.kind` and the
    selections path's `kind` (`SelectableKindPath`) name `ActivatableKind`, one enum for every kind.
    The widened enum reaches `EffectiveArtifact.kind`, `SharedSkip.kind`, `SkippedDeactivated.kind` and the
    `EffectiveKindQuery` parameter as well, so a response can carry `mission_type`. The generated class
    `SelectableKind` disappears: client code that names it no longer compiles and uses `ActivatableKind`.
    `SelectedArtifacts` and `DeactivatedArtifacts` gain `mission_type`; `CascadeKind` gains `mission_type` (rule
    W21). The exclusions path accepts `kind=mission_type`: a deny entry on a required or locked mission type is
    `409 artifact_required` / `artifact_locked`, like any kind, and an unknown one is `404 mission_type_not_found`.
  - The stored document's `schemaVersion` is `2` (`minimum` and `maximum` `2` in `ActivationConfig.schema.json` and
    its mirror, so the generated DTO keeps an integer). A document stored at version 1 is answered as its version 2
    upgrade (its `missionTypes` become `selected.mission_type`, every selection is kept, `activatedKinds` is
    discarded) and stored as version 2 on the next write (decision `DM-01M6RFGMDH859CJP3RHHF9YC3H`), so every
    activation's revision and `ETag` change once: a client holding an old `ETag` gets one `412`.
  - `ActivationChange` gains the required `droppedPins` (`[{packId, version}]`, `[]` when none): the four
    per-artifact writes and `applyActivationPreset` drop a version pin no pack of the closure holds instead
    of answering `422 version_not_held` (B1); `createActivation` keeps `422 version_not_held` for a pin in its own
    body (decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`).
  - The preset schema (`contracts/packs/schemas/preset.schema.json`) loses `activated_kinds`: a preset carrying it
    refuses the pack as `500 pack_unreadable` when it is loaded, as the preset item id grammar does
    (`2026-11-07-preset-item-id-grammar.md`); a `422` on registration is planned for PR A. It gains `activated_skills` and
    `activated_glossary_packs`, so a preset may name any kind; `activated_mission_types` is a plain member list applied as `selected.mission_type`.
  - The canonical form drops an empty list (FR-042): `selected.<kind>: []` is left out on the wire, so a read answers
    the kind absent, not `[]`, and the revision of a document that held one changes. A client that compares
    `selected.<kind>` to `[]` must treat absent as none.
  - The `ActivationConfig` corpus moves to version 2: `valid/02`, `valid/03` and `valid/08` are rewritten, a
    `selected.mission_type` document is now valid, and `invalid/14-activated-kinds-left-out.json` and
    `invalid/18-mission-types-left-out.json` pin the two removed members.
  - Changed with it, compatible on its own: `applyActivationPreset` applies every kind of the preset; selecting a
    mission type with a cascade reaches the steps it holds, crossed like `requires` (Q1, rule W20), and a cascading
    deactivate of a mission type unselects what only it kept (rule W23); a mission type is deniable unless it is
    required or locked (Q2).
  - Core's seam: core must regenerate its strict DTOs from this contract before it calls a server with this change.
    It then selects mission types under `selected.mission_type`, sends neither `activatedKinds` nor `missionTypes`
    (each is refused), and loses `activated_kinds` narrowing: a core project that narrows it sees a larger effective
    set. Nothing is edited in core by this change (C-003); the migration note is handed over in the pull request.

- The per-artifact writes run the registration impact check (IC-15; FR-025; operator ruling
  DM-01M6VG6GC40AKYYFRR35SV8HVW): `registerPackDirective`, `putPackDirective` and the same two of a paradigm, a
  styleguide and a toolguide, and `putPackRelationship`, judge every closure the pack joins as it will be after the
  write, under the registry lock, as a registration of the whole pack does. A write that would leave a closure unable to
  compose (two packs of one level defining one id is `pack_sibling_conflict`) or breaking a relation rule
  (`override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`) is refused
  `422`, naming the closure's root pack and the packs involved, and writes nothing. Such a write answered `201` or `200`
  before, so a documented success becomes a refusal; core's seam must expect these `422`s from the per-artifact writes
  as from a registration. The `422` description of each of the nine operations says so, and the `operations` map of the
  eleven composition codes in `CharterRefusalCode` lists the nine operations at `422` (a composition code is `500` on a
  read). Nothing is edited in core by this change (C-003).

- Registering a pack replaces the whole pack (ADR-0065 D1, D2; PR-PACK-REGISTER-003): two sources that claim one pack id
  no longer refuse, the later registration replaces the stored pack under the identity checks (a replace keeps the
  `pack_id` and the level; a new pack may not take the `pack_id` or the name of another configured pack). The old
  `@WIP` promise `registration/register-pack.feature` that said the second claim is refused with `SOURCE_CONFLICT` is
  retired, and `whole-pack-registration.feature` states the replace. Registration is asynchronous: `PUT /packs/{packId}`
  and `POST /packs` answer `202` and a status resource, not `201 Created` (ADR-0019 decision 5 is amended), and the `409`
  and `422` answers of the flow are the `problem` of a `refused` status. ADR-0026 decision 2 ("no `DELETE`") is
  superseded: the contract gains two `DELETE` operations, which `Link`'s description (a pack write is never `DELETE`)
  is to follow. Core's seam: core must not wait for `SOURCE_CONFLICT`, must poll `GET /pack-registrations/{id}` instead
  of reading a `201`, and must send `If-Match` on a `DELETE`. Nothing is edited in core by this change (C-003).

- The write gate treats an id that a pack below the written pack already holds as a replace of that pack's artifact, on
  both entry points: the whole-pack registration (`registerPackFromArchive`, `registerPackFromSource`) and
  the per-artifact registrations (`registerPackDirective`, `putPackDirective` and the other kinds' `registerPack*` and
  `putPack*`, which are served). The level is that of the holder's policy, scoped to what the holder and the packs it builds
  on hold (A15), and the written pack's own policy never judges it (B8). `locked` and `extendable` refuse it with `409
  governance_policy_violation`; `open` allows it. Until now a pack built on another could register an id the other pack
  declared `extendable` (the gate saw a registration, which `extendable` permits), so a per-artifact `POST` of such an id
  that was accepted is now refused (decision 01M6VEDYX8TQ4AKCP7C19WAP75, amending 01M6VD94VEVPSC1JXQ1RA7YYHQ). No scenario
  relied on overriding an `extendable` ancestor id, so none changed its expectation. The code and its status are the
  existing ones; the refusal text names the replace.

- Removed members (issue #240, ADR-0064): a relationship is authored only in a pack's graph documents and written
  only through `putPackRelationship`, so no artifact shape carries one any more.
  - Registration bodies: `DirectiveRegistration` loses `procedures`, `references` and `relationships`; `ParadigmRegistration` loses
    `directiveRefs`, `references` and `relationships`; `StyleguideRegistration` and `ToolguideRegistration` lose
    `references`.
  - Read shapes: `Directive` loses `procedures`, `references` and `relationships`; `Paradigm` loses `directiveRefs`, `references`
    and `relationships`; `Styleguide` loses `references` and `relationships`; `Tactic`, `TacticStep` and `Toolguide`
    lose `references`. The item schemas `DirectiveReference`, `ParadigmReference` and `TacticReference` are deleted.
  - A directive's steps are procedure artifacts, reached through a graph edge from the directive, not a list of
    prose steps on it.
  - Added refusal: a registration body (directive, paradigm, styleguide, toolguide, relationship) that sends a member
    its schema does not declare is refused 422 `validation_failed` (existing code) naming the member, before the body
    is read, so a client still sending `relationships` is never answered as though the edges were written.
  - `listArtifactReferences` answers the edges of the pack's graph documents whose source is the artifact, each with
    its edge's relation, then the artifact's derived relations (a guard's `implemented_by`). A target is listed once
    per relation, and `ReferencedArtifact.relation` is now required: every reference has one.
  - Scenarios: `directives.feature`, `paradigms.feature` (a registered artifact has no relationship field; a
    relationship registered through the relationship write is read through the references; an outline refusing each
    removed member), `styleguides.feature`, `toolguides.feature`, `artifacts.feature`, `composition.feature` (the
    cross-pack references now come from a graph shard) and journey 06.

  Breaking on the unreleased 0.2.0-SNAPSHOT line, which ADR-0022 allows; no release carried these members. Core's
  seam: none until the service is incorporated into core; the pack schemas under `contracts/packs/schemas` drop the
  same fields (a directive's `procedures` among them), and an org fragment's edge gains `when`.

- The activation register is built, and an activation's packs get a read of their own (#125, #360; ADR-0020 D3–D6,
  ADR-0059 D4–D5). `createActivation`, `getActivation`, `replaceActivation` and `deleteActivation` lose `x-reserved`
  and their `501`, so the activation set reserves nine operations, not thirteen. `Activation` loses two required
  members, `summary` and `governanceFingerprint`: its `ETag` is the quoted `revision` and its bytes depend only on the
  stored document, so a stale document (an id the offering no longer has, a deny entry on an id now required or
  locked) is served as stored and refused on write (`422`, `409`). `summary` moves to `EffectiveArtifactList`, which
  requires it. `getActivation` and the four effective reads take `If-None-Match` and answer a new success status,
  `304`, without a body (ADR-0020 D8 lists a new success status as breaking). The new `listActivationPacks`
  (`GET /activations/{activationId}/packs`) answers an `ActivationPackList`: the `depends_on` closure rooted at the
  document's `packs` (every configured pack when absent), in dependency order, each item an `OfferingPack` with its
  `ResolvedDependency` facts, with an `ETag` over the revision and the closure's packs, `304`, `404
  activation_not_found` and the offering's `500` composition codes (`pack_reference_pack_missing` among them). A
  `replaceActivation` whose `If-None-Match` is not `*` is `400`. Absent means none (ADR-0053 d10): an absent
  `activatedKinds`, `missionTypes` or `selected.<kind>`, `skill` included, puts nothing in force, like `[]`, and the
  two stay distinct on the wire; an absent `packs` means every configured pack, and its text drops "a later pack
  wins". Core's seam: stop reading `summary` and `governanceFingerprint` from an `Activation` (read the counts from
  `EffectiveArtifactList.summary`), treat `304` on these reads as "unchanged", and stop relying on an absent key's
  per-kind default. For #359, `PackPin` is unchanged; a pinned pack's dependency facts are on the closure read. The
  four activation refusal codes, `activation_not_found`, `activation_invalid`, `artifact_required` and
  `artifact_locked`, become raised by an operation in this change (their `raised` flags are set with the code that
  raises them).
- `getActivation` and `listActivationPacks` declare no `Charter-Source-Fingerprint` on their `200` (the reserved
  `getActivation` declared one): the document read depends only on the stored document (ADR-0059 D5), and the
  closure read reports a change through its `ETag`, which covers the revision and the closure's digest; the two lint
  rules on the fingerprint leave both paths out.

- The pack composition mission's one change set (C-006; `pack-composition-depends-on-and-the-dependency-resolver-01M6F541`):
  the eleven refusal codes of the dependency resolver are declared, and `GET /offering` raises all eleven:
  `pack_dependency_cycle`, `pack_dependency_unknown`, `pack_dependency_out_of_range` (which also covers two ranges on
  one pack that share no version), `pack_parent_unresolved`, `pack_level_violation`, `pack_name_conflict`,
  `pack_duplicate_id`, `pack_reference_unqualified` (a bare reference, in a graph edge, that resolves only in another pack; the typed references, a directive's
  `procedures` and a paradigm's `directive_refs` named here before carry no reference since
  `2026-11-05-edges-only-in-graph-documents`, ADR-0064) and `pack_reference_pack_missing` (a qualified reference whose pack is outside the referring pack's own
  `depends_on` closure), `pack_reference_artifact_missing` (a qualified reference to a pack of the closure that does not
  hold the artifact) and `pack_sibling_conflict`, all with 500 like `pack_unreadable`: the request is fine and the
  configured packs do not compose (operator ruling of 2026-11-03). Each names the violating pack in `detail` and in the new
  optional `packs` member of the refusal body (`Problem.packs`, a list of `RefusalPack`: the `descriptorPackId` (the `pack_id` its
  `pack.yaml` declares), the `packId` (the configured identifier, as on `OfferingPack` and `HoldingPack`) and the `name` of every pack
  the refusal names, the violating pack first). The read exposes what the compose
  resolved (FR-019, FR-020).
  `Urn` stays the bare node identity, `<kind>:<id>` (a pack is provenance, not identity, ADR-0048 decision 10). A
  reference that relies on another pack, `<pack-name>::<kind>:<id>`, is the new `PackQualifiedReference` schema; a
  registered relationship's `to` is one, so a relationship reads back the qualifier the artifact wrote, while its
  `from` stays an artifact of the pack.
  The pack descriptor tightens in the same set: `pack_id`, `parent_pack`, `depends_on[].pack_id` and `overrides[].member`
  must be ULIDs and `name` must match `^[a-z0-9][a-z0-9_-]*$`, or `pack.yaml` is unreadable (`pack_unreadable`).
  `OfferingPack` answers the required `dependsOn` array per pack: each declared dependency's target `pack_id` as `descriptorPackId`, the
  declared PEP 440 `range` and the `version` that satisfied it (`[]` for a pack that declares none), in a new
  `ResolvedDependency` schema, and the required `descriptorPackId`, the `pack_id` the pack's `pack.yaml` declares,
  which `dependsOn[].descriptorPackId` names (`packId` stays the configured identifier, so a client joins a dependency edge to
  its offering entry by `descriptorPackId`). The offering is in the merge order: a dependency comes before the packs that need it, each level stays
  contiguous (built-in, then org, then project), and the packs no dependency relates stay in configured-level, then
  pack-id code-point order. `PackPin` does not carry these facts yet: the activation reads that
  would fill them are reserved, and the member is deferred to #359. The additive response properties are graded `ERR` by
  `../compat/severity-levels.txt`, so `info.version` moves `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT` (ADR-0022: a 0.x
  breaking change takes a minor bump and this `### Breaking` entry). No released contract is broken incompatibly:
  no release was cut under any version (C-002), so the compatibility check has no baseline. Scenarios: the new
  `scenarios/composition.feature` drives the eleven codes `GET /offering` raises with a negative control on the same
  fixture shape, plus the diamond, the descriptor without `depends_on` and the settled sibling contest; the raised-codes
  table of `scenarios/errors.feature` lists the eleven codes. Core's seam: a client that reads `GET /offering` must
  tolerate the `dependsOn` member (or move to the new version) and may now meet the eleven refusal codes.

- `CharterRefusalCode`'s `x-refusal` entries may declare a status per operation: `operations.<operationId>.status` is the
  status that one operation answers the code with, in place of the entry's `status` (shape chosen as the smallest that
  keeps one entry per code; the entries without `operations` are unchanged). `pack_reference_pack_missing`,
  `pack_reference_unqualified` and `pack_reference_artifact_missing` declare 422 for `putPackRelationship` and stay 500
  everywhere else. `PUT /packs/{packId}/relationships` now judges its `to` against the compose of the pack it registers
  into, with the one rule the offering applies to what a pack writes, and refuses before any file is written: a
  qualified `to` naming a pack outside the pack's `depends_on` closure (`pack_reference_pack_missing`), a bare `to` that
  only another pack holds (`pack_reference_unqualified`) or a qualified `to` naming an artifact the named pack does not
  hold (`pack_reference_artifact_missing`), each 422 with the `packs` member (the pack registered into first). A `to`
  that no pack holds stays external and is accepted, and a qualified `to` that names another pack of the closure with
  the same id as the `from` is legal. A `to` with the same id as the `from` that is bare, or qualified with the pack
  registered into, is a self-loop, refused `422` `validation_failed`. The `422` response of the operation
  documents the three codes. Scenario: `scenarios/composition.feature`.

- `GET /offering` and `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` refuse `pack_unreadable` (500) when
  any file of any configured pack is invalid or cannot be read, an asset sidecar whose payload the pack does not hold
  included, not only when a `pack.yaml` is (operator ruling of 2026-11-04): both read the compose, and the compose names every
  configured pack whole, so there is no partial answer. A client that read `GET /offering` against a set of packs one
  of which holds a broken file now meets this refusal; the pack-scoped read of an artifact of that pack still answers.
  Scenario: `scenarios/offering.feature`. Known gap: the pinned built-in pack cannot be served by `GET /offering` until
  spec-kitty-open-packs#75 lands.

- New operation in this release: `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` (`listArtifactReferences`), a composed read: it
  resolves the compose once inside the read and answers the references one artifact writes (`ArtifactReferenceList`),
  each as written (`PackQualifiedReference`), with its relation when the artifact mints one, the `HoldingPack` that
  holds what it names (`packId`, `descriptorPackId`, `name`), the bare `urn` it resolves to and an `artifact` link to the
  pack-scoped read of the target. A bare reference is held by the pack that writes it, which shadows a same-named
  artifact of another pack. A compose the resolver cannot serve refuses it with the same 500 composition code and
  `packs` as `GET /offering`; the pack-scoped read of the artifact still answers. The pack, the kind and the artifact
  are judged as for the artifact read (404). New relation key `artifact`. Scenarios: `scenarios/composition.feature`.

- Added refusal (issue #323): `putPackRelationship`'s `RelationshipRegistration` body is at most 1 MiB; a larger
  body is refused 422 `validation_failed` (existing code) before it is read, by its `Content-Length` and otherwise
  one byte past the limit. The service holds it with the request-body guard that bounds the directive, paradigm and
  styleguide registration bodies. The 422 description and the schema description say so. Scenario: in
  `relationships.feature`, "A relationship registration body over 1 MiB is refused before it is read", with a body
  under the limit answered by the precondition check instead. Breaking on the 0.x line, which ADR-0022 allows; no
  release carried the unbounded body. Core's seam: none (no schema shape, enum or status changes; a relationship
  body is a few hundred bytes).

- Added refusals (issues #292, #309, #310): a directive, paradigm, styleguide or toolguide registration with a `null`
  list, or a `null` item in one, is refused 422 `validation_failed` (existing code) on POST and PUT; a list is sent as
  an array, `[]` where it may be empty, and an optional one may be left out. The refusal comes before the body is bound,
  so it wins over a 400 the same body would otherwise get; a `null` field inside an object item stays a 400. A
  directive, paradigm or styleguide registration body is at most 1 MiB; a larger body is refused 422 `validation_failed`
  before it is read, by its `Content-Length` and otherwise one byte past the limit (the toolguide's 7 MiB body limit is
  unchanged). The eight 422 descriptions of `registerPack{Directive,Paradigm,Styleguide,Toolguide}` and
  `putPack{Directive,Paradigm,Styleguide,Toolguide}` and the four `*Registration` schema descriptions say so; the
  service has refused them since #311 and #318. A detail names the list and, for an item, its index counted from 0.
  Scenarios: in `directives.feature`, `paradigms.feature`, `styleguides.feature` and `toolguides.feature`, "A null list,
  or a null item in a list, is refused before the body is bound" (a null-list and a null-item row each, with the exact
  detail), and in the first three "A <kind> registration body over 1 MiB is refused before it is read", with a body
  under the limit registered instead. Breaking on the 0.x line, which ADR-0022 allows; no release carried the accepted
  `null` or the unbounded body. Core's seam: none (no schema shape, enum or status changes; a client that sent `null`
  for a list sends `[]` or leaves it out, and no shipped artifact body is near 1 MiB).

### Removed

- The response components `ActionNotFound` and `NotImplemented` (issue #312): no operation referenced either, so no
  bundle held them and no check judged them. `ActionNotFound` named `mission_type_not_found` and `action_not_found`
  for the action-context read #139 removed; those codes are raised through `MissionStepNotFound` and the activation
  step reads. `NotImplemented` was kept for a reservation to come, and no operation is reserved today; a reserved
  operation declares its 501 when it is reserved. The example `CharterRefusal.action-not-found` stays: three responses
  use it. A contract test, `ReachableContractFilesTest`, now fails on any definition file no `$ref` from `openapi.yaml`
  reaches. No operation changes; core's seam changes nothing.

### Added

- A pack's `policy.yaml` may pin roles: `role_pins: {<role>: <profile-id>}` (ADR-0030, ADR-0032 decision 4, ruling D3;
  a role token `^[a-z][a-z0-9-]*$`, never `generic`, to a profile-id `^[a-z0-9][a-z0-9_-]*$`, each 1 to 64
  characters, in authored order). Role locks stay reserved. A whole-pack registration judges the pins over the pack's
  own closure, with the write in place, and ends `refused` with `422` and the code, naming the closure, when a pin
  names a profile no pack of the closure holds (`role_pin_unknown`), names a profile whose `role` is not the pinned
  role and whose `alt-roles` do not list it, a human-performed profile included (`role_pin_not_fitting`), or when two
  packs of the closure pin one role to different profiles (`role_pin_conflict`; pins are tighten-only).
- A registration also ends `refused` with `422` when the pack's fallback profile is human-performed
  (`fallback_profile_not_agent`, ruling D4), and when the `specializes_from` edges among the agent profiles of a
  closure it joins form a cycle (`specialization_cycle`, ruling D2), naming the cycle's profile ids in order. The
  startup census logs a stored pack that breaks one of these rules.
- Core's seam: nothing until the selection read exists.

- `selectAgentProfile`: `GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=` (tag
  `EffectiveAgentProfiles`; ADR-0032 decision 1, ADR-0069 decision 4, rulings D1 to D4 of slice G, #42, #102). It
  answers which agent profile in force fills a role: `ProfileSelectionRead` holds the role, the asked context
  (`ProfileSelectionContext`) and the `candidates`, each a `ProfileSelectionCandidate` with its `id`, the `pack` that
  holds the version in force, its `fit` (`primary`, `alt` or `fallback`) and an `effective-artifact` link; `tie` is
  true when several are answered. The
  order: the role pin of the closure's `policy.yaml` `role_pins` narrows the candidates to the pinned profile and its
  specializations, a human-performed profile serves only `operator` or a role pinned to it, then role fit, the
  context (`languages` and `frameworks` of a specialization), the tier, the longest `specializes_from` chain and
  `routing-priority`; with nothing left, the fallback in force, never for `operator`. An enhancer is never a
  candidate. The role pattern refuses `generic` (`400`); the ETag follows the effective reads and names the role and
  the context.
- Refusals: `404 activation_not_found`; then `422 fallback_profile_conflict` and `422 role_pin_conflict` (two packs
  that pin the asked role differently); then `404 no_profile_matched`. `no_profile_matched` and
  `fallback_profile_conflict` are now raised (`raised: true`), with their declared statuses.
- `selectAgentProfileForStep`: `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/profile-selection?language=&framework=`
  (tag `EffectiveAgentProfiles`; FR-012 of slice G, #42). It answers what `selectAgentProfile` answers for the role
  the step names, the step read at its place as the step, context and prompt reads read it; a step that names no role
  answers the fallback in force. `ProfileSelectionRead.role` is the step's role and is absent for a step that names
  none, so it is no longer required. An unknown mission type or step is `404 mission_type_not_found` or
  `404 action_not_found`, after the activation and before the conflicts; the ETag names the step and the context.
- Link relation `profile-selection`: the effective step read (`getEffectiveMissionStep`) and the action context
  (`getEffectiveActionContext`) link the step's selection under the same activation.
- Core's seam: core may select a step's profile through the step-keyed read instead of reading the step's role and
  walking the profiles itself.

- A new refusal code, `profile_enhancement_drops_field` (rulings D5 and D7, #380): an agent profile that `enhances`
  another may add to it, never drop part of it. Its identity fields (`profile-id`, `name`, `purpose`, `role`,
  `alt-roles`, `performer`, `routing-priority`) are its own and never merged; every other top-level field it declares
  replaces the enhanced profile's, and is refused when it loses a list item (in any order), a sub-field (recursively)
  or a value. An enhancer that declares a non-empty `excluding` is refused the same way, naming `excluding`. The detail
  names the enhancing pack first and the field. A whole-pack registration, and each write judged over the closures its
  pack joins, ends `refused` with `422` and this code; a read of content stored before the rule answers `500`. The
  startup census logs a stored pack that breaks the rule. Core's seam: nothing until the effective profile read exists.

- `getKindTaxonomy`, `GET /offering/kinds` (R18, #112; ADR-0077 decision 1, operator ruling
  DM-01M7B1YE4J84K0T4ZA27M48QKG): the kind taxonomy of the charter's model, built from the kernel's enums and the
  same whatever packs are configured. `kinds` lists every node kind in the model's order (`KindRow`: `kind`; `plural`,
  absent for a kind with none; `artifactKind`, the token the pack list's `?kind=` takes, absent for a kind that is no
  artifact kind (`action`); `artifact`; `activatable`; `declarableInGraphs`, whether a graph document may declare it;
  `edgeTarget`, whether an authored edge may target it; and a templated `artifacts` link, built from `artifactKind`,
  for a kind the pack list serves). The two graph facts are published apart, not as one `authoredInGraphs`, because
  the parallel glossary work (#106) adds a kind that is only an edge target and has no plural; today both are false
  only for `guard` and `lifecycle`. The service checks them apart as well: the domain's single `authoredInGraphs`
  is removed, a node declaration and an edge's source are checked against `declarableInGraphs` and an edge's target
  against `edgeTarget`, so no answer changes while the two agree for every kind;
  `relations` lists every relation, then `in_tension_with` (`RelationRow`: `relation`, `heldAs`, `directed`, `derived`
  and its allowed `{source, target}` pairs, `EndpointPair`). New schemas `KindTaxonomy`, `KindRow`, `RelationRow`,
  `EndpointPair`, `TaxonomyKind` and `TaxonomyRelation`; the header `KindTaxonomyETag`, a strong `ETag` that is the
  sha256 of the served body, with `304` on a matching `If-None-Match`; the example `KindTaxonomy.model`. It carries the
  `Charter-*` headers and no source fingerprint, and none of core's org facts. The taxonomy carries its own kind and
  relation enums, because `NodeKind` and `Relation` leave out the kinds and relations no graph read or relationship
  write names (guard, lifecycle, `implemented_by`, `in_tension_with`); a parity test keeps both equal to the domain.
  `ApiRoot` and `Offering` link it as `kinds`. The switch-gate register lists `getKindTaxonomy` as `ready`. Core's
  seam: read the kinds, plurals, flags and endpoint pairs from it instead of hand-kept lists.

- Scenarios: `journeys/journey-12-a-mission-walk-under-one-activation.feature` (#572) walks one mission under one
  activation over charter's example packs, by links from the API root: the example pack `org-base` registered from the
  filesystem source, the charter in force with nothing refused, Java Jenny's governance, the 13 steps of `software-dev`
  in order, and the review step's role with the review profile and the `adversarial-squad-deployment` procedure it
  requires, in force. The scenario that reads every step's action context and prompt is `@WIP` until slice F (#537)
  adds the example packs' missing action nodes. No operation or schema changes.

- `listPackGlossaryContexts` (`GET /packs/{packId}/artifacts/glossary`), `getPackGlossaryContext`
  (`GET …/glossary/{contextId}`) and `getPackGlossaryTerm` (`GET …/glossary/{contextId}/terms/{termId}`), tag
  `Glossaries` (#106; ADR-0049 decision 7, ADR-0074 decisions 3 and 9 to 14). The list answers
  `GlossaryContextList {packId, items: [GlossaryContextSummary {id, name, description, tags?, termCount, _links}],
  _links}` in id order, unpaged. The context answers the `Artifact` envelope with member `glossary`
  (`GlossaryContext`: the pack file in camel case, every term as a `GlossaryContextTerm`, `appliesTo` as a
  `GlossaryAppliesTo`) and links `self`, `collection` (the context list) and `term`, templated by the term's id, a new
  relation. The term answers `GlossaryTerm {id, name, definition, status, examples?, aliases?, avoid?, seeAlso?, urn,
  contextId, _links}` with links `self` and `up` (the context). The path parameters `contextId` and `termId` take a
  letter-first kebab slug of at most 64 characters, anything else is `400`. The three answer the pack revision as
  `ETag` and the source fingerprint. The segments are judged in path order: an unknown pack is
  `404 pack_not_found`, then a context the pack does not hold is `404 artifact_not_found` with the detail
  ``glossary context `<contextId>` is not held``, then a term the context does not hold is `404 artifact_not_found`
  with the detail ``term `<termId>` is not held in glossary context `<contextId>` ``. `listPackArtifacts` lists a
  pack's glossary contexts after its skills, titled by their name, and `?kind=glossary` filters on them; the templated
  item read refuses a glossary context `404 kind_not_found` and names the literal path, and `deletePackArtifact` does
  not remove one. The three operations are `ready` in the switch-gate register.

- The activation twins of the glossary reads (#106; FR-011 to FR-013, ADR-0074 decision 12):
  `listEffectiveGlossaryContexts` (`GET /activations/{activationId}/artifacts/glossary`),
  `getEffectiveGlossaryContext` (`GET …/glossary/{contextId}`) and `getEffectiveGlossaryTerm` (`GET
  …/glossary/{contextId}/terms/{termId}`), tag `EffectiveGlossaries`. Each answers the copy of a context the layer
  merge keeps across the levels of the closure, whole: a term only a copy the winner replaced holds is not in force.
  The list answers `GlossaryContextList` of the closure's context winners, whatever their state, in id order and
  unpaged, with no `packId` (each item's `artifact` link leads to its pack's copy): `GlossaryContextList` serves both trees, and its
  `packId` is present in a pack and absent under an activation. A list item links `effective-artifact` and `artifact`.
  The context answers `EffectiveGlossaryContextRead`, the members of `EffectiveArtifactRead` (`state`, `reason`,
  `policy`, `required`, `satisfiedBy?`, `suggestion?`) plus `glossary` (`GlossaryContext`), whatever its state, and
  links `self`, `artifact`, `collection`, `term` (templated by the term's id) and `up`. The term answers
  `EffectiveGlossaryTermRead`, the same members plus `term` (`GlossaryContextTerm`), with its context's facts, because
  a term has no state of its own, and links `self`, `artifact` (`getPackGlossaryTerm` in the winner's pack) and `up`
  (the context under the activation). Each answers a strong `ETag` of its own read and `304` to a matching
  `If-None-Match`. The segments are judged in path order, before any `If-None-Match`: an unknown activation is `404
  activation_not_found`, then a context no pack of the closure holds is `404 artifact_not_found` with the detail
  ``glossary context `<contextId>` is not held``, then a term the winning copy does not hold is `404
  artifact_not_found` with the detail ``term `<termId>` is not held in glossary context `<contextId>` ``; two packs of
  one level holding one context are `500 pack_sibling_conflict`. An `Activation` links `glossaries`
  (`listEffectiveGlossaryContexts`), a new relation, after `skills`. The three operations are `ready` in the
  switch-gate register.

- The vocabulary reads of an activation (#106; FR-014 to FR-017, ADR-0076), tag `Vocabulary`, each `ready` in the
  switch-gate register. `listActivationVocabularyTerms` (`GET /activations/{activationId}/vocabulary/terms`) answers
  `VocabularyTermList`: `activationId`, `revision`, `governanceFingerprint`, `terms` and `_links` (`self`, `activation`).
  The terms are those of the winning copy of each glossary context in force, whole (a term only a replaced copy holds is
  not listed), whatever their status, ordered by context id and then term id in Unicode code point order. A
  `VocabularyTerm` carries `urn`, `contextId`, `id`, `name`, `definition`, `status`, `examples?`, `aliases?`, `avoid?`,
  `seeAlso?`, the `packId` and `level` of its context's winning copy, and `_links` (`effective-term`,
  `effective-glossary`). `getActivationVocabularyContextive` (`GET …/vocabulary/contextive`) answers a Contextive v1
  definitions file as `application/yaml`, a string with an example in the contract: the contexts in force by id
  (`name`, `domainVisionStatement`, `paths?`, `terms`), their terms by id (`name`, `definition`, `examples?`,
  `aliases?`, `meta: {urn, status}`); an `Accept` that excludes `application/yaml` is `406`, a Problem with no code.
  `listActivationVocabularyTaboos` (`GET …/vocabulary/taboos`) answers `TabooList`: `entries` of `TabooEntry` (`word`,
  `urn`, the URN of the term to use instead, and `termName`, its name), one per avoided word per term, ordered by word and then URN in Unicode code point order, and
  `_links` (`self`, `activation`). Each read answers its own strong `ETag` and `Charter-Source-Fingerprint`, and `304`
  to a matching `If-None-Match`, judged after its refusals: `404 activation_not_found`, and the closure's `500` codes
  (`pack_sibling_conflict` among them). An activation with no glossary context in force answers empty `terms`,
  `entries` and `contexts`. An `Activation` links `vocabulary-terms`, `vocabulary-contextive` and `vocabulary-taboos`,
  after `glossaries`; the relations `activation`, `effective-term` and `effective-glossary` are new too. The REST
  conventions' success media type gains its `application/yaml` exceptions, `/openapi.yaml` and the Contextive read.
- One term, three schemas, one shape (#106; operator ruling 2026-11-14): `GlossaryTerm`, `GlossaryContextTerm` and
  `VocabularyTerm` hold the term members (`id`, `name`, `definition`, `status`, `examples?`, `aliases?`, `avoid?`,
  `seeAlso?`) with identical schemas, and a contract test pins them. A `seeAlso` entry's two ids are letter-first kebab
  slugs of at most 64 characters, in the contract schemas as in `glossary.schema.json`.
- A profile's governance under an activation lists a term (#106; operator ruling 2026-11-14): a `vocabulary` edge
  onto `term:<context-id>/<term-id>` gives an `EffectiveGovernanceEntry` while the context's winning copy is in force
  (state `active`) and holds the term, and none otherwise. The entry has kind `glossary`, the context's id as
  `artifactId`, the new optional member `termId`, the facts of the context's winning copy, and links `effective-term`.

- An `Activation` links its held assets and skills (#409, #105, #107): `assets` (`listEffectiveAssets`), `asset`,
  templated `…/activations/{activationId}/artifacts/asset/{artifactId}` (`getEffectiveAsset`), and `skills`
  (`listEffectiveSkills`), after `effective-context`. A guard runner (`asset:<id>`) expands `asset` with the asset's id
  and reads it, and its payload, without building a path. The relation table gains the three relations, in the
  contract on an activation.

- The switch-gate register, `scenarios/switch-gate.yaml` (ADR-0059 D1, amended 2026-11-12; issues #9 and #72). It
  says, per operation, whether core may switch to it: `ready` when every status its responses declare is asserted on
  it by a `@contract` scenario, or `held` with the `statuses` it is held for and a reason. The contract suite fails
  when the register and the scenarios disagree, and writes a per-operation report of the scenarios of each answer. 44
  operations are ready; `deleteActivation` and `getPackRegistration` are held, because their declared `500` is a failure of storage
  the scenarios cannot fail. The register ships in `charter-scenarios.tar.gz`; changing its keys, or moving an
  operation from ready to held, is a breaking change.
- Scenarios that assert, on the operation itself, each declared answer no scenario asserted there before:
  `every-operation.feature` (the worktree guard's `401` and `403` on all 46 operations), `unexpected-failure.feature`
  (`500 internal_error` on the 40 operations whose declared 500 a pack read can raise), `activation-write-refusals.feature`,
  `pack-write-refusals.feature`, `read-refusals.feature` and `registration/pack-source-refusals.feature`. The service's
  answers are unchanged.

- `listPackSkills` (`GET /packs/{packId}/artifacts/skill`) and `getPackSkill` (`GET …/skill/{artifactId}`), tag
  `Skills` (#107; ADR-0074 decisions 4, 9 and 10). The list answers `SkillList {items: [SkillSummary {id, title,
  description, form, _links}], _links}` in id order, unpaged. The item answers the `Artifact` envelope with member
  `skill` and links `self` and `collection` (the skill list): the skill as its file declares it, in camel case, with a
  prompt skill's `body` verbatim, exactly as the pack holds it, and no `body` for a wrapper skill. `bodyPath`,
  `overrides` and `enhances` are not answered. Both answer the pack revision as `ETag` and the source fingerprint; an
  unknown pack is `404 pack_not_found`, a skill the pack does not hold `404 artifact_not_found`. Both operations are
  `ready` in the switch-gate register.

- `listPackPresets` (`GET /packs/{packId}/presets`) and `getPackPreset` (`GET /packs/{packId}/presets/{presetId}`),
  tag `Presets`: a pack's activation presets in id order, unpaged, and one preset, each with its `description` and,
  per kind it lists, an array `activated<Plural>` of `PresetItem {id, mustHave}` (schemas `Preset`, `PresetItem`,
  `PresetList`). Both answer the pack revision as `ETag` and the source fingerprint. An unknown pack is
  `404 pack_not_found`, a preset the pack does not hold `404 preset_not_found`. A pack answer (`getPack`, a succeeded
  registration's result) links `presets`, and both operations are `ready` in the switch-gate register (#98).

- `getEffectiveStepPrompt`, `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt`
  (ADR-0066 decision 9; FR-003 to FR-005; #438): the prompt in force of one step, as it is stored, at the step's
  place (the mission type's version in force when it declares the step, else the merged winner of the action whose
  version declares it), and every slot it declares with the fragments of the step enhancements in force that fill
  it, ordered by pack level, then pack name, then enhancement id (decision 5), each with its pack and enhancement. A slot
  nothing fills is listed empty; a slot holding legacy text is listed with its fragments and a `prompt_slot_not_empty`
  diagnostic. It answers an `ETag` (`EffectiveETag`, rule E17's recipe) and `304`, and sends
  `Charter-Source-Fingerprint` on its `200`. Its refusals come before the `304`, in order: `404 activation_not_found`;
  the `500` of a closure that cannot compose or merge (its composition code, `pack_unreadable`), before the names are
  judged; `404 mission_type_not_found`, `action_not_found`; `409 mission_type_not_in_force`; then
  `500 prompt_slot_missing`. New schemas `EffectiveStepPrompt` (`promptPackId` optional, `diagnostics` reusing
  `Diagnostic`), `EffectivePromptSlot`, `EffectivePromptFragment` and `SlotId`; a response `MissionTypeNotInForce`; the
  operation is `ready` in `switch-gate.yaml`.
- The relation `prompt`, listed in the hypermedia relation table and the `link-relations-are-known` rule:
  `getEffectiveActionContext`'s `_links.prompt` leads to the effective prompt of the same action, present exactly when
  the activation puts the mission type in force (FR-007). The prompt links back to the context as `effective-context`
  and to the step under the activation as `effective-artifact`.

- The references twin (#532; FR-012, research R1 and R10), tag `EffectiveReferences`:
  `listEffectiveArtifactReferences` (`GET /activations/{activationId}/artifacts/{kind}/{artifactId}/references`)
  answers `ArtifactReferenceList`, the references the version in force writes. It is the pack read of the references
  (`listArtifactReferences`) of the pack that holds the layer-merge winner, for the same kind and id, item for item and
  in its order, read against the source fingerprint of the read that named the winner (`500 stale_input` when the
  packs changed between the two); an overridden artifact answers the references its winning copy writes. Each item whose
  target the activation's closure holds, of a listed kind, also links `effective-artifact`, its read under the
  activation; the answer links `self` and `up` (the activation). The artifact is answered whatever its state. An
  unknown activation is `404 activation_not_found`, then a kind the service does not list `404 kind_not_found`, then an
  artifact no pack of the closure holds `404 artifact_not_found`. As the pack read, it has no `ETag`. No literal path
  ends in `/references`, so the template answers every listed kind, an asset and a skill included. The operation is
  `ready` in the switch-gate register.

- The activation twins of the asset and skill reads (#105, #107; FR-009 to FR-011, research R1 to R3 and R15), tags
  `EffectiveAssets` and `EffectiveSkills`. `listEffectiveAssets` (`GET /activations/{activationId}/artifacts/asset`)
  answers `AssetList` of the assets the closure holds, each the copy the layer merge keeps (a project copy over a
  built-in one), in id order, unpaged; an asset is in force when a pack of the closure holds it.
  `getEffectiveAsset` (`GET …/asset/{artifactId}`) answers `EffectiveAssetRead {activationId, revision,
  governanceFingerprint, kind: asset, artifactId, packId, level, policy, asset, _links}`: no `state`, `reason` or
  `required`, which an asset never has, and links `self`, `artifact`, `payload`, `collection` and `up`.
  `getEffectiveAssetPayload` (`GET …/asset/{artifactId}/payload`) answers the winning copy's bytes as
  `getPackAssetPayload` answers a pack's, read against the source fingerprint of the read that named the winner
  (`500 stale_input` when the packs changed between the two). `listEffectiveSkills` (`GET …/artifacts/skill`) answers
  `SkillList` of the closure's skill winners, whatever their state, and `getEffectiveSkill` (`GET …/skill/{artifactId}`)
  answers `EffectiveSkillRead`, the members of `EffectiveArtifactRead` plus `skill`, whatever its state. A list item
  links `effective-artifact` and `artifact`; a list links `self` and `up`. Each of the five answers its own
  `EffectiveETag` and `304` to a matching `If-None-Match`; an unknown activation is `404 activation_not_found`, then an
  asset or skill no pack of the closure holds `404 artifact_not_found`, judged before `If-None-Match`. The effective
  list keeps answering `?kind=asset` with `400`, and the templated effective read (`getEffectiveArtifact`), which the
  literal routes win over, refuses `asset` with `kind_not_found` naming the literal path. The five operations are
  `ready` in the switch-gate register.

- `listPackAssets` (`GET /packs/{packId}/artifacts/asset`), `getPackAsset` (`GET …/asset/{artifactId}`) and
  `getPackAssetPayload` (`GET …/asset/{artifactId}/payload`), tag `Assets` (#105; ADR-0074 decisions 2, 9 to 11 and
  13). The list answers `AssetList {items: [AssetSummary {id, mime, executable, _links}], _links}` in id order,
  unpaged. The item answers the `Artifact` envelope with member `asset` and links `self`, `payload` (the bytes) and
  `collection` (the asset list). The payload answers the bytes the pack holds, unchanged, with the asset's `mime` as
  `Content-Type` when it is a well-formed `type/subtype` (validate's one rule) and `application/octet-stream`
  otherwise; it ignores `Accept`, and every `200` carries `X-Content-Type-Options: nosniff` and
  `Content-Security-Policy: sandbox; default-src 'none'`, and no `Content-Disposition`, on purpose (headers `XContentTypeOptions`, `ContentSecurityPolicy`). All three answer the
  pack revision as `ETag` and the source fingerprint; the payload answers `304` to a matching `If-None-Match`,
  `*` included. An unknown pack is `404 pack_not_found`, an asset the pack does not hold `404 artifact_not_found`
  (response `AssetNotFound`). The relation `payload` joins the relation table and the lint ruleset's relation
  enum; the proposed relation `assets` (ADR-0019's `/packs/{packId}/assets`) is dropped (ADR-0074 decision 13). The
  three operations are `ready` in the switch-gate register.

- `asset` and `skill` are listed kinds of a pack (#105, #107): `listPackArtifacts` lists a pack's assets and skills
  after its procedures and before its agent profiles, and `?kind=asset` and `?kind=skill` filter on them. An asset's
  summary title is its title, or its id when it has none; a skill's is its title. The new `Artifact` members and kind
  tokens are under `### Breaking`. The templated item read
  (`getPackArtifact`) renders neither: it refuses `404 kind_not_found` and names the literal path, as for an
  `agent_profile`. `deletePackArtifact` does not remove an asset or a skill: it answers `404 kind_not_found`, as before.
  A reference whose target is an asset or a skill the compose holds now carries its `artifact` link.

- Every answer that carries an `Activation` (`getActivation`, `createActivation`, `replaceActivation`) links what a
  client does next from it (#409): `offering`; `packs`, `graph` and `skipped`; `effective-context`, templated
  `…/mission-types/{missionType}/actions/{action}/context{?depth}`; `edit` (`PUT`) and `delete` (`DELETE`) on the
  activation; `select` and `exclude` (`PUT`), templated `…/selections/{kind}/{artifactId}` and
  `…/exclusions/{kind}/{artifactId}`; and `apply-preset` (`PUT`), templated `…/presets/{presetId}`, beside `self` and
  `artifacts`. `unselect` and `include` are not on the activation. An `ActivationChange` still links only `up`. The
  relation table moves these relations into the contract on an activation; no relation is new.

- Procedures are served (#484). `procedure` joins the listed kinds: `GET /packs/{packId}/artifacts?kind=procedure`,
  the item `GET /packs/{packId}/artifacts/procedure/{artifactId}` (member `procedure`, schema `Procedure` with
  `ProcedureStep` and `ProcedureAntiPattern`; an absent procedure is `404 artifact_not_found`), its `/references`,
  and under an activation `GET /activations/{activationId}/artifacts/procedure/{artifactId}` and the effective
  listing. The pack item carries the source fingerprint and, as the writable kinds do, the pack revision as its `ETag`;
  that revision is the `If-Match` of a removal, and the service serves the removal of a procedure
  (`DELETE /packs/{packId}/artifacts/procedure/{artifactId}`, an absent one is `404 artifact_not_found`). A procedure
  has no `PUT`. The `ETag` on every other pack item read (tactic, mission step contract, mission type) follows in
  #487.
  `ArtifactKindToken` lists `procedure`. An action context entry of a procedure, already listed, now links
  `effective-artifact` and `artifact`. Core's seam: nothing; additive.

- Every pack read of one thing answers the pack revision as its `ETag` (#487): `getPackArtifact` for a tactic and a
  mission_step_contract, `getPackMissionType`, `getPackMissionStep` and `getPackMissionSkeleton` join the kinds that
  already did. The published "no ETag" statements of these reads are withdrawn. For the mission-type family the tag
  is a concurrency token naming the pack, not a cache validator: the answer can carry content from dependency packs
  that the revision does not cover. No pack read takes `If-None-Match` or answers 304; activation reads are unchanged.
  Core's seam: nothing to change. Additive. Closes the contract half of #487.

- The pack document is read (#451). `GET /packs/{packId}/doc` (`getPackDocument`, tag `Registration`) answers the
  pack's `README.md` in the form `Accept` chooses (the service's producible-type matching ranks by quality and
  specificity, does not treat `q=0` as a refusal, and settles a tie by the first type listed in `Accept`):
  `text/markdown` (and `text/*`) answers the held file, byte for byte, frontmatter included, as
  `text/markdown;charset=UTF-8`; `application/json` (and `application/*`, `*/*`, and no `Accept`) answers the new
  closed schema `PackDocumentRead` (`title`, left out when the document has no H1; `body`, the Markdown below the H1 in
  canonical form, without the frontmatter; `_links` with `self` and `pack`). When both forms are named at equal quality
  the type the client lists first answers. The `ETag` is the pack revision, quoted, the same for both forms, as
  `getPack` answers it, and the 200 carries `Charter-Source-Fingerprint`. Every 200, 304 and 406 carries
  `Vary: Accept`; every operation's `406` now carries `Vary: Accept`. `If-None-Match` is honoured: a match, or `*`, is `304` with the `ETag`, `Vary` and no body. An
  `Accept` that names neither form is `406`, a `Problem` with no code. A pack with no `README.md` is
  `404 pack_document_not_found`, a new refusal code; an unknown or built-in pack is `404 pack_not_found`; a pack whose
  `pack.yaml` cannot be read is `500 pack_unreadable`, as `getPack` answers it; a `README.md` the store refuses is
  `500 pack_unreadable`.
  The `doc` relation joins the link table: the pack resource (`getPack`, and the result of a succeeded registration)
  carries `doc` exactly when the pack holds a `README.md`. `PackRegistration` is unchanged otherwise. The generated
  client types `getPackDocument` by its first response media type, `application/json`, so it reads the JSON form; the
  Markdown form is the raw call. Core's seam: nothing; additive. A client that matches on the closed set of refusal
  codes learns `pack_document_not_found`.

- The effective reads link the mission type reads (#99, #450, FR-008): an `EffectiveArtifact` item of kind
  `mission_type` or `mission_step_contract` (effective list, action context) carries `effective-artifact`
  (`getEffectiveMissionType`, or `getEffectiveMissionStepContract` for a step contract) and `artifact` (`getPackMissionType` or
  `getPackArtifact` of the pack that holds the version in force), and an `EffectiveGraphNode` of those kinds carries
  `effective-artifact`. A step node `action:<type>/<step>` carries `effective-artifact`, the step's read under the
  activation (`getEffectiveMissionStep`), and the `EffectiveActionContext` carries `effective-artifact` (the same read)
  and `artifact`, the step's read in the pack that holds the mission type's version in force (`getPackMissionStep`).
  A graph node names no pack, so it has no `artifact`. The profiles mission's WP08 scenarios that pinned these entries
  without `_links` now pin the links.

- A pack may declare its fallback agent profile in `pack.yaml`: `fallback_profile: <profile-id>` (ADR-0068, #457;
  string, in the identifier grammar `^[a-z0-9][a-z0-9_-]*$`, 1 to 64 characters). A whole-pack registration judges it over the pack's own closure, with the
  write in place, and ends `refused` with `422` and the code, naming the closure, when the profile is held by no pack
  of the closure (`fallback_profile_unknown`), is not `locked` along it (`fallback_profile_not_locked`), or does not
  declare `role: generic` with no alt-roles (`fallback_profile_not_role_neutral`). The startup census logs a stored
  pack that breaks the rule.
- Reserved refusal codes (`raised: false`): `fallback_profile_missing` (the built-in pack declares no fallback; raised
  once open-packs ships the declaration), `fallback_profile_conflict` and `no_profile_matched` (`404`), both for the
  profile-selection read.
- Core's seam: nothing until the selection read exists.

- The mission type reads under an activation, tag `EffectiveMissionTypes` (#99, FR-007): `getEffectiveMissionType`
  (`GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}`), `getEffectiveMissionStep`
  (`.../steps/{stepId}`) and `getEffectiveMissionSkeleton` (`.../skeletons/{skeletonName}`), the literal paths the
  generic `getEffectiveArtifact` leaves for the kind, as `getEffectiveAgentProfile` is for `agent_profile`. They answer
  the bodies of the pack-scoped reads (`Artifact` with `missionType`, `MissionStep`, `MissionSkeleton`) for the
  layer-merge winner of the type in the activation's merged closure, whatever its state: `packId` is the pack that holds
  the winner, the skeletons and the step prompt and guidelines are that pack's, and a step's `step-contract` link names
  the merged closure's step contract bound to the step. Every link stays under the activation, but the type's
  `artifact`, the pack-scoped read of the winner (`getPackMissionType` of the holding pack). Refusals in order:
  `activation_not_found`, then `mission_type_not_found` for a type no pack of the closure holds, then `action_not_found`
  (an unknown step) or `skeleton_not_found`. No `ETag`. Scenarios: `effective-mission-types.feature`.
- `getEffectiveMissionStepContract` (`GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}`, tag
  `EffectiveMissionTypes`, D11): the layer-merge winner of a step contract under an activation, in the `Artifact`
  envelope with `missionStepContract` as the pack read answers it, `packId` the pack that holds the winner. The generic
  `getEffectiveArtifact` refuses `mission_step_contract` with `404 kind_not_found`. Its
  `mission-type` and `step` links and a held delegation candidate's `effective-artifact` stay under the activation;
  `artifact` is the pack read of the winner. A step's `step-contract` link under an activation now lands on it.
  Refusals: `activation_not_found`, then
  `mission_step_contract_not_found`. No `ETag`. The 404 of each mission read under an activation names only its own
  codes (`ActivationMissionTypeNotFound`, `ActivationMissionStepNotFound`, `ActivationMissionSkeletonNotFound`,
  `ActivationMissionStepContractNotFound`).

- The mission type reads, tag `MissionTypes` (#99, #148, #96): `getPackMissionType`
  (`GET /packs/{packId}/artifacts/mission_type/{missionTypeId}`, the `Artifact` envelope with member `missionType`: the
  stored fields, the steps then the on-demand steps, each linked, and the skeletons, each linked), `getPackMissionStep`
  (`.../steps/{stepId}`, the stored prompt and guidelines byte for byte, expected input and output artifacts, and the
  `mission-type`, `step-contract` and `skeleton` links) and `getPackMissionSkeleton` (`.../skeletons/{skeletonName}`,
  the stored bytes as `text` or `base64` with `sha256` and `size`). `{skeletonName}` is one path segment: the path
  under the type's `templates/` escaped as in RFC 6901 (`~` as `~0`, then `/` as `~1`), so
  `templates/divio/howto-template.md` is `divio~1howto-template.md` (D9). Refusals in order: `pack_not_found` or
  `pack_unreadable`, `mission_type_not_found`, then `action_not_found` (an unknown step) or `skeleton_not_found`.
  `mission_step_contract` is served by the generic item read
  (`getPackArtifact`); `mission_type` is listed, filtered and referenced like the other kinds, and its item is answered
  by `getPackMissionType`. Neither kind answers an `ETag`. New relations `mission-type`, `step`, `step-contract` and
  `skeleton` join the hypermedia guide's table and `link-relations-are-known`.
- `deletePackArtifact` removes a mission type and a step contract (#99, D10). A mission type goes with its step prompt
  and guidelines files and its skeletons as one removal under one impact check; it is refused `409`
  `artifact_referenced` while a step contract whose `mission` names it, or an edge that names one of its action nodes
  (`action:<type>/<step>`) or skeletons, refers to it, and `409` `registration_conflict` while an author-written graph
  document declares the type's, a skeleton's or an action node's node, or holds an edge from the type or a skeleton. A step contract is removed as the five kinds are. An unknown id is
  `mission_type_not_found` or `mission_step_contract_not_found`. Neither kind answers an `ETag`: `If-Match: *` or the
  pack revision. `deletePackArtifact` now also prunes the directories a removal leaves empty, up to the pack's root, for
  every kind (operator ruling): the last directive of a pack takes `directives/` with it.

- The links from the API root to the effective reads, without building a URL (#407, #450 for the served kinds). Every
  member is optional or lies in an open `_links` map, and nothing is renamed or removed.
  - The API root links `activations` (`/api/v1/activations`, `method: POST`), the register a client creates an activation
    in. The `Activation` answer (`createActivation`, `getActivation`, `replaceActivation`) links `artifacts`, the RFC 6570
    template `/api/v1/activations/{activationId}/artifacts{?kind,state}` (`templated: true`) of its effective list, beside
    `self`.
  - `EffectiveArtifact` (the item of the effective list and of the action context) and `EffectiveGraphNode` gain an
    optional `_links`, the open `Links`. For a kind the pack-scoped artifact routes serve, an item links
    `effective-artifact` (`getEffectiveArtifact`, or `getEffectiveAgentProfile` for an `agent_profile`: the artifact's read
    under the activation) and `artifact` (the pack-scoped read of the pack that holds the version in force). A graph
    node names no pack, so it links `effective-artifact` only. `_links` is absent for any other item, a step (`action`)
    and a mission type among them, and an item has no `self`. The mapper asks the service for the served kinds at each
    read, so a kind the packs slice opens later is linked without a contract change.
  - Every entry of an `EffectiveAgentProfileRead.governance` that carries `artifact` also carries `effective-artifact`:
    the read of the entry's winner under the same activation. `EffectiveGovernanceEntry` still holds `_links` as an
    object that is empty for a kind no pack-scoped read serves.
  - `effective-artifact` is a new relation of the hypermedia guide and of the `link-relations-are-known` rule; the
    `activations` relation is now in the contract on the root, and the `artifact` and `up` rows name the effective reads
    they appear on.

  This is additive, not breaking (ADR-0022): a new key of an open `_links` map is a link a client must tolerate
  (`Links`), and the two new `_links` members of closed response schemas are optional and absent where nothing is linked.
  No contract version has been released (`0.1.0-SNAPSHOT` was never cut) and the closed-schema rule for response fields
  freezes at 1.0.0, so core's seam changes only if it regenerates its client and wants to follow the links.

- `getEffectiveArtifact`, `GET /activations/{activationId}/artifacts/{kind}/{artifactId}` (tag `Activation`), the generic
  activation-scoped read of one artifact (#407, #96): the version the layer merge keeps across the levels of the
  activation's closure, as `EffectiveArtifactRead` (closed): `activationId`, `revision`, `governanceFingerprint`, `kind`,
  `artifactId`, the `packId` and `level` that hold the version in force, and its `state`, `required`, `policy` and `reason`
  as the effective list gives them, with `satisfiedBy` and `suggestion` where the list has them. It embeds no body:
  `_links` holds `self` (with `depth` when the request sent it), `artifact` (the pack-scoped read of the winner's pack) and
  `up` (the activation). `kind` is any kind the pack-scoped artifact routes serve; an `agent_profile` is answered by its
  own literal operation. The refusals are judged in order: `activation_not_found`, `kind_not_found` and, for an id that
  no pack of the closure holds, `artifact_not_found`, before any `If-None-Match`. The answer carries
  `Charter-Source-Fingerprint` and a strong `ETag` over the activation's revision, the packs of its closure and the kind,
  the id and the `depth`; a matching `If-None-Match` is `304`, and `depth` outside 0 to 1 is a `400`.

- `getEffectiveAgentProfile`, `GET /activations/{activationId}/artifacts/agent_profile/{artifactId}` (tag
  `EffectiveAgentProfiles`), the read of one agent profile under an activation (#407, #96 for `agent_profile`), the literal
  path the generic `getEffectiveArtifact` leaves for the kind. It answers `EffectiveAgentProfileRead` (closed): the members
  of `EffectiveArtifactRead` (`activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, the `packId` and
  `level` that hold the version in force, its `state`, `required`, `policy` and `reason`, with `satisfiedBy` and
  `suggestion` where the effective list has them), the winner's profile in `agentProfile` (the same closed `AgentProfile` as
  the pack-scoped read) and `governance`, a list of `EffectiveGovernanceEntry` sorted by `urn`. An entry has the facts of an
  effective artifact (`kind`, `artifactId`, the winner's `packId` and `level`, `state`, `required`, `policy`, `reason`,
  `satisfiedBy`) and the walk's `step`, `relation`, `via`, `hops` and `edge`. It is not `PackGovernanceEntry`: it has no `held` and no `urn`, its `packId` is the winner's pack and never null,
  and its `reason` is the effective reason, not a word of the walk. The walk is judged by the activation: an artifact in
  force is listed with the step that reached it, a suggestion out of force is listed with `state: inactive`,
  `reason: not_selected`, and an ancestor (`specializes_from`) that is not in force is dropped and seeds
  nothing, which diverges from the cascade of the activation writes, which passes an ancestor through (operator ruling W1).
  The profile the walk starts from is exempt: it is answered whatever its state, and the state is on the read. The stored
  winner was answered when the operation was added; in this release `agentProfile` is the effective profile of #380
  (see Breaking: the enhancers and the `specializes_from` ancestors are folded in). `_links` holds `self`
  (with `depth` when the request sent it), `artifact` (the pack-scoped read of the winner's pack) and `up` (the activation);
  an entry's `_links.artifact` is the pack-scoped read of its winner, for the kinds the generic artifact route serves. The
  refusals are judged in order: `activation_not_found`, then `artifact_not_found` for an id that no pack of the closure
  holds as a profile, before any `If-None-Match`. The answer carries `Charter-Source-Fingerprint` and a strong `ETag` over
  the activation's revision, the packs of its closure and the profile and the `depth`; a matching `If-None-Match` is `304`,
  and `depth` outside 0 to 1 is a `400`. No relation is added.
- Every `EffectiveGovernanceEntry` carries `edge`, the `GovernanceEdgeWords` of the pack-scoped read (the schema is
  introduced with `getPackAgentProfile`): the words of the edge the walk reached the entry through (FR-004), whatever its
  relation, and whether the artifact is in force (`state: active`) or out of force (`inactive`, `not_selected`). It is
  always present and empty where the edge states neither word, so a wordless entry looks the same in force and out of
  force. `edge` is a schema of its own because `EffectiveArtifactSuggestion` is defined only for an artifact out of force
  that a `suggests` edge reaches (#408 ruling 3) and keeps that meaning on every generic effective read.
- The tag `EffectiveAgentProfiles` is separate from `AgentProfiles`, which the pack-scoped profile operation uses, so that
  the profiles slice implements this operation in a controller of its own (`useTags=true` generates one API interface per
  tag). The operation id, the path and the body are unaffected.

- `NodeReference` (`{kind: NodeKind, id}`, closed), the reference of a graph node that may not be an activatable
  artifact (#429, D15). `id` admits the slash of a step (`^[A-Za-z0-9][A-Za-z0-9_./-]*$`). It is introduced once and
  reused: `ArtifactReference` stays the reference of an artifact an activation can select or deny, `NodeReference` names
  a node that may not be activatable, and `Urn` names the identity of a graph node. `NodeKind` lists the authored kinds
  only, so no carrier of `NodeReference` can name a guard or a lifecycle.
  Adding guard or lifecycle to `NodeKind` later is a Breaking change for `EffectiveGraphNode`, `NodeReference` and every
  `SkippedReport` carrier.

- `getPackAgentProfile`, `GET /packs/{packId}/artifacts/agent_profile/{artifactId}` (tag `AgentProfiles`), the pack-scoped
  read of one agent profile (#407, #96 for `agent_profile`). It answers the `Artifact` envelope with `kind: agent_profile`,
  the profile in the new `agentProfile` member and, in the new optional `governance` list, the artifacts the profile's
  governance walk lists over the pack's composed `depends_on` closure. `depth` (0 or 1, default 0) is the number of
  `suggests` hops the walk follows. `AgentProfile` is closed and mirrors `contracts/packs/schemas/agent-profile.schema.json`
  field for field in camelCase; `AgentProfileExcluding` is the closed wire form of `excluding` (`wholeFields` or `fieldValues`,
  the other null). `PackGovernanceEntry` is one governance entry (`kind`, `artifactId`, `urn`, `step`, `relation`, `via`,
  `hops`, `edge`, `packId`, `held`, `_links`), `GovernanceStep` its walk step (`lineage`, `requires`, `suggests`,
  `requires_of_suggested`, `vocabulary`). The walk follows the merged closure, so where a pack of the closure overrides
  an artifact (a layer override), an entry's `packId` and its `artifact` link name the copy that wins the merge, whose
  edges the walk followed, as the read under an activation does (operator ruling `01M72NRA73EZ02HYYMBQQVRJF3`). A
  parent the edge's place does not hold is listed with `held: false`, a null `packId` and no link. `held` describes the edge that listed the entry: a URN listed early by a dangling edge keeps
  `held: false` even when a later step reaches it through a held edge. The walk never goes through an entry with
  `held: false`, so nothing is listed below it, even where another pack of the closure holds the same URN.
  Every entry carries `edge`, the new closed schema `GovernanceEdgeWords` (`when` and `reason`, both optional): the words
  of the edge the walk reached the entry through, whatever its relation (`specializes_from`, `requires`, `suggests` or
  `vocabulary`). It is an empty object where the edge states neither word, and it is always present. Where more than one
  edge could reach an entry, the words are those of the edge of its `via`, the first reach the walk keeps.
  `suggests` entries are listed by this service; core's profile channel treats them as inert. The body references
  (`directiveReferences` and the others) are body fields and are not walked. The answer carries `Charter-Source-Fingerprint`,
  the digest the read verified, and an `ETag` that is the pack revision, as a directive's is: the precondition a
  `DELETE` of the profile sends in `If-Match`, not a validator of the governance, which a dependency pack can change
  without changing it (the read takes no `If-None-Match`). A profile the pack does not
  hold is `404 artifact_not_found`. The `Depth` parameter now speaks of any walk, and the `references` relation is added
  to the relation table for the profile's link to its references read.
  `ArtifactKindToken` gains `agent_profile`, last, so `GET /packs/{packId}/artifacts?kind=agent_profile` lists a pack's
  profiles (a request-side enum widened: a client that never sends the token is unaffected). The pack's artifact list,
  `listArtifactReferences` and `deletePackArtifact` now serve the kind (see the `### Changed` fragment of this change); the `collection`,
  `references` and lineage `artifact` links of a profile read therefore answer 200.

- `getEffectiveGraph` (`GET /activations/{activationId}/graph`) and `getEffectiveActionContext`
  (`GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/context`) are declared as operations
  that answer (IC-11): `ReservedOperationCoverageTest` counts no reserved operation, and `activations.feature` loses
  its two `501` pins; `effective-graph-and-context.feature` pins the reads (US5 AS1 to AS5). Each answers an `ETag` of
  its own representation over the activation's revision and the packs of its closure, and `304` on a matching
  `If-None-Match`.
  - The graph answers the nodes in force (the artifacts in force, and each held step whose mission type is in force),
    the edges among them resolved through the layer overrides (an edge from an overridden version is absent, an edge to
    one ends at the winner), and `layerOverrides`, one record per losing version of a node. A template, an asset, a
    guard and a lifecycle are not nodes, and their overrides are not listed.
  - The action context answers ADR-0018's walk from the action's step over the artifacts in force, sorted by URN, each
    entry as `listEffectiveArtifacts` lists it. An artifact the walk reaches only through `suggests` from a listed
    artifact, that the activation does not select, is listed with `state: inactive`, `reason: not_selected` and the
    edge's `when` and `reason` in `suggestion`; an artifact also reached through `requires` or another hard relation is
    not. A mission type the activation does not put in force answers `artifacts: []` and `enhancements: []`, and the
    mission type is in `skipped.missionTypeNotInForce`. An unknown mission type or action is `404`.
  - `action_not_found` is raised: `404` on `getEffectiveActionContext`, judged against the merged closure.
- `LayerOverride` (`{artifact, winner, loser}`) and `LayerOverrideSide` (`{packId, level}`), `ActionEnhancement`
  (`{target: NodeReference, enhancedBy}`): the enhancers in force of the action's step and of each artifact the context
  lists. A step enhancer enhances the step, which is no artifact, so `enhancements` is the one member that carries it.
- Examples: `EffectiveGraph.one-edge` gains `layerOverrides: []`, `EffectiveGraph.layer-override` is new, and
  `EffectiveActionContext.implement` carries a suggestion and an enhancement.
- `getEffectiveGraph` and `getEffectiveActionContext` declare `400` as the shared `Problem` response, as the other
  activation reads do (`2026-11-06-activation-reads-and-writes-declare-400.md`). The service answers it for a
  malformed query: a `depth` outside 0 to 1 on the context, pinned in `effective-graph-and-context.feature`. The context
  and the graph also say that an unknown mission type or action is `404`, and a closure that cannot merge is `500`,
  before any `304`.
- The context's `skipped.missionTypeNotInForce` is the one exception to the `SkippedReport` carrier rule: it holds the
  requested mission type when that is not in force and is empty otherwise, so an artifact bound to another mission type
  that is not in force is absent from it. `createActivation` and `replaceActivation` describe the `500
  pack_unreadable` of a closure that cannot merge, as the deactivate does (DIV-230). The headers
  `Charter-Source-Fingerprint` and `ETag` no longer call the graph and the context reserved.

- `listEffectiveArtifacts` (`GET /activations/{activationId}/artifacts`) and `getActivationSkippedReport`
  (`GET /activations/{activationId}/skipped`) lose `x-reserved` and their `501`, so the contract declares them as
  operations that answer (IC-08). `ReservedOperationCoverageTest` counts two reserved operations of the `activation`
  set (the effective graph and the effective action context, PR C), and `activations.feature` loses the two `501`
  pins; `effective-reads.feature` and `layer-merge.feature` replace them with scenarios that pin the reads.
  - The list answers every artifact of the merged closure with its `state` (ADR-0020 D7, operator ruling Q1): "in
    force" is `state=active`. It is filtered by `kind` and `state`, paged by a keyset cursor over `(kind, id)` bound to
    the tag of the set it pages (a cursor from another tag is `400`), and answers the `summary` of the whole set,
    identical on every page. `ETag` and `If-None-Match` follow the representation (revision, the packs of the
    activation's closure, `kind`, `state` and the cursor), so one representation never answers `304` to the tag of
    another. A `500 pack_unreadable` is documented for an override the mutability level forbids (ADR-0063 D3) and for
    an `overrides` cycle among active artifacts; the language filter applies at read only (#408 ruling 2).
  - The skipped report lists each artifact not in force once, in the typed bucket of its first deciding rule (data
    model: EffectiveSkippedReport).
- `EffectiveArtifact.satisfiedBy` (`ArtifactReference`, optional): for a required artifact that is out of force, the
  active artifact that overrides it and so satisfies the requirement. It never names an enhancer.
- `EffectiveArtifact.suggestion` (`EffectiveArtifactSuggestion {when, reason}`, optional, #408 ruling 3): present only for an artifact reached only through a `suggests` edge, with the edge's `when` and `reason`; absent for one reached through `requires` or another hard relation. It serves the list and, in PR C, the action context.
- `EffectiveArtifactReason` gains `overridden`, `mission_type_not_in_force` and `enhanced_target_not_in_force` (the
  last for an enhancer none of whose targets is in force, operator ruling Q8). `EffectiveArtifact.required` is true
  for a required artifact that stays out of force for either of the last two reasons.
- `SkippedReport` item schemas: `DenyNotApplied`, `OverriddenArtifact`, `EnhancerWithoutTarget` and
  `RequiredNotInForce`. `pinNotHeld` reuses `PackPin`.
- `ActivationWarning` (`{code, artifact}`, codes `enhanced_target_not_in_force` and `overridden`).
- `Activation.denyNotApplied` (`[Urn]`, optional, see Breaking; B4, FR-027): the deny entries of the stored document that the required
  floor or a lock overrides, present only when there is one.
- `PackRegistration.notices.activationsAffected` (`[ActivationId]`, optional, B4, FR-027): the stored activations whose
  in-force set a registration changes. The other notice lists still name activatable kinds only (issue #417).
- Examples: `EffectiveArtifactList.first-page` and `EffectiveActionContext.implement` carry `packId` and `level`,
  `ActivationSkippedReport.nothing-skipped` carries the empty buckets, and `ActivationSkippedReport.each-bucket` is new.

- A cascading select and a cascading deactivate take an optional `depth`: the number of `suggests` hops the walk may
  cross (IC-21, FR-043; issue #408 ruling 1, decision `DM-01M6TDH0SY5Q224GX709DXWG8K`). `SelectionRequest` and
  `ExclusionRequest` gain `depth` (integer, `format: int32`, `minimum: 0`). Absent means unlimited, exactly as before
  (ADR-0059 D2), so a client that does not send it sees no change.
  - Only `suggests` is counted, as ADR-0018 D4's `depth` counts it on the action-context walk. `requires`, the other
    hard relations, `enhances`, the `specializes_from` pass-through and a mission type's steps are walked in full.
    `depth: 0` follows no `suggests` edge; `depth: 1` follows one, and so on. An artifact the walk reaches over
    `requires` is selected at any depth, even when a `suggests` path also reaches it. Unlike ADR-0018 D4's read
    `depth`, this one has no default and no maximum.
  - A cascading deactivate honours `depth` in the target's own walk, so a deactivate at depth N undoes a select at
    depth N in what it removes. The walk of the other active artifacts, which decides what is kept, stays unlimited
    (decision `DM-01M6TF3BA808JMW0W0A0CPXBNW`), so the round trip is not exact when another active artifact reaches
    a candidate over `suggests`: that candidate is kept and reported in `skippedShared`. `ExclusionRequest.cascade` no
    longer says the two round-trip without that exception.
  - `SkippedReport` gains the optional `skippedByDepth` (a list of `ArtifactReference`): the `suggests` targets one
    hop past the bound, present only when a cascade and a `depth` were sent, so a client knows whether a deeper `depth` would reach
    more.
  - `depth` without `cascade` is accepted and has no effect. A `depth` that is not an integer (`"1"`, `1.5`) is
    `422 activation_invalid` with the violation `unreadable_body` at `/depth`; a negative one is `422 activation_invalid`
    with the violation `below_minimum` at `/depth`, and one past the int32 range with `above_maximum`.
  - A new client that sends `depth` to an older server gets `422 unknown_member` at `/depth`: the older body guard
    refuses a member its schema does not declare.

- Whole-pack registration and removal (ADR-0065; PR A of the layer merge mission, issue #10). One new tag,
  `Registration`. Each operation was declared reserved (`x-reserved`, contract set `pack-registration`, `501` under
  ADR-0020 D8) and is built by a later change of the same release (the `...-served.md` fragments); at the end of PR A
  none is reserved:
  - `registerPackFromArchive`, `PUT /packs/{packId}` (new `paths/packs_packId.yaml`): the body is the pack directory as
    one raw archive (`application/gzip`, `application/x-gzip`, `application/x-tar+gzip` or `application/zip`, at most
    32 MiB). It answers `202` with a `PackRegistrationStatus` (`pending`) and `Location:
    /api/v1/pack-registrations/{registrationId}`, and replaces the pack the service holds whole, removals included
    (FR-001, FR-024). `If-Match` (optional, `parameters/IfMatch.yaml`, now also the quoted strong tag of a pack
    revision) and `If-None-Match: *` (create only) give `412` at acceptance, both together `400`. The synchronous
    answers are `202`, `400`, `401`, `403`, `412`, `413`, `415` and `503` (with `Retry-After` when the queue is full,
    `pack_source_unavailable` when registration is switched off). There is no `428` here.
  - `registerPackFromSource`, `POST /packs` (new `paths/packs.yaml`): the body is a `PackSource`; the answers are the
    `PUT`'s, with the one `428` of a registration: a git branch ref that names no commit, sent with neither `If-Match` nor
    `If-None-Match: *` (FR-004).
  - `getPackRegistration`, `GET /pack-registrations/{registrationId}` (new
    `paths/pack-registrations_registrationId.yaml`): `200` with the status in `pending`, `running`, `succeeded` (with
    the `PackRegistration`) or `refused` (with the `Problem` the synchronous API would have answered); `404
    registration_not_found` once the status is forgotten (one hour after the job ends, or after a restart). A `refused`
    status carries every `409` and `422` of the flow, and `412` after a revision moved, so the registration codes are
    declared on this operation's `x-refusal` entries, not on the two registrations (the `x-refusal` text of
    `CharterRefusalCode` says so). It reads the registration jobs, not a pack file, so its `200` carries no
    `Charter-Source-Fingerprint` (decision DM-01M6TV6VDNHNCVWRHHC74WATV0); the path is exempt from the lint rules of
    that header, as the activation reads are.
  - `getPack`, `GET /packs/{packId}`: `200` with the stored pack as a `PackRegistration` (no outcome), the quoted
    revision as its `ETag`, and `_links` of `self` and `artifacts`; `404 pack_not_found` (FR-039). No pack or status
    carries an `edit` link, and the contract names no `edit` relation for a pack, so the whole-pack `PUT` is not
    advertised by a link (the examples `PackRegistration.stored` and `PackRegistrationStatus.succeeded` show none).
  - `deletePackArtifact`, `DELETE /packs/{packId}/artifacts/{kind}/{artifactId}` (in the existing item path file): `204`
    with the new revision as `ETag`; `If-Match` on the pack revision is required (`428` absent, `412` stale); `404`
    `pack_not_found`, `kind_not_found` or the not-found code of the kind (`directive_not_found` and the like, the code
    `getPackArtifact` answers on the same path); `409` `artifact_referenced` (with `packs` and
    `violations`), `governance_policy_violation` (the new `REMOVE` operation of the write gate) or
    `registration_conflict` (the artifact is written in the pack's author-written graph documents, which a `DELETE`
    never rewrites); `422` for a closure that no longer composes (PR A stays strict, decision
    `DM-01M6TQ67C9ZTNGZB9CY28JS54F`) (FR-005, FR-036).
  - `deletePackRelationship`, `DELETE /packs/{packId}/relationships?from=&to=` (in the existing path file): `from` and
    `to` are required `PackQualifiedReference` query parameters. It removes the edge of the unordered pair only from
    `registered.graph.yaml` and never rewrites an author file (decision `DM-01M6TQ62HXYF362GAFS2BZ3PGF`): `204`; `404
    relationship_not_found`; `409 relationship_not_registered` for an edge authored in a source shard or
    `drg/fragment.yaml`, for a derived relation, and for a pair held in both kinds of document (decision
    `DM-01M6TR1KDAJ64GMJD6AYNZKT59`); `412`, `428`, and `422` as above (FR-006).
  - Schemas `PackRegistration` (`packId`, `descriptorPackId`, `name`, `version`, `level` as `org` or `project`,
    `revision`, `outcome`, `source`, `notices` of FR-027: `floorAdded`, `floorRemoved`, `locksAdded`, `locksRemoved`,
    and `_links`), `PackRegistrationStatus` (`registrationId`, `state`, `packId`, `acceptedAt`, `finishedAt`, `result`,
    `problem`, `_links`, with a `pack` link on a succeeded status: the new relation `pack` of the hypermedia guide) and `PackSource` (one closed object: `kind` as `https_archive`, `git` or `filesystem`,
    `packId`, and by kind `url`, `ref`, `commit`, `sha256` or `path`). `PackSource.url` is `^https://\S+$`: a pattern is
    matched against the whole value (by the bean validation of the service's generated model and by any generated client
    that anchors it), so it accepts an `https://` URL of one or more characters without whitespace; the host, the port,
    the user information and the address checks are the fetcher's, and a refused one ends the registration `refused`
    with `422 pack_source_refused`. `PackSourceRecord.url`, the `source` a status and a `PackRegistration` carry, is the
    URL without its query, its fragment and its user information (the status holds a registration for an hour, and a
    presigned URL's signature must not be readable by every holder of it; the audit locator cuts them too);
    `source.path`, `source.ref` and `source.kind` are as sent.
  - `CharterRefusalCode` gains `pack_version_conflict`, `artifact_referenced`, `relationship_not_found`,
    `relationship_not_registered`, `registration_not_found`, `pack_source_refused`, `pack_source_unavailable`,
    `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`,
    `registration_timed_out` (`504`, the planner's proposal, now confirmed) and `pack_storage_full` (`503`), each
    `raised: false` until its operation is served. `x-refusal.operations` of `getPackRegistration`,
    `deletePackArtifact` and `deletePackRelationship` give the composition codes `422` in place of `500`.
  - New contract scenarios, bound by the serving work packages (WP16 to WP19), none of them `@WIP`:
    `scenarios/registration/whole-pack-registration.feature` (replaces the `@WIP` `registration/register-pack.feature`)
    and `scenarios/pack-removal.feature`.

- A pack declares its mutability levels in a policy file (FR-022, ADR-0063; pack-schema change, no API schema change).
  `pack.yaml` gains the optional `mutability_policy`, a pack-relative path that the `pack-descriptor` schema admits only
  as `policy.yaml` in this version. The new closed `contracts/packs/schemas/policy.schema.json` validates the file:
  `schema_version` (the integer 1), `kind_defaults` (an artifact kind token to `open`, `extendable` or `locked`) and
  `artifacts` (a bare URN to a level); role locks and pins (ADR-0030) are reserved, so a `roles` or `pins` key makes
  the file invalid. A pack whose `pack.yaml` names a policy file that is missing or invalid is refused
  `pack_unreadable`, on the offering and on any closure that holds it (no new refusal code). A registration is judged at
  the highest level along the written pack's composition closure, and a refusal names the pack that set the level. The
  file is digested in the source fingerprint after the manifest, so a lock edit moves the `ETag`; a pack without it
  keeps its fingerprint. No pack ships a policy file yet, so no answer changes.

- Scenarios (issue #374): in `offering.feature`, "A configured pack holding an agent profile that fails its schema
  refuses the offering" (`PR-CHARTER-SHARING-001`) and "A configured pack holding an asset whose path leaves assets/
  refuses the offering" (`PR-CHARTER-SHARING-004`, an escaping path whose target exists) pin the 500
  `pack_unreadable` the service already answers, with the exact detail (`pattern violated at /roles/0`;
  `payload assets/../../../.kittify/charter.yaml does not exist`, reworded by #382: see the sidecar-path-escape entry). `@WIP` without `@contract`, so not run: in the
  new `profiles.feature`, "An agent profile enhancement that would drop fields of the profile it enhances is
  refused" (`PR-CHARTER-REFUSAL-001`, issue #380), with its provisional target answer; and the pack registration
  and distribution scenarios of ADR-0005 and ADR-0019 (issue #43), moved from `src/test/resources/features/` into
  `registration/`, `trust/`, `integrity/` and `distribution/`, decided and not built. No operation, schema or
  refusal code changes; core's seam: none.

### Changed

- The `Offering` tag's description covers the model's kind taxonomy as well as the configured packs, and the lint
  rules `fingerprint-on-verified-reads` and `fingerprint-only-on-verified-reads` exempt `/offering/kinds`, which reads
  no pack file (`docs/development/rest-resource-conventions.md`; planted fixture
  `fingerprint-only-on-verified-reads.kinds.yaml`). No answer changes.
- `deletePackRelationship`'s `422` description names `substep_delegation_unscoped` at `/to`, the refusal of removing
  the last `scope` edge a substep's delegation candidate needs, which the service already answers; a scenario of
  `pack-removal.feature` pins it, with a control that removes a `scope` edge no substep needs. The composing-read
  scenario of `mission-types.feature` for an unlinked step also asserts that the detail names the step. No answer
  changes.

- The `packId` path parameter declares the grammar the service enforces, `^[a-z0-9][a-z0-9-]{0,62}$`, and its
  `maxLength` is 63, not 64; an id outside it was already a `400` without a code, and is now one by the contract itself.
  The asset and skill item and payload paths, in a pack and under an activation, take a new `artifactId` parameter
  (`parameters/AssetOrSkillId.yaml`) with the kebab grammar of `Asset.id` and `Skill.id`,
  `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters: `…/asset/Logo` is a `400`, not a `404`.

- The effective-read entity tag (`EffectiveETag`) moves to version `v3` (#536), because existing reads answer other
  bodies for the same inputs: `getActivation` (and the `Activation` of `createActivation` and `replaceActivation`)
  links its writes and effective reads, and a skill item of `listEffectiveArtifacts`, `getEffectiveGraph`,
  `getEffectiveActionContext` and an agent profile's governance (`getEffectiveAgentProfile`) now carries its
  `artifact` and `effective-artifact` links. A client holding a tag from before gets a `200` with the new body, never
  a `304`.

- The detail of a `pack_unreadable` for a `pack.yaml` that cannot be read says why: its parse failure, or each schema
  violation, an undeclared key named (issue #431). It was `pack.yaml of <pack> cannot be read` alone, so a descriptor
  that still declares `active_charter` was refused without naming the key. A registration's `pack_unreadable`
  violation at `pack` carries the same detail. The codes and statuses do not change.

- The description of `prompt_slot_missing` (`CharterRefusalCode`) names a step enhancement in force, where it named an
  active or inactive one (OD-1 (B)), and the schema's paragraph that kept the code reserved for #438 now records that
  history (`2026-11-12-prompt-slot-missing-raised.md`).

- `preset_not_found` is also raised by `getPackPreset`: its description now reads "a preset a pack, or the
  activation's closure, does not hold", where it named only the activation's closure. No code is added and its status
  stays 404 (#98).

- The description of `override_not_permitted` (`CharterRefusalCode`) also covers an enhancement of a step whose
  mission type is locked (ADR-0066 decision 7's integrity bullet, OD-2: a step takes its mission type's level), beside
  an `overrides` edge onto a target that is not open; the status and the operations are unchanged, and no enum value is
  added (`2026-11-12-step-enhancement-compose-target-and-lock.md`).

- `getEffectiveMissionStep` answers `200` for a step that only a version the winning mission type replaced keeps,
  where it answered `404 action_not_found` (A7, DIV-243; research R8, operator ruling of 2026-11-12). One rule places
  a step: the winner of `mission_type:<missionType>` when that version declares the step, else, for a step the closure
  holds, the pack that holds the merged winner of `action:<missionType>/<step>` when its version declares it. A step
  placed there is read from that pack, with its prompt and guidelines, and its skeleton links name that pack's
  `getPackMissionSkeleton`. A step no version of the closure holds is still `404 action_not_found`, judged as
  `getEffectiveActionContext` judges it, now with that read's detail. A step the winning type declares is answered as
  before. No shape change.
- The `artifact` link of `getEffectiveActionContext` names the pack of the step's place by the same rule, so it and
  `effective-artifact` answer the same step; it named the pack that holds the mission type's version in force, which
  answered `404` for a step a replaced version keeps.
- `getEffectiveMissionStepContract` links `step` whenever `getEffectiveMissionStep` answers the step, where it did only
  when the winning mission type declares it.

- The templated item read answers a `mission_step_contract` as it answers every other kind: a pack whose aggregate or
  revision cannot be read after its `pack.yaml` was read is `404 pack_not_found`, where a step contract answered
  `500 pack_unreadable`. A pack with no readable `pack.yaml` is still `500 pack_unreadable` for every kind.

- A read that meets a pack in the middle of a replace answers the pack's last committed state (issue #490). The
  read takes no lock and does not wait for the replace. Before, it answered `404 pack_not_found` or
  `500 pack_unreadable`, or the pack listing waited up to 10 s for the replace. A read that the pack's directory
  moved under is run again. A read whose pack directory moves under it eight times in a row (one replace moves it
  twice, so about four replaces) answers `500 stale_input`, which the caller may retry. A pack directory that is
  empty, the reservation of a create in flight, is no pack: it is `404 pack_not_found` and is not listed. A replace
  whose second step fails puts the pack back as it was; a read that ran in between is run again over the restored
  pack, so it answers the last committed state, never what it saw while the pack was moved aside. A verified read whose load refuses while any pack is in the middle of a
  replace still answers `500 stale_input`, which the caller may retry, rather than the load's own refusal; whether it
  should is an open call for the operator, recorded in ADR-0073 (Risks).

- The `Procedure` schema states the id and text bounds the pack schema puts on a procedure (#489). Its `id` carries
  the pack schema's pattern and `maxLength: 64`. Its `title`, `description`, `entryCondition`, `exitCondition` and
  `notes` have `minLength: 1`, and so does the `title` of a step and of an anti-pattern. Every procedure the service
  serves already meets them. Core's seam: nothing.

- A 400 for a malformed request parameter stays a `Problem` without a code and now carries a `detail` that names where
  the parameter is (query, path or header), its name, the value it carried and what it accepts (issue #411): for
  example `The query parameter kind is "bogus", which is not one of tactic, directive, ... or mission_step_contract.`,
  `The query parameter pageSize is "0", which is less than 1.` and `The query parameter pageCursor is "***", which is
  not a cursor this list gave.` The value is quoted with its quotes, backslashes and control characters escaped and is
  cut after 64 code points. The pack writes (`packId`, `If-Match`, `If-None-Match`), the relationship removal (`from`,
  `to`) and the activation writes' precondition headers answer the same shape; two headers that exclude each other
  are named together. The wording is the service's own on every route: the activation routes and
  `getEffectiveAgentProfile` answered a validator's message before (`The request's depth must be less than or equal to
  1.`), and every other route a bare 400. The `Problem` schema's `detail` description states the rule; a client
  branches on the status, never on the detail. Scenarios: `artifacts.feature` pins the detail of an unknown, an
  unserved and a malformed kind filter, a bad cursor and a page size out of range or not a number;
  `effective-reads.feature` of the kind and state filters, a malformed cursor and a cursor of another set;
  `agent-profile-reads.feature`, `effective-agent-profile.feature` and `effective-artifact.feature` of a depth outside
  0 to 1; `activation-register-guards.feature` of a malformed `If-Match` and activation id;
  `registration/whole-pack-registration.feature` of a `packId` outside the grammar and both validators sent;
  `pack-removal.feature` of a malformed and a missing end; `mission-types.feature` of a mission type id too long or
  not a slug; `errors.feature` says a 4xx Problem under the API carries a detail only where the service writes one.
  Additive: a member the closed `Problem` already declared appears where it was absent; no operation changes.

- An excluded file is not refused at registration and is not held (#451, ADR-0065 D1 as amended 2026-11-11). A file
  that a `file-kinds` exclusion names (a `README.md` beside a mission type or its templates, `plan-field-declaration.yaml`,
  a hidden file under `assets/` or `templates/`) and any Markdown file that no rule models and no artifact claims as a
  companion (an author's `docs/notes.md`) are not pack content. The registration removes them from the staged tree
  before it computes the revision and commits, so the stored pack is the registered tree less those files, and a
  registration that differs from the stored pack only in such a file ends `unchanged`. Each file it does not hold is
  reported in the answer's `diagnostics` as a `file_not_held` warning (a new `DiagnosticCode`), whose `detail` names
  the file and the rule's reason: `docs/notes.md: not held (Author aid: Markdown that no files rule models (#451).
  Not pack content: not held, not served, not refused.)`. A file nothing models that is not Markdown (`notes.txt`)
  is still refused as `unmodelled`, and the retired `pack.md` and `PROVENANCE.md` are still refused as `retired`.
  Core's seam: a registration that used to answer `422 validation_failed` for such a file now answers a 2xx with
  warnings; a client that treated the absence of `diagnostics` as "the whole tree was stored" reads `file_not_held`
  to learn what was left out; the `DiagnosticCode` enum gains `file_not_held`, so a generated client regenerates.

- Metadata only, no wire shape change. The `x-source` citations of `NodeKind`, `Relation`, `DiagnosticCode` and
  `Diagnostic` now name the Java sources that exist (`service/kernel` and `service/packs`) instead of the
  `service/charter-domain` module that no longer does.

- The service holds step enhancements and their `*.prompt-fragment.md` companions (#428; Refs #424), so a step
  enhancement is selectable like any other artifact:
  - `PUT /activations/{activationId}/selections/step_enhancement/{artifactId}` answers `200` for a held enhancement
    instead of `404 artifact_not_found` (scenario "A held step enhancement is selectable", `step-enhancements.feature`).
  - A preset's `activated_step_enhancements` ids are kept, no longer reported stale, when the activation is written
    again (`activation-presets.feature`).
  - A registration of a pack that carries step enhancements is accepted, where it was refused `422` as an unmodelled
    file (`paths/packs_packId.yaml`), and the whole-pack write keeps the enhancement files and the fragments beside
    them (scenario "A pack that carries a step enhancement and its fragment registers", `step-enhancements.feature`).
  - Now that the service holds step enhancements, a selected one is in force exactly when the mission type of the step
    it enhances is in force (rule E6 as amended by OD-6, `2026-11-09-c-step-enhancer-binding.md`).
  - `PUT /packs/{packId}/relationships` accepts the one `enhances` edge of a held step enhancement, where it answered
    `409 registration_conflict`.

- `prompt_slot_malformed` and `prompt_slot_not_empty` are raised (#424; Refs #428), as violation codes of a refused
  registration under the amended definition of `raised`: a registration lists the fault at the step prompt's
  pack-relative file inside `422 validation_failed`, `{pointer: <prompt file>, code: prompt_slot_malformed}` or
  `{pointer: <prompt file>, code: prompt_slot_not_empty}`, and a verified read of a closure holding such a pack
  answers `500 pack_unreadable` naming the file. The reserved paragraph of the description now keeps only
  `prompt_slot_missing` reserved, raised by the phase-2 mission (#438). `errors.feature`'s raised-code table lists
  both codes, and `prompt-slots.feature` covers them at registration and on a read.
- `DiagnosticCode`'s description no longer says the `prompt_slot_not_empty` warning is reserved for #424: it is listed
  in the `diagnostics` of the result of a succeeded registration for a pack whose descriptor declares no
  `schema_version`; the effective-prompt read's `diagnostics` arrive with the phase-2 mission (#438).

- `prompt_fragment_invalid` is raised (#428; Refs #424). A pack registration refuses an invalid step enhancement or
  prompt fragment with `422 validation_failed`, listing every fault as a violation `{pointer: <pack-relative file>,
  code: prompt_fragment_invalid}`; a verified read of a closure that holds such a pack answers `500 pack_unreadable`
  naming the file. The code's description now reads "an invalid step enhancement or prompt fragment".
- The definition of `raised` in `x-refusal` (`CharterRefusalCode`) is amended: a code is also raised when it is
  raised as a violation code of a `422 validation_failed`. The declared `status` of such a code is kept and is read
  as the status of the read that meets the fault.
- `drg_schema_error` is raised by the same definition: a registration refuses a graph document that breaks its schema
  with it, as a violation of `422 validation_failed` (operator ruling OD-3 (a)). Only `pack_level_mismatch` stays
  unraised among the codes the earlier text listed. The raised-code table of `errors.feature` lists both codes.
- The shared texts say what a registration lists: `Problem.violations` also covers the `422 validation_failed` of a
  registration, one entry per fault of the pack, and `RefusalViolation.pointer` is the pack-relative path of the file at
  fault on such a violation. The reserved paragraph of the description now keeps only `prompt_slot_malformed`,
  `prompt_slot_not_empty` and `prompt_slot_missing` reserved, and no longer suggests `artifact_locked` for an ADR-0066
  refusal.
- The `step-enhancement` schema's description (contract text, no property changes) lists the rules that answer
  `prompt_fragment_invalid`: an enhancement whose id differs from its file stem is refused by the store's stem rule as a
  schema violation, not `prompt_fragment_invalid`, as a slot that breaks `<namespace>:<name>` and
  `prompt_fragments: []` (`minItems`) are; a repeated slot, a missing, shared or orphan companion, the edge rules, the
  directory, a repeated id and an absent `prompt_fragments` key are `prompt_fragment_invalid`. It also says that guards
  and emits are fields (`add_guards`, `add_lifecycle_guards`, `emits`) built under #434, not edges. The `path`
  description no longer binds a companion to its enhancement's id (one companion per entry, any stem matching the
  pattern, each named by exactly one entry), and `file-kinds.yaml` carries the same wording.

- A selected step enhancer is grounded exactly when the mission type of the step it enhances is in force (rule E6, as
  amended by operator ruling OD-6). It applies to a `step_enhancement` with exactly one `enhances` edge, to an `action:`
  URN, and to no other shape: an enhancer with two targets, with an action among other targets, or of another kind
  whose target is an action keeps the enhancer rule as before. When the step is one the closure holds (a pack defines
  `mission_type:M` and declares `action:M/s`), the enhancer is bound to `mission_type:M`: it is in force while `M` is,
  and `EffectiveArtifact.reason` is `selected` (or `required`, `locked`); with `M` out of force its reason is
  `mission_type_not_in_force` and the `SkippedReport` lists it in `missionTypeNotInForce`. When no pack holds the step,
  the edge stays and the enhancer is out of force with the reason `enhanced_target_not_in_force`, as any enhancer whose
  target is not in force. An enhancer selected next to its mission type, with no step in the selection (the selection
  cannot name an action), was listed in `enhancedTargetNotInForce` on a read and warned about with
  `enhanced_target_not_in_force` in `ActivationChange.warnings` on a write; it is now in force, and neither carries it.
  `requiredNotInForce` lists a required or locked step enhancer with the reason `mission_type_not_in_force`. No
  operation, schema or refusal code changes.

- The write cascade follows only held steps (operator ruling OD-9, DIV-242): a mission type reaches `action:M/s` when a
  pack of the closure defines `mission_type:M` and declares `action:M/s`. An `action:M/s` declared where no pack defines
  `mission_type:M` is no longer selected, so `applied` and `selected` can differ from before on the five per-artifact
  writes (`selections`, `exclusions` and `presets`). No operation, schema or response shape changes. The effective
  reads' tag domain moved to v2, so an `ETag` a client cached before misses once and the read answers `200`.

- `agent_profile` is a served kind of the generic pack-artifact surface (#407, #96). Three wordings change, and every
  change is additive.
  - **The served kinds.** The pack's artifact list (`listPackArtifacts`), its `?kind=` filter, the references read
    (`listArtifactReferences`) and `deletePackArtifact` serve `agent_profile` beside tactic, directive, styleguide, paradigm
    and toolguide. The descriptions that listed five kinds (`ArtifactKindToken`, the `kind` path parameter,
    `ArtifactSummary.kind`, the item, list, references and delete paths, and `kind_not_found` in `CharterRefusalCode`) now
    name six. The templated item read `getPackArtifact` never carries a profile body: the literal operation
    `getPackAgentProfile` answers `agent_profile`, and an item link to a profile is that read. A list that held no
    profile lists the same items as before; a pack with profiles lists them last, after its toolguides, and a client
    already skips a kind it does not know.
  - **`artifact_not_found` on the pack path.** A profile the pack does not hold is `404 artifact_not_found`, on
    `getPackAgentProfile`, `listArtifactReferences` and `deletePackArtifact`. The rule from here on: a kind served after
    the first five uses `artifact_not_found`, not a code of its own. The five keep `tactic_not_found` and the rest. The
    code was already 404, and the activation set already raised it; `CharterRefusalCode` states the wider scope in its
    description and adds no value.
  - **The profile's `ETag`.** `getPackAgentProfile` answers the pack revision as its `ETag`, as a directive's read does,
    and `deletePackArtifact` of a profile takes it as `If-Match` (428 without it, 412 when stale) and answers 204 with the
    revision after the removal. A profile is refused as a directive is: 409 `artifact_referenced` while another artifact,
    pack, preset or override still refers to it, `governance_policy_violation` when an ancestor of the pack locks it,
    `registration_conflict` when the pack's author-written graph documents name it, and 422 when the closure it leaves
    does not compose.

- The activation write cascade walks the merged edges of the closure (FR-038, rule E20): a cascading select or
  deactivate never follows an edge that a version replaced by a later level declared, and where no layer override
  exists the answer is unchanged. A write over a closure whose layer merge refuses answers `500`, with `pack_unreadable`
  (an override the mutability level forbids, an `overrides` cycle, an unstable answer or an `overrides` edge that ends at
  its own source) or `pack_sibling_conflict` (a step contest no `overrides` entry settles), as the effective reads
  do. No operation, schema or response changes: the sub-resource writes already declare the `500` of
  `OfferingUnreadable`, which names the composition codes (`pack_sibling_conflict` among them), `pack_unreadable`,
  `stale_input` and `internal_error`. The preconditions of a write are judged before the packs are read, except that an `If-Match` that is a closure tag reads the closure to judge the `412`.

- `ActivationChange.warnings` (the `ActivationWarning` codes `enhanced_target_not_in_force` and `overridden`) warn only
  for the artifacts the write puts in: a selection, or an item a preset adds to `selected`. A removal (an unselect), a
  deny entry and a reactivation never warn, though the artifact's reason at read may be the enhancer's or the
  override's, which E9 ranks before the deny row; a write answered such a warning for them before. A skipped artifact is
  listed once: a required or locked artifact the enhancer rule or the binding rule removes from force is in
  `requiredNotInForce` only, no longer in `enhancedTargetNotInForce` or `missionTypeNotInForce` as well, and
  `ActivationSummary.skipped` counts it once. No operation or schema changes.

- Scenarios of the activation set that compared getActivation's `ETag` with the revision now state the declared
  contract (decisions `01M6Y11E55YP5TG1EJ1MDKCFRP` and `01M6Y3GZFRC5WEQQ7G6GT0YMW3`): the `ETag` of `getActivation` is
  the closure tag, so a read that stored nothing is checked on `/revision` and on the `ETag` differing from the
  revision, a conditional read sends the tag it was answered, the revision as `If-None-Match` answers `200`, and a
  write that carries the tag of a closure a pack change has since altered is `412`. Twenty-eight scenarios of
  `activation-cascade-depth`, `activation-kind-model`, `activation-presets`, `activation-register`,
  `activation-register-guards`, `activation-ruling-changes` and `activation-selections` changed their expectation, and
  `layer-merge` and `effective-reads` lost their `@WIP` tags (the glue is bound). Six steps were added to the
  vocabulary (`scenarios/README.md`), and `I remember the field` takes a JSON pointer. No operation, schema or response changes.

- Descriptions of the activation set say what PR B built, with no change of any operation, schema or response:
  `ActivationSummary.required` and `EffectiveArtifact.required` count what a preset requires or a pack locks, and a
  locked artifact is in force unless it is an enhancer with no target in force or is bound to a mission type not in force
  (E9) instead of "always in force"; the `400` sentences of the writes read "neither `*` nor one quoted sha256 tag (the
  revision or the `getActivation` tag)" (operator decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`) and a stray `,.` is gone;
  `Charter-Source-Fingerprint` says the effective artifact list and the skipped report send it on their `200` and that the
  graph and context reads, still reserved, will; `EffectiveETag` names the reads that carry it.

- `notices.activationsAffected` of a pack registration names an activation whose effective set the registration
  changes, which includes a change of the version in force of an artifact that stays in force: the pack that holds it,
  that pack's level, or its mutability level. It named only an activation whose set of artifacts in force differed
  before. No operation or schema changes.

- The `202` of `registerPackFromArchive` and `registerPackFromSource` says its body is the status `pending`, except that a
  job the deadline (or a shutdown) ended between its slot and its start answers `202` with that end, `refused` with
  `registration_timed_out` for the deadline (issue #418, F2). Text only: the status was always a `PackRegistrationStatus`,
  and no status or code is added. `POST /packs` now answers that end as `PUT` already did.

- `registerPackFromArchive` (`PUT /packs/{packId}`) reads its body as a stream and takes its registration slot when the body
  is spooled, not before it is read (issue #418, A3, A4): a slow upload no longer holds one of the two running and eight
  waiting places while it arrives. A full queue (`503` with `Retry-After`) and a stale `If-Match` are still answered before
  the body is read, and are judged again when it is spooled. The `90` second deadline of a registration starts then. No
  status or code is added.
- `registerPackFromArchive` refuses with the declared `503` and `Retry-After` when ten uploads are open already, counted
  apart from the queue (issue #418, F1), and its admission free-space check counts the declared bytes of the uploads
  already open. No status or code is added.
- `registerPackFromArchive` can answer `503 pack_storage_full` synchronously, before the body is read, when the staging or
  the pack volume lacks the room for the declared length (issue #418, A6). The `503` was declared; the code was a refused
  status's. `pack_storage_full` now means less than the per-job peak is free, about three times the 32 MiB archive bound
  (the archive, the tree it expands to and the copy the commit makes), where it was twice the bound on the pack volume only.

- `deletePackRelationship` gates the removed edge as a `RELATIONSHIP` write on its source, as a whole-pack replace
  that drops the edge is judged (DM-01M6TR1KDAJ64GMJD6AYNZKT59 item 4): a `409 governance_policy_violation` when the
  mutability level of the pack that owns the source does not permit it. The `409` was already listed for the
  operation (for `relationship_not_registered`); its description now names the second code, and the code needs no
  new status. The `from` and `to` descriptions say that a pack-qualified reference matches only an edge end that
  names the same pack (with the edges `A` to `base::X` and `A` to `other::X`, naming `base::X` removes one edge), and
  that a reference that names no pack matches by its URN, as before.

- `PackRegistrationNotices` says in its description that notices name activatable kinds only: an entry of another kind
  (a template, an asset, a guard, a lifecycle) is left out of the lists, as it already was, and the service now logs it
  at WARN with its URN. No schema change. Widening the kinds is tracked in issue #417.

- `Link` no longer says that a pack write is never `DELETE`. Its descriptions name the two removals of PR A,
  `deletePackArtifact` and `deletePackRelationship`, beside the activation `DELETE`; no other pack write is a `DELETE`,
  and no link uses `PATCH`. A description change only: the `method` pattern is unchanged, and no link advertises either
  removal (decision DM-01M6TVMGH94ZF07KSWSCANEASV).

- `registerPackFromSource` (`POST /packs`) is built for the `git` source kind (FR-004, NFR-005, NFR-011, NFR-012, C-008;
  PR A of the layer merge mission): it fetches one ref of a public repository over https with the host's `git` binary,
  never as a work tree, and registers the tree it holds as `PUT /packs/{packId}` does. A `git` source no longer answers
  `503` for being unbuilt; it answers `503 pack_source_unavailable` only when `charter.pack-sources.git.enabled` is off or
  the system `git` is missing or older than 2.50.1, which leaves the other kinds working. The URL is `https`, port 443 or
  none, on `charter.pack-sources.git.allowed-hosts`, with no user information, query or fragment; the ref is a commit id
  or a `refs/heads/` or `refs/tags/` name; the host must resolve to global addresses only, and git is pinned to the
  address the service checked. A redirect is `redirect_refused`; a ref of another form or that names nothing is `bad_ref`;
  a named `commit` that is not the one the ref resolved to is `commit_mismatch`; a link, a submodule or any entry mode but
  a file or a directory in the tree is a `422 validation_failed` naming the entry, as for an archive. A successful result
  carries `source` with `kind`, `url`, `ref` and `commit`, the commit the ref resolved to.
- `registerPackFromSource` answers `428` for a `git` source whose ref is a branch and that names no `commit`, when the
  request carries neither `If-Match` nor `If-None-Match: *`; a tag, a commit id and a branch with a `commit` need neither.

- `pack_source_refused` has two more reasons in its fixed set, `bad_ref` (a git ref that is not a commit id or a
  `refs/heads/` or `refs/tags/` name of the grammar, or that names nothing in the repository) and `commit_mismatch` (the
  commit the ref resolved to is not the commit the request named), for the git source of `registerPackFromSource`
  (FR-004, NFR-012, WP18). The description of `CharterRefusalCode` and of the operation list them with the others.

- `deletePackArtifact` and `deletePackRelationship` answer `503 pack_source_unavailable` while
  `charter.registration.enabled` is off, which is the default, before the pack, the path or `If-Match` is read
  (ADR-0065 D13). Both operations are first built in this release, so no client of an earlier release sees a change;
  the `503` is declared on both paths.

- Wording only, no behaviour change. The `CharterRefusalCode` text names the twelve operations that answer the pack
  composition codes as `422` (it named four); the `getPack` description says that a built-in pack is `404 pack_not_found`;
  the `deletePackRelationship` description states its order of checks (the pack, then `If-Match`, then the pair), which
  differs from `deletePackArtifact`, whose path is judged before `If-Match`.

- The wording of the composition refusals says that a preset item or policy lock naming a held artifact only by case
  is `pack_reference_artifact_missing`, and that `putPackRelationship` also answers `422` for such a preset or lock
  mismatch until the pack is fixed, the detail naming the pack. No behaviour changes.

- A preset item whose id is outside the id grammar (`^[A-Za-z0-9][A-Za-z0-9_/.-]*$`, the characters a `policy.yaml`
  key allows) refuses the pack as `500 pack_unreadable`, naming the preset file. Spelled as a URN
  (`directive:DIRECTIVE_001`), with another case on the kind (`DIRECTIVE:directive_001`), with whitespace
  (`DIRECTIVE_001` with a trailing space) or pack-qualified (`alpha-pack::DIRECTIVE_001`), an item matched no held id: a must-have item
  put nothing on the floor and `PUT /activations/{id}` of a deny entry on it answered `201`, and a non-must-have
  `::` item made `GET /offering` answer `500 internal_error`. The pack's ids are unchanged; only the preset item
  spelling is refused. No pack of the pinned open-packs refuses.

- `required` is reworded from "a required artifact is always active" to the rule of the layer model: a governing
  preset requires it or a pack locks it; a locked one is always active; a required one is active unless an active
  override of it satisfies the requirement, or it cannot stand in force (a required enhancer with no target in force,
  or a required artifact bound to a mission type not in force, decisions `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` and
  `DM-01M6RFGFW3S0YJ222QMHD2W11B`). It is never satisfied by an enhancement. `ActivationSummary.required` and `locked`
  follow ("in force or satisfied"), and `ActivationSummary.skipped` counts the entries across every bucket of the
  skipped report.
- `SkippedReport.skippedByScope` says it is always empty at read; `SkippedDeactivated` says that at read `viaRequires`
  is `false` and `requiredBy` is `[]`. `EffectiveArtifact.kind` takes the merged kind enum, so the list shows mission
  types.
- `EffectiveArtifactList.governanceFingerprint` and `ActivationSkippedReport.governanceFingerprint` are the
  `charter-closure/v1` digest of the packs of the activation's closure alone (their configured id and level, and their
  files). They are not `ServiceInfo.sourceFingerprint` and not the `Charter-Source-Fingerprint` header, which digest the
  whole source; a change to a pack outside the closure leaves them unchanged.
- `Activation` and `getActivation` say that `denyNotApplied` is judged against the packs of the closure and that the `ETag`
  covers the revision and the closure's fingerprint (see the Breaking entry).
- The relaxed write rule (IC-13, operator ruling Q9: a deny entry on a required artifact that an active override
  satisfies is accepted) is not part of this change; it ships with PR C. The write still refuses a deny entry on a
  required or locked artifact (409), and this fragment declares nothing for the relaxed rule.
- `If-Match` on an activation write takes the quoted revision or the current `getActivation` tag (see the Breaking entry); `EffectiveETag` is reworded to cover the closure's packs and `getActivation`.

- `includeDeactivated` clears only the deny entries of what the cascade reaches and selects (B5, decision
  `DM-01M6RA9XQSH3E07C7VCPCVD6R4`): a lineage ancestor the walk passes through, which it never selects, keeps its deny
  entry. It used to be cleared as the walk passed. `SelectionRequest.includeDeactivated` and `selectActivationArtifact`
  say so; `activation-selections.feature` ("includeDeactivated passes through a denied ancestor inside the scope and
  keeps its deny entry") and `activation-ruling-changes.feature` pin it.
- `replaceActivation` is judged against the document it replaces (A4, decision `DM-01M6RA7XC5HC8915GZT2R3H83W`), so a
  GET then PUT of the same body never fails without a pack change: the floor is judged on the deny entries the body
  adds only, so a deny entry the replaced document held is kept (`200`) when a pack now requires or locks it, where it
  was `409`; a new one is still `409`. The id dropping and the pin dropping are under `### Breaking`.
- A pack's mutability policy locks only the artifacts of the pack and of the packs it builds on (`depends_on` and
  `parent_pack`, transitively) (A15, decision `DM-01M6RA97220XBR7RR447FE2JXD`, rule C22): a `kind_defaults` entry no
  longer locks the artifacts of an unrelated pack that shares the closure, so a deny entry on one is accepted where it
  was `409 artifact_locked`.
- A pack's own policy never refuses its own write (B8, decision `DM-01M6RAA6Y7NVVGKVKQV05GZYES`, C25 aligned with
  C21): the per-artifact registrations (`PUT`/`POST /packs/{packId}/artifacts/<kind>…`) of an artifact the pack's own
  policy locks succeed (`200`/`201`) where they were `409 governance_policy_violation`. The policy still binds every
  pack built on the pack.
- The required floor holds only the must-have items a pack of the closure holds (A10, decision
  `DM-01M6RA8MG55V8C2V9PXWDTRNQS`): an unheld must-have item leaves the floor and is reported (by PR B's reads,
  `requiredNotHeld`) instead of being put in force; a held item of the same preset stays required.
- Two packs that `enhances` one artifact compose (A14, decision `DM-01M6RA92BS6GGCYTRCFQDYYY5S`): an `enhances` edge
  is additive and contests nothing, so sibling enhancers are no `pack_sibling_conflict`; a settlement names exactly one
  winner. The sibling-contest scenarios of `composition.feature` use `overrides` edges, the change that contests an
  artifact; `activation-ruling-changes.feature` pins two sibling enhancers composing.
- `ArtifactIdPath` says that a mission type's path id is normalized as every kind's is (its full URN or another case
  names the declared id): it said that a mission type's id is matched exactly, as the document's `missionTypes` was.
- New `activation-ruling-changes.feature` (`@contract`, SC-005) pins one counter-example per ruling above, each with a
  control in the same answer; its two A10 scenarios stay `@WIP`: "an artifact another pack's preset names cannot be deleted" for PR A (WP19) and
  "the unheld must-have item is in requiredNotHeld" for PR B (WP22), each removed by its own WP.
  `activation-kind-model.feature` loses its `@WIP` tag. The scenarios that pinned the old answers are rewritten, not
  deleted: in `activation-register.feature` the version pin refused on `PUT`, the stale id refused on write and the
  re-deny of a now locked or required artifact; in `activation-selections.feature` the denied ancestor cleared by
  `includeDeactivated`, the exact match of a mission type path id and the `422` deactivate of a mission type; and the
  scenarios that sent `schemaVersion: 1`, `activatedKinds` or `missionTypes` in `activation-register.feature`,
  `activation-register-guards.feature`, `activation-selections.feature` and `activation-presets.feature` (the step
  `Given the project activates the pack(s)` of `composition.feature` stores version 2 now); the reads of an emptied
  `selected.<kind>` expect it absent, its canonical form (FR-042).
- `ActivationInvalid` says that a create or replace can also answer `422 validation_failed` (violation
  `removed_member`, when `activatedKinds` or `missionTypes` is sent) and names the depth violation codes
  `below_minimum` and `above_maximum`; the new example `activation-removed-member` shows the refusal, and
  `CharterRefusalCode` says that a document carrying a removed member is `validation_failed`, not `activation_invalid`.

- `deletePackArtifact` (`DELETE /packs/{packId}/artifacts/{kind}/{artifactId}`) and `deletePackRelationship`
  (`DELETE /packs/{packId}/relationships`) are built (ADR-0065 D4; FR-005, FR-006, FR-007, FR-036; PR A of the layer merge
  mission): they lose `x-reserved` and their `501`, and the two `501` pins of
  `scenarios/registration/reserved-registration.feature` go with them. With `POST /packs` built as well
  (`2026-11-07-a-pack-source-served.md`), no operation of the `pack-registration` set is reserved, and
  `reserved-registration.feature` is deleted. Both are
  synchronous, run under the registry lock and then the pack's lock, and answer `204` with the pack's new revision as the
  `ETag`; `If-Match` is required (`428` without it, `412` with a stale one, judged under the locks). The artifact
  `DELETE` removes the artifact, its companion files, its outgoing edges from `registered.graph.yaml` and the preset
  items of the pack that name it, and deletes a preset key left empty (every preset list needs an item); it answers `404`
  by code (`pack_not_found`, `kind_not_found`, or the not-found code of the kind), `409 artifact_referenced` while another
  artifact or pack refers to it (judged against the tree after the removal; another pack's preset item and descriptor
  `overrides` entry count, the data-only fields of ADR-0064 D11 do not), `409 governance_policy_violation` (the
  `REMOVE` operation, only when an ancestor of the pack holds and locks the id), and `409 registration_conflict` when a
  source shard or `drg/fragment.yaml` the author wrote declares the artifact's node or an edge from it, since a `DELETE`
  never rewrites an author file. The relationship `DELETE` removes the edge of the unordered pair only from
  `registered.graph.yaml`: `404 relationship_not_found` when no graph document and no derived relation joins the pair,
  `409 relationship_not_registered` when the pair is held outside the registered shard, or in both. Both are strict
  (decision DM-01M6TQ67C9ZTNGZB9CY28JS54F): a removal after which a closure the pack joins no longer composes, or breaks a
  relation rule, is `422` with the composition or relation code, naming the packs, and writes nothing.
- `CharterRefusalCode`: `relationship_not_found` and `relationship_not_registered` are `raised: true`; the `x-refusal`
  text no longer says they stay `raised: false`, and `scenarios/errors.feature` lists them among the codes an operation
  raises.

- `registerPackFromArchive` (`PUT /packs/{packId}`), `getPack` (`GET /packs/{packId}`) and `getPackRegistration`
  (`GET /pack-registrations/{registrationId}`) are built (ADR-0065, FR-001, FR-024, FR-037, FR-039; PR A of the layer
  merge mission): they lose `x-reserved` and their `501`, and the three `501` pins of
  `scenarios/registration/reserved-registration.feature` go with them. `POST /packs` and the two `DELETE`s are built by
  their own changes (`2026-11-07-a-pack-source-served.md`, `2026-11-08-a-git-source-served.md`,
  `2026-11-07-a-removal-served.md`), so at the end of PR A no operation of the `pack-registration` set is reserved. The `PUT` answers `202` with a pending `PackRegistrationStatus` and `Location` after the synchronous
  checks (the `packId` grammar `400` before the body is read, both validators `400`, a declared length over 32 MiB
  `413` before a byte is read and a chunked body counted while it streams, a media type that does not match the leading
  bytes `415`, a stale `If-Match` or an `If-None-Match: *` over an existing pack `412`, registration switched off
  `503 pack_source_unavailable`, a full queue `503` with `Retry-After`); the job runs the flow behind it, and a registry
  lock that is not free in time ends the job `refused` with `503`.
- `CharterRefusalCode`: `registration_not_found`, `pack_source_unavailable`, `registration_timed_out` and
  `pack_storage_full` are `raised: true`. The `x-refusal` text no longer says that a reserved operation's codes stay
  `raised: false`: a code's flag follows what the service names (`pack_source_refused` and the two `DELETE` codes are
  raised by the changes that build their operations).
- A `Problem` with `packs` and `violations` together: `artifact_referenced` lists the referring packs (at most 100) as well
  as the referring artifacts.
- Where the status answers a pack that is built in: `getPack` answers `404 pack_not_found` for a built-in pack, which is
  read-only and has no registration summary (`PackRegistration.level` is `org` or `project`).

- `CharterRefusalCode`: `pack_version_conflict` is `raised: true` (ADR-0065 D2, FR-002). The whole-pack registration
  service refuses a replace that carries the stored `pack_version` with another revision with it, `409`, and the
  registration job answers it as the `problem` of a `refused` status.
  The scenario `errors.feature` lists it among the codes an operation raises.

- `registerPackFromSource` (`POST /packs`) is built for the `https_archive` and `filesystem` source kinds (ADR-0065 D10,
  FR-004, NFR-004, NFR-005, NFR-012; PR A of the layer merge mission): it loses `x-reserved` and its `501`, and the `501`
  pin of `scenarios/registration/reserved-registration.feature` goes with them (the `DELETE`s are built by
  `2026-11-07-a-removal-served.md`). It
  answers `202` with a pending `PackRegistrationStatus` and a `Location` after the synchronous checks (registration and
  the kind's own flag switched on, the `PackSource` schema, both validators, a stale `If-Match`, a full queue); the fetch
  runs in the job, outside every lock, and a successful result carries `source` (`kind` and `url` or `path`, as sent).
  A source the service refuses ends `refused` with `422 pack_source_refused`, the detail of whose problem starts with one
  reason of a fixed set (`host_not_allowed`, `address_not_global`, `url_refused`, `redirect_refused`, `wrong_media_type`,
  `too_large`, `digest_mismatch`, `name_mismatch`, `source_malformed`, `path_refused`), never raw upstream text; an
  upstream failure ends `refused` with a plain `502` and an upstream timeout with a plain `504`. A `filesystem` source
  refuses every path it will not read, a missing one, a link or a file with another hard link, with the same
  `path_refused`. A `git` source is served by the git source change of WP18 (`2026-11-08-a-git-source-served.md`).
- `CharterRefusalCode`: `pack_source_refused` is `raised: true`. `errors.feature` lists it among the codes an operation
  raises.

- `parameters/IfMatch.yaml` covers a pack revision as well as an activation: it is also the quoted strong `sha256:` tag
  of a pack's revision. It stays `required: false`; the two `DELETE`s of a pack state their `428` in their
  descriptions, and the whole-pack registrations leave it optional (without it the write is last-writer-wins on the
  whole pack). There is no `PackIfMatch` parameter.
- `CharterRefusalCode`: the text of `kind_not_found` names `deletePackArtifact` beside the generic item read, and the
  dispatch text makes `registration_timed_out` and `pack_storage_full` retryable beside `internal_error` and
  `stale_input`.
- The contract lint rule `error-responses-are-problems` exempts `202`, the status answer of a registration, beside
  the `200`, `201` and `204` successes.

- `CharterRefusalCode`: `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override` and
  `override_cycle` are `raised: true` (IC-15, FR-025; A6, A8, A11, A12, C28). The registration impact check composes every
  closure a registered pack joins, the closure of each pack that builds on it and the root set of each stored activation
  that holds it, and refuses the registration, `422`, naming the closure's roots and the packs involved, when the closure
  holds an `overrides` edge onto a target that is not `open` (judged with each policy scoped to what its pack and the packs
  it builds on hold, the source's own pack left out), an `enhances` cycle or an enhancer with no route to an artifact that
  enhances nothing (an enhancement of an artifact nobody holds has none), an artifact that both enhances and overrides one
  target, an `overrides` edge whose target resolves to its own source, or a cycle of `overrides` edges. A composition code
  (`pack_dependency_unknown`, `pack_dependency_out_of_range`, `pack_sibling_conflict` and the rest) is refused the same
  way, with the closure's roots named in the detail and among `packs`, as the `problem` of a `refused` registration
  status. The per-artifact writes and the two `DELETE`s answer them synchronously (`2026-11-07-a-per-artifact-impact-check.md`,
  `2026-11-07-a-removal-served.md`).
  The scenario `errors.feature` lists the five among the codes an operation raises.

- `CharterRefusalCode`: `artifact_referenced` is `raised: true` (ADR-0065 D4, FR-036). The whole-pack registration
  service refuses a replace that removes an artifact which an edge, a preset item or a descriptor `overrides` entry of
  another pack still names, `409`, before any compose, as the `problem` of a `refused` registration status. Its `packs`
  member lists the referring packs (at most 100) and its `violations` name every referrer, by its pack and artifact, in
  the pointer. The artifact `DELETE` raises it too (`2026-11-07-a-removal-served.md`).
  The scenario `errors.feature` lists it among the codes an operation raises.

- `ActivationInvalid` names the pack pinned to a version the service does not hold: a `422 activation_invalid` with the
  violation `version_not_held` at `/packs/{i}/version`, whose `detail` names the held and the pinned version
  ("The pack 'alpha' is held at 1.0.0, not 2.0.0."). A new example, `activation-version-not-held`, shows it, and the
  scenario that pins the refusal pins the detail too. The behavior is unchanged (operator ruling of 2026-11-06).

- `replaceActivation` and `deleteActivation` say that a storage `500` may follow a committed change: the new document
  is stored, or the old one is gone, when the device refuses to force the directory. Read before retrying. The service
  now fails loud on a refused directory force instead of ignoring every failure to force; only a directory that cannot
  be opened (Windows) or is gone is tolerated.

- `RequiringPack.via` is populated on the `409 artifact_required` and `409 artifact_locked` refusals of an
  activation write: the `parent_pack` chain from the pack the activation names up to the requiring pack, that
  pack excluded. It stays `[]` when the named pack declares the item itself, and for a pack reached over
  `depends_on` only. An activation write also evaluates its preconditions before it judges the document: a stale
  `If-Match`, or `If-None-Match: *` on a stored id, is a `412` whatever the document names (RFC 9110 §13.2.2). A
  body the strict reader refuses as it reads it (an unknown member, a `null`, a duplicate key) is still `422` first.
- The `governance_policy_violation` text of `CharterRefusalCode` and its `errors.feature` scenario say that each
  pack's `policy.yaml` is read (ADR-0063) and that no open pack ships one yet; they said that no policy file is read.
- `replaceActivation`, `deleteActivation`, `PreconditionFailed` and `UnknownActivation` say that an `If-Match` on an
  activation that does not exist is `412`, not `404`: no stored revision matches it (RFC 9110 §13.1.1). Two
  scenarios in `activation-register-guards.feature` pin it. The behavior is unchanged.
- The shared `Problem` caps `violations` at `maxItems: 100`. A refusal of more parts lists the first 100 and ends
  `detail` with "N more violations are not listed." A 1 MiB body of nulls or unknown ids used to answer about
  200,000 entries. Core's seam reads at most 100 entries and takes the count from `detail`.
- `getActivation` and `deleteActivation` declare their `500` as the shared `Problem` response: their only `500` is
  `internal_error`, a failure of the storage, not the offering's composition codes `OfferingUnreadable` lists.
  `listActivationPacks`' `pack_unreadable` also covers a pack the stored document names that is no longer configured.

- A composition is refused `500 pack_reference_artifact_missing` when a preset item or a `policy.yaml` lock names an
  artifact the closure holds only under another case (`directive_001` where the closure holds `DIRECTIVE_001`). Ids
  are matched exactly, so the mention governed nothing: a must-have item put nothing on the floor, and a deny entry
  that should have been refused answered `201`. The detail names the mentioning pack, the holding pack and both
  spellings. The existing code is used because it is the nearest: a pack's reference to an artifact the closure does
  not hold as written. A mention of an id no pack of the closure holds is still served, and a lock on it changes no
  write. No pack of the pinned open-packs refuses.

- `parent_pack` is an ordering and a cycle edge, as `depends_on` is (operator ruling of 2026-11-06). The pack a child
  extends merges before the child, whatever the pack ids sort as, so `GET /offering` and the closure read serve the
  parent first and the child's preset takes the place of its parent's. A `parent_pack` cycle, a pack that names itself
  included, is refused `500 pack_dependency_cycle`; it was served before. Core's seam: none, `parent_pack` was
  already in the descriptor. `getOffering`, `listActivationPacks` and `ActivationPackList` say the order puts dependencies
  and `parent_pack` parents before the packs that need them.
- Two packs of one level that hold one preset id and are not related by `parent_pack` are refused
  `500 pack_sibling_conflict`; the last holder in load order used to win. A child and a pack on its `parent_pack` line
  may hold one id, the child winning, and a preset id held at different levels is still a layer override (ADR-0053
  decision 6, ADR-0059 decision 3). `getOffering` says so; `composition.feature` and `activation-register.feature` pin it.

- `PackPin.version` (and `ActivationConfig.schema.json`) declare `pattern: ^\s*\S.*$`: a pinned version that is only
  blanks is invalid. The service answered it `500`; it is now `422 activation_invalid` with the violation
  `invalid_value` at `/packs/{i}/version`. The corpus case `17-pack-version-blank` pins it. Core's seam: a blank
  `version` was never a version; send none to take the held one.

- A write may spell an artifact id as the declared id, as its full URN of the list's kind (`directive:DIRECTIVE_001`)
  or in another case, and the service stores the declared id (ADR-0020 D4, operator ruling of 2026-11-06). `selected`
  and `deactivated` both normalize, so every spelling gives one document and one revision. A spelling that matches two
  declared ids differing only by case is `422 activation_invalid` with the new violation code `ambiguous_artifact`; an
  unknown id, or a URN of another kind than the list's, stays `unknown_artifact`. `ArtifactId` widens its `pattern` to
  admit the optional `kind:` prefix (in `ActivationConfig.schema.json` too), and its text, `ArtifactIdPath` and
  `ActivationConfig` describe the rule. The stored and answered document is unchanged: it holds declared ids.

- The case-insensitive match of an artifact id spelling covers the URN's kind prefix too: `DIRECTIVE:directive_001`
  normalizes to the declared id. `ArtifactId` (and `ActivationConfig.schema.json`) widen the prefix of their `pattern`
  to `[A-Za-z][A-Za-z_]*`; the id after the colon keeps its grammar. `maxLength: 128` covers the whole string, so the
  description now says a declared id longer than 128 less the kind and the colon (118 for `directive:`) cannot be
  written as a URN, and is written as itself.

- The per-artifact writes and `applyActivationPreset` declare `400` (the shared `Problem`): an `If-Match` that is
  neither `*` nor one quoted revision breaks its pattern and is answered `400` before anything else is judged (J10).
  Their precondition order is stated in all five descriptions, `unselectActivationArtifact` included: the `400`, then
  `428`, `404 activation_not_found`, `412`, and only then a `422`, the body's and the path's kind included.
  A fault of a `SelectionRequest`, `ExclusionRequest` or `PresetApplication` body (a member the schema does not
  declare, a `null`, a value of the wrong type, a `cascade` token outside `CascadeKind`, an empty `cascade`, a body
  that is not JSON or is missing) is `422 activation_invalid` after the preconditions, never before them; a `cascade`
  token outside the kinds is the violation `unknown_kind` at `/cascade/<i>`. The wire shapes are unchanged.
- No scalar of an activation body is coerced into another: `1`, `0`, `"true"` or `""` where a boolean is declared
  (`includeDeactivated`, `force`), `"1"`, `1.0` or `true` where an integer is (`schemaVersion`), and a number or a
  boolean where a string is (a `cascade` token, an id, a pack pin) are a value of the wrong type,
  `422 activation_invalid` with the violation `unreadable_body` at the value. A member declared twice in one object
  is the same violation at that member, never the last one winning. This holds for every activation write: after the
  preconditions for the per-artifact and preset writes, and for `createActivation` and `replaceActivation`, whose
  document such a value was read into before.
- `409 ArtifactPolicyRefused` stays declared on `deactivateActivationArtifact` alone, the one write that can add a deny
  entry. A deny entry stored before a pack upgrade put its artifact on the required or locked floor is tolerated
  (ADR-0059 D4, "a stored one is tolerated but not applied"): a select, unselect, re-activate or preset of another id
  answers as if it were not there, and keeps it. `createActivation` and `replaceActivation` still judge the whole
  document they are sent.
- `SelectionRequest.includeDeactivated` says what it clears (operator ruling of 2026-11-06, ADR-0059 amendment A3):
  only the deny entries the walk passes through within the cascade's kinds, that is what it reaches within them, each
  lineage ancestor it passes whose kind is among them (cleared, never selected) and the target. A denied artifact
  outside the cascade's kinds keeps its deny entry: the walk stops there and reports it in `skippedDeactivated`, with
  what lies only behind it in `unreachedBehindDeactivated`, as without `includeDeactivated`. This reverses the
  earlier reading that cleared out-of-scope deny entries the walk went through; three scenarios of
  `activation-selections.feature` pin it.
- A cascading deactivate never removes an artifact on the required or locked floor; a selected one the cascade reached
  is kept, and reported in `skippedShared` when another active artifact still references it, named by that artifact.
  One no other active artifact reaches is not reported: it was never a candidate, and `referencedBy` never names the
  kept artifact itself. `SkippedDeactivated.requiredBy` and `SharedSkip.referencedBy` name only an
  artifact an `ArtifactReference` can name: a template, an asset, an unselected intermediate or a lineage ancestor on
  the way leads back to the artifact that put it in the walk (for an ancestor, its descendant), as
  `ArtifactReference`'s description and ADR-0059 D2.4 say; among the nearest, the first in code-point order.
- Stale ids are dropped on write (operator ruling of 2026-11-06, ADR-0059 amendment A5). Every per-artifact and preset
  write first removes from the stored document each id the activation's closure no longer declares (a pack upgrade
  removed it), in `selected`, `deactivated` and `missionTypes`, and reports them in the new optional
  `ActivationChange.dropped`, an array of `ArtifactReference`, present only when the write dropped one. A dropped id
  is a change: a write that only drops stale ids is not a no-op and answers a new revision. `applied` and `cleared`
  report the write's own change, measured after the drop, so a dropped id appears only in `dropped`. Before, such a
  document made every per-artifact and preset write `422 activation_invalid` until a full `replaceActivation`.
- A preset whose stale member the stored document already names (`applyActivationPreset`) drops the member and puts
  it back, so the computed document equals the stored one; a write that dropped an id is still not a no-op, so the
  document is judged and the write is `422 activation_invalid` (`unknown_artifact`, pointer `""`), storing nothing.
- An unselect (`unselectActivationArtifact`) or a re-activate (`reactivateActivationArtifact`) whose path id the
  closure no longer holds proceeds when the stored list it removes the id from still names it (`selected`, or
  `missionTypes` for kind `mission_type`; `deactivated`): the id is dropped with every other stale id, listed in
  `dropped` only, and the write answers `200` with a new revision. Before, it was `404 artifact_not_found`. An id
  in neither the closure nor that stored list is still `404 artifact_not_found` (`mission_type_not_found`) and drops
  nothing, judged after the preconditions and every `422` as before; a select or a deactivate of an id the closure
  no longer holds is still `404` too.
- Unchanged: an unknown pack or a pinned version the closure does not hold is still `422 activation_invalid`
  (`unknown_pack`, `version_not_held`), which only a `replaceActivation` recovers; and `createActivation` and
  `replaceActivation` still refuse an unknown id in the body they are sent with `422`. No refusal code changes, so
  `CharterRefusalCode` and its `x-refusal` map are unchanged; five scenarios of `activation-selections.feature` pin
  the drop.

- The four per-artifact writes of an activation are served (#291, ADR-0020 D6, ADR-0059 D2): `selectActivationArtifact`
  and `unselectActivationArtifact` (`PUT`/`DELETE …/selections/{kind}/{artifactId}`), and
  `deactivateActivationArtifact` and `reactivateActivationArtifact` (`PUT`/`DELETE …/exclusions/{kind}/{artifactId}`)
  lose `x-reserved` and their `501`, so the activation set reserves five operations, not nine. Each requires `If-Match`
  (`428` without it, `412` when stale, both before any content is judged) and answers an `ActivationChange` with the
  new revision as its `ETag`; a write that changes nothing answers `200` with the same revision and an empty `applied`
  and `cleared`, and stores nothing. A path id the activation's closure does not hold is `404 artifact_not_found`
  (`mission_type_not_found` for a select or unselect of kind `mission_type`), and a path kind outside the operation's
  enum (`mission_type` on the exclusions path included) is `422 activation_invalid` with a violation at `/kind`; the
  two `404` codes become raised by an operation, so `artifact_not_found` and `mission_type_not_found` are `raised:
  true` in `x-refusal`. The writes' `404` is the new `ActivationArtifactNotFound` response, which names only codes
  they raise. `ActivationChange.skipped` reports the write-time walk only; what a read leaves out is reported by the
  effective reads (`getActivationSkippedReport`, a later change), as the schema's description now says. The write's path has no
  `GET`, so an `ActivationChange` carries no `self`: its `up` link names the activation (`Links` and the example say
  so).
- A cascading deactivate walks the `specializes_from` lineage exactly as a cascading select does (operator ruling of
  2026-11-06, ADR-0059 amendment A2): what a select reached through a profile's ancestor is a candidate of the
  deactivate of that profile, so a cascading select and a cascading deactivate round-trip. An artifact another active
  artifact needs is still kept and reported in `skippedShared`. `ExclusionRequest.cascade` and
  `deactivateActivationArtifact` say so; two scenarios of `activation-selections.feature` pin it.
- A cascade walks `enhances` as one of its relations, in its declared direction (operator ruling of 2026-11-06,
  ADR-0059 amendment A4): `E enhances T` runs from E to T, as `requires` and `scope` do. A cascading select of E
  selects T; one of T selects no enhancer; a cascading deactivate of E unselects T unless another active artifact
  needs it, which is then kept and named in `skippedShared`. The relation set is core's five and `enhances`; four
  scenarios of `activation-selections.feature` pin it. No write walks `enhances` any other way: there is no
  write-time `enhances` pull, so `includeDeactivated` clears no deny entry the walk does not reach, and a cascading
  deactivate keeps an artifact an active enhancer enhances because the shared walk crosses `enhances` to it, not by a
  rule of its own. `SelectionRequest.includeDeactivated`, `ActivationChange` and the two write descriptions say so.
- The path id of the four per-artifact writes is read as an id of the activation document is (`ArtifactIdPath`,
  ADR-0020 D4, operator ruling of 2026-11-06): the declared id, its full URN of the path's kind or another case of
  either names the declared id, which is what the write stores and reports. An unselect or re-activate reads a stale
  id the same way against the stored list. A spelling that matches two declared ids differing only by case is
  `422 activation_invalid` (`ambiguous_artifact`, at `/artifactId`); a URN of another kind is an unknown id, `404`.
  A `mission_type` path id is not normalized: it is matched exactly, as a document's `missionTypes` is, so another
  case or its URN is `404 mission_type_not_found`.

- `getActivation`, `replaceActivation`, `deleteActivation` and `listActivationPacks` declare `400` as the shared
  `Problem` response, as `listPackArtifacts` does. The service already answered it: an `activationId` outside its
  pattern, an `If-Match` that is neither a quoted revision nor `*`, and (on `replaceActivation`) both validators on one
  request or an `If-None-Match` other than `*`. Two scenarios in `activation-register-guards.feature` pin the first two.
  The behavior is unchanged.

- `applyActivationPreset` (`PUT /activations/{activationId}/presets/{presetId}`) is served (ADR-0059 D6, ADR-0020 D6,
  FR-016) and loses `x-reserved` and its `501`. Each key the preset lists replaces that key of the document: each
  `selected.<kind>` it lists becomes exactly its members, and `activatedKinds` and `missionTypes` are replaced when it
  lists them; a key it does not list is left as it is. A preset lists a key when its file holds at least one member
  for it, so an empty list counts as not listed (the packs read an absent list and an empty one alike). Without
  `force` a member the document denies is not selected and its deny entry stays; with `force: true` the deny entries
  of the preset's members are cleared and reported in `cleared`. `applied` lists every id the write added to or
  removed from `selected`, `deactivated` or `missionTypes`, so a replaced key's dropped ids are listed too; a kind is
  never an entry. The preset is resolved in the activation's own closure (the last of two packs holding one preset id
  in merge order wins): a preset no pack of it holds, one held only outside it included, is `404 preset_not_found`,
  which becomes `raised: true` in `x-refusal`. The operation's `404` is the new `ActivationPresetNotFound` response,
  which names only `activation_not_found` and `preset_not_found`. A preset member the closure does not hold (a preset a pack upgrade left
  stale) is `422 activation_invalid` with an `unknown_artifact` violation at the whole request (pointer `""`), since
  the write has no body list to point into; nothing is stored. The write requires `If-Match` (`428`, `412`) and
  answers an `ActivationChange` with the new revision as its `ETag`; a write that changes nothing answers `200` with
  the same revision.
- This closes contract B: only the four effective reads (`listEffectiveArtifacts`, `getActivationSkippedReport`,
  `getEffectiveGraph`, `getEffectiveActionContext`) stay reserved, and none of them takes a request body, so the
  scenario that pinned the `501` before an unbindable body is bound is retired (a test now fails if a reserved
  operation declares a `requestBody`).
- `ActivationChange.skipped` reports the write-time skips only; what a read leaves out is reported by the effective
  reads, which the effective-reads mission builds.

- `ActivationPackList.packs` no longer says it is never empty. A document that names packs has at least one, but a
  document without packs builds on every configured pack, and a service with no configured pack answers `[]`. The
  schema was always `minItems`-free; its description is corrected, and says the closure follows `parent_pack` as well
  as `depends_on`. The behavior is unchanged.

- An activation body that states a member twice in one object is `422 activation_invalid` with the violation
  `duplicate_member` at the member's pointer; the last occurrence used to win without a word. A scalar of another type
  than the one declared (`"1"` or `1.0` for `schemaVersion`, a number or a boolean in an id list, a number for a pinned
  version) is `422 activation_invalid` with `unreadable_body` at its pointer; the service used to coerce it.
  `ActivationInvalid` says so, and `activation-register.feature` pins both, and now also the pointer and code of
  `unknown_member`. Core's seam: send each member once, and a JSON value of the declared type.

- The `403` description (`responses/Forbidden.yaml` and the `charterWorktreeToken` security scheme) covers a
  containerized deployment (issue #268, ADR-0041 decision 6). There, the `Host` name is compared with the
  deployment's configured list of host names instead of the loopback names. The port is still not judged, and an
  HTTP/1.0 request without a `Host` header is refused (an HTTP/1.1 one is the servlet container's 400, as before). The
  description no longer says every 403 comes from a browser. The status, the body and the headers are unchanged. Core's seam: none.
  A local daemon still accepts only the loopback names.

- `CharterRefusalCode`'s `x-refusal` entry for `governance_policy_violation` says `raised: true` (issue #293): the
  registration writes (the directive, paradigm, styleguide and toolguide writes and `registerRelationship`) refuse a
  write the mutability level of the pack that owns its target does not permit with it, 409 (ADR-0012). No policy file
  is read yet, so every target is open and no write is refused with it until one is; the schema description says so.
  The 409 descriptions of those writes name it beside `registration_conflict`, and the item writes, which raise no
  `registration_conflict` outside the toolguide's guide file, no longer promise a duplicate id. `drg_schema_error`
  stays `raised: false`: the whole-pack load raises it, and no operation reaches that load. The schema description
  now says what `raised` means: an operation of this version reaches the code that refuses with it. Scenarios: in
  `errors.feature`, the `@reserved` declaration pin "governance_policy_violation is reserved" becomes "is raised by
  the registration writes (#293)", and the raised-codes table gains `governance_policy_violation`. No enum value
  changes; core's seam changes nothing.

- Changed (lint): the new rule `no-content-only-on-delete` lets only a `delete` operation declare a `204` (issue
  #295). A removal is the one success without the resource (ADR-0020 D9); `no-content-has-no-body` already held a
  `204` to having no body, but let it sit on any method. The contract's one `204`, `deleteActivation`'s, is on a
  `delete`, so the contract does not change. Core's seam: none.

- The bundled contract core vendors (`charter-openapi.yaml`) keeps `components` instead of inlining every `$ref`
  (issue #294). It shrinks from 5,238,317 to 248,462 bytes, so a YAML reader with snakeyaml's default limit (3 MiB of
  code points) reads it again; the build's own readers drop the 64 MiB limit, and the lint's rule timeout falls from
  60 to 20 seconds. Each file the split contract references is one component named after the file (a
  schema's name is its `title`), in `schemas`, `responses`, `parameters`, `headers` or `examples`; every `$ref` in
  the bundle is `#/components/<section>/<name>`; `paths` holds the path items themselves. The API is unchanged:
  `oasdiff breaking` finds no change against the inlined bundle, and resolving every reference gives that bundle
  exactly; the `getOpenApiDocument` description says what the served document is. This replaces the 0.1.0 line
  above that the inlined bundle grows to about 5.2 MB and that core raises its YAML limit: no release carried that
  shape. Core's seam: a reader of the vendored file that assumed no `$ref` resolves the component references
  (an OpenAPI parser or generator does); a reader that raised the YAML limit for the inlined bundle may drop it.

### Folded in: the never-released 0.1.0-SNAPSHOT

No release was cut under `0.1.0-SNAPSHOT`, so its section is folded into this first release rather
than kept as a version of its own (operator ruling R1, 2026-11-16). Its text follows as it stood,
its subsections one level down.

The first contract version core will vendor, not yet released: an unreleased contract version
carries the `-SNAPSHOT` suffix. A first `0.1.0-SNAPSHOT` draft landed before the operator rulings
of 2026-10-18, which renumbered it `1.0.0-SNAPSHOT`. The operator ruling of 2026-10-20 (ADR-0022)
withdrew that label: the contract is published as 0.x while it iterates with core's generated
client, and 1.0.0 is minted once it is stable. The version is `0.1.0-SNAPSHOT` again. No release
was cut under either label. `Added` lists what this version holds that the first draft did not;
`Changed` lists what differs from that draft.

#### Breaking

The activation set, declared and reserved (mission activation-set, issue #125; ADR-0020; operator rulings of
2026-10-31). It lands inside this unreleased version, beside #126, by the operator's ruling, so `info.version` stays
`0.1.0-SNAPSHOT`. Breaking on the 0.x line, which ADR-0022 allows; no release carried the old shape. What core's seam
has to change is said per item.

- Added: 13 operations under the new tag `Activation`, each declared in full (request body, success statuses,
  refusals, conditional headers) and reserved: `x-reserved: {contractSet: activation, issue: "#125"}` and a `501`
  `Problem` with no code, which the service answers before it reads the request (ADR-0020 D8). They are
  `createActivation` (`POST /activations`); `replaceActivation`, `getActivation` and `deleteActivation`
  (`PUT`, `GET`, `DELETE /activations/{activationId}`); `selectActivationArtifact` and `unselectActivationArtifact`
  (`…/selections/{kind}/{artifactId}`); `deactivateActivationArtifact` and `reactivateActivationArtifact`
  (`…/exclusions/{kind}/{artifactId}`); `applyActivationPreset` (`…/presets/{presetId}`); and the effective reads
  `listEffectiveArtifacts` (`…/artifacts`, paged with the shared `PageInfo`), `getActivationSkippedReport`
  (`…/skipped`), `getEffectiveGraph` (`…/graph`) and `getEffectiveActionContext`
  (`…/mission-types/{missionType}/actions/{action}/context`). Core's seam: generate the `ActivationApi` client;
  treat `501` as "not built yet" and keep its in-process backend.
- Added: the activation document `ActivationConfig` (closed; `schemaVersion`, `packs` as `{packId, version?}` in
  merge order, `activatedKinds`, `missionTypes`, `selected`, `deactivated`, `languages`), canonical as JSON Schema
  2020-12 at `contracts/offering/schemas/ActivationConfig.schema.json` (ADR-0017's pattern), which this CHANGELOG
  versions too, and mirrored in `schemas/ActivationConfig.yaml`. Per-entry provenance, `contextActivations` and
  `appliedPresets` are left out by ruling. `selected` and `deactivated` hold one member per activatable kind (11).
  Core's seam: map `.kittify/charter/activation.yaml`, `config.yaml` and `charter.yaml` to and from it (content
  alignment, not file shape).
- Added: `Activation`, `ActivationSummary`, `ActivationChange`, `SkippedReport` (`skippedByScope`,
  `skippedDeactivated`, `unreachedBehindDeactivated`, `skippedShared`), `ActivationSkippedReport`,
  `EffectiveArtifactList`, `EffectiveArtifact`, `EffectiveGraph`, `EffectiveActionContext`, the request bodies
  `SelectionRequest`, `ExclusionRequest` and `PresetApplication`, their parts (`PackPin`, `SelectedArtifacts`,
  `DeactivatedArtifacts`, `ArtifactIdList`, `DeniedArtifactIdList`, `ArtifactId`, `ActivationId`, `LanguageToken`,
  `ActivatableKind`, `SelectableKind`, `CascadeKind`, `CascadeScope`, `ArtifactReference`, `SkippedDeactivated`,
  `SharedSkip`, `EffectiveGraphNode`, `EffectiveGraphEdge`, `EffectiveArtifactState`, `EffectiveArtifactReason`,
  `ArtifactPolicy`), the vendored `PageInfo` (now used) and the new `RequiringPack` and `RefusalViolation`, the
  shared hypermedia components `Links` and `Link`
  (ADR-0019 D3, shared with #126), and the conditional headers: `ETag` (the quoted revision, `sha256:` of the
  RFC 8785 serialization of the canonical document), an opaque `ETag` on the effective reads that changes with the
  revision or the offering, `If-Match`, `If-None-Match: *`, and `Location` on a `201`. The conditional writes
  declare `412` (a stale or failed precondition), every write with a body `415` (a body that is not JSON), and a
  sub-resource write without `If-Match` `428`, each a `Problem` with no code. Four questions stay open until the
  operations are built (ADR-0020, open questions 4 to 7): the validator of an `Activation` answer, a stored document
  an offering change made stale, what a preset sets, and conditional reads.
- Added: six refusal codes to `CharterRefusalCode`, each `raised: false`: `activation_not_found`,
  `artifact_not_found`, `preset_not_found` (404), `artifact_required`, `artifact_locked` (409) and
  `activation_invalid` (422). A closed enum gaining values is breaking for a generated client. Core's seam:
  regenerate; nothing raises them yet.
- Changed: the shared `Problem` gains two optional members, `requiredBy` (a list of `RequiringPack`, only on a 409
  `artifact_required`/`artifact_locked`) and `violations` (a list of `RefusalViolation`, only on a 422
  `activation_invalid`), because `CharterRefusal` extends the closed `Problem` (`_shared/VENDORED.md`). Every
  generated `Problem` gains them; no answer of this version carries them. Core's seam: regenerate.
- Changed: `mission_type_not_found` and `action_not_found` are reserved for the effective context of an activation
  again, not described as gone.
- Changed: the answer to a method the service does not support (`TRACE`) lists `DELETE` in `Allow`: the reserved
  activation set routes it.
- Changed: the bundled contract core vendors (`charter-openapi.yaml`, every `$ref` inlined) grows from about
  2.5 MB to about 5.2 MB. A YAML reader with snakeyaml's default limit (3 MiB of code points) refuses it; the
  build's own readers raise the limit. Core's seam: raise the limit where it parses the bundle, if it does.
- Lint: new rules `x-reserved-shape`, `reserved-operation-declares-501`, `built-operation-declares-no-501` and
  `no-content-has-no-body`; `error-responses-are-problems` admits `204` (ADR-0020 D9 supersedes ADR-0018 D10's
  "200 only"). The closure check exempts `_links`, a map of `Link`, as the one open map (ADR-0019 D3).
- Scenarios: `activations.feature` holds one `@contract @reserved` 501 pin per operation, a second for selecting a
  mission type, and one for a malformed body; `errors.feature` pins the six codes as declarations. The `@reserved` tag now has two forms, a declaration pin
  and a 501 pin (`scenarios/README.md`). The functional scenarios of `docs/architecture/offering-and-activation.md`
  stay proposed until the operations are built.

Toolguide guide over HTTP (mission procedure-antipattern-toolguide-round-trip, issue #194; operator rulings of
2026-10-28 and 2026-10-29). Breaking on the 0.x line, which ADR-0022 allows; no release carried the old shape.

- Removed: `guidePath` from `ToolguideRegistration` (the request of `registerPackToolguide` and `putPackToolguide`)
  and from `Toolguide` (the response of `listPackToolguides`, `getPackToolguide` and both registrations). The API
  no longer assumes a filesystem shared between client and service.
- Added: `ToolguideRegistration.guide` (the guide's Markdown) and `ToolguideRegistration.guideUri` (a string,
  `format: uri`; the schema carries no pattern, and the service enforces `https`, since a `pattern` on a field the
  generator types `URI` would be a server error once bean validation is on the classpath). A registration carries
  exactly one of them; the schema says so with a `oneOf` of two `required` branches. The generator this repository
  builds with ignores that `oneOf` (it emits two optional fields), so the service enforces the rule itself: both,
  neither, an empty `guideUri` or an explicit `null` (also beside a valid other field) is refused 422
  `validation_failed`, whatever a generated client lets through. `guide` declares `minLength: 1`: an empty `guide`
  breaks a constraint the schema declares and is refused 400 Bad Request (the contract's `default` problem,
  `{"title":"Bad Request","status":400}`) before the controller runs, since bean validation runs on the generated
  DTOs (ADR-0041 decision 2). `guideUri` declares no `minLength`, since the generator types it `URI` and a `@Size`
  on a `URI` would be a server error; its empty value stays a 422. A `guideUri` that is not a URI at all fails to
  deserialize and is a 400 plain `Problem`, like any unreadable body.
- Added: `maxItems: 256` on `ToolguideRegistration.commands`, `appliesToLanguages` and `references`; a longer list
  breaks a constraint the schema declares and is refused 400 Bad Request before the controller (ADR-0041 decision 2).
- Added: `Toolguide.guide` (required): the stored guide's Markdown, its H1 written from `title`. An incoming guide
  may omit the H1; one that has an H1 must have the title as it, or it is refused.
- Added refusals, all with existing codes (no new refusal code): 422 `validation_failed` for the exactly-one rule
  above, for an inline guide over the guide cap of UTF-8 (1 MiB by default, configurable via
  `charter.guide-fetch.max-size`; refused before it is parsed), for an inline guide holding an unpaired surrogate
  (refused, never stored as `?`), for a registration body over six times the guide cap plus 1 MiB (7 MiB by
  default; refused before it is read), for a toolguide id with a trailing or doubled hyphen (`a-`, `a--b`: the
  id is `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, the grammar of its guide file `<id>.guide.md`), for a title that cannot
  be written as the guide's H1 (for example one ending in a closing `#`), for a guide that is not a valid
  guide (frontmatter, text before the first H2, no `## Overview`, fixed blocks out of order, an H1 that differs from
  the title; the detail names the rule), and for a refused fetch of `guideUri` (the detail names the typed reason,
  for example `HOST_NOT_ALLOWED` or `TOO_LARGE`, and the host, never the URI's path or query); 409
  `registration_conflict` when the guide's file `<id>.guide.md` is already claimed by another artifact of the pack.
  The 409 and 422 response descriptions of both registration paths name them.
- Fetch posture of `guideUri`: the service fetches the guide once per registration (a PUT with the same URI fetches
  again), after the refusals that need no guide (an unknown pack, a stale `If-Match`, a POST naming an existing id,
  a write the policy refuses; none of them costs a fetch) and before it takes the pack's write lock, and reads the
  fetched text exactly like an inline guide. The fetch is direct, never through a proxy; HTTPS on port 443 only;
  the host must be exactly on the service's allowlist, which is empty by default, so every `guideUri` is refused
  until an operator allowlists its host; only global unicast addresses, checked at resolution and after every
  redirect. By default (configurable via `charter.guide-fetch.*`): at most 3 redirects, a 10-second deadline over
  the whole exchange and a 1 MiB cap, streamed, with a body whose `Content-Length` passes it refused unread. The
  answer must be `text/markdown`, `text/x-markdown` or `text/plain` (parameters such as `charset` allowed; an
  absent `Content-Type` is refused), with identity encoding and UTF-8 only; no credentials are forwarded. A
  deployment whose service reaches the guide host only through a proxy must send the guide as content.
- Migrating a client: stop sending `guidePath`. Send the guide file's Markdown as `guide`, or host it on an
  allowlisted HTTPS host and send `guideUri`; leave out the field you do not send. Read the guide from the
  response's `guide`, whose H1 is the title, instead of resolving `guidePath`. A pack renames each guide file to
  `<id>.guide.md` beside its toolguide.
- Scenarios: `toolguides.feature` registers with `guide` content instead of `guidePath` and reads the guide back
  (H1 added from the title, or kept when it equals the title); new scenarios cover an H1 that differs from the
  title, a guide without an overview, both, neither, null (alone or beside the other field) and an empty `guideUri`
  (an outline, 422), an empty `guide` (400 Bad Request, ADR-0041 decision 2), an inline guide over 1 MiB
  against one of exactly 1 MiB, a `guideUri` fetched, stored and fetched again on a later PUT, and a `guideUri`
  whose host is not allowlisted. `journeys/journey-05-remove-python.feature` registers its toolguide with `guide`
  content. Three steps are new (scenario README): `When I PUT "<path>" with the body, its "<field>" padded to <n>
  bytes:`, `Then the field "<field>" of the response body is the lines:` and `Given the guide host serves "<uri>"
  with:`; a binding answers the guide host itself and opens no socket. The scenario check now implements
  `maxItems`.

Gate-A read surface (mission gate-a-read-surface, #126, #175, #176; operator rulings of 2026-10-31). Breaking on
the 0.x line, which ADR-0022 allows; no release carried the old shape. A client discovers the API from its root
(`GET /`), reads the configured packs at `GET /offering`, and reads and writes a pack's artifacts of every served kind
in one tree, `/packs/{packId}/artifacts`. Every JSON success answer carries `_links`. The source fingerprint is
declared exactly where the service sends it, and the refusal examples name only codes the service raises. The
contract has 15 operations.

- Removed (FR-006), with no alias: every per-kind route and its operation.
  - The reads `listPackTactics` (`GET /packs/{packId}/tactics`), `getPackTactic`
    (`GET /packs/{packId}/tactics/{tacticId}`), `listPackDirectives` (`GET /packs/{packId}/directives`),
    `getPackDirective` (`GET /packs/{packId}/directives/{directiveId}`), `listPackStyleguides`
    (`GET /packs/{packId}/styleguides`), `getPackStyleguide` (`GET /packs/{packId}/styleguides/{styleguideId}`),
    `listPackParadigms` (`GET /packs/{packId}/paradigms`), `getPackParadigm`
    (`GET /packs/{packId}/paradigms/{paradigmId}`), `listPackToolguides` (`GET /packs/{packId}/toolguides`) and
    `getPackToolguide` (`GET /packs/{packId}/toolguides/{toolguideId}`). A pack's artifacts are read through
    `listPackArtifacts` and `getPackArtifact` only.
  - The eight writes at their per-kind paths (moved below, operationIds kept).
  - Each removed path, `/packs/{packId}/{tactics,directives,styleguides,paradigms,toolguides}` and the same with an
    id, answers the 404 of a path no operation defines: the shared `Problem` without a code, whatever the method and
    the body.
  - The tag `Tactics`, with its only operations; the parameter `TacticId` and the response `TacticNotFound`, which
    only the tactic reads referenced (`ArtifactNotFound` keeps the `tactic-not-found` example). `DirectiveNotFound`,
    `StyleguideNotFound`, `ParadigmNotFound` and `ToolguideNotFound` go too: the moved writes answer 404 only for
    an unknown pack, so their 404 is `PackNotFound` (`pack_not_found`); a PUT creates the artifact it does not find,
    and no write raises a kind's not-found code.
  - The declaration of `Charter-Source-Fingerprint` on every response that is not the 200 of a verified read (#175,
    ruling 4): on the refusals (401, 403, 404, 409, 422, 500), on the shared `default` `Problem`, on the 200 and 201
    of every write, and on the 200 and 304 of `getOpenApiDocument`. The service already sent it on none of them.
- Moved (FR-005, ruling 2): the eight writes, each keeping its operationId, tag, request body, `If-Match`, headers
  and refusals (404 `pack_not_found`, now as `PackNotFound`; 409 `registration_conflict`, 412, 422
  `validation_failed`, 500). The item parameter of every PUT is now `artifactId` (`parameters/ArtifactId.yaml`).
  Each 200 and 201 answers `Artifact` (below):
  - `registerPackDirective`: `POST /packs/{packId}/directives` → `POST /packs/{packId}/artifacts/directive`;
  - `putPackDirective`: `PUT /packs/{packId}/directives/{directiveId}` →
    `PUT /packs/{packId}/artifacts/directive/{artifactId}`;
  - `registerPackStyleguide`: `POST /packs/{packId}/styleguides` → `POST /packs/{packId}/artifacts/styleguide`;
  - `putPackStyleguide`: `PUT /packs/{packId}/styleguides/{styleguideId}` →
    `PUT /packs/{packId}/artifacts/styleguide/{artifactId}`;
  - `registerPackParadigm`: `POST /packs/{packId}/paradigms` → `POST /packs/{packId}/artifacts/paradigm`;
  - `putPackParadigm`: `PUT /packs/{packId}/paradigms/{paradigmId}` →
    `PUT /packs/{packId}/artifacts/paradigm/{artifactId}`;
  - `registerPackToolguide`: `POST /packs/{packId}/toolguides` → `POST /packs/{packId}/artifacts/toolguide`;
  - `putPackToolguide`: `PUT /packs/{packId}/toolguides/{toolguideId}` →
    `PUT /packs/{packId}/artifacts/toolguide/{artifactId}`.

  The literal write paths sit beside the templated `/packs/{packId}/artifacts/{kind}/{artifactId}`. A `GET` on a
  write collection (for example `/packs/{packId}/artifacts/directive`) and a `PUT` on the item of a kind without a
  write (a tactic) are a 405 `Problem` without a code, not `kind_not_found`. A 201's `Location` path is the answer's
  `_links.self.href`. `PUT /packs/{packId}/relationships` (`putPackRelationship`) stays where it is. Generic
  OpenAPI tooling matches the path item before the method, so a third-party validator or mock resolves
  `GET /packs/p/artifacts/directive/X` to the literal, PUT-only path item and refuses it; the service and this
  repository's conformance harness match the method first and answer it from `getPackArtifact` (ruling 2 stands).
- Added: operations.
  - `getApiRoot` (`GET /`, tag `Service`; FR-001, ADR-0019 D2 and D4). The root is `/api/v1/`, with the trailing
    slash. It answers `ApiRoot`, `_links` only: `self` (`/api/v1/`), `info`, `offering`, `artifacts` (the RFC 6570
    template `/api/v1/packs/{packId}/artifacts{?kind}`, `templated: true`) and `service-desc`
    (`/api/v1/openapi.yaml`). It reads no pack file, so its 200 declares no fingerprint; it answers 401, 403 and
    `default`, no 500. `/api/v1`, without the slash, is not mapped to the root: it answers the 404 of a path no
    operation defines.
  - `getOffering` (`GET /offering`, new tag `Offering`; FR-002, ADR-0020 D1), the configured packs in force. It
    answers `Offering`: `packs` (each an `OfferingPack`) and `_links.self`. The order is the merge order: by
    configured level (built-in, org, project), then by `packId` in code-point order. The level is the one storage
    serves the pack at: the `pack_level` its `pack.yaml` declares, `org` when it declares no valid one (ADR-0038);
    the offering never recomputes it from `pack.yaml`. Merging is not applied yet (#10, #11). The deferred offering reads
    (the action context, the merged graph, presets, mission types and the action index, `/offering/kinds`) and
    `GET /packs/{packId}` are not part of this version, and no link points to them (ruling 3).
  - `listPackArtifacts` (`GET /packs/{packId}/artifacts`, new tag `Artifacts`; FR-003). It answers `ArtifactPage`:
    `items` (each an `ArtifactSummary`), `page` (the shared `PageInfo`) and `_links`: `self` (the request with its
    query), `first` (without `pageCursor`) and `next` (only when `page.hasNextPage`). The order is the served-kind
    order (tactic, directive, styleguide, paradigm, toolguide), then by id in code-point order. `kind` filters on
    one served kind; `pageSize` is 1 to 200, 50 by default; `pageCursor` names the position the page starts
    strictly after, so a deleted anchor still pages on.
  - `getPackArtifact` (`GET /packs/{packId}/artifacts/{kind}/{artifactId}`, tag `Artifacts`; FR-004). It answers
    `Artifact`, with `_links.self` and `collection` (the pack's artifacts of that kind, `…/artifacts?kind=<kind>`).
    Its 200 declares an optional `ETag`, which the service sends for the writable kinds only.
- Added: schemas, parameters, responses and a header.
  - `Link` (closed: `href` required, an absolute path under `/api/v1` or an RFC 6570 template; `templated`;
    `method`, a string matching `^(GET|POST|PUT|DELETE)$`, no enum, where `DELETE` is for activations only (ADR-0020;
    ADR-0026 D2 rules out `DELETE` and `PATCH` for pack and artifact writes) and no link uses `PATCH`; `title`) and `Links` (an open map from a relation key
    to a `Link`), the hypermedia components of ADR-0019 D3.
  - `ArtifactKindToken`: the served kinds, `tactic`, `directive`, `styleguide`, `paradigm` and `toolguide`. It
    types the `kind` query parameter only, a request-side filter. Other kernel kinds (`procedure`, `agent_profile`
    and the rest) are not served.
  - `ArtifactSummary` (`kind`, `id`, `title`, `_links.self`; the title is a tactic's or a paradigm's name and the
    other kinds' title), `ArtifactPage` and `Artifact`: a closed
    envelope of `kind`, `id`, `packId`, `_links` and exactly one kind-named member (`tactic`, `directive`,
    `styleguide`, `paradigm` or `toolguide`) holding that kind's existing schema unchanged (ruling 1). The
    exactly-one rule is a `oneOf` of `required` branches, as for `ToolguideRegistration`; the generator ignores it,
    and the service fills the one member. `ArtifactSummary.kind` and `Artifact.kind` are open strings, the slug
    pattern of the `ArtifactKind` path parameter, not `ArtifactKindToken`: this version serves the five kinds, and
    a client tolerates a kind it does not know by skipping the item, so a generated client does not throw on a kind
    a later version serves (`glossary`, `procedure`; ADR-0019).
  - `ApiRoot`, `Offering` and `OfferingPack` (`packId`, the configured `level` as `PackLevel`, `name` and `version`
    from `pack.yaml`, all required, and `_links.artifacts`; no `self`, since `GET /packs/{packId}` is not built).
  - `RelationshipRegistered` (R-13), a response-only schema: `relationship`, the registered
    `RelationshipRegistration` unchanged, and `_links.artifacts`, the list of the pack's artifacts
    (`/api/v1/packs/{packId}/artifacts`). It has no `self` and the answer no `Location`: the relationships path has
    no `GET`.
  - The parameters `ArtifactKind` (a `Slug`, no enum), `ArtifactId` and `ArtifactKindFilter`; the response
    `ArtifactNotFound` (`pack_not_found`, then `kind_not_found`, then the kind's own not-found code).
  - The header `Link` (`headers/ServiceDescLink.yaml`), required on the 200 of `getServiceInfo` only:
    `</api/v1/openapi.yaml>; rel="service-desc"` (RFC 8631, the #208 hand-over). No other response declares or
    sends it.
  - The examples `ApiRoot.links`, `Offering.three-levels`, `ArtifactPage.first-page`, `Artifact.<kind>` for each
    served kind and `RelationshipRegistered.requires`, so every JSON success response has a linked example.
- Added: the refusal code `kind_not_found` (404, raised; FR-007, ruling 6b): the path names an artifact kind the
  service does not serve. The checks run in this order: a path kind that is not a slug (`Tactic`, `-tactic`) is a
  400 `Problem` without a code, before the pack is judged; an identifier that matches no configured pack is 404
  `pack_not_found`, so `GET /packs/nope/artifacts/bogus/x` is `pack_not_found`; then a slug that names no served
  kind is `kind_not_found`; then an id the pack does not hold is the kind's own not-found code (`tactic_not_found`,
  …), so no raised code is retired. An id the kind's identifier rules refuse (a lower-case directive id) is the
  kind's not-found, not a server error. An unknown `kind` filter, a malformed cursor, a cursor for another kind than
  the filter, and a `pageSize` out of range or not a number are each a 400 `Problem` without a code.
- Added: a required `_links` on every JSON success schema (FR-008): `ServiceInfo._links` holds `self`
  (`/api/v1/info`) and `service-desc`; the new schemas above carry theirs. The writes answer `Artifact` and the
  relationships PUT answers `RelationshipRegistered`, where they answered the kind's bare schema and the bare
  `RelationshipRegistration`. A client of the relationships PUT reads the edge at `relationship` now.
  `RelationshipRegistration`, the request body and the `relationships` items of the kinds, carries no `_links`.
- Added: `Charter-Source-Fingerprint` declared `required: true` on the 200 of every verified read, where it was
  optional (#175, ruling 4; FR-012): `getServiceInfo`, `getOffering`, `listPackArtifacts` and `getPackArtifact`.
  These four responses are the only ones that declare it.
- Added: a raised-code check, `checkRefusalExamples` in `:contracts:check` (FR-014, R-22). It reads the raised codes
  from `CharterRefusalCode`'s `x-refusal` map and fails a `CharterRefusal` example whose `code` is not raised, and
  a response description that names a code of the map that is not raised (#176).
- Changed (operator ruling of 2026-10-31, option A of the question the WP03 review raised): a configured pack whose
  `pack.yaml` is missing, unreadable or invalid is not a readable canonical pack. `getOffering`, `listPackArtifacts`
  and `getPackArtifact` refuse it with a 500 `pack_unreadable`, whatever kind and id the item names, as they do a
  configured pack whose directory is missing. `OfferingPack.name` and `OfferingPack.version` are therefore required.
  404 `pack_not_found` is answered only for an identifier that names no configured pack. The item judges
  `pack.yaml` and reads the artifact inside one verified read, so a `pack.yaml` that changes during it is
  `stale_input`, never a fingerprinted 200. The ruling supersedes plan research R-18's optional name and version.
- Changed: `OfferingUnreadable` (the 500 of every operation that reads the packs) carries the examples
  `pack-unreadable` and `stale-input`, and its description names only the codes the service raises with 500: `pack_unreadable`,
  `stale_input` and `internal_error`. It named `pack_level_mismatch` and `drg_schema_error`, which no operation
  raises (#176). The `stale-input` example reads "The inputs changed while the artifacts were read; read them
  again.", the detail of the generic list. The toolguide registrations' 409 no longer names the reserved
  `governance_policy_violation`.
- Changed (descriptions and citations only, no shape change): `Charter-Source-Fingerprint` names the four verified
  reads; `PackNotFound`, `ArtifactKind` and `getPackArtifact` describe the order of the checks above; `PackLevel`
  describes a declared or a configured level and cites `service/kernel/.../PackLevel.java` (its `x-source` named
  the retired `service/charter-domain/...`). The vendored `PageInfo` cites each property, and itself, with
  `x-derived` (`_shared/VENDORED.md`); the generator types `nextPageCursor` as a nullable string, so it is not
  narrowed. `Links`, `RelationshipRegistration` and the five kind schemas carry a top-level citation, which
  `resource-property-cited` reads where they are a property of another schema.
  `CharterRefusalCode`'s description follows the 2026-10-31 ruling (a configured pack whose `pack.yaml` is
  missing or invalid is `pack_unreadable` on the offering and every artifact read) and names the four verified
  reads; `info.summary` no longer calls the contract read-only.
- Changed (closure check, FR-010): the one exemption ADR-0019 D3 names. A schema reached as the property `_links`,
  typed `object` alone, whose `additionalProperties` is a schema titled `Link` that the checker proves closed, is
  closed. Every other map is still reported; fixtures pin the exemption and three lookalikes (another property name,
  another value title, an open `Link`).
- Changed (lint): `charter-headers-on-every-response` is narrowed to `Charter-Contract-Version` and
  `Charter-Worktree`; its `missing-fingerprint` fixture is now `missing-worktree`. Five rules are new, each with
  planted fixtures, and `clean.yaml` and every earlier fixture now carry `_links` and a linked example:
  - `fingerprint-on-verified-reads`: the 200 of every `GET` other than `/` and `/openapi.yaml` declares the
    fingerprint `required: true`;
  - `fingerprint-only-on-verified-reads`: no other response declares it, the root's and `HEAD`, `OPTIONS` and
    `TRACE` answers included;
  - `success-schemas-carry-links` (FR-008): every 2xx `application/json` schema requires `_links`;
  - `success-responses-have-link-examples`: every 2xx `application/json` response has an example, and every example
    carries `_links` at its top level;
  - `link-relations-are-known` (FR-011): every key of a `_links` in an example is a relation of the table in
    `docs/development/hypermedia-hateoas.md`. `RelationTableParityTest` (`contracts` tests) fails when the rule's
    list and the table differ. The table gains `service-desc`, `first` and `next`.
- Not yet linked: registration is not advertised by a link in this minor. No answer carries an `edit` link (R-24):
  a client reaches the writes through the paths above.
- Migrating a client (core's seam). Core does not consume the HTTP contract today: its seam is the in-process
  `charter.offering.api`, so no core code calls the removed routes. Core's generated client (#13, ADR-0021) will be
  generated from this version. It types `Links` and `Link`, starts at `GET /` and follows `_links` by relation name
  (ADR-0019's requests to core), ignoring a relation it does not know. It reads an artifact of any served kind
  through the `Artifact` envelope, from the member its `kind` names, and lists a pack's artifacts through
  `ArtifactPage`, following `next` or sending `pageCursor` until `page.hasNextPage` is false. It sends a
  registration to `/packs/{packId}/artifacts/<kind>[/{artifactId}]` and reads the registered artifact from the
  envelope. To replace one, it sends the item's `ETag` from `getPackArtifact` as the PUT's `If-Match`. It reads a
  registered relationship at `relationship`. It reads `Charter-Source-Fingerprint` only on the 200 of a verified
  read. A summary's or an artifact's `kind` is an open string: it skips an item whose kind it does not know, so a
  kind a later version serves is an additive change, not a breaking one.
- Scenarios (NFR-004). The JUnit count of `CharterContractSuite` (outline examples expanded) is 106 before the
  mission and 189 after. No scenario is removed or renamed; each one that called a per-kind route calls its
  successor on the artifact tree under the same name, so it is its own successor.
  - Migrated (every scenario of each): `directives.feature`, `styleguides.feature`, `paradigms.feature` and
    `toolguides.feature` write to `POST /packs/qa/artifacts/<kind>` and `PUT /packs/qa/artifacts/<kind>/<id>`, read
    from `GET /packs/qa/artifacts/<kind>/<id>`, and read fields in the envelope's member. "A duplicate id is a
    conflict on POST while a distinct id is created" of each kind also pins the exact envelope of the 201, its
    `ETag`, and that the path of `Location` is `_links.self.href`. `tactics.feature` lists with
    `GET /packs/qa/artifacts?kind=tactic` and reads `GET /packs/qa/artifacts/tactic/<id>`; the stale list's detail is
    the generic list's; its pack `qa` gains a `pack.yaml`. `relationships.feature` (both scenarios),
    `service.feature` ("A refusal carries the contract version and the worktree, not the fingerprint":
    `GET /packs/no-such-pack/artifacts`), `journeys/journey-01` ("The starter pack's directives can be read"),
    `journey-02` (both runnable scenarios), `journey-03` ("The tactics a recipe can draw on are readable"; its pack
    gains a `pack.yaml`), `journey-04`, `journey-05` and `journey-06` (every runnable scenario): the same paths.
  - Changed expectations: `relationships.feature`'s "A relationship from a mission type of the pack is registered"
    pins the exact `RelationshipRegistered` body and that it has no `self` link, instead of the field `from`.
    `errors.feature`'s "The refusal codes the contract marks raised are exactly those an operation raises" lists
    `kind_not_found` and says the generic item raises the kinds' not-found codes. `service.feature`'s "When the
    packs cannot be read the refusal carries no fingerprint but still carries the other headers" also pins that the
    500 has no `Link` header.
  - New in `tactics.feature`: "One tactic of a pack whose directory is missing is refused with pack_unreadable
    (#176)" (FR-015). New in `service.feature`: "The info answer links to itself and to the contract document, and
    says so in a Link header".
  - New `artifacts.feature`: "A tactic is read in the envelope, with no ETag" and the same for a directive, a
    styleguide, a paradigm and a toolguide (with an ETag); "A list filtered by a served kind answers that kind's
    summaries (the positive control of the kind filter)"; "A kind filter the service does not know is a 400 Problem
    without a code"; "A summary's title is the name of a tactic or a paradigm and the title of the other kinds"; "A
    list without a filter is in the served-kind order, then by id in code-point order"; "An empty pack answers an
    empty page"; "A kind the pack holds none of answers an empty page"; "A page of exactly pageSize items is the
    last page: no next link and no trailing empty page"; "Pages of one item cross the kind boundary and serve every
    artifact exactly once"; "A next link keeps the kind filter and the page size, and its page keeps first without
    the cursor"; "The anchor of a cursor deleted between two pages still pages on from its position"; "A list whose
    inputs change while it is answered is refused with stale_input" and the same for an item; and the outlines "A
    bad page cursor is a 400 Problem without a code" (another kind, not base64url, a kind not served, no slash), "A
    page size out of range or not a number is a 400 Problem without a code" (0, 201, `abc`), "An unknown pack is
    pack_not_found, on the list and on the item" (four paths), "A path kind the service does not serve is
    kind_not_found" (`procedure`, `bogus`), "An unknown id of a served kind is that kind's not-found refusal" (five
    kinds), "A configured pack without pack.yaml is pack_unreadable, on the list and on the item" (the list, the
    list filtered by `tactic`, the item of an existing tactic, of a missing one, and of an unserved kind) and "A
    path kind that is not a slug is a 400 Problem without a code, before the pack is judged" (`Tactic`, `-tactic`,
    and `Tactic` on an unknown pack).
  - New `removed-routes.feature`: the outlines "A removed per-kind read answers 404 without a code" (10 paths) and
    "A removed per-kind write answers 404 without a code, whatever its body" (8 method and path pairs, each with a
    body the successor accepts, and the kind's list still empty after it), and the scenarios "A GET on a write path
    is a 405 Problem without a code" and "A PUT on the item of a kind that has no write is a 405 Problem without a
    code, not kind_not_found".
  - New `root.feature`: "The root answers its links and carries no fingerprint" (the exact body, no `Link` header);
    "The info link of the root answers the service info", "The offering link of the root answers the offering", "The
    service-desc link of the root answers the contract document"; "The artifacts template of the root, expanded with
    a configured pack, answers its artifacts" and the same "with a pack and a kind"; "The root needs the worktree
    token"; "The server prefix without its trailing slash is no operation".
  - New `offering.feature`: "The packs are ordered by configured level, then by pack id in code-point order" (six
    packs configured out of order, `Zeta` before `alpha` at each level, the exact body); "The configured level wins
    over the level pack.yaml declares"; "A pack whose pack.yaml declares no level is listed at its configured level";
    "Without configured packs the offering is empty"; "The artifacts link of every pack answers its artifacts"; "A
    pack whose pack.yaml is invalid refuses the offering with pack_unreadable"; "A configured pack without pack.yaml
    refuses the offering with pack_unreadable"; "A configured pack whose directory is missing refuses the offering
    with pack_unreadable"; "An offering whose inputs change while it is answered is refused with stale_input"; "The
    offering needs the worktree token".
  - The scenario hooks. The fingerprint rule is the hook's own, not read from the contract (R-08 revised): a `GET`
    answered 200 with a JSON body on any path but `/` carries exactly one fingerprint, equal to `/info`'s; every
    other `GET` answer carries none; writes stay exempt. Every 2xx JSON answer's links are checked statically
    (R-20): `_links.self.href` is `/api/v1` plus the request path (and query, for a `GET`) when the path has a
    `GET`, the `Location`'s path on a 201 without one, and absent otherwise; every link at any depth resolves to a
    path template of the contract that defines its `method`; `templated` matches the `href`; every relation key is
    in the guide's table. After the last scenario, the suite fails when a 2xx status of an operation was exercised
    by no scenario; all 20 are exercised. The conformance check validates each value of a schema-valued
    `additionalProperties`, matches a response on path template and method, reads a YAML answer as text, and
    refuses an `additionalProperties` mapping that holds only annotations.
  - Steps (scenario README). Gone: `the response body is a list with an element whose field "<field>" is "<text>"`
    (its uses read `the field at "/items/0/id" is …`). Renamed: `the response body has <n> relationship(s)` and
    `a relationship of the response body points to "<urn>"` become `the artifact in the response body has <n>
    relationship(s)` and `a relationship of the artifact in the response body points to "<urn>"`; `the field
    "<field>" of the response body is the lines:` becomes `the field at "<json-pointer>" is the lines:`. New:
    `the field at "<json-pointer>" is ...` and `... starts with "<text>"`, `the answer has no link "<relation>"`,
    `the problem has no code`, `I follow the link "<relation>"`, `I follow the templated link "<relation>" with:`
    (a `| name | value |` table), `following "<relation>" links from the answer, the "<member>" ids are:` (at most
    100 pages, so a looping `next` fails instead of hanging), `following the link "<relation>" of every element of
    "<member>" answers <n>`, `the file "<path>" of the pack "<name>" is deleted`, `the path of the Location header
    is the href of the link "<relation>"` and `When I send <method> "<path>" as JSON with the body:`.
- Integrated with the activation set (#125, landed first). One `Link` and one `Links`, this mission's: `Link.method`
  is `^(GET|POST|PUT|DELETE)$`, widened over #125's `^(GET|POST|PUT)$`, and `Links` keeps #125's sentences that no
  relation points at an unbuilt operation and what `self` is on an activation answer. The closure check keeps this
  mission's one `_links` exemption; #125's second, which also let an `allOf`-wrapped `_links` by, is dropped with its
  fixtures. The fingerprint follows #175 on the activation set too: the 200 of each reserved `GET`
  (`getActivation`, `listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`,
  `getEffectiveActionContext`) declares it `required: true`, the verified read it becomes once built, and no write,
  refusal, `501` or other activation response declares it; while reserved they answer `501` and send none. Every 2xx of
  the activation set has a linked example (`Activation.minimal`, `ActivationChange.selected`,
  `EffectiveArtifactList.first-page`, `ActivationSkippedReport.nothing-skipped`, `EffectiveGraph.one-edge`,
  `EffectiveActionContext.implement`), each with `self` only. Reserved operations (`x-reserved`, ADR-0020 D8) are
  excluded from the success coverage of the scenario hook, since a `501` exercises no 2xx, and are never a link
  target: a link that resolves to one is reported. `checkRefusalExamples` does not judge a reserved operation's
  refusals: they describe it once built, and its codes stay `raised: false` until the marker goes. The contract has
  28 operations; the JUnit count of `CharterContractSuite` is 210 with the activation set's scenarios.
#### One wording for a pack file that is not UTF-8 (issue #273)

No schema, operation, parameter, response or header changes in this contract. A pack file whose bytes are not valid
UTF-8 is refused by validate mode with the detail `<pack>/<file> is not valid YAML: the file is not valid UTF-8`, as
before. The service's own load now words the refusal the same way: it said `the file cannot be read as UTF-8 text`.
That reason is not answered by any operation of this contract.

#### The charter schema declares `activated_skills` and `activated_anti_patterns` (issue #283)

No schema, operation, parameter, response or header changes in this contract; the change is to the pack schema
`contracts/packs/schemas/charter.schema.json`, which now declares all thirteen activation keys core writes.

- Added: `activated_skills` and `activated_anti_patterns`, root keys core's activation writer puts in `charter.yaml`,
  in the shape of the other `activated_*` keys: absent or `null`, `[]` (none) or a list of ids. An absent or `null`
  `activated_skills` puts only the org packs' required skills in force, not every skill; the key's description says
  so. Additive, not breaking (ADR-0022). A `charter.yaml` found inside a pack that holds either key was refused as
  `artifact_schema_error`, `unknown key '<key>'`; it now validates, and a value that is not a list of strings is
  refused at the key.

#### The charter schema declares `activated_glossary_packs` (issue #131)

No schema, operation, parameter, response or header changes in this contract; the change is to the pack schema
`contracts/packs/schemas/charter.schema.json`.

- Added: `activated_glossary_packs`, the root key core's activation writer puts in `charter.yaml`, in the shape of the
  other `activated_*` keys: absent or `null` (every glossary pack is in force), `[]` (none) or a list of glossary pack
  ids. Additive, not breaking (ADR-0022). A `charter.yaml` found inside a pack (`file-kinds.yaml`, `**/charter.yaml`)
  that holds the key was refused as `artifact_schema_error`, `unknown key 'activated_glossary_packs'`; it now
  validates, and a value that is not a list of strings is refused at the key.

#### Mission types are an artifact kind (issue #194)

No schema, operation, parameter, response or header changes (mission mission-type-consolidation, ADR-0044).

- Changed: `putPackRelationship` (`PUT /packs/{packId}/relationships`) registers an edge whose `from` is
  `mission_type:<id>` when the pack holds that mission type's file, `missions/<id>/<id>.mission-type.yaml`. Until
  now every such edge was refused 409 `registration_conflict`: a mission type was a node kind only, so no artifact of
  the pack had that URN. A `from` naming a mission type the pack does not hold is still 409. The
  `NodeKind` schema's description, "every artifact kind plus action and mission_type", is kept: `mission_type` is
  now one of the artifact kinds, so the enum is unchanged. Recorded as DIV-503 in the divergence register.
- Changed: the source fingerprint and the pack revision (`ETag`) of a pack with mission content change once, as the
  mission-type file replaces the retired mission files.
- Scenarios: `relationships.feature` is new ("A relationship may be authored from a mission type") and holds "A
  relationship from a mission type of the pack is registered (ADR-0044, NFR-001)" and "A relationship from a mission
  type the pack does not hold is still a conflict". No step is new.

#### CRG tension and graph-node fields (issues #235 and #236)

- Changed (description only): the `Relation` description no longer says that a graph document's
  `in_tension_with` edge is read as `impacts` with strength -1. The service holds it as a tension
  beside the relationships and writes it back as written (ADR-0028, ADR-0045); it never answers
  `in_tension_with` as a relationship, as before. No enum value, path or wire shape changes. The
  `Removed` entry and the reserved-`impacts` item under `Added` below record the reading as it was
  then. The graph-node fields of #236 have no contract surface.

#### Pack-level files round trip (issue #195)

- Changed: the `DiagnosticCode` value `pack_unknown_key` is retired and never raised (ADR-0036
  decision 3). A `pack.yaml` key the pack descriptor schema does not declare, a retired field such
  as `accompanies_doctrine_pack` included, no longer loads with this warning: the schema is closed,
  and the pack is refused as `pack_unreadable`. The value stays in the enum, so no shape changes; no
  operation references `DiagnosticCode`. Operator approval: #195, 2026-10-28.

#### The contract document (issue #208)

- Added: `getOpenApiDocument` (`GET /openapi.yaml`, Service tag) answers the bundled contract the
  service was built from, byte for byte, as `application/yaml` (RFC 9512), with a strong `ETag`: the
  SHA-256 digest of those bytes, `"sha256:<64 hex>"` (the new `ContractETag` header). An optional
  `If-None-Match` is compared weakly; a match, or `*`, is a `304` without a body that carries the
  `ETag` again. It needs the worktree token like every operation (`401` and `403` as the shared
  responses) and never sends `Charter-Source-Fingerprint`, which now says so. No version bump: the
  contract is unreleased, so this is part of 0.1.0. The `service-desc` link (RFC 8631) to it is left
  to #126, which sets how the contract expresses links.
- Lint: `error-responses-are-problems` exempts a `304`, which RFC 9110 forbids a body, and the new
  rule `not-modified-has-no-body` holds every `304` to no content, with a planted fixture.
- The service serves it (#209): the deployable packages the `bundleContract` output, so the answer
  is the bundle a release publishes, and the `ETag` equals its `.sha256` checksum. A value of
  `If-None-Match` that is not an entity-tag list matches nothing and is answered `200`. (Between
  #208 and #209 the service answered a `501` `Problem`.) No scenario calls it yet; its scenarios
  are #210.

#### Contract tooling (issues #119 and #45)

- No shape changes. Code generated from the contract now has one class per schema: the
  generator's parser read some `additionalProperties: false` as a schema object standing for
  `false`, which the generator took for an open map, and it emitted that reading as a copy
  (`Problem1`, `Tactic1`, `Directive1`, `DirectiveRegistration1` and seven more). The service's
  build reads such a boolean schema back as its boolean (issue #119), so a client that generates
  from the split contract with the same generator should do the same.
- A pushed `contract-charter-v<version>` tag publishes the release: the workflow
  `contract-release.yml` attaches `charter-openapi.yaml`, its `.sha256` and
  `charter-scenarios.tar.gz` (the contract scenarios) to a GitHub Release of the tag, and
  publishes nothing unless the tag, `info.version` and the top heading name one released
  version (issue #45). No release is cut yet.

#### User journey scenarios (mission charter-conceptual-model-and-journeys)

- Scenarios: `journeys/journey-01-getting-started.feature` to `journeys/journey-11-human-reviews.feature` are new, one
  per user journey (`@journey-NN`). Their runnable scenarios use only the existing step vocabulary; the scenarios that
  need a surface the contract does not have yet are tagged `@WIP`, a new tag the contract suite excludes. The scenario
  archive a release publishes includes them, so a consumer that runs the scenarios must exclude `@WIP` too. No
  operation, schema or existing expectation changes.

#### Regular artefact read and registration (mission regular-artefact-read-registration)

- Added: per-kind reads and registrations for directive, styleguide, paradigm and toolguide:
  `listPack<Kind>s`, `getPack<Kind>`, `registerPack<Kind>` (POST, create; a duplicate id is 409
  `registration_conflict`) and `putPack<Kind>` (PUT, create or replace). Registration is functional
  (ADR-0026): there is no `DELETE` or `PATCH`. A registration answers 201 (create) or 200 (replace)
  with the resource and an `ETag` (the pack revision); its `Location` is the created resource's full
  request URI. An item read sends the `ETag` of the snapshot its body was read from. `If-Match` is
  optional on an artifact write and compared strongly: a weak tag never matches, `*` matches, and a
  list matches when any member does; a stale one is 412.
- Added: `putPackRelationship` (`PUT /packs/{packId}/relationships`) registers one relationship,
  idempotent on the unordered `{from, to}` pair (ADR-0027); it requires `If-Match` (428 without it)
  and answers 201 when it creates the pair, 200 when it replaces it.
- Added: refusal codes `directive_not_found`, `styleguide_not_found`, `paradigm_not_found` and
  `toolguide_not_found` (404, a pack without that artifact), `validation_failed` (422) and
  `registration_conflict` (409), with their responses. A list endpoint's 404 is the new
  `PackNotFound` response: an unknown pack is its only cause.
- Changed: `governance_policy_violation` is answered with 409, not 500. ADR-0018 D10 is amended: a
  registration answers 200 or 201, and the problem-only lint rule exempts both successes.
  `Charter-Source-Fingerprint` names every read that sends it and the `*.toolguide.yaml` files it
  digests; a registration does not send it.
- Scenarios: `directives.feature`, `paradigms.feature`, `styleguides.feature` and `toolguides.feature`
  are new ("<Kind>s are read and registered functionally"). `errors.feature` lists the new raised
  codes and expects `governance_policy_violation` with 409.

#### Verified source fingerprint on every read (mission verified-source-fingerprint)

Description text only: no schema, operation, parameter, response or header declaration changes.

- Fixed: `listPackTactics` and `getPackTactic` send `Charter-Source-Fingerprint` on 200, the digest the
  read verified; they refuse with `stale_input` when their inputs change while they are read (#173). The
  source fingerprint covers the tactic, directive, styleguide and paradigm files the service loads
  (#120).
- Changed: a configured pack whose directory is missing, a pack holding a file the fingerprint covers
  that cannot be read, or a pack directory that cannot be listed, refuses every read with 500
  `pack_unreadable`: a tactic read answered 404 `pack_not_found` before, and `/info` answered 200
  for an unreadable tactic, directive, styleguide or paradigm file or an unlistable subdirectory,
  because it digested only `pack.yaml` and the root of each pack. A file that vanishes while a pack
  is walked (an atomic save) is not an error. A load
  refusal whose inputs changed meanwhile is `stale_input`. The fingerprint's value changes for packs
  that hold loaded-kind files. The descriptions of `Charter-Source-Fingerprint`,
  `ServiceInfo.sourceFingerprint`, `CharterRefusalCode`, `TacticNotFound` and both tactic operations
  state this; the dead `x-source` path of `ServiceInfo.worktree` now names `ServiceInfoQuery` in the
  `packs` module.
- Scenarios: `service.feature` gains "The source fingerprint follows a tactic file". `tactics.feature`
  is new ("The tactic reads answer with the source fingerprint they verified") and holds "A tactic
  list answers with the source fingerprint it verified", "One tactic answers with the source
  fingerprint it verified", "A tactic list whose tactic file changes while it is answered is refused
  with stale_input", "One tactic whose file changes while it is answered is refused with
  stale_input", "A read of an absent tactic whose inputs change is stale, not not-found", "A tactic
  read that watches for a change and sees none answers with the digest it verified", "A tactic read
  of a pack whose directory is missing is refused with pack_unreadable" and "A missing sibling pack
  directory refuses a tactic read of a healthy pack".

#### Tactic retrieval; pack listing and action context removed (mission application-edge-read-path)

The inbound contract changes (C-004) while it is still `0.1.0-SNAPSHOT`.

- Added: `listPackTactics`, `GET /packs/{packId}/tactics` (tag `Tactics`), a `200` array of `Tactic`;
  `getPackTactic`, `GET /packs/{packId}/tactics/{tacticId}`, a `200` `Tactic`. Both answer `404` with the new
  `TacticNotFound` response, a `CharterRefusal`.
- Added: schemas `Tactic`, `TacticStep`, `TacticReference`; parameters `PackId`, `TacticId`.
- Added: `CharterRefusalCode` values `pack_not_found` (the pack is absent or unreadable; since the verified
  source fingerprint, an identifier that matches no configured pack, and an unreadable pack is
  `pack_unreadable`) and `tactic_not_found`
  (the pack has no such tactic), both `404`, not retryable, raised.
- Removed: `listPacks`, `GET /packs`; `refreshPacks`, `POST /packs/refresh`; the action-context operation,
  `GET /mission-types/{missionType}/actions/{action}/context`. With them go the schemas `PackListing`,
  `PackSummary`, `ActionContext`, `GuidanceEntry`, the parameters `Action`, `Depth`, `MissionType`, their
  examples, and the scenario features `packs.feature`, `refresh.feature` and `action_context.feature`.
- Kept: `GET /info`. The vocabulary schemas `Diagnostic`, `DiagnosticCode`, `NodeKind`, `PackLevel`, `Relation`,
  `Urn` and `Slug` stay although no operation references them now: the load-path vocabulary test reads them.
  `mission_type_not_found` and `action_not_found` stay in `CharterRefusalCode`, now marked not raised: the
  queries behind them are gone. So are `pack_level_mismatch` and `drg_schema_error`, which only the removed
  offering load raised. The `errors.feature`, `service.feature` and `security.feature` probes moved to `/info` and
  the tactic paths.

#### Added

- The `@contract` tag on the contract scenarios in `scenarios/`, which selects them, and the
  `@provisional` tag on `action_context.feature`.
- Provenance on every resource property: `x-source` names the Java producer of a read field,
  `x-derived` the rule and contract inputs of a computed one (`ActionContext.action`,
  `ActionContext.missionType`, `ActionContext.guidance`, `GuidanceEntry.via`); a citation on a
  referenced enum or scalar schema holds wherever that schema is used.
- `x-provisional` on the action-context operation and on `PackSummary.diagnostics`.
- Descriptions of `PackListing.packs`, `Diagnostic.detail` and `ActionContext.guidance`.
- Scenario "A directive scoped by both an action and its mission type is answered once": pins
  the built-in `DIRECTIVE_042` case (one entry, through the action). *(Replaced, see "Action
  context and the seam surface" below: the mission-type node is no longer walked.)*
- Scenario "A scope edge to a node the graph does not declare is answered without a label":
  pins today's answer (the entry is listed with a `null` label).
- Scenario "A context read over a pack whose graph shard is malformed is refused": pins
  today's `500` with `drg_schema_error`.
- Feature `errors.feature`, "Failures outside the charter's refusals": scenario "A path the
  contract does not define is answered with a not-found problem" pins `404` with the body
  `{"title": "Not Found", "status": 404}`, and scenario "A request that accepts no
  representation the operation has is answered with a not-acceptable problem" pins `406` with
  `{"title": "Not Acceptable", "status": 406}`, served as `application/problem+json`. Both
  bodies are the closed `Problem`; until now these failures answered with the framework's own
  error document (`timestamp`, `error`, `path`) or an empty body.
- The step `When I GET "<path>" accepting "<media type>"`, for the `406` scenario.
- `CharterRefusalCode` value `internal_error` (operator ruling DM
  01M599MEAJS44RYEY0X387BY4H): every 500 is a `CharterRefusal`, and a failure no other code
  names is refused with `internal_error` and a generic detail that names nothing of the
  failure. The `OfferingUnreadable` 500 response says so. Scenario "An unexpected failure is
  refused with internal_error and says nothing about it" in `errors.feature` pins the body,
  with the step `Given the service fails unexpectedly while it reads the packs`.
- The scenario suite checks every response against the contract: the content type must be
  one the operation declares for the status (or its `default`), and the body must validate
  against that content's schema. A path no operation defines must answer the shared
  `Problem` response.
- What the cutover needs, reserved before the release (ADR-0009, operator decisions of
  2026-10-19, provisional): a response field added after 1.0 is a breaking change, so these are
  in 1.0 from the start.
  - `Relation` value `impacts`: a directed, signed edge (core ADR 2026-07-26-3). A graph edge
    with relation `impacts` carries its strength in `impacts`, from -1 (sabotages) to +1
    (reinforces); a negative strength is a tension claim. A strength outside [-1, 1], a missing
    one, or one on another relation is refused as `drg_schema_error`.
  - `GuidanceEntry.impacts`: the strength of the edge that brought the artifact in, `null` for
    every relation but `impacts`; the walk follows no `impacts` edge, so it is `null`
    throughout this version.
  - `CharterRefusalCode` values `governance_policy_violation` and `stale_input`, reserved when
    they were added. `governance_policy_violation` is for the pack mutability policy
    (ADR-0012) and is still raised by nothing; `stale_input` is now raised (see "Action context
    and the seam surface" below) and may be retried, like `internal_error`.
  - Query parameter `depth` on the action-context operation and `ActionContext.depth`, which
    echoes it. Its range and meaning are under "Action context and the seam surface" below. A
    depth outside the range is a `400` Problem, not a refusal.
  - `ActionContext.governanceFingerprint`: a `sha256:` digest of the packs in force and the
    merged graph the answer was computed from, independent of host paths, formatting and
    declaration order. New schema `Sha256Digest`.
  - Operation `getServiceInfo`, `GET /info` (tag `Service`), answering the closed `ServiceInfo`:
    `contractVersion`, `worktree` (an opaque id, `charter.worktree-id` or one derived from the
    pack directories, never a host path) and `sourceFingerprint` (a digest of the sources the
    offering is loaded from; its inputs are under "Action context and the seam surface" below).
    A pack that cannot be read refuses it with a `500`.
  - Response headers `Charter-Contract-Version`, `Charter-Worktree` and
    `Charter-Source-Fingerprint`, defined once in `headers/`. Which responses carry the
    fingerprint is under "Action context and the seam surface" below.
- Scenarios in `action_context.feature`:
  - "The depth of the walk is echoed, and this version answers depth 1 only" *(replaced, see
    "Action context and the seam surface" below)*;
  - "A depth this version does not answer is a bad request, not a charter refusal" *(renamed
    "A depth outside 0 to 2 is a bad request, not a charter refusal")*;
  - "The governance fingerprint is the same while the governance is, and changes with it";
  - "An impacts edge brings nothing into scope" (which also reads a legacy `in_tension_with`
    edge);
  - "An impacts edge without a strength in [-1, 1] is refused";
  - "A strength on an edge that is not impacts is refused".
- Feature `service.feature`, "What the service says about itself": the info operation, the
  three headers on an answer, and the source fingerprint following the files. *(The refusal
  half changed: a refusal carries the contract version and the worktree, never the
  fingerprint. See "Action context and the seam surface" below.)*
- Steps for digests and headers: `Then the response body, apart from "<field>", is:`, the
  field and header steps, and remembering a field to compare it later (`scenarios/README.md`).
- `DiagnosticCode` value `pack_unknown_key` (#114, operator ruling R5): a key in `pack.yaml` that
  the pack descriptor schema does not declare, a retired field included, is reported as a
  diagnostic and ignored, not refused. Scenario "A pack descriptor with an undeclared key loads
  with a diagnostic" in `packs.feature` pins the answer.
- Scenario "A pack descriptor whose requires_core is not a version specifier is refused" in
  `packs.feature` (#114, R3): `requires_core: banana` is refused as `pack_unreadable`, with the
  detail `pack.yaml of <name>: requires_core '<v>' is not a PEP 440 version specifier`.
- Scenario "An org pack without a drg/fragment.yaml is refused" in `packs.feature` (#114, R16): an
  org pack with no `drg/fragment.yaml` is refused as `pack_unreadable`, with the detail
  `org pack <name> has no drg/fragment.yaml`. Before, it loaded with an empty graph. The scenario
  "The answer carries the load findings of the offering, as the pack listing does" in
  `action_context.feature` gives its org pack `qa` a `drg/fragment.yaml` instead of a root
  `qa.graph.yaml`; its answer is unchanged.
- Scenario "A graph shard that is not valid YAML is refused as a DRG document" in `packs.feature`
  (#114, R17): pins `drg_schema_error` for a shard that is not YAML; the body is compared apart from
  `detail`, which is the parser's own words.
- Scenario "A pack descriptor whose pack_level is an explicit null is refused" in `packs.feature`
  (#114, R19): pins `pack_unreadable` with the detail `pack.yaml of <name>: pack_level must be a
  string`.
- Scenario "A fragment that carries a header field is refused" in `packs.feature` (#114, R7): a
  fragment with `pack_name` is refused as `drg_schema_error`, with the detail
  `<pack>/drg/fragment.yaml: unknown key 'pack_name'`.

#### Changed

- `info.version` is `0.1.0-SNAPSHOT` (was `1.0.0-SNAPSHOT`; ADR-0022). The examples of
  `ServiceInfo.contractVersion` read `0.1.0` (was `1.0.0`; description and example only, no
  schema change).
- The release gate (`ReleaseGate`) treats a 0.x minor as the breaking boundary while the major
  version is 0 (ADR-0022): the baseline is the newest release of the same `0.y`, a minor bump
  (`0.y` to `0.(y+1)`) needs a `### Breaking` subsection, and a patch is compared against the
  newest release of its minor and fails on a breaking change. On a minor bump the build reports
  what breaks against the previous minor, for information only. From 1.0.0 the major version is
  the boundary, as before.
- The three action-context scenarios that answer `200` also expect `depth` and `impacts: null`
  on every entry, and compare the body apart from `governanceFingerprint` (ADR-0009).
- Every `CharterRefusalCode` and `DiagnosticCode` value is snake_case on the wire:
  `mission_type_not_found`, `action_not_found`, `pack_level_mismatch`, `pack_unreadable`,
  `drg_schema_error`, `pack_level_missing` (`unresolved_edge_endpoint` is unchanged). The
  integration seam in core maps them to core's own spellings. The scenarios in
  `packs.feature` and `action_context.feature` assert the new spellings.
- The action-context answer is sorted by full URN, the order core gives the resolved
  artifacts of an action; it was the order of the edges in the merged graph. Scenario "Action
  scope comes first, then mission-type scope, without repeats" is renamed "The scope of the
  action and of its mission type is answered once each, sorted by URN" and expects that order.
  *(Replaced, see "Action context and the seam surface" below.)*
- `GuidanceEntry.via` is described as the node the edge starts from, so it stays true when the
  resolution walks further than one scope edge. `ActionContext.action` and `missionType` have
  descriptions of their own. The three keep their `Urn` type.
- Scenario "An action the mission type does not have is refused, not answered empty" is
  renamed "An action that is not a node of the graph is refused, not answered empty"; the
  answer is the same `404`.
- Every refusal scenario (three in `action_context.feature`, three in `packs.feature`) also
  asserts the whole body with `Then the response body is:`. A refusal body omits an absent
  optional member; it carried `"type": null` and `"instance": null`, which the closed
  `Problem` schema does not allow.
- The `ActionContext.guidance` derivation rule reads "an artifact in both scopes keeps its
  action entry" (wording only). *(Replaced: the rule is now the five-step walk, see "Action
  context and the seam surface" below.)*
- Scenario "A pack descriptor without pack_level loads at its configured level, with a
  warning" is renamed "A pack descriptor without pack_level loads at its configured level,
  with a diagnostic", the name the answer gives it (`diagnostics`); the answer is unchanged.

- `CharterRefusalCode` states the consumer dispatch rules (description only, no scenario or
  schema change): a body with a `code` is a charter refusal; a body without one is a transport or
  routing failure; `internal_error` is a fault and may be retried; every other code is a
  deterministic refusal.
- The shared `default` `Problem` response (`contracts/_shared/responses/Problem.yaml`) of every
  operation declares the response headers `Charter-Contract-Version`, `Charter-Worktree` and
  `Charter-Source-Fingerprint`, by reference to `headers/`. The service already sent them
  there; the contract did not say so (#90, ADR-0011). A header declaration is not a response
  property, so no schema changes. `contracts/lint/ruleset.yaml` gains the rule
  `charter-headers-on-every-response`, which fails any response of any operation that lacks one
  of the three. `_shared/VENDORED.md` records that this repository's copy is canonical.
- Scenario "A bad request carries the headers too" in `service.feature`: the `400` for a depth
  this version does not answer carries the three headers. *(Renamed "A bad request carries the
  contract version and the worktree, not the fingerprint", and it asserts there is no
  fingerprint. See "Action context and the seam surface" below.)*
- `CharterRefusalCode` value `unresolved_edge_endpoint` is no longer raised (#114, R6): graph edge
  endpoints are URNs, and an endpoint that is not one is refused as `drg_schema_error`. The value
  stays in the enum; its description changed and its `x-refusal` entry says `raised: false`.
  Scenario "Every other refusal code is raised by an operation" in `errors.feature` no longer lists
  it and is renamed "The refusal codes the contract marks raised are exactly those an operation
  raises".
- The `OfferingUnreadable` response description gives `drg_schema_error` instead of
  `unresolved_edge_endpoint` as its example code, since the latter is no longer raised (description
  only, #114).
- Scenario "A bare edge endpoint the fragment does not declare is refused" in `packs.feature` is
  renamed "A bare edge endpoint is refused as not a URN" and expects `drg_schema_error` ("Invalid
  DRG document") with the detail `qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN`
  (#114, R6). It was `unresolved_edge_endpoint`.
- Scenario fixtures in `packs.feature` (#114, R6, R7, R16): fragment edge endpoints are URNs
  (`directive:QA_001`), the `pack_name` header is dropped from fragments (the fragment schema has
  no such key), and the org packs `internal` and `rogue` carry a minimal `drg/fragment.yaml`. Their
  pinned answers are unchanged.
- A graph shard or fragment that is not valid YAML is refused as `drg_schema_error` (#114, R17;
  behavior change, no schema change: the code is already a `CharterRefusalCode` value). It was
  `pack_unreadable`. The detail `<pack>/<file> is not valid YAML: <first line of the parser's
  message>` is unchanged. A `pack.yaml` that is not valid YAML keeps `pack_unreadable`.
  The scenarios "A pack file rewritten while it is parsed is stale, not a schema error" (renamed
  from "..., not unreadable", which the answer no longer contrasts with) in
  `errors.feature` and "A refresh of a pack that cannot be loaded is refused with the pack's code"
  in `refresh.feature` expect `drg_schema_error` for their invalid shard (was `pack_unreadable`).
- An explicit `null` in a `pack.yaml` plain-string field is no longer read as absent (#114, R19;
  behavior change, no schema change: the codes are already `CharterRefusalCode` and
  `DiagnosticCode` values). It covers the four fields the `pack-descriptor` schema types as a plain
  string: `pack_id`, `pack_version`, `name` and `pack_level`. `pack_level: null` used to load with a
  `pack_level_missing` diagnostic (`200`); it is now refused with `pack_unreadable` (`500`) and the
  detail `pack.yaml of <name>: pack_level must be a string`. An explicit `null` `pack_id`,
  `pack_version` or `name` is refused with `pack.yaml of <name>: <key> must be a string` instead
  of `pack.yaml of <name> has no <key>`; the code is unchanged. An absent key keeps its answer.
  `parent_pack`, `requires_core`, `active_charter` and `pack_drg` are nullable in the schema and
  still accept `null`.
- A plain `~`, `Null` or `NULL` in any pack file is now null (#114, R18; behavior change, no schema
  change), as core's parser reads it. In `pack.yaml` a null in a required string field is therefore
  refused like an explicit `null`: `pack_unreadable`, `pack.yaml of <name>: <key> must be a
  string`. Before, it was read as the string `~`, `Null` or `NULL`. Quoted forms stay strings.
- A pack file whose bytes are not valid UTF-8 is now a parse failure, coded by its file kind (#114,
  R17; behavior change, no schema change). A shard or the fragment is refused as
  `drg_schema_error`, `<pack>/<file> is not valid YAML: the file is not valid UTF-8`; a
  `pack.yaml` as `pack_unreadable`, `pack.yaml of <name> is not valid YAML`. Before, every such file
  was `pack_unreadable`, `<pack>/<file> cannot be read`. An I/O failure keeps `cannot be read`.
- Pack files on the load path are validated against the schemas in `contracts/packs/schemas`
  (#114; behavior change, no schema change in this contract: the codes are already
  `CharterRefusalCode` values). Each of these used to load (`200`) and is now refused. In a
  graph shard or the fragment, with `drg_schema_error`:
  - a fragment that carries a header field (`pack_name`, `source_kind`, `layer_index`, and any
    other key the fragment schema does not declare), `<pack>/drg/fragment.yaml: unknown key
    '<key>'` (R7, which the scenario "A fragment that carries a header field is refused" pins);
  - a fragment edge with `when` (a shard edge keeps it), `<pack>/drg/fragment.yaml edge: unknown
    key 'when'`;
  - a fragment node id that is not `<kind>:<id>`-safe, such as `a:b`, `<pack>/drg/fragment.yaml:
    node id 'a:b' is not <kind>:<id>-safe`;
  - an explicit `edges:` with no value (`null`) in a fragment, `<pack>/drg/fragment.yaml edges:
    expected a list`; an absent `edges` key still loads;
  - a shard without `schema_version`, `generated_at` or `nodes`, `<pack>/<file>: missing '<key>'`;
  - a shard `schema_version` that is not the string `1.0` (`2` is not a string, `'2.0'` does not
    match the pattern `^1\.0$`), `'schema_version' must be a string` or `'schema_version' does not
    match its pattern`;
  - a shard `generated_by` or `generated_at` that is not a string, `'<key>' must be a string`;
  - a shard node `tags` that is not a list, `<pack>/<file> tags: expected a list`.

  In `pack.yaml`, with `pack_unreadable`: a `requires_core` or `active_charter` that is not a string
  (`requires_core: 3`), `pack.yaml of <name>: <key> must be a string`.

  One shape is accepted that was refused: a shard node with `provenance`, which the shard schema
  declares (it was `drg_schema_error`, `unknown key 'provenance'`).
- An empty `pack_version` in `pack.yaml` is refused with its own detail (#114, R4; behavior
  change, no schema change: the code is already `pack_unreadable`). The `pack-descriptor` schema
  gives `pack_version` a `minLength` of 1, so `pack_version: ''` is `pack_unreadable` (`500`) with
  the detail `pack.yaml of <name>: pack_version must not be empty`. The detail is the same for
  any other plain-string field the schema gives a `minLength`; it has one today.
- `CharterRefusalCode` names the mutability-policy epic #55 (ADR-0012, steps #57 to #61), not #48,
  as the work that will raise `governance_policy_violation` (description only, #114): #48 is the
  pack-level policy and raises nothing. The scenario "governance_policy_violation is reserved" in
  `errors.feature` and `scenarios/README.md` say the same.

#### Action context and the seam surface

Decided in ADR-0018 (provisional, 2026-10-19) and built before the 1.0 tag, while a shape change
is still compatible. By oasdiff's rules several of these are breaking for a strict consumer of the
closed responses; no tag exists, so no baseline is broken. The divergences from core are in
[`docs/conformance/divergence-register.md`](../../docs/conformance/divergence-register.md).

**The walk.**

- `getActionContext` answers the offering's walk in five breadth-first steps: scope from the
  action node only (the mission-type node's scope is no longer walked); `requires` from the
  scoped artifacts, transitively; `suggests` from the scoped artifacts only, up to `depth`;
  `requires` of the suggested artifacts, transitively (DIV-110, operator-confirmed); `vocabulary`,
  one hop. An artifact is listed by the first step that reaches it, and the answer is sorted by
  the full URN string. The operation applies no activation filter, for good (activation is the
  effective-context operation of #103).
- `depth` (parameter): minimum 0, maximum 2, default 2 (was 1 to 1, default 1). It now counts
  `suggests` hops. Core's `depth` means the same, so the seam maps a first load to 2, a recorded
  load to 1 and the public bundle read to 0.
- `ActionContext.depth`: minimum 0, still no maximum, so raising the request maximum later is
  compatible. The compat fixture `compatible-depth-max-raised` pins that, and
  `breaking-response-depth-max-raised` pins that a maximum on the response would make it
  breaking.
- `GuidanceEntry.relation` answers `scope`, `requires`, `suggests` or `vocabulary`.
  `GuidanceEntry.via` is the parent in the walk that listed the entry: the action node for a
  scope entry, otherwise another entry of the answer.
- `GuidanceEntry.hops` (required, integer, minimum 1): 1 for a scope entry, `hops(via) + 1`
  otherwise. Following `via` reaches the action in exactly `hops` edges.
- The walk goes through a node the graph does not declare (documented behavior, pinned by a unit
  test): the declared artifacts behind it are listed with `via` set to that node, and the node
  itself has a `null` label.
- `ActionContext.diagnostics` (required): the offering's load findings, as
  `PackSummary.diagnostics` carries them per pack.
- The path description and the `x-provisional` decision are rewritten and link the divergence
  register. The `software-dev/plan` example is regenerated from the service over open-packs.
- Scenarios: `action_context.feature` is rewritten (12 to 22 scenarios). The scenarios "The
  scope of the action and of its mission type is answered once each, sorted by URN", "A
  directive scoped by both an action and its mission type is answered once" and the echo
  scenario are replaced by the ones below, because the mission-type node is no longer walked:
  depth, each walk, the cycle and diamond cases, the DIV-110 cases both ways, precedence, the
  parent tie-breaks, the `via`/`hops` invariant, vocabulary, the undeclared-target case and `diagnostics`.
  `ContractShapeTest` checks that `ActionContext.depth` has no maximum.

**Service info and the fingerprint.**

- `ServiceInfo.serviceVersion` (required): the build's own version (`gradle.properties`
  `version`, `0.1.0-SNAPSHOT`), with a semantic-version pattern. It is separate from
  `contractVersion`, which names the whole document: an additive contract set is new operations
  and a minor bump. Every module jar name now carries the version.
- The source fingerprint covers every pack file the service loads, plus `config.yaml` and
  `charter.yaml` under the configured `charter.project-config-dir`, each under a domain-tagged
  label with a presence byte. A configured directory that does not exist fails startup. It names
  no host path.
- `Charter-Source-Fingerprint` is on **200 answers only**, once, with the digest that read
  verified. No refusal, 401 and 403 included, carries it (was: every response). The header stays
  optional and declared on every response, so the lint rule
  `charter-headers-on-every-response` passes.
- Scenarios in `service.feature` (6 to 10): the build version, the fingerprint following the project
  configuration, an edit of `config.yaml`, an absent file against an empty one, and the packs-only
  control; refusals and bad requests assert no fingerprint.

**Refusals.**

- `stale_input` is **raised** by every read, including `/info`, `/packs`, the refresh and the
  context read: inputs are digested before and after the answer, and also when a load fails or the
  second digest is refused. A change after the second digest, or one reverted between them, goes
  unseen.
- `/info` refuses `stale_input` when its second digest is refused, as the other reads do.
  `stale_input` is logged at INFO, without a stack trace.
- `governance_policy_violation` stays reserved: a declared pin, enforced by a unit test, until the
  mutability-policy epic #55 raises it.
- `CharterRefusalCode` carries the machine-readable `x-refusal` map (`status`, `retryable`,
  `raised`) for all nine codes. The description adds the 401 and 403 dispatch sentence: they have
  no charter code and are transport failures.
- Scenarios in `errors.feature` (3 to 9, 13 with their examples): `pack_unreadable` over a project
  configuration file that is a directory (rows for `/info`, `/packs`, the context read and
  `POST /packs/refresh`), the `internal_error` scenario retitled, `stale_input` on `/packs`, the
  context read, `POST /packs/refresh` and `/info`, a pack rewritten while it is parsed, a negative
  control, the `@contract @reserved` scenario for `governance_policy_violation` and the list of
  raised codes.
- `pack_unreadable` is no longer pinned on a shard that is not valid YAML: a shard parse failure
  becomes `drg_schema_error` under ruling R17.

**Security.**

- `components.securitySchemes.charterWorktreeToken` (HTTP bearer) and a root `security`
  requirement: every operation needs the token. New shared responses `Unauthorized` (401, with
  `WwwAuthenticate`) and `Forbidden` (403), declared on every operation. Both are `Problem` bodies
  without a charter code and carry `Charter-Contract-Version` and `Charter-Worktree`.
- 403 is a browser request: an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header, or a `Host`
  whose name (not port) is not `localhost`, `127.0.0.1` or `[::1]`. An unknown path answers 401
  before 404. Container-level 400s answer before the guard and carry no Charter header. `TRACE`
  now allows `GET,HEAD,OPTIONS,POST`.
- Service behavior behind it: a token of at least 32 characters is required unless
  `charter.insecure-no-token=true`, and a token from argv or a system property (including through
  `spring.application.json`) refuses startup.
- Scenarios: `security.feature` (13 scenarios, 30 with their examples) and the steps for POST,
  headers and the suite token.

**Refresh.**

- Operation `refreshPacks`, `POST /packs/refresh` (tag `Packs`; operator ruling): no request body (a
  non-empty body is a `400`), answering `200` with the refreshed `PackListing` and one
  `Charter-Source-Fingerprint`; refuses with `401`, `403` and `500` like a read. Idempotent. It
  replaces the `POST /refresh` of an earlier draft, which was never released.
- Scenarios: `refresh.feature` (11).

**Other.**

- Compat fixtures: `compatible-depth-max-raised` and `breaking-response-depth-max-raised`
  (`compatibilityFixtures` now has 5 breaking and 2 compatible).
- Known contract tooling issue: the generator duplicates `Problem` as `Problem1` and `PackListing`
  as `PackListing1` (a swagger-parser `additionalProperties: false` bug). The wire shape is
  unaffected; the generator-side dedupe is issue #119. Fixed: see Contract tooling above.
- `scenarios/README.md` lists every new step.

#### Removed

- `Relation` value `in_tension_with`, replaced by `impacts` (ADR-0009, core ADR 2026-07-26-3).
  `reconciles_tension` now points at the sides of a pair joined by a negative `impacts`. Until
  core migrates its graphs, the service reads a legacy `in_tension_with` edge as `impacts` with
  strength -1; it never answers `in_tension_with`.

#### Provisional

- The action-context operation (`x-provisional`): the registered divergences from core are
  decided by the conformance corpus (issue #9). `action_context.feature` is tagged
  `@provisional`. The divergences (DIV-101 to DIV-110) live in the
  [divergence register](../../docs/conformance/divergence-register.md). The earlier note that the
  `requires`, `suggests` and `vocabulary` walks were unfinished is superseded by the walk
  described under "Action context and the seam surface" above.
- An action that is not a node of the graph answers `404 action_not_found`; for the same input
  core falls back to its non-bootstrap context (`charter/activation/context.py`). The shadow
  comparison (roadmap step 7) maps the charter's `404 action_not_found` to core's fallback and
  counts the pair as matching. The scenario "An action that is not a node of the graph is
  refused, not answered empty" says so. Registered as
  [DIV-101](../../docs/conformance/divergence-register.md).
- A scope edge to a node the graph does not declare is listed with a `null` label; core skips
  that artifact (`charter/activation/context_renderers/delivery_table.py`). The scenario "A
  scope edge to a node the graph does not declare is answered without a label" pins the
  charter's answer and says core omits it. Registered as
  [DIV-102](../../docs/conformance/divergence-register.md).
- `PackSummary.diagnostics` (`x-provisional`): `pack_level_missing` becomes a refusal when core
  ADR 2026-10-11-2 closes its deprecation window.
