### Breaking

- Every id a request carries is capped in length (#443, after the pack-side caps of #440):
  - 64: a path's `packId` (`PackId`), and `ActivationId`, whose pattern already allowed no more and now says so as
    `maxLength`. `PackSource.packId` states the 63 its pattern allows.
  - 128, the cap `ArtifactId` already carries: a path's `artifactId` (`ArtifactId` parameter), a step contract's id
    (`StepContractId`), and the `id` of a directive, paradigm, styleguide or toolguide registration. A declared id
    over 64 is still refused by the domain, `422 validation_failed`.
  - 255, as written: a relationship registration's `from` and `to`, and the `from` and `to` of a relationship
    `DELETE`. The new `PackQualifiedReferenceWrite` carries the cap for the request side. `PackQualifiedReference`
    and `Urn` stay uncapped, because responses answer references that packs write, which the pack schemas do not cap.

  A request over a cap is a `400` Problem without a code, and nothing is read or written. The relationship mapping
  refuses a longer `from` or `to` too (`422 validation_failed`), for a caller that bypasses the HTTP binding. Core's
  seam: a client that sent a longer id got a not-found or had the id stored; no core call sends one.
