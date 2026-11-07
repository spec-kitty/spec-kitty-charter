# Contract delta (sketch)

**Folded 2026-11-07 (phase A delta squad):** the relationship `DELETE` removes a registered edge only
(`DM-01M6TQ62HXYF362GAFS2BZ3PGF`); PR A stays strict (`DM-01M6TQ67C9ZTNGZB9CY28JS54F`); the refusal codes, the
`If-Match` parameter, the `x-refusal` home of the registration codes and the contract paths are corrected. See
`plan.md`, "Fold of the phase A delta squad (2026-11-07)".

The authoritative change lands in `contracts/charter/`, with one fragment per PR in `contracts/charter/changes/`,
before the code that serves it (C-001). This page lists the change so the work packages and the reviewers share one
view. Every change is on the 0.x line (ADR-0022). A break carries a `### Breaking` entry that says what core's seam
changes.

## PR A: registration and removal

### New operations

- **`registerPackFromArchive`: `PUT /packs/{packId}`** (new `paths/packs_packId.yaml`).
  - **Request body.** Required, raw bytes, `application/gzip` (also `application/x-gzip` and
    `application/x-tar+gzip`) or `application/zip`, with `type: string, format: binary`. The `Content-Type` picks the
    format.
  - **Headers** (research R-lock):
    - `If-Match` (`parameters/IfMatch.yaml`, extended to cover a pack revision; there is no `PackIfMatch`) is
      optional: absent means the last writer wins, and stale answers `412`;
    - `If-None-Match: *` makes the request create-only: `412` when the pack exists;
    - an identical archive is an `unchanged` no-op for the `If-Match` check under the lock, because the version rule
      runs first; a stale `If-Match` is a synchronous `412` at acceptance, before any job exists (spec US1 AS6). The
      description says so, citing RFC 9110 §13.1.1.
  - **Answers (asynchronous, A5; folded 2026-11-06):**
    - `202`: accepted. `PackRegistrationStatus` (`pending`), `Location: /api/v1/pack-registrations/{registrationId}`.
      Every answer below except `400`, `401`, `403`, `412` (at acceptance), `413`, `415` and `503` is now the
      `problem` of a `refused` status, not a synchronous answer (the `409` and `422` lists below included); a
      `succeeded` status carries the `PackRegistration` (outcome `created`, `replaced` or `unchanged`) and its revision.
      The `refused` problem also carries `registration_timed_out` (`504`) and `pack_storage_full` (`503`).
    - `503`: the registration queue is full (`Retry-After`), or registration is disabled
      (`pack_source_unavailable`).
    - `400`: both `If-Match` and `If-None-Match` were sent.
    - `401`, `403`.
    - `409`:
      - `pack_version_conflict` (new): same `pack_version`, other content;
      - `registration_conflict`: built-in level, or `pack_id` or name used by another configured pack;
      - `governance_policy_violation`: a change or removal the level forbids;
      - `artifact_referenced` (new): a removed artifact another pack references (operator, Q3).
    - `412`: stale `If-Match`, or `If-None-Match: *` on an existing pack, as a plain `Problem`.
    - `413`: body over 32 MiB, as a plain `Problem`, answered before the body is read.
    - `415`: another `Content-Type`, or magic bytes that do not match it, as a plain `Problem`.
    - `422`:
      - `validation_failed` with `violations` (at most 100): an archive path or entry type refused, over 10,000
        entries or 32 MiB expanded, an unmodelled file, an invalid artifact, a changed `pack_level` or `pack_id`; and,
        from the load of the staged pack (ADR-0064 D5, A7, d5bcf030): a `registered.graph.yaml` without the registration
        stamp (a reserved shard; a stamped one is accepted), a removed field refused as an unknown field (a directive's
        `references`, `procedures` or `relationships`), a preset with `activated_kinds`, a preset item id outside
        `^[A-Za-z0-9][A-Za-z0-9_/.-]*$`, and a `PACK_UNREADABLE` of the load of a staging directory (never `500`);
      - a composition code: a closure containing this pack no longer composes, naming the packs (the impact check,
        IC-15), under ADR-0056 per-operation status;
      - `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`
        (A6, A8, A11, A12, C28).
    - `500`: `pack_unreadable`, `internal_error`.
- **`registerPackFromSource`: `POST /packs`** (new `paths/packs.yaml`).
  - **Request body.** `PackSource` (JSON), guarded by the shared body limit. It names the target `packId`.
  - **Answers.** As `PUT` (`202` and a status), except:
    - the same `If-Match` and `If-None-Match: *` as the `PUT`, applied to the target `packId`; the fetched
      descriptor's `name` must match it;
    - a refused source (a host not allowed, a non-global address, a bad ref, the wrong media type, a digest or
      commit mismatch) ends `refused` with `422 pack_source_refused` and a fixed reason; an upstream failure `502`,
      an upstream timeout `504`; a disabled source kind, or `git` missing or older than 2.50.1, `503
      pack_source_unavailable`.
- **`getPackRegistration`: `GET /pack-registrations/{registrationId}`** (new). `200` with `PackRegistrationStatus`;
  `404 registration_not_found` once forgotten (1 hour after it ends, or after a restart).
  - **`x-refusal` home of the registration codes (ADR-0056).** The codes a `refused` status returns in its `200` body
    are declared on this operation: `x-refusal.<code>.operations.getPackRegistration.status` gives the status the
    `problem` carries (`422` in place of `500` for the eleven composition codes). The two registration operations
    declare only their synchronous answers. This is the planner's recommendation (plan delta squad, finding 9); WP11
    may move it, and must state where it put it in the `x-refusal` text.
- **`getPack`: `GET /packs/{packId}`** (new, FR-039). `200` with the stored pack's `PackRegistration` (outcome
  absent), `ETag`, `_links.self`; `404 pack_not_found`.
- **`deletePackArtifact`: `DELETE /packs/{packId}/artifacts/{kind}/{artifactId}`** (added to the existing item path).
  - **Path file.** The existing `paths/packs_packId_artifacts_kind_artifactId.yaml`, beside `getPackArtifact`.
  - **Header.** `If-Match` on the pack revision is required through `parameters/IfMatch.yaml`, extended to cover a
    pack revision (there is no `PackIfMatch` parameter; the file stays `required: false`, as on the activation
    sub-resource writes, and the operation's description states the `428`). Absent answers `428`, stale answers `412`,
    checked under the registry lock (research R-lock).
  - **Answers:**
    - `204`, with `ETag` (the new revision);
    - `404`: `pack_not_found`, `kind_not_found` (existing) or `artifact_not_found`, each its own code;
    - `409`:
      - `artifact_referenced`, whose `packs` member (at most 100) names the referring packs and whose `violations`
        name every referring artifact, a preset item or a descriptor `overrides` entry included (A10, B3).
        References are judged against the tree after the removal, from the edges, presets and descriptor `overrides`
        of every configured pack (not from `PackCompositionFacts.references`); the data-only fields of ADR-0064 D11
        do not count;
      - `governance_policy_violation` (ADR-0030 D3): the new `REMOVE` operation of the write gate, which fires only
        when an ancestor of the pack holds the id and locks it;
      - `registration_conflict`: the artifact is written in the pack's author-written graph documents (a source
        shard or `drg/fragment.yaml`), which a `DELETE` never rewrites (`DM-01M6TQ62HXYF362GAFS2BZ3PGF`, applied by
        decision `DM-01M6TR1KDAJ64GMJD6AYNZKT59`);
    - `412`, `428`;
    - `422`: PR A stays strict (`DM-01M6TQ67C9ZTNGZB9CY28JS54F`): the closures after the removal fail to compose or
      break a rule (a composition code or one of IC-15's relation codes, naming the packs). #404 (repair writes) is out
      of scope for PR A;
    - `500`.
- **`deletePackRelationship`: `DELETE /packs/{packId}/relationships?from=&to=`** (added to the existing path).
  - **Path file.** The existing `paths/packs_packId_relationships.yaml`, beside `putPackRelationship`.
  - **Parameters.** `from` and `to` are required query parameters, each a `PackQualifiedReference`. A URN may hold
    `/` (`action:<type>/<step>`), so the pair is not a path segment. `If-Match` is as on `deletePackArtifact`.
  - **What it removes (`DM-01M6TQ62HXYF362GAFS2BZ3PGF`).** The edge of the unordered pair, **only from
    `registered.graph.yaml`**, the shard `putPackRelationship` writes. A `DELETE` never rewrites an author file.
  - **Answers:**
    - `204` with `ETag`;
    - `404`: `pack_not_found`, or `relationship_not_found` (new) when no graph document of the pack holds an edge on
      the pair and no derived relation joins it;
    - `409 relationship_not_registered` (new, precise): the pack holds an edge on the pair but not in
      `registered.graph.yaml` (the author wrote it in a source shard or `drg/fragment.yaml`), or it is a derived
      relation (a guard's `implemented_by`, ADR-0064 D7). The fixed reason says to register the whole pack. This
      replaces the old `409 registration_conflict` "the edge is a field of an artifact's own body", which can no
      longer happen (ADR-0064 D1 and D2 dropped the edge-minting fields);
    - `412`, `428`;
    - `422`: PR A stays strict, as for `deletePackArtifact`;
    - `500`.

### New schemas

- **`PackRegistration`**, closed.
  - It holds `packId`, `descriptorPackId`, `name`, `version`, `level` (`PackLevel`, restricted to `org` and
    `project`), `revision` (`Sha256Digest`) and `outcome` (`created`, `replaced`, `unchanged`).
  - It also holds an optional `source`: `PackSourceRecord`, with `kind`, `url`, `ref`, `path` and `commit`.
  - `_links`: `self` (`getPack`), `edit` (the `PUT`) and `artifacts`.
  - `notices` (B4): `floorAdded`, `floorRemoved`, `locksAdded`, `locksRemoved`, and from PR B `activationsAffected`.
- **`PackRegistrationStatus`**, closed: `registrationId`, `state` (`pending`, `running`, `succeeded`, `refused`),
  `packId`, `acceptedAt`, `finishedAt`, `result` (`PackRegistration`), `problem` (`Problem`).
- **`PackSource`**, one closed object (post-plan contract squad): `kind` (`https_archive`, `git`, `filesystem`),
  `packId` (required, `^[a-z0-9][a-z0-9-]{0,62}$`), and per kind `url` + optional `sha256` (https archive), `url`
  + `ref` + optional `commit` (git), `path` (filesystem); a field foreign to the kind is `422`. `url` has
  `format: uri` and the pattern `^https://`. `ref` follows the grammar of research R9. `path` is a relative path
  with no `..` segment.

### `CharterRefusalCode`

- **Added, raised:**
  - `pack_version_conflict` (409);
  - `artifact_referenced` (409);
  - `relationship_not_found` (404);
  - `relationship_not_registered` (409; the relationship `DELETE` of an authored or derived edge);
  - `registration_not_found` (404);
  - `pack_source_refused` (422) and `pack_source_unavailable` (503);
  - `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle` (422);
  - `registration_timed_out` (a `refused` registration at the source deadline plus 30 s, NFR-008; `504`, the
    planner's proposal) and `pack_storage_full` (`503`, less than 2x the archive bound free, NFR-009). Both are
    carried in the `problem` of `getPackRegistration`.
- **Existing, not added:** `kind_not_found` (404, raised; `CharterRefusalCode.yaml` and `RefusalCode.KIND_NOT_FOUND`).
  `deletePackArtifact` is added to its text only.
- **Raised by the new operations,** under ADR-0056's per-operation status (`x-refusal.operations`):
  - `artifact_not_found` (404 on `deletePackArtifact`);
  - `validation_failed` (422);
  - `registration_conflict` (409);
  - `governance_policy_violation` (409);
  - the eleven composition codes (422 on the two registrations).
- `ReservedRefusalCodesTest` follows.

### Other changes

- **`parameters/IfMatch.yaml`** (existing, extended, plan delta squad finding 14): there is no `PackIfMatch`
  parameter, and `putPackRelationship` and the per-kind artifact writes declare `If-Match` inline. The two
  registrations and the two `DELETE`s reuse `IfMatch.yaml`; its description is extended from an `Activation` answer's
  tag to also cover a pack revision's strong tag (its pattern already admits the quoted `sha256:` form). It stays
  `required: false`, as on the activation sub-resource writes, and each `DELETE` states its `428` in its description.
  `PackIfMatch.yaml` and `PackIfMatchRequired.yaml` are not created.
- **The per-artifact registrations' `If-Match` text** (`registerPack{Directive,Paradigm,Styleguide,Toolguide}` and
  `putPack{Directive,Paradigm,Styleguide,Toolguide}`, `POST`/`PUT /packs/{packId}/artifacts/<kind>…`; there is no
  generic `putPackArtifact`): it stays
  optional, and adds "without it, the write is last-writer-wins on the whole pack". This is a description change
  only.
- **The relationship `DELETE`'s `If-Match`** is required as above. `putPackRelationship` is unchanged (already
  required).
- **ADR-0019's table.** `paths/packs.yaml` adds `POST /packs`; `GET /packs/{packId}` and
  `GET /pack-registrations/{registrationId}` are added. The pack collection `GET /packs` stays undeclared.
- **Scenarios.**
  - `scenarios/registration/whole-pack-registration.feature` is new and tagged `@contract`. It covers US1 AS1 to AS7
    and the source kinds, and takes the place of the `@WIP` `register-pack.feature` promises PR-PACK-REGISTER-001
    (git and filesystem kinds) and 002.
  - `trust/source-trust.feature` PR-PACK-TRUST-001, 002 and 004 become `@contract`. PR-PACK-TRUST-003 and 005 stay
    `@WIP`.
  - `scenarios/pack-removal.feature` is new. It covers US2.
  - The lockfile, published-artifact and distribution promises stay `@WIP`.
- **Fragments.** `### Added` for the operations, the schemas and the new codes listed above, and `### Changed` for
  the raised codes.

## PR B: the merge, `listEffectiveArtifacts` and the skipped report

- **`EffectiveArtifact`** changes:
  - it gains required `packId` (configured identifier) and `level` (`PackLevel`): the winner's provenance (FR-013).
    `### Breaking`: new required response members of a closed schema;
  - it gains an optional `satisfiedBy` (`ArtifactReference`): for a required artifact out of force, the active
    artifact that overrides it. It never names an enhancer (operator, Q6 and Q8; research R11);
  - `required` is reworded from "a required artifact is always active" to "a governing preset requires it or a pack
    locks it. A locked one is always active. A required one is active unless an active override of it satisfies the
    requirement".
- **`EffectiveArtifactReason`**:
  - `kind_default` is removed (FR-022, ADR-0053 d10). `### Breaking`.
  - `overridden`, `mission_type_not_in_force` and `enhanced_target_not_in_force` are added. The last one means an
    enhancer none of whose targets is in force (operator, Q8).
  - The description states the precedence.
- **`SkippedReport`** gains seven required buckets. `### Breaking`: a write's `ActivationChange.skipped` answers them
  as `[]`.

  | Bucket | Item |
  |---|---|
  | `staleIds` | `ArtifactReference` |
  | `denyNotApplied` | new `DenyNotApplied {kind, artifactId, reason: required \| locked}` |
  | `languageFiltered` | `ArtifactReference` |
  | `overridden` | new `OverriddenArtifact {kind, artifactId, overriddenBy: ArtifactReference}` |
  | `missionTypeNotInForce` | `ArtifactReference` |
  | `overrideNotApplied` | `OverriddenArtifact`: an override of a locked artifact, not applied |
  | `enhancedTargetNotInForce` | new `EnhancerWithoutTarget {kind, artifactId, targets: [ArtifactReference]}` |

  Two existing buckets change their wording:
  - `skippedByScope` stays the write report of the `cascade` scope; its description says it is always empty at read
    (the kind-gate reading is gone with IC-20) (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`);
  - `skippedDeactivated` says: at read, `viaRequires` is false and `requiredBy` is empty.
- **`listEffectiveArtifacts` and `getActivationSkippedReport`:**
  - `x-reserved` is removed, and `501` leaves their responses.
  - Their descriptions say what is listed (operator, Q1): each artifact of the merged closure with its state, and
    "in force" is `state=active`.
  - The `ETag` covers the revision and the fingerprint (`EffectiveETag`, wording unchanged).
  - `500 OfferingUnreadable` documents `pack_unreadable` for an override the mutability level forbids and for an
    `overrides` cycle among active artifacts.
  - `ActivationSummary.skipped` is "entries across the skipped report's buckets".
  - `ActivationSummary.required` loses "always active". It counts the required and locked artifacts, in force or
    satisfied.
- **The relaxed write rule (IC-13, operator Q9; research R12).** `### Changed`: a write now accepts more.
  - `createActivation`, `replaceActivation` and `deactivateActivationArtifact`: the descriptions say that a deny
    entry on a required, not locked artifact is accepted when an override of it is active in the document as
    stored after the write. Otherwise it is `409 artifact_required`. A locked artifact is always
    `409 artifact_locked`.
  - `responses/ArtifactPolicyRefused.yaml` and the `CharterRefusalCode` text of `artifact_required`:
    "…required by a governing preset, and no active override of it satisfies the requirement".
  - Apply-preset with `force` and select with `includeDeactivated` say the same of the deny entries they add.
- **Pins.** `activations.feature` loses two `@reserved` pins, and `ReservedOperationCoverageTest` goes 4 → 2.
- **Examples.**
  - `EffectiveArtifactList.first-page` gains `packId` and `level`.
  - `ActivationSkippedReport.nothing-skipped` gains the empty buckets.
  - A new `ActivationSkippedReport.each-bucket` is added.
- **Scenarios.**
  - `scenarios/layer-merge.feature` covers US3 AS1 and US6.
  - `scenarios/effective-reads.feature` covers US3 AS2 to AS7 and US4. It adds:
    - a required `T` satisfied by an active override;
    - an enhancer alone not satisfying a required `T`;
    - an enhancer with no target in force;
    - an override of a locked `T` not applied.
  - `activation-register.feature` and `activation-selections.feature` gain IC-13's write scenarios.
- **Fragments.** `### Breaking` for the reason removed and the required members, `### Added` for the two reads,
  and `### Changed` for the relaxed write rule.

## PR C: the effective graph and the action context

- **`EffectiveGraph`** gains required `layerOverrides: [LayerOverride]` (FR-018). `### Breaking`.
- **`LayerOverride`** (new, closed): `artifact` (`Urn`), `winner {packId, level}` and `loser {packId, level}`. One
  record per losing version, and only for artifacts in force.
- **`EffectiveGraphEdge`.** The description adds: the edge resolves through layer overrides; an edge from an
  overridden version is absent, and an edge to it ends at the winner.
- **`EffectiveActionContext`.**
  - The description says `artifacts` is ADR-0018's walk from the action over the in-force merged graph, with active
    enhancers.
  - `skipped` uses the extended `SkippedReport`. A mission type not in force answers `artifacts: []` with the type
    in `missionTypeNotInForce`.
- **`getEffectiveGraph` and `getEffectiveActionContext`:**
  - `x-reserved` is removed, and `501` leaves their responses.
  - `action_not_found` becomes raised: `404` on `getEffectiveActionContext`, judged against the merged closure.
- **Pins.** `ReservedOperationCoverageTest` goes 2 → 0. `docs/api/service-contract.md` loses its "reserved" rows
  for the four reads.
- **Examples.** `EffectiveGraph.one-edge` gains `layerOverrides: []`, and a new `EffectiveGraph.layer-override` is
  added.
- **Scenarios.** `scenarios/effective-reads.feature` gains US5 AS1 to AS5.
- **Fragments.** `### Breaking` for the required `layerOverrides`, and `### Added` for the two reads.

## Not changed

- `GET /offering` and every pack read (FR-023, SC-004).
- `ActivationConfig`.
- The per-artifact registrations' shapes and statuses (`POST`/`PUT /packs/{packId}/artifacts/<kind>…`,
  `PUT …/relationships`). Only the `If-Match` description changes, as above.
- `contracts/packs/` (the archive is the pack directory as authored).

## Fold of 2026-11-06 (ruleset rulings and post-plan contract squad)

- **PR A0 (#399 behaviour; its own PR, `DM-01M6RFGB9NVD9MDWZ6AP0PBYH7`).** `### Changed`: `replaceActivation` judges the floor
  only on added deny entries and drops stale ids into `dropped` (A4); `includeDeactivated` clears only what the walk
  reaches (B5). `### Breaking`: an unsatisfiable version pin is dropped into `ActivationChange.droppedPins`
  (`{packId, version}`, declared in A0 by WP01) instead of `422 version_not_held` on replace, the per-artifact writes
  and apply-preset (B1); `createActivation` keeps `422 version_not_held` for a pin in its own body
  (`DM-01M6RN5Y406SB4KA13QYMQ2P8J`).
- **PR B.** `ActivationChange.warnings` (B6/B7), `Activation.denyNotApplied` (B4), `SkippedReport.pinNotHeld`,
  `requiredNotHeld` and `requiredNotInForce` (B1, A10, the bound and ungrounded floor),
  `PackRegistration.notices.activationsAffected`. `SkippedReport.skippedByScope` stays (the write report of the
  `cascade` scope, always empty at read; `DM-01M6RN5MTWC98PXEVJNFQSRRJX`).
- **PR A refusal codes (post-tasks squad; now also in the "Added" list above, 2026-11-07).** `registration_timed_out` (the job's end state at the source deadline +
  30 s, NFR-008), `pack_storage_full` (`503`, less than 2× the archive bound free, NFR-009), `registration_not_found`
  (`404`). `PUT`/`POST /packs` answer no `428`, except a git source naming a branch ref without a `commit` and without
  `If-Match` (`428`).
- **Paging.** `listEffectiveArtifacts` uses a keyset cursor over (`kind`, `id`) bound to the `ETag`; a cursor from
  another tag is `400`. `summary` is computed over the whole set and is identical on every page.
- **Breaking declarations.** Each PR's fragment carries one `### Breaking` block listing all its breaks.
- **A7, the kind model (IC-20, slice A0; breaking, allowed on 0.x with no `contract-charter-v*` baseline).**
  - `activatedKinds` and `missionTypes` are removed from `ActivationDocument`, `CreateActivationRequest`,
    `ReplaceActivationRequest` and `ActivationChange`; sent on a write, each is `422 validation_failed` with a
    violation naming `selected.mission_type` (refused now, no deprecation window).
  - `selected` and `deactivated` gain `mission_type`; the kind enums merge into one schema; preset schemas accept
    every kind, and the preset key `activated_kinds` is refused at registration.
  - `EffectiveArtifactReason` drops `kind_not_activated`; `SkippedReport.skippedByScope` stays as the `cascade`
    scope's write report, empty at read (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`).
  - The document `schemaVersion` becomes 2 (upgrade on read; every `ETag` changes once).
  - Corpus: `valid/02`, `valid/03`, `valid/08` and `invalid/14` rewritten; new invalid cases for each removed member.
    DIV-203 and DIV-501 updated; a new DIV for core's `activated_kinds` narrowing.
  - Core: regenerate the strict DTOs before calling a server with this change; the migration note is handed over
    (C-003).
