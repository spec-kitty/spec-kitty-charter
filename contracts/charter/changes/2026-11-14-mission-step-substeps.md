### Breaking

- A mission type's step authors `substeps`: a flat, ordered list, one level deep, of `{id, description, command,
  inputs, delegates_to {kind, candidates}, guidance}`, the content a step contract's step held (operator ruling
  DM-01M7B1YH1GF15RD0G4ZPSA8KW9, FR-004). A substep id is a letter-first kebab slug, unique within its step; an input's
  `optional` is written only as `true`; `delegates_to.kind` is a guidance kind, a `scope` target of an action,
  never `agent_profile` (a mission type names roles, not profiles: ADR-0032 decision 1, operator ruling
  DM-01M7DTN8JWR4G1P35NXGCMS65M), and each candidate is written `[<pack>::]<id>`, each once. `getPackMissionStep` and
  `getEffectiveMissionStep` answer `substeps` in authored order (`MissionSubstep`), each delegation candidate linked to
  its read when the scope holds it: `artifact` in a pack for a candidate written bare or with the pack's own name,
  `effective-artifact` under an activation (examples `MissionStep.specify`, `MissionStep.activation-implement`).
  `MissionStepDelegation` and `MissionStepDelegationCandidate` are renamed `MissionSubstepDelegation` and
  `MissionSubstepDelegationCandidate`.
- Every candidate resolves to an artifact of its kind and the step's action has a `scope` edge to it (FR-005). A pack
  that breaks either is refused: a registration answers `422 validation_failed` with one violation per candidate at
  the mission type file, code `substep_delegation_unresolved` or `substep_delegation_unscoped` (examples
  `PackRegistrationStatus.substep-delegation-unresolved` and `.substep-delegation-unscoped`), and a composing read
  answers `500 pack_unreadable`. A candidate written with another pack's name is resolved when the packs compose; one
  the named pack of the closure does not hold refuses with `substep_delegation_unresolved` before its scope edge's
  reference is judged. `deletePackRelationship` of the last scope edge a candidate needs answers `422
  validation_failed` with `substep_delegation_unscoped` at `/to`. Validate mode reports `duplicate_step_id` for a
  substep id repeated within a step. Core's seam: a client that reads the violations of `validation_failed` meets two
  more codes; no `CharterRefusalCode` is added.
