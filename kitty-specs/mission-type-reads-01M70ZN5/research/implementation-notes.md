# Implementation notes (deviations from the plan, and hand-offs between work packages)

## WP01

- **Mirrors the profiles edge module (f0e34e89).** `:missions-edge` holds a package-info and one empty `@Configuration`
  per ring (`missions.application`, `missions.infra.api`, `missions.infra.packs`, `missions.infra.activations`), no
  logic. It is registered in `settings.gradle.kts`, `:deployable` (dependency and the four `missions.*.config`
  packages in `DeployableConfiguration`'s scan), `charter-reports` `serviceModules` (`missions` now lists
  `missions.domain`, `missions-edge` lists `missions.application` and `missions.infra`: Javadoc takes a package into
  one group only), `sonar.yml`, and `mutation_summary.py` `EXPECTED_MODULES`.
- **ArchUnit.** `MISSIONS_NAME_PACKS_ONLY_FROM_ADAPTER`, `MISSIONS_PACKS_ADAPTER_NAMES_PACKS_APPLICATION`,
  `MISSIONS_NAME_ACTIVATIONS_ONLY_FROM_ADAPTER`, `MISSIONS_ACTIVATIONS_ADAPTER_NAMES_ACTIVATIONS_APPLICATION` and
  `NO_PACKS_OR_ACTIVATIONS_CLASS_NAMES_THE_MISSIONS_EDGE`, each with a known-bad, and
  `everyMissionsEdgeRingIsOnTheGatedClasspath` (non-vacuity). The per-slice factories (`SLICE_RINGS`) already list
  `missions` in `SLICES`, so `applicationIsEdge`, `entrypointOverApplication` and `onlyDeployableComposesAdapters`
  gate the new rings unchanged; `SLICE_COVERAGE` keys on domain rings only and needs no change.
- **Beyond the owned files** (additive, as in the profiles commit): the known-bads of the not-named-back rule must live
  in the packs and activations slices, so `packs.application.badfixture.ApplicationNamesMissionsApplication` and
  `activations.application.badfixture.ApplicationNamesMissionsInfra` are new test files there. The module table of
  `docs/architecture/hexagonal-service.md` gains a `missions-edge` row. `scripts/ci/test_mutation_summary.py` pins the
  expected module list exactly, so its lists gain `:missions-edge` (the same registry extension the profiles commit
  made for `:profiles-edge`; no other expectation changed).
- **The nightly mutation floor is red for `:missions-edge` until WP04.** `checkMutationFloors` fails a group with no
  mutant ("no mutant lies in its packages"), and the skeleton has none (the `config` packages are excluded). The
  profiles mission did the same: a floor of 85 with a build-file comment, no exemption. This WP follows it; the nightly
  goes green for the module once WP04 puts the reads in the rings. `mutation-nightly.yml`'s header comment still lists
  five modules, as it did after the profiles commit.

## WP02

- **The activation reads are declared in WP05, not reserved.** The plan (squad C8) declared them `x-reserved` with a
  `501` until WP05. Reserving needed `ReservedOperationCoverageTest`'s assertions changed (it pins that no operation is
  reserved), an operator-escalation class. WP05 lands in the same PR, so the contract instead declares them when they
  are built. Their contract parts are kept in `wp05-activation-contract.patch`.
- **Hand-off to WP03 and WP04:** `mission_step_contract_not_found` (WP03) and `skeleton_not_found` (WP04) are
  `raised: false` until the reads raise them. The WP that builds a read flips the code to `raised: true` and adds its
  example (`CharterRefusal.*-not-found.yaml`, already written) to the 404 response files.
- **Choices beyond the plan, put to the WP02 review:**
  - a `step` relation (step contract to step);
  - skeleton links on each expected output artifact (`expectedOutputArtifacts[i]._links.skeleton`);
  - delegation candidates as `{id, _links}`;
  - `MissionStep.description` optional;
  - skeleton `path` spelled like `template_path`;
  - `sha256` as `Sha256Digest`.
- **Hand-offs to WP04 unit tests:** the base64 skeleton encoding (the inline fixture step writes UTF-8 only, so no
  scenario can store bytes that are not UTF-8) and the lowest-id tie-break between two step contracts bound to one step.
- **`effective-artifact` stays** in the hypermedia guide's relation table and in `link-relations-are-known`, although
  the profiles mission adds the same relation: the rebase on the profiles PR dedupes it.
- **Review fold:** lists are present and empty when the file has none (`MissionStep.expectedInputArtifacts`,
  `expectedOutputArtifacts`, `MissionStepContractStep.inputs`), as for the existing kinds. The mission type's step and
  skeleton self links are each followed by an explicit GET: the step "following the link ... of every element of ..."
  reads a top-level array only, and these sit under `missionType`.

## WP03

- **Three kind sets in `ArtifactReadService`.** `SERVED` keeps its meaning and its five kinds (the kinds with a per-kind
  read; its pin in `ArtifactReadServiceTest` is unchanged). `ITEM_KINDS` is `SERVED` then `mission_step_contract` (the
  generic item read); `LISTED` is `SERVED` then `mission_type` then `mission_step_contract` (the list, `?kind=`, the
  cursor, `/references`, the reference `artifact` link and the removal). Both are derived from `SERVED`, so the
  profiles mission's `agent_profile`, appended to `SERVED`, lands between the five and the mission kinds in both.
  `listedKind` and `itemKind` sit beside the unchanged `servedKind`. `ArtifactPosition` orders and admits `LISTED`.
- **The generic item read refuses a `mission_type` `kind_not_found`** (after the pack), never a throwing branch; a unit
  test pins it. WP04's literal route answers it in the deployed service.
- **A step contract is read from the pack aggregate** in one verified read (`packs.load`) that also judges what its
  links need: whether the pack holds its mission type, whether that type declares the step its `action` names, and
  which delegation candidates of a listed kind the pack holds (`Item.OfMissionStepContract`, no revision).
- **Beyond the owned files: `MissionTypeSteps`.** The step link needs the step ids of a mission type, and neither
  `packs.application` nor `packs.infra.api` may name `missions.domain`. A one-method port
  `packs.domain.MissionTypeSteps.stepIds(CharterArtifact)` is implemented by `DomainRepositoryService` (the store that
  already binds the missions domain). WP04 owns `packs.infra.storage`; the hunk is additive.
- **Beyond the owned files: `ArtifactJson`** mixes the null-omission rule into `MissionStepContractStep`,
  `MissionStepContractInput` and `MissionStepContractGate` (optional members were written as `null`). WP04 needs the
  same for the mission type, step and skeleton models in its own adapter.
- **`mission-type` and `step` links are string-keyed** in `ArtifactDtos`, not new `Relation` constants:
  `HypermediaTest.everyRelationHasItsWireKey` pins `Relation`'s values exactly, and extending it is an
  existing-assertion change. Both keys are rows of the hypermedia guide's table and of `link-relations-are-known`.
- **Removal.** `ArtifactRemovalService.requireHeld` takes `LISTED` less `NOT_YET_REMOVABLE` = {`mission_type`}: a
  mission type's `DELETE` must cascade its skeletons (D10), so until WP07 builds it, it is `kind_not_found` and changes
  nothing (unit test). A step contract's `DELETE` follows the five kinds' rules unchanged (D10) and works through the
  existing kind-agnostic removal (unit test: the file goes, the mission type stays, the next `DELETE` is
  `mission_step_contract_not_found`). **Hand-off to WP07:** remove `mission_type` from `NOT_YET_REMOVABLE` with the
  cascade, and name `mission_step_contract_not_found` and `mission_type_not_found` in `deletePackArtifact`'s
  description and 404 (they still name the five kinds' codes only).
- **`mission_step_contract_not_found` is `raised: true`**, with its example in `ArtifactNotFound.yaml` (used by the
  item and references reads) and the change fragment updated. The raised-codes registry scenario in `errors.feature`
  needs the code added to its table; that edit of an existing scenario's expected list was put to the orchestrator.

## Operator rulings during WP03 (2026-11-10)

- The raised-code registry of `errors.feature` gains a row for each new raised code
  (`mission_step_contract_not_found` in WP03, `skeleton_not_found` in WP04). This is registry maintenance; the rule the
  scenario pins is unchanged.
- `WireTokenParityTest` pairs `ArtifactKindToken` with `LISTED` (the `?kind=` kinds), not `SERVED`.

## Coordination with the profiles mission

- Its `ArtifactReadService.listed()` (WP05) and the activation read's kind gate (`AvailablePacks.listedKinds()`, WP06)
  read the listed kinds. At the merge, `listed()` returns this mission's `LISTED`, so `mission_type` passes the activation
  gate. `listed()` must never hold a kind that cannot be activated (template, asset, guard, lifecycle).
- Its reviewer flagged `references()` for kinds that are listed but not served: it must not reach `servedItem`'s throwing
  default branch. It is checked in the WP03 review.

## WP03 review (approved, 2026-11-10)

- Folded: `MissionStepDelegationCandidate` says "lists", not "serves".
- Accepted as is: until WP07, a `mission_type` DELETE answers `kind_not_found` with the detail "serves no artifact
  kind". WP07 removes the `NOT_YET_REMOVABLE` branch.
- **Hand-off to WP04:** `DomainRepositoryService.stepIds` needs a storage test covering the on-demand step union and
  the "not a `MissionType`" branch; WP04's on-demand scenario covers the first end to end. The `artifact` links to a
  mission type (references, delegation candidates) resolve once WP04's literal route exists.

## Operator ruling during WP01 (2026-11-10)

- `scripts/ci/test_mutation_summary.py`: its pinned module lists gain `:missions-edge`, the same registry maintenance as
  the profiles mission's `:profiles-edge` rows. The operator accepted it, and future module-list rows are treated the
  same way.

## WP01 review (approved, 2026-11-10)

- **Hand-off to WP07's docs pass:** in `docs/architecture/hexagonal-service.md`, rename the stale `missiontypes` row to
  `missions` (domain only; its edge is `missions-edge`).
- Out of scope and left as found: the `charter-reports` Javadoc `-group` entries match exact packages, so edge
  `*.config` packages land under "Other Packages" (the profiles edge too); and the `mutation-nightly.yml` header comment.

## WP04

- **The composite read.** `packs.application.MissionTypeHoldingRead.holding(pack, typeId)` judges the descriptor
  (`pack_not_found`, then `pack_unreadable` for a pack without `pack.yaml`), then inside one `ConsistentRead` loads the
  pack and answers `Holding`: the type as `CharterArtifact`, its `Template`s (`missionType == type`, by id in code-point
  order), the `MissionStepContract`s whose `mission` is the type (by id), the stored step files, and the fingerprint as
  a string. An unknown type is `mission_type_not_found` ("Pack p has no mission type t.").
- **`PackCompanionFiles` is per step, not per path.** The plan named `bytes(pack, path)`, but `packs.application` cannot
  know a step's file paths without naming the missions domain. The port is
  `stepFiles(PackIdentifier, CharterArtifact) -> Map<stepId, StepFiles(Optional<Payload> prompt, guidelines)>`;
  `DomainRepositoryService` implements it by reading `missions/<type>/steps/<step>.prompt.md` and `.guidelines.md` (the
  S8 paths `MissionTypeBinding` derives) through `FilesystemPackStore.readPayload` (the containment rule of every
  payload), for each step whose loaded document is present. A file that cannot be read now is `pack_unreadable`; inside
  the verified read a changed file is `stale_input`. No re-render: `MissionTypeStoredFilesTest` pins an irregular file
  (two blank lines, a trailing space) byte for byte.
- **Storage test for `stepIds`** (WP03 hand-off): `MissionTypeStoredFilesTest` covers the on-demand union and the
  "not a `MissionType`" branch (empty step ids and empty step files).
- **T016:** `MissionTypeHoldingCorpusTest` (tag `open-packs`, so `:packs:openPacksRoundTrip`) reads the pinned
  built-in `software-dev` through the production store and `MissionTypeHoldingRead`: 8 + 5 steps, each prompt and
  guidelines equal to the checkout's file byte for byte (absent where the checkout has none), 5 prompts keep their
  `spdd:reasons-block` markers, 4 skeletons, 5 bound step contracts. It configures the built-in pack alone: the other
  packs of the checkout have no `pack.yaml`, which the source digest refuses.
- **missions.application.** `PackMissionTypes.holding(PackIdentifier, typeId) -> MissionTypeHolding` (missions-owned:
  `MissionType`, `StoredSkeleton(path under templates/, StoredFile)`, `BoundContract(id, action)`,
  `StepFiles(Optional<StoredFile>...)`, fingerprint). `MissionTypeReadService` answers `Fingerprinted<MissionTypeView |
  StepView | SkeletonView>`. `SkeletonNames` is the D9 codec (a decode that meets `~` not followed by `0`/`1`, a `/`,
  or an empty name yields nothing); the skeleton is looked up among the type's own skeletons only. `StoredFile.text()`
  decodes strictly (REPORT on malformed input). A step file that is not UTF-8 is an `IllegalStateException` (the store
  parsed it as Markdown text), never a lossy decode. Refusal details: "Mission type t of pack p has no step s." and
  "... has no skeleton n.".
- **missions.infra.packs.** `PacksMissionTypes` (the one bean of the port) narrows the type with `instanceof
  MissionType`, else `IllegalStateException`; it strips `<type>/` off the template id.
- **missions.infra.api.** `MissionTypeController implements MissionTypesApi`; `MissionLinks` builds hrefs as the packs
  `Hypermedia` does (base path `${charter.api.base-path:/api/v1}`, `UriUtils.encodePathSegment`, so `~` stays literal);
  `MissionRelation` holds only the five relations it uses (`self`, `collection`, `mission-type`, `step-contract`,
  `skeleton`). `MissionJson` mixes NON_NULL into `MissionType`, `MissionPathConventions`, `MissionStep` and
  `MissionExpectedArtifact` (`NullOmissionTest` green). The mission type's own expected outputs carry no `_links`; a
  step's output carries `_links.skeleton` only when bound, else no `_links` at all.
- **Routing (FR-006):** `MissionTypeControllerTest` maps a stand-in at `ArtifactsApi.PATH_GET_PACK_ARTIFACT` beside the
  controller and pins that `.../artifacts/mission_type/software-dev` reaches the literal mapping while
  `directive/GUARD` and `mission_step_contract/specify` reach the generic one. The deployed service is pinned by the
  scenarios.
- **Beyond the owned files (additive):** `MissionRelationParityTest` lives in `:deployable`'s tests beside
  `RelationSetParityTest`, reusing `RelationTable` and the guide the build already copies there (so `MissionRelation`
  is public). The `skeleton_not_found` flip touched `CharterRefusalCode.yaml`, `MissionSkeletonNotFound.yaml` (example
  and description), the `### Breaking` fragment and the one operator-approved `errors.feature` row.
- **Ring configurations.** Each of `MissionsApplicationConfiguration`, `MissionsApiConfiguration` and
  `MissionsPacksConfiguration` now `@ComponentScan`s its ring (the deployable scans only the `config` packages).
  `MissionsEdgeConfigurationsTest` needs the packs composite read to start the rings, so its runner gains a mocked
  `MissionTypeHoldingRead` bean (a setup line and the class comment; its assertions are unchanged). No WP01 class was
  removed: every ring keeps its `config` class, and `missions.infra.activations` has no other class until WP05, so the
  classpath guard needs it.
- **Mutation floor:** the module now has mutants, so the nightly group is no longer empty; its score against the floor
  of 85 is not measured in-mission.
- **Hand-off to WP05:** `MissionTypeHolding` is scope-neutral. Add the `ActivationMissionTypes` port (key
  `ActivationId`) returning the same `MissionTypeHolding` (winner type, skeletons and step files of its holding pack,
  merged-closure step contracts whose `mission` is the type), then split each public method of `MissionTypeReadService`
  into "fetch the holding" and a private builder over it (the view logic, `boundContract`, `boundSkeleton` and the
  skeleton lookup already take only the holding). Generalise the refusal details ("of pack p") to the scope.
  `MissionLinks` is pack-scoped (each method takes a `packId`); the activation controller needs the same methods under
  `activations/{id}/artifacts/...`, and `effective-artifact` is not in `MissionRelation` yet.
- **Hand-off to WP07:** nothing in WP04 writes. The cascade can enumerate a type's skeletons with
  `Pack.templates()` filtered by `missionType`, as `MissionTypeHoldingRead` does; its step companions are the ones
  `PackCompanionFiles` reads. `mission_type` stays in `NOT_YET_REMOVABLE`.

## WP04 review (approved, 2026-11-10)

- Mutation probe: 6 of 6 deliberate breaks killed by unit tests (D9 decode order, tie-break, lossy decode, `templates/`
  strip, step order, the `mission == type` filter).
- **Hand-offs to WP05**, which touches the same files:
  - Say in `MissionTypeReadService.utf8`'s comment why the non-UTF-8 branch cannot be reached: the store drops a type
    whose companion is not UTF-8, and the digest guards the bytes.
  - Rename `MissionsEdgeConfigurationsTest.eachRingsConfigurationStartsAsOneBeanAndHoldsNoOther`. It is a name change,
    not an assertion change.

## WP07 (T022 to T024; T025, the usability run, deferred)

- **One removal.** `ArtifactRemovalService` lost `NOT_YET_REMOVABLE`: every `LISTED` kind is removable. A mission type's
  removal is the type plus its skeletons (`Pack.templates()` whose `missionType` is the type, by id), one
  `PackRemovalStore.Removal` whose `artifact` became `artifacts` (a list; `ofArtifact` stays, `ofArtifacts` is new). The
  step prompt and guidelines files are the type's companions (`MissionTypeBinding.companions`), so `ArtifactFiles`
  removes them with its file; it now reads the pack once for all the removed artifacts. The registered edges that start
  at any removed artifact go, every write-gate question is asked per removed artifact, and the referrer scan and the
  impact check run over the facts with every removed artifact taken out (`WriteFacts.withoutArtifact`, chained).
- **Empty directories.** `PackCommitStore.remove` prunes the directories a removed file leaves empty, up to the pack's
  root (exclusive), so a removed type leaves no `missions/<type>/` behind. This applies to the five kinds too (the last
  directive of a pack takes `directives/` with it); no existing test pins a leftover directory.
- **The new referrers (D10)** live in `InboundReferences.scanRemoval`, beside the unchanged registration `scan`
  (additive, beyond the owned files). Over the registration's references it adds: each `bindings()` entry (a step
  contract whose `mission` names a type) as a reference to the type, and each edge that starts at an action node as a
  reference to that node. A reference to `action:<type>/<step>` counts when `<type>` is removed, and resolves where the
  type does (`StepHolding.missionTypeOf`). An edge from the removed type itself (`mission_type:t requires
  action:t/s`, as the built-in graph writes) is not a referrer, like any edge from a removed artifact. The whole-pack
  registration's scan is unchanged: a replace that drops a mission type still bound by a contract is not refused by
  this rule (out of scope; noted for #99's follow-up).
- **Codes.** Both new referrers answer `409 artifact_referenced`, whether the edge is registered or author-written;
  `registration_conflict` stays for an author-written graph document that declares the type's, a skeleton's or an
  action node's node, or holds an edge from the type or a skeleton (the scan runs first, so an author-written edge on
  an action node is `artifact_referenced`).
- **If-Match.** Neither kind has an `ETag`; the `DELETE` takes `If-Match: *` or the pack revision, as for a tactic. The
  scenario pins 428 and 412 for a mission type.
- **Scenarios** are in `mission-types.feature` (its fixtures), four of them: the step contract removal, the cascade
  (with 428/412, every read then `mission_type_not_found`, the files and the type's directory gone, the other type's
  skeleton kept), the bound-contract 409 and the authored action-node 409 (byte-identical pack). They were written after
  the unit tests had turned the service green, so the red step was the unit tests'. **Beyond the owned files:** a step
  `Then the pack "<name>" holds no file "<path>"` (`CharterSteps`, `ScenarioPacks.holds`), listed in the scenarios
  README with a `mission-types.feature` entry.
- **Contract:** `deletePackArtifact`'s description, 404 and 409 name `mission_type_not_found`,
  `mission_step_contract_not_found` and the new referrers; one line joins the `### Added` fragment of this mission.
- **Docs:** `service-contract.md` gains the three mission reads in the built table, a paragraph on both kinds (the D9
  naming rule, the removal, the pack scope: activation-scoped reads and `effective-artifact` links come with WP05 and
  WP06), and the #96, #99 and new #100 rows; `hexagonal-service.md` names `missions` in place of `missiontypes`.

## WP07 review fold (2026-11-10)

- **Untested author-written branches (major).** Three `ArtifactRemovalServiceTest` cases: an author-written edge
  `mission_type:documentation -> action:documentation/discover` is `registration_conflict` with the files unchanged
  (kills the mutant that drops the `sourceRemoved` guard of `InboundReferences.referrersOf`: it answers
  `artifact_referenced`); a document that only declares `action:documentation/discover` is `registration_conflict`
  (kills the mutant that drops the `declared()` action-node fallback of `requireNotAuthored`: the removal is accepted);
  registered edges `mission_type:documentation -> action:documentation/discover` and
  `template:documentation/spec-template.md -> mission_type:documentation` go with the removal and the shard ends
  empty. Each mutant was applied, seen to fail exactly its test, and restored with `git checkout`.
- **`PackCommitStoreTest`** pins that pruning the emptied directories of a removal keeps the pack root and `pack.yaml`.
- **Wording.** The `### Added` fragment and the codes note above say when `registration_conflict` applies, in the
  review's words.
- **Registration hole, not folded.** `PackRegistrationService.requireNoInboundReference` uses `scan`, not
  `scanRemoval`, so a whole-pack replace that drops a mission type still bound by a step contract, or named by an
  edge on one of its action nodes, is not refused `409`. The draft follow-up issue is
  `followup-registration-mission-type-referrers.md` in this directory.

## WP07 review (T022–T024 approved on re-review, 2026-11-10)

- First review: REJECT on untested author-written branches (mutants c and e). Fold 414e3e23 and 33b857cf: both mutants
  are now killed. Re-review: APPROVE. WP07 stays in progress until T025 (the usability script) lands.
- Operator ruling: empty directories a removal leaves are pruned for every kind. The PR text names this behaviour change
  for the five kinds.

## WP05

- **Names and tag.** The three operations are `getEffectiveMissionType`, `getEffectiveMissionStep` and
  `getEffectiveMissionSkeleton` (the patch and `plan.md` said `getActivationMission*`), tag `EffectiveMissionTypes`,
  after the profiles mission's `getEffectiveAgentProfile` (tag `EffectiveAgentProfiles`) and the activation set's
  `getEffectiveArtifact`/`getEffectiveActionContext`. Its own tag gives `EffectiveMissionTypesApi`, implemented by
  `EffectiveMissionTypesController` beside `MissionTypeController`: one tag per controller, no shared generated
  interface, as the profiles edge does. Paths: `/activations/{activationId}/artifacts/mission_type/{missionTypeId}`
  (exactly the URL the generic route answers for the kind, so the profiles WP08 `effective-artifact` link of a mission
  type lands on the literal route) and its `steps/{stepId}` and `skeletons/{skeletonName}`, declared after the
  `agent_profile` literal and before the templated `{kind}/{artifactId}`. Not reserved; the patch's parts went in, its
  `openapi.yaml` and `CharterRefusalCode.yaml` hunks by hand (the merged state had moved).
- **Bodies and links.** The same bodies as the pack reads. `packId` is the pack that holds the winner. Links stay under
  the activation (`self`, `collection` = `/activations/{id}/artifacts?kind=mission_type`, steps, skeletons,
  `mission-type`, `skeleton`, and `step-contract` = `/activations/{id}/artifacts/mission_step_contract/{id}`, the generic
  effective read). The mission type alone also carries `artifact`, the pack read of the winner
  (`/packs/{holder}/artifacts/mission_type/{id}`), as `getEffectiveArtifact` and `getEffectiveAgentProfile` link it; a
  step and a skeleton are no artifacts, so they carry no `artifact`. No `up`. `MissionRelation` gains `ARTIFACT`
  (`artifact`, an existing row of the guide's table; `MissionRelationParityTest` green). No `effective-artifact` is
  used here.
- **What "unreached" means.** The read answers what the merged closure holds, whatever its state, as
  `EffectiveReadService.artifact` does: a type the activation does not select is answered (scenario), and a type no
  pack of the closure holds is `mission_type_not_found` (`artifact` answers `artifact_not_found` there; the mission
  reads keep their kind's code, as the pack reads and the action context do). Order: `activation_not_found`, then
  `mission_type_not_found`, then `action_not_found`/`skeleton_not_found`.
- **The composite read** is `EffectiveReadService.missionType(ActivationId, typeId) -> EffectiveMissionTypeAnswer`
  (`stored`, both fingerprints, `HeldMissionType`), beside `artifact`, one `PacksSession`. The session answers
  `PacksSession.missionType(typeId) -> Optional<HeldMissionType>` (a **default** method that throws, so the test fakes
  of both missions keep compiling; `ClosureViewSession` and the unresolved session override it). `ClosureViewSession`:
  the winner is `ClosureView.winner(kind, id)` (new, additive: the entity read `effectiveArtifact` did, now shared, with
  no governance and no in-force resolution); the holding pack's skeletons and step files come from the packs slice's
  `MissionTypeHoldingRead.holding(holder, typeId)`, whose verified (whole-source) fingerprint must equal the session's
  `ClosureView.fingerprint()`, else `stale_input`, so the answer never mixes two states; the step contracts are the
  merged closure's: `MergedView.bindings()` entries of kind `mission_step_contract` bound to `mission_type:<type>`, each
  read as its own winner (`ClosureView.winner`), by id in code-point order. `PacksAvailability` gains an `@Autowired`
  `(ClosureQueries, MissionTypeHoldingRead)` constructor; the one-argument one stays (tests), its sessions refuse a
  mission type read as a wiring fault. Activations-owned values: `HeldMissionType`, `HeldBytes` (never hands its array
  out), `EffectiveMissionTypeAnswer`; `activations.application` names no packs type (the adapter maps `Template`,
  `Payload`, `MissionStepContract`).
- **missions.** `ActivationMissionTypes.holding(activationId, typeId) -> EffectiveMissionTypeHolding(holder,
  MissionTypeHolding)`: the same `MissionTypeHolding` value as the pack port, with the holding pack beside it (the type
  read needs it for `packId` and `artifact`). `MissionTypeReadService`'s public methods are now "fetch the holding" plus
  package-private builders `missionTypeOf`, `stepOf`, `skeletonOf` that take the holding and a scope phrase; the
  refusal details say "of pack p" or "of activation a" (the pack wording is unchanged, so its pinned messages hold).
  `EffectiveMissionTypeReadService` is the sibling service over the activations port. `ActivationsMissionTypes` (the
  one bean of `missions.infra.activations`, which its configuration now scans) narrows to `MissionType` and strips
  `<type>/` like `PacksMissionTypes`. `MissionBodies` holds the wire bodies both controllers build; `MissionLinks.Scope`
  (`pack(id)`, `activation(id)`) builds the same paths under either root, and its pack methods delegate to it.
- **WP04 review hand-offs.** `MissionTypeReadService.utf8`'s comment says why the branch is unreachable;
  `MissionsEdgeConfigurationsTest.eachRingsConfigurationStartsAsOneBeanAndHoldsNoOther` is renamed
  `theFourRingConfigurationsStartTogetherEachOnce` (name only), and its runner gains a mocked `EffectiveReadService`
  bean, a setup line like WP04's `MissionTypeHoldingRead` (assertions unchanged).
- **Scenarios:** `effective-mission-types.feature` (new, beside `effective-artifact.feature`, its own fixture: `main` at
  org, `proj` at project overriding `software-dev`, `elsewhere` outside the closure). Red first: committed and run
  against the service before the build, 5 of its 6 scenarios failed (the type path answered by the generic read: an
  `ETag` and the `EffectiveArtifactRead` body; the step and skeleton paths the router's 404 without a code) and
  `OperationCoverage` named the two uncovered 200s. The sixth, the generic read of a step contract, was green already
  (see below). Unit tests were written beside the code, not before it; the red step was the scenarios'.
- **Coordinator addition (profiles WP08's `effective-artifact` links).** `GET /activations/{id}/artifacts/mission_step_contract/{id}`
  answers 200 today through the profiles mission's generic `getEffectiveArtifact` (winner, state, `artifact` link to the
  pack read, no body), and `404 artifact_not_found` for a step contract no pack of the closure holds; the scenario pins
  both, plus the `artifact` link followed to the pack read. No profiles-owned code was changed. A `mission_type` is
  answered by the literal route at the same URL.
- **For the profiles mission:** `ClosureView.winner(kind, id)` is new and `effectiveArtifact` now calls a shared
  private `winningEntity` (behaviour unchanged); `PacksSession.missionType` is a default method; `PacksAvailability` has
  a second public constructor and the `@Autowired` moved to it. `EffectiveProfileProblems` (the 400 parity advice) has
  no missions counterpart: a malformed activation id on the three new routes is the shared handler's bare 400. If the
  parity is wanted on every activation route, a `@RestControllerAdvice(assignableTypes = EffectiveMissionTypesController.class)`
  like it is the fix.
- **Hand-offs to WP06.** An action node `action:<type>/<step>` under an activation should link
  `/activations/{activationId}/artifacts/mission_type/{type}/steps/{step}` (`getEffectiveMissionStep`), and its mission
  type `/activations/{activationId}/artifacts/mission_type/{type}`; in a pack, `getPackMissionStep`. Build them with
  `MissionLinks.Scope.step(type, step)` if the links are built in the missions edge; the activation set's own graph and
  context answers live in `activations.infra.api` and cannot name `MissionLinks`, so they spell the path. The relation
  is the step's own (`step`, a row of the guide, or `effective-artifact` for the type), WP06's choice. Under an
  activation the step read answers the winner's step even when the type is not selected.
- **Hand-off to WP07's docs:** `service-contract.md`'s note that the activation-scoped reads come with WP05 can now name
  the three operations.

## WP05 review (approved, 2026-11-10)

- Mutation probe: 4 of 4 killed (skeletons from the winner's holder, the stale-fingerprint check, merged-closure step
  contracts, the `artifact` link).
- Operator ruling: "a mission type the activation does not reach" means one that no pack of the activation's closure
  holds. A held but unselected type is answered whatever its state (spec US4 AS3 amended).
- Follow-ups, recorded for the PR's Deferred section:
  - the activation-scoped bodies carry no `state`. Link `effective-artifact` (the generic read, which has it) from the
    type in WP06, or file an issue;
  - `PacksSession.missionType` is a throwing default method. Make it abstract after both missions land;
  - a malformed `activationId` on the three routes answers the shared bare 400, unlike `EffectiveProfileProblems`. A
    scoped advice is the fix (follow-up);
  - `paths/activations_activationId_artifacts_kind_artifactId.yaml` (profiles-owned) should name
    `getEffectiveMissionType` for a `mission_type`. WP07's docs pass, or the profiles hand-off;
  - `ClosureView.winner` has no direct `:packs` test (add a `ClosureViewTest` case).

## WP06

- **Mission kinds link like the served kinds.** The temporary filter of `2ce4b500` (`EffectiveReadMapper`'s
  `linkedKindsOf`) is gone; the mapper links every kind of `EffectiveReadService.listedKinds()` again. An effective list
  or action context item of kind `mission_type` links `effective-artifact` (`/activations/{id}/artifacts/mission_type/{type}`,
  the literal `getEffectiveMissionType`) and `artifact` (`/packs/{packId}/artifacts/mission_type/{type}`); a
  `mission_step_contract` item the generic `getEffectiveArtifact` and `getPackArtifact`. A graph node of either kind
  links `effective-artifact` only (the sibling's rule: a node names no pack).
- **Steps.** While `MISSION_TYPE` is a linked kind, a graph node `action:<type>/<step>` links `effective-artifact` to
  `/activations/{id}/artifacts/mission_type/{type}/steps/{step}` (`getEffectiveMissionStep`), and the action context's
  own `_links` gains `effective-artifact` (the same read) and `artifact`
  (`/packs/{holder}/artifacts/mission_type/{type}/steps/{step}`, `getPackMissionStep`). The context has no action node
  object (its action is `missionType` + `action`), so its `_links` beside `self` is where the step's links go. The
  paths are spelled by two new `ActivationMapper` methods (`effectiveMissionStep`, `packMissionStep`):
  `activations.infra.api` may not name `MissionLinks`.
- **Where the holder comes from (beyond the plan).** `ContextRead` gains `Optional<String> missionTypePack`: the
  `packId` of the mission type's item of the effective set the context is already built from (no extra read); a
  five-argument constructor keeps the old shape (empty). The graph names no pack and the plan's "graph nodes carry both
  links" would need the effective set in the graph read, which the sibling's rule ("a node names no pack, so it has no
  artifact link") already declines for every node; a step node follows it: `effective-artifact` only. The step read it
  leads to links `mission-type`, whose answer links `artifact`.
- **Which steps are linked (decision).** The mapper links every step the graph or the context answers: a step the
  closure holds (`StepHolding`: a pack defines the mission type and a graph declares `action:<type>/<step>`), the same
  rule that decides whether the node is listed or the context answers 200. The mapper does not see the winning version's
  YAML steps, so a step a graph declares but the winning mission type's YAML does not (or that only a losing copy
  declares) is linked and its read answers `404 action_not_found`. Checking it would mean a `PacksSession.missionType`
  read per type in the graph and context reads (a holding read with a fingerprint check), which the fakes refuse; the
  schemas say what such a link answers. No "dangling" action node (a declared node whose mission type no pack holds)
  is ever listed, so none is linked.
- **The `state` follow-up of WP05** stays as recorded: nothing was added to the activation-scoped mission type body.
- **Existing assertions changed (pre-authorised by decision 2; each was "no `_links`" on an entry the brief now links).**
  - `effective-reads.feature`, scenario "WP08 - each item of the list links ...", line 192:
    before `And the field at "/items/1/_links" is absent`; after
    `And the field at "/items/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"`
    and `And the field at "/items/1/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev"`.
    Its description's "the control of a kind no pack-scoped read serves yet: it is listed and links nothing" now reads
    "listed and linked like them (#99 WP06)".
  - `effective-graph-and-context.feature`, scenario "WP08 - a graph node and a context item ...", line 1032 (the action
    node): before `And the field at "/nodes/0/_links" is absent`; after
    `And the field at "/nodes/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement"`.
  - same scenario, line 1036 (the mission type node): before `And the field at "/nodes/3/_links" is absent`; after
    `And the field at "/nodes/3/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"`.
  - same scenario's name ("..., and a step links nothing" -> "..., and a step links its step read") and description
    (the step and the mission type now link their reads). Every other expectation of both scenarios is unchanged.
  - The unit test `EffectiveReadMapperTest.aGraphNodeOfAServedKindLinks...AnActionOrAnUnservedNodeLinksNothing` is
    unchanged and green: its mapper does not list `MISSION_TYPE`, and step links are gated on that kind.
- **Red first.** The three new scenarios of `effective-mission-types.feature` and the two edited sibling scenarios were
  committed (`9eaebc19`) and run against the unchanged service: all five failed on their first link assertion
  (plus `OperationCoverage`, as any filtered run does). The first red run also showed that the background fixture holds
  no step (no graph declares one: the graph listed only `mission_type:software-dev`, the context answered
  `action_not_found`), so the graph and context scenarios add a `proj` graph that declares the two steps.
- **Verification.** `:contracts:check --continue` green; `:activations:test` 877 tests, 0 failures;
  `:missions-edge:test` 45, 0 failures; `:activations:spotlessCheck checkstyleMain checkstyleTest javadoc` green;
  `:deployable:test --tests '*HexagonalArchitectureTest*' '*RelationSetParityTest*' '*MissionRelationParityTest*'
  '*CharterContractSuite*'`: 764 tests, 0 failures (650 Cucumber cases, 73 ArchUnit, no rule edited).
- **For the profiles mission:** the two WP08 scenarios above now pin the mission kinds' and the step's links;
  `ContextRead` has a sixth component (a five-argument constructor keeps the old one); the guide rows of `artifact` and
  `effective-artifact` name the step reads.

## WP06 review (approved, 2026-11-10)

- Mutation probe: 4 of 4 killed (the mission-kind filter, the step-node link, the context's `artifact` target, the
  `MISSION_TYPE` gate).
- Follow-ups for the PR's Deferred section:
  - a step link is emitted when the closure holds the step (`StepHolding`), not when the winning mission type declares
    it, so a pack whose graph declares a step its winning type lacks gets a link that answers `404 action_not_found`.
    This conflicts with the guide's "Availability is a link". Gate the links on the winner declaring the step (cheap for
    the context, one holding read per type for the graph). The schemas note it meanwhile;
  - only scenarios kill a re-added mission-kind filter in `EffectiveReadMapper`'s `@Autowired` constructor. An optional
    unit test is listed.

## WP07 T025

- **The walk.** `scripts/usability/service-usability-run.sh` gains a mission type section (walks 3 and 4), added
  beside the profile walks, which are unchanged; `main` calls the two new walks after them and `summary` prints a
  `MISSION` line per scope before `RESULT`. Pack-scoped: root `artifacts` (`packId=built-in`, `kind=mission_type`) →
  the list item's `self` → 13 step `self` links (the 8 in sequence checked in order) → `step-contract` where present →
  each expected output's `skeleton` → the 4 listed skeletons' `self`. Under the activation: the activation's
  `artifacts` (`kind=mission_type`) → `effective-artifact` → the same. Expected ids are the `MT_*` constants; a set
  that misses or exceeds them fails a hop naming the relation. The section has its own list finder (`find_artifact`):
  the sibling's `find_item` is profile-specific and was left as it is.
- **Graph and action context.** The activation links only `self` and `artifacts`, so no relation leads to the graph or
  to the action context. The walk takes both paths from the contract, appended to the activation's `self` href, under
  the named workaround `unlinked-graph-and-context` (README table). From there it follows the `effective-artifact` of
  every `action:software-dev/*` node (6 at the pin: implement, plan, retrospect, review, specify, tasks; specify is
  required) and the specify context's `effective-artifact` and `artifact`. This gap is the activation set's, not this
  mission's.
- **Self-check.** A "mission type walk" group (7 checks: the set helpers, `mt_steps`, and `mt_expect` naming the
  relation): `--self-check` is 37/37.
- **The run** (jar built from `2634cfcb` with one `--no-daemon` Gradle command, then `java -jar` through `--jar`; the
  script stopped the service):
  `RESULT PASS hops=131/131 failed=0 workarounds=5 unheld=7 unlinked=15 p95=320/348ms commit=07abe2e6 pin=a2a93794`;
  `MISSION pack ... steps=13/13 contracts=5/5 bound-skeletons=2/2 skeletons=4/4 hops=34/34`, the same under the
  activation (held by `built-in`), graph and context 11/11. SC-001 holds in both scopes. Recorded in
  `docs/plans/engineering-notes/service-usability-run-2026-11-10-mission-types.md`.

## WP07 T025 review (approved, 2026-11-10)

- Folded: `mt_expect` fails on a repeated item, with a self-check case (38/38); the README says the action context path
  is always built; the note says `07abe2e6` is tree-identical to `87b033d8`.
- Deferred to the PR's Deferred section:
  - the activation links neither `graph` nor an action-context template (`unlinked-graph-and-context`, filed as #469). Its owner is the
    activation set; the draft issue goes to the orchestrator;
  - the walk counts a contract by its `step-contract` link answering, not by its body naming the step's action.

## Operator approvals recorded after the fact (pre-PR squad, 2026-11-10)

- `ArtifactReadServiceTest`: the profiles mission's `theListedKindsAreTheOnesTheListNamesAndInThisMissionTheServedOnes`
  (`listed() == SERVED`) became `theListedKindsAreTheOnesTheListNamesTheServedOnesThenTheMissionKinds`
  (`listed() == LISTED`). Our `eachListedKindHasItsNotFoundCode` skips `AGENT_PROFILE`. Operator-approved at the merge
  (commit a351af56, "Fix both").
- `ReservedRefusalCodesTest`: its `NamesEveryCode` fixture gains the two new constants (WP02). This is fixture-forced
  and additive: the test requires the probe to name every enum value. No assertion changed.

## Pre-PR fold (code)

- **D12, the ring refactor.** The activations slice answers only what it owns. `EffectiveReadService.missionTypeWinner`
  (`EffectiveMissionTypeWinner`: the winning type as a kernel `CharterArtifact`, the holder's configured id, the merged
  closure's step contracts bound to the type, each its own winner, by id in code-point order, and the source
  fingerprint) is built from one session's existing reads: `merged()` (whose `MergedSnapshot` now carries the merge's
  `bindings`, filled by `ClosureViewSession.mergedOf` from `ClosureView.MergedView.bindings()`; a four-argument
  constructor keeps the old shape) and `effectiveArtifact`. `:missions-edge` composes it:
  `EffectiveMissionTypeReadService` asks `ActivationMissionTypes` (adapter `missions.infra.activations`) for the winner
  and `PackMissionTypes` (adapter `missions.infra.packs`, over `MissionTypeHoldingRead`) for the holder's skeletons and
  step files, refuses `stale_input` when the two fingerprints differ, and binds steps to the merged contracts, narrowed
  by the new `StepContracts` port (adapter `PacksStepContracts`, the one class that names `MissionStepContract`).
  Removed: `HeldMissionType`, `HeldBytes`, `EffectiveMissionTypeAnswer`, the default `PacksSession.missionType`,
  `ClosureViewSession`'s `Holdings`, the second `PacksAvailability` constructor, `FakeAvailablePacks`' mission hook,
  `ClosureView.winner`, `EffectiveMissionTypeHolding`, `EffectiveReadServiceMissionTypeTest` and
  `ClosureViewSessionMissionTypeTest`. Their intent lives in `EffectiveReadServiceMissionWinnerTest` (the project's
  copy wins; contracts bound to the type from whichever pack holds them; one session), `ClosureViewSessionBindingsTest`
  (bindings and the winner of a real merge) and the missions-edge tests (files from the winner's holder, merged
  contracts, `stale_input`). One behaviour difference, deliberate: the winner is read through `effectiveArtifact`, so
  the mission reads resolve the in-force states once per request, as `getEffectiveArtifact` does; a closure whose
  in-force resolution refuses (an `overrides` cycle among artifacts in force) now refuses them too.
- **D11.** `getEffectiveMissionStepContract` (literal route in `EffectiveMissionTypesController`; activations side
  `EffectiveReadService.stepContractWinner`). Links: `self`, `collection`, `mission-type` when the closure holds the
  type (the merge's binding), `step` when the winning type declares the step (steps or on-demand steps), `artifact` to
  the winner's pack read. **Candidate relation (decision):** under an activation a delegation candidate of a listed kind
  the closure holds links `effective-artifact` (its read under the activation, generic or literal), never `artifact`:
  the scope's own read, as a pack's contract links the pack read. `MissionStepDelegationCandidate` says which relation
  each scope uses; the guide rows of `artifact` and `effective-artifact` name the step contract read. A step's
  `step-contract` link under an activation now lands on the literal read.
- **Existing assertions of this mission changed (ours, WP05):** `effective-mission-types.feature`, the scenario "The
  step contracts are read under the activation by the generic effective read, and an unreached one is not found" is
  replaced by the two D11 scenarios (the URL now answers the envelope, and an unheld id is
  `mission_step_contract_not_found`, not `artifact_not_found`); in the step scenario (US4 AS2) the followed
  `step-contract` answer's `/artifactId` became `/id`. `EffectiveMissionTypesControllerTest`'s first test expected the
  stand-in generic route to answer a step contract; it now expects the literal answer, and a directive is the control
  the generic route still answers. No assertion of origin/main or of the profiles branch changed.
- **Red first.** The D11 scenarios were committed (`84e70a4c`) and the Cucumber suite run against the service before the operation:
  688 tests, 3 failed (the two D11 scenarios and the step scenario; the generic read answered without `/id`, with an
  `ETag`, and without `mission-type`). After: 688, 0 failed.
- **Removal cascade (squad major).** `ArtifactRemovalServiceTest` gains a recording gate (asked exactly `REMOVE
  mission_type:documentation` and each `REMOVE template:documentation/...`), a gate refusing
  `template:documentation/spec-template.md` (refused, files and revision unchanged) and a preset naming the type
  (rewritten without it). Mutants: "authorize only the type" is killed by the first two. "`rewritten(current,
  List.of(artifact))`" survives and is equivalent: a template is no activatable kind, so no preset can name a skeleton
  (`Preset.ACTIVATION_KINDS`, which the third test pins). "No preset rewrite" is killed by the third test and two older
  ones.
- **D9 link encoding.** `MissionLinks` writes `;` as `%3B` (a controller test links and reads back `x;y.md`). A `\` is
  written `%5C`, as any character a segment may not hold (decision: encode, the simplest honest rule); the embedded
  Tomcat refuses an encoded backslash, so such a skeleton is linked but not readable over HTTP. No known pack holds one.
  Deferred.
- **Cleanups.** `MissionLinks` builds every href from the generated `*Api.PATH_*` templates (and so do
  `ActivationMapper.effectiveMissionStep`/`packMissionStep`, strictly encoded); its unused pack-level methods are gone.
  Code-point order is one helper per ring that needs it (`missions.application.CodePoints`; the packs ring keeps
  `MissionTypeHoldingRead`'s; the activations ring sorts `ArtifactRef`s, which order by code point). Mission type
  narrowing is `MissionTypeNarrowing`, the skeleton naming `MissionTypeHolding.StoredSkeleton.ofTemplate`. The stale
  "no logic yet" comment, the `RefusalCode` "Reserved for #99" Javadoc and the long `ArtifactController` Javadoc line
  are fixed. The 404 of each read under an activation names only its own codes (four responses).
- **Version header parity (decision).** The effective mission reads keep `Charter-Source-Fingerprint` and send no
  `ETag`. An `EffectiveTag` would need new `Read` values in the activation set's domain (one per mission read, so a tag
  never names two representations) and conditional-request support (`If-None-Match`, `304`) on four operations: not
  cheap, and not this fold's. The unused `stored`/`governanceFingerprint` fields went with D12. Deferred: an ETag on the
  four effective mission reads.
- **A commit slip.** `b542c3b8` ("style(packs): format the mission type removal tests") also carries the D12 removals
  and the restored profiles files, which were staged when it was made; `8523d3bf` holds the rest of D12. The two
  together are the D12 change; neither compiles alone. Left as is (no amend or rebase).
- **Mutation probe of the fold:** 7 of 7 killed in `:missions-edge` (no stale check; the holder's own contracts; files
  not from the holder; no `artifact` link; every candidate linked; a step linked whenever the type is held; `;` not
  encoded), plus the removal mutants above.
- **Profiles-owned activations files against `origin/issue-407-agent-profile-reads`:** `PacksSession`,
  `PacksAvailability`, `FakeAvailablePacks` and `ClosureView` are identical. Remaining differences, all this mission's:
  `EffectiveReadService` (`ContextRead.missionTypePack`, WP06; `missionTypeWinner`, `stepContractWinner`, `winnerOf`),
  `MergedSnapshot` (`bindings`), `ClosureViewSession` (fills `bindings`), `ActivationMapper` and `EffectiveReadMapper`
  (WP06 step links), `EffectiveReadServiceTest`/`EffectiveReadMapperTest` (WP06), and the new
  `EffectiveMissionTypeWinner`, `EffectiveStepContractWinner` and their tests.
- **Verification.** `:contracts:check --continue` green; `:missions-edge:build` green (58 tests); `:activations:test`
  875, 0 failures (plus checkstyle, javadoc, spotless); `:packs:test` 4542, 0 failures (plus spotless, checkstyle);
  `:deployable:test --tests '*HexagonalArchitectureTest*' '*MissionsEdgeWiringTest*' '*MissionRelationParityTest*'
  '*RelationSetParityTest*' '*CharterContractSuite*'`: 767 tests, 0 failures (688 Cucumber cases, 73 ArchUnit, no rule
  edited).

## Landing rulings mirrored (#470)

The landing of agent-profile-reads (PR #470) made four rulings; this fold mirrors them for the mission-type reads.

- **A pack that changes mid-read is `stale_input`** (as `a58a7555`). `EffectiveMissionTypeReadService` reads the
  winner under the activation, then the holding pack's holding. A holding pack that is configured no longer
  (`pack_not_found`) or no longer holds the type (`mission_type_not_found`) at that second read is now refused
  `stale_input`, with the first refusal as its cause, instead of leaking a refusal of a pack the request did not name.
  `pack_unreadable` passes unchanged: the second read reads step files the first does not, and a file that stays
  unreadable is no change that reading again would cure. Test first:
  `aHolderThatLeftTheConfigurationOrNoLongerHoldsTheTypeAtTheSecondReadIsStaleInputForEachRead` failed
  (`expected: STALE_INPUT but was: PACK_NOT_FOUND`), then passed with the fix. `getEffectiveMissionStepContract`
  makes one read of the activation's session, which the session verifies itself, so it has no second read to guard.
- **An internal fault names the read that lacks its data** (as `200a9e78`). Narrowing a winner that is no mission type
  or no step contract, a template outside its type's directory, a step file that is not UTF-8, and a merged winner the
  session could not read (`missionTypeWinner`, `stepContractWinner` in `:activations`) each name the read, for example
  `The read of the mission type software-dev under activation a-1 holds mission_type:software-dev as a ..., not a
  mission type.`
- **The contract says how the pack and activation reads differ** (as `12fba990`), on the four activation-scoped
  paths and in `docs/api/service-contract.md`: under an activation the type is the layer-merge winner; its steps,
  skeletons and step files come from the pack that holds the winner; its step contracts are each the merged closure's
  winner bound to the type, possibly from another pack; a pack-scoped read answers the requested pack's own copy. The
  type path no longer calls its answer one verified read: it is two reads at one fingerprint.
- **No entity tag can collide** (as `f122d100`). The four literal routes send no `ETag` and ignore `If-None-Match`,
  so no tag of theirs exists for another route to match. The literal paths win for every request to a
  `mission_type` or `mission_step_contract` item, a conditional `GET` (`If-None-Match: *`) and a `HEAD` included:
  `aConditionalOrHeadRequestReachesTheLiteralPathsTooSoNoGenericTagCanAnswerThem` pins a `200` without an `ETag` and
  without the generic body. The generic route's tag (`EffectiveTag.Read.ARTIFACT`) is therefore never sent for these
  kinds; its Javadoc, which still excluded agent profiles after `f122d100`, now says it covers every kind.

## Deferred (for the PR body)

- **Registration hole.** A whole-pack replace that drops a mission type still bound by a step contract, or named by an
  edge on one of its action nodes, is not refused `409` (`PackRegistrationService` uses `scan`, not `scanRemoval`);
  draft issue `research/followup-registration-mission-type-referrers.md`. Reason: registration is outside this
  mission's removal scope.
- **No `state` on the activation-scoped mission bodies.** A client reads it from the effective list or
  `getEffectiveArtifact`. Reason: a body change beyond the stored representation; needs its own ruling.
- **Malformed `activationId` is the shared bare 400** on the four effective mission routes, unlike
  `EffectiveProfileProblems`. Reason: a scoped `@RestControllerAdvice` is a follow-up, not needed for the reads.
- **Step links are gated on `StepHolding`**, not on the winning type declaring the step, so a graph step the winner
  lacks is linked and answers `404 action_not_found`. Reason: the check costs a holding read per type in the graph
  read; the schemas say what such a link answers.
- **Only scenarios kill a re-added mission-kind filter** in `EffectiveReadMapper`'s constructor. Reason: an optional
  unit test, listed by the WP06 review.
- **No `ETag` on the four effective mission reads** (they send `Charter-Source-Fingerprint`). Reason: needs new
  `Read` values in the activation set's domain and `If-None-Match`/`304` on four operations.
- **A `\` in a skeleton name** is linked as `%5C`, which the embedded Tomcat refuses, so such a skeleton is not readable
  over HTTP. Reason: no known pack holds one.
- **The usability walk counts a step contract by its `step-contract` link answering**, not by its body naming the
  step's action. Reason: walk depth; the scenarios pin the binding.
- **The activation links neither the graph nor an action-context template** (`unlinked-graph-and-context`, #469).
  Reason: the activation set owns that resource.
- **The nightly mutation floor of `:missions-edge`** (85) is not measured in-mission. Reason: the nightly runs after
  the merge; in-mission mutation probes were run by hand instead.
- **`charter-reports` Javadoc grouping**: edge `*.config` packages land under "Other Packages" (profiles edge too).
  Reason: the `-group` entries match exact packages; a reports-wide fix.
- **The base64 skeleton encoding and the lowest-id tie-break** between two step contracts bound to one step are pinned
  by unit tests only. Reason: the inline fixture step writes UTF-8 only, so no scenario can store non-UTF-8 bytes; the tie-break has no
  scenario fixture.
- **Out of scope (spec):** pack-level generic asset templates (D7); the composed step prompt with ADR-0066 fragments
  (#438); moving `NodeRef` to the kernel (#438 phase 2); the action-context scoping of required directives (#450's
  first half); the pack structure fix for misplaced skeletons (spec-kitty/spec-kitty-open-packs#180).
