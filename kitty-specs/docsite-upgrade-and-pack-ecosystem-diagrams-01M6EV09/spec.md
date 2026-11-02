# Mission Specification: Docsite on DocKitty 0.6.0 and pack-ecosystem diagrams

**Mission Branch**: `feat/docsite-upgrade-and-pack-ecosystem-diagrams`
**Created**: 2026-11-02
**Status**: Draft
**Input**: User description: "Docsite upgrade and pack-ecosystem diagrams: (1) upgrade the docsite's @spec-kitty/doc-toolkit from 0.5.0 to DocKitty 0.6.0 and adopt its relevant new capabilities where they fit (OpenAPI reference pages from contracts/, kind: Schematic schema pages); (2) bring the PlantUML architecture diagrams in line with the accepted ADR-0048 to ADR-0052 pack-ecosystem decisions."

## Intent Summary (confirmed 2026-11-02)

- **Primary actors:** docsite readers (pack authors, charter-service contributors, core maintainers); the maintainer who keeps the docs CI green.
- **Trigger:** DocKitty 0.6.0 shipped on 2026-11-02 and ADR-0048 to ADR-0052 were accepted the same day. The docsite still builds on 0.5.0, and its architecture diagrams draw the pack model those records change.
- **Outcome:** the docsite builds on DocKitty 0.6.0; the charter contract is browsable as API reference pages; every pack schema has its own page with a diagram; the architecture diagrams draw the decided pack-ecosystem design.
- **Rule that must always hold:** a diagram never draws an open question of ADR-0048 to ADR-0052 as decided.
- **Decisions recorded:** the diagrams are redrawn to the decided design, replacing today's picture (DM-01M6EV0R4SHXB96AP33N7ZMDAY); adopt both OpenAPI reference pages and Schematic pages (DM-01M6EVF0XBYJRQR1EQTZJG3HTW); switch the docsite to build-time diagram rendering against the pinned PlantUML service (DM-01M6EVZQX7RRC4CM27JG3ZD2JX).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The docsite builds on DocKitty 0.6.0 (Priority: P1)

A maintainer merges a docs change; the docsite builds, passes its link and OKF checks, and deploys from `main`, on DocKitty 0.6.0.

**Why this priority**: every other story depends on the 0.6.0 toolkit, and a broken docs build blocks every docs PR.

**Independent Test**: the docs-site workflow on a pull request builds the site on 0.6.0 and passes every check it ran on 0.5.0.

**Acceptance Scenarios**:

1. **Given** the docsite pinned to 0.5.0, **When** the pin moves to 0.6.0, **Then** the pull-request build, the built-link check and the OKF check pass, and the lockfile resolves `@spec-kitty/doc-toolkit` to exactly 0.6.0.
2. **Given** an existing page, link, ADR or changelog entry that the 0.5.0 site published, **When** the 0.6.0 site is built, **Then** it is still published at the same URL.

---

### User Story 2 - A contract reader browses the charter API on the docsite (Priority: P2)

A pack author or client developer opens the docsite and reads the charter contract's operations, schemas and responses as reference pages, instead of opening the OpenAPI YAML in the repository.

**Why this priority**: the contract is the service's public surface; reading it in the repository is the most common friction for a contract reader.

**Independent Test**: the built site has API reference pages for the charter contract, and the published raw contract file is the single-file bundle that the repository's contract bundler produces, with a checksum that verifies.

**Acceptance Scenarios**:

1. **Given** the charter contract in the repository, **When** the site is built, **Then** its operations are browsable under the site's API reference section, marked unreleased while the contract version is a snapshot.
2. **Given** a reader who wants the exact contract, **When** they download the published raw file and its checksum, **Then** the checksum verifies.

---

### User Story 3 - A pack author reads a pack schema as a page (Priority: P2)

A pack author looks up a pack descriptor or artifact schema on the docsite and sees its description, a diagram, its properties and its source.

**Why this priority**: pack authors write against these schemas; a page per schema replaces reading raw JSON Schema.

**Independent Test**: the built site has one schema page per pack schema, each with a description, a diagram, a properties section and the source.

**Acceptance Scenarios**:

1. **Given** the pack schemas in the repository, **When** the site is built, **Then** each has its own page with description, diagram, properties and source sections.
2. **Given** a schema page whose diagram could not be rendered, **When** the site is built, **Then** the build fails; no schema page is published without its diagram.

---

### User Story 4 - A reader sees the decided pack-ecosystem design in the architecture diagrams (Priority: P1)

A pack author or charter-service contributor reads the architecture pages and sees diagrams of the decided design: named packs composed through `depends_on`, glossary contexts and terms, mission types and lifecycles as state machines, the event registry, and anti-patterns as authored artifacts.

**Why this priority**: the accepted records change the pack model; diagrams of the old model mislead every reader planning work against the decided design.

**Independent Test**: each redrawn or new diagram cites the ADRs it draws from, the committed SVG matches its source, no diagram draws an open question as decided, and the page text around each diagram no longer claims today's sources for it.

**Acceptance Scenarios**:

1. **Given** a diagram whose subject ADR-0048 to ADR-0052 change, **When** a reader opens its page, **Then** the diagram draws the decided design, names the ADRs it draws from, and the page says the code does not do this yet.
2. **Given** an element that hangs on an open question (for example the `gate` kind, or the `{kind}` path spelling), **When** it is drawn, **Then** it is visibly marked as pending a ruling.
3. **Given** a redrawn diagram source, **When** the docs checks run, **Then** its committed rendering is current.

### Edge Cases

- A DocKitty 0.6.0 behaviour change (for example `.html` redirect keys, upper-case ADR id patterns, GitHub alert blockquotes rendered as asides) alters an existing page: the change is either intended and accepted, or corrected so the page reads as before.
- Build-time rendering also renders the site's Mermaid diagrams; the build must still succeed with every existing Mermaid diagram.
- The PlantUML render service is unavailable or not pinned in CI: the build fails loudly, it never publishes a schema page with a silently missing diagram.
- A pack schema with no description or an unresolvable reference: the build fails with the toolkit's schema error, it never publishes an empty page.
- A diagram whose subject the accepted records do not change stays as it is.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Build on DocKitty 0.6.0 | As a maintainer, I want the docsite to build on `@spec-kitty/doc-toolkit` 0.6.0 (pin, lockfile and CI notes) so that it gets the current toolkit's fixes and features. | High | Open | [build] | no — the lockfile must resolve exactly 0.6.0 |
| FR-002 | No regression in published pages | As a reader, I want every page, ADR, changelog entry and glossary page published on 0.5.0 to stay published at the same URL on 0.6.0 so that links keep working. | High | Open | [ratchet] | yes — paired with FR-001's lockfile check on the same build |
| FR-003 | Charter API reference pages | As a contract reader, I want the charter OpenAPI contract published as API reference pages, labelled unreleased while its version is a snapshot, so that I can browse the contract on the docsite. | High | Open | [build] | no |
| FR-004 | Raw contract and checksum | As a contract reader, I want the single-file contract bundle (the output of the repository's contract bundler, not a hand-made copy) and a checksum that verifies it published beside the reference pages so that I can fetch the exact contract. | Medium | Open | [build] | no |
| FR-005 | Schema pages for the pack schemas | As a pack author, I want one Schematic page per pack schema, with description, diagram, properties and source, so that I can read a schema without opening raw JSON. | High | Open | [build] | no |
| FR-006 | Build-time diagram rendering | As a maintainer, I want the docsite to render diagrams at build time against DocKitty's pinned PlantUML render service so that schema pages carry their diagrams and existing Mermaid diagrams still render. | High | Open | [build] | no |
| FR-007 | Diagrams draw the decided design | As a pack author, I want the pack-model diagrams whose subject ADR-0048 to ADR-0052 change (`pack-write-model`, `descriptor-kinds-overview`, `descriptor-governance`, `descriptor-doctrine-kinds`, `descriptor-mission-skill-glossary`) redrawn to the decided design so that they match the accepted records. The code-structure diagrams (`class-diagram`, `c4-components`) draw the service as built, which the records do not change; they keep their picture and only their stale "Procedure deferred pending #150" notes are corrected (ADR-0052 decision 1). | High | Open | [build] | no |
| FR-008 | Open questions stay marked | As a reader, I want every element that hangs on an open question of ADR-0048 to ADR-0052 visibly marked as pending a ruling so that no diagram presents an open question as decided. | High | Open | [build] | no — reviewed against each ADR's open-questions list |
| FR-009 | Diagrams cite their sources | As a reader, I want each redrawn diagram to name the ADRs it draws from, and its page to say the code does not do this yet, so that I know what the diagram represents. | Medium | Open | [build] | no |
| FR-010 | Renderings are current | As a maintainer, I want each redrawn diagram's committed rendering regenerated with the repository's pinned renderer so that the docs freshness check passes. | High | Open | [ratchet] | yes — the existing diagrams-current check already guards it |
| FR-011 | Discoverable new pages | As a reader, I want the API reference and schema pages reachable from the docsite navigation and from the architecture pages that discuss the contract and the schemas so that I can find them. | Medium | Open | [build] | no |
| FR-012 | Mission type and lifecycle diagram | As a pack author, I want one new diagram of a mission type as a declared state machine (steps, transitions with guards, emit actions, the lifecycle reference, step enhancements) embedded in the mission-type section so that the decided design of ADR-0050 has a picture; no existing diagram draws a mission type. | High | Open | [build] | no |
| FR-013 | Page text matches the diagrams | As a reader, I want the text around each redrawn or new diagram (source lines, status notes, the "Decided, not built yet" notes, today's built-in counts, and the regeneration instructions) updated so that no page claims today's sources or data for a diagram that now draws the decided design. | High | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Build time | The docs-site pull-request build takes at most 10 minutes longer than its 0.5.0 run, measured on the workflow's job duration. | Performance | Medium | Open |
| NFR-002 | Pinned dependencies | Every new external dependency of the docs build (render service, browser, toolkit peers) is pinned by exact version or digest; 0 floating tags or ranges. | Security | High | Open |
| NFR-003 | Fail closed | 0 pages published with a missing diagram, an empty schema section or a broken link; any such case fails the build, and a check after the build confirms every schema page carries a rendered diagram. | Reliability | High | Open |
| NFR-004 | Green checks | 100% of the existing docs checks (frontmatter, links, terms, indexes, markdown lint, spellcheck, diagrams-current, built links, OKF) pass on the final head. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Docs and docs CI only | No change to contracts, pack schemas or service code; only the docsite, its CI workflow, the diagrams and the docs pages change. | Technical | High | Open |
| C-002 | Accepted records are the source | The diagrams follow ADR-0048 to ADR-0052 as accepted. Where a decision rests on an open question, the element is drawn as the decision text shapes it and is visibly marked pending, citing the ADR and open-question number; it is never left out silently and never drawn as settled. | Business | High | Open |
| C-003 | Repository's own renderer | Committed architecture-diagram renderings are produced with the repository's pinned PlantUML jar, as the diagrams-current check expects. | Technical | High | Open |
| C-004 | Pages deploy unchanged | Publishing to GitHub Pages from `main` keeps working; a pull request still never deploys. | Technical | High | Open |

### Key Entities

- **Docsite**: the documentation site built from `docs/` with the DocKitty toolkit and published to GitHub Pages.
- **API reference page set**: the generated pages for one contract version, with its raw file and checksum.
- **Schema page**: a page documenting one pack schema: description, diagram, properties, source.
- **Architecture diagram**: a PlantUML source under `docs/architecture/diagrams/` with its committed rendering, embedded in an architecture page.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The docsite build on the final head runs on DocKitty 0.6.0 and passes 100% of its checks. — [build] · no-op passable: no
- **SC-002**: A reader can reach every operation of the charter contract from the docsite in at most 3 clicks from the home page. — [build] · no-op passable: no
- **SC-003**: Every pack schema (23 today) has a published page with a diagram. — [build] · no-op passable: no
- **SC-004**: Every pack-model diagram whose subject ADR-0048 to ADR-0052 change, plus the new mission-type diagram, draws the decided design, and 0 diagrams draw an open question as decided. — [build] · no-op passable: no
- **SC-005**: 0 URLs published by the 0.5.0 site are missing from the 0.6.0 site. — [ratchet] · no-op passable: yes — paired with SC-001 on the same build

## Brownfield findings (post-spec squad, 2026-11-02)

Two lenses ran against the real repository: researcher-robbie (toolkit and CI feasibility) and architect-alphonso (diagram inventory against the ADRs). Each finding is dispositioned:

- **Contract is split across files; the toolkit takes one document.** Accepted: FR-004 and US2 now name the bundle; the plan bundles it in the docs build.
- **"Byte for byte with the repository file" was unmeetable.** Accepted: restated against the bundle (US2, FR-004).
- **Schema files must sit inside the docsite directory.** Accepted: Assumptions; the plan adds a copy step before validation and build.
- **Browser rendering silently drops the schema diagrams.** Accepted: NFR-003 adds a post-build check; FR-006 already pins build-time rendering.
- **Build-time rendering needs a pinned browser for Mermaid.** Accepted: NFR-002 already requires pinning; the plan sets the exact versions.
- **`class-diagram` and `c4-components` draw built code the records do not change.** Changed: FR-007 now leaves them, except the stale #150 note.
- **No diagram draws a mission type, so User Story 4 could not be met.** Accepted: FR-012 adds one diagram.
- **Page text around the redrawn diagrams would still claim today's sources.** Accepted: FR-013.
- **C-002 could be read as "leave pending elements out".** Accepted: C-002 restated.
- **User Story 3 scenario 2 had no real schema to test against (no schema references another file).** Accepted: the scenario now tests the fail-closed diagram rule instead.
- **Labels, the second PlantUML version (render service vs repository jar), sidebar placement, the OKF gap and peer versions.** Deferred to the plan: implementation facts, not scope.

## Assumptions

- DocKitty 0.6.0 is published to GitHub Packages and installable by the docs CI job with its `GITHUB_TOKEN`, as 0.5.0 is.
- The two optional peers that the OpenAPI feature needs (`starlight-openapi@0.27.0`, `@astrojs/markdown-satteri@0.4.4`) are compatible with the docsite's Astro and Starlight versions; the plan confirms it.
- The charter contract (`contracts/charter/openapi.yaml`, OpenAPI 3.1) is the one contract published as reference pages; the offering and pack schemas are documented as schema pages, not API pages. Because the contract is split across files and the toolkit takes one self-contained document, the published contract is the bundle the repository's contract bundler already produces.
- The toolkit reads schema files only from inside the docsite's own directory, so the build copies the pack schemas in before it validates and builds; the copies are not committed.
- The API reference pages are not part of the toolkit's OKF export (a known toolkit gap, doc-kitty #257); the schema pages are.
- Build-time rendering may require a browser for Mermaid in CI; the plan settles how it is provided and pinned.
- Diagrams whose subject the accepted records do not change (for example `descriptor-agent-profile`, `descriptor-directive-example`) are reviewed but left as they are when nothing they draw changes.
