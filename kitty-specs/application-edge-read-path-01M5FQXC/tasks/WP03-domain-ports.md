---
work_package_id: WP03
title: 'Domain: single violations owner + upsert contract + read ports'
dependencies: []
requirement_refs:
- FR-009
- FR-010
- C-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: e32d9c11e13d0920f29789df4e8f25390d7e33c3
created_at: '2026-10-21T19:38:34.617611+00:00'
subtasks:
- T011
- T012
- T013
- T014
- T015
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRepository.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 – Domain: single violations owner + upsert contract + read ports

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Make `Pack` the **single owner** of read-time violations, pin `TacticRepository.save` as an **upsert** contract, and ensure the tactic read ports (all + one-by-id) are expressed on the domain. **Additive only** — do not delete `PackInspectionQuery` here (WP07 removes it once storage/app stop using it). IC-06 (domain half). Decisions: [research.md](../research.md) DM `contract.save-semantics`; [data-model.md](../data-model.md).

## Context
- `Pack` aggregate root + `Tactic` child entity exist. `PackInspectionQuery.partialReads()` currently duplicates `Pack.violations()` — squad MAJOR. `TacticRepository.save` javadoc currently says "replacing the existing tactic"; the operator ruled **upsert** (create-or-update).
- Java style (C-005): fluent chainable builders public; package-private full ctor for serialization only; final class over public record for many-field types.

### T011 — `Pack` is the single owner of read violations
- `Pack.violations()` is canonical. Do not add a second violations surface. (Removal of `PackInspectionQuery` is WP07 — here, just ensure the aggregate is the authority and nothing new duplicates it.)

### T012 — `TacticRepository.save` upsert contract
- Change the `save` javadoc/signature to state **create-or-update**: "stores the tactic, creating it when its identity is unknown and replacing it when known." The port is the first copyable contract — make the semantics unambiguous. Keep signatures naming only domain/kernel/JDK/jspecify types (gate `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES`).

### T013 — Tactic read ports
- Ensure the domain ports express: retrieve all tactics of a pack, and retrieve one tactic by `(PackIdentifier, TacticIdentifier)`. Add methods if missing; keep them point-wise (the storage realization must not fan out — enforced in WP04).

### T014 — Domain tests
- Strengthen `PackAggregateTest` for the single-owner invariant (foreign tactic rejected, duplicate id rejected — already covered; add that `violations()` is the only read-violation surface). Do **not** add a separate domain-level `save`-contract test — the behavioural both-arm upsert proof lives at the storage edge (WP04's `TacticSaveContractTest`); a domain duplicate would be inert (DIRECTIVE_041). Prefer strengthening existing tests over new ones.

### T015 — Keep `PackInspectionQuery` compiling
- Leave `PackInspectionQuery` in place and compiling; its consumers (storage) still reference it until WP04 collapses the impl and WP07 deletes the port. Add a `// removed in WP07 cutover` note.

## Definition of Done
- `:packs:test` domain tests green; `Pack.violations()` is the sole read-violation owner; `TacticRepository` javadoc states upsert; read ports are point-wise.

## Reviewer guidance (test economy — DIRECTIVE_041)
- Reject a new test that merely re-asserts record equality or pins shape. The upsert behaviour is proved at the edge (WP04) — a domain test here should assert contract intent, not duplicate the edge test. Prefer strengthening `PackAggregateTest`.
