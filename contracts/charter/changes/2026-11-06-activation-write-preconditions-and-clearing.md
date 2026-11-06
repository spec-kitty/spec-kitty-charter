### Changed

- The per-artifact writes and `applyActivationPreset` declare `400` (the shared `Problem`): an `If-Match` that is
  neither `*` nor one quoted revision breaks its pattern and is answered `400` before anything else is judged (J10).
  Their precondition order is stated in all five descriptions, `unselectActivationArtifact` included: the `400`, then
  `428`, `404 activation_not_found`, `412`, and only then a `422`, the body's and the path's kind included.
  A fault of a `SelectionRequest`, `ExclusionRequest` or `PresetApplication` body (a member the schema does not
  declare, a `null`, a value of the wrong type, a `cascade` token outside `CascadeKind`, an empty `cascade`, a body
  that is not JSON or is missing) is `422 activation_invalid` after the preconditions, never before them; a `cascade`
  token outside the kinds is the violation `unknown_kind` at `/cascade/<i>`. The wire shapes are unchanged.
- No scalar of an activation body is coerced into another: `1`, `0`, `"true"` or `""` where a boolean is declared
  (`includeDeactivated`, `force`), `"1"`, `1.0` or `true` where an integer is (`schemaVersion`), and a number or a
  boolean where a string is (a `cascade` token, an id, a pack pin) are a value of the wrong type,
  `422 activation_invalid` with the violation `unreadable_body` at the value. A member declared twice in one object
  is the same violation at that member, never the last one winning. This holds for every activation write: after the
  preconditions for the per-artifact and preset writes, and for `createActivation` and `replaceActivation`, whose
  document such a value was read into before.
- `409 ArtifactPolicyRefused` stays declared on `deactivateActivationArtifact` alone, the one write that can add a deny
  entry. A deny entry stored before a pack upgrade put its artifact on the required or locked floor is tolerated
  (ADR-0059 D4, "a stored one is tolerated but not applied"): a select, unselect, re-activate or preset of another id
  answers as if it were not there, and keeps it. `createActivation` and `replaceActivation` still judge the whole
  document they are sent.
- `SelectionRequest.includeDeactivated` says what it clears (operator ruling of 2026-11-06, ADR-0059 amendment R3):
  only the deny entries the walk passes through within the cascade's kinds, that is what it reaches within them, each
  lineage ancestor it passes whose kind is among them (cleared, never selected) and the target. A denied artifact
  outside the cascade's kinds keeps its deny entry: the walk stops there and reports it in `skippedDeactivated`, with
  what lies only behind it in `unreachedBehindDeactivated`, as without `includeDeactivated`. This reverses the
  earlier reading that cleared out-of-scope deny entries the walk went through; three scenarios of
  `activation-selections.feature` pin it.
- A cascading deactivate never removes an artifact on the required or locked floor; a selected one the cascade reached
  is kept, and reported in `skippedShared` when another active artifact still references it, named by that artifact.
  One no other active artifact reaches is not reported: it was never a candidate, and `referencedBy` never names the
  kept artifact itself. `SkippedDeactivated.requiredBy` and `SharedSkip.referencedBy` name only an
  artifact an `ArtifactReference` can name: a template, an asset, an unselected intermediate or a lineage ancestor on
  the way leads back to the artifact that put it in the walk (for an ancestor, its descendant), as
  `ArtifactReference`'s description and ADR-0059 D2.4 say; among the nearest, the first in code-point order.
- Stale ids are dropped on write (operator ruling of 2026-11-06, ADR-0059 amendment R5). Every per-artifact and preset
  write first removes from the stored document each id the activation's closure no longer declares (a pack upgrade
  removed it), in `selected`, `deactivated` and `missionTypes`, and reports them in the new optional
  `ActivationChange.dropped`, an array of `ArtifactReference`, present only when the write dropped one. A dropped id
  is a change: a write that only drops stale ids is not a no-op and answers a new revision. `applied` and `cleared`
  report the write's own change and never a dropped id. Before, such a document made every per-artifact and preset
  write `422 activation_invalid` until a full `replaceActivation`. Unchanged: a path id the closure no longer holds is
  `404 artifact_not_found` and drops nothing; an unknown pack or a pinned version the closure does not hold is still
  `422 activation_invalid` (`unknown_pack`, `version_not_held`), which only a `replaceActivation` recovers; and
  `createActivation` and `replaceActivation` still refuse an unknown id in the body they are sent with `422`. No
  refusal code changes, so `CharterRefusalCode` and its `x-refusal` map are unchanged; three scenarios of
  `activation-selections.feature` pin the drop.
