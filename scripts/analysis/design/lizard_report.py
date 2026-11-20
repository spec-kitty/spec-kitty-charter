# usage: python3 -I lizard_report.py lizard-main.csv  (columns: NLOC,CCN,token,PARAM,length,location,file,func,long,start,end)
import csv,sys,collections,os
rows=list(csv.reader(open(sys.argv[1])))
print("## methods length>60 or CCN>=15 (sorted by length)")
sel=[r for r in rows if int(r[4])>60 or int(r[1])>=15]
sel.sort(key=lambda r:-int(r[4]))
for r in sel:
    print(f"{r[4]:>5} len {r[0]:>4} nloc ccn {r[1]:>3} params {r[3]} | {os.path.basename(r[6])}:{r[9]} {r[7]}")
print("## params>=6")
for r in sorted(rows,key=lambda r:-int(r[3])):
    if int(r[3])>=6: print(f"params {r[3]} ccn {r[1]} | {os.path.basename(r[6])}:{r[9]} {r[7]}")
print("## per-file sum CCN top 25")
agg=collections.defaultdict(lambda:[0,0,0,0])
for r in rows:
    a=agg[r[6]]; a[0]+=int(r[1]); a[1]+=1; a[2]=max(a[2],int(r[1])); a[3]+=int(r[0])
for f,a in sorted(agg.items(),key=lambda kv:-kv[1][0])[:25]:
    print(f"sumCCN {a[0]:>4} fns {a[1]:>3} maxCCN {a[2]:>3} fnNLOC {a[3]:>5} | {f}")
