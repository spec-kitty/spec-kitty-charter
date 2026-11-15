### Added

- `selectAgentProfile`: `GET /activations/{activationId}/roles/{role}/profile-selection?language=&framework=` (tag
  `EffectiveAgentProfiles`; ADR-0032 decision 1, ADR-0069 decision 4, rulings D1 to D4 of slice G, #42, #102). It
  answers which agent profile in force fills a role: `ProfileSelectionRead` holds the role, the asked context
  (`ProfileSelectionContext`) and the `candidates`, each a `ProfileSelectionCandidate` with its `id`, the `pack` that
  holds the version in force, its `fit` (`primary`, `alt` or `fallback`) and an `effective-artifact` link; `tie` is
  true when several are answered. The
  order: the role pin of the closure's `policy.yaml` `role_pins` narrows the candidates to the pinned profile and its
  specializations, a human-performed profile serves only `operator` or a role pinned to it, then role fit, the
  context (`languages` and `frameworks` of a specialization), the tier, the longest `specializes_from` chain and
  `routing-priority`; with nothing left, the fallback in force, never for `operator`. An enhancer is never a
  candidate. The role pattern refuses `generic` (`400`); the ETag follows the effective reads and names the role and
  the context.
- Refusals: `404 activation_not_found`; then `422 fallback_profile_conflict` and `422 role_pin_conflict` (two packs
  that pin the asked role differently); then `404 no_profile_matched`. `no_profile_matched` and
  `fallback_profile_conflict` are now raised (`raised: true`), with their declared statuses.
- Core's seam: core may select a step's profile by its role through this read instead of walking the profiles itself.
