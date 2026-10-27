---
work_package_id: WP01
title: Content form in the pack vocabulary
dependencies: []
requirement_refs:
- FR-001
- FR-004
- FR-008
- FR-013
- C-002
- C-004
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
role: implementer
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
execution_mode: code_change
owned_files:
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ContentForm.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ContentForm.java
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Content form in the pack vocabulary

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Make `contracts/packs/file-kinds.yaml` — the single data authority for the pack vocabulary — say **how** each pack
file is read, held, written and compared (its **content form**), and classify the schema-less payload files of a pack
(asset payloads and top-level templates). Operator rulings R1, R4, R8, R9 (see `research.md`) and decision
DM-01M5Z18 ("fallback root rule") bind this WP. Read first: `spec.md` (FR-001, FR-004, FR-008, FR-013), `plan.md`
("Key design decisions" D1, "Acceptance checks" 7 and 12), `contracts/file-kinds-content-form.md` (the exact shape),
`research.md` post-plan findings P1, P2, P5, P6, P19.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP01 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Context

- `PackFileKind` (`service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java`) parses
  the bundled table into `files` rules (`match`, `schema`) and `exclusions` (`match`, `reason`); `classify(path)`
  returns `Mapped(kind=schema name, schema location)`, `Excluded` or `Unclassified`. Consumers: `PackFileSet` (load
  and digest), `WholePackCensus` (gate kinds, test), `FileSystemPackInspector` (validate mode), `PackSchemas`
  (iterates `fileRules()` by schema), `VocabularyEqualityTest`.
- The glob language: `*` within one segment, `**/` zero or more whole segments; **a trailing `**` is NOT a recursive
  wildcard** (it compiles to `[^/]*[^/]*`). Write `*/assets/**/*`, not `*/assets/**`.
- Paths are relative to the directory that holds the pack directories, e.g. `built-in/assets/audiences/line_manager.md`.
- Shared file with sibling #192 (it will add `markdown` rows): put our rows in one contiguous block with a comment
  naming #193; do not reorder existing rows.

## Subtasks

### T001 — `ContentForm`, the `Mapped` shape, `PayloadRule`

**Purpose**: give the table a typed content form and let a classification carry "no schema".

**Steps**:
1. Create `ContentForm` (package-private enum in `infra.storage`): `YAML`, `MARKDOWN`, `TEXT`, `BYTES`, each with its
   table token (`yaml`, `markdown`, `text`, `bytes`) and a `fromToken(String)` that fails with an
   `IllegalStateException` naming the bad token and the resource (same style as `PackFileKind.string`).
2. Change `Mapped` to `record Mapped(String kind, Optional<String> schema, ContentForm contentForm)`. For a `files`
   rule, `kind` stays the schema name and `schema` the classpath location (`packs/schemas/<name>.schema.json`); for a
   payload rule, `kind` is the rule's `kind` and `schema` is empty. Update every caller in this WP's files; callers
   outside (`PackFileSet`, `WholePackCensus`, `FileSystemPackInspector`) only read `kind()` — keep that accessor.
3. Add `record PayloadRule(String match, String kind, ContentForm contentForm)` and `FileRule` gains
   `ContentForm contentForm` (default `YAML`). Keep `FileRule.schema()` a required string so `PackSchemas` and
   `VocabularyEqualityTest` keep iterating `fileRules()` unchanged.

**Validation**: compiles; existing `PackFileKindTest` cases adapted to the new `Mapped` shape pass.

### T002 — Parse `content_form` and `payloads`; overlap rules

**Steps**:
1. `parse`: read an optional `content_form` on each `files` rule (absent → `YAML`). Read an optional top-level
   `payloads` list of `{match, kind, content_form}`; every key required; unknown keys on any rule refused (same
   fail-loud style as today: `IllegalStateException` naming the resource and the key).
2. `classify(path)`: `files`, then `exclusions` (unchanged precedence and messages), then `payloads`. Compile payload
   globs once, like the others.
3. `matchingRules(path)` stays over `files` + `exclusions` only (payload rules overlap file rules by design: a
   sidecar `core/assets/x.asset.yaml` matches both `**/*.asset.yaml` and `*/assets/**/*`). Add
   `matchingPayloadRules(path)` so a test can prove no path matches two payload rules.
4. Expose `payloadRules()` for tests and consumers.
5. Unknown keys on any rule (`files`, `exclusions`, `payloads`) are refused at table load. This is **new** strictness
   (today `parse` checks no keys): verify the bundled table passes and note it in the commit message, since #192 edits
   the same table.
6. The table accepts the tokens `yaml` and `bytes` only for now: a rule declaring `markdown` or `text` fails at table
   load with a message naming the form and "not built yet (#192 builds markdown)" (C-004, post-plan P19). The enum keeps
   all four names; building a form later is one line here plus its codec.

### T003 — Edit the table and its README

**Steps** (`contracts/packs/file-kinds.yaml`):
1. Header: correct the false claim that "the open-packs acceptance test fails on an overlap, and on a file that
   matches nothing" — the only overlap check is `PackFileKindTest`; say so. Describe `content_form` (default `yaml`; extension and `mime` never choose it) and the `payloads` section
   (schema-less files, consulted after `files` and `exclusions`; payload rules must not overlap each other). Change
   "exclusions: Rules for YAML files that have no schema" to "Rules for files that are not pack content or have no
   schema, each with a reviewable reason". Note that `**/*` is how a rule spans nested files.
2. Add the exclusion (reason: orientation notes beside the assets they describe; not pack content, not an asset
   payload; operator ruling R4, #193):
   ```yaml
   - match: "*/assets/**/README.md"
   ```
3. Retire the `**/*.config.yaml` exclusion (its only match at the pin is
   `built-in/assets/docs_structural_lint.config.yaml`, which operator ruling R1 makes an asset payload). This is the
   one non-append edit; say so in the commit message.
4. Add:
   ```yaml
   # Files with no schema (#193, ADR-0040): consulted only when no files or exclusions rule matches.
   payloads:
     - match: "*/assets/**/*"
       kind: asset-payload
       content_form: bytes
     - match: "*/templates/**/*"
       kind: template
       content_form: bytes
   ```
5. `contracts/packs/README.md`: describe content form and the `payloads` section in a short paragraph next to where it
   describes `files`/`exclusions` (find the section by reading the file).
6. Check the bundled copy: confirm how the build puts `contracts/packs/file-kinds.yaml` on the test/main classpath
   (`packs/file-kinds.yaml`) — no copy to edit by hand if it is a resource mapping.

### T004 — Validate mode and schema-less files

**Purpose**: validate mode (`FileSystemPackInspector`) classifies every `*.yaml` file and validates `Mapped` ones
against their schema. After T003, `docs_structural_lint.config.yaml` classifies as `Mapped(asset-payload, no schema)`
and `PackSchemas.validate("asset-payload")` would throw.

**Steps**:
1. In `readAll`, a `Mapped` with an empty `schema()` is not read or validated; it is recorded as a `ClassifiedFile`
   of kind `asset-payload` (decided: `PackVerdict` carries the file list, which has no consumer outside tests; the
   **findings** must stay identical). The file list entry for `docs_structural_lint.config.yaml` changes from
   excluded `**/*.config.yaml` to kind `asset-payload` — that is the one expected difference.
2. Test (in `FileSystemPackInspectorTest`): a fixture with a sidecar, its payload, a sidecar-less `x.config.yaml`
   under `assets/` and a template; the inspector reports no exception, does not list the payloads as unreadable, and
   the facts it reports for the sidecar are unchanged (paired with the existing asset-fact tests).
3. Acceptance check 12: run validate mode over the real built-in pack (the existing open-packs corpus helpers show
   how a test reaches the checkout; use the same tag/task) and assert its **findings** equal the findings on `main`
   at 90de8f9 — capture them by running the current code before your change (record how in a comment) and pin them as
   a literal expectation, not by recomputing. The reviewer re-captures them to check.

### T005 — Classification tests

**Steps** (`PackFileKindTest`, `VocabularyEqualityTest`):
1. Mapped samples gain the content form (`YAML` for every existing rule).
2. New payload samples (nested on purpose): `built-in/assets/audiences/line_manager.md`,
   `internal/assets/debrief/fonts/FallingSky-Bold.otf`, `qa/assets/qa_carrier_lint.py`,
   `built-in/assets/docs_structural_lint.config.yaml` → `Mapped(asset-payload, empty, BYTES)`;
   `built-in/templates/writing/meeting-minutes.md` → `Mapped(template, empty, BYTES)`.
3. Sidecar `built-in/assets/audiences/line_manager.md.asset.yaml` stays `Mapped(asset, schema, YAML)` (files first).
4. `built-in/assets/README.md` and `built-in/assets/audiences/README.md` → `Excluded`.
5. Unclassified stays: `qa/missions/qa/templates/test-report-template.md`, `built-in/missions/software-dev/templates/spec-template.md`.
6. Remove `core/assets/lint.config.yaml` from `EXCLUDED`.
7. No sample path matches two payload rules; the existing two-rule check (files + exclusions) still passes.
8. A table text with an unknown key on any rule, a missing `kind`, `content_form: html`, or `content_form: markdown`
   fails to load, naming the problem (use the `PackFileKind(String yaml)` constructor); positive control: the same
   text with `content_form: bytes` loads.
9. Trailing-`**` guard: a sample proving `*/assets/**/*` matches a depth-3 path and `*/templates/**/*` does not match
   `x/missions/m/templates/a.md`.

## Definition of Done

- All subtasks' tests pass: `./gradlew :packs:test --tests '*PackFileKind*' --tests '*VocabularyEquality*' --tests '*FileSystemPackInspector*'`.
- `./gradlew :packs:check` green on Java 25 (or `-Pcharter.javaVersion=21` locally, noted in the review handoff).
- `./gradlew :packs:openPacksRoundTrip` green (it is part of `:packs:check`).
- Known and accepted: the whole-pack gate (`:packs:wholePackRoundTrip`, CI `whole-pack.yml`, not in `check`) is red
  from here until WP07 (`other:templates` stale, `other:assets` unexpectedly green, new kinds unlisted). The PR to
  `main` is opened only after WP07.
- No change to `contracts/charter/openapi.yaml`.

## Risks / reviewer guidance

- Reviewer: confirm the validate verdict over built-in is identical, the glob uses `**/*`, payload rules are consulted
  last, and the `**/*.config.yaml` retirement is the only non-append edit to existing table rows.
- Collision: #192 edits the same table; our block is contiguous and commented.
