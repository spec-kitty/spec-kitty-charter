### Changed

- `RequiringPack.via` is populated on the `409 artifact_required` and `409 artifact_locked` refusals of an
  activation write: the `parent_pack` chain from the pack the activation names up to the requiring pack, that
  pack excluded. It stays `[]` when the named pack declares the item itself, and for a pack reached over
  `depends_on` only. An activation write also evaluates its preconditions first: a stale `If-Match`, or
  `If-None-Match: *` on a stored id, is a `412` whatever the document holds (RFC 9110 §13.2.2).
- `ArtifactId`, the `artifactId` path parameter and `ActivationConfig` say that an id is matched exactly as declared.
  They promised that another known spelling is accepted and normalized to the declared id (ADR-0020 D4); that
  normalization is not built, and another spelling is an unknown id (DIV-208). No behavior changes.
- The `governance_policy_violation` text of `CharterRefusalCode` and its `errors.feature` scenario say that each
  pack's `policy.yaml` is read (ADR-0062) and that no open pack ships one yet; they said that no policy file is read.
- `replaceActivation`, `deleteActivation`, `PreconditionFailed` and `UnknownActivation` say that an `If-Match` on an
  activation that does not exist is `412`, not `404`: no stored revision matches it (RFC 9110 §13.1.1). Two
  scenarios in `activation-register-guards.feature` pin it. The behavior is unchanged.
