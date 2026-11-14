---
okf_version: "0.2"
title: "Usability walk as a BDD test: research, 2026-11-14"
description: Whether the manual service usability sequence can become a BDD test, where it should live, what it asserts, and the operator's ruling on its home.
doc_status: draft
updated: 2026-11-14T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/service-usability-run-2026-11-14
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0057-performance-budgets-never-block-a-pull-request
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you build the usability journey; it names the scenarios, the glue to add and what not to assert.
  - profile: governance-lead
    guidance_text: Read this for why the test is a per-PR journey and not a nightly, and the ruling that settled it.
---

# Usability walk as a BDD test: research, 2026-11-14

**Status: design experiment, with one operator ruling.** The operator asked whether the sequence of the
[usability run of 2026-11-14](/plans/engineering-notes/service-usability-run-2026-11-14/) can be formalized as a
BDD test, as part of the nightly, whatever the run's result. A research squad answered. This note changes no code.

## The sequence

1. Build the service and start it.
2. Configure the built-in pack and register the `internal` pack through `POST /packs`.
3. Read, in a pack: the agent profile `java-jenny`, the mission type `software-dev` and a paradigm.
4. Turn this repository's `.kittify/charter/charter.yaml` into an activation and create it.
5. Under the activation: the profile, the mission type and every step, each step's action context and prompt, the
   `adversarial-squad-deployment` procedure (the review squad) and the profiles a review squad would load.

Today it runs as `scripts/usability/service-usability-run.sh` plus an ad-hoc probe.

## The squad

Three read-only lenses, each with its profile loaded through `spec-kitty charter context` where the tool allowed it:
architecture and governance fit (architect), test quality (reviewer), CI mechanics and prior art (researcher). Their
findings were checked against the files they cite before this note was written.

## Verdict: feasible, as a per-PR journey, not as a nightly

All three lenses found the sequence can be formalized, and all three advised against running it as written in a
nightly.

- **Its inputs would contradict ADR-0070 as amended on 2026-11-13.** Charter's tests read charter's own example packs
  (decision 1); the round trip and the watch over open-packs content move to open-packs, and `open-packs-drift.yml`
  is removed (#557). A charter nightly over open-packs content would rebuild the watch that was just ruled out.
- **The live charter is not a fixture.** `.kittify/charter/charter.yaml` changes with operator rulings, and selects
  artifacts no example pack holds (it selects 16 directives and 9 procedures; the example packs hold 5 and 2). The script copes by dropping
  unheld selections and still passing (`service-usability-run.sh` lines 684 to 697; `scripts/usability/README.md`,
  exit code 0). In an unattended test that is fake green (charter, "no fake green"; `QA_003`). The test needs an
  activation document written for its fixtures.
- **Its useful part is fast.** The contract scenarios run in-process: one `@SpringBootTest` context with MockMvc and
  temporary roots (`CucumberSpringConfiguration.java`), selected by `CharterContractSuite` (`@contract`, not `@WIP`)
  inside `verifyAll` and so inside `CI gate / gate`. Every nightly in the repository exists because its job is slow or
  external (native build, CPU budgets, PIT, SonarCloud, link checks, open-packs `main`). This walk is neither.
- **Timing must not be asserted.** The script fails on a p95 over 500 ms. ADR-0057 decision 3 keeps time budgets
  report-only and out of `verifyAll`.

## What is new, and what is already covered

Each single read already has narrow `@contract` scenarios over the example packs: agent profiles, mission types and
effective mission types, paradigms, activation create, the effective profile, the action context
(`effective-graph-and-context.feature`), the step prompt (`effective-step-prompt.feature`, SC-001 reads software-dev's
plan prompt under an activation over `built-in` and `org-base`), procedures under an activation and pack
registration. The switch gate (ADR-0059 decision 1) already counts these operations.

What no scenario covers:

1. **One mission walk under one activation:** every step of `software-dev`, its action context and its prompt.
2. **A client that follows only links** from the API root to all of it.
3. **The review squad:** the `adversarial-squad-deployment` procedure in force, and the review profiles read under the
   same activation.
4. **The packaged jar** booted with a token, a random port and the filesystem pack source. This is packaging, not behavior: the
   release smoke (`scripts/release/smoke-service.sh`, `service.yml`) covers it.

Items 1 to 3 fill the `@WIP` gaps of journey 01 ("The first Mission starts against the active charter", line 48),
journey 08 (resolve a step) and journey 11 (human reviews).

## Proposal

A new journey, `contracts/charter/scenarios/journeys/journey-12-a-mission-walk-under-one-activation.feature`, tagged
`@contract @journey-12`:

- **Packs:** the example pack `built-in` configured, and `org-base` registered through `POST /packs` from the
  filesystem source, standing in for `internal`. One new step registers an example pack from the filesystem source
  (today the steps register only archives, `PackSourceSteps`).
- **Activation:** written in the scenario file, over the example packs only. It selects nothing the packs do not hold, so a
  refused selection fails the scenario.
- **Reads:** by following links, hidden in the glue; the scenario text names business outcomes, not URLs.
- **Cost:** seconds inside the existing test JVM; no new workflow.

Draft scenarios (declarative):

1. **The project's charter is in force on the example packs.** Given the built-in pack and an org pack registered,
   when the project activates its charter, then every selection is in force and nothing is refused.
2. **Java Jenny's governance is in force.** When the team reads Java Jenny under the activation, then
   `java-conventions` is a required item and every governance item says how it applies.
3. **The software-dev mission is governed step by step.** When the team reads the effective `software-dev`, then its
   steps run in the order the example pack declares, and each step that has a contract carries it.
4. **Every step comes with the context and prompt to act on.** For each step of `software-dev`, the action context
   names what is in force and links each item to its body, and the prompt fills every slot it declares or lists it as
   empty.
5. **A review squad is available.** The `adversarial-squad-deployment` procedure is in force, and the review profile
   is readable under the activation.

**Do not assert:** hop counts, link names, URLs, latency, exact body text, or counts copied from open-packs content.
State the example pack's own counts in the scenario file, so a fixture change and its assertion land in one diff.

## Limits the squad found

- **The squad's cast is not data.** The example procedure says "cast profiles for the question" and names no role or
  profile (`adversarial-squad-deployment.procedure.yaml`, step "Cast profiles for the question"). The service can
  serve the procedure and each profile, but which profiles form a squad is the client's choice. Scenario 5 asserts the
  procedure and the review profile only; selecting a profile by role is slice G (#538, #42).
- **The example packs hold no architect or researcher profile** (`built-in` holds generic-agent, implementer-ivan,
  java-jenny, python-pedro and reviewer-renata). Adding them is allowed (ADR-0070 decision 2) and must keep
  `ExamplePackCoverageTest` and the whole-pack round trip green.
- **A multi-operation journey adds nothing to the switch gate**, which counts per operation (ADR-0059 decision 1). It
  is a plain `@contract` scenario.

## What stays a script

`service-usability-run.sh` stays a manual tool against the open-packs pin, for usability runs like this one. The
proof over real open-packs content belongs to open-packs (spec-kitty-open-packs#252, #253). If a jar-level walk is
wanted later, extending the release smoke with a few link hops is cheaper than booting the jar from a scenario.

## Ruling

**Operator, 2026-11-14:** the test is a **per-PR `@contract` journey** over charter's example packs, with a
hand-written activation, as proposed above. Not a nightly. The real-content proof stays with open-packs.

The journey is filed as #572. Like every `@contract` scenario, it will also run in the nightly gating pass that #15
adds, so the operator's wish to have the walk in the nightly is met without a second suite.
