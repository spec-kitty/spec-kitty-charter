#!/usr/bin/env python3
"""The nightly mutation summary (#239): one Markdown table of every module's mutation floors.

Reads each ``service/<module>/build/reports/mutation-floors/floors.json`` that the floor check
(``checkMutationFloors``, build-logic ``MutationFloors.kt``) writes, and renders one row per floor group:
mutants, score, test strength, uncovered mutants, floor, verdict and the classes with the most surviving or
uncovered mutants. An expected module without a ``floors.json`` shows as "no report": PIT did not run there,
or its report could not be scored (the check deletes a stale summary before it writes a new one).

The table goes to standard output and, when ``$GITHUB_STEP_SUMMARY`` is set, is appended to the job summary.
It reports only: the exit status is 0 whatever the verdicts, since the ``pitest`` step is what fails the run.

Standard library only. Run locally from the repository root after ``./gradlew pitest --continue``::

    python3 scripts/ci/mutation_summary.py
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

# The modules the nightly expects a summary from, in the order they are listed. A module found on disk but not
# listed here is shown after them.
EXPECTED_MODULES = ("kernel", "missions", "missions-edge", "profiles", "profiles-edge", "packs", "activations")

SUMMARY_PATH = Path("build", "reports", "mutation-floors", "floors.json")

HEADER = (
    "| Module / group | Mutants | Score | Strength | No coverage | Floor | Verdict | Top surviving or uncovered classes |",
    "|---|--:|--:|--:|--:|--:|---|---|",
)

PASS = "✅ pass"
FAIL = "❌ **FAIL**"
NO_REPORT = "⚠️ **no report**"
UNREADABLE = "⚠️ **unreadable report**"


def cell(text: object) -> str:
    """A table cell: pipes escaped and line breaks flattened, so a value never breaks the row."""
    return str(text).replace("\\", "\\\\").replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def simple_name(class_name: str) -> str:
    return class_name.rsplit(".", 1)[-1]


def top_classes(group: dict) -> str:
    parts = []
    for gaps in group.get("topClasses") or []:
        name = cell(simple_name(str(gaps.get("class", "?"))))
        survived = gaps.get("survived", 0)
        uncovered = gaps.get("noCoverage", 0)
        detail = ", ".join(
            part for part in (f"{survived} survived" if survived else "", f"{uncovered} uncovered" if uncovered else "") if part
        )
        parts.append(f"`{name}` ({detail})")
    return "<br>".join(parts) if parts else "—"


def find_modules(root: Path, expected: tuple[str, ...]) -> list[str]:
    """The expected modules, then any other module under ``service/`` that has a summary, sorted."""
    found = sorted(p.parents[3].name for p in root.glob("service/*/" + SUMMARY_PATH.as_posix()))
    return list(expected) + [m for m in found if m not in expected]


def load(path: Path) -> dict | None:
    """The parsed summary, or None when it is not a summary the table can read."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict) or not isinstance(data.get("groups"), list):
        return None
    if not all(isinstance(g, dict) for g in data["groups"]):
        return None
    return data


def group_row(module: str, group: dict) -> tuple[str, bool]:
    passed = group.get("passed") is True
    row = "| " + " | ".join(
        [
            f"`:{cell(module)}` {cell(group.get('group', '?'))}",
            cell(group.get("mutants", "?")),
            f"{cell(group.get('score', '?'))} %",
            f"{cell(group.get('strength', '?'))} %",
            cell(group.get("noCoverage", "?")),
            f"{cell(group.get('floor', '?'))} %",
            PASS if passed else FAIL,
            top_classes(group),
        ]
    ) + " |"
    return row, passed


def missing_row(module: str, verdict: str) -> str:
    return f"| `:{cell(module)}` | — | — | — | — | — | {verdict} | — |"


def render(root: Path, expected: tuple[str, ...] = EXPECTED_MODULES) -> str:
    """The whole summary: a heading, a one-line verdict and the table."""
    rows: list[str] = []
    failing: list[str] = []
    for module in find_modules(root, expected):
        path = root / "service" / module / SUMMARY_PATH
        if not path.is_file():
            rows.append(missing_row(module, NO_REPORT))
            failing.append(f"`:{module}` (no report)")
            continue
        data = load(path)
        if data is None:
            rows.append(missing_row(module, UNREADABLE))
            failing.append(f"`:{module}` (unreadable report)")
            continue
        if not data["groups"]:
            rows.append(missing_row(module, NO_REPORT))
            failing.append(f"`:{module}` (no group)")
            continue
        for group in data["groups"]:
            row, passed = group_row(module, group)
            rows.append(row)
            if not passed:
                failing.append(f"`:{module}` {group.get('group', '?')}")

    lines = ["## Mutation floors", ""]
    if failing:
        lines.append("**❌ Not every group is at its floor:** " + ", ".join(failing) + ".")
    else:
        lines.append("**✅ Every group is at or above its floor.**")
    lines += [
        "",
        "Score: detected over all mutants; strength: detected over covered mutants (PIT's definitions). "
        "Floors and their raise rule: #239 and ADR-0062. The HTML reports are in the `mutation-report` artifact.",
        "",
        *HEADER,
        *rows,
        "",
    ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="the repository root (default: the current directory)")
    args = parser.parse_args(argv)

    summary = render(args.root)
    sys.stdout.write(summary)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as out:
            out.write(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
