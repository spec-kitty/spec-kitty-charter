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
#      whose checksums check and whose archive holds the contract.
# On the release pull request, where the top heading is already the release, it skips step 1 and rehearses that
# release as it stands. The clone has no remote, so its tags can never be pushed. service.yml runs it after verifyAll.
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
if [[ "$snapshot" != *-SNAPSHOT ]]; then
  # The release pull request: the top heading is the release, so rehearse it as it stands.
  version="$snapshot"
  tag="contract-packs-v$version"
  echo "== The top heading $version is a release: it is assembled as it stands"
else
version="${snapshot%-SNAPSHOT}"
tag="contract-packs-v$version"

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
git -c user.name=rehearsal -c user.email=rehearsal@invalid commit --quiet --all --message "rehearsal: release $version"
fi

echo "== The release $version is assembled"
# -f: a checkout of the tagged release commit carries the tag already, and the clone has no remote to push it to.
git tag -f "$tag" > /dev/null
scripts/release/packs-contract-release-assets.sh "$tag" "$work/assets"
for asset in packs-contract.tar.gz packs-contract.tar.gz.sha256 packs-contract.sha256; do
  if [[ ! -s "$work/assets/$asset" ]]; then
    echo "error: the release has no $asset" >&2
    exit 1
  fi
done
# The consumer's checks, as docs/guides/pin-the-packs-contract.md writes them.
(cd "$work/assets" && sha256sum --check --strict packs-contract.tar.gz.sha256)
mkdir "$work/consumer"
tar -xzf "$work/assets/packs-contract.tar.gz" -C "$work/consumer"
(cd "$work/consumer" && sha256sum --check --strict --quiet "$work/assets/packs-contract.sha256")
# The archive holds exactly the tracked files of contracts/packs at the tag, and the release record.
diff <({ git ls-files contracts/packs | sed 's|^contracts/packs/|packs-contract/|'; echo packs-contract/RELEASE.json; } \
  | LC_ALL=C sort) <(awk '{print $2}' "$work/assets/packs-contract.sha256" | LC_ALL=C sort)
grep -q "\"commit\": \"$(git rev-parse HEAD)\"" "$work/consumer/packs-contract/RELEASE.json"
grep -q "\"tag\": \"$tag\"" "$work/consumer/packs-contract/RELEASE.json"
grep -qx "## $version" "$work/consumer/packs-contract/CHANGELOG.md"
echo "Rehearsed $tag: refused while unreleased, assembled once released. Nothing was published."
