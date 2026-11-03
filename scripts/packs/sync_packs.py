#!/usr/bin/env python3
"""Sync packs/internal and packs/qa from the pinned spec-kitty-open-packs commit, and verify them.

Run from the repository root as a bare ``python3 scripts/packs/sync_packs.py`` (standard library only), or through
``./gradlew syncPacks``, which wraps it. One implementation serves developers and CI.

The pin is ``openPacksRef`` in gradle.properties. The digests are in packs/open-packs.lock.yaml, whose header
defines the algorithm. The source is read from a git object database at the pinned commit with ``git archive``,
never from a working tree, so a dirty checkout cannot leak in. Source resolution, in order:

1. ``--open-packs-dir`` (Gradle's ``-PopenPacksDir``; relative to the repository root);
2. the sibling ``../spec-kitty-open-packs``;
3. otherwise an error that says what to do.

Fails closed: a pin that is not a commit of the source, a lockfile for another commit, or a digest that differs
from the lockfile exits 1 and leaves packs/internal and packs/qa as they were.

    python3 scripts/packs/sync_packs.py                 sync, then verify
    python3 scripts/packs/sync_packs.py --check         verify the synced trees against the lockfile only
    python3 scripts/packs/sync_packs.py --update-lock   sync, then rewrite the lockfile (a pin bump)
"""

from __future__ import annotations

import argparse
import hashlib
import io
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
SUBTREES = ("packs/internal", "packs/qa")
LOCK = REPO / "packs" / "open-packs.lock.yaml"
ALGORITHM = "tree-sha256-v1"
SIBLING = "../spec-kitty-open-packs"
SHA = re.compile(r"[0-9a-f]{40}")
DIGEST = re.compile(r"[0-9a-f]{64}")

LOCK_HEADER = f"""\
# Digests of the packs synced from spec-kitty-open-packs at the commit pinned by openPacksRef in gradle.properties.
# Written by `./gradlew syncPacks -PupdatePacksLock=true` (scripts/packs/sync_packs.py); never edit by hand.
# A pin bump changes openPacksRef, this file's commit and these digests in one commit.
#
# algorithm {ALGORITHM}: sha256 over the regular files of the tree, in ascending order of their relative path
# (POSIX separators, compared as UTF-8 bytes). Per file, the digest is fed: the relative path as UTF-8, a NUL byte,
# the decimal byte length of the file, a NUL byte, then the file's bytes. File modes, timestamps, empty
# directories and line endings are not part of it (the bytes are taken as stored). A symbolic link or other
# non-regular entry in a tree is refused.
"""


class SyncError(Exception):
    """A refusal; the message says what to do."""


def pin() -> str:
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("openPacksRef="):
            ref = line.split("=", 1)[1].strip()
            if not SHA.fullmatch(ref):
                raise SyncError(f"openPacksRef in gradle.properties must be 40 lowercase hex characters, but is '{ref}'")
            return ref
    raise SyncError("gradle.properties has no openPacksRef")


def tree_digest(root: Path) -> str:
    """The ``tree-sha256-v1`` digest of the directory ``root``."""
    files = []
    for path in root.rglob("*"):
        if path.is_symlink() or (not path.is_dir() and not path.is_file()):
            raise SyncError(f"{path} is a symbolic link or special file; a synced tree holds regular files only")
        if path.is_file():
            files.append((path.relative_to(root).as_posix().encode("utf-8"), path))
    digest = hashlib.sha256()
    for name, path in sorted(files):
        data = path.read_bytes()
        digest.update(name + b"\0" + str(len(data)).encode("ascii") + b"\0")
        digest.update(data)
    return digest.hexdigest()


def read_lock() -> tuple[str, dict[str, str]]:
    if not LOCK.is_file():
        raise SyncError(
            f"{LOCK.relative_to(REPO)} is missing; create it with: python3 scripts/packs/sync_packs.py --update-lock"
        )
    commit = ""
    digests: dict[str, str] = {}
    in_trees = False
    for line in LOCK.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.startswith("commit:"):
            commit = line.split(":", 1)[1].strip()
            in_trees = False
        elif line.startswith("algorithm:"):
            in_trees = False
            if line.split(":", 1)[1].strip() != ALGORITHM:
                raise SyncError(f"the lockfile's algorithm is not {ALGORITHM}")
        elif line.rstrip() == "trees:":
            in_trees = True
        elif in_trees and line.startswith("  "):
            name, _, value = line.strip().partition(":")
            digests[name.strip()] = value.strip()
        else:
            raise SyncError(f"unexpected line in {LOCK.relative_to(REPO)}: {line!r}")
    for subtree in SUBTREES:
        if not DIGEST.fullmatch(digests.get(subtree, "")):
            raise SyncError(f"{LOCK.relative_to(REPO)} has no valid digest for {subtree}")
    if not SHA.fullmatch(commit):
        raise SyncError(f"{LOCK.relative_to(REPO)} has no valid commit")
    return commit, digests


def write_lock(commit: str, digests: dict[str, str]) -> None:
    body = f"{LOCK_HEADER}algorithm: {ALGORITHM}\ncommit: {commit}\ntrees:\n"
    body += "".join(f"  {subtree}: {digests[subtree]}\n" for subtree in SUBTREES)
    LOCK.write_text(body, encoding="utf-8")


def git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, check=False)
    if result.returncode != 0:
        raise SyncError(f"git {' '.join(args)} failed in {repo}: {result.stderr.decode('utf-8', 'replace').strip()}")
    return result.stdout


def resolve_source(explicit: str | None) -> Path:
    if explicit is not None:
        if not explicit.strip():
            raise SyncError("-PopenPacksDir is empty; pass a checkout path, or leave it out for " + SIBLING)
        candidate = (REPO / explicit).resolve()
        if not candidate.exists():
            raise SyncError(f"no spec-kitty-open-packs checkout at {candidate} (from -PopenPacksDir / --open-packs-dir)")
        return candidate
    candidate = (REPO / SIBLING).resolve()
    if candidate.exists():
        return candidate
    raise SyncError(
        f"no spec-kitty-open-packs checkout: {candidate} does not exist. Clone it beside this repository "
        f"(git clone https://github.com/spec-kitty/spec-kitty-open-packs.git {candidate}) or pass "
        "-PopenPacksDir=<checkout> (--open-packs-dir <checkout>): an absolute path, or one relative to the "
        "repository root."
    )


def extract(source: Path, ref: str, subtree: str, into: Path) -> Path:
    """Extract ``subtree`` as committed at ``ref`` into ``into`` and return the extracted directory."""
    try:
        git(source, "rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}")
    except SyncError:
        raise SyncError(f"{source} has no commit {ref}. Fix: git -C {source} fetch origin {ref}") from None
    archive = git(source, "archive", "--format=tar", ref, subtree)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        for member in tar.getmembers():
            if member.issym() or member.islnk() or not (member.isfile() or member.isdir()):
                raise SyncError(f"{subtree} at {ref} holds {member.name}, which is not a regular file")
        tar.extractall(into, filter="data")
    return into / subtree


def verify(subtree: str, root: Path, expected: str, what: str) -> None:
    actual = tree_digest(root)
    if actual != expected:
        raise SyncError(
            f"digest mismatch for {subtree} ({what}): the lockfile says {expected}, the tree is {actual}. "
            "A synced tree is never edited by hand; re-run the sync. After a deliberate pin bump, run it with "
            "--update-lock (./gradlew syncPacks -PupdatePacksLock=true) and commit the lockfile with the new "
            "openPacksRef."
        )


def run(args: argparse.Namespace) -> None:
    ref = pin()
    if args.check:
        commit, digests = read_lock()
        if commit != ref:
            raise SyncError(f"the lockfile is for {commit} but openPacksRef is {ref}; run the sync with --update-lock")
        for subtree in SUBTREES:
            target = REPO / subtree
            if not target.is_dir():
                raise SyncError(f"{subtree} is not synced. Run: ./gradlew syncPacks")
            verify(subtree, target, digests[subtree], "synced copy")
        print(f"packs verified against the lockfile at {ref}")
        return

    source = resolve_source(args.open_packs_dir)
    with tempfile.TemporaryDirectory(prefix="sync-packs-") as tmp:
        staged = {subtree: extract(source, ref, subtree, Path(tmp)) for subtree in SUBTREES}
        if args.update_lock:
            digests = {subtree: tree_digest(staged[subtree]) for subtree in SUBTREES}
        else:
            commit, digests = read_lock()
            if commit != ref:
                raise SyncError(
                    f"the lockfile is for {commit} but openPacksRef is {ref}; run the sync with --update-lock"
                )
            for subtree in SUBTREES:
                verify(subtree, staged[subtree], digests[subtree], f"{source} at {ref}")
        for subtree in SUBTREES:
            target = REPO / subtree
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(staged[subtree], target)
    for subtree in SUBTREES:
        verify(subtree, REPO / subtree, digests[subtree], "synced copy")
    if args.update_lock:
        write_lock(ref, digests)
    print(f"synced and verified {', '.join(SUBTREES)} at {ref} from {source}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--open-packs-dir", help="the open-packs checkout (default: the sibling ../spec-kitty-open-packs)")
    parser.add_argument("--check", action="store_true", help="verify the synced trees against the lockfile; do not sync")
    parser.add_argument("--update-lock", action="store_true", help="rewrite the lockfile from the pinned commit (a pin bump)")
    args = parser.parse_args()
    if args.check and args.update_lock:
        parser.error("--check and --update-lock exclude each other")
    try:
        run(args)
    except SyncError as error:
        print(f"sync_packs: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
