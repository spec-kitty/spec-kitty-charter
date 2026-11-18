# Research: Model-task routing

Design authority: ADR-0080 (accepted 2026-11-18). This note records the planning decisions that sit below the ADR.

## R1. Build on the main #355 leaves

- **Decision**: no code before #355 (PR #599) merges. Then this branch is rebased onto main, signed, and IC-01's
  surfaces are rechecked before the first WP starts.
- **What #599 touches** (squad, architecture lens, from `git diff --stat origin/main...origin/feat/355-closure-in-pack-ruleset`):
  - It rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
    `ReferenceFacts`/`ReferenceClosure`.
  - It deletes `MissionTemplates` and `GuardClosure`.
  - It does not touch `PackFileKind`, `KindBindings`, `ArtifactKind` or `NodeKind`.

  The real collision is where the FR-002 rules plug in: a `RoutingCatalogFacts` view in `PackContents` (IC-01).
- **Alternatives**: build on the #599 branch. Rejected: it may change before merge.
- **Rechecked on 2026-11-18, after #599 merged** (main `a925ede5`):
  - Every literal path the WPs own exists; only globs over files still to be created match nothing.
  - `PackContents` is a record with `List<MissionTypeFacts> missionTypes` and an `Assembly`, whose
    `references()` gives the `ReferenceFacts`. `PackRulesets.content()` composes `MissionTypeRules`, then
    `ReferenceRules`, then `FragmentRules.ORPHAN`.
  - WP05's `RoutingCatalogFacts` is a new record component beside `missionTypes`. Its rules join `content()` before
    `ReferenceRules`.
  - #601 (a pre-lock judge race in `CommitStorePackWriteStore.commit`) is outside every WP's map: no WP touches the
    commit store.

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
  `top(inside) ∪ top(fitting)`, each `top` with all ties; order by rank descending, then model id. With no tier in play
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

## R7. Example-pack content: the fixture matrix

- **Model names.** Models are `model-a` … `model-e`, with no vendor names.
- **Weights and tiers.** Every catalog below has `risk` and `latency` weights of 0 and declares no confidence or
  latency tier, unless a row says otherwise.
- **Order independence.** In org-base's `default`, `model-b` is listed before `model-a`, so the
  order-independence row fails if the file's order leaks into the answer.

**Catalogs.**

| Pack | Catalog id | Objective | Weights (quality, cost) | Content |
| --- | --- | --- | --- | --- |
| built-in | `default` | `quality_first` | 1, 0.2 | `design`: `model-a` fit 0.3 `premium` (score 0.3); `model-b` fit 0.1 `low` (score 0.3) |
| org-base | `default` (replaces built-in's) | `balanced` | 1, 0.2 | `design` as built-in. `review` with `max_tier: medium`: `model-c` fit 0.9 `premium` (0.9); `model-d` fit 0.6 `medium` (0.732); `model-e` fit 0.5 `low` (0.7). `triage` declared, with no fit. |
| org-base | `cost-led` (not in the preset) | `cost_first` | 0.5, 0.5 | `design`: `model-a` fit 0.3 `premium` (0.15); `model-b` fit 0.1 `low` (0.55) |

**Profiles and steps.**

- **A profile with a model.** One built-in agent profile declares `model: model-x` (in no catalog) and
  `effort: high`.
- **A profile with none.** `generic-agent` declares no `model`.
- **A step with a tier.** One built-in step declares `model_tier: low`.

**Expected answers.** Each row is one `@contract` scenario that asserts the exact ordered candidate list:

| # | Rule | Activation, catalog in force | Request | Expected candidates (in order) |
| --- | --- | --- | --- | --- |
| 1 | `quality_first`, unique top, decimal | built-in alone; `default` (`quality_first`) | `design`, profile with model | `model-a` (0.3, fit 0.3); then profile `model-x` with effort `high` |
| 2 | `balanced`, exact-decimal tie, file order | project closure; org-base `default` | `design`, profile with model | `model-a` (0.3), `model-b` (0.3); then profile `model-x`. Under binary floating point `model-b` would score 0.30000000000000004 and win alone |
| 3 | `cost_first` | org-base with `cost-led` selected and `default` deselected | `design`, `generic-agent` | `model-b` (0.55) only |
| 4 | tier constraint flags, never filters | project closure | `review`, `generic-agent` | `model-c` (0.9, `withinTierConstraint: false`), `model-d` (0.732, true) |
| 5 | step tier | project closure | `review`, with the `low` step | `modelTier` effective `low`, source `step`, offer `low`. Candidates: `model-c` (false/false), `model-e` (0.7, true/true) |
| 6 | request overrides the step, and the tiers combine | project closure | `review`, the `low` step, `modelTier=premium` | `modelTier` effective `premium`, source `request`, offer `low`. Candidates: `model-c` (false/true), `model-d` (true/true). The `medium` cap still holds against `premium` |
| 7 | no candidate | project closure | `triage`, `generic-agent` | none (200) |
| 8 | conflict | project closure with `cost-led` selected | `design` | `409 routing_catalog_conflict` naming `cost-led` and `default` |
| 9 | not in force | project closure with `default` deselected | `design` | `409 routing_catalog_not_in_force` |
| 10 | unknown task type | project closure | `deploy` | `404 task_type_not_found` |

Rows 5 and 6 differ in their candidates, not only in `source`, so an implementation that reports the request's tier
but computes with the step's fails row 6. Row 1 separates `quality_first` from a weighted ranking, and row 2 separates
exact decimal from binary floating point.

## Plan-point squad (2026-11-18)

**The squad.** Three lenses ran, each loading its profile (read-only):

- domain (`analyst-annie`);
- contract (`reviewer-renata`);
- architecture and migration (`architect-alphonso`).

**How findings were handled.**

- No lens contradicted another, so no second opinion was needed.
- I checked the one claim that would change the design against the source. `EndpointTable`'s `ENHANCES` and
  `OVERRIDES` diagonals read `ArtifactKind.activatable()`. A layer override needs no edge: across levels the same id
  is the override (composition rules, line 251).
- Every finding is listed below with its disposition. All were accepted into the plan. The one that touches the
  accepted ADR's wording (C1, the link names) is a convention fix, recorded there and reported to the operator.

| # | Lens | Sev. | Finding | Disposition |
| --- | --- | --- | --- | --- |
| D1 | domain | MAJOR | Example packs do not make each rule observable; no exact expectations | Accepted: R7 fixture matrix |
| D2 | domain | MAJOR | The request's override can pass by asserting only `source` | Accepted: rows 5 and 6 differ in candidates |
| D3 | domain | MAJOR | Scenarios pass on containment | Accepted: exact ordered lists; unique-top row 1 (IC-05) |
| D4 | domain | MAJOR | "Inside every tier in play" has no scenario that fails | Accepted: row 6 (`premium` request against a `medium` cap); data-model rule 3 |
| D5 | domain | MEDIUM | Risk and latency scales are ambiguous | Accepted: full table in data-model rule 1 |
| D6 | domain | MEDIUM | Score range, weight sum and all-zero weights are not stated | Accepted: data-model rule 1 |
| D7 | domain | MEDIUM | The score's wire form is open | Accepted: data-model rule 7 |
| D8 | domain | MEDIUM | No fixture for exact decimal | Accepted: row 2 |
| D9 | domain | MEDIUM | The profile candidate's place and duplicates are not stated | Accepted: data-model rules 5 and 6 |
| D10 | domain | MINOR | Order direction | Accepted: descending (rule 6) |
| D11 | domain | MINOR | 400 for a malformed `taskType` or `modelTier` | Accepted: refusal order 1 |
| D12 | domain | MINOR | Order independence has no fixture | Accepted: R7, org-base file order |
| C1 | contract | MAJOR | Link keys `catalog`, `agentProfile`, `step` fail `link-relations-are-known` | Accepted: kebab-case `catalog`, `agent-profile`, `step`, `routing-catalogs`; ruleset, hypermedia table, activation link (IC-02, IC-05); ADR-0080 decision 8 wording corrected |
| C2 | contract | MEDIUM | The 500's place in the order | Accepted: after the step 404s, before the 409s, as `selectAgentProfileForStep` |
| C3 | contract | MEDIUM | The changelog fragment must be `### Breaking` | Accepted: IC-06 |
| C4 | contract | MEDIUM | Packs CHANGELOG and classifier cases are missing | Accepted: IC-01 |
| C5 | contract | MEDIUM | The sketch leaves out statuses the switch gate counts | Accepted: IC-02; `contracts/operations.md` |
| C6 | contract | MEDIUM | No tag for the new read | Accepted: `ModelRecommendations` (IC-05) |
| C7 | contract | MEDIUM | `artifacts.feature` and `offering.feature` enumerate kinds | Accepted: IC-01, IC-02; kind appended last |
| C8 | contract | MINOR | Parameter files, patterns, `CostTier` schema, `x-source` | Accepted: IC-02, IC-05 |
| C9 | contract | MINOR | 404 and 409 interleave | Accepted: refusal order 8 |
| C10 | contract | MINOR | `CharterRefusalCode` prose and `x-refusal` | Accepted: IC-05 |
| C11 | contract | MINOR | A step whose mission type is not in force | Accepted: offered, as `selectAgentProfileForStep`; no `mission_type_not_in_force` (refusal order 4) |
| A1 | architecture | MAJOR | `ENHANCES` and `OVERRIDES` admit catalog edges | Accepted: excluded in `EndpointTable`; ADR-0077 decision 5 rows amended; endpoint refusal scenario (IC-01) |
| A2 | architecture | MAJOR | FR-002 rules cannot live in `routing.domain` under `PACKS_DOMAIN_IS_PURE` | Accepted: option (a), a `RoutingCatalogFacts` view in `PackContents` (IC-01) |
| A3 | architecture | MEDIUM | Slice governance and build wiring missing | Accepted: new IC-00, with an ADR-0037 amendment |
| A4 | architecture | MEDIUM | IC-03 depends on the tier vocabulary | Accepted: kernel `CostTier` in IC-01; IC-03 depends on it |
| A5 | architecture | MEDIUM | R1 misstates #599's overlap | Accepted: R1 corrected |
| A6 | architecture | MEDIUM | Native null-omission mix-ins and hints | Accepted: IC-02, IC-03, IC-05 |
| A7 | architecture | MINOR | Taxonomy indexes pinned | Accepted: kind appended last (IC-01) |
| A8 | architecture | MINOR | Manifests and count scenarios ripple | Accepted: IC-01 |
| A9 | architecture | MINOR | The open-packs follow-up's scope, and the 409 until it lands | Accepted: C-004 in the spec names the scope and the interim 409. Filing time stays as the orchestrator set it |
