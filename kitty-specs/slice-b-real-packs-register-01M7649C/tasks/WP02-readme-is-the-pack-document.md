---
work_package_id: WP02
title: The pack document is README.md; pack.md and PROVENANCE.md are retired
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-016
- FR-019
- FR-022
- NFR-004
planning_base_branch: feat/466-slice-b-real-packs-register
merge_target_branch: feat/466-slice-b-real-packs-register
branch_strategy: Planning artifacts for this mission were generated on feat/466-slice-b-real-packs-register. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/466-slice-b-real-packs-register unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-b-real-packs-register-01M7649C
base_commit: 70ce4bbbedb2ec59a5f2f17b600320d9faf0d99b
created_at: '2026-11-11T21:19:54.723603+00:00'
subtasks:
- T004
- T005
- T006
- T007
- T008
- T009
phase: Phase 1 - Foundation
history:
- at: '2026-11-11T21:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/packs/
create_intent:
- service/packs/src/testFixtures/resources/example-packs/built-in/README.md
- service/packs/src/testFixtures/resources/example-packs/org-base/README.md
- contracts/charter/changes/2026-11-11-pack-document-is-readme.md
execution_mode: code_change
model: sonnet
owned_files:
- contracts/packs/file-kinds.yaml
- contracts/packs/schemas/pack-document.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDocumentMarkdown.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ContentForm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDocument.java
- service/packs/src/testFixtures/resources/example-packs/built-in/README.md
- service/packs/src/testFixtures/resources/example-packs/built-in/pack.md
- service/packs/src/testFixtures/resources/example-packs/org-base/README.md
- service/packs/src/testFixtures/resources/example-packs/org-base/pack.md
- service/packs/src/test/resources/digest-baseline/**
- service/packs/src/test/resources/markdown-rule-fixtures/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestFixtureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionCaseFoldTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MarkdownFilesRuleTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadOnlyPackSaveTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionDigestTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RetiredFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RetiredFileFixture.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackDocumentTest.java
- contracts/charter/changes/2026-11-11-pack-document-is-readme.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - The pack document is README.md; pack.md and PROVENANCE.md are retired

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read
`/root/.cache/spec-kitty/open-packs/a79c36318b7a506ca2ab26d74705c5e3e406004f/packs/built-in/agent_profiles/java-jenny.agent.yaml`
and adopt it. Then read `.kittify/charter/charter.md` (the canonical-model rule binds this WP).

## Objective

`README.md` at the pack root becomes the pack's document everywhere `pack.md` was: classification, store read, canonical
write, digest tail, validate mode, gate model. `pack.md` and `PROVENANCE.md` at the pack root become `retired` rules,
refused by name. A root file that differs only in case from a rule file is refused by name (FR-022). No alias, no
fallback reader, no deprecation window (C-001). Requirements: FR-001, FR-002, FR-003, FR-016, FR-019 (document part),
FR-022.

## Context

Read `kitty-specs/slice-b-real-packs-register-01M7649C/research.md` §A (every file:line) and
`research/squad-plan-point.md` (R3, R4, R7, R8, R10, A10). Abbreviations: `S/` =
`service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`, `T/` = the test twin.

Out of scope here (later WPs): the `ignored` fallback and "excluded is not refused" (WP03); `org-charter.yaml` (WP04).
Until WP03 lands, a root `README.md` is no longer unmodelled (it is the document) but other unmodelled Markdown is still
refused; that is expected.

## Subtasks

### T004 - Rules in `contracts/packs/file-kinds.yaml`

- Replace the `files` rule `*/pack.md` (:170-174) with `match: "*/README.md"`, `schema: pack-document`,
  `content_form: markdown`, and update its comment (#451, ADR-0042 decision 8 as amended 2026-11-11; only the root file
  is the document; a README deeper in the pack keeps its existing exclusions).
- Append to `retired` (after the ADR-0044 rules, keep their order): `*/pack.md` with reason
  `the pack document is README.md at the pack root (#451, ADR-0042 as amended 2026-11-11)`, and `*/PROVENANCE.md` with
  reason `folded into README.md as its provenance section (#451)`. Update the comment above `retired:` (it says the
  section holds only the retired mission tier).
- Rewrite the `text` content-form comment (:31-37, #432): text companions exist (#192, ADR-0039: a skill body is held as
  a `text` companion of its skill), and no standalone `text` kind or `files` rule exists; a rule naming `text` still
  fails the table. Update the header list at :50 (`*/pack.md` → `*/README.md`).
- Check: no new rule overlaps an existing one (`*/README.md` is one segment deep, so `*/missions/README.md` and
  `*/assets/**/README.md` stay separate).

### T005 - The document constant, schema, digest

- `S/PackDocumentMarkdown.java:24`: `FILE = "README.md"`. Every caller follows the constant: `FilesystemPackStore`
  (read :389-409, write :857-859), `WholePackStore` (:269-271, :888-895), `DomainRepositoryService` (:338-340,
  :865-867), `FileSystemPackInspector` (:361-377, :432-437). Those files are owned by other WPs; if any spells
  `"pack.md"` literally or in javadoc, fix it as a recorded out-of-map edit.
- `contracts/packs/schemas/pack-document.schema.json:5`: the description names `README.md`. Copy the file byte for byte
  to `service/packs/src/test/resources/markdown-rule-fixtures/pack-document.schema.json` (a test pins equality).
- `S/PackFileSet.java`: javadoc :38, :50, :111, :218, :235, :309-312; the tail order stays presets, document, manifest,
  policy, then retired files.
- `S/ContentForm.java:17`, `M/domain/PackDocument.java` javadoc: name `README.md`.

### T006 - Case variants are refused by name (FR-022)

Exact classification is case-sensitive (`S/PackFileKind.java:306-339`); companions use `classifyFolded` (:320). Add: when
a file's exact classification is Unclassified but its case-folded classification is a `files` or `retired` rule
anchored at the pack root, the file is refused by name. Model it as a `Retired`-like result with the reason
`differs only in letter case from '<match>'`, so load, validate mode, registration, digest and census treat it like a
retired file without new plumbing (check `FilesystemPackStore.java:366,489-495`, `FileSystemPackInspector:381-383`,
`WholePackCensus`). Keep it a property of classification, not of the callers. Tests in `PackFileKindTest` and
`RetiredFilesTest`: `x/Readme.md`, `x/readme.md`, `x/Pack.md`, `x/PROVENANCE.MD`, `x/Provenance.md` are refused naming
the rule; `x/docs/readme.md` is not (non-root); `x/README.md`, `x/pack.md` keep their exact results. After WP03 the
fallback must not pre-empt this (WP03 consults the fallback last).

### T007 - Example packs (canonical shape)

- `service/packs/src/testFixtures/resources/example-packs/built-in/pack.md` and `org-base/pack.md`: `git mv` to
  `README.md`, then add a `## Provenance` section (one or two sentences: authored in this repository as charter's own
  example pack, ADR-0070). Keep one H1 and the existing sections. No level, `pack_id` or `depends_on` facts.
- Check every pack-document codec rule: no CR, no BOM, no setext headings, one H1.

### T008 - Retired fixtures and tests

- `T/RetiredFileFixture.java`: append the two rules to `RULES` (exact, ordered equality; append after the ADR-0044
  rules, document the append order in the javadoc), samples `pack.md` and `PROVENANCE.md` for the root layout, and
  namesakes that are not retired: `docs/pack.md`, `assets/PROVENANCE.md` (asset payload), `missions/x/templates/pack.md`
  (template).
- `T/RetiredFilesTest.java`: the families now include the two files; keep the README overlap test (:69-87) correct; add
  a nest asserting the store load violation and the validate-mode finding name the reason for each.
- An error-handling test is the only place `pack.md` or `PROVENANCE.md` may appear as content (C-002).

### T009 - Retarget tests that name `pack.md`; recapture the digest pin

For each test in research.md §A and squad R4 that uses `pack.md` as the document, switch it to `README.md`; where it
uses `pack.md` as a decoy or a neighbour, decide its new meaning explicitly:

- `PackDocumentRoundTripTest`: document is `README.md`; the root-only decoy becomes `docs/README.md`.
- `PackFileSetTest:155-184`: tail order with `README.md`; decoy `docs/README.md`.
- `GeneratedPackTree`: the root-document shape uses `README.md`; the `sub/pack.md` decoy stays (not root, so not
  retired); add a RETIRED verdict for a root `pack.md` (squad R4: the `Verdict` enum has none; a retired file is
  refused and digested in the tail).
- `PackFileKindTest`: `MAPPED` `core/README.md` → `pack-document`; `core/docs/pack.md` stays Unclassified until WP03;
  `fileRules().hasSize(28)` stays 28.
- `KindBindingsTest:215-222`, `CompanionCaseFoldTest:40,63` (retarget `PACK.md` to `Readme.md` per R4),
  `MarkdownFilesRuleTest:25`, `MarkdownCorpusTest:28,37,78` (glob `*/README.md`), `ReadOnlyPackSaveTest:142-144`,
  `CompanionDigestTest:133,241` (README is now digested: update the assertion at :241 deliberately),
  `PackFileSetExactnessTest:174-182,328,482`, `domain/PackDocumentTest`.
- `PackDigestFixtureTest`: rename the fixture's `pack.md` to `README.md`; recapture `FINGERPRINT` once with the
  trigger recorded in the test's javadoc: "pack document moved to README.md (#451, 2026-11-11)". The DIGESTED list may
  change only by that swap.
- Tests in files owned by WP03 that break here (`WholePackStoreTest:320-330`, `:607-651` message
  `rule '*/pack.md'`, `CompanionFilesTest:170-185`): make the minimal change so they pass with README.md as the
  document, as a recorded out-of-map edit; WP03 revisits them.
- Fragment `contracts/charter/changes/2026-11-11-pack-document-is-readme.md`, one `### Breaking` block (rules in
  `contracts/charter/changes/README.md`): the pack document is `README.md`; a pack that ships `pack.md` or
  `PROVENANCE.md` at its root is refused at registration with violation code `retired`; a stored pack that holds one
  reads as `pack_unreadable` until re-registered; core's seam: read the document from `README.md` (and, after WP05,
  from `/doc`), not `pack.md`.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :packs:test
./gradlew --max-workers=1 :packs:wholePackRoundTrip
./gradlew --max-workers=1 :contracts:check
```

`pack-document` stays GREEN in the gate (`WholePackRoundTripTest:149-150`); `expected-red.yaml` unchanged (NFR-004).

## Branch Strategy

Planning base and merge target: `feat/466-slice-b-real-packs-register`; execution worktrees per computed lane
(`lanes.json`). Implement with
`spec-kitty agent action implement WP02 --agent claude --mission slice-b-real-packs-register-01M7649C`.
Commit convention: `feat(packs): …` / `test(packs): …`, issue refs `(#451)`, `(#432)`; signed; no model trailers.

## Definition of Done

- No production or test file reads or writes `pack.md` except the retired-refusal tests.
- Case variants refused by name; tests above green; gate green; fragment present.

## Risks and reviewer guidance

Tests whose meaning flips must be changed deliberately, never deleted to pass. Check the fingerprint recapture names
its trigger. Check `text` wording matches what is built.
