# Research: Procedure, anti-pattern and toolguide guide round trip

Two adversarial squads ground this mission (procedure `adversarial-squad-deployment`). Their reports and syntheses,
with every finding's disposition (accepted / changed / deferred_with_rationale), are kept verbatim:

- Pre-spec squad (researcher-robbie, architect-alphonso, analyst-annie, planner-priti): `research/squad/`,
  disposition table in `research/squad/synthesis.md`.
- Content squad on the toolguide guide (analyst-annie, doctrine-daphne, architect-alphonso):
  `research/content-squad/`, disposition table in `research/content-squad/synthesis.md`.

This file consolidates the resulting decisions.

## D1 — The sanitized-shape rule

- **Decision**: storage shapes are designed, not mirrored from the pack. Cross-kind rules (from the merged schemas,
  architect report §2): snake_case keys; required string `schema_version` `"1.0"`; kebab `id` equal to the file
  stem; `title` is the display label; `description` is the one "what is this" summary; `content` is a body; one
  shared `Reference` definition; `<role>_path` names a file relative to the artifact's file; `role` names who acts;
  one authority per fact; closed objects.
- **Rationale**: operator requirement (2026-10-28) and ADR-0036 (unknown fields refused).
- **Alternatives**: round-trip as-is (issue wording) — overruled by the operator.

## D2 — Procedure shape

- **Decision**: `procedure.schema.json` rewritten in place: `schema_version`, `id`, `title` (was `name`),
  `description` (was `purpose`), `entry_condition`, `exit_condition`, `steps[]` `{title, description?, role?,
  on_success?, on_failure?}` (`role` replaces `actor`), `anti_patterns[]` `{title, content?, bad_example?,
  good_example?}`, `references[]` `{type, id, when?, reason?}`, `notes?`, `applies_to_languages?`.
- **Rationale**: R4/R5/R7/R10 of D1; `content` for bodies (operator, 2026-10-28); ADR-0032 (roles, not profiles).
- **Alternatives**: keep `{name, description}` anti-pattern entries (R8 as first proposed) — replaced by the
  operator's `content` rule; `description` required on anti-patterns — made optional (`content?`) so bare
  "avoid" entries need no invented text.

## D3 — Procedure references are kept, not minted

- **Decision**: references are stored on the procedure but produce no relationship.
- **Rationale**: `ArtifactEdges` mints typed references as SUGGESTS while `procedure.graph.yaml` holds the same edge
  as `requires`; minting would create conflicting duplicates. Edge authority is #189's decision.
- **Alternatives**: drop references (pre-empts #189); mint them (conflicting edges).

## D4 — Anti-patterns

- **Decision**: no standalone anti-pattern kind or file (ADR-0037 decision 4). Procedure anti-patterns use the new
  entry shape; styleguide anti-patterns keep their merged shape (rename listed on #186). `anti_pattern.graph.yaml`
  stays expected-red for node tags under #236.
- **Rationale**: operator decisions (2026-10-28).

## D5 — Toolguide guide: structured, stored as a Markdown companion

- **Decision**: a `Guide` value with fixed blocks `overview` (required), `when_to_use`, `setup`, `usage`, `cautions`,
  `known_limitations`, `references`, plus freeform `sections[]` `{title, content}`; stored as an ADR-0039 companion
  whose content form is `markdown`; fixed blocks are H2 sections with canonical headings, freeform sections are the
  other H2 sections in authored order; the H1 is written from the toolguide `title`; `guide_path` is relative to
  the toolguide's file. The toolguide YAML stays the single home of title, summary, commands and artifact references.
- **Rationale**: content squad: 24 guides share no skeleton (no H2 in more than 3 guides) but show high-coverage
  meanings (overview 24/24, cautions 14, usage 13, setup 11); doctrine adds distinct meanings (when to use, known
  limitations, external references). Inline YAML rejected: the service's emitter has no scalar-style control, so a
  trailing space or tab turns a section into a quoted one-line string. A YAML companion is forbidden (ADR-0039).
- **Alternatives**: plain text companion (operator wanted structure); inline single block; sections only.

## D6 — The `markdown` content form

- **Decision**: model `MarkdownDocument(frontmatter?, title?, preamble, sections[{title, content}])`. H1 and H2 count
  only at column 0 and outside fences; a fence at any indentation hides headings; H3 and deeper stay in content.
  Equal in meaning = equal models (frontmatter by YAML meaning, strings exactly; blank lines between blocks and the
  final newline ignored). Refused as ambiguous: unclosed fence, CR or BOM, an H1 that is not the first block, an
  indented heading (1–3 spaces), a setext underline. Written canonically; `parse(write(d)) == d` is checked on write.
  Admitted for `files` rules (ADR-0040 decision 1 amended) and for companions (ADR-0039 widened). Structure is
  checked by a JSON Schema over the parsed model using standard 2020-12 keywords only (`PackSchemas` refuses unknown
  keywords).
- **Rationale**: all 72 Markdown files at the pin (24 guides, pack.md, 47 prompts and guidelines) are already
  canonical (no trailing whitespace, tabs, CR; closed fences); 15 guides hold heading-looking lines inside fences.
  One model serves the guides, #195's pack.md and later mission step prompts.
- **Alternatives**: a CommonMark library (new dependency, its own normalization); byte comparison (no structure).

## D7 — Getting green: migrate upstream, re-pin

- **Decision**: a throwaway test-scope harness converts the pinned procedures, toolguides and guides with this
  service's writer; the output is landed in spec-kitty-open-packs (issues per class of change) and `openPacksRef`
  moves to it. The known-failure list keeps only residual divergences.
- **Rationale**: operator (2026-10-28). A refused toolguide leaves its guide unowned, and the known-failure list can
  only pin files of a modelled kind, so listing would leave `other:toolguides` red.
- **Alternatives**: widen known-failure semantics; amend the success criteria.

## D8 — Guide over HTTP

- **Decision**: `ToolguideRegistration` carries exactly one of `guide` (the guide's Markdown) or `guideUri`; the bare
  `guidePath` input is removed (the server derives the stored path from the id). A URI is fetched by a guarded
  adapter and stored as if sent as content.
- **Rationale**: operator: the API must not assume shared filesystem access; fetch and store.
- **Security controls (NFR-004)**: HTTPS only; host allowlist from configuration (`charter.` root, ADR-0038), empty by
  default (URI registration refused until configured); DNS resolved and private, loopback, link-local and
  unique-local addresses refused before connecting and on every redirect, with the connection made to the checked
  address; at most 3 redirects; 10 s total; 1 MiB streamed cap; no cookies or credentials forwarded; typed refusals.
- **Alternatives**: store the URI only (declined); keep `guidePath` (no content to store).

## D9 — Delivery in two PRs

- **Decision**: PR-A1 = the `markdown` form and the ADR-0040 amendment; PR-A2 = the rest.
- **Rationale**: #195 needs the form early (parent session confirmed 2026-10-28).

## Supply chain (DIRECTIVE_051)

No dependency is added, upgraded or removed: the Markdown scanner is purpose-built and the fetcher uses the JDK
HTTP client. The `supply-chain-install-safety` controls therefore have no artifact to apply to; result recorded as
"no dependency change". Gradle resolution in this session goes through a Maven Central mirror (session init script
outside the repo, documented in PR #259); strict SHA-256 verification is unchanged.
