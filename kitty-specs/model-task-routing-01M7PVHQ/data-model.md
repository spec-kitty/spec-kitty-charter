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
| `_links` | `self`, `catalog`, `agentProfile`, `step?` | always |

- **Candidate, `source: catalog`**: `modelId`, `score` (weighted sum), `fitScore`, `rationale?`, `costTier`,
  `withinTierConstraint` (true when no constraint), `withinModelTier` (present when there is an effective tier).
- **Candidate, `source: profile`**: `modelId`, `effort?`.

## Rules

1. Score = `w.quality·fit + w.cost·cost(tier) + w.risk·risk(confidence) + w.latency·latency(tier)`; cost `low` 1,
   `medium` 0.66, `high` 0.33, `premium` 0; risk and latency `high`/`low` 1 … 0.33 as ADR-0080 decision 10; absent
   counts 0.5. Exact decimal.
2. Rank key: `quality_first` → (fit, score); `balanced`, `cost_first` → (score).
3. Tiers in play: the task type's `max_tier`, the effective tier (request over step). Inside = cost tier ≤ every tier
   in play.
4. Catalog candidates = top(inside) ∪ top(all), all ties each; order by rank, then model id.
5. Profile candidate = the effective profile's `model` and `effort`; none when `model` is absent.

## Refusal order

`400` → `404 activation_not_found` → `404 artifact_not_found` (profile) → `404 mission_type_not_found` →
`404 action_not_found` → `409 routing_catalog_not_in_force` → `409 routing_catalog_conflict` →
`404 task_type_not_found` → `304` on a matching `If-None-Match` → `200`.
