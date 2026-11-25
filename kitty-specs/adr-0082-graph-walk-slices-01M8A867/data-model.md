# Data model: the graph, the scope, the walker and the rules

All types are in `ai.speckitty.charter.packs.domain` unless named otherwise. Nothing is persisted.

| Type | Kind | Holds | Invariants |
|---|---|---|---|
| `GraphWalker<N, E>` (kernel) | interface | `onNode(N)`, `onRelationship(E)` → `Descent` | the kernel keeps only the contract |
| `PackGraphNode` | sealed interface | `pack()`, `origin()` | permits `PackNode`, `ArtifactNode`, `EdgeNode` only |
| `PackNode` | record | configured pack, descriptor | one per configured pack, in merge order |
| `ArtifactNode` | record | holding pack, URN, held | from the pack's composition facts |
| `EdgeNode` | record | `EdgeKind`, writing pack, the edge as declared | never resolved to a holder |
| `EdgeKind` | enum | `DEPENDS_ON`, `PARENT_PACK`, `OVERRIDES`, `ENHANCES`, `SPECIALIZES`, `REFERENCES`, `STEP_LINK`, `DELEGATES` | `SPECIALIZES_FROM` maps to `SPECIALIZES`; a mission type's `requires` to its own step is `STEP_LINK` |
| `Scope` | record | roots, selection predicate | selects only; never names edges; the predicate is pure |
| `PackGraph` | final class | packs by merge order and by `pack_id`, lazy facts | walks iteratively; a cycle ends; `SKIP` and `STOP` honoured |
| `ValidationWalker<N>` | final class, `GraphWalker<N, N>` | phases of checks | one traversal per phase; one bucket per check, joined in check order; a phase that records a `VIOLATION` stops the later ones |
| `Check` (`OnNode`, `OnClose`) | sealed | name, node kind, predicate, rule | an `OnNode` sees only its kind; an `OnClose` sees the selected nodes in visit order |
| `GraphCheck` | record | walk mode, phases | what the factory hands out; the caller passes a graph and a scope |
| `Findings` | record | the ledger (`List<ClosureFault>`) | keeps ledger order; `rationale()` projects it |
| `ProfileReader` (rules) | interface | `read(holder, id)` | replaces `ClosureWalk.Profiles` |
