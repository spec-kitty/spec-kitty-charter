#!/usr/bin/env python3
"""Self-test of verify_migration.py: plants one defect of each class it checks and asserts each is reported.

Makes two throwaway git worktrees, one of the pinned commit and one of the migrated commit, plants the defects in
their working trees, runs the verifier and checks its exit code and that every planted defect shows in its report.

usage: self_test.py <migrated open-packs checkout> <pinned commit>
"""
import os
import subprocess
import sys
import tempfile

NEW_REPO, PIN = sys.argv[1], sys.argv[2]
VERIFIER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "verify_migration.py")


def edit(path, old, new):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    assert old in text, (path, old)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text.replace(old, new, 1))


# (what is planted, a text its report line contains)
EXPECTED = [
    ("a mapped field changed", "qa/qa.title: expected"),
    ("a step title changed", "built-in/plan.steps: expected"),
    ("a prompt byte changed", "built-in/research/scoping: prompt is not byte-equal"),
    ("a guidelines byte changed", "guidelines are not byte-equal"),
    ("a stray file added", "added a file the migration does not name: packs/qa/missions/qa/notes.md"),
    ("a non-retired file deleted", "deleted a file no retired family names: packs/qa/README.md"),
    ("an extra line in a fragment", "packs/qa/drg/fragment.yaml: adds a line the migration does not name"),
    ("a repeat that differs", "governance profile id: repeat 'research-x' differs"),
    ("an unaccounted pinned key", "pinned key 'owner' (step.yaml) is neither mapped nor a listed drop"),
    ("a step description over the bound", "description is longer than 160 characters"),
    ("a runtime key that differs", "runtime mission.key: repeat 'plan-x' differs"),
    ("a nested plan step that differs", "runtime mission.steps: repeat"),
    ("an unaccounted runtime-block key", "pinned key 'parallel' (runtime block) is neither mapped nor a listed drop"),
    ("a template_path naming no file", "template_path templates/test-strategy-template.md names no file"),
]

with tempfile.TemporaryDirectory() as tmp:
    old, new = os.path.join(tmp, "old"), os.path.join(tmp, "new")
    subprocess.run(["git", "-C", NEW_REPO, "worktree", "add", "-q", "--detach", old, PIN], check=True)
    subprocess.run(["git", "-C", NEW_REPO, "worktree", "add", "-q", "--detach", new, "HEAD"], check=True)
    try:
        qa = f"{new}/packs/qa/missions/qa/qa.mission-type.yaml"
        edit(qa, "title: QA", "title: Quality")
        edit(f"{new}/packs/built-in/missions/plan/plan.mission-type.yaml", "title: Specify", "title: Specified")
        with open(f"{new}/packs/built-in/missions/research/steps/scoping.prompt.md", "a", encoding="utf-8") as f:
            f.write(" ")
        guidelines = sorted(
            os.path.join(r, n) for r, _, ns in os.walk(f"{new}/packs") for n in ns if n.endswith(".guidelines.md"))
        with open(guidelines[0], "a", encoding="utf-8") as f:
            f.write("x")
        with open(f"{new}/packs/qa/missions/qa/notes.md", "w", encoding="utf-8") as f:
            f.write("# Notes\n")
        os.remove(f"{new}/packs/qa/README.md")
        with open(f"{new}/packs/qa/drg/fragment.yaml", "a", encoding="utf-8") as f:
            f.write("# planted\n")
        edit(f"{old}/packs/built-in/missions/research/governance-profile.yaml", "\nid: research\n", "\nid: research-x\n")
        edit(f"{old}/packs/built-in/missions/mission-steps/research/scoping/step.yaml", "id: scoping\n",
             "id: scoping\nowner: planted\n")
        edit(f"{new}/packs/built-in/missions/research/research.mission-type.yaml",
             "description: Define the research question", "description: " + "x" * 161 + " Define the research question")

        runtime = f"{old}/packs/built-in/missions/plan/mission-runtime.yaml"
        edit(runtime, "  key: plan\n", "  key: plan-x\n")
        edit(runtime, "      name: Specify\n", "      name: Specified\n")
        edit(runtime, "    terminal_step: review\n", "    terminal_step: review\n    parallel: false\n")
        os.remove(f"{new}/packs/qa/missions/qa/templates/test-strategy-template.md")

        run = subprocess.run([sys.executable, "-I", VERIFIER, old, new], capture_output=True, text=True)
        print(run.stdout.strip())
        missed = [what for what, text in EXPECTED if text not in run.stdout]
        print(f"verifier exit code: {run.returncode} (expected 1)")
        print(f"planted: {len(EXPECTED)}, detected: {len(EXPECTED) - len(missed)}")
        for what in missed:
            print(f"MISSED: {what}")
    finally:
        for wt in (old, new):
            subprocess.run(["git", "-C", NEW_REPO, "worktree", "remove", "--force", wt], check=False)
    sys.exit(0 if run.returncode == 1 and not missed else 1)
