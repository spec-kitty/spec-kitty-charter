---
affected_files: []
cycle_number: 1
mission_slug: serve-tensions-01M7RDX1
reproduction_command: spec-kitty agent tasks move-task WP04 --to approved --mission serve-tensions-01M7RDX1 --agent reviewer-session_019C2Tk2RcGZ2qH78xUiRot5
reviewed_at: '2026-11-19T11:32:43Z'
reviewer_agent: reviewer-session_019C2Tk2RcGZ2qH78xUiRot5
wp_id: WP04
---

Approved by reviewer-session_019C2Tk2RcGZ2qH78xUiRot5: approval:WP04

# WP04 review cycle 1: approved (independent reviewer session_019C2Tk2RcGZ2qH78xUiRot5)

Reviewed review/602-wp01 @ 9fa27cd74. TensionsInForce follows G2 (place-based resolution; the writer's own losing
version makes the tension absent; any other side resolves to the winner; not EdgeIndex.merged's source-place rule);
both sides in force; canonical; collapse with the first writer's words; reconcilers reach both sides and are in force.
The dropped self-pair branch is unreachable (kernel refuses a bare self-tension); the record guards source < target.
GraphProjection takes tension edges only from TensionsInForce. The tensions example pack matches the five cases; the
fragment and lowercase tactic-id deviations are accepted. verifyAll + wholePackRoundTrip pass on the reviewer's run.
Heads-up: MixedReadConcurrencyTest failed its stale_input bound once under load (197 > 192), passed on rerun and in
isolation; unrelated to WP04, follow up only if it recurs. Nit: the permutation test shuffles within one pack only, which
is intended (order across packs decides the first writer).
