---
affected_files: []
cycle_number: 1
mission_slug: regex-bounds-and-sonar-cleanup-01M706DF
reproduction_command:
reviewed_at: '2026-11-09T13:31:03Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP02
---

# WP02 review feedback (cycle 1) - reviewer-renata

Verdict: changes requested. The regex rewrites are correct and equivalent (possessive is safe because `-`/`.` are not in the quantified classes); `:missions:test` is green. Two gaps:

1. **Missing MissionStep step-id cap boundary test** (MissionStep.java:47-49). The out-of-map edit adds `id.length() <= MissionTypeIdentifier.MAX_LENGTH`, but no test pins it. The missions mutation floor is 70; an un-tested `<=` boundary survives mutation. Add a test in IdentifierBoundsTest (or a MissionStep test): a step id of exactly 64 chars is accepted, 65 is refused as ConstraintBreachException, and a 20 001-char kebab id is refused without StackOverflowError. Also record the out-of-map MissionStep edit in the commit body as the WP prompt requires.

2. **GuardReference cap is checked after matching, not before** (GuardReference.java:33-36 vs parse at :45-50). FR/WP require the cap be checked BEFORE matching. `parse` runs `SPELLING.matcher(text).matches()` on unbounded input and only the canonical constructor enforces 64. Add a pre-match length guard in `parse` (e.g. refuse `text.length() > 2*64 + "::guard:".length()`), keep the per-part check in the constructor. Tests already cover the refusal; consider asserting the message/no-match path for an over-long input.
