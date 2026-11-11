### Added

- Procedures are served (#484). `procedure` joins the listed kinds: `GET /packs/{packId}/artifacts?kind=procedure`,
  the item `GET /packs/{packId}/artifacts/procedure/{artifactId}` (member `procedure`, schema `Procedure` with
  `ProcedureStep` and `ProcedureAntiPattern`; an absent procedure is `404 artifact_not_found`), its `/references`, its
  removal, and under an activation `GET /activations/{activationId}/artifacts/procedure/{artifactId}` and the effective
  listing. The pack item carries the pack revision as its `ETag` and the source fingerprint, like the other kinds served
  by pack. `ArtifactKindToken` lists `procedure`. An action context entry of a procedure, already listed, now links
  `effective-artifact` and `artifact`. Core's seam: nothing; additive.
