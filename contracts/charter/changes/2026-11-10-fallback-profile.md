### Added

- A pack may declare its fallback agent profile in `pack.yaml`: `fallback_profile: <profile-id>` (ADR-0068, #457;
  string, in the identifier grammar `^[a-z0-9][a-z0-9_-]*$`, 1 to 64 characters). A whole-pack registration judges it over the pack's own closure, with the
  write in place, and ends `refused` with `422` and the code, naming the closure, when the profile is held by no pack
  of the closure (`fallback_profile_unknown`), is not `locked` along it (`fallback_profile_not_locked`), or does not
  declare `role: generic` with no alt-roles (`fallback_profile_not_role_neutral`). The startup census logs a stored
  pack that breaks the rule.
- Reserved refusal codes (`raised: false`): `fallback_profile_missing` (the built-in pack declares no fallback; raised
  once open-packs ships the declaration), `fallback_profile_conflict` and `no_profile_matched` (`404`), both for the
  profile-selection read.
- Core's seam: nothing until the selection read exists.
