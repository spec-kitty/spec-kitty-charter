---
work_package_id: WP04
title: Retired files are refused by name
dependencies:
- WP03
requirement_refs:
- FR-003
- FR-008
- NFR-003
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T013
- T014
- T015
- T016
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companions.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/**
- service/packs/src/test/resources/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP04 — Retired files are refused by name

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

Add `Retired(match, reason)` to `PackFileKind.Classification`, read from a `retired` section (`match`, `reason`, both required) and covered by the overlap check; make `PackFileKind.parse` refuse an unknown top-level key. Collect retired files once in `PackFileSet.Listing.retired`; append them to `digested` in the tail after the manifest, in path order. Report each as a store `PackElementViolation` (`retired`, the reason) and as a validate-mode finding (YAML and Markdown alike) from that listing; `Companions.claimedByRule` refuses a retired path; the gate census buckets them as `retired:`. Prove it on fixture tables (`PackFileKind(String)`) and fixture packs holding one file of every family ADR-0044 §6 names, at both the root and the `missions/` layout; a fingerprint test; a source scan over `:missions` and `:packs` main sources forbidding the retired field names. The bundled `file-kinds.yaml` gains no `retired` rule here (WP06).

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew :packs:test :packs:openPacksRoundTrip` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
