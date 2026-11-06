### Changed

- `RequiringPack.via` is populated on the `409 artifact_required` and `409 artifact_locked` refusals of an
  activation write: the `parent_pack` chain from the pack the activation names up to the requiring pack, that
  pack excluded. It stays `[]` when the named pack declares the item itself, and for a pack reached over
  `depends_on` only. An activation write also evaluates its preconditions before it judges the document: a stale
  `If-Match`, or `If-None-Match: *` on a stored id, is a `412` whatever the document names (RFC 9110 §13.2.2). A
  body the strict reader refuses as it reads it (an unknown member, a `null`, a duplicate key) is still `422` first.
- `ArtifactId`, the `artifactId` path parameter and `ActivationConfig` say that an id is matched exactly as declared.
  They promised that another known spelling is accepted and normalized to the declared id (ADR-0020 D4); that
  normalization is not built, and another spelling is an unknown id (DIV-208). No behavior changes.
- The `governance_policy_violation` text of `CharterRefusalCode` and its `errors.feature` scenario say that each
  pack's `policy.yaml` is read (ADR-0062) and that no open pack ships one yet; they said that no policy file is read.
- `replaceActivation`, `deleteActivation`, `PreconditionFailed` and `UnknownActivation` say that an `If-Match` on an
  activation that does not exist is `412`, not `404`: no stored revision matches it (RFC 9110 §13.1.1). Two
  scenarios in `activation-register-guards.feature` pin it. The behavior is unchanged.
- The shared `Problem` caps `violations` at `maxItems: 100`. A refusal of more parts lists the first 100 and ends
  `detail` with "N more violations are not listed." A 1 MiB body of nulls or unknown ids used to answer about
  200,000 entries. Core's seam reads at most 100 entries and takes the count from `detail`.
- `getActivation` and `deleteActivation` declare their `500` as the shared `Problem` response: their only `500` is
  `internal_error`, a failure of the storage, not the offering's composition codes `OfferingUnreadable` lists.
  `listActivationPacks`' `pack_unreadable` also covers a pack the stored document names that is no longer configured.
