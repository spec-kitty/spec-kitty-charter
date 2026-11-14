---
work_package_id: WP08
title: Records, docs and closeout material
dependencies:
- WP03
- WP07
requirement_refs:
- FR-021
- FR-022
- NFR-001
- SC-005
- C-002
- C-004
- C-005
- C-006
- FR-018
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T040
- T041
- T042
- T043
- T044
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/term-id-map.md
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/open-packs-draft-glossary-contexts.md
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/closeout.md
execution_mode: planning_artifact
owned_files:
- docs/api/service-contract.md
- docs/conformance/divergence-register.md
- docs/conformance/divergences.yaml
- docs/plans/pack-ecosystem-work-breakdown.md
- docs/plans/next-slices-2026-11-12.md
- docs/plans/roadmap.md
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/term-id-map.md
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/open-packs-draft-glossary-contexts.md
- kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/closeout.md
role: documentarian
tags: []
tracker_refs: []
---

# Work Package Prompt: WP08 - Records, docs and closeout material

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load scribe-sally`, or read `scribe-sally.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`
- `plan.md`: IC-07
- `research.md`: R4, R11 and R16
- `research/squad-post-plan.md`

Also read `docs/development/issue-tracker.md` (Rules 1, 2 and 4) and the grounding note,
`docs/development/notes/glossary-contexts-grounding.md`, its dispositions table.

## Objective

Make the records say what the service now does (FR-021), and prepare the closeout material (FR-022, SC-005):

- the open-packs draft issue;
- the term-id map;
- the issue lines the orchestrator and the closing PR use.

Write in the repository's documentation style: short sentences, an Oxford comma, no marketing words. Every page keeps
its front matter, and its `updated` is bumped.

## Subtasks

### T040: `docs/api/service-contract.md`

#106's acceptance criterion: "The decision is recorded, with its rationale … in `docs/api/service-contract.md`. If it
becomes an operation: its shape is drafted (path, schema, refusals), the contract scenarios are listed, and the corpus
cases are named."

- Update the R12 row or section. Say that R12 is served:
  - the 6 glossary operations;
  - the 3 vocabulary operations, with their paths, schemas and refusals;
  - the scenarios: `glossary.feature`, `effective-glossary.feature`, `vocabulary.feature` and
    `activation-glossary-pack-refused.feature`;
  - the corpus and planted cases from WP03.
- Cite ADR-0049, ADR-0074 decision 3 and ADR-0076.
- Update the operation lists on that page.

### T041: The divergence register

Edit both `divergence-register.md` and `divergences.yaml`, and keep them in step; `DivergenceRegisterTest` checks it.

| Entry | Change |
| --- | --- |
| DIV-012 | Charter's URN kinds now include `glossary_context` and `term`. Core's `glossary:` stays refused. |
| DIV-106 | The vocabulary walk: charter serves the vocabulary in force (ADR-0076), and core's `glossary_scopes` are retired (ADR-0049 d11). |
| DIV-211 | Presets carry `activated_glossaries`. |
| The lenient-conversions entry that cites `glossary-pack.schema.json` | Cite `glossary.schema.json`, and say what changed. |

Read each entry first. Change only what this Mission changed.

### T042: Plans

- `docs/plans/pack-ecosystem-work-breakdown.md`:
  - item 4 ("the `glossary` kind …") is done, delivered by this Mission and ADR-0076;
  - "Proposed issues (not filed) #1" is delivered by #106;
  - P5 (the glossary migration) points at the open-packs draft.
- `docs/plans/next-slices-2026-11-12.md`: in slice A2's table and state line, #106 is delivered; F's "glossary joins
  it when #106's Mission lands" becomes true.
- `docs/plans/roadmap.md`: the A2 row.

### T043: Research material

- **`research/term-id-map.md`**: the 139 surface-to-id rows of the example pack `spec-kitty-core`, from WP02's review
  notes. Regenerate them from the fixture if the notes are incomplete. Add a one-paragraph rule statement
  (research R4).
- **`research/open-packs-draft-glossary-contexts.md`**: a draft issue for `spec-kitty/spec-kitty-open-packs`, with
  placeholder `open-packs#TBD-glossary-contexts` (charter: the orchestrator files it). It has:
  - a title;
  - a context paragraph (ADR-0049, ADR-0076, this Mission and the charter release that carries it);
  - what to migrate: every `glossary_packs/*.glossary-pack.yaml` (the four glossaries the grounding note names:
    `spec-kitty-core`, qa, event-driven and internal) to `glossaries/<id>.glossary.yaml`;
  - the field moves, and the term-id rule, with the map for `spec-kitty-core`;
  - presets: `activated_glossary_packs` becomes `activated_glossaries`;
  - shards: `glossary_pack:` becomes `glossary_context:`;
  - the acceptance criteria: the content passes charter's `glossary` schema, and the drift watch is green;
  - a note that this repository's `packs/internal` and `packs/qa` load again once it lands.

### T044: Closeout notes

Write `research/closeout.md` with:

- **For the PR body**: `Closes #106`, `Closes #536`, and `Refs #94 #142 #361 #164 #537`.
  - A "Not done" list: any squad minor recorded rather than folded.
  - A note that the branch was rebased on 2026-11-14 and every commit re-signed, including the two CLI-written
    commits that were unsigned before (operator ruling).
- **For the orchestrator** (issue-tracker Rule 2):
  - tick #106 in #536;
  - tick R12 in #94;
  - file the open-packs draft and replace the placeholder;
  - comment on #518, the text written out, pointing its "glossary evaluator" at `avoid` and the taboo projection
    (`/activations/{id}/vocabulary/taboos`), and noting that its field names (`banned_synonyms`, `synonyms_to_avoid`)
    are gone.
- **Evidence**, per issue: the file or scenario that shows the behaviour. #106 is `glossary.feature`; #536's glossary
  clause is `effective-glossary.feature`.

## Validation

```bash
python3 scripts/docs/check_docs.py all
npx --yes markdownlint-cli2@0.18.1 docs/api/service-contract.md docs/conformance/divergence-register.md docs/plans/*.md
npx --yes cspell --no-progress docs/api/service-contract.md docs/conformance/*.md docs/plans/*.md kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/*.md
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :deployable:test --tests '*DivergenceRegister*'
```

## Definition of Done

- The docs checks are green.
- The divergence register's page and YAML agree.
- The draft issue, the term-id map and the closeout notes exist.
- No issue was filed or closed from this WP. The orchestrator does that.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Implement with**:
  `spec-kitty agent action implement WP08 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed, with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
