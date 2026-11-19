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
#   contract, which the gate marks as such; then a final release over a contract release candidate, refused, and a
#   pre-release over one, let through (operator ruling R1).
# It then assembles the asset set from placeholder build outputs (scripts/release/service-release-assets.sh) and
# expects exactly the ADR's 13 files (ADR-0046 as amended 2026-11-19, #478: the pack-check jar joins), 6 lines in
# SHA256SUMS and checksums that verify, a refusal when the pack-check jar is missing, and one when a target is missing.
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

commit() { git -c user.name=rehearsal -c user.email=rehearsal@invalid commit --quiet --all --allow-empty --message "rehearsal: $1"; }
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
set_version 0.3.0
set_contract 0.3.0-rc.1
commit "release 0.3.0 of a contract release candidate"
git tag contract-charter-v0.3.0-rc.1
refused service-v0.3.0 "needs a final contract release"
set_version 0.3.0-rc.1
commit "pre-release 0.3.0-rc.1 of a contract release candidate"
passes service-v0.3.0-rc.1 "version=0.3.0-rc.1" "prerelease=true" "contract=0.3.0-rc.1" "contractReleased=true"

echo "== 3. The asset set"
in="$work/in"
prefix=spec-kitty-charter-service
for target in linux-x64 linux-arm64 macos-arm64; do
  mkdir -p "$in/native-$target"
  printf 'placeholder %s\n' "$target" > "$in/native-$target/$prefix"
done
mkdir -p "$in/native-windows-x64" "$in/service-jar" "$in/pack-check-jar"
printf 'placeholder windows-x64\n' > "$in/native-windows-x64/$prefix.exe"
printf 'placeholder jar\n' > "$in/service-jar/deployable-0.2.0-rc.1.jar"
printf 'placeholder pack-check jar\n' > "$in/pack-check-jar/pack-check-0.2.0-rc.1.jar"
"$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/assets"
count="$(ls "$work/assets" | wc -l | tr -d ' ')"
if [[ "$count" != 13 ]]; then
  echo "error: the release has $count files, not 13" >&2
  exit 1
fi
(cd "$work/assets" && sha256sum --check --strict --quiet SHA256SUMS)
if [[ "$(wc -l < "$work/assets/SHA256SUMS" | tr -d ' ')" != 6 ]]; then
  echo "error: SHA256SUMS does not list exactly the six assets" >&2
  exit 1
fi
if ! grep -qE '^[0-9a-f]{64}  pack-check-0\.2\.0-rc\.1\.jar$' "$work/assets/SHA256SUMS"; then
  echo "error: SHA256SUMS does not list pack-check-0.2.0-rc.1.jar" >&2
  exit 1
fi
mv "$in/pack-check-jar/pack-check-0.2.0-rc.1.jar" "$work/pack-check.jar"
if "$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/no-pack-check" 2> "$work/no-pack-check.log"; then
  echo "error: a release without its pack-check jar was assembled" >&2
  exit 1
fi
if ! grep -q "no .*pack-check-jar/pack-check-0\.2\.0-rc\.1\.jar" "$work/no-pack-check.log"; then
  echo "error: the assets script refused a release without its pack-check jar, but did not name it:" >&2
  cat "$work/no-pack-check.log" >&2
  exit 1
fi
echo "refused, as expected: an asset set without the pack-check jar"
mv "$work/pack-check.jar" "$in/pack-check-jar/pack-check-0.2.0-rc.1.jar"
rm "$in/native-macos-arm64/$prefix"
if "$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/partial" 2> "$work/partial.log"; then
  echo "error: a release without its macos-arm64 executable was assembled" >&2
  exit 1
fi
grep -q "no .*native-macos-arm64" "$work/partial.log"
echo "refused, as expected: an asset set without macos-arm64"

echo "not rehearsed here (they need the remote; service-release.yml checks them): the tagged commit on main, the tag still naming the built commit"
echo "service release rehearsal: every case behaved; nothing was published"
