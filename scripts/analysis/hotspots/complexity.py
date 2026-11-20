#!/usr/bin/env python3
"""Per-file lizard metrics for main Java code of a source tree.
Usage (run under lizard 1.17.13):
    uvx --from lizard==1.17.13 python <l1>/complexity.py <tree-root> <out.json>
<tree-root> is either the repo root or a `git archive` extraction of a commit.
Scope: service/*/src/main/**/*.java and client-java/src/main/**/*.java (hand-written; the generated
client lives under build/ and is never committed). Test Java (src/test, src/testFixtures,
src/driftTest) is measured separately for LOC only.
"""
import glob, json, os, sys
import re
import lizard

WILDCARD = re.compile(r"([<,]\s*)\?")

root, out = sys.argv[1], sys.argv[2]
rows = []
pats = ["service/*/src/*/**/*.java", "client-java/src/*/**/*.java"]
for pat in pats:
    for p in sorted(glob.glob(os.path.join(root, pat), recursive=True)):
        rel = os.path.relpath(p, root)
        parts = rel.split("/")
        module = parts[1] if parts[0] == "service" else "client-java"
        srcset = parts[3] if parts[0] == "service" else parts[2]
        with open(p, encoding="utf-8", errors="replace") as fh:
            code = fh.read()
        physical = code.count("\n")
        # lizard counts every '?' token as a ternary decision; Java wildcard generics (Map<?, ?>,
        # List<? extends X>) are not decisions, so they are rewritten to a plain type name first.
        wildcards = len(WILDCARD.findall(code))
        info = lizard.analyze_file.analyze_source_code(p, WILDCARD.sub(r"\1W", code))
        raw = lizard.analyze_file.analyze_source_code(p, code)
        # lizard 1.17.13 does not parse Java records: `record R(...) { body }` comes back as ONE function named R
        # spanning the whole body. Those pseudo-functions are kept in sum_ccn (their decision points are real) but
        # are kept out of max_ccn and the function count, and reported as record_lumped.
        recs = set(re.findall(r"\brecord\s+(\w+)\s*[(<]", code))
        lumped = [f for f in info.function_list if f.name.split("::")[-1] in recs]
        fns = [f for f in info.function_list if f not in lumped]
        allfns = info.function_list
        pkg = os.path.dirname(rel.split("/java/", 1)[1]) if "/java/" in rel else ""
        rows.append({
            "path": rel, "module": module, "srcset": srcset, "package": pkg.replace("/", "."),
            "lines": physical, "nloc": info.nloc, "functions": len(fns),
            "sum_ccn": sum(f.cyclomatic_complexity for f in allfns),
            "record_lumped": len(lumped), "record_lumped_ccn": sum(f.cyclomatic_complexity for f in lumped),
            "record_lumped_nloc": sum(f.nloc for f in lumped),
            "max_ccn": max((f.cyclomatic_complexity for f in fns), default=0),
            "max_fn": max(fns, key=lambda f: f.cyclomatic_complexity).name if fns else "",
            "max_fn_line": max(fns, key=lambda f: f.cyclomatic_complexity).start_line if fns else 0,
            "fns_ccn_gt10": sum(1 for f in fns if f.cyclomatic_complexity > 10),
            "raw_sum_ccn": sum(f.cyclomatic_complexity for f in raw.function_list),
            "raw_max_ccn": max((f.cyclomatic_complexity for f in raw.function_list), default=0),
            "wildcards": wildcards,
            "fns_ccn_gt15": sum(1 for f in fns if f.cyclomatic_complexity > 15),
        })
json.dump(rows, open(out, "w"), indent=0)
print(len(rows), "files ->", out)
