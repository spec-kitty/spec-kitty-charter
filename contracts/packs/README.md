# Pack schemas

JSON Schemas (Draft 2020-12) for everything an author writes in a Charter Pack: the pack
descriptor, graph shards, the org fragment, the org charter, the nine artifact kinds, and the
project `charter.yaml`.

These schemas are canonical in this repository and are edited in place; core consumes them
([ADR-0015](../../docs/adr/0015-charter-repository-is-canonical-for-charter-structure.md) decisions 1, 2
and 5, ruling R20). Their history is a one-time import from core commit `9554e7b2` with changes C1-C16,
recorded in the mission's
[`research.md`](../../kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16)
and in git history. Where a schema differs from core's behavior, the
[divergence register](../../docs/conformance/divergence-register.md) says why.

## Files

| Path | Holds |
|---|---|
| `schemas/<kind>.schema.json` | One schema per kind, 14 in all |
| `file-kinds.yaml` | Node kinds, fragment plurals, which file gets which schema, and the reviewed exclusions |

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
