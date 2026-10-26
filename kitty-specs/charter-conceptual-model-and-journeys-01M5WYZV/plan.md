# Documentation plan: charter conceptual model, journeys and scenarios

Design step of mission `charter-conceptual-model-and-journeys-01M5WYZV`. Implements `spec.md`
(FR-001 to FR-012) against `research/drift-audit.md`. No scope change from discover.

## Divio allocation

| Area | Explanation | Reference | How-to | Tutorial | Scenarios |
|---|---|---|---|---|---|
| Conceptual model | **required** | — | deferred | deferred | — |
| User journeys | **required** (one page each) | — | — | — | **required** |
| Glossary | — | **required** | — | — | — |
| Logical model | **required** (translation) | **required** (tables) | — | — | — |
| ADRs | **required** | — | — | — | — |

Deferrals: how-to and tutorial content describes using surfaces that do not exist yet (C-002,
C-003); they follow the slices that build them.

## Navigation hierarchy

```text
docs/
  context/
    README.md                     (hub: adds conceptual model + journeys)
    conceptual-model.md           (explanation, meaning only; appendix A: research findings)
    user-journeys/
      README.md                   (hub: journey index, persona × journey table)
      01-getting-started.md … 11-human-reviews.md
    domain.md                     (kept; points to the conceptual model and glossary)
  glossary/                       (generated pages from site/.contextive/definitions.yaml, now authored here)
  architecture/
    logical-model.md              (concept → representation; absorbs the representation notes)
    resolution-model.md, offering-and-activation.md, service-lifecycle.md, descriptors.md
                                  (drift fixes only)
  adr/
    0028-impacts-is-value-impact-tension-is-the-relationship.md (new)
    0029-charter-glossary-is-owned-here.md                       (new)
    0011, 0012, 0013, 0020, 0026, 0027 (amendments); 0009, 0014 (superseded-in-part notes)
  plans/open-questions.md         (drift fixes #26, #27; core ADR 2026-10-03-1 amendment recorded)
contracts/charter/scenarios/journeys/journey-NN-<slug>.feature (11 files)
```

Journey slugs: 01 getting-started, 02 adopt-a-pack, 03 plain-language-request, 04 org-house-rules,
05 remove-python, 06 publish-a-pack, 07 add-a-mission-type, 08 resolve-a-step, 09 upgrade-packs,
10 host-centrally, 11 human-reviews.

## Templates

**Conceptual model page**: frontmatter per docsite rules (`type: Context`, `kind: Explanation`,
audience: governance-lead, pack-author, charter-service-contributor). Sections: guiding principles;
the charter (offering, active charter); packs and derivation; the three levers (requirement,
integrity, removal vs deactivation); artifacts (rules, resources, paradigms, procedures);
relationships (`supports`, `tension`); mission types and roles; profiles (selection order, fallback,
pin, ethos, operator); values and creeds; the active charter over time (activation context,
upgrades, local and hosted); open-core boundary; where this goes next (link to logical model).
Each concept: what it is, what it is for, how it relates, what it is not. One concept diagram
(Mermaid flowchart) at the top. Changed rules link their ADR.

**Journey page**: frontmatter (`type: Context`, `kind: Explanation`, audience = the persona).
Sections: Persona and goal · Concepts used (links into the conceptual model) · Steps · Diagram
(Mermaid `sequenceDiagram` or `flowchart`) · Service operations (table: operation, status
exists / envisioned / missing, source) · Today (how core does it, gaps) · Scenarios (link to the
`.feature` file).

**Scenario file**: line 1 `@contract @journey-NN`; line 2 `# Journey: docs/context/user-journeys/NN-<slug>.md`;
`Feature:` title = journey goal; scenarios use the existing step vocabulary; any scenario needing
a missing surface carries `@WIP` (per scenario, not per file). New steps are not added in this
mission.

## Decisions (ADR-shaped)

Folded from the plan-point review (architect-alphonso, reviewer-renata, 2026-10-26).

**D1. Glossary: keep the generator, own its source (ADR-0029).** `site/.contextive/definitions.yaml`
becomes the authored source; `site/tools/import_core_glossary.py` and anything naming it are removed;
`docs/glossary/**/README.md` stay generated and are regenerated and committed by G6 (no diff after a
second run). G6 also rewrites every "imported" wording: the `definitions.yaml` header,
`check_docs.py` comments and the alias reason, `sections.yaml` glossary purpose, `doc-rules.yaml`,
`terminology.yaml`, `docsite.md` §Glossary, ADR-0003 (by a clarification note). The `terms` exemption for
generated pages stays (reason text changes). Superseded identifiers become aliases only where they are
not plain words (`in_tension_with` yes, `impacts` no); G6 runs `terms` over the whole tree and adds
`allow` entries for historical ADR text, and runs `wordlist` into `spelling-words.txt`.

**D2. New ADRs, not bundled amendments.** The repository's ADR rule keeps an accepted decision
immutable; a reversal is a new record. Each new record cites the concept-session ruling (operator,
2026-10-26) and each record it supersedes in part gets an inline "Superseded in part by ADR-00NN"
annotation at the changed point plus an index status suffix in `docs/adr/README.md`:

| ADR | Decision | Supersedes in part |
|---|---|---|
| 0028 | `impacts` is an artifact's per-value list; `tension` is the relationship between two rules and may coexist with one structural relationship on the same pair | 0009 (relation naming), 0014 (D-2 naming), 0027 (one relationship per pair), 0025 (`impacts` on the relationship) |
| 0029 | This repository owns the charter glossary | 0003 (glossary import, by clarification) |
| 0030 | Removal (pack-wide, by a derived pack; `open` and `extendable` removable, `locked` protected) is distinct from deactivation (per project); role locks and pins live in the pack-root policy file | 0012 (D1 table, D2 file contents), 0026 (§2 "removal is deactivation") |
| 0031 | Requirement is authored as a preset; the required set is derived from it | 0012 §7 (`required_*` as the authoring form); compatibility reading of `required_*` and how a governing preset is told from a convenience preset: **open questions** |
| 0032 | Steps name roles; profile selection is role fit → specialization (lineage + `routing-priority`) → declared fallback → not-found refusal; a derived pack may pin the profile for a role (specializations still fit) | — (new); tie handling is an **open question** (return all candidates vs random with warning) |
| 0033 | The active charter: local `activation.yaml` is the source of truth, written through to the service; local wins visibly on a cold-start divergence; it names pack ids and versions; upgrades are opt-in per project and the update is the acknowledgement; outdated packs get a notice | 0013 (single ledger wording), 0020 (empty activation default, service wins on divergence) |
| 0034 | Deployment options: local (spawned by the CLI, offline) and hosted (configured endpoint, standard login token, fails closed); open-core vs commercial boundary | 0011 (loopback-only); records the core ADR `2026-10-03-1` amendment core owes (no core change) |

**D3.** No in-place amendments beyond the inline "superseded in part" annotations above.

**D4. Logical model marks undecided representation.** G8 labels every representation choice not backed by
an ADR as "candidate, undecided" (endpoint paths, `ExecutorRole`, the `CharterArtifact` home); no
contract paths are introduced (C-003).

**D5. `@WIP` exclusion via `@ExcludeTags("WIP")`** on `CharterContractSuite`, combined with the existing
`@IncludeTags("contract")`; documented in the scenarios README; one `contracts/charter/CHANGELOG.md`
line. G5 also runs a Cucumber dry run over the journey files to show every non-`@WIP` step is defined,
and checks no non-`@WIP` scenario touches a surface the journey page marks missing.

**D6. Journey scenarios live in `scenarios/journeys/`**; tags `@contract @journey-NN` on the feature,
`@WIP` per scenario; the `# Journey:` comment sits between the tag line and `Feature:`; the Feature
title names the journey.

**D7. The working notes become the formal page in place**; research findings become appendix A;
representation notes move to `logical-model.md`.

**D8. Personas.** Journeys map to existing persona pages only: 01, 02 individual-developer;
03 non-engineer-developer; 04, 05, 10, 11 governance-lead; 06, 07 pack-author; 08 contract-consumer;
09 core-maintainer and individual-developer. No new persona pages.

**D9. Mission types** are stated as both: offered and activated through the same lever as other
artifacts, and modelled as their own concept with their own domain model and endpoints.

## Cross-link strategy

- Conceptual model ↔ glossary: each concept links its glossary term; glossary entries link back.
- Journey → concepts (links) → glossary; journey → scenario file; scenario file → journey (comment).
- Conceptual model → logical model ("how this is represented"); logical model → conceptual model per
  section ("what this means").
- Changed rules → ADR / amendment.

## Generation slices (for the generate step)

| Slice | Content | FRs |
|---|---|---|
| G6 | Glossary ownership (ADR-0029, tooling and wording, definitions.yaml terms, regenerate) | FR-005 |
| G7 | ADR-0028 to 0034, inline annotations, ADR index | FR-007 to FR-011 |
| G1 | Conceptual model page (formal), appendix A, links to glossary and ADRs; context hub retitle | FR-001, FR-012 |
| G2 | Journey hub (linked from the context hub) + journeys 01–04 | FR-002 |
| G3 | Journeys 05–08 | FR-002 |
| G4 | Journeys 09–11 (journey 10: commercial capabilities as a one-line boundary note) | FR-002 |
| G5 | Scenario files 01–11, `@ExcludeTags`, README, contract CHANGELOG, dry run | FR-003, FR-004 |
| G8 | Logical model + drift fixes in architecture pages + inbound links (after G7) | FR-006 |

Order: G6 and G7 in parallel; then G1; then G2–G5 and G8 in parallel.

## Validation

- `python3 scripts/docs/check_docs.py all`, markdownlint-cli2, cspell, `npm run validate` (doc-kitty
  frontmatter schema) where the site toolchain is available.
- `./gradlew :deployable:test --tests '*CharterContractSuite*'` plus the dry run.
- NFR-002: grep the conceptual model for backticked identifiers and file names outside its pointer
  section and appendix.
- SC-002: the enumerated concept list (appendix B of the conceptual model) checked against
  `definitions.yaml`.
- SC-004: a fresh-context reviewer subagent answers the five sample questions from the page alone; answers
  recorded in the mission directory.
- Adversarial review squad on G1, G7, G8 before the PR leaves draft.
