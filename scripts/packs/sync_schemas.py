#!/usr/bin/env python3
"""Export core's pack models as JSON Schema and apply this repository's recorded changes.

The committed files under ``contracts/packs/schemas/`` are exactly
``transform(export(core@<commit in VENDORED.md>))`` (ADR-0015 D4, ADR-0017). Every change
from the raw export is a function named after its change id (``c1_...`` to ``c16_...``) and is
listed in ``contracts/packs/VENDORED.md`` with its ruling.

Usage (always through the core venv, isolated)::

    ~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py            # regenerate the schemas
    ~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py --check    # prove they are reproducible
    ~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py --ledger   # print the C2 ledger
    ~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py --probe CHECKOUT   # corpus probe

The build never validates with a JSON Schema library (C-002). ``--probe`` imports ``jsonschema``
lazily, and only there.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib
import importlib.metadata
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
PACKS_DIR = REPO_ROOT / "contracts" / "packs"
SCHEMAS_DIR = PACKS_DIR / "schemas"
FILE_KINDS = PACKS_DIR / "file-kinds.yaml"
VENDORED = PACKS_DIR / "VENDORED.md"

DRAFT = "https://json-schema.org/draft/2020-12/schema"
ID_BASE = "https://schemas.spec-kitty.ai/charter/packs/"

# schema name -> (module, class). The 14 models, in the order of the WP02 frontmatter.
MODELS: dict[str, tuple[str, str]] = {
    "pack-descriptor": ("charter.offering.packs.pack_descriptor", "PackDescriptor"),
    "graph-shard": ("charter.offering.drg.models", "DRGGraph"),
    "org-fragment": ("charter.offering.drg.org_pack_loader", "OrgDRGFragment"),
    "org-charter": ("charter.activation.org_charter", "OrgCharterPolicy"),
    "directive": ("charter.offering.directives.models", "Directive"),
    "tactic": ("charter.offering.tactics.models", "Tactic"),
    "styleguide": ("charter.offering.styleguides.models", "Styleguide"),
    "toolguide": ("charter.offering.toolguides.models", "Toolguide"),
    "paradigm": ("charter.offering.paradigms.models", "Paradigm"),
    "procedure": ("charter.offering.procedures.models", "Procedure"),
    "agent-profile": ("charter.offering.agent_profiles.profile", "AgentProfile"),
    "mission-step-contract": ("charter.offering.missions.step_contracts", "MissionStepContract"),
    "asset": ("charter.offering.assets.models", "AssetManifest"),
    "charter": ("charter.activation.schemas", "CharterYaml"),
}

URN_ID_CLASS = "[A-Za-z0-9_/.-]"
LOWERCASE_ROLE = "^[a-z][a-z0-9_-]*$"
# Constructs allowed in a pattern: ECMA-262 and Java agree on them (C14).
PORTABLE_PATTERN = re.compile(
    r"^(?:\^|\$|\||\(|\)|\.|\*|\+|\?|\{\d+(?:,\d*)?\}"
    r"|\\[.\\dD/\-]"
    r"|\[\^?(?:\\[.\\dD/\-]|[^\]\\])+\]"
    r"|[A-Za-z0-9_ :/-])+$"
)

# The shard relation enum is core's (drg/models.py:134-150) plus `impacts` (C7).
IMPACTS = "impacts"


class SyncError(RuntimeError):
    """A premise of the transform does not hold, or the inputs are not the pinned ones."""


Ledger = dict[str, dict[str, list[str]]]


def _expect(condition: bool, message: str) -> None:
    if not condition:
        raise SyncError(f"premise failed: {message}")


def _load_yaml(path: Path) -> Any:
    from ruamel.yaml import YAML

    return YAML(typ="safe").load(path.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------------------
# Vocabulary (contracts/packs/file-kinds.yaml is the single data authority)
# --------------------------------------------------------------------------------------


def load_vocabulary() -> dict[str, Any]:
    vocab = _load_yaml(FILE_KINDS)
    node_kinds: list[str] = vocab["node_kinds"]
    _expect(len(node_kinds) == len(set(node_kinds)), "file-kinds.yaml node_kinds has a duplicate")
    for kind in node_kinds:
        _expect(re.fullmatch(r"[a-z_]+", kind) is not None, f"node kind {kind!r} is not [a-z_]+")
    _expect(
        set(vocab["artifact_kinds"]) <= set(node_kinds),
        "an artifact kind is not a node kind",
    )
    fragment_kinds: dict[str, str] = vocab["fragment_kinds"]
    _expect(set(fragment_kinds.values()) <= set(node_kinds), "a fragment kind maps to no node kind")
    _expect(
        set(vocab["artifact_kinds"]) <= set(fragment_kinds.values()),
        "an artifact kind has no fragment plural",
    )
    return vocab


def urn_pattern(node_kinds: list[str]) -> str:
    return f"^({'|'.join(node_kinds)}):{URN_ID_CLASS}+$"


def node_id_pattern() -> str:
    return f"^{URN_ID_CLASS}+$"


# --------------------------------------------------------------------------------------
# Provenance of the export
# --------------------------------------------------------------------------------------


def pinned_origin() -> dict[str, str]:
    """Read the pinned core commit and pydantic version from VENDORED.md."""
    text = VENDORED.read_text(encoding="utf-8") if VENDORED.exists() else ""
    commit = re.search(r"core commit: `([0-9a-f]{40})`", text)
    pydantic = re.search(r"pydantic: `([^`]+)`", text)
    if not commit or not pydantic:
        raise SyncError("VENDORED.md does not name 'core commit: `<40 hex>`' and 'pydantic: `<version>`'")
    return {"commit": commit.group(1), "pydantic": pydantic.group(1)}


def _core_checkout(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit).resolve()
    dist = importlib.metadata.distribution("spec-kitty-cli")
    raw = dist.read_text("direct_url.json")
    if not raw:
        raise SyncError("installed spec-kitty-cli has no direct_url.json; pass --core-checkout")
    url = json.loads(raw)["url"]
    if not url.startswith("file://"):
        raise SyncError(f"installed core comes from {url}, not a checkout; pass --core-checkout")
    return Path(url.removeprefix("file://"))


def _tree_digest(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*.py")):
        digest.update(path.relative_to(root).as_posix().encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def verify_core(core_commit: str | None, core_checkout: str | None) -> None:
    """Fail loudly unless the installed core is the commit and pydantic named in VENDORED.md."""
    pinned = pinned_origin()
    import pydantic

    if pydantic.VERSION != pinned["pydantic"]:
        raise SyncError(f"pydantic {pydantic.VERSION} installed, VENDORED.md pins {pinned['pydantic']}")
    checkout = _core_checkout(core_checkout)
    actual = subprocess.run(
        ["git", "-C", str(checkout), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    expected = core_commit or pinned["commit"]
    if actual != expected:
        raise SyncError(f"core checkout {checkout} is at {actual}, expected {expected}")
    if core_commit and core_commit != pinned["commit"]:
        raise SyncError(f"--core-commit {core_commit} differs from VENDORED.md {pinned['commit']}")
    import charter

    installed = Path(charter.__file__).resolve().parent
    if _tree_digest(installed) != _tree_digest(checkout / "src" / "charter"):
        raise SyncError(f"the installed charter package differs from the checkout at {checkout}")


# --------------------------------------------------------------------------------------
# Export
# --------------------------------------------------------------------------------------


def export_models() -> dict[str, dict[str, Any]]:
    raw: dict[str, dict[str, Any]] = {}
    for name, (module, cls) in MODELS.items():
        model = getattr(importlib.import_module(module), cls)
        raw[name] = model.model_json_schema(mode="validation")
    return raw


# --------------------------------------------------------------------------------------
# Schema walking helpers
# --------------------------------------------------------------------------------------

_SCHEMA_MAPS = ("properties", "$defs", "patternProperties")
_SCHEMA_LISTS = ("anyOf", "allOf", "oneOf", "prefixItems")
_SCHEMA_VALUES = ("items", "additionalProperties", "if", "then", "else", "not", "contains")


def walk_schemas(node: Any, pointer: str = "#"):
    """Yield (pointer, schema) for the schema and every subschema, never a properties map."""
    if not isinstance(node, dict):
        return
    yield pointer, node
    for key in _SCHEMA_MAPS:
        sub = node.get(key)
        if isinstance(sub, dict):
            for name, child in sub.items():
                yield from walk_schemas(child, f"{pointer}/{key}/{name}")
    for key in _SCHEMA_LISTS:
        sub = node.get(key)
        if isinstance(sub, list):
            for index, child in enumerate(sub):
                yield from walk_schemas(child, f"{pointer}/{key}/{index}")
    for key in _SCHEMA_VALUES:
        sub = node.get(key)
        if isinstance(sub, dict):
            yield from walk_schemas(sub, f"{pointer}/{key}")


def _record(ledger: Ledger, change: str, kind: str, note: str) -> None:
    ledger.setdefault(change, {}).setdefault(kind, []).append(note)


def _optional(prop: dict[str, Any]) -> bool:
    return "default" in prop


# --------------------------------------------------------------------------------------
# The changes. Each function is named after its id in VENDORED.md.
# --------------------------------------------------------------------------------------


def c1_identity(kind: str, schema: dict[str, Any]) -> dict[str, Any]:
    """C1: $schema and $id first; every $ref local; every IRI ends in .json."""
    out = {"$schema": DRAFT, "$id": f"{ID_BASE}{kind}.schema.json", **schema}
    for _, sub in walk_schemas(out):
        ref = sub.get("$ref")
        _expect(ref is None or ref.startswith("#/$defs/"), f"{kind}: non-local $ref {ref!r}")
    return out


def c2_close_nested(kind: str, schema: dict[str, Any], ledger: Ledger) -> None:
    """C2: every object schema with properties gets additionalProperties: false.

    Free-form maps (additionalProperties a schema or true, and no properties) stay as exported.
    """
    for pointer, sub in walk_schemas(schema):
        if "properties" not in sub:
            if isinstance(sub.get("additionalProperties"), (dict, bool)) and sub.get("additionalProperties"):
                _record(ledger, "C2-open", kind, pointer)
            continue
        if "additionalProperties" in sub:
            _expect(
                sub["additionalProperties"] is False,
                f"{kind}: {pointer} has properties and a non-false additionalProperties",
            )
            continue
        sub["additionalProperties"] = False
        _record(ledger, "C2", kind, pointer)


def c3_urn_patterns(kind: str, schema: dict[str, Any], vocab: dict[str, Any]) -> None:
    """C3: URN pattern on the shard node urn and on every edge source and target."""
    pattern = urn_pattern(vocab["node_kinds"])
    defs = schema["$defs"]
    edge = defs["DRGEdge" if kind == "graph-shard" else "_OrgDRGEdge"]
    if kind == "graph-shard":
        defs["DRGNode"]["properties"]["urn"]["pattern"] = pattern
    for field in ("source", "target"):
        edge["properties"][field]["pattern"] = pattern


def c4_kind_prefix(schema: dict[str, Any], vocab: dict[str, Any]) -> None:
    """C4: shard node kind enum = node_kinds, plus one prefix branch per kind."""
    node = schema["$defs"]["DRGNode"]
    kinds = vocab["node_kinds"]
    node["properties"]["kind"] = {"title": "Kind", "type": "string", "enum": list(kinds)}
    node["allOf"] = [
        {
            "if": {"properties": {"kind": {"const": k}}, "required": ["kind"]},
            "then": {"properties": {"urn": {"pattern": f"^{k}:"}}},
        }
        for k in kinds
    ]
    _expect("NodeKind" in schema["$defs"], "graph-shard has no NodeKind $def to retire")
    del schema["$defs"]["NodeKind"]


def c5_fragment_node(schema: dict[str, Any], vocab: dict[str, Any]) -> None:
    """C5: fragment node kind enum = fragment_kinds keys; id is non-empty and id-safe."""
    node = schema["$defs"]["_OrgDRGNode"]["properties"]
    node["kind"] = {"title": "Kind", "type": "string", "enum": list(vocab["fragment_kinds"])}
    node["id"] = {"title": "Id", "type": "string", "minLength": 1, "pattern": node_id_pattern()}


def c6_fragment_header(schema: dict[str, Any]) -> None:
    """C6: the header fields core overwrites from configuration are not authored."""
    for name in ("pack_name", "source_kind", "source_ref", "layer_index", "provenance_marker"):
        _expect(name in schema["properties"], f"org-fragment lacks {name}")
        del schema["properties"][name]
    schema["required"] = [r for r in schema["required"] if r in schema["properties"]]
    _expect(schema["required"] == [], "org-fragment still requires a field after C6")
    del schema["required"]


def c7_shard_relation(schema: dict[str, Any]) -> dict[str, Any]:
    """C7: the shard Relation enum is core's plus impacts. Returns the $def for the fragment."""
    relation = schema["$defs"]["Relation"]
    _expect(IMPACTS not in relation["enum"], "core's Relation already has impacts")
    relation["enum"] = [*relation["enum"], IMPACTS]
    relation["description"] += (
        "\n\n``IMPACTS`` is added by this repository (WP02 C7): core has no such member yet."
        " ADR-0009 D1 rules that the contract leads and core aligns at the cutover."
    )
    return copy.deepcopy(relation)


def c7_fragment_relation(schema: dict[str, Any], relation: dict[str, Any]) -> None:
    """C7: fragment edges use the shard Relation enum, not a free-form string."""
    edge = schema["$defs"]["_OrgDRGEdge"]["properties"]
    _expect(edge["relation"] == {"title": "Relation", "type": "string"}, "fragment relation is not free-form")
    stale = (
        "(free-form string label; the merge bridges to\n"
        "``charter.offering.drg.models.Relation`` when possible, handled in\n``charter.drg``)"
    )
    description = schema["$defs"]["_OrgDRGEdge"]["description"]
    _expect(stale in description, "fragment edge description changed")
    schema["$defs"]["_OrgDRGEdge"]["description"] = description.replace(stale, "(a ``Relation`` value)")
    schema["$defs"]["Relation"] = relation
    edge["relation"] = {"$ref": "#/$defs/Relation"}


def c8_shard_generated_by(schema: dict[str, Any]) -> None:
    """C8: generated_by is optional; generated_at stays required."""
    _expect("generated_by" in schema["required"], "export does not require generated_by")
    schema["required"].remove("generated_by")
    _expect("generated_at" in schema["required"], "generated_at is not required")


def c9_pack_descriptor(schema: dict[str, Any], ledger: Ledger) -> None:
    """C9: pack_version required, pack_level optional, requires_core optional, top level open."""
    props = schema["properties"]
    _expect("pack_version" not in schema["required"], "export already requires pack_version")
    _expect("pack_level" in schema["required"], "export does not require pack_level")
    _expect(
        props["requires_core"]["anyOf"][0] == {"type": "string"} and _optional(props["requires_core"]),
        "requires_core is not an optional string in the export",
    )
    _expect(schema["additionalProperties"] is False, "export top level is not closed")
    schema["required"] = [r for r in schema["required"] if r != "pack_level"]
    schema["required"].insert(schema["required"].index("name") + 1, "pack_version")
    _expect(
        props["pack_version"].get("anyOf") == [{"type": "string"}, {"type": "null"}]
        and props["pack_version"].get("default", "absent") is None,
        "pack_version is not anyOf [string, null] with default null in the export",
    )
    props["pack_version"] = {
        "title": props["pack_version"]["title"],
        "description": "Semantic version string; required at every pack level, non-null and non-empty.",
        "type": "string",
        "minLength": 1,
    }
    props["pack_level"]["description"] = (
        "Declared Charter Pack tier: built-in, org or project. Optional; when present it is one of the three."
    )
    schema["additionalProperties"] = True
    _record(ledger, "C9", "pack-descriptor", "top-level additionalProperties: true; pack_version non-null string, minLength 1")


def c10_coercions(kind: str, schema: dict[str, Any], ledger: Ledger) -> None:
    """C10: lenient conversions dropped; the export's own strictness is asserted."""
    if kind == "agent-profile":
        roles = schema["properties"]["roles"]["items"]
        _expect(roles == {"type": "string"}, "agent roles items are not a plain string")
        roles["pattern"] = LOWERCASE_ROLE
        _expect("role" not in schema["properties"], "agent profile declares a scalar role")
        _expect(schema["additionalProperties"] is False, "agent profile top level is open")
        _record(ledger, "C10", kind, "roles items pattern; scalar role refused by the closed object")
        for name in ("routing-priority", "max-concurrent-tasks"):
            _expect(schema["properties"][name]["type"] == "integer", f"{name} is not an integer")
    if kind == "org-charter":
        _expect(schema["properties"]["schema_version"]["type"] == "integer", "org-charter schema_version")


def c11_lost_validators(kind: str, schema: dict[str, Any], ledger: Ledger) -> None:
    """C11: validators the export lost and a schema can express."""
    if kind == "agent-profile":
        rp = schema["properties"]["routing-priority"]
        _expect((rp["minimum"], rp["maximum"]) == (0, 100), "routing-priority is not already 0..100")
        mc = schema["properties"]["max-concurrent-tasks"]
        _expect(mc.get("exclusiveMinimum") == 0, "max-concurrent-tasks has no exclusiveMinimum 0")
        _record(ledger, "C11", kind, "premise held in the export; no edit")
    if kind == "directive":
        _expect("explicit_allowances" in schema["properties"], "directive lacks explicit_allowances")
        schema["allOf"] = [
            {
                "if": {
                    "properties": {"enforcement": {"const": "lenient-adherence"}},
                    "required": ["enforcement"],
                },
                "then": {
                    "properties": {"explicit_allowances": {"minItems": 1}},
                    "required": ["explicit_allowances"],
                },
            }
        ]


def c12_edge_impacts(kind: str, schema: dict[str, Any]) -> None:
    """C12: optional numeric edge impacts; the strength rule lives in the domain."""
    edge = schema["$defs"]["DRGEdge" if kind == "graph-shard" else "_OrgDRGEdge"]
    _expect("impacts" not in edge["properties"], f"{kind}: edge already has impacts")
    edge["properties"]["impacts"] = {
        "title": "Impacts",
        "description": "Edge strength. Core's DRGEdge has none; ADR-0009 D1 rules that the contract leads.",
        "type": "number",
    }


def c13_nullable(kind: str, schema: dict[str, Any], ledger: Ledger) -> None:
    """C13: nullable anyOf[T, null] kept as exported; counted for the record."""
    count = sum(
        1 for _, sub in walk_schemas(schema) if isinstance(sub.get("anyOf"), list) and {"type": "null"} in sub["anyOf"]
    )
    if count:
        _record(ledger, "C13", kind, f"{count} nullable anyOf kept")


def c14_portable_patterns(kind: str, schema: dict[str, Any]) -> None:
    """C14: every pattern is built from anchors, character classes and alternation only."""
    for pointer, sub in walk_schemas(schema):
        pattern = sub.get("pattern")
        if pattern is not None:
            _expect(
                PORTABLE_PATTERN.match(pattern) is not None, f"{kind}: {pointer} pattern {pattern!r} is not portable"
            )
            re.compile(pattern)


def c16_edge_provenance(schema: dict[str, Any]) -> None:
    """C16: edge provenance is a merge-time marker, not authored data."""
    props = schema["$defs"]["DRGEdge"]["properties"]
    _expect("provenance" in props, "DRGEdge has no provenance to remove")
    del props["provenance"]


# --------------------------------------------------------------------------------------
# Transform
# --------------------------------------------------------------------------------------


def transform_all(raw: dict[str, dict[str, Any]], vocab: dict[str, Any]) -> tuple[dict[str, dict[str, Any]], Ledger]:
    ledger: Ledger = {}
    out: dict[str, dict[str, Any]] = {}
    # Shard first: its Relation $def is shared with the fragment (C7).
    for kind in ("graph-shard", *[k for k in raw if k != "graph-shard"]):
        schema = copy.deepcopy(raw[kind])
        c2_close_nested(kind, schema, ledger)  # before any construct this script adds
        if kind == "graph-shard":
            c3_urn_patterns(kind, schema, vocab)
            c4_kind_prefix(schema, vocab)
            relation = c7_shard_relation(schema)
            c8_shard_generated_by(schema)
            c12_edge_impacts(kind, schema)
            c16_edge_provenance(schema)
        if kind == "org-fragment":
            c3_urn_patterns(kind, schema, vocab)
            c5_fragment_node(schema, vocab)
            c6_fragment_header(schema)
            c7_fragment_relation(schema, relation)
            c12_edge_impacts(kind, schema)
        if kind == "pack-descriptor":
            c9_pack_descriptor(schema, ledger)
        if kind != "charter":  # C15: the charter schema takes C1 and C2 only
            c10_coercions(kind, schema, ledger)
            c11_lost_validators(kind, schema, ledger)
            c13_nullable(kind, schema, ledger)
        c14_portable_patterns(kind, schema)
        out[kind] = c1_identity(kind, schema)
    return {k: out[k] for k in raw}, ledger


def ledger_markdown(ledger: Ledger) -> str:
    """The C2 ledger as it must appear in VENDORED.md (closed schemas per file)."""
    lines = []
    for kind in MODELS:
        closed = ledger.get("C2", {}).get(kind, [])
        shown = ", ".join(f"`{p}`" for p in closed) if closed else "none"
        lines.append(f"- `{kind}`: {shown}")
    return "\n".join(lines) + "\n"


def render(schema: dict[str, Any]) -> str:
    return json.dumps(schema, indent=2, ensure_ascii=False) + "\n"


def generate(core_commit: str | None, core_checkout: str | None) -> tuple[dict[str, str], Ledger]:
    verify_core(core_commit, core_checkout)
    vocab = load_vocabulary()
    schemas, ledger = transform_all(export_models(), vocab)
    return {f"{kind}.schema.json": render(s) for kind, s in schemas.items()}, ledger


# --------------------------------------------------------------------------------------
# --check
# --------------------------------------------------------------------------------------


def check(core_commit: str | None, core_checkout: str | None) -> int:
    with tempfile.TemporaryDirectory(prefix="pack-schemas-") as tmp:
        files, ledger = generate(core_commit, core_checkout)
        for name, text in files.items():
            (Path(tmp) / name).write_text(text, encoding="utf-8")
        problems: list[str] = []
        committed = {p.name for p in SCHEMAS_DIR.glob("*.json")} if SCHEMAS_DIR.is_dir() else set()
        for name in sorted(committed | set(files)):
            path = SCHEMAS_DIR / name
            if name not in files:
                problems.append(f"{name}: committed but not generated")
            elif name not in committed:
                problems.append(f"{name}: generated but not committed")
            elif path.read_text(encoding="utf-8") != files[name]:
                problems.append(f"{name}: differs from the regenerated file")
        if ledger_markdown(ledger) not in VENDORED.read_text(encoding="utf-8"):
            problems.append("VENDORED.md does not contain the C2 ledger printed by --ledger")
    for problem in problems:
        print(f"CHECK FAILED: {problem}", file=sys.stderr)
    if not problems:
        print(f"ok: {len(files)} schemas reproduce from core {pinned_origin()['commit'][:8]}")
    return 1 if problems else 0


# --------------------------------------------------------------------------------------
# --probe (jsonschema is imported here only)
# --------------------------------------------------------------------------------------


def _glob_regex(glob: str) -> re.Pattern[str]:
    out, i = "", 0
    while i < len(glob):
        if glob.startswith("**/", i):
            out += "(?:[^/]+/)*"
            i += 3
        elif glob[i] == "*":
            out += "[^/]*"
            i += 1
        else:
            out += re.escape(glob[i])
            i += 1
    return re.compile(out + r"\Z")


def classify(rel: str, vocab: dict[str, Any]) -> tuple[str, str]:
    """Return ('mapped', schema), ('excluded', reason) or ('unclassified', ''); overlap raises."""
    hits = [("mapped", r["schema"]) for r in vocab["files"] if _glob_regex(r["match"]).match(rel)]
    hits += [("excluded", r["reason"]) for r in vocab["exclusions"] if _glob_regex(r["match"]).match(rel)]
    if len(hits) > 1:
        raise SyncError(f"{rel} matches {len(hits)} rules")
    return hits[0] if hits else ("unclassified", "")


def probe(checkout: str) -> int:
    import jsonschema

    vocab = load_vocabulary()
    root = Path(checkout) / "packs"
    validators = {
        p.name.removesuffix(".schema.json"): jsonschema.Draft202012Validator(json.loads(p.read_text(encoding="utf-8")))
        for p in SCHEMAS_DIR.glob("*.schema.json")
    }
    for validator in validators.values():
        jsonschema.Draft202012Validator.check_schema(validator.schema)
    mapped = excluded = 0
    unclassified: list[str] = []
    invalid: list[str] = []
    for path in sorted(root.rglob("*.yaml")):
        rel = path.relative_to(root).as_posix()
        status, value = classify(rel, vocab)
        if status == "excluded":
            excluded += 1
        elif status == "unclassified":
            unclassified.append(rel)
        else:
            mapped += 1
            errors = sorted(validators[value].iter_errors(_load_yaml(path)), key=lambda e: list(e.path))
            for err in errors[:3]:
                invalid.append(f"{rel} [{value}] {'/'.join(map(str, err.path))}: {err.message[:160]}")
    bad_files = len({i.split(" ")[0] for i in invalid})
    print(f"mapped={mapped} excluded={excluded} unclassified={len(unclassified)} invalid_files={bad_files}")
    for line in [*(f"UNCLASSIFIED {u}" for u in unclassified), *invalid]:
        print(line)
    return 1 if unclassified or invalid else 0


# --------------------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="regenerate into a temp dir; exit 1 on any difference")
    parser.add_argument("--ledger", action="store_true", help="print the C2 ledger for VENDORED.md")
    parser.add_argument("--probe", metavar="CHECKOUT", help="validate every mapped YAML file under CHECKOUT/packs")
    parser.add_argument("--core-commit", help="expected core commit; must equal VENDORED.md and the checkout")
    parser.add_argument("--core-checkout", help="core checkout; default: the one the installed core came from")
    args = parser.parse_args(argv)
    try:
        if args.probe:
            return probe(args.probe)
        if args.check:
            return check(args.core_commit, args.core_checkout)
        files, ledger = generate(args.core_commit, args.core_checkout)
        if args.ledger:
            print(ledger_markdown(ledger), end="")
            return 0
        SCHEMAS_DIR.mkdir(parents=True, exist_ok=True)
        for name, text in files.items():
            (SCHEMAS_DIR / name).write_text(text, encoding="utf-8")
        print(f"wrote {len(files)} schemas to {SCHEMAS_DIR.relative_to(REPO_ROOT)}")
        return 0
    except SyncError as err:
        print(f"sync_schemas: {err}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
