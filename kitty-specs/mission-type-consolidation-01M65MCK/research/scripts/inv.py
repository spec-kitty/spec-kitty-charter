import yaml,glob,collections,sys,os,json
root='/home/user/spec-kitty-open-packs/packs'
fams={
 'mission_types/*.yaml':['built-in/missions/mission_types/*.yaml','*/mission_types/*.yaml'],
 'governance-profile.yaml':['**/governance-profile.yaml'],
 'step.yaml':['**/mission-steps/**/step.yaml'],
 'actions/index.yaml':['**/actions/*/index.yaml'],
 'mission.yaml':['**/missions/*/mission.yaml'],
 'mission-runtime.yaml':['**/mission-runtime.yaml'],
 'expected-artifacts.yaml':['**/expected-artifacts.yaml'],
}
def walk(o,p,acc,f):
    if isinstance(o,dict):
        for k,v in o.items():
            q=p+'.'+str(k) if p else str(k)
            acc[q]['n']+=1; acc[q]['t'].add(type(v).__name__); acc[q]['files'].add(f)
            if not isinstance(v,(dict,list)) and len(acc[q]['ex'])<4: acc[q]['ex'].add(repr(v)[:50])
            if isinstance(v,list) and len(v)==0: acc[q]['empty']+=1
            if v is None: acc[q]['null']+=1
            walk(v,q,acc,f)
    elif isinstance(o,list):
        for i in o: walk(i,p+'[]',acc,f)
for fam,pats in fams.items():
    files=set()
    for pt in pats: files|=set(glob.glob(os.path.join(root,pt),recursive=True))
    acc=collections.defaultdict(lambda:{'n':0,'t':set(),'ex':set(),'files':set(),'empty':0,'null':0})
    for f in sorted(files):
        walk(yaml.safe_load(open(f)),'',acc,f)
    print(f'## {fam} ({len(files)} files)')
    for f in sorted(files): print('   ',f.replace(root+'/',''))
    for k,v in acc.items():
        print(f'  {k}: n={v["n"]} files={len(v["files"])} types={sorted(v["t"])} empty[]={v["empty"]} null={v["null"]} ex={sorted(v["ex"])}')
