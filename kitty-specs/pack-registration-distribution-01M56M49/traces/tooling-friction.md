# Tooling friction — pack-registration-distribution-01M56M49

Running the built-in **`plan`** mission type end-to-end in this charter project
surfaced a cascade of defects. The type is **not production-ready**: three
layered gaps block it, two worked around, one unresolved. This log is the source
for a **core P0 issue** to remediate the `plan` mission type holistically.

- **[2026-10-18] `plan` mission type ships without step contracts.** Composition
  failed with `No step contract found for mission/action plan/specify` — the
  built-in pack had `research-*` / `documentation-*` / bare software-dev
  contracts but no `plan-specify|research|plan|review.step-contract.yaml` in
  `built-in/missions/built_in_step_contracts/`. **Disposition: fixed (pack).**
  Authored canonically in `spec-kitty-open-packs#42`, ported to core
  `spec-kitty/spec-kitty#6372` (WIP), reached the core read path via re-vendor.

- **[2026-10-18] `plan` is missing from the hardcoded `_ACTION_PROFILE_DEFAULTS`
  table (core code).** After contracts landed, composition failed with
  `profile_hint is required when no action default exists for plan/specify`.
  The table (`src/specify_cli/mission_step_contracts/profile_defaults.py:28`)
  enumerates only `software-dev` / `research` / `documentation`; a mission not
  in it must resolve via each step's `agent_profile`, but `plan`'s
  `mission-steps/plan/*/step.yaml` are `agent_profile: null` (correctly, as
  built-in steps). **Disposition: worked around** by hand-setting `agent_profile`
  on the four steps in this mission's **frozen run template**
  (`.kittify/runtime/runs/1be14d90e35.../mission_template_frozen.yaml`) — this
  mission only. **Design issue filed: this repo #42** (make profile roles
  pack-declared + charter-resolved, retire the hardcoded table).

- **[2026-10-18] `plan` artifact gate hits the `_check_cli_guards`
  software-dev-hardcode defect — UNRESOLVED, BLOCKING.** After the profile
  workaround, `next --result success` fails with `Required artifact missing:
  spec.md` even though `spec.md` is committed in the root-checkout mission dir
  AND copied into the coord worktree; the presence check is filesystem
  (`.exists()`), yet reports missing from both real locations, and
  `guard_failure_paths` returns `{}` (cannot even name the directory checked).
  Root cause is the documented defect: `_check_cli_guards` hardcodes
  `mission_family="software-dev"` for every type
  (`src/runtime/next/runtime_bridge.py:234`; noted in this repo's
  `.kittify/missions/plan/expected-artifacts.yaml:11-18`), so the `plan` gate
  resolves against a path that is not its mission dir. **No surgical
  per-mission workaround found.** **Disposition: PIVOTED** — doing the design
  work outside the runtime step-gate (operator decision 2026-10-18); the mission
  artifacts live in `kitty-specs/pack-registration-distribution-01M56M49/`, and
  Decision Moments are used for operator calls, without forcing step-state
  advancement.

## For the core P0 issue (to file later)

Remediate the `plan` mission type (and the class of non-software-dev built-in
mission types) holistically:
1. Ship step contracts for every built-in mission type (done for `plan` via
   open-packs#42 / core#6372) — add a completeness check so a shipped
   mission type without its contracts fails CI, not a downstream project.
2. Replace the hardcoded `_ACTION_PROFILE_DEFAULTS` with pack-declared
   recommended roles resolved through the activated-charter seam (this repo #42).
3. Fix `_check_cli_guards`' `mission_family="software-dev"` hardcode so each
   mission type's artifact gate resolves against its own family/mission dir
   (`runtime_bridge.py:234`); make `guard_failure_paths` always name the path
   it read. This is the blocking P0.
