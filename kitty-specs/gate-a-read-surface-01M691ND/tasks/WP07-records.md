---
work_package_id: WP07
title: Records
dependencies:
- WP06
requirement_refs:
- FR-017
- FR-018
- NFR-006
- C-001
- C-002
- C-006
- SC-005
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T032
- T033
- T034
- T035
phase: Phase 3 - Enforcement and records
agent: claude
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-31-gate-a-read-surface.md
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/CHANGELOG.md
- contracts/charter/scenarios/README.md
- contracts/lint/README.md
- docs/adr/0019-packs-resource-model-and-hypermedia.md
- docs/adr/0026-write-surface-is-functional-registration.md
- docs/api/service-contract.md
- docs/architecture/architectural-patterns-applied.md
- docs/architecture/packs-resource-model.md
- docs/plans/roadmap.md
- docs/changelog/2026-10-31-gate-a-read-surface.md
- docs/context/**
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP07 – Records

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

---

## Objective

Make every record match the shipped surface:

- consolidate the contract CHANGELOG's mission block under `0.1.0-SNAPSHOT` → `### Breaking`;
- add the two ADR notes (no new ADR: C-001);
- update the docs that still describe the per-kind routes;
- pass the docs checks.

## Context

- Spec FR-017, FR-018, NFR-006; C-001 (only an ADR-0026 D1.2 paths amendment and ADR-0019's implementation
  note); C-002 (version stays `0.1.0-SNAPSHOT`); C-006 (no provisional markings).
- **CHANGELOG conventions.** See `contracts/charter/CHANGELOG.md:1-80`, in particular #194's
  `### Breaking` entry: bullets "Removed:", "Added:", "Migrating a client:", "Scenarios:". The intro says
  every scenario change gets a line.
- **Core's seam.** Core does not consume the HTTP contract. Its seam is the in-process
  `charter.offering.api` (research §4). The seam line is therefore addressed to core's future generated
  client (#13, ADR-0021, ADR-0019's requests to core).
- **Coordination.** #125 also lands inside `0.1.0-SNAPSHOT` `### Breaking` (ruling 6a). Keep this mission's
  block self-contained under its own lead sentence, so a rebase only reorders blocks.
- **Records the squad found stale**:
  - `docs/architecture/architectural-patterns-applied.md:102`;
  - `docs/architecture/packs-resource-model.md:31` (status note);
  - `docs/plans/roadmap.md:60`;
  - `docs/api/service-contract.md` (the operation table and the read inventory rows).
- **Docs checks.** `python3 scripts/docs/check_docs.py all` (eight checks), markdownlint-cli2 and cspell.
  The CI site build is stricter about `related:` slugs: every one must be a real page, and `adr/README` is
  not one.

## Subtasks

### T032 — CHANGELOG consolidation

Rewrite the mission block that WP01–WP06 appended into one ordered entry under `### Breaking`. Its lead
sentence: "Gate-A read surface (mission gate-a-read-surface, #126, #175, #176; operator rulings of
2026-10-31). Breaking on the 0.x line, which ADR-0022 allows; no release carried the old shape."

Bullets:

- **Removed:** every per-kind operation and path, listed by operationId and path; the `Tactics` tag;
  `TacticNotFound`; `TacticId`; and the fingerprint declaration on refusals, writes, the contract document
  and the shared Problem.
- **Moved:** the 8 writes, old path → new path, with their operationIds unchanged; each answers
  `Artifact`.
- **Added:**
  - `getApiRoot`, `getOffering`, `listPackArtifacts`, `getPackArtifact`;
  - `Link`, `Links`, `Artifact`, `ArtifactSummary`, `ArtifactPage`, `ArtifactKindToken`, `ApiRoot`,
    `Offering`, `OfferingPack`, `RelationshipRegistered`;
  - the `Link` header on info;
  - `kind_not_found`;
  - a required `_links` on every JSON success schema;
  - the fingerprint required on verified read 200s.
- **Changed:** the `OfferingUnreadable` examples and description; the `PageInfo` citation (and narrowing,
  if any); and the lint rules: narrowed, plus five new.
- **Not yet linked:** registration is not advertised by a link in this minor (no `edit`).
- **Migrating a client (core's seam):** core's generated client (#13) is generated from this version,
  types `Links`, starts at `GET /`, follows `_links`, reads artifacts through the envelope, and pages with
  `pageCursor`. No core code consumes the removed routes today.
- **Scenarios:** the mapping from WP04, the new feature files, and the `CharterContractSuite` count before
  and after.

### T033 — ADR notes

1. **ADR-0019.** Replace the implementation note at :42-49, which says #139 deviates from D2. Say instead
   that #126 aligns the routes with D2, and that `_links`, the root and `/offering` (pack list only) landed
   in 0.1.0. Keep the decision text unchanged.
2. **ADR-0026.** Add a dated amendment note under D1.2 (2026-10-31, operator ruling 2 on #126). The paths
   are now `POST /packs/{packId}/artifacts/<kind>` and `PUT /packs/{packId}/artifacts/<kind>/{artifactId}`.
   "One family per kind" and every other part of D1.2 are unchanged. D1.3 (relationships) is unchanged.

### T034 — Docs

1. **`docs/api/service-contract.md`.** Update the operation table and the read inventory: R2 is answered
   by the artifact reads. R4 is partly answered by `GET /offering`, with presets deferred. R1, R3, R5 and
   R18 are deferred to the merge minor.
2. **Scenarios README.** The new steps (from WP03), the root path note (from WP05), and the 405 notes
   (from WP04). Check they are all present and accurate.
3. **Lint README.** Check that every rule added in WP01 and WP06 has its row.
4. **Architecture docs.** `architectural-patterns-applied.md:102`, `packs-resource-model.md:31` (status),
   and `roadmap.md:60`.
5. **New `docs/changelog/2026-10-31-gate-a-read-surface.md`.** Copy the frontmatter and shape of a recent
   entry such as `docs/changelog/2026-10-28-contract-serves-itself.md`.
6. **Glossary** (`docs/context/`). Check whether "offering", "served kind", "links" and "artifact envelope"
   need an entry or an update (DIRECTIVE_032). Add only what is missing.

### T035 — Docs checks and the removed-route grep

1. Run:
   - `python3 scripts/docs/check_docs.py all`
   - `npx markdownlint-cli2` on the changed files
   - `npx cspell` on the changed files

   Fix every finding in the changed files. Add genuine new terms to `cspell.json` only when they are
   real words of the domain.
2. Run `grep -rnE "/packs/\{packId\}/(tactics|directives|styleguides|paradigms|toolguides)|listPackTactics|getPackTactic" docs contracts --include=*.md`.
   Every hit must be historical (dated changelog entries, ADR history, earlier CHANGELOG sections) or
   corrected. List them in the activity log.

## Definition of Done

- [ ] One coherent mission block in the CHANGELOG, with the seam line and the scenario mapping.
- [ ] Both ADR notes in place; no new ADR file.
- [ ] Docs checks: 0 new findings in the changed files.
- [ ] The grep's remaining hits are all historical, listed in the activity log.

## Reviewer guidance

- Read the CHANGELOG block as core's maintainer would. Could they regenerate the client and migrate from
  it alone?

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP07 --agent claude --mission gate-a-read-surface-01M691ND`.
