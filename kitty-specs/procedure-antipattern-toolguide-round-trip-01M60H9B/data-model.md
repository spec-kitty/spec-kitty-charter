# Data Model: Procedure, anti-pattern and toolguide guide round trip

Entities are charter domain types in `service/packs` (`domain`), with their storage shapes (DSOs) in
`infra/storage`. Field names below are the stored (YAML / Markdown) names.

## Procedure (CharterArtifact, kind `procedure`, file `<id>.procedure.yaml`)

| Field | Type | Required | Notes |
|---|---|---|---|
| `schema_version` | string `"1.0"` | yes | |
| `id` | kebab string | yes | equals the file stem |
| `title` | string | yes | `CharterArtifact.label()` |
| `description` | string | yes | `CharterArtifact.description()` |
| `entry_condition` | string | no | |
| `exit_condition` | string | no | |
| `steps` | `ProcedureStep[]`, min 1 | yes | ordered |
| `anti_patterns` | `AntiPatternEntry[]` | no | ordered |
| `references` | `Reference[]` | no | kept; never minted as relationships (#189) |
| `notes` | string | no | |
| `applies_to_languages` | string[] | no | |

**ProcedureStep** `{title*, description?, role?, on_success?, on_failure?}` — `role` is an open role token
(ADR-0032; `operator` asks a human); no profile id is accepted.

**AntiPatternEntry** `{title*, content?, bad_example?, good_example?}` — a value of its owner, no identity.

**Reference** `{type*: ArtifactKind, id*, when?, reason?}`.

Invariants: `id` matches the file stem; step titles non-blank; unknown fields refused; retired names (`name`,
`purpose`, `actor`, anti-pattern `name`/`description`) refused.

## Toolguide (existing CharterArtifact, kind `toolguide`) — changes

| Field | Change |
|---|---|
| `guide_path` | `<id>.guide.md`, relative to the toolguide's file (was repo- or pack-rooted, `UPPER_CASE.md`); names the guide companion |
| (domain) `guide` | new `Guide` value, filled from the companion; not a YAML field |

All other fields unchanged (renames of merged fields are #186 follow-ups).

## Guide (value of Toolguide; stored as the Markdown companion at `guide_path`)

| Block | Markdown heading | Required | Content |
|---|---|---|---|
| title | `# <toolguide title>` | written | from `Toolguide.title`; an incoming guide may omit it; a present H1 that differs is refused |
| `overview` | `## Overview` | yes | Markdown |
| `when_to_use` | `## When to use` | no | Markdown |
| `setup` | `## Setup` | no | Markdown |
| `usage` | `## Usage` | no | Markdown |
| `sections[]` | any other `## <title>` | no | `{title, content}`, authored order |
| `cautions` | `## Cautions` | no | Markdown |
| `known_limitations` | `## Known limitations` | no | Markdown |
| `references` | `## References` | no | Markdown list of external links |

Order on write: title, overview, when_to_use, setup, usage, sections…, cautions, known_limitations, references.
A guide whose fixed blocks are out of this order, duplicated, or missing `overview`, that has a reserved heading in
another letter case, a freeform section after `cautions`, a preamble (text between the H1 and the first H2; it
belongs in `overview`) or frontmatter is refused; H3+ stays inside content.

Pinned actor mapping (migration only): `human` → `role: operator`; `agent`, `system` → no `role`.

## MarkdownDocument (content-form model, `infra/storage`)

`{frontmatter?: YAML mapping, title?: string, preamble: string, sections: [{title, content}]}`.
Equality: frontmatter by YAML meaning, all strings exactly; blank lines between blocks and the final newline are not
part of the model. A kind-specific structure (the Guide) is a schema over this model.
Construction invariants: titles single-line and non-blank; content has no leading or trailing blank line and no
column-0 H1/H2 outside a fence; an empty document is valid.

## Toolguide over HTTP (see `contracts/`)

Registration: exactly one of `guide` (Markdown, ≤ 1 MiB) or `guideUri` (HTTPS, allowlisted); `guidePath` removed.
Response: `guide` (the stored Markdown, H1 written from the title); `guidePath` removed. The stored `guide_path` is
`<id>.guide.md`, beside the toolguide file.

## Known-failure entry (existing)

`{path, at, reason, issue: spec-kitty/spec-kitty-open-packs#<n>}` — used only for residual divergences after the
migration.
