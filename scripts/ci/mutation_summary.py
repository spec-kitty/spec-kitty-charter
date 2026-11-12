#!/usr/bin/env python3
"""The nightly mutation summary (#239): one Markdown table of every module's mutation floors.

Reads each ``<module>/build/reports/mutation-floors/floors.json`` that the floor check
(``checkMutationFloors``, build-logic ``MutationFloors.kt``) writes, and renders one row per floor group:
mutants, score, test strength, uncovered and timed-out mutants, floor, margin to the floor, verdict and the classes
with the most surviving or uncovered mutants. The modules come from the build (#388): ``./gradlew mutationModules``
writes ``build/reports/mutation-floors/modules.txt``, one module directory per line, for every project that applies
``charter.mutation-conventions``. A listed module without a ``floors.json`` shows as "no report": PIT did not run
there, or its report could not be scored (the check deletes a stale summary before it writes a new one). Without
the list the summary says so and is never green.

The table goes to standard output and, when ``$GITHUB_STEP_SUMMARY`` is set, is appended to the job summary.
It reports only: the exit status is 0 whatever the verdicts, since the ``pitest`` step is what fails the run.

Standard library only. Run locally from the repository root after ``./gradlew mutationModules pitest --continue``::

    python3 scripts/ci/mutation_summary.py --modules build/reports/mutation-floors/modules.txt
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

SUMMARY_PATH = Path("build", "reports", "mutation-floors", "floors.json")

HEADER = (
    "| Module / group | Mutants | Score | Strength | No coverage | Timed out | Floor | Margin | Verdict"
    " | Top surviving or uncovered classes |",
    "|---|--:|--:|--:|--:|--:|--:|--:|---|---|",
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


NO_LIST = (
    "**⚠️ the module list (`./gradlew mutationModules`) was not written**, so a module whose PIT run wrote no report"
    " may be missing from this table."
)


def read_modules(path: Path) -> list[str] | None:
    """The module directories ``mutationModules`` wrote, one per line; None when the file is missing or empty."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeDecodeError):
        return None
    modules = [line.strip() for line in lines if line.strip()]
    return modules or None


UNMUTATED = "did not pass without mutation"


def unmutated_failures(path: Path) -> list[str]:
    """PIT's lines naming the tests that fail with no mutation applied (#461); empty without a log or such lines."""
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    return [line.strip() for line in lines if UNMUTATED in line]


def find_modules(root: Path, listed: list[str]) -> list[str]:
    """The listed module directories, then any other one under ``service/`` that has a summary, sorted."""
    found = sorted(p.parents[3].relative_to(root).as_posix() for p in root.glob("service/*/" + SUMMARY_PATH.as_posix()))
    return list(listed) + [m for m in found if m not in listed]


def is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def complete(group: dict) -> bool:
    """Whether the group has the figures ``MutationFloors.toJson`` writes for #389; a group without them is invalid."""
    return (
        is_number(group.get("marginPoints"))
        and isinstance(group.get("marginMutants"), int)
        and not isinstance(group.get("marginMutants"), bool)
        and isinstance(group.get("timedOut"), int)
        and not isinstance(group.get("timedOut"), bool)
    )


def margin(group: dict) -> str:
    """Points over the floor and the detected mutants the group can lose and still pass; "?" when invalid."""
    if not complete(group):
        return "?"
    return f"{group['marginPoints']:+.1f} pts, {group['marginMutants']}"


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
    # A group without its verdict or its #389 figures is invalid content, never a pass.
    passed = group.get("passed") is True and complete(group)
    row = "| " + " | ".join(
        [
            f"`:{cell(module)}` {cell(group.get('group', '?'))}",
            cell(group.get("mutants", "?")),
            f"{cell(group.get('score', '?'))} %",
            f"{cell(group.get('strength', '?'))} %",
            cell(group.get("noCoverage", "?")),
            cell(group.get("timedOut", "?")),
            f"{cell(group.get('floor', '?'))} %",
            margin(group),
            PASS if passed else FAIL,
            top_classes(group),
        ]
    ) + " |"
    return row, passed


def missing_row(module: str, verdict: str) -> str:
    return f"| `:{cell(module)}` | — | — | — | — | — | — | — | {verdict} | — |"


def render(root: Path, listed: list[str] | None, unmutated: list[str] | None = None) -> str:
    """The whole summary: a heading, a one-line verdict, the table and any test that failed unmutated.

    ``listed`` is None when there is no module list; ``unmutated`` holds PIT's lines about tests that fail with no
    mutation applied, which leave their module without a report.
    """
    rows: list[str] = []
    failing: list[str] = []
    for directory in find_modules(root, listed or []):
        # The label is the Gradle project name (``:kernel``), which is the directory's name for every module under
        # service/; the report is read from the full relative directory.
        module = Path(directory).name
        path = root / directory / SUMMARY_PATH
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
    if listed is None:
        lines += [NO_LIST, ""]
    if failing:
        lines.append("**❌ Not every group is at its floor:** " + ", ".join(failing) + ".")
    elif listed is None:
        lines.append("**⚠️ Every group shown is at or above its floor, but the list of modules is missing.**")
    else:
        lines.append("**✅ Every group is at or above its floor.**")
    lines += [
        "",
        "Score: detected over all mutants; strength: detected over covered mutants (PIT's definitions). "
        "Timed out: mutants PIT counts as detected because a test ran past its time, often noise from a loaded runner. "
        "Margin: the score minus the floor in points, and the detected mutants the group can lose and still pass "
        "(negative: how many it must gain; #389). "
        "Floors and their raise rule: #239 and ADR-0062. The HTML reports are in the `mutation-report-<shard>` artifacts.",
        "",
        *HEADER,
        *rows,
        "",
    ]
    if unmutated:
        lines += [
            "### Tests that fail with no mutation applied",
            "",
            "PIT needs a green suite before it mutates, so their module wrote no report (#461):",
            "",
            *(f"- `{line.replace('`', chr(39))}`" for line in unmutated),
            "",
        ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="the repository root (default: the current directory)")
    parser.add_argument(
        "--modules",
        type=Path,
        required=True,
        help="the module list ./gradlew mutationModules writes (build/reports/mutation-floors/modules.txt)",
    )
    parser.add_argument(
        "--log",
        type=Path,
        help="the output of the PIT step, to quote the tests that fail with no mutation applied (#461)",
    )
    args = parser.parse_args(argv)

    unmutated = unmutated_failures(args.log) if args.log else []
    summary = render(args.root, read_modules(args.modules), unmutated)
    sys.stdout.write(summary)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as out:
            out.write(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
