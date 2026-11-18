---
title: "ADR-0080: The routing catalog is an artifact kind, and the service recommends a model under an activation"
description: "R14 (#108): the routing catalog as an artifact kind in foundation, its reads, and the advisory model recommendation an activation answers for a task."
doc_status: active
updated: 2026-11-18T00:00:00Z
decision_status: Proposed
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

**Proposed**, 2026-11-18, by Mission `model-task-routing-01M7PVHQ` (#108). It is the build ADR that
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 8 requires before any pack changes.

- **Ruled by the operator, 2026-11-18** (recorded on #108): decisions 1 and 2 below.
- **Proposed, for the operator:** decisions 3 to 14. Each states the recommendation; the alternatives are listed
  under [Alternatives considered](#alternatives-considered). The points the brief named are 3 (the pack), 4 (advisory
  only), 14 (#151), 5 and 6 (schema and paths), 7 to 9 (the read and its refusals), and 10 to 13 (the rules).

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
    only reported: it never changes either candidate.
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
### The rulings (operator, 2026-11-18)

1. **The routing catalog is an artifact kind**, not a pack-level file like a preset. It gets layering, the mutability
   policy (`open`, `extendable`, `locked`), activation and the effective read under an activation, as the other kinds
   do. An org pack can override it, and a project sees the one catalog in force.
2. **The service serves the catalog and evaluates a task against it.** The evaluation is a read under an activation.
   Its inputs are a task type, an agent profile and, optionally, a mission step; its output is a recommended model. The
   answer is advisory: the catalog's pick and the profile's pick come back as separate candidates, each tagged with its
   source.

### Where the catalog lives, and what it decides (proposed)

3. **`foundation` holds the catalog**, as `routing_catalogs/default.routing-catalog.yaml`. It is the built-in-level
   pack every closure reaches, it holds the profiles whose `model` the recommendation sets beside the catalog's pick,
   and `built-in` is being emptied of artifacts. Its policy leaves the catalog `open`, so an org pack replaces it by
   the same id. Which preset selects it is open-packs' call, made when it moves the catalog.
4. **The recommendation is advisory only.** There is no enforcement mode: the catalog has no `override_policy`, and the
   answer has no field that tells a client to obey one candidate over the other. A client that wants enforcement
   builds it on the answer. Core's `gated` and `required` modes, never exercised, are not carried over.

### The kind (proposed)

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
   | `routing_policy` | `objective` (`quality_first` or `weighted`), `weights` (`quality`, `cost`, `risk`, `latency`, each 0 to 1), `tier_constraints[]` (`task_type`, `max_tier`) | `objective` narrowed (decision 10); `override_policy` and `freshness_policy` dropped (decisions 4 and 14) |
   | `sources[]` | `name`, `url`, `access_method` (`api`, `dataset`, `manual`), `snapshot_at`, `license_notes?` | kept |

   `schema_version` and `source_snapshot` are dropped: the pack schema versions the shape, and a seed mission's id is
   not catalog content. The cost tiers are `low` < `medium` < `high` < `premium`; the latency tiers and confidences are
   `low`, `medium`, `high`. A reference to an undeclared task type, a duplicate id, or a second `tier_constraints` entry
   for one task type is invalid content, refused at registration.
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

### The recommendation read (proposed)

7. **One read under an activation:**
   `GET /activations/{activationId}/task-types/{taskType}/model-recommendation` (`getModelRecommendation`), in the
   pattern of `…/roles/{role}/profile-selection`. Query parameters:
   - `agentProfile` (required): the profile whose pick is set beside the catalog's;
   - `missionType` and `step` (optional, both or neither): the step whose `model_tier` is offered (decision 12);
   - `modelTier` (optional): a tier that overrides the step's offer.
8. **The answer, `ModelRecommendation`:** `taskType`; `catalog` (the id, pack and level of the catalog in force);
   `objective`; `modelTier` when a tier was offered or asked for (`effective`, `source` `step` or `request`, and the
   step's `offer`); and `candidates`, at most two, each `source` (`catalog` or `profile`) and `modelId`. A catalog
   candidate adds `score` (its weighted sum) and the fit's `rationale`; a profile candidate adds `effort`. Links:
   `self`, `catalog` (the effective catalog read), `agentProfile` (the effective profile read) and, with a step,
   `step`. It carries the source fingerprint, an `ETag` and answers `If-None-Match` with `304`, as the selection reads
   do.
9. **Its refusals, in this order:**
   1. a malformed parameter, or `missionType` without `step` or the reverse: `400`;
   2. an unknown activation: `404 activation_not_found`;
   3. a profile no pack of the closure holds: `404 artifact_not_found`, as the effective profile read judges it;
   4. with a step: `404 mission_type_not_found`, then `404 action_not_found`, as the effective step read judges them;
   5. no catalog in force: `409 routing_catalog_not_in_force` (new), as `mission_type_not_in_force` is `409`;
   6. more than one catalog in force: `409 routing_catalog_conflict` (new), naming the ids in force;
   7. a task type the catalog in force does not declare: `404 task_type_not_found` (new).

   A declared task type that no model fits, after the filters, is not refused: the answer has no catalog candidate. A
   profile with no `model` gives no profile candidate. Both can hold at once, and the answer is `200` with no
   candidates.

### The rules (proposed)

10. **The objective is `quality_first` or `weighted`.** Under `weighted` the rank is the weighted sum of four
    scores: quality (the fit's `score`), cost (`low` 1, `medium` 0.66, `high` 0.33, `premium` 0), risk (the
    fit's `confidence`: `high` 1, `medium` 0.66, `low` 0.33) and latency (`low` 1, `medium` 0.66, `high` 0.33); an
    absent confidence or latency counts 0.5. Under `quality_first` the fit's `score` ranks first and the weighted sum
    breaks ties. Core's `balanced` and `cost_first` behave identically as a weighted sum, so they become `weighted`;
    a cost-first catalog says so in its weights. Arithmetic is exact decimal, so equal inputs give equal scores.
11. **The tier filter runs before scoring, and ties are broken by cost, then id.** A model whose `cost.tier` exceeds the
    task type's `tier_constraints` cap, or the effective model tier (decision 12), is not a candidate, whatever the
    objective. Among models still equal after the objective, the lower cost tier wins, then the lower model id in
    code-point order. The rule does not depend on the order of `models` in the file.
12. **A step offers a model tier, and the request's tier wins.** A mission step gains an optional `model_tier`, one of
    the cost tiers. The effective tier is `modelTier` when the request sends it (source `request`), else the step's
    `model_tier` (source `step`), else none. The effective tier caps the catalog's pick as a `tier_constraints` entry
    does; with a task type cap as well, the lower cap holds. It never changes the profile candidate.
13. **The profile's pick is the effective profile's `model`.** The profile is resolved as the effective profile read
    resolves it (ADR-0078: enhancers merged, the `specializes_from` chain folded), whatever its own state. Its `model`
    and `effort` are the profile candidate, with no score. The service does not check that the model is in the
    catalog.

### Upkeep (proposed)

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
- The answer is deterministic and states its inputs (the catalog in force, the effective tier and its source), so a
  client can explain a recommendation without re-running it.
- A step's tier now changes the advice instead of being reported beside it.

### Negative

- A new kind with two collection reads, two item reads and one evaluation read widens the contract, with three new
  refusal codes.
- The mission step schema gains `model_tier`, so open-packs' steps and the example packs change with it.
- Core's callers change what they read: a stale flag, a `None` catalog and an unknown task type become refusals or
  disappear.

### Risks

- **Two catalogs in force.** An activation that selects two catalogs gets a `409` until it deselects one. Mitigation:
  the refusal names the ids, and a project normally selects one through the built-in preset.
- **The tier vocabulary.** Reading a step's tier as a cost tier ties two scales together. Mitigation: it is advisory,
  and a catalog can still cap a task type on its own.

## Alternatives considered

- **Keep core's tie rule (the first model listed).** Not recommended: it gives meaning to list order, which a merge or
  a reformatting tool could change (ADR-0036), and it hides the rule in the file.
- **Return every tied model.** Not recommended: an advisory answer with one catalog pick is what core's callers use.
- **Keep `balanced` and `cost_first`.** Not recommended: two names for one behavior.
- **Report the step's tier without applying it**, as core does. Not recommended: a client would have to re-implement
  the filter to use it, and the catalog is now pack content like the step, so the "doctrine never routes" reason for
  keeping it apart no longer holds.
- **Name the catalog in the request instead of refusing two in force.** Not recommended: it adds a parameter for a
  case the activation should not reach.
- **An unknown task type answers no catalog candidate**, as core does. Not recommended: it hides a misspelled task type
  behind a valid-looking answer.
- **Hold the catalog in `built-in`, or in a pack of its own.** Not recommended: `built-in` is being emptied of
  artifacts, and a separate pack must be added to every closure that has profiles.
- **Keep #151 in charter as an ops track.** Not recommended: the pipeline edits open-packs content, and pack content is
  filed in open-packs (issue-tracker rules).

## Divergences from core

Recorded in the [divergence register](/conformance/divergence-register/) when the reads are built, each `open` with
this ADR as its ruling: the catalog is pack content, not package data; `override_policy` and `freshness_policy` are
gone, and no answer is stale; `objective` is `quality_first` or `weighted`; ties go to cost, then id; the step's tier
caps the pick; an unknown task type, no catalog and two catalogs are refusals.

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
