---
title: "ADR-0082: The domain owns every graph walk, and ports transform and interface"
description: "One domain-driven walk that outer layers narrow by a scope, rules chosen by a predicate, one Rationale and a static rule factory; ports transform and interface."
doc_status: active
updated: 2026-11-21T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-21'
type: ADR
kind: ADR
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0006-onion-vertical-slices-archunit
  - adr/0081-pack-uninstall-and-the-version-order
  - adr/0057-performance-budgets-never-block-a-pull-request
  - architecture/architectural-patterns-applied
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you write a new walk over packs or artifacts, add a pack check, or add a port that judges a pack.
  - profile: core-maintainer
    guidance_text: Read this for where the pack rules live and why the API, pack-check and the doctor share one walk.
---

# ADR-0082: The domain owns every graph walk, and ports transform and interface

## Status

**Accepted**, 2026-11-21. Decider: Stijn Dejongh (operator). The operator gave the steer on 2026-11-21, while
[PR #618](https://github.com/spec-kitty/spec-kitty-charter/pull/618) (ADR-0075 slice 7) was landing. That PR builds
three judging ports and flags an ambiguity (its own label for it is S1; it is not ADR-0075's ruling S1) against ruling R3 of ADR-0075 ("the rules stay in the packs domain,
with no judging port"). A design squad then read the code and proposed a target design.

The decision below records the steer. The type design and the migration slices on
[Architectural patterns applied](/architecture/architectural-patterns-applied/)
are **proposed and pending operator approval**. They are not part of this decision.

**Relation to ADR-0075.** This ADR confirms ruling R3 of
[ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/)'s 2026-11-16
amendment, as S1 to S3 and S6 left it, and extends it to every graph walk. It supersedes nothing in ADR-0075.
Decision 6's judging ports were already replaced by that amendment; PR #618 re-introduces them, and this ADR resolves
that against R3.

## Context

The service walks the pack graph in many places. The walk down `depends_on` and `parent_pack` is written five times,
four of them in `packs.application`. Other walks over references, step links and relationships are written by hand
too. Cycle detection lives in application code. The results come back in several shapes beside the kernel's
`Rationale`.

ADR-0075 decision 6 put the closure checks behind ports (`ClosureWalk`, `ImpactCheck`, `MutabilityCheck`). Ruling R3
of its 2026-11-16 amendment replaced them: the rules stay in the packs domain, with no judging port. PR #618 builds the
three ports again and asks which one holds.

## Decision

### 1. The domain owns how any walk is done

The packs domain decides which edges a walk follows, in which order, how a cycle is handled, and how a closure is
checked. No application service, port or adapter writes its own walk over packs or artifacts.

### 2. One walking mechanism, driven by the domain, constrained through a scope

There is one walking mechanism, a Visitor over the graph. The driver lives in the domain; the kernel holds only the
visitor contract (`GraphWalker`, `Descent`). Rules plug in as visitors and never drive. The driver's name and shape
are part of the proposed type design.

An outer layer constrains the walk only by handing it a scope, an active set or a determined selection. For the walk,
the scope is nothing but a selection filter. The domain alone decides which edges are followed. The scope's exact
shape is part of the proposed type design.

### 3. Validators run on the elements a predicate selects

A walk is given rules and a predicate per rule. When the predicate holds for the element being visited, the rule
runs on it.

### 4. One accumulating rationale

A walk keeps one ledger of every finding: its severity, its message and the identifier of the element it came from.
The ledger is the kernel's `Rationale`.

### 5. A public static rule factory

The domain exposes its walkers, and a public static rule factory built on generic functional interfaces such as
`Predicate<T>`. The API, pack-check and the planned doctor CLI import them. They do not hold rules of their own.

### 6. Ports transform formats and interface with the user

A port or adapter does two things only:

- format transformation, from a storage format into the canonical domain shape and from a `Rationale` to a wire
  shape;
- interfacing with the user: the API, the CLIs and the runners.

### 7. PR #618's flagged ambiguity is resolved in principle

A judging port is not where a rule lives. R3 holds, as S1 to S3 and S6 left it: the rules stay in the packs domain,
and the only domain port is a reading port (`PackReading`). The three ports of PR #618 are, in principle, replaced by
domain rules run by the one walk. How and when is a migration question; the proposed slices are on the architecture
page and wait for the operator.

## Consequences

### Positive

- One walk to read, test and make fast, instead of a dozen.
- The API, pack-check and the doctor answer from the same rules, so they cannot drift.
- A new check is a rule and a scope, not a new walk.

### Negative

- Code that walks or judges is expected to move into `packs.domain`; the slices are proposed.
- PR #618's ports would be short-lived.

### Risks

- A walker that grows a parameter per caller becomes a god object. The architecture page lists the guards.
- The order of findings decides the refusal code. The one walk must keep today's order.

## Alternatives considered

### Keep the judging ports of PR #618

Not chosen: each port hides a walk of its own, and rules end up outside the domain, against R3.

## References

- [Architectural patterns applied](/architecture/architectural-patterns-applied/): the proposed type design, the
  checks as walker, scope and rules, and the migration slices.
- [ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/),
  [ADR-0006](/adr/0006-onion-vertical-slices-archunit/)
