"""Each check against a tiny docs tree: a clean tree passes, one targeted break is reported."""

from __future__ import annotations

import datetime as dt
import sys
from pathlib import Path

import pytest
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_docs as cd  # noqa: E402

TODAY = dt.date(2026, 10, 14)
# Built from fragments so this file does not itself contain the retired term.
RETIRED = "cere" + "mony"

DESC = "A description that is comfortably longer than fifty characters and shorter than the cap."


def page(title: str = "Page", body: str = "Text.\n", **meta: object) -> str:
    base = {
        "title": title,
        "description": DESC,
        "doc_status": "active",
        "updated": "2026-10-01",
        "type": "Guide",
        "kind": "Explanation",
        "audience": "docs/context/audience/dev.md",
    }
    base.update(meta)
    dumped = yaml.safe_dump({k: v for k, v in base.items() if v is not None}, sort_keys=False)
    return "---\n" + dumped + "---\n\n# " + title + "\n\n" + body


@pytest.fixture
def root(tmp_path: Path) -> Path:
    files = {
        "docs/README.md": page("Home", "- [Guide](/guides/)\n- [Context](/context/)\n- [ADRs](/adr/)\n- [Changelog](/changelog/)\n"),
        "docs/context/README.md": page("Context", "- [Audience](/context/audience/)\n"),
        "docs/context/audience/README.md": page("Audience", "- [Dev](/context/audience/dev/)\n"),
        "docs/context/audience/dev.md": page("Dev"),
        "docs/guides/README.md": page("Guides", "- [One](/guides/one/)\n"),
        "docs/guides/one.md": page("One", "## Setup\n\nSee [home](/) and [setup](#setup).\n", related=["guides/README"]),
        "docs/adr/README.md": page("ADRs", "- [1](/adr/0001-first/)\n- [2](/adr/0002-second/)\n", type="ADR"),
        "docs/adr/0001-first.md": page("First", type="ADR"),
        "docs/adr/0002-second.md": page("Second", type="ADR"),
        "docs/changelog/README.md": page(
            "Changelog", "- [b](/changelog/2026-10-02-b/)\n- [a](/changelog/2026-10-01-a/)\n", type="Changelog"
        ),
        "docs/changelog/2026-10-01-a.md": page("A", type="Changelog"),
        "docs/changelog/2026-10-02-b.md": page("B", type="Changelog"),
        "docs/_meta/terminology.yaml": (
            "forbidden:\n  - {term: feature, pattern: '\\bfeatures?\\b', prefer: Mission, reason: canon}\n"
            f"  - {{term: retired, pattern: '\\b{RETIRED}\\b', prefer: status commit, reason: retired}}\n"
            "avoid: []\nallow: []\n"
        ),
    }
    for rel, text in files.items():
        f = tmp_path / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text, encoding="utf-8")
    return tmp_path


def run(name: str, root: Path, today: dt.date = TODAY) -> list[cd.Finding]:
    return cd.CHECKS[name](cd.Tree(root), today)


def rewrite(root: Path, rel: str, old: str, new: str) -> None:
    f = root / rel
    text = f.read_text(encoding="utf-8")
    assert old in text, f"rewrite: {old!r} not found in {rel}; the test would pass vacuously"
    f.write_text(text.replace(old, new), encoding="utf-8")


@pytest.mark.parametrize("name", cd.ALL)
def test_clean_tree_passes(root: Path, name: str) -> None:
    assert run(name, root) == []


def test_frontmatter_reports_missing_key_short_description_and_dangling_audience(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "kind: Explanation\n", "")
    (root / "docs/guides/README.md").write_text(
        page("Guides", "- [One](/guides/one/)\n", description="Too short."), encoding="utf-8"
    )
    rewrite(root, "docs/context/audience/dev.md", "docs/context/audience/dev.md", "docs/context/audience/nobody.md")
    messages = [f.message for f in run("frontmatter", root)]
    assert any("missing required key `kind`" in m for m in messages)
    assert any("description is 10 characters" in m for m in messages)
    assert any("nobody.md" in m for m in messages)


def test_audience_accepts_profile_slug_form(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "audience: docs/context/audience/dev.md", "audience:\n- profile: dev\n  guidance_text: x")
    assert run("frontmatter", root) == []
    rewrite(root, "docs/guides/one.md", "profile: dev", "profile: nobody")
    assert any("nobody" in f.message for f in run("frontmatter", root))


def test_related_must_resolve(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "guides/README", "guides/missing")
    assert [f.message for f in run("related", root)] == [
        "related `guides/missing` does not resolve (use a docs-root slug such as adr/0001-x)"
    ]


def test_freshness_flags_stale_active_but_not_durable_or_changelog(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "2026-10-01", "2026-01-01")
    rewrite(root, "docs/guides/README.md", "2026-10-01", "2025-01-01")
    rewrite(root, "docs/guides/README.md", "doc_status: active", "doc_status: 'durable'")
    rewrite(root, "docs/changelog/2026-10-01-a.md", "2026-10-01", "2020-01-01")
    rewrite(root, "docs/context/README.md", "type: Guide", "type: [Guide]")  # a malformed type must not crash
    found = run("freshness", root)
    assert [f.path for f in found] == ["docs/guides/one.md"]


def test_freshness_flags_future_date(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "2026-10-01", "2027-01-01")
    assert "future" in run("freshness", root)[0].message


def test_adr_gap_duplicate_and_unlisted(root: Path) -> None:
    (root / "docs/adr/0004-fourth.md").write_text(page("Fourth", type="ADR"), encoding="utf-8")
    (root / "docs/adr/0002-other.md").write_text(page("Other", type="ADR"), encoding="utf-8")
    messages = " | ".join(f.message for f in run("adr", root))
    assert "gap in numbering: no ADR 0003" in messages
    assert "ADR number 0002 used by" in messages
    assert "not listed" in messages
    (root / "docs/adr/Notes.md").write_text(page("Notes", type="ADR"), encoding="utf-8")
    (root / "docs/adr/0003-third.md").write_text(page("Third", type="ADR"), encoding="utf-8")
    rewrite(root, "docs/adr/README.md", "# ADRs\n", "# ADRs\n\nSee 0003-third in prose.\n")
    messages = [f"{f.path}: {f.message}" for f in run("adr", root)]
    assert "docs/adr/Notes.md: filename must be NNNN-kebab-slug.md" in messages
    assert "docs/adr/0003-third.md: not listed in docs/adr/README.md" in messages  # a prose mention is not a listing


def test_changelog_filename_listing_order_and_length(root: Path) -> None:
    (root / "docs/changelog/notes.md").write_text(page("Notes", type="Changelog"), encoding="utf-8")
    rewrite(root, "docs/changelog/2026-10-01-a.md", "Text.", "x" * (cd.CHANGELOG_MAX_CHARS + 1))
    rewrite(root, "docs/changelog/README.md", "- [b](/changelog/2026-10-02-b/)\n- [a](/changelog/2026-10-01-a/)\n",
            "- [a](/changelog/2026-10-01-a/)\n- [b](/changelog/2026-10-02-b/)\n")
    messages = " | ".join(f.message for f in run("changelog", root))
    assert "YYYY-MM-DD-kebab-slug" in messages
    assert "characters; keep entries under" in messages
    assert "newest first" in messages


def test_terms_flags_forbidden_terms_but_not_code_urls_or_allowlisted(root: Path) -> None:
    rewrite(root, "docs/_meta/terminology.yaml", "avoid: []", "avoid:")  # an empty list key loads as None
    rewrite(root, "docs/guides/one.md", "Setup\n\nSee", f"Setup\n\nA feature and a {RETIRED}. `feature` https://x.test/feature See")
    found = run("terms", root)
    assert sorted(f.message.split("`")[1] for f in found) == ["feature", "retired"]
    (root / "docs/_meta/terminology.yaml").write_text(
        (root / "docs/_meta/terminology.yaml").read_text().replace(
            "allow: []", "allow:\n  - {path: docs/guides/one.md, term: feature, reason: test}"
        )
    )
    assert [f.message.split("`")[1] for f in run("terms", root)] == ["retired"]


def test_terms_skips_generated_glossary(root: Path) -> None:
    (root / "docs/glossary").mkdir()
    (root / "docs/glossary/index.md").write_text(page("Glossary", "A feature of the core.\n"), encoding="utf-8")
    assert run("terms", root) == []


def test_orphan_page_not_in_hub(root: Path) -> None:
    (root / "docs/guides/two.md").write_text(page("Two"), encoding="utf-8")
    assert [f.path for f in run("orphans", root)] == ["docs/guides/two.md"]


def test_links_broken_anchor_relative_md_and_self_github_link(root: Path) -> None:
    rewrite(
        root,
        "docs/guides/one.md",
        "See [home](/) and [setup](#setup).",
        "[a](/nope/) [b](#nowhere) [c](two.md) [d](/guides/#zzz) "
        "[e](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/x.md) [f](https://example.org/ok)",
    )
    messages = " | ".join(f.message for f in run("links", root))
    for needle in ("`/nope/` does not resolve", "`#nowhere`", "relative page link", "#zzz", "own docs"):
        assert needle in messages
    assert "example.org" not in messages
    (root / "docs/glossary").mkdir()
    (root / "docs/glossary/index.md").write_text(page("Glossary", "[ok](./index.md) [gone](./missing/)\n"), encoding="utf-8")
    glossary = [f.message for f in run("links", root) if f.path == "docs/glossary/index.md"]
    assert glossary == ["`./missing/` does not exist"]  # generated pages are link-checked, in core's relative style


def test_links_allow_github_url_of_a_non_page_file(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "See [home](/)", "See [t](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/x.yaml)")
    assert run("links", root) == []


def test_links_ignore_fenced_code(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "See [home](/)", "```\n[bad](/nope/)\n```\nSee [home](/)")
    assert run("links", root) == []


def test_svg_normalisation_ignores_geometry_and_fonts_but_not_content() -> None:
    def svg(label: str, x: str, font: str, extra: str = "") -> str:
        return (
            f'<?plantuml 1.1?><svg width="{x}"><g class="entity" data-qualified-name="A" id="ent0001">'
            f'<rect x="{x}"/><path d="M{x},1"/><text font-family="{font}" x="{x}" textLength="{x}">{label}</text>{extra}</g></svg>'
        )

    base = cd._normalise_svg(svg("Label", "10", "Helvetica"))
    assert base == cd._normalise_svg(svg("Label", "99.5", "DejaVu Sans", "<line x1=\"3\"/>"))
    assert base != cd._normalise_svg(svg("Other", "10", "Helvetica"))
    assert base != cd._normalise_svg(svg("Label", "10", "Helvetica").replace('"A"', '"B"'))


def test_links_accept_generated_route_prefix_from_doc_rules_only(root: Path) -> None:
    rewrite(root, "docs/guides/one.md", "See [home](/)", "See [api](/javadoc/index.html) [gone](/nope/x.html) [up](/javadoc/../nope/) [enc](/javadoc/%2e%2e/nope.html) [seg](/javadoc%2Fx.html)")
    before = [f.message for f in run("links", root)]
    assert any("/javadoc/index.html" in m for m in before)  # no rule yet: a missing file fails
    (root / "docs/_meta/doc-rules.yaml").write_text(
        "version: 1\nrules:\n  - id: site-generated-routes\n    rule: r\n    check: c\n    prefixes: [/javadoc/]\n",
        encoding="utf-8",
    )
    messages = [f.message for f in run("links", root)]
    assert not any("/javadoc/index.html" in m for m in messages)
    # Positive controls: an unlisted root-absolute link to a missing file still fails, and so does
    # one that only starts with the prefix before leaving it,
    # also percent-encoded, and one whose encoded slash makes it a single segment to a server.
    assert any("`/nope/x.html` does not resolve" in m for m in messages)
    assert any("`/javadoc/../nope/` does not resolve" in m for m in messages)
    assert any("`/javadoc/%2e%2e/nope.html` does not resolve" in m for m in messages)
    assert any("`/javadoc%2Fx.html` does not resolve" in m for m in messages)
