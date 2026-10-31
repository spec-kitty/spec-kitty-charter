# Pre-spec research: the activation set (#125)

Read-only research before `/spec-kitty.specify`, 2026-10-31. Branch `issue-125-activation-set` at
`e2f206f5` (origin/main). This note moves into the mission directory once the mission exists.

Sources read: issue #125 and its three comments; ADR-0012, 0013, 0015, 0017, 0020, 0022, 0030,
0031, 0033; `docs/architecture/offering-and-activation.md`; the charter
(`.kittify/charter/charter.md`); the contract (`contracts/charter/**`, `contracts/_shared/**`,
`contracts/lint/**`, `contracts/src/**`); the service wiring; core at `/opt/spec-kitty` (`main`
`512567ee`, 4.0.0rc8); issues #9, #45, #46, #62-#67, #103, #126, #131, #196, #283; PRs #279, #288,
#289.

## 1. The contract today

- `info.version` is `0.1.0-SNAPSHOT`; there are **no tags** (local or origin). `CHANGELOG.md` has
  one heading, `## 0.1.0-SNAPSHOT`, and already holds a `### Breaking` subsection. With no tag the
  release gate has no baseline (`ReleaseGate` -> `NoBaseline`), so `oasdiff` compares nothing.
- `openapi.yaml` holds tags, the path map and the security scheme only; every component is a
  file `$ref` (`paths/`, `schemas/`, `responses/`, `parameters/`, `headers/`, `examples/`).
  No `Activation` tag and no `x-reserved` anywhere.
- Refusals: `CharterRefusal` = `allOf [Problem, {code}]`; `CharterRefusalCode` carries the enum
  and `x-refusal: {status, retryable, raised}`; six codes are already `raised: false`
  (`governance_policy_violation` pinned by `errors.feature:96-106`).
- `_shared/schemas/Problem.yaml` is **closed** and already carries `code`. Members added only to
  `CharterRefusal`'s second `allOf` part are refused by the first part, so `requiredBy` and
  `violations` need either the shared `Problem` extended (recorded in `_shared/VENDORED.md`) or a
  restructured `CharterRefusal`.
- Kind tokens on the wire are snake_case (`NodeKind`: `agent_profile`, `mission_step_contract`,
  `glossary_pack`, `anti_pattern`, `mission_type`, ...).

## 2. Tooling constraints the change must meet

- **Lint** (`contracts/lint/ruleset.yaml`, Spectral format run by vacuum, core functions only).
  `error-responses-are-problems` already admits `200`, `201`, `304`; a `204` fails its
  `content: truthy` check, so it needs admitting plus a no-body rule like `not-modified-has-no-body`.
  `charter-headers-on-every-response` applies to `201`/`204`/`501` too. `resource-property-cited`
  needs `x-source` or `x-derived` on every property of a titled object, unbuilt ones included.
  Each rule needs a fixture that fails only it; `LintContract` counts the rules.
- **ClosureChecker** fails closed on unknown keywords; it lacks `$schema`, `$id`, `$defs`,
  `propertyNames`, `unevaluatedProperties`, `contains`. Map-valued `additionalProperties` counts as
  open.
- **ContractConformance** (`service/deployable` test) supports only whole-file `$ref` and the
  keywords `$ref allOf oneOf type properties required additionalProperties:false items enum pattern
  minLength maxLength maxItems minimum maximum title description examples format`. `anyOf`,
  `const`, `minItems`, `uniqueItems`, `propertyNames` and map-valued `additionalProperties` fail
  the suite.
- **The ADR-0017 pattern is not wired into OpenAPI.** `contracts/packs/schemas/*.schema.json` are
  validated by the service (networknt) only; `contracts/offering/` does not exist; `bundleContract`
  and the generator read only `contracts/charter` and `contracts/_shared`.
- **Generator**: spring, `interfaceOnly`, `useTags: true`. Tag `Activation` -> `ActivationApi`;
  nothing forces an implementation, so 501 needs an `ActivationController` in
  `packs/infra/api`. There is no 501 handling in the service today. `NullOmissionTest` exempts only
  `*Registration` request models; `GeneratedModelNamesTest` forbids `_1` duplicates.
  `WireTokenParityTest` forces new codes into `RefusalCode.java` and `RefusalHandler.statusOf`.
- **Scenarios**: `scenarios/README.md:124-127` says a `@reserved` scenario "call[s] no operation".
  ADR-0020 D8 wants a `@reserved` scenario that calls the operation and sees `501`. The README rule
  must be amended (or another tag chosen). `ReadConsistencySteps` asserts every refusal observed is
  `raised: true`; a `501` carries no code, so it does not trip that.

## 3. The charter's own activation vocabulary (content alignment)

`contracts/packs/schemas/charter.schema.json` (this repository) has 11 activation keys after
#131/PR #279; PR #288 (#283, open) brings it to core's 13:

| Charter / core key | `ActivationConfig` member | Absent means |
|---|---|---|
| `activated_kinds` | `activatedKinds` | every kind |
| `mission_type_activations` | `missionTypes` | **none** (absent and `[]` both mean none) |
| `activated_directives`, `_tactics`, `_styleguides`, `_toolguides`, `_paradigms`, `_procedures`, `_agent_profiles`, `_mission_step_contracts`, `_glossary_packs`, `_anti_patterns` | `selected.<kind>` | every artifact of the kind |
| `activated_skills` | `selected.skill` | **only the required skills** (core `_REQUIRED_WHEN_ABSENT_KINDS = {SKILL}`, `artifact_kinds.py:262-274`; PR #288) |
| `deactivated_<plural>` (core ledger, same 11 kinds) | `deactivated.<kind>` | no exclusion; `[]` is never written |

Activatable kinds = every artifact kind except `template` and `asset` (core
`artifact_kinds.py:192-205`). `activated_kinds` and mission types have no deny key.

## 4. Core's activation ledger (core ADR `4.x/2026-10-19-1`, read-only reference)

The charter rules that the rewrite is designed against this repository's model, not core's
formats (epic #186). Core is cited for content alignment only.

- Ledger `.kittify/charter/activation.yaml`, flat, every key optional (ADR D1 :85-109); key set
  from `ArtifactKind.activatable` (`activation_ledger.py:146-165`).
- Writers: `prepare_ledger_write` (`activation_ledger.py:620-651`, sorts, dedupes, drops empty deny
  lists :610-617), guarded by `DenyListChoice` {`activate`, `deactivate`,
  `cascade-include-deactivated`, `preset-force`} (:537-570); `plan_activation` /
  `plan_deactivation` / `commit_plan` (`activation_engine.py:343-690`).
- Readers: `load_activation_ledger` (`activation_ledger.py:463-483`), `load_denied`
  (`pack_context.py:383`), `resolve_deny` / `DenySet` (`activation_ledger.py:270-332`).
- Required floor: union of org `required_<plural>` over declared packs and `extends:` chains,
  `read_org_required_provenance` -> `RequiringPack(pack, via)` (`org_pack_discovery.py:297-373`);
  never persisted; deactivating one raises `RequiredArtifactDeactivationError`
  (`activation_engine.py:165-203`). In this repository the required set is derived from presets'
  `must_have` items (ADR-0031, ADR-0042), not `required_*`.
- `CascadeScope` is **not an enum**: `all` or a non-empty set of kinds (`cascade.py:165-240`);
  no cascade is `None`; `mission-type` is not an operator token.
- Skipped report: `ReferencedArtifact{kind, artifact_id, urn, via_requires, required_by[urns]}`;
  `CascadeActivationResult{activated, skipped_by_scope, not_cascaded_kind_filtered,
  skipped_deactivated, unreached_behind_deactivated}`; `SharedSkip{urn, referencing_active_urn}`
  (`cascade.py:348-661`).
- Presets: replace semantics; `--force` clears listed exclusions; **no applied-preset provenance**
  ("The preset name is never persisted", `preset_application.py:55`).
- Context activations: `governance.activations` in `charter.yaml`,
  `{activation_context{mission_type, action}, charter_pack_id, artifact_id, artifact_kind?}`
  (`activations.py:194-229`). Not in the ledger.
- Languages: `catalog.languages` in `charter.yaml` (None = no signal, `[]` = none). Not in the
  ledger.
- **No per-entry provenance** in the ledger (plain id lists).
- **No `locked`** in core yet.

## 5. Deny-list semantics (#62-#67) and R9 (#103)

- #62-#64, #66, #67 were re-scoped 2026-10-20: the authority is `ActivationConfig.deactivated`;
  the cascade result is the structured `SkippedReport`; a deny on a required/locked id is `409`
  naming every requiring pack; required and locked are always active on read (ADR-0020 D7, a
  logged divergence, #9).
- #103 owns the semantics of the effective reads; #125 owns their shapes. `ActionContext` was
  removed by #139 and returns under `/offering` with #126, so **no schema exists today** for the
  effective context or an effective graph.

## 6. Siblings and numbering

- #126 (offering, API root, `Links`, `_links`): no PR yet. It is also a 0.x minor with a
  `### Breaking`. If it lands first, `Activation._links` uses its `Links` component.
- PR #288 (13 keys) is open and does not touch `openapi.yaml`; PR #289 adds no ADR.
- Highest ADR on main is 0045; no open PR adds one.
- 0.1.0 is held (#45, ruling 2026-10-27); #196 (tag ruleset) is open.
- #46 (`x-stability-level`) applies to `x-provisional` operations only; nothing is provisional now
  and it is dormant until 1.0.0.

## 7. Questions raised to the orchestrator

Batched in one message on 2026-10-31; the rulings are recorded in the mission's `research.md`.
