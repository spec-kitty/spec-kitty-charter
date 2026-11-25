#!/usr/bin/env python3
"""L1 hotspot / coupling / trend / team analysis. Run from the repo root after churn.py and
complexity.py (see run-all.sh). Reads <l1>/out/{commits.json,cx-25e24c37.json,cx-fd20faac.json},
writes markdown tables to <l1>/out/*.md and stdout.

Definitions (all at REV = 25e24c37, history = `git log REV --no-merges -M --numstat`):
  bookkeeping commit = subject matches ^(chore\\(|op\\(|docs\\(mission|docs\\(record-analysis)
  excluded path      = kitty-specs/**, kitty-ops/**, .kittify/charter/generated/**, **/build/**
  revisions(f)       = number of non-bookkeeping commits touching f (rename-resolved to its name at REV)
  rev_small(f)       = same, counting only commits with <= MAXCS non-excluded files (squash/sweep filter)
  hotspot score      = revisions x sum_ccn (lizard cyclomatic complexity summed over the file's functions)
  norm score         = (revisions / max revisions) x (sum_ccn / max sum_ccn)   (0..1)
  coupling(a,b)      = shared(a,b) / ((rev(a) + rev(b)) / 2), commits with <= MAXCS files only,
                       files with rev >= MINREV, pairs with shared >= MINSHARED
"""
import collections, itertools, json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("ANALYSIS_OUT", os.path.join(os.getcwd(), "build", "analysis", "hotspots"))
REV = os.environ.get("ANALYSIS_REV", "25e24c372bae838d95a9328be57ee2063f6c05d1")
BASE = os.environ.get("ANALYSIS_BASE", "fd20faacb")
REV8, BASE8 = REV[:8], BASE[:8]
MAXCS, MINREV, MINSHARED = 30, 8, 5

C = json.load(open(os.path.join(OUT, "commits.json")))
CX = {r["path"]: r for r in json.load(open(os.path.join(OUT, f"cx-{REV8}.json")))}
CX0 = json.load(open(os.path.join(OUT, f"cx-{BASE8}.json")))

def sh(*a):
    return subprocess.run(a, capture_output=True, text=True, check=True).stdout

after = set(sh("git", "rev-list", "--no-merges", f"{BASE}..{REV}").split())
base_ct = int(sh("git", "log", "-1", "--format=%ct", BASE).strip())
before = set(h for h in sh("git", "rev-list", "--no-merges", BASE, f"--since={base_ct - 30*86400}").split())
DAYS_AFTER = (int(sh("git", "log", "-1", "--format=%ct", REV).strip()) - base_ct) / 86400
DAYS_BEFORE = 30.0

def module_of(p):
    s = p.split("/")
    if s[0] == "service" and len(s) > 1: return s[1]
    if s[0] in ("client-java", "build-logic", "contracts", "scripts", "docs", ".github", "site", "src", "packs", "tests"): return s[0]
    return "(root)"

def ring_of(p):
    for r in ("domain", "application", "infra", "infrastructure", "web", "api", "edge"):
        if f"/{r}/" in p: return "infra" if r == "infrastructure" else r
    return "-"

def is_code(p):
    return p.endswith((".java", ".kt", ".kts", ".py", ".sh")) or (p.startswith("contracts/") and p.endswith((".yaml", ".yml", ".json")))

nb = [c for c in C if not c["bookkeeping"]]
rev, rev_small, rev_after, rev_before, churn = (collections.Counter() for _ in range(5))
touch = collections.defaultdict(list)
for c in nb:
    fs = {f["final"] for f in c["files"] if not f["excluded_path"]}
    small = len(fs) <= MAXCS
    for f in c["files"]:
        if f["excluded_path"]: continue
        churn[f["final"]] += f["add"] + f["del"]
    for p in fs:
        rev[p] += 1
        touch[p].append(c)
        if small: rev_small[p] += 1
        if c["h"] in after: rev_after[p] += 1
        if c["h"] in before: rev_before[p] += 1

# ---------- 1. churn summary ----------
md = []
def table(head, rows):
    md.append("| " + " | ".join(head) + " |")
    md.append("|" + "|".join("---:" if i else "---" for i in range(len(head))) + "|")
    for r in rows: md.append("| " + " | ".join(str(x) for x in r) + " |")
    md.append("")

md.append("## Churn summary\n")
book = [c for c in C if c["bookkeeping"]]
sizes = [c["n_src_files"] for c in nb]
tot_files = sum(sizes)
tot_lines = sum(f["add"] + f["del"] for c in nb for f in c["files"] if not f["excluded_path"])
rows = []
for N in (20, 30, 50, 100):
    big = [c for c in nb if c["n_src_files"] > N]
    rows.append((f"> {N} files", len(big), f"{100*len(big)/len(nb):.1f}%",
                 f"{100*sum(c['n_src_files'] for c in big)/tot_files:.1f}%",
                 f"{100*sum(f['add']+f['del'] for c in big for f in c['files'] if not f['excluded_path'])/tot_lines:.1f}%"))
md.append(f"Commits: {len(C)} non-merge (0 merge commits: main is linear), {len(book)} bookkeeping, {len(nb)} analysed. "
          f"File-touches {tot_files}, lines changed {tot_lines}.\n")
table(["commit size (non-excluded files)", "commits", "% of commits", "% of file-touches", "% of lines changed"], rows)
sq = [c for c in nb if c["squash"]]
table(["squash-merge commit", "files", "main Java", "lines", "subject"],
      [(c["h"][:8], c["n_src_files"], c["n_java_main"], sum(f["add"]+f["del"] for f in c["files"] if not f["excluded_path"]), c["subj"][:80]) for c in sq])
table(["largest commits", "files", "main Java", "lines", "subject"],
      [(c["h"][:8], c["n_src_files"], c["n_java_main"], sum(f["add"]+f["del"] for f in c["files"] if not f["excluded_path"]), c["subj"][:80].replace("|", "/"))
       for c in sorted(nb, key=lambda c: -c["n_src_files"])[:12]])

# ---------- 2. hotspots ----------
main = {p: r for p, r in CX.items() if r["srcset"] == "main"}
maxrev = max(rev[p] for p in main) or 1
maxccn = max(r["sum_ccn"] for r in main.values()) or 1
hs = []
for p, r in main.items():
    hs.append(dict(r, rev=rev[p], rev_small=rev_small[p], churn=churn[p], score=rev[p] * r["sum_ccn"],
                   norm=(rev[p] / maxrev) * (r["sum_ccn"] / maxccn)))
hs.sort(key=lambda h: -h["score"])
short = lambda p: p.split("/java/", 1)[-1].replace("ai/speckitty/charter/", "")
md.append("## Top 25 hotspots (score = revisions x sum CCN)\n")
table(["#", "module", "file", "rev", "rev<=30f", "NLOC", "fns", "sum CCN", "max CCN (fn:line)", "score", "norm"],
      [(i + 1, h["module"], short(h["path"]), h["rev"], h["rev_small"], h["nloc"], h["functions"], h["sum_ccn"],
        f"{h['max_ccn']} ({h['max_fn'].split('::')[-1]}:{h['max_fn_line']})", h["score"], f"{h['norm']:.3f}")
       for i, h in enumerate(hs[:25])])
# rank stability under the squash filter
hs_small = sorted(hs, key=lambda h: -h["rev_small"] * h["sum_ccn"])
top20, top20s = [h["path"] for h in hs[:20]], [h["path"] for h in hs_small[:20]]
md.append(f"Top-20 overlap with the squash-filtered ranking (rev counted only in commits <= {MAXCS} files): "
          f"{len(set(top20) & set(top20s))}/20. Entering under the filter: "
          + ", ".join(short(p) for p in top20s if p not in top20) + "\n")

for key, label in (("package", "package"), ("module", "Gradle module")):
    agg = collections.defaultdict(lambda: [0, 0, 0, 0, 0])
    for h in hs:
        a = agg[h[key] if key == "module" else h["module"] + ":" + h["package"].replace("ai.speckitty.charter.", "")]
        a[0] += 1; a[1] += h["nloc"]; a[2] += h["sum_ccn"]; a[3] += h["score"]; a[4] += h["rev"]
    tot = sum(a[3] for a in agg.values())
    md.append(f"## Hotspot aggregate by {label}\n")
    table([label, "files", "NLOC", "sum CCN", "sum revisions", "sum score", "% of score"],
          [(k, *a[:3], a[4], a[3], f"{100*a[3]/tot:.1f}%") for k, a in sorted(agg.items(), key=lambda kv: -kv[1][3])[:20 if key == "package" else 40]])

# ---------- 3. change coupling ----------
cand = {p for p in rev_small if rev_small[p] >= MINREV and is_code(p) and p in CX or (rev_small[p] >= MINREV and is_code(p) and os.path.exists(p))}
shared = collections.Counter()
for c in nb:
    fs = sorted({f["final"] for f in c["files"] if not f["excluded_path"]})
    if len(fs) > MAXCS: continue
    fs = [p for p in fs if p in cand]
    for a, b in itertools.combinations(fs, 2): shared[(a, b)] += 1

def base_name(p): return os.path.basename(p).rsplit(".", 1)[0]
def is_own_test(a, b):
    na, nb_ = base_name(a), base_name(b)
    for t, m in ((a, b), (b, a)):
        if "/src/test" in t and "/src/main/" in m and re.sub(r"(Test|IT|Tests|PropertyTest|ContractTest)$", "", base_name(t)) == base_name(m):
            return True
    return False

pairs, own = [], []
for (a, b), s in shared.items():
    if s < MINSHARED: continue
    cpl = s / ((rev_small[a] + rev_small[b]) / 2)
    rec = (a, b, s, rev_small[a], rev_small[b], cpl)
    (own if is_own_test(a, b) else pairs).append(rec)
pairs.sort(key=lambda r: (-r[5], -r[2]))
def tag(a, b):
    t = []
    ma, mb = module_of(a), module_of(b)
    if ma != mb: t.append(f"X-module {ma}<->{mb}")
    ra, rb = ring_of(a), ring_of(b)
    if ma == mb and ra != rb and "-" not in (ra, rb): t.append(f"X-ring {ra}<->{rb}")
    if ("/src/test" in a) != ("/src/test" in b): t.append("test<->other prod")
    return "; ".join(t)
md.append(f"## Change coupling, file pairs (commits <= {MAXCS} files, rev >= {MINREV}, shared >= {MINSHARED}); own-test pairs excluded\n")
md.append(f"Candidate files: {len(cand)}. Pairs >= {MINSHARED} shared: {len(pairs) + len(own)}; own test<->production pairs: {len(own)}.\n")
table(["#", "file A", "file B", "shared", "revA", "revB", "coupling", "crossing"],
      [(i + 1, f"{module_of(a)}:{base_name(a)}", f"{module_of(b)}:{base_name(b)}", s, ra, rb, f"{100*cp:.0f}%", tag(a, b))
       for i, (a, b, s, ra, rb, cp) in enumerate(pairs[:40])])
xm = [r for r in pairs if module_of(r[0]) != module_of(r[1])]
md.append("### Top cross-module pairs\n")
table(["file A", "file B", "shared", "revA", "revB", "coupling", "crossing"],
      [(f"{module_of(a)}:{base_name(a)}", f"{module_of(b)}:{base_name(b)}", s, ra, rb, f"{100*cp:.0f}%", tag(a, b)) for a, b, s, ra, rb, cp in xm[:25]])
mainmain = [r for r in pairs if "/src/main/" in r[0] and "/src/main/" in r[1]]
md.append("### Top production<->production pairs\n")
table(["file A", "file B", "shared", "revA", "revB", "coupling", "crossing"],
      [(f"{module_of(a)}:{short(a)}", f"{module_of(b)}:{short(b)}", s, ra, rb, f"{100*cp:.0f}%", tag(a, b)) for a, b, s, ra, rb, cp in mainmain[:25]])
own.sort(key=lambda r: -r[5])
# own-test ratio: for main files with rev_small >= MINREV that have a test, share of their commits that also touch the test
md.append("### Own test<->production coupling (healthy signal, reported separately)\n")
cps = [r[5] for r in own]
md.append(f"{len(own)} pairs; median coupling {sorted(cps)[len(cps)//2]*100:.0f}%, "
          f"{sum(1 for x in cps if x >= .5)} at >= 50%.\n" if cps else "none\n")
table(["test", "production", "shared", "rev test", "rev prod", "coupling"],
      [(base_name(a), base_name(b), s, ra, rb, f"{100*cp:.0f}%") for a, b, s, ra, rb, cp in own[:10]])

# module-level coupling
mods = collections.Counter(); mshared = collections.Counter()
for c in nb:
    fs = {f["final"] for f in c["files"] if not f["excluded_path"] and is_code(f["final"])}
    if not fs or len(fs) > MAXCS: continue
    ms = sorted({module_of(p) for p in fs})
    for m in ms: mods[m] += 1
    for a, b in itertools.combinations(ms, 2): mshared[(a, b)] += 1
md.append(f"## Module-level coupling (code files only, commits <= {MAXCS} files)\n")
table(["module", "commits touching (code)"], sorted(mods.items(), key=lambda kv: -kv[1]))
mp = [(a, b, s, mods[a], mods[b], s / ((mods[a] + mods[b]) / 2)) for (a, b), s in mshared.items() if s >= 5]
mp.sort(key=lambda r: -r[5])
table(["module A", "module B", "shared", "revA", "revB", "coupling"], [(a, b, s, ra, rb, f"{100*cp:.0f}%") for a, b, s, ra, rb, cp in mp[:30]])
edge = [("missions", "missions-edge"), ("profiles", "profiles-edge"), ("routing", "routing-edge"), ("packs", "artifacts-edge"),
        ("missions", "artifacts-edge"), ("kernel", "packs"), ("packs", "deployable")]
md.append("### Domain vs edge modules\n")
table(["module A", "module B", "shared", "revA", "revB", "coupling"],
      [(a, b, mshared[tuple(sorted((a, b)))], mods[a], mods[b], f"{100*mshared[tuple(sorted((a,b)))]/((mods[a]+mods[b])/2):.0f}%" if mods[a] + mods[b] else "-") for a, b in edge])

# ---------- 4. trend ----------
old2new = {}
for c in C:
    for f in c["files"]:
        old2new.setdefault(f["path"], f["final"])
        if f["old"]: old2new.setdefault(f["old"], f["final"])
cx0 = {}
for r in CX0:
    cx0[old2new.get(r["path"], r["path"])] = r
md.append(f"## Trend: {BASE} .. {REV8} ({DAYS_AFTER:.1f} days, {len(after)} commits) vs 30 days before {BASE} ({len(before)} commits)\n")
NA = sum(1 for c in nb if c["h"] in after); NBF = sum(1 for c in nb if c["h"] in before)
md.append(f"Non-bookkeeping commits: {NBF} in the 30 days before ({NBF/DAYS_BEFORE*7:.0f}/week), {NA} after ({NA/DAYS_AFTER*7:.0f}/week). "
          "The label compares each file's SHARE of the window's non-bookkeeping commits, so a different commit rate "
          "between the two windows does not by itself read as heating or cooling: rel = (rev_after/NA) / (rev_before/NBF); heating rel >= 1.5 "
          "(and >= 3 revisions after), cooling rel <= 0.67, else stable.\n")
def rel(p):
    sb = rev_before[p] / NBF
    return (rev_after[p] / NA) / sb if sb else float("inf")
def label(h):
    r = rel(h["path"])
    if r >= 1.5 and rev_after[h["path"]] >= 3: return "heating"
    if r <= 0.67: return "cooling"
    return "stable"
rows = []
for h in hs[:25]:
    o = cx0.get(h["path"])
    d = f"{h['sum_ccn'] - o['sum_ccn']:+d}" if o else "new"
    dn = f"{h['nloc'] - o['nloc']:+d}" if o else "new"
    rows.append((short(h["path"]), rev_before[h["path"]], f"{rev_before[h['path']]/DAYS_BEFORE*7:.1f}", rev_after[h["path"]],
                 f"{rev_after[h['path']]/DAYS_AFTER*7:.1f}", f"{rel(h['path']):.2f}", o["sum_ccn"] if o else "-", h["sum_ccn"], d, dn, label(h)))
table(["file", "rev before (30d)", "/wk", "rev after", "/wk", "rel share", "CCN then", "CCN now", "dCCN", "dNLOC", "label"], rows)
# last-assessment hotspots specifically
named = ["PackPolicy", "PackCompositionResolver", "FilesystemPackStore", "FileSystemPackInspector", "WholePackStore", "Pep440Range"]
md.append("### The 2026-11-12 assessment's named hotspots\n")
rows = []
for n in named:
    then = [r for r in CX0 if os.path.basename(r["path"]) == n + ".java" and r["srcset"] == "main"]
    now = [r for r in main.values() if os.path.basename(r["path"]) == n + ".java"]
    for t in then:
        np = old2new.get(t["path"], t["path"])
        nw = CX.get(np)
        rows.append((n, t["nloc"], t["sum_ccn"], t["max_ccn"], nw["nloc"] if nw else "gone", nw["sum_ccn"] if nw else "-",
                     nw["max_ccn"] if nw else "-", rev_before[np], rev_after[np], np if nw else "(deleted/renamed)"))
table(["file", "NLOC then", "CCN then", "max then", "NLOC now", "CCN now", "max now", "rev before", "rev after", "path now"], rows)
# module totals then/now
agg0, agg1 = collections.Counter(), collections.Counter()
for r in CX0:
    if r["srcset"] == "main": agg0[r["module"]] += r["sum_ccn"]
for r in main.values(): agg1[r["module"]] += r["sum_ccn"]
md.append("### Main-code CCN by module, then vs now\n")
table(["module", f"CCN {BASE8}", f"CCN {REV8}", "delta"], [(m, agg0[m], agg1[m], f"{agg1[m]-agg0[m]:+d}") for m in sorted(set(agg0) | set(agg1), key=lambda m: -agg1[m])])

# ---------- 5. team / streams ----------
ISSUE = re.compile(r"#(\d+)")
SCOPE = re.compile(r"^[a-zA-Z]+\(([^)]*)\)")
MISSION = re.compile(r"([a-z0-9-]+-01M[0-9A-Z]{5,7})")
def streams(c):
    s = c["subj"]
    iss = set(ISSUE.findall(s))
    sc = SCOPE.match(s)
    m = MISSION.findall(s)
    return iss, (sc.group(1) if sc else ""), set(m)
md.append("## Streams touching the top 15 hotspots\n")
rows = []
for h in hs[:15]:
    cs = touch[h["path"]]
    iss, scopes, missions, authors = collections.Counter(), collections.Counter(), set(), collections.Counter()
    noref = 0
    for c in cs:
        i, sc, m = streams(c)
        for x in sorted(i, key=int): iss[x] += 1
        scopes[sc] += 1; missions |= m; authors[c["author"].split()[0]] += 1
        if not i and not m: noref += 1
    top = max(iss.values()) if iss else 0
    rows.append((short(h["path"]), len(cs), len(iss), ", ".join(f"#{k}({v})" for k, v in iss.most_common(4)),
                 ", ".join(f"{k}({v})" for k, v in scopes.most_common(3)), noref,
                 f"{100*top/len(cs):.0f}%" if cs else "-", ", ".join(f"{k}:{v}" for k, v in authors.items())))
table(["file", "revs", "distinct issues", "top issues", "top scopes", "revs w/o issue/mission ref", "top-issue share", "authors"], rows)
# overall attribution
att = collections.Counter()
for c in nb:
    i, sc, m = streams(c)
    att["with issue ref"] += bool(i); att["with mission slug"] += bool(m); att["neither"] += (not i and not m)
    att["scope=landing"] += sc == "landing"
md.append("Attribution of the analysed commits: " + ", ".join(f"{k}: {v}" for k, v in att.items()) + f" (of {len(nb)}).\n")
authors = collections.Counter(c["author"] for c in C)
md.append("Authors (all commits): " + ", ".join(f"{k}: {v}" for k, v in authors.most_common()) + "\n")

# squash-hidden history: main files whose every non-bookkeeping revision is a squash merge or a >MAXCS sweep
md.append("## History hidden by squash merges and sweeps (main Java files at REV)\n")
rows = []
for m in sorted({h["module"] for h in hs}):
    fs = [h for h in hs if h["module"] == m]
    sq_only = [h for h in fs if touch[h["path"]] and all(c["squash"] for c in touch[h["path"]])]
    big_only = [h for h in fs if touch[h["path"]] and all(c["n_src_files"] > MAXCS for c in touch[h["path"]])]
    rows.append((m, len(fs), len(sq_only), len(big_only), f"{100*len(big_only)/len(fs):.0f}%"))
table(["module", "main files", "only squash-merge revisions", f"only >{MAXCS}-file revisions", "share"], rows)
md.append("## lizard caveats at REV (main)\n")
md.append(f"Wildcard '?' tokens rewritten before measuring: {sum(h['wildcards'] for h in hs)}; raw lizard sum CCN "
          f"{sum(h['raw_sum_ccn'] for h in hs)} vs corrected {sum(h['sum_ccn'] for h in hs)}. Files with a record body lizard lumps "
          f"into one pseudo-function: {sum(1 for h in hs if h['record_lumped'])} ({sum(h['record_lumped_nloc'] for h in hs)} NLOC, "
          f"{sum(h['record_lumped_ccn'] for h in hs)} CCN). Functions (excl. lumped) with CCN > 10: {sum(h['fns_ccn_gt10'] for h in hs)}; "
          f"> 15 (the Checkstyle limit): {sum(h['fns_ccn_gt15'] for h in hs)}; of {sum(h['functions'] for h in hs)} functions.\n")

# ---------- 6. LOC by module ----------
loc = collections.defaultdict(lambda: collections.Counter())
for r in CX.values():
    k = "main" if r["srcset"] == "main" else "test"
    loc[r["module"]][k + "_files"] += 1; loc[r["module"]][k + "_lines"] += r["lines"]; loc[r["module"]][k + "_nloc"] += r["nloc"]
md.append(f"## Java LOC by module at {REV8} (main = src/main; test = src/test + testFixtures + driftTest)\n")
rows = [(m, v["main_files"], v["main_lines"], v["main_nloc"], v["test_files"], v["test_lines"], v["test_nloc"],
         f"{v['test_nloc']/v['main_nloc']:.2f}" if v["main_nloc"] else "-") for m, v in sorted(loc.items(), key=lambda kv: -kv[1]["main_nloc"])]
T = [sum(r[i] for r in rows) for i in range(1, 7)]
rows.append(("**total**", *T, f"{T[5]/T[2]:.2f}"))
table(["module", "main files", "main lines", "main NLOC", "test files", "test lines", "test NLOC", "test/main NLOC"], rows)
md.append("## Largest main files (physical lines)\n")
table(["module", "file", "lines", "NLOC", "fns", "sum CCN", "max CCN", "fns CCN>10"],
      [(r["module"], short(r["path"]), r["lines"], r["nloc"], r["functions"], r["sum_ccn"], r["max_ccn"], r["fns_ccn_gt10"])
       for r in sorted(main.values(), key=lambda r: -r["lines"])[:15]])
md.append("## Most complex functions (main)\n")
fn_rows = sorted(main.values(), key=lambda r: -r["max_ccn"])[:12]
table(["module", "file", "function:line", "CCN"], [(r["module"], short(r["path"]), f"{r['max_fn']}:{r['max_fn_line']}", r["max_ccn"]) for r in fn_rows])

open(os.path.join(OUT, "tables.md"), "w").write("\n".join(md))
json.dump(hs, open(os.path.join(OUT, "hotspots.json"), "w"), indent=0)
print("\n".join(md))
