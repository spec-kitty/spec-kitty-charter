### Added

- A pack's `policy.yaml` may pin roles: `role_pins: {<role>: <profile-id>}` (ADR-0030, ADR-0032 decision 4, ruling D3;
  a role token `^[a-z][a-z0-9-]*$`, never `generic`, to a profile-id `^[a-z0-9][a-z0-9_-]*$`, each 1 to 64
  characters, in authored order). Role locks stay reserved. A whole-pack registration judges the pins over the pack's
  own closure, with the write in place, and ends `refused` with `422` and the code, naming the closure, when a pin
  names a profile no pack of the closure holds (`role_pin_unknown`), names a profile whose `role` is not the pinned
  role and whose `alt-roles` do not list it, a human-performed profile included (`role_pin_not_fitting`), or when two
  packs of the closure pin one role to different profiles (`role_pin_conflict`; pins are tighten-only).
- A registration also ends `refused` with `422` when the pack's fallback profile is human-performed
  (`fallback_profile_not_agent`, ruling D4), and when the `specializes_from` edges among the agent profiles of a
  closure it joins form a cycle (`specialization_cycle`, ruling D2), naming the cycle's profile ids in order. The
  startup census logs a stored pack that breaks one of these rules.
- Core's seam: nothing until the selection read exists.
