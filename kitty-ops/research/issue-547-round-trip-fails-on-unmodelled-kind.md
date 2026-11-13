# Op 01M7B1EZ2S942DRRGVCKK6PGC1: the round trip fails loudly on a kind it does not model (#547)

Profile: java-jenny. Kind: test quality and bug fix. Size: S to M. Parent epic: #549.

## Why an Op, not a Mission

The change is confined to the whole-pack gate's test harness (`service/packs/src/test`, `src/driftTest`), its
checked-in expectation lists, the gate's workflow and one guide. It adds no wire code, schema, contract operation or
production behavior, so there is no spec or work-package split to make.

## Why it lands now

Open-packs PR #256 waits for this merge commit to pin charter (operator ruling). Under ADR-0070 decision 4, as amended
on 2026-11-13 (PR #548), open-packs runs charter's `:packs:driftTest` from its pinned checkout as a required gate. That
gate would inherit the silent pass over an unmodelled kind, so #547 lands before the first pin (operator comment on
#547, 2026-11-13). Epic #549's #555 later moves these classes into `pack-validation`, so this lands first.

## Grounding

- **Drift test.** `OpenPacksDriftTest.drift` ran `WholePackRoundTripTest.observe`, which loops over
  `roundTrip.roundTrip(...)`'s outcomes only (`WholePackRoundTripTest.java` around lines 765-805 on `main`). A census
  file with no outcome produced no line. `WholePackExpectation.evaluate`, which says `no adapter models kind X`
  (`WholePackExpectation.java` around line 325), was never called by the drift test.
- **Gate kind check.** `theBuiltInPackRoundTripsAsExpected` asserted `modelledKinds == bound`, where `bound` was
  `KindBindings` plus nine names typed into the test (lines 113-132). That compares the adapter with the registry,
  not with `contracts/packs/file-kinds.yaml`. The table has 27 schemas; the round trip models 22 of them, plus the two
  payload kinds (`asset-payload`, `template`). The five it does not model are `charter`, `toolguide-guide`,
  `mission-step-prompt`, `mission-step-guidelines` and `prompt-fragment`.
- **Example packs.** The gate round-tripped `built-in` only. `org-base` had one hand-written test (#545), and
  `project` had none.
- **The expectation lists were single-pack.** File entries and exclusions were pack-relative. `empty_kinds` excused a
  kind the one pack lacked. A stale `kinds:` entry was one with "no file of this kind in the census". None of these
  could be shared across three runs.

## Squads

**Tracked issues.**

| Issue | Relation | Disposition |
| --- | --- | --- |
| #477 (`impacts` strength) | Its red now shows in two packs: the `org-base` fragment and `project/project.graph.yaml`. The second was never round-tripped; #477's own body says the `impacts` example moved there because the round trip did not cover `project`. | Both pinned in `expected-red.yaml` with the whole refusal text, issue #477. They expire (`UNEXPECTEDLY_GREEN`) when #477 lands. No field is excluded. |
| #555 (move into `pack-validation`) | Moves the same classes; names #547 as landing with it or before it. | This lands first. #555 moves the result as is. No overlap with open PR #561 (#553), which touches none of these files. |
| #559 (publish the packs contract) | Consumers pin `file-kinds.yaml`. | Unaffected. The new schema check reads the bundled table, so a published table's schemas are held to the round trip. |
| open-packs #252 (round trip as an open-packs gate) | The consumer of this fix. | The drift output changes form (`UNEXPECTED_RED <path> (<kind>): …`). Nothing parses it, either in charter's `open-packs-drift.yml` or in open-packs' workflows. |
| open-packs #255 | Could not be read: this session has no GitHub API access to open-packs. | Not checked here; the orchestrator should check it. |
| Slice C (#467) | No open PR. | No overlap. |

**Similar surfaces.** A read-only sweep looked for other places that check "every file" but iterate only what an
adapter produced. Its findings are recorded below under "Similar surfaces".

## Decisions

1. **The drift test judges every pack with no expectation** (`WholePackExpectation.none()`, done-when 1). This takes
   the route the issue suggests. A census file that is not excluded and has no outcome fails as
   `UNEXPECTED_RED <path> (<kind>): no adapter models kind <kind>`, or as `… the round trip gave no outcome for this
   file` when the kind is modelled. The judgement lives in `WholePackDrift` (test source set), so the gate's
   planted-break control runs the same code as the drift test.
2. **The schema check ties the gate to the table** (done-when 2). `schemaProblems` fails on a `files` schema that is
   neither modelled nor listed in `kinds:`, and on a `kinds:` entry that is not a schema of the table. The modelled
   check stays in `evaluate` (`BASELINED_MODELLED_KIND`). `REQUIRED_KINDS` and the `modelledKinds == bound` assertion
   are removed: the schema check supersedes them, and `bound` restated the adapter's own list.
3. **One parameterized gate test over `ExamplePacks.names()`** (done-when 3). It replaces
   `theBuiltInPackRoundTripsAsExpected` and `theOrgPackRoundTripsWholeWithItsFragment`. To share one set of lists
   across three runs:
   - file entries, known failures and exclusions name their pack as the first path segment;
   - `forPack(name)` scopes them to one run;
   - `unscopedEntries` fails an entry that names no example pack.
   - `kinds:` names schemas, so it is not stale against one pack's census. Its staleness moves to the schema check.
4. **`empty_kinds` is removed, not kept beside the new rule** (charter: canonical shape, no legacy reader). With three
   packs, "a modelled kind with no file" is a property of the packs together (ADR-0070: every kind is in some example
   pack). `emptyKindProblems` checks it across the packs. A single run over an empty census fails as `EMPTY_PACK`, so
   one run still cannot pass vacuously.
5. **A refused file entry pins the whole refusal.** Before, `at:` compared only the text before the first `": "`,
   which for any refusal is `refused`, so any refusal would have matched. Known failures already pin the whole
   refusal, and file entries now use the same rule. This keeps what the removed `org-base` test asserted (`place=[edges,
   2]` and the `impacts` detail) without a hand-written test per pack.
6. **An unpaired written file is allowed only as the copy of a file pinned as refused.** That generalizes the removed
   `org-base` test's `containsExactly(drg/fragment.yaml)`.
7. **The report becomes one file per pack**, `build/reports/whole-pack/<pack>.md`. `whole-pack.yml` shows each in the
   job summary and uploads all of them.
8. **The built-in asset, template and document checks (#193, #260) keep their own test.** They are about what
   built-in holds, not a per-pack copy of the gate.

## Planted break (done-when 4)

`ORG_FRAGMENT` was removed from `DomainRoundTrip.MODELLED`, the build was run, and the file was restored:

- `:packs:wholePackRoundTrip`: 15 tests, 3 failed.
  - `everyFileKindsSchemaIsModelledOrListedWithAReason`: `UNMODELLED_SCHEMA org-fragment: …`.
  - `everyExamplePackRoundTripsAsExpected[org-base]`: `CHANGED_RED drg/fragment.yaml (org-fragment): … now: no
    adapter models kind org-fragment`.
  - The committed control `anUnmodelledOrgFragmentFailsTheDriftAndTheSchemaChecks`: its faithful branch is now
    unmodelled too.
- `:packs:driftTest` over a checkout holding the example packs: failed with
  `org-base: UNEXPECTED_RED drg/fragment.yaml (org-fragment): no adapter models kind org-fragment` and
  `org-base: written but unpaired drg/fragment.yaml`.

The committed control proves the same thing on every gate run, with a decorator that drops the kind's outcomes.

## Open-packs at `736e613` (current `main`)

With the fix, `:packs:driftTest` fails with exactly the three manifest reds that the 2026-11-13 drift research
expected after #545: `built-in`, `foundation` and `writing` `pack-manifest.yaml`, all spec-kitty-open-packs#250.
There is no new red. The new judgement only adds problems, so no earlier red is hidden.

## Similar surfaces

A read-only sweep of `service/` found nothing HIGH. No correctness gate passes vacuously today.

| Finding | Severity | Disposition |
| --- | --- | --- |
| `ExamplePackCoverageTest.schemaGaps` lets a schema skip its example file when the store reports a placeholder as unmodelled (`leftUnmodelled`), so the excuse comes from what the store does, not from a list. A new unbound schema with no example would pass that test. | MEDIUM, mitigated | Not done here. The new `everyFileKindsSchemaIsModelledOrListedWithAReason` fails on the same input, so the suite still goes red. Replacing the excuse with an explicit allowlist is out of #547's scope; it is recorded in the PR body. |
| `ExamplePackCoverageTest.fileGlobs` silently drops an `ArtifactKind` whose token is spelled differently from its schema. | LOW | Dropped (rule 1). The table's own vocabulary tests pin the lists. |
| `WholePackExamplesRoundTripTest.meaning()` leaves out the policy, and its round trip compares neither the reloaded violations nor `unmodelled()`. | MEDIUM, mitigated | Not done here. `PolicyRoundTripTest` and `ExamplePacksTest` cover the store write and unmodelled files. #555 rebuilds this round trip in `pack-validation`. Recorded in the PR body. |

No issue is filed: none of these is an uncovered correctness, data or security risk (`docs/development/issue-tracker.md`, rule 1).

## Adversarial squad (pre-merge)

One reviewer read the diff without my conclusions.

| Finding | Severity | Disposition |
| --- | --- | --- |
| A `kinds:` entry excused every census file of its kind as expected red, and with the per-census kind ratchets removed nothing made it stale. A stray `charter.yaml`, or a companion whose owner stopped naming it, would have passed. | MAJOR | Folded. A kind entry now answers `schemaProblems` alone and excuses no file. A file of an unmodelled kind is red in every run, and a file entry for such a file fails as `FILE_ENTRY_UNMODELLED`. Unit tests cover both. |
| `service-build.md` still promised a kind-level `UNEXPECTEDLY_GREEN`. | MINOR | Folded: the wording now matches the ratchets. |
| `ExamplePacks.names()` is hard-coded, so a fourth example-pack directory would not be gated. | MINOR | Folded: the union test asserts the directories equal the gated packs. |
| The `charter` entry cites #547, which this PR closes. | MINOR | Kept. The entry records why the schema is unmodelled and excuses no file, so it needs no owner to turn it green. |
| A stale report from an earlier local build can survive in the report directory. | MINOR | Dropped: CI checks out fresh. |

After the fold, the planted break makes the `org-base` run fail as `UNEXPECTED_RED drg/fragment.yaml (org-fragment):
no adapter models kind org-fragment` and `FILE_ENTRY_UNMODELLED drg/fragment.yaml`, not as `CHANGED_RED`. The
committed control is unchanged.
