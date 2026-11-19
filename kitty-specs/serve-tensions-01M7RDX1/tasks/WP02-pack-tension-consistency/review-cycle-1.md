---
affected_files: []
cycle_number: 1
mission_slug: serve-tensions-01M7RDX1
reproduction_command:
reviewed_at: '2026-11-19T08:31:18Z'
reviewer_agent: reviewer-session_019C2Tk2RcGZ2qH78xUiRot5
wp_id: WP02
---

# WP02 review cycle 1: rejected, small fixes (independent reviewer session_019C2Tk2RcGZ2qH78xUiRot5)

Code approvable as is: verifyAll green; rule matches T010 and the G1 extension; twins present; each load fixture
judged through StorageRulesets.verdict gives its expected code, pointer and detail.

Required:
1. DoD: no registration-path test or row shows a pack with a tension fault refused at registration. Add one (reversed
   tension -> validation_failed listing tension_orientation, beside a canonical twin that registers) and record which.
2. Activity log: record the tension_self choice (U2), the out-of-map edits to GraphBinding and GraphBindingTest, and
   why there are no planted or golden changes.

Observation: a pack writing a fully foreign tension needs the qualifier's pack in its depends_on closure
(EdgeIndex.checkQualified); WP03/WP04 scenarios must declare it.
