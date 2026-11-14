#!/usr/bin/env python3
"""Judge an open-packs tree against slice F's model (ADR-0077), after `research/edge-census.py` has counted it.

    python3 -I census-check.py <open-packs-tree> <edge_census.csv> [<charter contracts/packs dir>]

Reports, for the `open-packs` corpus of the census CSV and for the tree itself:
- refused edges: a (relation, source kind, target kind) row the endpoint table does not allow, or a retired relation;
- unlinked steps: a step or on-demand step with no authored `mission_type:<t> -requires-> action:<t>/<s>` edge in its
  pack, or such an edge to an action that is no step of the type (`mission_step_unlinked`);
- unresolved and unscoped candidates: a substep candidate that resolves to no artifact of its kind in the pack (bare)
  or in the named pack of the closure (qualified), or whose step's action has no `scope` edge to it, qualified as the
  candidate is (`substep_delegation_unresolved`, `substep_delegation_unscoped`);
- retired files: `*.step-contract.yaml` and `mission_step_contract.graph.yaml`;
- with the contract dir: each mission type, graph shard and fragment validated against its pack schema.
Exit 0 only when every count is 0.
"""
import csv
import glob
import json
import os
import re
import sys

import yaml

G = {'directive', 'tactic', 'styleguide', 'toolguide', 'paradigm', 'procedure'}
ACTIVATABLE = G | {'agent_profile', 'glossary_pack', 'skill', 'anti_pattern', 'mission_type', 'step_enhancement'}
AUTHORED_IN_GRAPHS = ACTIVATABLE | {'asset', 'template', 'action'}
ADVISORS = G | {'agent_profile', 'skill'}
GLOSSARY = {'glossary_pack', 'glossary'}  # the table follows ArtifactKind (#106 renames it)


def allowed(rel, s, t):
    """The endpoint table of ADR-0077 decision 5."""
    return {
        'requires': (s in ADVISORS and t in G | {'asset', 'template'}) or (s, t) in {('asset', 'asset'), ('mission_type', 'action')},
        'suggests': s in ADVISORS and t in G | {'asset', 'template', 'agent_profile'},
        'applies': s == 'action' and t in {'directive', 'tactic'},
        'scope': s in {'mission_type', 'action'} and t in G | {'agent_profile'},
        'vocabulary': s in AUTHORED_IN_GRAPHS and t in GLOSSARY,
        'instantiates': s == 'action' and t == 'template',
        'refines': s in G and t in G,
        'specializes_from': s == t == 'agent_profile',
        'enhances': (s, t) == ('step_enhancement', 'action') or (s == t and s in ACTIVATABLE - {'mission_type', 'step_enhancement'}),
        'overrides': s == t and s in ACTIVATABLE - {'step_enhancement'},
        'reconciles_tension': s in G and t in G,
        'rejects': s in G and t == 'anti_pattern',
        'in_tension_with': s in G and t in G,  # held beside the relations as a Tension (ADR-0028)
    }.get(rel, False)  # implemented_by is derived and never authored; impacts, replaces, delegates_to are retired


def load(path):
    with open(path, encoding='utf-8') as f:
        return yaml.safe_load(f)


def main(argv):
    tree, census = argv[1], argv[2]
    contract = argv[3] if len(argv) > 3 else None
    problems = {'refused edges': [], 'unlinked steps': [], 'unresolved candidates': [], 'unscoped candidates': [],
                'retired files': [], 'schema violations': [], 'unparseable graph documents': []}

    # 1. The census CSV, open-packs corpus.
    census_edges = 0
    with open(census, encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['corpus'] != 'open-packs':
                continue
            census_edges += int(row['count'])
            if not allowed(row['relation'], row['source_kind'], row['target_kind']):
                problems['refused edges'].append(f"{row['relation']} {row['source_kind']} -> {row['target_kind']} x{row['count']}")

    # 2. The tree.
    root = os.path.join(tree, 'packs')
    dirs = sorted(d for d in os.listdir(root) if os.path.isfile(os.path.join(root, d, 'pack.yaml')))
    desc = {d: load(os.path.join(root, d, 'pack.yaml')) for d in dirs}
    by_name = {desc[d]['name']: d for d in dirs}
    by_id = {desc[d]['pack_id']: d for d in dirs}

    def closure(d):
        seen, todo = [], [d]
        while todo:
            x = todo.pop(0)
            if x and x not in seen:
                seen.append(x)
                todo += [by_id.get(p['pack_id']) for p in desc[x].get('depends_on') or []]
        return seen

    def graph_docs(d):
        docs = sorted(glob.glob(os.path.join(root, d, '*.graph.yaml')))
        fragment = os.path.join(root, d, 'drg', 'fragment.yaml')
        return docs + ([fragment] if os.path.isfile(fragment) else [])

    edges, nodes = {d: set() for d in dirs}, {d: set() for d in dirs}
    for d in dirs:
        for path in graph_docs(d):
            try:
                doc = load(path) or {}
            except yaml.YAMLError as exc:
                problems['unparseable graph documents'].append(f'{path}: {exc}')
                continue
            for n in doc.get('nodes') or []:
                nodes[d].add(n['urn'] if 'urn' in n else f"{n['kind'].removesuffix('s')}:{n['id']}")
            for e in doc.get('edges') or []:
                edges[d].add((e['source'], e['target'], e['relation']))
    suffix = {'directive': 'directive', 'tactic': 'tactic', 'styleguide': 'styleguide', 'toolguide': 'toolguide',
              'paradigm': 'paradigm', 'procedure': 'procedure', 'agent_profile': 'agent'}
    held = {d: {tuple(u.split(':', 1)) for u in nodes[d]} for d in dirs}
    for d in dirs:
        for kind, sfx in suffix.items():
            for path in glob.glob(os.path.join(root, d, '**', f'*.{sfx}.yaml'), recursive=True):
                doc = load(path)
                if isinstance(doc, dict) and 'id' in doc:
                    held[d].add((kind, str(doc['id'])))

    tree_edges = sum(len(v) for v in edges.values())
    for d in dirs:
        for src, tgt, rel in sorted(edges[d]):
            sk, tk = (x.split('::', 1)[-1].split(':', 1)[0] for x in (src, tgt))
            if not allowed(rel, sk, tk):
                problems['refused edges'].append(f'{d} (tree): {rel} {src} -> {tgt}')
    substeps = candidates = 0
    for path in sorted(glob.glob(os.path.join(root, '*', 'missions', '*', '*.mission-type.yaml'))):
        d = os.path.relpath(path, root).split('/')[0]
        mt = load(path)
        t = mt['id']
        all_steps = list(mt['steps']) + list(mt.get('on_demand_steps') or [])
        ids = {s['id'] for s in all_steps}
        for s in all_steps:
            if (f'mission_type:{t}', f"action:{t}/{s['id']}", 'requires') not in edges[d]:
                problems['unlinked steps'].append(f"{d}: {t}/{s['id']}")
            for sub in s.get('substeps') or []:
                substeps += 1
                if not re.fullmatch(r'[a-z][a-z0-9]*(-[a-z0-9]+)*', sub['id']):
                    problems['schema violations'].append(f"{d}: {t}/{s['id']}: substep id {sub['id']}")
                dl = sub.get('delegates_to')
                for c in (dl or {}).get('candidates', []):
                    candidates += 1
                    q, cid = c.split('::', 1) if '::' in c else (None, c)
                    home = d if q is None else by_name.get(q)
                    if home is None or home not in closure(d) or (dl['kind'], cid) not in held[home]:
                        problems['unresolved candidates'].append(f"{d}: {t}/{s['id']}/{sub['id']}: {dl['kind']} {c}")
                    target = f"{q + '::' if q else ''}{dl['kind']}:{cid}"
                    if (f"action:{t}/{s['id']}", target, 'scope') not in edges[d]:
                        problems['unscoped candidates'].append(f"{d}: action:{t}/{s['id']} -> {target}")
        for src, tgt, rel in edges[d]:
            if rel == 'requires' and src == f'mission_type:{t}' and tgt.startswith('action:') and tgt.split('/', 1)[-1] not in ids:
                problems['unlinked steps'].append(f'{d}: stray edge {src} -> {tgt}')

    for pattern in ('**/*.step-contract.yaml', '**/mission_step_contract.graph.yaml'):
        problems['retired files'] += glob.glob(os.path.join(root, pattern), recursive=True)

    if contract:
        from jsonschema import Draft202012Validator
        schemas = {n: Draft202012Validator(json.load(open(os.path.join(contract, 'schemas', f'{n}.schema.json'))))
                   for n in ('mission-type', 'graph-shard', 'org-fragment')}
        targets = [('mission-type', p) for p in glob.glob(os.path.join(root, '*', 'missions', '*', '*.mission-type.yaml'))]
        targets += [('graph-shard', p) for p in glob.glob(os.path.join(root, '*', '*.graph.yaml'))]
        targets += [('org-fragment', p) for p in glob.glob(os.path.join(root, '*', 'drg', 'fragment.yaml'))]
        for name, path in sorted(targets, key=lambda x: x[1]):
            for err in schemas[name].iter_errors(load(path)):
                problems['schema violations'].append(f"{os.path.relpath(path, tree)} ({name}): /{'/'.join(map(str, err.path))}: {err.message[:160]}")

    print(f'census open-packs edges: {census_edges}; tree edges: {tree_edges}; substeps: {substeps}; candidates: {candidates}')
    for key, items in problems.items():
        print(f'{key}: {len(items)}')
        for item in items[:40]:
            print(f'  {item}')
    return 0 if not any(problems.values()) else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv))
