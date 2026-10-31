#!/usr/bin/env python3
"""Independent verifier of the #194 PR-B mission-tier migration (ADR-0044).

Shares no code with the Java harness: it re-derives each migrated mission type from the pinned (old) files by
ADR-0044's tables, reading raw YAML, and compares it field by field with the migrated (new) file. It also checks
counts, that every reference id names an artifact in the packs, that every pinned key maps to a target field or a
listed drop (and that each value dropped as a repeat equals the value kept), that only retired files were deleted, that
every prompt and guidelines file is byte-equal to its pinned source (a prompt keeps its frontmatter description as its
detailed description: ruling Q1 as refined on 2026-10-30), that step descriptions are at most 160 characters, and that
every added or modified file is one the migration names, with only the change it names.

usage: verify_migration.py <old open-packs checkout> <new open-packs checkout>
"""
import fnmatch
import glob
import os
import re
import subprocess
import sys

import yaml

OLD, NEW = sys.argv[1], sys.argv[2]
PACKS = ["built-in", "cave-kitty", "qa"]
RETIRED = [
    "*/**/mission_types/*.yaml", "*/mission_types/*.yaml", "*/**/mission_types/*/governance-profile.yaml",
    "*/mission_types/*/governance-profile.yaml", "*/missions/*/mission.yaml", "*/missions/*/mission-runtime.yaml",
    "*/missions/*/governance-profile.yaml", "*/missions/*/expected-artifacts.yaml", "*/missions/*/actions/*/index.yaml",
    "*/**/mission-steps/**", "*/mission-steps/**", "*/missions/*/command-templates/*",
]
LISTS = ["directives", "tactics", "paradigms", "styleguides", "toolguides", "procedures"]
DESCRIPTION_MAX = 160
# Action indexes that belong to no step and are not carried (spec-kitty/spec-kitty-open-packs#98).
KNOWN_UNCARRIED = {"built-in/software-dev/retrospect"}
dropped_titles, frontmatter_only, uncarried = [], [], []
problems = []

# Every pinned key, by source file: "map" (carried to a target field), "repeat" (dropped once checked equal to the
# value kept) or "drop" (dropped, ADR-0044 §3 and divergence.md §4). A key in none fails the check.
SELECTED = {f"selected_{k}": "map" for k in LISTS}
KEY_TABLE = {
    "mission_type": {"schema_version": "drop", "id": "repeat", "display_name": "map", "requires_substantive_spec": "map"},
    "mission.yaml": {"name": "drop", "description": "map", "version": "drop", "domain": "drop", "workflow": "drop",
                     "artifacts": "drop", "paths": "map", "validation": "drop", "mcp_tools": "drop",
                     "agent_context": "map", "task_metadata": "drop", "commands": "drop", "mission": "drop",
                     "initial": "drop", "states": "drop", "transitions": "drop"},
    "mission-runtime.yaml": {"mission": "repeat", "steps": "map"},
    "runtime mission": {"key": "repeat", "name": "drop", "title": "drop", "version": "drop", "description": "map",
                        "steps": "repeat", "runtime": "repeat"},
    "runtime block": {"loop_type": "repeat", "terminal_step": "repeat", "step_transition": "drop",
                      "prompt_template_dir": "drop"},
    "runtime step": {"id": "map", "title": "map", "description": "map", "depends_on": "drop",
                     "prompt_template": "map", "agent-profile": "map"},
    "governance-profile.yaml": {"id": "repeat", "mission_type": "repeat", "template_set": "drop",
                                "selected_agent_profiles": "drop", "selected_mission_step_contracts": "drop",
                                "available_tools": "drop", "activations": "drop", **SELECTED},
    "expected-artifacts.yaml": {"schema_version": "drop", "mission_type": "repeat", "manifest_version": "drop",
                                "required_always": "map", "optional_always": "map", "required_by_step": "map"},
    "expected artifact": {"artifact_key": "map", "artifact_class": "map", "path_pattern": "map", "blocking": "map"},
    "action index": {"action": "repeat", "agent_profiles": "map", **{k: "map" for k in LISTS}},
    "step.yaml": {"id": "repeat", "display_name": "map", "step_type": "drop", "prompt_template": "drop",
                  "agent_profile": "map", "guidance": "drop", "delegates_to": "drop", "depends_on": "drop",
                  "sequence_index": "drop", "in_action_sequence": "drop", "template": "map"},
    "step template": {"artifact_key": "map", "template_file": "map"},
    "prompt frontmatter": {"description": "map"},
}


def y(path):
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


def frontmatter_description(path):
    text = open(path, encoding="utf-8").read()
    if not text.startswith("---\n"):
        return None
    fm = yaml.safe_load(text[4:text.index("\n---\n", 4)])
    return fm.get("description")


def covered(where, table, mapping):
    """Every key of a pinned mapping is carried, dropped as a repeat, or dropped: never unaccounted for."""
    for key in mapping or {}:
        if key not in KEY_TABLE[table]:
            problems.append(f"{where}: pinned key {key!r} ({table}) is neither mapped nor a listed drop")


def same(where, kept, repeated):
    if kept != repeated:
        problems.append(f"{where}: repeat {repeated!r} differs from the value kept {kept!r}")


def roles():
    out = {}
    for f in glob.glob(f"{OLD}/packs/**/*.agent.yaml", recursive=True):
        d = y(f)
        if d.get("profile-id") and d.get("roles"):
            out[d["profile-id"]] = d["roles"][0]
    return out


ROLES = roles()


def ref(kind, ident):
    m = re.fullmatch(r"(\d{3})-[a-z0-9-]+", ident)
    if kind == "directive" and m:
        ident = "DIRECTIVE_" + m.group(1)
    return {"type": kind, "id": ident}


def artifact(e, template=None):
    out = {"artifact_key": e["artifact_key"], "artifact_class": e["artifact_class"], "path_pattern": e["path_pattern"]}
    if e.get("blocking") is True:
        out["blocking"] = True
    if template:
        out["template_path"] = template
    return out


def expected_type(pack, type_file):
    tid = os.path.basename(type_file)[:-5]
    base = f"{OLD}/packs/{pack}"
    mt = y(type_file)
    mdir = f"{base}/missions/{tid}"
    mission, runtime, ea = y(f"{mdir}/mission.yaml"), y(f"{mdir}/mission-runtime.yaml"), y(f"{mdir}/expected-artifacts.yaml")
    gp_path = f"{mdir}/governance-profile.yaml"
    if not os.path.exists(gp_path):
        gp_path = os.path.join(os.path.dirname(type_file), tid, "governance-profile.yaml")
    gp = y(gp_path)
    where = f"{pack}/{tid}"
    covered(f"{where} type file", "mission_type", mt)
    same(f"{where} type file id", tid, mt.get("id"))
    covered(f"{where} mission.yaml", "mission.yaml", mission)
    covered(f"{where} mission-runtime.yaml", "mission-runtime.yaml", runtime)
    head = runtime["mission"]
    covered(f"{where} runtime mission", "runtime mission", head)
    same(f"{where} runtime mission.key", tid, head.get("key"))
    if "steps" in head:
        same(f"{where} runtime mission.steps",
             [{"id": r["id"], "name": r["title"], "description": r["description"], "order": i + 1,
               "depends_on": r.get("depends_on")} for i, r in enumerate(runtime["steps"])], head["steps"])
    if "runtime" in head:
        covered(f"{where} runtime block", "runtime block", head["runtime"])
        same(f"{where} runtime block", {"loop_type": "sequential", "terminal_step": runtime["steps"][-1]["id"]},
             {k: head["runtime"].get(k) for k in ("loop_type", "terminal_step")})
    for r in runtime["steps"]:
        covered(f"{where} runtime step {r['id']}", "runtime step", r)
    covered(f"{where} governance profile", "governance-profile.yaml", gp)
    same(f"{where} governance profile id", tid, gp.get("id"))
    same(f"{where} governance profile mission_type", tid, gp.get("mission_type"))
    covered(f"{where} expected-artifacts.yaml", "expected-artifacts.yaml", ea)
    same(f"{where} expected-artifacts mission_type", tid, ea.get("mission_type"))
    if ea.get("required_always"):
        problems.append(f"{where}: required_always is not empty and has no target")
    for entries in list((ea.get("required_by_step") or {}).values()) + [ea.get("optional_always") or []]:
        for e in entries or []:
            covered(f"{where} expected artifact {e.get('artifact_key')}", "expected artifact", e)
    steps_root = next((p for p in [f"{base}/missions/mission-steps/{tid}", f"{base}/mission-steps/{tid}"] if os.path.isdir(p)), None)
    by_step = {k: (v or []) for k, v in (ea.get("required_by_step") or {}).items()}
    type_outputs = [artifact(e) for e in ea.get("optional_always") or []]
    used = set()

    def step_dir(sid, prompt_template):
        if not steps_root:
            return None
        if os.path.isdir(f"{steps_root}/{sid}"):
            return sid
        if prompt_template and os.path.isdir(f"{steps_root}/{prompt_template[:-3]}"):
            return prompt_template[:-3]
        return None

    def prompt_file(sdir, command):
        if sdir and os.path.exists(f"{steps_root}/{sdir}/prompt.md"):
            return f"{steps_root}/{sdir}/prompt.md"
        c = f"{mdir}/command-templates/{command}"
        return c if os.path.exists(c) else None

    def build(sid, title, desc, profile, sdir, pfile):
        idx_path = f"{mdir}/actions/{sid}/index.yaml"
        idx = y(idx_path) if os.path.exists(idx_path) else {}
        if idx:
            covered(f"{where} action index {sid}", "action index", idx)
            same(f"{where} action index {sid} action", sid, idx.get("action"))
        if sdir:
            sy_own = y(f"{steps_root}/{sdir}/step.yaml")
            covered(f"{where} step.yaml {sdir}", "step.yaml", sy_own)
            same(f"{where} step.yaml {sdir} id", sdir, sy_own.get("id"))
            if sy_own.get("template"):
                covered(f"{where} step.yaml {sdir} template", "step template", sy_own["template"])
        if pfile and open(pfile, encoding="utf-8").read().startswith("---\n"):
            text = open(pfile, encoding="utf-8").read()
            covered(f"{where} prompt {sid}", "prompt frontmatter", yaml.safe_load(text[4:text.index("\n---\n", 4)]))
        if len(idx.get("agent_profiles") or []) > 1:
            problems.append(f"{where} action index {sid}: names more than one agent profile")
        if not profile and idx.get("agent_profiles"):
            profile = idx["agent_profiles"][0]
        s = {"id": sid, "title": title.strip()}
        if desc:
            s["description"] = desc.strip()
        if profile:
            s["role"] = ROLES[profile]
        has_prompt = pfile and open(pfile, encoding="utf-8").read().strip()
        if has_prompt:
            s["prompt_path"] = f"steps/{sid}.prompt.md"
        if sdir and os.path.exists(f"{steps_root}/{sdir}/guidelines.md"):
            s["guidelines_path"] = f"steps/{sid}.guidelines.md"
        outs = [artifact(e) for e in by_step.pop(sid, by_step.pop(sid.replace("-", "_"), []))]
        sy = y(f"{steps_root}/{sdir}/step.yaml") if sdir and os.path.exists(f"{steps_root}/{sdir}/step.yaml") else {}
        t = sy.get("template")
        if t:
            match = [o for o in outs if o["artifact_key"].split(".")[1] == t["artifact_key"]]
            if not match:
                moved = [o for o in type_outputs if o["artifact_key"].split(".")[1] == t["artifact_key"]]
                type_outputs.remove(moved[0])
                outs.append(moved[0])
                match = moved
            match[0]["template_path"] = "templates/" + t["template_file"]
        if outs:
            s["expected_output_artifacts"] = outs
        refs = [ref(k[:-1], i) for k in idx if k in LISTS for i in idx[k] or []]
        if refs:
            s["references"] = refs
        return s, (pfile if has_prompt else None)

    steps, prompts = [], {}
    for r in runtime["steps"]:
        sid = r["id"]
        sdir = step_dir(sid, r.get("prompt_template"))
        used.add(sdir)
        sy = y(f"{steps_root}/{sdir}/step.yaml") if sdir else {}
        pfile = prompt_file(sdir, r.get("prompt_template") or f"{sid}.md")
        desc = r.get("description") or (pfile and frontmatter_description(pfile))
        if r.get("agent-profile") and sy.get("agent_profile"):
            same(f"{where} step {sid} agent profile", r["agent-profile"], sy["agent_profile"])
        if r.get("title") and sy.get("display_name") and r["title"].strip() != sy["display_name"].strip():
            dropped_titles.append(f"{where}/{sid}")
        if not r.get("description") and desc:
            frontmatter_only.append(f"{where}/{sid}")
        s, p = build(sid, r.get("title") or sy["display_name"], desc, r.get("agent-profile") or sy.get("agent_profile"), sdir, pfile)
        steps.append(s)
        prompts[sid] = (p, sdir)
    on_demand = []
    for sdir in sorted(os.listdir(steps_root)) if steps_root else []:
        if sdir in used:
            continue
        sy = y(f"{steps_root}/{sdir}/step.yaml")
        pfile = prompt_file(sdir, f"{sdir}.md")
        desc = pfile and frontmatter_description(pfile)
        if desc:
            frontmatter_only.append(f"{where}/{sdir}")
        s, p = build(sdir, sy["display_name"], desc, sy.get("agent_profile"), sdir, pfile)
        on_demand.append(s)
        prompts[sdir] = (p, sdir)
    for idx_path in sorted(glob.glob(f"{mdir}/actions/*/index.yaml")):
        action = os.path.basename(os.path.dirname(idx_path))
        if action not in [x["id"] for x in steps + on_demand]:
            covered(f"{where} action index {action}", "action index", y(idx_path))
            if f"{where}/{action}" not in KNOWN_UNCARRIED:
                problems.append(f"{where}: action index {action} belongs to no step and is not a known uncarried one")
            uncarried.append(f"{where}/{action}")
    if any(by_step.values()):
        problems.append(f"{pack}/{tid}: unmapped required_by_step {[k for k, v in by_step.items() if v]}")
    out = {"schema_version": "1.0", "id": tid, "title": mt["display_name"],
           "description": (mission.get("description") or runtime["mission"]["description"]).strip()}
    if mission.get("agent_context"):
        out["content"] = mission["agent_context"].strip()
    if mt.get("requires_substantive_spec") is True:
        out["requires_substantive_spec"] = True
    if mission.get("paths"):
        out["path_conventions"] = mission["paths"]
    if type_outputs:
        out["expected_output_artifacts"] = type_outputs
    trefs = [ref(k[9:-1], i) for k in gp if k.startswith("selected_") and k[9:] in LISTS for i in gp[k] or []]
    if trefs:
        out["references"] = trefs
    out["steps"] = steps
    if on_demand:
        out["on_demand_steps"] = on_demand
    return tid, out, prompts, steps_root


def type_files(root, pack):
    return sorted(glob.glob(f"{root}/packs/{pack}/missions/mission_types/*.yaml") + glob.glob(f"{root}/packs/{pack}/mission_types/*.yaml"))


def known_ids():
    ids = {}
    for f in glob.glob(f"{NEW}/packs/**/*.yaml", recursive=True):
        m = re.search(r"\.(directive|tactic|paradigm|styleguide|toolguide|procedure)\.yaml$", f)
        if m:
            d = y(f)
            ids.setdefault(m.group(1), set()).add(d.get("id"))
    return ids


IDS = known_ids()
counts = {"types": 0, "steps": 0, "on_demand": 0, "prompts": 0, "guidelines": 0}
for pack in PACKS:
    for tf in type_files(OLD, pack):
        tid, want, prompts, steps_root = expected_type(pack, tf)
        path = f"{NEW}/packs/{pack}/missions/{tid}/{tid}.mission-type.yaml"
        if not os.path.exists(path):
            problems.append(f"missing {path}")
            continue
        got = y(path)
        counts["types"] += 1
        tdir = os.path.dirname(path)
        for o in got.get("expected_output_artifacts", []) + [
                o for s in got["steps"] + got.get("on_demand_steps", []) for o in s.get("expected_output_artifacts", [])]:
            if "template_path" in o and not os.path.isfile(f"{tdir}/{o['template_path']}"):
                problems.append(f"{pack}/{tid}: template_path {o['template_path']} names no file")
            counts["template_paths"] = counts.get("template_paths", 0) + ("template_path" in o)
        counts["steps"] += len(got["steps"])
        counts["on_demand"] += len(got.get("on_demand_steps", []))
        if got != want:
            for k in sorted(set(want) | set(got)):
                if want.get(k) != got.get(k):
                    problems.append(f"{pack}/{tid}.{k}: expected {want.get(k)!r}, got {got.get(k)!r}")
        for s in got["steps"] + got.get("on_demand_steps", []):
            if len(s.get("description", "")) > DESCRIPTION_MAX:
                problems.append(f"{pack}/{tid}/{s['id']}: description is longer than {DESCRIPTION_MAX} characters")
            for r in s.get("references", []):
                if r["id"] not in IDS.get(r["type"], set()):
                    problems.append(f"{pack}/{tid}/{s['id']}: reference {r} names no artifact")
            pfile, sdir = prompts[s["id"]]
            if "prompt_path" in s:
                counts["prompts"] += 1
                new = f"{NEW}/packs/{pack}/missions/{tid}/{s['prompt_path']}"
                if open(new, "rb").read() != open(pfile, "rb").read():
                    problems.append(f"{pack}/{tid}/{s['id']}: prompt is not byte-equal to {pfile}")
            if "guidelines_path" in s:
                counts["guidelines"] += 1
                new = f"{NEW}/packs/{pack}/missions/{tid}/{s['guidelines_path']}"
                if open(new, "rb").read() != open(f"{steps_root}/{sdir}/guidelines.md", "rb").read():
                    problems.append(f"{pack}/{tid}/{s['id']}: guidelines are not byte-equal to the pinned file")
        for r in got.get("references", []):
            if r["id"] not in IDS.get(r["type"], set()):
                problems.append(f"{pack}/{tid}: reference {r} names no artifact")

# What the migration changed: the pinned commit against the migrated tree (committed or not).
pin = subprocess.run(["git", "-C", OLD, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
diff = subprocess.run(["git", "-C", NEW, "diff", "--no-renames", "--name-status", pin], capture_output=True, text=True, check=True).stdout
deleted = [l.split("\t", 1)[1] for l in diff.splitlines() if l.startswith("D\t")]
modified = [l.split("\t", 1)[1] for l in diff.splitlines() if l.startswith("M\t")]
added = [l.split("\t", 1)[1] for l in diff.splitlines() if l.startswith("A\t")]
# A file not yet added to the index is added all the same.
added += subprocess.run(["git", "-C", NEW, "ls-files", "--others", "--exclude-standard"], capture_output=True,
                        text=True, check=True).stdout.split()
others = [l for l in diff.splitlines() if l[:1] not in ("A", "D", "M")]
if others:
    problems.append(f"changes neither added, deleted nor modified: {others}")
ADDED = [r"packs/[a-z-]+/missions/([a-z][a-z0-9-]*)/\1\.mission-type\.yaml",
         r"packs/[a-z-]+/missions/[a-z][a-z0-9-]*/steps/[a-z][a-z0-9-]*\.(prompt|guidelines)\.md"]
for a in added:
    if not any(re.fullmatch(p, a) for p in ADDED):
        problems.append(f"added a file the migration does not name: {a}")
counts["added"] = len(added)
counts["added_companions"] = len([a for a in added if a.endswith(".md")])


def changed_lines(path):
    """The removed and added lines of one modified file, against the pin."""
    out = subprocess.run(["git", "-C", NEW, "diff", "-U0", pin, "--", path], capture_output=True, text=True,
                         check=True).stdout
    lines = [l for l in out.splitlines() if l[:1] in "+-" and not l.startswith(("+++", "---"))]
    return [l for l in lines if l.startswith("-")], [l for l in lines if l.startswith("+")]


EXPECTED_EDITS = {
    # path: (a regex every removed line matches, a regex every added line matches)
    "packs/built-in.source.yaml": (r"-  value: [0-9a-f]{64}", r"\+  value: [0-9a-f]{64}"),
    "packs/qa/drg/fragment.yaml": (r"-\s+body_path: mission_types/qa/governance-profile\.yaml",
                                   r"\+\s+body_path: missions/qa/qa\.mission-type\.yaml"),
    "packs/cave-kitty/drg/fragment.yaml": (r"-\s+body_path: mission_types/cave-dev/governance-profile\.yaml",
                                           r"\+\s+body_path: missions/cave-dev/cave-dev\.mission-type\.yaml"),
    "scripts/ci/check_vocabulary.py": (r'-\s+(\(|\),|"packs/built-in/missions/documentation/governance-profile\.yaml",|"common-docs-curation",?|"common-docs-curation was folded into",)', r"(?!)"),
    "tests/vocabulary/test_check_vocabulary.py": (r"-\s+rel, stem, phrase = vocab\.PROVENANCE_PHRASES\[2\]",
                                                  r'\+\s+rel, stem, phrase = next\(row for row in vocab\.PROVENANCE_PHRASES if row\[0\] == "packs/qa/PROVENANCE\.md"\)'),
}
for d in deleted:
    rel = d[len("packs/"):]
    if not any(fnmatch.fnmatch(rel, g) for g in RETIRED):
        problems.append(f"deleted a file no retired family names: {d}")
for m in modified:
    if m.startswith("docs/packs/") and m.endswith(".md"):
        continue  # generated: checked below by the generator's own --check
    if m not in EXPECTED_EDITS:
        problems.append(f"modified a file the migration should not touch: {m}")
        continue
    removed, plus = changed_lines(m)
    gone, came = EXPECTED_EDITS[m]
    if not removed and not plus:
        problems.append(f"{m}: listed as modified but has no changed line")
    for line in removed:
        if not re.fullmatch(gone, line):
            problems.append(f"{m}: removes a line the migration does not name: {line!r}")
    for line in plus:
        if not re.fullmatch(came, line):
            problems.append(f"{m}: adds a line the migration does not name: {line!r}")
if any(m.startswith("docs/packs/") for m in modified):
    check = subprocess.run([sys.executable, "scripts/docs/gen_pack_reference.py", "--check"], cwd=NEW,
                           capture_output=True, text=True)
    if check.returncode != 0:
        problems.append(f"docs/packs/ is not what the generator writes: {check.stdout.strip()} {check.stderr.strip()}")

counts["retired_deleted"] = len(deleted)
counts["dropped_titles"] = len(dropped_titles)
counts["frontmatter_only_descriptions"] = len(frontmatter_only)
counts["uncarried_action_indexes"] = len(uncarried)
print("counts:", counts)
expected = {"types": 6, "steps": 40, "on_demand": 6, "prompts": 43, "guidelines": 17, "retired_deleted": 153,
            "template_paths": 13, "dropped_titles": 17, "frontmatter_only_descriptions": 4, "uncarried_action_indexes": 1,
            "added": 66, "added_companions": 60}
for k, v in expected.items():
    if counts[k] != v:
        problems.append(f"count {k}: expected {v}, got {counts[k]}")
print("problems:", len(problems))
for p in problems:
    print(" -", p)
sys.exit(1 if problems else 0)
