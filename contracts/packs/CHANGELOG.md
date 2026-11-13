# Changelog

All notable changes to the packs contract are recorded here, newest first. The packs contract is
everything under `contracts/packs/` that a reader of packs depends on: `file-kinds.yaml`, the JSON
Schemas under `schemas/` (the pack-document structure schema among them) and the classifier
conformance cases under `conformance/`. It is versioned on its own changes, apart from the charter
contract (`../charter/CHANGELOG.md`), and the top heading below is its version: there is no other
version carrier. A change to any of those files adds a line to the top section in the same pull
request.

The rules are those of the charter contract (ADR-0022): an unreleased version carries `-SNAPSHOT`;
while the major is 0 a minor may break and a patch may not, and a minor bump over a release holds a
`### Breaking` subsection that lists each break. A release is tagged `contract-packs-v<version>`;
`contracts/packs/README.md` ("Releasing the packs contract") has the procedure, and
`docs/guides/pin-the-packs-contract.md` how a consumer pins and checks a release.

## 0.1.0-SNAPSHOT

The first packs contract version, not yet released (#559).

### Added

- The packs contract as a versioned line of its own, published as a release
  (`contract-packs-v<version>`) with a checksum for every file.
- Classifier conformance cases (`conformance/classifier-cases.yaml`): a pack file path and the
  outcome an implementation of `file-kinds.yaml` must give it (`mapped` with its kind, schema and
  content form, `excluded`, `retired` or `unmodelled`), one case for every rule at least. The
  charter service runs them against its own classifier in CI.
