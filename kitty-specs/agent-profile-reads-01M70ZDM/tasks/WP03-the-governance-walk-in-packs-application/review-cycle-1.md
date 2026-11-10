---
affected_files: []
cycle_number: 1
mission_slug: agent-profile-reads-01M70ZDM
reproduction_command:
reviewed_at: '2026-11-09T22:41:35Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP03
---

# WP03 review: changes requested (reviewer-renata)

## Blocking

**Issue 1: `EdgeIndex.writtenLinks()` resolves edge targets differently from the charter's own model, so the pack-scoped walk can list a dangling target as `held=true`.**

`links()` resolves each edge through `neighbour(edge)`: a bare target belongs to the source's pack, and a qualified target belongs to the pack that `holdingPack` names. The target is kept only if `holds(place)` at that place. That is the GraphWalk resolution rule. `writtenLinks()` uses the raw `edge.to()` instead, and `GovernanceWalk.overWritten` judges `held` with `holdsUrn(urn)`, which is true when *any* pack of the closure holds the URN. So when a bare edge `agent_profile:P requires directive:D` is written in pack `qa`, and only `builtin` holds `D`:

- `links()` returns nothing, so the activation-scoped walk drops D (dangling).
- `writtenLinks()` returns P→D, so the pack-scoped walk lists D as `held=true`, names `builtin` as its holder, and expands it.

I confirmed this with a throwaway probe over `EdgeIndexTest.Closure` (builtin holds `directive:D`; qa depends on builtin, holds P, writes the bare edge). Output: `links=[]`, `over=[]`, `overWritten=[Entry[urn=directive:D, step=REQUIRES, ..., held=true]]`. A qualified target whose named pack does not hold the URN, while another pack does, has the same problem.

Why it blocks: W2 asks for `links()` *plus* the dangling targets. It does not ask for a second resolution rule. The two entry points answer differently for the same edge. The pack-scoped read also hides exactly the declared-but-absent reference that `held=false` is there to report. That is a divergence from the charter's model.

Fix:
- Build `writtenLinks()` on `neighbour(edge)`.
  - A resolved and held place gives a held link to `target.urn()`.
  - An unresolved or unheld place gives a dangling link.
- Carry `held` per link (or as the set of dangling targets) instead of `holdsUrn(urn)`.
- Decide and document what happens when one URN is reached by both a dangling edge and a held edge (minimum step and the tie order pick the parent, so `held` should follow that parent's link).
- Add red tests in `EdgeIndexTest` and `GovernanceWalkTest` (or `ClosureViewGovernanceTest`) for:
  - a bare cross-pack target;
  - a qualified target its named pack does not hold.

  Both must give `held=false`, no holder and no expansion.
- Rework the javadoc of `writtenLinks` ("whichever pack it names it in") and of `holdsUrn`.

## Non-blocking (fix if convenient, else route)

1. `ClosureView.effectiveArtifact` on a view built without a `PackRepository` (the 3-argument `ClosureQueries` constructor used by `RegistrationImpact`, or the 2-argument `MergeInputs`) refuses `PACK_UNREADABLE`. That is a wiring fault, not an unreadable pack. Consider having `WinnerSource.none()` throw `IllegalStateException`. No test pins the `PACK_UNREADABLE` path; add one (the stored pack lacks the profile the facts hold, and the digest is unchanged).
2. `ProfileGovernanceCorpusTest`'s display name says "lists ... nor a dangling parent", but nothing asserts that no entry has `held=false`. Assert it or reword the name.
3. `snapshotWithin` sets `view.fingerprint()` to the closure digest, not the whole-source digest. Route to WP04: it must not use that value as the source fingerprint or ETag.
4. The lazy winner read can throw `STALE_INPUT` after the session's snapshot. Route to WP04: re-run the session on that refusal, as `snapshot` does, or document that it reaches the client.

## Checked and fine

- c056bd01 changes no action-walk answer:
  - With `expandable = held -> true`, the frontier is still `parents.keySet()` in TreeMap order.
  - `step.relation() == SUGGESTS` is equivalent to `step == Step.SUGGESTS`, because only SUGGESTS carries that relation.
  - The enhancer helpers moved unchanged.
  - The `ActionContextWalkTest` diff is empty.
- W1, W3, W4 and W6 are implemented and tested: minimum step, tie order, cycles and candidates.
- The winner read reads first, then compares the closure digest, and checks STALE before PACK_UNREADABLE. That order is sound, apart from a negligible ABA window.
- `snapshotWithin` reuses `loaded()` and `viewOf()`, so its composition cannot drift from `snapshot`'s.
- The `ClosureQueries` edit is justified as the smallest accessor. The 3-argument constructor is still used by `RegistrationImpact`.
- Gates: `:packs:check -PopenPacksDir=...` and `:deployable:test` (CharterContractSuite, HexagonalArchitectureTest) are green.
