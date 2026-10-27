# Pack schemas

JSON Schemas (Draft 2020-12) for everything an author writes in a Charter Pack: the pack
descriptor, graph shards, the org fragment, the org charter, the eleven artifact kinds, and the
project `charter.yaml`.

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
| `schemas/<kind>.schema.json` | One schema per kind, 16 in all |
| `file-kinds.yaml` | Node kinds, fragment plurals, which file gets which schema, the reviewed exclusions, and the schema-less payloads |

`file-kinds.yaml` classifies a pack file in three steps: its `files` rules map a file to the schema that
validates it, its `exclusions` name files that are not pack content or have no schema (each with a reviewable
reason), and its `payloads` rules, consulted only when neither matches, give a schema-less file a kind: a file
under a pack's `assets/` is an `asset-payload` and a file under its top-level `templates/` is a `template`. Each
rule also says the file's **content form**, how it is read, held, written and compared: `yaml` (the default of a
`files` rule), `markdown`, `text` or `bytes`. The form decides how the service holds the file: a `bytes` file is
held by its rule's kind, an `asset-payload` as an asset file and a `template` as a template, round-tripped byte for
byte; a `yaml` file is validated by its rule's schema. A file's extension and an asset's `mime` never choose the
form. The table fails when it loads, naming the rule, if a rule names a form the service does not build yet
(`markdown`, `text`), a `files` rule names a form other than `yaml` (every schema-bearing kind is YAML today), or a
`bytes` rule names a kind other than `asset-payload` or `template`.

## Validate a file

1. Find the schema for the file in `file-kinds.yaml`. `pack.yaml` at a pack root is
   `pack-descriptor`, `*.graph.yaml` is `graph-shard`, `drg/fragment.yaml` is `org-fragment`, and
   `*.tactic.yaml` is `tactic`. Files under `exclusions` have no schema.
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
