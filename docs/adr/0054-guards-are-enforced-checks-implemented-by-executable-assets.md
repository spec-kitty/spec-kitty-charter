---
title: "ADR-0054: Guards are enforced checks implemented by executable assets"
description: "Transitions name guard artifacts, each implemented_by an executable asset or a closed built-in, run to a fixed interface and failing closed; replaces the gate kind."
doc_status: active
updated: 2026-11-02T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0040-content-form-and-payload-kinds
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - architecture/logical-model
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: pack-author
    guidance_text: Read this for how to declare a guard, back it with a script, and attach it to a transition.
  - profile: charter-service-contributor
    guidance_text: Read this before writing the guard schema, the asset executable block or the implemented_by relation.
  - profile: core-maintainer
    guidance_text: Read this for the guard/1 interface core runs, the built-in guard list and the fail-closed rule that replaces fail-open gates.
---

# ADR-0054: Guards are enforced checks implemented by executable assets

## Status

**Accepted**, 2026-11-02. Decider: Stijn Dejongh (operator). The operator did not accept the `gate` kind
[ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) proposed in decision 14 (a declared
condition with a severity). The operator ruled instead: "I see them as enforced guards with an `implemented_by` /
`provided_through` relationship to an executable asset, making them deterministic and checkable." A research squad
(architect, contract impact, devil's advocate) then designed the model, and the operator ruled on its points the same
day ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 16).

This record **supersedes ADR-0050 decision 14** and **amends ADR-0050 decision 12** (the guard entry shape). It
settles [#156](https://github.com/spec-kitty/spec-kitty-charter/issues/156), which should be renamed to the
`guard` kind, and [#155](https://github.com/spec-kitty/spec-kitty-charter/issues/155) folds into the guard entry.

## Context

- **Decision 12 already declares guards inline on transitions**, as `guard: [{gate, config?}]`. Decision 13 makes
  blocking expected artifacts the built-in structural guard.
- **No guard runs an asset today.** Core's step-contract `GateBinding{on_transition, handler, handler_kind,
  fail_open}` admits `handler_kind: asset`, but nothing runs one until core #2599. `GATE_REGISTRY` holds one entry,
  `spec-kitty-pre-review` on `in_progress -> for_review`, and every gate fails open (core constraint C-003). (Core
  `9aed3a8`: `review/gate_registry.py:120-126`, `tasks_move_task_gates.py:903-946`.)
- **One pack asset already behaves like a guard.** The qa pack's `qa_carrier_lint.py` exits 0 when ready, 1 when
  blocked and 2 when it cannot measure. It is "agent-invoked, never machine-enforced" for now.
- **Core's lane guards are in-process predicates** over the transition inputs (`subtasks_complete`,
  `reviewer_approval`, `review_ref`, …) in `status/wp_state.py`.
- **An asset sidecar cannot say it is executable.** It holds `id`, `mime`, `path` and `title`, closed. No relation
  names "implemented by". The built-in `docs_structural_lint.py.asset.yaml` path is anchored wrongly, the defect
  [ADR-0040](/adr/0040-content-form-and-payload-kinds/) names.
- **Core already accepted asset-backed gates.** Core ADR `3.x/2026-08-13-2` (carried here as
  [#156](https://github.com/spec-kitty/spec-kitty-charter/issues/156), from core #3418) designed gates as asset-backed
  artifacts with a gate-to-asset edge. Its acceptance was amended on 2026-09-27 with hardening:
  - `interpreter` is checked against a fixed allowlist of vetted tokens, and a shell (`sh`, `bash`, …) or an absolute
    path is refused at load;
  - `args` is a list, with the asset as exactly one argv element, and no shell-string field exists.

  This record keeps that hardening and moves it to the asset's `executable` block (decision 5).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
1. **The kind is `guard`, not `gate`.** A guard is an artifact, `guards/<id>.guard.yaml`, read at
   `/packs/{packId}/artifacts/guard/{id}`. Proposed shape, closed:

   | Field | Required | Shape |
   |---|---|---|
   | `schema_version` | yes | `"1.0"` |
   | `id` | yes | kebab-case, the file stem |
   | `title` | yes | string |
   | `description` | no | string |
   | `interface` | yes | `guard/1` (decision 4) |
   | `implemented_by` | yes | `asset:<id>` (or `<pack>::asset:<id>`), or `builtin:<id>` (decision 6) |
   | `config_schema` | no | a JSON Schema the entry's `config` is checked against at load |
   | `limits` | no | `{timeout_seconds?, network?}`; network defaults to `false` |
   | `on_indeterminate` | no | `block` (default) or `pass` (decision 8) |

2. **A transition names guards, not assets.** The entry is `guard: [{ref, config?, blocking?}]`, in mission types,
   lifecycles and step enhancements alike:
   - `ref` names a guard, bare in the same pack and `<pack>::guard:<id>` across packs
     ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 7);
   - `config` is checked against the guard's `config_schema`;
   - `blocking` (operator addition, default `true`) decides whether a failing guard blocks this transition or only
     reports. It sits on the entry, so one guard can block on one transition and only warn on another.

   A guard in between keeps the check reusable across mission types and lifecycles, survives a script rename, and
   carries the interface version and limits. One asset can implement several guards.
3. **`implemented_by` is the relation from a guard to its implementation.** It joins the `Relation` vocabulary,
   directed from guard to asset. A guard has exactly one implementation; an asset may implement many guards. It is
   authored as the guard's field and derived into the graph, so the closure check refuses a guard whose asset is
   missing. `provided_through` was the other name offered; it is not used, because it reads like pack delivery and
   would blur with `depends_on`.
4. **The guard/1 interface makes a guard deterministic and checkable.**
   - **Input:** the client passes one JSON object on stdin: the mission type, the step or lifecycle, `from`, `to`,
     `on`, the work-package id when there is one, the repository root, the changed files, and the entry's `config`.
   - **Exit code:** 0 pass, 1 fail, 2 could not evaluate. Any other code, or overrunning `timeout_seconds`, counts
     as 2.
   - **Output:** stdout carries `{verdict, findings: [{message, path?, severity?}]}`. A missing, non-JSON or
     ill-formed verdict on stdout counts as 2, the same as a non-0/1/2 exit.
   - **The charter checks at load:** the guard's schema, that `implemented_by` resolves in the `depends_on`
     closure, that the asset declares an `executable` block (decision 5), and that every entry's `config` fits.
   - **The client enforces:** running the guard in a sandbox (a read-only repository and no network unless
     `limits.network`), the timeout, the trust check (decision 7) and the verdict.
   - Real determinism also depends on the script being a function of its input; the charter can check only the
     declaration.
5. **An asset can declare that it runs.** The asset sidecar gains an optional `executable` block:
   `{runtime, min_version?, entry, args?}`, for example `{runtime: python, min_version: "3.11", entry:
   qa_carrier_lint.py, args: ["--guard"]}`. Only an asset with this block can implement a guard, or be named by a
   declared action (ADR-0053 decision 17). The asset path anchoring defect is fixed before any asset backs a guard.
   The block carries core ADR `3.x/2026-08-13-2`'s hardening:
   - `runtime` is one of a fixed allowlist of vetted tokens (`python` first), checked at load;
   - a shell or an absolute path is refused;
   - `args` is a list in which `entry` is exactly one argv element;
   - there is no shell-string field.
6. **Core's lane checks are built-in guards.** `subtasks_complete`, `reviewer_approval`, `review_ref` and the rest,
   and today's `spec-kitty-pre-review`, become guard artifacts in the foundation pack with
   `implemented_by: builtin:<id>`. The `builtin:` ids are a closed list, versioned with the contract; the client's
   own code implements them, and a client refuses an id it does not know. A built-in runs in the client's own
   process: `guard/1` is the protocol for asset-backed guards, and a built-in returns the same verdict shape, so the
   transition rule reads one result either way. Blocking expected artifacts stay the structural guard of ADR-0050
   decision 13, with no artifact.
7. **Guards fail closed.** A guard that reports a *fail* blocks its transition when the entry has `blocking: true`; with `blocking: false` the
   failure is reported and the transition proceeds. A guard that could not be evaluated is an *indeterminate* result:
   it blocks under the same `blocking` rule, unless the guard itself declares `on_indeterminate: pass` (decision 8),
   which makes it a pass. Indeterminate
   covers exit 2, a timeout, a missing runtime, and an asset whose trust cannot be verified under the policy of
   [#157](https://github.com/spec-kitty/spec-kitty-charter/issues/157). The client's `force` (ADR-0050 decision 20)
   is the only bypass. Until #157 lands, asset-backed guards run only from packs the project explicitly trusts, and
   the project's activation file names them (open question 3, ruled on 2026-11-02); a
   guard from any other pack is refused at load, and that refusal is not an indeterminate verdict, so neither
   `blocking` nor `on_indeterminate` overrides it. Built-in guards need no trust check.
8. **`on_indeterminate: pass` is allowed** (operator ruling). A guard may declare that "could not evaluate" counts
   as a pass, for example a check that cannot measure a project without tests. It is the guard author's decision,
   stated in the guard file, so a reader sees it.
9. **A guard carries no severity.** Whether a failing guard stops the transition is the entry's `blocking`. The
   `smell` / `severe` / `breaking` ladder
   ([ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) decision 10) may appear on
   findings, for reporting.
10. **A step may add guards to the lifecycle it runs.** `submachine.add_guards: [{on: {from, to}, guard: [...]}]`
    attaches guards to transitions of the shared lifecycle for this mission type only. It is add-only and may not
    remove a guard.
11. **A cross-pack asset is pinned by the pack, not the reference.** A guard in pack A implemented by an asset in
    pack B relies on A's `depends_on` range on B and B's pack fingerprint, with no digest per reference.
12. **Migration of today's binding.**
    - `review.step-contract.yaml`'s `gates[0]` becomes `ref: spec-kitty-pre-review` on the foundation work-package
      lifecycle's `in_progress -> for_review` transition.
    - `handler_kind` maps to `implemented_by`. `fail_open` does not migrate as a flag: it is core's blanket swallow of
      every fault (`NO_COVERAGE` for any exception, `CANCELLED` for an interrupt), which is the behavior this record
      ends. A guard that must keep tolerating one condition declares that condition: `on_indeterminate: pass` when it
      cannot evaluate, or a guard that returns pass itself. `provenance` is dropped,
      because the pack gives the origin.
    - `gates` and `$defs.GateBinding` leave `mission-step-contract.schema.json`.
    - Core's `GATE_REGISTRY` becomes its table of built-in implementations, and core #2599 becomes the asset runner.
    - Today's pre-review gate returns `SKIPPED` when no test command is configured. It counts as a pass, so the move
      to fail-closed does not block such projects.

```yaml
# lifecycles/work-package.lifecycle.yaml (foundation), one transition
- from: in_progress
  to: for_review
  guard:
    - ref: subtasks-complete             # same pack: bare id
    - ref: qa::guard:traceability-ready  # another pack: pack-qualified
      config: {carrier: qa-carrier.yaml}
      blocking: false                    # report only on this transition

# qa/guards/traceability-ready.guard.yaml
schema_version: "1.0"
id: traceability-ready
title: QA traceability carrier is ready
interface: guard/1
implemented_by: asset:qa-traceability-lint
config_schema: {type: object, required: [carrier], properties: {carrier: {type: string}}}
limits: {timeout_seconds: 120, network: false}

# qa/assets/qa_carrier_lint.py.asset.yaml (new block)
executable: {runtime: python, min_version: "3.11", entry: qa_carrier_lint.py, args: ["--guard"]}
```

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- A guard is deterministic as far as a declaration can make it: one verdict shape, a sandbox and a timeout for an
  asset-backed guard, and a result the client cannot reinterpret.
- Pack authors ship checks as scripts, not as requests to core. An org's static-analysis gate before review is a
  pack asset and a guard file.
- Core's hardcoded lane guards become declared artifacts that can be inspected, without being rewritten.

### Negative

- Two files per scripted check (the guard and the asset), and a process start per evaluated transition.
- Fail-closed reverses core's fail-open gates (C-003); a project that relied on a silently skipped gate now sees a
  blocked transition, unless the guard opts into `on_indeterminate: pass` or the entry sets `blocking: false`.
- The `builtin:` list is an exception to "everything is an asset". It is kept closed and versioned for that reason.

### Risks

- `on_indeterminate: pass` is always allowed, so a careless guard can quietly pass everything. The client should
  report every indeterminate pass.
- Sandboxing differs per client and operating system; the charter cannot verify it.

### Follow-ups

Nothing below is applied in this record.

- **Pack schemas** (#114):
  - `guard.schema.json` replaces the planned `gate.schema.json`;
  - the guard entry `{ref, config?, blocking?}` replaces `{gate, config?}` in the mission-type, lifecycle and
    step-enhancement schemas;
  - `asset.schema.json` gains `executable`;
  - `mission-step-contract.schema.json` loses `gates` and `GateBinding`;
  - `file-kinds.yaml` gains `*.guard.yaml`, and `guard` joins the kind lists.
  - `config_schema` is an open map, so the S4 rule of
    [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) needs an amendment.
- **Contract:**
  - `Relation` gains `implemented_by`;
  - the `guard` read joins the `Artifact` `oneOf`;
  - `NodeKind` gains `guard` and loses the planned `gate`;
  - these are breaking on the 0.x line (ADR-0022).
- **Glossary:** "Gate" is deprecated in favor of "Guard"; "Guard" is redefined by this record.
- **Core:** #340 (spec-kitty-charter) becomes "guard registry and the guard/1 runner". #339 maps `GateBinding` to
  guard entries.
- **spec-kitty-open-packs:** the foundation built-in guard files, the qa lint as the first asset-backed guard, and
  the asset path fix.

## Open questions

1. **The `builtin:` list.** *Recommended:* exactly core's current lane guards plus `spec-kitty-pre-review`,
   enumerated in the contract when the guard schema lands.
   *Ruled 2026-11-02 (operator, #156): fewer, coarser ids. The closed list is six: `transition-context-complete` (an
   actor, a workspace context, and a reason where the edge needs one), `dependency-ready`, `implementation-ready`
   (subtasks complete and implementation evidence present), `review-approved` (reviewer approval, the in-review
   approval, the review result and its consistency), `review-ref`, and `spec-kitty-pre-review`. Core's
   `no_review_conflict` always allows and has no id. `guard.schema.json` and the service's `BuiltinGuard` hold the
   list; the contract enumerates it with the guard read (work breakdown item 13).*
2. **The changed-files input.** *Recommended:* relative to the repository root, against the transition's base
   (the work package's base commit).
   *Left open by the operator on 2026-11-02 (#156): no schema or check of the charter depends on it; it is decided
   with the guard/1 runner (core C9, #340).*
3. ~~**Where the interim trust mark lives.**~~ **Ruled 2026-11-02 (operator):** the project's activation file names the
   packs it explicitly trusts, where the pack ids and versions are already named, so one read settles the trust check.
   That file is the source of truth for the anchors, so the load-time refusal has one owner; #157 may widen the
   policy, not move the mark.

## Alternatives considered

### A declared `gate` kind with a severity (ADR-0050 decision 14)

Rejected by the operator. A declared condition with no implementation is not checkable: each client would interpret
it.

### Transitions name assets directly

Rejected. The interface version, limits and config schema would repeat at every use, and renaming a script would
touch every transition.

### Lane checks rewritten as foundation scripts

Rejected by the operator in favor of the `builtin:` ids. Rewriting the predicates gains nothing that a closed,
versioned list does not already give, and costs a process start per lane move.

### Fail-open until trust lands

Rejected by the operator. A guard that silently passes is not enforced.

## References

- [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decisions 12 to 14 and 20;
  [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decisions 7, 16, 17 and 19.
- #155 (closed into #156), #156, #157, #339, #340; core #2599, #3418 and constraint C-003; core ADR
  `3.x/2026-08-13-2` and its 2026-09-27 amended acceptance.
- Core `9aed3a8`: `review/gate_registry.py`, `review/gate_bindings.py`, `status/wp_state.py`,
  `cli/commands/agent/tasks_move_task_gates.py`; spec-kitty-open-packs `1060bfc`: `packs/qa/assets/qa_carrier_lint.py`,
  `packs/built-in/missions/built_in_step_contracts/review.step-contract.yaml`.
