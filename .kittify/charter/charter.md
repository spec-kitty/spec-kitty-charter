# Project Charter — curated companion

<!--
This file is a hand-authored, display-only companion to the authoritative
`.kittify/charter/charter.yaml`. It holds the human *rationale* behind your
project's governance and doctrine.

It is NEVER a charter-resolving input, and `charter generate` NEVER overwrites
it. Keep it in step with charter.yaml's governance and activation sections.
-->

Interviewed with the operator on 2026-10-18. Amended 2026-10-27 (operator): the round-trip
rule of PROJECT_002 reads as equality of meaning (ADR-0036). Amended 2026-10-28: rules for
orchestrating cloud sessions, and the rewrite is designed without regard to core. Amended
2026-10-29 (operator): until the service is incorporated into core, PROJECT_002's first two
failures are judged against the charter's own model, contract and packs (epic #186). Amended
2026-11-05 (operator): the whole-pack round trip is a gate before merge (epic #186). Amended
2026-11-10 (operator, charter #456 to #458): the charter's model is the canonical data model, and
legacy shapes get no compatibility. Amended 2026-11-11 (operator, ADR-0070): the round-trip gate
runs over charter's own example packs; spec-kitty-open-packs proves its content against a pinned charter release.
Amended 2026-11-11 (operator, issue triage): rules for filing and closing issues.

## What this repository is for

The Java 25 charter offering service that replaces core's Python charter by rewrite
(ADR-0008), and the charter contract it serves (`contracts/charter/`, ADR-0002). Core's
Python charter stays in core as the oracle; core vendors the contract and generates its
DTOs from it.

## What must never go wrong

1. **Divergence from the charter's own model.** The governance content the service answers
   matches the charter's own model, contract and packs.
2. **Breaking the OpenAPI contract.** The service honors the contract, and contract changes
   follow the contract's own versioning rules (ADR-0022: the 0.x line may break on a minor,
   with a `### Breaking` CHANGELOG entry; no released contract is broken incompatibly).

Until the service is incorporated into core, core is not a consumer of the service or its
contract, so both are judged against the charter's own model, contract and packs, not core's
current behavior (operator ruling, epic #186, 2026-10-28). When the service is incorporated
into core, the oracle and core-compatibility clauses return; that work is planned then.

Also: pack YAML always round-trips without loss of meaning, proven on charter's own example packs
(ADR-0070; ADR-0036: the same
meaning, not the same bytes, so formatting and comments may change), and no unverified
dependency enters the build. None of these is traded for speed, and no exception is granted for the first
two.

## How work lands

- Non-trivial work runs as a spec-kitty mission; a small ad-hoc fix runs as an Op. Both
  land by pull request.
- Contract, architecture and domain changes get an **adversarial review squad** at the
  plan point and before merge, then operator approval.
- A call made without review is logged as an open review item, never left implicit.
- **Issues stay small** (operator, 2026-11-11; the detail is in
  `docs/development/issue-tracker.md`):
  - A squad finding below MEDIUM is folded into the pull request, recorded under its "Not done",
    or dropped, never filed. The boy-scout rule files only defects found outside the Mission's
    domain, not tidy-ups.
  - Every Mission closeout and landing closes the issues it delivered, with evidence, and ticks
    the parent epic.
  - An auto-filed follow-up carries the `follow-up` label and expires after 21 days without a
    milestone or assignee.
  - Pack content is filed in `spec-kitty-open-packs`, and core work is not filed here until
    incorporation.
  - The native issue type is the only kind carrier.

## Designing during the rewrite

While the charter service is rewritten, work is designed against this repository's own model,
contract and packs, as if core did not exist (operator, 2026-10-28; epic #186):

- No compatibility with core's current formats or behavior, and no "core first" ordering.
- No core issues as prerequisites; pack divergences are fixed in `spec-kitty-open-packs`.
- **The charter's model is the canonical data model** (operator, 2026-11-10; charter #456 to #458).
  The shapes packs carry today grew incrementally and were never redesigned; they are not set in
  stone. A change defines the rationalized shape and replaces the old one outright: no deprecation
  window, no reading of the old shape beside the new one. `spec-kitty-open-packs` follows the
  model to stay valid; core aligns once the API is functional and released, through the generated
  client.
- **Tests and examples carry only the canonical shape.** An old or invalid shape appears in a test
  only to exercise an error-handling path (an invalid pack or invalid content), never as a
  supported input. Fixtures, example packs and contract examples are updated with the model,
  never exempted from a parity or round-trip check.
- Core changes when the service is incorporated into core; that work is planned then.
- This repository's own org layer is lost under the as-is CLI, and that is accepted (operator,
  2026-11-05; open-packs #118). `.kittify/config.yaml` registers `packs/internal` and `packs/qa` by
  path, and once synced they declare `depends_on`, which CLI 4.0.0rc8 rejects. `charter status` and
  `charter context` still exit 0; `doctor charter-packs` exits 1 and names the fault.

## Orchestrating cloud sessions

When a session hands Missions to child cloud sessions, it follows these rules. The detail is in
`docs/development/cloud-session-setup.md` ("Running several cloud sessions").

- Session tooling (the Spec Kitty CLI, CodeGraph) is installed by the environment setup script,
  never per session.
- Sibling repositories are cloned read-only; child sessions are not given push access to them.
- A child session that needs to write to a sibling repository writes draft issues into its Mission
  research directory, with `open-packs#TBD-<slug>` placeholders; the orchestrator files them and
  replaces the placeholders before merge.
- An ADR number is chosen when the ADR is written, against `main` and the open PRs.
- A session the permission classifier blocks is replaced, not retried.

## Gates before merge

- `./gradlew verifyAll` on **Java 25**, with strict SHA-256 dependency verification: the
  charter's own contract checks, contract validation and generation, unit tests, the
  ArchUnit gate, the contract scenarios and the whole-pack round trip over charter's example packs (ADR-0070). CI never builds with
  `-Pcharter.javaVersion=21`.
- The **Java style check** on hand-written sources, wired into `verifyAll`: palantir-java-format
  (Spotless) and Checkstyle, with Error Prone, NullAway and doclint beside them. What each
  gate covers: `docs/guides/service-build.md`.
- The **whole-pack round trip** (`:packs:wholePackRoundTrip`), required through `CI gate / gate` for every
  pull request that changes the contracts, the service or the build (operator ruling, 2026-11-05; epic #186).
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

- `charter consistency-check` reports the two activated glossary packs as missing from
  the compiled catalog: `generate` does not compile glossary packs.
