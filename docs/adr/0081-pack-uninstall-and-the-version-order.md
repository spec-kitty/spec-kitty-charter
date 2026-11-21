---
title: "ADR-0081: A registered pack is removed whole, its writes have a dry run, and a replacement moves its version forward"
description: "#517: DELETE /packs/{packId} with a dependents check, a dry run of the delete and of both registrations, and a PEP 440 version order on a replace. Amends ADR-0065 D2, D4 and D9."
doc_status: active
updated: 2026-11-20T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-19'
type: ADR
kind: ADR
related:
  - adr/0065-pack-registration-replaces-the-whole-pack
  - adr/0022-contract-versions-start-at-0-x
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
audience:
  - profile: contract-consumer
    guidance_text: Read this for how a pack is uninstalled, what refuses it, what a dry run answers, and which pack_version a replacement may carry.
  - profile: charter-service-contributor
    guidance_text: Read this before you change the pack removal, the dependents check, the dry run or the version rule of a registration.
  - profile: core-maintainer
    guidance_text: Read this for the service operations core's org-pack lifecycle calls at incorporation.
---

# ADR-0081: A registered pack is removed whole, its writes have a dry run, and a replacement moves its version forward

## Status

**Accepted**, 2026-11-19. Decider: Stijn Dejongh (operator). The operator ruled D1 to D10 of issue
[#517](https://github.com/spec-kitty/spec-kitty-charter/issues/517) through the orchestrating session, each a Decision
Moment of Mission `pack-delete-and-update-guard-01M7SANQ` (`kitty-specs/pack-delete-and-update-guard-01M7SANQ/decisions/`).
Decision 2 reverses [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) D2 and its ruling B1
(`DM-01M6RA9BKJY7X9BFV93QW1KM1T`); the operator acknowledged the reversal.

The design calls of the Mission's plan (P1 to P10) were made without a ruling and are open review items for the
pre-merge review: the shape change of the activations port, the dry run's answer shapes, and the refusal of a linked
pack.

## Context

An external org pack can be registered (`POST /packs`, `PUT /packs/{packId}`) and replaced, but it cannot be removed,
and a replacement is not checked against what it replaces: any version replaces, an older one included. Core's org-pack
lifecycle (an org architecture pack, D06 of spec-kitty#5243) needs a clean install, an authenticated update and
an uninstall, and a way to see what each would do before it is done.

The service already finds every closure that holds a pack (`ClosureRoots`, the reverse `depends_on` and lineage index
and the activations' root sets) and every artifact of another pack that refers to one of its artifacts
(`InboundReferences`). The ADR-0075 slice that puts these checks behind ports (#556) has not started.

## Decision

### 1. The dependents check is built now, behind a port

`PackDependents` (packs application) finds a pack's direct dependents over the existing finders. #556's `ImpactCheck`
implements or absorbs it later; this decision adds no rule, no fault collection and no parity test of #556.

*Note 2026-11-20 ([#556](https://github.com/spec-kitty/spec-kitty-charter/issues/556)):* `ImpactCheck` absorbed it.
`PackDependents` and `ClosurePackDependents` are deleted; the finders moved into the impact check
(`ClosureImpactCheck`, `RemovalDependents`), which judges a removal's dependents first, as one
`409 pack_has_dependents` naming every dependent, and composes nothing when there is one. The refusal and the dry run
answer as before.

### 2. A replacement moves the version forward (amends ADR-0065 D2)

- A registration of the stored content (the same revision) is `unchanged`, whatever its version says.
- Otherwise a replacement's `pack_version` must be newer than the stored pack's by PEP 440 public-version precedence
  (`Pep440Range.compareVersions`, the order the `depends_on` ranges use). A version that is older or equal, the same
  version included, is refused `409 pack_version_conflict`; the detail names both versions. No new code is added: the
  conflict is the same one, a version that cannot name this content.
- A local segment (`+local`) does not order, so `1.0+a` to `1.0+b` with other content is refused.
- **Order of checks.** A label that already names other content (the stored version, or one in the history) is judged
  before `If-Match` and `If-None-Match`, as ADR-0065 D2 has it. That the version moves forward is judged after them,
  so a client that named what it replaces, and lost a race to another writer, reads its `412`.
- `pack_version` is already a PEP 440 version at read (`pack_version_invalid`), so the two versions always order.
- The rule applies to `PUT /packs/{packId}` and `POST /packs` alike.

### 3. (Withdrawn)

A non-PEP 440 version never reaches the rule: the read refuses it.

### 4. The from-version is reported, and If-Match guards it

A replace reports the version it moved from as `PackRegistration.previousVersion`. The request names no new header:
`If-Match` on the pack revision already pins the state a client moves from, exactly.

### 5. A dry run is a flag on the write

`?dryRun=true` on `DELETE /packs/{packId}`, `PUT /packs/{packId}` and `POST /packs` runs the write's own code, through
every check and under the same locks, with the commit switched off: nothing is stored, no history entry is written and
the attempt is not audited. A dry run the worktree guard refuses (`401`, `403`) is audited as every refused attempt is,
so the flag cannot hide a probe.

- The registrations stay asynchronous: the job ends `succeeded` with the outcome it would have had and
  `dryRun: true`, or `refused` with the same problem.
- The delete answers `200 PackImpact` for a judgement (`deleted`, or `refused` with the `409 pack_has_dependents` or
  `422` the delete answers). An answer decided before the judgement or by infrastructure (`503`, `404`, `428`,
  `409 registration_conflict`, `412`, `500`) keeps its status.

### 6. Only an activation that names the pack is a dependent

An activation whose `packs` list names the pack is a dependent. An activation that lists no packs roots at every
configured pack; it is not a dependent, or no pack could ever be removed while one exists. Its closure is composed
without the pack, strictly, and a closure that no longer composes refuses the delete `422`, as an artifact `DELETE`
does. A selection it holds of one of the removed pack's artifacts reads as stale and is dropped on the next write, as
after an artifact `DELETE` (ADR-0065 D4).

### 7. `409 pack_has_dependents` names every dependent

The dependents are the packs whose `depends_on` or `parent_pack` names the pack, the activations of decision 6, and the
artifacts of other packs that refer to one of its artifacts. The refusal has the shape of `artifact_referenced`: a
`packs` member naming the dependent packs, and violations naming every dependent (`depends_on`, `parent_pack`,
`activation`, `artifact_reference`), at most 100, the detail counting the rest. A configured pack whose descriptor cannot
be read cannot be judged and refuses the delete `500 pack_unreadable`: a dependent that cannot be read never lets a
delete through. The removed pack's own `pack.yaml` need not be readable: such a pack can still be deleted, and is judged
by its configured name, the name an activation and a qualified reference name it by. Its `pack_id` is unknown, so a
pack whose `depends_on` or `parent_pack` names it is not found; that pack's closure already holds a pack that cannot be
read, so the delete takes nothing from a closure that composed. The dry run's `PackImpact` then has no `version`. A
delete is how an operator clears a pack that no longer reads.

### 8. The removal (amends ADR-0065 D4)

`DELETE /packs/{packId}` is a third `DELETE`, synchronous like the other two:

- `503 pack_source_unavailable` while registration is off, before anything is judged;
- `404 pack_not_found` for an identifier no configured pack matches, so a repeated delete changes nothing;
- `If-Match` required (`428` absent, `412` stale, `*` accepted);
- `409 registration_conflict` for a built-in pack, a pack in a read-only location, or a pack whose entry is a link (the
  service never deletes outside its writable root; the operator removes the link);
- then the dependents (decision 7) and the strict compose (decision 6).

It resolves the pack under the registry lock and takes the resolved directory's lock (D12), hides any stale aside copy,
and renames the live directory straight to a hidden discard name in one step under the write generation's bracket, so
a concurrent read sees the pack or no pack, and the crash repair of #281 has nothing to restore. Then it deletes the
discarded tree.

### 9. The history survives, for label reuse only (amends ADR-0065 D9)

A pack `DELETE` keeps the version history, after healing the stored pair into it. After a delete there is no stored
version to be newer than: a re-install may take any version the history does not hold with other content. Labels in the
history are matched by PEP 440 equality, so `1.1`, `1.1.0` and `1.1.0+x` name one label.

### 10. Records

This ADR amends ADR-0065 D2, D4 and D9; DIV-602 is rewritten; the contract change is a `### Breaking` fragment
(ADR-0022, 0.x).

## Consequences

### Positive

- A pack can be uninstalled, and nothing that depends on it is left broken.
- A client sees what a delete, a replace or an install would do before doing it, by the same code.
- A replacement cannot silently move a pack back to older content.

### Negative

- **Breaking for clients that went back.** A registration that moved a pack to an older version now answers `409`.
  Delete the pack and register the older version, or publish the older content under a newer version.
- **Activations port.** `ActivationImpact.activationRoots()` now carries the activation id with each root set.

### Risks

- A registration queued before a delete, with no `If-Match`, re-creates the pack after the delete. `If-Match` or
  `If-None-Match: *` is the client's guard; a dry run cannot predict a write that was not yet judged.
- A dry run takes the registry lock like the write, so dry runs in a burst can make writers wait into `503`.
- An activation's `PackPin.version` can pin a version a forward replace then moves past; that is not new.

## Alternatives considered

### Wait for #556 (D1 (b))

Not chosen: P1 work blocked on a slice nobody had started, while the finders it needs exist.

### A new code `pack_version_not_newer` (D2)

Not chosen: the conflict is the one `pack_version_conflict` already names, a version that cannot name this content.

### A `Charter-Pack-From-Version` header (D4)

Not chosen: `If-Match` already pins the from-state exactly; a version header would be a second, weaker precondition.

### Separate impact reads (D5)

Not chosen: a read beside the write keeps its own copy of the checks, and the dry run would drift from the write.

### Any activation whose closure holds the pack is a dependent (D6)

Not chosen: one activation that lists no packs would make every pack undeletable.

## References

- [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/), [ADR-0022](/adr/0022-contract-versions-start-at-0-x/),
  [ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)
- `contracts/charter/paths/packs_packId.yaml` (`deletePack`), `contracts/charter/schemas/PackImpact.yaml`,
  `contracts/charter/parameters/DryRun.yaml`
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/PackUninstallService.java`,
  `ClosureImpactCheck.java` and `RemovalDependents.java` (which absorbed `PackDependents.java`, #556),
  `PackRegistrationService.java`
