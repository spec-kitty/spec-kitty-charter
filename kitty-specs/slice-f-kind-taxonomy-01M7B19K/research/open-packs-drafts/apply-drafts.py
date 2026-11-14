#!/usr/bin/env python3
"""Content sketch for open-packs drafts 1 to 3 (slice F, ADR-0077): apply them to a COPY of open-packs.

    python3 -I apply-drafts.py <open-packs-copy> [--drafts 1,2,3] [--tables <dir>]

Never run it on a real checkout: it rewrites the copy in place.

- Draft 1 (TBD-fold-step-contracts): folds each `*.step-contract.yaml` into the `substeps` of the step it names
  (`mission` + `action`), renames substep ids to the kernel grammar (`_` -> `-`), writes a candidate held by another
  pack of the closure as `<pack>::<id>`, then deletes the 28 files, their directories and the 2
  `mission_step_contract.graph.yaml` fragments. The `review` contract's gate is NOT folded: it is draft 4's guard.
- Draft 2 (TBD-step-requires-edges): adds the missing `mission_type:<t> -requires-> action:<t>/<s>` edges, one per
  step and on-demand step, and the missing `action:<t>/<s>` nodes.
- Draft 3 (TBD-delegation-scope-edges): adds a `scope` edge from the step's action to each candidate that has none,
  qualified as the candidate is.

Edits are textual (the files keep their formatting and comments): a substeps block is inserted at the end of its step,
a node is inserted in sorted place (or appended when the list is unsorted), and an edge is inserted after the last edge
with the same source (or appended). `--tables <dir>` writes the generated Markdown tables the drafts quote.
"""
import collections
import glob
import os
import shutil
import sys

import yaml

GRAMMAR_OK = __import__('re').compile(r'^[a-z][a-z0-9]*(-[a-z0-9]+)*$')
SUFFIX = {'directive': 'directive', 'tactic': 'tactic', 'styleguide': 'styleguide', 'toolguide': 'toolguide',
          'paradigm': 'paradigm', 'procedure': 'procedure', 'agent_profile': 'agent'}


def load(path):
    with open(path, encoding='utf-8') as f:
        return yaml.safe_load(f)


class Packs:
    def __init__(self, root):
        self.root = root
        self.packs = os.path.join(root, 'packs')
        self.dirs = sorted(d for d in os.listdir(self.packs) if os.path.isfile(os.path.join(self.packs, d, 'pack.yaml')))
        self.desc = {d: load(os.path.join(self.packs, d, 'pack.yaml')) for d in self.dirs}
        self.name = {d: self.desc[d]['name'] for d in self.dirs}
        by_id = {self.desc[d]['pack_id']: d for d in self.dirs}
        self.deps = {d: [by_id.get(x['pack_id']) for x in self.desc[d].get('depends_on') or []] for d in self.dirs}
        self.art = {d: self._artifacts(d) for d in self.dirs}

    def graph_docs(self, d):
        docs = sorted(glob.glob(os.path.join(self.packs, d, '*.graph.yaml')))
        fragment = os.path.join(self.packs, d, 'drg', 'fragment.yaml')
        return docs + ([fragment] if os.path.isfile(fragment) else [])

    def nodes(self, d):
        for path in self.graph_docs(d):
            for n in (load(path) or {}).get('nodes') or []:
                yield path, n['urn'] if 'urn' in n else f"{n['kind'].removesuffix('s')}:{n['id']}"

    def edges(self, d):
        for path in self.graph_docs(d):
            for e in (load(path) or {}).get('edges') or []:
                yield path, e

    def _artifacts(self, d):
        found = set()
        for kind, suffix in SUFFIX.items():
            for path in glob.glob(os.path.join(self.packs, d, '**', f'*.{suffix}.yaml'), recursive=True):
                doc = load(path)
                if isinstance(doc, dict) and 'id' in doc:
                    found.add((kind, str(doc['id'])))
        for _, urn in self.nodes(d):
            kind, ident = urn.split(':', 1)
            found.add((kind, ident))
        return found

    def closure(self, d):
        seen, todo = [], [d]
        while todo:
            x = todo.pop(0)
            if x and x not in seen:
                seen.append(x)
                todo += self.deps.get(x, [])
        return seen

    def home(self, d, kind, ident):
        return next((x for x in self.closure(d) if (kind, ident) in self.art[x]), None)

    def mission_types(self):
        out = {}
        for path in sorted(glob.glob(os.path.join(self.packs, '*', 'missions', '*', '*.mission-type.yaml'))):
            d = os.path.relpath(path, self.packs).split('/')[0]
            out[load(path)['id']] = (d, path)
        return out


# ------------------------------------------------------------------ textual list edits


def _section(lines, key):
    """(start, end): the item lines of top-level list `key` (exclusive end), trailing blanks excluded."""
    start = next(i for i, line in enumerate(lines) if line.rstrip('\n') == f'{key}:') + 1
    end = start
    while end < len(lines) and not (lines[end][:1].isalpha() and ':' in lines[end]):
        end += 1
    while end > start and lines[end - 1].strip() == '':
        end -= 1
    return start, end


def _items(lines, start, end):
    """[(first, last+1, parsed)] of the list items in lines[start:end]."""
    heads = [i for i in range(start, end) if lines[i].lstrip().startswith('- ') and
             len(lines[i]) - len(lines[i].lstrip()) == _indent(lines, start, end)]
    out = []
    for k, h in enumerate(heads):
        stop = heads[k + 1] if k + 1 < len(heads) else end
        text = ''.join(lines[h:stop])
        out.append((h, stop, (yaml.safe_load(text) or [None])[0]))
    return out


def _indent(lines, start, end):
    for i in range(start, end):
        if lines[i].lstrip().startswith('- '):
            return len(lines[i]) - len(lines[i].lstrip())
    return 0


def _render(item, indent):
    text = yaml.safe_dump([item], sort_keys=False, allow_unicode=True, width=1000)
    return [(' ' * indent) + line + '\n' for line in text.splitlines()]


def _node_key(n):
    return n['urn'] if 'urn' in n else f"{n['kind']}:{n['id']}"


def add_nodes(path, new):
    with open(path, encoding='utf-8') as f:
        lines = f.readlines()
    if not new:
        return
    start, end = _section(lines, 'nodes')
    indent = _indent(lines, start, end)
    items = _items(lines, start, end)
    keys = [_node_key(i[2]) for i in items]
    ordered = keys == sorted(keys)
    for n in new:
        block = _render(n, indent)
        if ordered:
            pos = next((h for h, _, it in items if _node_key(it) > _node_key(n)), end)
        else:
            pos = end
        lines[pos:pos] = block
        start, end = _section(lines, 'nodes')
        items = _items(lines, start, end)
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(lines)


def add_edges(path, new):
    if not new:
        return
    with open(path, encoding='utf-8') as f:
        lines = f.readlines()
    for e in new:
        start, end = _section(lines, 'edges')
        indent = _indent(lines, start, end)
        items = _items(lines, start, end)
        same = [stop for _, stop, it in items if it['source'] == e['source']]
        pos = same[-1] if same else end
        lines[pos:pos] = _render(e, indent)
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(lines)


def add_substeps(path, step_id, substeps):
    with open(path, encoding='utf-8') as f:
        lines = f.readlines()
    head = next(i for i, line in enumerate(lines) if line.rstrip('\n') == f'- id: {step_id}')
    stop = head + 1
    while stop < len(lines) and not (lines[stop].startswith('- ') or (lines[stop][:1].isalpha() and ':' in lines[stop])):
        stop += 1
    while lines[stop - 1].strip() == '':
        stop -= 1
    text = yaml.safe_dump({'substeps': substeps}, sort_keys=False, allow_unicode=True, width=1000)
    lines[stop:stop] = ['  ' + line + '\n' for line in text.splitlines()]
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(lines)


# ------------------------------------------------------------------ the drafts


def clean(text):
    return ' '.join(text.split()) if isinstance(text, str) else text


def substep(d, packs, raw):
    out = {'id': raw['id'].replace('_', '-'), 'description': clean(raw['description'])}
    if 'command' in raw:
        out['command'] = raw['command']
    if raw.get('inputs'):
        out['inputs'] = [{**{k: i[k] for k in ('flag', 'source')}, **({'optional': True} if i.get('optional') else {})}
                         for i in raw['inputs']]
    if 'delegates_to' in raw:
        kind = raw['delegates_to']['kind']
        cands = []
        for c in raw['delegates_to']['candidates']:
            home = packs.home(d, kind, str(c))
            if home is None:
                raise SystemExit(f'{d}: {kind}:{c} resolves in no pack of the closure')
            cands.append(str(c) if home == d else f'{packs.name[home]}::{c}')
        out['delegates_to'] = {'kind': kind, 'candidates': cands}
    if 'guidance' in raw:
        out['guidance'] = clean(raw['guidance'])
    return out


def main(argv):
    root = argv[1]
    tables = argv[argv.index('--tables') + 1] if '--tables' in argv else None
    drafts = set(argv[argv.index('--drafts') + 1].split(',')) if '--drafts' in argv else {'1', '2', '3'}
    packs = Packs(root)
    mts = packs.mission_types()
    contracts = sorted(glob.glob(os.path.join(packs.packs, '**', '*.step-contract.yaml'), recursive=True))
    renames, folds, gates = [], [], []
    scope_new = collections.OrderedDict()  # (pack dir, source, target) -> [(contract, substep)]

    # Draft 1: fold.
    for path in contracts:
        rel = os.path.relpath(path, root)
        d = rel.split('/')[1]
        sc = load(path)
        t, s = sc['mission'], sc['action']
        mt_dir, mt_path = mts[t]
        assert mt_dir == d, f'{rel}: mission type {t} lives in {mt_dir}'
        mt = load(mt_path)
        steps = {x['id'] for x in mt['steps']} | {x['id'] for x in mt.get('on_demand_steps') or []}
        assert s in steps, f'{rel}: {t} has no step {s}'
        subs = [substep(d, packs, raw) for raw in sc['steps']]
        ids = [x['id'] for x in subs]
        assert len(ids) == len(set(ids)), f'{rel}: duplicate substep ids after the rename'
        assert all(GRAMMAR_OK.match(i) and len(i) <= 64 for i in ids), f'{rel}: {ids}'
        for raw in sc['steps']:
            if raw['id'] != raw['id'].replace('_', '-'):
                renames.append((rel, t, s, raw['id'], raw['id'].replace('_', '-')))
        for x in subs:
            for c in (x.get('delegates_to') or {}).get('candidates', []):
                q, cid = c.split('::') if '::' in c else (None, c)
                target = f"{q + '::' if q else ''}{x['delegates_to']['kind']}:{cid}"
                scope_new.setdefault((d, f'action:{t}/{s}', target), []).append((rel, x['id']))
        if sc.get('gates'):
            gates.append((rel, sc['gates']))
        if '1' in drafts:
            add_substeps(mt_path, s, subs)
        folds.append((rel, os.path.relpath(mt_path, root), t, s, len(subs),
                      sum(len((x.get('delegates_to') or {}).get('candidates', [])) for x in subs),
                      sum(1 for x in subs for c in (x.get('delegates_to') or {}).get('candidates', []) if '::' in c)))
    removed = list(contracts) + sorted(glob.glob(os.path.join(packs.packs, '*', 'mission_step_contract.graph.yaml')))
    if '1' in drafts:
        for path in removed:
            os.remove(path)
        for d in {os.path.dirname(p) for p in contracts}:
            if not os.listdir(d):
                os.rmdir(d)

    # Draft 2: requires edges and action nodes.
    existing_edges = collections.defaultdict(set)
    for d in packs.dirs:
        for _, e in packs.edges(d):
            existing_edges[d].add((e['source'], e['target'], e['relation']))
    existing_nodes = {d: {urn for _, urn in packs.nodes(d)} for d in packs.dirs}
    req_rows, node_rows = [], []
    for t, (d, mt_path) in mts.items():
        mt = load(mt_path)
        for kind, step in [('step', x) for x in mt['steps']] + [('on-demand', x) for x in mt.get('on_demand_steps') or []]:
            urn = f"action:{t}/{step['id']}"
            if (f'mission_type:{t}', urn, 'requires') not in existing_edges[d]:
                req_rows.append((d, t, step['id'], kind))
            if urn not in existing_nodes[d]:
                node_rows.append((d, t, step['id'], step['title']))
    for d in sorted({r[0] for r in req_rows} | {r[0] for r in node_rows}) if '2' in drafts else []:
        fragment = os.path.join(packs.packs, d, 'drg', 'fragment.yaml')
        if os.path.isfile(fragment):
            mt_doc = act_doc = fragment
            nodes = [{'id': f'{t}/{s}', 'kind': 'actions', 'title': title} for dd, t, s, title in node_rows if dd == d]
        else:
            mt_doc = os.path.join(packs.packs, d, 'mission_type.graph.yaml')
            act_doc = os.path.join(packs.packs, d, 'action.graph.yaml')
            nodes = [{'urn': f'action:{t}/{s}', 'kind': 'action', 'label': s} for dd, t, s, _ in node_rows if dd == d]
        add_nodes(act_doc, nodes)
        add_edges(mt_doc, [{'source': f'mission_type:{t}', 'target': f'action:{t}/{s}', 'relation': 'requires'}
                           for dd, t, s, _ in req_rows if dd == d])

    # Draft 3: scope edges.
    scope_rows = []
    for (d, source, target), uses in scope_new.items():
        if (source, target, 'scope') not in existing_edges[d]:
            scope_rows.append((d, source, target, uses))
    for d in sorted({r[0] for r in scope_rows}) if '3' in drafts else []:
        fragment = os.path.join(packs.packs, d, 'drg', 'fragment.yaml')
        doc = fragment if os.path.isfile(fragment) else os.path.join(packs.packs, d, 'action.graph.yaml')
        add_edges(doc, [{'source': s, 'target': t, 'relation': 'scope'} for dd, s, t, _ in scope_rows if dd == d])

    print(f'draft 1: {len(folds)} step contracts folded into {len({f[1] for f in folds})} mission types, '
          f'{sum(f[4] for f in folds)} substeps, {sum(f[5] for f in folds)} candidates '
          f'({sum(f[6] for f in folds)} qualified), {len(renames)} ids renamed, {len(removed)} files removed, '
          f'{len(gates)} gate left for draft 4')
    print(f'draft 2: {len(req_rows)} requires edges, {len(node_rows)} action nodes '
          f'{dict(collections.Counter(r[1] for r in req_rows))} / {dict(collections.Counter(r[1] for r in node_rows))}')
    print(f'draft 3: {len(scope_rows)} scope edges for {sum(len(r[3]) for r in scope_rows)} candidate uses '
          f'{dict(collections.Counter(r[0] for r in scope_rows))}')
    if tables:
        os.makedirs(tables, exist_ok=True)
        with open(os.path.join(tables, 'renames.md'), 'w', encoding='utf-8') as f:
            f.write('| Step contract | Step | Old substep id | New substep id |\n|---|---|---|---|\n')
            for rel, t, s, old, new in renames:
                f.write(f'| `{rel}` | `{t}/{s}` | `{old}` | `{new}` |\n')
        with open(os.path.join(tables, 'folds.md'), 'w', encoding='utf-8') as f:
            f.write('| Step contract (removed) | Folds into | Step | Substeps | Candidates | Qualified |\n'
                    '|---|---|---|---|---|---|\n')
            for rel, mt_rel, t, s, n, c, q in folds:
                f.write(f'| `{rel}` | `{mt_rel}` | `{s}` | {n} | {c} | {q} |\n')
        with open(os.path.join(tables, 'requires.md'), 'w', encoding='utf-8') as f:
            f.write('| Pack | Edge `mission_type:<t> -requires-> action:<t>/<s>` | Step kind | Action node added |\n'
                    '|---|---|---|---|\n')
            added = {(r[1], r[2]) for r in node_rows}
            for d, t, s, kind in req_rows:
                f.write(f"| {d} | `mission_type:{t}` → `action:{t}/{s}` | {kind} | {'yes' if (t, s) in added else 'no (exists)'} |\n")
            for d, t, s, _ in node_rows:
                if not any(r[1] == t and r[2] == s for r in req_rows):
                    f.write(f'| {d} | (node only) `action:{t}/{s}` | | yes |\n')
        with open(os.path.join(tables, 'scope.md'), 'w', encoding='utf-8') as f:
            f.write('| # | Pack | Source | Target | Substeps that delegate to it |\n|---|---|---|---|---|\n')
            for k, (d, s, t, uses) in enumerate(scope_rows, 1):
                f.write(f"| {k} | {d} | `{s}` | `{t}` | {', '.join(f'`{u[1]}`' for u in uses)} |\n")
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
