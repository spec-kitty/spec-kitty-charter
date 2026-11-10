# Follow-up (draft issue): a whole-pack registration that drops a mission type judges its D10 referrers

Refs #99 (D10, FR-010).

## Why

The `DELETE` of a mission type is refused `409 artifact_referenced` while a step contract binds the type (its
`mission` names it) or a graph edge names one of its action nodes `action:<type>/<step>`
(`InboundReferences.scanRemoval`, mission-type-reads WP07). A whole-pack registration (`registerPackFromArchive`,
`registerPackFromSource`) that replaces a pack and drops the same mission type is judged by
`PackRegistrationService.requireNoInboundReference`, which calls `InboundReferences.scan`. That scan does not count
bindings or action-node edges, so the replace is accepted. The two ways of removing one mission type then disagree,
and a step contract of a pack that builds on the replaced one is left bound to a type that is gone (it drops out of
force without a refusal, `InForceResolution.bindingOut`).

## What

- `requireNoInboundReference` judges the dropped artifacts with the removal rules: bindings (`PackCompositionFacts
  .bindings()`) and edges on the action nodes of a dropped mission type count as referrers, as `scanRemoval` does.
  Either call `scanRemoval` there, or fold the two scans into one and delete `scan`'s narrower variant.
- A step contract the same upload keeps but whose type it drops is a referrer from the staged pack itself; one the
  upload drops too is not (the source-removed rule).
- The contract text of the registration's `409 artifact_referenced` names the new referrers, and a `### Breaking`
  fragment says a replace that used to be accepted is now refused (ADR-0022).

## Scenarios to add (`registration/whole-pack-registration.feature`)

1. A replace that drops a mission type while another pack's step contract binds it is refused `409
   artifact_referenced`, naming the contract and its pack; the stored pack is unchanged. Control: the same replace
   after the other pack's contract is removed is accepted.
2. A replace that drops a mission type and keeps its own step contract bound to it is refused; one that drops both
   is accepted.
3. A replace that drops a mission type while another pack's registered edge names `action:<type>/<step>` is refused;
   an edge on another type's action node does not block.
