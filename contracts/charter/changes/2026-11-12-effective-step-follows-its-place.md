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
- `getEffectiveStepPrompt` reads the prompt in force at the step's place by the same rule (operator ruling Q3,
  2026-11-13), where it read it at the merged winner of `action:<missionType>/<step>`; `promptPackId` names that place.
  The two differ only when the mission type's version in force declares the step and another pack's graph declares
  the action: the prompt is now the winning version's, the one the step read answers.
- The slot check (C38, `prompt_slot_missing`) judges the prompt at the step's place by the same rule, on composing
  reads (`500`), registration, the impact-checked pack writes and the activation writes (`422`), so it never judges a
  prompt other than the one `getEffectiveStepPrompt` serves: a closure whose winning mission type declares the step
  with a prompt that lacks a slot an enhancement in force fills is refused, although the merged winner of the action
  declares it; one where the action's winner's prompt lacks it and the winning version's declares it now composes. A
  step with no place has no prompt in force and is not judged.
- `getEffectiveMissionStepContract` links `step` whenever `getEffectiveMissionStep` answers the step, where it did only
  when the winning mission type declares it.
