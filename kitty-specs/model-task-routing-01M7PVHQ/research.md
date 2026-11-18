# Research: Model-task routing

Design authority: ADR-0080 (accepted 2026-11-18). This note records the planning decisions that sit below the ADR.

## R1. Build on the main #355 leaves

- **Decision**: no code before #355 (PR #599) merges; then rebase this branch (signed) and recheck IC-01's surfaces
  (`PackRulesets`, `PackFileKind`, the reference closure rule) before WP01 starts.
- **Rationale**: #355 moves the closure into one pack ruleset and touches the kind table; a kind added before it would
  conflict on the same files.
- **Alternatives**: build on the #599 branch (rejected: it may change before merge).

## R2. Where the domain lives

- **Decision**: a `routing` domain module (as `profiles`) holds the catalog value types and the ranker; `packs` depends
  on it for the binding; a `routing-edge` module (as `profiles-edge`) holds the use case and controller.
- **Rationale**: the ranker is pure and Spring-free; ArchUnit keeps slices apart.
- **Alternatives**: the ranker in `activations` (rejected: routing rules in activation code); the catalog type in
  `packs/domain` like `Skill` (rejected: the ranker would depend on `packs`).

## R3. Exact decimal

- **Decision**: scores are `BigDecimal`, parsed from the YAML text without a float step; constants are exact
  (`0.66`, `0.33`, `0.5`); ranks compare with `compareTo`; the wire carries the score as a JSON number.
- **Rationale**: NFR-002 and decision 11 (ties) need exact equality.
- **Alternatives**: `double` with an epsilon (rejected: an epsilon is a tie-break by another name).

## R4. The two top-ranked sets

- **Decision**: rank every model with a fit for the task type; `inside` = models within every tier in play; answer
  `top(inside) ∪ top(all)`, each `top` with all ties; order by rank descending, then model id. With no tier in play
  the two sets are equal.
- **Rationale**: ADR-0080 decision 12.

## R5. The effective profile

- **Decision**: the profile candidate reads the ADR-0078 resolution (`PacksSession.profileResolution`), whatever the
  profile's state; a profile no pack of the closure holds is `404 artifact_not_found`.
- **Rationale**: ADR-0080 decision 13; the selection reads use declared fields, which is not what decision 13 asks.

## R6. The ETag

- **Decision**: a new `EffectiveTag` read, `RECOMMENDATION`, over the revision, the closure fingerprint and the
  normalized query (task type, profile, mission type, step, tier); refusals are judged before the 304.
- **Rationale**: as `selectAgentProfile` (ADR-0078's refusal order).

## R7. Example pack content

- **Decision**: built-in holds `default` (`quality_first`), with a locked policy entry in a scenario fixture only;
  org-base holds `default` (`balanced`, replacing built-in's) and `cost-led` (`cost_first`), not selected by default,
  so selecting it gives the two-catalog conflict; models are named `model-a` … `model-e` with no vendor names; one task
  type has two models tied; one model sits above the tier constraint and outranks the rest; one built-in profile
  declares `model` and `effort`; one built-in step declares `model_tier`.
- **Rationale**: each ADR-0080 rule needs a fixture that fails when the rule is removed (SC-002).

## Plan-point squad

To run on this plan (charter: contract, architecture and domain change). Findings and dispositions are recorded here.
