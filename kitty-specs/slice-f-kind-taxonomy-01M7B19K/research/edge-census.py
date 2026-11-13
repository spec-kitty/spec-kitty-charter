import os, sys, yaml, csv, collections, re
import os as _os; CH=_os.path.abspath(_os.path.join(_os.path.dirname(__file__), '../../..')); OP=_os.environ.get('OPEN_PACKS_DIR', _os.path.join(CH, '..', 'spec-kitty-open-packs'))
NONACT={'asset','template','guard','lifecycle'}
def docs(root):
    for d,_,fs in os.walk(root):
        if '/.git' in d or '/build/' in d+'/': continue
        for f in fs:
            p=os.path.join(d,f)
            if f.endswith('.graph.yaml') or p.endswith('drg/fragment.yaml') or f=='registered.graph.yaml': yield p
def group(p):
    if p.startswith(OP):
        r=p[len(OP)+1:]
        return 'open-packs' if r.startswith('packs/') else 'open-packs-tests'
    r=p[len(CH)+1:]
    if r.startswith('packs/'): return 'charter-synced-copies'
    if '/testFixtures/resources/example-packs/' in r: return 'charter-example-packs'
    if '/src/test/resources/planted/' in r: return 'charter-planted-fixtures'
    if '/src/test/resources/load-fixtures/' in r: return 'charter-load-fixtures'
    if '/src/test' in r or r.startswith('tests/'): return 'charter-other-test-fixtures'
    return 'charter-other'
def kind(e):
    if not isinstance(e,str): return None,'?'
    pack=None
    if '::' in e: pack,e=e.split('::',1)
    if ':' not in e: return pack,'<bare>'
    return pack,e.split(':',1)[0]
rows=collections.Counter(); files=collections.Counter(); errs=[]; ov=[]
for root in (CH,OP):
    for p in sorted(docs(root)):
        g=group(p)
        try: d=yaml.safe_load(open(p,encoding='utf-8'))
        except Exception as ex: errs.append((p,'unparseable')); continue
        if not isinstance(d,dict) or not isinstance(d.get('edges'),list): continue
        files[g]+=1
        for e in d['edges']:
            if not isinstance(e,dict): continue
            sp,sk=kind(e.get('source')); tp,tk=kind(e.get('target'))
            rel=e.get('relation'); 
            rows[(g,str(rel),sk,tk)]+=1
            if rel=='overrides' and sk in NONACT: ov.append((g,p,e.get('source'),e.get('target')))
out=os.path.dirname(os.path.abspath(__file__))+'/edge_census.csv'
with open(out,'w',newline='') as f:
    w=csv.writer(f); w.writerow(['corpus','relation','source_kind','target_kind','count'])
    for k,v in sorted(rows.items()): w.writerow([*k,v])
print('files with edges',dict(files)); print('unparseable',len(errs))
for g in sorted({k[0] for k in rows}):
    print('\n==',g, sum(v for k,v in rows.items() if k[0]==g))
    for k,v in sorted(rows.items()):
        if k[0]==g: print(f'  {k[1]:20} {k[2]:22} -> {k[3]:22} {v}')
print('\noverrides from non-activatable:'); 
for o in ov: print(' ',o)
