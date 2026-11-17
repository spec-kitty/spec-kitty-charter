"""The workflows' change detectors still see a match on a changed-file list longer than the pipe buffer (#579).

A detector that runs ``printf '%s\\n' "$changed" | grep -Eq "$re"`` under ``set -o pipefail`` fails open: ``grep -q``
exits on its first match, ``printf`` then dies of SIGPIPE, the pipeline returns non-zero and the match reads as "no
match", so the gate is skipped while CI goes green. These tests run each detector step's real ``run:`` script, taken
from the workflow file, against a stub ``git`` that lists 20,000 changed paths with the matching one first, and
assert the step writes ``true``.

Standard library only (unittest), so it runs with either runner from the repository root::

    python3 -m unittest discover -s scripts/ci -p 'test_*.py'
    python3 -m pytest -p no:cacheprovider scripts/ci -q
"""

from __future__ import annotations

import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

WORKFLOWS = Path(__file__).resolve().parents[2] / ".github" / "workflows"

# Far more than a pipe buffer (64 KiB on Linux): about 1 MiB of paths after the match.
FILLER_PATHS = 20_000


def run_script(workflow: str, step_id: str) -> str:
    """The ``run: |`` block of the step with ``id: <step_id>``, dedented, as bash will see it."""
    lines = (WORKFLOWS / workflow).read_text(encoding="utf-8").splitlines()
    id_line = next(i for i, line in enumerate(lines) if re.fullmatch(rf"\s+id: {re.escape(step_id)}", line))
    step_indent = len(lines[id_line]) - len(lines[id_line].lstrip())
    run_line = next(
        i for i in range(id_line + 1, len(lines)) if re.fullmatch(rf"\s{{{step_indent}}}run: \|", lines[i])
    )
    body: list[str] = []
    for line in lines[run_line + 1:]:
        if line.strip() and len(line) - len(line.lstrip()) <= step_indent:
            break
        body.append(line)
    indent = min(len(line) - len(line.lstrip()) for line in body if line.strip())
    script = "\n".join(line[indent:] for line in body) + "\n"
    # The only expression the detectors read inline; every other input comes through env.
    return script.replace("${{ github.event_name }}", "pull_request")


class ChangeDetectorTest(unittest.TestCase):
    def run_detector(self, workflow: str, step_id: str, matching_path: str) -> dict[str, str]:
        """Runs the step with ``git diff`` listing ``matching_path`` first, then filler that matches nothing."""
        script = run_script(workflow, step_id)
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            listing = tmp_path / "changed.txt"
            filler = (f"zz-unmatched/filler/path-{n:05d}.bin" for n in range(FILLER_PATHS))
            listing.write_text("\n".join([matching_path, *filler]) + "\n", encoding="utf-8")
            stub = tmp_path / "bin" / "git"
            stub.parent.mkdir()
            stub.write_text(f'#!/usr/bin/env bash\ncat "{listing}"\n', encoding="utf-8")
            stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
            output = tmp_path / "github_output"
            output.touch()
            env = {
                **os.environ,
                "PATH": f"{stub.parent}{os.pathsep}{os.environ['PATH']}",
                "BASE": "0" * 40,
                "HEAD": "1" * 40,
                "GITHUB_OUTPUT": str(output),
                "GITHUB_EVENT_NAME": "pull_request",
            }
            result = subprocess.run(
                ["bash", "-c", script], env=env, cwd=tmp, capture_output=True, text=True, check=False
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            return dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines() if "=" in line)

    def test_ci_gate_selects_each_group_from_a_long_list(self) -> None:
        for group, path in {
            "service": "service/kernel/src/Main.java",
            "docs": "docs/index.md",
            "wholepack": "contracts/charter.yaml",
        }.items():
            with self.subTest(group=group):
                self.assertEqual(self.run_detector("ci.yml", "detect", path).get(group), "true")

    def test_ci_gate_selects_no_group_when_nothing_matches(self) -> None:
        outputs = self.run_detector("ci.yml", "detect", "zz-unmatched/first.bin")
        self.assertEqual(outputs, {"service": "false", "docs": "false", "wholepack": "false"})

    def test_diagram_render_runs_from_a_long_list(self) -> None:
        outputs = self.run_detector("docs-cleanliness.yml", "scope", "docs/architecture/diagrams/c4.puml")
        self.assertEqual(outputs.get("run"), "true")

    def test_no_workflow_pipes_into_a_quiet_grep(self) -> None:
        offenders = [
            f"{path.name}:{number}"
            for path in sorted(WORKFLOWS.glob("*.y*ml"))
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1)
            if re.search(r"\|\s*grep\s+-[A-Za-z]*q", line)
        ]
        self.assertEqual(offenders, [], "pipe into grep -q fails open under pipefail (#579); use a here-string")


if __name__ == "__main__":
    unittest.main()
