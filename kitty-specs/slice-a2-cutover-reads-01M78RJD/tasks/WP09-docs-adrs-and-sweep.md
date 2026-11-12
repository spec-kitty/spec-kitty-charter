---
work_package_id: WP09
title: Docs, ADRs, routing pins and the final sweep
dependencies:
- WP01
- WP02
- WP03
- WP04
- WP05
- WP06
- WP07
- WP08
requirement_refs:
- FR-015
- NFR-001
- NFR-002
- C-003
- C-004
- C-005
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on kitty/mission-slice-a2-cutover-reads-01M78RJD, landing on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
subtasks:
- T039
- T040
- T041
- T042
phase: ''
agent: claude
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: docs/
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/PackArtifactRoutingTest.java
execution_mode: code_change
owned_files:
- docs/api/service-contract.md
- docs/adr/0074-the-remaining-cutover-reads-and-their-paths.md
- docs/adr/0019-packs-resource-model-and-hypermedia.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRoutingTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/PackArtifactRoutingTest.java
- docs/_meta/spelling-words.txt
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP09 - Docs, ADRs, routing pins and the final sweep

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Record what was built, pin the routing, and run the full gate.

## Subtasks

### T039: Service contract page

In `docs/api/service-contract.md`, mark the built rows for the 13 operations (presets, assets and payload, skills, their twins, the references twin) and the activation links, matching the page's existing style (around :262 and :292-293).

### T040: ADRs

- **ADR-0074:**
  - Add a dated `## Amendment 2026-11-12` section recording decision 9's reading for assets under an activation (research.md R3). Cite packs decision `01M702A71YF7WNDTG2AA89K8ZA`, and state that it was made under the operator's "make reversible calls" instruction and is open for the operator's check-in.
  - Add a status line: decisions 1, 2, 4 and the paths of 9 to 14 are built for presets, assets and skills, with the glossary (#106) pending its own mission.
  - Annotate decision 9 and decision 12 where they stand.
- **ADR-0019:** annotate that the `assets` rows are replaced, if that is not already annotated.
- Keep `updated:` current. Run the docs checks: `check_docs.py`, markdownlint-cli2 and cspell (add words to `docs/_meta/spelling-words.txt` only when they are real terms).

### T041: Routing pins

- `ActivationRoutingTest`: the literal asset, skill and payload paths route to the edge controllers, and the references path routes to the references controller. Check that no path is shadowed by `{kind}/{artifactId}/references`.
- `PackArtifactRoutingTest`: the same for the pack tree (`…/asset/{id}/payload` versus `…/{kind}/{id}/references`).

### T042: Final sweep

- Run `./gradlew verifyAll --max-workers=1` on Java 25.
- Check that every fragment exists and `checkContractChangelogFragments` is green, that the switch-gate report lists 13 new `ready` operations, and that `ExampleCoverageTest` and `ReservedOperationCoverageTest` are green.
- Fix what fails. A fix in another WP's files is a recorded out-of-map edit.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 verifyAll
python3 scripts/check_docs.py   # or the documented docs-check entry point
```

## Definition of Done

`verifyAll` is green, the docs checks are green, the ADR amendment is recorded, and the routing is pinned.

## Risks and reviewer guidance

The ADR amendment must say plainly that it is an agent call awaiting operator review.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP09 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#536)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
