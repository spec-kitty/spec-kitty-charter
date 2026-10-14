---
title: Python package API
description: The designed in-process public surface of the extracted charter package, derived from core's charter.offering.api — entry points, types, operations and errors.
doc_status: draft
updated: 2026-10-14
type: API
kind: Explanation
related:
  - api/compatibility
  - architecture/logical-model
---

# Python package API

> **Designed API — not implemented here.** This page describes the surface the extracted
> charter package is designed to export. **The code is still to be added to this
> repository.** It is derived from core's `charter.offering.api`
> (`src/charter/offering/api.py` in `spec-kitty/spec-kitty`), where these symbols exist
> today, and from core ADR `2026-10-12-2` D-1.

## Entry point

```python
from charter.offering.api import (
    ArtifactKind, slug_for,
    AssetRepository, AssetManifest,
    AssetResolutionError, AssetNotFoundError, AssetPathEscapeError,
    load, CatalogLoadResult,
    evaluate, RoutingRecommendation,
)
```

`charter.offering.api` is the single, enumerable manifest of the public surface: its
`__all__` lists every public symbol, sorted. Anything not in `__all__` is internal, even when
importable. In core today this module serves the charter facades; core's runtime
(`specify_cli`) may not import it directly yet. Core ADR `2026-10-12-2` makes it the only
entry point for every caller once the import move (epic spec-kitty#6099) is done.

## Artifact kinds

| Symbol | Kind | Purpose |
|---|---|---|
| `ArtifactKind` | `StrEnum` | The artifact-kind taxonomy and its per-kind facts. See [Logical model](/architecture/logical-model/#artifact-kinds). |
| `slug_for(kind: str, identifier: str) -> str` | function | The canonical, filesystem-safe slug for an artifact identity. Pure. Directive ids are lower-cased with `_` turned into `-`; other ids are kept; the result is percent-quoted. |

`ArtifactKind` members: `directive`, `tactic`, `styleguide`, `toolguide`, `paradigm`,
`procedure`, `agent_profile`, `mission_step_contract`, `template`, `asset`, `glossary_pack`,
`anti_pattern`, `skill`. Properties: `plural`, `glob_pattern`, `has_built_in_content_dir`,
`activatable`, `org_requirable`, `selection_overlayable`, `has_layered_repository`,
`effective_when_absent` (`"all"` or `"required"`) and `operator_token`. Class methods:
`from_plural(plural)` and `from_operator_token(token)`; the latter raises
`MissionTypeNotAnArtifactKind` for `mission-type`.

## Assets

| Symbol | Kind | Purpose |
|---|---|---|
| `AssetManifest` | frozen Pydantic model, extra keys forbidden | The `*.asset.yaml` sidecar: `id`, `mime`, `path`, optional `title`. |
| `AssetRepository(built_in_dir=None, *, org_dirs=None, project_dir=None)` | class | Resolves asset ids across the built-in, org and project layers; later layers win. |

Operations on `AssetRepository`:

- `get(asset_id) -> AssetManifest | None`, `list_all()`, `get_provenance(asset_id)` — the
  layered-repository reads.
- `source_path(asset_id) -> Path` — the manifest file that declared the id.
- `resolve_path(asset_id) -> Path` — the blob path, anchored at the winning layer's root and
  contained.

Errors (all fail closed, catchable by type):

| Error | Raised when | Attributes |
|---|---|---|
| `AssetResolutionError` | base class of asset failures | — |
| `AssetNotFoundError` | no manifest resolves the id | `asset_id` |
| `AssetPathEscapeError` | the blob path escapes its root by `..` or a symlink | `asset_id`, `blob_path`, `root` |

## Model and task-type routing

| Symbol | Kind | Purpose |
|---|---|---|
| `load(catalog_path: Path \| None = None) -> CatalogLoadResult \| None` | function | Loads and validates the model-to-task-type catalog. Returns `None` when the catalog is missing or invalid as a whole; raises `pydantic.ValidationError` for a malformed entry. |
| `CatalogLoadResult` | frozen dataclass | `catalog` (the validated catalog) and `is_stale`. |
| `evaluate(catalog, task_type, profile, *, recommended_model_tier=None, model_tier_override=None) -> RoutingRecommendation` | function | Pure and deterministic. Never raises on "no match"; returns fewer candidates instead. |
| `RoutingRecommendation` | frozen dataclass | `task_type`, `objective`, `override_mode`, `candidates`, `model_tier`; properties `catalog_candidate` and `profile_candidate`. |

Under the advisory override mode, both the catalog pick and the profile's declared model are
returned with provenance; neither is enforced over the other.

## Gaps in today's surface

The designed package exposes the [logical model](/architecture/logical-model/), but core's
`charter.offering.api` covers only kinds, assets and model routing. These reads exist in core
behind other modules and have no public entry point yet:

| Read | Where it lives in core today |
|---|---|
| Action-scoped context | `charter.activation` (`spec-kitty charter context --action <name>`) |
| Profile resolution | `charter.activation.profile_resolution`, `AgentProfileRepository` |
| Activation plan and commit | `charter.activation.activation_engine` (`plan_activation`, `commit_plan`) |
| Graph queries | `charter.offering.drg` (`DRGGraph`, `NodeKind`, `Relation`) |
| Pack diagnostics | `spec-kitty doctor charter-packs --json` |

Adding them to the public surface is part of the import-move precondition (core ADR
`2026-10-12-2` D-2, epic spec-kitty#6099). Each addition is a minor version; see
[Versioning and compatibility](/api/compatibility/). The same reads are the operations of the
[service contract](/api/service-contract/).
