### Breaking

- Added refusal (issue #323): `putPackRelationship`'s `RelationshipRegistration` body is at most 1 MiB; a larger
  body is refused 422 `validation_failed` (existing code) before it is read, by its `Content-Length` and otherwise
  one byte past the limit. The service holds it with the request-body guard that bounds the directive, paradigm and
  styleguide registration bodies. The 422 description and the schema description say so. Scenario: in
  `relationships.feature`, "A relationship registration body over 1 MiB is refused before it is read", with a body
  under the limit answered by the precondition check instead. Breaking on the 0.x line, which ADR-0022 allows; no
  release carried the unbounded body. Core's seam: none (no schema shape, enum or status changes; a relationship
  body is a few hundred bytes).
