### Breaking

- An agent profile declares `role` and optional `alt-roles`; the positional `roles` list is retired with no window
  (ADR-0069, #458). In the `agent-profile` pack schema `role` is required and `roles` is not a key, so a profile file
  that still carries `roles` is unreadable (`500 pack_unreadable`). A role token is kebab-case,
  `^[a-z][a-z0-9-]*$`, at most 64 characters; `alt-roles` holds at least one item when present, each once and never
  the `role` (a repeat is reported as `uniqueItems` at `/alt-roles/<n>`). `generic` is reserved for a fallback profile
  (ADR-0068).
- `AgentProfile` (the profile read) answers `role` and `altRoles` (`[]` when none) and no longer answers `roles`.
- An `excluding` entry that named `roles` names `alt-roles`.
- Core's seam: core reads profiles through this contract only after the cutover; it adopts `role` and `altRoles`
  through the generated client then. spec-kitty-open-packs migrates its profiles before its pin moves
  (open-packs#70).
