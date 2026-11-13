# Pack schemas

JSON Schemas (Draft 2020-12) for everything an author writes in a Charter Pack: the pack
descriptor, graph shards, the org fragment, the presets and the pack manifest,
the artifact kinds that have a schema (a template and an anti-pattern have none), the Markdown
files with a structure (a toolguide's guide, the pack document `README.md`, a mission step's prompt
and guidelines, and a step enhancement's prompt fragment), and the project `charter.yaml`.

These schemas are canonical in this repository and are edited in place; core consumes them
([ADR-0015](../../docs/adr/0015-charter-repository-is-canonical-for-charter-structure.md) decisions 1, 2
and 5, ruling R20). Their history is a one-time import from core commit `9554e7b2` with changes C1-C16,
recorded in the mission's
[`research.md`](../../kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16)
and in git history. Of the schemas added later, two have outside sources: `skill` is core's
`skill.schema.yaml` at `581b5c46da` as JSON, and `glossary-pack` is authored here from core's pydantic
model, since core exports no schema for it. The rest were designed here: `preset`, `pack-manifest`
and `pack-document` (ADR-0042), `toolguide-guide` (ADR-0043), `mission-type` with its two step
schemas (ADR-0044), and `step-enhancement` with its `prompt-fragment` companion (ADR-0053 decision 22
and ADR-0066). Where a schema differs from core's behavior, the
[divergence register](../../docs/conformance/divergence-register.md) says why.

## Files

| Path | Holds |
|---|---|
| `schemas/<kind>.schema.json` | One schema per kind, 27 in all (the pack-level `preset` and `pack-manifest` included, ADR-0042), five of them Markdown structure schemas (`toolguide-guide`, `pack-document`, `mission-step-prompt`, `mission-step-guidelines`, `prompt-fragment`; ADR-0043, ADR-0042 decision 8, ADR-0044, ADR-0066) |
| `file-kinds.yaml` | Node kinds, fragment plurals, which file gets which schema, the reviewed exclusions, the retired files, the schema-less payloads and the `ignored` fallback |
| `conformance/classifier-cases.yaml` | The classifier conformance cases (#559): a pack file path and the outcome an implementation of `file-kinds.yaml` must give it, one case for every rule at least |
| `CHANGELOG.md` | What changed in the packs contract, newest first; its top heading is the packs contract's version |

`file-kinds.yaml` classifies a pack file in five steps, in this order: its `files` rules map a file to the schema
that validates it, its `exclusions` name files that are not pack content or have no schema (each with a reviewable
reason), its `retired` rules name the files of a layout the service no longer reads, and its `payloads` rules,
consulted only when none of those matches, give a schema-less file a kind: a file
under a pack's `assets/` is an `asset-payload`, and a file under its top-level `templates/` or under a mission
type's `missions/<type>/templates/` is a `template` (a `README.md` under `assets/` or `missions/`, and a hidden file
under either templates directory or `assets/`, are excluded). A retired file is refused by name with its rule's
reason, never skipped (ADR-0044 decision 6); the pack-level `pack.md` and `PROVENANCE.md` (#451) and
`org-charter.yaml` (#431) are retired rules too, and a root file that differs from a root rule's file only in letter
case (`Readme.md`, `Pack.md`) is refused by name under that rule. Last, after the companion claims of the kind
bindings (a skill's body, a toolguide's guide), the `ignored` rules (`match` and `reason`, both required) take what
nothing else took: `**/*.md`, so Markdown no rule models (an author's `docs/notes.md`) is excluded. An excluded file,
whether an `exclusions` or an `ignored` match, is not held, not served, not digested and not refused: a registration
removes it before it commits and reports it as a `file_not_held` diagnostic (ADR-0073). A file that no step takes
and that is not Markdown is refused as `unmodelled`. Each
rule also says the file's **content form**, how it is read, held, written and compared: `yaml` (the default of a
`files` rule), `markdown`, `text` or `bytes`. The form decides how the service holds the file: a `bytes` file is
held by its rule's kind, an `asset-payload` as an asset file and a `template` as a template, round-tripped byte for
byte; a `yaml` file is validated by its rule's schema. A file's extension and an asset's `mime` never choose the
form. The table fails when it loads, naming the rule, if a rule names `text` (a text companion exists, such as a
skill's body, #192, but no standalone `text` kind or `files` rule does, #432), a `files` rule names `bytes` (a schema validates a parsed document), a `markdown` rule's schema does not
load, a `payloads` rule names a form other than `bytes` (the only form the service holds a schema-less file in), or
a `bytes` rule names a kind other than `asset-payload` or `template`.

A `files` rule may declare `content_form: markdown` with a `schema`: its files are read into a Markdown model
(optional frontmatter, optional H1 title, a preamble, and the H2 sections in order, each a `title` and its
`content`), compared by that model and written back in a canonical form. The rule's schema is a **structure
schema**, a Draft 2020-12 schema in `schemas/` with standard keywords only, that validates the model as the JSON
tree `{"frontmatter": {...}, "title": "...", "preamble": "...", "sections": [{"title": "...", "content": "..."}]}`;
`frontmatter` and `title` are left out when the document has none. Five rules declare `markdown`: the toolguide
guide (`**/toolguides/*.guide.md`, schema `toolguide-guide`), the pack document (`*/README.md`, schema
`pack-document`), a mission step's prompt and guidelines
(`*/missions/*/steps/*.prompt.md` and `*.guidelines.md`, schemas `mission-step-prompt` and
`mission-step-guidelines`), and a step enhancement's prompt fragment
(`*/missions/*/enhancements/*.prompt-fragment.md`, schema `prompt-fragment`).

## Prompt slots and fragments (ADR-0066)

A step enhancement is an artifact of the enhancing pack at
`missions/<type>/enhancements/<id>.step-enhancement.yaml`. Its URN is `step_enhancement:<id>`, its fragment node
kind is `step_enhancements`, and its `id` equals its file stem. It has no `target` field: exactly one `enhances`
edge in the enhancing pack's graph documents joins it to `action:<type>/<step>`, pack-qualified across packs. The
shape is closed, with required `schema_version: "1.0"` and `id`, and the optional additions list
`prompt_fragments`. A preset activates it with `activated_step_enhancements`, applied as
`selected.step_enhancement`; no previous preset key named this kind. There is no second activation gate for its
fragments.

Each `prompt_fragments` entry has exactly two fields:

```yaml
prompt_fragments:
  - slot: spdd:reasons-block
    path: spdd-specify.prompt-fragment.md
```

- `slot` is one `<namespace>:<name>` slot id. Each part matches `[a-z][a-z0-9-]*`; the namespace names a concern,
  not a pack. One enhancement names a slot at most once.
- `path` names one `<id>.prompt-fragment.md` companion beside the enhancement. It is a file name, not a
  subdirectory, URL or pack-qualified reference. The companion is Markdown content with no frontmatter and
  non-blank text in its H1, preamble or at least one H2. It contains no slot marker of any id.
- There is no range, anchor, position, priority or order field. The enhancement cannot replace base text or add a
  slot. Multiple enhancements into one slot compose; their service-side order is pack level, pack name and
  enhancement id, not activation order.

A base prompt declares a slot between `<!-- <slot-id>:start -->` and `<!-- <slot-id>:end -->`. Each id is declared
once, its start precedes its end, starts and ends pair correctly, and slots do not nest. Only the prompt in force
for the step declares slots; a fragment never does. The slot id grammar is the same as `prompt_fragments[].slot`.
The `mission-step-prompt` structure schema and `prompt-fragment` structure schema both document these rules, but
they validate the parsed Markdown model, so raw-marker checks are **semantic checks beside those schemas**, not
JSON Schema keywords. The service implementation of those checks and of compose resolution is #424.

The pack descriptor's optional `schema_version` is the string `"1.0"` when written. That version declares the
current `depends_on` shape and switches on the empty-slot check: every paired slot in the pack's mission step
prompts holds only whitespace. An unversioned descriptor is the legacy shape and keeps legacy text between paired
markers with a warning. Descriptor 1.0 makes that finding a refusal. There is no lenient flag; a pack empties its
slots and moves to 1.0 in the same change.

The load- and compose-time codes are:

| Code | Meaning |
|---|---|
| `prompt_slot_malformed` | A base prompt has a malformed id or marker, duplicate declaration, nesting, wrong order or unpaired marker. Always a refusal. |
| `prompt_slot_not_empty` | Non-whitespace occurs between paired markers. A refusal at descriptor 1.0, a warning for an unversioned descriptor. |
| `prompt_fragment_invalid` | An enhancement or fragment breaks a load rule: bad schema or slot grammar, a repeated slot, a missing or blank companion, frontmatter, a slot marker in a fragment, or a non-step `enhances` target. |
| `prompt_slot_missing` | At compose, regardless of activation, the prompt in force (a layer override included) declares no slot the enhancement names. |

A missing target pack or artifact keeps `pack_reference_pack_missing` or
`pack_reference_artifact_missing`; a locked target keeps `artifact_locked`. The four new refusal codes are reserved
with `raised: false` in `CharterRefusalCode` until #424 raises them. The warning spelling is reserved in
`DiagnosticCode`.

## Validate a file

1. Find the schema for the file in `file-kinds.yaml`. `pack.yaml` at a pack root is
   `pack-descriptor`, `*.graph.yaml` is `graph-shard`, `drg/fragment.yaml` is `org-fragment`, and
   `*.tactic.yaml` is `tactic`. Files under `exclusions` have no schema, and files under `retired` are
   refused.
2. Load the YAML and validate it against `schemas/<schema>.schema.json` with any Draft 2020-12
   validator. For example, with Python `jsonschema`:

   ```python
   import json, jsonschema
   from ruamel.yaml import YAML

   schema = json.load(open("contracts/packs/schemas/tactic.schema.json"))
   jsonschema.Draft202012Validator(schema).validate(YAML(typ="safe").load(open("x.tactic.yaml")))
   ```

All references are local, so nothing is fetched. The `$id` values are identifiers only.

## Change a schema

Edit the schema in `schemas/` and, when the change touches a node kind, a fragment plural or a file
rule, `file-kinds.yaml` in the same commit. `VocabularyEqualityTest`, `PackSchemasTest` and the
open-packs round-trip fail when the schemas, `file-kinds.yaml` and the service disagree.

Every change to a schema, `file-kinds.yaml` or the conformance cases adds a line to the top section of `CHANGELOG.md`
in the same pull request. A change to a rule of `file-kinds.yaml` also adds or changes its conformance case:
`ClassifierConformanceTest` runs every case against `PackFileKind.classify` and fails when a rule has no case, or a
case names a rule the table does not hold. `:contracts:checkPacksChangelog`, part of `check`, refuses a malformed or
misordered heading, or a 0.x minor bump without `### Breaking`.

## The packs contract and its releases (#559)

The schemas, `file-kinds.yaml` and the conformance cases are the **packs contract**: what a reader of packs outside
the service (doc-kitty's pack pages, open-packs' checks) vendors and holds itself to. It is versioned on its own
changes, apart from the charter contract: its version is the top heading of `CHANGELOG.md`, with the 0.x rules of
ADR-0022 (a minor may break while the major is 0, and lists each break under `### Breaking`). A consumer pins and
checks a release as [Pin the packs contract](../../docs/guides/pin-the-packs-contract.md) shows.

### Releasing the packs contract

1. In a pull request, turn the top heading of `CHANGELOG.md` into the release (`## 0.1.0`), adding `### Breaking` for
   a new minor while the major is 0.
2. After the merge, tag `main` at the merge commit, `git tag contract-packs-v0.1.0 <merge commit>`, and push the tag.
   Only the operator pushes this tag. Until a tag ruleset on `contract-packs-v*` is in place (#196, which covers
   `contract-charter-v*` today), anyone with write access could; push the first tag only once it is.
3. Open the next version: a new top heading `## 0.2.0-SNAPSHOT` (or the next patch).

Pushing the tag in step 2 publishes the release: `.github/workflows/packs-contract-release.yml` rebuilds the assets at
the tag with `scripts/release/packs-contract-release-assets.sh` and attaches `packs-contract.tar.gz`,
`packs-contract.tar.gz.sha256` and `packs-contract.sha256` to a GitHub Release of the tag; the archive also holds
`packs-contract/RELEASE.json` (the version, the tag and the commit). It refuses, and publishes
nothing, unless the tag and the top heading name one released version
(`./gradlew :contracts:checkPacksReleaseTag -PreleaseTag=<tag>`), the classifier passes the conformance cases, and the
tagged commit is on `main`; it also refuses a tag that moved while the assets were built. `service.yml` rehearses the whole path on every change
(`scripts/release/rehearse-packs-contract-release.sh`). To rehearse a real release, run the assets script with the tag
on the release pull request's branch, or the workflow by hand (workflow_dispatch): neither creates a Release.
