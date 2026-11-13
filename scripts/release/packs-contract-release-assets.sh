#!/usr/bin/env bash
# Assembles the release assets of one packs contract version (#559) into an output directory, from the checked-out
# commit:
#
#   packs-contract.tar.gz          the tracked files of contracts/packs/ under packs-contract/: file-kinds.yaml, the
#                                  schemas, the classifier conformance cases, the CHANGELOG and the README; and
#                                  packs-contract/RELEASE.json, which names the version, the tag and the commit
#   packs-contract.sha256          one sha256sum line per file of the archive, by its path in the archive
#   packs-contract.tar.gz.sha256   the archive's checksum, in sha256sum format
#
# Usage: scripts/release/packs-contract-release-assets.sh <tag> <out-dir>
#
# It refuses unless the tag and the top heading of contracts/packs/CHANGELOG.md name one released version
# (:contracts:checkPacksReleaseTag) and the packs contract checks pass: the classifier runs the conformance cases, and
# the table, the schemas and the service's vocabulary agree (:packs:test, the four test classes below). The assets are built in a scratch
# directory and moved into <out-dir> only once all of them exist and check, so a refusal or a failure leaves <out-dir>
# without assets. .github/workflows/packs-contract-release.yml runs it, and rehearse-packs-contract-release.sh runs it
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

# The assets are those of the tagged commit: when the tag exists, a run on another commit refuses rather than mislabel
# them. A workflow_dispatch rehearsal names a tag that does not exist yet and builds from the chosen ref.
tagged="$(git rev-parse --verify --quiet "$tag^{commit}" || true)"
if [[ -n "$tagged" ]] && [[ "$tagged" != "$(git rev-parse HEAD)" ]]; then
  echo "error: the tag $tag does not name HEAD ($(git rev-parse --short HEAD)): check out the tag first" >&2
  exit 2
fi

# The publishing gate first: nothing is built for a tag the packs contract does not carry.
./gradlew --no-build-cache :contracts:checkPacksReleaseTag -PreleaseTag="$tag"
./gradlew --no-build-cache :packs:test \
  --tests '*.ClassifierConformanceTest' --tests '*.PackFileKindTest' --tests '*.PackSchemasTest' \
  --tests '*.VocabularyEqualityTest'

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
# The tracked files only, as the commit holds them: an untracked or modified file never enters a release.
if [[ -n "$(git status --porcelain -- contracts/packs)" ]]; then
  echo "error: contracts/packs has uncommitted changes; a release is built from the commit" >&2
  exit 1
fi
mkdir "$stage/packs-contract"
git archive --format=tar HEAD contracts/packs | tar -x -C "$stage/packs-contract" --strip-components=2
# The release record a consumer's lock copies (doc-kitty's contract.lock.json names the charter commit).
printf '{\n  "contract": "packs",\n  "version": "%s",\n  "tag": "%s",\n  "commit": "%s"\n}\n' \
  "${tag#contract-packs-v}" "$tag" "$(git rev-parse HEAD)" > "$stage/packs-contract/RELEASE.json"
(cd "$stage" && find packs-contract -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum > packs-contract.sha256)

# A reproducible archive: sorted names, fixed owner and modes, and the commit's time as every member's time.
mtime="$(git log -1 --format=%ct)"
(cd "$stage" && find packs-contract -print0 | LC_ALL=C sort -z \
  | tar --sort=name --format=gnu --owner=0 --group=0 --numeric-owner --mode='u=rwX,go=rX' --mtime="@$mtime" \
      --no-recursion --null --files-from=- -cf - \
  | gzip -9n > packs-contract.tar.gz)
(cd "$stage" && sha256sum packs-contract.tar.gz > packs-contract.tar.gz.sha256)

# The checks a consumer runs, before anything leaves the stage.
(cd "$stage" && sha256sum --check --strict --quiet packs-contract.tar.gz.sha256)
check="$(mktemp -d)"
trap 'rm -rf "$stage" "$check"' EXIT
tar -xzf "$stage/packs-contract.tar.gz" -C "$check"
(cd "$check" && sha256sum --check --strict --quiet "$stage/packs-contract.sha256")
for required in RELEASE.json file-kinds.yaml CHANGELOG.md conformance/classifier-cases.yaml schemas/pack-document.schema.json; do
  if [[ ! -s "$check/packs-contract/$required" ]]; then
    echo "error: the archive has no packs-contract/$required" >&2
    exit 1
  fi
done

rm -rf "$stage/packs-contract"
mkdir -p "$out"
mv "$stage"/* "$out/"
ls -l "$out"
