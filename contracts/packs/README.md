# Pack schemas

JSON Schemas (Draft 2020-12) for everything an author writes in a Charter Pack: the pack
descriptor, graph shards, the org fragment, the org charter, the presets and the pack manifest,
the twelve artifact kinds that have a schema (a template and an anti-pattern have none), the
Markdown companions with a structure (a toolguide's guide, a mission step's prompt and
guidelines), and the project `charter.yaml`.

These schemas are canonical in this repository and are edited in place; core consumes them
([ADR-0015](../../docs/adr/0015-charter-repository-is-canonical-for-charter-structure.md) decisions 1, 2
and 5, ruling R20). Their history is a one-time import from core commit `9554e7b2` with changes C1-C16,
recorded in the mission's
[`research.md`](../../kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16)
and in git history. The two added later have their own sources: `skill` is core's
`skill.schema.yaml` at `581b5c46da` as JSON, and `glossary-pack` is authored here from core's pydantic
model, since core exports no schema for it. Where a schema differs from core's behavior, the
[divergence register](../../docs/conformance/divergence-register.md) says why.

## Files

| Path | Holds |
|---|---|
| `schemas/<kind>.schema.json` | One schema per kind, 23 in all (the pack-level `preset` and `pack-manifest` included, ADR-0042), four of them Markdown structure schemas (`toolguide-guide`, `pack-document`, `mission-step-prompt`, `mission-step-guidelines`; ADR-0043, ADR-0042 decision 8, ADR-0044) |
| `file-kinds.yaml` | Node kinds, fragment plurals, which file gets which schema, the reviewed exclusions, the schema-less payloads and the retired files |

`file-kinds.yaml` classifies a pack file in four steps, in this order: its `files` rules map a file to the schema
that validates it, its `exclusions` name files that are not pack content or have no schema (each with a reviewable
reason), its `retired` rules name the files of a layout the service no longer reads, and its `payloads` rules,
consulted only when none of those matches, give a schema-less file a kind: a file
under a pack's `assets/` is an `asset-payload`, and a file under its top-level `templates/` or under a mission
type's `missions/<type>/templates/` is a `template` (a `README.md` under `assets/` or `missions/`, and a hidden file
under either templates directory or `assets/`, are excluded). A retired file is refused by name with its rule's
reason, never skipped (ADR-0044 decision 6). Each
rule also says the file's **content form**, how it is read, held, written and compared: `yaml` (the default of a
`files` rule), `markdown`, `text` or `bytes`. The form decides how the service holds the file: a `bytes` file is
held by its rule's kind, an `asset-payload` as an asset file and a `template` as a template, round-tripped byte for
byte; a `yaml` file is validated by its rule's schema. A file's extension and an asset's `mime` never choose the
form. The table fails when it loads, naming the rule, if a rule names a form the service does not build yet
(`text`), a `files` rule names `bytes` (a schema validates a parsed document), a `markdown` rule's schema does not
load, a `payloads` rule names a form other than `bytes` (the only form the service holds a schema-less file in), or
a `bytes` rule names a kind other than `asset-payload` or `template`.

A `files` rule may declare `content_form: markdown` with a `schema`: its files are read into a Markdown model
(optional frontmatter, optional H1 title, a preamble, and the H2 sections in order, each a `title` and its
`content`), compared by that model and written back in a canonical form. The rule's schema is a **structure
schema**, a Draft 2020-12 schema in `schemas/` with standard keywords only, that validates the model as the JSON
tree `{"frontmatter": {...}, "title": "...", "preamble": "...", "sections": [{"title": "...", "content": "..."}]}`;
`frontmatter` and `title` are left out when the document has none. Four rules declare `markdown`: the toolguide
guide (`**/toolguides/*.guide.md`, schema `toolguide-guide`), the pack document (`*/pack.md`, schema
`pack-document`) and a mission step's prompt and guidelines
(`*/missions/*/steps/*.prompt.md` and `*.guidelines.md`, schemas `mission-step-prompt` and
`mission-step-guidelines`).

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
