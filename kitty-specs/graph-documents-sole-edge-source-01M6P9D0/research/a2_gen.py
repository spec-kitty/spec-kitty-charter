import glob, os, re, sys, json
import yaml

sys.path.insert(0, os.path.dirname(__file__))
from spec import PROCS

ROOT = sys.argv[1]
os.chdir(ROOT)

# ---- load directive steps ----
dirs = {}
for f in sorted(glob.glob('packs/*/directives/*.directive.yaml')):
    d = yaml.safe_load(open(f))
    if 'procedures' in d:
        dirs[d['id']] = dict(file=f, pack=f.split('/')[1], steps=[s.strip() for s in d['procedures']])
total = sum(len(v['steps']) for v in dirs.values())
assert len(dirs) == 41 and total == 192, (len(dirs), total)

# ---- coverage ----
covered = {}
for p in PROCS:
    for st in p['steps']:
        ref = st[1]
        did, n = ref.split('#')
        n = int(n)
        assert did in dirs and 1 <= n <= len(dirs[did]['steps']), ref
        assert dirs[did]['pack'] == p['pack'], (ref, p['id'])
        covered.setdefault(ref, []).append(p['id'])
        # every directive owning a step must be a user of the procedure
        assert did in [u[0] for u in p['users']], (did, p['id'])
missing = [f"{d}#{i+1}" for d, v in dirs.items() for i in range(len(v['steps'])) if f"{d}#{i+1}" not in covered]
dup = {k: v for k, v in covered.items() if len(v) > 1}
assert not missing, missing
assert not dup, dup
print('coverage ok:', len(covered), '/', total)

# ---- id collisions ----
existing = set()
for f in glob.glob('packs/*/**/*.yaml', recursive=True):
    m = re.match(r'.*/([^/]+?)\.[a-z_]+\.yaml$', f)
    if m:
        existing.add(m.group(1))
ids = [p['id'] for p in PROCS]
assert len(set(ids)) == len(ids)
coll = [i for i in ids if i in existing]
assert not coll, coll

# ---- emit procedures ----
def scalar(s):
    out = yaml.safe_dump(s, width=10**6, default_style=None).rstrip('\n')
    if out.endswith('\n...'):
        out = out[:-4]
    return out

def block(s, indent):
    s = s.strip()
    pad = ' ' * indent
    return '|\n' + '\n'.join(pad + line if line else '' for line in s.split('\n')) + '\n'

for p in PROCS:
    lines = ["schema_version: '1.0'\n", f"id: {p['id']}\n", f"title: {scalar(p['title'])}\n",
             'description: ' + block(p['description'], 2),
             'entry_condition: ' + block(p['entry'], 2),
             'exit_condition: ' + block(p['exit'], 2),
             'steps:\n']
    for st in p['steps']:
        title, ref = st[0], st[1]
        did, n = ref.split('#')
        text = st[2] if len(st) > 2 else dirs[did]['steps'][int(n) - 1]
        lines.append(f"- title: {scalar(title)}\n")
        lines.append('  description: ' + block(text, 4))
    path = f"packs/{p['pack']}/procedures/{p['id']}.procedure.yaml"
    assert not os.path.exists(path), path
    open(path, 'w').write(''.join(lines))
    back = yaml.safe_load(open(path))
    assert back['id'] == p['id'] and back['title'] == p['title'] and len(back['steps']) == len(p['steps'])
    for st, b in zip(p['steps'], back['steps']):
        did, n = st[1].split('#')
        text = st[2] if len(st) > 2 else dirs[did]['steps'][int(n) - 1]
        assert b['title'] == st[0] and b['description'].strip() == text.strip(), (p['id'], st[0])

# ---- strip procedures: blocks ----
for did, v in dirs.items():
    src = open(v['file'], newline='').read()
    m = re.search(r'(?ms)^procedures:\n.*?(?=^[a-z_]+:)', src)
    assert m, v['file']
    new = src[:m.start()] + src[m.end():]
    a, b = yaml.safe_load(src), yaml.safe_load(new)
    del a['procedures']
    assert a == b, v['file']
    open(v['file'], 'w', newline='').write(new)

# ---- built-in edges + nodes ----
def reason_for(did, p):
    others = [u[0] for u in p['users'] if u[0] != did]
    owned = [st[1].split('#')[1] for st in p['steps'] if st[1].split('#')[0] == did]
    r = (f"{did} carried these steps inline as its `procedures` list (step{'s' if len(owned) > 1 else ''} "
         f"{', '.join(owned)}); they were extracted into this procedure (charter #240), so the directive requires it for its how-to.")
    if others:
        r += f" The procedure is a shared slice: {', '.join(others)} also requires it."
    return r

gpath = 'packs/built-in/directive.graph.yaml'
g = yaml.safe_load(open(gpath))
existing_pairs = set()
for gf in glob.glob('packs/built-in/*.graph.yaml'):
    for e in (yaml.safe_load(open(gf)).get('edges') or []):
        existing_pairs.add(frozenset((e['source'], e['target'])))
new_edges = []
for p in PROCS:
    if p['pack'] != 'built-in':
        continue
    for did, when in p['users']:
        s, t = f"directive:{did}", f"procedure:{p['id']}"
        assert frozenset((s, t)) not in existing_pairs, (s, t)
        new_edges.append(dict(source=s, target=t, relation='requires', when=when, reason=reason_for(did, p)))

text = open(gpath).read()
head, edges_txt = text.split('\nedges:\n')
# split existing edge blocks
blocks = re.split(r'(?m)^(?=- source: )', edges_txt)
assert blocks[0] == ''
blocks = blocks[1:]
def key_of(b):
    d = yaml.safe_load(b)[0]
    return (d['source'], d['target'])
allb = [(key_of(b), b) for b in blocks]
for e in new_edges:
    b = yaml.safe_dump([e], width=10**6, sort_keys=False)
    allb.append(((e['source'], e['target']), b))
allb.sort(key=lambda x: x[0])
open(gpath, 'w').write(head + '\nedges:\n' + ''.join(b for _, b in allb))
g2 = yaml.safe_load(open(gpath))
assert len(g2['edges']) == len(g['edges']) + len(new_edges)

ppath = 'packs/built-in/procedure.graph.yaml'
text = open(ppath).read()
head, rest = text.split('\nnodes:\n', 1)
nodes_txt, edges_part = rest.split('\nedges:', 1)
nblocks = re.split(r'(?m)^(?=- urn: )', nodes_txt + '\n')
nblocks = [b for b in nblocks if b]
for p in PROCS:
    if p['pack'] == 'built-in':
        nblocks.append(yaml.safe_dump([dict(urn=f"procedure:{p['id']}", kind='procedure', label=p['title'])], width=10**6, sort_keys=False))
nblocks.sort(key=lambda b: yaml.safe_load(b)[0]['urn'])
open(ppath, 'w').write(head + '\nnodes:\n' + ''.join(nblocks).rstrip('\n') + '\nedges:' + edges_part)
yaml.safe_load(open(ppath))

# ---- internal fragment ----
fpath = 'packs/internal/drg/fragment.yaml'
text = open(fpath).read()
anchor = '    body_path: directives/spec-kitty-red-main-policy.directive.yaml\n'
assert text.count(anchor) == 1
nodes_add = ('  # Procedures extracted from the internal directives\' former inline `procedures`\n'
             '  # lists (charter #240); each directive requires its procedure by an edge below.\n')
for p in PROCS:
    if p['pack'] == 'internal':
        nodes_add += (f"  - id: {p['id']}\n    kind: procedures\n    title: {json.dumps(p['title'], ensure_ascii=False)}\n"
                      f"    body_path: procedures/{p['id']}.procedure.yaml\n")
text = text.replace(anchor, anchor + nodes_add)
edges_add = ('  # Each internal directive requires the procedure that now carries the steps it\n'
             '  # formerly listed inline as `procedures` (charter #240).\n')
for p in PROCS:
    if p['pack'] == 'internal':
        for did, _ in p['users']:
            edges_add += (f"  - source: \"directive:{did}\"\n    target: \"procedure:{p['id']}\"\n    relation: requires\n"
                          f"    reason: {json.dumps(reason_for(did, p), ensure_ascii=False)}\n")
if not text.endswith('\n'):
    text += '\n'
text += edges_add
open(fpath, 'w').write(text)
fr = yaml.safe_load(open(fpath))
for e in fr['edges']:
    pass

# report tables
json.dump(dict(covered=covered, users={p['id']: [u[0] for u in p['users']] for p in PROCS},
               pack={p['id']: p['pack'] for p in PROCS}),
          open(os.path.join(os.path.dirname(__file__), 'map.json'), 'w'), indent=1)
print('procedures', len(PROCS), 'built-in edges', len(new_edges))
