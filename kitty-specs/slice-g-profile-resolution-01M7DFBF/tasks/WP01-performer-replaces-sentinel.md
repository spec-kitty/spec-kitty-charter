---
work_package_id: WP01
title: performer replaces sentinel
dependencies: []
requirement_refs:
- C-001
- FR-007
- FR-008
- NFR-001
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts for this mission were generated on slice-g-538-profile-resolution. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-g-538-profile-resolution unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - Profile model
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/schemas/
create_intent:
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/Performer.java
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/human-operator.agent.yaml
execution_mode: code_change
owned_files:
- contracts/packs/schemas/agent-profile.schema.json
- contracts/charter/schemas/AgentProfile.yaml
- contracts/charter/examples/Artifact.agent_profile.yaml
- contracts/charter/examples/EffectiveAgentProfileRead.java-jenny.yaml
- contracts/charter/scenarios/agent-profile-reads.feature
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/AgentProfile.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/Performer.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RoleBearer.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AgentProfileDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AgentProfileBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AgentProfileYaml.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileJson.java
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/AgentProfileEntityTest.java
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/AgentProfileRolesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AgentProfileYamlTest.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/ProfileDtosTest.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/FullProfile.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/ProfileJsonTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileCorpusTest.java
- service/packs/src/testFixtures/resources/example-packs/built-in/agent_profiles/java-jenny.agent.yaml
- service/packs/src/testFixtures/resources/example-packs/built-in/pack-manifest.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/human-operator.agent.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/pack-manifest.yaml
- docs/architecture/diagrams/descriptor-agent-profile.puml
- docs/architecture/diagrams/descriptor-agent-profile.svg
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 — performer replaces sentinel

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Replace the agent profile's `sentinel: boolean` with `performer: agent | human` everywhere, in one change, with no
reader of the old key (charter, "Designing during the rewrite"; ruling D4, `research.md` §6a). Reserve the role
`operator` for human-performed profiles. This is the charter half of open-packs#139 (#512).

Ruled behaviour (D4):
- `performer` is optional in the pack file, with values `agent` or `human`. The read invents no default
  (`AgentProfile.yaml` description: "no default is invented"), so the wire **omits** `performer` when the file is
  silent. Selection (WP03) reads absent as `agent`.
- A profile whose `role` is `operator`, or whose `alt-roles` contain `operator`, and which is not
  `performer: human`, is invalid. Report it like the alt-role rule (ADR-0069 d3): a schema violation at the item
  (`/role` or `/alt-roles/<n>`), raised by the entity's constraint and by the pack reader.
- `sentinel` disappears: a file that carries it is unreadable (`500 pack_unreadable`) or, on registration,
  `422 validation_failed`, because the schema is closed. That appears only in an invalid-pack test.

## Subtasks

### T001 Pack schema
- `contracts/packs/schemas/agent-profile.schema.json`: remove `sentinel` (around :375). Add `performer`:
  `{"type": "string", "enum": ["agent", "human"], "title": "Performer", "description": ...}`, with no `default`.
  The description says who carries out the profile; that absent means the file is silent and selection treats it as
  an agent; and that `operator` is reserved for `human`.
- Keep the schema closed (`additionalProperties: false`).

### T002 Domain
- New `Performer` enum (`AGENT`, `HUMAN`) in `profiles/domain`, with a `token()` accessor (`agent`, `human`) and
  `fromToken`.
- `AgentProfile`: replace `Optional<Boolean> sentinel` (:90, :119, :361-366, :477, :688-694) with
  `Optional<Performer> performer`, plus a builder method and an accessor. Add `public boolean humanPerformed()`
  (true only when `HUMAN`).
- Add the constraint `OPERATOR_ROLE_HUMAN_ONLY` beside `ALT_ROLES_NOT_GENERIC` (:37-58). Rule text: "An agent
  profile that takes the role operator must be performed by a human." Add a reserved constant
  `OPERATOR_ROLE = "operator"` next to `GENERIC_ROLE`.
- `RoleBearer` (kernel): add `boolean humanPerformed()`, so packs-side rules (WP02's `fallback_profile_not_agent`)
  can read it without depending on the profiles slice.

### T003 Storage
- `AgentProfileDso` (:37, :65, :95, :123), `AgentProfileBinding` (:92, :156) and `AgentProfileYaml` (:21, :58, :107):
  replace `sentinel` with `performer` (a string token). The writer emits `performer` only when it is present
  (round trip: meaning-equal, ADR-0036).
- The reader reports `operator` without `human` at the item pointer, as for alt-roles
  (`AgentProfileYaml` / the reader's schema-violation path).

### T004 Contract and DTOs
- `contracts/charter/schemas/AgentProfile.yaml`: remove `sentinel` (:9, :127-132) and add `performer`
  (`enum: [agent, human]`), not required. Add it to the description's list of fields "omitted when the profile does
  not declare it".
- `ProfileDtos.java:181` and `ProfileJson.java:81`: map `performer`, omitting it when absent.
- Examples: `Artifact.agent_profile.yaml:194` and `EffectiveAgentProfileRead.java-jenny.yaml:199`. Remove
  `sentinel`. Keep the example's profile silent, or set `performer: agent`, matching the example pack file exactly.

### T005 Example packs
- `built-in/agent_profiles/java-jenny.agent.yaml:202`: remove `sentinel`.
- New `org-base/agent_profiles/human-operator.agent.yaml`: `profile-id: human-operator`, `role: operator`,
  `performer: human`, plus the required `name`, `purpose` and `specialization`. Keep it minimal and canonical.
  Restamp `org-base/pack-manifest.yaml` and `built-in/pack-manifest.yaml`. Find the generator the repository uses
  for example-pack manifests; never hand-edit hashes.
- This example satisfies `ExamplePackCoverageTest.everySchemaPropertyHasAnExample` for `performer`.

### T006 Tests and scenarios
- Update `AgentProfileEntityTest` (:61, :93, :135, :293, :307, :317), `AgentProfileYamlTest` (:55, :65, :77, :133),
  `ProfileDtosTest` (:206, :264), `FullProfile.java:58`, `ProfileJsonTest.java:35` and `ProfileCorpusTest.java:185`.
- Add tests:
  - an `operator` profile performed by an agent is refused, by the entity and by the reader with the pointer;
  - a `sentinel` key makes the file unreadable (the error-handling test is the only place the old shape appears);
  - `performer` round-trips, and an absent `performer` stays absent.
- `contracts/charter/scenarios/agent-profile-reads.feature:79`: the row naming `sentinel` names `performer`.
  Existing scenarios over org-base profile lists or counts that now include `human-operator` are updated in the same
  change, with no exemption.
- Diagram `descriptor-agent-profile.puml:18` and its `.svg`: `sentinel` becomes `performer`.

## Definition of Done
- `grep -rn sentinel contracts service docs/architecture` finds nothing. `plan.prompt.md` is unrelated and stays.
- `./gradlew verifyAll` is green on Java 25. Nothing is exempted from the round trip.
- Do not add a CHANGELOG fragment here; WP06 writes the Breaking fragment for the whole slice.

## Reviewer guidance
- No legacy reader, no `@Deprecated`, no alias.
- Absent stays absent on the wire.
- `RoleBearer.humanPerformed()` is used by no packs code yet; WP02 uses it.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP01 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
