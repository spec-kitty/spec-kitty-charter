# Samples N method-level javadoc blocks (seeded) from main sources, printing the block's first sentence and the
# declaration it documents, for a manual restates-vs-explains classification.
# usage: python3 -I sample_javadoc.py <file-list> <repo> <N> <seed>
import re,sys,random,os
lst,root,n,seed=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4])
blocks=[]
for rel in open(lst).read().split():
    t=open(os.path.join(root,rel),encoding='utf-8').read()
    for m in re.finditer(r'/\*\*(.*?)\*/\s*\n(\s*[^\n]+)',t,re.S):
        decl=m.group(2).strip()
        if '(' in decl and not decl.startswith('@'):
            body=' '.join(l.strip().lstrip('*').strip() for l in m.group(1).split('\n')).strip()
            blocks.append((rel.split('/java/')[1]+':'+str(t.count('\n',0,m.start())+1),body,decl))
random.Random(seed).shuffle(blocks)
for loc,body,decl in blocks[:n]:
    print(f"- {loc}\n  DOC: {body[:260]}\n  DECL: {decl[:120]}")
print(len(blocks),"method/ctor javadoc blocks")
