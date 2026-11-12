### Changed

- `getEffectiveMissionStep` answers `200` for a step that only a version the winning mission type replaced keeps,
  where it answered `404 action_not_found` (A7, DIV-243; research R8, operator ruling of 2026-11-12). One rule places
  a step: the winner of `mission_type:<missionType>` when that version declares the step, else, for a step the closure
  holds, the pack that holds the merged winner of `action:<missionType>/<step>` when its version declares it. A step
  placed there is read from that pack, with its prompt and guidelines, and its skeleton links name that pack's
  `getPackMissionSkeleton`. A step no version of the closure holds is still `404 action_not_found`, judged as
  `getEffectiveActionContext` judges it, now with that read's detail. A step the winning type declares is answered as
  before. No shape change.
- The `artifact` link of `getEffectiveActionContext` names the pack of the step's place by the same rule, so it and
  `effective-artifact` answer the same step; it named the pack that holds the mission type's version in force, which
  answered `404` for a step a replaced version keeps.
- `getEffectiveMissionStepContract` links `step` whenever `getEffectiveMissionStep` answers the step, where it did only
  when the winning mission type declares it.
