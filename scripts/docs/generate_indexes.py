#!/usr/bin/env python3
"""Generate the changelog and ADR indexes from the entries' frontmatter.

The hubs `docs/changelog/README.md` and `docs/adr/README.md` are shared files: when every PR added
its own line to them, every merge put the other open PRs into conflict. So no PR edits the lists.
Each hub holds its prose and a generated block between two markers. The block is committed empty;
this script fills it when the site is built (`docs-site.yml`), and `check_docs.py` checks the hub
as it regenerates, in memory. ADR-0047 records the decision.

    python3 scripts/docs/generate_indexes.py            # check: report what cannot be listed
    python3 scripts/docs/generate_indexes.py --write    # fill the blocks (site build, local preview)

Each entry supplies its own line:
- a changelog entry `docs/changelog/YYYY-MM-DD-slug.md`: its date from the filename, its `title`;
- an ADR `docs/adr/NNNN-slug.md`: `title`, `decision_status` and `decision_date`.

Standard library + PyYAML. Exit 0 clean, 1 an entry cannot be listed or a hub has no block.
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import yaml

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
BEGIN = "<!-- BEGIN GENERATED INDEX: scripts/docs/generate_indexes.py fills this list; do not edit it by hand -->"
END = "<!-- END GENERATED INDEX -->"
BLOCK_RE = re.compile(re.escape(BEGIN) + r"\n.*?" + re.escape(END), re.DOTALL)

CHANGELOG_ENTRY_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$")
ADR_RE = re.compile(r"^(\d{4})-[a-z0-9][a-z0-9-]*\.md$")
FRONTMATTER_RE = re.compile(r"\A---\r?\n(.*?)\r?\n---\r?\n?", re.DOTALL)


@dataclass(frozen=True)
class Problem:
    path: str  # relative to the repository root, POSIX
    message: str


def _rel(docs: Path, path: Path) -> str:
    return f"docs/{path.relative_to(docs).as_posix()}"


def _meta(path: Path) -> dict[str, object] | None:
    m = FRONTMATTER_RE.match(path.read_text(encoding="utf-8"))
    if not m:
        return None
    try:
        data = yaml.safe_load(m.group(1))
    except (yaml.YAMLError, ValueError):  # ValueError: an impossible unquoted date, e.g. 2026-13-45
        return None
    return data if isinstance(data, dict) else None


def _text(value: object) -> str | None:
    """A frontmatter value as one line of text; "" when missing or empty, None when not text."""
    if value is None:
        return ""
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    if isinstance(value, (str, int, float)) and not isinstance(value, bool):
        return " ".join(str(value).split())
    return None


def _fields(docs: Path, path: Path, keys: tuple[str, ...], index: str) -> tuple[list[str] | None, list[Problem]]:
    meta = _meta(path)
    if meta is None:
        return None, [Problem(_rel(docs, path), f"no readable frontmatter; the {index} index cannot list it")]
    values = [_text(meta.get(k)) for k in keys]
    problems = [
        Problem(
            _rel(docs, path),
            f"frontmatter `{k}` must be text for the {index} index"
            if v is None
            else f"frontmatter needs a non-empty `{k}` for the {index} index",
        )
        for k, v in zip(keys, values)
        if not v
    ]
    return (None, problems) if problems else ([v for v in values if v], [])


def _link_text(text: str) -> str:
    """Text that cannot end or nest the Markdown link it sits in."""
    return re.sub(r"([\\\[\]])", r"\\\1", text)


def changelog_lines(docs: Path) -> tuple[list[str], list[Problem]]:
    """One bullet per entry: newest date first, then by filename."""
    rows: list[tuple[str, str, str]] = []
    problems: list[Problem] = []
    for p in sorted((docs / "changelog").glob("*.md")):
        m = CHANGELOG_ENTRY_RE.match(p.name)
        if p.name == "README.md" or not m:
            continue  # check_docs reports a misnamed file
        fields, found = _fields(docs, p, ("title",), "changelog")
        problems += found
        if fields:
            rows.append((m.group(1), p.stem, fields[0]))
    rows.sort(key=lambda r: r[1])
    rows.sort(key=lambda r: r[0], reverse=True)  # stable: same-day entries stay in filename order
    return [f"- [{date} — {_link_text(title)}](/changelog/{stem}/)" for date, stem, title in rows], problems


def _cell(text: str) -> str:
    """Text that cannot end the table cell it sits in."""
    return text.replace("\\", "\\\\").replace("|", "\\|")


def adr_lines(docs: Path) -> tuple[list[str], list[Problem]]:
    """The ADR table, in number order."""
    lines = ["| ID | Title | Status | Date |", "| --- | --- | --- | --- |"]
    problems: list[Problem] = []
    for p in sorted((docs / "adr").glob("*.md")):
        m = ADR_RE.match(p.name)
        if not m:
            continue  # README.md, template.md; check_docs reports a misnamed file
        fields, found = _fields(docs, p, ("title", "decision_status", "decision_date"), "ADR")
        problems += found
        if fields:
            title, status, date = (_cell(f) for f in fields)
            lines.append(f"| [{m.group(1)}](/adr/{p.stem}/) | {title} | {status} | {date} |")
    return lines, problems


HUBS: dict[str, Callable[[Path], tuple[list[str], list[Problem]]]] = {
    "changelog/README.md": changelog_lines,
    "adr/README.md": adr_lines,
}


def splice(text: str, lines: list[str]) -> str | None:
    """The hub text with its generated block holding `lines`; None unless the hub has exactly one block."""
    if text.count(BEGIN) != 1 or text.count(END) != 1 or not BLOCK_RE.search(text):
        return None
    block = BEGIN + "\n\n" + "".join(line + "\n" for line in lines) + ("\n" if lines else "") + END
    return BLOCK_RE.sub(lambda _: block, text, count=1)


@dataclass(frozen=True)
class Regenerated:
    """A hub as it regenerates (None when it has no block), what kept it from regenerating
    cleanly, and how its lines map back to the file as committed."""

    text: str | None
    problems: list[Problem]
    begin_line: int = 0  # 1-based line of BEGIN in the committed file
    end_line: int = 0  # 1-based line of END in the committed file
    shift: int = 0  # lines the regenerated block adds to the committed one

    def committed_line(self, line: int) -> int:
        """The committed file's line for a line of the regenerated text. A line inside the generated
        block maps to its BEGIN marker: the entry file, not the hub, is what to fix."""
        if self.text is None or line < self.begin_line:
            return line
        if line >= self.end_line + self.shift:
            return line - self.shift
        return self.begin_line


def regenerate(docs: Path, hub: str, *, require_empty: bool = True) -> Regenerated:
    """The hub as it regenerates, and what kept an entry out of it. `require_empty` refuses a committed
    block that holds anything; `--write` turns it off, since it overwrites a filled block on purpose."""
    path = docs / hub
    lines, problems = HUBS[hub](docs)
    committed = path.read_text(encoding="utf-8")
    text = splice(committed, lines)
    if text is None:
        problem = Problem(_rel(docs, path), f"needs exactly one generated block: one `{BEGIN}` line, then one `{END}` line")
        return Regenerated(None, [problem, *problems])
    m = BLOCK_RE.search(committed)
    assert m is not None  # splice found it
    begin_line = committed[: m.start()].count("\n") + 1
    end_line = begin_line + m.group(0).count("\n")
    if require_empty and m.group(0)[len(BEGIN) : -len(END)].strip():
        problems = [
            Problem(
                _rel(docs, path),
                "the committed generated block must be empty: the site build fills it, so a pull request never "
                "edits the list (do not commit `--write` output)",
            ),
            *problems,
        ]
    shift = text.count("\n") - committed.count("\n")
    return Regenerated(text, problems, begin_line, end_line, shift)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--docs", type=Path, help="the docs directory (default: ROOT/docs)")
    ap.add_argument("--write", action="store_true", help="fill the generated blocks in place")
    args = ap.parse_args(argv)
    docs = (args.docs or args.root / "docs").resolve()
    failed = False
    for hub in HUBS:
        result = regenerate(docs, hub, require_empty=not args.write)
        for p in result.problems:
            print(f"[index] {p.path}: {p.message}", file=sys.stderr)
        failed = failed or bool(result.problems)
        if args.write and result.text is not None:
            (docs / hub).write_text(result.text, encoding="utf-8")
    if args.write:
        print("Filled the generated blocks for a preview. Do not commit them: `git checkout -- docs/*/README.md`.",
              file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
