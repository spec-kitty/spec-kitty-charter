---
title: Pin the packs contract
description: Fetch a packs contract release, check the archive and every file against their SHA-256, record the pin, and run the classifier conformance cases against your own reader.
doc_status: draft
updated: 2026-11-13T00:00:00Z
type: Guide
kind: How-To
related:
  - guides/generate-a-client
  - adr/0022-contract-versions-start-at-0-x
audience:
  - profile: contract-consumer
    guidance_text: Follow this to vendor the packs contract at a release, check it, and hold your reader of packs to its conformance cases.
  - profile: pack-author
    guidance_text: Read this for which packs contract version a tool that checks your pack was built against.
---

# Pin the packs contract

This page is for a tool that reads Charter Packs without running the charter service, such as a documentation
generator or a pack checker. It shows how to vendor the **packs contract** at a release, check what you downloaded,
and prove that your reader classifies pack files as the charter does.

The packs contract is the part of `contracts/packs/` a reader of packs depends on:

- `file-kinds.yaml`, which decides what each file of a pack is;
- the JSON Schemas under `schemas/`, the Markdown structure schemas (`pack-document`, for a pack's `README.md`) among
  them;
- the classifier conformance cases, `conformance/classifier-cases.yaml`.

It is versioned on its own changes, apart from the charter's OpenAPI contract. Its version is the top heading of
`contracts/packs/CHANGELOG.md`, and it follows the 0.x rules of
[ADR-0022](/adr/0022-contract-versions-start-at-0-x/): a patch does not break, while the major is 0 a minor may, and
each break is listed under `### Breaking`.

**No release is tagged yet.** Until `contract-packs-v0.1.0` is tagged, pin a commit of `main` as below, and switch to a
release once there is one. The commands are the same; only the source differs.

## Before you start

- `curl`, `tar`, and GNU `sha256sum`. On macOS, install them with `brew install coreutils`, or use `shasum -a 256 -c`
  in place of `sha256sum --check`.

## Step 1: Download a release and check it

Each release `contract-packs-v<version>` carries three assets at
`https://github.com/spec-kitty/spec-kitty-charter/releases/download/contract-packs-v<version>/`:

| Asset | Holds |
|---|---|
| `packs-contract.tar.gz` | The tracked files of `contracts/packs/` at the tag, under `packs-contract/` |
| `packs-contract.tar.gz.sha256` | The archive's SHA-256 |
| `packs-contract.sha256` | One SHA-256 per file of the archive, by its path in the archive |

```bash
version=0.1.0
base="https://github.com/spec-kitty/spec-kitty-charter/releases/download/contract-packs-v$version"
for asset in packs-contract.tar.gz packs-contract.tar.gz.sha256 packs-contract.sha256; do
  curl -fsSLO "$base/$asset"
done
sha256sum --check --strict packs-contract.tar.gz.sha256
tar -xzf packs-contract.tar.gz
sha256sum --check --strict packs-contract.sha256
```

Both checks print `OK` for every file. On `FAILED`, download again; never vendor a file that does not match. A
checksum served beside its file proves the download is whole, not where it came from. Record the archive's SHA-256 in
your pin (Step 2) and compare with it on every refresh: that, not the served checksum, is what you trust.

The archive is built reproducibly: the same tag always gives the same bytes.

## Step 2: Record the pin

Record, in your own lock file:

- the release tag, `contract-packs-v<version>`;
- the SHA-256 of `packs-contract.tar.gz`;
- the SHA-256 of each file you vendor, copied from `packs-contract.sha256`.

A unit test of yours then fails when a vendored file's bytes and its recorded SHA-256 disagree, so a hand edit of the
snapshot cannot pass unnoticed. A file you do not vendor (the `README.md`, say) needs no line.

**Before the first release**, pin a commit instead: take the files of `contracts/packs/` at that commit (for example
with `git archive <commit> contracts/packs`), and record the commit and each file's SHA-256 the same way.

## Step 3: Run the conformance cases against your reader

`conformance/classifier-cases.yaml` lists pack file paths and what a classifier must make of each: `mapped` (with the
file's `kind`, its `schema` relative to `contracts/packs/` and its `content_form`), `excluded`, `retired` or
`unmodelled`, and the `rule` of `file-kinds.yaml` that decides it. The file's header says how the rules are
consulted. Run every case in your own tests and fail on any difference. The charter runs the same cases against its
own classifier in CI, and fails when a rule of `file-kinds.yaml` has no case, so a release's cases cover every rule
of its table.

The cases cover classifying a path. They do not cover the companion files a kind claims after classification (a
skill's body, a toolguide's guide).

## Step 4: Move to a newer release

Read the new version's section of `contracts/packs/CHANGELOG.md`. A patch is a drop-in. A minor while the major is 0
may break: its `### Breaking` subsection lists each break. Repeat Steps 1 to 3 with the new version, and update the
pin.

## Related

- [Generate your own client](/guides/generate-a-client/), for the charter service's OpenAPI contract.
- `contracts/packs/README.md`, for what each file of the contract holds and how a release is cut.
