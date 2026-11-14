# Squad: similar surfaces (pre-spec, 2026-11-14)

Lens: what slice G must reuse or align with, on `main` at `b1d73b1`. Dispositions in the last column.

## (a) Selection by role (#42)

| Surface | Where | Disposition |
| --- | --- | --- |
| `RoleFit` (`PRIMARY`, `ALT`, `NONE`), `AgentProfile.roleFit(asked)` (never fits `generic`) | `service/profiles/.../domain/RoleFit.java`, `AgentProfile.java:227` | Reuse: selection steps 1 and 2 |
| `RoleBearer` (kernel view of role and alt-roles) | `service/kernel/.../domain/RoleBearer.java` | Reuse for packs-side pin checks |
| `routing-priority` (0 to 100, schema default 50; `Optional` in the entity) | `agent-profile.schema.json:316`, `AgentProfile.java:284` | Reuse; the selection applies the default 50 |
| `specialization-context` (six descriptor lists) | `SpecializationContext.java` | Reuse for "fits the context" (ruling D2) |
| `fallback_profile` read and stored; three refusals raised at registration | `PackDescriptor.fallbackProfile()`, `FallbackProfileRule`, `RegistrationImpact.judgeFallbacks` | Reuse. "The fallback in force" (most derived declaring pack) is **not built**: G builds it |
| Reserved `fallback_profile_conflict` (422) and `no_profile_matched` (404), mapped in `RefusalHandler` | `RefusalCode.java:237,239`, `CharterRefusalCode.yaml:897-906` | G raises both: flip `raised`, name in main code, `errors.feature` list and a pin scenario |
| Role pins | `policy.schema.json:17` reserves them; the closed schema refuses a `pins` key | G defines the shape (ruling D3) |
| Step role | `MissionStep.yaml:38`, `mission-type.schema.json:27`, `MissionStep.java` (`Optional<String> role`) | On `main` already; slice F does not change it |
| Effective-read pattern: found → open session → require → tag → 304 | `EffectiveReadService.actionContext` (:486), `prompt` (:541); `EffectiveTag` queries | Reuse; add a sibling query, not a new mechanism |
| Lineage walk (`specializes_from`, transitive, in-force) | `GovernanceWalk` LINEAGE, `ReachRequest.Lineage.specializesFrom()`, `ClosureViewSession:358` | Reuse; it lists entries but exposes no ordered chain or depth, which G adds once |

Example packs (`service/packs/src/testFixtures/resources/example-packs`): built-in `generic-agent` (generic, locked,
the fallback in built-in and project), `implementer-ivan` (implementer, 50), `java-jenny` and `python-pedro`
(implementer, 80, both specialize `implementer-ivan`), `reviewer-renata` (reviewer, 50); org-base
`qa-defect-triager-dmitri`; project `project-reviewer` (reviewer, alt `implementer`). software-dev steps ask for
implementer, reviewer and facilitator. Missing: a pin, a human-performed profile, an `operator` step, a profile
`enhances` edge, a sibling fallback conflict.

## (b) Human performer (#512)

`sentinel` appears in: the pack schema (`agent-profile.schema.json:375`), the contract (`AgentProfile.yaml:127`), two
contract examples, `agent-profile-reads.feature:79`, the entity, the DSO, binding and YAML codec, two DTO mappers,
`java-jenny.agent.yaml:202`, eight test classes, and `descriptor-agent-profile.puml`. All are replaced in one change; no
reader of the old key is kept (charter, "Designing during the rewrite"). `plan.prompt.md:198` ("sentinels") is
unrelated and stays.

## (c) Enhancement that drops fields (#380)

- No profile field merge exists; `AgentProfile.java:24` and `getEffectiveAgentProfile`'s description say so.
- Reuse: `ImpactRelations` (enhances cycle, enhancer without root, enhances-and-overrides conflict),
  `InForceResolution` (an enhancer is out of force when none of its targets is), and the step add-only merge
  (`EnhancementCompose`, `StepEnhancementRules`) as the model for an add-only profile merge.
- `excluding` is stored as authored only (`AgentProfile.excluding()`, `Exclusion.java`).
- The placeholder is `contracts/charter/scenarios/profiles.feature` (`@WIP`, promise `PR-CHARTER-REFUSAL-001`); the
  promise row is `tests/scenarios/charter/PROMISES.md:24`. `refuses-unsound-governance.feature`, named in #380, was
  folded away by #374.

## (d) R8 resolution and lineage (#102)

`getPackAgentProfile` and `getEffectiveAgentProfile` exist (`AgentProfilesController`,
`EffectiveAgentProfilesController`, `ProfileReadService`, `ActivationsEffectiveProfiles`). The effective read answers
the stored leaf body plus lineage-walked governance entries. `docs/api/service-contract.md:284` leaves the field
merge to #102.

## Reuse, don't duplicate

1. `roleFit` and `RoleFit`; `RoleBearer` on the packs side.
2. One lineage walk: extend `GovernanceWalk`'s LINEAGE with an ordered chain, rather than writing a second traversal.
3. `ClosureView.holds`, `winnerPackOf`, `isLocked` and `FallbackProfileRule`'s holder lookup for the fallback in force.
4. The effective-read order and `EffectiveTag`.
5. The reserved codes, already mapped to statuses.
