# Pack schemas

JSON Schemas (Draft 2020-12) for everything an author writes in a Charter Pack: the pack
descriptor, graph shards, the org fragment, the org charter, the nine artifact kinds, and the
project `charter.yaml`. They are canonical here (ADR-0015); core aligns to them. They were
imported from core and changed on import, and every change is listed in
[`VENDORED.md`](VENDORED.md).

## Files

| Path | Holds |
|---|---|
| `schemas/<kind>.schema.json` | One schema per kind, 14 in all |
| `file-kinds.yaml` | Node kinds, fragment plurals, which file gets which schema, and the reviewed exclusions |
| `VENDORED.md` | Origin, every change from core's export, and the reproducibility rule |

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

## Regenerate and check

```bash
~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py          # regenerate
~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py --check  # fail on any difference
```

Do not edit a schema by hand: change the script or `file-kinds.yaml`, then regenerate.
