# Data model: Pack composition

**Date**: 2026-11-02 | **Mission**: pack-composition-depends-on-and-the-dependency-resolver-01M6F541

## Authored (in `pack.yaml`)

### PackDependency

One declared edge, authored on the depending pack.

| Field | Type | Required | Rule |
|---|---|---|---|
| `pack_id` | string | yes | ULID, 26 characters, the same shape `pack_id` and `parent_pack` already use |
| `range` | string | yes | a PEP 440 specifier set (`>=1.2,<2`), the grammar `requires_core` already uses; an unparsable value is a schema error |

The item is closed: a key the schema does not declare makes `pack.yaml` unreadable (ADR-0036 decision 3). A descriptor
may declare no dependencies.

### PackDescriptor (extended)

`depends_on: [PackDependency]`, defaulting to empty, and `overrides: [PackOverride]`, defaulting to empty. When `depends_on` is present and non-empty, `pack_level` becomes
required, because a pack that composes must say which level it composes at; a descriptor that declares no dependencies
keeps today's optional `pack_level` and its diagnostic.

### PackOverride

The distribution pack's own declaration that one member's version of an artifact wins over a sibling's, so a sibling
refinement is settled rather than refused (ADR-0048 decision 9; its open question 5 as ruled by ADR-0053 decision 5).
Authored in the distribution pack, not derived.

| Field | Type | Required | Rule |
|---|---|---|---|
| `artifact` | string | yes | the contended artifact's URN, which must resolve inside the compose |
| `member` | string | yes | the `pack_id` (ULID) of the pack whose version wins; it must be in the compose |

The item is closed. An `overrides` edge names exactly one winner. A second `overrides` edge on the same artifact inside
one descriptor is a schema error: that `pack.yaml` is refused as `pack_unreadable`. Two distribution packs in one
compose settling the same artifact is not expressible in a single descriptor schema; the compose refuses it once the
distribution-pack flow exists, and assigning that refusal a code is a follow-up of this mission, not a rule it
implements.

## Derived (never authored)

### ResolvedCompose

The result of resolving one configured level's closure.

| Field | Type | Meaning |
|---|---|---|
| `level` | PackLevel | the level the compose is resolved at; one compose is one closure at one level |
| `entries` | ordered list of `ResolvedPack` | dependencies before dependents; a total order, stable across runs |
| `edges` | list of `ResolvedEdge` | the dependency edges that were satisfied, each with the range and the version that satisfied it |

### ResolvedPack

| Field | Type | Meaning |
|---|---|---|
| `id` | PackIdentifier | the configured pack id |
| `name` | string | the descriptor's `name`, the qualifier a cross-pack reference uses |
| `version` | string | the descriptor's `pack_version` |
| `level` | PackLevel | the level storage serves the pack at |
| `dependencies` | list of `ResolvedEdge` | the edges this pack declares |

### ResolvedEdge

`from` pack id, `to` pack id, the declared `range`, and the satisfying `version`. The pair `(from, to)` appears once in
a resolved compose.

## Invariants

1. **One closure, rooted at one level.** A compose is rooted at one configured level; a dependency may reach a pack at a
   lower level (project, then org, then built-in) but never a higher one. The layer merge across levels stays a separate
   idea
   (ADR-0048 decision 6).
2. **Unique identity inside a compose.** A `pack_id` appears once, and a `name` appears once, so a qualified reference
   is never ambiguous.
3. **Dependency direction.** A pack depends only on its own level or a lower one (project, then org, then built-in).
4. **Total order.** Dependencies precede dependents; a shared dependency precedes every dependent; the order is
   independent of directory listing order.
5. **Ratified ranges.** Every edge's declared range admits the version that satisfied it, and two ranges on one pack
   overlap.
6. **Parent lineage.** A `parent_pack` resolves to a configured pack, or the compose is refused; it is never degraded
   to a root.
7. **Rooting.** A compose is rooted at one configured level; no pack may depend upward. The resolver
   enforces the direction of every edge; it does not yet compute or report a compose's rooting level, which is a
   follow-up rather than a rule this mission implements.

## Refusal reasons (new contract codes)

| Situation | Code | Status |
|---|---|---|
| A dependency edge closes a cycle | `pack_dependency_cycle` | 422 |
| A dependency names an id no configured pack declares | `pack_dependency_unknown` | 422 |
| The held version fits no declared range for that pack | `pack_dependency_out_of_range` | 422 |
| A `parent_pack` does not resolve | `pack_parent_unresolved` | 422 |
| A pack depends across a level boundary it may not cross | `pack_level_violation` | 422 |
| Two packs in a compose declare the same name | `pack_name_conflict` | 422 |
| Two configured packs declare the same `pack_id` | `pack_duplicate_id` | 422 |
| Two sibling packs refine one artifact with no `overrides` | `pack_sibling_refinement_conflict` | 409 |
| A bare reference resolves only in another pack | `pack_reference_unqualified` | 422 |
| A qualified reference names a pack outside the closure | `pack_reference_pack_missing` | 422 |

Codes are snake_case on the wire and part of the contract, so consumers branch on them, never on the message.

## Our own terms

- **Compose**: one `depends_on` closure at one level.
- **Closure**: the set reachable from the configured packs through `depends_on`, each id once.
- **Qualified reference**: `<pack-name>::<urn>`; the URN part stays opaque to this mission.
- **Level**: the pack tier a pack sits at, which bounds the direction of a dependency.
