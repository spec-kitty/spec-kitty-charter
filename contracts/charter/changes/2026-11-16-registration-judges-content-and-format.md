### Breaking

- A registration (`PUT` of a whole pack, an archive or a source) now refuses, as validate mode already does, the
  repeated asset id and the format checks of the one read (#554, operator ruling S2; R2 rows 14, 15 and 38 in
  `docs/development/notes/pack-code-unification.md`). Each is a violation of the `422 validation_failed` refusal at
  its file, in validate mode's words:
  - `duplicate_artifact_id` at the second asset sidecar that declares an id (row 15);
  - `requires_core_invalid`, `pack_version_invalid` and `dependency_range_invalid` at `pack.yaml`, for a
    `requires_core`, a `pack_version` or a `depends_on` range that is not PEP 440 (row 38);
  - `asset_mime_invalid` at the sidecar, for an asset `mime` that is not `type/subtype` or differs from the type its
    extension implies (row 14).

  A pack registered before with such a format answered `200`; it is now refused. Golden rows:
  `asset-mime-does-not-match-the-extension`, `asset-mime-malformed`, `asset-repeated-id-only-the-first-owner-is-checked`
  and `asset-repeated-id-the-first-owner-escapes` (each bare and `+pack.yaml`) and `requires-core-invalid`.
- The composing read keeps serving a stored pack with those formats (operator ruling S4): it reads the store half of
  the one read, which never judges them, so its answers are unchanged.
- Scenarios added: `assets.feature` (registration of a sidecar with a malformed mime is refused `asset_mime_invalid`,
  while a stored one keeps composing) and `registration/whole-pack-registration.feature` (one per S2 code).
