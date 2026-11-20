#!/usr/bin/env python3
"""L1 churn extraction. Run from the repo root:
    python3 -I <l1>/churn.py [REV]   (default REV=25e24c372bae838d95a9328be57ee2063f6c05d1)
Writes <l1>/out/commits.json : one record per non-merge commit reachable from REV, with
rename-resolved paths (paths are mapped to their name at REV), and the classification flags.
"""
import json, os, re, subprocess, sys

REV = sys.argv[1] if len(sys.argv) > 1 else "25e24c372bae838d95a9328be57ee2063f6c05d1"
OUT = os.environ.get("ANALYSIS_OUT", os.path.join(os.getcwd(), "build", "analysis", "hotspots"))
os.makedirs(OUT, exist_ok=True)

BOOK_SUBJ = re.compile(r"^(chore\(|op\(|docs\(mission|docs\(record-analysis|record-analysis)")
BOOK_PATH = re.compile(r"^(kitty-specs/|kitty-ops/|\.kittify/charter/generated/)|(^|/)build/")
SEP = "\x1e"

raw = subprocess.run(
    ["git", "log", REV, "--no-merges", "-M", "--numstat",
     f"--format={SEP}%H\t%at\t%ct\t%an\t%s"],
    capture_output=True, text=True, check=True).stdout

def norm(p):
    # numstat rename forms: "a/{b => c}/d" or "old => new"
    m = re.match(r"^(.*)\{(.*) => (.*)\}(.*)$", p)
    if m:
        pre, a, b, post = m.groups()
        old = (pre + a + post).replace("//", "/")
        new = (pre + b + post).replace("//", "/")
        return old, new
    if " => " in p:
        a, b = p.split(" => ", 1)
        return a, b
    return None, p

commits = []
for block in raw.split(SEP)[1:]:
    lines = block.strip("\n").split("\n")
    h, at, ct, an, subj = lines[0].split("\t", 4)
    files = []
    for ln in lines[1:]:
        if not ln.strip():
            continue
        a, d, p = ln.split("\t", 2)
        old, new = norm(p)
        files.append({"add": int(a) if a != "-" else 0, "del": int(d) if d != "-" else 0,
                      "old": old, "path": new})
    commits.append({"h": h, "at": int(at), "ct": int(ct), "author": an, "subj": subj, "files": files})

# git log is newest-first: resolve renames to their name at REV
final = {}
for c in commits:
    for f in c["files"]:
        f["final"] = final.get(f["path"], f["path"])
    for f in c["files"]:
        if f["old"]:
            final[f["old"]] = f["final"]

for c in commits:
    c["bookkeeping"] = bool(BOOK_SUBJ.match(c["subj"]))
    c["squash"] = "squash merge" in c["subj"]
    for f in c["files"]:
        f["excluded_path"] = bool(BOOK_PATH.search(f["final"]))
    c["n_src_files"] = sum(1 for f in c["files"] if not f["excluded_path"])
    c["n_java_main"] = sum(1 for f in c["files"] if f["final"].endswith(".java") and "/src/main/" in f["final"])

json.dump(commits, open(os.path.join(OUT, "commits.json"), "w"))
print(f"{len(commits)} commits written to {OUT}/commits.json")
