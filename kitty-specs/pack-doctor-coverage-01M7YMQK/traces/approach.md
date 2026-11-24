# Approach

> Reconstructed on 2026-11-24 from the orchestrating session's record, not appended as it happened (see
> `tooling-friction.md` for why).

- **[2026-11-21][plan] One PR, lanes with a coordination branch.** WP01 to WP09 were planned. The operator's R-p to
  R-r added WP10 to WP12 for all of #154, and R-y later added WP13.
- **[2026-11-21][implement] Implement then independent review per WP, with profile-loaded agents.** Lane heads were
  pushed and confirmed after each approval. Several review cycles caught real defects; WP08 took four, the last a
  code review of the integration.
- **[2026-11-22][implement] A dedicated integrator merged every code lane into lane-h before WP08.** This avoided
  hand-merging inside a docs WP. Its fixes were later found to sit outside any WP's authorship (see friction).
- **[2026-11-23][accept] Acceptance exposed two spec gaps that went back to the operator.** R-x deferred
  ClosureVerdict; R-y added the policy-lock case-only check as WP13.
- **[2026-11-23][consolidate] The stale lanes were refreshed by hand in dependency order.** That was a, b, d, i, c, j,
  k, e, f, g, h and l, reusing the reviewed conflict resolutions through a three-way merge. The final lane-l tree was
  byte-identical to the approved one.
- **[2026-11-23][land] Pre-merge adversarial squad with four lenses.** The squad was reviewer, architect, debugger and
  lexicon. Its findings were folded in three batches split by file ownership (rules, storage/CLI, docs), then
  independently reviewed. Five rulings (R-z to R-ad) came from it.
- **[2026-11-24][land] The Tier R review widened the YAML guard in four rounds.** The rounds were B3 (string aliases),
  B3′ (number aliases), B3″ (float exponents) and F1 (an off-by-one in the plain form). Lesson: bound content by its
  expanded and rendered size at parse time, for every scalar type at once, not one probe at a time.
