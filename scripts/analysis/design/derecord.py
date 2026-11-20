# Rewrites Java record headers into class headers (preserving line numbers) so lizard 1.17.13,
# which treats a whole record body as one function, reports the record's methods individually.
# usage: python3 -I derecord.py <repo-root> <file-list> <out-dir>
import re,sys,os
root,lst,out=sys.argv[1:4]
hdr=re.compile(r'\brecord\s+(\w+)\s*(<[^>{;]*>)?\s*\(')
for rel in open(lst).read().split():
    src=open(os.path.join(root,rel),encoding='utf-8').read()
    names=[]
    res=[];i=0
    for m in hdr.finditer(src):
        if m.start()<i: continue
        # skip if inside comment line (crude): check text since line start
        ls=src.rfind('\n',0,m.start())+1
        pre=src[ls:m.start()].lstrip()
        if pre.startswith('*') or pre.startswith('//'): continue
        depth=0;j=m.end()-1
        while j<len(src):
            c=src[j]
            if c=='(':depth+=1
            elif c==')':
                depth-=1
                if depth==0:break
            j+=1
        names.append(m.group(1))
        seg=src[m.start():j+1]
        repl='class '+m.group(1)+(m.group(2) or '')
        repl+=''.join(ch if ch=='\n' else ' ' for ch in seg[len(repl):]) if len(seg)>len(repl) else ''
        res.append(src[i:m.start()]);res.append(repl);i=j+1
    res.append(src[i:]);txt=''.join(res)
    for n in set(names):  # compact constructors: "public Name {" -> "public Name() {" (not the class header)
        def fix(mm):
            before=txt[max(0,mm.start()-40):mm.start()]
            if re.search(r'class\s*$',before): return mm.group(0)
            return mm.group(0)[:-1].rstrip()+'() {'
        txt=re.sub(r''+n+r'\s*\{', fix, txt)
    p=os.path.join(out,rel);os.makedirs(os.path.dirname(p),exist_ok=True);open(p,'w',encoding='utf-8').write(txt)
