---
affected_files: []
cycle_number: 1
mission_slug: serve-tensions-01M7RDX1
reproduction_command:
reviewed_at: '2026-11-19T10:22:49Z'
reviewer_agent: reviewer-session_019C2Tk2RcGZ2qH78xUiRot5
wp_id: WP03
---

# WP03 review cycle 1: rejected (independent reviewer session_019C2Tk2RcGZ2qH78xUiRot5)

Reviewed review/602-wp01 @ c00bca937. Verified: verifyAll green (reviewer's own run); slot upsert and per-slot
201/200; stored() matches WP02's ownership rule; refuseSecondTension answers tension_duplicated at /to; step-enhancement
guards use sameSlot; removal rows 1-7 against RelationshipRemovalService and its tests; ArtifactRemovalService drops a
registered tension on either side by identity; InboundReferences adds the tension's target side with a positive control.

Required:
1. Correctness: a tension's unheld bare stored source was never resolved (targets.require only for a qualified from),
   so a bare source another pack holds bypassed PACK_REFERENCE_UNQUALIFIED. Require the source; test with its
   qualified twin.
2. T015.2: a PackRegistrationService test that `A requires B` and `A tension B` in one shard diff as separate writes.
3. T013.4: the request example RelationshipRegistration.tension.yaml, indexed.
4. T018 scenarios: a later registration keeps the tension; removal row 2; removal row 5 (409 whose detail names
   relation).
5. T014.1: a tension registered in the reverse orientation replaces the pair's tension (one edge, new rationale).

Nits: RelationshipRegistration.yaml `to` description; ArtifactRemovalService comment wrap. Open point to record: the
wire accepts a qualified from for any tension and stored() normalizes even when the pack holds to.
