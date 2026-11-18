# Mission Specification: Model-task routing: the catalog kind and the advisory recommendation

**Mission Branch**: `feat/108-model-task-routing`
**Created**: 2026-11-18
**Status**: Draft
**Input**: Charter #108 (cutover read R14). Operator rulings of 2026-11-18, recorded on #108 and in
[ADR-0080](../../docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md) (accepted the same day).

## Intent Summary

- **Primary actor:** a client of the charter service, such as core's dispatch, choosing a model for a task.
- **Trigger:** it has a task type, the agent profile that performs it and, optionally, the mission step it runs in,
  under a project's activation.
- **Outcome:** the service answers advice. It gives the catalog's top-ranked models and the profile's own model, each
  tagged with its source, and says how each catalog model sits against the tiers in play. The client decides.
- **Invariant:** the service never picks for the client and never hides a model. Ties are all answered; a model
  outside a tier stays visible, flagged.
- **Second actor:** a pack author, who ships, overrides and locks the routing catalog like any other artifact.
- **Canonical terms:** *routing catalog* (the artifact kind `routing_catalog`), *task type*, *model tier* (a cost tier
  offered by a step or asked for by the request), *tier constraint* (the catalog's advised highest tier for a task
  type), *catalog candidate*, *profile candidate*. Avoid *preferred model* (core's field name), *routing decision* (the
  service decides nothing) and *filter* for a tier.

The intent is confirmed by the operator's rulings on ADR-0080, relayed by the orchestrator on 2026-11-18.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pack ships a routing catalog, and a project reads the one in force (Priority: P1)

A pack author adds a routing catalog to a pack. A client reads it in the pack, and reads the catalog in force under a
project's activation, where an org pack's catalog of the same id replaces the built-in one and a locked catalog
cannot be replaced.

**Why this priority**: the recommendation reads the catalog in force, so the kind must exist first.

**Independent Test**: register an example pack with a catalog, read it through the pack reads and the activation
reads, and check the layer merge and the policy against example packs at two levels.

**Acceptance Scenarios**:

1. **Given** a pack holding a catalog, **When** a client lists the pack's catalogs and reads one, **Then** it gets the
   summary fields and the whole catalog in its canonical shape.
2. **Given** an activation whose closure holds a built-in catalog and an org catalog of the same id, **When** the
   client reads the effective catalog, **Then** it gets the org catalog, with its pack, level, state and policy.
3. **Given** a catalog whose `task_fit` names a task type it does not declare, **When** the pack is registered,
   **Then** the registration is refused as invalid content.
4. **Given** a locked built-in catalog, **When** an org pack registers a catalog of the same id, **Then** the write is
   refused by the mutability policy.

---

### User Story 2 - A client asks which model fits a task (Priority: P1)

A client asks, under an activation, for a model for a task type and a profile. It gets the catalog's top-ranked models
with their scores and rationale, and the profile's model and effort.

**Why this priority**: it is the read core's dispatch makes today.

**Independent Test**: call the recommendation read on example packs whose catalogs make each rule visible.

**Acceptance Scenarios**:

1. **Given** a `quality_first` catalog, **When** the client asks, **Then** the model with the highest fit score is the
   catalog candidate, and the weighted sum only separates equal fits.
2. **Given** a `balanced` or a `cost_first` catalog, **When** the client asks, **Then** the models are ranked by the
   weighted sum, and the answer names the objective the catalog declares.
3. **Given** two models tied at the top rank, **When** the client asks, **Then** exactly these two are the catalog candidates, in rank order and then by model id.
4. **Given** a profile that declares a model and an effort, **When** the client asks, **Then** a profile candidate
   carries them, beside the catalog candidates.
5. **Given** a declared task type no model fits and a profile with no model, **When** the client asks, **Then** the
   answer is a success with no candidates.

---

### User Story 3 - A step's tier and the catalog's tier constraint advise the choice (Priority: P2)

A step offers a model tier, the request may ask for another, and the catalog advises a highest tier per task type. The
answer reports the effective tier and its source, and flags each catalog candidate against each tier.

**Why this priority**: it completes the advice; without it a client cannot honor a step's tier without re-ranking.

**Independent Test**: call the read with and without a step and with a tier override, on a catalog whose best model
is above the tiers.

**Acceptance Scenarios**:

1. **Given** a step with a `model_tier`, **When** the client asks with that step, **Then** the answer reports the tier
   with source `step` and the step's offer.
2. **Given** the same step and a `modelTier` in the request, **When** the client asks, **Then** the request's tier is
   effective, with source `request`, and the step's offer is still reported.
3. **Given** a best model above the effective tier, **When** the client asks, **Then** exactly the best model inside the tier
   and the best model overall are the catalog candidates, each flagged.
4. **Given** a best model above the task type's tier constraint, **When** the client asks, **Then** it is answered,
   flagged outside the constraint, beside the best model inside it.

---

### User Story 4 - A wrong request is refused clearly (Priority: P2)

**Independent Test**: one scenario per refusal, in the documented order.

**Acceptance Scenarios**:

1. **Given** no catalog in force, **When** the client asks, **Then** the answer is `409 routing_catalog_not_in_force`.
2. **Given** two catalogs in force, **When** the client asks, **Then** the answer is `409 routing_catalog_conflict`,
   naming both.
3. **Given** a task type the catalog does not declare, **When** the client asks, **Then** the answer is
   `404 task_type_not_found`.
4. **Given** an unknown activation, profile, mission type or step, or a mission type without a step, **When** the
   client asks, **Then** it gets the refusal ADR-0080 decision 9 lists, in that order.

### Edge Cases

- Ties at the top inside the tiers and outside them at once: every tied model of both sets is answered, once each.
- No model inside the tiers fits: only the top-ranked overall are answered, flagged outside.
- No tier in play: the catalog candidates are the top-ranked models, with no tier flags beyond
  `withinTierConstraint: true`.
- The profile's model is not in the catalog: it is still the profile candidate.
- The profile is held but not in force: it still gives its candidate, as the effective profile read answers it.
- A catalog deselected by the activation is not in force; with no other catalog the read is refused.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | The routing_catalog kind | As a pack author, I want the routing catalog to be an artifact kind with its pack schema and file rule, so that a pack can ship one and registration judges it. | High | Open | [build] | no |
| FR-002 | Catalog content rules | As a pack author, I want an undeclared task type in a fit or a tier constraint, a duplicate id and two constraints for one task type refused at registration, so that the recommendation never reads inconsistent content. | High | Open | [build] | no |
| FR-003 | The pack reads | As a client, I want the pack's catalog list and item reads, and `?kind=routing_catalog` on the pack's artifact list, so that I can read a pack's catalogs. | High | Open | [build] | no |
| FR-004 | The effective reads | As a client, I want the activation's catalog list and item reads, and `?kind=routing_catalog` on the effective list, answering the layer-merge winner with state, reason, policy and required, so that I can read the catalog in force. | High | Open | [build] | no |
| FR-005 | Activation and policy | As a project, I want to select, deny and require a catalog, and an org pack to replace an open one and not a locked one, so that the catalog composes like every kind. | High | Open | [build] | no |
| FR-006 | The recommendation read | As a client, I want a read under an activation that takes a task type, a profile and optionally a mission step and a tier, so that I get model advice for a task. | High | Open | [build] | no |
| FR-007 | Ranking by objective | As a client, I want `quality_first` to rank by fit score with the weighted sum second, and `balanced` and `cost_first` to rank by the weighted sum, so that the answer follows the catalog's stated objective. | High | Open | [build] | no |
| FR-008 | Every tie answered | As a client, I want every model tied at the top rank answered, so that I choose among equals. | High | Open | [build] | no |
| FR-009 | Tiers flag and never hide | As a client, I want the top-ranked models inside every tier in play and, when different, the top-ranked overall, each flagged against the tier constraint and the effective tier, so that no model is hidden and I need no ranking of my own. | High | Open | [build] | no |
| FR-010 | The step's model tier | As a pack author, I want a mission step to offer an optional model tier, and as a client, I want my requested tier to win over it, with both reported, so that a step can advise the tier. | Medium | Open | [build] | no |
| FR-011 | The profile candidate | As a client, I want the effective profile's model and effort as a profile candidate, so that I see the profile's own choice beside the catalog's. | High | Open | [build] | no |
| FR-012 | The refusals | As a client, I want the refusals of ADR-0080 decision 9, in that order, with three new codes, so that a wrong request is never answered as advice. | High | Open | [build] | no |
| FR-013 | Caching | As a client, I want the recommendation to carry the source fingerprint and an `ETag`, and to answer a matching `If-None-Match` with `304`, so that I can cache it as the selection reads. | Medium | Open | [build] | no |
| FR-014 | The switch gate | As core, I want every new operation ready in the switch-gate register, so that the reads can be switched to. | High | Open | [build] | no |
| FR-015 | Records | As a reader, I want the R14 row of the service contract, the divergence register (DIV-701 to DIV-707, open once built) and the contract changelog updated, so that the decision is visible where clients look. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Determinism | The same activation revision, packs and parameters give a byte-identical answer body and `ETag` on 100 repeated calls; no answer depends on the clock or on the order of models in the file. | Reliability | High | Open |
| NFR-002 | Exact arithmetic | Scores are computed in exact decimal; a test with two models whose weighted sums are equal in decimal and differ in binary floating point reports them tied. | Reliability | High | Open |
| NFR-003 | Gates | `./gradlew verifyAll` passes on Java 25, with the whole-pack round trip over the example packs and every new operation `ready` in `switch-gate.yaml` (0 held). | Quality | High | Open |
| NFR-004 | Contract docs | `check_docs.py` (all eight), markdownlint and cspell report no new finding on the changed docs. | Quality | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Code after #355 | No serving or evaluation code before #355 (PR #599) has merged; the code is built on that main. | Technical | High | Open |
| C-002 | Charter's own packs | Tests read only charter's example packs (ADR-0070), in the canonical shape only, but for tests of invalid content. | Technical | High | Open |
| C-003 | No core change | Core is not touched; core aligns later through the generated client. Differences go in the divergence register. | Business | High | Open |
| C-004 | No open-packs change here | The orchestrator files the open-packs follow-up once the kind's schema is on main. It covers moving the catalog into `foundation`, steps adopting `model_tier`, profiles declaring `model`, and refiling #151. Until it lands, a real closure answers `409 routing_catalog_not_in_force`. | Business | High | Open |
| C-005 | Advisory only | No enforcement mode, no tie-break and no tier filter (ADR-0080 decisions 4, 11 and 12). | Business | High | Open |

### Key Entities

- **Routing catalog**: an artifact of kind `routing_catalog`: task types, models with their fit per task type, cost
  and latency, a routing policy (objective, weights, tier constraints) and sources.
- **Model recommendation**: the advice for one task type, profile and optional step under an activation: the catalog
  in force, the objective, the tiers in play and the candidates.
- **Candidate**: a model tagged `catalog` (with score, fit score, rationale, cost tier and tier flags) or `profile`
  (with effort).
- **Mission step's model tier**: an optional cost tier a step offers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every operation the Mission adds is `ready` in the switch-gate register, with every declared status
  asserted by a scenario. — [build] · no-op passable: no
- **SC-002**: Each recommendation rule of ADR-0080 (each of the three objectives, ties, the tier constraint, the step's
  tier, the request's override, the profile candidate, no candidate) has at least one contract scenario that fails
  when the rule is removed. — [build] · no-op passable: no
- **SC-003**: The example packs carry a catalog at two levels, and the whole-pack round trip passes over them with
  no field excluded. — [build] · no-op passable: no
- **SC-004**: #108 is closed by the PR, #94's last child is ticked, and the R14 row of the service contract names the
  operations. — [build] · no-op passable: no

## Assumptions

- The cost tiers (`low` < `medium` < `high` < `premium`) are the vocabulary of a step's `model_tier`.
- The built-in preset that selects the catalog is chosen by open-packs when it moves the catalog; charter's example
  packs select theirs in their own presets.
