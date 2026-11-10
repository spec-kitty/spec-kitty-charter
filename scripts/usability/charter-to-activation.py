#!/usr/bin/env python3
"""Translate .kittify/charter/charter.yaml into an activation document (ActivationConfig, schemaVersion 2).

Run as ``python3 -I scripts/usability/charter-to-activation.py`` (standard library only). The document goes to
standard output as JSON; notes go to standard error.

    --charter PATH     the charter file (default: .kittify/charter/charter.yaml of this repository)
    --profile ID       the agent profile to add to selected.agent_profile (default java-jenny)
    --drop KIND:ID     leave one selection out (repeatable); the caller names every one, so nothing is dropped silently
    --self-test        check every slug of the table against the numbering rule and the charter, then exit
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent

# Directive slugs as the charter spells them, to the ids the service holds (gap 6 of the usability note).
DIRECTIVE_IDS: dict[str, str] = {
    "001-architectural-integrity-standard": "DIRECTIVE_001",
    "003-decision-documentation-requirement": "DIRECTIVE_003",
    "010-specification-fidelity-requirement": "DIRECTIVE_010",
    "025-boy-scout-rule": "DIRECTIVE_025",
    "030-test-and-typecheck-quality-gate": "DIRECTIVE_030",
    "032-conceptual-alignment": "DIRECTIVE_032",
    "034-test-first-development": "DIRECTIVE_034",
    "037-living-documentation-sync": "DIRECTIVE_037",
    "041-tests-as-scaffold-not-friction": "DIRECTIVE_041",
    "051-supply-chain-install-safety": "DIRECTIVE_051",
    "no-full-heavy-suites-in-mission": "NO_FULL_HEAVY_SUITES_IN_MISSION",
    "qa-003-no-fake-green": "QA_003",
    "reconcile-change-scope-tensions": "RECONCILE_CHANGE_SCOPE_TENSIONS",
    "use-c4-model-techniques": "USE_C4_MODEL_TECHNIQUES",
    # The project pack's own directives (.kittify/charter-packs/directive/).
    "001-project-intent-directive": "PROJECT_001",
    "002-risk-appetite-directive": "PROJECT_002",
}

# charter.yaml key -> activation kind. Slugs of every kind but directive are the service's ids, unchanged: a slug a
# pack does not hold is the service's refusal to report, not this script's to guess at.
KIND_OF_KEY: dict[str, str] = {
    "mission_type_activations": "mission_type",
    "activated_directives": "directive",
    "activated_paradigms": "paradigm",
    "activated_styleguides": "styleguide",
    "activated_toolguides": "toolguide",
    "activated_procedures": "procedure",
    "activated_glossary_packs": "glossary_pack",
}


class UnknownSlug(Exception):
    """A directive slug the table does not name; the table is extended by hand, never guessed."""


class UnmappedKey(Exception):
    """A charter key this script would have to drop: an ``activated_*`` key it does not map, or a mapped key whose
    list it cannot read. Failing is the point: a selection must never vanish from the document without a word."""


def read_sections(text: str) -> dict[str, list[str]]:
    """The top-level ``key:`` blocks of the charter that are plain ``- item`` lists, plus ``catalog.languages``.

    A line parser is enough for the keys this script reads, and keeps the script free of third-party modules.
    It raises UnmappedKey for any top-level ``activated_*`` key that KIND_OF_KEY does not map, and for a mapped key
    whose list is not a plain column-0 ``- item`` list (an inline ``[a, b]`` or an indented list), because both
    would otherwise be dropped without a word.
    """
    sections: dict[str, list[str]] = {}
    current: list[str] | None = None
    in_catalog = False
    in_languages = False
    top_key = ""
    for raw in text.splitlines():
        line = raw.rstrip()
        if not line or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" ") and not line.startswith("- "):
            key, _, rest = line.partition(":")
            top_key = key
            if key.startswith("activated_") and key not in KIND_OF_KEY:
                raise UnmappedKey(
                    f"top-level key '{key}' is an activation list this script does not map (KIND_OF_KEY); "
                    "add it to KIND_OF_KEY, do not let it vanish"
                )
            if key in KIND_OF_KEY and rest.strip() not in ("", "[]"):
                raise UnmappedKey(f"key '{key}' holds '{rest.strip()}' inline; only a '- item' block list is read")
            in_catalog = key == "catalog"
            in_languages = False
            current = sections.setdefault(key, []) if line.endswith(":") else None
        elif line.startswith("- ") and current is not None:
            current.append(line[2:].strip().strip("'\""))
        elif top_key in KIND_OF_KEY and line.startswith(" ") and line.lstrip().startswith("- "):
            raise UnmappedKey(
                f"an indented list item '{line.strip()}' under a top-level key; only column-0 '- item' lists are read"
            )
        elif in_catalog and line.strip() == "languages:" and line.startswith("  languages:"):
            in_languages = True
            current = sections.setdefault("languages", [])
        elif in_languages and line.startswith("  - "):
            current = sections["languages"]
            current.append(line[4:].strip().strip("'\""))
        elif in_catalog and line.startswith("  ") and not line.startswith("  - "):
            in_languages = False
            current = None
    return sections


def nested_list(text: str, path: list[str]) -> list[str]:
    """The ``- item`` list at ``path`` (for example governance, charter, selected_directives) of a block-style YAML
    file. Used by the self-test only, to compare the table with the selections the charter itself names."""
    stack: list[tuple[int, str]] = []
    items: list[str] = []
    found = False
    for raw in text.splitlines():
        line = raw.rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        body = line.strip()
        if body.startswith("- "):
            if found and [k for _, k in stack] == path:
                items.append(body[2:].strip().strip("'\""))
            continue
        while stack and stack[-1][0] >= indent:
            stack.pop()
        key = body.split(":", 1)[0]
        stack.append((indent, key))
        found = found or [k for _, k in stack] == path
        if found and [k for _, k in stack][: len(path)] != path:
            break
    return items


def translate(sections: dict[str, list[str]], profile: str, dropped: set[str]) -> dict:
    """The activation document: every activated artifact of the charter, plus the agent profile.

    ``dropped`` holds ``kind:id`` entries the caller chose to leave out; they are left out of the document only.
    """
    selected: dict[str, list[str]] = {}
    for key, kind in KIND_OF_KEY.items():
        ids = []
        for slug in sections.get(key, []):
            if kind == "directive":
                if slug not in DIRECTIVE_IDS:
                    raise UnknownSlug(f"directive slug '{slug}' is not in DIRECTIVE_IDS; add it to the table")
                ids.append(DIRECTIVE_IDS[slug])
            else:
                ids.append(slug)
        kept = sorted({i for i in ids if f"{kind}:{i}" not in dropped})
        if kept:
            selected[kind] = kept
    if f"agent_profile:{profile}" not in dropped:
        selected["agent_profile"] = [profile]
    document: dict = {"schemaVersion": 2, "selected": selected}
    if sections.get("languages"):
        document["languages"] = list(sections["languages"])
    return document


def self_test() -> int:
    failures = []

    def check(label: str, actual: object, expected: object) -> None:
        if actual != expected:
            failures.append(f"{label}: expected {expected!r}, got {actual!r}")

    # Every entry of the table, pinned from outside the table: a numbered slug "NNN-..." is DIRECTIVE_NNN, and the
    # six others are listed here by hand. A wrong id in the table would reach the service as a 422 unknown_artifact
    # and look like a pack gap, so each one is checked, not a sample.
    unnumbered = {
        "001-project-intent-directive": "PROJECT_001",
        "002-risk-appetite-directive": "PROJECT_002",
        "no-full-heavy-suites-in-mission": "NO_FULL_HEAVY_SUITES_IN_MISSION",
        "qa-003-no-fake-green": "QA_003",
        "reconcile-change-scope-tensions": "RECONCILE_CHANGE_SCOPE_TENSIONS",
        "use-c4-model-techniques": "USE_C4_MODEL_TECHNIQUES",
    }
    numbered = {
        slug: "DIRECTIVE_" + slug[:3]
        for slug in DIRECTIVE_IDS
        if re.match(r"^\d{3}-", slug) and slug not in unnumbered
    }
    expected = {**numbered, **unnumbered}
    check("table size", len(DIRECTIVE_IDS), 16)
    check("numbered slugs", len(numbered), 10)
    for slug, want in sorted(expected.items()):
        check(f"slug {slug}", DIRECTIVE_IDS.get(slug), want)
    check("slugs of the table", sorted(DIRECTIVE_IDS), sorted(expected))
    sample = (
        "overrides: {}\n"
        "catalog:\n  mission: software-dev\n  languages:\n  - java\n  - python\n  references:\n  - id: x\n"
        "mission_type_activations:\n- software-dev\n"
        "activated_directives:\n- 034-test-first-development\n- 001-project-intent-directive\n"
        "activated_toolguides:\n- terminology-guard\n"
        "metadata:\n  a: b\n"
    )
    document = translate(read_sections(sample), "java-jenny", set())
    check("document", document, {
        "schemaVersion": 2,
        "selected": {
            "mission_type": ["software-dev"],
            "directive": ["DIRECTIVE_034", "PROJECT_001"],
            "toolguide": ["terminology-guard"],
            "agent_profile": ["java-jenny"],
        },
        "languages": ["java", "python"],
    })
    check("drop", translate(read_sections(sample), "java-jenny", {"directive:PROJECT_001"})["selected"]["directive"],
          ["DIRECTIVE_034"])
    try:
        translate({"activated_directives": ["no-such-slug"]}, "java-jenny", set())
        failures.append("an unknown directive slug was accepted")
    except UnknownSlug:
        pass
    for label, text, want in (
        ("an unmapped activated_ key", "activated_tactics:\n- x\n", UnmappedKey),
        ("an unmapped empty activated_ key", "activated_agent_profiles: []\n", UnmappedKey),
        ("an inline list", "activated_toolguides: [a, b]\n", UnmappedKey),
        ("an indented list", "activated_toolguides:\n  - a\n", UnmappedKey),
    ):
        try:
            read_sections(text)
            failures.append(f"{label} was accepted")
        except want:
            pass
    live = REPO / ".kittify" / "charter" / "charter.yaml"
    if not live.is_file():
        failures.append(f"{live} does not exist; the cross-check against the charter's selections cannot run")
    else:
        text = live.read_text(encoding="utf-8")
        try:
            translated = translate(read_sections(text), "java-jenny", set())
        except (UnknownSlug, UnmappedKey) as error:
            failures.append(f"this repository's charter: {error}")
            translated = {"selected": {}}
        selected = nested_list(text, ["governance", "charter", "selected_directives"])
        if not selected:
            failures.append("governance.charter.selected_directives was not found or is empty in the live charter")
        for directive in selected:
            if directive not in DIRECTIVE_IDS.values():
                failures.append(f"charter selects {directive}, which no value of the table is")
            if directive not in translated["selected"].get("directive", []):
                failures.append(f"charter selects {directive}, which the translated document does not carry")
    for failure in failures:
        print(f"FAIL {failure}", file=sys.stderr)
    print("self-test: " + ("FAILED" if failures else "ok"), file=sys.stderr)
    return 1 if failures else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--charter", default=str(REPO / ".kittify" / "charter" / "charter.yaml"))
    parser.add_argument("--profile", default="java-jenny")
    parser.add_argument("--drop", action="append", default=[], metavar="KIND:ID")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    try:
        text = Path(args.charter).read_text(encoding="utf-8")
        document = translate(read_sections(text), args.profile, set(args.drop))
    except (OSError, UnknownSlug, UnmappedKey) as error:
        print(f"charter-to-activation: {error}", file=sys.stderr)
        return 3
    json.dump(document, sys.stdout, indent=2, sort_keys=False)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
