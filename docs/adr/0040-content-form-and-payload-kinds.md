---
title: "ADR-0040: A file-kind rule declares its content form, and asset payloads and templates are pack content"
description: How a pack file is read and compared is declared by its file-kind rule; asset payloads and top-level templates are read, written and fingerprinted as bytes.
doc_status: draft
updated: 2026-10-28T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
  - adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a file kind that is not YAML, or before touching how the store reads, writes or digests asset payloads and templates.
  - profile: pack-author
    guidance_text: Read this for how an asset's sidecar and payload, and a template under templates/, are read and written back, and what makes a sidecar refused.
  - profile: core-maintainer
    guidance_text: Read this for the content_form key and payloads section of file-kinds.yaml, and the asset path anchoring the built-in pack must fix.
---

# ADR-0040: A file-kind rule declares its content form, and asset payloads and templates are pack content

## Status

**Proposed** — 2026-10-27. Decider: Stijn Dejongh (operator). Records the operator rulings of
2026-10-27 on [#193](https://github.com/spec-kitty/spec-kitty-charter/issues/193) (part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), numbered R1 to R11 in the
mission's research and held under `kitty-specs/asset-template-round-trip-01M5YY7G/decisions/`, and
the reconciliation with [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) the
operator ruled the same day (`DM-01M5ZAMP0R5V5YF5KM09YXY0NS`). The code #193 ships implements
it, so the points it amends in ADR-0036 and ADR-0039 are in force for that code; the record stays
Proposed pending the operator's acceptance, which the pull request for #193 asks for.

It widens [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) decision 4
(Accepted), which left non-YAML content to the kind missions.

It **supersedes in part** [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
(Accepted), on rulings R8 (`DM-01M5YY9WFDRVZT6GZS8X59E2AZ`), R9 (`DM-01M5YYA2CT1YSH7N6KVEPQ3XQC`),
R1 (`DM-01M5YY8HQKCGHH77B7VY0GHYC4`), R4 (`DM-01M5YY93YD7JZJZSV4W9H5EBCQ`) and R11
(`DM-01M5YYAKM5S4TD42P0V115YFW0`). The points it supersedes are Decision 2's "Other files"
paragraph, Decision 6's list of exclusions, the per-kind table's introduction ("Compared as"), and
the per-kind rows `**/*.asset.yaml`, `**/*.config.yaml`, `*.md` and "Any other file". ADR-0036
keeps its text and carries a dated amendment under its Status, with each point annotated where it
stands.

It amends [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) (Accepted) decision 2
and two of its consequences, on the reconciliation ruling `DM-01M5ZAMP0R5V5YF5KM09YXY0NS` (see the
amendment in that record).

### Amendment 2026-10-28 (#195): the fingerprint gains a pack-level tail

Extended in part by
[ADR-0043](/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest/) (Proposed,
2026-10-28) for [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195), on the
mission's decision `DM-01M60H8FP4AJ8TJWH670JT4WAV` under
`kitty-specs/pack-level-files-round-trip-01M60H1A/decisions/`: every file the store reads is in
the fingerprint.
It changes decision 9 only:

- **Decision 9, the order.** After the templates, `PackFileSet.digested` ends with the pack-level
  tail, in one ordered step: the presets, in path order, then `pack-manifest.yaml`. When `pack.md`
  is held through #194's `markdown` form, it joins the tail between the two. A pack with no
  presets and no manifest keeps its exact digest list and fingerprint. A pack that holds them gets a
  new `Charter-Source-Fingerprint` and ETag once.

Decision 1's table still admits `yaml` and `bytes` only. The `markdown` form is #194's to build,
and `pack.md` uses it when built (ADR-0043 decision 8). The decision text below is unchanged, and
decision 9 is annotated where it stands. The amended point is in force for the code #195 ships,
which implements it. Both records are Proposed, pending the operator's acceptance, which the pull
requests for #193 and #195 ask for.

## Context

Issue #193 asks for the two payload-bearing kinds: an **asset** (a `*.asset.yaml` sidecar and the
file its `path` names, relative to the pack's `assets/`) and a **template** (a file under the
pack's top-level `templates/`). Neither is activatable. Both carry a file that is not YAML: scripts,
Markdown, HTML, fonts and an image, across 20 sidecars, 23 files under `assets/` and 2 templates at
`openPacksRef` `01edb4e`. Four of the files are binary, with NUL bytes, CR/LF byte pairs and no
final newline.

Four facts force a decision:

- **The storage seam moved text only.** The file walk took `*.yaml` files, the serialization
  strategy returned strings, and both stores wrote with `Files.writeString`. ADR-0037 decision 4
  names that limit and leaves it to the kind missions.
- **ADR-0036 chose a comparison by file extension.** A `*.md` file compared frontmatter as YAML
  and body as normalized text; any other file compared by bytes. A Markdown asset payload sat in
  both rows. The operator ruled that the form must not depend on the extension (R8).
- **Core checks `mime` against the extension** (`mimetypes.guess_type`). Choosing the form by
  `mime` would restate the extension, cover assets only and add a second authority beside
  `file-kinds.yaml`.
- **ADR-0039 landed first** (#192). Its companions are files an artifact names, resolved against
  the artifact's own file, never YAML, with one owner each. An asset payload resolves against
  `assets/`, may have no sidecar at all (`built-in/assets/docs_structural_lint.config.yaml`, R1)
  and may be YAML; a template is an artifact in its own right (R2).

## Decision

1. **A file-kind rule declares its content form.** A rule of `contracts/packs/file-kinds.yaml`
   says how its files are read, held, written and compared, with `content_form`: `yaml` (the
   default, so the existing `files` rules do not write it), `markdown`, `text` or `bytes`. A file's
   extension and an asset's `mime` never choose it. `mime` stays metadata: kept as written,
   validated by the asset schema (a string that is not empty) and, in validate mode, by
   `PackPolicy` through `AssetMimeTable` (a well-formed `type/subtype`, consistent with the
   payload's extension, a policy finding when it is not), and never a selector. The table
   admits only the forms the store builds, `yaml` and `bytes`; a rule naming `markdown` or `text`
   fails when the table loads, naming the form, and so does any key a rule's section does not
   define. The mission that first needs another form builds it and admits it. The form decides how
   the store holds a file, never its path or its kind token alone: a `bytes` file is held by its
   rule's kind, an `asset-payload` as an asset file and a `template` as a template (decision 4), and
   a `bytes` rule of any other kind fails when the table loads, naming the rule. A `payloads` rule
   has no schema and the store holds a schema-less file only as `bytes`, so a `payloads` rule
   declaring any other form fails when the table loads, naming the rule and the form, rather than
   leave its files held nowhere. A `files` rule has a
   schema, which validates a parsed YAML document, so a `files` rule declaring any form but `yaml`
   fails when the table loads, naming the rule: no schema-bearing kind is `bytes` today.
2. **Files with no schema have their own section, `payloads`.** A payload rule names `match`,
   `kind` and `content_form`, all required. It is consulted only when no `files` and no
   `exclusions` rule matches, so it overlaps them by design and the first match counts; no two
   payload rules may match one file. The no-overlap check over `files` and `exclusions` is
   unchanged, and the schema iteration (`PackSchemas`) still reads `files` only. The rules:
   - `*/assets/**/*` is an `asset-payload` of the `bytes` form;
   - `*/templates/**/*` is a `template` of the `bytes` form. The single leading `*/` covers only a
     pack's top-level `templates/`: mission templates (`missions/*/templates/`) stay outside this
     record (#194);
   - `*/assets/**/README.md` is excluded: an orientation note, not a payload (R4);
   - `*/assets/**/.*` and `*/templates/**/.*` are excluded: a hidden file at any depth (a
     `.DS_Store`, a `.gitkeep`, the store's own `.pack-write-*.tmp` temporary file) is not pack
     content. A hidden file that a `files` rule maps keeps that kind, since `files` rules come first;
   - the `**/*.config.yaml` exclusion is retired: its only match is the sidecar-less asset payload
     above (R1).

   Validate mode walks `*.yaml` files only: it lists a YAML payload (the sidecar-less
   `*.config.yaml`) with its kind and does not parse it, and of the rest it checks that a sidecar's
   `path` stays inside `assets/`, not that the payload exists. That is a known gap: on the built-in
   pack at the pin, validate mode reports no finding for the six sidecars the load refuses
   (decision 6). It is tracked as a follow-up issue.
3. **Asset payloads and templates are `bytes`.** They are read with no charset, held as a
   `Payload` (the exact bytes; equal when the bytes are equal), written byte for byte and compared
   byte for byte by the whole-pack gate, which reports the first differing offset. A payload that
   is not UTF-8 is no violation. An empty file round-trips as empty. Frontmatter in a template is
   bytes like the rest (R9). `markdown` is named and not built here. #192
   ([ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/)) did not build it either: it
   shipped companions, text files an artifact names, read and written byte for byte as UTF-8
   (`CompanionContent.Text`), with no frontmatter split and no normalization. `markdown` stays a
   declared, unbuilt form until a kind needs it.
4. **The registry widens, without a second registry** (ADR-0037 decision 4). The file walk
   classifies every regular file once, not only `*.yaml`. In the domain:
   - an **`Asset`** is a `CharterArtifact` holding the sidecar's metadata (`id`, `mime`, `path` as
     authored, optional `title`; no schema version). Its sidecar is an ordinary YAML kind, bound by
     `AssetBinding` and appended to `KindBindings` after the skill;
   - an **`AssetFile`** (a path under `assets/` and its `Payload`) is the one pack-level collection
     beside the kind map. Every file the `asset-payload` rule classifies is one, whether or not a
     sidecar names it (R1), so the sidecar-less payload needs no invented id. `Pack.toBuilder` and
     `Pack.with` carry the asset files, so a registration keeps them;
   - a **`Template`** is a `CharterArtifact` whose local id is its path under `templates/`
     (`writing/meeting-minutes.md`, URN `template:writing/meeting-minutes.md`, the `<dir>/<file>`
     shape core mints for mission templates) and whose content is a `Payload`. It is mapped by a
     concrete `TemplateBinding`, not a `KindBinding`, which is YAML- and schema-shaped. The binding
     interface for non-YAML kinds is extracted at the second such kind (the rule of three), not
     here. A duplicate template id is refused. Two templates or asset files whose paths fold to one
     path with case ignored are refused on the whole-pack write; the per-artifact store save does
     not yet carry that case-fold guard
     ([#252](https://github.com/spec-kitty/spec-kitty-charter/issues/252)). One file has one owner: a template or an asset file whose path
     would not classify back as its kind (`templates/x.tactic.yaml` is a tactic's file,
     `templates/plan-field-declaration.yaml` is excluded) is refused on the store's save and on the
     whole-pack write, `is a pack file of kind <kind>, not a template`, as a companion is.

   No repository port is added. A sidecar with a key its schema does not declare is refused naming
   the key (`undeclared key '<name>'`); the store's schema reason names an undeclared key this way
   for every kind.
5. **Asset payloads and templates are not companions; the low-level pieces are shared.** An
   ADR-0039 companion is owned by an artifact and resolved against its file. A payload with no
   sidecar has no owner (R1), a template is an artifact itself (R2), and a payload's base is
   `assets/`. So `CompanionContent` keeps its one `Text` case and gains no `Bytes` case until a
   companion needs one. What both share, so there is one path and not two:
   - one containment rule, `Companions.inPack`, for every pack-relative path the store writes
     (asset files, templates, every part of a whole-pack write), with the companion rule built on
     the same check; and one real-path check (`FilesystemPackStore.realPathContained`) for every
     companion, asset file and template the store reads, so a file link whose real location leaves
     the pack is a violation and is not read;
   - in the store, one bytes read (`FilesystemPackStore.readBytes`), with text as a strict UTF-8
     view over it. The source fingerprint (`FileSystemPackSources`), the commit path's revision
     (`PackCommitStore`, `CommitStorePackWriteStore`) and validate mode (`FileSystemPackInspector`)
     read bytes on their own, as before;
   - in the store, one atomic write (`writeAtomically`) for artifact files, companions, asset files
     and templates. The whole-pack write stages its files with its own `put` and swaps the directory
     (ADR-0037 decision 5);
   - one fingerprint order (decision 9) and one revision over bytes.

   Nor can a companion be one. One file has one owner (ADR-0039 decision 3): a companion path that
   resolves to a file a `payloads` rule classifies (an asset file, a template) is refused by the
   companion rule, `is a pack file of kind <kind>, not a companion`, on the read, the upsert and the
   fingerprint alike. The file stays with its rule; the artifact naming it is left out as a
   `companion` violation, so the whole-pack write refuses the pack (ADR-0037 decision 5). It is a
   pack inconsistency, fixed in the pack, with no precedence between the two claims.
6. **A sidecar whose payload is missing is refused, as a violation** (R3). The store holds a
   sidecar only when `assets/<path>` is an asset file it read; otherwise the sidecar's file is a
   violation of kind `asset`, `payload assets/<path> does not exist`, and the sidecar does not join
   the pack. A path that leaves `assets/` or names a file another rule classifies is the same
   refusal. A second sidecar naming a file an earlier one named is refused,
   `payload assets/<path> is already named by asset <id>`. The `Pack` guards the link: every asset
   names a held asset file, and no two assets name the same one. The whole-pack write refuses a pack
   with any violation (ADR-0037 decision 5).

   The six built-in sidecars write `path: assets/...`, anchored at the pack root, against the
   schema and core's model. That is a pack inconsistency, fixed in `spec-kitty-open-packs`
   ([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75), ADR-0036
   Decision 5); the service adds no built-in anchoring as core does (ruling 3 of ADR-0036). The six
   refusals are pinned with their exact details (`refused: payload assets/<path> does not exist`)
   as known failures in the gate's known-failure list
   ([#247](https://github.com/spec-kitty/spec-kitty-charter/issues/247)), which both the
   whole-pack gate and the open-packs round trip read: either fails on a new refusal or on a pinned
   one that no longer occurs (R10), and the gate fails once the pin moves past the fix until the
   six entries are removed. A copy of the built-in pack with the six paths corrected round-trips whole.
7. **File mode is not meaning** (R5). The executable bit is neither compared nor preserved: a
   written payload has the default mode.
8. **`asset:` and `template:` URNs are relationship endpoints** once the pack holds the artifact
   (R6). This follows from the artifacts being in the pack; an unknown URN is still refused, and a
   mutability policy that locks a kind refuses an edge to it. It is a served behavior change with no
   change to the shape of the HTTP contract.
9. **The fingerprint and the revision cover the bytes.** `PackFileSet.digested` lists `pack.yaml`,
   every file of every bound kind (each followed by its companions, ADR-0039 decision 7, with the
   asset sidecars the last bound kind), the root graph shards and `drg/fragment.yaml`, then the asset
   files, then the templates; no companion is an asset file or a template (decision 5), so each is
   listed once. A pack with no asset files and no templates keeps its exact digest list and
   fingerprint. A pack that holds them gets a new `Charter-Source-Fingerprint` and ETag once, as
   binding any kind does (ADR-0037). The commit path's revision is computed over bytes
   (`PackRevision.computeOverBytes`); the text form is the same computation over UTF-8, so every
   revision of a text-only pack keeps its value, and a pack with a binary payload commits and
   answers `If-Match`.
   *(Extended by ADR-0043, 2026-10-28: after the templates come the presets, in path order, then
   `pack-manifest.yaml`; `pack.md` joins between them with #194's form. See the amendment under
   Status.)*

## Consequences

### Positive

- How a file is compared has one authority, the file-kind rule, for every kind to come: a
  Markdown kind declares `markdown` on its own rule and changes no other row.
- Every asset payload and template at the pin round-trips byte for byte; a one-byte change is a
  gate failure at that file and a new fingerprint.
- A broken pack (a sidecar naming nothing) is never written as if whole.
- In the store, one containment rule, one bytes read and one atomic write serve companions and
  payloads alike.

### Negative

- The `Pack` holds file paths for one collection, the asset files: the recorded exception to
  ADR-0037's "the kind entities stay free of paths".
- `TemplateBinding` is a second binding shape beside `KindBinding` until the interface is extracted.
- The `built-in`, `internal`, `qa` and `event-driven` packs' fingerprints and ETags change once.
- The built-in pack's six sidecars stay refused until the open-packs fix
  ([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75)) lands and
  `openPacksRef` moves; the gate then fails until the six known-failure entries are removed.
- A written internal pack's scripts lose their executable bit.
- The store's schema reason for an undeclared key now ends with the key, for every kind.
- A load holds every asset file and template in memory, as the whole-pack gate needs to compare
  them. That is about 0.6 MB for the largest open pack today (NFR-004); it is a scaling limit, and a
  pack with large payloads would need them streamed.
- Validate mode does not check that a sidecar's payload exists (decision 2), so it can pass a pack
  the load refuses.

### Risks

- The `payloads` section could grow into a second `files` list. A payload rule has no schema by
  definition; a schema-bearing kind belongs in `files`, whose rules are `yaml` until a schema-bearing
  kind of another form is built.
- A payload rule shadowing a future `files` rule is resolved by order, silently. The sample-path
  tests catch it for the paths they list.

## Alternatives considered

### Extension-based rows

ADR-0036's original reading. Rejected by the operator (R8): a Markdown asset payload matched two
rows, and the extension says how a file is named, not what it holds.

### The asset's `mime` decides

Rejected: it restates the extension (core checks one against the other), covers assets only, and
puts a second authority beside `file-kinds.yaml`.

### Classify a payload only by the sidecar that names it

Rejected by the operator (R1): the sidecar-less config file would stay excluded or unread.

### The form on `KindBinding`

Rejected: a second authority beside the rule, and invisible to the census and validate mode.

### A sealed union of bindings

Rejected for now: one non-YAML kind does not justify reshaping the registry siblings append to.
It is the extraction point when a second one arrives.

### Asset payloads as ADR-0039 companions, with a `Bytes` case

Rejected: a companion needs an owning artifact and resolves against its file, and refuses YAML.
It cannot hold a payload without a sidecar, a template that is an artifact, or a base of
`assets/`. Only the low-level pieces are shared (decision 5).

### Switch the gate to `WholePackStore`

Deferred: the gate's adapter is shared with every Wave 1 sibling. The gate keeps `DomainRoundTrip`
and compares a `bytes` file by bytes.

## References

- `contracts/packs/file-kinds.yaml` (`content_form`, `payloads`), `contracts/packs/README.md`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/`: `Asset.java`, `AssetFile.java`,
  `Payload.java`, `Template.java`, `TemplateIdentifier.java`, `Pack.java`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`: `ContentForm.java`,
  `PackFileKind.java`, `PackFileSet.java`, `FilesystemPackStore.java`, `Companions.java`,
  `AssetBinding.java`, `TemplateBinding.java`, `WholePackStore.java`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java`.
- `service/packs/src/test/resources/whole-pack/known-failures.yaml` (the six pinned refusals).
- [spec-kitty-open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75) (the
  fix for the six built-in sidecars).
- [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/),
  [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/),
  [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/).
