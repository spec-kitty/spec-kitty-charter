---
title: "Service usability run, 2026-11-09"
description: How ready the charter service is for core, from registering the open-packs, activating this charter and reading a profile and a mission.
doc_status: draft
updated: 2026-11-10T00:00:00Z
type: Plan
kind: Planning
related:
  - guides/service-build
  - guides/compose-packs
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the gaps a client meets today, each with the issue that tracks it.
  - profile: governance-lead
    guidance_text: Read this for the readiness verdict and the recommended order of the next work.
---

# Service usability run, 2026-11-09

**Status: assessment, not a decision.** It answers one question: how close is the service to
being ready for core? The test plays a client's part end to end. It changes no code.

## What was run

- **Service:** `main` at `55c7d824`, which includes #448 (PR C) and the DocKitty 0.7.0 docsite. Built with
  `:deployable:bootJar` and started locally with registration and the filesystem pack source on.
- **Packs:** spec-kitty-open-packs `origin/main` (`a2a9379`), not the pin. The `built-in` pack was copied into
  `<config-root>/packs/`. `internal`, `qa`, `spdd`, `event-driven` and `cave-kitty` were registered through
  `POST /packs`.
- **Project:** this repository's project pack (`.kittify/charter-packs`), registered at the project level.
- **Activation:** this repository's `.kittify/charter/charter.yaml` translated into an activation document,
  plus `agent_profile: java-jenny`.
- **Questions asked of the API:**
  - Can the `java-jenny` profile and its charter guidance be read from the offering?
  - Can the same be read under the activation?
  - Can the `software-dev` mission, its steps and its prompts be read?

## Verdict

**Not yet ready for core.** These parts work end to end:

- pack registration;
- composition;
- the activation document and its validation;
- the effective reads.

A client still cannot read what it needs to act:

- profiles, mission types, steps and prompts have no read;
- the action context is a list of ids, with no text or links;
- real packs need editing by hand before they register;
- core's generated project pack needs editing by hand before it registers.

## What works

- **Startup:** the service starts in under 2 s, and the token guard behaves as documented.
- **Registration:**
  - it runs asynchronously, and `GET /pack-registrations/{id}` reports its state;
  - a refusal names each faulty file with a pointer and a code.
- **Composition:** the offering composes all six packs. The `depends_on` ranges, the levels and the rule that a
  reference across packs carries the other pack's name (`built-in::…`) all hold.
- **Activation:** creating an activation refuses ids its packs don't hold, with one pointer per id.
- **Effective reads:** the artifact list, the skipped report, the graph and the action context are all served.
  No activation operation answers `501` any more.
- **Required directives:** the `internal` pack's two required directives are in force.
- **`java-jenny`:**
  - she can be selected and is listed as active;
  - against open-packs `main`, a cascade from her reaches `java-conventions` (#405), and with it, wrongly, the
    Python and JavaScript guides (#408).

## Gaps

| # | Gap | Tracked in |
|---|---|---|
| 1 | **Most artifact kinds have no read.** Bodies are served for directive, tactic, styleguide, paradigm and toolguide only. `agent_profile/java-jenny`, `mission_type/software-dev`, procedures, step contracts and templates answer `404 kind_not_found`, though the activation accepts and lists them. | #407, #96 |
| 2 | **The `software-dev` mission's steps and prompts can't be read.** The mission and its six steps (`action:software-dev/implement`, …) appear only as ids in the graph, with no step contract, template or prompt text. | #99, #148, #437, #438 |
| 3 | **The action context differs from core's.** For `software-dev/implement` it lists 7 directives as ids, states and reasons. Core lists 16 and renders their text. Among those it leaves out are the org-required `NO_FULL_HEAVY_SUITES_IN_MISSION` and `PROJECT_001`, `PROJECT_002` and `QA_003`. Its items carry no links to the bodies. | #450 |
| 4 | **No open-packs pack registers as it is.** Every pack was refused as `unmodelled` over `README.md`, `PROVENANCE.md` and `org-charter.yaml`, and `spdd` also over its step enhancements and prompt fragments. | #451, #431, #437 |
| 5 | **Core's synthesized project pack doesn't register.** It needed four workarounds: it has no `pack.yaml` and uses singular folders; its graph uses a different node format; its directives carry a `procedures` key the schema refuses; and its references to other packs carry no pack name or name the wrong pack. | #452 |
| 6 | **Selections are spelled differently.** `charter.yaml` names directives by slug (`001-project-intent-directive`, `qa-003-no-fake-green`); the service needs `PROJECT_001` and `QA_003`. Core has to translate them at the seam. | #13 |
| 7 | **Language tags don't match.** `java-supply-chain` declares `jvm`, so a project with `languages: [java]` loses it to the language filter. | #453 |
| 8 | **Usability:** a bare `404` at `/` gives no pointer to `/api/v1`; the root does not link to activations; a refused token source is reported inside a stack trace of about 60 lines. | #454, #409 |

**Follow-up, 2026-11-10.** Gap 1: agent profiles are read pack-scoped and under an activation with their
governance (#407, #96), and mission types are taken up by the parallel mission-type reads (#99). Gap 3: the items of
the action context, the effective list and the effective graph link their reads under the activation
(`effective-artifact`) and, where the pack is known, their pack-scoped reads (`artifact`), the item-link part of #450;
the scoping rule stays open there. Gap 8: the root now links `activations`. The repeatable walk is
`scripts/usability/service-usability-run.sh`.

## Tickets after the run

- **Closed:** #10 and #11, which #414, #419, #427 and #448 delivered.
- **Updated:**
  - #407: the effective reads are served, and the profile read remains the P0 gap.
  - #103: the effective sets are built, and the follow-up is #450.
  - #431: the refusal is now proved by a run.
  - #405: fixed in open-packs `main` and closes with the pin bump.
  - #435: open-packs#175 has merged.
- **Filed:** #450 to #454.

## Recommended order

1. **Artifact bodies for every kind,** profiles first (#407, #96). Almost every core read depends on them.
2. **Reads of mission types and steps, and the effective step prompt** (#99, #148, then #437 and #438).
3. **Real packs register:**
   - documentation files are accepted or skipped (#451);
   - `org-charter.yaml` is held (#431);
   - the open-packs pin is bumped (#405, #435).
4. **The action context matches core:** decide its scoping rule and link its items to their bodies (#450).
5. **Core writes what the service accepts:**
   - the synthesized project pack (#452);
   - the id spellings at the seam (#13, under #361).
