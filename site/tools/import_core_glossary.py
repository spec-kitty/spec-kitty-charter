#!/usr/bin/env python3
"""Import the charter-related glossary contexts from the Spec Kitty core repository.

Writes site/.contextive/definitions.yaml, the Contextive file doc-kitty builds the
glossary pages from. The core glossary (docs/context/*.md in spec-kitty/spec-kitty) is the
authority; this file is a copy and is never edited by hand.

Usage, from the repository root, with a checkout of the core repository:

    python3 site/tools/import_core_glossary.py /path/to/spec-kitty

The doc-kitty build regenerates docs/glossary/ with minimal frontmatter on every run. This
script owns the rest: after an import, and after every build (`npm run build` calls
`--finalize`), it rewrites the frontmatter of those pages to the full set the docs checks
require (description, doc_status, updated = the import date, type, kind, audience and
`source_ref` = the core commit). Run it alone with:

    python3 site/tools/import_core_glossary.py --finalize

It reuses the core repository's own parser (scripts/generate_contextive_glossaries.py), so
a term is read here exactly as core reads it for its IDE glossaries.
"""

from __future__ import annotations

import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

# Core context file (docs/context/<slug>.md) -> the context's name and one-line purpose here.
CONTEXTS = {
    "charter": (
        "Charter",
        "The governing term `charter` and the doctrine domain model: Charter Packs, artifact "
        "kinds, activation, selection and the DRG relations.",
    ),
    "governance": (
        "Governance",
        "Rule ownership, precedence and policy controls: the charter interview and compiler, "
        "pack levels, and agent-profile activation.",
    ),
    "product-lines": (
        "Product lines",
        "The product lines of Spec Kitty Inc.: Build Kitty, Brain Kitty and Team Kitty.",
    ),
}

# Core defines `charter` twice: the canonical entry in charter.md and an older one in
# governance.md. Two contexts owning one term stop doc-kitty from linking it, so only the
# canonical entry is imported.
SKIP = {("governance", "Charter")}

OUTPUT = Path(__file__).resolve().parents[1] / ".contextive" / "definitions.yaml"
GLOSSARY_DIR = Path(__file__).resolve().parents[2] / "docs" / "glossary"

# The readers the imported vocabulary serves (personas under docs/context/audience/).
AUDIENCE = (
    ("charter-service-contributor", "Read this for the vocabulary the Java domain must use unchanged."),
    ("pack-author", "Read this for the pack and activation terms you write against."),
)
HUB_DESCRIPTION = (
    "The charter vocabulary, imported from the Spec Kitty core glossary and grouped by the "
    "bounded context that owns each term."
)



def main() -> int:
    if sys.argv[1:] == ["--finalize"]:
        return finalize()
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    core = Path(sys.argv[1]).resolve()
    sys.path.insert(0, str(core / "scripts"))
    import generate_contextive_glossaries as core_glossary  # noqa: PLC0415 (path set above)

    quote = core_glossary._yaml_quote
    commit = subprocess.run(
        ["git", "-C", str(core), "rev-parse", "--short=10", "HEAD"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()

    lines = [
        "# Contextive Community definitions (format v1): the charter glossary.",
        "#",
        "# IMPORTED. Do not edit by hand. The terms are copied from the Spec Kitty core glossary",
        "# (spec-kitty/spec-kitty, docs/context/charter.md, governance.md and product-lines.md)",
        f"# at core commit {commit}, imported on {dt.date.today().isoformat()}. The core glossary is the",
        "# authority: change a term there, then",
        "# run site/tools/import_core_glossary.py again. See docs/guides/docsite.md.",
        "#",
        "# doc-kitty builds docs/glossary/ from this file and links the terms across the docs.",
        "contexts:",
    ]
    count = 0
    for slug, (name, purpose) in CONTEXTS.items():
        context = core_glossary.parse_context_file(core / "docs" / "context" / f"{slug}.md")
        lines.append(f"  - name: {quote(name)}")
        lines.append(f"    domainVisionStatement: {quote(purpose)}")
        lines.append("    terms:")
        for term in context.terms:
            if (slug, term.name) in SKIP:
                continue
            count += 1
            lines.append(f"      - name: {quote(term.name)}")
            lines.append(f"        definition: {quote(term.definition)}")
            if term.aliases:
                lines.append("        aliases:")
                lines.extend(f"          - {quote(alias)}" for alias in term.aliases)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{count} terms in {len(CONTEXTS)} contexts -> {OUTPUT}")
    print("Now run `cd site && npm run build` to regenerate docs/glossary/, which finalizes it.")
    return 0


def _clip(text: str, limit: int = 180) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 1].rsplit(" ", 1)[0].rstrip(",;:") + "\u2026"


def _frontmatter(fields: dict[str, str], commit: str, date: str) -> str:
    quote = lambda v: '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'  # noqa: E731
    lines = ["---"]
    lines += [f"title: {quote(fields['title'])}", f"description: {quote(fields['description'])}"]
    lines += ["doc_status: active", f"updated: {date}", "type: Glossary", f"kind: {fields['kind']}"]
    lines += [f"glossary_context: {quote(fields['context'])}"] if "context" in fields else []
    lines += ["audience:"]
    for profile, guidance in AUDIENCE:
        lines += [f"  - profile: {profile}", f"    guidance_text: {quote(guidance)}"]
    lines += [f"source_ref: {commit}", "---"]
    return "\n".join(lines) + "\n"


def finalize() -> int:
    """Give the generated glossary pages the full frontmatter; idempotent."""
    header = OUTPUT.read_text(encoding="utf-8")
    commit = re.search(r"core commit ([0-9a-f]{7,40})", header)
    date = re.search(r"imported on (\d{4}-\d{2}-\d{2})", header)
    if not commit or not date:
        print(f"{OUTPUT} lacks the core commit / import date header; run the import first", file=sys.stderr)
        return 2
    by_slug = {slug: (name, purpose) for slug, (name, purpose) in CONTEXTS.items()}
    pages = sorted(GLOSSARY_DIR.rglob("index.md"))
    if not pages:
        print(f"no generated pages under {GLOSSARY_DIR}; run the site build first", file=sys.stderr)
        return 2
    for page in pages:
        text = page.read_text(encoding="utf-8")
        match = re.match(r"---\n.*?\n---\n", text, re.DOTALL)
        if not match:
            print(f"{page}: no frontmatter block", file=sys.stderr)
            return 2
        old = match.group(0)
        title = re.search(r'^title: "(.*)"$', old, re.MULTILINE)
        slug = page.parent.name
        if page.parent == GLOSSARY_DIR:
            fields = {"title": "Glossary", "description": HUB_DESCRIPTION, "kind": "Hub"}
        elif slug in by_slug:
            name, purpose = by_slug[slug]
            fields = {
                "title": name,
                "description": _clip(f"Glossary terms of the {name} context, imported from the core glossary. {purpose}"),
                "kind": "Glossary",
                "context": name,
            }
        else:
            print(f"{page}: context `{slug}` is not in CONTEXTS", file=sys.stderr)
            return 2
        if title and title.group(1) != fields["title"]:
            print(f"{page}: unexpected title {title.group(1)!r}", file=sys.stderr)
            return 2
        page.write_text(_frontmatter(fields, commit.group(1), date.group(1)) + text[match.end():], encoding="utf-8")
    print(f"finalized {len(pages)} glossary pages (source_ref {commit.group(1)}, updated {date.group(1)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
