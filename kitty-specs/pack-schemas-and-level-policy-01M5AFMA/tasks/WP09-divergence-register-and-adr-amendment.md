---
work_package_id: WP09
title: Divergence register and the ADR-0017 amendment
dependencies:
- WP02
requirement_refs:
- FR-014
- FR-015
- FR-016
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-schemas-and-level-policy-01M5AFMA
base_commit: fdb29d3b02cc65189fd0efe086e0f0c002c3bd9d
created_at: '2026-10-19T18:35:31.310381+00:00'
subtasks:
- T042
- T043
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
- at: '2026-10-19T19:05:00Z'
  event: revised
  by: post-tasks squad (split from the docs WP; register ids needed early by WP08)
agent_profile: scribe-sally
authoritative_surface: docs/conformance/
create_intent:
- docs/conformance/divergence-register.md
- docs/conformance/divergences.yaml
execution_mode: code_change
model: sonnet
owned_files:
- docs/conformance/**
- docs/adr/0017-pack-schemas-imported-as-canonical-json-schema.md
- docs/_meta/sections.yaml
role: implementer
tags: []
tracker_refs: []
---

# WP09 — Divergence register and the ADR-0017 amendment

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `scribe-sally`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt. `docs/context/` is the terminology authority. Read the
**mission-wide rules** in [tasks.md](../tasks.md).

## Objective

Write the two decision records the rest of the mission cites.

1. **The divergence register** under `docs/conformance/` (R10, FR-014). It is the single register
   ADR-0016 decision 4 means, consulted by #72 and #115. WP08's `core-verdicts.json` cites its ids,
   so it comes first.
2. **A dated amendment to ADR-0017** (FR-015a). It records the operator's rulings, which refine or
   override the ADR as accepted.

The architecture docs, glossary and diagram follow in WP10, after the load path lands (WP07).

Read first:
- [spec.md](../spec.md): the rulings table R1-R16 and the open review items;
- [plan.md](../plan.md): calls P1-P10;
- [research.md](../research.md): R3, R4 and R6;
- `contracts/packs/VENDORED.md` (C1-C16, from WP02);
- ADR-0015, ADR-0016, ADR-0017 and ADR-0009 D1;
- `docs/plans/open-questions.md` Q13;
- `contracts/charter/CHANGELOG.md` `### Provisional` (around lines 140-160).

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json`.
- Implement with:
  `spec-kitty agent action implement WP09 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T042 — The divergence register

**Steps**:

1. **`docs/conformance/divergences.yaml`** is machine-readable; #72 and #115 read it. Document its
   schema at the top of the file:

   ```yaml
   - id: DIV-004
     surface: pack.yaml / pack_version
     core: required for built-in packs only (pack_descriptor.py:143-148)
     service: required at every level
     rationale: ...
     ruling: open-questions Q13 (2026-10-18); R4 (2026-10-19)
     status: open        # open | aligned | retired
     since: 2026-10-19
     evidence: [...]     # core file:line, charter file, VENDORED.md change id
   ```

2. **Use exactly these ids.** WP08 cites them. Status is `open` for all.

| Id | Surface |
|----|---------|
| DIV-001 | An action that is not a graph node answers 404 (contract CHANGELOG Provisional, `action_not_found`; link the line) |
| DIV-002 | The null-label scope edge (contract CHANGELOG Provisional; link the line) |
| DIV-003 | `pack_level_missing` is a diagnostic at every level and for a pack with no `pack.yaml` (contract CHANGELOG Provisional; Q13) |
| DIV-004 | `pack_version` is required at every level (R4) |
| DIV-005 | Unknown `pack.yaml` keys are warned (`pack_unknown_key`), not refused (R5) |
| DIV-006 | Retired `pack.yaml` fields are warned like any unknown key (R13) |
| DIV-007 | Edge endpoints are URNs everywhere; a bare local id is refused (R6) |
| DIV-008 | The fragment header fields are refused (R7) |
| DIV-009 | Shard `generated_by` is optional (R8). The service is laxer than core here |
| DIV-010 | The lenient conversions are dropped: scalar `role`, `schema_version: "1"`, role lowercasing, string-to-integer (R9) |
| DIV-011 | Fragment node kinds have one name per concept. `actions` and `anti_patterns` are accepted; `mission_steps` and `mission_type` are refused (R14) |
| DIV-012 | URN kinds: core's `glossary` and `glossary_scope` are refused (R15) |
| DIV-013 | Nested sections are closed where core's pydantic ignores extras |
| DIV-014 | Nested tactics under `built-in/tactics/<sub>/` are validated; core's `validate_pack` skips them |
| DIV-015 | The fragment relation alias `extends` is refused (P2) |
| DIV-016 | The asset MIME check uses a fixed extension table, not the platform's (P8) |
| DIV-017 | Validate mode collects every finding; core stops at an invalid `pack.yaml` (research R3, rule row P14) |
| DIV-018 | The `impacts` relation and the edge `impacts` strength are modeled. Core has neither yet (ADR-0009 D1, ADR-0014 D-2: the contract leads and core aligns at the cutover; VENDORED C7, C12) |
| DIV-019 | Edge `provenance`, a merge-time marker in core, is not authored and is refused (P10, R7's principle; VENDORED C16) |

3. **`docs/conformance/divergence-register.md`** is the human-readable page.
   - One section per entry: core's behavior, the service's behavior, the rationale, the ruling,
     the status, and evidence as file:line.
   - The introduction states that this is **the** register ADR-0016 decision 4 means, and that
     expected answers are never hand-edited: a deliberate difference goes here.
   - Cross-link it with `contracts/packs/VENDORED.md`.
   - Add a short "Consumers" note. #72's conformance gate reads `divergences.yaml`, and so does
     #115's doctor parity test, whose design is in the mission plan,
     `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/plan.md`, section "#115 doctor parity test
     design". Link that section (FR-016).
   - Add the new section to `docs/_meta/sections.yaml`, the nav source of truth, following how the
     existing sections are declared. Add a section `index.md` only if the house pattern needs one.

### T043 — The ADR-0017 dated amendment

**Steps**: add a section `## Amendment 2026-10-19 (operator rulings)`, following the house pattern
(see how ADR-0005 decision 9 was amended). Record:

- `pack_version` is required at **every** level, per open-questions Q13. Q13 holds over decision 2's
  "a built-in pack declares `pack_version`" (R4).
- The dropped conversions: the ADR's two, plus role lowercasing and string-to-integer (R9).
- The fragment header fields are not authored (R7). Endpoints are URNs only (R6). Unknown and
  retired `pack.yaml` keys are warned (R5, R13). Fragment node kinds have one name per concept
  (R14), with 15 URN kinds (R15). `generated_by` is optional (R8). The `impacts` relation is
  modeled per ADR-0009 (C7, C12). Edge `provenance` is not authored (P10).
- **Corrections to Context.**
  - Corpus: 275 artifact files under core's scanner, plus 14 shards, 4 fragments, 2 `pack.yaml`
    and 4 `org-charter.yaml`, gives 299. At the pinned commit the service classifies all 516 YAML
    files: 409 mapped and 107 excluded.
  - The lost-validator list gains `routing_priority`, `max_concurrent_tasks`, lenient adherence
    and the retired-field rejections.
- Update `updated:`, and link the register and `VENDORED.md`.

**Gates**: run `python3 scripts/docs/check_docs.py all`, `npx --yes markdownlint-cli2@0.18.1` and
`npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`, and get all three clean.
- The wordlist, `docs/_meta/spelling-words.txt`, belongs to WP10, which runs later in another lane.
  If cspell flags a genuine term, record it in the evidence for WP10 to add, and do not edit the
  wordlist here.
- If `npx` cannot reach the registry, record that in the evidence, and the orchestrator runs the
  gates at closeout.

## Definition of Done

- [ ] `divergences.yaml` holds DIV-001 to DIV-019 exactly, with the documented schema, and the
      register page is cross-linked with `VENDORED.md` and in the nav.
- [ ] The ADR-0017 amendment cites Q13, every ruling and P10, and carries the corrections.
- [ ] The docs gates are clean, or their unreachable status is recorded.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
