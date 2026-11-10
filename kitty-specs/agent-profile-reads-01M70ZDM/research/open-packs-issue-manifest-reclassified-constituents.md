# Draft issue for spec-kitty/spec-kitty-open-packs (filed as spec-kitty/spec-kitty-open-packs#182)

**Title:** built-in pack-manifest.yaml omits procedure:git-flow and toolguide:execution-lanes after their reclassification

## What is wrong

At `a2a9379` (main), `packs/built-in/pack-manifest.yaml` does not list two constituents that the built-in pack holds and references:

| Constituent | File on disk | Referenced by |
|---|---|---|
| `procedure:git-flow` | `packs/built-in/procedures/git-flow.procedure.yaml` | `procedure.graph.yaml:98` (node) and `:359-365` (edges); `action.graph.yaml:344`, `:518`; `missions/built_in_step_contracts/implement.step-contract.yaml` (`branching_procedure` step) |
| `toolguide:execution-lanes` | `packs/built-in/toolguides/execution-lanes.toolguide.yaml` (+ `execution-lanes.guide.md`) | `toolguide.graph.yaml:14` (node) and `:78` (edge); `action.graph.yaml:341`, `:515`; `implement.step-contract.yaml` (`workspace` step) |

Every other procedure and toolguide file in the built-in pack is in the manifest: 65 of 66 procedures and 18 of 19 toolguides are listed, and these two are the ones missing.

## How it happened

The two reclassification commits for #65 (2026-11-08) removed the old paradigm entries from the manifest but never added the new ones:

- `a6e11d9 refactor(built-in)!: reclassify git-flow as a procedure` deletes the `kind: paradigm, id: git-flow` constituent and adds no `kind: procedure` entry.
- `7174cc9 refactor(built-in)!: reclassify execution-lanes as a toolguide` does the same for `execution-lanes`.

`pack.md` says the manifest is generated and must never be hand-edited, so the manifest was most likely not regenerated after the moves.

## Why it matters

The manifest is the pack's constituent inventory and integrity record. A consumer that inventories or verifies the pack through it does not see these two artifacts. Meanwhile the pack's own graphs, its action index and the `implement` step contract delegate to both of them. The result is a pack whose manifest and graph disagree, with dangling references for any consumer that trusts the manifest.

Downstream, the Spec Kitty charter service bumped its pin to `a2a9379` and its manifest-parity counts dropped (315 → 310 / 317 → 312) for this reason.

## Expected

- Regenerate `packs/built-in/pack-manifest.yaml` so that it lists `kind: procedure, id: git-flow, path: procedures/git-flow.procedure.yaml` and `kind: toolguide, id: execution-lanes, path: toolguides/execution-lanes.toolguide.yaml`, with content hashes and an updated `manifest_hash`.
- Add a CI guard so that every authored constituent file of a doctrine kind under a pack directory appears in that pack's manifest, and every graph node has a manifest constituent. Neither `validate-packs.sh` nor `check-built-in.sh` caught this drift.

## Not in scope

- The manifest also leaves out mission step contracts, mission types, presets and `assets/docs_structural_lint.config.yaml`. That looks intentional (they are not doctrine constituents). Please confirm.
- Toolguides `terminology-guard` and `test-quality-triage` live in `packs/internal`, not `built-in`. They are correct where they are.
