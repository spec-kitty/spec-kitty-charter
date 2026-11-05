# Data Model: Activation register and activated-pack closure

## Activation slice (owned here)

### ActivationId (value object)

- An opaque id that is either minted by the service or chosen by the client (ADR-0020 D3).
- Grammar: the contract's `activationId` pattern.

### ActivationDocument (aggregate root; the `ActivationConfig` shape)

| Field | Type | Rules |
|---|---|---|
| schemaVersion | int | must be the supported version |
| packs | absent \| non-empty list of PackRef | absent means every configured pack; each `packId` once; order kept as written (merge order) |
| activatedKinds | absent \| set of kind | absent and `[]` both mean none (ADR-0053 d10); distinct on the wire |
| missionTypes | absent \| set of id | as above |
| languages | absent \| set of token | absent means no language filter |
| selected | map kind → absent \| set of id | as above, per kind |
| deactivated | map kind → set of id | an empty list is omitted |

**Invariants**

- **Canonical form.**
  - Sets are sorted and deduplicated, and ids are stored as declared ids.
  - An empty deny list is dropped. `[]` is kept on every allow list.
- **Validation is closure-scoped.** Every id names an artifact held in the document's closure.
- **The floor is never denied.** No deny entry is written for a required or locked artifact. A stored one is tolerated
  but not applied.
- **No `null` anywhere.** The document is at most 1 MiB.

**Revision:** `sha256:` + hex(sha256(JCS(canonical document))).

### PackRef (value object; contract `PackPin`)

- `packId`, plus an optional `version`, which must equal the held version.

### Activation (stored record)

- `id`, `document`, `revision`. The answer envelope adds `_links` and nothing that depends on the offering
  (ADR-0059 D5).

### ActivationChange (result of a per-artifact or preset write)

- `revision`: the new revision.
- `applied`: entries of `{kind, id, via}`, where `via` is one of `selected`, `requires`, `suggests`, `refines`,
  `scope`, `instantiates`, `enhances` or `preset`.
- `cleared`: the deny entries removed.
- `skipped`: the skipped report.

### SkippedReport

- `skippedByScope`.
- `skippedDeactivated`, each entry with `viaRequires` and `requiredBy`, which is empty for `enhances`.
- `unreachedBehindDeactivated`.
- `skippedShared`, each entry with the active artifact that still reaches it.

### State transitions (document level)

```mermaid
stateDiagram-v2
  [*] --> Stored: create (201)
  Stored --> Stored: replace / select / unselect / deactivate / reactivate / preset (If-Match ok)
  Stored --> Stored: stale If-Match → 412 (unchanged)
  Stored --> [*]: delete (204)
```

Each transition is a compare-and-set on the revision, under the per-id lock.

## Packs slice (extended)

| Element | Change |
|---|---|
| `PackCompositionResolver` | `resolveRooted(available, roots)`; public `closureOf` (depends_on and parent_pack) |
| `PackCompositionFacts` | gains `edges: List<(source urn, relation, target ref)>` as written in graph documents |
| `ClosureFacts` (new answer of the rooted compose) | per pack: id, name, version, level, `dependsOn: [{packId, range}]`, in dependency order |
| `PolicyFile` (new) | `kind_defaults: Map<NodeKind, MutabilityLevel>`, `artifacts: Map<Urn, MutabilityLevel>`; becomes a `MutabilityPolicySource` |
| `PackDescriptor` | gains optional `mutabilityPolicy` (pack-relative path) |
| Queries (application) | `lockOf(closure, urn) -> (level, pack)` (the maximum level along the closure, owner first); `requiredIn(closure) -> Map<urn, pack>`; `presetIn(closure, presetId) -> (Preset, pack)`; `holds(closure, urn)`; edge helpers (forward filtered, reverse enhances, ancestors) |

## Kernel

- `ArtifactKind.InForceWhenAbsent` collapses to "none" (ADR-0053 d10).
