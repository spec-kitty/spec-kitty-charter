---
work_package_id: WP08
title: Records and gates
dependencies:
- WP07
requirement_refs:
- FR-013
- FR-014
- NFR-003
- C-002
- C-004
- SC-002
- SC-004
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/602-tensions unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-serve-tensions-01M7RDX1
base_commit: d2d38832dffe528c6b1d273919bf8cf8aa79d0a2
created_at: '2026-11-19T15:02:53.129068+00:00'
subtasks:
- T038
- T039
- T040
- T041
agent: claude
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- contracts/charter/changes/2026-11-19-tensions-are-served.md
- contracts/charter/changes/2026-11-19-tension-is-a-relation.md
- docs/changelog/2026-11-19-tensions.md
execution_mode: code_change
owned_files:
- docs/api/service-contract.md
- docs/conformance/divergences.yaml
- docs/conformance/divergence-register.md
- docs/_meta/terminology.yaml
- site/.contextive/definitions.yaml
- contracts/packs/README.md
- docs/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse.md
- docs/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship.md
- docs/adr/0077-kind-taxonomy-and-step-authority.md
- contracts/charter/changes/2026-11-19-tensions-are-served.md
- contracts/charter/changes/2026-11-19-tension-is-a-relation.md
- docs/changelog/2026-11-19-tensions.md
role: implementer
tags: []
tracker_refs:
- '#602'
---

# Work Package Prompt: WP08 - Records and gates

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load scribe-sally` and adopt the profile before reading anything else.

Then read, in `kitty-specs/serve-tensions-01M7RDX1/`: `spec.md`, `plan.md` (its last section, "Revisions after the
plan-point squad and rulings G1 to G3", wins over the concerns above it), `data-model.md`, `research.md`,
`research/squad-plan.md`; and `docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md`
(accepted, with rulings G1 to G3). Read `.kittify/charter/charter.md` and `AGENTS.md`.

Charter rules that bind every subtask:

- The charter's model is canonical: no reader of `in_tension_with`, no shim, no deprecation window.
- Tests and examples carry only the canonical shape, except error-handling fixtures. Never exclude a field from a
  parity or round-trip check to make a test pass.
- Tests are held to the production bar: outcome assertions, a positive control beside every refusal, no fake green.

## Branch Strategy

- Planning base and final merge target: `feat/602-tensions` (PR #606 to `main`). The mission branch is
  `kitty/mission-serve-tensions-01M7RDX1`.
- The execution worktree is allocated per computed lane from `lanes.json`.
- Start with `spec-kitty agent action implement WP08 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

The records match the reads, and every gate is green (spec FR-013, FR-014, NFR-003, C-002, C-004, SC-002, SC-004;
plan IC-08 and the Revisions section).

## Subtasks

### T038: Divergence register and the service-contract page

1. `docs/conformance/divergences.yaml` (entries at :311-329 and :632-640) and the register page: replace "no tension
   annotation until the creed set" with the decided reads, and record each intended difference from core:
   - the spelling (`tension`; core's `in_tension_with`);
   - the read shape (graph edge `tension`, context `tensions`, a separate activation read; core's governance-bundle
     annotation `tension_arbiters` / `unarbitrated_tensions`);
   - the resolution vocabulary (`reconcile`, `deactivate_one_side`, offered per excludable side; core's two fixed
     strings);
   - the reconciler rule and word ("reconciler", reaching both sides, every one in force; core's "arbiter");
   - stored orientation (the owning side; core's smaller-URN-first) and the stricter load refusals.
2. `docs/api/service-contract.md`: move the "Tension annotation" row (:329) out of the creed set into the decided
   reads, naming the three reads and ADR-0045; mark "The other reads (expected-artifact manifests)" (:339, #101)
   delivered (ADR-0044, PR #472); add `getActivationTensions` to the operation table (:414) and the activation links
   (:217-219); the `deletePackRelationship` row names `relation`.

### T039: Contract change fragments (ADR-0047)

`contracts/charter/changes/2026-11-19-tension-is-a-relation.md` and `…-tensions-are-served.md`, in the fragment
format of the neighbours:
- `### Breaking`: `Relation` gains `tension` (reaches EffectiveGraphEdge, ReferencedArtifact, the governance entries
  as schema); `in_tension_with` is no longer read (pack schemas); a pack with a duplicate, reversed, foreign-source or
  self tension is refused; EffectiveGraphEdge gains `when`, `reason`, `reconciledBy` on tension edges; the required
  `EffectiveActionContext.tensions`; the taxonomy row renamed; the effective tag domain v4. Name what core's seam must
  regenerate.
- `### Added`: `getActivationTensions`, `ActivationTensions`, `TensionFinding`, `ContextTension`, the `tensions` link.
- `### Changed`: `deletePackRelationship`'s optional `relation`; `putPackRelationship` accepts `tension` and the
  per-class 201; registration keeps tensions.

### T040: Glossary and notes

- `docs/_meta/terminology.yaml` (and the generated `site/.contextive/definitions.yaml`): `tension`, `reconciler`,
  `pair class`, `unreconciled tension`; `in_tension_with` as a term to avoid.
- `contracts/packs/README.md`: the `tension` edge and its stored orientation.
- A dated amendment note in ADR-0027 (identity is the pair and its class, ADR-0045), ADR-0028 decision 5 (answered by
  ADR-0045) and ADR-0077 (the taxonomy row is `tension`).
- `docs/changelog/2026-11-19-tensions.md` if the docs changelog takes an entry for a contract change (check the
  neighbours; regenerate the hubs with `generate_indexes.py --write`).

### T041: Gates

- `./gradlew verifyAll --max-workers=1` on Java 25.
- `:pack-validation:wholePackRoundTrip`.
- `python3 scripts/docs/check_docs.py all`, markdownlint-cli2 and cspell (install with `npx --yes` if missing).
- `git grep -n in_tension_with` lists only the refusal fixture, the divergence register and core's planted verdicts.
- The open-packs draft is `kitty-specs/serve-tensions-01M7RDX1/research/draft-open-packs-tension-rename.md`
  (placeholder `open-packs#316`); confirm the orchestrator filed it and replace the placeholder.

## Definition of Done

Every gate above green, recorded in the activity log with the commands and their results.
