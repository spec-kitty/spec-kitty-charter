#!/usr/bin/env bash
# Rehearses a service release (ADR-0046) without publishing anything, so the gate and the asset set are exercised on
# every change, not first on release day.
#
# Usage: scripts/release/rehearse-service-release.sh
#
# In a throwaway clone of HEAD with no remote and no tags, it expects the publishing gate
# (./gradlew checkServiceReleaseTag) to refuse each of these: malformed tags (service-v0.1, a pre-release with a
# hyphen, build metadata), the placeholder 0.0.0, a -SNAPSHOT, a tag that is not the build's version, a final release
# whose contract is unreleased, a final release whose contract tag is not merged. Every release, a pre-release too,
# bundles released contracts (#650): it then expects a refusal while the packs contract is unreleased or its tag is not
# merged, a release once both contract tags are merged, a release over a packs contract reopened as -SNAPSHOT but
# unchanged since its release, a refusal once a packs file changes, and a refusal of a pre-release of an unreleased
# charter contract. Last, over contract release candidates (operator ruling R1): a final release refused, a
# pre-release let through, and a final release over a final charter contract but a packs candidate refused. And the
# unchanged packs contract: a README edit does not end it, and of several releases on one commit, the highest version
# counts (a final above its candidates). A released version is the bytes of its release (review probe P1): a packs or
# charter contract file edited without reopening the version as a -SNAPSHOT is refused, a CHANGELOG or README edit is not.
# It then assembles the asset set from placeholder build outputs (scripts/release/service-release-assets.sh) and
# expects exactly the ADR's 13 files (ADR-0046 as amended 2026-11-19, #478: the pack-doctor jar joins), 6 lines in
# SHA256SUMS and checksums that verify, a refusal when the pack-doctor jar is missing, and one when a target is missing.
# Not rehearsed here, because they need the remote: the tagged commit being on main, both contract releases being
# published with their assets, and the tag still naming the built commit when publishing; service-release.yml checks
# them. The clone has no remote, so nothing can be pushed.
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
set_packs() { sed -i "0,/^## .*$/s//## $1/" contracts/packs/CHANGELOG.md; }

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

echo "== 2. Every release bundles released contracts"
git tag contract-charter-v0.1.0
refused service-v0.1.0 "packs contract .*-SNAPSHOT is unreleased"
set_packs 0.1.0
commit "the packs contract 0.1.0, not yet tagged"
refused service-v0.1.0 "contract-packs-v0.1.0 is not merged"
git tag contract-packs-v0.1.0
passes service-v0.1.0 "version=0.1.0" "prerelease=false" "contract=0.1.0" "packsContract=0.1.0"
set_version 0.1.1
set_packs 0.2.0-SNAPSHOT
commit "release 0.1.1 over a packs contract reopened but unchanged"
passes service-v0.1.1 "version=0.1.1" "contract=0.1.0" "packsContract=0.1.0"
set_version 0.1.1-rc.1
printf '\na rehearsal note\n' >> contracts/packs/README.md
commit "pre-release 0.1.1-rc.1 after a packs README edit, which is not the contract"
passes service-v0.1.1-rc.1 "packsContract=0.1.0"
set_version 0.1.2
printf '# a rehearsal change\n' >> contracts/packs/file-kinds.yaml
commit "release 0.1.2 over a changed packs contract"
refused service-v0.1.2 "packs contract 0.2.0-SNAPSHOT is unreleased"
set_packs 0.2.0
commit "the packs contract 0.2.0"
git tag contract-packs-v0.2.0
set_version 0.2.0-rc.1
set_contract 0.2.0-SNAPSHOT
commit "pre-release 0.2.0-rc.1 of an unreleased charter contract"
refused service-v0.2.0-rc.1 "charter contract 0.2.0-SNAPSHOT is unreleased"

echo "== 3. Contract release candidates"
set_version 0.3.0
set_contract 0.3.0-rc.1
set_packs 0.3.0-rc.1
commit "release 0.3.0 of contract release candidates"
git tag contract-charter-v0.3.0-rc.1
git tag contract-packs-v0.3.0-rc.1
refused service-v0.3.0 "needs a final contract release"
set_version 0.3.0-rc.1
commit "pre-release 0.3.0-rc.1 of contract release candidates"
passes service-v0.3.0-rc.1 "version=0.3.0-rc.1" "prerelease=true" "contract=0.3.0-rc.1" "packsContract=0.3.0-rc.1"
set_version 0.3.0
set_contract 0.3.0
commit "release 0.3.0 of a final charter contract and a packs candidate"
git tag contract-charter-v0.3.0
refused service-v0.3.0 "needs a final packs contract release"
set_packs 0.3.0
commit "release 0.3.0 of final contracts"
git tag contract-packs-v0.3.0
git tag contract-packs-v0.3.1-rc.1
git tag contract-packs-v0.3.0-rc.2
passes service-v0.3.0 "version=0.3.0" "prerelease=false" "contract=0.3.0" "packsContract=0.3.0"
set_version 0.3.1
set_packs 0.4.0-SNAPSHOT
commit "release 0.3.1 over an unchanged packs contract whose highest release is a candidate on the final's commit"
refused service-v0.3.1 "needs a final packs contract release, but the bundled packs contract 0.3.1-rc.1"
git tag -d contract-packs-v0.3.1-rc.1 > /dev/null
passes service-v0.3.1 "packsContract=0.3.0"

echo "== 4. A released version is the bytes of its release (review probe P1)"
set_version 0.3.2
set_packs 0.3.0
commit "release 0.3.2 over the released contracts"
passes service-v0.3.2 "contract=0.3.0" "packsContract=0.3.0"
printf '# a rehearsal change\n' >> contracts/packs/file-kinds.yaml
commit "release 0.3.2 after a packs contract edit that kept the released version"
refused service-v0.3.2 "packs contract 0.3.0, but its files changed since contract-packs-v0.3.0"
git reset --quiet --hard HEAD~1
printf '# a rehearsal change\n' >> contracts/charter/openapi.yaml
commit "release 0.3.2 after a charter contract edit that kept the released version"
refused service-v0.3.2 "charter contract 0.3.0, but its files changed since contract-charter-v0.3.0"
git reset --quiet --hard HEAD~1
printf '# a rehearsal change\n' >> contracts/charter/scenarios/README.md
commit "release 0.3.2 after a charter scenario edit that kept the released version"
refused service-v0.3.2 "charter contract 0.3.0, but its files changed since contract-charter-v0.3.0"
git reset --quiet --hard HEAD~1
printf '\na rehearsal note\n' >> contracts/charter/CHANGELOG.md
printf '\na rehearsal note\n' >> contracts/packs/README.md
commit "release 0.3.2 after CHANGELOG and README edits, which are not the contracts"
passes service-v0.3.2 "contract=0.3.0" "packsContract=0.3.0"

echo "== 5. The asset set"
in="$work/in"
prefix=spec-kitty-charter-service
for target in linux-x64 linux-arm64 macos-arm64; do
  mkdir -p "$in/native-$target"
  printf 'placeholder %s\n' "$target" > "$in/native-$target/$prefix"
done
mkdir -p "$in/native-windows-x64" "$in/service-jar" "$in/pack-doctor-jar"
printf 'placeholder windows-x64\n' > "$in/native-windows-x64/$prefix.exe"
printf 'placeholder jar\n' > "$in/service-jar/deployable-0.2.0-rc.1.jar"
printf 'placeholder pack-doctor jar\n' > "$in/pack-doctor-jar/pack-doctor-0.2.0-rc.1.jar"
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
if ! grep -qE '^[0-9a-f]{64}  pack-doctor-0\.2\.0-rc\.1\.jar$' "$work/assets/SHA256SUMS"; then
  echo "error: SHA256SUMS does not list pack-doctor-0.2.0-rc.1.jar" >&2
  exit 1
fi
mv "$in/pack-doctor-jar/pack-doctor-0.2.0-rc.1.jar" "$work/pack-doctor.jar"
if "$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/no-pack-doctor" 2> "$work/no-pack-doctor.log"; then
  echo "error: a release without its pack-doctor jar was assembled" >&2
  exit 1
fi
if ! grep -q "no .*pack-doctor-jar/pack-doctor-0\.2\.0-rc\.1\.jar" "$work/no-pack-doctor.log"; then
  echo "error: the assets script refused a release without its pack-doctor jar, but did not name it:" >&2
  cat "$work/no-pack-doctor.log" >&2
  exit 1
fi
echo "refused, as expected: an asset set without the pack-doctor jar"
mv "$work/pack-doctor.jar" "$in/pack-doctor-jar/pack-doctor-0.2.0-rc.1.jar"
rm "$in/native-macos-arm64/$prefix"
if "$root/scripts/release/service-release-assets.sh" 0.2.0-rc.1 "$in" "$work/partial" 2> "$work/partial.log"; then
  echo "error: a release without its macos-arm64 executable was assembled" >&2
  exit 1
fi
grep -q "no .*native-macos-arm64" "$work/partial.log"
echo "refused, as expected: an asset set without macos-arm64"

echo "not rehearsed here (they need the remote; service-release.yml checks them): the tagged commit on main, both contract releases published with their assets, the tag still naming the built commit"
echo "service release rehearsal: every case behaved; nothing was published"
