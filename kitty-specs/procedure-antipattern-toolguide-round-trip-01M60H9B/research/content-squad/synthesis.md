# Content squad synthesis — toolguide guide shape (charter #194 PR-A)

Point-cut: ad-hoc decision. Question: which generic forms (fixed blocks + freeform sections) fit toolguide guides, and how is that stored?
Cast: analyst-annie (empirical mining), doctrine-daphne (doctrinal role), architect-alphonso (storage design).
Operator inputs: structured guide = fixed blocks + freeform sections; PR-A builds the `markdown` content form; operate as if core does not exist (#186).

## Convergent
| # | Finding | Lenses | Disposition |
|---|---|---|---|
| C1 | One home per fact: title, summary, commands, references (artifact links) in toolguide YAML; H1 rendered from `title` (drifts in 7–8 guides); no fixed Commands block; overview must not repeat summary | An, D, Ar | accepted — data-model + ADR-0042 |
| C2 | Freeform `sections: [{title, body}]`, H3+ nested in body, ~71% of H2 content | An, D, Ar | accepted (Section.title per R4 — pending operator Q4) |
| C3 | Storage: Markdown companion (ADR-0039) parsed by the `markdown` form into a Guide value; YAML inline rejected (emitter has no scalar-style control); YAML companion forbidden (ADR-0039) | Ar (An, D neutral) | accepted |
| C4 | Guide prose links to styleguides/tactics are not relationships; do not mint; open-packs issue | D | accepted |
| C5 | `guide_path` relative to the toolguide file; both pinned variants diverge | An, D, Ar | accepted |

## Divergent / consequential
| # | Point | Adjudication | Disposition |
|---|---|---|---|
| X1 | Fixed set: analyst {overview*, setup, usage, cautions} by coverage ≥40%; doctrine {overview, when_to_use, out_of_scope, setup, verification, anti_patterns, known_limitations, references} by meaning | Both valid lenses; merged proposal offered to operator | pending operator Q1 |
| X2 | SC-001/SC-003 vs pin: a refused toolguide leaves its guide unowned, so `other:toolguides` stays red and known-failures cannot list unowned files | Confirmed against WholePackExpectation semantics (known-failures list files of a modelled kind) | pending operator Q2 |
| X3 | HTTP ToolguideRegistration carries guidePath but no guide content; C-001 forbids contract change | Confirmed structural gap | pending operator Q3 (raise on #194) |
| X4 | Gate compares companions as YAML (headings become comments) — must learn companion forms | Confirmed (WholePackCensus/MeaningComparator) | accepted — in scope |
| X5 | PackSchemas refuses unknown keywords and loads only `files`-rule schemas; guide schema needs registration path | Confirmed | accepted — in scope |
| X6 | ADR-0040 decision 1 amendment so a `files` rule may declare `markdown` (#195) | — | accepted — ADR amendment in PR-A |

## Smaller items (decided here)
- QA "Known limitation" text → `known_limitations` block if adopted, else freeform; drop the copy in YAML summary (open-packs issue).
- Drupal trailing attribution → `references`; QA-RESULT-INGEST provenance note → removed upstream (open-packs issue, not tolerated).
- Banners / `---` rules / emoji headings → not part of the sanitized shape; open-packs issue.
- Markdown form refusals (unclosed fence, CR/BOM, misplaced H1, indented heading, setext) — none occur at the pin.

## Operator decisions (2026-10-28)
- X1 fixed set → merged: overview (required), when_to_use, setup, usage, cautions, known_limitations, references; rest freeform. → accepted.
- X2 pin → model first; migrate the pinned packs with our own writer via a throwaway harness; land in open-packs; re-pin. → changed (neither offered option exactly).
- X3 HTTP → registration carries guide content or a URI, never a bare path; a URI is fetched and stored (allowlist, limits, SSRF protection). Authorized contract change. → changed.
- Naming → sections {title, content}; `content` replaces `description` where it carries the body; new shapes now, merged kinds (styleguide anti-patterns, templates, assets) listed on epic #186. → accepted.
