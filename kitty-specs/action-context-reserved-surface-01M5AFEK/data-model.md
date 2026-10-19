# Data Model: Action-context read and the reserved 1.0 surface

The contract schemas under `contracts/charter/schemas/` are authoritative. This page summarizes
what changes and the invariants the service keeps.

## ActionContext (changed)

| Field | Type | Rule |
|---|---|---|
| `action` | Urn | `action:<missionType>/<action>` |
| `missionType` | Urn | `mission_type:<missionType>` |
| `depth` | int ≥ 0 (no maximum in the response) | the `suggests` hops the walk followed: the request's `depth` (0 to 2), or 2 |
| `governanceFingerprint` | Sha256Digest | unchanged |
| `guidance` | GuidanceEntry[] | the walk's result, one entry per URN, sorted by full URN string |
| `diagnostics` | Diagnostic[] | the offering's load findings, as `PackSummary` carries them per pack |

## GuidanceEntry (changed: `hops` added)

| Field | Type | Rule |
|---|---|---|
| `urn`, `kind`, `label` | as today | `label` is null when the graph does not declare the node |
| `relation` | Relation | `scope`, `requires`, `suggests` or `vocabulary` in this version |
| `via` | Urn | the parent in the step that listed the entry: the action node for a scope entry, otherwise another entry of the answer |
| `hops` | int ≥ 1 | 1 for scope; `hops(via) + 1` otherwise |
| `impacts` | number or null | null for every relation this read walks |

**Invariants**

- Each URN appears once, listed by the first step that reaches it. Within a step, the parent
  is chosen by the minimum step distance (breadth-first), then the fewest `hops`, then the
  smallest URN string.
- Scope is walked from the action node only; the mission-type node's scope edges are not walked.
- The walk runs five steps:
  1. scope from the action;
  2. `requires` transitively from the scoped artifacts;
  3. `suggests` up to `depth` steps from the scoped artifacts;
  4. `requires` transitively from the suggested artifacts;
  5. `vocabulary` one hop from everything listed.
- Each step is breadth-first and cycle-safe. It never lists a node an earlier step listed, but
  it still seeds later steps from it.
- `via` is the action node or another entry, and `hops = hops(via) + 1`. Following `via`
  reaches the action in exactly `hops` edges.
- The action and mission-type nodes are never entries.

## ServiceInfo (changed: `serviceVersion` added)

| Field | Type | Rule |
|---|---|---|
| `contractVersion` | string | `info.version` of the contract |
| `serviceVersion` | string, semantic version | the service build's version |
| `worktree` | string | opaque, never a host path |
| `sourceFingerprint` | Sha256Digest | covers every pack file loaded, and `config.yaml` and `charter.yaml` (presence and bytes) under the configured project configuration directory |

## Requests and answers (new)

- **`POST /packs/refresh`:** no body. Re-reads the packs and the project configuration and discards any built read model. Answers 200 `PackListing` with one verified `Charter-Source-Fingerprint`. Idempotent.
- **401:** `Problem` without `code`, for a missing, malformed or wrong bearer token, with
  `WWW-Authenticate: Bearer`. Carries `Charter-Contract-Version` and `Charter-Worktree`, never
  `Charter-Source-Fingerprint`.
- **403:** `Problem` without `code`, for a request with an `Origin`, `Sec-Fetch-Site` or
  `Sec-Fetch-Mode` header, or a `Host` whose name is not a loopback name (port not checked). It carries the same headers as
  a 401.
- **`Charter-Source-Fingerprint`:** only on a 200, set once, the digest that read verified.

## Refusal codes (values unchanged)

| Code | Status | Retry | Raised in this version |
|---|---|---|---|
| `stale_input` | 500 | may be retried | yes: by any operation whose inputs change while it is answered, including a load that fails while they change |
| `governance_policy_violation` | 500 | no | no (reserved; #48) |
| `pack_unreadable` | 500 | no | yes: an unreadable pack file |

## Configuration (service)

- **`charter.project-config-dir`** (optional): the worktree's `.kittify` directory. Its
  `config.yaml` and `charter.yaml` join the fingerprint. A configured directory that does not
  exist fails startup.
- **`charter.worktree-token`** (required): the per-worktree token, at least 32 characters. Every
  request must carry it as `Authorization: Bearer <token>`. It is read from the environment
  (`CHARTER_WORKTREE_TOKEN`); a token from the command line or a system property fails startup.
- **`charter.insecure-no-token`** (development only): lets the service start without a token,
  and logs a warning at every start.
