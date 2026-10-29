---
work_package_id: WP05
title: Markdown companions in store and gate
dependencies:
- WP02
requirement_refs:
- FR-005a
- FR-009
- NFR-002
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
subtasks:
- T021
- T022
- T023
- T024
- T025
phase: PR-A2 - Guides
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MarkdownCompanionTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompanionContent.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companions.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SkillBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MarkdownCompanionTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Markdown companions in store and gate

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Widen ADR-0039 companions: a companion declares its content form (`text` as today, or `markdown` with a structure
schema); the store reads, validates and writes it by form; a companion may not claim any file another rule
classifies; the gate classifies, pairs and compares companions by form. The skill companion keeps its exact behaviour.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP05 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.** Parallel with WP03/WP04/WP08.

**Out-of-map edits (recorded)**: `WholePackCensus.java` (owned by WP02, approved before this WP starts) — add
companion classification/pairing; a small, dependency-ordered edit.

## Context

- `KindBinding.Companion(path, Optional<CompanionContent>)`, `companions(dso)`, `withCompanions(dso, contents)`,
  `hasCompanions()`; `CompanionContent` sealed with only `Text`; `Companions.resolve/read/text`, the one-owner rule
  (refuses `.yaml` and schema-less payload files); `SkillBinding` (the only companion user today);
  `WholePackStore.companionFiles`, `putArtifact`; `FilesystemPackStore.readText`, `realPathContained`.
- `research.md` D6, D10; plan IC-02; adversary review findings on companion ownership and 500s.
- WP01's `MarkdownCodec` and WP02's `PackSchemas.validateMarkdown`, `MeaningComparator.compareMarkdown`.

## Subtasks

### T021 — Companion declares a form

- `CompanionContent.Markdown(MarkdownDocument document)` joins the sealed interface; `Companions.text(content)` stays
  exhaustive (Markdown writes via `MarkdownCodec.write`).
- `KindBinding.Companion` gains `ContentForm form` and `Optional<String> structureSchema` (Text: `TEXT`-like legacy
  marker — use `ContentForm.TEXT` only as the companion's declared form, it remains unbuilt for `files` rules; or add a
  dedicated companion-form enum `CompanionForm {TEXT, MARKDOWN}` — choose the smaller change and justify it in
  javadoc). Update `SkillBinding` to declare `TEXT` explicitly with no behaviour change.

### T022 — Ownership: refuse any classified path

`Companions.resolve`: refuse a companion path that any `files` rule, `payloads` rule **or** the exclusions list
classifies (today only schema-less `Mapped` are refused) — except the companion's own `files` rule, which WP06 adds
for guides (`**/toolguides/*.guide.md`): a companion is accepted at a path classified by a rule whose kind the binding
declares as its companion kind. Keep case-fold claims. Refusal reason names the owning rule.

### T023 — Store by form

- Read: `Companions.read` returns `Found(at, Text|Markdown)` by the declared form; Markdown is parsed with
  `MarkdownCodec` and validated with the declared structure schema; a refusal becomes a violation naming the artifact,
  companion path, line and reason.
- Write: `WholePackStore` / `FilesystemPackStore` write Markdown with `MarkdownCodec.write` (canonical) through the
  existing atomic write; the fingerprint includes the companion bytes as today (a guide-only change must move the
  fingerprint — test in T025).

### T024 — Gate pairing by form

`DomainRoundTrip` (and `WholePackCensus`, out-of-map): a companion file is paired with its owning artifact's written
companion at the same relative path; compared with `compareMarkdown` for Markdown, exact text for Text; an unpaired
source companion or an extra written one is a gate difference. Markdown never reaches `Yaml.load`.

### T025 — Conflicts as client errors; tests

- Companion conflicts ("taken", "differs only in case", "named by another artifact", "classified by rule X") surface
  as the refusal the API maps to 409/422, never `pack_unreadable`/500 — find where `CompanionFilesTest` asserts today's
  mapping and add the client-error mapping at the same seam.
- `MarkdownCompanionTest` with a test binding (like `CompanionFilesTest.Fixture`): Markdown companion round-trips;
  one-character change inside a section is a gate difference at that section; structure-schema violation refused;
  companion at `pack.md`-like classified path refused; guide-only change moves the fingerprint; skill companion tests
  unchanged and green.

## Definition of Done

- `./gradlew :packs:test` green; skill behaviour unchanged (existing `SkillYamlTest`, `GlossaryPackAndSkillCorpusTest`,
  `CompanionFilesTest` green); style clean.
- Mark T021–T025 done.

## Risks / reviewer guidance

- Reviewer: exhaustive switches over the companion form; no Markdown to `Yaml.load`; ownership rule tightened without
  breaking skills or assets; conflicts are client errors.
