# Package and module dependency graph over main sources (imports + inline fully-qualified ai.speckitty.charter refs).
# Prints package-level SCCs (cycles), module edges, and edge-module edges.
# usage: [STRIP_COMMENTS=1] python3 -I import_graph.py <service-dir>   (STRIP_COMMENTS=1 ignores javadoc/comment mentions)
import re,os,sys,collections
root=sys.argv[1]
pkg_of_file={};mod_of_pkg={};deps=collections.defaultdict(set);ev=collections.defaultdict(list)
files=[]
for d,_,fs in os.walk(root):
    if '/build/' in d+'/' or '/src/main/java' not in d: continue
    for f in fs:
        if f.endswith('.java'): files.append(os.path.join(d,f))
cls_pkg={}
for p in files:
    t=open(p,encoding='utf-8').read()
    m=re.search(r'^package ([\w.]+);',t,re.M); pk=m.group(1)
    mod=p[len(root):].strip('/').split('/')[0]
    pkg_of_file[p]=pk; mod_of_pkg.setdefault(pk,mod)
    cls_pkg[pk+'.'+os.path.basename(p)[:-5]]=pk
def target_pkg(ref):
    parts=ref.split('.')
    for i in range(len(parts),0,-1):
        q='.'.join(parts[:i])
        if q in cls_pkg: return cls_pkg[q]
        if q in mod_of_pkg and i<len(parts) and parts[i][:1].isupper(): return q
    return None
STRIP=os.environ.get('STRIP_COMMENTS')=='1'
def strip(t):
    t=re.sub(r'/\*.*?\*/',lambda m:chr(10)*m.group(0).count(chr(10)),t,flags=re.S)
    return re.sub(r'//[^\n]*','',t)
for p in files:
    t=open(p,encoding='utf-8').read(); src=pkg_of_file[p]
    if STRIP: t=strip(t)
    for m in re.finditer(r'\b(ai\.speckitty\.charter(?:\.\w+)+)',t):
        tp=target_pkg(m.group(1).rstrip('.'))
        if tp and tp!=src:
            deps[src].add(tp); ev[(src,tp)].append(os.path.basename(p))
# Tarjan
idx={};low={};st=[];on=set();sccs=[];c=[0]
sys.setrecursionlimit(10000)
def sc(v):
    idx[v]=low[v]=c[0];c[0]+=1;st.append(v);on.add(v)
    for w in deps.get(v,()):
        if w not in idx: sc(w);low[v]=min(low[v],low[w])
        elif w in on: low[v]=min(low[v],idx[w])
    if low[v]==idx[v]:
        comp=[]
        while True:
            w=st.pop();on.discard(w);comp.append(w)
            if w==v:break
        if len(comp)>1: sccs.append(comp)
for v in list(mod_of_pkg):
    if v not in idx: sc(v)
short=lambda s:s.replace('ai.speckitty.charter.','')
print("# package cycles (SCCs of size>1):",len(sccs))
for comp in sorted(sccs,key=len,reverse=True):
    print(f"\n## SCC size {len(comp)} (module {', '.join(sorted({mod_of_pkg[x] for x in comp}))})")
    cs=set(comp)
    for a in sorted(comp):
        for b in sorted(deps[a]&cs):
            print(f"  {short(a)} -> {short(b)}  [{', '.join(sorted(set(ev[(a,b)]))[:4])}]")
print("\n# module edges")
med=collections.defaultdict(set)
for a,bs in deps.items():
    for b in bs:
        ma,mb=mod_of_pkg[a],mod_of_pkg[b]
        if ma!=mb: med[ma].add(mb)
for m in sorted(med): print(f"  {m} -> {', '.join(sorted(med[m]))}")
print("\n# edges out of *-edge modules (package level)")
for a in sorted(deps):
    if mod_of_pkg[a].endswith('-edge'):
        for b in sorted(deps[a]):
            if mod_of_pkg[b]!=mod_of_pkg[a]:
                print(f"  [{mod_of_pkg[a]}] {short(a)} -> [{mod_of_pkg[b]}] {short(b)}  ({len(ev[(a,b)])} refs)")
print("\n# intra-slice adapter-to-adapter edges (infra.X -> infra.Y, same slice root)")
for a in sorted(deps):
    ma=re.match(r'ai\.speckitty\.charter\.(\w+)\.infra\.(\w+)',a)
    if not ma: continue
    for b in sorted(deps[a]):
        mb=re.match(r'ai\.speckitty\.charter\.(\w+)\.infra\.(\w+)',b)
        if mb and mb.group(1)==ma.group(1) and mb.group(2)!=ma.group(2):
            print(f"  {short(a)} -> {short(b)}  [{', '.join(sorted(set(ev[(a,b)]))[:5])}]")
