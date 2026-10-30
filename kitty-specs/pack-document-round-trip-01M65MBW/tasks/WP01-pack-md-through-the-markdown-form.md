---
work_package_id: WP01
title: pack.md through the markdown content form
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- NFR-001
- NFR-002
- C-001
- C-002
- C-004
- C-005
- SC-001
- SC-002
- SC-003
planning_base_branch: issue-260-pack-md-round-trip
merge_target_branch: issue-260-pack-md-round-trip
branch_strategy: Planning artifacts for this mission were generated on issue-260-pack-md-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-260-pack-md-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - Hold
history:
- at: '2026-10-30T06:30:00Z'
  actor: claude
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/
create_intent: []
execution_mode: code_change
owned_files:
- contracts/packs/file-kinds.yaml
- contracts/packs/schemas/pack-document.schema.json
- service/packs/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP01 — pack.md through the markdown content form

Load the `java-jenny` profile first (`spec-kitty agent profile show java-jenny`).

## Objective

Hold `pack.md` in the `Pack` aggregate through #194's `markdown` content form (spec FR-001 to FR-010; plan D1-D6).

## Subtasks

- **T001** Red first. `PackDocumentRoundTripTest` (frontmatter, none, empty, no final newline, CRLF, BOM, empty
  file, nested `docs/pack.md`, lossy body, unwritable document, gate runs, validate-mode parity); the gate's
  `REQUIRED_KINDS`, `bound`, `withPayloads`, the lossy body writer over the built-in pack, `pack-document` pinned at
  one green; the count pins (`PackFileKindTest`, `PackSchemasTest`, `DomainRoundTripTest`,
  `WholePackExpectationTest`); `PackFileSetTest` digest order.
- **T002** `PackDocument` (domain) and `Pack.document()`; `Builder.document`, `withoutDocument`; `toBuilder` keeps it.
- **T003** `*/pack.md` → `pack-document`, `content_form: markdown`, appended after the toolguide-guide rule; the
  schema; a fixture copy pinned equal to the bundled one.
- **T004** `PackFileSet.Listing.document`, `markdownHolder` routing, `packLevelTail`; `PackDocumentMarkdown`
  (`read`, `toDomain`, `toMarkdown`, `toWritable`); `FilesystemPackStore` read and `writePackDocument`;
  `DomainRepositoryService` assembly and `writePackDocument`; `FileSystemPackInspector` Markdown walk.
- **T005** `WholePackStore` modelled set and write before the manifest; `DomainRoundTrip.PACK_DOCUMENT` and the shared
  in-place outcome; `expected-red.yaml` loses `other:pack.md` only.
- **T006** `GeneratedPackTree` plants `pack.md`; `PackFileSetExactnessTest` names it when a digest drops it.

## Definition of Done

`./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip` green; built-in `pack.md` green; no new
suppression; `contracts/charter/**` untouched.
