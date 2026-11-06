### Changed

- The four per-artifact writes of an activation are served (#291, ADR-0020 D6, ADR-0059 D2): `selectActivationArtifact`
  and `unselectActivationArtifact` (`PUT`/`DELETE …/selections/{kind}/{artifactId}`), and
  `deactivateActivationArtifact` and `reactivateActivationArtifact` (`PUT`/`DELETE …/exclusions/{kind}/{artifactId}`)
  lose `x-reserved` and their `501`, so the activation set reserves five operations, not nine. Each requires `If-Match`
  (`428` without it, `412` when stale, both before any content is judged) and answers an `ActivationChange` with the
  new revision as its `ETag`; a write that changes nothing answers `200` with the same revision and an empty `applied`
  and `cleared`, and stores nothing. A path id the activation's closure does not hold is `404 artifact_not_found`
  (`mission_type_not_found` for a select or unselect of kind `mission_type`), and a path kind outside the operation's
  enum (`mission_type` on the exclusions path included) is `422 activation_invalid` with a violation at `/kind`; the
  two `404` codes become raised by an operation, so `artifact_not_found` and `mission_type_not_found` are `raised:
  true` in `x-refusal`. The writes' `404` is the new `ActivationArtifactNotFound` response, which names only codes
  they raise. `ActivationChange.skipped` reports the write-time walk only; what a read leaves out is reported by the
  effective reads (`getActivationSkippedReport`, a later change), as the schema's description now says. The write's path has no
  `GET`, so an `ActivationChange` carries no `self`: its `up` link names the activation (`Links` and the example say
  so).
- A cascading deactivate walks the `specializes_from` lineage exactly as a cascading select does (operator ruling of
  2026-11-06, ADR-0059 amendment A2): what a select reached through a profile's ancestor is a candidate of the
  deactivate of that profile, so a cascading select and a cascading deactivate round-trip. An artifact another active
  artifact needs is still kept and reported in `skippedShared`. `ExclusionRequest.cascade` and
  `deactivateActivationArtifact` say so; two scenarios of `activation-selections.feature` pin it.
- A cascade walks `enhances` as one of its relations, in its declared direction (operator ruling of 2026-11-06,
  ADR-0059 amendment A4): `E enhances T` runs from E to T, as `requires` and `scope` do. A cascading select of E
  selects T; one of T selects no enhancer; a cascading deactivate of E unselects T unless another active artifact
  needs it, which is then kept and named in `skippedShared`. The relation set is core's five and `enhances`; four
  scenarios of `activation-selections.feature` pin it. No write walks `enhances` any other way: there is no
  write-time `enhances` pull, so `includeDeactivated` clears no deny entry the walk does not reach, and a cascading
  deactivate keeps an artifact an active enhancer enhances because the shared walk crosses `enhances` to it, not by a
  rule of its own. `SelectionRequest.includeDeactivated`, `ActivationChange` and the two write descriptions say so.
- The path id of the four per-artifact writes is read as an id of the activation document is (`ArtifactIdPath`,
  ADR-0020 D4, operator ruling of 2026-11-06): the declared id, its full URN of the path's kind or another case of
  either names the declared id, which is what the write stores and reports. An unselect or re-activate reads a stale
  id the same way against the stored list. A spelling that matches two declared ids differing only by case is
  `422 activation_invalid` (`ambiguous_artifact`, at `/artifactId`); a URN of another kind is an unknown id, `404`.
  A `mission_type` path id is not normalized: it is matched exactly, as a document's `missionTypes` is, so another
  case or its URN is `404 mission_type_not_found`.
