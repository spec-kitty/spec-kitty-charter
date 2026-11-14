---
okf_version: "0.2"
title: "Service usability run, 2026-11-14: a mission walk under the charter"
description: "After slices C and D: a profile, a mission type and a paradigm, this charter as an activation, every software-dev step and the review squad, and what a real run still lacks."
doc_status: draft
updated: 2026-11-14T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/service-usability-run-2026-11-12-slice-b-pin
  - plans/engineering-notes/usability-walk-as-bdd-research-2026-11-14
  - plans/next-slices-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what a client gets today when it walks a whole mission under this repository's charter, and the gaps with their owners.
  - profile: governance-lead
    guidance_text: Read this for whether the service can govern a real run, and the slices the findings point to.
---

# Service usability run, 2026-11-14: a mission walk under the charter

**Status: test debrief, not a decision.** The operator asked for a usability test after slices C (#467) and D (#468)
landed: build the service, register the built-in and internal packs, read the `java-jenny` profile, the
`software-dev` mission type and a paradigm, then activate this repository's charter and see whether the review squad
and the mission steps resolve as they would for an actual run. It follows the
[slice B pin run](/plans/engineering-notes/service-usability-run-2026-11-12-slice-b-pin/).

## What was run

- **Service:** the `service-jar` artifact of `service` run 43598708009 on `main` at `b1d73b1` (slices C and D, #547,
  #559, #551, #553 included). The artifact zip matched its published digest (`sha256:fe91354f…`); the jar's sha256 is
  `49b3b54e7a2b…`. A local build was not possible in this session: the Gradle plugin portal's redirect to Maven
  Central answered 429 for about 40 minutes, so the jar CI built from the same commit was used.
- **Packs:** spec-kitty-open-packs at the pin `1692a3b`. `built-in` and its `depends_on` closure (`architecture`,
  `foundation`, `java`, `planning-analysis`, `refactoring`, `testing`, `writing`) were configured as the distribution
  level, each copy checked against its pinned git tree; `internal` was registered through `POST /packs`. `built-in`
  holds no artifact of its own since open-packs #73, so "registering built-in" means configuring it.
- **Activation:** `.kittify/charter/charter.yaml` translated by `charter-to-activation.py`, plus `java-jenny`.
- **Client:** `scripts/usability/service-usability-run.sh --packs internal --keep`, then an ad-hoc probe against the
  same service for every step's context and prompt and for the review squad.

```bash
scripts/usability/service-usability-run.sh --open-packs ../spec-kitty-open-packs --profile java-jenny \
  --packs internal --keep --jar <service-jar of run 43598708009>
```

## Result

```text
[ ok ] R-internal registered 'spec-kitty-internal' unmodified from packs/internal
RESULT PASS hops=145/145 failed=0 workarounds=2 unheld=8 unlinked=0 p95=159/256ms client=28958e7a jar=49b3b54e7a2b pin=1692a3b1
```

| Check | Result |
| --- | --- |
| Register `internal` | 201, unmodified |
| `java-jenny` in its pack (`java`) | 200; role `implementer`; 35 governance entries, each with an edge; `java-conventions` is a `requires` entry |
| `software-dev` in its pack (`foundation`) | 200; 13 steps (8 in sequence, 5 on demand), 5 step contracts, 2 bound and 4 listed skeletons |
| A paradigm (`architecture/c4-incremental-detail-modeling`) | 200, with its principles |
| The charter as an activation | 201, after leaving out 8 selections no registered pack holds (below) |
| The same three reads under the activation | 200 each; the graph (73 nodes) is now linked from the activation, so the #469 workaround is gone |
| The `adversarial-squad-deployment` procedure under the activation | 200, `active`, `reason: selected`, 7 steps |
| Squad profiles under the activation (reviewer-renata, architect-alphonso, researcher-robbie, debugger-debbie, planner-priti) | 200 each; 20 profiles are in force |

### Every step of `software-dev` under the activation

| Step | Role | Step read | Action context | Step prompt |
| --- | --- | --- | --- | --- |
| discovery | — | 200 | 404 `action_not_found` | 404 `action_not_found` |
| specify | — | 200 | 200: 12 directives, 3 paradigms, 3 required | 200, 48 kB, slot `spdd:reasons-block` empty |
| plan | — | 200 | 200: 13 directives, 2 paradigms | 200, 22 kB, slot empty |
| tasks | — | 200 | 200: 11 directives, 2 paradigms | 200, 28 kB, slot empty |
| analyze | — | 200 | 404 `action_not_found` | 404 `action_not_found` |
| implement | implementer | 200 | 200: 14 directives, 2 paradigms | 200, 17 kB, slot empty |
| review | reviewer | 200 | 200: 15 directives, 2 paradigms | 200, 14 kB, slot empty |
| accept | — | 200 | 404 `action_not_found` | 404 `action_not_found` |
| charter (on demand) | — | 200 | 404 `action_not_found` | 404 `action_not_found` |
| tasks-outline, tasks-packages, tasks-finalize (on demand) | — | 200 | 404 `action_not_found` | 404 `action_not_found` |
| retrospect (on demand) | facilitator | 200 | 200: 11 directives, 2 paradigms | 200, 3 kB, no slots |

Every item of every context carries its links (slice C, #450). Each context's skipped report is empty in all 14 of
its categories. The empty
`spdd:reasons-block` slot is expected: the charter does not activate the `spdd` pack, and the prompt read lists an
unfilled slot rather than failing (ADR-0066 decision 9).

## Findings

### 1. Seven of the thirteen steps cannot be run: their action has no node (content gap, slice F interaction)

`foundation/action.graph.yaml` at the pin declares action nodes for six `software-dev` steps only: `implement`,
`plan`, `retrospect`, `review`, `specify` and `tasks`. Open-packs `main` (`fd7d644`) still has the same six. The other
seven steps are served as steps, but their action context and prompt answer `404 action_not_found`, as the contract
says for an action the closure does not hold. A client that drives the mission gets no governance for `discovery`,
`analyze` or `accept`, which are in the sequence.

Slice F's plan (draft PR #567) makes `mission_type -requires-> action` authored and complete, and refuses a pack that
misses one at load. When slice F lands, `foundation` will be refused unless open-packs adds the seven actions first,
or slice F scopes the rule. **This needs a decision before slice F's implementation starts.**

### 2. The review squad resolves only through a client-side hop (slice G)

The chain a real run needs is: the `review` step names the role `reviewer` → a profile for that role → its governance
→ the squad procedure. What the service answers:

- the `review` step carries `role: reviewer` (only `implement`, `review` and `retrospect` name a role in the content);
- **no operation turns a role into a profile.** The client has to choose `reviewer-renata` itself. This is slice G
  (#538: #102, #42);
- `reviewer-renata` under the activation lists `requires → procedure:adversarial-squad-deployment`, linked, and the
  procedure is in force;
- the `review` action context holds directives and paradigms only, so the squad is not reachable from the step without
  the profile hop;
- the procedure names no cast: "Cast profiles for the question" is prose. Which profiles form a squad stays the
  client's choice; the service can serve each one, and all five tried are in force.

So the squad resolves as an actual run needs it only once profile selection by role exists.

### 3. Eight charter selections have no pack (known, #452 and the run's scope)

The activation was refused `422` with eight `unknown_artifact` violations, and the run left them out:

- `PROJECT_001`, `PROJECT_002` and five style guides (`documentation-policy`, `java`, `python`, `quality-gates`,
  `testing`) live in this repository's project pack, `.kittify/charter-packs`, which the service refuses for its
  layout (#452, core's synthesizer);
- `QA_003` lives in the `qa` pack, which this run did not register (the operator asked for `internal`; the 2026-11-12
  run registered both and had seven unheld).

A real run under this charter would therefore miss eight of its selections today.

### 4. Smaller observations

- `GET /packs` answers `405`: there is no pack list operation; a client finds packs through `GET /offering`. Not a gap
  against the contract, noted for client authors.
- The slice B and C gains hold: `internal` registers unmodified, the activation links its graph (the `#469` workaround
  is gone since the 2026-11-12 run), and every context item links its body.
- p95 was 159 ms in a pack and 256 ms under the activation, under the script's 500 ms limit (report only; ADR-0057).

## What a real run gets today

A client can read the profile, the mission type and its steps, step contracts and skeletons, and, for six of the
thirteen steps, the governance and the prompt it needs, all under this charter. It cannot yet get governance for the
other seven steps, turn a step's role into a profile, or include the project pack. The test sequence itself is judged
in [Usability walk as a BDD test](/plans/engineering-notes/usability-walk-as-bdd-research-2026-11-14/).

## What the run changed

Nothing in the repository. The probe was ad hoc and is not kept; its reads are the contract's own operations
(`getEffectiveActionContext`, `getEffectiveStepPrompt`, `getEffectiveArtifact`, the procedure references twin).
