# Contract delta (sketch)

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
    - `If-Match` (`PackIfMatch`) is optional: absent means the last writer wins, and stale answers `412`;
    - `If-None-Match: *` makes the request create-only: `412` when the pack exists;
    - an identical archive answers `200` (`unchanged`) even under a stale `If-Match`, because the version rule runs
      first. The description says so, citing RFC 9110 §13.1.1.
  - **Answers:**
    - `201`: created. `PackRegistration`, `ETag` (revision), `Location: /api/v1/packs/{packId}`.
    - `200`: replaced, or unchanged when the revision is identical. `PackRegistration` and `ETag`.
    - `400`: both `If-Match` and `If-None-Match` were sent.
    - `401`, `403`.
    - `409`:
      - `pack_version_conflict` (new): same `pack_version`, other content;
      - `registration_conflict`: built-in level, or `pack_id` or name used by another configured pack;
      - `governance_policy_violation`: a change or removal the level forbids;
      - `artifact_referenced` (new): a removed artifact another pack references (operator, Q3).
    - `412`: stale `If-Match`, or `If-None-Match: *` on an existing pack, as a plain `Problem`.
    - `413`: body over 32 MiB, as a plain `Problem`, answered before the body is read.
    - `415`: another `Content-Type`, as a plain `Problem`.
    - `422`:
      - `validation_failed` with `violations` (at most 100): an archive path or entry type refused, over 10,000
        entries or 32 MiB expanded, an unmodelled file, an invalid artifact, a changed `pack_level` or `pack_id`;
      - a composition code: the stored set no longer composes with this pack, under ADR-0056 per-operation status.
    - `500`: `pack_unreadable`, `internal_error`.
- **`registerPackFromSource`: `POST /packs`** (new `paths/packs.yaml`).
  - **Request body.** `PackSource` (JSON), guarded by the shared body limit.
  - **Answers.** As `PUT`, except:
    - the same `If-Match` and `If-None-Match: *` as the `PUT`, applied to the pack the fetched descriptor's
      `pack_id` names;
    - `Location` on both `201` and `200`;
    - a refused source (a host not allowed, a non-global address, unreachable, a bad ref, the wrong media type, the
      git binary missing or too old) is `422 validation_failed` with the guard's reason. A refused `guideUri`
      answers the same way.
- **`deletePackArtifact`: `DELETE /packs/{packId}/artifacts/{kind}/{artifactId}`** (added to the existing item path).
  - **Header.** `If-Match` on the pack revision is required, through the new pack-specific parameter
    `parameters/PackIfMatch.yaml`, not the activations' `IfMatch.yaml`. Absent answers `428`, stale answers `412`,
    checked under the registry lock (research R-lock).
  - **Answers:**
    - `204`, with `ETag` (the new revision);
    - `404`: `pack_not_found`, `kind_not_found` or `artifact_not_found`;
    - `409`:
      - `artifact_referenced`, whose `packs` member (at most 100) names the referring packs. When the referrer is
        another artifact of the same pack, `detail` and `violations` name that artifact (operator, Q2);
      - `governance_policy_violation` (ADR-0030 D3);
    - `412`, `428`;
    - `500`.
- **`deletePackRelationship`: `DELETE /packs/{packId}/relationships?from=&to=`** (added to the existing path).
  - **Parameters.** `from` and `to` are required query parameters, each a `PackQualifiedReference`. A URN may hold
    `/` (`action:<type>/<step>`), so the pair is not a path segment.
  - **Answers:**
    - `204` with `ETag`;
    - `404`: `pack_not_found` or `relationship_not_found` (new);
    - `409 registration_conflict`: the edge is a field of an artifact's own body;
    - `412`, `428`;
    - `500`.

### New schemas

- **`PackRegistration`**, closed.
  - It holds `packId`, `descriptorPackId`, `name`, `version`, `level` (`PackLevel`, restricted to `org` and
    `project`), `revision` (`Sha256Digest`) and `outcome` (`created`, `replaced`, `unchanged`).
  - It also holds an optional `source`: `PackSourceRecord`, with `kind`, `url`, `ref`, `path` and `commit`.
  - `_links`: `edit` (the `PUT`) and `artifacts`. There is no `self`, because `GET /packs/{packId}` is not
    declared.
- **`PackSource`**, a `oneOf` discriminated on `kind`:
  - `https-archive {url}`;
  - `git {url, ref}`;
  - `filesystem {path}`.

  Each is closed. `url` has `format: uri` and the pattern `^https://`. `ref` follows the grammar of research R9.
  `path` is a relative path with no `..` segment.

### `CharterRefusalCode`

- **Added, raised:**
  - `pack_version_conflict` (409);
  - `artifact_referenced` (409);
  - `relationship_not_found` (404).
- **Raised by the new operations,** under ADR-0056's per-operation status (`x-refusal.operations`):
  - `artifact_not_found` (404 on `deletePackArtifact`);
  - `validation_failed` (422);
  - `registration_conflict` (409);
  - `governance_policy_violation` (409);
  - the eleven composition codes (422 on the two registrations).
- `ReservedRefusalCodesTest` follows.

### Other changes

- **`parameters/PackIfMatch.yaml`** (new): a strong entity tag of the pack revision. It is used by the four new
  operations, which keep their own `required` flag.
- **The per-artifact registrations' `If-Match` text** (`POST`/`PUT /packs/{packId}/artifacts/<kind>…`): it stays
  optional, and adds "without it, the write is last-writer-wins on the whole pack". This is a description change
  only.
- **The relationship `DELETE`'s `If-Match`** is required as above. `putPackRelationship` is unchanged (already
  required).
- **ADR-0019's table.** `paths/packs.yaml` adds `POST /packs`. The pack collection `GET /packs` stays undeclared.
- **Scenarios.**
  - `scenarios/registration/whole-pack-registration.feature` is new and tagged `@contract`. It covers US1 AS1 to AS7
    and the source kinds, and takes the place of the `@WIP` `register-pack.feature` promises PR-PACK-REGISTER-001
    (git and filesystem kinds) and 002.
  - `trust/source-trust.feature` PR-PACK-TRUST-001, 002 and 004 become `@contract`. PR-PACK-TRUST-003 and 005 stay
    `@WIP`.
  - `scenarios/pack-removal.feature` is new. It covers US2.
  - The lockfile, published-artifact and distribution promises stay `@WIP`.
- **Fragments.** `### Added` for the four operations, two schemas and three codes, and `### Changed` for the raised
  codes.

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
  - `skippedByScope`'s description and `x-derived` rule say: at read, outside `activatedKinds` (the ADR-0059
    follow-up);
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
