#!/usr/bin/env bash
# Rehearses a contract release without publishing anything, so the release path is exercised before release day.
#
# Usage: scripts/release/rehearse-contract-release.sh
#
# In a throwaway clone of HEAD with no remote, while info.version is a -SNAPSHOT, it:
#   1. tags the unreleased commit and expects the publishing gate (:contracts:checkReleaseTag) to refuse it (a
#      -SNAPSHOT is never published) and contract-release-assets.sh to leave no assets;
#   2. commits a synthetic release pull request (info.version and the top CHANGELOG heading without -SNAPSHOT) that
#      leaves a changelog fragment unassembled, rehearses it with this script and expects the publishing gate to
#      refuse its tag (ADR-0047);
#   3. as the release pull request does, assembles the fragments (:contracts:assembleContractChangelog) under the
#      -SNAPSHOT heading, then turns it into the release, and rehearses that commit with this script;
#   4. expects the publishing gate to refuse a malformed release candidate tag (<version>-rc-1: a pre-release is
#      letters, digits and dots), from the unreleased commit;
#   5. as a release candidate's pull request does (operator ruling R1), assembles the fragments and turns the -SNAPSHOT
#      into the next unused candidate <version>-rc.N (rc.1 when none is recorded), expects the gate to refuse the tag
#      of another candidate (<version>-rc.N+1), and rehearses that commit with this script;
#   6. tags that candidate, reopens <version>-SNAPSHOT above it (heading and info.version together) and expects the
#      breaking-change check (:contracts:checkContractCompatibility) to pass: a candidate is never its baseline.
# On the release pull request, where info.version is already the release or a release candidate, it rehearses that
# release as it stands (#571): it tags it locally and expects the four assets, with a checksum that matches the bundle.
# Steps 2, 3 and 5 run this branch, so every change rehearses the release pull request too.
# The clone has no remote, so its tags can never be pushed. service.yml runs it after verifyAll.
set -euo pipefail

self="$(realpath "$0")"
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

release() { # release <tag> <out-dir>: tags HEAD, assembles its assets and checks them
  # -f: a checkout of the tagged release commit carries the tag already, and the clone has no remote to push it to.
  git tag -f "$1" > /dev/null
  scripts/release/contract-release-assets.sh "$1" "$2"
  for asset in charter-openapi.yaml charter-openapi.yaml.sha256 charter-scenarios.tar.gz ActivationConfig.schema.json; do
    if [[ ! -s "$2/$asset" ]]; then
      echo "error: the release has no $asset" >&2
      exit 1
    fi
  done
  (cd "$2" && sha256sum --check --strict charter-openapi.yaml.sha256)
  # grep reads the whole listing: grep -q would exit at the first match and tar would die on SIGPIPE under pipefail.
  tar -tzf "$2/charter-scenarios.tar.gz" | grep -x 'scenarios/README.md' >/dev/null
  tar -tzf "$2/charter-scenarios.tar.gz" | grep -x 'scenarios/switch-gate.yaml' >/dev/null
}

if [[ "$snapshot" != *-SNAPSHOT ]]; then
  # The release pull request: info.version is the release, so rehearse it as it stands. The publishing gate refuses
  # it unless the top CHANGELOG heading names the same release and no changelog fragment is left.
  version="$snapshot"
  tag="contract-charter-v$version"
  echo "== The release $version is assembled as it stands"
  release "$tag" "$work/assets"
  echo "Rehearsed $tag as it stands: assembled. Nothing was published."
  exit 0
fi
if [[ -n "${REHEARSAL_OF_A_RELEASE:-}" ]]; then
  # Steps 2 and 3 run this script on a commit they released: a -SNAPSHOT here means their edit missed.
  echo "error: info.version is still $snapshot on the synthetic release commit" >&2
  exit 1
fi
version="${snapshot%-SNAPSHOT}"
tag="contract-charter-v$version"
start="$(git rev-parse HEAD)"

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
# This script, run on that commit, takes the release pull request's branch.
if (cd "$clone" && REHEARSAL_OF_A_RELEASE=1 "$self") 2>&1 | tee "$work/unassembled.log"; then
  echo "error: the release $version was assembled with a changelog fragment left" >&2
  exit 1
fi
if ! grep -q "not assembled into the CHANGELOG" "$work/unassembled.log"; then
  echo "error: :contracts:checkReleaseTag did not refuse the unassembled fragment" >&2
  exit 1
fi

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
# The release pull request's state: this script rehearses it as it stands.
(cd "$clone" && REHEARSAL_OF_A_RELEASE=1 "$self")

gate_refuses() { # gate_refuses <tag> <expected words>: the publishing gate alone refuses the tag at HEAD
  if ./gradlew --quiet :contracts:checkReleaseTag -PreleaseTag="$1" > "$work/gate.log" 2>&1; then
    echo "error: :contracts:checkReleaseTag let $1 through" >&2
    exit 1
  fi
  if ! grep -q "$2" "$work/gate.log"; then
    echo "error: :contracts:checkReleaseTag refused $1, but not for '$2':" >&2
    cat "$work/gate.log" >&2
    exit 1
  fi
  echo "refused, as expected: $1 ($2)"
}

# The next unused release candidate number of <version>: 1 after none, else one above the highest the CHANGELOG lists,
# so the rehearsal still works once a real candidate (<version>-rc.1) is recorded below the reopened -SNAPSHOT.
next_rc() { # next_rc <changelog>
  local n
  n="$(sed -n "s/^## ${version//./\\.}-rc\.\([0-9][0-9]*\)\$/\1/p" "$1" | sort -n | tail -n 1)"
  echo $(( ${n:-0} + 1 ))
}
rc_n="$(next_rc "$changelog")"
rc="$version-rc.$rc_n"
other="$version-rc.$(( rc_n + 1 ))"
echo "== 4. A malformed release candidate is refused"
git checkout --quiet --detach "$start"
gate_refuses "contract-charter-v$version-rc-1" "not letters, digits and dots"

echo "== 5. A synthetic release candidate $rc is assembled"
./gradlew --quiet :contracts:assembleContractChangelog
sed -i "0,/^  version: $snapshot\$/s//  version: $rc/" "$openapi"
sed -i "0,/^## $snapshot\$/s//## $rc/" "$changelog"
git add --all contracts/charter
commit "rehearsal: release candidate $rc"
gate_refuses "contract-charter-v$other" "names $other, but the contract at that tag says its info.version is $rc"
(cd "$clone" && REHEARSAL_OF_A_RELEASE=1 "$self")

echo "== 6. After the candidate, $snapshot is open again and compared with no candidate"
git tag "contract-charter-v$rc"
sed -i "0,/^  version: $rc\$/s//  version: $snapshot/" "$openapi"
sed -i "0,/^## $rc\$/s//## $snapshot\n\nWork toward the next candidate or the final $version.\n\n## $rc/" "$changelog"
commit "rehearsal: $snapshot after $rc"
./gradlew --quiet :contracts:checkContractCompatibility > "$work/after-rc.log" 2>&1 || {
  cat "$work/after-rc.log" >&2
  echo "error: the breaking-change check refused $snapshot after $rc" >&2
  exit 1
}
echo "Rehearsed $tag and $rc: refused while unreleased, unassembled or malformed, assembled once released, and"
echo "$snapshot reopens after the candidate. Nothing was published."
