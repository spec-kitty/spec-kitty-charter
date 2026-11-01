#!/usr/bin/env bash
# Rehearses a contract release without publishing anything, so the release path is exercised before release day.
#
# Usage: scripts/release/rehearse-contract-release.sh
#
# In a throwaway clone of HEAD with no remote, it:
#   1. tags the unreleased commit and expects the publishing gate (:contracts:checkReleaseTag) to refuse it (a
#      -SNAPSHOT is never published) and contract-release-assets.sh to leave no assets;
#   2. commits a synthetic release (info.version and the top CHANGELOG heading without -SNAPSHOT) that leaves a
#      changelog fragment unassembled, and expects the publishing gate to refuse its tag (ADR-0047);
#   3. as the release pull request does, assembles the fragments (:contracts:assembleContractChangelog) under the
#      -SNAPSHOT heading, then turns it into the release, tags it locally and expects the assets, with a checksum
#      that matches the bundle.
# The clone has no remote, so its tags can never be pushed. service.yml runs it after verifyAll.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
clone="$work/clone"

git clone --quiet --no-local "$root" "$clone"
cd "$clone"
git checkout --quiet --detach "$(git -C "$root" rev-parse HEAD)"
git remote remove origin

openapi=contracts/charter/openapi.yaml
changelog=contracts/charter/CHANGELOG.md
# sed quits after the first match: piping into `head -n 1` would close the pipe early and kill sed on SIGPIPE under pipefail.
snapshot="$(sed -n '/^  version:/{s/^  version: *\(.*\)$/\1/p;q}' "$openapi")"
if [[ "$snapshot" != *-SNAPSHOT ]]; then
  echo "error: info.version is $snapshot, not a -SNAPSHOT: rehearse on a commit before the release" >&2
  exit 1
fi
version="${snapshot%-SNAPSHOT}"
tag="contract-charter-v$version"

echo "== 1. The unreleased $snapshot is refused"
git tag "$tag"
if scripts/release/contract-release-assets.sh "$tag" "$work/refused" 2>&1 | tee "$work/refused.log"; then
  echo "error: the assets of $snapshot were assembled under $tag" >&2
  exit 1
fi
# The publishing gate refuses it, before anything is built; the contract checks would refuse it too, later.
if ! grep -q "The release tag $tag is refused" "$work/refused.log"; then
  echo "error: :contracts:checkReleaseTag did not refuse $tag" >&2
  exit 1
fi
if [[ -e "$work/refused" ]] && [[ -n "$(ls -A "$work/refused")" ]]; then
  echo "error: a refused release left assets in $work/refused" >&2
  exit 1
fi
git tag -d "$tag" > /dev/null

commit() {
  git -c user.name=rehearsal -c user.email=rehearsal@invalid commit --quiet --all --message "$1"
}

echo "== 2. A release that leaves a changelog fragment unassembled is refused"
sed -i "0,/^  version: $snapshot\$/s//  version: $version/" "$openapi"
sed -i "0,/^## $snapshot\$/s//## $version/" "$changelog"
probe=contracts/charter/changes/2000-01-01-rehearsal-probe.md
printf '### Added\n\n- A fragment the rehearsal plants, to prove the gate refuses an unassembled release.\n' > "$probe"
git add "$probe"
commit "rehearsal: release $version with a fragment left"
git tag "$tag"
if scripts/release/contract-release-assets.sh "$tag" "$work/unassembled" 2>&1 | tee "$work/unassembled.log"; then
  echo "error: the release $version was assembled with a changelog fragment left" >&2
  exit 1
fi
if ! grep -q "not assembled into the CHANGELOG" "$work/unassembled.log"; then
  echo "error: :contracts:checkReleaseTag did not refuse the unassembled fragment" >&2
  exit 1
fi
git tag -d "$tag" > /dev/null

echo "== 3. A synthetic release $version is assembled"
# The documented order: assemble under the -SNAPSHOT heading, then release it. Assembly refuses a released section.
sed -i "0,/^## $version\$/s//## $snapshot/" "$changelog"
./gradlew --quiet :contracts:assembleContractChangelog
sed -i "0,/^## $snapshot\$/s//## $version/" "$changelog"
if ! grep -q "A fragment the rehearsal plants" "$changelog" || [[ -e "$probe" ]]; then
  echo "error: :contracts:assembleContractChangelog did not move the fragment into $changelog" >&2
  exit 1
fi
git add --all contracts/charter
commit "rehearsal: release $version"
git tag "$tag"
scripts/release/contract-release-assets.sh "$tag" "$work/assets"
for asset in charter-openapi.yaml charter-openapi.yaml.sha256 charter-scenarios.tar.gz ActivationConfig.schema.json; do
  if [[ ! -s "$work/assets/$asset" ]]; then
    echo "error: the release has no $asset" >&2
    exit 1
  fi
done
(cd "$work/assets" && sha256sum --check --strict charter-openapi.yaml.sha256)
# grep reads the whole listing: grep -q would exit at the first match and tar would die on SIGPIPE under pipefail.
tar -tzf "$work/assets/charter-scenarios.tar.gz" | grep -x 'scenarios/README.md' >/dev/null
echo "Rehearsed $tag: refused while unreleased or unassembled, assembled once released. Nothing was published."
