# Tasks: Round-trip pack.md through the markdown content form

**Mission**: `pack-document-round-trip-01M65MBW` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Branch**: `issue-260-pack-md-round-trip` (planning base and merge target)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Red first: `PackDocumentRoundTripTest`, the gate tests (`REQUIRED_KINDS`, `bound`, lossy body writer, `pack-document` pinned green), the count pins and the digest-order test | WP01 | |
| T002 | Domain `PackDocument` and the `Pack` document slot (builder, `toBuilder`, `withoutDocument`) | WP01 | |
| T003 | `*/pack.md` rule, `pack-document` structure schema (and its fixture copy) | WP01 | |
| T004 | Store path: listing slot, `PackDocumentMarkdown` read and mapper, assembly, in-place write, fingerprint tail, validate mode | WP01 | |
| T005 | `WholePackStore` load and write; `DomainRoundTrip` models `pack-document`; `expected-red.yaml` loses `other:pack.md` | WP01 | |
| T006 | `GeneratedPackTree` plants `pack.md`; the digest oracle covers it | WP01 | |
| T007 | ADR-0042 amendment (decision 8 done), ADR-0037, ADR-0040 and ADR-0036 notes | WP02 | [P] |
| T008 | Changelog entry, logical model, `contracts/packs/README.md`, `file-kinds.yaml` header | WP02 | [P] |

## WP01 — pack.md through the markdown content form

**Goal**: the built-in `pack.md` is read into the `Pack`, written back in the canonical form, digested between the
presets and the manifest, validated in validate mode, and green in the whole-pack gate as `pack-document`.
**Priority**: P1 · **Prompt**: [tasks/WP01-pack-md-through-the-markdown-form.md](tasks/WP01-pack-md-through-the-markdown-form.md)
**Independent test**: `./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip`

T001 Red first: the pack-document tests and the gate pins (WP01)
T002 Domain `PackDocument` and the `Pack` slot (WP01)
T003 The rule and the structure schema (WP01)
T004 The store path, the fingerprint tail and validate mode (WP01)
T005 `WholePackStore`, `DomainRoundTrip` and the expected-red list (WP01)
T006 The digest oracle plants `pack.md` (WP01)

**Dependencies**: none. **Risks**: the rule and the store path must land together (research R3); sibling #194 PR-B
edits the same shared files (append-only, C-004).

## WP02 — Docs: pack.md is held

**Goal**: the ADRs, the changelog and the logical model say what is now true.
**Priority**: P2 · **Prompt**: [tasks/WP02-docs-pack-md-is-held.md](tasks/WP02-docs-pack-md-is-held.md)
**Independent test**: `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2`, `npx cspell --no-progress`

T007 ADR amendments (WP02)
T008 Changelog, logical model and contract README (WP02)

**Dependencies**: WP01.
