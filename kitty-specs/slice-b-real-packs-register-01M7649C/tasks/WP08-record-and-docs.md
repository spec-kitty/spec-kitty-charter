---
work_package_id: WP08
title: ADRs, how-to, architecture, glossary, usability script
dependencies:
- WP07
requirement_refs:
- FR-012
- FR-017
- NFR-003
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: c431ce05069d47cc6ed284c24e2259b752b01ae9
created_at: '2026-11-12T06:26:33.293052+00:00'
subtasks:
- T036
- T037
- T038
- T039
- T040
phase: Phase 5 - The record
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/guides/enforce-org-presets.md
- docs/changelog/2026-11-11-slice-b-real-packs-register.md
execution_mode: code_change
model: sonnet
owned_files:
- docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md
- docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md
- docs/adr/0031-requirement-is-authored-as-a-preset.md
- docs/adr/0065-pack-registration-replaces-the-whole-pack.md
- docs/adr/0019-*.md
- docs/adr/0040-content-form-and-payload-kinds.md
- docs/adr/0072-*.md
- docs/adr/README.md
- docs/guides/enforce-org-presets.md
- docs/guides/compose-packs.md
- docs/guides/README.md
- docs/architecture/logical-model.md
- docs/architecture/descriptors.md
- docs/architecture/descriptor-schemas.md
- docs/architecture/pack-schemas/pack-document.md
- docs/architecture/pack-registration-distribution.md
- docs/architecture/overview.md
- docs/architecture/resolution-model.md
- docs/architecture/diagrams/descriptor-governance.*
- docs/architecture/diagrams/descriptor-kinds-overview.*
- docs/architecture/diagrams/pack-write-model.*
- docs/context/domain.md
- docs/context/system-context.md
- docs/context/user-journeys/04-org-house-rules.md
- docs/context/user-journeys/05-remove-python.md
- docs/conformance/divergences.yaml
- docs/conformance/divergence-register.md
- docs/development/epic-186-sanitized-shape-follow-ups.md
- docs/glossary/**
- docs/changelog/2026-11-11-slice-b-real-packs-register.md
- contracts/packs/README.md
- site/.contextive/definitions.yaml
- scripts/usability/README.md
- scripts/usability/service-usability-run.sh
role: documentarian
tags: []
tracker_refs: []
---

# Work Package Prompt: WP08 - ADRs, how-to, architecture, glossary, usability script

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load scribe-sally` (or read `scribe-sally.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/`) and adopt
it. Read `docs/development/README.md` and an existing ADR and guide for the frontmatter and voice (short sentences,
active voice, no em-dash runs; the docs checks enforce frontmatter and links).

## Objective

The record matches what WP02 to WP07 built. Requirements: FR-012, FR-017, FR-018 (record), and squad A5, A6, A8, A13.

## Subtasks

### T036 - ADRs

- **New ADR.** Choose the number against `main` and the open PRs (`ls docs/adr`, and open PRs that add ADRs: ask the
  orchestrator if unsure; 0072 is the next free number on `main` at planning time). Title: "Real packs register as they
  are: the pack document, excluded files, and readers during a swap". Records the calls beyond the rulings, each with
  its alternatives: spec.md "Calls made by the mission" 1 to 9 and research.md D1 to D9 (exclusions not refused but
  reported and stripped; the `ignored` fallback; case variants; JSON body shape; negotiation defaults; one strong ETag
  for two representations with `Vary: Accept`; `active_charter` via the closed descriptor; the identity-checked
  re-read; `ConsistentRead` in one odd generation). Status: Proposed, decider the operator. Link the squad record.
- **ADR-0042 amendment "2026-11-11 (#451, #431)"** under Status: decision 8 names `README.md` (the held
  `PackDocument`); decision 9's tail is presets, `README.md`, manifest, policy, then retired files; the per-kind table
  row; `pack.md` and `PROVENANCE.md` retired; follow-up #206 superseded by retiring `org-charter.yaml` (its
  `required_<plural>` lists map to a governing preset). Annotate each changed point where it stands (as the 2026-10-30
  amendment did).
- Dated notes: ADR-0036 (decision 6 and the table rows for `ORG_CHARTER_SCHEMA_ERROR` and `pack.md`); ADR-0031 (#206
  superseded; close #206 is for the closeout, not this WP); ADR-0065 D1 ("holds the staged tree less the files it
  reports as not held"; readers during a swap read the aside); ADR-0019's packs map gains `/doc`; ADR-0040 (the
  `ignored` section of `file-kinds.yaml`). `docs/adr/README.md` index.

### T037 - How-to: enforce presets on an org's projects

`docs/guides/enforce-org-presets.md` (link from `docs/guides/README.md` and `compose-packs.md`): an org pack
(`pack_level: org`) ships a governing preset `presets/<id>.yaml` with `must_have: true` items; projects whose closure
holds the pack get the required floor (`RequiredFloor`; a denial is `409 artifact_required`); `policy.yaml` locks what
must not change (ADR-0063; `409 artifact_locked`); a project applies the preset with
`PUT /activations/{activationId}/presets/{presetId}`. A field-by-field table from `org-charter.yaml`: `required_<plural>`
→ the preset's `activated_<plural>` items with `must_have: true`; `activations` → a preset; `governance_policies` →
`policy.yaml`; `org_name`, `extends`, `interview_defaults`, `skill_namespace`, `schema_version` → dropped, each with a
reason. A worked example from charter's example packs (`org-base/presets/`). Facts: `research.md` §B "Enforcement today".

### T038 - Architecture, context, schema pages, diagrams

From research.md §A and §B "Docs": `logical-model.md` (:81-84, :110-114, :228, :329, :460-469), `descriptors.md`
(:85, :107, :185+), `descriptor-schemas.md:78`, `pack-schemas/pack-document.md:8,16`,
`pack-registration-distribution.md:195`, `overview.md:57`, `resolution-model.md:41`, `context/domain.md:69`,
`system-context.md:53`, `user-journeys/04-org-house-rules.md:78`, `05-remove-python.md:89`, `contracts/packs/README.md`
(:6, :17, :26, :52-53, plus the `ignored` section and the `text` wording). Diagrams: drop `OrgCharter` and
`active_charter` from the three `.puml` files and re-render their `.svg` (find the render command in
`docs/development/README.md` or `scripts/docs/`).

### T039 - Glossary, divergences, tracker

`site/.contextive/definitions.yaml:53,173,265,397` (Charter Pack, Organization Tier, `selected_<kind>`/
`required_<kind>`, Must-have mark), then regenerate `docs/glossary/**` with the generator `check_docs.py` names (never
hand-edit). Divergence register: core still reads `pack.md`, `PROVENANCE.md` and `org-charter.yaml`; update DIV-010
evidence and DIV-021 (`divergences.yaml:185-187, 352-364`), add an entry if the register's rules ask for one, mirror in
`divergence-register.md` (`DivergenceRegisterTest` pins ids). `epic-186-sanitized-shape-follow-ups.md:51,57`: resolved.
`docs/changelog/2026-11-11-slice-b-real-packs-register.md` per the changelog conventions.

### T040 - Usability script

`scripts/usability/service-usability-run.sh:419-432` filters violations with code `unmodelled` as the #451 workaround;
`scripts/usability/README.md:90` lists the files. Remove the workaround (the canonical model: no tolerance for the old
shapes); the README says the run's registration checks pass once open-packs applies `open-packs#TBD-readme-doc` and
`open-packs#TBD-org-charter-preset` (placeholders, replaced by the orchestrator).

## Validation

```bash
python3 scripts/docs/check_docs.py all
npx --yes markdownlint-cli2 <changed .md files>
npx --yes cspell --no-progress <changed files>
export JAVA_HOME=/usr/lib/jvm/temurin-25 && ./gradlew --max-workers=1 verifyAll
```

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`. Implement with
`spec-kitty agent action implement WP08 --agent claude --mission slice-b-real-packs-register-01M7649C`. Commits `docs(…):`,
signed, no model trailers.

## Definition of Done

Docs checks green; no page names `pack.md`, `PROVENANCE.md`, `org-charter.yaml` or `active_charter` as current
(historical records and ADR history excepted); the how-to exists and is linked.
