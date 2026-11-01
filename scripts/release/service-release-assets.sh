#!/usr/bin/env bash
# Assembles the assets of a service release (ADR-0046 decision 4) from the build's workflow artifacts.
#
# Usage: scripts/release/service-release-assets.sh <version> <in-dir> <out-dir>
#
# <in-dir> holds the downloaded artifacts, one directory each (actions/download-artifact without merge-multiple):
#   native-linux-x64/spec-kitty-charter-service       native-linux-arm64/spec-kitty-charter-service
#   native-macos-arm64/spec-kitty-charter-service     native-windows-x64/spec-kitty-charter-service.exe
#   service-jar/deployable-<version>.jar
# <out-dir> receives exactly the ADR's asset set, nothing else:
#   spec-kitty-charter-service-<version>-{linux-x64,linux-arm64,macos-arm64}, -windows-x64.exe, and -<version>.jar;
#   one <asset>.sha256 per asset; SHA256SUMS over the five assets (not the .sha256 files), in sha256sum format with
#   bare names, sorted with LC_ALL=C, the file core's service pin reads.
# A missing input, an extra output, or a checksum that does not verify fails the run and leaves the cause on stderr.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <version> <in-dir> <out-dir>" >&2
  exit 2
fi
version="$1"
in="$2"
out="$3"
prefix="spec-kitty-charter-service"

if [[ -e "$out" ]] && [[ -n "$(ls -A "$out")" ]]; then
  echo "error: $out is not empty" >&2
  exit 1
fi
mkdir -p "$out"

take() { # take <source> <asset>
  if [[ ! -f "$1" ]]; then
    echo "error: the build produced no $1" >&2
    exit 1
  fi
  cp "$1" "$out/$2"
}

take "$in/native-linux-x64/$prefix" "$prefix-$version-linux-x64"
take "$in/native-linux-arm64/$prefix" "$prefix-$version-linux-arm64"
take "$in/native-macos-arm64/$prefix" "$prefix-$version-macos-arm64"
take "$in/native-windows-x64/$prefix.exe" "$prefix-$version-windows-x64.exe"
take "$in/service-jar/deployable-$version.jar" "$prefix-$version.jar"
# The executables keep their mode across the artifact round trip only on some runners; a download sets it again.
chmod 0755 "$out/$prefix-$version-linux-x64" "$out/$prefix-$version-linux-arm64" "$out/$prefix-$version-macos-arm64"

cd "$out"
assets=()
while IFS= read -r name; do assets+=("$name"); done < <(ls | LC_ALL=C sort)
for name in "${assets[@]}"; do
  sha256sum "$name" > "$name.sha256"
done
printf '%s\n' "${assets[@]}" | LC_ALL=C sort | xargs sha256sum > SHA256SUMS

expected="$(
  {
    for target in linux-arm64 linux-x64 macos-arm64; do
      echo "$prefix-$version-$target"
      echo "$prefix-$version-$target.sha256"
    done
    echo "$prefix-$version-windows-x64.exe"
    echo "$prefix-$version-windows-x64.exe.sha256"
    echo "$prefix-$version.jar"
    echo "$prefix-$version.jar.sha256"
    echo SHA256SUMS
  } | LC_ALL=C sort
)"
if ! diff -u <(printf '%s\n' "$expected") <(ls | LC_ALL=C sort); then
  echo "error: the release's files are not exactly the ADR-0046 asset set (diff above: - expected, + present)" >&2
  exit 1
fi
# Core reads SHA256SUMS line by line as <64 lowercase hex><space><space or *><bare name>.
if grep -Evq '^[0-9a-f]{64}  [^/ ]+$' SHA256SUMS; then
  echo "error: SHA256SUMS has a line core's pin cannot read" >&2
  exit 1
fi
sha256sum --check --strict --quiet SHA256SUMS
for name in "${assets[@]}"; do
  sha256sum --check --strict --quiet "$name.sha256"
done
echo "assembled $(( ${#assets[@]} )) assets of $version in $out, with SHA256SUMS and one .sha256 each"
