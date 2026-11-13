### Changed

- The effective-read entity tag (`EffectiveETag`) moves to version `v3` (#536), because existing reads answer other
  bodies for the same inputs: `getActivation` (and the `Activation` of `createActivation` and `replaceActivation`)
  links its writes and effective reads, and a skill item of `listEffectiveArtifacts`, `getEffectiveGraph`,
  `getEffectiveActionContext` and an agent profile's governance (`getEffectiveAgentProfile`) now carries its
  `artifact` and `effective-artifact` links. A client holding a tag from before gets a `200` with the new body, never
  a `304`.
