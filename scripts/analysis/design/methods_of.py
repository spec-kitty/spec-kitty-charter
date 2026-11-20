# usage: python3 -I methods_of.py lizard.csv ClassFile.java  -> methods in line order with nloc/ccn/params
import csv,sys
for r in sorted((r for r in csv.reader(open(sys.argv[1])) if r[6].endswith('/'+sys.argv[2])),key=lambda r:int(r[9])):
    print(f"{r[9]:>5} {r[7].split('::',1)[-1]} nloc={r[0]} ccn={r[1]} p={r[3]}")
