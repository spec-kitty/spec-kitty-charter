# Project Charter — curated companion

<!--
This file is a hand-authored, display-only companion to the authoritative
`.kittify/charter/charter.yaml`. It holds the human *rationale* behind your
project's governance and doctrine.

It is NEVER a charter-resolving input, and `charter generate` NEVER overwrites
it. Keep it in step with charter.yaml's governance and activation sections.
-->

Interviewed with the operator on 2026-10-18.

## What this repository is for

The Java 25 charter offering service that replaces core's Python charter by rewrite
(ADR-0008), and the charter contract it serves (`contracts/charter/`, ADR-0002). Core's
Python charter stays in core as the oracle; core vendors the contract and generates its
DTOs from it.

## What must never go wrong

1. **Divergence from the oracle.** The service may change the wire format, but the
   governance content it answers must match the Python charter closely enough that
   core does not break.
2. **Breaking the OpenAPI contract.** The service honours the contract, and no
   incompatible contract change reaches core.

Also: open-packs YAML always round-trips exactly, and no unverified dependency enters
the build. None of these is traded for speed, and no exception is granted for the first
two.

## How work lands

- Non-trivial work runs as a spec-kitty mission; a small ad-hoc fix runs as an Op. Both
  land by pull request.
- Contract, architecture and domain changes get an **adversarial review squad** at the
  plan point and before merge, then operator approval.
- A call made without review is logged as an open review item, never left implicit.

## Gates before merge

- `./gradlew verifyAll` on **Java 25**, with strict SHA-256 dependency verification: contract
  validation and generation, unit tests, the ArchUnit gate, the BDD scenarios and the
  open-packs round-trip. CI never builds with `-Pcharter.javaVersion=21`.
- A **Java style check** on hand-written sources. It is not yet wired into `verifyAll`;
  until it is, reviewers apply the `java-conventions` styleguide.
- The docs checks: `check_docs.py` (all eight), markdownlint-cli2 and cspell.

## Practices in force

- **BDD / specification by example:** scenarios first; `contracts/charter/scenarios/`
  drives the implementation.
- **Test first**, with tests held to the same quality bar as production code: no fake
  green, outcome assertions, tests as scaffold rather than friction.
- **Domain-driven design** in core's charter vocabulary.
- **C4** levels for architecture documentation.
- **Boy-scout rule**, reconciled with smallest-viable-diff by the
  change-scope-tensions directive.
- Architectural integrity, decision documentation, specification fidelity, conceptual
  alignment, living documentation and supply-chain safety.

Everything else in the offering stays available, but not in force.

## Known gaps

- The internal org pack requires glossary pack `spk-internal-glossary`, but the vendored
  pack's file stem is `spk-internal`. It is activated under the stem until the pack is
  fixed.
- `charter consistency-check` reports the two activated glossary packs as missing from
  the compiled catalog: `generate` does not compile glossary packs.
