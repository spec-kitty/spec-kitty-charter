#!/usr/bin/env python3
"""Docs cleanliness checks for the charter docsite.

One entry point, one subcommand per check, so a CI log names the check that failed:

    python3 scripts/docs/check_docs.py [--root DIR] [--today YYYY-MM-DD] <check> [...]

Checks: frontmatter, related, freshness, adr, changelog, terms, orphans, links, plantuml,
wordlist; `all` runs every check except `plantuml` and `wordlist` (the first needs Java).
Rule -> check mapping: docs/_meta/doc-rules.yaml. Usage and rationale: docs/guides/docsite.md.

Standard library + PyYAML. Exit 0 clean, 1 findings, 2 usage/setup error.
"""

from __future__ import annotations

import argparse
import datetime as dt
import fnmatch
import hashlib
import json
import os
import posixpath
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
from collections.abc import Callable, Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

import yaml

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
SELF_REPO = "spec-kitty/spec-kitty-charter"
SITE_BASE = "/spec-kitty-charter"

STATUSES = {"draft", "active", "deprecated", "superseded", "durable"}
REQUIRED_KEYS = ("title", "description", "doc_status", "updated", "type", "kind", "audience")
# doc-kitty 0.4.0 owns the generated glossary pages and writes an exact frontmatter key set
# (okf_version, title, description, kind, [glossary_context], doc_status, generated); its
# generator refuses to overwrite a page that carries any other key. So the generated pages
# cannot carry these authored-only keys, and this gate must not demand them of a generated page.
GENERATED_EXEMPT_KEYS = frozenset({"updated", "type", "audience"})
DESC_MIN, DESC_MAX = 50, 180
AUDIENCE_DIR = "docs/context/audience"
DOC_RULES = "docs/_meta/doc-rules.yaml"
# The rule in DOC_RULES whose `prefixes` list the routes the site build generates.
GENERATED_ROUTES_RULE = "site-generated-routes"

# Freshness SLA in days by doc_status, adapted from core's docs-freshness-sla styleguide
# (tiers by volatility). None = never aged out. ADR and Changelog pages are dated records:
# they carry the longest tier / are exempt.
SLA_DAYS: dict[str, int | None] = {
    "draft": 90,
    "active": 180,
    "deprecated": 365,
    "superseded": None,
    "durable": None,
}
SLA_BY_TYPE: dict[str, int | None] = {"ADR": 365, "Changelog": None}

CHANGELOG_ENTRY_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$")
CHANGELOG_MAX_CHARS = 3000
ADR_NON_ENTRIES = ("README.md", "template.md")
ADR_RE = re.compile(r"^(\d{4})-[a-z0-9][a-z0-9-]*\.md$")

FRONTMATTER_RE = re.compile(r"\A---\r?\n(.*?)\r?\n---\r?\n?", re.DOTALL)
FENCE_RE = re.compile(r"^\s*(```|~~~)")
LINK_RE = re.compile(r"(?<!\!)\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
IMAGE_RE = re.compile(r"\!\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
EXPLICIT_ANCHOR_RE = re.compile(r"^\{#([A-Za-z0-9_-]+)\}\s*$")
EXTERNAL_RE = re.compile(r"^(?:[a-z][a-z0-9+.-]*:|//)", re.IGNORECASE)
INLINE_CODE_RE = re.compile(r"`[^`\n]*`")
SELF_GITHUB_RE = re.compile(
    rf"https?://github\.com/{re.escape(SELF_REPO)}/(?:blob|tree)/[^/\s)]+/docs/[^\s)#]*", re.IGNORECASE
)


@dataclass(frozen=True)
class Finding:
    check: str
    path: str
    message: str
    line: int | None = None

    def render(self) -> str:
        where = f"{self.path}:{self.line}" if self.line else self.path
        if os.environ.get("GITHUB_ACTIONS") == "true":
            ln = f",line={self.line}" if self.line else ""
            return f"::error file={self.path}{ln},title={self.check}::{self.message}"
        return f"[{self.check}] {where}: {self.message}"


@dataclass
class Page:
    rel: str  # path relative to the repository root, POSIX
    path: Path
    meta: dict[str, Any] | None
    meta_error: str | None
    text: str
    body_start: int  # 0-based line index where the body starts

    @property
    def docs_rel(self) -> str:
        return self.rel[len("docs/") :]


class Tree:
    """The docs tree under one repository root."""

    def __init__(self, root: Path) -> None:
        self.root = root.resolve()
        self.docs = self.root / "docs"
        self._pages: list[Page] | None = None
        self._generated_prefixes: tuple[str, ...] | None = None

    def pages(self) -> list[Page]:
        if self._pages is None:
            self._pages = [self._load(p) for p in self.iter_markdown()]
        return self._pages

    def iter_markdown(self) -> Iterator[Path]:
        for p in sorted(self.docs.rglob("*.md")):
            rel = p.relative_to(self.docs).as_posix()
            # _meta holds templates and registries, not pages; adr/template.md is the
            # pre-move location of the ADR template (placeholder frontmatter).
            if rel.startswith("_meta/") or rel == "adr/template.md":
                continue
            yield p

    def _load(self, path: Path) -> Page:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(self.root).as_posix()
        m = FRONTMATTER_RE.match(text)
        if not m:
            return Page(rel, path, None, "no frontmatter block", text, 0)
        start = text[: m.end()].count("\n")
        try:
            data = yaml.safe_load(m.group(1))
        except yaml.YAMLError as exc:
            return Page(rel, path, None, f"frontmatter is not valid YAML: {exc}", text, start)
        if not isinstance(data, dict):
            return Page(rel, path, None, "frontmatter is not a mapping", text, start)
        return Page(rel, path, data, None, text, start)

    def generated_route_prefixes(self) -> tuple[str, ...]:
        """Root-absolute route prefixes the site build generates (DOC_RULES, GENERATED_ROUTES_RULE)."""
        if self._generated_prefixes is None:
            data = load_yaml(self.root / DOC_RULES) or {}
            rules = data.get("rules") if isinstance(data, dict) else None
            rule = next(
                (r for r in rules or [] if isinstance(r, dict) and r.get("id") == GENERATED_ROUTES_RULE),
                {},
            )
            prefixes = rule.get("prefixes") or []
            self._generated_prefixes = tuple(
                p for p in prefixes if isinstance(p, str) and p.startswith("/") and p.endswith("/") and p != "/"
            )
        return self._generated_prefixes

    def is_generated(self, page: Page) -> bool:
        # Generator-owned = under docs/glossary/ AND carrying doc-kitty's own `generated:`
        # stamp. Keying the frontmatter/terms/link exemptions on the stamp, not the path
        # alone, ties them to the real ownership signal: a hand-authored page misfiled under
        # docs/glossary/, or a generated page that lost its stamp, is treated as authored and
        # stays subject to the full authored-only rules.
        return page.docs_rel.startswith("glossary/") and bool((page.meta or {}).get("generated"))

    # -- route resolution (doc-kitty: root-absolute routes, docs-root-relative slugs) --

    def resolve_doc(self, ref: str) -> Path | None:
        ref = ref.strip().lstrip("/").rstrip("/")
        if ref.endswith(".md"):
            ref = ref[:-3]
        candidates = (
            [self.docs / "README.md", self.docs / "index.md"]
            if not ref
            else [
                self.docs / f"{ref}.md",
                self.docs / ref / "README.md",
                self.docs / ref / "index.md",
            ]
        )
        return next((c for c in candidates if c.is_file()), None)

    def resolve_audience(self, value: str) -> bool:
        """A persona is a repo-relative .md path, or a profile slug, under the audience dir."""
        catalog = self.root / AUDIENCE_DIR
        value = value.strip()
        if value.endswith(".md"):
            target = (self.root / value).resolve()
            return target.is_file() and catalog.resolve() in target.parents
        if not catalog.is_dir() or not re.fullmatch(r"[a-z0-9][a-z0-9-]*", value):
            return False
        return any(p.stem == value for p in catalog.rglob("*.md"))


# ---------------------------------------------------------------- markdown helpers


def body_lines(page: Page) -> Iterator[tuple[int, str]]:
    """(1-based line number, line) of the body outside fenced code blocks."""
    fence: str | None = None
    for i, line in enumerate(page.text.splitlines()):
        if i < page.body_start:
            continue
        m = FENCE_RE.match(line)
        if m:
            fence = None if fence == m.group(1) else (fence or m.group(1))
            continue
        if fence is None:
            yield i + 1, line


def prose(line: str) -> str:
    """The line without inline code, link targets and bare URLs."""
    line = INLINE_CODE_RE.sub("", line)
    line = re.sub(r"\]\([^)]*\)", "]", line)
    return re.sub(r"https?://\S+", "", line)


def slugify(heading: str) -> str:
    """GitHub-slugger style, which Starlight uses for heading ids."""
    text = re.sub(r"[`*_]|<[^>]+>|\[([^\]]*)\]\([^)]*\)", lambda m: m.group(1) or "", heading)
    text = re.sub(r"[^\w\s-]", "", text.strip().lower())
    return re.sub(r"\s", "-", text)


def anchors_of(page: Page) -> set[str]:
    seen: dict[str, int] = {}
    out: set[str] = set()
    for _, line in body_lines(page):
        em = EXPLICIT_ANCHOR_RE.match(line.strip())
        if em:
            out.add(em.group(1))
            continue
        hm = HEADING_RE.match(line)
        if hm:
            slug = slugify(hm.group(2))
            n = seen.get(slug, 0)
            seen[slug] = n + 1
            out.add(slug if n == 0 else f"{slug}-{n}")
    return out


def as_date(value: Any) -> dt.date | None:
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    # The swept `updated:`/`stale_after:` instants (YYYY-MM-DDT00:00:00Z) must stay UNQUOTED
    # so PyYAML loads them as a datetime (handled by the branch above). A quoted instant would
    # arrive here as a str, fail the date-only fullmatch below, and be wrongly flagged.
    if isinstance(value, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        try:
            return dt.date.fromisoformat(value)
        except ValueError:
            return None
    return None


def audience_entries(value: Any) -> list[str] | None:
    """Persona references of an `audience:` value; None when the shape is wrong."""
    items = value if isinstance(value, list) else [value]
    out: list[str] = []
    for item in items:
        if isinstance(item, str):
            out.append(item)
        elif isinstance(item, dict) and isinstance(item.get("profile"), str):
            out.append(item["profile"])
        else:
            return None
    return out or None


# ---------------------------------------------------------------- checks


def check_frontmatter(tree: Tree, _today: dt.date) -> list[Finding]:
    out: list[Finding] = []
    for page in tree.pages():
        if page.meta is None:
            out.append(Finding("frontmatter", page.rel, page.meta_error or "unreadable"))
            continue
        meta = page.meta
        generated = tree.is_generated(page)
        for key in REQUIRED_KEYS:
            # doc-kitty forbids `type` on the bundle-root README (the site home).
            if key == "type" and page.rel == "docs/README.md":
                continue
            # A generated glossary page carries doc-kitty's own frontmatter stamp and
            # cannot hold these authored-only keys (see GENERATED_EXEMPT_KEYS).
            if generated and key in GENERATED_EXEMPT_KEYS:
                continue
            if meta.get(key) in (None, "", []):
                out.append(Finding("frontmatter", page.rel, f"missing required key `{key}`"))
        desc = meta.get("description")
        if not generated and isinstance(desc, str) and not DESC_MIN <= len(desc.strip()) <= DESC_MAX:
            out.append(
                Finding(
                    "frontmatter",
                    page.rel,
                    f"description is {len(desc.strip())} characters; must be {DESC_MIN}-{DESC_MAX}",
                )
            )
        status = meta.get("doc_status")
        if status is not None and status not in STATUSES:
            out.append(Finding("frontmatter", page.rel, f"doc_status `{status}` not in {sorted(STATUSES)}"))
        if meta.get("updated") is not None and as_date(meta["updated"]) is None:
            out.append(Finding("frontmatter", page.rel, f"`updated: {meta['updated']}` is not an ISO date"))
        if meta.get("audience") not in (None, "", []):
            refs = audience_entries(meta["audience"])
            if refs is None:
                out.append(Finding("frontmatter", page.rel, "`audience` must be persona refs or {profile, guidance_text}"))
            else:
                out.extend(
                    Finding("frontmatter", page.rel, f"audience `{ref}` does not resolve to a persona under {AUDIENCE_DIR}/")
                    for ref in refs
                    if not tree.resolve_audience(ref)
                )
    return out


def check_related(tree: Tree, _today: dt.date) -> list[Finding]:
    out: list[Finding] = []
    for page in tree.pages():
        related = (page.meta or {}).get("related")
        if related is None:
            continue
        if not isinstance(related, list):
            out.append(Finding("related", page.rel, "`related` must be a list"))
            continue
        for entry in related:
            ref = entry if isinstance(entry, str) else (entry or {}).get("ref") if isinstance(entry, dict) else None
            if not isinstance(ref, str):
                out.append(Finding("related", page.rel, f"unreadable related entry: {entry!r}"))
            elif not EXTERNAL_RE.match(ref) and tree.resolve_doc(ref.split("#")[0]) is None:
                out.append(Finding("related", page.rel, f"related `{ref}` does not resolve (use a docs-root slug such as adr/0001-x)"))
    return out


def check_freshness(tree: Tree, today: dt.date) -> list[Finding]:
    out: list[Finding] = []
    for page in tree.pages():
        meta = page.meta or {}
        updated = as_date(meta.get("updated"))
        if updated is None:
            continue  # reported by `frontmatter`
        if updated > today:
            out.append(Finding("freshness", page.rel, f"`updated: {updated}` is in the future"))
            continue
        status = meta.get("doc_status")
        doc_type = meta.get("type")
        sla = SLA_BY_TYPE[doc_type] if isinstance(doc_type, str) and doc_type in SLA_BY_TYPE else SLA_DAYS.get(status)
        if status in ("superseded", "durable"):
            sla = None
        age = (today - updated).days
        if sla is not None and age > sla:
            out.append(
                Finding(
                    "freshness",
                    page.rel,
                    f"stale: updated {updated} ({age} days ago); {status} pages must be re-verified within {sla} days",
                )
            )
    return out


def check_adr(tree: Tree, _today: dt.date) -> list[Finding]:
    adr_dir = tree.docs / "adr"
    out: list[Finding] = []
    readme = adr_dir / "README.md"
    if not readme.is_file():
        return [Finding("adr", "docs/adr/README.md", "ADR index is missing")]
    listed = re.findall(r"\(/adr/([^)/]+)/?\)", readme.read_text(encoding="utf-8"))
    numbers: dict[int, list[str]] = {}
    for p in sorted(adr_dir.glob("*.md")):
        if p.name in ADR_NON_ENTRIES:
            continue
        m = ADR_RE.match(p.name)
        if not m:
            out.append(Finding("adr", f"docs/adr/{p.name}", "filename must be NNNN-kebab-slug.md"))
            continue
        numbers.setdefault(int(m.group(1)), []).append(p.name)
        if p.stem not in listed:
            out.append(Finding("adr", f"docs/adr/{p.name}", "not listed in docs/adr/README.md"))
    for n, names in numbers.items():
        if len(names) > 1:
            out.append(Finding("adr", "docs/adr", f"ADR number {n:04d} used by {', '.join(names)}"))
    if numbers:
        missing = sorted(set(range(1, max(numbers) + 1)) - set(numbers))
        out.extend(Finding("adr", "docs/adr", f"gap in numbering: no ADR {n:04d}") for n in missing)
    return out


def check_changelog(tree: Tree, _today: dt.date) -> list[Finding]:
    cdir = tree.docs / "changelog"
    out: list[Finding] = []
    readme = cdir / "README.md"
    if not readme.is_file():
        return [Finding("changelog", "docs/changelog/README.md", "changelog index is missing")]
    index = readme.read_text(encoding="utf-8")
    listed = re.findall(r"\(/changelog/([^)/]+)/?\)", index)
    entries = sorted(p for p in cdir.glob("*.md") if p.name != "README.md")
    for p in entries:
        rel = f"docs/changelog/{p.name}"
        m = CHANGELOG_ENTRY_RE.match(p.name)
        if not m:
            out.append(Finding("changelog", rel, "filename must be YYYY-MM-DD-kebab-slug.md"))
            continue
        if as_date(m.group(1)) is None:
            out.append(Finding("changelog", rel, f"`{m.group(1)}` is not a real date"))
        if p.stem not in listed:
            out.append(Finding("changelog", rel, "not listed in docs/changelog/README.md"))
        size = len(p.read_text(encoding="utf-8"))
        if size > CHANGELOG_MAX_CHARS:
            out.append(Finding("changelog", rel, f"{size} characters; keep entries under {CHANGELOG_MAX_CHARS} (link to the docs for detail)"))
    dates = [s[:10] for s in listed]
    if dates != sorted(dates, reverse=True):
        out.append(Finding("changelog", "docs/changelog/README.md", "entries must be listed newest first"))
    return out


def load_yaml(path: Path) -> Any:
    return yaml.safe_load(path.read_text(encoding="utf-8")) if path.is_file() else None


def glossary_aliases(tree: Tree) -> list[dict[str, str]]:
    """Aliases of canonical terms in site/.contextive/definitions.yaml -> non-canonical names."""
    data = load_yaml(tree.root / "site" / ".contextive" / "definitions.yaml") or {}
    out = []
    for ctx in data.get("contexts", []) or []:
        for term in ctx.get("terms", []) or []:
            for alias in term.get("aliases", []) or []:
                out.append(
                    {
                        "term": alias,
                        "pattern": rf"\b{re.escape(alias)}\b",
                        "prefer": term["name"],
                        "reason": "alias in the imported glossary",
                    }
                )
    return out


def check_terms(tree: Tree, _today: dt.date) -> list[Finding]:
    cfg = load_yaml(tree.docs / "_meta" / "terminology.yaml")
    if not isinstance(cfg, dict):
        return [Finding("terms", "docs/_meta/terminology.yaml", "terminology file is missing or not a mapping")]
    rules = [("forbidden", r) for r in cfg.get("forbidden") or []]
    rules += [("avoid", r) for r in (cfg.get("avoid") or []) + glossary_aliases(tree)]
    allow = cfg.get("allow", []) or []
    out: list[Finding] = []
    for page in tree.pages():
        if tree.is_generated(page):
            continue  # copied verbatim from the core glossary; core owns its wording
        for lineno, line in body_lines(page):
            text = prose(line)
            for kind, rule in rules:
                if not re.search(rule["pattern"], text, re.IGNORECASE):
                    continue
                if any(
                    fnmatch.fnmatch(page.rel, a["path"]) and a.get("term") == rule["term"] for a in allow
                ):
                    continue
                out.append(
                    Finding(
                        "terms",
                        page.rel,
                        f"{kind} term `{rule['term']}`: use {rule['prefer']} ({rule['reason']})",
                        lineno,
                    )
                )
    return out


def internal_targets(page: Page) -> Iterator[tuple[int, str]]:
    for lineno, line in body_lines(page):
        for m in LINK_RE.finditer(INLINE_CODE_RE.sub("", line)):
            yield lineno, m.group(1)


def check_orphans(tree: Tree, _today: dt.date) -> list[Finding]:
    """Every page is linked from the hub (README.md / index.md) of its own directory."""
    out: list[Finding] = []
    pages = {p.path: p for p in tree.pages()}
    hub_links: dict[Path, set[Path]] = {}
    for page in pages.values():
        if page.path.name in ("README.md", "index.md"):
            targets: set[Path] = set()
            for _, t in internal_targets(page):
                if EXTERNAL_RE.match(t):
                    continue
                path = t.split("#")[0]
                if not path:
                    continue
                resolved = (
                    tree.resolve_doc(path)
                    if path.startswith("/")
                    else tree.resolve_doc(os.path.relpath((page.path.parent / path).resolve(), tree.docs))
                )
                if resolved:
                    targets.add(resolved)
            hub_links[page.path] = targets
    for page in pages.values():
        if page.path.parent == tree.docs and page.path.name in ("README.md", "index.md"):
            continue
        hub = next((page.path.parent / n for n in ("README.md", "index.md") if (page.path.parent / n).is_file()), None)
        if page.path.name in ("README.md", "index.md"):
            parent = page.path.parent.parent
            hub = next((parent / n for n in ("README.md", "index.md") if (parent / n).is_file()), None)
        if hub is None:
            out.append(Finding("orphans", page.rel, "its directory has no README.md hub"))
        elif page.path != hub and page.path not in hub_links.get(hub, set()):
            out.append(Finding("orphans", page.rel, f"not linked from its hub {hub.relative_to(tree.root).as_posix()}"))
    return out


def check_links(tree: Tree, _today: dt.date) -> list[Finding]:
    out: list[Finding] = []
    anchor_cache: dict[Path, set[str]] = {}
    pages = {p.path: p for p in tree.pages()}

    def anchors(path: Path) -> set[str]:
        if path not in anchor_cache:
            anchor_cache[path] = anchors_of(pages[path]) if path in pages else set()
        return anchor_cache[path]

    for page in tree.pages():
        for lineno, line in body_lines(page):
            for m in SELF_GITHUB_RE.finditer(line):
                if re.search(r"\.(?!md$)[A-Za-z0-9]+$", m.group(0)):
                    continue  # a file that is not a page (template, diagram source) has no route
                out.append(Finding("links", page.rel, f"absolute link to this repository's own docs ({m.group(0)}...); use a root-absolute route", lineno))
            text = INLINE_CODE_RE.sub("", line)
            targets = [m.group(1) for m in LINK_RE.finditer(text)] + [m.group(1) for m in IMAGE_RE.finditer(text)]
            for target in targets:
                out.extend(_check_target(tree, page, lineno, target, anchors))
    return out


def _check_target(tree: Tree, page: Page, lineno: int, target: str, anchors: Callable[[Path], set[str]]) -> list[Finding]:
    if EXTERNAL_RE.match(target):
        return []
    path, _, frag = target.partition("#")
    bad = lambda msg: [Finding("links", page.rel, msg, lineno)]  # noqa: E731
    if not path:
        return [] if not frag or frag in anchors(page.path) else bad(f"anchor `#{frag}` not found on this page")
    if path.startswith(SITE_BASE + "/"):
        return bad(f"`{target}` includes the site base path; the toolkit adds it")
    if path.startswith("/"):
        if _under_generated_route(tree, path):
            # Built by the site build (for example the Javadoc), so no source file exists here;
            # check:built-links checks it in the built site.
            return []
        doc = tree.resolve_doc(path)
        if doc is None:
            asset = tree.docs / path.lstrip("/")
            return [] if asset.is_file() else bad(f"`{target}` does not resolve to a page or file")
    else:
        resolved = (page.path.parent / path).resolve()
        if tree.is_generated(page) and (resolved.suffix == ".md" or resolved.is_dir()):
            # The glossary is copied from core with its own relative links: they must resolve,
            # but their style is core's, not ours.
            return [] if resolved.exists() else bad(f"`{target}` does not exist")
        if resolved.suffix == ".md" or resolved.is_dir():
            return bad(f"`{target}` is a relative page link; use a root-absolute route such as /architecture/overview/")
        return [] if resolved.is_file() else bad(f"`{target}` does not exist")
    if frag and frag not in anchors(doc):
        return bad(f"anchor `#{frag}` not found in {doc.relative_to(tree.root).as_posix()}")
    return []


def _under_generated_route(tree: Tree, path: str) -> bool:
    """A link is under a generated route only when its raw path and its decoded path both start
    with the prefix, and the decoded, normalized path stays inside it. Decoding, as a browser
    does, catches `%2e%2e` escapes; the raw test refuses `/javadoc%2Fx.html`, one segment to a
    server."""
    decoded = urllib.parse.unquote(path)
    normalized = posixpath.normpath(decoded)
    if decoded.endswith("/"):
        normalized += "/"
    return any(
        path.startswith(prefix) and decoded.startswith(prefix) and normalized.startswith(prefix)
        for prefix in tree.generated_route_prefixes()
    )


# ---------------------------------------------------------------- PlantUML

PINS = "plantuml_pins.json"


_SVG_SEMANTIC_ATTRS = (
    "id",
    "class",
    "data-qualified-name",
    "data-entity-1",
    "data-entity-2",
    "data-link-type",
    "data-source-line",
    "data-diagram-type",
    "href",
    "{http://www.w3.org/1999/xlink}href",
)


def _normalise_svg(svg: str) -> str:
    """Reduce an SVG to its semantic content: text, entity identity and link targets.

    Geometry, styles, fonts, text widths, path data and element counts depend on the fonts and
    Java of the machine that renders, so none of them is compared. What is compared: the sorted
    texts, plus the sorted (id, class, entity, relation, source line, link) attributes PlantUML
    stamps on its elements. A changed label, class, member, relation or link still differs.
    """
    root = ElementTree.fromstring(svg)
    texts: list[str] = []
    marks: list[str] = []
    for el in root.iter():
        tag = el.tag.rsplit("}", 1)[-1]
        if tag == "text":
            texts.append(" ".join("".join(el.itertext()).split()))
        found = [f"{a.rsplit('}', 1)[-1]}={el.attrib[a]}" for a in _SVG_SEMANTIC_ATTRS if a in el.attrib]
        if found:
            marks.append(f"{tag} " + " ".join(found))
    return "\n".join([*sorted(t for t in texts if t), "--", *sorted(marks)])


def _plantuml_command(pins: dict[str, str]) -> list[str]:
    jar = os.environ.get("PLANTUML_JAR")
    if jar:
        digest = hashlib.sha256(Path(jar).read_bytes()).hexdigest()
        if digest != pins["plantuml_jar_sha256"]:
            raise SystemExit(f"PLANTUML_JAR sha256 {digest} != pinned {pins['plantuml_jar_sha256']}")
        return ["java", "-Djava.awt.headless=true", "-jar", jar]
    exe = shutil.which("plantuml")
    if exe is None:
        raise SystemExit("plantuml not found: install PlantUML or set PLANTUML_JAR to the pinned jar")
    return [exe, "-Djava.awt.headless=true"]


def check_plantuml(tree: Tree, _today: dt.date, strict: bool = False) -> list[Finding]:
    pins = json.loads((Path(__file__).with_name(PINS)).read_text(encoding="utf-8"))
    cmd = _plantuml_command(pins)
    version = subprocess.run([*cmd, "-version"], capture_output=True, text=True, check=False).stdout
    if pins["plantuml_version"] not in version:
        raise SystemExit(f"PlantUML {pins['plantuml_version']} required; found: {version.splitlines()[:1]}")
    out: list[Finding] = []
    diagrams = tree.docs / "architecture" / "diagrams"
    for puml in sorted(diagrams.glob("*.puml")):
        rel = puml.relative_to(tree.root).as_posix()
        committed = puml.with_suffix(".svg")
        if not committed.is_file():
            out.append(Finding("plantuml", rel, f"no committed {committed.name}; render it"))
            continue
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copytree(diagrams, tmp, dirs_exist_ok=True, ignore=shutil.ignore_patterns("*.svg"))
            proc = subprocess.run([*cmd, "-tsvg", str(Path(tmp) / puml.name)], capture_output=True, text=True, check=False)
            rendered = Path(tmp) / committed.name
            if proc.returncode != 0 or not rendered.is_file():
                out.append(Finding("plantuml", rel, f"does not render: {(proc.stderr or proc.stdout).strip()[:200]}"))
                continue
            keep = os.environ.get("PLANTUML_RENDER_DIR")  # CI uploads this so a mismatch can be diagnosed
            if keep:
                Path(keep).mkdir(parents=True, exist_ok=True)
                shutil.copy2(rendered, Path(keep) / committed.name)
            new, old = rendered.read_text(encoding="utf-8"), committed.read_text(encoding="utf-8")
            same = new == old if strict else _normalise_svg(new) == _normalise_svg(old)
            if not same:
                out.append(Finding("plantuml", rel, f"{committed.name} is stale; re-render it with `plantuml -tsvg {puml.name}` in its directory"))
    orphans = {p.stem for p in diagrams.glob("*.svg")} - {p.stem for p in diagrams.glob("*.puml")}
    out.extend(Finding("plantuml", f"docs/architecture/diagrams/{s}.svg", "SVG has no .puml source") for s in sorted(orphans))
    return out


# ---------------------------------------------------------------- word list


def glossary_words(tree: Tree) -> list[str]:
    data = load_yaml(tree.root / "site" / ".contextive" / "definitions.yaml") or {}
    words: set[str] = set()
    for ctx in data.get("contexts", []) or []:
        words.update(re.findall(r"[A-Za-z][A-Za-z']+", ctx.get("name", "")))
        for term in ctx.get("terms", []) or []:
            words.update(re.findall(r"[A-Za-z][A-Za-z']+", term["name"]))
    return sorted(w.lower() for w in words if len(w) > 3)


def cmd_wordlist(tree: Tree, _today: dt.date) -> list[Finding]:
    """Print glossary-derived words not yet in docs/_meta/spelling-words.txt."""
    path = tree.docs / "_meta" / "spelling-words.txt"
    have = {w.strip().lower() for w in path.read_text(encoding="utf-8").splitlines()} if path.is_file() else set()
    for word in glossary_words(tree):
        if word not in have:
            print(word)
    return []


CHECKS: dict[str, Callable[..., list[Finding]]] = {
    "frontmatter": check_frontmatter,
    "related": check_related,
    "freshness": check_freshness,
    "adr": check_adr,
    "changelog": check_changelog,
    "terms": check_terms,
    "orphans": check_orphans,
    "links": check_links,
    "plantuml": check_plantuml,
    "wordlist": cmd_wordlist,
}
ALL = ["frontmatter", "related", "freshness", "adr", "changelog", "terms", "orphans", "links"]


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today())
    ap.add_argument("--strict-svg", action="store_true", help="plantuml: require byte-identical SVGs")
    ap.add_argument("checks", nargs="+", metavar="check", help="one or more of: " + ", ".join([*CHECKS, "all"]))
    args = ap.parse_args(argv)
    names = [n for c in args.checks for n in (ALL if c == "all" else [c])]
    unknown = [n for n in names if n not in CHECKS]
    if unknown:
        ap.error(f"unknown check(s): {unknown}; choose from {[*CHECKS, 'all']}")
    tree = Tree(args.root)
    if not tree.docs.is_dir():
        print(f"no docs/ under {tree.root}", file=sys.stderr)
        return 2
    failed = 0
    for name in names:
        extra = {"strict": args.strict_svg} if name == "plantuml" else {}
        findings = CHECKS[name](tree, args.today, **extra)
        for f in findings:
            print(f.render())
        if name != "wordlist":
            print(f"{name}: {'FAIL, ' + str(len(findings)) + ' finding(s)' if findings else 'ok'}", file=sys.stderr)
        failed += len(findings)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
