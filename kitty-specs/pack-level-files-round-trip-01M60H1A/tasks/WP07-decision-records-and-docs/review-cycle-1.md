---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T09:05:14Z'
reviewer_agent: claude
wp_id: WP07
---

# WP07 review 1 (reviewer-renata, independent delegate): changes requested

**MEDIUM**

1. The NFR-004 claim overreaches: the timing was of the internal pack, which has no manifest. Say what was measured.
2. "Pack-level files (#195)" is called built in ADR-0043:47 and ADR-0037:92 and :162, but `pack.md` (WP08) is pending.
   Qualify both.
3. DIV-026 breaks the register's block rule. Its block is mission 01M5AFMA's, and 026 was reserved there and then
   dropped. Open a new block for #195 and list it. The entry should name a corpus case.

**LOW**

- DIV-005 and DIV-006 are "retired", not "closed".
- The DIV-026 ruling text differs between the md and the yaml.
- `descriptor-schemas.md:111`: "designed here" also covers `pack-descriptor`, which was imported.
- The ADR-0031 frontmatter still says "open questions stay recorded".
- ADR-0043's list of `Preset` invariants leaves out the id grammar; the blank-description message does not name a value.
