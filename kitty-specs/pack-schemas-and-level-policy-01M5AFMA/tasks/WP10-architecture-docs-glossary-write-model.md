---
work_package_id: WP10
title: Architecture docs, glossary and the write-model diagram
dependencies:
- WP07
- WP09
requirement_refs:
- FR-015
- NFR-004
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-schemas-and-level-policy-01M5AFMA
base_commit: b00010357bb5e4656e669d62b39248607e9b079a
created_at: '2026-10-19T20:41:36.274278+00:00'
subtasks:
- T044
- T045
- T046
- T047
history:
- at: '2026-10-19T19:05:00Z'
  event: created
  by: post-tasks squad (split from the docs WP; follows the load path)
agent_profile: scribe-sally
authoritative_surface: docs/architecture/
create_intent:
- docs/architecture/diagrams/pack-write-model.puml
- docs/architecture/diagrams/pack-write-model.svg
execution_mode: code_change
model: sonnet
owned_files:
- docs/architecture/descriptors.md
- docs/architecture/descriptor-schemas.md
- docs/architecture/logical-model.md
- docs/architecture/resolution-model.md
- docs/architecture/pack-registration-distribution.md
- docs/architecture/diagrams/pack-write-model.puml
- docs/architecture/diagrams/pack-write-model.svg
- docs/context/domain.md
- docs/context/system-context.md
- docs/plans/open-questions.md
- docs/guides/service-build.md
- docs/_meta/terminology.yaml
- docs/_meta/spelling-words.txt
role: implementer
tags: []
tracker_refs: []
---

# WP10 — Architecture docs, glossary and the write-model diagram

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `scribe-sally`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt. `docs/context/` is the terminology authority. Read the
**mission-wide rules** in [tasks.md](../tasks.md).

## Objective

Give every concept one authority in the docs, describing what landed in WP07 (FR-015 b-h, R14,
NFR-004):

- re-point the architecture pages at `contracts/packs/schemas/`;
- add the glossary rows and record the avoided synonyms;
- draw the write-model diagram;
- record the #48 follow-up;
- add the wordlist terms WP09 recorded. FR-016's parity design is in plan.md, and WP09 links it
  from the register.

House style: the existing `docs/` pages, with frontmatter, canonical terms and short sentences.

Read first:
- [plan.md](../plan.md): calls P1-P10 and "#115 doctor parity test design";
- [data-model.md](../data-model.md), section "Write model";
- `docs/conformance/divergence-register.md` (WP09) and `contracts/packs/VENDORED.md` (WP02);
- `docs/_meta/doc-rules.yaml`, which says which pages are generated and must not be hand-edited.

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json`.
- Implement with:
  `spec-kitty agent action implement WP10 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T044 — Architecture docs, `service-build.md` and open-questions

| Page | Change |
|---|---|
| `docs/architecture/descriptors.md` | Line 61, `pack_version` "(built-in)", becomes "every level (Q13; DIV-004)". The JSON Schemas in `contracts/packs/schemas/` are the structural authority, and this page explains them. Mention `pack_unknown_key` |
| `docs/architecture/descriptor-schemas.md` | Re-source it from `contracts/packs/schemas/` (ADR-0017), not core's hand-written YAML schemas. Fix the precedence clause at `:20-27`, `:49` and `:59`: the schemas win for structure |
| `docs/architecture/logical-model.md` | `:225-226`: endpoints are URNs only (DIV-007). Link the schemas |
| `docs/architecture/resolution-model.md` | `:59-62`: endpoint resolution is URN-only, and `unresolved_edge_endpoint` is no longer raised |
| `docs/architecture/pack-registration-distribution.md` | `:148`: the same `UNRESOLVED_EDGE_ENDPOINT` correction, plus `pack_unknown_key` |
| `docs/context/system-context.md` | `:53`: the pack schemas row points at `contracts/packs/schemas/` |
| `docs/guides/service-build.md`, "Change a dependency" | One sentence: a non-Spring module may import the Boot BOM as a platform to pin a transitive to the service's version, as `adapter-out-yaml` does for networknt's Jackson and slf4j |
| `docs/plans/open-questions.md` | Q13 gains a link to the ADR-0017 amendment and DIV-004 |

Then grep `docs/` for stale claims: `bare id`, `unresolved_edge_endpoint`, `(built-in)` next to
`pack_version`, and "hand-written YAML schemas".

### T045 — Glossary

**Steps**:

1. Add rows to **`docs/context/domain.md`** in the house table style:
   - **Pack finding**: one judgement on a pack file, with code, severity (blocking or warning),
     file, place and detail. A blocking finding on a load-path file becomes a refusal, and a
     warning becomes a diagnostic.
   - **Declared level** and **Configured level**.
   - The canonical node-kind names (R14):
     - **Mission step contract**, the one name; `mission_steps` is avoided;
     - **Action** (`action:<mission-type>/<action>`);
     - **Anti-pattern**;
     - **Mission type**.
2. `domain.md` says the core glossary wins. Add a sentence: since ADR-0015 this repository is
   canonical for the charter structure and the pack schemas. Keep the pointer to core for terms
   this repository does not own.
3. **Do not edit `docs/glossary/charter/index.md`.** `doc-rules.yaml` marks it as imported from core
   and generated. Record a core-glossary follow-up for the orchestrator: core's glossary should
   carry these terms, and spec-kitty#6403 already tracks one correction there.
4. Add the avoided synonyms to **`docs/_meta/terminology.yaml`**, so the `terms` check enforces
   them: `mission_steps` becomes `mission_step_contracts`. Follow the file's documented shape.

### T046 — The PlantUML write-model diagram

**Steps**:

1. Write `docs/architecture/diagrams/pack-write-model.puml`, a class diagram at C4 code level,
   using `!include _style.iuml`. It shows the authored pack files and their multiplicities, with
   canonical names only:
   - `Pack`;
   - `PackDescriptor`: `pack_version` required; `pack_level` and `requires_core` optional;
   - `GraphShard`;
   - `OrgFragment`: required for org packs, with no header fields;
   - `Edge`: URN endpoints, the relation, and `impacts` only on `impacts` edges;
   - `ArtifactFile`, with the nine schema'd kinds;
   - `AssetManifest`;
   - `OrgCharter`.

   Follow data-model.md "Write model".
2. Render the SVG with the **pinned** PlantUML jar (`scripts/docs/plantuml_pins.json`, SHA-256
   verified), as the other committed SVGs were rendered. Commit both files.
3. Link the diagram from `descriptors.md` and `descriptor-schemas.md`.
4. Run `python3 scripts/docs/check_docs.py plantuml` with `JAVA_HOME=/opt/jdk-25`.

### T047 — Follow-ups, the parity-design link and the docs gates

**Steps**:

1. Add an open question to `docs/plans/open-questions.md`: narrowing the refusal-code enum per HTTP
   response, for the next contract version (#48). Tell the orchestrator, who files the GitHub
   issue at closeout, together with the core-glossary follow-up from T045.
2. Add the genuine terms WP09 recorded in its evidence to `docs/_meta/spelling-words.txt`.
3. Run, and get every one clean:
   - `python3 scripts/docs/check_docs.py all`;
   - `python3 scripts/docs/check_docs.py plantuml`;
   - `npx --yes markdownlint-cli2@0.18.1`;
   - `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`.

   The wordlist is `docs/_meta/spelling-words.txt`; add only genuine terms. If `npx` cannot reach
   the registry, record it, and the orchestrator runs the gates at closeout.

## Definition of Done

- [ ] Every page in T044 is updated, and the grep for stale claims is clean.
- [ ] The glossary rows are in `domain.md`, the avoided synonyms are in `terminology.yaml`, and
      the generated glossary page is untouched, with its follow-up recorded.
- [ ] The diagram's `.puml` and `.svg` are committed and linked, and the plantuml check is clean.
- [ ] The #48 follow-up is recorded, and WP09's wordlist terms are added.
- [ ] All four docs gates report 0 errors.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Reviewer guidance (opus, doctrine-daphne lens)

- Check the terminology against `docs/context/`: canonical names only, and no new synonyms.
- Check that no page still names core's hand-written YAML schemas as the authority.
