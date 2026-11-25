# Quickstart: add a check of the pack graph

1. Write the rule in `packs.domain.rules` as a function of the node it judges (or, for a whole-closure rule, of the
   selected nodes), returning `ClosureFault`s. Read nothing from storage; take a reader as a factory argument.
2. Add it to the phase it belongs to in `PackRulesets` (`ownClosure`, `references`, `relations`), at the position that
   keeps the order of findings: rules report in the order the phase declares them.
3. Give it a predicate on its node when it applies only to some nodes.
4. Run it: `PackRulesets.<check>(..).run(graph, scope)`. Never write a loop over packs or edges outside
   `packs.domain`; `PackGraphWalkBoundaryTest` fails the build if you do.
5. Test red first: a fixture that breaks the rule, and the same fixture fixed, on the production path (registration or
   `pack-doctor`).
