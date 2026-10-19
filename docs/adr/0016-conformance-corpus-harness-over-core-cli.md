---
title: "ADR-0016: The conformance corpus is a harness over core's CLI, on open-packs and fixture projects"
description: Corpus inputs come from open-packs and small fixture projects; expected answers come from core's existing CLI, mapped onto the contract shape.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - plans/roadmap
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what a corpus case is, where its expected answer comes from and how a deliberate difference from core is recorded.
  - profile: core-maintainer
    guidance_text: Read this for why core needs no new export code, and which CLI outputs the harness relies on.
  - profile: evaluator
    guidance_text: Read this for the evidence behind the switch-over gate.
---

# ADR-0016: The conformance corpus is a harness over core's CLI, on open-packs and fixture projects

## Status

**Accepted (provisional)** — 2026-10-19. Decider: Stijn Dejongh (operator). Like every decision
taken on 2026-10-19, this one is provisional: it is revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when).

This record clarifies [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)
decision 2. Its decision stands: the corpus's expected results are produced by core's Python
charter. What changes is how. The roadmap's "core-side export script" is replaced by a harness
in this repository over core's existing CLI.

Amended 2026-10-19 by [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/): see
[Amendment](#amendment-2026-10-19).

## Context

[ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) makes the conformance corpus the only
gate for the switch-over. Issue #9 and roadmap step 3 planned a core-side script to export
expected answers. The operator asked whether the corpus is not simply the content of
spec-kitty-open-packs plus the OpenAPI file, with nothing left to export from core.

An investigation on 2026-10-19 (charter `624b606`, core `9554e7b2`, open-packs `7f063ae`) found:

- **Inputs: mostly in open-packs.** The built-in pack with its mission types and actions, four
  org packs with their `required_*` lists (`packs/internal/org-charter.yaml:5-22`) and three
  presets are there. The OpenAPI file fixes the shape of every answer.
- **Missing inputs.** Project packs, activation subsets and deactivations, a charter present or
  absent (which sets core's mode), first-load state and broken packs per refusal code are not in
  open-packs. They have to be written as fixture projects.
- **Expected answers: in neither repository.** The rules that turn packs into an answer live
  only in core's Python:
  - catalog-default seeding and `required_*` (`action_governance_bundle.py:53-91, 173-205`);
  - the activation filter (`drg_activation.py:446-484`);
  - the scope, `requires`, `suggests` and `vocabulary` walk (`offering/drg/query.py:184-244`);
  - dropping artifacts scoped by another action (`context_renderers/delivery_table.py:195-330`);
  - depth from first-load state (`context_state.py:40, 89-109`);
  - an empty answer on a load error (`action_governance_bundle.py:454`);
  - the three-layer merge (`merge.py:959, 1226, 1322`).
- **The gap is large.** For the software-dev plan action over the built-in pack, core's
  `charter context --json` answers 29 directives and 105 tactics. The service's provisional rule
  over the same data gives 9 artifacts, 3 of them directives.

Hand-written answers would re-derive those rules on paper. That risks the charter's first
"must never go wrong", divergence from the oracle, on the one gate the switch-over has.

## Decision

1. **Inputs.** A corpus case is a pack set, a project configuration, a charter state and a load
   history. Packs come from spec-kitty-open-packs at a pinned commit. A small set of fixture
   projects in this repository supplies what open-packs lacks.
2. **Expected answers come from core's existing CLI.** A harness in this repository runs
   `charter context --json` (with and without `--mark-loaded`), `charter pack list --json` and
   `doctor charter-packs --json` over each case. No new export code is written in core.
3. **One mapping.** The harness maps core's output onto the contract's shape: ids to URNs, core's
   errors to the contract's refusal codes. The seam and the shadow comparison reuse the same
   mapping.
4. **Core is the oracle for governance content, not for shapes.** This repository mints its own
   contract ([ADR-0002](/adr/0002-contract-source-of-record/)) and owns the pack schemas that
   content is written against ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/),
   decision 5). Where the service answers differently on purpose, the difference goes in a reviewed
   divergence register that the conformance gate (#72) consults, never into a hand-edited expected
   answer. The register starts with the three differences in `contracts/charter/CHANGELOG.md`.
5. **The contract scenarios stay the hand-written specification.** The corpus checks the service
   against core; the scenarios state what the contract promises.
6. **Coverage follows the read inventory.** The corpus covers the reads the cutover inventory
   (#94) decides, for every mission type and action, at both depths. *(Amended 2026-10-19: at depths 0, 1 and 2.)*

## Consequences

### Positive

- No core change is needed to produce the corpus. Open-packs' own CI already drives core's CLI
  this way (`scripts/ci/validate-packs.sh:29-72`).
- The mapping is written once and serves the corpus, the seam and the shadow.
- Every deliberate improvement on core is visible and reviewed in one register.

### Negative

- The harness depends on core's CLI output, which is not a published contract. A change there
  can break the harness.
- The fixture projects are authored here and must be kept in step with the inputs core reads.

### Risks

- A rule core applies without exposing it in the CLI output stays invisible. Mitigation: the
  fixture projects vary every input the investigation listed, and the shadow comparison (#14)
  covers real use.

## Alternatives considered

### Hand-written expected answers from the scenarios and open-packs data

No dependency on core, but it re-derives core's merge, activation and walk rules on paper. Not
chosen: it puts the oracle's behavior into prose on the only switch-over gate.

### A new export script in core

The previous plan. Not chosen: core's CLI already emits the answers, and an export script adds
core code that only this repository uses.

## Revisit when

- Core's CLI output changes shape often enough that the harness breaks repeatedly.
- The divergence register grows large enough that core is no longer a useful oracle for a read.
- The Python reads are retired (stage 4); the corpus then becomes the regression suite.

## Requests to core

- Keep the `--json` output of `charter context`, `charter pack list` and `doctor charter-packs`
  stable, or record a change in core's changelog so the harness can follow.

## References

- Issue #9 (the corpus), #72 (conformance as a blocking gate, which consults the divergence
  register), #94 (the read inventory).
- [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/),
  [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/),
  [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/).

## Amendment (2026-10-19)

[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) changes one point of decision
6. The corpus covers the reads "at depths 0, 1 and 2", not "at both depths": `depth` counts
`suggests` hops, and the seam maps a first load to 2, a recorded load to 1 and the public bundle
read to 0. The register that decision 4 describes now exists as the
[divergence register](/api/divergence-register/), with stable ids; its machine-readable format
stays with #72. The original decision text above is unchanged.
