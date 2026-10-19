"""WP06 / T029: compare core-walk.json with service-walk.json and classify every difference.

Usage (core's venv, because the reference model re-runs core's ``walk_edges`` over the same graph):
    /root/.venv-sk/bin/python -I compare.py <open-packs path> core-walk.json service-walk.json

Prints a markdown report (summary table, classified differences, via/hops invariant check).
Classification of charter - core: the extras must equal exactly the transitive ``requires`` of the
suggested artifacts that core lists no other way (DIV-010, research.md D2 steps 3 and 4).
"""
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, "/home/user/spec-kitty/spec-kitty/src")
from charter.offering.drg.loader import load_graph_or_dir  # noqa: E402
from charter.offering.drg.models import Relation  # noqa: E402
from charter.offering.drg.query import walk_edges  # noqa: E402

packs, core_p, svc_p = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
core, svc = json.load(open(core_p)), json.load(open(svc_p))
g = load_graph_or_dir(packs / "built-in")
nodes = {n.urn for n in g.nodes}
edges = defaultdict(set)  # (relation, source) -> targets
for e in g.edges:
    edges[(e.relation.value, e.source)].add(e.target)

rows, findings, inv_bad, undeclared_rows = [], [], [], []
inv = Counter()
for action in sorted(core["actions"]):
    for d in ("0", "1", "2"):
        c = core["actions"][action][d]
        s = svc["actions"][action][d]
        if "http_status" in s:
            findings.append(f"UNREGISTERED {action} depth {d}: HTTP {s['http_status']}")
            continue
        entries = s["guidance"]
        urn_rel = {e["urn"]: e for e in entries}
        S_charter = {e["urn"] for e in entries if e["relation"] != "vocabulary"}
        V_charter = {e["urn"] for e in entries if e["relation"] == "vocabulary"}
        S_core = set(c["artifacts"])
        G_core = set(c["glossary_scopes"])
        und = set(core["undeclared"][action][d])
        # reference model: DIV-010 expected extras, computed with core's own walk_edges
        scoped = walk_edges(g, {action}, {Relation.SCOPE}, 1) - {action}
        suggested = walk_edges(g, scoped, {Relation.SUGGESTS}, int(d))
        step4 = walk_edges(g, suggested, {Relation.REQUIRES})
        div010 = step4 - S_core
        core_minus = S_core - S_charter
        char_minus = S_charter - S_core
        ids = []
        if char_minus:
            if char_minus == div010:
                ids.append("DIV-010")
            else:
                ids.append("UNREGISTERED")
                findings.append(f"UNREGISTERED charter-core {action} d{d}: unexplained={sorted(char_minus - div010)} missing_expected={sorted(div010 - char_minus)}")
        elif div010:
            ids.append("UNREGISTERED")
            findings.append(f"UNREGISTERED {action} d{d}: DIV-010 extras absent from charter: {sorted(div010)}")
        if core_minus:
            if core_minus <= und:
                ids.append("DIV-002")
                undeclared_rows.append((action, d, sorted(core_minus)))
            else:
                ids.append("UNREGISTERED")
                findings.append(f"UNREGISTERED core-charter {action} d{d}: {sorted(core_minus - und)}")
        elif und:
            undeclared_rows.append((action, d, sorted(und)))
        if V_charter != G_core:
            ids.append("UNREGISTERED")
            findings.append(f"UNREGISTERED vocabulary {action} d{d}: core={sorted(G_core)} charter={sorted(V_charter)}")
        rows.append((action, d, len(S_core), len(S_charter), len(core_minus), len(char_minus), len(G_core), len(V_charter), ",".join(ids) or "-"))
        # via/hops invariant
        if s["depth"] != int(d):
            inv_bad.append(f"{action} d{d}: depth echo {s['depth']}")
        if [e["urn"] for e in entries] != sorted(e["urn"] for e in entries):
            inv_bad.append(f"{action} d{d}: not sorted by URN")
        if len(urn_rel) != len(entries):
            inv_bad.append(f"{action} d{d}: duplicate URNs")
        if action in urn_rel:
            inv_bad.append(f"{action} d{d}: lists the action itself")
        for e in entries:
            inv["entries"] += 1
            via, hops, rel = e["via"], e["hops"], e["relation"]
            if via == action:
                ok = hops == 1 and rel == "scope"
            elif via in urn_rel:
                ok = hops == urn_rel[via]["hops"] + 1
            else:
                ok = False
            real = e["urn"] in edges.get((rel, via), set())
            if not ok:
                inv["bad_via_hops"] += 1
                inv_bad.append(f"{action} d{d}: {e['urn']} via {via} hops {hops}")
            if not real:
                inv["no_real_edge"] += 1
                inv_bad.append(f"{action} d{d}: {e['urn']} is not a {rel} edge from {via}")

print("| action | depth | core S | charter S | core-charter | charter-core | core glossary | charter vocab | register ids |")
print("|---|---|---|---|---|---|---|---|---|")
for r in rows:
    print("| " + " | ".join(str(x) for x in r) + " |")
print()
by_depth = defaultdict(lambda: [0, 0, 0, 0])
for r in rows:
    t = by_depth[r[1]]
    t[0] += r[2]; t[1] += r[3]; t[2] += r[4]; t[3] += r[5]
print("Totals per depth (core, charter, core-charter, charter-core):", dict(by_depth))
print("Actions:", len(core["actions"]), "cells:", len(rows))
print("Cells by register ids:", dict(Counter(r[8] for r in rows)))
print("Undeclared nodes in core results:", undeclared_rows)
print("Invariant:", dict(inv), "violations:", len(inv_bad))
for b in inv_bad[:20]:
    print("  INV:", b)
print("Findings (unregistered):", len(findings))
for f in findings[:40]:
    print("  ", f)
