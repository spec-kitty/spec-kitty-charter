#!/usr/bin/env bash
# Rehearses a service release (ADR-0046) without publishing anything, so the gate and the asset set are exercised on
# every change, not first on release day.
#
# Usage: scripts/release/rehearse-service-release.sh
#
# In a throwaway clone of HEAD with no remote and no tags, it expects the publishing gate
# (./gradlew checkServiceReleaseTag) to refuse each of these, and to let the last two through:
#   malformed tags (service-v0.1, a pre-release with a hyphen, build metadata), the placeholder 0.0.0, a -SNAPSHOT,
#   a tag that is not the build's version, a final release whose contract is unreleased, a final release whose
#   contract tag is not merged; then a final release whose contract tag is merged, and a pre-release of an unreleased
#   contract, which the gate marks as such.
# It then assembles the asset set from placeholder build outputs (scripts/release/service-release-assets.sh) and
# expects exactly the ADR's 11 files with checksums that verify, and a refusal when a target is missing.
# Not rehearsed here, because they need the remote: the tagged commit being on main, and the tag still naming the
# built commit when publishing; service-release.yml checks both. The clone has no remote, so nothing can be pushed.
# service.yml runs it with the other release rehearsals.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
clone="$work/clone"

git clone --quiet --no-local "$root" "$clone"
cd "$clone"
git checkout --quiet --detach "$(git -C "$root" rev-parse HEAD)"
git remote remove origin
git tag -l | xargs -r git tag -d > /dev/null

commit() { git -c user.name=rehearsal -c user.email=rehearsal@invalid commit --quiet --all --message "rehearsal: $1"; }
set_version() { sed -i "s/^version=.*/version=$1/" gradle.properties; }
set_contract() { sed -i "0,/^  version: .*$/s//  version: $1/" contracts/charter/openapi.yaml; }

gate() { # gate <tag>: runs the gate, output in $work/gate.log
  ./gradlew --quiet checkServiceReleaseTag -PreleaseTag="$1" > "$work/gate.log" 2>&1
}
refused() { # refused <tag> <expected words>
  if gate "$1"; then
    echo "error: the gate let $1 through" >&2
    cat "$work/gate.log" >&2
    exit 1
  fi
  if ! grep -q "The release tag $1 is refused: .*$2" "$work/gate.log"; then
    echo "error: the gate refused $1, but not for '$2'" >&2
    cat "$work/gate.log" >&2
    exit 1
  fi
  echo "refused, as expected: $1 ($2)"
}
passes() { # passes <tag> <expected gate.properties lines...>
  local tag="$1"
  shift
  if ! gate "$tag"; then
    echo "error: the gate refused $tag" >&2
    cat "$work/gate.log" >&2
    exit 1
  fi
  for line in "$@"; do
    if ! grep -qxF "$line" build/service-release/gate.properties; then
      echo "error: the gate passed $tag without '$line':" >&2
      cat build/service-release/gate.properties >&2
      exit 1
    fi
  done
  echo "passed, as expected: $tag"
}

echo "== 1. The gate refuses what may not be published"
refused service-v0.1 "not a service release tag"
refused service-v0.1.0-rc-1 "not a service release tag"
refused service-v0.1.0+build.1 "not a service release tag"
refused service-v0.0.0 "placeholder"
set_version 0.1.0-SNAPSHOT
commit "a snapshot build"
refused service-v0.1.0-SNAPSHOT "never published"
refused service-v0.1.0 "build's version in gradle.properties is 0.1.0-SNAPSHOT"
set_version 0.1.0
set_contract 0.1.0-SNAPSHOT
commit "release 0.1.0 of an unreleased contract"
refused service-v0.1.0 "unreleased"
set_contract 0.1.0
commit "release 0.1.0 of a contract whose tag is not merged"
refused service-v0.1.0 "contract-charter-v0.1.0 is not merged"

echo "== 2. The gate lets a release through"
git tag contract-charter-v0.1.0
passes service-v0.1.0 "version=0.1.0" "prerelease=false" "contractReleased=true"
set_version 0.2.0-rc.1
set_contract 0.2.0-SNAPSHOT
commit "pre-release 0.2.0-rc.1 of an unreleased contract"
passes service-v0.2.0-rc.1 "version=0.2.0-rc.1" "prerelease=true" "contract=0.2.0-SNAPSHOT" "contractReleased=false"

echo "== 3. The asset set"
in="$work/in"
prefix=spec-kitty-charter-service
for target in linux-x64 linux-arm64 macos-arm64; do
  mkdir -p "$in/native-$target"
  printf 'placeholder %s\n' "$target" > "$in/native-$target/$prefix"
done
mkdir -p "$in/native-windows-x64" "$in/service-jar"
printf 'placeholder windows-x64\n' > "$in/native-windows-x64/$prefix.exe"
printf 'placeholder jar\n' > "$in/service-jar/deployable-0.2.0-rc.1.jar"
"$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/assets"
count="$(ls "$work/assets" | wc -l | tr -d ' ')"
if [[ "$count" != 11 ]]; then
  echo "error: the release has $count files, not 11" >&2
  exit 1
fi
(cd "$work/assets" && sha256sum --check --strict --quiet SHA256SUMS)
if [[ "$(wc -l < "$work/assets/SHA256SUMS" | tr -d ' ')" != 5 ]]; then
  echo "error: SHA256SUMS does not list exactly the five assets" >&2
  exit 1
fi
rm "$in/native-macos-arm64/$prefix"
if "$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/partial" 2> "$work/partial.log"; then
  echo "error: a release without its macos-arm64 executable was assembled" >&2
  exit 1
fi
grep -q "no .*native-macos-arm64" "$work/partial.log"
echo "refused, as expected: an asset set without macos-arm64"

echo "service release rehearsal: every case behaved; nothing was published"
