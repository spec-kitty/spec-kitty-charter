---
affected_files: []
cycle_number: 1
mission_slug: model-task-routing-01M7PVHQ
reproduction_command:
reviewed_at: '2026-11-18T21:49:19Z'
reviewer_agent: claude-reviewer
wp_id: WP10
---

# WP10 review — cycle 1: changes requested (one line)

Reviewer: independent reviewer (session_019C2Tk2EBfnVS3Cj2pGMmxF). Reviewed at lane-j 0121a1a8b; WP diff f5ff7f466..0121a1a8b.

What is good and stays:
- The changelog fragment: one `### Breaking`, with every item T037 lists and core's seam.
- The service-contract operation and path rows, and the R14 row, decided and built.
- DIV-701 to DIV-707 moved to `open` in both forms, each citing the scenarios and tests that pin it.
- The logical-model kind row and its facts.
- The rest of the compose-packs section.
- `check_docs.py` (frontmatter, related, links, adr) is clean here.
- Your #94 finding stands: the tension-annotation and other-reads rows are still not built, so #94 stays open at
  closeout and only the R14 row is ticked.

## Must fix

1. **`docs/guides/compose-packs.md`, "Ship a routing catalog", the Layering bullet reverses the mutability rule.**
   "A catalog marked `locked` in the pack's `policy.yaml` cannot be overridden by a lower pack" has the direction
   backwards. An override comes from a pack at a higher level, one that depends on the locking pack (org over
   built-in). This guide says so itself: line 1653 says "a same-id enhancement at a higher level replaces the lower
   one", and lines 296 and 326 speak of "a locked artifact of an ancestor pack". The FR-005 scenario in
   `routing-catalogs.feature` pins the same thing: built-in core locks `default`, and the org pack's replacement is
   refused. A pack author would read the rule backwards.
   - Fix, for example: "A catalog its pack's `policy.yaml` marks `locked` cannot be replaced by a pack at a higher
     level (an org pack over built-in); one marked `open` can."
   - Rerun cspell and markdownlint on the file.
