"""WP06 / T027: core's bare walk over an open-packs ``built-in/`` directory.

Usage (from a directory outside open-packs):
    /root/.venv-sk/bin/python -I core_walk.py /home/user/spec-kitty-open-packs > core-walk.json

Runs ``charter.offering.drg.query.resolve_context`` (core, read-only oracle) for every
``action:`` node at depths 0, 1 and 2. Output:
    {"core_commit", "open_packs_commit", "actions": {urn: {depth: {"artifacts": [...],
     "glossary_scopes": [...]}}}, "undeclared": {urn: {depth: [urn not a graph node]}}}
"""
import json
import subprocess
import sys
from pathlib import Path

CORE = Path("/home/user/spec-kitty/spec-kitty")
sys.path.insert(0, str(CORE / "src"))

from charter.offering.drg.loader import load_graph_or_dir  # noqa: E402
from charter.offering.drg.query import resolve_context  # noqa: E402


def commit(repo: Path) -> str:
    return subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()


def main() -> None:
    packs = Path(sys.argv[1])
    graph = load_graph_or_dir(packs / "built-in")
    nodes = {n.urn for n in graph.nodes}
    out = {
        "core_commit": commit(CORE),
        "open_packs_commit": commit(packs),
        "actions": {},
        "undeclared": {},
    }
    for action in sorted(u for u in nodes if u.startswith("action:")):
        out["actions"][action] = {}
        out["undeclared"][action] = {}
        for depth in (0, 1, 2):
            ctx = resolve_context(graph, action, depth=depth)
            out["actions"][action][str(depth)] = {
                "artifacts": sorted(ctx.artifact_urns),
                "glossary_scopes": sorted(ctx.glossary_scopes),
            }
            out["undeclared"][action][str(depth)] = sorted(u for u in ctx.artifact_urns if u not in nodes)
    json.dump(out, sys.stdout, indent=1, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
