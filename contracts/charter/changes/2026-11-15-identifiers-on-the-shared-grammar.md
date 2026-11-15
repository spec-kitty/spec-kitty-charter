### Breaking

- Every identifier is on the shared kernel grammar (ADR-0075 decision 2, operator ruling "everything strict", #552):
  a doubled hyphen (`a--b`), a trailing hyphen (`a-`) and a leading hyphen are refused where they were accepted.
  - **The pack id** is a kebab slug of at most 63 characters, `^[a-z0-9]+(-[a-z0-9]+)*$` with `maxLength: 63`, a
    digit first still allowed: the parameter `PackId`, the `packId` path parameters of `packs_packId` and
    `packs_packId_doc`, and `PackSource.packId`. A registration under such an id answers `400` without a code before
    the body is read (`whole-pack-registration.feature` gains the `acme--x` and `acme-` rows); a
    `registerPackFromSource` body that names one breaks the schema of `PackSource.packId`.
  - **A role** is a letter-first kebab slug of at most 64 characters, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`:
    `AgentProfile.role` and `altRoles`, `ProcedureStep.role` (now also `maxLength: 64`), and `MissionStep.role`,
    `ProfileSelectionRead.role` and the `role` path parameter of `selectAgentProfile`, whose patterns still refuse
    the reserved `generic` without a lookahead.
  - **A slot id** is two letter-first kebab slugs joined by a colon, each at most 64 characters: `SlotId` (now with
    `maxLength: 129`).
  - The pack schemas follow (packs contract `0.1.0-SNAPSHOT`, `### Changed`).
  - Core's seam: regenerate the DTOs; a client that sent or stored an id, a role or a slot with a doubled, leading or
    trailing hyphen must rename it. No refusal code is added.
