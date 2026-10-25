# Quickstart: the directive reference slice

This walkthrough is the skeleton the rest of the mission copies. It proves the graph core and the
registration pattern end-to-end on **directive** (a top-level DRG entry point), before styleguide,
paradigm and toolguide replicate it.

## Prerequisites
- The kernel graph core exists (IC-01): `Relationship`, `RelationType`, `Directionality`, `GraphWalker`, `Descent`.
- `DrgEdge` is a storage DSO with a `Relationship`↔`DrgEdge` mapper over the pack DRG shard (IC-02).
- `CharterArtifact.relationships()`, `ComponentResolver` and the `GraphWalk` driver exist (IC-03).

## 1. Register a directive with a relationship (write)
```
PUT /packs/{packId}/directives/{directiveId}
{ "...directive fields...", "relationships": [
    { "from": "directive:{directiveId}", "to": "procedure:some-procedure", "type": "requires", "rationale": "..." }
] }
```
Expect `201`/`200` + `ETag`. The service loads the Pack, assembles the directive, **revalidates the
whole aggregate** (belongs-to-pack, no-duplicate-id), writes via stage + atomic swap, and persists the
relationship as a `DrgEdge` in the pack DRG shard.

## 2. Read it back (read)
```
GET /packs/{packId}/directives/{directiveId}
```
Expect the directive **value-equal** to what was submitted, relationships included (round-trip, NFR-003).

## 3. Walk its relationships (lazy traversal)
From the directive, `GraphWalk` follows `relationships()` and resolves each neighbour on demand via
`ComponentResolver`. A target whose kind is out of scope/unwired (e.g. `procedure:`) resolves to empty
and the walk continues (D6); the walk never loads the whole DRG (NFR-001).

## 4. Refusals (same-fixture positive controls)
- A duplicate id → `409`, pack byte-for-byte unchanged; a distinct id on the same pack → `201`.
- A malformed body / unknown relation endpoint kind → `422`, pack unchanged; a well-formed request → accepted.
- A write beyond the pack's mutability level → `409` (`governance_policy_violation`); a within-level write → accepted.
- A stale `If-Match` → `412`; a fresh one → accepted. A sub-resource write with no precondition → `428`.

## Acceptance anchors
Maps to spec FR-003/004/005/006/007/008/009/010/012 and SC-001/002/003. BDD scenarios for each land in
`contracts/charter/scenarios/` **before** the slice is implemented (specification-by-example).
