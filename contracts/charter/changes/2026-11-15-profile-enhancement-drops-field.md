### Added

- A new refusal code, `profile_enhancement_drops_field` (rulings D5 and D7, #380): an agent profile that `enhances`
  another may add to it, never drop part of it. Its identity fields (`profile-id`, `name`, `purpose`, `role`,
  `alt-roles`, `performer`, `routing-priority`) are its own and never merged; every other top-level field it declares
  replaces the enhanced profile's, and is refused when it loses a list item (in any order), a sub-field (recursively)
  or a value. An enhancer that declares a non-empty `excluding` is refused the same way, naming `excluding`. The detail
  names the enhancing pack first and the field. A whole-pack registration, and each write judged over the closures its
  pack joins, ends `refused` with `422` and this code; a read of content stored before the rule answers `500`. The
  startup census logs a stored pack that breaks the rule. Core's seam: nothing until the effective profile read exists.
