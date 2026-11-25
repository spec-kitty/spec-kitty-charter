# Research: ADR-0082 slices 2 to 4

Sources: the pre-spec squad ([research/pre-spec-squad.md](research/pre-spec-squad.md)), the review of PR #665, and two
read-only research passes on 2026-11-25 (D1 type design, D2 edge-index codes). Line references are to `main` at
ebe6eca72.

## R-1 The kernel visitor contract becomes generic

- **Decision**: `GraphWalker<N, E>` with `onNode(N)` and `onRelationship(E)`; `Descent` unchanged; no end-of-walk hook.
- **Rationale**: `GraphWalker` takes `DrgNode` and `Relationship`, which cannot carry a pack node or a `depends_on`
  edge. The only users are `GraphWalk` (`:76`, `:91`), `Reachability` (`Cascade :117`, `Ancestry :305`) and
  `GraphWalkTest`; each gains `<DrgNode, Relationship>`.
- **Alternatives**: encode packs as `DrgNode`s (lossy, no pack kind in `NodeKind`); a second contract beside it (two
  mechanisms, against ADR-0082 decision 2).

## R-2 Nodes, edges, scope and walk modes

- **Decision**: `sealed interface PackGraphNode permits PackNode, ArtifactNode, EdgeNode`. `PackNode` holds the
  configured pack and its descriptor, not `PackUnderResolution`, so there is no class cycle with the resolver.
  `EdgeNode` carries an `EdgeKind` and the edge as declared, never resolved to a holder. `PackGraph.of(packsInMergeOrder,
  factsOf)` reads facts lazily, once. `record Scope(roots, selection)` only selects; the domain's walk mode (`PACKS`,
  `RELATIONS`) picks which edges are emitted, and a `GraphCheck` from the factory carries it, so a caller never picks
  edges.
- **Rationale**: which pack a reference means depends on the closure, and one graph serves many closures. Lazy facts
  keep the per-registration cost flat (NFR-003).
- **Alternatives**: a scope that names edges (against the page's Scope rule); resolved edges (one graph per closure).

## R-3 Order: rule first, then the graph's visit order

- **Decision**: one bucket per rule within a phase, joined in the factory's declared order; subjects in the graph's
  visit order, which is the caller's merge order, with a pack's artifacts and edges in declared order. Whole-closure
  rules (`OnClose`) take their declared place. The architecture page's determinism guard is amended to say so
  (Proposed).
- **Rationale**: today `ImpactRelations` reports rule by rule (`:94-101`), and the first fault decides the code. The page's
  "pack id in code-point order" contradicts today's input order. `PresetItemRules :85` and `CaseOnlyRules :171` sort
  by UTF-16 order; they are not moved, and the guard does not claim code-point order for them.
- **Alternatives**: subject-major order (the slice 3 draft; it changes the first code, squad S1).

## R-4 Own closure: the root pack node is the subject

- **Decision**: `PackRulesets.ownClosure(ClosureFacts, ProfileReader, Optional<ProfileEnhancementGateway>)` returns
  a `GraphCheck(PACKS, ...)` whose scope selects the declaring `PackNode`; its checks are the fallback (when the
  descriptor names one), the pack's own pins, the pin conflicts (`OnClose`) and the enhancements (`OnClose`).
- **Rationale**: `ClosureFacts` describes a closure, not a pack; the node carries the declaring pack and its fallback,
  so the predicate tests the subject (squad S7). This replaces the spec's "ClosureFacts carries the declaring pack".
- **Alternatives**: `ClosureFacts.declaring()` (mixes closure and pack facts); factory arguments (a constant
  predicate, squad S7).

## R-5 The edge-index refusals today (D2)

| Fault | Read today | Registration today | Equivalent? |
|---|---|---|---|
| Qualified endpoint outside `depends_on` (`EdgeIndex.of`) | `pack_reference_*` (unreachable after a compose) | same codes, 422 | yes; no change |
| Self-override after the merge re-points an edge (`EdgeIndex :309-326`) | 500 `pack_unreadable`, `self_override` in the detail | `self_override` 422, judged without re-pointing | no: registration accepts the re-pointed case (`ClosureImpactCheckTest :574`) |
| Stray step link (`EdgeIndex :354-375`) | 500 `pack_unreadable`, `mission_step_unlinked` in the detail | none for another pack's type | no twin |
| Locked step enhanced (`ClosureView :172-192`) | 500 `pack_unreadable`, first only | `override_not_permitted` 422, every one | same condition |

**A live defect found**: the own-closure rules read `closure.winnerPackOf`, which runs the merge unguarded
(`FallbackProfileRule :106-112`, `RolePinRule :70`), so a registration whose own closure declares a fallback or a pin
can end in `500 pack_unreadable` (`ClosureImpactCheck :482-487`). Slice 4 closes it: the faults become closure
checks that registration places. Filed as a defect issue (2026-11-25).

- **Decision (provisional, Q-A and Q-C open)**: self-override answers `self_override`, judged on the re-pointed index on
  both surfaces. The locked step answers `override_not_permitted`, and the read reports every fault. The stray link
  answers `pack_reference_artifact_missing`, with the violation `mission_step_unlinked`. Statuses follow ADR-0056: 500 on reads, and
  422 on the impact-checked writes through `x-refusal` operation overrides.
- **Wire impact**: `CharterRefusalCode.yaml` (descriptions and `x-refusal` for the two codes), `OfferingUnreadable.yaml`,
  `step-enhancement-compose.feature :11-13, :838-854`, `mission-types.feature :834-899`, `RefusalHandler`, and a
  `### Breaking` fragment in `contracts/charter/changes`. Clients dispatch on `code` (`http-status-codes.md`), so the
  change is breaking under ADR-0022 D2; the 0.x line takes it in a minor. ReleaseGate rule 7 does not fail (no final
  0.2.x baseline).
- **Consumers**: open-packs reads only `/offering`, which never runs the merge: no impact. doc-kitty: no reference found
  in this repository; flagged to the dispatcher.

## R-6 What stays

- `GraphWalk`, `Reachability`, `GovernanceWalk`, `ActionContextWalk`: artifact walks, on epic #636's checklist.
- C21 (`LayerMerge`): same read/registration split; Q-B.
- The `ClosureImpactCheck` per-closure loop and detail prefixes: slice 6.
