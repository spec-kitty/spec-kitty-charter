# Code grounding: mission type, step, step contract and template reads

Read-only pass at `main` `1d962f26` (2026-11-09), open-packs at the current pin `22c14b2d` and the bumped pin
`a2a9379`. Scope: issues #99, #148, #96 (for `mission_type`, `mission_step_contract`, `template`), #100 and #450 (item
links for these kinds). The operator decisions in the dispatch brief bind this mission and are not re-opened here.

## Target surfaces

| Surface | File | Size | Role |
|---|---|---|---|
| Generic artifact reads | `service/packs/.../packs/application/ArtifactReadService.java` | 553 lines | `SERVED` (5 kinds), `ABSENT`, sealed `Item`, `servedItem` switch, `kind_not_found` |
| Generic controller | `service/packs/.../packs/infra/api/ArtifactController.java` | 188 lines | implements generated `ArtifactsApi`; `{kind}/{artifactId}` |
| Wire mapping | `service/packs/.../packs/infra/api/ArtifactDtos.java` | | switch over `Item` |
| Links | `service/packs/.../packs/infra/api/Hypermedia.java` | | **package-private**; encodes each segment with `UriUtils.encodePathSegment` |
| Mission domain | `service/missions/.../missions/domain/` | 19 files | `MissionType`, `MissionStep`, `StepDocument`, `TemplateReference`, `ExpectedArtifact` |
| Step contract, template | `service/packs/.../packs/domain/MissionStepContract.java`, `Template.java` | | live in packs.domain |
| Storage | `packs/infra/storage/KindBindings.java`, `MissionTypeBinding`, `MissionStepContractBinding`, `PackFileSet.templateId` | | done on main |
| ArchUnit | `service/deployable/src/test/.../HexagonalArchitectureTest.java` | 1317 lines | `APPLICATION_IS_EDGE` (packs.application only), `ENTRYPOINT_STAYS_IN_ITS_SLICE` (packs.infra.api may not name missions.domain) |
| Contract | `contracts/charter/openapi.yaml`, `schemas/Artifact.yaml`, `ArtifactKindToken.yaml`, `parameters/ArtifactKind.yaml`, `paths/packs_packId_artifacts_kind_artifactId.yaml`, `changes/` | | ADR-0022, ADR-0047 |

## Facts verified at HEAD

- `ArtifactKind` already has `MISSION_TYPE`, `MISSION_STEP_CONTRACT` and `TEMPLATE` (`TEMPLATE` is not activatable).
  `Pack.artifacts(kind)` returns them generically, so the list page needs no new storage.
- Refusal codes: `mission_type_not_found` exists and is raised by the action-context walk. There is **no**
  `mission_step_contract_not_found`, `template_not_found` or step-level not-found code. `CharterRefusalCode.yaml` is
  currently owned by the ADR-0066 phase 1 run (#437); our hunk there must be minimal.
- Software-dev at `a2a9379`: 18 step prompt/guidelines files; step contracts `specify`, `plan`, `tasks`, `implement`,
  `review` carry `mission: software-dev` and `action: <step>`; contract steps `delegates_to` directives and tactics, so a
  step contract can link onward to the already-served kinds.
- Templates: `software-dev/{spec,plan,tasks,task-prompt}-template.md` plus pack-level `writing/meeting-minutes.md`.

## Gradle module placement (finding, needs a decision)

`:packs` declares `api(project(":missions"))` (the store binds `MissionType`). The operator's rings for the missions
slice (`missions.application`, `missions.infra.api`, `missions.infra.packs` over `packs.application`) therefore cannot
live in the `:missions` Gradle module: it would be a project cycle. The activations slice has its own module that
depends on `:packs`, which is why its adapter works. The profiles slice has the same shape (`:packs` api-depends on
`:profiles`), so the sibling profiles run meets the same wall.

Proposal: a new Gradle module (ruled: `:missions-edge`, see `operator-decisions.md` D6) that holds the
missions slice's application, inbound and packs-adapter packages, depends on `:missions`, `:packs`, `:charter-api` and
`:web-transport`, and is wired into `:deployable`. The packages stay `ai.speckitty.charter.missions.*`, so the
ArchUnit slice rules apply unchanged (no JPMS in the build). Coordinate the naming with the profiles run.

## Template ids contain a slash (finding, contract decision)

`PackFileSet.templateId` gives `<type>/<path>` for a mission type's template and `<path>` under `templates/` for a
pack-level one, for example `software-dev/spec-template.md` and `writing/meeting-minutes.md`. Every template id holds at
least one `/`, some hold two. A single `{artifactId}` path segment cannot carry it: `Hypermedia` would emit `%2F`, which
Tomcat rejects by default, and allowing encoded slashes is a service-wide transport change. Options:

1. Serve a mission type's templates as a sub-resource,
   `/packs/{packId}/artifacts/mission_type/{type}/templates/{name}`, and leave pack-level templates out of this run.
   Deep (two-segment) template paths still need an answer.
2. Keep `template` an ordinary kind and allow encoded slashes in the one `artifactId` variable (transport change, ADR).
3. Keep `template` an ordinary kind and give the wire id a slash-free spelling (a new id rule the packs do not use).

## Hypermedia reuse

`Hypermedia` and `Relation` are package-private in `packs.infra.api`. A missions controller in `missions.infra.api`
cannot use them. Either promote them to `web-transport` (a shared ring every inbound adapter may use) or duplicate a
small link builder. Promotion is the boy-scout choice; it touches the profiles run's files too, so it is coordinated.

## Invariants that must survive

- PROJECT_002 (1): what the reads answer equals the stored pack content (ADR-0036 round trip untouched).
- ADR-0022 / ADR-0047: additive contract change on the 0.x line, a `changes/` fragment, never a direct `CHANGELOG.md`
  edit.
- ADR-0037 amendment: `packs.infra.api` names no missions domain type, hence the literal routes in the missions slice.
- ArchUnit: no rule weakened; new rules added for the new rings (decision 1).
- `ConsistentRead`: every read is digest-verified; refusals keep the pack-before-kind order.

## Interactions

- Sibling profiles run (`issue-407-agent-profile-reads`): owns the pin bump, `ArtifactReadService.SERVED`,
  `ArtifactDtos`, `Artifact` oneOf, `ArtifactKindToken`, and `GET /activations/{id}/artifacts/{kind}/{artifactId}`.
  Every one of those files is on our path; we plug in after it lands or rebase on its branch.
- ADR-0066 phase 1 (#437): `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest`, `ContractDescriptionsTest`,
  `errors.feature`. Phase 2 (#438): the effective step prompt; we serve stored bodies only. `NodeRef` stays put.

## Discovery

- Similar surfaces: the other unserved kinds (`procedure`, `glossary_pack`, `skill`, `anti_pattern`, `asset`, `guard`,
  `lifecycle`, `step_enhancement`) share the same gap. Disposition: out of scope; `agent_profile` is the sibling's. No
  new issue is filed: #96 already tracks artifact bodies for the rest.
- Tracked issues: #99, #148, #96 (partly), #100 and #450 (the item-link half for these kinds). #450's directive-scoping
  half belongs to the action-context walk, not to this run (Refs, not Closes).

## Churn (90 days)

`ArtifactReadService` 10 commits, `ArtifactDtos` 3, the missions domain 3 to 5 per file: active, conflict-prone
surfaces shared with the sibling run.
