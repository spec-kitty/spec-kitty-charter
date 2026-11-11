### Added

- Procedures are served (#484). `procedure` joins the listed kinds: `GET /packs/{packId}/artifacts?kind=procedure`,
  the item `GET /packs/{packId}/artifacts/procedure/{artifactId}` (member `procedure`, schema `Procedure` with
  `ProcedureStep` and `ProcedureAntiPattern`; an absent procedure is `404 artifact_not_found`), its `/references`,
  and under an activation `GET /activations/{activationId}/artifacts/procedure/{artifactId}` and the effective
  listing. The pack item carries the source fingerprint and, as the writable kinds do, the pack revision as its `ETag`;
  that revision is the `If-Match` of a removal, and the service serves the removal of a procedure
  (`DELETE /packs/{packId}/artifacts/procedure/{artifactId}`, an absent one is `404 artifact_not_found`). A procedure
  has no `PUT`. The `ETag` on every other pack item read (tactic, mission step contract, mission type) follows in
  #487.
  `ArtifactKindToken` lists `procedure`. An action context entry of a procedure, already listed, now links
  `effective-artifact` and `artifact`. Core's seam: nothing; additive.
