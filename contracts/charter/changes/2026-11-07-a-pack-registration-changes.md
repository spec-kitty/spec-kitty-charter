### Changed

- `parameters/IfMatch.yaml` covers a pack revision as well as an activation: it is also the quoted strong `sha256:` tag
  of a pack's revision. It stays `required: false`; the two `DELETE`s of a pack state their `428` in their
  descriptions, and the whole-pack registrations leave it optional (without it the write is last-writer-wins on the
  whole pack). There is no `PackIfMatch` parameter.
- `CharterRefusalCode`: the text of `kind_not_found` names `deletePackArtifact` beside the generic item read, and the
  dispatch text makes `registration_timed_out` and `pack_storage_full` retryable beside `internal_error` and
  `stale_input`.
- The contract lint rule `error-responses-are-problems` exempts `202`, the status answer of a registration, beside
  the `200`, `201` and `204` successes.
