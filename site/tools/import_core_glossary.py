#!/usr/bin/env python3
"""Import the charter-related glossary contexts from the Spec Kitty core repository.

Writes site/.contextive/definitions.yaml, the Contextive file doc-kitty builds the
glossary pages from. The core glossary (docs/context/*.md in spec-kitty/spec-kitty) is the
authority; this file is a copy and is never edited by hand.

Usage, from the repository root, with a checkout of the core repository:

    python3 site/tools/import_core_glossary.py /path/to/spec-kitty

It reuses the core repository's own parser (scripts/generate_contextive_glossaries.py), so
a term is read here exactly as core reads it for its IDE glossaries.
"""

from __future__ import annotations

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


def main() -> int:
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
        f"# at core commit {commit}. The core glossary is the authority: change a term there, then",
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
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
