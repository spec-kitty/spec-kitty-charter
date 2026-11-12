### Added

- `getEffectiveStepPrompt`, `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt`
  (ADR-0066 decision 9; FR-003 to FR-005; #438): the prompt in force of one step, as it is stored, at the merged
  winner's place of the step, and every slot it declares with the fragments of the step enhancements in force that fill
  it, ordered by pack level, then pack name, then enhancement id (decision 5), each with its pack and enhancement. A slot
  nothing fills is listed empty; a slot holding legacy text is listed with its fragments and a `prompt_slot_not_empty`
  diagnostic. It answers an `ETag` (`EffectiveETag`, rule E17's recipe) and `304`, and sends
  `Charter-Source-Fingerprint` on its `200`. Its refusals come before the `304`: `404 activation_not_found`,
  `mission_type_not_found`, `action_not_found`, `409 mission_type_not_in_force`, then the closure's `500` codes,
  `prompt_slot_missing` included. New schemas `EffectiveStepPrompt` (`promptPackId` optional, `diagnostics` reusing
  `Diagnostic`), `EffectivePromptSlot`, `EffectivePromptFragment` and `SlotId`; a response `MissionTypeNotInForce`; the
  operation is `ready` in `switch-gate.yaml`.
- The relation `prompt`, listed in the hypermedia relation table and the `link-relations-are-known` rule:
  `getEffectiveActionContext`'s `_links.prompt` leads to the effective prompt of the same action, present exactly when
  the activation puts the mission type in force (FR-007). The prompt links back to the context as `effective-context`
  and to the step under the activation as `effective-artifact`.
