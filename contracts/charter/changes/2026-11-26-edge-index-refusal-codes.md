### Breaking

- A read and a registration of the same closure now answer each fault of its layer merge with one code (issue #639,
  operator rulings D2, Q-A, Q-B and Q-C). A read that merges a closure (the effective reads, the activation reads
  and the activation writes) answered every one of these faults `500 pack_unreadable`, the first fault only. It now
  answers them as follows, every fault in one refusal, with the code in the problem's `code` and, for several faults,
  one header violation per fault:
  - `self_override` for an `overrides` edge that the layer merge re-points to its own source (C36, A12);
  - `override_not_permitted` for a layer override that the overridden artifact's level forbids (C21), and for an
    enhancement of a step whose mission type is locked (C22, C25);
  - `pack_reference_artifact_missing`, with the violation `mission_step_unlinked`, for another pack's `requires` edge
    at a mission type in force that names no step of the type.

  The statuses follow ADR-0056: `self_override` and `override_not_permitted` move from 422 to 500 as their own status,
  and their `x-refusal` entries gain `operations` overrides of 422 for `getPackRegistration` and the twelve
  impact-checked writes, as the composition codes have. A client that dispatches on `code` reads the new codes on the
  reads; one that reads `self_override` or `override_not_permitted` on a write sees no change there.
- Registration and the impact-checked writes now refuse three shapes they accepted before, with the codes above, as 422:
  an `overrides` edge that the merge re-points to its own source (an edge from a layer override onto the copy it
  replaces), a layer override of an artifact that is not open along the closure (a pack that locks an artifact a later
  level replaces), and another pack's step link that names no step of the mission type. A stored pack in such a closure
  is named by the startup census. A stored pack that replaces a locked artifact (C21) can then be cleared only by its
  `DELETE`: a re-registration either keeps the override, refused `override_not_permitted`, or drops it, refused
  `governance_policy_violation` by the write gate, since a locked artifact may not be removed either. A merge that
  refuses with a code of its own (`pack_sibling_conflict` for a step contest no entry settles) refuses no write that it
  did not refuse before; on a read, the fault the merge meets first stays the answer, as before. A registration whose
  own closure declares a fallback profile or a role pin beside such a fault answered `500 pack_unreadable` from the
  merge (issue #667); it answers the fault's 422 now. Core's seam: the generated client regenerates the two `x-refusal`
  entries; no schema changes. spec-kitty-open-packs reads only `/offering`, which never runs the merge, and is
  unaffected.
