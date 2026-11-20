# Closeout: the pack-check CLI and its service release

**Mission:** `pack-check-cli-01M7SASC` (#478, slice 8 of epic #549). **Closing PR:** #608
(`kitty/mission-pack-check-cli-01M7SASC` into `main`).

Written 2026-11-19 by WP08 and completed at closeout from the merged code (all eight WPs approved; integrated on the
mission branch by merge `7577b896c`).

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

**Code** (WP01's commits `da2a9a16..6741fa29`, merged into the mission branch by `7577b896c` and into `main` by #608):

- **The code:** `pack_entry_linked`, `StorageCodes.PACK_ENTRY_LINKED`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java`.
- **The rule:** `LinkRules`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/LinkRules.java`, with
  `StorageRulesets`.
- **The reads that never follow a link:** `PackFileSet.unlinkedFile` and `PackFileSet.unlinkedBytes`, in
  `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java`.
- **The contract:** the `### Breaking` fragment `contracts/charter/changes/2026-11-19-pack-entry-linked.md` and the
  code `pack_entry_linked`, named under `validation_failed` in
  `contracts/charter/schemas/CharterRefusalCode.yaml`.

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

**Issue matrix.** The `#597` row of `issue-matrix.json` on the mission branch cites lane-a's current range. At
closeout, cite WP01's commits as merged in #608.

### #478: the pack-check CLI and its release (`Refs` until the rc.2 release exists)

The FR-to-WP map comes from `spec.md` and `tasks.md`; the evidence names the merged code.

| FR | Delivered by | Evidence |
| --- | --- | --- |
| FR-001, FR-002, FR-006 | WP04 | `service/pack-check`: `PackCheckMain`, `Arguments`, `ConfigRoot`, `Problems`, `ReportWriter`; `PackCheckMainTest`, `ArgumentsTest`, `ConfigRootTest`, `ProblemsTest` |
| FR-003, FR-007 | WP02, WP03 | `PackReadout.facts()` on the one read; `PackRulesets.closureFaults`/`closure(presetIdsOf)`; `packvalidation.set.PackSetCheck`, `PackSetEntry`, `PackSetVerdict`; `PackSetCheckTest`, `ReadoutFactsTest`, `PackRulesetsClosureTest` |
| FR-004 | WP03, WP04 | `--round-trip` through the `RoundTripping` port and `PackRoundTrip`; `PackSetCheckTest$RoundTrip`, `PackCheckMainTest`, jarTest case 1 |
| FR-005 | WP05 | `contracts/charter/schemas/PackCheckReport.yaml`, `contracts/charter/changes/2026-11-19-pack-check-report.md`, `ReachableContractFilesTest` (`STANDALONE`), `PackCheckReportContractTest`, `PlantedParityTest` (118 subjects) |
| FR-008 | WP01 | as for #597 above |
| FR-009 | WP04 | `fr-009-inventory.md`: no `pack-validation` type renders or parses for the CLI; the set-check types hold checks |
| FR-010 | WP06, WP07 | `pack-check-<version>.jar` in `scripts/release/service-release-assets.sh` (13 files, 6 `SHA256SUMS` lines) and `rehearse-service-release.sh`; the jar built by `charter.pack-check-jar`, proven Spring-free by `PackCheckJarTest` (14 tests) |
| FR-011 | WP07 | the ADR-0046 amendment, ADR-0075's record, `docs/guides/service-build.md`, `docs/guides/check-packs.md` |
| FR-012 | after merge | `release-runbook.md`: the version PR, the `service-native` run, the `service-release` run, the release, the snapshot PR |

**On #478, at the merge of #608**, comment with the following:

- #608 delivers FR-001 to FR-011.
- Only the release step remains: FR-012, `service-v0.1.0-rc.2` (D4).
- The issue closes when the release exists.

**When the release exists**, post the evidence comment (`release-runbook.md`, step 6) and close #478 as `completed`.

## Integration: approval stamps and merged heads

`spec-kitty consolidate` refused (`APPROVAL_STAMP_NOT_ON_LANE`), so the approved lanes were merged by hand into the
mission branch at `7577b896c` (its second parents are lane-g `871b3d45e` and WP08 `44fb67f33`). The stamps that are not
reachable from the mission branch are pushed as evidence under `kitty/pack-check-cli-01M7SASC-approval-stamps/wpNN`
(unsigned spec-kitty commits, never merged). Each row's code diff, `git diff --stat <stamp> <head> -- . ':!kitty-specs'`,
is empty.

| WP | Approval stamp | Merged head | Relation |
| --- | --- | --- | --- |
| WP01 | `1fc6004d9` | `6741fa298` | the same commits re-signed (signed-commit rule); identical code |
| WP02 | `2957d32c1` | `2957d32c1` | the stamp itself |
| WP03 | `a8d726bde` | `1cb7115c2` | the stamp is spec-kitty's unsigned "merge mission into lane" commit; its first parent is the head |
| WP04 | `2e0420c6d` | `746edcca1` | as WP03 |
| WP05 | `a417bbd1b` | `8973920fc` | as WP03 |
| WP06 | `8410d013d` | `c23bded79` | as WP03 |
| WP07 | `34c1c88de` | `871b3d45e` | as WP03 |
| WP08 | (planning lane) | `44fb67f33` | on `feat/478-pack-check`; touches only `kitty-specs/` |

So only WP01 was re-signed after its approval. WP03 to WP07 were committed before their approvals; spec-kitty's own
status merges on top were dropped because they are unsigned.

**Code after the approvals.** The main merge `6c7a5b522` and its fixes `b48d6e038` and `804339fa0` (#583 and #602), and
the fixes of the independent review's findings, came after the per-WP approvals. The independent adversarial review of
#608 covers them.

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
2. Done at closeout: every value above names the merged code.
3. Put "For the PR body" into #608's body (`Closes #597`, `Refs #478`).
4. At the merge of #608, comment on #478 that only the release step remains.
5. Run `release-runbook.md`. The operator pushes the tag.
6. When the release exists, post the evidence on #478, close it as `completed`, and tick #549's line.
