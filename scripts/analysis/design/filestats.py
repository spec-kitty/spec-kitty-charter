# per-file: physical lines, code lines, comment lines, nested type decls, public methods, fields; for files > N lines
# usage: python3 -I filestats.py <repo> <file-list> <min-lines>
import re,sys,os
root,lst,mn=sys.argv[1],sys.argv[2],int(sys.argv[3])
print("| File | Lines | Code | Comment | Types (nested) | public/package methods | static fields |")
print("|---|---:|---:|---:|---:|---:|---:|")
rows=[]
for rel in open(lst).read().split():
    L=open(os.path.join(root,rel),encoding='utf-8').read().split('\n')
    if len(L)<mn: continue
    code=com=0;inc=False
    for l in L:
        s=l.strip()
        if inc:
            com+=1
            if '*/' in s: inc=False
            continue
        if s.startswith('/*'):
            com+=1; inc='*/' not in s; continue
        if s.startswith('//'): com+=1; continue
        if s: code+=1
    txt='\n'.join(L)
    types=len(re.findall(r'^\s*(?:public |private |protected )?(?:static )?(?:final |sealed |non-sealed |abstract )*(?:class|record|interface|enum)\s+\w+',txt,re.M))
    pub=len(re.findall(r'^\s{4}public (?!(?:static )?(?:final )?(?:class|record|interface|enum))[\w<>\[\], ?.]+\s+\w+\(',txt,re.M))
    stat=len(re.findall(r'^\s*(?:private |public |protected )?static final ',txt,re.M))
    rows.append((len(L),rel,code,com,types,pub,stat))
for r in sorted(rows,reverse=True):
    print(f"| {r[1].split('/java/')[1]} | {r[0]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} | {r[6]} |")
