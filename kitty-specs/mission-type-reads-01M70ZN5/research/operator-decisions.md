# Operator decisions taken during grounding (2026-11-09)

These come on top of the five binding decisions in the dispatch brief.

## D6: module placement

The missions slice's new rings live in a new Gradle module, `service/missions-edge` (`:missions-edge`). The module
holds `missions.application`, `missions.infra.api`, `missions.infra.packs` and `missions.infra.activations`, and
depends on `:missions`, `:packs`, `:activations`, `:charter-api` and `:web-transport`. `:missions` stays the pure
domain module that `:packs` binds. The packages stay `ai.speckitty.charter.missions.*`; ArchUnit rules gate the new
rings, and an ADR-0006/0037 amendment records the edge-module shape.

Reason: `:packs` api-depends on `:missions`, so putting the rings in `:missions` would create a project cycle. The
operator first accepted `:missions-service`, then ruled on the profiles mission's escalation (`:profiles-edge`, relayed
by the orchestrator on 2026-11-09) that missions mirrors it as `:missions-edge`. The profiles PR lands the amendment
and the rule shapes first; this mission follows that pattern.

## D7: two meanings of "template"

The stored kind `template` covers two different concepts (operator):

- a **skeleton**: a file under `missions/<type>/templates/`, the starting shape of a document a mission's step
  produces. Served on the wire as a sub-resource of its mission type under the name `skeletons`;
- a **generic asset template**: a file under the pack's top-level `templates/`. Out of scope for this run; it belongs
  with the asset reads.

The stored kind and the URN (`template:<type>/<path>`) do not change. Renaming them is a packs concern.

## D8: skeleton addressing

- The home of a skeleton is its mission type: `GET /packs/{packId}/artifacts/mission_type/{type}/skeletons/{name}`.
  The same shape applies under an activation. *Superseded in part by D9: `{name}` is one path segment.*
- The mission type read lists all of its skeletons.
- The step read links each expected output artifact that has a `template_path` to its skeleton. Skeletons that prompt
  prose names, or that nothing references, are not linked from a step.
- Several files under `missions/<type>/templates/` are really generic asset templates (`divio/*`, `generators/*`,
  `release-template.md`), and some skeletons are named only in prose. The service serves what is stored. The pack
  structure fix goes to open-packs (spec-kitty/spec-kitty-open-packs#180).

## D9: a skeleton is addressed by a single-segment name (plan-point squad, 2026-11-09)

The plan-point squad found that a multi-segment path variable would need changes to the contract harness
(`ContractConformance`, `LinkChecks`, `OperationCoverage`), to `Link.yaml`'s href rule and to Tomcat's handling of
`%2F` and `..`. The operator chose a **single-segment name based on the skeleton's id** instead.

Rule (this mission's spelling of the operator's ruling): a skeleton's name is its id with the `<type>/` prefix removed,
escaped as in RFC 6901: `~` becomes `~0` and then `/` becomes `~1`. The mapping is one-to-one and reversible.
`software-dev/spec-template.md` is named `spec-template.md`; `documentation/divio/howto-template.md` is named
`divio~1howto-template.md`. A name that does not decode to a skeleton of the type is `skeleton_not_found`.

## D10: DELETE of a mission type cascades its skeletons (operator, 2026-11-09)

`DELETE` of a mission type removes the type, its step prompt and guidelines companions and its skeletons as one removal,
with one impact check. It is refused with `409` while a step contract binds the type or a graph edge names the type
or one of its `action:<type>/<step>` nodes, as for any referrer of the five kinds. `DELETE` of a step contract follows
the five kinds' rules unchanged.

## D11: a literal step contract read under an activation (pre-PR squad, 2026-11-10)

`getEffectiveMissionStepContract`, `GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}`, answers
the layer-merge winner's body in the `Artifact` envelope. Its `mission-type`, `step` and delegation-candidate links stay
under the activation. It is a literal route like `getEffectiveMissionType`, so a step's `step-contract` link never leaves
the activation for a copy that lost the merge (spec US4 AS2, as written).

## D12: the missions read model leaves the activations slice (pre-PR squad, 2026-11-10)

The activations slice answers only what it owns: the winning artifact, the pack that holds it, the merged step contracts
bound to the type, and the source fingerprint. `:missions-edge` composes that with the pack-scoped holding read and
refuses `stale_input` when the two fingerprints differ. `HeldMissionType`, `HeldBytes`, `EffectiveMissionTypeAnswer`, the
default `PacksSession.missionType`, `Holdings` and the second `PacksAvailability` constructor go. This follows ADR-0037's
slice-edge-module amendment.
