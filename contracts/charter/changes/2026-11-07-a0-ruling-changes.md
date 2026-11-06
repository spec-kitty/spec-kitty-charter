### Changed

- `includeDeactivated` clears only the deny entries of what the cascade reaches and selects (B5, decision
  `DM-01M6RA9XQSH3E07C7VCPCVD6R4`): a lineage ancestor the walk passes through, which it never selects, keeps its deny
  entry. It used to be cleared as the walk passed. `SelectionRequest.includeDeactivated` and `selectActivationArtifact`
  say so; `activation-selections.feature` ("includeDeactivated passes through a denied ancestor inside the scope and
  keeps its deny entry") and `activation-ruling-changes.feature` pin it.
- `replaceActivation` is judged against the document it replaces (A4, decision `DM-01M6RA7XC5HC8915GZT2R3H83W`), so a
  GET then PUT of the same body never fails without a pack change: the floor is judged on the deny entries the body
  adds only, so a deny entry the replaced document held is kept (`200`) when a pack now requires or locks it, where it
  was `409`; a new one is still `409`. The id dropping and the pin dropping are under `### Breaking`.
- A pack's mutability policy locks only the artifacts of the pack and of the packs it builds on (`depends_on` and
  `parent_pack`, transitively) (A15, decision `DM-01M6RA97220XBR7RR447FE2JXD`, rule C22): a `kind_defaults` entry no
  longer locks the artifacts of an unrelated pack that shares the closure, so a deny entry on one is accepted where it
  was `409 artifact_locked`.
- A pack's own policy never refuses its own write (B8, decision `DM-01M6RAA6Y7NVVGKVKQV05GZYES`, C25 aligned with
  C21): the per-artifact registrations (`PUT`/`POST /packs/{packId}/artifacts/<kind>…`) of an artifact the pack's own
  policy locks succeed (`200`/`201`) where they were `409 governance_policy_violation`. The policy still binds every
  pack built on the pack.
- The required floor holds only the must-have items a pack of the closure holds (A10, decision
  `DM-01M6RA8MG55V8C2V9PXWDTRNQS`): an unheld must-have item leaves the floor and is reported (by PR B's reads,
  `requiredNotHeld`) instead of being put in force; a held item of the same preset stays required.
- Two packs that `enhances` one artifact compose (A14, decision `DM-01M6RA92BS6GGCYTRCFQDYYY5S`): an `enhances` edge
  is additive and contests nothing, so sibling enhancers are no `pack_sibling_conflict`; a settlement names exactly one
  winner. The sibling-contest scenarios of `composition.feature` use `overrides` edges, the change that contests an
  artifact; `activation-ruling-changes.feature` pins two sibling enhancers composing.
- `replaceActivation` and `PreconditionFailed` say that a replace without `If-Match` is judged again from a fresh
  read when another writer changes the activation in between, up to eight times, and answers `412` with nothing
  stored when every attempt loses. The behaviour is unchanged.
- `ArtifactIdPath` says that a mission type's path id is normalized as every kind's is (its full URN or another case
  names the declared id): it said that a mission type's id is matched exactly, as the document's `missionTypes` was.
- New `activation-ruling-changes.feature` (`@contract`, SC-005) pins one counter-example per ruling above, each with a
  control in the same answer; its A10 report and `DELETE` half stays `@WIP` for PR B (WP22) and PR A (WP19).
  `activation-kind-model.feature` loses its `@WIP` tag. The scenarios that pinned the old answers are rewritten, not
  deleted: in `activation-register.feature` the version pin refused on `PUT`, the stale id refused on write and the
  re-deny of a now locked or required artifact; in `activation-selections.feature` the denied ancestor cleared by
  `includeDeactivated`, the exact match of a mission type path id and the `422` deactivate of a mission type; and the
  scenarios that sent `schemaVersion: 1`, `activatedKinds` or `missionTypes` in `activation-register.feature`,
  `activation-register-guards.feature`, `activation-selections.feature` and `activation-presets.feature` (the step
  `Given the project activates the pack(s)` of `composition.feature` stores version 2 now); the reads of an emptied
  `selected.<kind>` expect it absent, its canonical form (FR-042).
