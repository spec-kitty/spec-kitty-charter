---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-12T01:00:56Z'
reviewer_agent: claude-reviewer
wp_id: WP04
---

# WP04 review 1: org-charter.yaml and active_charter are retired (#431)

Reviewer: Reviewer Renata (read-only)
Range: `57bd4dd5..0f5b5270` (`daec03be` retires the file, `0f5b5270` removes the key)
Worktree: `/home/user/spec-kitty-charter/.worktrees/slice-b-real-packs-register-01M7649C-lane-c`

## Verdict: REJECT

There is one MEDIUM finding (M1): a canonical contract schema still describes `org-charter.yaml` as a live document. No other WP owns that file. The fix is one sentence. Everything else meets the WP prompt, FR-009 to FR-011, the charter and the AGENTS.md rules, and every gate is green.

## Verification run

| What | Where | Result |
|---|---|---|
| `:packs:spotlessCheck :kernel:check :packs:test` | `daec03be`, detached scratch worktree | BUILD SUCCESSFUL |
| `:packs:check :contracts:check :deployable:test --tests '*CharterContractSuite*'` | `daec03be` | BUILD SUCCESSFUL |
| `:kernel:check :packs:check :packs:wholePackRoundTrip :contracts:check :deployable:test --tests '*CharterContractSuite*'` | `0f5b5270` (tip) | BUILD SUCCESSFUL. Deployable: 716 tests, 0 skipped, 0 failed. Both new registration scenarios ran (not `@WIP`, not skipped). |
| Pin recapture proof: base `57bd4dd5` with only `roundtrip-fixtures/pack-a/pack.yaml` and the two re-pinned test files checked out from the tip, then `FileSystemPackSourcesTest` and `PackCommitStoreTest` | base digest and revision code | BUILD SUCCESSFUL |
| Independent `manifest_hash` recompute (Python: SHA-256 of LF-normalised bytes per constituent, then JCS-sorted compact JSON without `manifest_hash`) | `example-packs/built-in` at the tip | every `content_hash` matches; computed `a905fd40…19c6` equals the stated value |
| `git diff --name-only 57bd4dd5..0f5b5270 -- 'service/*/src/main'` digest, revision and manifest code | | `PackFileSet`, `FileSystemPackSources`, `PackCommitStore` and `PackManifests` are unchanged |
| Signatures | both commits | `gpgsig` present (SSH). No trailers, no model identifiers in messages or diff. Author matches the repo's history. |
| The scratch worktree | | removed |

## Findings

### M1 (MEDIUM): canonical `charter.schema.json` still names `org-charter.yaml` as a document in scope

- Evidence: `contracts/packs/schemas/charter.schema.json:7` reads: "...rejected with `RETIRED_PACK_FIELD` ... for both the project `charter.yaml` and an `org-charter.yaml`; there is no alias (C-001)."
- Why it matters:
  - The implementer's premise was "an imported core schema, left alone". `contracts/packs/README.md:9-11` contradicts it: "These schemas are canonical in this repository and are edited in place; core consumes them". The core import was a one-time event.
  - The file lies in WP04's `authoritative_surface` (`contracts/packs/schemas/`).
  - The WP's Validation grep allows hits only in "the retired rule, its fixture, the agreement test and the fragment". This hit falls outside that set.
  - The charter's canonical-model rule says the old shape is replaced outright.
  - The schema page `docs/architecture/pack-schemas/charter.md` is generated from this schema.
  - WP08's `owned_files` do not include this schema, so nobody else will fix it.
- Fix: in `charter.schema.json:7`, replace "for both the project ``charter.yaml`` and an ``org-charter.yaml``" with "for the project ``charter.yaml``". This is a description-only change. Optionally add one clause to `contracts/charter/changes/2026-11-11-org-charter-is-retired.md` noting it. Then re-run `:contracts:check :packs:test`.
- Alternative, if the operator prefers: add `contracts/packs/schemas/charter.schema.json` to WP08's `owned_files` and the T038/T039 list. This review still treats WP04 as the right owner, because the file sits in WP04's authoritative surface and DoD grep.

### L1 (LOW, pre-existing, out of scope): load and registration do not name the unknown descriptor key

- Evidence: `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java:670-672` drops the `FileRead.Unreadable` detail and throws `"pack.yaml of <id> cannot be read"`. `WholePackStore.java:730` turns that into the 422 violation.
- Impact: this applies equally to every unknown key, including the earlier retired `accompanies_doctrine_pack`. See the planted case `control-retired-pack-key-is-a-warning` and `CHANGELOG.md:481-485`. It is a diagnostics gap, not a correctness, data or security risk.
- Fix: none in WP04. If the operator wants one, a follow-up can carry the reader's "unknown key 'x'" detail into the refusal message. That would apply to all descriptor keys, not to `active_charter` alone. Under `docs/development/issue-tracker.md`, it is not filed unless the operator asks.

### L2 (LOW): commit 1 is breaking without its changelog fragment

- Evidence: `daec03be` deletes `org-charter.schema.json` and makes the file a 422 `retired`. The `### Breaking` fragment arrives only in `0f5b5270`.
- Impact: `checkContractChangelogFragments` passes at `daec03be`, so this concerns bisect and readability only.
- Fix: optional. If the commits are re-cut for M1, move the fragment's file half into commit 1.

### L3 (LOW, cosmetic): new over-long lines

- Evidence: `contracts/packs/file-kinds.yaml:266` (143 cols), `FileSystemPackSourcesTest.java:264` (145 cols), `PackCommitStoreTest.java:356` (126 cols). The surrounding text wraps at about 120 columns.
- Fix: optional re-wrap. Spotless and checkstyle accept the lines as they are.

## Rulings on the six self-reported points

1. **`active_charter` refusal: satisfies the spec; the missing key name is a separate, pre-existing gap (L1).**
   - Spec call 6 asks for "the closed descriptor's unknown-key refusal, not a retired rule of its own". That is exactly what happens. The reader names the key: `PackDescriptorReaderTest:228` now parameterises `active_charter` beside `accompanies_doctrine_pack`. Validate mode names it too (`OrgCharterRetirementTest:179`).
   - The load (500 `pack_unreadable`) and registration (422 `validation_failed`, one `pack_unreadable` at `pack`) give the same treatment as `accompanies_doctrine_pack`. That key has been refused this way since #195.
   - FR-011's agreement test is about the retired file. The four paths do agree for the file: all four name it `retired` with the same reason.
   - User Story 3, acceptance 2 ("when it is read ... refused as an unknown key") holds.
   - Registrants are told through the `### Breaking` fragment. A key-naming message would serve every unknown key and belongs to a follow-up the operator chooses. It is not WP04 scope.
2. **`PackFileSetTest` (file removed from `TREE`): acceptable.**
   - The NFR-002 pin guards "a pack without payloads keeps its pre-payload fingerprint". With `org-charter.yaml` kept, the retired tail would legitimately move the fingerprint for a reason unrelated to NFR-002.
   - The retired-tail behaviour the prompt wanted is asserted instead in the stale-pack test (`PackFileSetTest:343-362`). That test now plants `org-charter.yaml` and checks `listing().retired()`, `digested()` (in the tail) and the unretiring table.
   - `OrgCharterRetirementTest:121-129` also checks the tail and the census.
   - No assertion was weakened.
3. **Re-captured pins: justified and verified.**
   - At the base digest and revision code, the only input change (`pack-a/pack.yaml` losing `active_charter`) reproduces the new `pack-a` fingerprint, `PINNED_ALL` and `PACK_A_REVISION_ON_MAIN`. The tests pass at `57bd4dd5` with just those three files from the tip.
   - `pack-descriptor-only` is unchanged, as expected.
   - The digest, revision and manifest code is untouched.
   - `manifest_hash` and all constituent hashes recompute independently to the committed values. Only the glossary pack's `content_hash` moved, from the reworded "Charter Pack" definition.
4. **`charter.schema.json`: not the right call (M1).** The schema is canonical here and edited in place (`contracts/packs/README.md:9`), not a frozen core import. Its description presents `org-charter.yaml` as current. Fix as in M1.
5. **Docs left for WP08: covered.**
   - `WP08-record-and-docs.md` `owned_files` and subtasks T038/T039 name: ADR-0036 (decision 6 and the `ORG_CHARTER_SCHEMA_ERROR` row); `logical-model.md`; `descriptors.md`; `overview.md:57`; `resolution-model.md:41`; the three `.puml`/`.svg` diagrams; `divergences.yaml` and `divergence-register.md` (DIV-010 evidence at `divergences.yaml:185,187`); `site/.contextive/definitions.yaml`; `scripts/usability/README.md` and `service-usability-run.sh`.
   - Its DoD says "no page names ... `org-charter.yaml` or `active_charter` as current".
   - The one gap is `charter.schema.json`, which is not a doc. See M1.
6. **Commit split, `PROMISES.md` and planted c10.**
   - Each commit is green on its own (runs above).
   - Marking `PR-CHARTER-SHARING-003` as retired, with #381 closed as not planned, is correct: the guarantee hung on the deleted schema's `skill_namespace`.
   - Deleting `c10-org-charter-schema-version-quoted` with its `core-verdicts.json` record and its `core_verdicts.py` entry is right, and the WP prompt (T019) mandates it.
   - A planted case records core's verdict on a schema rule. The rule is gone, and the retired-file refusal has no core verdict to compare: core still reads the file, which is a register matter for WP08.
   - Retired-file behaviour is proven by `OrgCharterRetirementTest`, `RetiredFileFixture` and `RetiredFilesTest`. That matches how WP02 handled `pack.md` and `PROVENANCE.md`, which got no planted cases.
   - DIV-010 keeps three `c10-agent-*` plants, so its register id stays exercised.

## Also judged

- **Refusal paths.**
  - Classification gives `Retired("*/org-charter.yaml", reason)`. Validate mode gives `artifact_schema_error` "is retired: <reason>". The load gives a `retired` violation. Registration gives 422 `validation_failed` with one `retired` violation at `org-charter.yaml` and no `unmodelled`.
  - All four agree in `OrgCharterRetirementTest:62-129` and in the HTTP scenario `whole-pack-registration.feature:425-442`.
  - `Org-Charter.yaml` gets the case-variant refusal (`OrgCharterRetirementTest:68-70`, `RetiredFileFixture:87`).
  - `assets/org-charter.yaml` stays a payload (`PackFileKindTest:257`). `sub/org-charter.yaml` matches no retired rule (`RetiredFileFixture:73`).
  - `GeneratedPackTree` turns the decoy into `RETIRED_ORG_CHARTER`, as the prompt asked.
- **Coordination with #496.**
  - `PackPolicy.java` changes are limited to `DESCRIPTOR_KEYS` (:91-92), the two javadoc blocks (:925-929, :951-955) and `codeFor` (:1037-1040).
  - `PackFindingCode.java` only loses the constant.
  - `CharterRefusalCode.yaml` is untouched.
- **Contract.**
  - `contracts/charter/changes/2026-11-11-org-charter-is-retired.md` is a single `### Breaking` block. It names core's seam (stop reading `org-charter.yaml` and `active_charter`, apply the governing preset's floor) and says stored packs read as `pack_unreadable` until re-registered.
  - The new scenarios are untagged.
  - `journey-04` keeps its two scenarios `@WIP`, reworded to the preset model.
  - Contract lint and compatibility are green.
- **Brownfield.**
  - 28→27 schemas (`PackSchemasTest:67`) and 28→27 `files` rules (`PackFileKindTest:348`) follow exactly from the one removed rule.
  - The deleted non-finite `schema_version` test was specific to the deleted schema. Non-finite coverage remains for other fields (`PackSchemasTest:426-472`).
  - `PackRoundTripTest` and `PackDescriptorReaderTest` lose `active_charter` because the field no longer exists, not because it was excluded from a check. The exact property list (`PackDescriptorReaderTest:250-258`) still pins every remaining key.
  - No tolerance or legacy reader was added: `PackPaths` has two components, and the second constructor is gone.
- **Fixtures and scenarios.** Every Background `pack.yaml` and every example or round-trip fixture carries the canonical shape. The only `active_charter` left in `service`, `contracts` and `scripts` is in error-handling tests and the fragment.

## Required to approve

- M1: drop the `org-charter.yaml` mention from `contracts/packs/schemas/charter.schema.json:7`, or have the operator move the file into WP08's scope. Then re-run `:packs:test :contracts:check`.
