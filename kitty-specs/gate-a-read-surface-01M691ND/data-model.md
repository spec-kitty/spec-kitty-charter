# Data model: Gate-A read surface

These are the wire resources this mission adds or changes. The domain model is unchanged except for
one port. The exact OpenAPI shapes are sketched in `contracts/gate-a-surface.sketch.yaml`.

## Hypermedia

| Schema | Fields | Rules |
|---|---|---|
| `Link` (closed) | `href` (required), `templated`, `method` (a string matching `^(GET|POST|PUT)$`; no enum, R-15), `title` | `href` starts with `/api/v1`. It is either an absolute path or an RFC 6570 template, with `templated: true` when it is a template. `method` defaults to `GET`. |
| `Links` (open map) | relation key → `Link` | It is the one map the closure checker accepts. Every key is in the relation table of `docs/development/hypermedia-hateoas.md`. |

**Invariants**:

- Every JSON 2xx answer has `_links.self`.
- No link targets an operation the contract reserves or does not hold.

## Resources

| Schema | Fields | Links |
|---|---|---|
| `ApiRoot` | `_links` | `self` `/api/v1/`, `info`, `offering`, `artifacts` (templated `/api/v1/packs/{packId}/artifacts{?kind}`), `service-desc` |
| `ServiceInfo` (changed) | the existing four fields and `_links` | `self` `/api/v1/info`, `service-desc` |
| `Offering` | `packs` (an array of `OfferingPack`, in merge order), `_links` | `self` `/api/v1/offering` |
| `OfferingPack` | `packId`, `level` (a `$ref` to `PackLevel`; the configured level), optional `name` and `version` (from `pack.yaml`; absent without one), `_links` | `artifacts` `/api/v1/packs/{packId}/artifacts` (no `self`: `GET /packs/{packId}` is deferred) |
| `ArtifactPage` | `items` (an array of `ArtifactSummary`), `page` (`PageInfo`), `_links` | `self` (the request, with its query), `first`, and `next` only when `page.hasNextPage` is true |
| `ArtifactSummary` | `kind` (`ArtifactKindToken`), `id`, `title`, `_links` | `self` `/api/v1/packs/{packId}/artifacts/{kind}/{id}` |
| `Artifact` | `kind`, `id`, `packId`, `_links`, and exactly one of `tactic`, `directive`, `styleguide`, `paradigm`, `toolguide` | `self` and `collection` (`…/artifacts?kind=<kind>`). No `edit` link in this minor (R-24). |
| `RelationshipRegistered` (new, response only) | `relationship` (the unchanged `RelationshipRegistration`), `_links` | `artifacts` only. There is no `self`, because the URI has no GET (R-13). |

- `ArtifactKindToken` is an enum: `tactic`, `directive`, `styleguide`, `paradigm`, `toolguide`. These
  are the served kinds, matching the kernel `ArtifactKind.token()` values.
- In `Artifact`, the kind-named member that is filled equals `kind`, and the other four are absent.
  The schema documents this with a `oneOf` of `required` branches; the service guarantees it.

## Orders and paging

- **Offering order**: by the configured `PackLevel` ordinal (built-in < org < project), then by
  `packId` in code-point order (R-18).
- **Artifact order**: by served-kind order (as listed above), then by `id` in code-point order.
- **Cursor**: opaque base64url of `<kind>/<id>`. The next page is the items strictly after that
  position, under the same `kind` filter.
- **`pageSize`**: 1-200, default 50.

## Refusals

| Case | Answer |
|---|---|
| Unknown pack | 404 `pack_not_found` |
| A path kind that is not served | 404 `kind_not_found` (new, `raised: true`) |
| Unknown id of a served kind | 404 with that kind's code (`tactic_not_found`, `directive_not_found`, `styleguide_not_found`, `paradigm_not_found`, `toolguide_not_found`) |
| Unknown `?kind=`, a malformed cursor or a cursor for another kind, `pageSize` out of range | 400 Problem without a code |
| A removed per-kind path (any method) | 404 Problem without a code |
| GET on a literal write path, or PUT on `/artifacts/tactic/{id}` | 405 Problem without a code |
| Unreadable inputs | 500 `pack_unreadable` |
| Inputs that change during the read | 500 `stale_input` |

## Headers

| Header | Declared on |
|---|---|
| `Charter-Contract-Version`, `Charter-Worktree` | every response (unchanged) |
| `Charter-Source-Fingerprint` | **required** on the 200 of `getServiceInfo`, `getOffering`, `listPackArtifacts` and `getPackArtifact`; declared nowhere else |
| `ETag` | `getPackArtifact` 200 for the writable kinds; the write answers (unchanged) |
| `Link` (`rel="service-desc"`) | `getServiceInfo` 200 only |

## Domain port (new)

`PackCatalogRepository` lives in `packs.domain`. Its method `List<ConfiguredPack> configured()`
returns each configured pack's identifier and configured level, and `DomainRepositoryService`
implements it over the storage locations. The `*Repository` suffix keeps it under the ArchUnit port
rules, and its signatures name only domain types.
