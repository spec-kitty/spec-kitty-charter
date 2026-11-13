# Data model: Slice C

## EffectiveStepPrompt (new read model, `getEffectiveStepPrompt`)

| Field | Type | Rule |
|---|---|---|
| `activationId`, `revision`, `governanceFingerprint`, `missionType`, `action` | as `EffectiveActionContext` | echo and validators |
| `prompt` | string | the stored prompt companion of the prompt in force, unchanged (research R8); empty when the step has none |
| `promptPackId` | string, optional | absent when the step has no prompt companion; else the configured id of the pack that holds the prompt in force |
| `slots` | `[EffectivePromptSlot]` | every slot the prompt declares, in declaration order |
| `diagnostics` | `[Diagnostic]` | `prompt_slot_not_empty` per slot holding legacy text (OD-7) |
| `_links` | `Links` | `self`; `effective-artifact` (the step under the activation); `effective-context` (the action context) |

`EffectivePromptSlot { slot: SlotId, fragments: [EffectivePromptFragment] }`.
`EffectivePromptFragment { packId, packName, level, enhancement, text }`, ordered by `level` (built-in < org < project),
then `packName`, then `enhancement`, by code point (ADR-0066 d5). Invariants: a fragment appears only when its
enhancement is in force (d4); no fragment names an undeclared slot (else the read refuses `prompt_slot_missing`).

## EffectiveContextArtifact (new item schema of the action context)

`EffectiveArtifact`'s members plus `appliesThrough: action | mission_type | project` (required). A suggestion carries
its suggester's value. Invariant: an item whose `appliesThrough` is `mission_type` or `project` is a directive, a
tactic or a paradigm, or is reached from one by ADR-0018 steps 2 to 5.

## Scope owners (internal, packs)

Per artifact URN, the held steps and mission types with a held `scope` edge to it on the merged closure (research R2).
`projectWide(u) ⇔ owners(u) = ∅`; `typeScoped(u, M) ⇔ mission_type:M ∈ owners(u)`.

## Prompt facts (part of `PackCompositionFacts`, packs)

Per pack: `declaredSlots: Map<action URN, List<DeclaredSlot(SlotId id, boolean holdsText)>>` (from the canonical
render of each held step prompt; `holdsText` marks a legacy slot with text between its markers, OD-7) and, per
held step enhancement, `fills: List<(SlotId, canonical fragment text)>`; filled where the facts are built (research R9).

## Refusal codes

| Code | Status | Raised by | New? |
|---|---|---|---|
| `mission_type_not_in_force` | 409 | `getEffectiveStepPrompt` | yes (`### Breaking`) |
| `prompt_slot_missing` | 500 on composing reads; 422 at registration, impact-checked writes and activation writes | the compose check | raised now (`### Changed`) |
| `override_not_permitted` | 422 at registration | also for an enhancement of a step of a locked mission type | description broadened |
