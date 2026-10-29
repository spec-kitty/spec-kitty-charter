---
work_package_id: WP01
title: Markdown document model and codec
dependencies: []
requirement_refs:
- FR-005
- SC-006
- C-006
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: PR-A1 - Markdown content form
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownDocument.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCodec.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownRefusal.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCodecTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownDocumentTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCorpusTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/**
- service/packs/src/test/resources/markdown-fixtures/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Markdown document model and codec

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Build the parsed model and the canonical codec behind ADR-0040's `markdown` content form: a Markdown document is
read into `MarkdownDocument(frontmatter?, title?, preamble, sections[{title, content}])`, written back canonically,
and two documents are equal in meaning when their models are equal. Ambiguous Markdown is refused with a typed
refusal that names the line. This WP is pure model and codec — no file-kind table, store or gate wiring (WP02).

## Branch Strategy

Planning branch and final merge target: `feat/194a-procedure-antipattern-toolguide`. Execution worktrees are
allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Implement with
`spec-kitty agent action implement WP01 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
This WP belongs to **PR-A1**, which lands before the rest of the mission (sibling mission #195 rebases on it).

## Context

- Read: `research.md` D6 and D12, `data-model.md` "MarkdownDocument", ADR-0040 (`docs/adr/0040-content-form-and-payload-kinds.md`),
  ADR-0036 (equality of meaning).
- Existing style to follow: `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/` (package-private
  types, records with compact-constructor checks, javadoc on every type and member, NullAway, `Objects.requireNonNull`).
  Put the new types in a sub-package `infra.storage.markdown`; types used by WP02/WP05 are `public` within the module
  (the module is not exported beyond the service), everything else package-private.
- Corpus at the pin (`../spec-kitty-open-packs`, `openPacksRef` in `gradle.properties`): 24 toolguide guides,
  `packs/built-in/pack.md`, 47 mission-step `prompt.md`/`guidelines.md` (155 `.md` files exist in total; the corpus
  check covers every `*.md` under `packs/*/toolguides/`, `packs/*/pack.md` and `packs/*/missions/mission-steps/**`).
  Known shapes: guides have an H1 and H2 sections, no frontmatter; prompts have frontmatter and no H1; `pack.md` has an
  H1 and no H2; two prompts are empty files; MERMAID_DIAGRAMMING.md:212-217 nests 3-backtick lines in a 4-backtick
  fence; tasks `prompt.md:127` has an indented H3 inside a list; 26 thematic-break lines (`---`) sit in 5 guides.

## Subtasks

### T001 — `MarkdownDocument` model with construction invariants

**Purpose**: the value the form compares.

**Steps**:
1. `record MarkdownDocument(Optional<Map<String, Object>> frontmatter, Optional<String> title, String preamble,
   List<Section> sections)` with `record Section(String title, String content)`. Frontmatter is the loaded YAML
   mapping (use the existing `Yaml` loader settings of `infra.storage` — expose what you need through a small
   package-visible helper rather than duplicating settings).
2. Invariants in compact constructors, refused with `IllegalArgumentException` naming the field:
   - `title` and every `Section.title`: single line, non-blank, no leading/trailing whitespace.
   - `preamble` and every `content`: no leading or trailing blank line; may be empty; must not contain a line that
     the scanner would read as a column-0 H1/H2 outside a fence (reuse the scanner's fence tracker so the rule is one
     rule).
   - Lists copied immutably.
3. Equality: records already compare strings exactly; frontmatter compares as loaded YAML values (maps/lists/scalars
   — `Map.equals` on loaded values is the YAML meaning). Document that blank lines between blocks and the final
   newline are not part of the model.

**Validation**: `MarkdownDocumentTest` covers each invariant with a refused and an accepted neighbour.

### T002 — Line scanner: frontmatter, H1/H2, CommonMark fences, refusals

**Purpose**: parse text into the model without a third-party parser.

**Rules** (research D6, D12):
- Input must be valid UTF-8 text; refuse a BOM (`﻿` first char) and any `\r` (name the line).
- Frontmatter: only when line 1 is exactly `---`; ends at the next line exactly `---`; content loaded as YAML and must
  be a mapping (else refuse); an unterminated frontmatter is refused.
- Fences: a line whose first non-space characters are at least three backticks or at least three tildes opens a fence
  at any indentation (inside lists too); it closes on a line whose first non-space characters are the same character
  repeated at least as many times as the opener, with nothing else but spaces after it. Info strings are allowed on
  the opener only. While a fence is open, no line is a heading. End of input with a fence open → refuse ("unclosed
  fence opened at line N").
- Headings: `# ` (H1) and `## ` (H2) only at column 0 outside fences. `### ` and deeper are content. A heading line
  with 1–3 leading spaces before `#` or `##` → refuse (ambiguous); deeper indented headings are content (the list H3 at
  `prompt.md:127` must parse). Trailing `#`s on a heading (`## Title ##`) → refuse (ambiguous closing sequence).
- Setext: a line of `=` or `-` directly under a non-blank paragraph line is a setext heading in CommonMark → refuse,
  **except** that a line of three or more `-` preceded by a blank line is a thematic break and is content.
- An H1 that is not the first block (after frontmatter and blank lines) → refuse; more than one H1 → refuse.
- Preamble: text after the H1 (or after frontmatter when there is no H1) and before the first H2.
- Section content: lines after an H2 until the next H2 or end; trim leading/trailing blank lines (that is the
  canonical form, not a refusal).
- Empty input → `MarkdownDocument(empty, empty, "", [])`.

`MarkdownRefusal` (an exception or result type, your choice — match how `Companions.Refused` reports reasons) carries
the 1-based line and a reason string used verbatim in violations.

### T003 — Canonical writer; write-time round-trip check

**Steps**:
1. Write order: frontmatter (`---`, YAML via the store's YAML writer settings, `---`), blank line, `# title`, blank
   line, preamble (if non-empty) + blank line, then each `## title`, blank line, content (if non-empty), blank line
   between sections; exactly one final `\n`; an empty document writes as the empty string.
2. `MarkdownCodec.write(doc)` re-parses its own output and throws `IllegalStateException` if
   `parse(write(doc)) != doc` (write-side invariant from the adversary review).
3. Document the canonical form in the class javadoc.

### T004 — Unit matrix [P]

`MarkdownCodecTest` (JUnit 5 + AssertJ, `@ParameterizedTest` where it reads better):
- Each refusal rule from T002 has a refused fixture **and** an accepted neighbour differing in the one property
  (e.g. a 4-backtick fence closed by 4 backticks is accepted; closed by 3 is refused as unclosed).
- Three structural non-vacuity pairs (QA review):
  1. `## Not a heading` inside a fence does not start a section; the same line outside a fence does (section count
     differs).
  2. Changing one character inside a section's content makes the models unequal and the difference is locatable to
     `sections[i]` (expose a small `differences(a, b)` helper returning paths like `sections[2].content` — WP02's gate
     comparison reuses it).
  3. Adding or removing blank lines between blocks keeps the models equal.
- Frontmatter: absent vs `---\n---` (empty mapping) are different models; non-mapping frontmatter refused.
- Thematic break after a blank line is content; `---` directly under a paragraph line is refused as setext.
- Empty file; frontmatter-only file.
Fixtures may live inline (text blocks) or under `src/test/resources/markdown-fixtures/`.

### T005 — Corpus check: pinned Markdown files byte-equal [P]

`MarkdownCorpusTest`, tagged `open-packs` like the other corpus tests (see `OpenPacksCorpus` for how the pinned
checkout is located and how a missing checkout is reported), parses every `.md` in the set described in Context and
asserts `write(parse(bytes)) == bytes` byte for byte. Report the count (expected 72 at the current pin) and fail with
the list of files that differ or are refused. If a pinned file is refused or not byte-canonical, **stop and report**
— do not loosen a rule; the finding goes to the orchestrator (it may become an open-packs issue).

## Definition of Done

- All T001–T005 tests green with `./gradlew :packs:test` and `./gradlew :packs:openPacksRoundTrip`
  (Java 25; locally `JAVA_HOME=/usr/lib/jvm/temurin-25`).
- No change outside `owned_files`; Spotless/Checkstyle/Error Prone/NullAway clean (`./gradlew :packs:spotlessCheck
  :packs:checkstyleMain :packs:checkstyleTest :packs:compileJava`).
- Javadoc on every type and member; no TODOs.
- Mark subtasks with `spec-kitty agent tasks mark-status T001 … --status done`.

## Risks / reviewer guidance

- Reviewer: check every refusal has an accepted neighbour (non-vacuity) and that fence closing follows the
  same-character, at-least-as-long rule. Check `parse(write(d)) == d` is enforced in `write`.
- The scanner is deliberately not full CommonMark: HTML blocks and comments are content (a `## ` inside an HTML
  comment at column 0 *is* read as a heading — documented divergence, none at the pin). Lists, blockquotes and tables
  are content.
- Do not add a dependency (DIRECTIVE_051).
