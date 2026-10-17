"""qa_promise_trace_lint — check the promise -> scenario chain before a gating run.

Reads two trees and reconciles them:

* **Promise pages**: Markdown pages in a DocKitty (Common Docs) docs tree whose
  frontmatter declares ``type: Promise`` and a ``promise:`` block. A promise page
  is the human-readable product description; its ``promise.id`` is the stable key
  everything else cites.
* **Scenario files**: Gherkin ``*.feature`` files. A scenario cites the promises it
  checks with ``@promise:<id>`` tags (on the scenario or inherited from the
  feature). Manual scenarios carry ``@manual`` plus Serenity-style result tags.

It never runs a scenario. It answers one question for the CI job that gates a
release: is every ratified promise checked by at least one scenario, and does
every scenario check a ratified promise? Running the scenarios stays with the
project's own runner.

Portability: stdlib + ``ruamel.yaml`` only, matching ``qa_carrier_lint.py``.

Invocation::

    python qa_promise_trace_lint.py --promises DOCS_DIR --scenarios FEATURES_DIR
        [--release VERSION] [--id-pattern REGEX] [--out PATH] [--json]
    python qa_promise_trace_lint.py --self-test

Exit ``0`` (chain intact), ``1`` (blocking findings), ``2`` (cannot measure:
no promise pages, no scenarios, or a parse error). Every finding list is
reported on every run, even when empty, so an empty list is evidence rather
than an omission (QA_001, QA_003).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ruamel.yaml import YAML
from ruamel.yaml.error import YAMLError

DEFAULT_ID_PATTERN = r"^PR-[A-Z0-9]+(?:-[A-Z0-9]+)*-\d{3,}$"
PROMISE_TYPE = "promise"
STATUSES = ("candidate", "ratified", "retired")
MANUAL_RESULTS = ("passed", "failed", "pending")

_FRONTMATTER = re.compile(r"\A---\s*\n(.*?)\n---\s*(?:\n|\Z)", re.DOTALL)
_SCENARIO = re.compile(r"^\s*(Scenario Outline|Scenario Template|Scenario|Example)\s*:\s*(.*)$")
_FEATURE = re.compile(r"^\s*Feature\s*:\s*(.*)$")
_RULE_OR_BACKGROUND = re.compile(r"^\s*(Rule|Background|Examples|Scenarios)\s*:")
_TAG = re.compile(r"@[^\s@]+")


class CannotMeasure(Exception):
    """Raised when the inputs cannot support a verdict at all (exit 2)."""


@dataclass
class Promise:
    id: str
    status: str
    path: str
    title: str
    ratified_by: str | None = None
    ratified_at: str | None = None
    gap: dict[str, Any] | None = None


@dataclass
class Scenario:
    name: str
    path: str
    line: int
    tags: list[str] = field(default_factory=list)

    def tag_values(self, prefix: str) -> list[str]:
        return [t[len(prefix) :] for t in self.tags if t.startswith(prefix)]

    @property
    def promise_ids(self) -> list[str]:
        return self.tag_values("@promise:")

    @property
    def manual(self) -> bool:
        return "@manual" in self.tags

    @property
    def ref(self) -> str:
        return f"{self.path}:{self.line} {self.name}"


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------
def _read_frontmatter(path: Path, yaml: YAML) -> dict[str, Any] | None:
    text = path.read_text(encoding="utf-8")
    match = _FRONTMATTER.match(text)
    if not match:
        return None
    try:
        data = yaml.load(match.group(1))
    except YAMLError as exc:
        raise CannotMeasure(f"{path}: unreadable frontmatter: {exc}") from exc
    return data if isinstance(data, dict) else None


def load_promises(root: Path) -> tuple[list[Promise], list[str]]:
    """Return promise pages under ``root`` and pages that declare the type but no block."""
    yaml = YAML(typ="safe")
    promises: list[Promise] = []
    missing_block: list[str] = []
    for path in sorted(root.rglob("*.md")):
        meta = _read_frontmatter(path, yaml)
        if not meta or str(meta.get("type", "")).strip().lower() != PROMISE_TYPE:
            continue
        rel = path.relative_to(root).as_posix()
        block = meta.get("promise")
        if not isinstance(block, dict) or not block.get("id"):
            missing_block.append(rel)
            continue
        gap = block.get("gap")
        promises.append(
            Promise(
                id=str(block["id"]).strip(),
                status=str(block.get("status", "")).strip().lower(),
                path=rel,
                title=str(meta.get("title", "")),
                ratified_by=block.get("ratified_by"),
                ratified_at=str(block["ratified_at"]) if block.get("ratified_at") else None,
                gap=dict(gap) if isinstance(gap, dict) else None,
            )
        )
    return promises, missing_block


def parse_feature(path: Path, rel: str) -> list[Scenario]:
    """Parse scenario headers and their effective tags (feature tags are inherited)."""
    feature_tags: list[str] = []
    pending: list[str] = []
    scenarios: list[Scenario] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("@"):
            pending.extend(_TAG.findall(line))
            continue
        if _FEATURE.match(line):
            feature_tags, pending = pending, []
            continue
        scenario = _SCENARIO.match(line)
        if scenario:
            tags = list(dict.fromkeys(feature_tags + pending))
            scenarios.append(Scenario(name=scenario.group(2).strip(), path=rel, line=number, tags=tags))
            pending = []
            continue
        if _RULE_OR_BACKGROUND.match(line):
            pending = []
    return scenarios


def load_scenarios(root: Path) -> list[Scenario]:
    scenarios: list[Scenario] = []
    for path in sorted(root.rglob("*.feature")):
        scenarios.extend(parse_feature(path, path.relative_to(root).as_posix()))
    return scenarios


# ---------------------------------------------------------------------------
# Reconciliation
# ---------------------------------------------------------------------------
def _check_promises(promises: list[Promise], id_pattern: re.Pattern[str]) -> dict[str, list[str]]:
    seen: dict[str, int] = {}
    for promise in promises:
        seen[promise.id] = seen.get(promise.id, 0) + 1
    return {
        "malformed_promise_ids": [p.path for p in promises if not id_pattern.match(p.id)],
        "duplicate_promise_ids": sorted(f"{pid} (x{n})" for pid, n in seen.items() if n > 1),
        "invalid_status": [f"{p.id}: {p.status or '<empty>'}" for p in promises if p.status not in STATUSES],
        "unratified_record": [
            p.id for p in promises if p.status == "ratified" and not (p.ratified_by and p.ratified_at)
        ],
    }


def _check_manual(scenario: Scenario, release: str | None) -> list[str]:
    problems: list[str] = []
    results = scenario.tag_values("@manual-result:")
    tested = scenario.tag_values("@manual-last-tested:")
    evidence = scenario.tag_values("@manual-test-evidence:")
    if not results or results[0] not in MANUAL_RESULTS:
        problems.append("manual_incomplete: no @manual-result:passed|failed|pending")
    elif results[0] == "failed":
        problems.append("manual_failed")
    elif results[0] == "pending":
        problems.append("manual_pending")
    if not tested:
        problems.append("manual_incomplete: no @manual-last-tested:<version>")
    elif release and tested[0] != release:
        problems.append(f"manual_stale: last tested on {tested[0]}, release is {release}")
    if results and results[0] == "passed" and not evidence:
        problems.append("manual_incomplete: passed without @manual-test-evidence:<link>")
    return problems


def reconcile(
    promises: list[Promise],
    scenarios: list[Scenario],
    *,
    id_pattern: str = DEFAULT_ID_PATTERN,
    release: str | None = None,
) -> dict[str, Any]:
    pattern = re.compile(id_pattern)
    findings: dict[str, list[str]] = _check_promises(promises, pattern)
    by_id = {p.id: p for p in promises}

    findings["untraced_scenarios"] = [s.ref for s in scenarios if not s.promise_ids]
    findings["dangling_promise_refs"] = []
    findings["cites_unratified_promise"] = []
    findings["manual_findings"] = []
    coverage: dict[str, dict[str, list[str]]] = {
        p.id: {"automated": [], "manual": []} for p in promises if p.status == "ratified"
    }
    for scenario in scenarios:
        for pid in scenario.promise_ids:
            promise = by_id.get(pid)
            if promise is None:
                findings["dangling_promise_refs"].append(f"{scenario.ref} -> {pid}")
            elif promise.status != "ratified":
                findings["cites_unratified_promise"].append(f"{scenario.ref} -> {pid} ({promise.status})")
            else:
                coverage[pid]["manual" if scenario.manual else "automated"].append(scenario.ref)
        if scenario.manual:
            findings["manual_findings"].extend(f"{scenario.ref}: {p}" for p in _check_manual(scenario, release))

    declared_gaps = [
        f"{p.id}: {p.gap.get('reason', 'no reason given')}"
        for p in promises
        if p.status == "ratified" and p.gap and not any(coverage[p.id].values())
    ]
    findings["uncovered_promises"] = [
        p.id for p in promises if p.status == "ratified" and not p.gap and not any(coverage[p.id].values())
    ]
    blocking = sorted(k for k, v in findings.items() if v)
    return {
        "schema": "qa-promise-trace/v1",
        "verdict": "blocked" if blocking else "ready",
        "blocking": blocking,
        "counts": {
            "promises": len(promises),
            "ratified": sum(p.status == "ratified" for p in promises),
            "candidate": sum(p.status == "candidate" for p in promises),
            "retired": sum(p.status == "retired" for p in promises),
            "scenarios": len(scenarios),
            "manual_scenarios": sum(s.manual for s in scenarios),
            "declared_gaps": len(declared_gaps),
        },
        "findings": findings,
        "declared_gaps": declared_gaps,
        "coverage": coverage,
    }


def run(promises_dir: Path, scenarios_dir: Path, *, id_pattern: str, release: str | None) -> dict[str, Any]:
    if not promises_dir.is_dir():
        raise CannotMeasure(f"promise tree not found: {promises_dir}")
    if not scenarios_dir.is_dir():
        raise CannotMeasure(f"scenario tree not found: {scenarios_dir}")
    promises, missing_block = load_promises(promises_dir)
    if not promises:
        raise CannotMeasure(f"no promise pages (type: Promise with a promise.id) under {promises_dir}")
    scenarios = load_scenarios(scenarios_dir)
    if not scenarios:
        raise CannotMeasure(f"no scenarios in *.feature files under {scenarios_dir}")
    report = reconcile(promises, scenarios, id_pattern=id_pattern, release=release)
    report["findings"]["promise_pages_without_block"] = missing_block
    if missing_block:
        report["blocking"] = sorted(set(report["blocking"]) | {"promise_pages_without_block"})
        report["verdict"] = "blocked"
    return report


def render_text(report: dict[str, Any]) -> str:
    lines = [f"Promise trace: {report['verdict'].upper()}"]
    lines.append("  " + ", ".join(f"{k}={v}" for k, v in report["counts"].items()))
    for key, values in report["findings"].items():
        lines.append(f"  {key}: {len(values)}")
        lines.extend(f"    - {v}" for v in values)
    lines.append(f"  declared_gaps: {len(report['declared_gaps'])}")
    lines.extend(f"    - {g}" for g in report["declared_gaps"])
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Self-test
# ---------------------------------------------------------------------------
_PAGE = """---
title: {title}
description: {title}.
doc_status: active
updated: 2026-10-10
type: Promise
kind: Reference
promise:
  id: {pid}
  status: {status}
{extra}---

{title}.
"""


def _self_test() -> int:
    checks: list[tuple[str, bool]] = []
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        docs, features = root / "docs" / "context" / "promises", root / "features"
        docs.mkdir(parents=True)
        features.mkdir()
        ratified = "  owner: product\n  ratified_by: Product Owner\n  ratified_at: 2026-10-10\n"
        (docs / "init.md").write_text(
            _PAGE.format(title="Init is idempotent", pid="PR-CLI-001", status="ratified", extra=ratified)
        )
        (docs / "help.md").write_text(
            _PAGE.format(title="Help lists commands", pid="PR-CLI-002", status="ratified", extra=ratified)
        )
        (docs / "idea.md").write_text(_PAGE.format(title="Future idea", pid="PR-CLI-003", status="candidate", extra=""))
        (features / "init.feature").write_text(
            "@promise:PR-CLI-001\nFeature: Init\n\n  Scenario: Re-running init changes nothing\n"
            "    Given an initialised project\n    When init runs again\n    Then no file changes\n"
        )
        (features / "help.feature").write_text(
            "Feature: Help\n\n  @promise:PR-CLI-002 @manual @manual-result:passed\n"
            "  @manual-last-tested:4.0.0 @manual-test-evidence:https://example.invalid/e/1\n"
            "  Scenario: Help is readable\n    Given a terminal\n    When help runs\n    Then commands are listed\n"
        )
        good = run(docs.parent.parent, features, id_pattern=DEFAULT_ID_PATTERN, release="4.0.0")
        checks.append(("intact chain is ready", good["verdict"] == "ready"))
        checks.append(("candidate excluded from coverage", "PR-CLI-003" not in good["coverage"]))
        checks.append(("feature tag inherited", bool(good["coverage"]["PR-CLI-001"]["automated"])))

        stale = run(docs.parent.parent, features, id_pattern=DEFAULT_ID_PATTERN, release="4.1.0")
        checks.append(("stale manual result blocks", "manual_findings" in stale["blocking"]))

        (features / "orphan.feature").write_text(
            "Feature: Orphans\n  Scenario: No promise\n    Given x\n    When y\n    Then z\n"
            "  @promise:PR-CLI-003\n  Scenario: Cites a candidate\n    Given x\n    When y\n    Then z\n"
            "  @promise:PR-CLI-999\n  Scenario: Cites nothing real\n    Given x\n    When y\n    Then z\n"
        )
        (features / "init.feature").unlink()
        bad = run(docs.parent.parent, features, id_pattern=DEFAULT_ID_PATTERN, release=None)
        f = bad["findings"]
        checks.append(("untraced scenario found", len(f["untraced_scenarios"]) == 1))
        checks.append(("candidate citation found", len(f["cites_unratified_promise"]) == 1))
        checks.append(("dangling reference found", len(f["dangling_promise_refs"]) == 1))
        checks.append(("uncovered promise found", f["uncovered_promises"] == ["PR-CLI-001"]))

        (docs / "init.md").write_text(
            _PAGE.format(
                title="Init is idempotent",
                pid="PR-CLI-001",
                status="ratified",
                extra=ratified + "  gap:\n    reason: no fixture yet\n    issue: '#1'\n",
            )
        )
        gapped = run(docs.parent.parent, features, id_pattern=DEFAULT_ID_PATTERN, release=None)
        checks.append(
            (
                "declared gap is listed, not blocking",
                gapped["findings"]["uncovered_promises"] == [] and len(gapped["declared_gaps"]) == 1,
            )
        )

        (docs / "dup.md").write_text(_PAGE.format(title="Dup", pid="PR-CLI-002", status="ratified", extra=""))
        dup = run(docs.parent.parent, features, id_pattern=DEFAULT_ID_PATTERN, release=None)
        checks.append(("duplicate id found", bool(dup["findings"]["duplicate_promise_ids"])))
        checks.append(("ratified without record found", "PR-CLI-002" in dup["findings"]["unratified_record"]))

        empty = root / "empty"
        empty.mkdir()
        try:
            run(empty, features, id_pattern=DEFAULT_ID_PATTERN, release=None)
            checks.append(("no promises cannot measure", False))
        except CannotMeasure:
            checks.append(("no promises cannot measure", True))

    for name, ok in checks:
        print(f"[{'PASS' if ok else 'FAIL'}] {name}")
    failed = sum(not ok for _, ok in checks)
    print(f"self-test: {len(checks) - failed}/{len(checks)} passed")
    return 0 if failed == 0 else 1


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--promises", type=Path, help="DocKitty docs tree holding promise pages")
    parser.add_argument("--scenarios", type=Path, help="directory holding *.feature files")
    parser.add_argument("--release", help="release under test; manual results from another version are stale")
    parser.add_argument("--id-pattern", default=DEFAULT_ID_PATTERN, help="regex every promise id must match")
    parser.add_argument("--out", type=Path, help="write the JSON report here")
    parser.add_argument("--json", action="store_true", help="print the JSON report instead of text")
    parser.add_argument("--self-test", action="store_true", help="run the built-in fixture suite")
    args = parser.parse_args(argv)

    if args.self_test:
        return _self_test()
    if not args.promises or not args.scenarios:
        parser.error("--promises and --scenarios are required")
    try:
        report = run(args.promises, args.scenarios, id_pattern=args.id_pattern, release=args.release)
    except CannotMeasure as exc:
        print(f"Promise trace: CANNOT MEASURE: {exc}", file=sys.stderr)
        return 2
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2) if args.json else render_text(report))
    return 0 if report["verdict"] == "ready" else 1


if __name__ == "__main__":
    sys.exit(main())
