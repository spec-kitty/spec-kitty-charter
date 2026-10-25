---
work_package_id: WP06
title: Mutability-policy write gate (ADR-0012 open/extendable/locked)
dependencies:
- WP05
requirement_refs:
- FR-011
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts were generated on feat/regular-artefacts; completed changes must merge back into feat/regular-artefacts. Worktree per lane from lanes.json.
subtasks:
- T035
- T036
- T037
- T038
phase: Phase 6 - Governance gate
agent: claude
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityLevel.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicySource.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicyTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityLevel.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicySource.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicyTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP06 — Mutability-policy write gate

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Refuse a write that exceeds the owning pack's mutability level (FR-011). Model ADR-0012's three
levels (`open` / `extendable` / `locked`) as operation sets, map the registration operations
(`REGISTER`/`REPLACE`/`RELATIONSHIP`) onto them, and implement the real `WritePolicyGate` (the port +
permissive default were introduced in WP04) so an over-level write is refused with
`governance_policy_violation` while a within-level write on the same fixture succeeds.

Ground truth: [spec.md](../spec.md) FR-011 + the mutability edge-case; [plan.md](../plan.md) IC-06;
**ADR-0012** (the levels, the operation sets, "tighten never loosen", the `governance_policy_violation`
refusal). `governance_policy_violation` already exists as a `RefusalCode` and is mapped to `409` by
`RefusalHandler` (WP04) — you raise it, you do not add it.

## Context & Constraints

- **This is net-new** (ADR-0012's levels are not modelled in Java; `PackDescriptor` has no policy
  field today). You own `PackDescriptor.java` (kernel) for the minimal policy field if you add one.
- **Enforce only through the WP04 `WritePolicyGate` seam.** The registration services (WP05/07/08/09)
  already call `WritePolicyGate.authorize(pack, operation, target)` before mutating. You provide the
  real implementation as the `@Primary` bean (superseding `PermissiveWritePolicyGate`). **Do NOT edit
  any registration service, controller, or the commit store** — they already consult the port.
- **Operation → required level (define per ADR-0012's table and document your mapping)**: ADR-0012's
  operations are *override* (replace/change an existing artifact's fields), *enhance* (an `enhances`
  relation), *deactivate*. Map the API operations: `REPLACE` of an existing artifact requires the
  target's level to permit *override* (i.e. `open`); `RELATIONSHIP` authoring an edge touching an
  artifact requires *enhance* (i.e. `open` or `extendable`); `REGISTER` of a brand-new artifact is
  allowed unless the pack forbids additions — treat a `locked` pack/target as refusing new content in
  its scope; a missing level defaults to `open` (ADR-0012: an artifact a policy does not name, in a
  pack with no policy file, is `open`). State your final mapping in the policy class javadoc and keep
  it consistent with ADR-0012's table.
- **Scope the policy source minimally.** ADR-0012's full design is a pack-root policy file referenced
  by `pack.yaml` with per-kind defaults + per-URN entries. For this mission (FR-011 is Medium), model
  a `MutabilityPolicySource` that resolves a `MutabilityLevel` for a target URN with: a per-URN entry,
  else a per-kind default, else `open`. Whether the source reads a declared policy field on
  `PackDescriptor` or a minimal pack-root file is your call — keep it additive and grounded in
  ADR-0012; do not implement the full `replaceable-builtins.yaml` migration (out of scope). Do not
  weaken PROJECT_002 round-trip: any new descriptor field must round-trip losslessly.
- Keep the policy model in `packs.domain` (pure); the gate reads the pack's levels, no IO beyond the
  source it is given.

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP06 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :packs:test --tests '*PackMutabilityPolicyTest'` (+ `:kernel:test` if you
touch `PackDescriptor`). No full heavy suites.

## Subtasks & Detailed Guidance

### T035 — `MutabilityLevel` enum + operation sets
- Create `MutabilityLevel { OPEN, EXTENDABLE, LOCKED }` with a token (`open`/`extendable`/`locked`)
  and a method answering whether it permits a given operation, per ADR-0012's table (override/enhance/
  deactivate). Add an `Operation`-aware predicate (reuse WP04's `WritePolicyGate.Operation` enum
  `REGISTER`/`REPLACE`/`RELATIONSHIP`), e.g. `boolean permits(Operation op)` implementing your
  documented mapping. `fromToken(String)` for parsing.

### T036 — `MutabilityPolicySource` + `PackDescriptor` policy field (if needed)
- `MutabilityPolicySource`: `MutabilityLevel levelOf(Pack pack, Urn target)` — per-URN entry, else
  per-kind default, else `OPEN`. Keep it a small, testable resolver.
- If you store declared levels on the descriptor, add a minimal field to `PackDescriptor` (kernel) +
  its convenience constructor, keeping every existing field and round-trip intact (PROJECT_002). If a
  pack-root policy file is cleaner, read it through an existing storage seam without editing WP04-owned
  files (inject what you need; do not edit `DomainRepositoryService`/`FilesystemPackStore`). Prefer the
  smallest change that satisfies FR-011.

### T037 — `PackMutabilityPolicy implements WritePolicyGate` (`@Primary`)
- Implement `authorize(Pack pack, Operation op, Urn target)`: resolve the target's `MutabilityLevel`
  via the source; if the level does not permit the operation, throw `CharterRefusal(GOVERNANCE_POLICY_VIOLATION,
  <message naming the artifact, level, operation>)` (→ `409`, WP04's map). Otherwise return/allow.
- Annotate `@Primary` (or whatever makes it win over `PermissiveWritePolicyGate`); it is component-
  scanned (`packs` application/domain config), so no `deployable` wiring edit.

### T038 — Tests (test-first, red before green)
- `PackMutabilityPolicyTest`: the level→operation matrix (open permits all; extendable permits
  relationship/enhance but refuses replace/override; locked refuses all mutating ops); **FR-011 paired
  control**: a within-level write authorizes, and an over-level write on the same fixture is refused
  with `governance_policy_violation`; a target with no declared level defaults to `open` and is
  permitted; `MutabilityPolicySource` resolution order (URN entry > kind default > open).
- If feasible, a thin integration assertion that a registration through WP05's service is refused
  when the target is `locked` (reuse the registration service with the real gate) — optional, keep it
  targeted; the full end-to-end governance scenario runs in WP10's `@contract` suite.

## Test strategy

Test-first, same-fixture positive control on every refusal (FR-011 is explicitly no-op-passable:no with
a paired control). Targeted `:packs:test` only (NO_FULL_HEAVY_SUITES_IN_MISSION).

## Definition of Done

- [ ] `MutabilityLevel` models ADR-0012's three levels as operation sets with a documented
  operation→level mapping.
- [ ] `PackMutabilityPolicy` is the `@Primary` `WritePolicyGate`; an over-level write is refused with
  `governance_policy_violation` (409); a within-level write succeeds (paired control).
- [ ] Policy source resolves URN-entry > kind-default > open; any new `PackDescriptor` field
  round-trips losslessly.
- [ ] No registration service / controller / commit-store file edited. Red+green hashes recorded;
  targeted tests green.

## Risks

- **Loosening**: ADR-0012 is "tighten never loosen" and the default is `open`; do not accidentally
  default to a stricter level that breaks existing open packs (oracle non-divergence on reads is
  unaffected, but existing writes must keep working at `open`).
- **Scope creep**: do not implement the `replaceable-builtins.yaml` migration or org-precedence work
  (out of scope); model only what FR-011 needs.
- **Seam bypass**: enforce only via the `WritePolicyGate` port; editing a registration service would
  collide with WP05/07/08/09 ownership.

## Reviewer guidance

Confirm the level→operation mapping matches ADR-0012 and is documented, the over-level refusal is
`governance_policy_violation`→409 with a same-fixture within-level control, the default is `open`, any
`PackDescriptor` field round-trips, and the gate is wired only through the WP04 port (no service edits).
