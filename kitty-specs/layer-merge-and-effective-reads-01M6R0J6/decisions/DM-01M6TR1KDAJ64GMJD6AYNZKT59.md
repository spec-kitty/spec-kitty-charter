# Decision Moment `01M6TR1KDAJ64GMJD6AYNZKT59`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.delta-followups`
- **Input key:** `phase_a_delta_followups`
- **Status:** `resolved`
- **Created:** `2026-11-07T10:14:39.787001+00:00`
- **Resolved:** `2026-11-07T10:14:41.975964+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

Phase A plan delta: the open points the planner left (artifact DELETE vs authored edges, a pair in both registered and authored documents, registration_timed_out status, edge gating on a whole-pack replace, auditing a 401)

## Options

_(none)_

## Final answer

(1) An artifact DELETE answers 409 registration_conflict when an author graph document holds an edge from the artifact or declares its node (removing the file alone would leave dangling edges; never rewrite author files). (2) A relationship DELETE of a pair that an author graph document also holds answers 409 relationship_not_registered and changes nothing, even when registered.graph.yaml holds it too (the author wrote it, per DM-01M6TQ62HXYF362GAFS2BZ3PGF; a 204 that leaves the edge in force would mislead). (3) registration_timed_out is reported with status 504 in the refused registration's problem. (4) A whole-pack replace gates every added or removed edge whose source is a locked ancestor artifact with the RELATIONSHIP operation, as putPackRelationship does. (5) A 401 on a registration or DELETE path is audited by the guard (NFR-010).

## Rationale

Within the spec and the operator's ruling DM-01M6TQ62HXYF362GAFS2BZ3PGF; closes the planner's to-confirm points.

## Change log

- `2026-11-07T10:14:39.787001+00:00` - opened
- `2026-11-07T10:14:41.975964+00:00` - resolved (final_answer="(1) An artifact DELETE answers 409 registration_conflict when an author graph document holds an edge from the artifact or declares its node (removing the file alone would leave dangling edges; never rewrite author files). (2) A relationship DELETE of a pair that an author graph document also holds answers 409 relationship_not_registered and changes nothing, even when registered.graph.yaml holds it too (the author wrote it, per DM-01M6TQ62HXYF362GAFS2BZ3PGF; a 204 that leaves the edge in force would mislead). (3) registration_timed_out is reported with status 504 in the refused registration's problem. (4) A whole-pack replace gates every added or removed edge whose source is a locked ancestor artifact with the RELATIONSHIP operation, as putPackRelationship does. (5) A 401 on a registration or DELETE path is audited by the guard (NFR-010).")
