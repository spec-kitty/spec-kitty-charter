### Breaking

- The pack composition mission's one change set (C-006; `pack-composition-depends-on-and-the-dependency-resolver-01M6F541`):
  the eleven refusal codes of the dependency resolver are declared, and `GET /offering` raises all eleven:
  `pack_dependency_cycle`, `pack_dependency_unknown`, `pack_dependency_out_of_range` (which also covers two ranges on
  one pack that share no version), `pack_parent_unresolved`, `pack_level_violation`, `pack_name_conflict`,
  `pack_duplicate_id`, `pack_reference_unqualified` (a bare reference, in a graph edge or a typed reference, that resolves only in
  another pack) and `pack_reference_pack_missing` (a qualified reference whose pack is outside the referring pack's own
  `depends_on` closure), `pack_reference_artifact_missing` (a qualified reference to a pack of the closure that does not
  hold the artifact) and `pack_sibling_conflict`, all with 500 like `pack_unreadable`: the request is fine and the
  configured packs do not compose (operator ruling of 2026-11-03). Each names the violating pack in `detail` and in the new
  optional `packs` member of the refusal body (`Problem.packs`, a list of `RefusalPack`: the `pack_id` and `name` of every pack
  the refusal names, the violating pack first). The read exposes what the compose
  resolved (FR-019, FR-020).
  `Urn` stays the bare node identity, `<kind>:<id>` (a pack is provenance, not identity, ADR-0048 decision 10). A
  reference that relies on another pack, `<pack-name>::<kind>:<id>`, is the new `PackQualifiedReference` schema; a
  registered relationship's `to` is one, so a relationship reads back the qualifier the artifact wrote, while its
  `from` stays an artifact of the pack.
  The pack descriptor tightens in the same set: `pack_id`, `parent_pack`, `depends_on[].pack_id` and `overrides[].member`
  must be ULIDs and `name` must match `^[a-z0-9][a-z0-9_-]*$`, or `pack.yaml` is unreadable (`pack_unreadable`).
  `OfferingPack` answers the required `dependsOn` array per pack: each declared dependency's target `pack_id` as `descriptorPackId`, the
  declared PEP 440 `range` and the `version` that satisfied it (`[]` for a pack that declares none), in a new
  `ResolvedDependency` schema, and the required `descriptorPackId`, the `pack_id` the pack's `pack.yaml` declares,
  which `dependsOn[].descriptorPackId` names (`packId` stays the configured identifier, so a client joins a dependency edge to
  its offering entry by `descriptorPackId`); `PackPin` carries the same facts as an optional member, mirrored into
  `../offering/schemas/ActivationConfig.schema.json`. The additive response properties are graded `ERR` by
  `../compat/severity-levels.txt`, so `info.version` moves `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT` (ADR-0022: a 0.x
  breaking change takes a minor bump and this `### Breaking` entry). No released contract is broken incompatibly:
  no release was cut under any version (C-002), so the compatibility check has no baseline. Scenarios: the new
  `scenarios/composition.feature` drives the eleven codes `GET /offering` raises with a negative control on the same
  fixture shape, plus the diamond, the descriptor without `depends_on` and the settled sibling contest; the raised-codes
  table of `scenarios/errors.feature` lists the eleven codes. Core's seam: a client that reads `GET /offering` must
  tolerate the `dependsOn` member (or move to the new version) and may now meet the eleven refusal codes.
