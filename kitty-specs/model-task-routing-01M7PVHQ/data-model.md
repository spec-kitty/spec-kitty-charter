# Data model: Model-task routing

## RoutingCatalog (artifact, kind `routing_catalog`)

| Field | Type | Rule |
| --- | --- | --- |
| `id` | kebab-case token, one path segment | identity; layer override by the same id |
| `description` | text, optional | |
| `generated_at` | ISO-8601 date-time | provenance only; never read by a rule |
| `task_types` | list of TaskType, at least one | ids unique |
| `models` | list of Model, at least one | ids unique |
| `routing_policy` | RoutingPolicy | |
| `sources` | list of Source, at least one | |

- **TaskType**: `id`, `title`, `description?`, `quality_sensitivity?`, `cost_sensitivity?` (`low`, `medium`, `high`).
- **Model**: `id`, `provider`, `family?`, `tools`, `strengths`, `weaknesses`, `task_fit` (at least one), `cost`,
  `latency_tier?` (`low`, `medium`, `high`).
- **TaskFit**: `task_type` (declared), `score` (decimal, 0 to 1), `confidence?` (`low`, `medium`, `high`), `rationale?`.
  At most one fit per task type per model.
- **Cost**: `tier` (`low` < `medium` < `high` < `premium`), `input_per_1m_usd?`, `output_per_1m_usd?`, `currency?`,
  `pricing_source_url?`.
- **RoutingPolicy**: `objective` (`quality_first`, `balanced`, `cost_first`), `weights` (`quality`, `cost`, `risk`,
  `latency`, each 0 to 1), `tier_constraints` (`task_type` declared, `max_tier`; at most one per task type).
- **Source**: `name`, `url`, `access_method` (`api`, `dataset`, `manual`), `snapshot_at`, `license_notes?`.

Registration refuses, as invalid content: a fit or constraint naming an undeclared task type, a duplicate task type or
model id, two fits for one task type in a model, two constraints for one task type.

## MissionStep (changed)

- `model_tier?`: one of the cost tiers. Wire: `modelTier`, absent when not declared.

## ModelRecommendation (answer of `getModelRecommendation`)

| Field | Type | Present |
| --- | --- | --- |
| `taskType` | token | always |
| `catalog` | `{id, packId, level}` of the catalog in force | always |
| `objective` | `quality_first` / `balanced` / `cost_first` | always |
| `tierConstraint` | cost tier | when the catalog caps the task type |
| `modelTier` | `{effective, source: step/request, offer?}` | when a step offers a tier or the request asks for one |
| `candidates` | list of Candidate | always (may be empty) |
| `_links` | `self`, `catalog`, `agent-profile`, `step?` (kebab-case relations, added to the lint's `link-relations-are-known` enum and the hypermedia table) | always |

- **Candidate, `source: catalog`**: `modelId`, `score` (weighted sum), `fitScore`, `rationale?`, `costTier`,
  `withinTierConstraint` (true when no constraint), `withinModelTier` (present when there is an effective tier).
- **Candidate, `source: profile`**: `modelId`, `effort?`.

## Rules

1. **Score.** `score = w.quality·fit + w.cost·cost(tier) + w.risk·risk(confidence) + w.latency·latency(tier)`, in exact
   decimal:

   | Part | `low` | `medium` | `high` | `premium` | absent |
   | --- | --- | --- | --- | --- | --- |
   | cost (`cost.tier`) | 1 | 0.66 | 0.33 | 0 | — |
   | risk (`task_fit.confidence`) | 0.33 | 0.66 | 1 | — | 0.5 |
   | latency (`latency_tier`) | 1 | 0.66 | 0.33 | — | 0.5 |

   Risk rewards high confidence; latency rewards low latency. Each weight is 0 to 1, and the weights need not sum to 1,
   so `score` runs from 0 to the sum of the weights. With all four weights 0, every fitting model scores 0 and,
   under `balanced` or `cost_first`, all of them tie and are answered (decision 11).
2. **Rank key.** `quality_first` ranks by (`fit`, `score`); `balanced` and `cost_first` rank by `score`. A rank is
   compared with `compareTo`.
3. **Tiers in play.** These are the task type's `max_tier` and the effective tier: the request's `modelTier`, else the
   step's `model_tier`. A model is *inside* when its cost tier is at most every tier in play, so the two tiers always
   combine and neither replaces the other. With no tier in play, every model is inside.
4. **Catalog candidates.** `top(inside) ∪ top(all)`, each `top` with all its ties, with no model listed twice.
5. **Profile candidate.** The effective profile's `model` and `effort`, and none when `model` is absent. It is listed
   even when a catalog candidate names the same model.
6. **Order.** Catalog candidates by rank descending, then by model id in code-point order; the profile candidate last.
   The order is presentation, not preference, and never depends on the order of `models` in the file.
7. **Wire form of a score.** A JSON number in plain notation, unrounded, with trailing zeros stripped: `0.3`, `0.732`,
   `1`. YAML numbers are read as decimals, never through `double`.

## Refusal order

1. `400`, a Problem with no code: a malformed `taskType` (not kebab-case), `agentProfile`, `missionType` or `step`; a
   `modelTier` that is not a cost tier; `missionType` without `step`, or the reverse.
2. `404 activation_not_found`.
3. `404 artifact_not_found`: no pack of the closure holds the profile.
4. `404 mission_type_not_found`, then `404 action_not_found`, judged as `selectAgentProfileForStep` judges them. A
   held step is offered whether or not its mission type is in force, as that read does, so
   `mission_type_not_in_force` is not raised.
5. `500`: the closure cannot compose or merge, or a pack is unreadable (`pack_unreadable`, `stale_input`,
   `internal_error`). This comes before any judgement that needs the merged closure.
6. `409 routing_catalog_not_in_force`.
7. `409 routing_catalog_conflict`, naming the ids in force.
8. `404 task_type_not_found`. The 404 response description states that it follows the two 409s.
9. `304` on a matching `If-None-Match`.
10. `200`.
