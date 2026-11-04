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
  optional `packs` member of the refusal body (`Problem.packs`, a list of `RefusalPack`: the `descriptorPackId` (the `pack_id` its
  `pack.yaml` declares), the `packId` (the configured identifier, as on `OfferingPack` and `HoldingPack`) and the `name` of every pack
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
  its offering entry by `descriptorPackId`). `PackPin` does not carry these facts yet: the activation reads that
  would fill them are reserved, and the member is deferred to #359. The additive response properties are graded `ERR` by
  `../compat/severity-levels.txt`, so `info.version` moves `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT` (ADR-0022: a 0.x
  breaking change takes a minor bump and this `### Breaking` entry). No released contract is broken incompatibly:
  no release was cut under any version (C-002), so the compatibility check has no baseline. Scenarios: the new
  `scenarios/composition.feature` drives the eleven codes `GET /offering` raises with a negative control on the same
  fixture shape, plus the diamond, the descriptor without `depends_on` and the settled sibling contest; the raised-codes
  table of `scenarios/errors.feature` lists the eleven codes. Core's seam: a client that reads `GET /offering` must
  tolerate the `dependsOn` member (or move to the new version) and may now meet the eleven refusal codes.

- `CharterRefusalCode`'s `x-refusal` entries may declare a status per operation: `operations.<operationId>.status` is the
  status that one operation answers the code with, in place of the entry's `status` (shape chosen as the smallest that
  keeps one entry per code; the entries without `operations` are unchanged). `pack_reference_pack_missing`,
  `pack_reference_unqualified` and `pack_reference_artifact_missing` declare 422 for `putPackRelationship` and stay 500
  everywhere else. `PUT /packs/{packId}/relationships` now judges its `to` against the compose of the pack it registers
  into, with the one rule the offering applies to what a pack writes, and refuses before any file is written: a
  qualified `to` naming a pack outside the pack's `depends_on` closure (`pack_reference_pack_missing`), a bare `to` that
  only another pack holds (`pack_reference_unqualified`) or a qualified `to` naming an artifact the named pack does not
  hold (`pack_reference_artifact_missing`), each 422 with the `packs` member (the pack registered into first). A `to`
  that no pack holds stays external and is accepted, and a qualified `to` that names another pack of the closure, or
  the pack itself, with the same id as the `from` is legal (it is not a self-loop). The `422` response of the operation
  documents the three codes. Scenario: `scenarios/composition.feature`.

- `GET /offering` and `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` refuse `pack_unreadable` (500) when
  any file of any configured pack is invalid or cannot be read, an asset sidecar whose payload the pack does not hold
  included, not only when a `pack.yaml` is (operator ruling of 2026-11-04): both read the compose, and the compose names every
  configured pack whole, so there is no partial answer. A client that read `GET /offering` against a set of packs one
  of which holds a broken file now meets this refusal; the pack-scoped read of an artifact of that pack still answers.
  Scenario: `scenarios/offering.feature`. Known gap: the pinned built-in pack cannot be served by `GET /offering` until
  spec-kitty-open-packs#75 lands.

- New operation in this release: `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` (`listArtifactReferences`), a composed read: it
  resolves the compose once inside the read and answers the references one artifact writes (`ArtifactReferenceList`),
  each as written (`PackQualifiedReference`), with its relation when the artifact mints one, the `HoldingPack` that
  holds what it names (`packId`, `descriptorPackId`, `name`), the bare `urn` it resolves to and an `artifact` link to the
  pack-scoped read of the target. A bare reference is held by the pack that writes it, which shadows a same-named
  artifact of another pack. A compose the resolver cannot serve refuses it with the same 500 composition code and
  `packs` as `GET /offering`; the pack-scoped read of the artifact still answers. The pack, the kind and the artifact
  are judged as for the artifact read (404). New relation key `artifact`. Scenarios: `scenarios/composition.feature`.

