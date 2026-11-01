# Vendored shared vocabulary

These files come from `contracts/_shared/` of
[spec-kitty/spec-kitty](https://github.com/spec-kitty/spec-kitty) at commit
`0746bec30cbb473329d9a02333bafada716ecfd7` (ADR-0002, decision 2).

This repository's copy is canonical (operator ruling 2026-10-19, ADR-0015 (PR #91); Decision
Moment `01M5A4PP44NQ3KWG4APT0PEJCT` of mission `problem-response-charter-headers-01M5A4PB`).
A change is made here, kept close to the origin unless there is good reason, and consumers,
core included, adapt. A refresh from core never overwrites a change listed below.

## Changed from the origin

- `responses/Problem.yaml` declares the two always-on `Charter-*` response headers,
  `Charter-Contract-Version` and `Charter-Worktree`, by reference to `contracts/charter/headers/`
  (#90, ADR-0018 D11: declared on every response, the shared default `Problem` included).
  `Charter-Source-Fingerprint` is declared only on the 200 of a verified read, never on the shared
  `Problem` (#175).
- `schemas/Problem.yaml` gains two optional members, `requiredBy` (a list of
  `schemas/RequiringPack.yaml`) and `violations` (a list of `schemas/RefusalViolation.yaml`),
  both new files here (#125, ADR-0020 D10). `CharterRefusal` extends the closed `Problem` with
  `allOf`, so a member it carries must be declared on `Problem`. Every generated `Problem` gains
  them; the service writes them only on a 409 `artifact_required`/`artifact_locked` and a 422
  `activation_invalid`. The two new files use inline patterns, so `_shared` names nothing under
  `contracts/charter/`.
- `schemas/PageInfo.yaml` cites each property, and the schema itself, with an `x-derived` (#126,
  research R-16; #125): the charter's `resource-property-cited` lint judges every titled object
  schema of the bundle, and `ArtifactPage.page` and the effective artifact listing bring `PageInfo`
  into it. The shape is unchanged. The generator types `nextPageCursor`'s `oneOf [PageCursor, null]`
  as a nullable `String`, so it is not narrowed (C-009).
