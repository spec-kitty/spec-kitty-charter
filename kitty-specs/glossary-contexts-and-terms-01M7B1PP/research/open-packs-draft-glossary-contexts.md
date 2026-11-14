# Issue draft for spec-kitty/spec-kitty-open-packs

**Placeholder:** `open-packs#TBD-glossary-contexts`. The orchestrator files this issue and replaces the placeholder in
charter's records before the closing pull request merges (issue-tracker Rule 4). This file is a draft only: nothing
was filed from the Mission.

**Title:** Migrate the glossaries from `glossary_pack` to glossary contexts (charter #106, ADR-0049, ADR-0076)

---

## Context

The charter replaced the artifact kind `glossary_pack` outright with `glossary`, in the shape of
[ADR-0049](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/adr/0049-glossary-contexts-and-terms.md)
(decisions 1 to 6, amended by ADR-0053 decisions 11 to 14). Mission `glossary-contexts-and-terms-01M7B1PP`
(spec-kitty/spec-kitty-charter#106) built it, with
[ADR-0076](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/adr/0076-the-activation-vocabulary-reads-and-projections.md)
for the vocabulary reads of an activation. It ships in the next charter contract release and packs contract release
after #106 merges; the orchestrator fills in the tags when they are cut.

There is no reader of the old shape and no upgrade (the charter's model is canonical). From that release on, a pack
that holds a `glossary_packs/*.glossary-pack.yaml` file is refused by name, and a preset that names
`activated_glossary_packs` makes its pack unreadable. Each artifact of kind `glossary` is a glossary context: a
bounded context of terms, one per file, and each term has an address, `term:<context-id>/<term-id>`.

## What to migrate

Every `glossary_packs/*.glossary-pack.yaml` under `packs/**` moves to `glossaries/<id>.glossary.yaml`, one context per
file. Charter's Mission counted four glossaries: `spec-kitty-core` (in `built-in`), `qa`, `event-driven` and
`internal`. List the files again at the commit you migrate, and migrate every one you find.

- The context `id` equals the file stem. The internal glossary's id is `spk-internal-glossary` in a file named
  `spk-internal`; pick one name for both, which also settles P7 (open-packs#47).
- The context `id` and each term `id` are letter-first kebab slugs of at most 64 characters
  (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`).

### Field moves

| Old (`glossary_pack`) | New (`glossary`) |
| --- | --- |
| no `schema_version` | `schema_version: "1.0"` |
| `id` | `id`, equal to the file stem |
| no `name` | `name`, a display name, for example `Spec Kitty core` |
| `description` | `description`, required; it becomes Contextive's `domainVisionStatement` |
| `provenance` | dropped; the pack level is the scope (ADR-0049 decision 11) |
| no `tags`, `applies_to` | optional; `applies_to.paths` becomes Contextive's `paths` |
| term `surface` | term `name`, and a minted term `id` |
| term `synonyms_to_avoid`, `banned_synonyms` | term `avoid`, deduplicated, in order |
| term `aliases`, `status`, `definition` | kept; `status` is `draft`, `active` or `deprecated` |
| term `see_also` (surfaces or free text) | term `see_also`, term references (below) |
| term `confidence`, `introduced_in_mission` | dropped |

Every object is closed: a field the table does not name is refused.

### Term ids

The rule (charter research R4, ruling `DM-01M7B1Q77QRB2PNDG0CDBNVVH3`): lower-case the surface, replace each run of
characters other than `[a-z0-9]` with one `-`, then trim `-`. A surface whose slug is not a valid id, or collides with
another term's in the same context, gets a hand-picked id; record it in the pull request.

For `spec-kitty-core`, the map of charter's example copy (139 terms, 139 distinct ids, none hand-picked) is
[`term-id-map.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/glossary-contexts-and-terms-01M7B1PP/research/term-id-map.md).
Open-packs' copy may hold other terms than charter's; apply the rule to each and check that the ids stay distinct.

### `see_also`

A `see_also` entry is a term reference, judged by the one reference rule (charter operator ruling, 2026-11-14):

- a surface of a term in the same pack becomes `term:<context-id>/<term-id>`;
- a term of another pack is `<pack>::term:<context-id>/<term-id>`, and that pack is in this pack's `depends_on`
  closure;
- an entry that names no term (a tactic, a requirement) is not a term reference. Drop it, or move it into the
  definition. Charter's example dropped the one such entry, on `catastrophic backtracking`.

A bare entry that only another pack holds is refused as unqualified, and an entry that no held context resolves is a
broken reference.

### The files around a glossary

- **Graph shards and fragments:** `glossary_pack.graph.yaml` becomes `glossary.graph.yaml`. A node
  `glossary_pack:<id>` becomes `glossary_context:<id>` with kind `glossary_context`, and the fragment plural is
  `glossaries`. A `vocabulary` edge targets `glossary_context:<id>` or `term:<context-id>/<term-id>`. Core's
  `glossary:<id>` URN stays refused.
- **Presets:** `activated_glossary_packs` becomes `activated_glossaries`, listing context ids.
- **Charter files:** `selected_glossary_packs` and `activated_glossary_packs` become `selected_glossaries` and
  `activated_glossaries`.
- **Policy:** `kind_defaults.glossary_pack` becomes `kind_defaults.glossary`, and an `artifacts` key is
  `glossary_context:<id>`.
- **Pack manifest:** the constituent kind is `glossary` at `glossaries/<id>.glossary.yaml`. Restamp the manifest.
- **Step contracts:** any `glossary_pack` token becomes `glossary`.

## Acceptance criteria

- No file under `packs/**` holds `glossary_pack`, `glossary-pack` or `glossary_packs`.
- Every `glossaries/*.glossary.yaml` passes charter's `glossary.schema.json` from the packs contract release that
  carries #106, and every pack registers unedited against the charter service of that release.
- Every `see_also` entry resolves, and every term id follows the rule or is recorded as hand-picked.
- The open-packs gate against the pinned charter release is green, and so is charter's drift watch
  (`open-packs-drift.yml`) while it exists.

## What it unblocks in charter

Charter's own governance packs, `packs/internal` and `packs/qa`, are synced from this repository's content by
`openPacksRef`. They are in the old shape, so the charter service refuses them until this lands. Once it lands and
charter raises `openPacksRef`, they load again, and the drift watch turns green.
