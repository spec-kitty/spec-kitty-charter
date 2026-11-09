### Breaking

- `EffectiveGraph` gains the required `layerOverrides` (`[LayerOverride]`): a closed response of `getEffectiveGraph`
  gains a member, as in PR B (the operation answered `501` before; FR-018, IC-11). `EffectiveActionContext` gains the
  required `enhancements` (`[ActionEnhancement]`) and its `skipped` is the extended `SkippedReport` the other reads
  carry, so a strict consumer of either response has to know the new members. `getEffectiveGraph` and
  `getEffectiveActionContext` lose `x-reserved` and their `501`: a client that treated either as unbuilt now gets the
  answers below. The `NodeReference` change to `skipped` is `2026-11-09-c-node-reference-step-targets.md`.
- Core's seam: core regenerates its strict DTOs of `EffectiveGraph` and `EffectiveActionContext`; its in-process backend
  has no counterpart (DIV-244).
