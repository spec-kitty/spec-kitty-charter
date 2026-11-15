---
affected_files: []
cycle_number: 3
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-13T21:30:26Z'
reviewer_agent: claude-reviewer
wp_id: WP03
---

# WP03 review, cycle 3: changes requested

Reviewed 12001a2d9. It is SSH-signed (gpgsig) and has no Co-Authored-By or model name. The lane tip 83bdd3a is the unsigned merge that the tooling made.

## Resolved

- **Issue 1 (control for the too-wide factory).** `aTooWideFactoryIsCaught` now pins two things. First, `requires` accepts `hasSize(18*18)`. Under the cycle-1 `leaves()`, `KindRef<?>` expanded to an empty set, so this assertion fails. Second, `outside(overrides)` contains `directive -> skill`. Under the cycle-1 code, `getBounds()[0]` was `Kind` and was not expanded, so the set was empty and this assertion fails. The `doesNotContain(step_enhancement -> skill)` check also pins the intersection, since a `Kind`-only expansion would let that pair in. The test now guards the fix.
- **Issue 3.** The EndpointTable javadoc is rewrapped.
- **Issue 4.** The commit message names EndpointTable.java and KindTypes.java as out-of-map files.

## Not resolved

**Issue 2 (MEDIUM): the EndpointRuleTest changes are not in the commit.** The 12001a2d9 message says that EndpointRuleTest "pins relationship_endpoint_not_allowed for every target-side neighbour, and adds the source-side and diagonal neighbours (skill -> anti_pattern rejects, action -> styleguide applies, mission_type -> mission_type enhances, step_enhancement -> step_enhancement overrides, and one per remaining relation)".

The commit touches only EndpointTable.java and EndpointTableAgreementTest.java. EndpointRuleTest.java was last changed in bb60f7d, and the lane worktree is clean. The file is still the cycle-2 version:

- `neighbourOf` varies only the target, so no source-side neighbour is tested.
- None of the four named neighbours (rejects, applies, enhances diagonal, overrides diagonal) is tested.
- The code assertion is still `isIn(ENDPOINT_NOT_ALLOWED, OVERRIDES_SOURCE_NOT_ACTIVATABLE)` inside the always-true `if`.

The change was probably never staged. Commit the EndpointRuleTest work that the message describes:

- Pin `relationship_endpoint_not_allowed` exactly and drop the `if`.
- Add the four named neighbours and a source-side neighbour for each live relation.
- Check that each CsvSource row is outside its relation's rows in EndpointTable.

Then run :kernel:check again.

## Low (record only)

- The odd-shape scan does not see varargs or `List<KindRef>` factories. This is accepted as recorded, because no such factory exists.
- 12001a2d9's message describes work the commit does not contain. The next commit message should describe only what it actually changes.
