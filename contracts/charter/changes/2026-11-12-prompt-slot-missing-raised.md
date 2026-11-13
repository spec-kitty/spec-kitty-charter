### Breaking

- `prompt_slot_missing` is raised (`CharterRefusalCode`, `raised: true`; ADR-0066 decision 8, phase 2; FR-001, FR-006;
  #438). The compose check C38 refuses a step enhancement in force that fills a slot the prompt in force of its step
  does not declare: the prompt at the merged winner's place of `action:<type>/<step>`, so a layer override of the
  mission type whose prompt drops the slot is caught, and only the winning copy of the enhancement is judged. An
  enhancement that is not in force never refuses (OD-1 (B)). Requests that answered before are now refused
  (`step-enhancement-slots.feature`):
  - **Composing reads, `500`**: every read of an activation that puts such an enhancement in force
    (`listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext` and the
    other effective reads), after the composition codes and the merge's `pack_unreadable` (F6).
  - **Registration and the impact-checked pack writes, `422`**: `getPackRegistration` (a refused registration),
    `registerPackDirective`, `putPackDirective`, `registerPackParadigm`, `putPackParadigm`, `registerPackStyleguide`,
    `putPackStyleguide`, `registerPackToolguide`, `putPackToolguide`, `putPackRelationship`, `deletePackArtifact` and
    `deletePackRelationship`, judged for every stored activation whose closure the write joins, the activation named in
    the detail, on the closure as the write leaves it: a write that repairs a closure is let through only when the
    closure composes after it, and one that leaves it broken is refused (G8, G21).
  - **Activation writes, `422`**, and nothing is stored: `createActivation`, `replaceActivation`,
    `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
    `reactivateActivationArtifact` and `applyActivationPreset`.

  The code's `operations` map lists every `422` operation. No enum value is added. Core: nothing while it is not a
  consumer (epic #186); a pack whose override drops a slot that an enhancement in force fills must declare it again.
