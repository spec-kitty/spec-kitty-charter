---
title: "ADR-0009: Contract 1.0 reserves what the cutover needs; later capabilities arrive as additive contract sets"
description: Contract 1.0 models the signed impacts relation and reserves refusal codes, load depth and version headers; activation, creed and policy come later as new endpoints.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0002-contract-source-of-record
  - adr/0004-public-api-returns-generated-dtos
  - adr/0007-rest-hateoas-contract-of-record
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - architecture/contract-and-dtos
  - api/compatibility
audience:
  - profile: contract-consumer
    guidance_text: Read this for what 1.0 holds, what it reserves, and how later capabilities reach the contract without breaking you.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a field, enum value or refusal code to the contract.
  - profile: core-maintainer
    guidance_text: Read this for the impacts mapping core's seam does until core migrates its edges.
---

# ADR-0009: Contract 1.0 reserves what the cutover needs; later capabilities arrive as additive contract sets

## Status

**Accepted (provisional)** — 2026-10-19. Decider: Stijn Dejongh (operator).
This decision is provisional: it is revisited as the system evolves, and a later record
supersedes it rather than editing it.

## Context

Contract `1.0.0-SNAPSHOT` (PR #54) is about to be released as the first version core
vendors. Its response schemas are closed. The breaking-change check counts an added response
property as breaking (`contracts/compat/severity-levels.txt`, operator ruling of 2026-10-19),
and an added refusal code as breaking too (`contracts/compat/fixtures/breaking-refusal-code-added/`).
A new path is not breaking. So whatever 1.0 leaves out of an existing response costs a 2.0
later.

Several needs are already known:

- **The `impacts` relation.** Core ADR
  [`2026-07-26-3`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-07-26-3-impacts-edge-subsumes-in-tension-with.md)
  (Accepted) replaces `in_tension_with` with a signed, authored `impacts` edge. PR #54's
  closed `Relation` enum still lists `in_tension_with` and has no `impacts`
  (`contracts/charter/schemas/Relation.yaml`). Core has not migrated its edges yet.
- **Refusals the cutover will raise.** A lower-tier pack that breaks the owning pack's
  policy (epic #55), and inputs that changed on disk while the service answered
  ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)).
- **Seam runtime data.** The seam must pass the context load depth, and check which contract
  version, worktree and input state answered
  ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)). A governance
  fingerprint on context answers lets runtime events cite the governance that applied
  (open questions 9 and 10).
- **Capabilities that are not designed yet.** Activation (with persistent exclusions and the
  cascade's skipped report, epic #56), creed and value sets (epics #16 and #77) and pack
  mutability policy (epic #55).

## Decision

1. **`impacts` is modeled in 1.0.** `Relation` gains `impacts`: a signed, authored edge
   between artifacts, never derived. `in_tension_with` is removed from the enum.
   `reconciles_tension` stays, and targets pairs whose `impacts` is negative. The contract
   leads: core aligns to it at the cutover. Until core's migration (B2) lands, core's seam
   maps every `in_tension_with` edge to `impacts` with a negative sign.
2. **Two refusal codes are reserved:** `governance_policy_violation` and `stale_input`.
   Neither is raised until its behavior lands, but clients must handle both from 1.0.
3. **The seam runtime surface is reserved:**
   - an integer `depth` request parameter on the context read, echoed in the response;
   - an info operation that returns the contract version, the service version, the worktree
     identity and the current input fingerprint;
   - the response headers `Charter-Contract-Version`, `Charter-Worktree` and
     `Charter-Source-Fingerprint`;
   - a governance fingerprint on context responses.
4. **Later capabilities arrive as additive contract sets.** Activation (including exclusions
   and the skipped report from its first version), creed and value sets, and pack policy
   levels and findings each arrive as new operations or a new contract set. Existing
   responses do not grow. A client opts in by calling the new operations. Adding operations
   to an existing contract set this way is not breaking.

## Consequences

### Positive

- The cutover needs no 2.0: everything it raises or reads is in 1.0.
- Core's seam regenerates its DTOs once, and later capabilities never change a response it
  already parses.
- The contract follows core's accepted vocabulary (`impacts`) instead of the edge type core is
  retiring.

### Negative

- 1.0 lists codes, headers and a parameter that nothing uses yet. Each needs a contract
  scenario and a `CHANGELOG.md` line so readers know it is reserved.
- Core's seam carries a mapping from `in_tension_with` to negative `impacts` until core's B2
  lands.
- New capabilities cannot enrich existing answers. A client that wants activation data on a
  context answer makes a second call.

### Risks

- A reservation can be shaped wrong before its behavior is designed. Mitigation: reserve only
  names and types, never structures.
- The `in_tension_with` mapping could lose information if an edge carries more than
  "these conflict". Mitigation: the conformance corpus pins the mapped answers.

## Alternatives considered

### Keep 1.0 identical to core today

No reservations; mirror `in_tension_with`. Not chosen: every one of these needs would become
a 2.0 within the cutover milestone.

### Reserve `impacts` but keep `in_tension_with` as deprecated

No mapping in core. Not chosen: removing it later is itself a 2.0, and the contract would
model an edge type core has already ruled out.

### Placeholder fields for activation on existing responses

Not chosen: the fields would be shaped before the activation model is designed, and closed
schemas make a wrong guess permanent.

### Open the `Relation` enum

Not chosen: it gives up validation and the closed-schema rule that PR #54 adopted.

## Revisit when

- Core's B2 migration lands. The seam mapping is then removed.
- A reserved name gets its first behavior and the shape turns out wrong before any client
  relies on it.
- A shared or hosted service (#24, #44) must serve several core versions at once. Closed
  schemas and additive-only growth may then need a versioning scheme per contract set.
- Core asks for capability data inline on existing answers, and the second call proves too
  costly.

## Requests to core

- **Seam mapping.** Until B2 lands, `charter.offering.api` maps each `in_tension_with` edge
  to `impacts` with a negative sign when it builds DTOs from the contract.
- **Glossary.** Mark `in_tension_with` as retired from the external contract, pointing at
  `impacts`.

## References

- [ADR-0002](/adr/0002-contract-source-of-record/): this repository is the source of record
  for the contract
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/): the runtime behavior
  behind the reserved codes, parameter and headers
- [Contract and DTOs](/architecture/contract-and-dtos/), [Compatibility](/api/compatibility/)
- Core ADR [`2026-07-26-3`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-07-26-3-impacts-edge-subsumes-in-tension-with.md)
- Epics #16, #55, #56 and #77
