---
title: "ADR-0039: A kind binding can carry companion files"
description: An artifact may name text files beside its own, like a skill's body; the store reads, writes and fingerprints them with it under one path rule.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0029-charter-glossary-is-owned-here
  - conformance/divergence-register
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before binding a kind whose artifact names another file, such as an asset payload, a template or a toolguide's guide.
  - profile: pack-author
    guidance_text: Read this for how a skill's body_path file is read, written and fingerprinted with the skill, and which paths it may name.
  - profile: core-maintainer
    guidance_text: Read this for the glossary-pack schema now canonical here, and the skill schema imported from core.
---

# ADR-0039: A kind binding can carry companion files

## Status

**Accepted** — 2026-10-27. Decider: Stijn Dejongh (operator), who confirmed on
[#192](https://github.com/spec-kitty/spec-kitty-charter/issues/192) that a skill's body is in scope
and kept as a pair with its skill. Amends [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)
decision 4 (Accepted), which left non-YAML content to the kind missions, and supersedes in part
[ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)'s per-kind table: its rows for
`*.skill.yaml`, `*.glossary-pack.yaml` and `*.skill.md`.

### Amendment 2026-10-27 (#193): asset payloads and templates are not companions

Made by [#193](https://github.com/spec-kitty/spec-kitty-charter/issues/193) on the operator's
ruling of 2026-10-27, `DM-01M5ZAMP0R5V5YF5KM09YXY0NS` (reconcile with ADR-0039: keep pack-level
asset files and templates, share the low-level pieces), recorded in
[ADR-0040](/adr/0040-content-form-and-payload-kinds/) (Proposed). The amended points are in force
for the code #193 ships, which implements them; ADR-0040 itself is Proposed, pending the operator's
acceptance, which the pull request for #193 asks for. The decision text below is
unchanged, and each point this note changes is annotated where it stands. It corrects three
statements about #193 that did not come true as written, and extends decision 3's rule to the new
pack-level files:

- **Decision 2, "#193 adds a `Bytes` case".** It does not. An asset payload may have no sidecar,
  a template is an artifact of its own, and a payload resolves against `assets/`, not its
  artifact's file; none fits a companion. They are pack-level files (ADR-0040 decisions 4 and 5),
  and `CompanionContent` keeps its one `Text` case until a companion needs another.
- **Decision 2, "the revision digests move to bytes with it".** They did, without a `Bytes` case:
  the commit path computes its revision over bytes (`PackRevision.computeOverBytes`), with the same
  value as before for text. The negative consequence "a binary companion would fail them until
  #193 moves them to bytes" no longer holds.
- **Consequences, "#193 can extend the same hooks".** It shares the low-level pieces instead: one
  containment check (`Companions.inPack`, the check decision 3's rule makes), one bytes read with
  text as a UTF-8 view over it, one atomic write, and decision 7's order, with asset files and
  templates digested after the graph documents.
- **Decision 3, one owner.** A companion that resolves to an asset file or a template (a file a
  `payloads` rule classifies) is refused, `is a pack file of kind <kind>, not a companion`, on the
  read, the upsert and the fingerprint alike (ADR-0040 decision 5): the file has its one owner
  already. The reason joins decision 4's list of companion violation reasons (commit `198a686`).

### Amendment 2026-10-29 (#194): a companion declares its form, and owns no classified path

Made by [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194) (part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)) for the toolguide guide, the
first Markdown companion, and recorded in
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) (Proposed). The
amended points are in force for the code #194 ships, which implements them. The decision text below
is unchanged, and each point this note changes is annotated where it stands.

- **Decision 1, a companion declares a form.** `Companion(path, content, form, structureSchema)`:
  `form` is `text` or `markdown`, the same `ContentForm` a file-kind rule declares
  ([ADR-0040](/adr/0040-content-form-and-payload-kinds/)), and `structureSchema` names the structure
  schema a Markdown companion's model is validated with, present exactly for `markdown`. A
  companion is never `yaml` or `bytes`. Declaring `text` on a companion does not admit `text` for a
  `files` rule, which the table still refuses.
- **Decision 2, content is text or Markdown.** `CompanionContent` gains a `Markdown` case beside
  `Text`. A `text` companion is still read and written byte for byte as UTF-8. A `markdown`
  companion is parsed into the Markdown model, validated against its structure schema, compared by
  that model and written in the canonical form: a read and a write agree on meaning, not on bytes.
  It never reaches the YAML reader.
- **Decision 3, one owner, tightened.** A companion may not claim a path that any file-kind rule
  classifies: a `files` rule, an `exclusions` rule or a `payloads` rule. The one exception is the
  companion's own rule, a `files` rule whose schema is the companion's structure schema and whose
  form is the companion's form (a toolguide's `<id>.guide.md`, classified by
  `**/toolguides/*.guide.md`). This keeps a companion off, for example, a pack's `pack.md`, which
  a toolguide with the id `pack` would otherwise claim.
- **Decision 4, new reasons.** `is excluded by file-kinds rule '<class>', not a companion` and
  `is a pack file of kind <kind> (file-kinds rule '<rule>'), not a companion` join the list, as do
  a Markdown refusal (naming its line) and a structure-schema breach of a `markdown` companion.
- **Consequences, "the toolguide guides can extend the same hooks".** They did: the guide is a
  companion of the toolguide binding, with no second non-YAML path.

## Context

ADR-0037 binds each kind once (`KindBinding`), and the store, the whole-pack seam and the source
fingerprint handle every bound kind through one path. That path moves one YAML file per artifact.
Its decision 4 says the registry does not yet cover non-YAML content and that the kind missions
widen it when they are built.

The first such content is the skill's prompt body. A `*.skill.yaml` of the prompt form names a
Markdown file with `body_path`, relative to the skill's file. Issue #192 keeps the two as a pair on
write. A body that is not read is lost on a whole-pack write; a body that is read but not digested
escapes the source fingerprint, which promises that a file the service loads cannot change unseen.
Asset payloads and templates (#193) and a toolguide's `guide_path` file need the same widening.

## Decision

1. **A binding names its companions.** `KindBinding` gains two default methods, so a kind without
   companions is unaffected:
   - `companions(dso)` lists the files an artifact names, each a `Companion(path, content)`: the
     path relative to the artifact's file, exactly as written, and the content once read. One list
     names both, so paths and contents cannot drift.
   - `withCompanions(dso, contents)` returns the shape with each companion's content filled in.

   A third default, `hasCompanions()`, is `true` exactly for a kind that overrides `companions`; a
   registry test holds every bound kind to it. A kind without companions is then never read to find
   any, so its cost and its fingerprint are unchanged. `KindBinding`'s "a binding knows no path"
   becomes "no path but its companions', relative to its artifact's file".
   *(Amended 2026-10-29, see Status: a `Companion` also declares its form, `text` or `markdown`,
   and for `markdown` its structure schema.)*
2. **Content is text today.** `CompanionContent` is a sealed type with one case, `Text`, read and
   written byte for byte as UTF-8: nothing is trimmed and no line ending is converted. #193 adds a
   `Bytes` case. The revision digests of the commit path (`PackCommitStore`,
   `CommitStorePackWriteStore`) read every digested file as text and move to bytes with it.
   *(Amended 2026-10-27, see Status: #193 adds no `Bytes` case; asset payloads and templates are
   pack-level files, not companions. The revision moved to bytes without one
   (`PackRevision.computeOverBytes`), with the same value for text.)*
   *(Amended 2026-10-29, see Status: a companion declares its form, `text` or `markdown`, and
   `CompanionContent` gains a `Markdown` case, compared by the Markdown model and written in the
   canonical form.)*
3. **One path rule, one owner** (`Companions`). A companion path is not empty and not absolute. It
   resolves, normalized, against the artifact file's directory to a file inside the pack directory.
   The resolved file name never ends in `.yaml`, so a companion can never be `pack.yaml`, a graph
   shard or an artifact file (`pack.yaml/` and `x.tactic.yaml/.` normalize to YAML files and are
   refused). Reading requires a regular file, not reached through a link, whose real path is in the
   pack, holding UTF-8 text. One file has one owner: a companion two artifacts name, case ignored,
   is refused for the second. The read, the upsert and the fingerprint apply the same path rule;
   the fingerprint lists a shared companion once rather than refusing it, since the load reports it.
   *(Amended 2026-10-27, see Status: a companion that is an asset file or a template is refused,
   `is a pack file of kind <kind>, not a companion`.)*
   *(Amended 2026-10-29, see Status: a companion may not claim any path a file-kind rule
   classifies, except at its own `files` rule.)*
4. **Read.** An artifact whose companion breaks the rule is left out, and the store records a
   `companion` violation of its file, `<path>: <reason>` (`is empty`, `is not a valid path`,
   `missing`, `leaves the pack`, `is a YAML file`, `is not a regular file`,
   `cannot be read as UTF-8 text`, `named by two artifacts`). The whole-pack write then refuses the pack rather than drop the
   companion.
   *(Amended 2026-10-27, see Status: `is a pack file of kind <kind>, not a companion` joins this
   list, a consequence of decision 3's one-owner rule.)*
   *(Amended 2026-10-29, see Status: the excluded-path and classified-path reasons, a Markdown
   refusal and a structure-schema breach join this list.)*
5. **Upsert.** `FilesystemPackStore.write` checks every companion before writing any. Each must
   have content, pass the rule, be named once, and not replace an existing file the element does
   not already own. It then writes the companions, then the artifact's file, then removes the
   companions the element's old file named and the new one does not, so a changed path leaves no
   orphan. The pair is atomic only through the commit path, which writes into a staging copy of the
   pack and swaps the directory; it is the only production writer of kinds with companions. An element whose
   old file no longer reads owns no companion, so it is repaired by hand, not over the top of it.
6. **Whole pack.** `WholePackStore` counts companions as modelled, so none is listed as unmodelled,
   and writes each beside its artifact's layout path; its existing checks refuse a path that leaves
   the pack or one written twice.
7. **Fingerprint.** `PackFileSet.digested` lists each companion right after its artifact's file,
   when it passes the rule, is UTF-8 text and is not already listed. It finds them by reading the
   artifact's file through the store's own schema-valid read path, statically, as `PackFileKind`
   and the store's id lookup already parse YAML statically: `companions(dso)` stays the one source
   of truth, and the three digest callers keep their constructors. A file that does not read
   contributes only itself; the load reports it, and fixing it changes the file. New kinds are
   appended to the registry, so a pack with no glossary pack or skill digests what it did before.
8. **Rows for ADR-0036's per-kind table** (as the text before that table asks a kind mission to
   record). They supersede the `**/*.skill.yaml`, `**/*.glossary-pack.yaml` "excluded" row there,
   and, for a skill's body, the `*.skill.md` entry of its `*.md` row: a body is compared byte for
   byte, with no line-ending or final-newline normalization.

   | Files (`match`) | Schema | Compared as | Sets (member identity) | Unknown fields refused |
   | --- | --- | --- | --- | --- |
   | `**/*.glossary-pack.yaml` | glossary-pack | YAML; `terms` is a sequence, `confidence` a number | none (a repeated `surface` is a domain violation) | yes |
   | `**/*.skill.yaml` | skill | YAML; the `body_path` file compares as text, byte for byte | none | yes |

9. **Schemas and names.** Under [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)
   the schemas are canonical here. `glossary-pack.schema.json` is authored from core's
   `GlossaryPack`/`GlossaryTerm` model, which exports none; `skill.schema.json` is core's
   `skill.schema.yaml` (core `581b5c46da`) as JSON. Both are closed.
   [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) imported the first schemas from
   core's model export and set aside core's hand-written YAML schemas because they had drifted from
   the models. The skill schema is one of those hand-written files, so it was checked against the
   model it says it mirrors (`PackSkill`): the same fields at every level, closed where the model
   forbids extras. Since ADR-0017's amendment R20 the schemas are edited here and core follows, so
   the source of a first version matters only for its fidelity, which this check settles. The
   glossary binding's schema name and file token are `glossary-pack`, settling ADR-0037 decision 4's
   "`glossary_pack` (excluded)"; the kind's token (`ArtifactKind`) stays `glossary_pack`. A glossary pack keeps its authored path whatever its
   file stem (the internal pack's `spk-internal` holds `spk-internal-glossary`,
   [open-packs#47](https://github.com/spec-kitty/spec-kitty-open-packs/issues/47)); a new one is
   created as `<id>.glossary-pack.yaml`. A glossary pack is pack content, unrelated to this
   repository's own glossary ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)).
10. **Nothing is normalized.** Core's skill model forces `model_invocable` off when a skill declares
    side effects and fills defaults (`tools`, `version`, `user_invocable`, `required`). Neither is
    done here: the round trip compares with the file (ADR-0036). Core's lax conversions (a quoted
    `confidence`) are refused by the schema, as [DIV-010](/conformance/divergence-register/) records.

## Consequences

### Positive

- A skill and its body are read, written and fingerprinted as a pair; editing only a body changes
  the pack's fingerprint and ETag.
- #193 and the toolguide guides can extend the same hooks instead of building a second non-YAML
  path. They may need more than reuse: an asset names its payload relative to the pack, not to its
  own file, so a binding would choose its resolution base, and the `*.yaml` exclusion may have to
  narrow.
  *(Amended 2026-10-27, see Status: #193 shares the low-level pieces instead of extending these
  hooks: one containment check, one bytes read, one atomic write and decision 7's order.)*
  *(Amended 2026-10-29, see Status: the toolguide guide extends these hooks as a `markdown`
  companion.)*
- A companion can never overwrite another part of the pack, through the upsert or the whole-pack
  write.

### Negative

- The fingerprint parses each skill file once more to name its body.
- The revision digests still read text; a binary companion would fail them until #193 moves them to
  bytes.
  *(Amended 2026-10-27, see Status: no longer holds; the commit path's revision is computed over
  bytes.)*
- The #187 gate covers only the built-in pack, which has no skill. Its expectation lists `skill` as
  an empty modelled kind (`empty_kinds`); the gate does not yet pair a companion with its artifact,
  and that entry goes stale, forcing the pairing, the day the built-in pack gains a skill. Skills are
  proved by a corpus test over every pinned skill and by the open-packs seam test.
- The `built-in`, `qa`, `internal` and `event-driven` packs' fingerprints and ETags change once, as binding any
  kind does (ADR-0037).

## Alternatives considered

- **Leave the body unmodelled**, listed by the whole-pack load, with a follow-up issue. Rejected by
  the operator: the issue keeps the pair on write.
- **Let the YAML strategy read the body.** The format would own paths, which the store owns.
- **A `*.skill.md` naming convention for the digest.** The schema leaves `body_path` free.
- **Pass the strategy into `PackFileSet.digested`.** It would change three callers' constructors and
  the deployable wiring for one kind; the static read gives the same single source of truth.
- **A third amendment to ADR-0037.** #190 and #191 amend it already; a separate record keeps the
  decision findable.
