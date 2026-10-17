"""qa_carrier_lint — the pack-shipped `qa-traceability/v1` carrier validator.

Loads/validates ``qa-traceability.yaml`` against the closed
``qa-traceability/v1`` schema (presence-based detection: any ``schema:``
starting ``qa-traceability/`` claims the carrier; a version mismatch is a
loud ERROR, never a silent skip), computes three-state coverage
(``measured``/``not_measured``/``error``) by resolving ``requirements[].
case_ids`` against ``cases[]`` and each case's evidence, reimplements
``specify_cli/review/baseline.py:168-207``'s JUnit-XML classification
verbatim (its private classification helper, lines 181-205: failure/error
before skipped, before passed), resolves ``*.session.yaml`` evidence, scores the
reserved ``evidence.ctrf`` key ``not_measured`` (never a silent pass), and
enforces the bridge invariant against ``tasks/WP*.md`` frontmatter — parsed
by this module itself, never by importing ``requirement_mapping.py``.

Portability (NFR-001): stdlib + ``ruamel.yaml`` only, nothing from the
``spec-kitty``/``specify_cli``/``doctrine``/``charter`` source tree, mirroring
``docs_structural_lint.py:33-38``'s own contract.

Invocation::

    python qa_carrier_lint.py --carrier PATH --mission-dir PATH [--json]
    python qa_carrier_lint.py --self-test

Exit ``0`` (ready), ``1`` (blocked), or ``2`` (cannot-measure/error).
``qa-coverage.json`` is written unconditionally to ``<mission-dir>/`` on
every run; ``--json`` only *additionally* echoes it to stdout — a deliberate
divergence from ``docs_structural_lint.py``'s exclusive-``--json`` precedent
(this module persists its output to disk, that one does not).

**Known limitation (FR-016)**: no runtime hook compels running this
validator or blocks on its verdict today: ``GateBinding.handler_kind:
"asset"`` remains inert pending upstream issue #2599 (open), and although
spec-kitty gained a live per-type artifact-presence gate on 2026-08-21
(``gather_artifact_presence``, closing #3597), it is wired only for the four built-in mission
families' own step vocabularies, ignores ``expected-artifacts.yaml``'s ``blocking:`` flag entirely,
and has no entry for a custom type such as this pack's own ``qa``, so
``accept`` still enforces nothing from that manifest. Mechanically checkable
(exit code, ``qa-coverage.json``) but agent-invoked, never machine-enforced,
until both of those conditions close.
"""

from __future__ import annotations

import argparse
import ast
import contextlib
import io
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from ruamel.yaml import YAML
from ruamel.yaml.error import YAMLError

SCHEMA_PREFIX = "qa-traceability/"
SCHEMA_V1 = "qa-traceability/v1"
OUTPUT_SCHEMA = "qa-coverage/v1"

TOP_KEYS = {"schema", "mission", "generated_at", "requirements", "cases", "defects"}
REQ_ALLOWED = {"id", "case_ids", "rationale"}
REQ_REQUIRED = {"id", "case_ids"}
CASE_ALLOWED = {"id", "wp", "type", "evidence"}
JUNIT_ALLOWED = {"file", "testcase"}
SESSION_ALLOWED = {"file"}
DEFECT_ALLOWED = {"id", "case_id", "description", "severity"}
DEFECT_REQUIRED = {"id", "description", "severity"}
SESSION_REQUIRED = {
    "charter", "timebox_minutes", "overrun", "areas",
    "bugs_found", "bug_ids", "status", "debrief",
}
CTRF_REASON = "CTRF not implemented in v1"

# A-11: the documented `cases[].wp` shape (data-model.md Section A worked example --
# "wp: WP01"/"wp: WP02" -- and this pack's own `tasks/WP\d\d-*.md` naming convention,
# spec.md:531, plan.md's worked examples, and every real WP file this mission itself
# ships under kitty-specs/.../tasks/). Exactly `WP` + two digits, nothing else --
# never a bare prefix a longer/unrelated id could satisfy.
WP_ID_RE = re.compile(r"^WP\d{2}$")

def _yaml() -> YAML:
    return YAML(typ="safe")

def _check_entry(d: object, required: set, allowed: set) -> str | None:
    """Error detail string, or None, for a closed-schema `d` against `allowed`/`required`."""
    if not isinstance(d, dict):
        return "entry is not a mapping"
    extra = set(d) - allowed
    if extra:
        return f"unrecognised key(s): {sorted(extra)}"
    missing = required - set(d)
    if missing:
        return f"missing required key(s): {sorted(missing)}"
    return None

def _err(kind: str, reason: str) -> dict:
    return {"ok": False, "error_kind": kind, "reason": reason}

def _resolve_contained(mission_dir: Path, rel: str) -> tuple[Path | None, str | None]:
    """A-8: `rel` must be a *relative* path that, once normalised, resolves inside
    `mission_dir`. The absolute case MUST be rejected before any join is attempted:
    `Path.__truediv__` discards its left operand entirely when the right side is
    absolute (`Path("/mission") / "/etc/passwd" == Path("/etc/passwd")`), so joining
    first and only then checking containment would silently honour the escape it was
    supposed to catch. The `..`-relative case is caught by resolving both sides and
    checking real containment via `relative_to` -- never a `startswith` check on
    unnormalised strings, which a merely lexical prefix match cannot safely decide
    (e.g. a sibling directory whose name happens to start with the mission-dir's own
    name). Returns `(resolved_path, None)` when contained, `(None, reason)` otherwise,
    with `reason` naming the rejected value (the caller names the case id)."""
    if Path(rel).is_absolute():
        return None, f"must be a relative path, got absolute path {rel!r}"
    # `.resolve()` (strict=False, the default) is left unguarded here -- proven,
    # by direct empirical test against this module's own target interpreter, to
    # swallow OSError internally rather than raise it (an unreadable ancestor
    # directory still resolves lexically). Named in `_IO_SWEEP_KNOWN_SAFE` below
    # so the mechanical sweep accounts for it explicitly rather than missing it.
    resolved_root = mission_dir.resolve()
    resolved = (mission_dir / rel).resolve()
    try:
        resolved.relative_to(resolved_root)
    except ValueError:
        return None, f"path escapes --mission-dir: {rel!r}"
    return resolved, None

def _validate_wp_id(mission_dir: Path, wp: object) -> tuple[bool, str | None]:
    """A-11: `cases[].wp` is a second carrier-supplied string that reaches the
    filesystem (via `_read_wp_frontmatter`'s glob, T005/FR-011) -- exactly the class
    A-8 already guards for `evidence.*.file`, just a field nobody had named yet. Two
    steps, in order, neither skippable:

    (a) format-validate `wp` against the documented WP-id shape (`WP_ID_RE`, "WP" +
        exactly two digits) *before* anything touches the filesystem -- a malformed
        value (wrong shape, non-string, empty, a traversal string like
        `"../../outside/leak"`) is rejected here and never reaches a glob/open call
        at all;
    (b) route the resulting candidate WP path through `_resolve_contained`, the
        SAME containment helper A-8 mandates for evidence paths -- never a second,
        parallel containment mechanism. Given (a) already constrains `wp` to
        `^WP\\d{2}$` (no `/`, no `..`, no leading `-`), this second step can never
        actually reject a format-valid `wp` today; it is kept anyway so this
        function -- not some future caller's memory -- is the one place that
        decides "this path is safe to read", and so a future relaxation of (a)
        cannot silently reopen the containment escape A-8 already closed once.

    Returns `(True, None)` when `wp` is acceptable, else `(False, reason)` naming
    the rejected value -- the caller names the case id."""
    if not isinstance(wp, str) or not WP_ID_RE.fullmatch(wp):
        return False, f"wp does not match required shape 'WP' + two digits: {wp!r}"
    _, path_err = _resolve_contained(mission_dir, f"tasks/{wp}.md")
    if path_err:
        return False, path_err
    return True, None

def validate(doc: object, mission_dir: Path) -> dict:
    """T001 — presence-based schema/version detection (inverted from a version-string check), closed-vocabulary
    enforcement at every level, then a straightforward id-lookup/id-count pass (never a bare
    dict lookup that could raise) building `duplicate_case_ids`/`dangling_case_refs`."""
    if not isinstance(doc, dict):
        return _err("schema_violation", "carrier root is not a mapping")
    schema = doc.get("schema")
    if not isinstance(schema, str) or not schema.startswith(SCHEMA_PREFIX):
        return _err("schema_violation", "missing or non-'qa-traceability/*' 'schema' key")
    if schema != SCHEMA_V1:
        return _err("version_mismatch",
                     f"schema version mismatch: found {schema!r}, expected {SCHEMA_V1!r}")
    extra = set(doc) - TOP_KEYS
    if extra:
        return _err("schema_violation", f"unrecognised top-level key(s): {sorted(extra)}")
    missing_top = TOP_KEYS - set(doc)
    if missing_top:
        return _err("schema_violation", f"missing required top-level key(s): {sorted(missing_top)}")

    requirements = doc.get("requirements") or []
    cases = doc.get("cases") or []
    defects = doc.get("defects") or []
    if not all(isinstance(x, list) for x in (requirements, cases, defects)):
        return _err("schema_violation", "requirements/cases/defects must each be a list")

    for r in requirements:
        detail = _check_entry(r, REQ_REQUIRED, REQ_ALLOWED)
        if detail:
            return _err("schema_violation", f"requirements[] entry invalid: {detail}")
        if not r.get("case_ids") and not (r.get("rationale") or "").strip():
            return _err("schema_violation",
                        f"requirement {r['id']} has empty case_ids without a non-empty rationale")

    for c in cases:
        detail = _check_entry(c, CASE_ALLOWED, CASE_ALLOWED)
        if detail:
            return _err("schema_violation", f"cases[] entry invalid: {detail}")
        # A-11: `wp` is carrier-supplied and reaches the filesystem (T005/FR-011's
        # bridge check, via `_read_wp_frontmatter`'s glob) exactly like `evidence.*.
        # file` does -- format-validate it against the documented WP-id shape and
        # route it through the SAME containment helper A-8 mandates (`_resolve_
        # contained`, wrapped here as `_validate_wp_id`) *before* any evidence/bridge
        # code ever sees it. A malformed value is a schema ERROR here, never a
        # silent promotion that later makes a genuine bridge violation vanish.
        wp_ok, wp_err = _validate_wp_id(mission_dir, c["wp"])
        if not wp_ok:
            return _err("schema_violation",
                         f"case {c['id']} wp rejected: {wp_err}")
        ev = c["evidence"]
        if not isinstance(ev, dict):
            return _err("schema_violation", f"case {c['id']} evidence is not a mapping")
        present = [k for k in ("junit", "session", "ctrf") if k in ev]
        if set(ev) - {"junit", "session", "ctrf"} or len(present) != 1:
            return _err("schema_violation",
                        f"case {c['id']} evidence must carry exactly one of junit/session/ctrf")
        # A-8 note: `ctrf` is intentionally exempt from `_resolve_contained` below --
        # Section A fixes it as "reserved, no required sub-fields in v1 ... carries no
        # `file` key" and no code path in this module ever reads a `ctrf` entry's keys
        # (compute_coverage always scores it `ctrf_pending` without touching `ev`), so
        # a `file`-shaped key under `ctrf` is syntactically legal (the closed-schema
        # check above never runs against it) but mechanically inert -- there is no
        # escape to close because there is no read to guard (Section D Pair 30.5).
        if "junit" in ev:
            detail = _check_entry(ev["junit"], JUNIT_ALLOWED, JUNIT_ALLOWED)
            if detail:
                return _err("schema_violation", f"case {c['id']} junit evidence invalid: {detail}")
            _, path_err = _resolve_contained(mission_dir, ev["junit"]["file"])
            if path_err:
                return _err("schema_violation",
                             f"case {c['id']} junit evidence file rejected: {path_err}")
        if "session" in ev:
            detail = _check_entry(ev["session"], SESSION_ALLOWED, SESSION_ALLOWED)
            if detail:
                return _err("schema_violation", f"case {c['id']} session evidence invalid: {detail}")
            _, path_err = _resolve_contained(mission_dir, ev["session"]["file"])
            if path_err:
                return _err("schema_violation",
                             f"case {c['id']} session evidence file rejected: {path_err}")

    for d in defects:
        detail = _check_entry(d, DEFECT_REQUIRED, DEFECT_ALLOWED)
        if detail:
            return _err("schema_violation", f"defects[] entry invalid: {detail}")

    id_counts: dict[str, int] = {}
    for c in cases:
        id_counts[c["id"]] = id_counts.get(c["id"], 0) + 1
    duplicate_case_ids = [{"id": cid, "count": n} for cid, n in id_counts.items() if n > 1]
    dup_ids = {row["id"] for row in duplicate_case_ids}
    all_case_ids = set(id_counts)
    cases_by_id = {c["id"]: c for c in cases if c["id"] not in dup_ids}

    dangling_case_refs = []
    dangling_requirement_ids: set[str] = set()
    for r in requirements:
        for cid in r.get("case_ids") or []:
            if cid not in all_case_ids:
                dangling_case_refs.append({"requirement_id": r["id"], "case_id": cid})
                dangling_requirement_ids.add(r["id"])

    return {
        "ok": True, "error_kind": None, "reason": None,
        "requirements": requirements, "requirements_total": len(requirements),
        "cases_by_id": cases_by_id, "cases_total": len(cases),
        "duplicate_case_ids": duplicate_case_ids,
        "dangling_case_refs": dangling_case_refs,
        "dangling_requirement_ids": dangling_requirement_ids,
        "defects": defects,
    }

def _parse_junit_xml(xml_path: Path, tree_cache: dict[Path, tuple[str, object]]) -> tuple[str, object]:
    """F4 (severity 2) — parse `xml_path` at most once per `compute_coverage` run, memoised
    in `tree_cache` keyed by path, mirroring `check_bridge`'s own per-path `cache` (T005):
    without this, N cases sharing one evidence file each called `ET.parse` fresh (measured
    1,000 requirements -> 0.9s, 5,000 -> 14.5s, ~16x for 5x input -- superlinear from the
    re-parse, not the classification work itself). Returns `(state, payload)`, state in
    ok/file_missing/unparseable/io_error; `payload` is the parsed tree only when state=="ok",
    else a detail string (io_error folds A-10's non-FileNotFoundError OSErrors in here too,
    since both are "this file could not be turned into a tree" -- the caller maps io_error
    to the same `unparseable` bucket `evidence_unparseable` already reports)."""
    if xml_path not in tree_cache:
        try:
            tree_cache[xml_path] = ("ok", ET.parse(xml_path))
        except FileNotFoundError:
            tree_cache[xml_path] = ("file_missing", None)
        except ET.ParseError as exc:
            tree_cache[xml_path] = ("unparseable", f"not valid XML: {exc}")
        except OSError as exc:
            # A-10: any other OSError reading evidence (e.g. PermissionError) is a
            # named ERROR, never an unhandled traceback and never exit 1.
            tree_cache[xml_path] = ("io_error", f"could not read {xml_path}: {exc}")
    return tree_cache[xml_path]

def classify_junit(
    xml_path: Path, testcase_ref: str, tree_cache: dict[Path, tuple[str, object]],
) -> tuple[str, str | None]:
    """T002 — classify one `classname::name` ref, mirroring baseline.py:181-205 verbatim.
    Returns `(state, detail)`, state in file_missing/unparseable/results_missing/passed/
    failed/skipped. failure/error checked before skipped: both-children classifies failed.
    `tree_cache` is the F4 memoisation cache -- the caller (`compute_coverage`) owns one
    cache dict per run and passes it to every call, exactly as `check_bridge` does for
    WP-frontmatter reads."""
    state, payload = _parse_junit_xml(xml_path, tree_cache)
    if state == "file_missing":
        return "file_missing", None
    if state == "unparseable" or state == "io_error":
        return "unparseable", payload
    tree = payload
    classname, _, name = testcase_ref.partition("::")
    for tc in tree.getroot().iter("testcase"):
        if tc.get("classname", "") == classname and tc.get("name", "") == name:
            if tc.find("failure") is not None or tc.find("error") is not None:
                return "failed", None
            if tc.find("skipped") is not None:
                return "skipped", None
            return "passed", None
    return "results_missing", f"testcase {testcase_ref!r} not found in {xml_path}"

def classify_session(yaml_path: Path) -> tuple[str, str | None, dict | None]:
    """T003 — classify one `*.session.yaml`. Returns `(state, detail, data)`; state in
    file_missing/unparseable/ok; `data` is the parsed mapping only when state == "ok"."""
    try:
        data = _yaml().load(yaml_path)
    except FileNotFoundError:
        return "file_missing", None, None
    except YAMLError as exc:
        return "unparseable", f"not valid YAML: {exc}", None
    except OSError as exc:
        # A-10: any other OSError reading evidence (e.g. PermissionError) is a named
        # ERROR, never an unhandled traceback and never exit 1.
        return "unparseable", f"could not read {yaml_path}: {exc}", None
    if not isinstance(data, dict):
        return "unparseable", "session file root is not a mapping", None
    missing = SESSION_REQUIRED - set(data)
    if missing:
        return "unparseable", f"missing required field: {sorted(missing)[0]}", None
    if data.get("status") not in ("completed", "aborted"):
        return "unparseable", "status not in {completed, aborted}", None
    if not isinstance(data.get("overrun"), bool):
        # A-9: `overrun` must be a real YAML boolean. A falsy non-boolean (`""`, `0`,
        # `null`) previously fell through Python truthiness into the `passed` branch
        # (silent fake green); a truthy non-boolean (`"false"`) degraded into the
        # `overrun` bucket by luck, not by design -- both are untyped and both are
        # now rejected the same way, never resolved by truthiness.
        return "unparseable", f"'overrun' must be a boolean, got {data.get('overrun')!r}", None
    return "ok", None, data

def _read_wp_frontmatter(mission_dir: Path, wp_id: str) -> tuple[str, set | None, str | None]:
    """Resolve `wp_id` to its `tasks/WP*.md` file and parsed `requirement_refs:`.
    `(wp_file, None, None)` -> file absent (not_measured); `(wp_file, None, reason)` ->
    exists but unparseable (a violation); else `(wp_file, refs, None)`.

    A-11 defense-in-depth: `validate()` already format-validates and contains every
    `wp` (via `_validate_wp_id`) before `compute_coverage`/`check_bridge` -- and
    therefore this function -- can ever run, so the branch below is unreachable
    through the CLI's own pipeline. It is repeated here anyway, exactly as A-10's
    amendment insists a rule be phrased as a class rather than a list of call sites:
    a future caller reaching `_read_wp_frontmatter` without going through `validate`
    first must not get a free pass either. A rejection here is reported as a real
    bridge violation (never the benign `bridge_not_measured`) -- an internal-
    invariant break is a resolvable defect, not an authoring gap.

    `tasks_dir.is_dir()`/`tasks_dir.glob(...)` are deliberately left unguarded here --
    both are proven, by direct empirical test against this module's own target
    interpreter (see the WP report for the A-10-class closure), to swallow any
    `OSError` internally (permission-denied on the directory itself, or on an
    ancestor, both resolve to an empty match list, never a raised exception) --
    they are named in `_IO_SWEEP_KNOWN_SAFE` below precisely so the mechanical
    sweep still accounts for them rather than silently ignoring them.

    A-11 anchoring: the raw glob `f"{wp_id}*.md"` is a *candidate* list only --
    `wp_id` is a validated `WP\\d\\d` token by this point, but an unanchored prefix
    match would still let it collide with an unrelated, longer id's file (e.g. `wp_id
    == "WP05"` matching a stray `WP050-other.md`). Every candidate is re-checked
    against `_WP_FILE_RE`, which requires the stem to be exactly `wp_id`, or `wp_id`
    followed by a `-` separator -- never a bare, unbounded prefix.

    `wp_path.read_text()` is the fifth I/O site A-10's amendment names directly:
    the four-site enumeration in the rule's first draft never covered this read,
    and an orchestrator repro (`chmod 000 tasks/WP01-x.md`) reached it as an
    unhandled `PermissionError` traceback at exit `1` immediately after the four
    named sites were closed. It is now guarded the same way every other read in
    this module already is: any `OSError` becomes a real, named `bridge_violations`
    entry (never the benign `bridge_not_measured`, since the WP file provably
    exists -- it is simply unreadable, a resolvable defect, not an authoring gap)."""
    wp_ok, wp_err = _validate_wp_id(mission_dir, wp_id)
    if not wp_ok:
        return f"tasks/{wp_id}.md", None, f"internal: wp rejected before frontmatter read: {wp_err}"
    tasks_dir = mission_dir / "tasks"
    wp_file_re = re.compile(rf"^{re.escape(wp_id)}(-.*)?$")
    matches = sorted(
        p for p in (tasks_dir.glob(f"{wp_id}*.md") if tasks_dir.is_dir() else [])
        if wp_file_re.fullmatch(p.stem)
    )
    if not matches:
        return f"tasks/{wp_id}.md", None, None
    wp_path = matches[0]
    wp_file = f"tasks/{wp_path.name}"
    try:
        text = wp_path.read_text()
    except OSError as exc:
        # A-10 (class): any OSError reading a WP-frontmatter file (e.g.
        # PermissionError) is a real, named bridge_violations ERROR -- never an
        # unhandled traceback, never exit 1. This is the fifth site.
        return wp_file, None, f"could not read {wp_path}: {exc}"
    parts = text.split("---", 2)
    if len(parts) < 3:
        return wp_file, None, "no YAML frontmatter block found"
    try:
        fm = _yaml().load(parts[1]) or {}
    except YAMLError:
        return wp_file, None, "frontmatter block is not valid YAML"
    if not isinstance(fm, dict):
        return wp_file, None, "frontmatter block is not a mapping"
    return wp_file, set(fm.get("requirement_refs") or []), None

def check_bridge(
    requirements: list, cases_by_id: dict, dangling_requirement_ids: set, mission_dir: Path,
) -> tuple[list, list]:
    """T005 — FR-011: every requirement's covering-WP set must list its own FR id. A missing
    WP file is `bridge_not_measured` (benign); a WP file that exists but is unparseable, or
    omits the FR id, is `bridge_violations` (a hard ERROR); `case_ids == []` is vacuous."""
    violations: list = []
    not_measured: list = []
    cache: dict[str, tuple[str, set | None, str | None]] = {}
    for r in requirements:
        rid = r["id"]
        if rid in dangling_requirement_ids:
            continue
        for cid in r.get("case_ids") or []:
            case = cases_by_id.get(cid)
            if case is None:
                continue
            wp = case["wp"]
            if wp not in cache:
                cache[wp] = _read_wp_frontmatter(mission_dir, wp)
            wp_file, refs, parse_error = cache[wp]
            if refs is None and parse_error is None:
                not_measured.append({"requirement_id": rid, "case_id": cid, "wp_file": wp_file,
                                      "reason": f"{wp_file} not found on disk"})
            elif parse_error is not None or rid not in refs:
                violations.append({"requirement_id": rid, "case_id": cid, "wp_file": wp_file})
    return violations, not_measured

def compute_coverage(validation: dict, mission_dir: Path) -> dict:
    """T004 — assemble qa-coverage.json's full record for a structurally-valid carrier:
    classify every `cases[]` entry's evidence, resolve per-requirement coverage against the
    Coverage rule, run the bridge check, derive `status`/`verdict`/`reason`."""
    requirements = validation["requirements"]
    cases_by_id = validation["cases_by_id"]
    dangling_req_ids = validation["dangling_requirement_ids"]

    case_outcomes: dict[str, str] = {}
    passed = failed = skipped = overrun = results_missing = evidence_file_missing = 0
    evidence_unparseable: list = []
    ctrf_pending: list = []
    reasons: dict[str, str] = {}
    # F4 — one parsed-tree cache per run, shared across every case, mirroring
    # `check_bridge`'s own per-path `cache`: N cases sharing one JUnit-XML file parse
    # it once, not N times.
    junit_tree_cache: dict[Path, tuple[str, object]] = {}

    for cid, case in cases_by_id.items():
        ev = case["evidence"]
        if "junit" in ev:
            file_rel = ev["junit"]["file"]
            state, detail = classify_junit(
                mission_dir / file_rel, ev["junit"]["testcase"], junit_tree_cache)
            if state == "file_missing":
                evidence_file_missing += 1
                case_outcomes[cid] = "evidence_file_missing"
            elif state == "unparseable":
                evidence_unparseable.append(
                    {"case_id": cid, "file": file_rel, "evidence_kind": "junit", "reason": detail})
                case_outcomes[cid] = "evidence_unparseable"
            elif state == "results_missing":
                results_missing += 1
                reasons[cid] = detail
                case_outcomes[cid] = "results_missing"
            else:
                case_outcomes[cid] = state
                if state == "passed":
                    passed += 1
                elif state == "failed":
                    failed += 1
                elif state == "skipped":
                    skipped += 1
        elif "session" in ev:
            file_rel = ev["session"]["file"]
            state, detail, data = classify_session(mission_dir / file_rel)
            if state == "file_missing":
                evidence_file_missing += 1
                case_outcomes[cid] = "evidence_file_missing"
            elif state == "unparseable":
                evidence_unparseable.append(
                    {"case_id": cid, "file": file_rel, "evidence_kind": "session", "reason": detail})
                case_outcomes[cid] = "evidence_unparseable"
            elif data["status"] == "aborted":
                failed += 1
                case_outcomes[cid] = "failed"
            elif data["overrun"]:
                overrun += 1
                case_outcomes[cid] = "overrun"
            else:
                passed += 1
                case_outcomes[cid] = "passed"
        else:
            ctrf_pending.append({"case_id": cid, "reason": CTRF_REASON})
            case_outcomes[cid] = "ctrf_pending"

    requirements_covered = 0
    requirements_uncovered: list = []
    requirements_blocked: list = []
    for r in requirements:
        rid = r["id"]
        if rid in dangling_req_ids:
            continue
        case_ids = r.get("case_ids") or []
        if not case_ids:
            requirements_uncovered.append(rid)
            reasons[rid] = r.get("rationale")
            continue
        if all(case_outcomes.get(cid) == "passed" for cid in case_ids):
            requirements_covered += 1
        else:
            requirements_blocked.append(rid)

    bridge_violations, bridge_not_measured = check_bridge(
        requirements, cases_by_id, dangling_req_ids, mission_dir)
    results_parsed = passed + failed + skipped + overrun

    error_now = bool(
        results_missing or bridge_violations
        or validation["duplicate_case_ids"] or validation["dangling_case_refs"]
        or evidence_unparseable)
    status = "error" if error_now else "measured"
    if status == "measured":
        verdict = "ready" if not requirements_uncovered and not requirements_blocked else "blocked"
        reason = None
    else:
        verdict = None
        reason = _carrier_error_reason(validation, results_missing, evidence_unparseable,
                                        bridge_violations)
        reasons["_carrier"] = reason

    return {
        "schema": OUTPUT_SCHEMA, "status": status, "verdict": verdict, "reason": reason,
        "requirements_total": validation["requirements_total"],
        "requirements_covered": requirements_covered,
        "requirements_uncovered": requirements_uncovered,
        "requirements_blocked": requirements_blocked,
        "cases_total": validation["cases_total"],
        "results_parsed": results_parsed,
        "passed": passed, "failed": failed, "skipped": skipped, "overrun": overrun,
        "results_missing": results_missing,
        "evidence_unparseable": evidence_unparseable,
        "evidence_file_missing": evidence_file_missing,
        "duplicate_case_ids": validation["duplicate_case_ids"],
        "dangling_case_refs": validation["dangling_case_refs"],
        "defects_total": len(validation["defects"]),
        "reasons": reasons,
        "bridge_violations": bridge_violations,
        "bridge_not_measured": bridge_not_measured,
        "ctrf_pending": ctrf_pending,
    }

def _carrier_error_reason(
    validation: dict, results_missing: int, evidence_unparseable: list, bridge_violations: list,
) -> str:
    parts = []
    if validation["dangling_case_refs"]:
        parts.append(f"{len(validation['dangling_case_refs'])} dangling case_ids reference(s)")
    if validation["duplicate_case_ids"]:
        parts.append(f"{len(validation['duplicate_case_ids'])} duplicate case id(s)")
    if evidence_unparseable:
        parts.append(f"{len(evidence_unparseable)} unparseable evidence file(s)")
    if results_missing:
        parts.append(f"{results_missing} unresolved JUnit-XML testcase reference(s)")
    if bridge_violations:
        parts.append(f"{len(bridge_violations)} bridge invariant violation(s)")
    return "; ".join(parts) or "carrier failed validation"

def _null_count_record(status: str, reason: str) -> dict:
    return {
        "schema": OUTPUT_SCHEMA, "status": status, "verdict": None, "reason": reason,
        "requirements_total": None, "requirements_covered": None,
        "requirements_uncovered": None, "requirements_blocked": None,
        "cases_total": None, "results_parsed": None,
        "passed": None, "failed": None, "skipped": None, "overrun": None,
        "results_missing": None, "evidence_unparseable": [], "evidence_file_missing": None,
        "duplicate_case_ids": [], "dangling_case_refs": [], "defects_total": None,
        "reasons": {"_carrier": reason}, "bridge_violations": [], "bridge_not_measured": [],
        "ctrf_pending": [],
    }

def run_validator(carrier_path: Path, mission_dir: Path) -> dict:
    """T006 — the single code path both the CLI and `--self-test` call. Absent carrier ->
    not_measured; present-but-malformed (bad YAML, version mismatch, schema violation) ->
    error with every count field null; else delegates to `validate` + `compute_coverage`."""
    # `.exists()` is left unguarded here -- proven, by direct empirical test
    # against this module's own target interpreter, to swallow OSError
    # internally and return False rather than raise (permission-denied resolves
    # the same as not-found). Named in `_IO_SWEEP_KNOWN_SAFE` below.
    if not carrier_path.exists():
        return _null_count_record("not_measured", f"carrier not found: {carrier_path}")
    try:
        doc = _yaml().load(carrier_path)
    except YAMLError as exc:
        return _null_count_record("error", f"carrier is not valid YAML: {exc}")
    except OSError as exc:
        # A-10: the carrier file exists (checked above) but could not be read (e.g.
        # PermissionError) -- a named ERROR, never an unhandled traceback, never exit 1.
        return _null_count_record("error", f"could not read carrier {carrier_path}: {exc}")
    validation = validate(doc, mission_dir)
    if not validation["ok"]:
        return _null_count_record("error", validation["reason"])
    return compute_coverage(validation, mission_dir)

def exit_code_for(record: dict) -> int:
    if record["status"] != "measured":
        return 2
    return 0 if record["verdict"] == "ready" else 1

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="qa_carrier_lint",
        description="Validate a qa-traceability/v1 carrier and compute qa-coverage.json. "
                     "Exit 0 (ready), 1 (blocked), or 2 (cannot-measure/error).",
    )
    parser.add_argument("--carrier", type=Path, help="Path to qa-traceability.yaml.")
    parser.add_argument("--mission-dir", type=Path, help="Mission-dir root evidence resolves under.")
    parser.add_argument("--json", action="store_true",
                         help="Additionally echo qa-coverage.json to stdout (always written to disk).")
    parser.add_argument("--self-test", action="store_true",
                         help=f"Run the {_derive_self_test_pair_count()} built-in fixture/assertion "
                              f"pairs; exit 0 only if all pass.")
    return parser

def main(argv: list[str] | None = None) -> int:
    """`--self-test` is dispatched *before* the A-10 backstop below, deliberately: a
    mutation that breaks a guard inside `self_test()`'s own fixtures must surface as a
    real crash/failed-assertion signal for a developer running `--self-test`, never be
    silently reclassified as a generic "unexpected I/O error" exit 2 by the very
    backstop meant to protect the CLI's *production* run path (Pair 33's mutation
    proof, see the WP report, is exactly this: without it, removing the WP-frontmatter
    guard would make `--self-test` exit 2 either way, masking which assertion broke).
    Every other invocation -- the real `--carrier`/`--mission-dir` path -- runs inside
    the backstop: no unhandled `OSError` may escape it, full stop, regardless of how
    confident any individual guard/proof below is (the amendment's own words are
    "including any added later")."""
    args = build_parser().parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.carrier or not args.mission_dir:
        sys.stderr.write(
            "qa_carrier_lint: --carrier and --mission-dir are required unless --self-test is passed\n")
        return 2
    try:
        return _run_and_report(args)
    except OSError as exc:
        sys.stderr.write(f"qa_carrier_lint: unexpected I/O error: {exc}\n")
        return 2

def _run_and_report(args: argparse.Namespace) -> int:
    record = run_validator(args.carrier, args.mission_dir)
    # A-10: creating --mission-dir or writing qa-coverage.json can each raise OSError
    # (e.g. --mission-dir names an existing regular file -> FileExistsError, or an
    # existing-but-unwritable directory -> PermissionError). Both MUST be reported as
    # status: error / exit 2 with a named reason -- never an unhandled traceback, and
    # never exit 1 (Section B reserves 1 for the measured/blocked verdict). Neither
    # failure can honour the "qa-coverage.json is written unconditionally" invariant,
    # since the write itself is what failed -- the reason says so explicitly instead
    # of pretending the file exists.
    try:
        args.mission_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        sys.stderr.write(
            f"qa_carrier_lint: could not create --mission-dir {args.mission_dir}: {exc}\n"
            f"qa_carrier_lint: qa-coverage.json NOT written (mission-dir unavailable)\n")
        return 2
    try:
        (args.mission_dir / "qa-coverage.json").write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n")
    except OSError as exc:
        sys.stderr.write(
            f"qa_carrier_lint: could not write qa-coverage.json under {args.mission_dir}: {exc}\n"
            f"qa_carrier_lint: qa-coverage.json NOT written\n")
        return 2
    if args.json:
        sys.stdout.write(json.dumps(record, indent=2, sort_keys=True) + "\n")
    else:
        sys.stdout.write(f"qa_carrier_lint: status={record['status']} verdict={record['verdict']}\n")
    return exit_code_for(record)

# T007 — self-test: a derived count of fixture/assertion pairs (see
# `_derive_self_test_pair_count` below -- the count is computed from the pair ids
# actually used in `self_test()`'s own source, never hand-typed here or in
# `--help`, precisely so this comment cannot go stale the way it already had:
# this line and `--help`'s text both said a hardcoded "32" while the suite had
# grown to 34, and two toolguide docs elsewhere in the pack separately said
# "29" -- all four caught stale in the same severity-2 finding this fix closes).
# The first fourteen are data-model.md Section D's own named pairs; pairs 15-23
# were added in WP01 review round 2, closing a defect as a CLASS rather than as
# a single 15th fixture; pairs 15.3, 24-29 were added in round 3; pairs 30-32
# were added in the pre-merge adversarial-squad fix round (2026-08-23), closing
# A-8/A-9/A-10 (data-model.md Section A addendum); pairs 33-34 closed A-10 as a
# CLASS the same day; pairs 35-38 close A-11 (data-model.md Section A addendum 2,
# 2026-08-23) -- each reproduced against the pre-fix build as a silent fake-green
# verdict before the addendum existed.
#
# Why round 3 exists, recorded here because the lesson outlived the bug: round
# 2 closed its findings against a HAND-ENUMERATED list of 34 mutations and
# reported zero survivors. Two independent reviewers then found two survivors
# that list did not contain -- one of them this WP's own prompt's third named
# falsifiable-bar example. So round 3 stopped writing the mutation list and
# GENERATED it, walking the AST of every Compare/BoolOp/If/all()-any()/
# dict.get()/container-literal site in the validation+classification+coverage+
# bridge code paths. That sweep found 218 mutable sites and 42 first-pass
# survivors. A mutation list written by hand measures its author's imagination;
# only a generated one measures the code. Every fixture is an in-memory
# string, never a file this mission ships; every assertion is a literal
# expected value. Every run goes through `run_validator`, the exact function
# `main()` calls — so `validate`/`compute_coverage`/`classify_junit` are
# exercised on the CLI's own code path, never a parallel reimplementation
# (pairs 32.x are the sole exception, deliberately going through `main()`
# itself via `_run_main`, since only `main()` owns the mkdir/write_text guards
# A-10 requires).

def _req(id_: str, case_ids: list, rationale: str | None = None) -> dict:
    return {"id": id_, "case_ids": case_ids, "rationale": rationale}

def _case(id_: str, wp: str, type_: str, evidence: dict) -> dict:
    return {"id": id_, "wp": wp, "type": type_, "evidence": evidence}

def _carrier(requirements: list, cases: list, defects: list | None = None,
             schema: str = SCHEMA_V1) -> str:
    return json.dumps({"schema": schema, "mission": "self-test", "generated_at": "2026-01-01T00:00:00Z",
                        "requirements": requirements, "cases": cases, "defects": defects or []})

def _junit_xml(testcases: list) -> str:
    return "<testsuite>" + "".join(testcases) + "</testsuite>"

def _tc(name: str, child: str = "") -> str:
    return f'<testcase classname="C" name="{name}">{child}</testcase>'

def _session(status: str, overrun: bool) -> str:
    return json.dumps({"charter": "c", "timebox_minutes": 30, "overrun": overrun, "areas": [],
                        "bugs_found": 0, "bug_ids": [], "status": status, "debrief": "d"})

def _run_fixture(carrier_text: str | None, files: dict) -> dict:
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        for rel, content in files.items():
            p = root / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content)
        carrier_path = root / "qa-traceability.yaml"
        if carrier_text is not None:
            carrier_path.write_text(carrier_text)
        return run_validator(carrier_path, root)

def _run_main(argv: list[str]) -> tuple[int, str, str]:
    """Used only by the A-10 Section D pairs (32.x), which must exercise `main()`'s own
    `--mission-dir` creation and `qa-coverage.json` write guards -- `run_validator` alone
    (what `_run_fixture` drives) never reaches either. Invokes `main()` in-process,
    capturing stdout/stderr and the real return code, so these pairs still run through
    the exact function the CLI entry point calls, never a parallel reimplementation."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = main(argv)
    return code, out.getvalue(), err.getvalue()

# A-10 (class) mechanical check -- data-model.md's amendment asks for "a sweep
# asserting no open/read_text/mkdir/write_text/glob/ET.parse call sits outside an
# OSError handler". `_sweep_unguarded_io` below IS that sweep, run every time
# `--self-test` runs (never only once, by hand, the way the four-site enumeration
# was): it walks this module's own AST, finds every call to a filesystem-touching
# method/function, and requires each one to be in exactly one of three buckets:
#   1. guarded -- lexically inside a `try` whose `except` catches OSError (or a
#      superclass: Exception/BaseException/bare except);
#   2. exempt -- inside a named test-harness function (`self_test`/`_run_fixture`/
#      `_run_main`/this sweep itself), which only ever touches a
#      `tempfile.TemporaryDirectory()` this same process just created, and whose
#      own escape (however unlikely) is still caught by `main()`'s backstop, since
#      `self_test()` is itself invoked through `main()`;
#   3. known-safe -- an exact (function, method) pair in `_IO_SWEEP_KNOWN_SAFE`
#      below, each with its own empirical-proof comment at the call site, so this
#      allowlist can never silently expand to cover a call nobody actually checked.
# Anything outside all three buckets is a violation -- this is precisely the check
# that would have caught the fifth (WP-frontmatter) site before an orchestrator had
# to find it by mutation, and the one meant to catch a sixth site added later.
_IO_METHOD_NAMES = frozenset({
    "open", "read_text", "write_text", "read_bytes", "write_bytes",
    "mkdir", "rmdir", "unlink", "rename", "replace", "touch", "symlink_to",
    "glob", "rglob", "iterdir", "stat", "lstat", "exists", "is_file",
    "is_dir", "is_symlink", "chmod", "resolve", "readlink", "walk", "load",
})
_IO_SWEEP_EXEMPT_FUNCS = frozenset({
    "self_test", "_run_fixture", "_run_main", "_sweep_unguarded_io",
    # A-11's own mechanical check and its `--help`/summary-line pair-count helper
    # (`_sweep_unvalidated_wp_access`, its nested `references_tainted_name`, and
    # `_derive_self_test_pair_count`) are exempt for the identical reason
    # `_sweep_unguarded_io` itself is: all three call `ast.walk(...)` repeatedly
    # while inspecting this module's own source, and `ast.walk` shares its bare
    # method name with `Path.walk()` (one of the real filesystem methods this
    # frozenset above lists) -- the sweep's own name-extraction cannot tell
    # `ast.walk(tree)` apart from `some_path.walk()` without inspecting the call's
    # receiver, which `ET.parse`'s existing special-case shows is possible but
    # which this module does not bother doing for a second, rarer case. Each of
    # these three functions' only OTHER filesystem touch is `Path(__file__).
    # read_text()`, already guarded by its own `try/except OSError`.
    "_sweep_unvalidated_wp_access", "references_tainted_name",
    "_derive_self_test_pair_count",
})
_IO_SWEEP_KNOWN_SAFE = frozenset({
    # Path.resolve(strict=False) -- swallows OSError internally (empirically
    # verified: an unreadable ancestor directory still resolves lexically).
    ("_resolve_contained", "resolve"),
    # Path.exists() -- swallows OSError internally, returns False instead.
    ("run_validator", "exists"),
    # Path.is_dir() / Path.glob() -- both swallow OSError internally (an
    # unreadable tasks/ directory itself still returns False / an empty list).
    ("_read_wp_frontmatter", "is_dir"),
    ("_read_wp_frontmatter", "glob"),
    # `_yaml().load(parts[1])` -- `parts[1]` is an in-memory string slice, not a
    # path; this call performs no filesystem I/O at all despite sharing a method
    # name with the two real file-reading `.load()` calls elsewhere.
    ("_read_wp_frontmatter", "load"),
})

def _sweep_unguarded_io() -> list[str]:
    """Returns a list of violation strings (empty == the class stays closed)."""
    try:
        source = Path(__file__).read_text()
    except OSError as exc:
        return [f"sweep could not read its own source ({__file__}): {exc}"]
    tree = ast.parse(source, filename=__file__)
    for node in ast.walk(tree):
        for child in ast.iter_child_nodes(node):
            child.parent = node  # type: ignore[attr-defined]

    def ancestors(node: ast.AST) -> list[ast.AST]:
        chain, n = [], node
        while hasattr(n, "parent"):
            n = n.parent  # type: ignore[attr-defined]
            chain.append(n)
        return chain

    def enclosing_func(node: ast.AST) -> str:
        for anc in ancestors(node):
            if isinstance(anc, (ast.FunctionDef, ast.AsyncFunctionDef)):
                return anc.name
        return "<module>"

    def is_oserror_guarded(node: ast.AST, anc_chain: list[ast.AST]) -> bool:
        for anc in anc_chain:
            if not isinstance(anc, ast.Try):
                continue
            if not anc.body:
                continue
            # Containment: node's line falls within the Try node's own `body`
            # span (not its handlers/orelse/finalbody) -- a call inside an
            # `except` clause is not "guarded", it is the handler itself.
            if anc.body[0].lineno <= node.lineno <= anc.body[-1].end_lineno:  # type: ignore[attr-defined]
                for handler in anc.handlers:
                    t = handler.type
                    caught = []
                    if t is None:
                        caught.append("bare")
                    elif isinstance(t, ast.Name):
                        caught.append(t.id)
                    elif isinstance(t, ast.Tuple):
                        caught.extend(e.id for e in t.elts if isinstance(e, ast.Name))
                    if any(c in ("OSError", "IOError", "Exception", "BaseException", "bare")
                           for c in caught):
                        return True
        return False

    seen_known_safe: set[tuple[str, str]] = set()
    violations: list[str] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        if isinstance(func, ast.Attribute):
            name = "ET.parse" if (func.attr == "parse" and isinstance(func.value, ast.Name)
                                   and func.value.id == "ET") else func.attr
        elif isinstance(func, ast.Name):
            name = func.id
        else:
            continue
        if name not in _IO_METHOD_NAMES and name != "ET.parse":
            continue
        fn = enclosing_func(node)
        if fn in _IO_SWEEP_EXEMPT_FUNCS:
            continue
        anc_chain = ancestors(node)
        if is_oserror_guarded(node, anc_chain):
            continue
        pair = (fn, name)
        if pair in _IO_SWEEP_KNOWN_SAFE:
            seen_known_safe.add(pair)
            continue
        violations.append(
            f"{__file__}:{node.lineno}: unguarded filesystem call {name!r} in {fn}() -- "
            f"neither inside a try/except OSError block nor in _IO_SWEEP_KNOWN_SAFE")
    stale = _IO_SWEEP_KNOWN_SAFE - seen_known_safe
    for fn, name in sorted(stale):
        violations.append(
            f"_IO_SWEEP_KNOWN_SAFE entry {(fn, name)!r} was never observed in the module -- "
            f"stale allowlist entry, remove it or fix the sweep")
    return violations

# A-11's own mechanical check ("in the same spirit as `_sweep_unguarded_io()` ...
# asserting that no filesystem access is constructed from carrier-supplied input
# without passing through the shared validate-and-contain helper"). `evidence.*.
# file` (A-8) and `wp` (A-11) are the two carrier-supplied strings this module
# constructs filesystem paths from today; every local/parameter NAME known, by
# direct code review, to hold one of them is registered here -- exactly the same
# "small, explicit, auditable allowlist, not a silent gap" discipline
# `_IO_SWEEP_KNOWN_SAFE` above already uses, extended by hand whenever a new
# carrier-supplied field starts touching the filesystem (there is no way to derive
# this set automatically from the schema tables without a real interprocedural
# taint engine -- see the honesty note in `_sweep_unvalidated_wp_access`'s own
# docstring below for exactly what this check does and does not prove).
_CARRIER_TAINTED_NAMES = frozenset({"wp", "wp_id"})

def _sweep_unvalidated_wp_access() -> list[str]:
    """Returns a list of violation strings (empty == the class stays closed).

    For every function DEFINED in this module, walks its body for a call to one of
    `_IO_METHOD_NAMES` (the same filesystem-touching-method inventory `_sweep_
    unguarded_io` uses) whose arguments lexically reference a name in `_CARRIER_
    TAINTED_NAMES` -- i.e. a carrier-supplied value flowing straight into a
    filesystem call. Any function found doing that MUST also contain, earlier in
    its own body (by source line order), a call to `_resolve_contained` or
    `_validate_wp_id` -- the one shared validate-and-contain helper A-8 built and
    A-11 requires every carrier-tainted path to route through. A function that
    touches the filesystem with a tainted name but never calls the helper first is
    a violation, named by file/line/function -- this is precisely the shape of the
    original A-11 defect (`check_bridge` -> `_read_wp_frontmatter` -> an unguarded,
    unanchored `tasks_dir.glob(f"{wp_id}*.md")`), and precisely what would catch a
    sixth carrier-tainted field added later without being routed the same way.

    Honesty note on scope (the mission's own instruction: "an honest 'could not'
    beats a check that cannot fail"). This is a LEXICAL/syntactic proxy for taint,
    exactly as `_sweep_unguarded_io` above is a lexical proxy for exception-safety
    -- it does not perform real interprocedural dataflow analysis. Concretely, it
    cannot see through a rename/alias (`x = wp_id; tasks_dir.glob(f"{x}*.md")`
    would evade it), cannot verify the guarding call's *result* is actually
    checked/honoured before the filesystem call runs (only that the call
    textually precedes it), and depends on `_CARRIER_TAINTED_NAMES` being kept
    up to date by hand -- a full static guarantee would need a real taint-
    tracking engine, which is out of reach for a single stdlib-only file under
    NFR-001. What it DOES prove, mechanically, every `--self-test` run: the
    concrete A-11 defect shape (a carrier-tainted name reaching a filesystem
    call with zero validating call anywhere in the same function) cannot recur
    silently in this module without the sweep failing first."""
    try:
        source = Path(__file__).read_text()
    except OSError as exc:
        return [f"wp-access sweep could not read its own source ({__file__}): {exc}"]
    tree = ast.parse(source, filename=__file__)
    for node in ast.walk(tree):
        for child in ast.iter_child_nodes(node):
            child.parent = node  # type: ignore[attr-defined]

    def enclosing_funcdef(node: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef | None:
        n = node
        while hasattr(n, "parent"):
            n = n.parent  # type: ignore[attr-defined]
            if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
                return n
        return None

    def references_tainted_name(call: ast.Call) -> bool:
        for arg_node in list(call.args) + [kw.value for kw in call.keywords]:
            for sub in ast.walk(arg_node):
                if isinstance(sub, ast.Name) and sub.id in _CARRIER_TAINTED_NAMES:
                    return True
        return False

    def call_name(node: ast.Call) -> str | None:
        func = node.func
        if isinstance(func, ast.Attribute):
            return func.attr
        if isinstance(func, ast.Name):
            return func.id
        return None

    violations: list[str] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        name = call_name(node)
        if name not in _IO_METHOD_NAMES or not references_tainted_name(node):
            continue
        fn_def = enclosing_funcdef(node)
        fn_name = fn_def.name if fn_def is not None else "<module>"
        guarded = False
        if fn_def is not None:
            for sub in ast.walk(fn_def):
                if (isinstance(sub, ast.Call) and call_name(sub) in
                        ("_resolve_contained", "_validate_wp_id") and sub.lineno <= node.lineno):
                    guarded = True
                    break
        if not guarded:
            violations.append(
                f"{__file__}:{node.lineno}: filesystem call {name!r} in {fn_name}() "
                f"references a carrier-tainted name ({sorted(_CARRIER_TAINTED_NAMES)}) "
                f"without a preceding _resolve_contained/_validate_wp_id call in the "
                f"same function")
    return violations

_PAIR_ID_RE = re.compile(r"^Pair(\d+)(?:\.\d+)?$")

def _derive_self_test_pair_count() -> int:
    """Severity-2 staleness fix: `--help` and the T007 comment above both used to
    hand-type "32" while the suite had grown to 34 pairs (and two toolguide docs
    elsewhere in the pack separately hand-typed "29") -- four independent places
    to keep in sync by hand, three of which had already drifted. This function IS
    the single source of truth `--help` and the final self-test summary line both
    call: it parses this module's OWN source (the same self-inspection technique
    `_sweep_unguarded_io`/`_sweep_unvalidated_wp_access` already use), walks
    `self_test`'s AST for every string-literal constant matching `Pair<N>` or
    `Pair<N>.<M>`, and counts the number of DISTINCT base numbers `N` -- e.g.
    `Pair31.1`..`Pair31.4` are four fixtures for the same numbered Pair 31,
    counted once, matching the base-number convention this file's own comments
    already use ("pairs 30-32", "pairs 15.3, 24-29"). Never hand-typed again."""
    try:
        source = Path(__file__).read_text()
    except OSError:
        return 0
    tree = ast.parse(source, filename=__file__)
    base_numbers: set[str] = set()
    for node in ast.walk(tree):
        if not isinstance(node, ast.FunctionDef) or node.name != "self_test":
            continue
        for sub in ast.walk(node):
            if isinstance(sub, ast.Constant) and isinstance(sub.value, str):
                m = _PAIR_ID_RE.match(sub.value)
                if m:
                    base_numbers.add(m.group(1))
    return len(base_numbers)

def self_test() -> int:
    failures: list[str] = []

    def eqs(pair: str, rec: dict, **expected: object) -> None:
        rec = {**rec, "exit_code": exit_code_for(rec)}
        for k, v in expected.items():
            if rec.get(k) != v:
                failures.append(f"{pair} - {k}: expected {v!r}, got {rec.get(k)!r}")

    def ok(pair: str, desc: str, cond: bool) -> None:
        if not cond:
            failures.append(f"{pair} - {desc}: condition failed")

    def expect_schema_violation(pair: str, carrier_text: str, files: dict, needle: str,
                                 desc: str, lower: bool = False) -> None:
        """Shared shape for Pairs 16.1-18.4 (round-2 review F3: nine-plus near-identical
        call sites, no shared helper): run the fixture, assert the closed-schema/
        structural-error triplet (status=error, exit_code=2, requirements_total=None),
        then assert `needle` names the specific offending key/id inside `reason` --
        every call site keeps its own literal needle and description text, so no
        assertion is weakened by this extraction."""
        rec = _run_fixture(carrier_text, files)
        eqs(pair, rec, status="error", exit_code=2, requirements_total=None)
        reason = rec["reason"] or ""
        ok(pair, desc, needle in (reason.lower() if lower else reason))

    _ERROR_REASON_PHRASES = ("dangling", "duplicate", "unparseable", "unresolved", "bridge invariant")

    def expect_only_reason_phrase(pair: str, rec: dict, phrase: str) -> None:
        """Round-3 AST-sweep closure: `_carrier_error_reason`'s five `if <cond>:
        parts.append(...)` branches, plus the `"; ".join(parts) or "..."`
        fallback, had zero fixture ever checking the composed `reason` text's
        actual content -- only that it was non-None (Pair4) -- so a mutant
        that always fell through to the generic "carrier failed validation"
        fallback, or that spuriously appended an unrelated category, survived
        unnoticed. Every pair below that isolates exactly one of the five
        error categories now asserts its own phrase is present and every
        sibling category's phrase is absent."""
        reason = rec["reason"] or ""
        ok(pair, f"reason contains '{phrase}'", phrase in reason)
        for other in _ERROR_REASON_PHRASES:
            if other != phrase:
                ok(pair, f"reason does not spuriously contain '{other}'", other not in reason)

    # Pair 1 — three-state loudness (SC-001): measured/not_measured/error(v-mismatch).
    rec = _run_fixture(
        _carrier([_req("FR-200", ["TC-001"])], [_case("TC-001", "WP01", "automated",
            {"junit": {"file": "evidence/results/a.xml", "testcase": "C::n"}})]),
        {"evidence/results/a.xml": _junit_xml([_tc("n")])})
    eqs("Pair1.1", rec, status="measured", verdict="ready", exit_code=0, requirements_total=1,
        schema=OUTPUT_SCHEMA)

    rec = _run_fixture(None, {})
    eqs("Pair1.2", rec, status="not_measured", exit_code=2,
        requirements_total=None, requirements_covered=None)

    rec = _run_fixture(
        _carrier([_req("FR-201", [], "synthetic rationale")], [], schema="qa-traceability/v2"), {})
    eqs("Pair1.3", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair1.3", "reason names v2 and v1",
       "v2" in (rec["reason"] or "") and "v1" in (rec["reason"] or ""))

    # Pair 2 — bridge invariant (SC-002), reusing this mission's own FR-011.
    bridge_carrier = _carrier([_req("FR-011", ["TC-009"])], [_case("TC-009", "WP99", "automated",
        {"junit": {"file": "evidence/results/b.xml", "testcase": "C::n"}})])
    xml_file = {"evidence/results/b.xml": _junit_xml([_tc("n")])}

    rec = _run_fixture(bridge_carrier, {**xml_file, "tasks/WP99.md":
        "---\n" + json.dumps({"requirement_refs": ["FR-999"]}) + "\n---\nbody\n"})
    eqs("Pair2.1", rec, exit_code=2)
    v = (rec["bridge_violations"] or [{}])[0]
    ok("Pair2.1", "violation names FR-011/TC-009/WP99.md",
       v.get("requirement_id") == "FR-011" and v.get("case_id") == "TC-009"
       and "WP99.md" in (v.get("wp_file") or ""))
    expect_only_reason_phrase("Pair2.1", rec, "bridge invariant")

    rec = _run_fixture(bridge_carrier, {**xml_file, "tasks/WP99.md":
        "---\n" + json.dumps({"requirement_refs": ["FR-011"]}) + "\n---\nbody\n"})
    eqs("Pair2.2", rec, bridge_violations=[], status="measured", verdict="ready")

    rec = _run_fixture(bridge_carrier, dict(xml_file))
    eqs("Pair2.3", rec, bridge_violations=[])
    nm = (rec["bridge_not_measured"] or [{}])[0]
    ok("Pair2.3", "not_measured names FR-011/TC-009/WP99.md",
       nm.get("requirement_id") == "FR-011" and nm.get("case_id") == "TC-009"
       and "WP99.md" in (nm.get("wp_file") or "") and "WP99.md" in (nm.get("reason") or ""))
    ok("Pair2.3", "not forced to error", exit_code_for(rec) != 2)

    # Pair 3 — JUnit-XML classification parity (SC-003, NFR-002): the fifth
    # testcase carries both <failure> and <skipped> and must classify failed.
    junit_cases = [_case(f"TC-30{i}", "WP01", "automated",
        {"junit": {"file": "evidence/results/j.xml", "testcase": f"C::t{i}"}}) for i in range(1, 6)]
    xml = _junit_xml([_tc("t1"), _tc("t2", '<failure message="x"/>'), _tc("t3", '<error message="x"/>'),
                       _tc("t4", "<skipped/>"), _tc("t5", '<failure message="x"/><skipped/>')])
    rec = _run_fixture(_carrier([], junit_cases), {"evidence/results/j.xml": xml})
    eqs("Pair3", rec, passed=1, failed=3, skipped=1, cases_total=5)

    # Pair 4 — results_missing forced-error, plus reasons._carrier extension.
    rec = _run_fixture(
        _carrier([_req("FR-202", ["TC-020"])], [_case("TC-020", "WP01", "automated",
            {"junit": {"file": "evidence/results/m.xml", "testcase": "C::does_not_exist"}})]),
        {"evidence/results/m.xml": _junit_xml([_tc("other")])})
    eqs("Pair4", rec, status="error", exit_code=2, results_missing=1,
        requirements_covered=0, requirements_blocked=["FR-202"])
    ok("Pair4", "reasons names TC-020 with testcase string",
       "does_not_exist" in (rec["reasons"].get("TC-020") or ""))
    ok("Pair4", "reasons._carrier duplicates top-level reason",
       rec["reasons"].get("_carrier") == rec["reason"] and rec["reason"] is not None)
    expect_only_reason_phrase("Pair4", rec, "unresolved")

    # Pair 5 — overrun-excluded session case: its own dedicated bucket, never passed.
    rec = _run_fixture(
        _carrier([_req("FR-203", ["TC-030"])], [_case("TC-030", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/s.session.yaml"}})]),
        {"evidence/sessions/s.session.yaml": _session("completed", True)})
    eqs("Pair5", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_uncovered=[], passed=0, failed=0, skipped=0, overrun=1, results_parsed=1)

    # Pair 6 — malformed evidence file, both kinds (evidence_unparseable, never
    # results_missing/evidence_file_missing).
    rec = _run_fixture(
        _carrier([_req("FR-204", ["TC-050"])], [_case("TC-050", "WP01", "automated",
            {"junit": {"file": "evidence/results/bad.xml", "testcase": "C::n"}})]),
        {"evidence/results/bad.xml": "not xml at all <<< unterminated"})
    eqs("Pair6.1", rec, status="error", exit_code=2, results_missing=0, evidence_file_missing=0,
        requirements_covered=0, requirements_blocked=["FR-204"])
    ok("Pair6.1", "evidence_unparseable[0] names TC-050/junit with a reason",
       len(rec["evidence_unparseable"]) == 1 and rec["evidence_unparseable"][0]["case_id"] == "TC-050"
       and rec["evidence_unparseable"][0]["evidence_kind"] == "junit"
       and bool(rec["evidence_unparseable"][0].get("reason")))
    expect_only_reason_phrase("Pair6.1", rec, "unparseable")

    bad_session = json.dumps({"timebox_minutes": 30, "overrun": False, "areas": [], "bugs_found": 0,
                               "bug_ids": [], "status": "completed", "debrief": "d"})
    rec = _run_fixture(
        _carrier([_req("FR-205", ["TC-051"])], [_case("TC-051", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/bad.session.yaml"}})]),
        {"evidence/sessions/bad.session.yaml": bad_session})
    eqs("Pair6.2", rec, status="error")
    ok("Pair6.2", "evidence_unparseable[0] names TC-051/session, reason names 'charter'",
       len(rec["evidence_unparseable"]) == 1 and rec["evidence_unparseable"][0]["case_id"] == "TC-051"
       and rec["evidence_unparseable"][0]["evidence_kind"] == "session"
       and "charter" in (rec["evidence_unparseable"][0].get("reason") or ""))

    # Pair 7 — dangling case_ids reference: never crashes, never exit 1.
    rec = _run_fixture(_carrier([_req("FR-206", ["TC-060"])], []), {})
    eqs("Pair7", rec, status="error", exit_code=2,
        dangling_case_refs=[{"requirement_id": "FR-206", "case_id": "TC-060"}])
    ok("Pair7", "requirement excluded from covered/uncovered/blocked",
       "FR-206" not in rec["requirements_uncovered"] and "FR-206" not in rec["requirements_blocked"])
    expect_only_reason_phrase("Pair7", rec, "dangling")

    # Pair 8 — duplicate cases[].id: structured by-count shape, excluded from every count.
    dup_cases = [_case("TC-070", "WP01", "automated", {"junit": {"file": "x.xml", "testcase": "C::n"}}),
                 _case("TC-070", "WP02", "automated", {"junit": {"file": "y.xml", "testcase": "C::n"}})]
    rec = _run_fixture(_carrier([], dup_cases), {})
    eqs("Pair8", rec, status="error", exit_code=2, results_parsed=0,
        duplicate_case_ids=[{"id": "TC-070", "count": 2}])
    expect_only_reason_phrase("Pair8", rec, "duplicate")

    # Pair 9 — requirements_blocked: a real, resolved, failing outcome (never
    # results_missing).
    rec = _run_fixture(
        _carrier([_req("FR-207", ["TC-080"])], [_case("TC-080", "WP01", "automated",
            {"junit": {"file": "evidence/results/f.xml", "testcase": "C::n"}})]),
        {"evidence/results/f.xml": _junit_xml([_tc("n", '<failure message="x"/>')])})
    eqs("Pair9", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_uncovered=[], requirements_blocked=["FR-207"])

    # Pair 10 — ctrf_pending literal reason text.
    rec = _run_fixture(
        _carrier([_req("FR-208", ["TC-090"])], [_case("TC-090", "WP01", "manual", {"ctrf": {}})]), {})
    eqs("Pair10", rec, status="measured", verdict="blocked",
        ctrf_pending=[{"case_id": "TC-090", "reason": CTRF_REASON}])

    # Pair 11 — evidence_file_missing positive count + the full cases identity.
    rec = _run_fixture(
        _carrier([_req("FR-209", ["TC-100"])], [_case("TC-100", "WP01", "automated",
            {"junit": {"file": "evidence/results/never-created.xml", "testcase": "C::n"}})]), {})
    eqs("Pair11", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_uncovered=[], requirements_blocked=["FR-209"], cases_total=1,
        results_parsed=0, results_missing=0, evidence_unparseable=[], ctrf_pending=[],
        evidence_file_missing=1)
    ok("Pair11", "cases identity holds", rec["cases_total"] == (
        rec["results_parsed"] + rec["results_missing"] + len(rec["ctrf_pending"])
        + len(rec["evidence_unparseable"]) + rec["evidence_file_missing"]
        + sum(d["count"] for d in rec["duplicate_case_ids"])))

    # Pair 12 — status: aborted session case lands in failed, never overrun.
    rec = _run_fixture(
        _carrier([_req("FR-210", ["TC-110"])], [_case("TC-110", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/aborted.session.yaml"}})]),
        {"evidence/sessions/aborted.session.yaml": _session("aborted", False)})
    eqs("Pair12", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_blocked=["FR-210"], passed=0, overrun=0, skipped=0, failed=1, results_parsed=1)

    # Pair 13 — all-empty-lists carrier: real zeroes, never null.
    rec = _run_fixture(_carrier([], [], []), {})
    eqs("Pair13", rec, status="measured", verdict="ready", exit_code=0, requirements_total=0,
        requirements_covered=0, cases_total=0, defects_total=0)

    # Pair 14 — requirements_covered/uncovered, reasons's uncovered-rationale
    # category, and defects_total, each driven above zero/empty for the first time.
    rationale_14 = ("Requires a manual audit step this mission does not ship; "
                     "tracked for visibility, not counted as covered.")
    rec = _run_fixture(
        _carrier([_req("FR-004", ["TC-140"]), _req("FR-014", [], rationale_14)],
                 [_case("TC-140", "WP01", "automated",
                        {"junit": {"file": "evidence/results/p.xml", "testcase": "C::n"}})],
                 [{"id": "DEF-001", "case_id": None,
                   "description": "Synthetic self-test defect record, not a real defect.",
                   "severity": "low"}]),
        {"evidence/results/p.xml": _junit_xml([_tc("n")])})
    eqs("Pair14", rec, status="measured", verdict="blocked", exit_code=1, requirements_total=2,
        requirements_covered=1, requirements_uncovered=["FR-014"], requirements_blocked=[],
        reasons={"FR-014": rationale_14}, defects_total=1)
    ok("Pair14", "requirements identity holds", rec["requirements_total"] == (
        rec["requirements_covered"] + len(rec["requirements_uncovered"])
        + len(rec["requirements_blocked"]) + len(rec["dangling_case_refs"])))
    ok("Pair14", "cases identity holds", rec["cases_total"] == (
        rec["results_parsed"] + rec["results_missing"] + len(rec["ctrf_pending"])
        + len(rec["evidence_unparseable"]) + rec["evidence_file_missing"]
        + sum(d["count"] for d in rec["duplicate_case_ids"])))

    # --- Round-2 closure pairs (WP01 review round 2, F1 closed as a CLASS via a
    # mutation-sweep-verified rule -> covering-pair map, not merely a 15th fixture) ---

    # Pair 15 — case_ids: [] requires a non-empty rationale (data-model.md Section A
    # `rationale` row: "A case_ids: [] entry with an empty/missing rationale is a
    # schema ERROR, not a silent blocked" -- this WP's own F1 finding: the check is
    # implemented correctly but no prior fixture ever drove it).
    rec = _run_fixture(_carrier([_req("FR-211", [])], []), {})
    eqs("Pair15.1", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair15.1", "reason names the offending requirement FR-211 (missing rationale)",
       "FR-211" in (rec["reason"] or ""))

    rec = _run_fixture(_carrier([_req("FR-212", [], "")], []), {})
    eqs("Pair15.2", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair15.2", "reason names the offending requirement FR-212 (empty-string rationale)",
       "FR-212" in (rec["reason"] or ""))

    # Pair 15.3 — round-3 AST-sweep survivor: `rationale` is an OPTIONAL key
    # (REQ_ALLOWED, not REQ_REQUIRED), so a real carrier's requirements[] entry
    # can omit the `rationale` key entirely (not merely set it null) -- distinct
    # from Pairs 15.1/15.2, which always construct the key (via `_req()`'s own
    # `rationale=None` default). `validate()`'s own empty-case_ids-without-
    # rationale check reads `r.get("rationale")`, safely returning `None` for
    # a key that is genuinely absent; the sweep's dict.get()->subscript mutant
    # on this exact call raised an unhandled KeyError against every pre-15.3
    # fixture, since none of them ever omits the key.
    req_no_rationale_key = _req("FR-221", [])
    del req_no_rationale_key["rationale"]
    rec = _run_fixture(_carrier([req_no_rationale_key], []), {})
    eqs("Pair15.3", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair15.3", "reason names the offending requirement FR-221 (rationale key absent)",
       "FR-221" in (rec["reason"] or ""))

    # Pair 16 — closed-schema enforcement at the document root and top level
    # (Section A "Closed-schema enforcement"), plus the "schema" key's own
    # presence/prefix rule read as a distinct sub-case from a version mismatch.
    expect_schema_violation("Pair16.1", "[]", {}, "mapping",
       "reason names the carrier root not being a mapping")

    expect_schema_violation("Pair16.2",
       json.dumps({"mission": "self-test", "generated_at": "2026-01-01T00:00:00Z",
                    "requirements": [], "cases": [], "defects": []}), {}, "missing",
       "reason names the missing/non-conforming 'schema' key specifically "
       "(not merely a version-mismatch fallback for a None schema value)", lower=True)

    bogus_top = json.loads(_carrier([], []))
    bogus_top["not_a_real_top_key"] = True
    expect_schema_violation("Pair16.3", json.dumps(bogus_top), {}, "not_a_real_top_key",
       "reason names the unrecognised top-level key")

    missing_mission = json.loads(_carrier([], []))
    del missing_mission["mission"]
    expect_schema_violation("Pair16.4", json.dumps(missing_mission), {}, "mission",
       "reason names the missing required top-level key 'mission'")

    # Pair 17 — closed-schema enforcement on requirements[]/cases[]/defects[]
    # entries (Section A per-entry field tables: unrecognised or missing keys).
    bad_req = _req("FR-213", ["TC-170"])
    bad_req["not_a_real_key"] = True
    expect_schema_violation("Pair17.1",
        _carrier([bad_req], [_case("TC-170", "WP01", "automated",
            {"junit": {"file": "evidence/results/q.xml", "testcase": "C::n"}})]),
        {"evidence/results/q.xml": _junit_xml([_tc("n")])}, "not_a_real_key",
        "reason names the unrecognised requirements[] key")

    bad_case = _case("TC-171", "WP01", "automated",
        {"junit": {"file": "evidence/results/r.xml", "testcase": "C::n"}})
    del bad_case["wp"]
    expect_schema_violation("Pair17.2", _carrier([], [bad_case]),
        {"evidence/results/r.xml": _junit_xml([_tc("n")])}, "wp",
        "reason names the missing cases[] key 'wp'")

    bad_defect = {"id": "DEF-002", "case_id": None, "description": "x", "severity": "low",
                  "not_a_real_key": True}
    expect_schema_violation("Pair17.3", _carrier([], [], [bad_defect]), {}, "not_a_real_key",
        "reason names the unrecognised defects[] key")

    # Pair 18 — closed-schema enforcement on `evidence`'s exactly-one-key rule
    # and on the JUnit/session evidence sub-objects (Section A cases[].evidence
    # row and cases[].evidence.{junit,session} field tables).
    zero_ev_case = _case("TC-180", "WP01", "automated", {})
    expect_schema_violation("Pair18.1", _carrier([], [zero_ev_case]), {}, "TC-180",
        "reason names the zero-evidence-key violation")

    two_ev_case = _case("TC-181", "WP01", "automated",
        {"junit": {"file": "evidence/results/s.xml", "testcase": "C::n"},
         "session": {"file": "evidence/sessions/s2.session.yaml"}})
    expect_schema_violation("Pair18.2", _carrier([], [two_ev_case]), {}, "TC-181",
        "reason names the two-evidence-key violation")

    bad_junit_case = _case("TC-182", "WP01", "automated",
        {"junit": {"file": "evidence/results/t.xml", "testcase": "C::n",
                   "not_a_real_key": True}})
    expect_schema_violation("Pair18.3", _carrier([], [bad_junit_case]),
        {"evidence/results/t.xml": _junit_xml([_tc("n")])}, "not_a_real_key",
        "reason names the unrecognised junit evidence key")

    bad_session_case = _case("TC-183", "WP01", "exploratory",
        {"session": {"file": "evidence/sessions/u.session.yaml", "not_a_real_key": True}})
    expect_schema_violation("Pair18.4", _carrier([], [bad_session_case]),
        {"evidence/sessions/u.session.yaml": _session("completed", False)}, "not_a_real_key",
        "reason names the unrecognised session evidence key")

    # Pair 19 — a session's `status: aborted` always lands in `failed`, regardless
    # of `overrun`'s value (Section C `overrun` row: "never counts as a passing
    # outcome ... even if status: completed" read together with "status: aborted
    # -> failed (regardless of overrun's value on an aborted session)"). Pair 12
    # only ever paired aborted with overrun: false; this pairs aborted with
    # overrun: true, proving the branch order (aborted checked before overrun),
    # not merely the aborted-alone outcome.
    rec = _run_fixture(
        _carrier([_req("FR-214", ["TC-190"])], [_case("TC-190", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/aborted-overrun.session.yaml"}})]),
        {"evidence/sessions/aborted-overrun.session.yaml": _session("aborted", True)})
    eqs("Pair19", rec, status="measured", verdict="blocked", requirements_blocked=["FR-214"],
        passed=0, overrun=0, skipped=0, failed=1, results_parsed=1)

    # Pair 20 — session evidence's own `evidence_file_missing` benign state
    # (Section A cases[].evidence.session row: the same three file-level states
    # JUnit evidence has apply here too, by the identical precedent). Pair 11
    # only ever drove this field positive for a `junit` case; this proves the
    # `session` kind shares the same benign (never `status: error`) home, not
    # `evidence_unparseable`.
    rec = _run_fixture(
        _carrier([_req("FR-215", ["TC-200"])], [_case("TC-200", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/never-created.session.yaml"}})]), {})
    eqs("Pair20", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_uncovered=[], requirements_blocked=["FR-215"], cases_total=1,
        results_parsed=0, evidence_unparseable=[], evidence_file_missing=1)

    # Pair 21 — a requirement excluded via `dangling_case_refs` is excluded from
    # the bridge check *entirely*, even when one of its other `case_ids` resolves
    # to a real, bridge-violating case (Section A dangling row: "excluded from
    # further coverage classification"; a requirement that is only *partially*
    # dangling must still never leak a `bridge_violations`/`bridge_not_measured`
    # entry for its resolvable case).
    rec = _run_fixture(
        _carrier([_req("FR-216", ["TC-210", "TC-211"])],
                 [_case("TC-210", "WP99", "automated",
                        {"junit": {"file": "evidence/results/v.xml", "testcase": "C::n"}})]),
        {"evidence/results/v.xml": _junit_xml([_tc("n")]),
         "tasks/WP99.md": "---\n" + json.dumps({"requirement_refs": ["FR-999"]}) + "\n---\nbody\n"})
    eqs("Pair21", rec, status="error", exit_code=2,
        dangling_case_refs=[{"requirement_id": "FR-216", "case_id": "TC-211"}],
        bridge_violations=[], bridge_not_measured=[])
    ok("Pair21", "requirement excluded from covered/uncovered/blocked too",
       "FR-216" not in rec["requirements_uncovered"] and "FR-216" not in rec["requirements_blocked"])

    # Pair 22 — a WP file that exists but carries no parseable YAML frontmatter
    # block at all is a real `bridge_violations` ERROR, never the benign
    # `bridge_not_measured` reserved for a WP file that does not exist
    # (Section C bridge read contract, the file-exists-but-unparseable/
    # non-conforming branch, distinct from Pair 2.3's file-absent branch).
    rec = _run_fixture(
        _carrier([_req("FR-217", ["TC-220"])], [_case("TC-220", "WP98", "automated",
            {"junit": {"file": "evidence/results/w.xml", "testcase": "C::n"}})]),
        {"evidence/results/w.xml": _junit_xml([_tc("n")]),
         "tasks/WP98.md": "no frontmatter delimiters in this file at all\njust body text\n"})
    eqs("Pair22", rec, exit_code=2, bridge_not_measured=[])
    v = (rec["bridge_violations"] or [{}])[0]
    ok("Pair22", "malformed-frontmatter WP file is a real violation naming FR-217/TC-220/WP98.md",
       v.get("requirement_id") == "FR-217" and v.get("case_id") == "TC-220"
       and "WP98.md" in (v.get("wp_file") or ""))

    # Pair 23 — the Coverage rule's "every case it references" is `all()`, not
    # `any()`: a requirement with two case_ids where only one resolves to a
    # passing outcome must still be `requirements_blocked`, never covered.
    rec = _run_fixture(
        _carrier([_req("FR-218", ["TC-230", "TC-231"])],
                 [_case("TC-230", "WP01", "automated",
                        {"junit": {"file": "evidence/results/x1.xml", "testcase": "C::n"}}),
                  _case("TC-231", "WP01", "automated",
                        {"junit": {"file": "evidence/results/x2.xml", "testcase": "C::n"}})]),
        {"evidence/results/x1.xml": _junit_xml([_tc("n")]),
         "evidence/results/x2.xml": _junit_xml([_tc("n", '<failure message="x"/>')])})
    eqs("Pair23", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_blocked=["FR-218"])

    # --- Round-3 closure pairs (WP01 review round 3, F1/F2: the AST-driven mutation
    # sweep this round ran mechanically over every Compare/BoolOp/If/all()-any()/
    # dict.get() site in the validation+classification+coverage+bridge code paths
    # found these same two survivors round 2's own independent reviewer had already
    # found by hand, among 42 first-pass survivors across 218 mutable sites. ---

    # Pair 24 — closes F1: check_bridge's `cases_by_id.get(cid)` / `if case is
    # None: continue` guard (T005) is reachable, and load-bearing, ONLY for a
    # `case_ids` reference that resolves at the `all_case_ids` membership check
    # (so is not `dangling_case_refs`) but has been excluded from `cases_by_id`
    # specifically because its id is *duplicated* in `cases[]` -- Pair 7 exercises
    # a genuinely dangling reference (excluded one level up, before this guard is
    # ever reached) and Pair 8 builds a duplicate-id fixture with an empty
    # `requirements` list, so neither pair ever drives execution into this guard.
    # Replacing the guard with a bare `cases_by_id[cid]` lookup (the WP01 prompt's
    # own third falsifiable-bar example) raises an unhandled KeyError here — a
    # crash colliding with the default exit code `1` — with every other 24
    # (pre-this-pair) fixture still green; this fixture is the one that reaches it.
    dup_ref_cases = [
        _case("TC-240", "WP01", "automated", {"junit": {"file": "x.xml", "testcase": "C::n"}}),
        _case("TC-240", "WP02", "automated", {"junit": {"file": "y.xml", "testcase": "C::n"}}),
    ]
    rec = _run_fixture(_carrier([_req("FR-219", ["TC-240"])], dup_ref_cases), {})
    eqs("Pair24", rec, status="error", exit_code=2,
        duplicate_case_ids=[{"id": "TC-240", "count": 2}],
        bridge_violations=[], bridge_not_measured=[], requirements_blocked=["FR-219"])
    ok("Pair24", "requirement excluded from covered/uncovered, lands in blocked only",
       "FR-219" not in rec["requirements_uncovered"] and rec["requirements_covered"] == 0)

    # Pair 25 — closes F2: the Coverage rule's `all(case_outcomes.get(cid) ==
    # "passed" for cid in case_ids)` check must exclude a `skipped` JUnit outcome
    # from counting as covering -- Pair 3 is the only prior fixture exercising
    # `skipped` at all, and its skipped testcase belongs to a case no requirement's
    # `case_ids` references. Widening the comparison to `in ("passed", "skipped")`
    # -- the QA_003 "no fake green" failure mode this pack exists to prevent --
    # left every pre-this-pair fixture green; this one links a `skipped`-classified
    # case directly to a requirement's own `case_ids`.
    rec = _run_fixture(
        _carrier([_req("FR-220", ["TC-250"])], [_case("TC-250", "WP01", "automated",
            {"junit": {"file": "evidence/results/y.xml", "testcase": "C::n"}})]),
        {"evidence/results/y.xml": _junit_xml([_tc("n", "<skipped/>")])})
    eqs("Pair25", rec, status="measured", verdict="blocked", requirements_covered=0,
        requirements_blocked=["FR-220"], skipped=1)

    # --- Round-3 AST-sweep closure pairs 26-29: mutable sites the mechanical
    # sweep found reachable that zero pre-round-3 fixture ever drove down --
    # every one of these guards is correct today; each was simply never
    # exercised by a fixture that makes its guarded condition true. See this
    # WP's own report for the full site-count and sweep result table. ---

    # Pair 26 — closed-schema/type-shape guards.
    rec = _run_fixture(_carrier(["not-a-mapping"], []), {})
    eqs("Pair26.1", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair26.1", "reason names a requirements[] entry that is not a mapping",
       "mapping" in (rec["reason"] or ""))

    bad_lists = json.loads(_carrier([], []))
    bad_lists["requirements"] = "not-a-list"
    rec = _run_fixture(json.dumps(bad_lists), {})
    eqs("Pair26.2", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair26.2", "reason names requirements/cases/defects must each be a list",
       "list" in (rec["reason"] or ""))

    rec = _run_fixture(_carrier([], [_case("TC-261", "WP01", "automated", "not-a-dict-evidence")]), {})
    eqs("Pair26.3", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair26.3", "reason names TC-261's evidence not being a mapping",
       "TC-261" in (rec["reason"] or "") and "mapping" in (rec["reason"] or ""))

    # `rationale` is deliberately present-and-non-empty here so the *other*
    # requirements[]-shape check (line ~118, "empty case_ids without a
    # non-empty rationale") cannot also independently reject this fixture --
    # only the required-key check (_check_entry) can, isolating it.
    req_missing_case_ids = {"id": "FR-223", "rationale": "some valid reason"}
    rec = _run_fixture(_carrier([req_missing_case_ids], []), {})
    eqs("Pair26.4", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair26.4", "reason names the missing required requirements[] key 'case_ids' "
       "specifically via the required-key check, not the empty-case_ids-without-"
       "rationale check (which this fixture's non-empty rationale cannot trip)",
       "missing required key" in (rec["reason"] or "") and "case_ids" in (rec["reason"] or ""))

    defect_missing_severity = {"id": "DEF-003", "case_id": None, "description": "x"}
    rec = _run_fixture(_carrier([], [], [defect_missing_severity]), {})
    eqs("Pair26.5", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair26.5", "reason names the missing required defects[] key 'severity'",
       "severity" in (rec["reason"] or ""))

    # Pair 27 — session-evidence content-level guards (classify_session).
    rec = _run_fixture(
        _carrier([_req("FR-224", ["TC-262"])], [_case("TC-262", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/list.session.yaml"}})]),
        {"evidence/sessions/list.session.yaml": "- a\n- b\n"})
    eqs("Pair27.1", rec, status="error", exit_code=2, requirements_total=1)
    ok("Pair27.1", "evidence_unparseable[0] names TC-262/session, reason names 'mapping'",
       len(rec["evidence_unparseable"]) == 1 and rec["evidence_unparseable"][0]["case_id"] == "TC-262"
       and "mapping" in (rec["evidence_unparseable"][0].get("reason") or ""))

    bad_status_session = json.dumps({"charter": "c", "timebox_minutes": 30, "overrun": False,
        "areas": [], "bugs_found": 0, "bug_ids": [], "status": "in_progress", "debrief": "d"})
    rec = _run_fixture(
        _carrier([_req("FR-225", ["TC-263"])], [_case("TC-263", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/badstatus.session.yaml"}})]),
        {"evidence/sessions/badstatus.session.yaml": bad_status_session})
    eqs("Pair27.2", rec, status="error", exit_code=2, requirements_total=1)
    ok("Pair27.2", "evidence_unparseable[0] names TC-263/session, reason names the closed status enum",
       len(rec["evidence_unparseable"]) == 1 and rec["evidence_unparseable"][0]["case_id"] == "TC-263"
       and "completed" in (rec["evidence_unparseable"][0].get("reason") or "")
       and "aborted" in (rec["evidence_unparseable"][0].get("reason") or ""))

    no_debrief_session = json.dumps({"charter": "c", "timebox_minutes": 30, "overrun": False,
        "areas": [], "bugs_found": 0, "bug_ids": [], "status": "completed"})
    rec = _run_fixture(
        _carrier([_req("FR-226", ["TC-264"])], [_case("TC-264", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/nodebrief.session.yaml"}})]),
        {"evidence/sessions/nodebrief.session.yaml": no_debrief_session})
    eqs("Pair27.3", rec, status="error", exit_code=2, requirements_total=1)
    ok("Pair27.3", "evidence_unparseable[0] names TC-264/session, reason names 'debrief'",
       len(rec["evidence_unparseable"]) == 1 and rec["evidence_unparseable"][0]["case_id"] == "TC-264"
       and "debrief" in (rec["evidence_unparseable"][0].get("reason") or ""))

    # Pair 28 — bridge-check frontmatter shape guards (_read_wp_frontmatter).
    rec = _run_fixture(
        _carrier([_req("FR-227", ["TC-265"])], [_case("TC-265", "WP94", "automated",
            {"junit": {"file": "evidence/results/z1.xml", "testcase": "C::n"}})]),
        {"evidence/results/z1.xml": _junit_xml([_tc("n")]),
         "tasks/WP94.md": "---\n- a\n- b\n---\nbody\n"})
    eqs("Pair28.1", rec, exit_code=2)
    v = (rec["bridge_violations"] or [{}])[0]
    ok("Pair28.1", "non-mapping WP94.md frontmatter is a real violation naming FR-227/TC-265",
       v.get("requirement_id") == "FR-227" and v.get("case_id") == "TC-265"
       and "WP94.md" in (v.get("wp_file") or ""))

    rec = _run_fixture(
        _carrier([_req("FR-228", ["TC-266"])], [_case("TC-266", "WP93", "automated",
            {"junit": {"file": "evidence/results/z2.xml", "testcase": "C::n"}})]),
        {"evidence/results/z2.xml": _junit_xml([_tc("n")]),
         "tasks/WP93.md": "---\n" + json.dumps({"other_field": "x"}) + "\n---\nbody\n"})
    eqs("Pair28.2", rec, exit_code=2)
    v = (rec["bridge_violations"] or [{}])[0]
    ok("Pair28.2", "frontmatter with no requirement_refs key at all omits FR-228, is a violation",
       v.get("requirement_id") == "FR-228" and v.get("case_id") == "TC-266"
       and "WP93.md" in (v.get("wp_file") or ""))

    # Pair 29 — a clean session (status: completed, overrun: false) counts as
    # a passing outcome end-to-end: no prior fixture ever drove a session
    # case all the way through to `passed`/`requirements_covered` (Pair18.4's
    # `_session("completed", False)` fixture is a schema-violation test that
    # never reaches compute_coverage at all).
    rec = _run_fixture(
        _carrier([_req("FR-229", ["TC-267"])], [_case("TC-267", "WP01", "exploratory",
            {"session": {"file": "evidence/sessions/clean.session.yaml"}})]),
        {"evidence/sessions/clean.session.yaml": _session("completed", False)})
    eqs("Pair29", rec, status="measured", verdict="ready", exit_code=0, requirements_covered=1,
        passed=1, failed=0, skipped=0, overrun=0, results_parsed=1)

    # --- Pre-merge squad closure pairs 30-32 (data-model.md Section A addendum,
    # 2026-08-23): A-8 (evidence-path containment), A-9 (`overrun` real-boolean), A-10
    # (OSError is ERROR, never a crash, never exit 1). Each was reproduced against the
    # pre-fix build with a fake-green (or crashing) result before this addendum existed;
    # every pair below asserts the specific status/exit code AND a reason naming the
    # offending case id/value -- not merely a non-zero exit. ---

    # Pair 30 — A-8: evidence paths must be relative and resolve inside --mission-dir.
    # Pre-fix reproduction (both silently honoured): an absolute `junit` `file` outside
    # the mission dir -> `measured / ready | passed: 1 | exit 0`; a `../`-relative
    # escape -> the same. `Path.__truediv__` discards its left operand entirely when
    # the right side is absolute, which is the actual pre-fix escape mechanism
    # (`mission_dir / "/abs/path" == Path("/abs/path")`) -- `_resolve_contained`
    # rejects the absolute case before any join is attempted, never after.

    rec = _run_fixture(
        _carrier([_req("FR-230", ["TC-300"])], [_case("TC-300", "WP01", "automated",
            {"junit": {"file": "/etc/passwd", "testcase": "C::n"}})]), {})
    eqs("Pair30.1", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair30.1", "reason names case TC-300 and the rejected absolute value /etc/passwd",
       "TC-300" in (rec["reason"] or "") and "/etc/passwd" in (rec["reason"] or ""))

    rec = _run_fixture(
        _carrier([_req("FR-231", ["TC-301"])], [_case("TC-301", "WP01", "automated",
            {"junit": {"file": "../planted-rel.xml", "testcase": "C::n"}})]), {})
    eqs("Pair30.2", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair30.2", "reason names case TC-301 and the rejected escaping value ../planted-rel.xml",
       "TC-301" in (rec["reason"] or "") and "../planted-rel.xml" in (rec["reason"] or ""))

    rec = _run_fixture(
        _carrier([_req("FR-232", ["TC-302"])], [_case("TC-302", "WP01", "exploratory",
            {"session": {"file": "/tmp/planted-abs.session.yaml"}})]), {})
    eqs("Pair30.3", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair30.3", "reason names case TC-302 and the rejected absolute session value",
       "TC-302" in (rec["reason"] or "") and "/tmp/planted-abs.session.yaml" in (rec["reason"] or ""))

    rec = _run_fixture(
        _carrier([_req("FR-233", ["TC-303"])], [_case("TC-303", "WP01", "exploratory",
            {"session": {"file": "../../planted-rel.session.yaml"}})]), {})
    eqs("Pair30.4", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair30.4", "reason names case TC-303 and the rejected escaping session value",
       "TC-303" in (rec["reason"] or "")
       and "../../planted-rel.session.yaml" in (rec["reason"] or ""))

    # Pair 30.5 — A-8 covers all three evidence kinds (`junit`, `session`, `ctrf`), not only
    # the one reproduced. `ctrf` is "reserved, no required sub-fields in v1 ... carries
    # no `file` key" (Section A) and no code path in this module ever reads a `ctrf`
    # entry's keys -- so a `file`-shaped key placed under `ctrf`, however hostile-
    # looking, is inert: it is syntactically legal (never an "unknown key" ERROR, per
    # Section A's own ctrf row) and mechanically never read. This proves that fact
    # rather than assuming it: the carrier still measures normally and the case still
    # resolves to the fixed `ctrf_pending`/not_measured outcome, never an escape.
    rec = _run_fixture(
        _carrier([_req("FR-234", ["TC-304"])], [_case("TC-304", "WP01", "manual",
            {"ctrf": {"file": "/etc/passwd"}})]), {})
    eqs("Pair30.5", rec, status="measured", verdict="blocked",
        ctrf_pending=[{"case_id": "TC-304", "reason": CTRF_REASON}])

    # Pair 31 — A-9: `overrun` must be a real YAML boolean, never resolved by Python
    # truthiness. Pre-fix reproduction: `overrun: ""` -> `measured / ready | passed: 1
    # | overrun: 0` (a falsy non-boolean silently fell through to the passing branch --
    # fake green); `overrun: "false"` -> bucketed as overrun (the truthy case degraded
    # in the safe direction by luck, not by design, and stayed untyped). Both are now
    # rejected identically, via the same `evidence_unparseable` home Section C already
    # uses for a session's other schema failures (missing field, bad `status` enum).

    def _overrun_pair(pair: str, case_id: str, req_id: str, overrun_value: object) -> None:
        bad_session = json.dumps({"charter": "c", "timebox_minutes": 30,
            "overrun": overrun_value, "areas": [], "bugs_found": 0, "bug_ids": [],
            "status": "completed", "debrief": "d"})
        rec = _run_fixture(
            _carrier([_req(req_id, [case_id])], [_case(case_id, "WP01", "exploratory",
                {"session": {"file": f"evidence/sessions/{case_id}.session.yaml"}})]),
            {f"evidence/sessions/{case_id}.session.yaml": bad_session})
        eqs(pair, rec, status="error", exit_code=2)
        entry = (rec["evidence_unparseable"] or [{}])[0]
        ok(pair, f"evidence_unparseable[0] names {case_id}/session, reason names 'overrun' "
                 f"and the rejected value {overrun_value!r}",
           entry.get("case_id") == case_id and entry.get("evidence_kind") == "session"
           and "overrun" in (entry.get("reason") or "")
           and repr(overrun_value) in (entry.get("reason") or ""))

    _overrun_pair("Pair31.1", "TC-310", "FR-235", "")
    _overrun_pair("Pair31.2", "TC-311", "FR-236", 0)
    _overrun_pair("Pair31.3", "TC-312", "FR-237", None)
    _overrun_pair("Pair31.4", "TC-313", "FR-238", "false")

    # Pair 32 — A-10: any OSError (reading the carrier, reading evidence, creating
    # --mission-dir, or writing qa-coverage.json) is `status: error` / exit 2 with a
    # named reason -- never an unhandled traceback, and in particular never exit 1
    # (Section B reserves 1 for the measured/blocked verdict; a caller branching on
    # exit code would otherwise misread a permissions failure as a real negative QA
    # verdict). These three exercise `main()` itself (via `_run_main`), since
    # `run_validator` alone never reaches the mkdir/write_text guards.

    # 32.1 — --mission-dir names an existing regular file (FileExistsError on mkdir):
    # qa-coverage.json cannot be written at all; the reason says so rather than
    # pretending it was written.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        mission_dir_as_file = root / "mission_as_file"
        mission_dir_as_file.write_text("not a directory")
        carrier_path = root / "qa-traceability.yaml"
        carrier_path.write_text(_carrier([], []))
        code, _out, err = _run_main(
            ["--carrier", str(carrier_path), "--mission-dir", str(mission_dir_as_file)])
        ok("Pair32.1", "exit code is 2, never 1", code == 2)
        ok("Pair32.1", "stderr names the mission-dir path, never an unhandled traceback",
           str(mission_dir_as_file) in err and "Traceback" not in err)
        ok("Pair32.1", "mission-dir file untouched (qa-coverage.json never pretended written)",
           mission_dir_as_file.read_text() == "not a directory")

    # 32.2 — --mission-dir exists but is unwritable (PermissionError on write_text):
    # again qa-coverage.json cannot be written; reason names the failure, not exit 1.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        unwritable = root / "unwritable_mission"
        unwritable.mkdir()
        carrier_path = root / "qa-traceability.yaml"
        carrier_path.write_text(_carrier([], []))
        unwritable.chmod(0o500)
        try:
            code, _out, err = _run_main(
                ["--carrier", str(carrier_path), "--mission-dir", str(unwritable)])
            ok("Pair32.2", "exit code is 2, never 1", code == 2)
            ok("Pair32.2", "stderr names the write failure, never an unhandled traceback",
               "qa-coverage.json" in err and "Traceback" not in err)
            ok("Pair32.2", "qa-coverage.json was never actually written",
               not (unwritable / "qa-coverage.json").exists())
        finally:
            unwritable.chmod(0o700)

    # 32.3 — --carrier exists but is unreadable (PermissionError reading it): distinct
    # from 32.1/32.2 -- here --mission-dir itself is fine, so qa-coverage.json IS still
    # written unconditionally, carrying `status: error` and a reason naming the carrier
    # path, proving the "written unconditionally" invariant holds even when the
    # *input* (not the output) is what failed to read.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        mission_dir = root / "m"
        mission_dir.mkdir()
        carrier_path = root / "qa-traceability.yaml"
        carrier_path.write_text(_carrier([], []))
        carrier_path.chmod(0o000)
        try:
            code, _out, err = _run_main(
                ["--carrier", str(carrier_path), "--mission-dir", str(mission_dir)])
            ok("Pair32.3", "exit code is 2, never 1", code == 2)
            cov = json.loads((mission_dir / "qa-coverage.json").read_text())
            ok("Pair32.3", "qa-coverage.json IS written unconditionally, status: error",
               cov["status"] == "error")
            ok("Pair32.3", "reason names the unreadable carrier path, never an unhandled traceback",
               str(carrier_path) in (cov["reason"] or "") and "Traceback" not in err)
        finally:
            carrier_path.chmod(0o644)

    # 32.4 — an evidence file exists but is unreadable (PermissionError reading it, the
    # fourth of A-10's four named I/O sites: "reading the carrier, reading an evidence
    # file, creating --mission-dir, or writing qa-coverage.json"). Runs through
    # `_run_fixture`/`run_validator` directly (unlike 32.1-32.3): the per-case guard
    # lives in `classify_junit`/`_parse_junit_xml`, not in `main()`.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        (root / "evidence" / "results").mkdir(parents=True)
        xml_path = root / "evidence" / "results" / "unreadable.xml"
        xml_path.write_text(_junit_xml([_tc("n")]))
        xml_path.chmod(0o000)
        try:
            carrier_path = root / "qa-traceability.yaml"
            carrier_path.write_text(
                _carrier([_req("FR-239", ["TC-314"])], [_case("TC-314", "WP01", "automated",
                    {"junit": {"file": "evidence/results/unreadable.xml", "testcase": "C::n"}})]))
            rec = run_validator(carrier_path, root)
            eqs("Pair32.4", rec, status="error", exit_code=2)
            entry = (rec["evidence_unparseable"] or [{}])[0]
            ok("Pair32.4",
               "evidence_unparseable[0] names TC-314/junit, reason names the unreadable file",
               entry.get("case_id") == "TC-314" and entry.get("evidence_kind") == "junit"
               and str(xml_path) in (entry.get("reason") or ""))
        finally:
            xml_path.chmod(0o644)

    # 32.5 — the session-evidence sibling of 32.4: `classify_session`'s own OSError
    # guard is a separate code path from `classify_junit`'s (a distinct except clause
    # in a distinct function), so 32.4 alone does not exercise it.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        (root / "evidence" / "sessions").mkdir(parents=True)
        session_path = root / "evidence" / "sessions" / "unreadable.session.yaml"
        session_path.write_text(_session("completed", False))
        session_path.chmod(0o000)
        try:
            carrier_path = root / "qa-traceability.yaml"
            carrier_path.write_text(
                _carrier([_req("FR-240", ["TC-315"])], [_case("TC-315", "WP01", "exploratory",
                    {"session": {"file": "evidence/sessions/unreadable.session.yaml"}})]))
            rec = run_validator(carrier_path, root)
            eqs("Pair32.5", rec, status="error", exit_code=2)
            entry = (rec["evidence_unparseable"] or [{}])[0]
            ok("Pair32.5",
               "evidence_unparseable[0] names TC-315/session, reason names the unreadable file",
               entry.get("case_id") == "TC-315" and entry.get("evidence_kind") == "session"
               and str(session_path) in (entry.get("reason") or ""))
        finally:
            session_path.chmod(0o644)

    # --- A-10-as-a-CLASS closure pairs 33-34 (data-model.md's same-day amendment,
    # coord commit 8f5f3b8): the four-site enumeration in A-10's first draft was
    # satisfied, correctly, by pairs 30/32 above -- then an orchestrator repro
    # (`chmod 000 tasks/WP01-x.md`) reached a fifth site, the WP-frontmatter read,
    # as an unhandled traceback at exit 1. Pair 33 reproduces that exact repro
    # against the fix; Pair 34 proves the backstop the amendment also requires,
    # independent of any single site's own guard. ---

    # Pair 33 — the fifth I/O site, reproduced exactly as the orchestrator found
    # it: an unreadable tasks/WP*.md frontmatter file must be a real, named
    # bridge_violations ERROR -- never an unhandled traceback, never exit 1.
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        (root / "tasks").mkdir()
        (root / "evidence" / "results").mkdir(parents=True)
        wp_path = root / "tasks" / "WP01-x.md"
        wp_path.write_text("---\n" + json.dumps({"requirement_refs": ["FR-241"]}) + "\n---\nbody\n")
        xml_path = root / "evidence" / "results" / "aa.xml"
        xml_path.write_text(_junit_xml([_tc("n")]))
        wp_path.chmod(0o000)
        try:
            carrier_path = root / "qa-traceability.yaml"
            carrier_path.write_text(
                _carrier([_req("FR-241", ["TC-320"])], [_case("TC-320", "WP01", "automated",
                    {"junit": {"file": "evidence/results/aa.xml", "testcase": "C::n"}})]))
            rec = run_validator(carrier_path, root)
            eqs("Pair33", rec, status="error", exit_code=2)
            v = (rec["bridge_violations"] or [{}])[0]
            ok("Pair33", "bridge_violations names FR-241/TC-320/WP01-x.md, the reason "
                         "surfaces the unreadable file, never a bare exit 1",
               v.get("requirement_id") == "FR-241" and v.get("case_id") == "TC-320"
               and "WP01-x.md" in (v.get("wp_file") or ""))
            code, _out, err = _run_main(
                ["--carrier", str(carrier_path), "--mission-dir", str(root)])
            ok("Pair33", "the exact orchestrator repro via main(): exit 2, never 1, no traceback",
               code == 2 and "Traceback" not in err)
        finally:
            wp_path.chmod(0o644)

    # Pair 34 — the backstop: `Path.resolve()` inside `_resolve_contained` is
    # deliberately left without its own try/except (proven, and recorded in
    # `_IO_SWEEP_KNOWN_SAFE`, never to raise OSError for how this module calls
    # it). This pair forces it to raise anyway, via a temporary monkeypatch, to
    # prove `main()`'s backstop -- never any individual site's own guard -- is
    # what prevents the unhandled-traceback/exit-1 defect for a site nobody
    # wrote a specific catch for: "even a site you believe unreachable must not
    # be able to produce exit 1".
    orig_resolve = Path.resolve

    def _boom_resolve(self, *a, **kw):
        raise OSError(13, "Permission denied (INJECTED for Pair34 backstop proof)")

    Path.resolve = _boom_resolve
    try:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            carrier_path = root / "qa-traceability.yaml"
            carrier_path.write_text(
                _carrier([_req("FR-242", ["TC-321"])], [_case("TC-321", "WP01", "automated",
                    {"junit": {"file": "evidence/results/bb.xml", "testcase": "C::n"}})]))
            code, _out, err = _run_main(
                ["--carrier", str(carrier_path), "--mission-dir", str(root)])
    finally:
        Path.resolve = orig_resolve
    ok("Pair34", "backstop yields exit 2, never 1, for an OSError with no per-site guard",
       code == 2)
    ok("Pair34", "backstop reason surfaces the injected error, never a bare traceback",
       "INJECTED" in err or "unexpected I/O error" in err)
    ok("Pair34", "stderr carries no unhandled Python traceback", "Traceback" not in err)

    # --- A-11 closure pairs 35-38 (data-model.md Section A addendum 2, found by the
    # fresh pre-merge sweep at severity 5, 2026-08-23): `check_bridge` passed the
    # carrier author's raw `wp` straight into `_read_wp_frontmatter`'s unanchored
    # glob; `validate()` checked only that the `wp` key was *present*. Reproduced by
    # the orchestrator, identical `tasks/` fixtures, only `wp` changing:
    #   wp: "WP05" (honest)  -> status error, 1 bridge violation, exit 2
    #   wp: ""                -> status measured, verdict ready, exit 0 (FAKE GREEN)
    #   wp: "AP"              -> status measured, verdict ready, exit 0 (FAKE GREEN)
    # and with `tasks/` completely empty, wp: "../../outside/leak" read a fabricated
    # file outside --mission-dir and returned measured/ready, exit 0. Pairs 35.1-35.4
    # below reproduce the malformed-value class (empty/wrong-shape/traversal/non-
    # string) as a schema ERROR before any filesystem access; Pair 36 reproduces the
    # exact "AP" repro with the decoy file physically present on disk, proving the
    # rejection fires on the malformed shape, never on what the decoy happens to
    # contain; Pair 37 proves the anchored glob cannot match a file it does not name
    # (a well-formed id colliding by bare prefix with an unrelated, longer id); Pair
    # 38 proves the deliberate boundary A-11 must NOT break: a well-formed `wp`
    # naming a WP file that does not exist yet stays `bridge_not_measured` and does
    # not block the verdict (Section C).

    def _malformed_wp_pair(pair: str, case_id: str, req_id: str, wp_value: object,
                            files: dict | None = None) -> None:
        rec = _run_fixture(
            _carrier([_req(req_id, [case_id])], [_case(case_id, wp_value, "automated",
                {"junit": {"file": "evidence/results/x.xml", "testcase": "C::n"}})]),
            {**(files or {}), "evidence/results/x.xml": _junit_xml([_tc("n")])})
        eqs(pair, rec, status="error", exit_code=2, requirements_total=None)
        reason = rec["reason"] or ""
        ok(pair, f"reason names case {case_id} and the rejected wp value {wp_value!r}",
           case_id in reason and repr(wp_value) in reason)
        ok(pair, "never the fake-green pre-fix verdict (measured/ready)",
           not (rec["status"] == "measured" and rec["verdict"] == "ready"))

    # Pair 35.1 — empty string.
    _malformed_wp_pair("Pair35.1", "TC-330", "FR-250", "")
    # Pair 35.2 — wrong shape (matches neither length nor the WP prefix rule).
    _malformed_wp_pair("Pair35.2", "TC-331", "FR-251", "AP")
    # Pair 35.3 — traversal string, `tasks/` completely empty (the orchestrator's
    # own fourth repro: no WP file for the requirement anywhere in the mission).
    _malformed_wp_pair("Pair35.3", "TC-332", "FR-252", "../../outside/leak")
    # Pair 35.4 — non-string (never a silent str() coercion / truthiness check).
    _malformed_wp_pair("Pair35.4", "TC-333", "FR-253", 5)

    # Pair 36 — the exact orchestrator repro: `tasks/` holds WP05-realwork.md
    # (covers FR-999 only) and AP-decoy.md (covers FR-100), wp: "AP". Pre-fix this
    # silently matched AP-decoy.md's frontmatter and returned measured/ready. The
    # fix must reject "AP" on shape alone, before the glob -- and the rejection
    # reason must name the offending value, never leak AP-decoy.md's own content.
    rec = _run_fixture(
        _carrier([_req("FR-254", ["TC-334"])], [_case("TC-334", "AP", "automated",
            {"junit": {"file": "evidence/results/y.xml", "testcase": "C::n"}})]),
        {"evidence/results/y.xml": _junit_xml([_tc("n")]),
         "tasks/WP05-realwork.md": "---\n" + json.dumps({"requirement_refs": ["FR-999"]}) + "\n---\nbody\n",
         "tasks/AP-decoy.md": "---\n" + json.dumps({"requirement_refs": ["FR-100"]}) + "\n---\nbody\n"})
    eqs("Pair36", rec, status="error", exit_code=2, requirements_total=None)
    ok("Pair36", "reason names TC-334 and the rejected value 'AP', never the decoy file",
       "TC-334" in (rec["reason"] or "") and repr("AP") in (rec["reason"] or "")
       and "AP-decoy" not in (rec["reason"] or ""))
    ok("Pair36", "never the fake-green pre-fix verdict (measured/ready)",
       not (rec["status"] == "measured" and rec["verdict"] == "ready"))

    # Pair 37 — anchoring: `tasks/` holds only WP050-other.md (a different,
    # longer id whose frontmatter does NOT list FR-255); the case's wp is the
    # well-formed, SHORTER id "WP05", which has no WP05.md/WP05-*.md file of its
    # own. An unanchored `f"{wp_id}*.md"` glob would wrongly match WP050-other.md
    # (a bare-prefix collision) and report a bridge violation (FR-255 missing
    # from its frontmatter); the anchored glob must instead treat WP05 as simply
    # absent -- `bridge_not_measured`, never a violation -- since WP05.md is not
    # WP050-other.md.
    rec = _run_fixture(
        _carrier([_req("FR-255", ["TC-335"])], [_case("TC-335", "WP05", "automated",
            {"junit": {"file": "evidence/results/z.xml", "testcase": "C::n"}})]),
        {"evidence/results/z.xml": _junit_xml([_tc("n")]),
         "tasks/WP050-other.md": "---\n" + json.dumps({"requirement_refs": ["FR-999"]}) + "\n---\nbody\n"})
    eqs("Pair37", rec, bridge_violations=[], status="measured", verdict="ready")
    nm = (rec["bridge_not_measured"] or [{}])[0]
    ok("Pair37", "not_measured names FR-255/TC-335/tasks/WP05.md, never matching WP050-other.md",
       nm.get("requirement_id") == "FR-255" and nm.get("case_id") == "TC-335"
       and nm.get("wp_file") == "tasks/WP05.md")

    # Pair 38 — the boundary A-11 must NOT break (Section C, explicitly preserved):
    # a well-formed `wp` naming a WP file that does not exist yet is
    # `bridge_not_measured` and correctly does not block the verdict.
    rec = _run_fixture(
        _carrier([_req("FR-256", ["TC-336"])], [_case("TC-336", "WP07", "automated",
            {"junit": {"file": "evidence/results/w.xml", "testcase": "C::n"}})]),
        {"evidence/results/w.xml": _junit_xml([_tc("n")])})
    eqs("Pair38", rec, bridge_violations=[], status="measured", verdict="ready")
    nm = (rec["bridge_not_measured"] or [{}])[0]
    ok("Pair38", "not_measured names FR-256/TC-336/tasks/WP07.md, reason names the missing file",
       nm.get("requirement_id") == "FR-256" and nm.get("case_id") == "TC-336"
       and nm.get("wp_file") == "tasks/WP07.md" and "WP07.md" in (nm.get("reason") or ""))
    ok("Pair38", "well-formed-but-absent WP never blocks the verdict",
       rec["status"] == "measured" and rec["verdict"] == "ready" and exit_code_for(rec) == 0)

    # The A-10 mechanical class-check itself (data-model.md's amendment: "the
    # check is a sweep asserting no open/read_text/mkdir/write_text/glob/
    # ET.parse call sits outside an OSError handler"). This is what makes the
    # class stay closed when someone adds a sixth site next year -- it fails
    # self-test on its own, independent of every fixture pair above.
    for violation in _sweep_unguarded_io():
        failures.append(f"IO-sweep - {violation}")

    # A-11's own mechanical class-check (data-model.md Section A addendum 2): "a
    # sweep asserting that no filesystem access is constructed from carrier-
    # supplied input without passing through the shared validate-and-contain
    # helper" -- catches a `wp`-shaped defect recurring, or a new carrier-tainted
    # field reaching the filesystem unguarded, independent of every fixture pair
    # above (see `_sweep_unvalidated_wp_access`'s own docstring for exactly what
    # this proves and its honestly-documented lexical-proxy limits).
    for violation in _sweep_unvalidated_wp_access():
        failures.append(f"wp-access-sweep - {violation}")

    if failures:
        sys.stderr.write(f"qa_carrier_lint --self-test: FAILED ({len(failures)} assertion(s))\n")
        for f in failures:
            sys.stderr.write(f"  - {f}\n")
        return 1
    sys.stdout.write(
        f"qa_carrier_lint --self-test: all {_derive_self_test_pair_count()} pairs passed, "
        f"IO-sweep clean, wp-access-sweep clean\n")
    return 0

if __name__ == "__main__":  # pragma: no cover - module-level CLI guard
    raise SystemExit(main())
