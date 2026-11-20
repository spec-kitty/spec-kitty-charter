# Heuristic: public/package-level methods in main whose NAME is never referenced (as .name( / ::name / name( outside its
# declaration line) anywhere in main sources, but is referenced in test sources. Name-based, so collisions hide hits.
# usage: python3 -I test_only_public.py <service-dir>
import re,sys,os,collections
root=sys.argv[1]
main,test=[],[]
for d,_,fs in os.walk(root):
    if '/build/' in d+'/': continue
    for f in fs:
        if f.endswith('.java'):
            p=os.path.join(d,f)
            (main if '/src/main/' in p else test if '/src/test/' in p else []).append(p)
decl=re.compile(r'^\s{4}public (?:static )?(?:final )?(?:<[^>]+>\s+)?[\w<>\[\], ?.]+\s+(\w+)\(',re.M)
mtxt={p:open(p,encoding='utf-8').read() for p in main}
ttxt='\n'.join(open(p,encoding='utf-8').read() for p in test)
decls=[]
for p,t in mtxt.items():
    for m in decl.finditer(t):
        decls.append((m.group(1),p,t.count('\n',0,m.start())+1))
allmain='\n'.join(mtxt.values())
for name,p,line in sorted(decls):
    if name in ('equals','hashCode','toString','main','value','of','close','get','run','apply','accept','test','compareTo'): continue
    uses=len(re.findall(r'(?:\.|::)'+name+r'\b',allmain)) + len(re.findall(r'(?<![\w.])'+name+r'\(',allmain)) - 1
    if uses<=0 and re.search(r'(?:\.|::)'+name+r'\b',ttxt):
        print(f"{p.split('/java/')[1]}:{line} {name}")
