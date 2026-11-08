### Added

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
    `PUT`'s, with the one `428` of a registration: a git branch ref that names no commit, sent without `If-Match`
    (FR-004).
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
    revision as its `ETag`, `_links.self`; `404 pack_not_found` (FR-039).
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
    `packId`, and by kind `url`, `ref`, `commit`, `sha256` or `path`).
  - `CharterRefusalCode` gains `pack_version_conflict`, `artifact_referenced`, `relationship_not_found`,
    `relationship_not_registered`, `registration_not_found`, `pack_source_refused`, `pack_source_unavailable`,
    `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`,
    `registration_timed_out` (`504`, the planner's proposal, now confirmed) and `pack_storage_full` (`503`), each
    `raised: false` until its operation is served. `x-refusal.operations` of `getPackRegistration`,
    `deletePackArtifact` and `deletePackRelationship` give the composition codes `422` in place of `500`.
  - New contract scenarios, all `@WIP` until the serving work packages bind them:
    `scenarios/registration/whole-pack-registration.feature` (replaces the `@WIP` `registration/register-pack.feature`)
    and `scenarios/pack-removal.feature`.
