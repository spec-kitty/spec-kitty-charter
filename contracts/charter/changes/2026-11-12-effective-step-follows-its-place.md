### Changed

- `getEffectiveMissionStep` answers `200` for a step that only a version the winning mission type replaced keeps,
  where it answered `404 action_not_found` (A7, DIV-243; research R8, operator ruling of 2026-11-12): the step is read
  from its place, the pack that holds the merged winner of `action:<missionType>/<step>`, with that pack's prompt and
  guidelines, and its skeleton links name that pack's `getPackMissionSkeleton`. A step no version of the closure holds
  is still `404 action_not_found`, judged as `getEffectiveActionContext` and `getEffectiveStepPrompt` judge it, so the
  three reads agree, and the prompt read's `effective-artifact` link answers. A step the winning type declares is
  answered as before. No shape change.
- The `artifact` link of `getEffectiveActionContext` names the pack of the step's place, where it named the pack that
  holds the mission type's version in force; the two differ only for a step a replaced version keeps, whose link
  answered `404` and now answers its step.
