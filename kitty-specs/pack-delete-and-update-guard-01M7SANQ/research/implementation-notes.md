# Implementation notes (calls made during implement)

These are logged as open review items for the pre-merge review. plan.md is not edited after the analysis, so that the
recorded analysis stays current.

- **I1 (WP05, P7).** A dry run that the worktree guard refuses (401, 403) is still entered in the audit log, like every
  refused attempt, so `dryRun=true` cannot hide a probe. Every other dry run is not entered (NFR-002). The same text is
  in `parameters/DryRun.yaml` and ADR-0081 decision 5.
- **I2 (WP05).** `RegistrationJobs.Origin` carries the request's `Mode`. A 5-argument constructor keeps the write
  (`COMMIT`) as the default for the callers that never dry-run.
- **I3 (WP04, P3), ruled (a) by the operator after review R4-1.** A pack whose own `pack.yaml` cannot be read can be
  deleted: it is judged by its configured name (the name an activation and a qualified reference name it by; a
  `depends_on` or `parent_pack` names a ULID `pack_id`, which is unknown, so such a pack is not found, and its closure
  already holds the unreadable pack), its referrers are found from what the salvage read finds, and any other pack the
  judgement needs fails it closed (`500 pack_unreadable`). The dry run's `PackImpact.version` is then absent, so
  `version` is no longer required. The spec's "can still be deleted" edge case stands. The first call, a
  `500 pack_unreadable` telling the client to replace the pack first, left a pack that no longer reads with no way out
  but a replace.
- **I4 (WP04).** `deletePack` joins the impact-checked writes of the refusal handler and the per-operation maps of
  `CharterRefusalCode` ("thirteen operations"), so it answers the composition codes as `422`, like the other
  `DELETE`s.
- **I5 (WP01 → WP05).** The contract change and the controller change land together: the generated `RegistrationApi`
  has no default methods (`skipDefaultInterface=true`), so the WP01 commit alone does not compile (reviewer finding 1).
  WP01's commit is kept for the record; the branch compiles from the WP04/WP05 commit on.

# WP01 review (independent reviewer agent, cycle 1): REJECT, and the dispositions

| # | Sev | Finding | Disposition |
|---|-----|---------|-------------|
| 1 | HIGH | WP01 alone does not compile (no default methods) | Folded: I5; the controller lands with WP04/WP05 |
| 2 | HIGH | `ReservedRefusalCodesTest`: the probe does not name `PACK_HAS_DEPENDENTS`, and no main class raises it | Folded: the probe names it; `PackDependents` raises it |
| 3 | MED | `PackImpact.version` is required, but an unreadable pack has none | Folded by I3, then reopened by its ruling: `version` is optional, absent for a pack whose `pack.yaml` cannot be read |
| 4 | MED | The shared 412 text says an If-Match on a missing pack is 412, not 404 | Folded: the text is limited to registrations, and says a DELETE answers 404 first |
| 5 | MED | The `PackRegistrationStatus` pack-link sentence was stale | Folded: "except a dry run's" |
| 6 | MED | The order of 409 `pack_has_dependents` and 500 `pack_unreadable` was ambiguous | Folded: 500 comes before any dependent is judged (path and code text) |
| 7 | LOW | The ADR-0065 D2 body had no inline amendment marker, and `decision_status` did not list it | Folded |
| 8 | LOW | The path-level `packId` description did not cover `deletePack` | Folded |
| 9 | LOW | The 422 text said "PR A is strict" | Folded: "The compose is strict" |
| 10 | LOW | DIV-602 cites `version-order.feature` before it is committed | Folded: it lands with WP02 |
| 11 | LOW | `compose-packs.md`: going back to an older version after a delete | Folded: it is accepted only with the content it was registered with |
