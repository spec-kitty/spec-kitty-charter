---
work_package_id: WP07
title: ADR-0040 and changelog
dependencies:
- WP01
requirement_refs:
- NFR-003
- C-001
- C-004
planning_base_branch: feat/192-glossary-pack-skill-round-trip
merge_target_branch: feat/192-glossary-pack-skill-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/192-glossary-pack-skill-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/192-glossary-pack-skill-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T024
- T025
- T026
phase: Phase 3 - Evidence and record
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
role: architect
authoritative_surface: docs/
execution_mode: planning_artifact
owned_files:
- docs/adr/0040-a-kind-binding-can-carry-companion-files.md
- docs/adr/README.md
- docs/changelog/2026-10-27-glossary-pack-and-skill-kinds.md
- docs/changelog/README.md
create_intent:
- docs/adr/0040-a-kind-binding-can-carry-companion-files.md
- docs/changelog/2026-10-27-glossary-pack-and-skill-kinds.md
tags: []
task_type: implement
---

# Work Package Prompt: WP07 — ADR-0040 and changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `architect`

## Objective

Record the one new decision of this mission and its user-visible effect (plan IC-05; NFR-003). 0039 is earmarked for
#227; check `main` and the open sibling PRs for the next free number before writing (expected: 0040).

## Branch Strategy

Planning base and merge target: `feat/192-glossary-pack-skill-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP07 --agent claude --mission glossary-pack-skill-round-trip-01M5YVEC`.

## Subtasks

### T024 — ADR-0040 "A kind binding can carry companion files"

Follow the frontmatter and section shape of ADR-0036/0037/0038 (`title`, `description`, `doc_status: draft`,
`updated`, `type: ADR`, `kind: ADR`, `related`, `audience`; Status **Proposed**, Context, Decision, Consequences,
Alternatives). Content:

- **Context**: ADR-0037 decision 4 left non-YAML content to the kind missions; #192's skill body (`body_path`) is
  the first; #193 (asset payloads, templates) and toolguide `guide_path` follow.
- **Decision**: the two hooks and `CompanionContent` (text now, bytes for #193); the path rule (relative, inside
  the pack, not `*.yaml`, one owner); read (violation, artifact skipped), upsert (companion first, old orphans
  removed, new-target collision refused), whole-pack write and load (modelled), digest (static schema-valid read,
  why: one source of truth, no constructor change; cost: one extra read per companion-bearing file); companion
  kinds are written only through the commit path (atomic by the directory swap).
- **Per-kind rows for ADR-0036's table** (as ADR-0036 asks a kind mission to record): `**/*.glossary-pack.yaml` —
  glossary-pack — YAML — sets none (`terms` is a sequence; unique `surface` a domain invariant) — refused: yes;
  `**/*.skill.yaml` — skill — YAML; its `body_path` file compares as text, byte for byte — sets none — refused: yes.
  Note the per-kind table in ADR-0036 still lists both as excluded; this record supersedes those rows.
- **Amends**: `KindBinding`'s "knows no path" (now: no path except its companions'); ADR-0037 decision 4's
  "glossary_pack (excluded)" is settled (schema and file token `glossary-pack`).
- **Consequences**: positive (pair kept, fingerprint covers bodies, #193 extends not rebuilds); negative (digest
  parses skill files; the revision digests read text and must move to bytes with #193; the gate does not pair
  companions yet — the `empty_kinds: skill` entry forces it); the qa, internal and event-driven fingerprints change
  once.
- Append the row to `docs/adr/README.md`'s index.

### T025 — Changelog

`docs/changelog/2026-10-27-glossary-pack-and-skill-kinds.md`, impact first, in the shape of the sibling entries
(e.g. the step-contract one on #191's branch): what changed for a pack author (glossary packs and skills are
validated by closed schemas and round-trip; unknown keys refused; a skill's body is read, written and fingerprinted
with it), for an operator (fingerprints/ETags of packs with these files change once), and what did not (no HTTP
contract change). Append its line to `docs/changelog/README.md`.

### T026 — Cross-check

After WP01 to WP06 land: re-read the ADR and changelog against the shipped code (hook names, refusal reasons, the
gate's `empty_kinds`), and run `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` on the changed files
and `npx cspell` on them. Fix anything they report.

## Definition of Done

- The docs checks pass with 0 findings on the changed files.
- The ADR's terms match the code exactly (a reviewer greps each hook name).
