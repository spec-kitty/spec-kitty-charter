---
affected_files: []
cycle_number: 2
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-22T13:16:08Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

**Issue 1 (MEDIUM, a doc claim that is wrong): ADR-0083's R-w rationale is not quoted whole. It drops "service-v*", which widens the ruling.**

`docs/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor.md:254` reads:

> rationale: "No release until WP01-WP09 land; open-packs aligns once against that release. ..."

`decisions/DM-01M812KK99P3T1FQ7ZBXT4Z3T1.md` reads:

> No service-v* release until WP01-WP09 land; open-packs aligns once against that release. WP09 draft (a) widened to
> the full alignment specification.

The ADR now states that "a rationale is quoted whole, except its lead-in" (line 227). "service-v*" is not part of the
lead-in. Dropping it changes the ruling's scope: the ruling holds back service releases, not every release (the
`contract-*` lines are separate). Decision 19 (line 217) already says "No `service-v*` release", so the table also
disagrees with the ADR's own decision.

Fix: quote the sentence as written: "No service-v* release until WP01-WP09 land; ...". If the bare `*` is a problem in
the table, write it as `service-v\*` or in code span backticks. R-t in the same table already carries `STALE_*` raw
and renders.

Everything else in cycle 2 checks out. check-packs.md names no rc.3, no release that does not exist and not the old
asset. R-t and R-v are quoted whole, matching their DM files. The dropped lead-in is stated. The line on R-s over
R-q for #632 is correct. The NoPackCheckSurfaceTest reason is fixed.
