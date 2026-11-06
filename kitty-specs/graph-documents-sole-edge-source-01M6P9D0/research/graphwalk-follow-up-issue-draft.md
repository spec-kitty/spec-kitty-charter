# Issue draft for spec-kitty/spec-kitty-charter

**Title:** Retire the artifact-field DRG walk (`GraphWalk`, `ComposeResolver`, `GraphWalker`, `Descent`)

---

Follow-up to #240 (ADR-0064). With the edge-minting fields gone, an artifact's only remaining
relationships are its derived ones (a guard's `implemented_by`). `GraphWalk`
(`service/packs/.../application/GraphWalk.java`) walks exactly those, so it no longer walks the DRG
that ADR-0025 decision 4 describes. Only tests construct it: no read path is wired through it or
through `ComposeResolver`.

Leaving it in place keeps a second walk model beside the graph documents. Either:

- **retire it**: delete `GraphWalk`, `ComposeResolver`, the kernel's `GraphWalker` and `Descent`, and
  `GraphWalkTest` (`CompositeComponentResolver` stays: the references read uses it); or
- **rebase it on the graph documents**: walk `PackEdgeRepository.edges(pack)` plus derived
  relations, which is what `ArtifactReadService` resolves today.

Either option amends ADR-0025's walk decision, so it needs an ADR (or an ADR-0064 amendment) of its
own. #240 deliberately left this out to keep the change self-contained; it deleted only the tests
that pinned the retired field mapping (`DirectiveWalkSliceTest`, `ParadigmWalkSliceTest`).

**Done when:** one walk model remains, it reads graph-document edges, and ADR-0025 says so.
