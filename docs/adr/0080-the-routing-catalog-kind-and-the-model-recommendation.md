---
title: "ADR-0080: The routing catalog is an artifact kind, and the service recommends a model under an activation"
description: "R14 (#108): the routing catalog as an artifact kind in foundation, its reads, and the advisory model recommendation an activation answers for a task."
doc_status: active
updated: 2026-11-18T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-18'
type: ADR
kind: ADR
related:
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0063-pack-mutability-policy-file
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - adr/0077-kind-taxonomy-and-step-authority
  - adr/0078-agent-profile-selection-performer-and-effective-profile
  - api/service-contract
audience:
  - profile: pack-author
    guidance_text: Read this for the routing catalog's file, its fields, how an org pack replaces it, and what a step's model_tier does.
  - profile: contract-consumer
    guidance_text: Read this for the catalog reads, the model recommendation read, its inputs, its answer and its refusals.
  - profile: charter-service-contributor
    guidance_text: Read this before you build or change the routing_catalog kind or the recommendation's rules.
  - profile: core-maintainer
    guidance_text: Read this for the read that replaces core's model_task_routing load and evaluate, and how its answer differs.
---

# ADR-0080: The routing catalog is an artifact kind, and the service recommends a model under an activation

## Status

**Accepted**, 2026-11-18. Decider: Stijn Dejongh (operator). Proposed the same day by Mission
`model-task-routing-01M7PVHQ` (#108) as the build ADR that
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 8 requires before any pack changes.
Decisions 1 and 2 are the operator's rulings recorded on #108. The operator ruled decisions 3 to 14 through the
orchestrator, each a Decision Moment of the Mission:

| Decision Moment | Decision | Ruling |
| --- | --- | --- |
| `DM-01M7PVRWFAXJGTN1NTTNNF3STQ` | 3 | `foundation` holds the catalog. |
| `DM-01M7PVRYYARC81VPNS83EKS3GH` | 4 | Advisory only; `override_policy` is dropped. |
| `DM-01M7PVS1ERRQT4NKZ5PQE9NBVT` | 5 | The kind and its schema, as proposed. |
| `DM-01M7PVS3ZQV0RBMC0NW6WFWE9X` | 6 | The catalog's reads, as proposed. |
| `DM-01M7PVS6FGRFTRJDAP8FGM1AHE` | 7, 8 | The read's path and answer, as proposed; the answer reshaped by decisions 11 and 12. |
| `DM-01M7PVS8Z0R6P76SFZA12BN4PG` | 9 | The refusals, as proposed. |
| `DM-01M7PVSBM3GM1XTGJAC04S6YMB` | 10 | **Changed:** the three objective names stay. Two compute alike today, but they differ in meaning. |
| `DM-01M7PVSEDKGQGVQ9BKCAMJKD2F` | 11 | **Changed:** every tied model is answered. Which one to use is the client's choice, not the service's. |
| `DM-01M7PVSH4K6NBF8WZDVACDAZ2M` | 12 | **Changed:** a tier is a recommendation the client may override, so no model is hidden. A candidate outside a tier stays visible, flagged. |
| `DM-01M7PVSKR35TTKABRF0ZKV9HYW` | 13 | The profile's pick, as proposed. |
| `DM-01M7PVSPB9DYJAFSZTK0SCBWQE` | 14 | #151 is refiled in open-packs. |

The changed rulings replace the proposals that were put: one `weighted` objective, ties broken by cost and then id,
and tiers that filter the catalog's pick. Those proposals are kept under
[Alternatives considered](#alternatives-considered).

## Context

- **R14 (#108).** Core routes a task to a model with a catalog it ships as package data
  (`src/charter/offering/model_task_routing/catalog/model-to-task_type.yaml`), a loader and an evaluator
  (`loader.py`, `evaluator.py`), re-exported by `charter/model_routing.py`. `cli/commands/dispatch.py` and
  `agent/workflow.py` call `load` and `evaluate`. ADR-0074 decision 5 keeps the read in the cutover as a contract
  operation, with the catalog moved into a pack.
- **Today's behavior, as evidence** ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)):
  - The catalog declares `task_types`, `models` (each with `task_fit` scores per task type, a `cost.tier` and a
    `latency_tier`), a `routing_policy` (`objective`, `weights`, `tier_constraints`, `override_policy`,
    `freshness_policy`) and `sources`.
  - `evaluate(catalog, task_type, profile, recommended_model_tier=, model_tier_override=)` drops every model whose cost
    tier exceeds the task type's `tier_constraints` cap, scores the rest, and returns the catalog's pick and the
    profile's `preferred_model` as two candidates tagged `catalog` and `profile`. Under `quality_first` the `task_fit`
    score ranks first and the weighted sum breaks ties; `balanced` and `cost_first` both rank by the weighted sum alone.
  - A remaining tie goes to the model listed first (Python's `max` keeps the first maximum).
  - The step's `recommended_model_tier` and a runtime override resolve with the override winning, and the result is
    only reported: it never touches a candidate.
  - Only `override_policy.mode: advisory` is exercised; `gated` and `required` are read and ignored. A catalog older
    than `freshness_policy.max_catalog_age_hours` is flagged stale; the packaged catalog omits the policy so it never
    expires.
  - An unknown task type, or one no model fits, yields no catalog candidate; a missing catalog yields `None`.
- **What charter has.** An agent profile carries `model` and `effort` (`agent-profile.schema.json`), and the
  effective profile read resolves `enhances` and `specializes_from` (ADR-0078). A mission step carries no model tier.
- **Where the content lives.** In `spec-kitty-open-packs`, `built-in` is a distribution pack that holds presets and no
  artifacts. `foundation` is a built-in-level member that depends on nothing, holds the agent profiles and declares the
  fallback profile (`fallback_profile: generic-agent`); its `policy.yaml` declares no `kind_defaults`, so its artifacts
  default to `open`.

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### The rulings recorded on #108

1. **The routing catalog is an artifact kind**, not a pack-level file like a preset. It gets layering, the mutability
   policy (`open`, `extendable`, `locked`), activation and the effective read under an activation, as the other kinds
   do. An org pack can override it, and a project sees the one catalog in force.
2. **The service serves the catalog and evaluates a task against it.** The evaluation is a read under an activation.
   Its inputs are a task type, an agent profile and, optionally, a mission step; its output is a recommended model. The
   answer is advisory: the catalog's pick and the profile's pick come back as separate candidates, each tagged with its
   source.

### Where the catalog lives, and what it decides

3. **`foundation` holds the catalog**, as `routing_catalogs/default.routing-catalog.yaml`. It is the built-in-level
   pack every closure reaches, it holds the profiles whose `model` the recommendation sets beside the catalog's pick,
   and `built-in` is being emptied of artifacts. Its policy leaves the catalog `open`, so an org pack replaces it by
   the same id. Which preset selects it is open-packs' call, made when it moves the catalog.
4. **The recommendation is advisory only.** There is no enforcement mode: the catalog has no `override_policy`, and the
   answer neither picks one candidate nor filters any out (decisions 11 and 12). A client that wants enforcement
   builds it on the answer. Core's `gated` and `required` modes, never exercised, are not carried over.

### The kind

5. **The kind's token is `routing_catalog`**, plural `routing_catalogs`, with a node kind of the same token. It is
   activatable, not declarable in graph documents and not an edge target: nothing relates to a catalog. The file
   matches `**/*.routing-catalog.yaml` (as `*.glossary.yaml` and `*.skill.yaml` do), and its schema is
   `routing-catalog.schema.json`, the canonical shape (snake case in the pack, camel case on the wire):

   | Field | Shape | Change from core |
   | --- | --- | --- |
   | `id` | kebab-case, one path segment (ADR-0074 decision 13) | new: the artifact's identity |
   | `description` | string, optional | new |
   | `generated_at` | ISO-8601 date-time | kept, as provenance only |
   | `task_types[]` | `id` (kebab-case, unique), `title`, `description?`, `quality_sensitivity?`, `cost_sensitivity?` (`low`, `medium`, `high`) | kept |
   | `models[]` | `id` (unique), `provider`, `family?`, `tools[]`, `strengths[]`, `weaknesses[]`, `task_fit[]` (`task_type`, `score` 0 to 1, `confidence?`, `rationale?`), `cost` (`tier`, `input_per_1m_usd?`, `output_per_1m_usd?`, `currency?`, `pricing_source_url?`), `latency_tier?` | kept; a `task_fit` must name a declared task type, at most once per model |
   | `routing_policy` | `objective` (`quality_first`, `balanced` or `cost_first`), `weights` (`quality`, `cost`, `risk`, `latency`, each 0 to 1), `tier_constraints[]` (`task_type`, `max_tier`) | `override_policy` and `freshness_policy` dropped (decisions 4 and 14); `tier_constraints` advises instead of filtering (decision 12) |
   | `sources[]` | `name`, `url`, `access_method` (`api`, `dataset`, `manual`), `snapshot_at`, `license_notes?` | kept |

   `schema_version` and `source_snapshot` are dropped: the pack schema versions the shape, and a seed mission's id is
   not catalog content. The cost tiers are `low` < `medium` < `high` < `premium`; the latency tiers and confidences are
   `low`, `medium`, `high`. A reference to an undeclared task type, a duplicate id, or a second `tier_constraints` entry
   for one task type is invalid content, refused at registration.

   *Amendment, 2026-11-18 (Mission WP06, accepted by the orchestrator):* not declarable and not an edge target, the
   catalog is still a kind the contract names. An activation holds it (the composition facts count every activatable
   artifact as held, not only the kinds a graph declares), layers it and reports its same-id override, so the contract's
   `NodeKind` lists `routing_catalog`. The kernel's `NodeKind.named()` is the one test of a kind the contract names: a
   graph declares it, an edge names it, or it is activatable. A guard and a lifecycle remain unnamed (#156). This
   replaces the Mission's earlier reading (WP05) that left `routing_catalog` out of the contract's `NodeKind`.
6. **The catalog's reads follow ADR-0074 decisions 9 to 14.** `?kind=routing_catalog` lists it in both trees, and the
   kind gets its own collection, item and twin:
   - `GET /packs/{packId}/artifacts/routing_catalog` (`listPackRoutingCatalogs`): id, description, `objective`, and
     the counts of task types and models;
   - `GET /packs/{packId}/artifacts/routing_catalog/{artifactId}` (`getPackRoutingCatalog`): the whole catalog;
   - `GET /activations/{activationId}/artifacts/routing_catalog` and `…/routing_catalog/{artifactId}`
     (`listEffectiveRoutingCatalogs`, `getEffectiveRoutingCatalog`): the layer-merge winners, with `state`, `reason`,
     `policy` and `required`, as for any activatable kind.

   The templated item reads do not render it, as for `agent_profile`. Registration is that of every activatable kind:
   the whole-pack registration and the per-artifact write, through the mutability policy.

### The recommendation read

7. **One read under an activation:**
   `GET /activations/{activationId}/task-types/{taskType}/model-recommendation` (`getModelRecommendation`), in the
   pattern of `…/roles/{role}/profile-selection`. Query parameters:
   - `agentProfile` (required): the profile whose pick is set beside the catalog's;
   - `missionType` and `step` (optional, both or neither): the step whose `model_tier` is offered (decision 12);
   - `modelTier` (optional): a tier that overrides the step's offer.
8. **The answer, `ModelRecommendation`:**
   - `taskType`; `catalog` (the id, pack and level of the catalog in force); `objective`;
   - `tierConstraint`, the task type's `max_tier` when the catalog declares one;
   - `modelTier` when a tier was offered or asked for: `effective`, `source` (`step` or `request`) and the step's
     `offer`;
   - `candidates`, each with `source` (`catalog` or `profile`) and `modelId`. A catalog candidate adds `score` (the
     weighted sum), `fitScore`, the fit's `rationale`, `costTier`, `withinTierConstraint` and, when there is an
     effective tier, `withinModelTier`. A profile candidate adds `effort`. There are zero or more catalog candidates
     (decisions 11 and 12) and at most one profile candidate. They are listed in rank order, then by model id; the
     order is presentation, not a preference.
   - Links: `self`, `catalog` (the effective catalog read), `agent-profile` (the effective profile read) and, with a
     step, `step`. These are kebab-case link relations, as every relation of the contract is, and the lint's closed
     list of relations gains them; the activation gains `routing-catalogs`.

   It carries the source fingerprint and an `ETag`, and answers `If-None-Match` with `304`, as the selection reads do.
9. **Its refusals, in this order:**
   1. a malformed parameter, or `missionType` without `step` or the reverse: `400`;
   2. an unknown activation: `404 activation_not_found`;
   3. a profile no pack of the closure holds: `404 artifact_not_found`, as the effective profile read judges it;
   4. with a step: `404 mission_type_not_found`, then `404 action_not_found`, as the effective step read judges them;
   5. no catalog in force: `409 routing_catalog_not_in_force` (new), as `mission_type_not_in_force` is `409`;
   6. more than one catalog in force: `409 routing_catalog_conflict` (new), naming the ids in force;
   7. a task type the catalog in force does not declare: `404 task_type_not_found` (new).

   A declared task type that no model fits is not refused: the answer has no catalog candidate. A profile with no
   `model` gives no profile candidate. Both can hold at once, and the answer is `200` with no candidates.

### The rules

10. **The objective is `quality_first`, `balanced` or `cost_first`, three names with three meanings.** Every model with
    a `task_fit` for the task type gets a score, the weighted sum of four parts: quality (the fit's `score`), cost
    (`low` 1, `medium` 0.66, `high` 0.33, `premium` 0), risk (the fit's `confidence`: `high` 1, `medium` 0.66, `low`
    0.33) and latency (`low` 1, `medium` 0.66, `high` 0.33). An absent confidence or latency counts 0.5. Arithmetic is
    exact decimal, so equal inputs give equal scores.
    - Under `quality_first`, the fit's `score` ranks first and the weighted sum breaks ties.
    - Under `balanced` and `cost_first`, the weighted sum ranks alone. The two compute alike today, and the catalog's
      `weights` say how much cost counts. They are kept as distinct names because they state different intents: a
      catalog author says which one they mean, and a later rule may set them apart without renaming any catalog. The
      contract documents that they are provisionally identical in execution.
11. **Ties are not broken: every tied model is answered.** Models whose rank is equal under the objective (both keys
    under `quality_first`, the weighted sum otherwise) are all candidates. Which one to use is the client's choice, not
    the service's. The answer does not depend on the order of `models` in the file.
12. **Tiers advise and never hide a model.** A mission step gains an optional `model_tier`, one of the cost tiers. The
    effective tier is `modelTier` when the request sends it (source `request`), else the step's `model_tier` (source
    `step`), else none. The tiers in play are the effective tier and the task type's `tier_constraints` cap. A model
    whose `cost.tier` is above a tier in play is outside it.
    - **The catalog candidates are two sets, merged:** the top-ranked models among those inside every tier in play
      (with every tie), and, when they are not the same, the top-ranked models overall (with every tie). Each is
      flagged `withinTierConstraint` and, with an effective tier, `withinModelTier`. With no tier in play, both sets
      are the top-ranked models.
    - **So nothing is hidden by a tier.** A model outside a tier that outranks every model inside it is answered,
      flagged. A client that honors the tiers takes the candidates flagged inside them, and needs no ranking of its
      own; a client that overrides them still sees the best model overall.
    - **`tier_constraints` advises too.** Core filters by it. Nothing in charter's model needs a hard filter: the
      catalog is pack content that a client reads as advice (decision 4), and charter's model is canonical
      (ADR-0015), so core's filter is evidence, not a rule to keep. A filter would contradict the ruling that
      selection is the client's. `max_tier` keeps its name and reads as the highest tier the catalog advises for the
      task type.
    - The tiers never touch the profile candidate.
13. **The profile's pick is the effective profile's `model`.** The profile is resolved as the effective profile read
    resolves it (ADR-0078: enhancers merged, the `specializes_from` chain folded), whatever its own state. Its `model`
    and `effort` are the profile candidate, with no score. The service does not check that the model is in the
    catalog.

### Upkeep

14. **#151 edits pack content in open-packs, by human-reviewed pull request.** Once the catalog is in `foundation`,
    the currency pipeline belongs with the content: it runs in `spec-kitty-open-packs`, diffs its sources against
    `packs/foundation/routing_catalogs/default.routing-catalog.yaml`, and opens a pull request there that edits the
    file, bumps `foundation`'s `pack_version`, regenerates its manifest and digest, and passes open-packs' validation
    against the pinned charter release (ADR-0070 decision 3). It never merges itself. Charter holds no pipeline: #151
    is closed here and refiled in open-packs, which answers its open question (a pack-content track, not an ops track).
    Freshness is the pipeline's job, not the read's: the answer never depends on the clock, so the fingerprint and the
    `ETag` stay stable, and `freshness_policy` is dropped.
<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- Core's `load` and `evaluate` become one read of the contract, judged by the activation: an org pack's catalog, a
  locked catalog and a deselected one all behave as for any kind.
- The answer is deterministic and states its inputs (the catalog in force, the tiers in play and their sources), so a
  client can explain a recommendation without re-running it.
- The service never decides for the client: ties, tiers and the profile's pick are all visible, and a client that
  follows the advice still needs no ranking of its own.

### Negative

- A new kind with two collection reads, two item reads and one evaluation read widens the contract, with three new
  refusal codes.
- The mission step schema gains `model_tier`, so open-packs' steps and the example packs change with it.
- Core's callers change what they read: one catalog pick becomes a list, a filtered model becomes a flagged one, and a
  stale flag, a `None` catalog and an unknown task type become refusals or disappear.
- `balanced` and `cost_first` give the same answer for the same catalog until a rule sets them apart.

### Risks

- **Two catalogs in force.** An activation that selects two catalogs gets a `409` until it deselects one. Mitigation:
  the refusal names the ids, and a project normally selects one through the built-in preset.
- **The tier vocabulary.** Reading a step's tier as a cost tier ties two scales together. Mitigation: the tier only
  flags candidates, and a catalog can still advise a cap per task type.
- **A client that takes the first candidate.** It may pick a model outside a tier. Mitigation: the flags are on every
  catalog candidate, and the contract states that order is not a preference.

## Alternatives considered

- **One `weighted` objective in place of `balanced` and `cost_first`.** Proposed; not chosen *(operator)*: the names
  differ in meaning, even where they compute alike.
- **Break ties by the lower cost tier, then the model id.** Proposed; not chosen *(operator)*: the choice is the
  client's.
- **Break ties by the first model listed**, as core does. Not chosen: it gives meaning to list order, which a merge or
  a reformatting tool could change (ADR-0036), and it decides for the client.
- **Let the effective tier and `tier_constraints` filter the catalog's pick.** Proposed for the tier, and core's rule
  for `tier_constraints`; not chosen *(operator)*: a tier is a recommendation the client may override, so a model
  outside it stays visible.
- **Report the step's tier without relating it to any candidate**, as core does. Not chosen: a client would have to
  re-rank the catalog to use it.
- **Answer every model with its score and flags.** Not chosen: the two top-ranked sets already show the best model in
  and out of the tiers, and the rest is in the catalog read.
- **Name the catalog in the request instead of refusing two in force.** Not chosen: it adds a parameter for a case the
  activation should not reach.
- **An unknown task type answers no catalog candidate**, as core does. Not chosen: it hides a misspelled task type
  behind a valid-looking answer.
- **Hold the catalog in `built-in`, or in a pack of its own.** Not chosen: `built-in` is being emptied of artifacts,
  and a separate pack must be added to every closure that has profiles.
- **Keep #151 in charter as an ops track.** Not chosen: the pipeline edits open-packs content, and pack content is
  filed in open-packs (issue-tracker rules).

## Divergences from core

Recorded in the [divergence register](/conformance/divergence-register/) as DIV-701 to DIV-707, `open` since the reads
are built: the catalog is pack content; there is no enforcement mode; no answer is stale; the tiers flag and never
filter, and the step's `recommended_model_tier` is renamed `model_tier` and narrowed to a cost tier; every tied model
is answered, and the scores are exact decimals where core's are binary floats; the step's tier and the request's
override relate to the candidates; and an unknown task type, no catalog and two catalogs are refusals, while a catalog
core tolerates (an undeclared task type reference, a duplicate) is refused at registration.

## References

- Issues [#108](https://github.com/spec-kitty/spec-kitty-charter/issues/108),
  [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94),
  [#151](https://github.com/spec-kitty/spec-kitty-charter/issues/151)
- [ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decisions 5, 8 to 14;
  [ADR-0078](/adr/0078-agent-profile-selection-performer-and-effective-profile/);
  [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1;
  [ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)
- Core at the evidence commit: `src/charter/offering/model_task_routing/` (`models.py`, `loader.py`, `evaluator.py`,
  `catalog/model-to-task_type.yaml`), `src/charter/offering/missions/step_offer_seam.py`,
  `src/charter/model_routing.py`
- [Service contract](/api/service-contract/), "The other reads"
