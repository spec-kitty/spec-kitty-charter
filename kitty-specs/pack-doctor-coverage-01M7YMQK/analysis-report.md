---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: pack-doctor-coverage-01M7YMQK
mission_id: 01M7YMQKJ7VHPK74YWKCQW8XBT
generated_at: '2026-11-21T22:14:04.615173+00:00'
analyzer_agent: claude
input_artifacts:
  spec.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/spec.md
    sha256: 85f4dd72626bd3d641339a0c86c1f126f159868aee015ce43dcc2513fab841c0
  plan.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/plan.md
    sha256: d153d185e77fdd0d45dfe821aaa98f7f0e0ee4533fd3a1049eb175d12152f4dc
  tasks.md:
    path: .worktrees/pack-doctor-coverage-01M7YMQK-coord/kitty-specs/pack-doctor-coverage-01M7YMQK/tasks.md
    sha256: d1decb156a1ba76e1f9bc170d6c9e86f0c88e21138ae7414cbd733ffcc1953f6
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 78969118cd3893697d6eae119c7306a91003f27d64a8ddfdb4744d92c50e4503
verdict: ready
issue_counts:
  medium: 4
  low: 8
  high: 0
  critical: 0
  info: 0
findings:
- id: Q2
  severity: medium
  category: ambiguity
  summary: The PackSetVerdict.Check value of the narrowed --full phase (R-s) is a report-contract value; CLOSURE_WALK would name the deferred own-closure walk. Asked of the operator; WP05 defaults to REFERENCES.
- id: U1
  severity: medium
  category: underspecification
  summary: New example-pack files (18 anti-patterns in built-in by WP10, 2 org-base tactics by WP03) vs WP04's manifest restamp across lanes i, c and d.
- id: U2
  severity: medium
  category: underspecification
  summary: "Out-of-map edits widen: WP10 edits three built-in styleguides (WP03's glob) and the built-in manifest (WP04's); WP12 edits the packs CHANGELOG (WP10's) and docs/conformance (WP02's); WP05 edits Arguments, PackSetCheck and service.yml."
- id: U3
  severity: medium
  category: underspecification
  summary: CompositionFactsTest is WP11's and WP03 renames its defined reads out-of-map; lanes j and c run in parallel, so the second merge meets a small conflict; facts/example-packs.json is re-recorded by WP10 then WP03.
- id: R1
  severity: low
  category: ownership
  summary: WP03 owns built-in/styleguides/** but no subtask edits it; left unchanged because lane-c is started.
- id: F1
  severity: low
  category: coverage
  summary: WP10 writes 11 procedure bad_examples and 18 explicit severities for the example packs (ADR-0070 curation); the reviewer reads each.
- id: K1
  severity: low
  category: coverage
  summary: .kittify/charter-packs and .kittify/charter/generated carry inline anti_patterns; spec-kitty governance doctrine, not example packs; not edited.
- id: O1
  severity: low
  category: coverage
  summary: 'Open-packs is refused at its repin: 60 files (~267 inline entries) and 13 file-less nodes; drafted under C-006.'
- id: I3
  severity: low
  category: inconsistency
  summary: spec US5.2 says remediate refuses on a domain fault; plan D7 defines the storage-verdict precondition.
- id: I4
  severity: low
  category: inconsistency
  summary: R-p's '#629 does not close' and R-q's 'file kind only' are superseded by R-q and R-r; WP08 records which governs.
- id: C2
  severity: low
  category: coverage
  summary: "No HTTP read of the anti_pattern kind; not in #154's acceptance criteria; additive later."
- id: N2
  severity: low
  category: coverage
  summary: 'WP10-WP12 fragments are ### Breaking per D8; a reviewer may read one as ### Changed.'
---

## Specification Analysis Report

Re-run 2026-11-21 after ruling R-r (DM-01M801H54DXR0447M3F2ZQWFPZ, "All of #154 now") and ruling R-s
(DM-01M801NHF0BFAX05EA3F19KHV7, option (a) of plan D5). R-r answers the earlier Q1 with option (c): WP10 grows (T042,
every example inline anti-pattern becomes a file), and two WPs are added: WP11 (the file-less node refusal, FR-014,
plan D13) and WP12 (the inline drop, FR-015, plan D14). R-s unholds WP05 and narrows `--full` to delegations,
references and enhancement targets. Earlier C1, I1 and I2 are resolved by R-s; Q1 by R-r; N1 now lands in WP05.

### #154, bullet by bullet (evidence in plan D12-D14)

| #154 bullet | State before this PR | Delivered by | Code |
|---|---|---|---|
| The kind, its file and closed schema | No schema, no `files` rule, no binding (`README.md:5`, `KindBindings.java:19-34`); `NodeKind.ANTI_PATTERN` and every kind enum already list it | WP10 (new) | — |
| No default severity | — | WP10: `severity` required in the closed schema | `artifact_schema_error` (existing, `Wording.codeFor`) |
| `bad_example` required | — | WP10: required in the closed schema | `artifact_schema_error` (existing) |
| No inline blocks | `procedure.schema.json:111-117`, `styleguide.schema.json:159-165`; roots closed (`:69`, `:121`) | WP12: drop the property, schema, domain, storage, API and charter contract | `artifact_schema_error`, unknown key (existing, `Wording.java:263-265`) |
| Rejects as graph edges | Exists: `RelationType.REJECTS`, `EndpointTable.java:69` (GUIDANCE incl. procedure) | — | `relationship_endpoint_not_allowed` (existing) |
| Cross-pack `rejects` needs `depends_on` | Exists: edge endpoints are references (`CompositionFactsBuilder.java:376-377`), `PackCompositionResolver.java:189-190, 582-600` | WP11 pins it for the kind | `pack_reference_pack_missing` (existing) |
| No file-less node | Accepted; `declaredWithoutFile` counts it (`CompositionFactsBuilder.java:445-462`) | WP11: one `GraphRules` content rule | `drg_schema_error` at `/nodes/<n>` (existing; precedent `GraphBinding.notAuthored`) |
| Activation does not cascade | Exists: activatable (`ArtifactKind.java:37`); `REJECTS` not in `CascadeRules.CASCADE_RELATIONS` (`:64-70`); no test | WP11 adds `RejectsIsNotCascadedTest` | — |
| The service reads the kind | No binding | WP10 (store; no HTTP read, C2) | — |
| Example packs canonical (ADR-0070) | 18 inline entries in 5 built-in files; 1 graph node without a file | WP10 (18 files, nodes, `rejects` edges) | — |

No contract code is minted: each refusal is an existing answer of the closed schema or the graph rules.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| Q2 | Ambiguity | MEDIUM | plan D5; data-model `--full`; WP05 T023 | The `PackSetVerdict.Check` value of the narrowed `--full` phase is a report-contract value; `CLOSURE_WALK` would name the deferred own-closure walk | Ask the operator (below). WP05 defaults to `REFERENCES` and lists it if unanswered |
| U1 | Underspecification | MEDIUM | WP10 T040/T042; WP03 T015; WP04 T021 | New example-pack files (18 in built-in, 2 tactics in org-base) vs the manifest restamp, across lanes i, c and d | Each WP stamps its own constituents through `ManifestMembership.stamp`; the lane that merges last re-runs the stamp |
| U2 | Underspecification | MEDIUM | WP10, WP12, WP05, WP03, WP06, WP07 | Out-of-map edits widen: WP10 edits three built-in styleguides (WP03's glob) and the built-in manifest (WP04's); WP12 edits the packs CHANGELOG (WP10's) and `docs/conformance/**` (WP02's); WP05 edits `Arguments`, `PackSetCheck` and `service.yml` | Merge lanes in dependency order; reviewers check each out-of-map edit is minimal and listed |
| U3 | Underspecification | MEDIUM | WP11 T044; WP03 T011 | `CompositionFactsTest` is WP11's (canonical nodes) and WP03 renames its `defined` reads out-of-map; lanes j and c run in parallel, so the second merge meets a small conflict. `facts/example-packs.json` is re-recorded by WP10 then WP03 | Merge lane-j before lane-c where possible; otherwise WP03 resolves on rebase. WP03's T011 example now uses an `action` node (edited) |
| R1 | Ownership | LOW | WP03 owned_files | WP03 owns `built-in/styleguides/**` but no subtask edits it; narrowing it would remove WP10's out-of-map edit but changes a started lane's write scope | Left unchanged (started lane); out-of-map edit listed in WP10 |
| F1 | Fixture | LOW | WP10 T042 | Eleven procedure anti-patterns need a `bad_example` written, and all 18 need a written severity (table in WP10) | Reviewer reads each; ADR-0070 makes the charter the curator of its example packs |
| K1 | Scope | LOW | `.kittify/charter-packs/`, `.kittify/charter/generated/` | Ten styleguides there carry inline blocks; they are spec-kitty's governance doctrine for this repo, read by no charter test | Not example packs; not edited (plan D14) |
| O1 | Consequence | LOW | research/open-packs-TBD-anti-pattern-files.md | Open-packs is refused at its repin: 60 files (~267 entries) with inline blocks and 13 file-less nodes | Expected (R-r); drafted under C-006, filed at landing with WP09's drafts |
| I3 | Inconsistency | LOW | spec US5.2; plan D7 | Wording of remediate's refusal | Use D7's wording at the next spec edit |
| I4 | Inconsistency | LOW | decisions R-p, R-q, R-r | R-p's "#629 does not close" and R-q's "file kind only" are superseded | WP08's ADR-0083 states which governs (now in its prompt) |
| C2 | Coverage | LOW | FR-013; ADR-0052 follow-ups | No HTTP read of the kind; not in #154's acceptance criteria | Leave as an additive later change; no issue filed (below MEDIUM) |
| N2 | Coverage | LOW | WP10, WP11, WP12; plan D8 | `### Breaking` vs `### Changed` for the fragments | Keep `### Breaking` per D8's rule |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 one-closure-fault-type | yes | T001-T005 | WP01 (approved) |
| FR-002 preset-item-rule | yes | T011-T013 | WP03 (blocked until WP10) |
| FR-003 preset-rule-surfaces | yes | T014, T016 | WP03 |
| FR-004 manifest-stale | yes | T017, T020 | WP04 (approved) |
| FR-005 round-trip-one-problem | yes | T018, T019 | WP04 (approved) |
| FR-006 validate-full | yes | T022-T024 | WP05, narrowed by R-s |
| FR-007 rename | yes | T006-T010 | WP02 (approved) |
| FR-008 report | yes | T025-T027 | WP06 |
| FR-009 remediate | yes | T028-T031 | WP07 |
| FR-010 fixtures | yes | T015, T021, T040, T042 | WP03, WP04, WP10, WP12 |
| FR-011 contract | yes | T012, T017, T010, T027 | fragments per WP |
| FR-012 adr | yes | T032-T034 | WP08 (R-a to R-s, ADR-0052 and ADR-0017 notes) |
| FR-013 anti-pattern-file-kind | yes | T037-T042 | WP10 |
| FR-014 file-less-node-refused | yes | T043-T045 | WP11 |
| FR-015 inline-anti-patterns-dropped | yes | T046-T050 | WP12 |
| NFR-001 red-first | yes | every WP | stated per subtask; WP11's non-cascade test is a pin, shown red by a local mutation |
| NFR-002 timing | yes | T009, T023 | WP05 fills the `--full` step |
| NFR-003 no-new-walk | yes | T013, T022, T043 | reviewer check |
| SC-004 #154 refusals | yes | T041, T043-T046 | WP10, WP11, WP12 |

**Charter Alignment Issues:** none. Canonical model: R-r replaces the anti-pattern shape outright in one PR (file
kind, file-less refusal, inline drop), with no deprecation window and no legacy reader; core aligns through the
generated client (WP12's `### Breaking` fragment). Tests and examples: the example packs move to the canonical shape
(WP10); the only fixtures with the old shape are refusal fixtures. ADR-0082: the new refusal is one rule in
`GraphRules`, composed by `PackRulesets.content()`, with no walk; the inline refusal is the closed schema at the
storage edge. No PIT. Tier R review applies to WP10-WP12.

**Lanes after re-finalize:** lane-a WP01, lane-b WP02, lane-c WP03 (on lane-b, lane-i), lane-d WP04 (on lane-b),
lane-i WP10 (on lane-b), lane-j WP11 (new, on lane-i), lane-k WP12 (new, on lane-i), lane-e WP05 (on lane-c, lane-j,
lane-k), lane-f WP06, lane-g WP07, lane-h WP08 (now also on lane-e, lane-j, lane-k), lane-planning WP09. The started
lanes a, b, c and d are unchanged in members, dependencies and write scope.

**Unmapped Tasks:** none (T035/T036 map to C-006 and SC-002).

**Metrics:**

- Total requirements: 15 FR + 3 NFR + 6 C + 4 SC
- Total tasks: 50 (T001-T050)
- Coverage: 100% of FRs mapped
- Ambiguity count: 1 (Q2)
- Duplication count: 0
- Critical issues: 0

## Operator question

**Q2 — What is the `PackSetVerdict.Check` value of the narrowed `--full` phase?** It appears in the stdout problem
JSON and the report's `checks` list (a contract value of `PackDoctorReport`). The phase runs delegations, references
and enhancement targets only (R-s).

- (a) **Recommended.** `REFERENCES`: names what the phase resolves; the own-closure walk keeps its name for #636.
- (b) `CLOSURE_WALK`: the name the plan used before R-s; it would later cover more than it does now.
- (c) `FULL`: names the option, not the checks.

## Next Actions

- No CRITICAL or HIGH findings. WP10 may start in lane-i now (WP02 is approved). WP03 stays blocked until WP10 is
  approved, then resumes in lane-c. WP11 and WP12 start in lanes j and k once WP10 is approved, in parallel with WP03.
- Q2 to the operator before WP05 reaches T023.
- U1/U2/U3: lanes merge in dependency order (a → b → i → {j, k} → c, d → e → f → g → h); reviewers check out-of-map
  edits.
- The PR body: `Closes #614 #629 #630 #154`, `Refs #632` naming item 3's remainder, ticks #549 and #142.
