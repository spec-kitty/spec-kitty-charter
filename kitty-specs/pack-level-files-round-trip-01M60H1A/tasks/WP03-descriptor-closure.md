---
work_package_id: WP03
title: "Close pack.yaml: refuse unknown keys, retire undeclaredKeys"
dependencies: []
requirement_refs:
- FR-011
- FR-012
- C-001
- SC-001a
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T007
- T008
- T009
- T010
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor
create_intent: []
execution_mode: code_change
owned_files:
- contracts/packs/schemas/pack-descriptor.schema.json
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorWriter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyOrderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterWiringTest.java
- docs/conformance/divergence-register.md
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP03 — Close pack.yaml: refuse unknown keys, retire undeclaredKeys

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make `pack.yaml` lossless the way ADR-0036 ruling 1 rules (spec FR-011, FR-012). The schema is closed, so an unknown
or retired key makes the pack unreadable and names the key. The undeclared-keys path goes from the code, and
**`contracts/charter/**` is not touched** (C-001).

## Subtask T007 — Close the schema

- In `contracts/packs/schemas/pack-descriptor.schema.json`, set `"additionalProperties": false`.
- Make the description text consistent:
  - `pack_level` is optional (`PACK_LEVEL_MISSING` covers its absence);
  - `pack_version` is required at every level, non-empty.
- Change no field.

## Subtask T008 — Remove the undeclared-keys path

- Remove the `undeclaredKeys` component from the kernel `PackDescriptor`, its 7-argument constructor and its javadoc,
  and from `PackDescriptorDso` and `PackDescriptorReader` (the `declaredProperties` filter).
- Remove `PackFindingCode.PACK_UNKNOWN_KEY`, the `Diagnostic.Code.PACK_UNKNOWN_KEY` constant and the `PackPolicy`
  rule.
- Remove `WholePackStore.write`'s undeclared-keys refusal and its javadoc. This is an out-of-map edit, recorded:
  `WholePackStore` is owned by WP04 and the edit is three lines.
- Delete the dead `PackDescriptorWriter` (reducer D5r). Nothing calls it, and its javadoc links the removed method.
- The contract's `DiagnosticCode` keeps `pack_unknown_key`. If `VocabularyEqualityTest` or `CharterWiringTest` pin
  the Java side against the contract, adjust them to "the contract may hold a retired value the code no longer
  raises". Do not edit the contract.

## Subtask T009 — Tests move to the refusal

These tests (`PackDescriptorReaderTest`, `PackRoundTripTest`, `WholePackStoreTest`, `DomainRepositoryServiceTest`,
`PackFindingTest`, `PackPolicyRulesTest`, `PackPolicyOrderTest`, `CharterWiringTest`) now assert the refusal:

- `surprise: 1` → `FileRead.Unreadable` with a schema violation naming `surprise` (keyword `additionalProperties`);
- `accompanies_doctrine_pack` → refused the same way;
- the pack load gives `CharterRefusal(PACK_UNREADABLE)` with `pack.yaml of <pack> cannot be read`.

Each is paired with a clean descriptor in the same test.

`WholePackStoreTest:423` is an out-of-map edit, recorded (owned by WP04): it now expects the load refusal.

## Subtask T010 — Conformance

In `docs/conformance/divergence-register.md`, close DIV-005 and DIV-006 (unknown keys are now refused, in line with
core), citing ADR-0036 and this mission.

## Definition of Done

- `grep -rn "undeclaredKeys\|PACK_UNKNOWN_KEY\|PackDescriptorWriter" service --include=*.java` finds nothing.
- `git diff origin/main -- contracts/charter` is empty.
- `./gradlew :packs:test :kernel:test :deployable:test` is green.
- The whole-pack gate is still green: both pinned `pack.yaml` files have no unknown key.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP03 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
