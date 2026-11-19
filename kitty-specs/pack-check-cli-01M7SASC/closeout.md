# Closeout: the pack-check CLI and its service release

**Mission:** `pack-check-cli-01M7SASC` (#478, slice 8 of epic #549). **Closing PR:** #608
(`kitty/mission-pack-check-cli-01M7SASC` into `main`).

Written 2026-11-19 by WP08. At that time WP01 was approved and WP02 to WP07 were not yet done. Every fact that only
WP03 to WP07 settle is marked **to confirm at closeout**. Fill it in from the merged code before #608 merges.

Nothing was filed, closed or commented on from this Mission. The orchestrator does each step in "For the orchestrator"
(issue-tracker Rule 2).

## For the PR body

```text
Closes #597
Refs #478
```

- **#597:** fully delivered by WP01 (evidence below).
- **#478 stays `Refs`:** the code lands with #608, but FR-012 (the `service-v0.1.0-rc.2` release, ruling D4) completes
  after the merge. Say so on #478, and close it once the release exists (`release-runbook.md`, step 6).

## Issues this Mission delivers

### #597: a staged tree with a link is registered (closes with #608)

`PackRegistrationService` accepted a staged tree whose symbolic link pointed outside the pack. Rulings D1 and D6: no
read follows a link, and every link inside a pack root is refused with `pack_entry_linked`.

**Code** (WP01, branch `kitty/mission-pack-check-cli-01M7SASC-lane-a`, commits `ed662d8d`..`1fc6004d`):

- **The code:** `pack_entry_linked`, `StorageCodes.PACK_ENTRY_LINKED`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java`.
- **The rule:** `LinkRules`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/LinkRules.java`, with
  `StorageRulesets`.
- **The reads that never follow a link:** `PackFileSet.unlinkedFile` and `PackFileSet.unlinkedBytes`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java`.
- **The contract:** the `### Breaking` fragment `contracts/charter/changes/2026-11-19-pack-entry-linked.md` and the
  `pack_entry_linked` entry in `contracts/charter/schemas/CharterRefusalCode.yaml`.

**Tests:**

- `service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationLinkTest.java` holds #597's
  three cases. The parameterized `aLinkIsRefused` covers a link to a valid directive (`directives/B.directive.yaml`),
  to a directory (`directives/more`) and to an unmodelled text file (`NOTES.txt`). Each answers `validation_failed`
  with `pack_entry_linked` at the link and stores nothing. The control is `theSamePackWithoutTheLinkRegisters`.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/LinkedEntriesTest.java` holds the read, load
  and write cases. Among them:
  - `aFileLinkIsALink` and `directoryDanglingAndLoopingLinksAreLinksOnly`: what counts as a link;
  - `noTargetByteReachesAnOutput` and `oneViolationPerLink`: what the refusal holds;
  - `theConfiguredLoadRefusesALinkedPack`: the load refuses it;
  - `aCommitNeverCopiesThroughALink`: the write path;
  - `aRootGivenThroughALinkIsReadWhole`: the root itself may be a link.
- The planted fixture `service/packs/src/testFixtures/resources/planted/pack-entry-linked-file/`.

**No HTTP contract scenario.** `whole-pack-registration.feature` was in WP01's write scope and was not changed. Over
HTTP, every stager refuses a link before the reader runs, so no request can reach `pack_entry_linked`:

- `ZipReader`, lines 185 to 190;
- `TarGzReader`, lines 74 to 77;
- `LocalSourceReader`, line 224;
- `GitSourceFetcher`, lines 395 to 401.

**Issue matrix.** The `#597` row of `issue-matrix.json` cites the pre-rebase range `89c6da5e..92408ae7`. The commits
on lane-a are `ed662d8d..1fc6004d`. Correct the row, or cite the merged commits, before #608 merges.

### #478: the pack-check CLI and its release (`Refs` until the rc.2 release exists)

The FR-to-WP map comes from `spec.md` and `tasks.md`. Fill in the code names from the merged code.

| FR | Delivered by | Evidence |
| --- | --- | --- |
| FR-001, FR-002, FR-006 | WP04 | `service/pack-check` `main` and its tests: **to confirm at closeout** |
| FR-003, FR-007 | WP02, WP03 | the pack-set check under `service/pack-validation/.../set/` and its tests: **to confirm at closeout** |
| FR-004 | WP03, WP04 | `--round-trip` and its test: **to confirm at closeout** |
| FR-005 | WP05 | `contracts/charter/schemas/PackCheckReport.yaml`, `contracts/charter/changes/2026-11-19-pack-check-report.md`, `PackCheckReportContractTest`: **to confirm at closeout** |
| FR-008 | WP01 | as for #597 above |
| FR-009 | WP04 | the inventory (no `pack-validation` type is CLI-only): **to confirm at closeout** |
| FR-010 | WP06, WP07 | `pack-check-<version>.jar` in `scripts/release/service-release-assets.sh` (13 files, 6 `SHA256SUMS` lines) and `rehearse-service-release.sh`: **to confirm at closeout** |
| FR-011 | WP07 | the ADR-0046 amendment, ADR-0075's record, `docs/guides/service-build.md`, `docs/guides/check-packs.md`: **to confirm at closeout** |
| FR-012 | after merge | `release-runbook.md`: the version PR, the `service-native` run, the `service-release` run, the release, the snapshot PR |

**On #478, at the merge of #608**, comment with the following:

- #608 delivers FR-001 to FR-011.
- Only the release step remains: FR-012, `service-v0.1.0-rc.2` (D4).
- The issue closes when the release exists.

**When the release exists**, post the evidence comment (`release-runbook.md`, step 6) and close #478 as `completed`.

## Epic #549

Tick this line in #549's checklist when #478 closes, that is, once the `service-v0.1.0-rc.2` release exists:

```text
- [ ] `pack-check` jar on the `service-v*` release (#478)
```

Add the PRs to the line as the other ticked lines do: #608 and the version PR. #549 stays open, because other lines
remain open.

## Follow-ups

- **Filed in this repository:** none.
- **Drafted for spec-kitty-open-packs:** `open-packs#TBD-pack-entry-linked`, in
  `research/issue-drafts/open-packs-pack-entry-linked.md`. `validate_pack_schemas.py` does not know
  `pack_entry_linked`. Retire it for `pack-check` at the pin (open-packs#252), or teach it the code. The orchestrator
  files it and replaces the placeholder in this file and in the draft before #608 merges (Rule 4).

## The D6 check (2026-11-19)

- **Charter:** 0 committed symbolic links.
- **open-packs:** 76, all outside its packs: 72 under `.agents/`, 2 under `.github/`, `CHANGELOG.md` and
  `CONTRIBUTING.md`.

No pack in either repository holds a link, so ruling D6 refuses no pack that exists today.

## For the orchestrator

1. File the open-packs draft. Replace `open-packs#TBD-pack-entry-linked` here and in the draft.
2. Fill every **to confirm at closeout** above from the merged code, and correct the `#597` row of `issue-matrix.json`.
3. Put "For the PR body" into #608's body (`Closes #597`, `Refs #478`).
4. At the merge of #608, comment on #478 that only the release step remains.
5. Run `release-runbook.md`. The operator pushes the tag.
6. When the release exists, post the evidence on #478, close it as `completed`, and tick #549's line.
