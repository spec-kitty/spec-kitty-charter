---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: adr-0066-step-enhancement-service-alignment-01M6ZRYK
mission_id: 01M6ZRYKV1QSKERC80RYR0XZQG
generated_at: '2026-11-09T16:17:30.389362+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/spec.md
    sha256: 3e6a7376590b31474dd3d9b07c1eea9c50d5192134fe8214e77353362c009ede
  plan.md:
    path: kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/plan.md
    sha256: 61a810857a635aef10e98b7cc43ed9feb2ef8662d1c94cf943ffb1cd2ff7c8cc
  tasks.md:
    path: kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/tasks.md
    sha256: 5d192886bf95fe2e5e012d35a548331942b07cf007f50c715b88d8a5ed1fa09b
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: ready
issue_counts:
  critical: 0
  low: 4
  medium: 1
  high: 0
  info: 0
findings:
- id: A1
  severity: medium
  category: inconsistency
  summary: "A2-harness was folded into WP01 T005, the tasks.md hot-files table and the plan's ruled table, but several 'no edit' statements still contradict it: plan IC-01 :347 says 'No edit to DomainRepositoryService (A8)', and plan :373-375 says DomainRoundTrip 'pairs through the held PackLayout place', which the adapter does not hold. IC-01's Affected surfaces leave out FilesystemPackStore, DomainRepositoryService and PlacedWriteTest. WP01 T006 :394 says 'Do not edit DomainRepositoryService', and WP05 :104 says WP04 made 'one listed edit' to it."
- id: A2
  severity: low
  category: underspecification
  summary: WP01's DomainRepositoryService listed edit allows 'one package-private saveArtifact(pack, artifact, place) overload … Nothing else in the file'. Dispatching a KindBinding<?, ?> needs a generic capture helper, which is why the private static <D> write(store, key, kind, artifact) exists. The Template branch of writeArtifact (:647-656) is not addressed either, so as worded the edit cannot compile.
- id: A3
  severity: low
  category: underspecification
  summary: The place-aware write in FilesystemPackStore refuses only a place that leaves the pack or is already taken. Nothing refuses a place whose file name is not <id>.<fileToken>.yaml, or that file-kinds.yaml classifies as another kind, so a bad place writes a file that the read-back refuses or misreads. PlacedWriteTest does not pin this.
- id: A4
  severity: low
  category: underspecification
  summary: DomainRoundTrip.writeAll is static and runs before roundTrip computes sourceIds (:136-147), so 'the source path the census already walks, keyed by kind and id' needs a reorder and an inverted map. writeAll and writtenByKindAndId would each name the not-placed-by-id set a second and third time, besides KindBindingsTest. The class javadoc (:42-43, 'written … at the pack root') goes stale.
- id: A5
  severity: low
  category: inconsistency
  summary: "In plan.md 'Hand-over (2026-11-09)', next steps 1–2 (fold, finalize-tasks) are already done, yet still listed as to-do. The pointer 'see Plan errata (fifth analysis) below' is wrong: that section is above (:1174)."
---

## Specification Analysis Report

Mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK`, phase 1 (WP01–WP06). Checked against the code at HEAD `9a256ab0` on branch `issue-424-428-adr-0066-service-alignment`, read-only. I did not re-raise anything the brief marks binding: OD-1..OD-12, D4b, R-OVR, R-REL, A1, A2, OD-10 rows 1–12, the A8 handling, A2-harness, E-1..E-11, D1–D22, the Split, and everything marked phase 2 (#438).

**How the fifth analysis's findings were folded (each checked against the code):**

| Fifth-analysis item | Fold in 95a1b5e1 / 9a256ab0 | Code check | Verdict |
|---|---|---|---|
| A1 → OD-10 row 12 | Added in four places: a WP05 listed edit (T024), "Files shared", DoD and reviewer guidance, plus the hot-files row. | `FileSystemPackInspectorTest.java:670` (method), `:679` (`assertThat(verdict.findings()).isEmpty()`) and `:677-678` (comment) are correct. The test runs under `:packs:openPacksRoundTrip`, which includes the `open-packs` tag (`packs/build.gradle.kts:117-125`), so "see it red before the edit" works. | Correct |
| A2 → A2-harness | WP01 T005 step 3, "Files shared", owned/create `PlacedWriteTest`, lanes.json, hot-files rows, plan ruled table. | `FilesystemPackStore.write` :884-911, `locate` :1108-1119, `newFile` :1125-1145 (calls `kind.fileName` at :1127/:1133), `DomainRepositoryService.saveArtifact` :215/:228-231, `DomainRoundTrip.writeAll` :230-249 (`pack.artifacts().forEach(to::saveArtifact)`), `writtenByKindAndId` :337-360 (`!nested \|\| placedAtItsFileName()`). All match. The approach works: `write`'s companion plan is relative to the target file, and `WholePackCensus.companionsOf` is binding-driven, so the companions pair once the binding overrides `companions`. Neither file is in a PR C lane (layer-merge `lanes.json`: `DomainRepositoryService` is WP21, `WholePackStore` is WP14), so C-004 holds. | Correct in substance. A1 and A2–A4 cover leftovers. |
| A3 (`ContractDescriptionsTest` pins) | WP04 T020 step 4 and WP05 "Files shared". | WP05 rewrites only the slot codes and the reserved paragraph. The C-07 reword stays WP04's. | Correct |
| A4 (row 6 in the raise commit) | WP04 T017 step 3, T020 step 3, tasks.md sketch. | The table is at `errors.feature:192-238`, under the scenario at :171. | Correct |
| A5 (orphan exclusion) | WP04 "Files shared", T019 step 2, hot-files row. | Matches WP03's facts (:186-199), data-model :86 and research P8. | Correct |
| A6 (companion overrides) | WP01 T004 step 1. | `KindBinding` :186-222: `Companion(path, Optional<CompanionContent>, form, structureSchema)` and `withCompanions(dso, Map<String, CompanionContent>)`. `CompanionFilesTest.boundKindsDeclareTheirCompanions` :283-290. | Correct |
| A7 (`RegistrationOutcome`) | WP05 T025 step 3. | The record is at `PackRegistrationService.java:123`, built at :607. Its only other callers are `PackRegistrationMapperNoticesTest:36` and `PackRegistrationMapperSourceTest:36`, and no other module constructs it. | Correct |
| A8 (`:web-transport`) | WP04 T020 step 5, Gates, closeout step 5. | `:web-transport` is a module (`settings.gradle.kts:33-34`) and uses the hand-written style convention. | Correct |
| A9–A14 | Folded as described. | `RefusalHandler.statusByOperation` :89-94 over 11 `IMPACT_CHECKED_WRITES` (:67-78). The two SPDD tests (:386, :404) never call `judge`. The bare branch is `PackCompositionResolver` :463-475, with `declaredWithoutFile` :580-597. Quickstart closeout matches tasks.md. | Correct |

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|---|---|---|---|---|---|
| A1 | inconsistency | medium | plan.md IC-01 :347-351 ("No edit to `DomainRepositoryService` (A8)"), :373-375 ("pairs through the held `PackLayout` place instead"), IC-01 "Affected surfaces" (:382-387); WP01 T006 step 1 (:394, "Do not edit `DomainRepositoryService`"); WP05 "Files shared" (:104, "has already made one listed edit"); contracts/README.md:45 and WP04 :148/:607 ("`FilesystemPackStore.java` … not edited / untouched") | The A2-harness ruling makes WP01 edit `DomainRepositoryService` and `FilesystemPackStore`. IC-01's body, which WP01 tells the implementer to read in full, and WP01's own T006 still forbid that. `DomainRoundTrip` holds no `PackLayout`, since it goes through `DomainRepositoryService.load`. A WP01 reviewer following T006 or IC-01 literally would reject the ruled seam. | In IC-01, replace the A8 bullet with: "no rule or load edit; one A2-harness overload". Replace the `writtenByKindAndId` bullet with the A2-harness text, and add the two files and `PlacedWriteTest` to Affected surfaces. Reword WP01 T006 the same way. WP05 :104 should name both earlier listed edits (WP01's overload, WP04's form). The README and WP04 lines should say "by WP04" or "beyond WP01's A2-harness seam". |
| A2 | underspecification | low | WP01 "Files shared" (`DomainRepositoryService` bullet), tasks.md hot-files row; `DomainRepositoryService.java:647-662` | `writeArtifact` resolves a `KindBinding<?, ?>` and must call a private generic `write` (:659-662) to capture `D`. A place-taking overload needs the same kind of helper, or a change to that helper. "Nothing else in the file" rules both out. A `Template` passed to the overload is also unspecified. | Allow "one overload plus its private generic helper", and say that the overload refuses a `Template` (or any kind placed by id) with `IllegalArgumentException`. |
| A3 | underspecification | low | WP01 "Files shared" (`FilesystemPackStore` bullet), T005 step 3 (`PlacedWriteTest`); `FilesystemPackStore.java:1125-1145`, `FileStems.java:40-51` | The new create path skips `newFile`'s place derivation but adds only the climb and taken checks. It does not check that the place's last segment is `<id>.<fileToken>.yaml`, or that `file-kinds.yaml` classifies it as the kind. A wrong place becomes a read-back failure instead of a write refusal. | Have the seam refuse a place whose stem is not the id or whose suffix is not the kind's file token (the rule `FileStems.mismatch` applies on read), and add one `PlacedWriteTest` case for it. |
| A4 | underspecification | low | WP01 T005 step 3; `DomainRoundTrip.java:128-147, 230-249, 337-360`, class javadoc :42-43 | `writeAll(from, to, id, pack)` is static and runs before `sourceIds`, so the census place needs a reorder and a (kind, id)→path map. The not-placed-by-id set would be named in `writeAll`, `writtenByKindAndId` and `KindBindingsTest`. The javadoc's "at the pack root, wherever its source lived" goes stale. | Say to compute the source places before `writeAll` and pass them in. Derive the set once, for example in one `DomainRoundTrip` constant checked against `KindBindingsTest`'s set. Update the javadoc. |
| A5 | inconsistency | low | plan.md "Hand-over (2026-11-09)" :1205-1213 | Steps 1–2 are done (95a1b5e1 is the finalize commit). "See … below" points to a section that is above. | Mark steps 1–2 done and change "below" to "above". |

## Coverage Summary Table

| Requirement | Has task? | Task IDs / WPs | Notes |
|---|---|---|---|
| FR-001 Hold step enhancements | Yes | T003–T006 (WP01) | A1–A4 (A2-harness wording) |
| FR-002 Hold fragment companions | Yes | T003–T005 (WP01) | Companion overrides now named (fifth A6) |
| FR-003 Composition and selection | Yes | T002, T006 (WP01) | — |
| FR-004 Fragment content checks | Yes | T012–T013 (WP03); T016–T019 (WP04) | — |
| FR-005 Structure checks | Yes | T014 (WP03); T016, T018–T019 (WP04) | Orphan exclusion aligned (fifth A5) |
| FR-006 Identity, target, R-OVR/R-REL | Yes | T015 (WP03); T016–T020 (WP04) | Handler map per operation (fifth A11) |
| FR-007 Base-prompt slot checks | Yes | T008–T011 (WP02); T022–T024 (WP05) | OD-10 row 12 folded |
| FR-011 Load codes raised, diagnostics | Yes | T017, T020 (WP04); T023, T025–T026 (WP05) | — |
| FR-012 Phase-1 rulings recorded | Yes | T021 (WP04); T027–T030 (WP06) | — |
| NFR-001 Fingerprint stability | Yes | T001, T006 (WP01) | — |
| NFR-002 Corpus stays loadable | Yes | T019 (WP04); T022, T024 (WP05) | — |
| C-001 Contract versioning | Yes | T007, T021, T026 | — |
| C-002 Round trip by meaning | Yes | T003, T005 | A2-harness covers the gate adapter |
| C-003 Graph-only relationships | Yes | T003, T015 | — |
| C-004 PR C boundary | Yes | All WPs | The new A2-harness files are not PR C-owned (checked in the layer-merge `lanes.json`) |
| C-005 Out of scope | Yes | WP06 | — |
| C-006 Layout kept | Yes | WP01, T027 | — |
| C-007 Test first | Yes | All WPs | — |
| SC-002 to SC-006 | Yes | WP01, WP03–WP06 | — |

## Charter Alignment Issues

None. The charter's non-negotiables are met:

- The adversarial squad runs at the plan point and before merge, followed by operator approval.
- `verifyAll` runs on Java 25 with dependency verification on and never with `-Pcharter.javaVersion=21`.
- `:packs:wholePackRoundTrip` is required.
- The docs checks run, and Spotless and Checkstyle cover every touched module, now including `:web-transport`.
- Every contract change has a dated fragment (ADR-0022), and no dependency is added.
- ADR-0036 round trip by meaning is kept (C-002). A2-harness makes the gate adapter write the enhancement in place instead of dropping it.

## Unmapped Tasks

None. T001–T030 each map to one WP, and each WP maps to FR, NFR, C or SC IDs.

## Metrics

- Work packages: 6 (WP01–WP06). Subtasks: 30 (T001–T030).
- Requirements: 9 functional, 2 non-functional, 7 constraints, 5 success criteria.
- Requirement coverage: 100%.
- Fifth-analysis items checked: 14 of 14. All are folded correctly; 5 leftover items found.
- Findings: 5 (critical 0, high 0, medium 1, low 4).
- Charter conflicts: 0.

## Next Actions

1. **A1 (medium):** before WP01 starts, align plan IC-01's body and Affected surfaces, WP01 T006, WP05 :104 and the README/WP04 "not edited" lines with the A2-harness ruling. These are wording edits only; no ruling is needed.
2. **A2–A4 (low):** sharpen the A2-harness instructions: the generic helper and `Template` refusal for the overload, a stem and suffix check in the seam with one `PlacedWriteTest` case, and the order of the source places in `DomainRoundTrip` plus a single set definition. These can also be handled by the WP01 implementer and reviewer.
3. **A5 (low):** tidy the Hand-over section.
4. There are no high or critical findings, so the mission is ready for implementation once A1 is fixed. Record this report with `spec-kitty agent mission record-analysis`, then start WP01 and WP02.

## Dispositions (run lead, 2026-11-09)

No high or critical finding, so no operator escalation. All five were folded before recording, in commit `04568e80`
(plan errata "sixth analysis", S-A1..S-A5): A1 (IC-01, WP01 T006, WP05, WP04, contracts README name the A2-harness
edits), A2 (overload helper and Template refusal), A3 (mis-named place refused, pinned in `PlacedWriteTest`), A4
(source places computed first, the set defined once, javadoc), A5 (hand-over next steps).
