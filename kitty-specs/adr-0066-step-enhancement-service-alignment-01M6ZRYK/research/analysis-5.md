---
schema: analysis-findings/v1
findings:
  - id: A1
    severity: high
    category: inconsistency
    summary: "WP05 T024 step 3 adds validate-mode PROMPT_SLOT_NOT_EMPTY_UNVERSIONED warning findings. The pinned built-in has 5 non-empty slots, so FileSystemPackInspectorTest:679 (open-packs tag, assertThat(verdict.findings()).isEmpty() over built-in in FULL mode) goes red. That assertion edit is not in OD-10 rows 1-11, and WP05 has no listed edit on this WP01-owned file."
  - id: A2
    severity: high
    category: inconsistency
    summary: "DomainRoundTrip.writeAll writes each artifact through DomainRepositoryService.saveArtifact, then FilesystemPackStore.write, then newFile, which calls kind.fileName(id). The binding's refusing fileName (A12) therefore fails the write, so WP01 T005's fixture-level DomainRoundTripTest over an enhancing pack cannot pass. The adapter holds no PackLayout to 'pair through'. The OD-11 reason text cites this test, and :packs:wholePackRoundTrip will fail once #435 pins spdd."
  - id: A3
    severity: medium
    category: inconsistency
    summary: "WP04 T020 step 4 pins the rewritten reserved paragraph in ContractDescriptionsTest while the two slot codes are still reserved. WP05 then rewrites that paragraph (keeping only prompt_slot_missing reserved) but has no listed edit on ContractDescriptionsTest (WP04-owned), and the hot-files table does not record one."
  - id: A4
    severity: medium
    category: inconsistency
    summary: "WP04 flips prompt_fragment_invalid/drg_schema_error raised in T017's commit but edits errors.feature's raised-code table (OD-10 row 6) in T020, so the 'exactly these refusal codes are raised' scenario is red between commits. WP05 T023 does the flag and the rows in one commit."
  - id: A5
    severity: medium
    category: inconsistency
    summary: "Orphan exclusion differs between moments. WP04 skips reclassifying an orphan at staged load when the directory holds a yaml that 'carries a violation' (rule faults on held enhancements included), while WP03 facts, data-model and P8 exclude only refused or withheld files. So an orphan beside an EDGE_COUNT enhancement is COMPANION_ORPHAN at V but plain unmodelled at L."
  - id: A6
    severity: medium
    category: underspecification
    summary: "WP01 T004 lists only idIsFileStem, placedAtItsFileName, companionStructures, fileName, fileToken and schemaName overrides. It does not name hasCompanions(), companions(dso) or withCompanions(dso, contents). Without them the store never reads the fragments (they end up unmodelled), and CompanionFilesTest:284-288 requires hasCompanions to hold exactly when companions is overridden."
  - id: A7
    severity: medium
    category: underspecification
    summary: "WP05 T025 carries the warnings into RegistrationOutcome but, unlike LoadedPack and PackContents, does not say to keep the existing constructor. PackRegistrationMapperNoticesTest:36 and PackRegistrationMapperSourceTest:36 construct RegistrationOutcome and are outside WP05's owned and listed files."
  - id: A8
    severity: medium
    category: coverage
    summary: "WP04 edits web-transport RefusalHandler.java and names RefusalHandlerTest, but its gate commands and closeout step 5 (spotless/checkstyle for :kernel, :packs, :deployable) leave out :web-transport:test and :web-transport:spotlessCheck/checkstyle."
  - id: A9
    severity: low
    category: inconsistency
    summary: "WP04 T019's heading lists StepEnhancementBinding among the shared edits, which contradicts erratum E-4, WP04's own 'Files shared' section ('no edit') and its reviewer guidance."
  - id: A10
    severity: low
    category: inconsistency
    summary: "registration-diagnostics.fragment.yaml's prompt_fragment_invalid x-refusal draft lacks R-REL's operations {putPackRelationship: {status: 422}} and the matching description, yet contracts/README says the draft shows the entries 'as they read after phase 1'."
  - id: A11
    severity: low
    category: underspecification
    summary: "RefusalHandler.statusByOperation gives the same registrationStatusCodes() map to all 12 operations. Adding PROMPT_FRAGMENT_INVALID→422 'for putPackRelationship only' therefore needs a per-operation restructure, which WP04 T020 does not mention."
  - id: A12
    severity: low
    category: inconsistency
    summary: "The A8 rationale ('FULL mode would flag EDGE_COUNT') does not apply to the two SPDD tests (:385-401, :403-417): they assert listing/unreadableFiles and never call PackPolicy.judge. The setup is harmless but not needed for the reason given."
  - id: A13
    severity: low
    category: inconsistency
    summary: "Plan IC-01 and WP01 T002 say a bare action:software-dev/plan is refused pack_reference_unqualified 'because only the base declares the node'. That holds only if the base's graph declares the action node (declaredWithoutFile). Otherwise holdingPackId is empty and the bare form passes silently."
  - id: A14
    severity: low
    category: inconsistency
    summary: "quickstart.md's closeout runs './gradlew verifyAll' without the JAVA_HOME=temurin-25 and --no-daemon form that tasks.md's closeout prescribes."
counts: {critical: 0, high: 2, medium: 6, low: 6, info: 0}
---

## Specification Analysis Report

Mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK`, phase 1 (WP01–WP06), checked against the code at HEAD `5a3f9cae`. Every seam and line number below was read in the code. What the brief marked as binding (the ruled table, the errata, the Split section, and anything marked "record only — phase 2 (#438)") is not raised.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|---|---|---|---|---|---|
| A1 | inconsistency | high | WP05 T024 step 3; `FileSystemPackInspectorTest.java:670-679` (`validateModeOverTheBuiltInPackFindsWhatMainFound…`, `@Tag("open-packs")`); plan IC-03 gates; OD-10 | Validate mode will report 5 `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` warnings for the pinned built-in (one per software-dev prompt). `assertThat(verdict.findings()).isEmpty()` at :679 then fails inside `:packs:openPacksRoundTrip`. That is an assertion edit outside OD-10 rows 1–11, in a WP01-owned file where WP05 has no listed edit. | Take it to the operator. Option 1: an OD-10 row 12 (the :679 expectation becomes "exactly the 5 warnings, each at its prompt file"), added as a WP05 listed edit and to the hot-files table. Option 2: rule that validate-mode slot warnings never enter `PackVerdict.findings()`, and say where they go instead. |
| A2 | inconsistency | high | WP01 T005 step 3; plan IC-01 ("`DomainRoundTrip` … pairs through the held `PackLayout` place"); OD-11 reason text; `DomainRoundTrip.java:230-249` (`writeAll` → `to::saveArtifact`); `DomainRepositoryService.java:215-231`; `FilesystemPackStore.java:884-888, 1125-1145` (`newFile` → `kind.fileName`) | The whole-pack gate's adapter writes every artifact through `saveArtifact`, which creates it at `kind.fileName(id)`. With A12's refusing `fileName`, `writeAll` returns a failure and nothing is written. The required fixture-level `DomainRoundTripTest` over an enhancing pack cannot pass, and `writtenByKindAndId` has no layout to pair through. At #435 `:packs:wholePackRoundTrip` fails the same way. | Decide, and record it in the plan, how the gate adapter writes a step enhancement. Options: a place-aware write seam, which would need an edit to `DomainRepositoryService` or `FilesystemPackStore` and so an ownership/listing decision; the adapter writing through `WholePackStore.write` with the layout; or the fixture test and the OD-11 text dropping their `DomainRoundTrip` claim. Then fix WP01 T005 and the OD-11 sentence. |
| A3 | inconsistency | medium | WP04 T020 step 4; WP05 "Files shared" and T026; tasks.md hot-files table | WP04 pins the reserved paragraph while it still names the two slot codes as reserved. WP05's rewrite breaks that pin, but WP05 has no listed edit on `ContractDescriptionsTest`. | Either have WP04 pin only sentences WP05 keeps (`prompt_fragment_invalid` raised, the C-07 reword, `prompt_slot_missing` reserved), or add a WP05 listed edit under D19 to the WP prompt and the hot-files table. |
| A4 | inconsistency | medium | WP04 T017 step 3 vs T020 step 3; WP05 T023 step 3; `errors.feature:171-238` | The flags flip in one commit and the raised-code table changes in a later one, so the contract suite is red in between. WP05 does both in one commit. | Move OD-10 row 6 (`prompt_fragment_invalid`, `drg_schema_error`) into T017's raise commit. |
| A5 | inconsistency | medium | WP04 "Files shared" (`WholePackStore` bullet) and T019 step 2; WP03 T013/T014 step 4; data-model PromptFragment orphan row; research P8 | Validate and load disagree on which directories are excluded from orphan judgement. | Narrow the staged-load exclusion to yaml files refused by the store (a remapped `companion` violation) or withheld (`STEM_REPEATED`), matching the facts, and add a twin with an orphan beside an `EDGE_COUNT` enhancement at both moments. |
| A6 | underspecification | medium | WP01 T004 step 1; `KindBinding.java:195-219`; `CompanionFilesTest.java:284-288`; `WholePackStore.companionFiles` (:623-645) | The binding's companion overrides are not named. Without `hasCompanions()` true, `companions(dso)` (Markdown form, `prompt-fragment` structure) and `withCompanions`, the fragments are never read or written back. | List the three overrides in T004 (the DSO carries the bodies). `CompanionFilesTest` already holds the pairing. |
| A7 | underspecification | medium | WP05 T025 step 3; `PackRegistrationService.java:121-147, 606-608`; `PackRegistrationMapperNoticesTest.java:36`; `PackRegistrationMapperSourceTest.java:36` | Adding a `RegistrationOutcome` component breaks two test call sites outside WP05's files. | Tell WP05 to keep the existing constructor (empty diagnostics), as it does for `LoadedPack` and `PackContents`. |
| A8 | coverage | medium | WP04 Gates and T020 step 5; tasks.md Closeout step 5 | `RefusalHandler` and `RefusalHandlerTest` live in `:web-transport`, which no WP04 command or closeout style gate runs; only the final `verifyAll` would catch a break. | Add `:web-transport:test :web-transport:spotlessCheck` to WP04's commands, and `:web-transport` to the closeout step 5 module list. |
| A9 | inconsistency | low | WP04 T019 heading | The heading names `StepEnhancementBinding` among the shared edits, against E-4. | Drop it from the heading. |
| A10 | inconsistency | low | `contracts/registration-diagnostics.fragment.yaml` (`x-refusal.prompt_fragment_invalid`); contracts/README "registration diagnostics" bullet | The draft predates R-REL. | Add the `operations` entry and a description clause for `putPackRelationship`, or say the draft omits R-REL. |
| A11 | underspecification | low | WP04 T020 step 5; `RefusalHandler.java:67-94` | One status map is shared by all 12 operations. | Say that `statusByOperation` gets a per-operation copy for `putPackRelationship`. |
| A12 | inconsistency | low | plan gates table and A8 ruling text; WP04 T018 step 5; `FileSystemPackInspectorTest.java:385-417` | Neither test judges, so no `EDGE_COUNT` could reach their assertions. | Keep the setup if wanted, but correct the stated reason. |
| A13 | inconsistency | low | plan IC-01 "edge target is pack-qualified"; WP01 T002 step 1; `DomainRepositoryService.declaredWithoutFile` (:579-597); `PackCompositionResolver.checkReferences` (:463-475, 505-515) | The bare-form refusal depends on a graph node declaration. | Reword: "refused when the base's graph declares the action node", or have fixtures declare it. |
| A14 | inconsistency | low | quickstart.md "Closeout" vs tasks.md Closeout step 2 | Different command forms for the same gate. | Align quickstart with tasks.md. |

## Coverage Summary Table

| Requirement | Has task? | Task IDs / WPs | Notes |
|---|---|---|---|
| FR-001 Hold step enhancements | Yes | T003–T006 (WP01) | Write-back through the gate adapter is blocked (A2); companion overrides not named (A6). |
| FR-002 Hold fragment companions | Yes | T003, T005 (WP01) | A6. |
| FR-003 Composition and selection | Yes | T002, T006 (WP01) | — |
| FR-004 Fragment content checks | Yes | T012–T013 (WP03), T016–T019 (WP04) | — |
| FR-005 Structure checks | Yes | T014 (WP03), T016/T019 (WP04) | A5 (orphan exclusion). |
| FR-006 Identity, target and R-OVR/R-REL | Yes | T015 (WP03), T016–T019 (WP04) | A11 (handler map). |
| FR-007 Base-prompt slot checks | Yes | T008–T011 (WP02), T022–T024 (WP05) | A1 (validate-mode warning vs :679). |
| FR-011 Load codes raised and diagnostics | Yes | T017, T020 (WP04); T023, T025, T026 (WP05) | A3, A4, A7. |
| FR-012 Phase-1 rulings recorded | Yes | T021 (WP04), T027–T030 (WP06) | — |
| NFR-001 Fingerprint stability | Yes | T001, T006 (WP01) | — |
| NFR-002 Corpus stays loadable | Yes | T019 (WP04), T022/T024 (WP05) | A1 turns the open-packs gate red. |
| C-001 Contract versioning | Yes | T007, T021, T026 | — |
| C-002 Round trip by meaning | Yes | T003, T005 | A2. |
| C-003 Graph-only relationships | Yes | T003, T015 | — |
| C-004 PR C boundary | Yes | All WPs' constraints | No PR C-owned file found in any write scope or listed edit. |
| C-005 Out of scope | Yes | WP06 | — |
| C-006 Layout kept | Yes | WP01, T027 | — |
| C-007 Test first | Yes | Every WP | — |
| SC-002 to SC-006 | Yes | WP01, WP03–WP06 | — |

## Charter Alignment Issues

None found. Every charter rule is met: the adversarial squad runs at the plan point and before merge, followed by operator approval; `verifyAll` runs on Java 25; `:packs:wholePackRoundTrip` is required; the docs checks run; every contract change has a dated fragment (ADR-0022); no dependency is added; the `open-packs#TBD` placeholder is replaced before merge; no new ADR is written.

A1 and A2 are not charter violations. They are WP instructions that would leave a merge gate red (`:packs:openPacksRoundTrip`, and the whole-pack gate once #435 lands) unless they are resolved before implementation.

## Unmapped Tasks

None. T001–T030 each map to a WP, and each WP maps to FR, NFR, C or SC IDs.

## Metrics

- Work packages: 6 (WP01–WP06); subtasks: 30 (T001–T030)
- Functional requirements: 9 (FR-001–FR-007, FR-011, FR-012); non-functional: 2; constraints: 7; success criteria: 5
- Requirement coverage: 100% have at least one task
- Findings: 14 (critical 0, high 2, medium 6, low 6)
- Charter conflicts: 0

## Next Actions

1. **A1 (needs the operator).** Rule how validate-mode slot warnings meet `FileSystemPackInspectorTest:679`: an OD-10 row 12 as a WP05 listed edit, or warnings kept out of `PackVerdict.findings()`. Record the ruling in the plan's ruled table, in WP05 and in the hot-files table.
2. **A2 (needs a design call).** Decide how `DomainRoundTrip` writes a step enhancement, given the refusing `fileName`: a place-aware seam (with its owner and listing), a write through `WholePackStore.write`, or dropping the claim. Then amend WP01 T005 and the OD-11 sentence.
3. **Medium items to fold before implementation:**
   - A3: settle the `ContractDescriptionsTest` pin or listing.
   - A4: move OD-10 row 6 into T017's commit.
   - A5: narrow the orphan exclusion at staged load.
   - A6: name the three companion overrides in WP01 T004.
   - A7: keep the `RegistrationOutcome` constructor.
   - A8: add `:web-transport` to WP04's commands and the closeout.
4. **Low items:** wording fixes (A9–A14) whenever the artifacts are next touched.
5. Then re-run `/spec-kitty.analyze`, or go straight to implementation if the operator accepts the A1 and A2 resolutions as written.
