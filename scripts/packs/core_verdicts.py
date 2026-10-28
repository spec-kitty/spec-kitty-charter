#!/usr/bin/env python
"""Record core's own verdict on every planted pack, as the raw output of its CLI.

Run with the venv core pins, isolated, from the repository root (not part of the build, never gating):

    ~/.venv-sk/bin/python -I scripts/packs/core_verdicts.py

It writes service/packs/src/test/resources/planted/core-verdicts.json, one record per directory of
planted/. For each plant that has files it copies the pack into a scratch directory, creates the symlinks the plant's
expected.json asks for, and runs

    spec-kitty charter pack validate --json <pack>

with a scratch HOME, from a scratch working directory (a bare-path validation needs no project, research R5). A record
holds the core commit, the command, the exit code and the raw {ok, errors, advisories} with every path made
pack-relative. A plant with no files is proven by a named test instead and has a record that says so.

The record says nothing about agreement. PlantedFilesTest derives agreement from the raw output through the FR-016
mapping of contracts/pack-finding-codes.md and compares it with the service's own findings. The only judgement kept
here is REGISTER_IDS: for a plant whose rule is a recorded divergence, the id of its entry in
docs/conformance/divergences.yaml. A proposed id that the register does not list yet is named in PlantedFilesTest.
"""

import importlib.metadata
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLANTED = ROOT / "service/packs/src/test/resources/planted"
# The core commit the plants were judged against (the commit the divergence register cites).
CORE_COMMIT = "9554e7b26afe7a61519f7744d0147346ab36f197"
OUT = PLANTED / "core-verdicts.json"

# plant -> id of the divergence that explains why core's verdict and the service's differ. The test fails a plant
# that has an id and agrees (stale), and a plant that differs and has none.
REGISTER_IDS: dict[str, str] = {
    "c2-agent-nested-unknown-key": "DIV-013",
    "c2-charter-nested-unknown-key": "DIV-013",
    "c3-fragment-bare-local-id-declared": "DIV-007",
    "r15-glossary-urn-in-a-shard": "DIV-012",
    "r15-glossary-urn-as-an-edge-endpoint": "DIV-012",
    "c5-fragment-kind-mission-steps": "DIV-011",
    "c5-fragment-kind-mission-type": "DIV-011",
    "c5-fragment-kind-actions-is-accepted": "DIV-011",
    "c6-fragment-header-pack-name": "DIV-008",
    "c7-fragment-relation-extends": "DIV-015",
    "c8-shard-without-generated-by-is-accepted": "DIV-009",
    "c9-pack-version-missing-at-the-org-level": "DIV-004",
    "c10-agent-role-uppercase": "DIV-010",
    "c10-agent-scalar-role": "DIV-010",
    "c10-agent-quoted-integer": "DIV-010",
    "c10-org-charter-schema-version-quoted": "DIV-010",
    "c12-edge-impacts-valid": "DIV-018",
    "c12-impacts-edge-without-impacts": "DIV-018",
    "c16-shard-edge-provenance": "DIV-019",
    # Proposed, not in the register yet; PlantedFilesTest names them (PROPOSED) until the orchestrator registers them.
    "c5-fragment-node-id-unsafe": "DIV-020",
    "charter-invalid": "DIV-021",
    "charter-cli-timeout-not-a-number": "DIV-021",
    "org-fragment-missing": "DIV-022",
    "c3-root-shard-urn-malformed": "DIV-023",
    "control-no-pack-level-on-a-bare-path": "DIV-024",
    "artifact-utf16-with-a-byte-order-mark": "DIV-025",
}


class VerdictError(Exception):
    pass


def core_commit() -> str:
    """The commit of the core checkout the installed spec-kitty-cli was built from; it must be the pinned one."""
    dist = importlib.metadata.distribution("spec-kitty-cli")
    raw = dist.read_text("direct_url.json")
    if not raw:
        raise VerdictError("the installed spec-kitty-cli has no direct_url.json")
    url = json.loads(raw)["url"]
    if not url.startswith("file://"):
        raise VerdictError(f"the installed core comes from {url}, not a checkout")
    checkout = url.removeprefix("file://")
    commit = subprocess.run(
        ["git", "-C", checkout, "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()
    if commit != CORE_COMMIT:
        raise VerdictError(f"the installed core is at {commit}, not the pinned CORE_COMMIT {CORE_COMMIT}")
    return commit


def normalise(value: object, pack: str) -> object:
    """Make every path pack-relative: the pack directory becomes '' in a file and '<pack>' in a message."""
    if isinstance(value, str):
        return value.replace(pack + "/", "").replace(pack, "<pack>")
    if isinstance(value, list):
        return [normalise(v, pack) for v in value]
    if isinstance(value, dict):
        return {k: normalise(v, pack) for k, v in value.items()}
    return value


def run_core(plant: Path, scratch: Path) -> tuple[list[str], int, dict]:
    pack = scratch / "pack"
    shutil.copytree(plant / "files", pack, symlinks=True)
    expected = json.loads((plant / "expected.json").read_text(encoding="utf-8"))
    for link in expected.get("symlinks", []):
        os.symlink(link["target"], pack / link["link"])
    home = scratch / "home"
    home.mkdir()
    work = scratch / "work"
    work.mkdir()
    executable = Path(sys.executable).parent / "spec-kitty"
    env = {"HOME": str(home), "PATH": f"{executable.parent}:{os.environ.get('PATH', '')}", "NO_COLOR": "1"}
    command = [str(executable), "charter", "pack", "validate", "--json", str(pack)]
    done = subprocess.run(command, capture_output=True, text=True, cwd=work, env=env, check=False)
    try:
        raw = json.loads(done.stdout)
    except json.JSONDecodeError as exc:
        raise VerdictError(f"{plant.name}: core did not answer JSON ({exc}): {done.stdout[:200]!r} {done.stderr[-300:]!r}")
    raw = normalise(raw, str(pack.resolve()))
    raw = normalise(raw, str(pack))
    return ["spec-kitty", "charter", "pack", "validate", "--json", "<pack>"], done.returncode, raw


def main() -> int:
    commit = core_commit()
    version = importlib.metadata.version("spec-kitty-cli")
    records = []
    for plant in sorted(p for p in PLANTED.iterdir() if p.is_dir()):
        expected = json.loads((plant / "expected.json").read_text(encoding="utf-8"))
        if "proven_by" in expected:
            records.append({"plant": plant.name, "core": None, "proven_by": expected["proven_by"]})
            continue
        with tempfile.TemporaryDirectory(prefix="core-verdicts-") as tmp:
            command, code, raw = run_core(plant, Path(tmp))
        record = {
            "plant": plant.name,
            "core": {"sha": commit, "version": version, "command": command, "exit_code": code, "raw": raw},
        }
        if plant.name in REGISTER_IDS:
            record["register_id"] = REGISTER_IDS[plant.name]
        records.append(record)
    OUT.write_text(json.dumps(records, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"wrote {len(records)} records to {OUT.relative_to(ROOT)} (core {commit[:8]}, spec-kitty-cli {version})")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except VerdictError as error:
        print(f"core_verdicts: {error}", file=sys.stderr)
        sys.exit(2)
