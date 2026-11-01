"""The changelog and ADR indexes are generated from the entries' frontmatter, never hand-kept."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_indexes as gi  # noqa: E402


def entry(**meta: object) -> str:
    return "---\n" + yaml.safe_dump(meta, sort_keys=False, allow_unicode=True) + "---\n\n# Body\n"


def hub(prose: str = "Prose.\n", block: str = "") -> str:
    return f"---\ntitle: Hub\n---\n\n# Hub\n\n{prose}\n{gi.BEGIN}\n{block}{gi.END}\n\nAfter the block.\n"


@pytest.fixture
def docs(tmp_path: Path) -> Path:
    files = {
        "changelog/README.md": hub(),
        "changelog/2026-10-01-alpha.md": entry(title="Alpha landed"),
        "changelog/2026-10-03-zeta.md": entry(title="Zeta landed"),
        "changelog/2026-10-03-beta.md": entry(title="Beta landed"),
        "adr/README.md": hub(),
        "adr/0002-second.md": entry(title="ADR-0002: Second", decision_status="Accepted", decision_date="2026-10-02"),
        "adr/0001-first.md": entry(
            title="ADR-0001: First | with a pipe",
            decision_status="Accepted, with Amendment 2026-10-28 (#208)",
            decision_date="2026-10-01",
        ),
        "adr/template.md": "not an entry\n",
    }
    for rel, text in files.items():
        f = tmp_path / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text, encoding="utf-8")
    return tmp_path


def test_changelog_lines_are_newest_first_then_by_filename_with_the_frontmatter_title(docs: Path) -> None:
    lines, problems = gi.changelog_lines(docs)
    assert problems == []
    assert lines == [
        "- [2026-10-03 — Beta landed](/changelog/2026-10-03-beta/)",
        "- [2026-10-03 — Zeta landed](/changelog/2026-10-03-zeta/)",
        "- [2026-10-01 — Alpha landed](/changelog/2026-10-01-alpha/)",
    ]


def test_adr_rows_are_in_number_order_with_status_and_date_from_frontmatter(docs: Path) -> None:
    lines, problems = gi.adr_lines(docs)
    assert problems == []
    assert lines == [
        "| ID | Title | Status | Date |",
        "| --- | --- | --- | --- |",
        "| [0001](/adr/0001-first/) | ADR-0001: First \\| with a pipe | Accepted, with Amendment 2026-10-28 (#208) | 2026-10-01 |",
        "| [0002](/adr/0002-second/) | ADR-0002: Second | Accepted | 2026-10-02 |",
    ]


def test_an_unquoted_yaml_date_renders_as_an_iso_date(docs: Path) -> None:
    (docs / "adr/0002-second.md").write_text(
        "---\ntitle: 'ADR-0002: Second'\ndecision_status: Accepted\ndecision_date: 2026-10-02\n---\n", encoding="utf-8"
    )
    lines, problems = gi.adr_lines(docs)
    assert problems == []
    assert lines[-1].endswith("| Accepted | 2026-10-02 |")


def test_an_entry_missing_a_key_is_reported_and_left_out(docs: Path) -> None:
    (docs / "adr/0003-third.md").write_text(entry(title="ADR-0003: Third", decision_date="2026-10-03"), encoding="utf-8")
    (docs / "changelog/2026-10-04-gamma.md").write_text("no frontmatter\n", encoding="utf-8")
    adr, adr_problems = gi.adr_lines(docs)
    log, log_problems = gi.changelog_lines(docs)
    assert [(p.path, p.message) for p in adr_problems] == [
        ("docs/adr/0003-third.md", "frontmatter needs a non-empty `decision_status` for the ADR index")
    ]
    assert [p.path for p in log_problems] == ["docs/changelog/2026-10-04-gamma.md"]
    assert not any("0003" in line for line in adr)
    assert not any("gamma" in line for line in log)


def test_files_that_are_not_entries_are_skipped(docs: Path) -> None:
    (docs / "changelog/notes.md").write_text(entry(title="Notes"), encoding="utf-8")
    (docs / "adr/Notes.md").write_text(entry(title="Notes"), encoding="utf-8")
    assert all("notes" not in line.lower() for line in gi.changelog_lines(docs)[0] + gi.adr_lines(docs)[0])


def test_splice_replaces_only_the_block_and_is_idempotent() -> None:
    text = hub(block="- stale line someone committed\n")
    once = gi.splice(text, ["- one", "- two"])
    assert once is not None
    assert "stale line" not in once
    assert f"Prose.\n\n{gi.BEGIN}\n\n- one\n- two\n\n{gi.END}\n\nAfter the block.\n" in once
    assert gi.splice(once, ["- one", "- two"]) == once


def test_splice_without_markers_returns_none() -> None:
    assert gi.splice("# Hub\n\n- a hand-kept line\n", ["- one"]) is None


NO_BLOCK = f"needs exactly one generated block: one `{gi.BEGIN}` line, then one `{gi.END}` line"


@pytest.mark.parametrize(
    "hub_text",
    [
        "# ADRs\n",
        f"{gi.END}\n{gi.BEGIN}\n",  # reversed
        f"{gi.BEGIN}\n{gi.END}\n\n{gi.BEGIN}\n- [x](/adr/nope/)\n{gi.END}\n",  # a second block would publish stale text
    ],
    ids=["no-markers", "reversed", "two-blocks"],
)
def test_regenerate_needs_exactly_one_block(docs: Path, hub_text: str) -> None:
    (docs / "adr/README.md").write_text(hub_text, encoding="utf-8")
    result = gi.regenerate(docs, "adr/README.md")
    assert result.text is None
    assert [p.message for p in result.problems] == [NO_BLOCK]


def test_a_filled_committed_block_is_a_problem(docs: Path) -> None:
    (docs / "changelog/README.md").write_text(hub(block="- [2026-10-01 — Alpha landed](/changelog/2026-10-01-alpha/)\n"))
    result = gi.regenerate(docs, "changelog/README.md")
    assert result.text is not None
    assert [p.message.split(":")[0] for p in result.problems] == ["the committed generated block must be empty"]


def test_lines_after_the_block_map_back_to_the_committed_file(docs: Path) -> None:
    committed = (docs / "changelog/README.md").read_text(encoding="utf-8").splitlines()
    result = gi.regenerate(docs, "changelog/README.md")
    assert result.text is not None
    regenerated = result.text.splitlines()
    after = regenerated.index("After the block.") + 1
    assert result.committed_line(after) == committed.index("After the block.") + 1
    begin = committed.index(gi.BEGIN) + 1
    assert result.committed_line(begin) == begin
    assert result.committed_line(begin + 3) == begin  # inside the block: the BEGIN marker
    assert result.committed_line(2) == 2


def test_titles_cannot_break_the_link_or_the_cell(docs: Path) -> None:
    (docs / "changelog/2026-10-01-alpha.md").write_text(entry(title="Fix [draft] links](evil) \\ ok"), encoding="utf-8")
    (docs / "adr/0002-second.md").write_text(
        entry(title="ADR-0002: a \\| b", decision_status="Accepted", decision_date="2026-10-02"), encoding="utf-8"
    )
    assert "- [2026-10-01 — Fix \\[draft\\] links\\](evil) \\\\ ok](/changelog/2026-10-01-alpha/)" in gi.changelog_lines(docs)[0]
    assert "| ADR-0002: a \\\\\\| b |" in gi.adr_lines(docs)[0][-1]


@pytest.mark.parametrize(
    ("raw", "message"),
    [
        ("decision_date: 2026-13-45", "no readable frontmatter; the ADR index cannot list it"),
        ("decision_date: [2026-10-02]", "frontmatter `decision_date` must be text for the ADR index"),
        ("decision_date: ''", "frontmatter needs a non-empty `decision_date` for the ADR index"),
    ],
    ids=["impossible-date", "a-list", "empty"],
)
def test_a_bad_value_is_reported_not_raised(docs: Path, raw: str, message: str) -> None:
    (docs / "adr/0002-second.md").write_text(f"---\ntitle: Second\ndecision_status: Accepted\n{raw}\n---\n")
    assert [p.message for p in gi.adr_lines(docs)[1]] == [message]


def test_an_unquoted_datetime_renders_as_its_date(docs: Path) -> None:
    (docs / "adr/0002-second.md").write_text(
        "---\ntitle: Second\ndecision_status: Accepted\ndecision_date: 2026-10-02 10:00:00\n---\n", encoding="utf-8"
    )
    assert gi.adr_lines(docs)[0][-1].endswith("| Accepted | 2026-10-02 |")


def test_write_fills_the_hubs_and_a_second_run_changes_nothing(docs: Path) -> None:
    assert gi.main(["--root", str(docs.parent), "--docs", str(docs), "--write"]) == 0
    first = (docs / "changelog/README.md").read_text(encoding="utf-8")
    assert "- [2026-10-03 — Beta landed](/changelog/2026-10-03-beta/)" in first
    assert "| [0002](/adr/0002-second/)" in (docs / "adr/README.md").read_text(encoding="utf-8")
    assert gi.main(["--root", str(docs.parent), "--docs", str(docs), "--write"]) == 0
    assert (docs / "changelog/README.md").read_text(encoding="utf-8") == first


def test_check_mode_fails_on_a_problem_and_writes_nothing(docs: Path) -> None:
    (docs / "changelog/2026-10-04-gamma.md").write_text(entry(description="no title"), encoding="utf-8")
    before = (docs / "changelog/README.md").read_text(encoding="utf-8")
    assert gi.main(["--root", str(docs.parent), "--docs", str(docs)]) == 1
    assert (docs / "changelog/README.md").read_text(encoding="utf-8") == before
