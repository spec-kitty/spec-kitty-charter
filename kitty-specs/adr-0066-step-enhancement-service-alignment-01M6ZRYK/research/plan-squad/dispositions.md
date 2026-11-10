# Plan-point squad: dispositions

Mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK`, branch `issue-424-428-adr-0066-service-alignment`,
HEAD `92026b5d`. Dispositioned by architect-alphonso on 2026-11-09, under the findings-disposition contract of
`adversarial-squad-deployment`: every finding gets exactly one disposition, and none is dropped.

## Inputs and method

- **Inputs:** [contract.md](contract.md) (C-01..C-15, reviewer-renata), [architecture.md](architecture.md) (A1..A13,
  paula-patterns) and [tests.md](tests.md) (T-01..T-21, debugger-debbie).
- **Method:** each finding was checked against the code before it was folded.

## Disposition vocabulary

- **folded:** the finding was accepted, or the design changed in another way that resolves it. The table names where
  the change landed.
- **operator item OD-n:** the finding needs a ruling, and is listed in plan.md, section "Open operator decisions (plan
  point, 2026-11-09)". The plan builds to no option of it, except where it marks a build-to default.
- **rejected:** the finding is wrong, with the evidence. None was rejected.

Where a reviewer's evidence was partly off but the fix stood, the table says "evidence corrected".

## Counts

| Disposition | Contract | Architecture | Tests | Total |
|---|---|---|---|---|
| folded | 10 | 9 | 17 | **36** |
| operator item | 5 | 4 | 4 | **13** |
| rejected | 0 | 0 | 0 | **0** |
| **Total** | 15 | 13 | 21 | **49** |

## Table

| Id | Sev. | Summary | Disposition | Artifact section changed |
|---|---|---|---|---|
| C-01 | BLOCKER | No operation is named as answering the three load codes; FR-011 and SC-004 can be met without the codes reaching the wire | operator item **OD-3**. The facts were verified and folded: reads answer `pack_unreadable`; `offWireRules` forbids calling `PackFindingCode.refusal()` outside `PackFinding`; the `drg_schema_error` precedent; the stale javadoc and `errors.feature` prose | plan OD-3, IC-04 (raised-code lists, SC-004 wording); contracts/README.md; data-model.md (load-fault note) |
| C-02 | MAJOR | N1(a) as written neither reports every fault nor names the file; the `_shared` texts contradict it | operator item **OD-3**. The mechanism is folded as the build-to default: per-file violation, kind = token, no throw; frontmatter mapped to `prompt_fragment_invalid`; `_shared` amendments conditional on OD-3 (a) | plan Structure Decision, IC-02 "Where" and companion faults, IC-04 `_shared`; research P3; contracts/README.md |
| C-03 | MAJOR | `prompt_slot_missing` and FR-009 are judged only on reads, so a breaking registration succeeds and then every read answers 500 | operator item **OD-4** | plan OD-4, IC-05, IC-06 "At registration"; contracts fragment (commented `operations`); data-model.md |
| C-04 | MAJOR | The d5 key is "the pack's name", but the answer exposes only `packId` | folded: required `packName` (descriptor name), key (level, descriptor name, id), example with names that sort opposite to ids. Verified: ADR-0066 d5; qualifiers resolve by descriptor `name` | plan Technical Context NFR-003, IC-05, IC-07; research P6; data-model.md; contracts fragment schema and example; contracts/README.md; quickstart Flow 3 |
| C-05 | MAJOR | The plan dropped D5's binding sub-ruling `_links.prompt` | folded: planned after PR C, landing with the built read (rel row, `RelationTableParityTest`, link and example). Verified: grounding §6 D5 | plan IC-05, IC-07, IC-08, Source tree; research D5; contracts fragment (trailing note); contracts/README.md |
| C-06 | MAJOR | N3: `404 mission_type_not_found` for a mission type held but not in force breaks the code's meaning and the dispatch rule | operator item **OD-5** | plan OD-5, IC-05, IC-07 step 1; contracts fragment description (placeholder); data-model.md |
| C-07 | MINOR | N2 (only if D14 is "inherit"): not `artifact_locked`; follow C21 and `override_not_permitted` | operator item **OD-2** (D14 sub-question). Folded whatever the ruling: the reword of `CharterRefusalCode.yaml:165-166` | plan OD-2, IC-04, IC-06; research D14; contracts/README.md |
| C-08 | MINOR | The shared `OfferingUnreadable` would advertise `prompt_slot_missing` on operations that never build the merge | folded: each operation that can answer the code names it; the shared description is scoped to "operations that build the layer merge" | plan IC-05; contracts fragment comment; contracts/README.md |
| C-09 | MINOR | The 400 description omits `missionType` and `action` | folded: all three parameters, plus scenarios. Verified: `Slug` pattern and `maxLength` | plan IC-05 (path, scenarios); contracts fragment description; data-model.md; contracts/README.md |
| C-10 | MINOR | `EffectivePromptSlot.slot` would fail `resource-property-cited` | folded: `allOf` wrapper with a description and `x-derived`. Verified: ruleset.yaml:384-396 | contracts fragment `EffectivePromptSlot`; plan IC-05, gates table; contracts/README.md |
| C-11 | MINOR | Contract and docs surfaces that list operations or raised codes are missing | folded: headers, `service-contract.md`, `http-status-codes.md`, `rest-resource-conventions.md`, `errors.feature` | plan Source tree, IC-04, IC-05 surfaces, IC-08; contracts/README.md |
| C-12 | MINOR | The IC-04 `### Changed` and `### Breaking` fragments are incomplete | folded: registration succeeds, the write keeps files, effective lists and the skipped report carry items; Breaking names core and the read-side effect | plan IC-04 fragments; contracts/README.md |
| C-13 | MINOR | The contract's `prompt_slot_missing` wording already takes D13 branch A, and the fragment changes it silently | folded, evidence corrected. The fragment's x-refusal text equals the current x-refusal entry (`CharterRefusalCode.yaml:725-729`); "active or inactive" is in the reserved paragraph (:164). That paragraph keeps the phrase until D13 | plan IC-04, OD-1; contracts fragment comment; contracts/README.md |
| C-14 | NIT | `enhancement` says "(step_enhancement:<id>)" | folded: "the declared id, without a kind prefix" | contracts fragment; data-model.md |
| C-15 | NIT | The `prompt_slot_not_empty` description says "a warning otherwise" | folded: points to `DiagnosticCode.prompt_slot_not_empty` | contracts fragment x-refusal; plan IC-04; contracts/README.md |
| A1 | BLOCKER | `missions.domain` placement of `PromptSlots`/`SlotId` breaks three ArchUnit rules | folded: `kernel.markdown.PromptSlots`/`SlotId`; kernel-typed `PromptText` port; adapter reads the `MissionType`. Verified: `HexagonalArchitectureTest.java:160-165, 258-262, 552-573`; `kernel/markdown` exists | plan Scale/Scope, Source tree, Structure Decision, Complexity Tracking, IC-03, C4, gates table; research P1; data-model.md |
| A2 | BLOCKER | Under E6 as built no step enhancement is ever in force; the fallback does not fix it; the owner is unclear | operator item **OD-6**. Verified: `InForceResolution.java:329-341`. The fallback hunk was withdrawn from IC-01 | plan OD-6, IC-01 Risks, IC-06, IC-07, IC-08, overlap list; research facts; data-model.md; quickstart Flow 3 |
| A3 | MAJOR | The load-path projection routes through paths the code and a gate forbid | operator item **OD-3** (rider: may `raised: true` mean "a violation code of `validation_failed`"?). The violation-kind mechanism is folded as the build-to default. Verified: `readable` :599-608, `offWireRules` :106-131, `loadStaged` :239-243 | plan Structure Decision, IC-02, IC-03, IC-04, OD-3; research P3; data-model.md |
| A4 | MAJOR | The `prompt_slot_not_empty` warning has no channel, and the descriptor does not reach the binding | operator item **OD-7** (wire surface). The seam is folded: check in `DomainRepositoryService.assembly`, warnings on a separate list, a test that built-in composes without `pack_unreadable` | plan Structure Decision, IC-03, OD-7; research P4; data-model.md; quickstart Flow 2 |
| A5 | MAJOR | No raw companion text seam on load; the parse would see three inputs | folded: canonical text `MarkdownCodec.write(document)` everywhere, frontmatter included and stated. Verified: `Companions.java:286-305`; `MissionTypeBinding.markdownOf` | plan Structure Decision, IC-03, IC-07 step 2; research P2; data-model.md (Slot, EffectiveStepPrompt); contracts fragment `prompt` description |
| A6 | MAJOR | FR-009 creates a second authority for "this step exists" | operator item **OD-9** (light; the PR C runner may settle it). The working definition and the limit to `step_enhancement:` edges are folded | plan IC-06 FR-009, OD-9; data-model.md |
| A7 | MAJOR | The compose-time slot check has no inputs in `packs.domain` | folded: `declaredSlots`/`filledSlots` in `compositionFacts`; pure check, the same for both D13 branches; surfaces added | plan Structure Decision, IC-06; research P5; data-model.md (Composition facts) |
| A8 | MINOR | IC-01 needs no edit to `DomainRepositoryService` | folded. Verified: `assembly` iterates `KindBindings.all()` (:700); `authoredInGraphs()` (:399-401; `NodeKind.java:61-63`) | plan IC-01 changes and surfaces; research facts |
| A9 | MINOR | The orphan refusal duplicates the existing unmodelled channel | folded: reclassify the `unmodelled` entry in `loadStaged` and validate mode; no throw. The reading of D11 "at load" is recorded. Verified: `WholePackStore.java:212, 244-248` | plan IC-01, IC-02 "Orphans"; research P8; data-model.md |
| A10 | MINOR | The PR C overlap list is incomplete, and PR C does not exist yet | folded: WP29 added, the pre- and post-PR C file lists, ownership by ruling, PR C status, assumed signatures pinned | plan IC map intro "Overlap with PR C" |
| A11 | MINOR | Same-id handling goes beyond C14 and is not stated | folded: the winning copy only; one cross-level and one settled-sibling test | plan IC-01, IC-06, IC-07 step 4; research P10; data-model.md |
| A12 | MINOR | The default `fileName` would place a canonical write at the pack root | folded: `fileName` overridden to refuse, with a test. Verified: `KindBinding.java:138-140` | plan IC-01; data-model.md |
| A13 | NIT | Vocabulary drifts across layers | folded: one name per concept (`EffectiveStepPrompt`, `EffectivePromptSlot`, `EffectivePromptFragment`) in every layer and the glossary | plan Source tree, IC-07 surfaces, IC-08; data-model.md header |
| T-01 | BLOCKER | The plan's assertion edits go beyond the approved D19 set | operator item **OD-10** (D19 addendum). Every edit is listed with path:line; `errors.feature:192-238` and `ReservedRefusalCodesTest`'s javadoc and off-wire set were found beyond the four named | plan OD-10, IC-01/02/04/07 gates, gates table, Charter Check; research D19 |
| T-02 | MAJOR | `PackFileSetExactnessTest` does not stay green just because the binding is appended | folded: `GeneratedPackTree.Kind.STEP_ENHANCEMENT` with a placed plant; the gate row corrected. Verified: :138, `GeneratedPackTree.java:105-120` | plan IC-01 scaffolding and gates, gates table |
| T-03 | MAJOR | The fragment round trip is not proven by meaning; CRLF/BOM unpinned; served text unspecified | folded: bodies in the DSO and comparators, a mutant test, CR/BOM refused under `prompt_fragment_invalid`, twins, served text = canonical render | plan IC-01 "Round trip by meaning", IC-02 companion faults, IC-07; research P7; data-model.md (PromptFragment) |
| T-04 | MAJOR | NFR-001 has no baseline | folded: option (a), constants captured on `main` before IC-01; a positive control; a fragment edit changes the fingerprint and the ETag | plan Technical Context NFR-001, IC-01 gates, IC-07 step 5, gates table; quickstart Flow 1 |
| T-05 | MAJOR | The slot parser has no edge-case table, and agreement is unproven | folded: the full table, with whitespace defined, and a corpus property test with a mutant. A5 makes the input single | plan IC-03 (table), Structure Decision; research P2; data-model.md (Slot) |
| T-06 | MAJOR | The read of a non-empty legacy slot is unspecified and untested | operator item **OD-8**. A test and a scenario are planned either way | plan OD-8, IC-05, IC-07 step 3; contracts fragment description (placeholder); data-model.md |
| T-07 | MAJOR | N1 default (a) describes a shape the code does not produce | operator item **OD-3** (corrected facts stated). The multi-fault registration scenario asserting pointers and count is folded | plan OD-3, IC-02 surfaces; quickstart Flow 2 |
| T-08 | MAJOR | It is unsettled which refusal wins when several rules fail | folded: both D12 shapes tested, `[]` documented as failing the schema first; schema before semantics; codec before grammar; deterministic first violation; companion twins | plan IC-02 "Precedence"; research P9; data-model.md; contracts/README.md (schema description) |
| T-09 | MAJOR | The NFR-003 permutation test can pass vacuously or fail on the fingerprint | folded: whole body and ETag compared, nothing excluded; a fixture where merge, name and id orders differ | plan Technical Context NFR-003, IC-07 Determinism; data-model.md |
| T-10 | MAJOR | The NFR-004 gate is under-specified | folded: named hooks, two sizes, exact counts cold and warm, digest counts, sort excluded with a rationale, valid enhancements | plan Technical Context NFR-004, IC-07 Cost; data-model.md |
| T-11 | MAJOR | The planned BDD coverage has gaps | folded: 304, ETag change and keep, 500 `internal_error` and `stale_input`, 400, the tactic positive control, preset, registration, sibling and twin, read-path 500 per load code, branch-A write 500 | plan IC-01 red-first list, IC-05 Scenarios |
| T-12 | MAJOR | Early fixtures must already satisfy later rules | folded: the two-pack `spdd` shape, conforming to the full rule set from IC-01 | plan IC-01 "Fixtures conform" |
| T-13 | MAJOR | A dormant mask in `DomainRoundTrip` would surface at #435 | folded: pairing through the held `PackLayout` place, and a fixture-level test in IC-01. Verified: `DomainRoundTrip.java:337-360` | plan IC-01 scaffolding and gates, gates table |
| T-14 | MINOR | The `empty_kinds` reason explains nothing | operator item **OD-11** (rewords D20's approved text); proposed wording recorded. The same-commit rule (`WholePackExpectation:392`) is folded | plan OD-11, IC-01; research D20 |
| T-15 | MINOR | IC-boundary ordering: the IC-01 orphan refusal would turn `ReservedRefusalCodesTest` red | folded: IC-01 raises no prompt code; the orphan and frontmatter refusals move to IC-02; each raised flag lands with its code | plan IC-01, IC-02 gates, IC-04 sequencing, gates table; research P8 |
| T-16 | MINOR | FR-009 covers only the qualified target | folded: bare resolves in the own pack, otherwise `pack_reference_unqualified`; six twins. Verified: `PackCompositionResolver.checkReferences` | plan IC-06 FR-009; data-model.md |
| T-17 | MINOR | The warning must be asserted positively | folded: exactly 5 warnings, each naming its file | plan IC-03 gates, gates table; quickstart Flow 2 |
| T-18 | MINOR | The D14 branch B no-op test needs a positive control | folded: a `mission_type:` key accepted and an `action:` key refused, in the same fixture | plan IC-06 D14 branch B |
| T-19 | MINOR | Docs checks do not pin ADR annotation text | folded: no positive control claimed for the ADR part; kept for the schema description | plan IC-08 Gates |
| T-20 | MINOR | The quickstart curl lacks a bearer token, and the filtered test lists miss breaking tests | folded: `Authorization: Bearer $CHARTER_TOKEN` (and a 304 example); whole `:packs:test` and `:activations:test` per concern | quickstart (intro, Flows 1–3) |
| T-21 | NIT | `PromptFragmentContractsTest:53-55` will be misread once D12 refuses at load | folded: assertion untouched; cross-reference comment in the D12 rule test | plan IC-02 gates, gates table |

## Notes on reviewer statements that conflict

- **"HexagonalArchitectureTest: the placement is sound"** (tests.md, "Areas that hold") conflicts with A1. A1 is
  right, as the cited rules show, and the kernel placement settles both.
- **C-13 evidence.** The x-refusal wording in the fragment was never changed. The "active or inactive" phrase is in
  the reserved paragraph, not the x-refusal entry. The fix, keeping the paragraph wording until D13, is folded
  unchanged.
- **T-14.** The reviewer marks it "not for the operator". The run lead's instruction classes it as an operator item,
  because it rewords text the operator approved in D20. It is listed as OD-11.
