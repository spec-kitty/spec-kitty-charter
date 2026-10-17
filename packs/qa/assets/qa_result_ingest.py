"""qa_result_ingest — the pack-shipped `qa-traceability/v1` carrier producer.

This is the *producer* — it reads `spec.md`'s Requirement Register, every `tasks/WP*.md`'s
frontmatter (including the `automation_ref` field), and JUnit-XML result file(s), and writes
(or refreshes) `qa-traceability.yaml` at the mission-dir root, in the exact `qa-traceability/v1`
schema `assets/qa_carrier_lint.py` already validates — so the carrier's producer and its
validator agree on one schema by construction, never two independently-maintained ones.

Three-exit-code contract (Decision 1, `spec.md`): exit ``0`` (``ingested``) — the carrier was
produced or refreshed from real, current-run evidence, no guard fired. Exit ``1`` (``blocked``)
— a specific, named guard fired on well-formed, interpretable input (e.g. zero parsed testcases,
stale results, an unresolvable `automation_ref`) and the carrier was deliberately not written or
refreshed. Exit ``2`` (``error``) — input this tool cannot use at all, either (a) syntactically
unparseable or entirely absent, or (b) well-formed but semantically un-interpretable (e.g. the
`status=`-attribute JUnit dialect, or two WPs colliding on one `automation_ref`). See
`_exit_code_taxonomy` below for the shared mechanism every guard in this file must use.

FR-003 — disclosed non-support (every excluded format named plainly, never a vague "other
formats" catch-all): this ingester does **not** support TAP, TRX (MSTest/VSTest's own native XML
schema — a different schema despite also being XML), Cucumber JSON, Robot Framework's native
XML, or CTRF. Within JUnit-XML itself, only the outcome-as-child-element dialect
(`<failure>`/`<error>`/`<skipped/>`) is supported — the same shape `classify_junit`
(`assets/qa_carrier_lint.py:291-315`) already classifies. The outcome-as-`status`-attribute
dialect some emitters use (e.g. `<testcase status="failed">` with no child element) is explicitly
named as unsupported: it is detected and refused (`enumerate_testcases`, FR-016, exit 2), never
silently mis-read as passing.

FR-015 — known-limitation disclosure (mechanically checkable, agent-invoked, never machine-
enforced): no runtime hook compels running this ingester or blocks on a `blocked`/`error` exit
today. The same `#2599` (`GateBinding.handler_kind: "asset"` inert) gap
`assets/qa_carrier_lint.py` already discloses for itself applies here too, and
`expected-artifacts.yaml`'s `blocking:` key (this mission's own WP08 entry) does not mechanically
enforce this either — this mission's own deliverable is mechanically checkable (exit code,
`qa-traceability.yaml`) but agent-invoked, never machine-enforced, as of this mission's own scope.

NFR-008 — bytecode hygiene (operational contract on the caller, not something this module can
enforce on itself once already running): every invocation of this asset — `--self-test` included
— MUST run with `PYTHONDONTWRITEBYTECODE=1` set (equivalently, `python3 -B`), so the interpreter
never writes `assets/__pycache__/*.pyc`, which would otherwise break gate 6 (flat-directory scan)
and gate 12 (machine-local-path leak scan — a compiled `.pyc` embeds this machine's own absolute
path). `--self-test`'s own NFR-007 subprocess proof sets this explicitly in every subprocess
environment it spawns, never relying on inheriting it from the parent shell.

Portability (NFR-001): stdlib + ``ruamel.yaml`` only, nothing from the
``spec-kitty``/``specify_cli``/``doctrine``/``charter`` source tree, mirroring
``assets/qa_carrier_lint.py``'s own contract (itself mirroring ``docs_structural_lint.py:33-38``).
``ruamel.yaml`` is not importable from a bare system ``python3`` — it lives only inside
spec-kitty's own installed interpreter's site-packages. Every invocation this asset documents
assumes spec-kitty's own interpreter, never a bare ``python3`` off ``PATH``.

Invocation::

    python qa_result_ingest.py --results PATH [--results PATH ...] --strategy path/to/spec.md \\
        --tasks-dir tasks/ --since TIMESTAMP --mission-dir PATH [--json]
    python qa_result_ingest.py --self-test

WP01 (Seam A) laid the scaffold, the shared exit-code taxonomy, and three readers (Requirement
Register, WP frontmatter, JUnit enumeration). WP02 (Seam B) added the five correlation and
exclusion guards that sit between those readers and the writer: the zero-parsed-testcases guard
(FR-005), the git-aware staleness guard (FR-006), the `automation_ref` correlation core and its
unresolvable-ref (FR-017) and duplicate-ref (FR-018) guards, and the no-silent-exclusion logic in
both directions (FR-007). WP03 (Seam C, this WP) finishes the file: the `qa-traceability.yaml`
writer (FR-010), `--self-test` (FR-008, proving every WP01/WP02 guard both fires and does not
fire), and this disclosure text (FR-003/FR-015).

A note on phrasing (Decision 2): this docstring and this module's other prose use "JUnit-XML"
(title case) throughout, never the bare lowercase literal — matching
`pack-structured-carriers-01M0ME39`'s own established neutral-phrasing precedent for the
validator side of this same carrier. The one exception is the schema's own literal field key
`evidence.junit`, quoted verbatim (backticked, lowercase) wherever the carrier's real shape is
being described, since neutralising a direct field-name quotation would misdescribe the schema.

A note on WP-frontmatter shape (carried forward from WP02's review): this module accepts two
documented frontmatter shapes for `tasks/WP*.md` (see `_iter_wp_case_frontmatter` below) — flat
(top-level `id` + `requirement_refs`) and nested (a top-level `cases:` list) — plus
`missions/qa/templates/test-case-template.md`'s shipped shape: fenced ```yaml case blocks in the
WP body, below the WPMetadata frontmatter. The body-fence shape is the only one a real mission
WP can carry (WPMetadata rejects case keys in the frontmatter); the two frontmatter shapes are
still read for files written before the body-fence reader existed. The nested shape's only known
citation is a carrier-output worked example, not any WP-frontmatter file, so it is supported
defensively, not attested.
"""

from __future__ import annotations

import argparse
import ast
import contextlib
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator, NoReturn

try:
    from ruamel.yaml import YAML
    from ruamel.yaml.error import YAMLError
except ImportError:
    print(
        f"error: ruamel.yaml not importable under {sys.executable}; "
        "run this asset with spec-kitty's own installed interpreter, "
        "never a bare system python3",
        file=sys.stderr,
    )
    sys.exit(2)


# --- Exit-code taxonomy (T002) -----------------------------------------------------------------
#
# The ONE shared exit-code vocabulary (Decision 1). Every guard in this file — in this WP and in
# WP02/WP03 — raises one of the two exceptions below and lets `main()`'s single `try`/`except`
# map it to the right `sys.exit()` call; no guard anywhere in this module calls `sys.exit()`
# directly. This is a deliberate choice of shape (exceptions carrying a reason string) over a
# pair of `fail_*()` helper functions that exit inline — WP02/WP03's authors should reuse this
# shape rather than inventing a second one.
#
# Reason-string convention for exit-2 conditions (Decision 1's (a)/(b) split): every `IngestError`
# raised in this file states, as the first word of its message, which clause it is grounded in —
# "absent:" for clause (a) (syntactically unparseable or entirely absent input), or
# "uninterpretable:" for clause (b) (well-formed input this tool cannot extract any supported
# semantics from at all). This is not a separate enum field — Decision 1 explicitly permits a
# reason-string convention as long as it is discoverable by reading the code, not just prose.


class BlockedError(Exception):
    """Exit 1 (`blocked`) — a named guard fired on well-formed, interpretable input.

    Not raised anywhere in this WP (Seam A) — every exit-1 guard (FR-005 zero-testcases, FR-006
    staleness, FR-017 unresolvable `automation_ref`) belongs to WP02 (Seam B). Declared here so
    the shared taxonomy exists in full before any guard needs it.
    """


class IngestError(Exception):
    """Exit 2 (`error`) — input this tool cannot use at all (Decision 1, clauses (a)/(b)).

    Raised by this WP's own guards: T005's FR-016 dialect guard (clause (b)) and T006's FR-019
    missing-`spec.md` guard and FR-001 missing-`--results` guard (both clause (a)).
    """


def _exit_code_taxonomy(exc: BlockedError | IngestError) -> NoReturn:
    """The single mapping site from a taxonomy exception to a real process exit. `main()` is the
    only caller — every guard raises, it never calls `sys.exit()` itself, so this function is the
    one place the exception-to-exit-code mapping is defined."""
    if isinstance(exc, BlockedError):
        print(f"qa_result_ingest: blocked: {exc}", file=sys.stderr)
        sys.exit(1)
    print(f"qa_result_ingest: error: {exc}", file=sys.stderr)
    sys.exit(2)


# --- CLI argument surface (T001) -----------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="qa_result_ingest",
        description="Read spec.md's Requirement Register, tasks/WP*.md frontmatter, and "
                     "JUnit-XML result file(s), and write (or refresh) qa-traceability.yaml. "
                     "Exit 0 (ingested), 1 (blocked), or 2 (error).",
    )
    parser.add_argument(
        "--results", action="append", default=[], type=Path,
        help="Path to a JUnit-XML result file. Repeatable. Required only when at least one "
             "tasks/WP*.md case declares a non-empty automation_ref anywhere in the mission.",
    )
    parser.add_argument(
        # Not argparse `required=True`: `--self-test` (T014) legitimately omits every one of
        # these four. main() enforces "required unless --self-test" manually, mirroring
        # qa_carrier_lint.py's own build_parser()/main() split for the identical reason.
        "--strategy", type=Path,
        help="Path to spec.md (the Requirement Register source). Required unless --self-test.",
    )
    parser.add_argument(
        "--tasks-dir", type=Path,
        help="Path to the tasks/ directory containing WP*.md files. Required unless --self-test.",
    )
    parser.add_argument(
        "--since",
        help="Timestamp boundary (ISO 8601); result files resolving older than this are stale. "
             "Required unless --self-test.",
    )
    parser.add_argument(
        "--mission-dir", type=Path,
        help="Mission-dir root qa-traceability.yaml is written under and evidence resolves "
             "under. Required unless --self-test.",
    )
    parser.add_argument(
        "--json", action="store_true",
        help="Emit the ingestion record as JSON to stdout.",
    )
    parser.add_argument(
        "--self-test", action="store_true",
        help=f"Run the {_derive_self_test_pair_count()} built-in fixture/assertion pairs "
             "(FR-008); exit 0 only if all pass. Ignores every other argument.",
    )
    return parser


def _yaml() -> YAML:
    return YAML(typ="safe")


# --- Requirement Register parser (T003, FR-009) --------------------------------------------------

# This pack's own regex intersection: uppercase, exactly three digits, FR-/NFR-/C- prefixed.
REQUIREMENT_ID_RE = r"(?:FR|NFR|C)-\d{3}"
_TABLE_ROW_RE = re.compile(
    rf"^\s*\|\s*({REQUIREMENT_ID_RE})\s*\|"
)
_BULLET_FORM_RE = re.compile(
    rf"^\s*-\s*\*\*({REQUIREMENT_ID_RE})\*\*\s*:"
)


def parse_requirement_register(spec_md_path: Path) -> list[str]:
    """Read `spec_md_path` and extract every `FR-`/`NFR-`/`C-` id declared in its Requirement
    Register, accepting both `test-strategy-template.md`-compliant shapes: the table-row form
    (`| FR-001 | ... |`) and the bullet-form (`- **FR-001**: ...`).

    Does not raise or exit on zero matches — a `spec.md` that exists and parses to zero rows is a
    legitimate (if unusual) `requirements: []` result (FR-009's own row in `spec.md`); only an
    absent `spec.md` is an error, and that is T006's guard, checked by the caller before this
    function is ever invoked, never by this function itself.

    Returns an ordered list (never a `set()`) — FR-010's determinism requirement (finished in
    WP03) depends on every enumerable list in this file being ordered from the start. Duplicate
    ids (the same id declared twice) are collapsed, first occurrence wins, order preserved.
    """
    text = spec_md_path.read_text()
    seen: dict[str, None] = {}
    for line in text.splitlines():
        match = _TABLE_ROW_RE.match(line) or _BULLET_FORM_RE.match(line)
        if match:
            seen.setdefault(match.group(1), None)
    return list(seen)


# --- WP frontmatter reader (T004, automation_ref extraction) -------------------------------------


def read_wp_automation_refs(tasks_dir: Path) -> list[tuple[str, str, str]]:
    """Glob `tasks/WP*.md` under `tasks_dir`, parse each file's YAML frontmatter, and extract
    every case entry carrying a non-empty `automation_ref` key.

    Self-determining by presence only: a case's `automation_ref` is "declared" if the key is
    present and non-empty on that case — never conditioned on any other field, and in particular
    never on a `type:` field (Decision 3 corrects an earlier draft that wrongly conditioned this
    on `type: automated`, a value the template's `type:` vocabulary never actually uses).

    Returns `(wp_id, case_id, automation_ref)` tuples as an ordered list, preserving
    file-then-case iteration order (sorted by filename, then by each case's position in its own
    frontmatter list). This reader's output feeds WP02's correlation logic and its
    duplicate-detection guard (FR-018) — it only reads and returns the raw declarations; it does
    not resolve or deduplicate anything itself.

    Frontmatter shape read here: **both** of the two legitimate shapes this pack's own templates
    README ties to `tasks/WP*.md`, resolved the same way FR-009's Requirement Register parser
    resolves its own two documented shapes (table-row and bullet-form, `parse_requirement_register`
    above) — accept both rather than picking a winner, because neither `spec.md` nor `plan.md`
    names a wrapper key anywhere; the choice was never specified, so both readings are equally
    legitimate and a WP author following either one must be read correctly:

    - **Nested** — a top-level `id:` (the WP id, e.g. `WP01`) and a top-level `cases:` list, each
      entry a mapping that may carry its own `id` (the case id, e.g. `TC-001`) and
      `automation_ref`. Several cases share one WP file this way (the sibling mission's carrier
      worked example, `pack-structured-carriers-01M0ME39/data-model.md`, shows two cases sharing
      one `wp: WP02`).
    - **Flat** — `missions/qa/templates/test-case-template.md`'s actual, shipped shape (WP04):
      one case's fields (`id`, `requirement_refs`, `priority`, `type`, `preconditions`, and
      optionally `automation_ref`) sit directly at the WP file's own top-level frontmatter, no
      `cases:` wrapper, one case per file. Here the file's own top-level `id` is the *case* id
      (e.g. `TC-001`), not a WP id — so in this shape `wp_id` is taken from the filename instead,
      never from `fm["id"]`, to avoid misreporting a case id as a WP id. Specifically, `wp_id` is
      the `WP\\d{2}` PREFIX of `wp_path.stem` (`_wp_id_from_stem`), never the bare full stem: a
      real WP file is named `WP\\d\\d-<slug>.md` (e.g. `WP01-login-flow.md`), and the validator's
      A-11 guard (`assets/qa_carrier_lint.py:82`) accepts only the bare `WP\\d\\d` token — PMI-01
      (pre-merge adversarial squad finding, severity 5).

    A frontmatter block is treated as flat-single-case only when it carries both `id` and
    `requirement_refs` (the two fields that, together, are unique to a test-case frontmatter block
    and never appear together on a bare WP-identity block) and has no `cases:` key. Any block that
    is neither shape — no `cases:` list and missing `id` or `requirement_refs` — yields no tuples
    for that file, not an error. A file with no frontmatter, no parseable case data in either
    shape, or an unparseable frontmatter block likewise yields no tuples for that file — this
    reader does not raise on a malformed WP file; that is a validation concern outside this seam's
    scope. Never silently skip a case that declares a non-empty `automation_ref` in either shape —
    that is the exact silent-ingestion defect this dual-shape read exists to prevent.

    The file/shape traversal itself (the glob, the frontmatter split, the YAML parse, and the
    nested-vs-flat shape detection) lives in `_iter_wp_case_frontmatter` below — this function is
    now a thin filter over that shared generator (WP03 factoring: WP01's reader and WP02's T011
    reader used to carry a byte-for-byte identical copy of this traversal; a future fix landing in
    one copy but not the other would have made `(wp_id, case_id)` keys diverge silently between
    this reader and `read_wp_case_requirement_refs`. One traversal, two thin callers, from here on).
    """
    results: list[tuple[str, str, str]] = []
    for wp_id, case in _iter_wp_case_frontmatter(tasks_dir):
        automation_ref = case.get("automation_ref")
        if not automation_ref:
            continue
        case_id = case.get("id", "")
        results.append((str(wp_id), str(case_id), str(automation_ref)))
    return results


# --- JUnit-XML testcase enumeration (T005, FR-002) + unsupported-dialect guard (FR-016) ----------


def enumerate_testcases(xml_path: Path) -> tuple[list[ET.Element], int]:
    """Enumerate every `<testcase>` in `xml_path` using the identical mechanism
    `classify_junit` already uses (`assets/qa_carrier_lint.py:307`):
    `tree.getroot().iter("testcase")`. `.iter()` walks the whole tree regardless of a bare
    `<testsuite>` vs. a wrapping `<testsuites>` root, so both are tolerated automatically — this
    function does not special-case root shape.

    Returns `(testcases, asserted_count)` — `asserted_count` is
    `len(list(tree.getroot().iter("testcase")))`, computed independently and returned alongside
    the list (never silently computed and discarded), per FR-002's own count-assertion
    requirement: `assert len(testcases) == asserted_count` always holds by construction here,
    but keeping both makes the count visible to a caller (WP03's self-test) as a fact this
    function proves, not a coincidence of implementation.

    Applies the FR-016 unsupported-dialect guard in the same enumeration pass: for each
    enumerated `<testcase>`, checks for a `status="..."` attribute carrying an outcome value
    (e.g. `status="failed"`, `status="skipped"`) with no corresponding `<failure>`/`<error>`/
    `<skipped/>` child element. If any such testcase is found, raises `IngestError` (grounded in
    Decision 1(b) — this dialect is well-formed XML this tool's supported outcome shape cannot
    extract any semantics from at all), naming the file and the first offending
    `classname::name` pair. The whole file is refused — this function does not return a partial
    result for a file that trips this guard; no further classification is ever handed the file.

    Raises `IngestError` (clause (a)) if `xml_path` does not parse as XML at all — a malformed or
    absent result file is unusable input, the same absence/unparseable condition every other
    clause-(a) guard in this file uses.
    """
    try:
        tree = ET.parse(xml_path)
    except (ET.ParseError, OSError) as exc:
        raise IngestError(f"absent: {xml_path} is not parseable XML: {exc}") from exc

    testcases = list(tree.getroot().iter("testcase"))
    asserted_count = len(list(tree.getroot().iter("testcase")))

    for tc in testcases:
        status = tc.get("status")
        if status is None:
            continue
        has_outcome_child = (
            tc.find("failure") is not None
            or tc.find("error") is not None
            or tc.find("skipped") is not None
        )
        if not has_outcome_child:
            classname = tc.get("classname", "")
            name = tc.get("name", "")
            raise IngestError(
                f"uninterpretable: {xml_path} uses the unsupported status=-attribute JUnit-XML "
                f"dialect (status={status!r} with no <failure>/<error>/<skipped/> child), first "
                f"offending testcase {classname}::{name}; refusing the whole file"
            )

    return testcases, asserted_count


def testcase_key(tc: ET.Element) -> str:
    """The `classname::name` correlation key WP02's guards resolve `automation_ref` against."""
    return f"{tc.get('classname', '')}::{tc.get('name', '')}"


# --- Zero-parsed-testcases guard (T007, FR-005) ---------------------------------------------------


def check_zero_testcases(xml_path: Path, testcases: list[ET.Element]) -> None:
    """FR-005: treat a present, well-formed JUnit-XML file containing zero `<testcase>` elements
    as a named, exit-1 `BlockedError` condition (Decision 1) — distinguished from a file
    containing N testcases, zero of them failed, which is a legitimate `ingested` result. This
    guard fires only on zero *testcases*, never on zero *failures*: a fixture with N passing
    testcases must never trip it.

    Distinct from FR-016 (WP01's dialect guard, exit 2, already applied inside
    `enumerate_testcases` before this function is ever reached): FR-016 fires on a specific
    unsupported outcome shape; FR-005 fires on an empty result set regardless of dialect. Callers
    must call `enumerate_testcases` first and pass its result here — this function does not parse
    XML itself.
    """
    if not testcases:
        raise BlockedError(f"zero testcases found in {xml_path}")


# --- Staleness guard with git-aware fallback (T008, FR-006) ---------------------------------------


def _tier1_testsuite_timestamp(root: ET.Element) -> str | None:
    """Tier 1's raw source: the `timestamp="..."` attribute on the root `<testsuite>` element
    itself, or the first `<testsuite>` descendant carrying one when the root is a `<testsuites>`
    wrapper. `.iter()` includes the root itself when it matches, so this tolerates both root
    shapes exactly the way `enumerate_testcases` (T005) already does — no special-casing of
    root tag needed. Returns `None` (not raised) when no `<testsuite>` in the tree carries a
    `timestamp` attribute at all — tier 1 is simply absent, not malformed."""
    for suite in root.iter("testsuite"):
        ts = suite.get("timestamp")
        if ts:
            return ts
    return None


def _parse_iso8601(value: str) -> datetime:
    """Parse an ISO-8601 timestamp — the shape both `<testsuite timestamp="...">` and
    `git log --format=%cI` produce. A naive (no UTC offset) value is assumed UTC: JUnit-XML
    `timestamp=` attributes commonly omit an offset, and every resolved datetime this module
    compares must be tz-aware, never a naive/aware `TypeError` waiting to happen at comparison
    time. Raises `ValueError` on a genuinely unparseable string — callers decide how to treat
    that (see `resolve_result_timestamp`'s tier-1 handling and `parse_since_boundary` below)."""
    dt = datetime.fromisoformat(value)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def resolve_result_timestamp(path: Path) -> tuple[datetime, str]:
    """FR-006's three-tier staleness resolution, tried in order:

    **Tier 1** — the `<testsuite timestamp="...">` attribute (root `<testsuite>`, or the first
    `<testsuite>` descendant of a `<testsuites>` wrapper), if present and parseable as ISO-8601.

    **Tier 2** — if tier 1 is absent, and `path` is tracked in this checkout's git history, the
    file's own last-commit time: `git log -1 --format=%cI -- <path>`, run with `cwd=path.parent`
    so git resolves the repository root itself by walking upward from the file's own directory,
    exactly as it would for any other git invocation inside a working tree. Chosen over a bare
    mtime: `git checkout`/`worktree add` reset filesystem mtime to operation time (ordinary git
    mechanics, not an attack), so a stale re-committed file's mtime alone would read as fresh the
    moment a tree is freshly checked out, defeating an mtime-only guard silently.

    **Tier 3** — if tier 2 is unavailable (git binary absent, file untracked, or the command
    returns a non-zero exit or empty stdout), the file's own filesystem mtime, as a last resort.

    **Disclosed residual gap (FR-006)**: tier 3 is still defeatable by the same staleness
    mechanism this guard exists to catch — a stale file that is untracked, or sits in a
    git-unavailable environment, has an mtime a fresh checkout or a deliberate `touch` resets to
    "now." Tiers 1 and 2 close the gap for the expected case (a committed evidence file, per
    `implement`'s evidence-commit-discipline section); tier 3 is stated as the weaker, last-resort
    fallback it is, never silently relied on as equivalent to the other two.

    Returns `(resolved_timestamp, tier_name)` — `tier_name` names which tier actually produced
    the value, so a firing guard can disclose it (never silently "some timestamp, from somewhere").

    **Malformed-but-present disclosure (carried forward from WP02's review, finding 3)**: FR-006
    defines no distinct guard for a tier-1/tier-2 timestamp that is *malformed* rather than
    *absent* (e.g. `timestamp="not-a-date"`) — falling through to the next tier, rather than
    raising, remains the right behaviour, since a syntax defect in one attribute of an otherwise
    well-formed, already-validated file is not the staleness condition this guard exists to catch.
    But silently falling through with no runtime trace at all means the staleness guard would
    evaluate against a provenance signal (a weaker tier) the author never declared, unannounced.
    This function now prints a named warning to stderr whenever it falls through past a malformed
    (not merely absent) tier-1 or tier-2 value, naming the file, the malformed raw value, and the
    tier actually used instead — never only a code comment.
    """
    malformed: list[tuple[str, str]] = []
    resolved: datetime | None = None
    tier: str | None = None

    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        root = None
    if root is not None:
        ts_str = _tier1_testsuite_timestamp(root)
        if ts_str:
            try:
                resolved, tier = _parse_iso8601(ts_str), "tier 1 (<testsuite timestamp> attribute)"
            except ValueError:
                malformed.append(("tier 1 (<testsuite timestamp> attribute)", ts_str))

    if resolved is None:
        try:
            proc = subprocess.run(
                ["git", "log", "-1", "--format=%cI", "--", str(path)],
                capture_output=True, text=True, cwd=path.parent,
            )
        except OSError:
            # git binary itself unavailable on PATH.
            proc = None
        if proc is not None and proc.returncode == 0 and proc.stdout.strip():
            raw = proc.stdout.strip()
            try:
                resolved, tier = _parse_iso8601(raw), "tier 2 (git commit time)"
            except ValueError:
                # `%cI` should always be well-formed ISO-8601; treated defensively the same as
                # tier 1's malformed case above, falling through to tier 3 — still disclosed below.
                malformed.append(("tier 2 (git commit time)", raw))

    if resolved is None:
        resolved = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
        tier = "tier 3 (filesystem mtime)"

    if malformed:
        detail = "; ".join(f"{t} carried malformed timestamp {raw!r}" for t, raw in malformed)
        print(
            f"qa_result_ingest: warning: {path}: {detail}; fell through to {tier} instead "
            f"(resolved {resolved.isoformat()})",
            file=sys.stderr,
        )
    return resolved, tier


def parse_since_boundary(value: str) -> datetime:
    """Parse the caller-supplied `--since` boundary once, at the top of `main()`, per FR-006.
    Raises `IngestError` (clause (a) — this is not well-formed input the tool can act on) rather
    than letting a malformed value crash with an unhandled traceback, consistent with this file's
    and `qa_carrier_lint.py`'s shared discipline of never letting an I/O or parse defect surface
    as anything other than a named, taxonomy-routed condition."""
    try:
        return _parse_iso8601(value)
    except ValueError as exc:
        raise IngestError(
            f"absent: --since value {value!r} is not a valid ISO-8601 timestamp: {exc}"
        ) from exc


def check_staleness(path: Path, since: datetime) -> None:
    """FR-006: resolve `path`'s timestamp via the three-tier fallback and raise `BlockedError`
    (exit 1) if it predates `since`, naming the file, the `--since` boundary, and which of the
    three tiers produced the timestamp that failed — never silently ingested as if it were
    current-run evidence."""
    resolved, tier = resolve_result_timestamp(path)
    if resolved < since:
        raise BlockedError(
            f"{path} resolved to timestamp {resolved.isoformat()} via {tier}, "
            f"older than --since boundary {since.isoformat()}"
        )


# --- automation_ref correlation core (T009) --------------------------------------------------------


def build_testcase_index(testcases: list[ET.Element]) -> dict[str, ET.Element]:
    """`classname::name` -> testcase element, first occurrence wins, built as an ordered `dict`
    (insertion order, never a `set()`). Callers build this once across every `--results` file
    supplied, combined, so a declared `automation_ref` can resolve against any of them — this is
    `correlate_automation_refs`'s `enumerated` argument."""
    index: dict[str, ET.Element] = {}
    for tc in testcases:
        index.setdefault(testcase_key(tc), tc)
    return index


@dataclass(frozen=True)
class CorrelationResult:
    """T009's output: every declared `(wp_id, case_id, automation_ref)` tuple (from WP01's T004
    reader) resolved against the enumerated `classname::name` testcase keys (from WP01's T005
    enumeration), recording all three outcome categories T010's guards and WP03's writer need.

    Pure data — this dataclass is never raised and this module never raises while building it;
    `correlate_automation_refs` below does not itself fire any guard. T010 decides what fires,
    from these contents alone.
    """

    matched: dict[tuple[str, str], str]
    """`(wp_id, case_id) -> automation_ref` for every declared tuple whose `automation_ref`
    matched an enumerated testcase key exactly — an ordered `dict`, built by iterating the
    `declared` argument in its own order, never a `set()`. A matched entry may still belong to a
    duplicate group (see `duplicate_groups`) — matching and duplicate-membership are independent
    facts about the same declared tuple."""

    unresolved: list[tuple[str, str, str]]
    """`(wp_id, case_id, automation_ref)` for every declared tuple whose `automation_ref` matched
    no enumerated testcase, in declared order. May include entries that are also duplicate-group
    members — T010's FR-017 guard is responsible for excluding duplicate members before treating
    this list as FR-017 candidates (FR-018 is checked first and independently)."""

    duplicate_groups: dict[str, list[tuple[str, str]]]
    """`automation_ref -> ordered list of (wp_id, case_id)` for every `automation_ref` value
    declared by two or more distinct `(wp_id, case_id)` pairs — an ordered `dict`, first-seen
    `automation_ref` order, each group's pair list in declared order. A single-declarer ref is
    never a key here, regardless of whether it resolved."""

    claimed_keys: list[str] = field(default_factory=list)
    """`classname::name` keys claimed (matched) by at least one declared `automation_ref`, in the
    order each was first claimed — never a `set()` in this field's own type, even though a `set`
    is used internally to avoid double-appending. Feeds T011's `unmapped_testcases` computation
    directly (FR-007(b))."""


def correlate_automation_refs(
    declared: list[tuple[str, str, str]], enumerated: dict[str, ET.Element],
) -> CorrelationResult:
    """T009: resolve every declared `(wp_id, case_id, automation_ref)` tuple against `enumerated`
    (`classname::name -> testcase element`, from `build_testcase_index`), producing the shared
    correlation map both T010's guards and WP03's carrier writer consume.

    For each declared tuple with a non-empty `automation_ref` (WP01's reader already filters to
    only non-empty refs — see `read_wp_automation_refs`), this function:

    1. Groups the full `declared` list by `automation_ref` value first, independent of whether any
       given ref resolves, to build `duplicate_groups` — a ref declared by 2+ distinct
       `(wp_id, case_id)` pairs is a duplicate group regardless of match outcome.
    2. Attempts a lookup of each tuple's `automation_ref` in `enumerated`; a match populates
       `matched` and, on first claim, `claimed_keys`; no match appends to `unresolved`.

    This subtask is pure plumbing — it does not itself raise or exit; T010 consumes this result to
    decide what fires, and in what order (FR-018 before FR-017). Returns an ordered structure
    throughout: `matched` and `duplicate_groups` are `dict`s built by iterating `declared` in its
    own order, `unresolved` and `claimed_keys` are `list`s in declared/first-claimed order — never
    a `set()` anywhere in the returned shape, preserving the determinism precondition WP03's
    FR-010 proof depends on.
    """
    by_ref: dict[str, list[tuple[str, str]]] = {}
    for wp_id, case_id, ref in declared:
        pair = (wp_id, case_id)
        group = by_ref.setdefault(ref, [])
        if pair not in group:
            group.append(pair)
    duplicate_groups = {ref: pairs for ref, pairs in by_ref.items() if len(pairs) >= 2}

    matched: dict[tuple[str, str], str] = {}
    unresolved: list[tuple[str, str, str]] = []
    claimed_keys: list[str] = []
    seen_claimed: set[str] = set()

    for wp_id, case_id, ref in declared:
        if ref in enumerated:
            matched[(wp_id, case_id)] = ref
            if ref not in seen_claimed:
                seen_claimed.add(ref)
                claimed_keys.append(ref)
        else:
            unresolved.append((wp_id, case_id, ref))

    return CorrelationResult(
        matched=matched, unresolved=unresolved,
        duplicate_groups=duplicate_groups, claimed_keys=claimed_keys,
    )


# --- Unresolvable automation_ref (FR-017) + duplicate automation_ref (FR-018) guards (T010) ------
#
# Ordering matters (spec.md Decision 1(b) Second Amendment, and the FR-018 row's own text: "checked
# ahead of / independently from FR-017 resolution"): FR-018 (a cross-file/cross-case collision,
# exit 2 -- the *combined* input carries no way to decide which declaration is authoritative) is
# checked BEFORE FR-017 (a single, non-colliding ref that simply matches nothing, exit 1). Getting
# this backwards would let a genuine collision surface as two separate "unresolved automation_ref"
# failures instead of one named duplicate-detection error -- main() must call
# check_duplicate_automation_refs() before check_unresolved_automation_ref().


def check_duplicate_automation_refs(correlation: CorrelationResult) -> None:
    """FR-018 (named, exit-2 `error`): when `correlation.duplicate_groups` is non-empty, two or
    more distinct WP cases declare the identical `automation_ref` value -- each individual WP file
    is well-formed, but the *combined* input carries no way to decide which declaration is
    authoritative (Decision 1(b), Second Amendment). Raises `IngestError`, naming **every**
    colliding WP, **every** colliding case id, and the duplicated string for **every** group --
    not just the first pair found. Must be called before `check_unresolved_automation_ref` (see
    module comment above)."""
    if not correlation.duplicate_groups:
        return
    parts = []
    for ref, pairs in correlation.duplicate_groups.items():
        colliders = ", ".join(f"{wp_id}/{case_id}" for wp_id, case_id in pairs)
        parts.append(f"automation_ref {ref!r} declared by {colliders}")
    raise IngestError("uninterpretable: duplicate automation_ref value(s): " + "; ".join(parts))


def check_unresolved_automation_ref(correlation: CorrelationResult) -> None:
    """FR-017 (named, exit-1 `blocked`): for the first declared `automation_ref` that matched no
    enumerated testcase AND is not part of a duplicate group (duplicate refs are FR-018's concern
    exclusively -- see the module comment above on ordering), raises `BlockedError` naming the WP,
    the case id, and the unresolved `automation_ref` string. Never silently dropped from the
    carrier, and never silently treated as `not_measured` the way an *absent* evidence file is
    (FR-006/FR-007's boundary) -- that is a different condition entirely, outside this guard's
    scope. Callers must call `check_duplicate_automation_refs` first so a duplicate never reaches
    this function's resolution check at all."""
    duplicate_refs = set(correlation.duplicate_groups)
    for wp_id, case_id, ref in correlation.unresolved:
        if ref in duplicate_refs:
            continue
        raise BlockedError(
            f"unresolvable automation_ref: {wp_id} {case_id} declares automation_ref {ref!r}, "
            "which matches no enumerated testcase"
        )


# --- No-silent-exclusion, both directions (T011, FR-007) -------------------------------------------


# PMI-01 (pre-merge adversarial squad, severity 5): every real WP file this pack ships is named
# `WP\d\d-<slug>.md` (this pack's own naming convention, spec.md:531, plan.md's worked examples,
# and every real `tasks/WP*.md` this mission itself ships) -- never a bare `WP\d\d.md`. The
# validator's own `WP_ID_RE` (`assets/qa_carrier_lint.py:82`, `^WP\d{2}$`) accepts only the bare
# two-digit token; `wp_path.stem` on a realistically-named file is the FULL stem
# (`"WP01-login-flow"`), which `WP_ID_RE` is guaranteed to reject -- killing the entire carrier's
# A-11 guard, not one case. This regex is the read-direction mirror of the validator's own
# write-direction tolerance (`wp_file_re = re.compile(rf"^{re.escape(wp_id)}(-.*)?$")`,
# `assets/qa_carrier_lint.py:391`): the validator already accepts `WP\d\d` optionally followed by
# `-<anything>`; this producer must emit exactly the `WP\d\d` prefix that relationship assumes.
_WP_ID_FROM_FILENAME_RE = re.compile(r"^(WP\d{2})(?:-.*)?$")


def _wp_id_from_stem(wp_path: Path) -> str:
    """PMI-01 fix: derive a validator-acceptable `WP\\d\\d` id from a WP file's own stem by
    matching its `WP\\d\\d` PREFIX (optionally followed by `-<slug>`) -- never returning the bare,
    full stem the way this function's predecessor (`wp_id = wp_path.stem`) did. See the module
    comment above `_WP_ID_FROM_FILENAME_RE` for why: every real WP file is `WP\\d\\d-<slug>.md`,
    and the validator's A-11 guard (`WP_ID_RE`, `^WP\\d{2}$`) rejects anything else, hard-failing
    the whole carrier as a `schema_violation`.

    Raises `IngestError` (clause (b), "uninterpretable:") naming the file when the stem carries no
    such prefix at all. Two silent options were both rejected: emitting the bare stem anyway would
    just move PMI-01's defect one layer deeper (the validator still rejects it); silently DROPPING
    the case instead would reintroduce a different named defect -- FR-007's no-silent-exclusion
    guarantee -- by discarding a real declared `automation_ref` this reader must never drop. Loud
    and exit-2, naming the offending path, is the only option that violates neither.
    """
    match = _WP_ID_FROM_FILENAME_RE.match(wp_path.stem)
    if not match:
        raise IngestError(
            f"uninterpretable: {wp_path} does not start with a 'WP' + two digits id "
            "(e.g. 'WP01-slug.md' or 'WP01.md'); cannot derive a validator-acceptable "
            "wp id from its filename"
        )
    return match.group(1)


def _iter_wp_case_frontmatter(tasks_dir: Path) -> Iterator[tuple[str, dict]]:
    """THE single shared file/shape traversal for `tasks/WP*.md` (WP03 factoring, carried forward
    from WP02's review): glob, frontmatter split, YAML parse, and nested-vs-flat shape detection,
    in one place. Yields `(wp_id, case_dict)` for every parseable case block, regardless of which
    fields the case dict itself carries -- see `read_wp_automation_refs` (T004) above for the full
    nested/flat shape contract this traversal implements (kept there, not duplicated here).

    Both of this module's WP-frontmatter readers route through this one generator:
    `read_wp_automation_refs` (T004, `(wp_id, case_id, automation_ref)` triples) and
    `read_wp_case_requirement_refs` (T011 below, `(wp_id, case_id) -> requirement_refs`). Before
    WP03, each carried its own byte-for-byte copy of this traversal -- today they could not
    disagree, but a future shape-detection fix landing in one copy and not the other would have
    made their `(wp_id, case_id)` keys diverge silently, breaking the key alignment
    `read_wp_case_requirement_refs` depends on to line up with `correlate_automation_refs`'s
    (T009) matched-case keys. One traversal now; both callers factor identically over it by
    construction, not by two authors' discipline staying in sync by hand.
    """
    if not tasks_dir.is_dir():
        return
    for wp_path in sorted(tasks_dir.glob("WP*.md")):
        try:
            text = wp_path.read_text()
        except OSError:
            continue
        parts = text.split("---", 2)
        if len(parts) < 3:
            continue
        try:
            fm = _yaml().load(parts[1]) or {}
        except Exception:
            continue
        if not isinstance(fm, dict):
            continue
        cases = fm.get("cases")
        if cases is None and "id" in fm and "requirement_refs" in fm:
            cases = [fm]
            wp_id = _wp_id_from_stem(wp_path)
        else:
            wp_id = fm.get("id") or _wp_id_from_stem(wp_path)
        seen: set[str] = set()
        if isinstance(cases, list):
            for case in cases:
                if isinstance(case, dict):
                    seen.add(str(case.get("id")))
                    yield str(wp_id), case
        # The template-mandated shape (`test-case-template.md`): one fenced ```yaml block per
        # case in the WP body, because the WP's own frontmatter belongs to WPMetadata, which
        # forbids extra keys. Frontmatter cases come first; a body block repeating a case id
        # already yielded for this WP is skipped (first occurrence wins).
        for case in _body_yaml_case_blocks(parts[2], wp_path):
            case_id = str(case["id"])
            if case_id in seen:
                sys.stderr.write(
                    f"warning: {wp_path} repeats case id {case_id!r}; the first occurrence is used\n"
                )
                continue
            seen.add(case_id)
            yield str(wp_id), case


_FENCE_OPEN_RE = re.compile(r"^(?P<indent>[ \t]*)(?P<fence>`{3,}|~{3,})[ \t]*(?P<info>[^\n]*)$")


def _body_yaml_case_blocks(body: str, wp_path: Path | None = None) -> Iterator[dict]:
    """Yield every fenced yaml block in a WP body that parses to a mapping with an `id`.

    Fences follow CommonMark: three or more backticks or tildes, closed by a run of the same
    character at least as long; the info string's first word is `yaml` or `yml` in any case
    (`yaml title=x` counts). An indented fence (inside a list item) is read too, with its
    indentation removed. Blocks that fail to parse, parse to a non-mapping, or carry no `id`
    are not cases and are skipped, as prose and other code fences are. A yaml fence left open
    runs to the end of the body and is reported on stderr, since it hides any case after it."""
    lines = body.splitlines()
    i = 0
    while i < len(lines):
        opening = _FENCE_OPEN_RE.match(lines[i])
        i += 1
        if opening is None or (opening["fence"][0] == "`" and "`" in opening["info"]):
            continue
        fence, indent = opening["fence"], len(opening["indent"].expandtabs(4))
        language = opening["info"].split(maxsplit=1)[0].lower() if opening["info"].strip() else ""
        closing = re.compile(rf"^[ \t]*{re.escape(fence[0])}{{{len(fence)},}}[ \t]*$")
        block: list[str] = []
        while i < len(lines) and closing.match(lines[i]) is None:
            line = lines[i].expandtabs(4)
            block.append(line[min(indent, len(line) - len(line.lstrip(" "))):])
            i += 1
        closed = i < len(lines)
        i += 1
        if language not in ("yaml", "yml"):
            continue
        if not closed:
            where = f"{wp_path}: " if wp_path is not None else ""
            sys.stderr.write(f"warning: {where}a {fence}{language} block is never closed\n")
        try:
            doc = _yaml().load("\n".join(block))
        except Exception:
            continue
        if isinstance(doc, dict) and "id" in doc:
            yield doc


def read_wp_case_requirement_refs(tasks_dir: Path) -> dict[tuple[str, str], list[str]]:
    """T011 direction (a) support: for every case block under `tasks/WP*.md` (both shapes), read
    its own declared `requirement_refs` -- the same field `test-case-template.md`'s flat shape
    carries directly at the case's own top level (`id`, `requirement_refs`, `priority`, `type`,
    `preconditions`), and, in the nested shape, the same field read per case entry inside
    `cases:`. Keyed by `(wp_id, case_id)`, using the identical id-resolution rules
    `_iter_wp_case_frontmatter` (the one shared traversal both this function and
    `read_wp_automation_refs` route through) implements, so this dict's keys line up exactly
    with `CorrelationResult.matched`'s keys.

    A case with no `requirement_refs` key, a non-list value, or an empty list is recorded as `[]`
    (present, empty) rather than omitted -- a lookup *miss* (a case this reader never saw a
    frontmatter block for at all) is thereby distinguishable from a case that declares zero
    requirements, though `associate_requirements_with_cases` below treats both the same way
    (`.get(..., [])`) since T011's own FR-007(a) scope does not require telling them apart.
    """
    result: dict[tuple[str, str], list[str]] = {}
    for wp_id, case in _iter_wp_case_frontmatter(tasks_dir):
        case_id = str(case.get("id", ""))
        refs = case.get("requirement_refs")
        if not isinstance(refs, list):
            refs = []
        result[(wp_id, case_id)] = [str(r) for r in refs]
    return result


def associate_requirements_with_cases(
    requirement_ids: list[str],
    correlation: CorrelationResult,
    case_requirement_refs: dict[tuple[str, str], list[str]],
) -> list[tuple[str, list[str], str | None]]:
    """FR-007(a): for every requirement id from WP01's T003 parser output (`parse_requirement_
    register`), in that same order, find every `(wp_id, case_id)` whose `automation_ref` resolved
    (present in `correlation.matched`) AND whose own declared `requirement_refs`
    (`case_requirement_refs`) names this requirement id. If none do, the row still appears with
    `case_ids: []` plus a non-empty, ingester-authored `rationale` -- never a silently dropped row.

    Returns an ordered list of `(requirement_id, case_ids, rationale_or_none)` tuples, one per
    Requirement Register row, in T003's own order; each row's `case_ids` iterates
    `correlation.matched` in its own (declared) insertion order -- never a `set()` anywhere in this
    function's construction, preserving FR-010's determinism precondition.
    """
    rows: list[tuple[str, list[str], str | None]] = []
    for req_id in requirement_ids:
        case_ids: list[str] = []
        for wp_id, case_id in correlation.matched:
            if req_id in case_requirement_refs.get((wp_id, case_id), []):
                case_ids.append(case_id)
        if case_ids:
            rows.append((req_id, case_ids, None))
        else:
            rows.append((
                req_id, [],
                "no automation_ref declared or resolved for this requirement in the current run",
            ))
    return rows


def compute_unmapped_testcases(
    testcases: list[ET.Element], correlation: CorrelationResult,
) -> list[str]:
    """FR-007(b): every enumerated JUnit testcase (`classname::name`) not claimed by any declared
    `automation_ref`, in enumeration order -- an ordered `list` in the return type, even though a
    `set` is used internally for the O(1) membership check, so no enumerable list this function
    produces is ever a bare `set()`. Always present in the tool's own output, even when empty
    (WP03's writer/`--json` output must never omit or null this field) -- distinct from any
    carrier-schema field: a testcase that is not a `cases[]` entry is not a carrier concept at all,
    this is where it becomes visible instead of vanishing.
    """
    claimed = set(correlation.claimed_keys)
    unmapped: list[str] = []
    seen: set[str] = set()
    for tc in testcases:
        key = testcase_key(tc)
        if key in claimed or key in seen:
            continue
        seen.add(key)
        unmapped.append(key)
    return unmapped


# --- Absence guards (T006): FR-019 missing-spec.md, FR-001 missing-results -----------------------


def check_spec_md_present(strategy_path: Path) -> None:
    """FR-019: before invoking `parse_requirement_register`, check that `--strategy`'s spec.md
    actually exists on disk. Raises `IngestError` (Decision 1(a), Second Amendment) naming the
    missing path if it does not. Must fire before the Requirement Register parser is ever called
    — a missing file is not the same condition as a present file parsing to zero rows (the
    parser's own legitimate `requirements: []` result)."""
    if not strategy_path.exists():
        raise IngestError(f"absent: spec.md not found at {strategy_path}")


def check_results_supplied_when_declared(
    automation_refs: list[tuple[str, str, str]], results: list[Path],
) -> None:
    """FR-001's own guard: after the WP-frontmatter reader has run, if at least one WP case
    declares a non-empty `automation_ref` anywhere in the mission and `--results` was supplied
    with zero paths, raise `IngestError` (Decision 1(a), Second Amendment) — declared automated
    evidence with nothing supplied to correlate against is absence, not a guard on data this tool
    distrusts (clause (b)), since no file was supplied for the tool to read in the first place.

    If no case declares `automation_ref` at all, `--results` being empty is legitimate — this
    guard does not fire, and the ingester proceeds with zero JUnit correlation attempted (the
    "legitimately exploratory/manual-only QA mission" path, `spec.md` Edge Cases).

    Explicitly does NOT implement FR-005's guard (a *supplied* file that parses to zero
    testcases) — that is WP02's job, a different condition (exit 1, not exit 2; data supplied
    but insufficient, not data absent)."""
    if automation_refs and not results:
        first_wp, first_case, first_ref = automation_refs[0]
        raise IngestError(
            "absent: --results was not supplied, but "
            f"{len(automation_refs)} case(s) declare automation_ref (e.g. {first_wp} "
            f"{first_case} -> {first_ref!r}); nothing was supplied to correlate against"
        )


# --- Self-test suite (T014-T016, FR-008/NFR-002/NFR-007) -------------------------------------------
#
# Mirrors qa_carrier_lint.py --self-test's own practice throughout: `PairN` (optionally
# `PairN.M`) string-literal constants name each fixture/assertion pair, `_derive_self_test_pair_
# count` below parses this module's OWN source (ast) and counts the DISTINCT base numbers N -- the
# printed count is derived, never hand-maintained, so it cannot drift from the actual fixture set
# the way a hand-typed number already has, twice, in this pack's own sibling tool.
#
# What --self-test proves, and what it deliberately does NOT: every guard WP01/WP02 built is
# exercised on the actual guard function itself (never a reimplementation of the guard's logic) --
# a fixture that trips it, and an otherwise-identical fixture that does not, so this suite proves
# each guard both fires and does not fire (this programme's own repeated defect: "an assertion
# that only ever passes"). FR-001's missing---results case and FR-019's missing-spec.md case are
# NOT here (per this WP's own tasks file, T014 step 3) -- both concern whether a required input
# was supplied at all, before any fixture content is read, and are proven instead by dedicated
# Success Criteria (SC-013, SC-018) run as real subprocess invocations of the finished CLI,
# recorded separately as this WP's own evidence (see the WP report, T037).
#
# T015 extended self_test() with FR-006 tiers 2/3 + the NFR-007 separate-process determinism
# proof. T016 (this commit) extends it further with the NFR-002 traversal-parity proof, plus a
# demonstration (not a `PairN`, see below) of WP02 review finding 3's malformed-timestamp warning.

_PAIR_ID_RE = re.compile(r"^Pair(\d+)(?:\.\d+)?$")


def _derive_self_test_pair_count() -> int:
    """The single source of truth `--help` and `--self-test`'s own summary line both call --
    parses this module's OWN source (the same self-inspection technique
    `qa_carrier_lint.py:_derive_self_test_pair_count` uses), walks `self_test`'s AST for every
    string-literal constant matching `Pair<N>` or `Pair<N>.<M>`, and counts the number of DISTINCT
    base numbers `N`. Never hand-typed, so it cannot drift from the actual fixture set the way a
    hand-typed count already has, in this pack's own sibling tool."""
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


def _independent_testcase_traversal(xml_path: Path) -> set[tuple[str, str]]:
    """NFR-002: a second, independently-written traversal of the same documented idiom
    (`tree.getroot().iter("testcase")`, `assets/qa_carrier_lint.py:307`) -- deliberately NOT a call
    into `enumerate_testcases` (T005); this function re-parses the fixture and re-walks the tree
    itself, so Pair11 below compares two separately-written implementations of one idiom, never
    one function checked against itself.

    **Scoped honestly (spec.md's own NFR-002 row, R1 adversarial squad finding EXEC-02)**:
    `qa_carrier_lint.py` exposes no standalone, importable function that enumerates the full
    `(classname, name)` set -- `classify_junit(xml_path, testcase_ref, tree_cache)` classifies one
    already-known ref via its own internal loop and returns only that ref's state, never the full
    set; `_parse_junit_xml()` only parses the tree, doing no enumeration. This function therefore
    compares two independently-written implementations of the same documented idiom, not this
    mission's traversal against `qa_carrier_lint.py`'s own shipped enumeration path (no such path
    exists to call). If `classify_junit`'s real traversal logic ever changed, Pair11 would not
    notice, since it never calls it. This is what "parity" proves here -- stated plainly, never
    implied to be stronger than it is.
    """
    tree = ET.parse(xml_path)
    return {(tc.get("classname", ""), tc.get("name", "")) for tc in tree.getroot().iter("testcase")}


def self_test() -> int:
    """FR-008: run every fixture/assertion pair below and exit 0 only if all pass."""
    failures: list[str] = []

    def ok(pair: str, desc: str, cond: bool) -> None:
        if not cond:
            failures.append(f"{pair} - {desc}: condition failed")

    def fires(pair: str, desc: str, exc_type: type, fn, *args) -> str:
        """Calls `fn(*args)`; records a failure unless `exc_type` was raised. Returns the raised
        exception's message (or '' if it did not fire, itself recorded as a failure)."""
        try:
            fn(*args)
        except exc_type as exc:
            return str(exc)
        except Exception as exc:  # noqa: BLE001 - deliberately broad: wrong-exception-type IS a failure
            failures.append(
                f"{pair} - {desc}: raised {type(exc).__name__} instead of {exc_type.__name__}: {exc}"
            )
            return ""
        failures.append(f"{pair} - {desc}: did not raise {exc_type.__name__}")
        return ""

    def does_not_fire(pair: str, desc: str, fn, *args) -> None:
        try:
            fn(*args)
        except Exception as exc:  # noqa: BLE001 - any exception here IS the failure being checked for
            failures.append(f"{pair} - {desc}: raised {type(exc).__name__} unexpectedly: {exc}")

    with tempfile.TemporaryDirectory(prefix="qa_result_ingest_selftest_") as root_str:
        root = Path(root_str)

        # --- Pair1 -- FR-002: enumeration count assertion (proves a <skipped/>-tagged testcase
        # is not silently dropped by a naive traversal). ---------------------------------------
        p1 = root / "pair1.xml"
        p1.write_text(
            '<testsuite name="s" tests="3">'
            '<testcase classname="c" name="t_pass"/>'
            '<testcase classname="c" name="t_fail"><failure message="x"/></testcase>'
            '<testcase classname="c" name="t_skip"><skipped/></testcase>'
            "</testsuite>"
        )
        testcases1, asserted_count1 = enumerate_testcases(p1)
        manual_count1 = len(list(ET.parse(p1).getroot().iter("testcase")))
        ok(
            "Pair1", "asserted_count matches enumerate_testcases's own returned list length",
            asserted_count1 == len(testcases1),
        )
        ok(
            "Pair1", "asserted_count matches an independent manual .iter('testcase') count",
            asserted_count1 == manual_count1 == 3,
        )
        ok(
            "Pair1", "the <skipped/>-tagged testcase is present, not silently dropped",
            any(tc.get("name") == "t_skip" for tc in testcases1),
        )

        # --- Pair2 -- FR-005: zero-parsed-testcases guard. --------------------------------------
        p2_empty = root / "pair2_empty.xml"
        p2_empty.write_text('<testsuite name="s" tests="0"/>')
        testcases2_empty, _ = enumerate_testcases(p2_empty)
        msg = fires(
            "Pair2.1", "check_zero_testcases fires on an empty <testsuite/>",
            BlockedError, check_zero_testcases, p2_empty, testcases2_empty,
        )
        ok("Pair2.1", "message names the file", str(p2_empty) in msg)

        p2_nfail = root / "pair2_nfail.xml"
        p2_nfail.write_text(
            '<testsuite name="s" tests="2">'
            '<testcase classname="c" name="t1"/>'
            '<testcase classname="c" name="t2"/>'
            "</testsuite>"
        )
        testcases2_nfail, _ = enumerate_testcases(p2_nfail)
        does_not_fire(
            "Pair2.2", "check_zero_testcases does not fire on N testcases, 0 failures",
            check_zero_testcases, p2_nfail, testcases2_nfail,
        )

        # --- Pair3 -- FR-006, tier 1 only (tiers 2/3 are Pair9, T015's construction). -----------
        since = parse_since_boundary("2026-01-01T00:00:00Z")
        p3_fresh = root / "pair3_fresh.xml"
        p3_fresh.write_text(
            '<testsuite name="s" timestamp="2026-06-01T00:00:00Z">'
            '<testcase classname="c" name="t"/></testsuite>'
        )
        does_not_fire(
            "Pair3.1", "check_staleness does not fire on a tier-1 timestamp after --since",
            check_staleness, p3_fresh, since,
        )

        p3_stale = root / "pair3_stale.xml"
        p3_stale.write_text(
            '<testsuite name="s" timestamp="2020-01-01T00:00:00Z">'
            '<testcase classname="c" name="t"/></testsuite>'
        )
        msg = fires(
            "Pair3.2", "check_staleness fires on a tier-1 timestamp before --since",
            BlockedError, check_staleness, p3_stale, since,
        )
        ok(
            "Pair3.2", "message names the file, the tier, and the --since boundary",
            str(p3_stale) in msg and "tier 1" in msg and since.isoformat() in msg,
        )

        # --- Pair4 -- FR-007, both directions (SC-004's own four sub-cases). --------------------
        tc_a = ET.Element("testcase", {"classname": "c", "name": "claimed"})
        tc_b = ET.Element("testcase", {"classname": "c", "name": "unclaimed"})
        corr_covered = CorrelationResult(
            matched={("WP01", "TC-001"): "c::claimed"}, unresolved=[], duplicate_groups={},
            claimed_keys=["c::claimed"],
        )
        rows_covered = associate_requirements_with_cases(
            ["FR-900"], corr_covered, {("WP01", "TC-001"): ["FR-900"]},
        )
        ok(
            "Pair4.1", "a covered requirement's row carries its case_ids, no rationale",
            rows_covered == [("FR-900", ["TC-001"], None)],
        )

        corr_empty = CorrelationResult(
            matched={}, unresolved=[], duplicate_groups={}, claimed_keys=[],
        )
        rows_uncovered = associate_requirements_with_cases(["FR-901"], corr_empty, {})
        ok(
            "Pair4.2",
            "an uncorrelated requirement row is present, not dropped, with a non-empty rationale",
            len(rows_uncovered) == 1 and rows_uncovered[0][0] == "FR-901"
            and rows_uncovered[0][1] == [] and bool((rows_uncovered[0][2] or "").strip()),
        )

        unmapped_covered = compute_unmapped_testcases([tc_a], corr_covered)
        ok("Pair4.3", "a claimed testcase is absent from unmapped_testcases", unmapped_covered == [])

        unmapped_uncovered = compute_unmapped_testcases([tc_b], corr_empty)
        ok(
            "Pair4.4", "an unclaimed testcase is present in unmapped_testcases, not absorbed",
            unmapped_uncovered == ["c::unclaimed"],
        )

        # --- Pair5 -- FR-009: dual-shape Requirement Register parser. ---------------------------
        p5_table = root / "pair5_table.md"
        p5_table.write_text(
            "# spec\n\n## Requirements\n\n"
            "| ID | Description |\n|---|---|\n"
            "| FR-910 | table row shape |\n"
            "| FR-911 | table row shape |\n"
        )
        p5_bullet = root / "pair5_bullet.md"
        p5_bullet.write_text(
            "# spec\n\n## Requirements\n\n"
            "- **FR-910**: bullet form shape\n"
            "- **FR-911**: bullet form shape\n"
        )
        reqs_table = parse_requirement_register(p5_table)
        reqs_bullet = parse_requirement_register(p5_bullet)
        ok(
            "Pair5", "table-row shape and bullet-form shape parse to the identical, non-zero id set",
            reqs_table == reqs_bullet == ["FR-910", "FR-911"],
        )

        # --- Pair6 -- FR-016: unsupported status=-attribute JUnit-XML dialect. ------------------
        p6_bad = root / "pair6_bad.xml"
        p6_bad.write_text(
            '<testsuite name="s"><testcase classname="c" name="t" status="failed"/></testsuite>'
        )
        msg = fires(
            "Pair6.1", "enumerate_testcases refuses the status=-attribute dialect",
            IngestError, enumerate_testcases, p6_bad,
        )
        ok(
            "Pair6.1", "message names the file and the offending testcase",
            str(p6_bad) in msg and "c::t" in msg,
        )

        p6_good = root / "pair6_good.xml"
        p6_good.write_text(
            '<testsuite name="s"><testcase classname="c" name="t">'
            "<failure message=\"x\"/></testcase></testsuite>"
        )
        does_not_fire(
            "Pair6.2", "enumerate_testcases ingests the child-element dialect cleanly",
            enumerate_testcases, p6_good,
        )

        # --- Pair7 -- FR-017: unresolvable automation_ref. --------------------------------------
        corr7_unresolved = correlate_automation_refs([("WP01", "TC-001", "c::missing")], {})

        def _check7_unresolved() -> None:
            check_duplicate_automation_refs(corr7_unresolved)
            check_unresolved_automation_ref(corr7_unresolved)

        msg = fires(
            "Pair7.1", "check_unresolved_automation_ref fires on a non-matching ref",
            BlockedError, _check7_unresolved,
        )
        ok(
            "Pair7.1", "message names the WP, case, and ref",
            "WP01" in msg and "TC-001" in msg and "c::missing" in msg,
        )

        corr7_resolved = correlate_automation_refs(
            [("WP01", "TC-001", "c::present")], {"c::present": None},
        )

        def _check7_resolved() -> None:
            check_duplicate_automation_refs(corr7_resolved)
            check_unresolved_automation_ref(corr7_resolved)

        does_not_fire("Pair7.2", "a matching automation_ref ingests cleanly", _check7_resolved)

        # --- Pair8 -- FR-018: duplicate automation_ref (checked before FR-017, T010's ordering). -
        corr8_dup = correlate_automation_refs(
            [("WP01", "TC-001", "c::dup"), ("WP02", "TC-002", "c::dup")], {},
        )
        msg = fires(
            "Pair8.1", "check_duplicate_automation_refs fires on a cross-file collision",
            IngestError, check_duplicate_automation_refs, corr8_dup,
        )
        ok(
            "Pair8.1", "message names both colliding WPs, both case ids, and the duplicated ref",
            all(s in msg for s in ("WP01", "TC-001", "WP02", "TC-002", "c::dup")),
        )

        corr8_nodup = correlate_automation_refs(
            [("WP01", "TC-001", "c::one"), ("WP02", "TC-002", "c::two")], {},
        )
        does_not_fire(
            "Pair8.2", "distinct automation_ref values do not fire the duplicate guard",
            check_duplicate_automation_refs, corr8_nodup,
        )

        # --- Pair9 -- FR-006, tiers 2 and 3, built inside an isolated sandbox (never this
        # checkout's own git history), per spec.md's own EXEC-04-derived construction method. ----
        try:
            tier2_dir = root / "pair9_tier2"
            tier2_dir.mkdir()
            subprocess.run(["git", "init", "-q"], cwd=tier2_dir, capture_output=True, text=True)
            subprocess.run(
                ["git", "config", "user.email", "selftest@example.invalid"],
                cwd=tier2_dir, capture_output=True, text=True,
            )
            subprocess.run(
                ["git", "config", "user.name", "selftest"],
                cwd=tier2_dir, capture_output=True, text=True,
            )
            tier2_file = tier2_dir / "results.xml"
            tier2_file.write_text('<testsuite name="s"><testcase classname="c" name="t"/></testsuite>')
            commit_env = {
                **os.environ,
                "GIT_AUTHOR_DATE": "2026-03-01T00:00:00+00:00",
                "GIT_COMMITTER_DATE": "2026-03-01T00:00:00+00:00",
            }
            subprocess.run(["git", "add", "results.xml"], cwd=tier2_dir, capture_output=True, text=True)
            commit_proc = subprocess.run(
                # `-c commit.gpgsign=false`: this is a disposable sandbox commit inside a tempdir
                # this self-test creates and tears down within the same run -- never a commit in
                # this checkout's own repository -- so a machine with global GPG signing enabled
                # must not be able to make --self-test hang waiting for a passphrase.
                ["git", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "selftest fixture"],
                cwd=tier2_dir, env=commit_env, capture_output=True, text=True,
            )
            if commit_proc.returncode != 0:
                failures.append(f"Pair9.1 - sandbox setup: git commit failed: {commit_proc.stderr}")
            else:
                resolved_dt, tier_name = resolve_result_timestamp(tier2_file)
                expected_dt = _parse_iso8601("2026-03-01T00:00:00+00:00")
                ok(
                    "Pair9.1",
                    "tier 2 resolves via git log -1 --format=%cI, matching the controlled commit time",
                    tier_name == "tier 2 (git commit time)" and resolved_dt == expected_dt,
                )
        except OSError as exc:
            failures.append(f"Pair9.1 - sandbox setup: git unavailable or failed: {exc}")

        tier3_dir = root / "pair9_tier3"
        tier3_dir.mkdir()
        tier3_file = tier3_dir / "results.xml"
        tier3_file.write_text('<testsuite name="s"><testcase classname="c" name="t"/></testsuite>')
        resolved_dt3, tier_name3 = resolve_result_timestamp(tier3_file)
        expected_mtime = datetime.fromtimestamp(tier3_file.stat().st_mtime, tz=timezone.utc)
        ok(
            "Pair9.2", "tier 3 falls through to filesystem mtime when untracked/git-unavailable",
            tier_name3 == "tier 3 (filesystem mtime)" and resolved_dt3 == expected_mtime,
        )

        # --- Pair10 -- NFR-007: separate-process determinism proof. -----------------------------
        try:
            det_root = root / "pair10"
            det_root.mkdir()
            shared_tasks = det_root / "tasks"
            shared_tasks.mkdir()
            (shared_tasks / "WP01.md").write_text(
                "---\n"
                "id: TC-500\n"
                "requirement_refs:\n"
                "  - FR-950\n"
                "priority: high\n"
                "type: functional\n"
                "preconditions: none\n"
                "automation_ref: c::t_det\n"
                "---\n"
                "# WP01\n"
            )
            spec_path = det_root / "spec.md"
            spec_path.write_text(
                "# spec\n\n## Requirements\n\n"
                "| ID | Description |\n|---|---|\n"
                "| FR-950 | determinism fixture |\n"
            )
            results_path = det_root / "results.xml"
            results_path.write_text(
                '<testsuite name="s" timestamp="2026-05-01T00:00:00Z">'
                '<testcase classname="c" name="t_det"/>'
                "</testsuite>"
            )
            # Both mission-dirs are named identically ("mission") in separate parent directories
            # at the same depth, so the two runs' `mission:` field (mission_dir.resolve().name)
            # and `evidence.junit.file` (relpath depth from mission_dir to the shared results.xml)
            # are structurally identical -- a real content difference here (not merely a path
            # difference this fixture introduced) is what NFR-007 is proving.
            mission_a = det_root / "run_a" / "mission"
            mission_b = det_root / "run_b" / "mission"
            script = str(Path(__file__).resolve())
            common_args = [
                "--results", str(results_path), "--strategy", str(spec_path),
                "--tasks-dir", str(shared_tasks), "--since", "2026-01-01T00:00:00Z",
            ]

            def _run_subprocess(mission_dir: Path) -> subprocess.CompletedProcess:
                # NFR-007: PYTHONHASHSEED=random set explicitly per subprocess, never inherited
                # from this parent process -- the exact process-separation fix
                # scripts/pack_ci.py's own --mode determinism-check uses. NFR-008:
                # PYTHONDONTWRITEBYTECODE=1 explicitly, never relying on inheritance either.
                env = {
                    **os.environ, "PYTHONHASHSEED": "random", "PYTHONDONTWRITEBYTECODE": "1",
                }
                return subprocess.run(
                    [sys.executable, script, *common_args, "--mission-dir", str(mission_dir)],
                    capture_output=True, text=True, env=env,
                )

            proc_a = _run_subprocess(mission_a)
            proc_b = _run_subprocess(mission_b)
            ok(
                "Pair10", "both separate-process runs exit 0 (ingested)",
                proc_a.returncode == 0 and proc_b.returncode == 0,
            )
            carrier_a = mission_a / "qa-traceability.yaml"
            carrier_b = mission_b / "qa-traceability.yaml"
            out_a = carrier_a.read_text() if carrier_a.exists() else ""
            out_b = carrier_b.read_text() if carrier_b.exists() else ""
            ok(
                "Pair10",
                "two separate-process runs (PYTHONHASHSEED=random per subprocess, never "
                "inherited) against byte-identical inputs produce byte-identical "
                "qa-traceability.yaml output",
                out_a != "" and out_a == out_b,
            )
        except OSError as exc:
            failures.append(f"Pair10 - subprocess setup failed: {exc}")

        # --- Pair11 -- NFR-002: JUnit traversal parity (scoped honestly, see
        # _independent_testcase_traversal's own docstring). --------------------------------------
        p11 = root / "pair11.xml"
        p11.write_text(
            '<testsuite name="s">'
            '<testcase classname="c" name="t1"/>'
            '<testcase classname="c" name="t2"><failure message="x"/></testcase>'
            '<testcase classname="c" name="t3"><skipped/></testcase>'
            "</testsuite>"
        )
        testcases11, _ = enumerate_testcases(p11)
        own_pairs = {(tc.get("classname", ""), tc.get("name", "")) for tc in testcases11}
        independent_pairs = _independent_testcase_traversal(p11)
        ok(
            "Pair11",
            "this module's own enumeration and an independent traversal of the same documented "
            "idiom agree on the full (classname, name) set",
            own_pairs == independent_pairs == {("c", "t1"), ("c", "t2"), ("c", "t3")},
        )

        # --- Pair12 -- PMI-01 (pre-merge adversarial squad, severity 5): a realistically-named
        # WP\d\d-<slug>.md file -- every real WP file this pack ships, never the bare `WP01.md`
        # every OTHER fixture in this suite (before this fix) used exclusively -- must derive a
        # validator-acceptable `wp_id`, and the carrier this producer writes for such a file must
        # be ACCEPTED by the real, unmodified `qa_carrier_lint.py`, not merely asserted in
        # isolation against this module's own fixture (the exact blind spot that let PMI-01 ship:
        # every fixture in both assets' self-tests used a bare `WP01.md`). ------------------------
        ok(
            "Pair12.1",
            "_wp_id_from_stem derives the bare WP\\d\\d token from a realistic hyphenated stem, "
            "never the full stem",
            _wp_id_from_stem(root / "WP07-some-realistic-slug.md") == "WP07",
        )
        msg = fires(
            "Pair12.2", "_wp_id_from_stem raises IngestError on a stem with no WP\\d\\d prefix",
            IngestError, _wp_id_from_stem, root / "not-a-wp-file.md",
        )
        ok("Pair12.2", "message names the offending file", "not-a-wp-file.md" in msg)

        # Every input this fixture supplies (spec.md, tasks/, results.xml) lives directly under
        # `p12_mission` itself -- `--mission-dir`'s own operational assumption
        # (`_relative_evidence_path`'s docstring: "every --results path supplied is expected to
        # resolve inside --mission-dir") -- so the produced `evidence.junit.file` is a plain,
        # contained relative path the validator's own A-8 containment check accepts, not an
        # unrelated `--mission-dir` layout choice this fixture would otherwise fail on for a
        # reason that has nothing to do with PMI-01.
        p12_dir = root / "pair12_e2e"
        p12_mission = p12_dir / "mission"
        p12_tasks = p12_mission / "tasks"
        p12_tasks.mkdir(parents=True)
        (p12_tasks / "WP07-realistic-slug.md").write_text(
            "---\nid: TC-700\nrequirement_refs:\n  - FR-700\npriority: high\ntype: functional\n"
            "preconditions: none\nautomation_ref: c::t_700\n---\n# realistic\n"
        )
        (p12_mission / "spec.md").write_text(
            "# spec\n\n## Requirements\n\n| ID | Description |\n|---|---|\n"
            "| FR-700 | realistic-filename fixture |\n"
        )
        p12_results = p12_mission / "results.xml"
        p12_results.write_text(
            '<testsuite name="s" timestamp="2026-08-01T00:00:00Z">'
            '<testcase classname="c" name="t_700"/></testsuite>'
        )
        p12_script = str(Path(__file__).resolve())
        p12_env = {**os.environ, "PYTHONHASHSEED": "random", "PYTHONDONTWRITEBYTECODE": "1"}
        p12_producer = subprocess.run(
            [
                sys.executable, p12_script,
                "--results", str(p12_results), "--strategy", str(p12_mission / "spec.md"),
                "--tasks-dir", str(p12_tasks), "--since", "2026-01-01T00:00:00Z",
                "--mission-dir", str(p12_mission),
            ],
            capture_output=True, text=True, env=p12_env,
        )
        ok(
            "Pair12.3",
            f"the producer itself exits 0 (ingested) against a realistically-named WP file "
            f"(stderr={p12_producer.stderr!r})",
            p12_producer.returncode == 0,
        )
        p12_carrier = p12_mission / "qa-traceability.yaml"
        p12_carrier_text = p12_carrier.read_text() if p12_carrier.exists() else ""
        ok(
            "Pair12.3",
            "the written carrier carries the bare 'WP07' token, never the realistically-named "
            "full stem",
            "wp: WP07" in p12_carrier_text and "WP07-realistic-slug" not in p12_carrier_text,
        )
        p12_lint_script = Path(__file__).resolve().parent / "qa_carrier_lint.py"
        if not p12_lint_script.exists():
            failures.append(
                "Pair12.3 - sibling qa_carrier_lint.py not found next to this asset; cannot "
                "prove end-to-end validator acceptance"
            )
        else:
            p12_lint = subprocess.run(
                [
                    sys.executable, str(p12_lint_script), "--carrier", str(p12_carrier),
                    "--mission-dir", str(p12_mission), "--json",
                ],
                capture_output=True, text=True, env=p12_env,
            )
            ok(
                "Pair12.3",
                "the real, unmodified qa_carrier_lint.py accepts this producer's output for a "
                f"realistically-named WP file (stdout={p12_lint.stdout!r} "
                f"stderr={p12_lint.stderr!r})",
                p12_lint.returncode == 0 and '"status": "measured"' in p12_lint.stdout,
            )

        # --- Pair13 -- PMC-01 (pre-merge adversarial squad, severity 5): the writer preserves a
        # pre-existing FOREIGN case (no `evidence.junit` -- manual/exploratory/session-evidenced)
        # instead of destroying it wholesale, refreshing only its own automation_ref-correlated
        # cases; a malformed prior carrier is refused loudly (never silently discarded, which
        # would reintroduce this same defect one layer up); a MISSING prior carrier is a
        # legitimate first run, never an error. -----------------------------------------------
        ok(
            "Pair13.1",
            "_is_producer_case is true for evidence.junit, false for evidence.session",
            _is_producer_case({"evidence": {"junit": {"file": "r.xml", "testcase": "c::t"}}})
            and not _is_producer_case({"evidence": {"session": {"file": "s.session.yaml"}}}),
        )

        p13_dir = root / "pair13_writer"
        p13_mission = p13_dir / "mission"
        p13_mission.mkdir(parents=True)
        p13_results = p13_dir / "results.xml"
        p13_results.write_text(
            '<testsuite name="s"><testcase classname="c" name="t_new"/></testsuite>'
        )
        (p13_mission / "qa-traceability.yaml").write_text(
            "schema: qa-traceability/v1\n"
            "mission: mission\n"
            "generated_at: '2026-01-01T00:00:00+00:00'\n"
            "requirements:\n"
            "- id: FR-002\n"
            "  case_ids: [TC-900]\n"
            "- id: FR-100\n"
            "  case_ids: []\n"
            "  rationale: not yet automated\n"
            "cases:\n"
            "- id: TC-900\n"
            "  wp: WP03\n"
            "  type: exploratory\n"
            "  evidence:\n"
            "    session:\n"
            "      file: s900.session.yaml\n"
            "defects: []\n"
        )
        p13_out = write_carrier(
            p13_mission,
            [
                (
                    "FR-002", [],
                    "no automation_ref declared or resolved for this requirement in the "
                    "current run",
                ),
                ("FR-100", ["TC-100"], None),
            ],
            {("WP01", "TC-100"): "c::t_new"},
            {"c::t_new": p13_results},
            datetime(2026, 2, 1, tzinfo=timezone.utc),
            [],
            json_mode=True,
        )
        p13_doc = _yaml().load(p13_out.read_text())
        p13_cases_by_id = {c["id"]: c for c in p13_doc["cases"]}
        p13_reqs_by_id = {r["id"]: r for r in p13_doc["requirements"]}
        ok(
            "Pair13.2",
            "a foreign case (TC-900, evidence.session) survives write_carrier untouched",
            "TC-900" in p13_cases_by_id
            and p13_cases_by_id["TC-900"]["evidence"] == {"session": {"file": "s900.session.yaml"}},
        )
        ok(
            "Pair13.2",
            "the requirement a preserved foreign case covers keeps its case_ids and carries no "
            "fabricated 'no automation_ref' rationale",
            p13_reqs_by_id["FR-002"]["case_ids"] == ["TC-900"]
            and "rationale" not in p13_reqs_by_id["FR-002"],
        )
        ok(
            "Pair13.2",
            "this producer's own case (TC-100) is freshly written from the current run's "
            "correlation",
            "TC-100" in p13_cases_by_id
            and p13_cases_by_id["TC-100"]["evidence"]["junit"]["testcase"] == "c::t_new"
            and p13_reqs_by_id["FR-100"]["case_ids"] == ["TC-100"],
        )

        p13_bad_mission = root / "pair13_malformed" / "mission"
        p13_bad_mission.mkdir(parents=True)
        (p13_bad_mission / "qa-traceability.yaml").write_text("this: [is, not: valid\n")

        def _p13_write_bad() -> None:
            write_carrier(
                p13_bad_mission, [], {}, {},
                datetime(2026, 2, 1, tzinfo=timezone.utc), [], json_mode=True,
            )

        msg = fires(
            "Pair13.3",
            "write_carrier refuses a malformed pre-existing carrier rather than silently "
            "discarding it (PMC-01)",
            IngestError, _p13_write_bad,
        )
        ok(
            "Pair13.3", "message names the malformed file",
            str(p13_bad_mission / "qa-traceability.yaml") in msg,
        )

        p13_missing_mission = root / "pair13_missing" / "mission"
        p13_missing_mission.mkdir(parents=True)

        def _p13_write_first_run() -> None:
            write_carrier(
                p13_missing_mission,
                [(
                    "FR-900", [],
                    "no automation_ref declared or resolved for this requirement in the "
                    "current run",
                )],
                {}, {}, datetime(2026, 2, 1, tzinfo=timezone.utc), [], json_mode=True,
            )

        does_not_fire(
            "Pair13.4",
            "write_carrier treats a MISSING prior carrier as a legitimate first run, never an "
            "error",
            _p13_write_first_run,
        )

        # --- Pair14 -- PMG-01 (pre-merge adversarial squad, severity 4): FR-007(b)'s non-`--json`
        # disclosure channel -- unmapped_testcases named in qa-traceability.yaml's own header
        # comment when --json is not supplied (always present, even when empty, mirroring the
        # `--json` record's own "never omitted" contract), and ABSENT when --json IS supplied
        # (that run's stdout record is FR-007(b)'s channel instead). ----------------------------
        p14_mission_text = root / "pair14_header" / "mission_text"
        p14_mission_text.mkdir(parents=True)
        p14_out_text = write_carrier(
            p14_mission_text, [], {}, {},
            datetime(2026, 2, 1, tzinfo=timezone.utc), ["c::t_unmapped"], json_mode=False,
        )
        p14_text = p14_out_text.read_text()
        p14_header = p14_text.splitlines()[0]
        ok(
            "Pair14",
            "non-JSON mode writes a header comment naming the count and identity of unmapped "
            "testcases",
            p14_header.startswith("#") and "1 unmapped" in p14_header
            and "c::t_unmapped" in p14_text,
        )

        p14_mission_json = root / "pair14_header" / "mission_json"
        p14_mission_json.mkdir(parents=True)
        p14_out_json = write_carrier(
            p14_mission_json, [], {}, {},
            datetime(2026, 2, 1, tzinfo=timezone.utc), ["c::t_unmapped"], json_mode=True,
        )
        ok(
            "Pair14",
            "--json mode writes no header comment (that run's --json stdout record is FR-007(b)'s "
            "channel instead)",
            not p14_out_json.read_text().lstrip().startswith("#"),
        )

        p14_mission_empty = root / "pair14_header" / "mission_empty"
        p14_mission_empty.mkdir(parents=True)
        p14_out_empty = write_carrier(
            p14_mission_empty, [], {}, {},
            datetime(2026, 2, 1, tzinfo=timezone.utc), [], json_mode=False,
        )
        p14_header_empty = p14_out_empty.read_text().splitlines()[0]
        ok(
            "Pair14",
            "the header comment is present even when unmapped_testcases is empty (never "
            "omitted, mirroring the --json record's contract)",
            p14_header_empty.startswith("#") and "0 unmapped" in p14_header_empty,
        )

        # --- Pair15 -- spec-kitty-qa-pack#5: the template-mandated shape (WPMetadata frontmatter,
        # cases as fenced ```yaml blocks in the WP body) is read, prose and non-yaml fences are
        # not cases, and a case in both frontmatter and body is counted once. ----------------
        p15_tasks = root / "pair15" / "tasks"
        p15_tasks.mkdir(parents=True)
        (p15_tasks / "WP01-body.md").write_text(
            "---\nwork_package_id: WP01\ntitle: Body cases\n---\n\n"
            "```yaml\nid: TC-001\nrequirement_refs: [FR-001]\nautomation_ref: m::t_one\n```\n\n"
            "Prose between the cases.\n\n"
            "```yaml\nid: TC-002\nrequirement_refs: [FR-002]\nautomation_ref: m::t_two\n```\n"
        )
        (p15_tasks / "WP02-noise.md").write_text(
            "---\nwork_package_id: WP02\ntitle: No cases\n---\n\n"
            "```python\nid = 'TC-999'\n```\n\n```yaml\nnot_a_case: true\n```\n\nPlain prose.\n"
        )
        (p15_tasks / "WP03-both.md").write_text(
            "---\nid: TC-010\nrequirement_refs: [FR-010]\nautomation_ref: m::t_ten\n---\n\n"
            "```yaml\nid: TC-010\nrequirement_refs: [FR-010]\nautomation_ref: m::t_ten\n```\n"
        )
        with contextlib.redirect_stderr(io.StringIO()) as p15_stderr:
            p15_refs = read_wp_automation_refs(p15_tasks)
        p15_wp01 = sorted(case_id for wp, case_id, _ in p15_refs if wp == "WP01")
        ok(
            "Pair15",
            "both body-fenced cases of a template-conformant WP are read, with their automation_refs",
            p15_wp01 == ["TC-001", "TC-002"]
            and ("WP01", "TC-001", "m::t_one") in p15_refs,
        )
        ok(
            "Pair15",
            "a non-yaml fence, a yaml block without an `id`, and prose yield no cases",
            not any(wp == "WP02" for wp, _, _ in p15_refs),
        )
        ok(
            "Pair15",
            "a case present in both the frontmatter and a body block is counted once",
            [case_id for wp, case_id, _ in p15_refs if wp == "WP03"] == ["TC-010"],
        )
        ok(
            "Pair15",
            "the repeated case id is reported on stderr, not dropped silently",
            "repeats case id 'TC-010'" in p15_stderr.getvalue(),
        )
        with contextlib.redirect_stderr(io.StringIO()):
            p15_requirement_refs = read_wp_case_requirement_refs(p15_tasks)
        ok(
            "Pair15",
            "read_wp_case_requirement_refs inherits the body-fence reader through the shared traversal",
            p15_requirement_refs.get(("WP01", "TC-002")) == ["FR-002"],
        )

        # --- Pair16 -- CommonMark fence variants: case-insensitive `yaml`/`yml`, info-string
        # attributes, tildes, longer fences, indented fences; an unclosed fence is reported. ---
        p16_tasks = root / "pair16" / "tasks"
        p16_tasks.mkdir(parents=True)
        p16_cases = [
            ("WP01-upper.md", "```YAML\nid: TC-101\nrequirement_refs: [FR-1]\n```\n"),
            ("WP02-attrs.md", "```yaml title=case\nid: TC-102\nrequirement_refs: [FR-1]\n```\n"),
            ("WP03-tilde.md", "~~~yml\nid: TC-103\nrequirement_refs: [FR-1]\n~~~\n"),
            ("WP04-long.md", "````yaml\nid: TC-104\nrequirement_refs: [FR-1]\nnotes: |\n  ```\n  a shorter fence does not close this block\n  ```\n````\n"),
            ("WP05-indent.md", "- a case:\n\n    ```yaml\n    id: TC-105\n    requirement_refs: [FR-1]\n    ```\n"),
            ("WP06-crlf.md", "```yaml\r\nid: TC-106\r\nrequirement_refs: [FR-1]\r\n```\r\n"),
        ]
        for name, body in p16_cases:
            (p16_tasks / name).write_text(f"---\nwork_package_id: {name[:4]}\n---\n\n{body}", newline="")
        p16_ids = sorted(read_wp_case_requirement_refs(p16_tasks))
        ok(
            "Pair16",
            "every CommonMark yaml fence variant yields its case",
            [case_id for _, case_id in p16_ids] == ["TC-101", "TC-102", "TC-103", "TC-104", "TC-105", "TC-106"],
        )
        p16_open = root / "pair16" / "open"
        p16_open.mkdir()
        (p16_open / "WP01-open.md").write_text(
            "---\nwork_package_id: WP01\n---\n\n```yaml\nid: TC-201\nrequirement_refs: [FR-1]\n"
        )
        with contextlib.redirect_stderr(io.StringIO()) as p16_stderr:
            read_wp_automation_refs(p16_open)
        ok(
            "Pair16",
            "a yaml fence that is never closed is reported on stderr",
            "never closed" in p16_stderr.getvalue(),
        )

        # --- WP02 review finding 3: malformed-but-present timestamp disclosure demo. Not a
        # `PairN` -- not one of FR-008's own eight named guards, and no FR in this WP mandates a
        # distinct guard for a malformed-but-present timestamp -- so deliberately excluded from
        # the derived pair count, but still exercised every self-test run, so this warning's own
        # presence is proven, not merely claimed by a code comment. --------------------------
        demo_dir = root / "malformed_demo"
        demo_dir.mkdir()
        demo_file = demo_dir / "malformed.xml"
        demo_file.write_text(
            '<testsuite name="s" timestamp="not-a-date">'
            '<testcase classname="c" name="t"/></testsuite>'
        )
        stderr_capture = io.StringIO()
        with contextlib.redirect_stderr(stderr_capture):
            resolve_result_timestamp(demo_file)
        warning_text = stderr_capture.getvalue()
        ok(
            "malformed-timestamp-disclosure",
            "resolve_result_timestamp prints a named warning naming the file, the malformed "
            "value, and the tier actually used, when a tier-1 timestamp is present but unparseable",
            str(demo_file) in warning_text and "not-a-date" in warning_text
            and "tier 3" in warning_text,
        )

    pair_count = _derive_self_test_pair_count()
    if failures:
        sys.stderr.write(f"qa_result_ingest --self-test: FAILED ({len(failures)} assertion(s))\n")
        for f in failures:
            sys.stderr.write(f"  - {f}\n")
        return 1
    sys.stdout.write(f"qa_result_ingest --self-test: all {pair_count} pairs passed\n")
    return 0


# --- qa-traceability.yaml writer (T012, FR-010) ---------------------------------------------------

# The qa-traceability/v1 schema itself is unchanged and owned by pack-structured-carriers-01M0ME39
# (assets/qa_carrier_lint.py:60-73's own TOP_KEYS/REQ_*/CASE_ALLOWED constants are the single
# source of truth). This module never imports that sibling asset (NFR-001 keeps every pack asset
# import-independent of every other) -- SCHEMA_V1 below is a literal re-statement of the same
# string, not a re-derivation, and this writer must never add, rename, or reshape a field beyond
# what those constants already allow.
SCHEMA_V1 = "qa-traceability/v1"


def build_testcase_source_index(
    testcase_sources: list[tuple[Path, ET.Element]],
) -> dict[str, Path]:
    """WP03: `classname::name` -> the first `--results` file it was enumerated from, in the
    identical first-occurrence-wins order `build_testcase_index` (T009) already uses for the same
    keys -- so `evidence.junit.file` always names the same file the matching `enumerated` entry
    that `build_testcase_index` produced actually came from. `build_testcase_index` itself only
    returns `classname::name -> element` (no file), which is all T009/T010's correlation guards
    need; this is a separate, additive index for the writer's own file-provenance need, not a
    signature change to WP02's owned function."""
    index: dict[str, Path] = {}
    for path, tc in testcase_sources:
        index.setdefault(testcase_key(tc), path)
    return index


def _relative_evidence_path(mission_dir: Path, results_path: Path) -> str:
    """Render `results_path` (as supplied via `--results`) relative to `--mission-dir`, the shape
    `qa_carrier_lint.py`'s own `_resolve_contained` (A-8) requires for every `evidence.*.file`
    value -- an absolute path, or one that resolves outside `--mission-dir`, fails that check
    loudly at validation time. Operational assumption, stated in this module's own docstring: every
    `--results` path supplied is expected to resolve inside `--mission-dir` (the normal case for a
    committed evidence file, per `implement`'s evidence-commit-discipline section). This function
    does not itself add a new guard for the violating case (no FR in this WP mandates one) -- it
    renders whatever relative form `os.path.relpath` produces either way, so a caller who violates
    the assumption gets a carrier that fails `qa_carrier_lint.py`'s own A-8 check loudly, never a
    silently wrong file reference accepted by this writer."""
    return os.path.relpath(str(results_path.resolve()), str(mission_dir.resolve()))


def _is_producer_case(case: dict) -> bool:
    """PMC-01 (pre-merge adversarial squad, severity 5): is `case` one this producer authored (and
    therefore this producer's to refresh from the current run), or a FOREIGN case (manual,
    exploratory, session-evidenced -- authored by a human or another tool) this writer must
    preserve untouched?

    The operator's ruling names the distinguishing signal as "carries an `automation_ref`" -- but
    `automation_ref` is a WP-frontmatter-only concept (`tasks/WP*.md`'s own field); it is never a
    literal key on a carrier `cases[]` entry (`assets/qa_carrier_lint.py`'s own `CASE_ALLOWED =
    {"id", "wp", "type", "evidence"}` has no such key, and no `automation_ref` string appears
    anywhere in that module). The schema-real equivalent is `evidence.junit` presence: this
    writer's OWN case-authoring path (`write_carrier` below) is, and has only ever been, JUnit-XML
    correlation -- every case this producer emits carries `evidence.junit.testcase` set to the
    literal `automation_ref` string that resolved it. A case with no `evidence.junit` key (a
    session-evidenced exploratory case like `TC-900`, or any future evidence shape this producer
    never emits) is therefore FOREIGN by construction, not by guesswork.
    """
    evidence = case.get("evidence")
    return isinstance(evidence, dict) and "junit" in evidence


def _load_existing_carrier(carrier_path: Path) -> dict | None:
    """PMC-01 fix: load a pre-existing `qa-traceability.yaml` (if any) before this run's write, so
    `write_carrier` can preserve any FOREIGN case it already holds instead of overwriting the file
    wholesale -- the operator's ruling, and FR-010's own "write (or **refresh**)" wording (never
    "write (or replace)").

    Three outcomes, kept distinct (never conflated -- conflating "missing" and "malformed" is
    exactly how PMC-01 could ship again one layer deeper):

    - **Missing** (no file at `carrier_path`): a legitimate first run. Returns `None` -- callers
      treat this as "no foreign state to preserve", never an error.
    - **Malformed** (present but not parseable YAML, or parses to something other than a mapping,
      or whose `cases`/`requirements` keys are present but are not lists of mappings): raises
      `IngestError` (the established exit-2 taxonomy) naming the file and the specific parse
      problem. Silently discarding an unparseable prior carrier here would reintroduce PMC-01's
      own defect one layer up -- a human's or another tool's foreign coverage, gone, with no guard
      ever having fired.
    - **Present and well-formed enough to read `cases`/`requirements` from**: returns the parsed
      `dict` as-is. This function does not otherwise validate carrier conformance -- that is
      `qa_carrier_lint.py`'s job, not this producer's; it only refuses to silently treat "I could
      not read this" as "there is nothing here".
    """
    if not carrier_path.exists():
        return None
    try:
        raw = carrier_path.read_text()
    except OSError as exc:
        raise IngestError(
            f"uninterpretable: existing carrier {carrier_path} could not be read: {exc}"
        ) from exc
    try:
        existing = _yaml().load(raw)
    except YAMLError as exc:
        raise IngestError(
            f"absent: existing carrier {carrier_path} is not parseable YAML ({exc}); refusing to "
            "silently discard it -- a malformed prior carrier may still hold foreign (manual/"
            "exploratory) coverage this tool must not destroy"
        ) from exc
    if not isinstance(existing, dict):
        raise IngestError(
            f"uninterpretable: existing carrier {carrier_path}'s top-level document is a "
            f"{type(existing).__name__}, not a mapping"
        )
    cases = existing.get("cases", [])
    if not isinstance(cases, list) or any(not isinstance(c, dict) for c in cases):
        raise IngestError(
            f"uninterpretable: existing carrier {carrier_path}'s 'cases' key is not a list of "
            "mappings"
        )
    requirements = existing.get("requirements", [])
    if not isinstance(requirements, list) or any(not isinstance(r, dict) for r in requirements):
        raise IngestError(
            f"uninterpretable: existing carrier {carrier_path}'s 'requirements' key is not a "
            "list of mappings"
        )
    return existing


def _foreign_carrier_state(existing: dict | None) -> tuple[list[dict], dict[str, list[str]]]:
    """PMC-01 fix: from an already-validated `_load_existing_carrier` result (or `None` on a
    first run), extract (a) every FOREIGN `cases[]` entry (`_is_producer_case` false), preserved
    verbatim, and (b) `requirement_id -> foreign case_ids` -- the subset of each existing
    requirement's `case_ids` that names a foreign case, in the existing carrier's own order. (b)
    is what lets a preserved foreign case keep counting toward its requirement's coverage: a
    requirement this run's own automation_ref correlation cannot see is still correctly reported
    covered when a foreign case the prior carrier already attributed to it is being preserved.
    """
    if existing is None:
        return [], {}
    foreign_cases: list[dict] = []
    foreign_ids: set[str] = set()
    for case in existing.get("cases", []):
        if not _is_producer_case(case):
            foreign_cases.append(case)
            foreign_ids.add(str(case.get("id", "")))
    foreign_case_ids_by_req: dict[str, list[str]] = {}
    for req in existing.get("requirements", []):
        req_id = str(req.get("id", ""))
        case_ids = req.get("case_ids")
        if not isinstance(case_ids, list):
            continue
        kept = [str(cid) for cid in case_ids if str(cid) in foreign_ids]
        if kept:
            foreign_case_ids_by_req[req_id] = kept
    return foreign_cases, foreign_case_ids_by_req


def write_carrier(
    mission_dir: Path,
    requirement_rows: list[tuple[str, list[str], str | None]],
    matched_cases: dict[tuple[str, str], str],
    testcase_source_file: dict[str, Path],
    generated_at: datetime,
    unmapped_testcases: list[str],
    *,
    json_mode: bool,
) -> Path:
    """T012: assemble the `qa-traceability/v1` document from WP02's T011 requirement-association
    output (`requirement_rows`, already carrying FR-007(a)'s rationale for every uncorrelated row)
    and T009's correlation map (`matched_cases`, `(wp_id, case_id) -> automation_ref`), and write
    it via `ruamel.yaml`'s `YAML(typ="safe")` -- the identical construction pattern
    `qa_carrier_lint.py` already uses for reading this same schema. Written (or refreshed in
    place) at `<mission-dir>/qa-traceability.yaml` directly -- never nested under a subdirectory.

    **PMC-01 fix -- preserve foreign cases, refresh only this producer's own (operator's ruling)**:
    before assembling anything, a pre-existing carrier at the output path (if any) is loaded via
    `_load_existing_carrier` and split via `_foreign_carrier_state` into its FOREIGN cases (no
    `evidence.junit` -- manual, exploratory, session-evidenced; see `_is_producer_case`) and the
    foreign `case_ids` each existing requirement already attributed. The final `cases[]` is
    `foreign_cases + produced_cases` (the operator's own formula), then sorted together; each
    requirement's `case_ids` is this run's own correlated ids UNION any preserved foreign ids the
    prior carrier already attributed to it -- so a preserved foreign case keeps counting toward
    its requirement's coverage, and a requirement a foreign case already covers is never
    (re)written with `associate_requirements_with_cases`'s "no automation_ref declared or resolved
    ... in the current run" rationale, since that claim would be false the moment real, still-
    preserved coverage exists. A case carrying `evidence.junit` (this producer's own) is always
    refreshed from the current run's correlation, never carried forward from the old file -- a
    stale `evidence.junit.file`/`testcase` pointer from a prior run is exactly what "refresh" (not
    "preserve") means here.

    **Ordered iteration and serialization throughout (FR-010/NFR-007)** -- no bare `set()` for any
    enumerable list here. `requirements[]` is sorted by requirement id, `cases[]` by `(case id, wp
    id)`, and each requirement's own `case_ids` is sorted too. The inputs handed to this function
    are already deterministically ordered (T009/T011 build `matched`/`_requirement_rows` from
    ordered `dict`s and `list`s, never a hash-ordered `set()`), so this explicit sort is
    belt-and-suspenders, not a correction of an existing defect -- it mirrors gate 19's own
    documented anti-pattern warning and makes the ordering guarantee visible at the one place that
    actually serializes to disk, rather than resting entirely on every upstream function's own
    discipline never lapsing. Note this determinism claim now presupposes a byte-identical prior
    carrier as well as byte-identical `--results`/`--tasks-dir`/`--strategy` inputs, since the
    prior carrier's own foreign-case content and order now feeds this run's output too.

    `generated_at` is deliberately the caller's own `--since` boundary (parsed once in `main()`),
    never wall-clock `datetime.now()` -- FR-010's own idempotency claim ("two runs against
    byte-identical inputs produce byte-identical output") is impossible to honour with a
    wall-clock timestamp, since two runs at different real times would then never agree. Using
    `--since` keeps `generated_at` a pure function of the declared inputs (same `--since` ==
    same `generated_at`), and is a reasonable proxy for "when this evidence run started" in its
    own right (FR-011 wires `--since` to `implement`'s own test-execution start time).

    Every `cases[]` entry this writer itself PRODUCES is `type: "automated"` (the only case-
    authoring path this module implements is JUnit-XML correlation) -- a `"manual"`/`session`-
    evidenced case added to the carrier by a human or another tool is preserved verbatim by this
    writer, never regenerated, renamed, or reshaped; it is the writer's job to carry it forward
    unchanged, not to touch it.

    **PMG-01 fix -- FR-007(b)'s non-`--json` path**: `unmapped_testcases` (always present, even
    when empty -- the same FR-007(b) contract `main()`'s `--json` record already honours) is
    threaded in here so this writer can be the one place BOTH of FR-007(b)'s two disclosure
    channels are produced. When `json_mode` is false (`--json` was not supplied, so nothing on
    stdout otherwise names the unclaimed testcases beyond a bare count), a `#`-comment header
    naming every unmapped testcase is written immediately above the YAML document itself -- the
    "instead" FR-007(b)'s own wording names. When `json_mode` is true, no header comment is
    written (the `--json` stdout record is FR-007(b)'s channel for that run), so the file stays
    the plain, comment-free document a `--json` consumer parsing it back with `_yaml().load()`
    (never expected to skip a header) can read either way -- ruamel's `YAML(typ="safe")` loader
    ignores `#`-comment lines regardless, so this choice is a legibility/on-disk-noise preference
    for the `--json` case, not a parseability requirement.
    """
    out_path = mission_dir / "qa-traceability.yaml"
    existing = _load_existing_carrier(out_path)
    foreign_cases, foreign_case_ids_by_req = _foreign_carrier_state(existing)

    requirements: list[dict] = []
    for req_id, case_ids, rationale in sorted(requirement_rows, key=lambda row: row[0]):
        merged_case_ids = sorted(set(case_ids) | set(foreign_case_ids_by_req.get(req_id, [])))
        entry: dict = {"id": req_id, "case_ids": merged_case_ids}
        # A requirement covered by a preserved foreign case is covered, full stop -- never
        # written with a rationale claiming no automation_ref was declared/resolved when real,
        # still-preserved coverage exists (PMC-01: "no fake red" is the mirror of QA_003's "no
        # fake green").
        if rationale is not None and not merged_case_ids:
            entry["rationale"] = rationale
        requirements.append(entry)

    produced_cases: list[dict] = []
    for (wp_id, case_id), ref in sorted(matched_cases.items(), key=lambda kv: (kv[0][1], kv[0][0])):
        source = testcase_source_file[ref]
        produced_cases.append({
            "id": case_id,
            "wp": wp_id,
            "type": "automated",
            "evidence": {
                "junit": {"file": _relative_evidence_path(mission_dir, source), "testcase": ref},
            },
        })

    cases = sorted(
        foreign_cases + produced_cases,
        key=lambda c: (str(c.get("id", "")), str(c.get("wp", ""))),
    )

    carrier = {
        "schema": SCHEMA_V1,
        "mission": mission_dir.resolve().name,
        "generated_at": generated_at.isoformat(),
        "requirements": requirements,
        "cases": cases,
        # Scope note: the operator's ruling (PMC-01) names `cases[]` preservation only --
        # `defects[]` is a separate top-level key no FR in this WP's scope touches or preserves;
        # left as `[]` unconditionally, exactly as before this fix, rather than silently widening
        # PMC-01's fix to a key nobody asked this writer to carry forward.
        "defects": [],
    }

    mission_dir.mkdir(parents=True, exist_ok=True)
    yaml = _yaml()
    yaml.default_flow_style = False
    with out_path.open("w") as fh:
        if not json_mode:
            # PMG-01: FR-007(b)'s non-`--json` disclosure channel -- always present, even when
            # `unmapped_testcases` is empty, mirroring the `--json` record's own "never omitted"
            # contract for this same field.
            fh.write(
                f"# qa_result_ingest FR-007(b): {len(unmapped_testcases)} unmapped testcase(s) "
                "(enumerated but claimed by no declared automation_ref):\n"
            )
            if unmapped_testcases:
                for key in unmapped_testcases:
                    fh.write(f"#   - {key}\n")
            else:
                fh.write("#   (none)\n")
        yaml.dump(carrier, fh)
    return out_path


# --- main() entry point -----------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    """Parses args, dispatches `--self-test` (T014) before anything else -- mirroring
    `qa_carrier_lint.py`'s own dispatch-early rationale verbatim: a mutation that breaks a guard
    inside `self_test()`'s own fixtures must surface as a real assertion-failure signal for a
    developer running `--self-test`, never be silently reclassified by the production-path
    OSError backstop below. Otherwise runs every guard through WP01's (T006) and WP02's
    (T007-T010) shared exit-code taxonomy, builds T011's two no-silent-exclusion data structures,
    and (T012) writes `qa-traceability.yaml` -- called only when every guard passed; a firing
    guard skips the write entirely (Decision 1: "the carrier was deliberately not written or
    refreshed").

    Guard order (deliberate, matches Decision 1 and the FR-017/FR-018 precedence spec.md states):
    `--since` parsed once at the top (FR-006) -> FR-019 spec.md presence -> FR-009 Requirement
    Register parse -> WP-frontmatter automation_ref read -> FR-001 missing-`--results`-when-
    declared -> per `--results` file: FR-016 dialect (inside `enumerate_testcases`) -> FR-005
    zero-testcases -> FR-006 staleness -> FR-018 duplicate automation_ref (checked first) ->
    FR-017 unresolvable automation_ref (checked second, excluding duplicate members) -> T012's
    writer.
    """
    args = build_parser().parse_args(argv)
    if args.self_test:
        return self_test()

    missing = [
        name for name, value in (
            ("--strategy", args.strategy), ("--tasks-dir", args.tasks_dir),
            ("--since", args.since), ("--mission-dir", args.mission_dir),
        ) if value is None
    ]
    if missing:
        print(
            f"qa_result_ingest: {', '.join(missing)} required unless --self-test is passed",
            file=sys.stderr,
        )
        return 2

    try:
        since = parse_since_boundary(args.since)
        check_spec_md_present(args.strategy)
        requirements = parse_requirement_register(args.strategy)
        automation_refs = read_wp_automation_refs(args.tasks_dir)
        check_results_supplied_when_declared(automation_refs, args.results)

        all_testcases: list[ET.Element] = []
        testcase_sources: list[tuple[Path, ET.Element]] = []
        for results_path in args.results:
            testcases, _asserted_count = enumerate_testcases(results_path)
            check_zero_testcases(results_path, testcases)
            check_staleness(results_path, since)
            all_testcases.extend(testcases)
            testcase_sources.extend((results_path, tc) for tc in testcases)

        enumerated = build_testcase_index(all_testcases)
        correlation = correlate_automation_refs(automation_refs, enumerated)
        check_duplicate_automation_refs(correlation)  # FR-018 first (see T010's module comment)
        check_unresolved_automation_ref(correlation)  # FR-017 second

        case_requirement_refs = read_wp_case_requirement_refs(args.tasks_dir)
        requirement_rows = associate_requirements_with_cases(
            requirements, correlation, case_requirement_refs,
        )
        unmapped_testcases = compute_unmapped_testcases(all_testcases, correlation)
        testcase_source_file = build_testcase_source_index(testcase_sources)

        carrier_path = write_carrier(
            args.mission_dir, requirement_rows, correlation.matched, testcase_source_file, since,
            unmapped_testcases, json_mode=args.json,
        )
    except (BlockedError, IngestError) as exc:
        _exit_code_taxonomy(exc)
    except OSError as exc:
        # Never an unhandled traceback for an I/O failure at the final write step (creating
        # --mission-dir, or writing qa-traceability.yaml itself) -- mirrors qa_carrier_lint.py's
        # own A-10 backstop. Not one of Decision 1's named guards (no FR in this WP mandates a new
        # taxonomy member for "the disk write itself failed"), so reported directly here, exit 2.
        print(f"qa_result_ingest: unexpected I/O error: {exc}", file=sys.stderr)
        return 2

    record = {
        "status": "ingested",
        "exit_code": 0,
        "carrier_path": str(carrier_path),
        "requirements_total": len(requirement_rows),
        "cases_total": len(correlation.matched),
        # FR-007(b): always present, never omitted or null, even when empty. PMI-02 fix: NOT
        # `sorted(...)` -- `compute_unmapped_testcases`'s own docstring documents its return
        # value as "in enumeration order" (the order each unclaimed testcase was first seen
        # across `--results`), and this is the one place that value reaches an actual consumer
        # (CLI/`--json` display); silently re-sorting it here made the code contradict its own
        # documented contract the moment anyone looked at what a user actually sees.
        "unmapped_testcases": unmapped_testcases,
    }
    if args.json:
        print(json.dumps(record, indent=2, sort_keys=True))
    else:
        print(
            f"qa_result_ingest: ingested requirements={record['requirements_total']} "
            f"cases={record['cases_total']} unmapped_testcases={len(unmapped_testcases)} "
            f"carrier={carrier_path}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
