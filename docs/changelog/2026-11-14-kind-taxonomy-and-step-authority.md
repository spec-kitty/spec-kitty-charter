---
okf_version: "0.2"
title: The kind taxonomy is served, and the step is the one authoring surface
description: Slice F serves the kind taxonomy, folds the step contract into the mission-type step, types and refuses each relation's endpoint kinds, and retires four old shapes.
doc_status: active
updated: 2026-11-14T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0077-kind-taxonomy-and-step-authority
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what slice F changed in the model, the contract and the pack schemas.
  - profile: pack-author
    guidance_text: Read this for where substeps are authored now, which edges each relation may draw, and what is refused.
  - profile: core-maintainer
    guidance_text: Read this for the new kind taxonomy read and the breaking contract entries core's generated client meets.
---

# 2026-11-14: The kind taxonomy is served, and the step is the one authoring surface

Slice F ([#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537)), recorded in
[ADR-0077](/adr/0077-kind-taxonomy-and-step-authority/). The contract changes are in the change fragments of
`contracts/charter/changes/`.

- **The kind taxonomy** (R18, #112): `getKindTaxonomy`, `GET /offering/kinds`, serves every kind and every relation
  with the kinds it may connect. The root and the offering link it as `kinds`.
- **Substeps on the step** (#514; operator ruling `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`): a mission-type step authors its `substeps`, with every field a step contract
  held. A delegation candidate needs a `scope` edge from the step's action.
- **`mission_step_contract` is retired** (#514, #533): its operation, schemas, enum values, refusal code, link and pack
  schema are gone, and its files are refused as retired file kinds. The `review` gate is the guard
  `spec-kitty-pre-review` on the work-package lifecycle.
- **Every step is linked from its type** (operator ruling `DM-01M7B1YQR5ZKQPDW6DX64KPNRN`): one authored `requires` edge per step, on-demand steps included
  (`mission_step_unlinked`).
- **Endpoint kinds** (#515, #350): each relation names the kinds it may join, typed in the kernel and refused at read
  and at write (`relationship_endpoint_not_allowed`).
- **Retired:** the relations `impacts`, `replaces` and `delegates_to`, the `impacts` edge strength, and `template_set`
  (#520).
- **Breaking** for core's generated client and for spec-kitty-open-packs, which follows the model from the mission's
  drafts.
