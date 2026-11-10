---
title: "Service usability run, 2026-11-10: mission types"
description: A client starting at the API root reaches software-dev, its 13 steps, 5 step contracts and its skeletons, from the pack and under an activation.
doc_status: draft
updated: 2026-11-10T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/service-usability-run-2026-11-09
  - guides/service-build
  - adr/0018-action-context-read-and-seam-access-surface
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the mission type walk proves, its hop counts and the one workaround it adds.
  - profile: governance-lead
    guidance_text: Read this for the evidence behind SC-001 of the mission type reads (#99).
---

# Service usability run, 2026-11-10: mission types

**Status: assessment, not a decision.** It answers one question: can a client that knows only `GET /api/v1/` read
the `software-dev` mission type, every step, the step contracts and the skeletons, from the pack and under an
activation? It is the proof of SC-001 of the mission type reads (#99). It follows the
[run of 2026-11-09](/plans/engineering-notes/service-usability-run-2026-11-09/) and uses the same script.

## What was run

- **Script:** `scripts/usability/service-usability-run.sh`, extended with a mission type walk (walks 3 and 4). The
  profile walks (1 and 2) are unchanged and ran in the same pass.
- **Service:** branch `issue-99-mission-type-reads` at `07abe2e6`, which is tree-identical to `87b033d8` on the branch
  (the same commit, re-signed before the push). The jar was built from `2634cfcb` with
  `:deployable:bootJar`. The commit between them changes only the script, so the service is the same. The script
  started the jar with `java -jar`, with registration and the filesystem pack source on, and stopped it at the end.
- **Packs:** spec-kitty-open-packs at the pin `a2a9379`. `built-in` was copied into `<config-root>/packs/` and its git
  tree checked against the pin. `internal` and `qa` were registered through `POST /packs`.
- **Activation:** this repository's `.kittify/charter/charter.yaml` translated by `charter-to-activation.py`, plus
  `agent_profile: java-jenny`. It selects `software-dev`.

```bash
JAVA_HOME=/usr/lib/jvm/temurin-25 scripts/usability/service-usability-run.sh \
  --open-packs ../spec-kitty-open-packs --jar service/deployable/build/libs/deployable-0.1.0-SNAPSHOT.jar
```

## Result

```text
MISSION pack type=software-dev steps=13/13 contracts=5/5 bound-skeletons=2/2 skeletons=4/4 hops=34/34
MISSION activation type=software-dev steps=13/13 contracts=5/5 bound-skeletons=2/2 skeletons=4/4 hops=34/34
MISSION graph+context type=software-dev hops=11/11
RESULT PASS hops=131/131 failed=0 workarounds=5 unheld=7 unlinked=15 p95=320/348ms commit=07abe2e6 pin=a2a93794
```

**SC-001 holds in both scopes.** Every number it names was reached by following links:

| What | Expected | From the pack | Under the activation |
|---|---|---|---|
| Steps in sequence, in order | 8: discovery, specify, plan, tasks, analyze, implement, review, accept | 8 | 8 |
| Steps on demand | 5: charter, tasks-finalize, tasks-outline, tasks-packages, retrospect | 5 | 5 |
| Step contracts (`step-contract`) | 5: specify, plan, tasks, implement, review | 5 | 5 |
| Skeletons bound by a step (`skeleton`) | 2: specify to `spec-template.md`, plan to `plan-template.md` | 2 | 2 |
| Skeletons the type lists | 4 | 4 | 4 |

Under the activation, the mission type is held by `built-in`, and its `step-contract` links lead to the generic
effective read.

## Hops

- **Walk 3, from the pack (34 hops):**
  - the root's `artifacts` with `packId=built-in` and `kind=mission_type`, the list item's `self`, the body;
  - each of the 13 steps' `self`;
  - the 5 `step-contract` links and the 2 `skeleton` links;
  - the 4 skeletons' `self`;
  - the set checks.
- **Walk 4, under the activation (34 hops):** the activation's `artifacts` with `kind=mission_type`, the item's
  `effective-artifact`, then the same steps, contracts and skeletons as in walk 3.
- **Graph and action context (11 hops):**
  - the effective graph has 66 nodes. Six of them are action nodes of `software-dev`: implement, plan, retrospect,
    review, specify and tasks, the steps the built-in graph declares. Each links `effective-artifact`, and each read
    answers its step;
  - the action context of `software-dev/specify` answers 5 artifacts. Its `effective-artifact` and `artifact` links
    answer the step under the activation and in `built-in`.
- **Registration, the root and walks 1 and 2 (the profile, 52 hops):** unchanged from the profile reads. The p95 of 20 reads was 320 ms from the
  pack and 348 ms under the activation, under the 500 ms limit.

## Failures

None. No hop failed, and no relation that the walk expected was missing.

## Workarounds

Five, each printed by the script when it was applied. The first four are the ones the profile walks already name.

| Name | Cause | Owner |
|---|---|---|
| `unmodelled-files (#451)`, twice | `internal` and `qa` are refused over `README.md`, `PROVENANCE.md`, `org-charter.yaml` and, in `qa`, two template files. Staged copies without them were registered. | pack registration (#451) |
| `project-pack (#452)` | The project pack has no `pack.yaml`. It is not registered. | core's project pack (#452) |
| `unheld-selections` | 7 selections no registered pack holds: `PROJECT_001`, `PROJECT_002` and five style guides. They were left out of the activation document. | the project pack (#452) |
| `unlinked-graph-and-context` ([#469](https://github.com/spec-kitty/spec-kitty-charter/issues/469), new) | The activation links only `self` and `artifacts`. The graph and the action context have no relation that leads to them. The walk took those two paths from the contract and appended them to the activation's `self` href. They are the only paths it built after the bootstrap. | the activation set (not this mission) |

The new workaround is a gap in the activation's links. It is not a gap in the mission type reads: once the graph
and the context were read, every step was reached through their links. Linking `graph` and a templated action
context from the activation would remove it.

## What this does not cover

- The skeletons' and the step prompts' bytes are not compared with the checkout here; `MissionTypeHoldingCorpusTest`
  does that against the pin.
- Only `specify`'s action context is read. The other five action nodes are reached through the graph.
- A step that a graph declares but the winning mission type lacks would be linked and answer `404 action_not_found`
  (WP06's recorded follow-up). It does not occur at the pin.
