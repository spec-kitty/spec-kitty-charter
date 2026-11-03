---
affected_files: []
cycle_number: 2
mission_slug: pack-composition-depends-on-and-the-dependency-resolver-01M6F541
reproduction_command:
reviewed_at: '2026-11-03T16:47:11Z'
reviewer_agent: reviewer-renata
wp_id: WP01
---

# WP01 recycled to planned (reviewer-renata, 2026-11-03)

## Why

1. **The implementer's identity was never recorded.** WP01's claim, in_progress and for_review transitions
   (status.events.jsonl, 2026-11-03T00:18:25 to 00:18:26) carry the placeholder actor `user`, with
   `agent_profile: __resolved_profile_absent__`. Only the 00:18:10 history annotation names java-jenny. The accept
   gate therefore refuses with SINGLE_BRANCH_REVIEW_NOT_INDEPENDENT: reviewer independence cannot be confirmed. The
   2026-11-03T02:13 approval is withdrawn on that ground. It cannot be repaired by editing the log; the cycle has to
   be redone.

2. **A fresh review found one BLOCKING defect (B1).**

### B1 (BLOCKING): an `overrides` edge settles a contest whatever member it names

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/PackCompositionResolver.java:261-278`.
  `refuseSiblingContests` puts every `override.artifact()` into `settled` and never reads `override.member()`. An
  edge whose member is not in the compose, or is not one of the contesting siblings, still silences the refusal.
  Nothing else in main code reads `member` (only the YAML writer does), so the "winner" it names is never checked.
- The rule is written down. data-model.md (PackOverride table): `member`: "it must be in the compose". WP01's own
  schema says the same: `contracts/packs/schemas/pack-descriptor.schema.json:88-89`. FR-012 / spec.md scenario 7:
  the edge names "the winning member".
- The test asserts the opposite of that rule:
  `service/packs/src/test/java/ai/speckitty/charter/packs/application/OfferingReadServiceTest.java:203-224`
  (`aSettlingOverridesEdgeServesTheOffering`). There, a contesting sibling (`left`) settles its own contest with
  member `01M4P01D5TWB5FKZGNNQG3943R`, which is no pack of the compose, and the offering is served.
- Why it blocks: with a typo in `member`, or a stale one, the contest resolves silently. Which sibling's version
  applies is then down to load order. FR-012 exists to rule that out ("a conflict is a decision, not load order").
  The check is cheap and the resolver stays pure: `byId` and the contesting siblings are already in hand. No new
  wire code is needed.

Note: in the 02:13 cycle I accepted the member check as a deferred residual. On a fresh read I reverse that. The
deferral that data-model.md actually records covers only two distribution packs settling one artifact. It does not
cover the member's field rule.

## What the implementer must do on resume

1. **Re-claim WP01 under your own identity.** Use java-jenny, the WP's `agent_profile`, through
   `spec-kitty agent action implement WP01 --agent <your-agent> --mission pack-composition-depends-on-and-the-dependency-resolver-01M6F541`
   (or the claim path your harness uses). Every transition (claimed, in_progress, for_review) must carry a named
   actor, not `user`. Check this in `status.events.jsonl` before handing off.
2. **Fix B1.**
   - An `overrides` edge settles a contest only if its `member` is one of the packs contesting that artifact at
     that level, which by construction puts it in the compose.
   - Any other edge leaves the contest unsettled. Refuse with the existing `SIBLING_CONFLICT` /
     `pack_sibling_refinement_conflict`. The detail names both siblings and the artifact, and says that the edge's
     member (named) is not one of them.
   - If you think a member that is in the compose but is not a contestant should settle the contest, raise it with
     the planner and do not decide it alone. Either way, a member outside the compose must not settle.
3. **Add the tests in PackCompositionResolverTest.** Both are negative controls: (a) the member is outside the
   compose: refused; (b) the member is in the compose but is not a contestant: refused, or whatever the planner
   rules. Fix `OfferingReadServiceTest.aSettlingOverridesEdgeServesTheOffering` so that the settling edge names a
   real contesting sibling. Do not weaken its assertions.
4. **Re-run the gates.** Run `:packs:test`, `:kernel:test`, the parity suites (WireTokenParityTest,
   ReservedRefusalCodesTest, RefusalHandlerTest, PackSchemasTest) and spotless/checkstyle/javadoc. Record what you
   ran with `spec-kitty agent tasks add-history WP01 --note ...` under your identity.
5. **Hand off** with `move-task WP01 --to for_review --agent <your-agent>`. reviewer-renata re-reviews.

## NON-BLOCKING (for the record, no action required for this cycle)

- N1: Any pack of the compose may settle a contest; nothing checks that the settler is the distribution pack. The
  code has no distribution-pack concept yet. Carry this as a follow-up with the distribution-pack flow.
- N2: spec.md scenario 7 says "with that member's version in force". `member` is not consumed by any merge, and
  WP01's success criteria only require that the compose resolves. This is a spec-fidelity gap for the planner and
  mission review, not for WP01.
- N3: Contested artifacts are compared as opaque strings: graph edge `target` against `override.artifact`. If the
  two are spelled differently (for example a pack-qualified form), the edge does not settle and the compose fails
  closed. That is acceptable, but worth a sentence in the docs.
