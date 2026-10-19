---
work_package_id: WP01
title: The default Problem response declares the Charter headers, guarded red-first
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- NFR-001
- NFR-002
- C-001
- C-002
- C-003
- C-004
- SC-001
- SC-002
- SC-003
planning_base_branch: fix/problem-response-charter-headers
merge_target_branch: fix/problem-response-charter-headers
branch_strategy: Planning artifacts for this mission were generated on fix/problem-response-charter-headers. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/problem-response-charter-headers unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-problem-response-charter-headers-01M5A4PB
base_commit: 861f6dcdad9a3d183a0e2d596732c0532c43bb67
created_at: '2026-10-19T13:33:38.245020+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
history:
- at: '2026-10-19T13:40:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: contracts/
create_intent:
- contracts/lint/fixtures/charter-headers-on-every-response.yaml
- contracts/lint/fixtures/charter-headers-on-every-response.missing-fingerprint.yaml
- docs/changelog/2026-10-19-problem-response-charter-headers.md
execution_mode: code_change
model: sonnet
owned_files:
- contracts/_shared/responses/Problem.yaml
- contracts/_shared/VENDORED.md
- contracts/lint/**
- contracts/charter/scenarios/service.feature
- contracts/charter/CHANGELOG.md
- docs/api/service-contract.md
- docs/changelog/2026-10-19-problem-response-charter-headers.md
- docs/changelog/README.md
role: implementer
tags: []
tracker_refs: []
---

# WP01 — The default Problem response declares the Charter headers, guarded red-first

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `implementer-ivan`). Then load governance context for this action:
`spec-kitty charter context --action implement`. The charter wins over this prompt.

## Objective

Issue #90: the shared `default` response `contracts/_shared/responses/Problem.yaml` is returned by
every charter operation (`listPacks`, the action-context operation, `GET /info`), and the
`CharterHeaders` filter already sends `Charter-Contract-Version`, `Charter-Worktree` and (when the
packs can be read) `Charter-Source-Fingerprint` on it. The contract does not declare them there.
Make the contract declare them, and add a lint rule that fails whenever any response of any
operation lacks one of the three, so ADR-0011's "headers on every response" rule cannot regress.

**Operator rulings that govern this WP**
- DM `01M5A4PP44NQ3KWG4APT0PEJCT`: edit `contracts/_shared/responses/Problem.yaml` itself.
- General ruling, 2026-10-19: spec-kitty-charter is the canonical owner of the charter structure,
  responses, contracts and pack schemas. Vendored content changed here is canonical; consumers,
  core included, adapt. Stay close to the source, deviate for good reason.

## Context

- Header definitions (the single source, C-002): `contracts/charter/headers/CharterContractVersion.yaml`
  (`required: true`), `CharterWorktree.yaml` (`required: true`), `CharterSourceFingerprint.yaml`
  (`required: false`, absent when a pack file cannot be read).
- How a response declares them today: `contracts/charter/responses/ActionNotFound.yaml` and
  `OfferingUnreadable.yaml` (`headers:` map with `$ref: ../headers/<File>.yaml`).
- The lint runs on the **bundle** (`./gradlew :contracts:lintContract`), every `$ref` inlined.
  `contracts/lint/README.md` and the header of `contracts/lint/ruleset.yaml` explain the rule and
  fixture conventions: every rule has a planted fixture `fixtures/<rule id>.yaml` (or
  `.<case>.yaml`) that fails **that rule and no other**, and `fixtures/clean.yaml` fails none
  (`./gradlew :contracts:lintFixtures`). `lintContract` also fails if vacuum loads a different
  number of rules than the ruleset declares.
- No runtime change (C-001). The contract is `1.0.0-SNAPSHOT`, unreleased (C-003).

## Branch Strategy

Planning branch and final merge target: `fix/problem-response-charter-headers`. Your execution
worktree is allocated per computed lane from `lanes.json`; implement with
`spec-kitty agent action implement WP01 --agent claude --mission problem-response-charter-headers-01M5A4PB`.

Build environment: `export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH`. `verifyAll` needs
the open-packs checkout at `../spec-kitty-open-packs` relative to the repository root (it exists at
`/home/user/spec-kitty-open-packs`, pinned commit `7f063ae`); from a lane worktree pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs`. The clone is shallow, so pass
`-PcontractBaseline=none` locally (CI compares for real). Maven Central sometimes answers 429:
wait and retry with `--max-workers=2`; never touch verification metadata to dodge it.

## Subtasks

### T001 — The rule, red first

**Purpose**: prove the gap with a failing check before fixing it (charter: test first; ADR 2026-07-17-1 red-first).

**Steps**:
1. In `contracts/lint/ruleset.yaml`, add rule `charter-headers-on-every-response`, severity
   `error`, with a description in the file's style, e.g. "Every response of every operation
   declares the Charter-Contract-Version, Charter-Worktree and Charter-Source-Fingerprint headers
   (ADR-0011)."
2. `given: "$.paths[*][get,put,post,delete,options,head,patch,trace].responses[*]"` (match the
   operation filter the other rules use). `then`: `field: headers`, `function: schema`, with a
   schema requiring the three keys, e.g.
   ```yaml
   then:
     field: headers
     function: schema
     functionOptions:
       schema:
         type: object
         required: [Charter-Contract-Version, Charter-Worktree, Charter-Source-Fingerprint]
   ```
   Also make a missing `headers` field fail (a `truthy` check on `headers`, as
   `error-responses-are-problems` pairs `truthy` with `schema`).
3. Add a comment above the rule naming ADR-0011 and #90, in the ruleset's comment style.
4. Run `./gradlew :contracts:lintContract -PcontractBaseline=none` and **capture the failure**
   (it must name the three `default` responses). Paste the relevant lines into the WP's red-first
   evidence (the mission's review notes or the commit message of the rule commit).

**Validation**: lintContract fails only on the three `default` responses with this rule.

### T002 — Fixtures

**Purpose**: keep `lintFixtures` honest: the new rule needs its planted fixture, and every existing
fixture must still fail only its own rule.

**Steps**:
**Scale of the change (squad-verified):** with the rule in place, every one of the 13 existing
fixtures, `clean.yaml` included, fails it on all 4 of its responses. So every response of every
fixture gains the three headers.

1. Create `contracts/lint/fixtures/charter-headers-on-every-response.yaml`, copying
   `clean.yaml` with the headers on every response except the `default`, which has no `headers`
   at all (this proves the `truthy` half). First comment line:
   `# Planted fixture for charter-headers-on-every-response: a default response without headers.`
2. Create `contracts/lint/fixtures/charter-headers-on-every-response.missing-fingerprint.yaml`:
   the same, but the `default` declares `Charter-Contract-Version` and `Charter-Worktree` and
   omits `Charter-Source-Fingerprint` (this proves the `schema` half, and pins that the
   not-required header must still be declared).
3. Add the three headers to **every** response of `clean.yaml` and of every other fixture.
   Fixtures inline their content: write the header objects once per fixture under a YAML anchor
   (`headers: &charter-headers ...`, then `headers: *charter-headers` on the other responses).
   vacuum expands anchors, and the bundle step's "still holds a $ref" check rejects a local
   `components.headers` + `$ref`, so do not use those. Copy each header file's `description`,
   `required` and `schema` (inline `Sha256Digest` for the fingerprint).
4. Run `./gradlew :contracts:lintFixtures :contracts:test -PcontractBaseline=none`
   (`clean.yaml` also feeds `:contracts:test` via `charter.lintCleanFixture`): each fixture fails
   exactly its rule; `clean.yaml` fails none.

**Validation**: lintFixtures green.

### T003 — The shared response declares the headers

**Steps**:
1. Edit `contracts/_shared/responses/Problem.yaml`: add
   ```yaml
   headers:
     Charter-Contract-Version:
       $ref: ../../charter/headers/CharterContractVersion.yaml
     Charter-Worktree:
       $ref: ../../charter/headers/CharterWorktree.yaml
     Charter-Source-Fingerprint:
       $ref: ../../charter/headers/CharterSourceFingerprint.yaml
   ```
   keeping `description` and `content` unchanged. Do not restate required-ness (C-002).
2. Run `./gradlew :contracts:bundleContract :contracts:lintContract -PcontractBaseline=none`: the
   bundle resolves the references and the rule now passes.

**Validation**: lintContract green; the bundle shows the headers on all three `default` responses.

### T004 — Scenario: the 400 Problem carries the headers

**Steps**:
1. In `contracts/charter/scenarios/service.feature`, after "A refusal carries the headers too",
   add a scenario, e.g. "A bad request carries the headers too":
   ```gherkin
   Scenario: A bad request carries the headers too
     The 400 for a depth this version does not answer is the shared Problem, and it carries the
     headers like every other response.
     When I GET "/mission-types/software-dev/actions/plan/context?depth=2"
     Then the response status is 400
     And the response header "Charter-Contract-Version" is the contract version
     And the response header "Charter-Worktree" is "scenario-worktree"
     And the response header "Charter-Source-Fingerprint" is a sha256 digest
   ```
   All steps exist already (`CharterSteps`). Note: the scenario conformance hook
   (`ContractConformance`) checks content type and body only, never headers, so these header
   steps are the only runtime proof that the 400 carries them.
2. Run the contract scenarios (`./gradlew :service:charter-service:test` or the module that runs
   them; see `docs/guides/service-build.md`).

**Validation**: the new scenario passes; no other scenario changes.

### T005 — VENDORED.md and the API docs

**Steps**:
1. Rewrite `contracts/_shared/VENDORED.md` so it states, in the repository's plain style:
   - where the files came from (core `contracts/_shared/` at `0746bec30cbb473329d9a02333bafada716ecfd7`);
   - that this repository's copy is **canonical** (operator ruling, 2026-10-19): changes are made
     here, kept close to the source unless there is good reason, and consumers, core included,
     adapt; a refresh from core never overwrites a change listed below;
   - a "Changed from the origin" list: `responses/Problem.yaml` declares the three `Charter-*`
     headers (#90, ADR-0011).
   Cite the ruling as "operator ruling 2026-10-19, ADR-0015 (PR #91)" and this mission's
   Decision Moment `01M5A4PP44NQ3KWG4APT0PEJCT`.
2. Remove the "Never edit these files here" sentence.
3. `docs/api/service-contract.md`, "Error model": it says the refusals reuse "the shared `Problem`
   schema of core's `contracts/_shared/`". Reword to say the shared `Problem` schema in
   `contracts/_shared/` is canonical here (ADR-0015) and that every answer, the `default`
   `Problem` included, carries the three `Charter-*` headers. Bump `updated`. Do **not** edit
   `docs/guides/service-build.md`: PR #91 changes it.

### T006 — Records and gates

**Steps**:
1. `contracts/charter/CHANGELOG.md`, under `## 1.0.0-SNAPSHOT`: in the existing subsection style,
   record that the `default` `Problem` response of every operation declares the three headers,
   the new lint rule, and the new scenario (the file's rule: every scenario change gets a line).
2. `contracts/lint/README.md`: document the new convention where the other conventions are listed.
3. A dated docs changelog entry `docs/changelog/2026-10-19-problem-response-charter-headers.md`
   (copy the frontmatter shape of `docs/changelog/2026-10-19-contract-impacts-reservations.md`),
   listed newest first in `docs/changelog/README.md`. Never write the word "feature" (forbidden
   term, `docs/_meta/terminology.yaml`): say "the service contract scenarios", not the file name.
4. Run `./gradlew verifyAll -PcontractBaseline=none -PopenPacksDir=/home/user/spec-kitty-open-packs`
   (green, NFR-001) and the docs checks: `python3 scripts/docs/check_docs.py --today 2026-10-19 all`,
   `npx --yes markdownlint-cli2@0.18.1`, `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`
   (NFR-002; the README under `contracts/` is outside CI's cspell globs, keep it clean anyway).

## Commits

Make the red-first proof visible in history: commit T001 + T002 (rule and fixtures; lintContract
red on the contract), then T003 (green), then T004–T006. End each commit message with:
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01QArhw4SXwmon2bSammdYmV
```
`lintContract` is part of `verifyAll`, so the red commit is intentionally red for that one task
only; say so in its message. (If the history cleanup later requires each commit green, the rule
and the fix are folded together and the red run is kept as evidence in the PR body.)

## Definition of Done

- [ ] All three `default` responses in the bundle declare the three headers (FR-001, SC-001).
- [ ] The rule failed on the pre-change contract (captured) and passes after (FR-002, SC-002).
- [ ] Both planted fixtures (no headers; missing fingerprint) fail only this rule.
- [ ] `lintFixtures` green with the new planted fixture.
- [ ] The 400 scenario passes (FR-003).
- [ ] `VENDORED.md` states the canonical-ownership ruling and lists the change (FR-004).
- [ ] Contract CHANGELOG, lint README, API docs and the dated docs changelog updated (FR-005).
- [ ] `verifyAll` and the docs checks green (NFR-001, NFR-002).
- [ ] No file outside `owned_files` changed; no runtime code changed (C-001).

## Reviewer guidance

- Run `lintContract` with T003 reverted: it must fail on the three `default` responses (half-by-half proof).
- Check that every fixture still fails exactly one rule.
- Check the header required-ness comes only from `contracts/charter/headers/` (C-002).
