---
affected_files: []
cycle_number: 1
mission_slug: glossary-contexts-and-terms-01M7B1PP
reproduction_command:
reviewed_at: '2026-11-14T18:15:22Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 review feedback, round 1 (reviewer-renata)

**Verdict: changes requested.** One MEDIUM finding. Everything else verified against lane-f and is sound.

## MEDIUM-1: architecture docs still describe the glossary kind as `glossary_pack` and "not built"

FR-021 asks that the records and docs say what the service does, and the charter rule is that the old shape is
gone, with no legacy description beside the new one. WP08 already replaced `pack-schemas/glossary-pack.md` with
`glossary.md`, but these pages describe charter's current state and still give the retired shape:

- `docs/architecture/descriptors.md:352-362` (audience: pack-author). The "Glossary pack — `*.glossary-pack.yaml`"
  section lists the old fields (`provenance`, `surface`, `confidence`) and CRG `glossary_pack:<id>`. Its note says
  "Decided, not built yet … Today's `*.glossary-pack.yaml` and `glossary_pack:<id>` stay until then". The service now
  refuses that file by name. Rewrite the section as "Glossary context — `glossaries/<id>.glossary.yaml`": the fields,
  the CRG `glossary_context:<id>` and `term:<context-id>/<term-id>`, and a link to
  `/architecture/pack-schemas/glossary/`. Drop the "not built" note. Fix the anchor the descriptor-schemas link uses
  if the heading changes.
- `docs/architecture/logical-model.md:662-671`. The heading "Skills, glossary packs, assets and templates" and "A
  **glossary pack** … Accepted, not built yet": say it is built (#106, ADR-0076). The table at line 297 is core's
  `ArtifactKind`, which is still correct for core. A one-line note that the service's kind is `glossary` would help,
  but it is optional.
- `docs/architecture/descriptor-schemas.md:116-124`. The heading "Mission-step contract, pack skill, glossary pack" and
  "Today's code and schemas still have the old shape": for the glossary this is no longer true. Charter has
  `glossary.schema.json`. Say so. The step-contract half of the note can stay as it is. Line 91 is a multi-kind
  "decided design" note: change the glossary part only.
- `docs/architecture/pack-registration-distribution.md:194` (the kinds list, which names `glossary_pack`) and `:202`
  (the "Decided, not built yet" note that names the glossary rename): put `glossary` in the list, and take the glossary
  out of the not-built note.
- `docs/architecture/diagrams/descriptor-mission-skill-glossary.puml:3-7`. The header says "not built yet" for
  ADR-0049. Mark the glossary part built. Re-render the SVG if the generated title changes.

These may stay, because they describe core or the past: `docs/api/python-package.md:47` (core's surface), the
triage and epic-186 notes, the grounding note, and the ADRs and changelogs. `descriptor-doctrine-kinds.puml:40` is
core-sourced decided design (LOW, fold in if convenient).

## LOW fold-ins (optional, do not block)

- `docs/architecture/composition-and-activation-rules.md:781`, W17 "Built (PR A0) … `skill`, `glossary_pack`": add
  "(`glossary` since #106)" so a reader does not take it as current.
- `docs/glossary/charter/README.md:332`: the `vocabulary` relation entry still says "glossary-scope node", and
  glossary scopes are retired (ADR-0049 d11).

## Verified, no change needed

- service-contract.md: the nine operation ids and tags (`Glossaries`, `EffectiveGlossaries`, `Vocabulary`), the paths,
  the slug grammar (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, max 64), both detail texts (`GlossaryReads`), `application/yaml`
  with no charset and the 406 Problem with no code, code point ordering, whole-context override, `GlossaryContextList`
  `packId`, the `activation` relation instead of `up`, `EffectiveTag` v3, the `ArtifactKindToken` enum, the scenarios,
  corpus case 19 and the eight planted packs all exist in lane-f.
- The divergence register page and YAML agree (DIV-010, -012, -106, -211). `NodeKind.yaml`, `file-kinds.yaml` and
  the ADR-0049 amendment back them.
- term-id-map.md: all 139 rows match the lane-f fixture exactly (name, id, status). They are sorted. Statuses are
  112, 15 and 12. The rule reproduces every id, and the maximum length is 28.
- The open-packs draft is actionable. The schema keys it names exist in lane-f.
- closeout.md is accurate and honest about the minors. It has no GitHub writes.
- `check_docs.py all` is green.
