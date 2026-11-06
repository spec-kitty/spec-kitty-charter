### Changed

- The per-artifact writes and `applyActivationPreset` declare `400` (the shared `Problem`): an `If-Match` that is
  not one quoted revision breaks its pattern and is answered `400` before anything else is judged (J10). Their
  precondition order is stated: `428`, `404 activation_not_found`, `412`, and only then a `422`, the body's included.
  A fault of a `SelectionRequest`, `ExclusionRequest` or `PresetApplication` body (a member the schema does not
  declare, a `null`, a value of the wrong type, a `cascade` token outside `CascadeKind`, an empty `cascade`, a body
  that is not JSON or is missing) is `422 activation_invalid` after the preconditions, never before them; a `cascade`
  token outside the kinds is the violation `unknown_kind` at `/cascade/<i>`. The wire shapes are unchanged.
- `409 ArtifactPolicyRefused` stays declared on `deactivateActivationArtifact` alone, the one write that can add a deny
  entry. A deny entry stored before a pack upgrade put its artifact on the required or locked floor is tolerated
  (ADR-0059 D4, "a stored one is tolerated but not applied"): a select, unselect, re-activate or preset of another id
  answers as if it were not there, and keeps it. `createActivation` and `replaceActivation` still judge the whole
  document they are sent.
- `SelectionRequest.includeDeactivated` says what it clears: the deny entries of everything the walk goes through,
  whatever the cascade's kinds (one outside them is cleared, stays in `skippedByScope` and is not selected; a lineage
  ancestor passed through is cleared and never selected), and of what the read-time `enhances` pull reaches from what
  the walk selects, within the kinds `activatedKinds` names (absent means none, ADR-0059 D2.2).
- A cascading deactivate never removes an artifact on the required or locked floor; a selected one the cascade reached
  is kept and reported in `skippedShared`. `SkippedDeactivated.requiredBy` and `SharedSkip.referencedBy` name only an
  artifact an `ArtifactReference` can name: a template, an asset, an unselected intermediate or a lineage ancestor on
  the way leads back to the artifact that put it in the walk (for an ancestor, its descendant), as
  `ArtifactReference`'s description and ADR-0059 D2.4 say; among the nearest, the first in code-point order.
