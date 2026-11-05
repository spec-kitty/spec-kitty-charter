### Added

- A pack declares its mutability levels in a policy file (FR-022, ADR-0062; pack-schema change, no API schema change).
  `pack.yaml` gains the optional `mutability_policy`, a pack-relative path that the `pack-descriptor` schema admits only
  as `policy.yaml` in this version. The new closed `contracts/packs/schemas/policy.schema.json` validates the file:
  `schema_version` (the integer 1), `kind_defaults` (an artifact kind token to `open`, `extendable` or `locked`) and
  `artifacts` (a bare URN to a level); role locks and pins (ADR-0030) are reserved, so a `roles` or `pins` key makes
  the file invalid. A pack whose `pack.yaml` names a policy file that is missing or invalid is refused
  `pack_unreadable`, on the offering and on any closure that holds it (no new refusal code). A registration is judged at
  the highest level along the written pack's composition closure, and a refusal names the pack that set the level. The
  file is digested in the source fingerprint after the manifest, so a lock edit moves the `ETag`; a pack without it
  keeps its fingerprint. No pack ships a policy file yet, so no answer changes.
