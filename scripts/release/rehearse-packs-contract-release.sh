#!/usr/bin/env bash
# Rehearses a packs contract release (#559) without publishing anything, so the release path is exercised before
# release day.
#
# Usage: scripts/release/rehearse-packs-contract-release.sh
#
# In a throwaway clone of HEAD with no remote, while the top heading of contracts/packs/CHANGELOG.md is a -SNAPSHOT, it:
#   1. tags the unreleased commit and expects the publishing gate (:contracts:checkPacksReleaseTag) to refuse it (a
#      -SNAPSHOT is never published) and packs-contract-release-assets.sh to leave no assets;
#   2. commits a synthetic release (the top heading without -SNAPSHOT), tags it locally and expects the three assets,
#      whose checksums check and whose archive holds the contract;
#   3. expects the publishing gate to refuse a malformed release candidate tag (<version>-rc-1: a pre-release is
#      letters, digits and dots), from the unreleased commit;
#   4. commits the next unused release candidate <version>-rc.N (rc.1 when none is recorded; operator ruling R1) from
#      the unreleased commit, and expects its three assets as in step 2;
#   5. at the tagged candidate, expects the packs CHANGELOG check (:contracts:checkPacksChangelog) to pass after a
#      commit outside the packs contract, and to refuse a commit that changes a packs contract file (#663);
#   6. reopens <version>-SNAPSHOT above the candidate and expects the packs CHANGELOG check to pass, and a later version
#      above the candidate, before its final release, to be refused.
# On the release pull request, where the top heading is already the release or a release candidate, it skips the
# other steps and rehearses that release as it stands. The clone has no remote, so its tags can never be pushed. service.yml runs it after verifyAll.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
clone="$work/clone"

git clone --quiet --no-local "$root" "$clone"
cd "$clone"
git checkout --quiet --detach "$(git -C "$root" rev-parse HEAD)"
git remote remove origin

changelog=contracts/packs/CHANGELOG.md
# sed quits after the first match: piping into `head -n 1` would close the pipe early and kill sed on SIGPIPE under pipefail.
snapshot="$(sed -n '/^## /{s/^## *\([^ ]*\).*$/\1/p;q}' "$changelog")"

commit() {
  git -c user.name=rehearsal -c user.email=rehearsal@invalid commit --quiet --all --message "$1"
}

assemble() { # assemble <version>: tags HEAD contract-packs-v<version>, assembles its assets and checks them
  local version="$1" tag="contract-packs-v$1" out="$work/assets-$1"
  echo "== The release $version is assembled"
  # -f: a checkout of the tagged release commit carries the tag already, and the clone has no remote to push it to.
  git tag -f "$tag" > /dev/null
  scripts/release/packs-contract-release-assets.sh "$tag" "$out"
  for asset in packs-contract.tar.gz packs-contract.tar.gz.sha256 packs-contract.sha256; do
    if [[ ! -s "$out/$asset" ]]; then
      echo "error: the release has no $asset" >&2
      exit 1
    fi
  done
  # The consumer's checks, as docs/guides/pin-the-packs-contract.md writes them.
  (cd "$out" && sha256sum --check --strict packs-contract.tar.gz.sha256)
  rm -rf "$work/consumer"
  mkdir "$work/consumer"
  tar -xzf "$out/packs-contract.tar.gz" -C "$work/consumer"
  (cd "$work/consumer" && sha256sum --check --strict --quiet "$out/packs-contract.sha256")
  # The archive holds exactly the tracked files of contracts/packs at the tag, and the release record.
  diff <({ git ls-files contracts/packs | sed 's|^contracts/packs/|packs-contract/|'; echo packs-contract/RELEASE.json; } \
    | LC_ALL=C sort) <(awk '{print $2}' "$out/packs-contract.sha256" | LC_ALL=C sort)
  grep -q "\"commit\": \"$(git rev-parse HEAD)\"" "$work/consumer/packs-contract/RELEASE.json"
  grep -q "\"tag\": \"$tag\"" "$work/consumer/packs-contract/RELEASE.json"
  grep -qx "## $version" "$work/consumer/packs-contract/CHANGELOG.md"
}

refused_by() { # refused_by <task> <expected words> [<gradle args>...]: the gradle task fails for that reason
  local task="$1" words="$2"
  shift 2
  if ./gradlew --quiet "$task" "$@" > "$work/gate.log" 2>&1; then
    echo "error: $task passed $*" >&2
    exit 1
  fi
  if ! grep -q "$words" "$work/gate.log"; then
    echo "error: $task failed, but not for '$words':" >&2
    cat "$work/gate.log" >&2
    exit 1
  fi
  echo "refused, as expected: $task $* ($words)"
}

if [[ "$snapshot" != *-SNAPSHOT ]]; then
  # The release pull request: the top heading is the release or a release candidate, so rehearse it as it stands.
  echo "== The top heading $snapshot is a release: it is assembled as it stands"
  assemble "$snapshot"
  echo "Rehearsed contract-packs-v$snapshot as it stands. Nothing was published."
  exit 0
fi
version="${snapshot%-SNAPSHOT}"
tag="contract-packs-v$version"
start="$(git rev-parse HEAD)"

echo "== 1. The unreleased $snapshot is refused"
git tag "$tag"
if scripts/release/packs-contract-release-assets.sh "$tag" "$work/refused" 2>&1 | tee "$work/refused.log"; then
  echo "error: the assets of $snapshot were assembled under $tag" >&2
  exit 1
fi
if ! grep -q "$tag names $version, but the top heading of $changelog at that commit is $snapshot" "$work/refused.log"; then
  echo "error: :contracts:checkPacksReleaseTag did not refuse $tag" >&2
  exit 1
fi
if [[ -e "$work/refused" ]] && [[ -n "$(ls -A "$work/refused")" ]]; then
  echo "error: a refused release left assets in $work/refused" >&2
  exit 1
fi
git tag -d "$tag" > /dev/null

echo "== 2. A synthetic release $version is committed"
sed -i "0,/^## $snapshot\$/s//## $version/" "$changelog"
commit "rehearsal: release $version"
assemble "$version"

echo "== 3. A malformed release candidate is refused"
git checkout --quiet --detach "$start"
refused_by :contracts:checkPacksReleaseTag "not letters, digits and dots" -PreleaseTag="contract-packs-v$version-rc-1"

# The next unused release candidate number of <version>: 1 after none, else one above the highest the CHANGELOG lists,
# so the rehearsal still works once a real candidate (<version>-rc.1) is recorded below the reopened -SNAPSHOT.
next_rc() { # next_rc <changelog>
  local n
  n="$(sed -n "s/^## ${version//./\\.}-rc\.\([0-9][0-9]*\)\$/\1/p" "$1" | sort -n | tail -n 1)"
  echo $(( ${n:-0} + 1 ))
}
rc="$version-rc.$(next_rc "$changelog")"
echo "== 4. A synthetic release candidate $rc is committed"
sed -i "0,/^## $snapshot\$/s//## $rc/" "$changelog"
commit "rehearsal: release candidate $rc"
assemble "$rc"

echo "== 5. Under the tagged candidate $rc, the packs contract's files are those of the release (#663)"
echo "rehearsal" >> README.md
commit "rehearsal: a change outside the packs contract under $rc"
./gradlew --quiet :contracts:checkPacksChangelog
echo "# rehearsal" >> contracts/packs/file-kinds.yaml
commit "rehearsal: a packs contract change under $rc"
refused_by :contracts:checkPacksChangelog "differ from that release"
git reset --quiet --hard "contract-packs-v$rc"

echo "== 6. After the candidate, $snapshot is open again"
sed -i "0,/^## $rc\$/s//## $snapshot\n\nWork toward the next candidate or the final $version.\n\n## $rc/" "$changelog"
./gradlew --quiet :contracts:checkPacksChangelog
next="${version%.*}.$(( ${version##*.} + 1 ))-SNAPSHOT"
sed -i "0,/^## $snapshot\$/s//## $next/" "$changelog"
refused_by :contracts:checkPacksChangelog "follows the release candidate $rc"
git checkout --quiet -- "$changelog"
echo "Rehearsed $tag and $rc: refused while unreleased or malformed, assembled once released, the packs contract's"
echo "files frozen under the tagged candidate, and $snapshot reopens after it. Nothing was published."
