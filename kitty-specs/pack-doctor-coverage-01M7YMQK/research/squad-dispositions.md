# Pre-spec alignment and design squad: dispositions

Run 2026-11-21 at the operator's request, over ADR-0082 and docs/architecture/architectural-patterns-applied.md
(PR #634). Lenses: architect-alphonso, planner-priti, researcher-robbie. Verdicts: all ALIGNED-WITH-CHANGES.

| # | Finding (lens) | Disposition | Evidence |
|---|---|---|---|
| A1 | #629 and #632.1 are one cause: one rule, one fault per item (architect) | accepted | spec FR-002 |
| A2 | RequiredFloor stays the read model, not the rule (architect) | accepted | spec FR-003, US1.4 |
| A3 | The preset refusal does not go into the compose, which answers 500 on reads (architect) | accepted | FR-003 |
| A4 | `--full` reuses collect-all domain forms; SpecializationCycleRule moves to the domain (architect, researcher) | accepted | FR-006, C-001; plan |
| A5 | No new closure computation in pack-validation (architect) | accepted | NFR-003 |
| A6 | The cycle scope differs per surface; test a sibling-pack cycle (architect) | accepted | US3.3, edge case |
| A7 | manifest_stale at the storage edge; the census carries the stale fields (architect) | accepted | FR-004; plan |
| A8 | The round trip drops only source-stale, not by file dedupe (architect) | accepted | FR-005 |
| A9 | remediate is a storage-edge facade that judges nothing, behind the verdict (architect, researcher) | accepted | FR-009, US5.2 |
| A10 | Sequencing on PR #618 (architect, planner) | changed | #618 merged 2026-11-21T10:30Z; FR-001 is WP01 |
| A11 | Docs and ADR amendments; the patterns page scope rows (architect) | accepted | FR-012 |
| A12 | One report model, two serializers (architect) | accepted | FR-008 |
| P1 | One mission, but slice it (planner) | accepted | plan/tasks: ordered WPs in one PR (R-f) |
| P4 | Contract fragments: Breaking for refusals and the report rename (planner) | accepted | FR-011 |
| P5 | Rename blast radius, release scripts move together (planner) | accepted | FR-007; occurrence map in plan |
| P6/P7 | Example presets would go red (spdd tactics, default anti-pattern) (planner) | accepted, ruled R-l | FR-010 |
| P8/P9 | Example manifests and the whole-pack baseline (planner) | accepted | FR-010, SC-003 |
| P10 | Exclusions recorded (planner) | accepted | C-005 |
| P11 | open-packs#TBD drafts (planner) | accepted | C-006 |
| R-closure | The own closure differs from the composed closure (researcher) | changed, ruled R-k | Domain Language, Assumptions |
| R8 | "pack-doctor" echoes core's doctor and the #115 name (researcher) | deferred_with_rationale | Operator ruling R-c chose the name deliberately |

## Post-plan squad (2026-11-21): architect-alphonso, randy-reducer, reviewer-renata

Verdicts: architect "split-brain in D3 and D5", reducer REVISE, reviewer "do not approve for tasking as written". All
findings dispositioned below; the plan is revised accordingly.

| # | Finding | Disposition | Evidence |
|---|---|---|---|
| PA1 | case-only has nothing to defer to in pack-doctor | accepted | plan D3: the preset phase computes caseOnlyMatches first |
| PA2 | `defined` mixes files and graph-only nodes | accepted | plan D3: `held`/`declared` replace `defined` |
| PA3 | WriteFacts rebuilds facts on writes | accepted | plan D3 |
| PA4 | a sibling specializes_from cycle is invisible per own closure | accepted; D5 held | plan D5, message to dispatcher |
| PA5 | `--full` is not "everything registration judges" | changed: proposal (a) narrows it, ruling pending | plan D5 |
| PA6 | moving SpecializationCycleRule drags ImpactRelations.cyclesIn (slice 2) | accepted | plan D5 (not moved in this mission unless ruled) |
| PA7 | name the ClosureImpactCheck call site; own-closure rules in two layers | accepted | plan D3 (walkFaults; slice-3 follow-up in ADR-0083) |
| PA8 | order of findings differs between surfaces | accepted | plan D3 order, D5 order |
| PA9 | wrong ArchUnit rule named; the precondition wording is split | accepted | plan D7 |
| PA10 | `stale()` emits missing entries; membership reasons are prose | accepted | plan D4 |
| PR1 | the report duplicates the problem model | accepted | plan D6, data-model |
| PR2 | an XSD plus a JSON Schema are two sources of truth | accepted (within R-n: own shape, not JUnit) | plan D6 |
| PR3 | remediate staging duplicates PackCommitStore | changed: reuse the staging helper only if it is framework-free and lock-free (architect: pack-doctor stays framework-free) | plan D7 |
| PR4 | the facade is thin | accepted | plan D7 (composes the existing pieces) |
| PR5 | the occurrence map is incomplete | accepted | occurrence_map.yaml exceptions |
| PR6 | check-packs.md path contradicts | accepted | plan IC-08, occurrence map |
| PR7 | `declared` duplicates facts | changed: `held`/`declared` replace `defined` outright (architect PA2 shows `defined` is wrong) | plan D3 |
| PR8 | twin collect-all and first-fault bodies | accepted | plan D5 (first of the collect-all form) |
| PR9 | the D9 hedge | accepted | plan D6 (checked in WP06 first, recorded) |
| PR10 | nothing droppable | noted | — |
| PV1 | A10 and the must_have scenarios cannot reach requiredNotHeld | accepted | plan D3 (test-only seed, count guard) |
| PV2 | placeholder-hash scenarios at 528-584 | accepted | plan D4 |
| PV3 | source-stale tests not listed | accepted | plan D4 |
| PV4 | vacuous doesNotContain assertions | accepted | plan D4 |
| PV5 | FR-001's "unchanged" vs the signature change; the deletion guard | accepted | plan D1 |
| PV6 | NFR-002 not measurable for --full | accepted | plan D11 |
| PV7 | fixture positive controls | accepted | plan D3 |
| PV8 | remediate test design | accepted | plan D7 |
| PV9 | red-first for the rename | accepted | plan D11 |
| PV10 | SC-001 parity table | accepted | plan D11 |

## Pre-merge squad (2026-11-23): agents A, B and C, then reviewer-renata's fold review

The pre-merge squad's ledger, folded on `landing/squad-fold-a`, `-b` and `-c` before the merge (AGENTS.md: every
finding is folded, LOWs and nits included, unless a concrete reason is recorded). Rulings R-z to R-ad are
ADR-0083's.

| # | Finding | Disposition | Commit, ruling or reason |
|---|---|---|---|
| A1 | case-only scope: registration's composed closure, one body | folded | R-z; `04b50016b` (`CaseOnlyRules`) |
| A2 | `REFERENCES` gated on composition faults only | folded | R-aa; `086c19c55` |
| A3 | one `PackRulesets` entry owns `REFERENCES`' order and stop | folded | R-ab; `b96dcd49f` (`ReferencesPhase`) |
| A4 | the case-only holder in registration's merge order | folded | `04b50016b` |
| A5 | parity rows for the cross-pack shapes, packs and detail compared; `@TempDir` | folded | `2839a54fe`, `9484c7786`; packs comparison tightened by R1-L2 |
| A6 | policy locks in sorted URN order | folded | `04b50016b` |
| A7 | `PackRulesets` Javadoc; no-arg `closure()` dropped | folded | `04b50016b` |
| A8 | `ResolverFaults` inlined and deleted | folded | `b96dcd49f` |
| A9 | an anti-pattern file refused for its own fault is one fault | folded | `e081c0386` |
| B1 | every round-trip load through `ParsedYaml`'s guards | folded | `f5e925b79`, `7de782daa` |
| B2 | XML report: reversible escape | folded | R-ac; `4ca7da573`, `c9683265f` |
| B3 | remediate follows a linked root | folded | `777727b20` |
| B4 | a canonical pack needs no write access | folded | `777727b20` |
| B5 | a failure names the aside copy; recovery documented | folded | `777727b20`; ADR-0083 d11 in `59764055d` |
| B6 | a pack holding the working directory is a usage fault | folded | `777727b20` |
| B7 | `--report` inside a checked pack refused; atomic write | folded | `97aaa8adc`; dangling link by R1-L4 |
| B8 | the report file written only after standard output took it | folded | `97aaa8adc` |
| B9 | remediate's usage faults name `remediate`; `value()` Javadoc | folded | `777727b20` |
| B10 | a YAML document past 1 Mi code points is too large | folded | `f5e925b79`; ADR-0083 d22 |
| B11 | a FIFO inside a pack | folded as a refusal | R-ad; `b2fc37767` |
| B12 | a pack named by its id when its `pack.yaml` is refused | folded | `643786b8f` |
| C1 | `/nodes/<n>` claims: the violation is at the document | folded | `ae7717755`, `59764055d`, `7c2fdd521`; feature header by R1-L5 |
| C2 | `PackDoctorReport` `packs[].name` is the pack id | folded | `ae7717755` |
| C3 | `--full` references resolve in the `depends_on` closure; Own closure glossary entry | folded | `d7c06eb64`, `ae7717755`, `59764055d` |
| C4 | glossary Procedure and Rejects as built | folded | `59764055d` |
| C5 | ADR-0075 and ADR-0083 record R-z to R-ad | folded | `59764055d` |
| C6 | C35 names `caseOnlyMatches`, dated amendment | folded | `59764055d` |
| C7 | build header and hexagonal-service name three adapters | folded | `d7c06eb64`, `59764055d` |
| C8 | the guide's points | folded | `59764055d` |
| C9 | `CharterRefusalCode` and `RefusalCode` wording | folded | `d7c06eb64`, `ae7717755` |
| C10 | DIV-404, the styleguide's anti-patterns | folded | `59764055d` |
| C11 | changelog: every accepted write restamps | folded | `59764055d` |
| C12 | the report fragment is Breaking; wrapping | folded | `ae7717755` |
| C13 | spec, data model, plan and the WP09 drafts' LOWs | folded | `7c2fdd521` |
| C14 | fragments for the fold's changes | folded | `ae7717755`; R-ab changes no observable behavior, so no fragment |
| R-ad | a special file in a pack is refused | folded | `b2fc37767`, `7216b21e1` |
| R1-M1 | remediate leaves a read-only old copy silently | folded | `978996ef6` (`OwnerOnly.ownerWritable`, the warning), `ff8a62a6a` |
| R1-M2 | the #510 rule sidestepped by `PosixFileAttributeView.setPermissions` | folded | `978996ef6` (rule widened, fixture, `OwnerOnly.carryMode`) |
| R1-L1 | the case-only scope does not state the activation boundary | folded | `ff8a62a6a` |
| R1-L2 | `isTheFault` accepts a problem that names no pack | folded | `978996ef6` |
| R1-L3 | an unchecked move failure bypasses the wording and the suppressed put-back | folded | `978996ef6` |
| R1-L4 | a dangling `--report` link into a pack | folded | `978996ef6` |
| R1-L5 | `anti-pattern-nodes.feature:4` still says "at the node" | folded | `ff8a62a6a` |
| R1-L6 | no record of the pre-merge squad's dispositions | folded | this table |

## Tier R review (2026-11-24): reviewer-renata, at `80875a6c8`

The independent review of draft PR #647 (REQUEST CHANGES), folded on `landing/tier-r-fix`. Each blocking item has a
red test first; the reviewer's repros were rerun on the rebuilt jar (`-Xmx2g`): B3's 965 062-code-point tactic, one
500 000-code-point scalar aliased 30 000 times, now answers `unreadable ... the document is too large`, exit 1 with
the full report, in 6 s (`validate --round-trip`) and 8 s (`remediate`), where it ran out of memory after about
100 s; B4's 705 000-code-point tab-filled tactic now answers `remediate` with exit 1 and the file named, in 5 s, where
it exited 2 with nothing on standard output.

| # | Finding | Disposition | Commit, ruling or reason |
| --- | --- | --- | --- |
| B1 | `Closes #614` while its `ClosureVerdict` clause is deferred (Rule 2) | folded | `Refs #614` in the PR body; records in `0d0560a6e` (`closure-evidence.md`, `issue-matrix.json`, `spec.md`, `plan.md`, ADR-0083's status); the verdict stays `in-mission` |
| B2 | `validate --full` passes a dependency's bare reference that registration refuses | folded under ruling R-ae | `916db7cd0`: `ReferencesPhase` judges per root over the composed closure, every pack in it (`references(rooted.order(), rooted.order(), factsOf)`), a fault several roots find reported once; `FullPassParityTest` pins the reviewer's probe, `pack_reference_unqualified` on both surfaces; ADR-0083 decision 8 |
| B3 | scalar-alias amplification past the alias and node budgets | folded | `a0b9a8b49`: `ParsedYaml` bounds the strings a document expands to at `Yaml.MAX_CODE_POINTS`, the same cap (ADR-0083 decision 22) |
| B4 | `remediate` exits 2 when a file's canonical form outgrows the cap | folded | `b1ac5505e`: the round trip judges each artefact's canonical form by the cap before writing it (one `round_trip_red`, named), and `PackRemediation.NotRoundTripped` answers the pack `refused`, exit 1 (ADR-0083 decisions 11 and 23) |
| N1 | a non-ASCII file name under the POSIX locale is an internal error, exit 2 | folded as the reviewer's second option; not fixed in code | `3dd1ed3f3`: the usage and the guide state the UTF-8 locale. It predates this PR: `main`'s `pack-check` at `9f1412a0e` exits 2 the same way, `InvalidPathException` from `FilesystemPackStore.readKind`, and exits 1 under `LC_ALL=C.UTF-8`. A code fix changes the storage walk every surface shares (refuse a name the locale cannot encode, or carry paths, not strings, through it), so it is not a fold for this branch |
| N2 | the PR body's `open-packs#TBD-*` placeholders | not in this fix branch | A pre-merge checklist item: the orchestrator files the open-packs issues and replaces each placeholder before merge (`closure-evidence.md`) |
| N3 | a FIFO as a step prompt is two faults for one cause | folded | `7c88e22b4`: `MissionTypeChecks` does not state a named companion as missing where a special file stands; the special file's `unreadable` is the one fault, as A9 |
| N4 | `MixedReadConcurrencyTest` timed out once | no change | Load-sensitive, not a regression: it tripped only while the reviewer's 2 GiB out-of-memory probes ran, and it passed on the reviewer's `verifyAll` rerun and in this branch's gates |
| B3′ | confirm pass: a long number aliased many times passes the string budget; plain `validate` runs for hours | folded | `07d2f6f4d`: a number's digits count in the budget at each reach, and a number scalar past 1 000 code points is refused on the parser's events before conversion. The confirm pass's probe (500 000 digits, 30 001 reaches) went from no answer after 120 s (exit 124, all three commands) to exit 1 in 1.9 s (`validate`), 4.1 s (`--round-trip`) and 5.7 s (`remediate`) |
| N5 | a graph shard whose canonical form passes the cap is worded "does not read" plus an unpaired copy | folded | `4b02d0cc9`: every YAML text the round trip writes is judged by the cap before it is written (artefact, preset, policy, descriptor, shard, fragment, manifest); one `round_trip_red` naming the size |
| per-file write | a service per-file write could store a canonical text past the cap | refuted; no change | `PackCommitStore.commit` → `requireNoNewViolation` re-reads the staged copy through `ParsedYaml`'s cap, so the write is refused 422 `validation_failed` |
| B3″ | a float with a tiny exponent (`1e-999999999`) expands to a billion digits plain; the round trip runs out of memory (also on `main`) | folded | `9e00c1380`: the plain-form length (exactly `toPlainString()`'s since F1) is judged from the exponent's text before conversion and counted at each reach. The probe went from out of memory in 22.5 s (`--round-trip`) and 20.2 s (`remediate`), stdout empty, to exit 1 in 1.6 s, 3.3 s and 3.8 s (`validate`, `--round-trip`, `remediate`), `unreadable ... the document is too large` |
| N6 | schema-violation collection is quadratic (20k violations in 620 KB take 44 s); also on `main` | not folded | Filed as follow-up issue [#648](https://github.com/spec-kitty/spec-kitty-charter/issues/648) (DoS risk) |
| F1 | the plain-form length is one short below 1 (`0.`), so `1e-999`, `0.5e-998` and `15e-999` pass PACK and the round trip refuses its own canonical output | folded | `5b6f84bfc`: the length is `toPlainString()`'s exactly; tests pin both sides of the bound for four values and read back the canonical form of each accepted one. `score: 15e-999` went from `round_trip_red ... no domain element was built` plus an unpaired copy to `unreadable ... the document is too large`; `1e-998` round-trips and `remediate` rewrites it, exit 0 |
| F2 | an unreadable constituent the stamped manifest lists also gets `round_trip_red` at `pack-manifest.yaml` ("lists constituent ... which the pack does not hold") | not folded | Not local. pack-doctor drops a round-trip reason only at a file validate mode already refuses (`PackSetCheck.notReported`, "an independent red is never hidden"), and the manifest's reason is at `pack-manifest.yaml`. In the round trip, skipping the entry in `DomainRoundTrip.manifest`'s membership check leaves a red anyway: a manifest restamped from the derived lists lacks the entry, which the manifest comparison reads as another meaning (`MeaningComparator` drops only the derived hashes); no outcome is "the round trip gave no outcome for this file" (`WholePackExpectation`); keeping the entry needs a hash of a file the round trip did not write, which the written-stale check (`PackRoundTrip`) flags. One fault needs a new outcome ("not judged: a listed constituent is refused") through `FileOutcome`, `RoundTripObservation` and `WholePackRoundTripRule`, or a structured cross-file drop in `PackSetCheck`. Recorded under "Not done" in the PR body |

### Tier R final confirm (2026-11-24), at `e66cdb782`

The final confirm pass confirmed B2 in code (ten multi-pack probes, parity in every pack order) and asked for six
documentation folds, all below MEDIUM.

| # | Finding | Disposition | Commit, ruling or reason |
| --- | --- | --- | --- |
| F1 | `PackDoctorReport.yaml`'s `checks` rule still says `REFERENCES` runs per pack within its own closure | folded | `cae82e865`: per root over the composed closure, every pack in it (R-ae) |
| F2 | `EnhancementCompose.targetFaultsOf`, `PackCompositionResolver.referenceFaultsOf` and `delegationFaultsOf` Javadocs state the old scope | folded | `cae82e865`, with `PackSetVerdict`'s `REFERENCES` Javadoc |
| F3 | "own closure" for `REFERENCES` in `closure-evidence.md`, `FullPassParityTest` and the 2026-11-23 references-gate fragment | folded | `cae82e865`: also the architecture patterns table and a `PackSetCheckTest` display name; the dated task files (WP05, `tasks.md`) are left as written |
| F4 | the open-packs figure (76 violations) was measured before R-ae | folded | `30d25a4fa`, then `e7264238b`: re-measured against open-packs `main` `a7243e2` at `fd41585bd` (79 violations; `REFERENCES` still skipped there); `--round-trip` gives 144 |
| F5 | "pack-id order" reads as the `pack_id` ULID; the code sorts by the configured id | folded | `2e0d43bf7`: "configured-id order", defined, in the Javadocs, ADR-0083, the data model and the plan; a `ReferencesPhaseTest` case tells the two orders apart (green at once: no bug) |
| F6 | two R-ae lines in `check-packs.md` past the page width | folded | `30d25a4fa` |

### Merge of main a18fb90f4 (2026-11-24)

Main's five commits (`d40c8ad6a`..`a18fb90f4`) put a pack-file violation's in-file JSON pointer on the wire as a URI
reference, `<file>#<pointer>` (`PackRefusalMapping.pointer`), and give `PackElementViolation` a pointer. Merged in
`6ee39aaae`, with no rebase.

| Item | Resolution |
| --- | --- |
| `Findings.java` (conflict) | Main's `pointer(place)`, `of(PackElementViolation)` and `atEdge`, each `Reason` with this branch's `Origin.NONE` (#614) |
| `StoreRead.java` (conflict) | This branch's `refusedEntries` (links, then special files, R-ad), each built with main's `PackElementViolation.wholeFile` |
| `PackRefusalMappingTest` (compile) | Main's new case builds its `Reason` with `Origin.NONE` |
| A file-less `anti_pattern` node | The finding was already at `/nodes/<n>`; the wire pointer is now `<document>#/nodes/<n>` on both surfaces. FR-014 and its edge case, ADR-0083 decisions 12 and 13, the data model, the anti-pattern-node fragment and `anti-pattern-nodes.feature` (header, `:81`, `:107`) say so |
| `manifest_stale` | At the field: `pack-manifest.yaml#/constituents/<n>/content_hash` (`#/presets/<n>/content_hash`, `#/manifest_hash`). ADR-0083 decision 5, FR-004, the manifest-stale fragment, `whole-pack-registration.feature:588` and `StaleManifestTest` say so |
| An anti-pattern file's schema and stem faults | At the breaching field: `#/severity` for an unknown severity, `#/id` for the placement and stem rule (`anti-pattern-files.feature`); a missing required key or an unknown key stays at the whole file |
| Preset items | Already `presets/<id>.yaml#/activated_<kinds>/<n>`, main's form; unchanged |
| A special file, a document past the size bound | Whole-file violations (`wholeFile`): the bare path, as before |
| Parity | `FullPassParityTest` now also compares the violation pointers of a one-problem row, registration's against pack-doctor's (a comparison added, none narrowed) |
