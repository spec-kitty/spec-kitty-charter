import yaml,os,glob
R='/home/user/spec-kitty-open-packs/packs'
M={'software-dev':('built-in/missions/software-dev','built-in/missions/mission-steps/software-dev'),
'documentation':('built-in/missions/documentation','built-in/missions/mission-steps/documentation'),
'research':('built-in/missions/research','built-in/missions/mission-steps/research'),
'plan':('built-in/missions/plan','built-in/missions/mission-steps/plan'),
'cave-dev':('cave-kitty/missions/cave-dev',None),
'qa':('qa/missions/qa','qa/mission-steps/qa')}
for m,(md,sd) in M.items():
    rt=yaml.safe_load(open(f'{R}/{md}/mission-runtime.yaml'))
    rts=[s['id'] for s in rt['steps']]
    rtdeps={s['id']:s.get('depends_on') for s in rt['steps']}
    rtprof={s['id']:s.get('agent-profile') for s in rt['steps']}
    rtpt={s['id']:s.get('prompt_template') for s in rt['steps']}
    ea=yaml.safe_load(open(f'{R}/{md}/expected-artifacts.yaml'))
    eas=list(ea['required_by_step'].keys())
    acts=sorted(os.listdir(f'{R}/{md}/actions')) if os.path.isdir(f'{R}/{md}/actions') else []
    mm=yaml.safe_load(open(f'{R}/{md}/mission.yaml'))
    ph=[p['name'] for p in mm['workflow']['phases']]
    cmds=list((mm.get('commands') or {}).keys())
    ct=sorted(os.listdir(f'{R}/{md}/command-templates')) if os.path.isdir(f'{R}/{md}/command-templates') else []
    steps={}
    if sd:
        for d in sorted(os.listdir(f'{R}/{sd}')):
            y=yaml.safe_load(open(f'{R}/{sd}/{d}/step.yaml'))
            steps[d]=y
    print(f'### {m}')
    print(' runtime steps:',rts)
    print(' step dirs    :',list(steps))
    print(' exp-artifacts:',eas)
    print(' actions dirs :',acts)
    print(' mission.yaml phases:',ph,' commands:',cmds)
    print(' command-templates:',ct)
    for d,y in steps.items():
        files=sorted(os.listdir(f'{R}/{sd}/{d}'))
        flag=[]
        if y['id']!=d: flag.append('ID!=DIR')
        if d in rtdeps and sorted(rtdeps[d] or [])!=sorted(y['depends_on']): flag.append(f'deps rt={rtdeps[d]} step={y["depends_on"]}')
        if d in rtprof and rtprof[d]!=y['agent_profile']: flag.append(f'profile rt={rtprof[d]} step={y["agent_profile"]}')
        print(f'   {d}: files={files} seq={y.get("sequence_index","<absent>")} ias={y["in_action_sequence"]} inRT={d in rts} inEA={d in eas} inAct={d in acts} {" ".join(flag)}')
    for s in rts:
        if s not in steps: print(f'   RUNTIME-ONLY step {s} (prompt_template={rtpt[s]}, profile={rtprof[s]})')
