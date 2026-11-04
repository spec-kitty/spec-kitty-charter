---
title: "ADR-0056: A refusal code may declare a different status for one operation"
description: "Operator ruling of 2026-11-04: x-refusal gives a code one status and may override it per operation, so the reference refusals are 422 on registering a relationship."
doc_status: active
updated: 2026-11-04T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-04'
type: ADR
kind: ADR
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0022-contract-versions-start-at-0-x
  - development/http-status-codes
  - guides/compose-packs
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a refusal code or changing the status an operation answers it with.
  - profile: core-maintainer
    guidance_text: Read this to see which refusals change status by operation, and how the contract declares it.
  - profile: pack-author
    guidance_text: Read this to know why registering a relationship answers 422 where the offering answers 500 for the same code.
---

# ADR-0056: A refusal code may declare a different status for one operation

## Status

**Accepted**, 2026-11-04. Decider: Stijn Dejongh (operator). The operator ruled this point on 2026-11-04, while landing
pull request [#365](https://github.com/spec-kitty/spec-kitty-charter/pull/365) (pack composition).

The decision extends the `x-refusal` map of `CharterRefusalCode`, which until now gave every code exactly one status.
It does not change any status a read answers.

## Context

The three reference codes of the pack composition, `pack_reference_pack_missing`, `pack_reference_unqualified` and
`pack_reference_artifact_missing`, are 500 like `pack_unreadable`: on `GET /offering` and on the references read, the
request is fine and the configured packs do not compose, so the same read is refused the same way until a pack is
fixed.

Registering a relationship (`PUT /packs/{packId}/relationships`) judges something else with the same rules: the
request's own `to`. A `to` that names a pack outside the pack's `depends_on` closure, a bare `to` that only another
pack holds, or a qualified `to` naming an artifact the named pack does not hold is the caller's mistake, and the
service refuses it before it writes a file. Answering 500 there would tell the caller the service is broken, and a
client that retries or alerts on 5xx would be wrong.

`x-refusal` allowed one status per code, so the contract could say 422 for the registration only by minting three
new codes that differ from the existing ones in nothing but status.

## Decision

A code keeps one status, and an entry of `x-refusal` may carry an `operations` map that overrides it for one
operation:

```yaml
x-refusal:
  pack_reference_pack_missing:
    status: 500
    retryable: false
    raised: true
    operations:
      putPackRelationship:
        status: 422
```

- `status` stays the status every operation answers the code with. `operations.<operationId>.status` is the status
  that one operation answers it with, in place of `status`. The key is the `operationId` of the contract.
- The map holds a status only. `retryable` and `raised` stay per code.
- Only `putPackRelationship` declares one, for the three reference codes above: 422, with the same code, the same
  `packs` member (the pack registered into first) and no file written. Every read keeps 500.

The service reads its status table for the override from the same map, and the contract scenarios hold the two to
each other.

## Consequences

### Positive

- A client branches on the code alone, as before; the status tells it whether the request or the configured packs
  are at fault.
- No new code is minted, so core's seam maps eleven composition codes, not fourteen.
- The shape is additive: entries without `operations` are unchanged.

### Negative

- A code no longer has one status in the contract. A consumer that built a code-to-status table from `status` alone
  must read `operations` as well. The contract fragment records this under `### Breaking` (ADR-0022).

### Risks

- A second override must be declared in the contract and in the service together. The scenarios fail when either
  disagrees with the other, which is the guard.

## Alternatives considered

### Option A: Three new codes for the registration

Not chosen. They would duplicate the existing codes' meaning and make core map the same cause twice.

### Option B: 500 on the registration too

Not chosen. It blames the service for a request the caller got wrong, and a retrying client would repeat it.

## References

- [HTTP status codes](/development/http-status-codes/)
- [Compose packs](/guides/compose-packs/#read-a-refusal)
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)
- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 7
