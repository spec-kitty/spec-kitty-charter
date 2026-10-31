#!/usr/bin/env bash
# Assembles the release assets of one contract version into an output directory, from the checked-out commit:
#
#   charter-openapi.yaml           the single-file bundle (./gradlew :contracts:bundleContract)
#   charter-openapi.yaml.sha256    its checksum, in sha256sum format
#   charter-scenarios.tar.gz       contracts/charter/scenarios/, the contract scenarios (the conformance corpus)
#   ActivationConfig.schema.json   contracts/offering/schemas/, the canonical activation document (JSON Schema 2020-12)
#
# Usage: scripts/release/contract-release-assets.sh <tag> <out-dir>
#
# It refuses unless the tag, info.version and the top CHANGELOG heading name one released version
# (:contracts:checkReleaseTag) and the contract checks pass (:contracts:check). It then checks the bundle against
# its checksum. The assets are built in a scratch directory and moved into <out-dir> only once all of them exist,
# so a refusal or a failure leaves <out-dir> without assets. Nothing comes from the build cache: the bundle is
# rebuilt from the commit. .github/workflows/contract-release.yml runs it, and rehearse-contract-release.sh runs it
# on a synthetic release in CI.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <tag> <out-dir>" >&2
  exit 2
fi
tag="$1"
out="$(realpath -m "$2")"
root="$(git rev-parse --show-toplevel)"
cd "$root"

if [[ -e "$out" ]] && [[ -n "$(ls -A "$out")" ]]; then
  echo "error: $out is not empty" >&2
  exit 2
fi

# The publishing gate first: nothing is built for a tag the contract does not carry.
./gradlew --no-build-cache :contracts:checkReleaseTag -PreleaseTag="$tag"
./gradlew --no-build-cache :contracts:check

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
cp build/contracts/contract/charter-openapi.yaml build/contracts/contract/charter-openapi.yaml.sha256 "$stage/"
# The canonical activation document (#125, ADR-0020 D4): the bundle mirrors it, and consumers may validate with it.
git show HEAD:contracts/offering/schemas/ActivationConfig.schema.json > "$stage/ActivationConfig.schema.json"
(cd "$stage" && sha256sum --check --strict charter-openapi.yaml.sha256)

# A reproducible archive of the tracked scenario files: sorted names, fixed owner and modes, and the commit's time
# as every member's time.
mtime="$(git log -1 --format=%ct)"
git -C contracts/charter ls-files -z scenarios \
  | tar -C contracts/charter --sort=name --format=gnu --owner=0 --group=0 --numeric-owner \
      --mode='u=rwX,go=rX' --mtime="@$mtime" --null --files-from=- -cf - \
  | gzip -9n > "$stage/charter-scenarios.tar.gz"

mkdir -p "$out"
mv "$stage"/* "$out/"
ls -l "$out"
