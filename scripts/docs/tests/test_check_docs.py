"""Each check against a tiny docs tree: a clean tree passes, one targeted break is reported."""

from __future__ import annotations

import datetime as dt
import sys
from pathlib import Path

import pytest
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_docs as cd  # noqa: E402
import generate_indexes as gi  # noqa: E402

TODAY = dt.date(2026, 10, 14)
# Built from fragments so this file does not itself contain the retired term.
RETIRED = "cere" + "mony"

DESC = "A description that is comfortably longer than fifty characters and shorter than the cap."

# doc-kitty's own frontmatter stamp on a generator-owned glossary page; its presence (not the
# docs/glossary/ path alone) is what grants the authored-only-rule exemptions.
STAMP = {"by": "doc-kitty-glossary/0.4.0", "at": "2026-10-19T15:57:59Z", "content_hash": "sha256:deadbeef"}


# A hub as committed: prose plus an empty generated block; the checks see it regenerated.
BLOCK = f"Prose.\n\n{gi.BEGIN}\n{gi.END}\n"
ADR_KEYS = {"decision_status": "Accepted", "decision_date": "2026-10-01"}


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
        "docs/adr/README.md": page("ADRs", BLOCK, type="ADR"),
        "docs/adr/0001-first.md": page("First", type="ADR", **ADR_KEYS),
        "docs/adr/0002-second.md": page("Second", type="ADR", **ADR_KEYS),
        "docs/changelog/README.md": page("Changelog", BLOCK, type="Changelog"),
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


def test_generated_glossary_exemption_requires_the_stamp(root: Path) -> None:
    gloss = root / "docs/glossary"
    gloss.mkdir()
    over_cap = "x" * (cd.DESC_MAX + 20)
    # A genuinely generated page (carries doc-kitty's `generated:` stamp) may omit the
    # authored-only keys updated/type/audience and carry a description past the cap.
    (gloss / "README.md").write_text(
        page("Glossary", generated=STAMP, description=over_cap, updated=None, type=None, audience=None),
        encoding="utf-8",
    )
    # A page misfiled under docs/glossary/ WITHOUT the stamp is authored and must still be
    # held to every authored-only rule — the fail-closed tightening this guards.
    (gloss / "sneaked.md").write_text(
        page("Sneaked", description=over_cap, updated=None, type=None, audience=None),
        encoding="utf-8",
    )
    # Even a stamped page is still held to the non-exempt required keys (e.g. doc_status).
    (gloss / "nostatus.md").write_text(page("NoStatus", generated=STAMP, doc_status=None), encoding="utf-8")

    by_path: dict[str, list[str]] = {}
    for f in run("frontmatter", root):
        by_path.setdefault(f.path, []).append(f.message)

    assert "docs/glossary/README.md" not in by_path  # exempted: no findings at all
    sneaked = " | ".join(by_path.get("docs/glossary/sneaked.md", []))
    assert "missing required key `updated`" in sneaked
    assert "missing required key `type`" in sneaked
    assert "description is" in sneaked  # over-cap still caught for an authored page
    assert any("doc_status" in m for m in by_path.get("docs/glossary/nostatus.md", []))


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


def test_adr_gap_duplicate_and_an_entry_the_index_cannot_list(root: Path) -> None:
    (root / "docs/adr/0004-fourth.md").write_text(page("Fourth", type="ADR", **ADR_KEYS), encoding="utf-8")
    (root / "docs/adr/0002-other.md").write_text(page("Other", type="ADR", **ADR_KEYS), encoding="utf-8")
    messages = " | ".join(f.message for f in run("adr", root))
    assert "gap in numbering: no ADR 0003" in messages
    assert "ADR number 0002 used by" in messages
    (root / "docs/adr/Notes.md").write_text(page("Notes", type="ADR"), encoding="utf-8")
    (root / "docs/adr/0003-third.md").write_text(page("Third", type="ADR"), encoding="utf-8")  # no decision_* keys
    rewrite(root, "docs/adr/README.md", "Prose.\n", "Prose. See 0003-third in prose.\n")
    messages = [f"{f.path}: {f.message}" for f in run("adr", root)]
    assert "docs/adr/Notes.md: filename must be NNNN-kebab-slug.md" in messages
    # A prose mention is not a listing: the entry the generator cannot list is reported.
    assert "docs/adr/0003-third.md: frontmatter needs a non-empty `decision_status` for the ADR index" in messages


def test_a_new_entry_passes_without_touching_the_hub(root: Path) -> None:
    """The point of the change: a PR adds an entry file and never edits the shared hub."""
    hubs = {rel: (root / rel).read_text(encoding="utf-8") for rel in ("docs/adr/README.md", "docs/changelog/README.md")}
    (root / "docs/adr/0003-third.md").write_text(page("Third", type="ADR", **ADR_KEYS), encoding="utf-8")
    (root / "docs/changelog/2026-10-03-c.md").write_text(page("C", type="Changelog"), encoding="utf-8")
    for name in ("adr", "changelog", "orphans", "links"):
        assert run(name, root) == [], name
    assert {rel: (root / rel).read_text(encoding="utf-8") for rel in hubs} == hubs  # the checks write nothing


def test_a_filled_block_is_never_compared_but_is_refused(root: Path) -> None:
    """The checks read the hub as it regenerates; a committed list is refused, not compared,
    because committing it would bring back the conflicts the generated index removes."""
    rewrite(root, "docs/changelog/README.md", f"{gi.BEGIN}\n", f"{gi.BEGIN}\n- [gone](/changelog/2026-09-01-gone/)\n")
    assert run("links", root) == []  # the stale line is not what the checks see
    assert [f.message.split(":")[0] for f in run("changelog", root)] == ["the committed generated block must be empty"]


def test_a_finding_after_the_block_names_the_committed_line(root: Path) -> None:
    rewrite(root, "docs/adr/README.md", f"{gi.END}\n", f"{gi.END}\n\nSee [gone](/nowhere/).\n")
    lines = (root / "docs/adr/README.md").read_text(encoding="utf-8").splitlines()
    (found,) = run("links", root)
    assert (found.path, found.line) == ("docs/adr/README.md", lines.index("See [gone](/nowhere/).") + 1)


def test_the_generated_hubs_are_exempt_from_freshness(root: Path) -> None:
    """No pull request edits a hub any more, so its `updated` cannot be what keeps it current."""
    hubs = {"docs/adr/README.md", "docs/changelog/README.md"}
    for rel in hubs:
        rewrite(root, rel, "doc_status: active", "doc_status: draft")
    rewrite(root, "docs/adr/README.md", "type: ADR", "type: Guide")
    found = {f.path for f in run("freshness", root, dt.date(2030, 1, 1))}
    assert "docs/guides/README.md" in found  # not vacuous: every authored page is stale by 2030
    assert not found & hubs


def test_a_hub_without_the_generated_block_is_reported(root: Path) -> None:
    rewrite(root, "docs/changelog/README.md", f"{gi.BEGIN}\n{gi.END}\n", "- [a](/changelog/2026-10-01-a/)\n")
    messages = [f"{f.path}: {f.message}" for f in run("changelog", root)]
    assert messages == [
        f"docs/changelog/README.md: needs exactly one generated block: one `{gi.BEGIN}` line, then one `{gi.END}` line"
    ]


def test_changelog_filename_title_and_length(root: Path) -> None:
    (root / "docs/changelog/notes.md").write_text(page("Notes", type="Changelog"), encoding="utf-8")
    rewrite(root, "docs/changelog/2026-10-01-a.md", "Text.", "x" * (cd.CHANGELOG_MAX_CHARS + 1))
    rewrite(root, "docs/changelog/2026-10-02-b.md", "title: B\n", "")
    messages = [f"{f.path}: {f.message}" for f in run("changelog", root)]
    assert "docs/changelog/notes.md: filename must be YYYY-MM-DD-kebab-slug.md" in messages
    assert any(m.startswith("docs/changelog/2026-10-01-a.md: ") and "keep entries under" in m for m in messages)
    assert "docs/changelog/2026-10-02-b.md: frontmatter needs a non-empty `title` for the changelog index" in messages


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
    (root / "docs/glossary/index.md").write_text(
        page("Glossary", "A feature of the core.\n", generated=STAMP), encoding="utf-8"
    )
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
    (root / "docs/glossary/index.md").write_text(
        page("Glossary", "[ok](./index.md) [gone](./missing/)\n", generated=STAMP), encoding="utf-8"
    )
    glossary = [f.message for f in run("links", root) if f.path == "docs/glossary/index.md"]
    assert glossary == ["`./missing/` does not exist"]  # generated pages are link-checked, in the generator's relative style


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
    # An embedded sprite is rasterised per machine: its bytes are geometry, its presence and link are not.
    sprite = svg("Label", "10", "Helvetica", '<image href="data:image/png;base64,AAAA"/><a href="/x/"/>')
    assert cd._normalise_svg(sprite) == cd._normalise_svg(sprite.replace("AAAA", "BBBB"))
    assert cd._normalise_svg(sprite) != base
    assert cd._normalise_svg(sprite) != cd._normalise_svg(sprite.replace('"/x/"', '"/y/"'))


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
