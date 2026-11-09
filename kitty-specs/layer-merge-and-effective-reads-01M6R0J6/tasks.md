# Tasks: Layer merge, pack registration and effective reads

**Mission:** `layer-merge-and-effective-reads-01M6R0J6` | **Plan:** [plan.md](plan.md) | **Spec:** [spec.md](spec.md)

Work packages are sliced by the four stacked PRs of C-005 (#399 → A0 → A → B → C). Each WP title carries its PR
letter. Subtask rows below are references; completion is recorded with
`spec-kitty agent tasks mark-status <Txxx> --status done` (event log), never by editing this file.

## Phases

- **PR A0 (WP01–WP09, WP30):** the ruling changes to #399's built behaviour (IC-19), the A7 kind model (IC-20) and
  the cascade depth of #408 (IC-21, WP30, added 2026-11-07).
- **PR A (WP10–WP20):** ADR-0065, asynchronous whole-pack registration with the impact check and history, sources
  (HTTPS archive, git, local path), `DELETE`s, the write generation and read retry, access and audit, docs.
- **PR B (WP21–WP26):** the layer merge, in-force resolution, the effective list and skipped report, write warnings,
  docs.
- **PR C (WP27–WP29):** the graph projection and action-context walk, the last two reads, the deny-satisfied-by-override
  write rule, docs.

## MVP

WP01 → WP08 (PR A0) is the first shippable slice: it lands every ruling that changes shipped behaviour and the kind
model the reads are built on.

## Subtask Index

| ID | Subtask | WP | Parallel |
|---|---|---|---|
| T001 | Merge the kind enums | WP01 | |
| T002 | Strip the remnant members from the document shapes | WP01 | |
| T003 | Offering schema, storage version and corpus | WP01 | |
| T004 | Preset schema: any kind | WP01 | |
| T005 | Changelog fragment with one Breaking block | WP01 | |
| T006 | Red scenarios: activation-kind-model.feature | WP01 | |
| T007 | Delete the dead flags | WP02 | |
| T008 | One activatable set and one plural | WP02 | |
| T009 | Kind token parsing | WP02 | |
| T010 | The document value | WP03 | |
| T011 | ClosureSnapshot without the mission-type slot | WP03 | |
| T012 | Codec: write v2, read v1 and v2 | WP03 | |
| T013 | Repositories: upgrade on read, store on write | WP03 | |
| T014 | Compile the consumers | WP03 | |
| T015 | Replace judges only added deny entries (A4) | WP04 | |
| T016 | Replace drops stale ids (A4) | WP04 | |
| T017 | Pins no pack satisfies are dropped (B1) | WP04 | |
| T018 | Remove the kind gate from the write rules | WP04 | |
| T019 | Service wiring | WP04 | |
| T020 | Mission-type reach to steps (Q1) | WP05 | |
| T021 | Selection service: no special case | WP05 | |
| T022 | Mission types deniable (Q2) | WP05 | |
| T023 | includeDeactivated clears reached-only (B5) | WP05 | |
| T024 | Report and lineage tests | WP05 | |
| T025 | Preset model on the registry | WP06 | |
| T026 | Preset YAML read and write | WP06 | |
| T027 | Apply-preset without the strip | WP06 | |
| T028 | Must-have items of every kind | WP06 | |
| T029 | Lock scope (A15) | WP07 | |
| T030 | Own policy does not bind self (B8) | WP07 | |
| T031 | Floor holds held ids only (A10) | WP07 | |
| T032 | Sibling settlement (A14) | WP07 | |
| T033 | Closure tests | WP07 | |
| T034 | Refuse the remnant members (Q4) | WP08 | |
| T035 | Map the v2 document | WP08 | |
| T036 | Scenario glue | WP08 | |
| T037 | Client round trip (FR-042) | WP08 | |
| T038 | Changed-behaviour fragment | WP08 | |
| T039 | Property and conformance tests | WP08 | |
| T040 | ADR-0020 and ADR-0042 amendments | WP09 | |
| T041 | ADR-0050 and ADR-0059 amendments | WP09 | |
| T042 | Divergence register | WP09 | |
| T043 | Docs changelog and core hand-over text | WP09 | |
| T044 | Write ADR-0065 | WP10 | |
| T045 | Amendment notes | WP10 | |
| T046 | ADR index | WP10 | |
| T047 | Paths and parameters | WP11 | |
| T048 | Schemas | WP11 | |
| T049 | Refusal codes | WP11 | |
| T050 | Changelog fragment | WP11 | |
| T051 | Red scenarios: registration | WP11 | |
| T052 | Red scenarios: removal | WP11 | |
| T053 | RegistryWriteLock | WP12 | |
| T054 | PackWriteGeneration | WP12 | |
| T055 | ConsistentRead and the retry | WP12 | |
| T056 | Existing writes take the registry lock | WP12 | |
| T057 | Concurrency tests (R-lock) | WP12 | |
| T058 | Dependency under DIRECTIVE_051 | WP13 | |
| T059 | Port and ring | WP13 | |
| T060 | tar.gz reader | WP13 | |
| T061 | zip reader | WP13 | |
| T062 | Path rules and writing | WP13 | |
| T063 | Hostile corpus (NFR-005) | WP13 | |
| T064 | Service skeleton and port | WP14 | |
| T065 | Load and identity (steps 2–3) | WP14 | |
| T066 | Revision and the version rule (steps 4–6) | WP14 | |
| T067 | Write gate and inbound scan (steps 7–8) | WP14 | |
| T068 | Commit (step 10) | WP14 | |
| T069 | Concurrency (NFR-006) | WP14 | |
| T070 | Closures containing the pack | WP15 | |
| T071 | Compose and mutability per closure | WP15 | |
| T072 | Relation refusals (A6, A8, A11, A12, C28) | WP15 | |
| T073 | Floor and lock notices (B4) | WP15 | |
| T074 | Version history (B1) | WP15 | |
| T075 | Census of stored packs | WP15 | |
| T076 | ArchiveBodyAdvice | WP16 | |
| T077 | RegistrationJobs | WP16 | |
| T078 | Controller and mapper | WP16 | |
| T079 | Access, flags and properties | WP16 | |
| T080 | Audit | WP16 | |
| T081 | Scenario glue and NFR-008 | WP16 | |
| T082 | Native upload test | WP16 | |
| T083 | Extract GuardedHttpsExchange | WP17 | |
| T084 | Allowlist union | WP17 | |
| T085 | HttpsArchiveFetcher | WP17 | |
| T086 | LocalSourceReader | WP17 | |
| T087 | Test seam and scenarios | WP17 | |
| T088 | Version check | WP18 | |
| T089 | GitCommand | WP18 | |
| T090 | URL and ref validation | WP18 | |
| T091 | Fetch, resolve, read objects | WP18 | |
| T092 | ArchUnit boundary | WP18 | |
| T093 | Fixture server and scenarios | WP18 | |
| T094 | Artifact removal | WP19 | |
| T095 | Relationship removal | WP19 | |
| T096 | Controller | WP19 | |
| T097 | Concurrency | WP19 | |
| T098 | Scenarios | WP19 | |
| T099 | Host requirement | WP20 | |
| T100 | Guides | WP20 | |
| T101 | Reference pages | WP20 | |
| T102 | Changelog and spelling | WP20 | |
| T103 | LayerMerge: per level and across levels | WP21 | |
| T104 | Mutability of overrides | WP21 | |
| T105 | MergedClosure and merged edges | WP21 | |
| T106 | Presets in the merge (A1) | WP21 | |
| T107 | Languages and the facts cache | WP21 | |
| T108 | ClosureView.merged() | WP21 | |
| T109 | EffectiveArtifact and reasons | WP22 | |
| T110 | SkippedReport buckets | WP22 | |
| T111 | Unreserve the two reads | WP22 | |
| T112 | Changelog fragment | WP22 | |
| T113 | Red scenarios | WP22 | |
| T114 | Request and result values | WP23 | |
| T115 | Strata 1–2 | WP23 | |
| T116 | Stratum 3: the floor to a fixed point | WP23 | |
| T117 | Stratum 4: enhancer and binding pruning | WP23 | |
| T118 | Property tests | WP23 | |
| T119 | Merged cascade (FR-038) | WP23 | |
| T120 | MergedSnapshot and the port | WP24 | |
| T121 | EffectiveSet precedence | WP24 | |
| T122 | Summary and buckets | WP24 | |
| T123 | EffectiveTag | WP24 | |
| T124 | Hexagonal check | WP24 | |
| T125 | EffectiveReadService: list and skipped | WP25 | |
| T126 | Controller: two reads | WP25 | |
| T127 | Write warnings (IC-17) | WP25 | |
| T128 | denyNotApplied on GET /activations/{id} | WP25 | |
| T129 | ActivationImpact adapter | WP25 | |
| T130 | Scenario glue and timing | WP25 | |
| T131 | Architecture pages | WP26 | |
| T132 | Divergences | WP26 | |
| T133 | ADR notes and changelog | WP26 | |
| T134 | Graph projection | WP27 | |
| T135 | Action-context walk | WP27 | |
| T136 | Unknown names | WP27 | |
| T137 | One read | WP27 | |
| T138 | Schemas and paths | WP28 | |
| T139 | Red scenarios | WP28 | |
| T140 | Serve the graph | WP28 | |
| T141 | Serve the action context | WP28 | |
| T142 | Reservation coverage | WP28 | |
| T143 | Contract red first | WP29 | |
| T144 | Red scenarios | WP29 | |
| T145 | The rule | WP29 | |
| T146 | Docs | WP29 | |
| T147 | SC-005 scenarios for the A0 rulings | WP08 | |
| T148 | Suite guard: an undefined or pending step fails the run | WP08 | |
| T149 | Ruleset scenario trace (SC-002, SC-005) | WP09 | |
| T150 | Mutation scope: the new packages and the excluded tags | WP13 | |
| T151 | Staging sweep and the free-space refusal (NFR-009) | WP16 | |
| T152 | Fake git shim and the dedicated CI job | WP18 | |
| T153 | Contract: `depth` on the cascade requests | WP30 | |
| T154 | Red scenarios | WP30 | |
| T155 | Domain and plan | WP30 | |
| T156 | The packs walk honours the bound | WP30 | |
| T157 | API glue and the client | WP30 | |
| T158 | ADR-0059 D2 amendment and divergence for the #408 rulings | WP09 | |
| T159 | One step predicate (OD-9) | WP27 | |
| T160 | E6 amendment (OD-6) | WP27 | |
| T161 | Narrow the pruned reason (D4b, packs) | WP27 | |
| T162 | `NodeReference` (D15) | WP28 | |
| T163 | Step references in the skipped report (#429) | WP28 | |
| T164 | Narrow the pruned reason (D4b, activations) | WP28 | |
| T165 | Rules page and divergences for the 2026-11-09 rulings | WP29 | |

## Work packages

### WP01 — A0 · Contract: one kind model on the activation document

- **Prompt:** [tasks/WP01-a0-contract-kind-model.md](tasks/WP01-a0-contract-kind-model.md)
- **PR:** A0
- **Dependencies:** none
- **Subtasks:**

T001 Merge the kind enums (WP01)
T002 Strip the remnant members from the document shapes (WP01)
T003 Offering schema, storage version and corpus (WP01)
T004 Preset schema: any kind (WP01)
T005 Changelog fragment with one Breaking block (WP01)
T006 Red scenarios: activation-kind-model.feature (WP01)

### WP02 — A0 · Kernel: one kind registry

- **Prompt:** [tasks/WP02-a0-kernel-kind-registry.md](tasks/WP02-a0-kernel-kind-registry.md)
- **PR:** A0
- **Dependencies:** WP01
- **Subtasks:**

T007 Delete the dead flags (WP02)
T008 One activatable set and one plural (WP02)
T009 Kind token parsing (WP02)

### WP03 — A0 · Activation document v2 and the storage upgrade

- **Prompt:** [tasks/WP03-a0-activation-document-v2.md](tasks/WP03-a0-activation-document-v2.md)
- **PR:** A0
- **Dependencies:** WP01, WP02
- **Subtasks:**

T010 The document value (WP03)
T011 ClosureSnapshot without the mission-type slot (WP03)
T012 Codec: write v2, read v1 and v2 (WP03)
T013 Repositories: upgrade on read, store on write (WP03)
T014 Compile the consumers (WP03)

### WP04 — A0 · Document rules: remnant refusal, replace round trip, pins dropped

- **Prompt:** [tasks/WP04-a0-document-rules-rulings.md](tasks/WP04-a0-document-rules-rulings.md)
- **PR:** A0
- **Dependencies:** WP03
- **Subtasks:**

T015 Replace judges only added deny entries (A4) (WP04)
T016 Replace drops stale ids (A4) (WP04)
T017 Pins no pack satisfies are dropped (B1) (WP04)
T018 Remove the kind gate from the write rules (WP04)
T019 Service wiring (WP04)

### WP05 — A0 · Cascade and selection: mission types and the includeDeactivated scope

- **Prompt:** [tasks/WP05-a0-cascade-mission-types.md](tasks/WP05-a0-cascade-mission-types.md)
- **PR:** A0
- **Dependencies:** WP03
- **Subtasks:**

T020 Mission-type reach to steps (Q1) (WP05)
T021 Selection service: no special case (WP05)
T022 Mission types deniable (Q2) (WP05)
T023 includeDeactivated clears reached-only (B5) (WP05)
T024 Report and lineage tests (WP05)

### WP06 — A0 · Presets name any kind

- **Prompt:** [tasks/WP06-a0-presets-any-kind.md](tasks/WP06-a0-presets-any-kind.md)
- **PR:** A0
- **Dependencies:** WP02, WP03
- **Subtasks:**

T025 Preset model on the registry (WP06)
T026 Preset YAML read and write (WP06)
T027 Apply-preset without the strip (WP06)
T028 Must-have items of every kind (WP06)

### WP07 — A0 · packs rulings: lock scope, own policy, held floor, sibling settlement

- **Prompt:** [tasks/WP07-a0-packs-lock-floor-sibling.md](tasks/WP07-a0-packs-lock-floor-sibling.md)
- **PR:** A0
- **Dependencies:** WP03
- **Subtasks:**

T029 Lock scope (A15) (WP07)
T030 Own policy does not bind self (B8) (WP07)
T031 Floor holds held ids only (A10) (WP07)
T032 Sibling settlement (A14) (WP07)
T033 Closure tests (WP07)

### WP08 — A0 · API mapping, scenario glue and the client round trip

- **Prompt:** [tasks/WP08-a0-api-glue-client-roundtrip.md](tasks/WP08-a0-api-glue-client-roundtrip.md)
- **PR:** A0
- **Dependencies:** WP04, WP05, WP06, WP07
- **Subtasks:**

T034 Refuse the remnant members (Q4) (WP08)
T035 Map the v2 document (WP08)
T036 Scenario glue (WP08)
T037 Client round trip (FR-042) (WP08)
T038 Changed-behaviour fragment (WP08)
T039 Property and conformance tests (WP08)
T147 SC-005 scenarios for the A0 rulings (WP08)
T148 Suite guard: an undefined or pending step fails the run (WP08)

### WP09 — A0 · Docs: ADR amendments, divergences and the core hand-over for A0

- **Prompt:** [tasks/WP09-a0-docs-adrs-divergences.md](tasks/WP09-a0-docs-adrs-divergences.md)
- **PR:** A0
- **Dependencies:** WP08, WP30
- **Subtasks:**

T040 ADR-0020 and ADR-0042 amendments (WP09)
T041 ADR-0050 and ADR-0059 amendments (WP09)
T042 Divergence register (WP09)
T043 Docs changelog and core hand-over text (WP09)
T149 Ruleset scenario trace (SC-002, SC-005) (WP09)
T158 ADR-0059 D2 amendment and divergence for the #408 rulings (WP09)

### WP30 — A0 · Cascade depth: suggests hops on a cascading select and deactivate (#408)

- **Prompt:** [tasks/WP30-a0-cascade-depth.md](tasks/WP30-a0-cascade-depth.md)
- **PR:** A0
- **Dependencies:** WP08
- **Subtasks:**

T153 Contract: `depth` on the cascade requests (WP30)
T154 Red scenarios (WP30)
T155 Domain and plan (WP30)
T156 The packs walk honours the bound (WP30)
T157 API glue and the client (WP30)

### WP10 — A · ADR-0065: registration replaces the whole pack

- **Prompt:** [tasks/WP10-a-adr-0065.md](tasks/WP10-a-adr-0065.md)
- **PR:** A
- **Dependencies:** WP09
- **Subtasks:**

T044 Write ADR-0065 (WP10)
T045 Amendment notes (WP10)
T046 ADR index (WP10)

### WP11 — A · Contract A: registration, status, removal and their red scenarios

- **Prompt:** [tasks/WP11-a-contract-registration.md](tasks/WP11-a-contract-registration.md)
- **PR:** A
- **Dependencies:** WP10
- **Subtasks:**

T047 Paths and parameters (WP11)
T048 Schemas (WP11)
T049 Refusal codes (WP11)
T050 Changelog fragment (WP11)
T051 Red scenarios: registration (WP11)
T052 Red scenarios: removal (WP11)

### WP12 — A · Write coordination: registry lock, write generation, read retry

- **Prompt:** [tasks/WP12-a-write-coordination.md](tasks/WP12-a-write-coordination.md)
- **PR:** A
- **Dependencies:** WP09
- **Subtasks:**

T053 RegistryWriteLock (WP12)
T054 PackWriteGeneration (WP12)
T055 ConsistentRead and the retry (WP12)
T056 Existing writes take the registry lock (WP12)
T057 Concurrency tests (R-lock) (WP12)

### WP13 — A · Bounded, path-safe archive extractor

- **Prompt:** [tasks/WP13-a-archive-extractor.md](tasks/WP13-a-archive-extractor.md)
- **PR:** A
- **Dependencies:** WP11
- **Subtasks:**

T058 Dependency under DIRECTIVE_051 (WP13)
T059 Port and ring (WP13)
T060 tar.gz reader (WP13)
T061 zip reader (WP13)
T062 Path rules and writing (WP13)
T063 Hostile corpus (NFR-005) (WP13)
T150 Mutation scope: the new packages and the excluded tags (WP13)

### WP14 — A · Whole-pack registration: validate, version rule, gate, atomic replace

- **Prompt:** [tasks/WP14-a-whole-pack-registration.md](tasks/WP14-a-whole-pack-registration.md)
- **PR:** A
- **Dependencies:** WP12, WP13, WP07
- **Subtasks:**

T064 Service skeleton and port (WP14)
T065 Load and identity (steps 2–3) (WP14)
T066 Revision and the version rule (steps 4–6) (WP14)
T067 Write gate and inbound scan (steps 7–8) (WP14)
T068 Commit (step 10) (WP14)
T069 Concurrency (NFR-006) (WP14)

### WP15 — A · Registration impact check, refusals and version history

- **Prompt:** [tasks/WP15-a-impact-check-history.md](tasks/WP15-a-impact-check-history.md)
- **PR:** A
- **Dependencies:** WP14
- **Subtasks:**

T070 Closures containing the pack (WP15)
T071 Compose and mutability per closure (WP15)
T072 Relation refusals (A6, A8, A11, A12, C28) (WP15)
T073 Floor and lock notices (B4) (WP15)
T074 Version history (B1) (WP15)
T075 Census of stored packs (WP15)

### WP16 — A · Asynchronous registration API, access, flags and audit

- **Prompt:** [tasks/WP16-a-async-registration-api.md](tasks/WP16-a-async-registration-api.md)
- **PR:** A
- **Dependencies:** WP15
- **Subtasks:**

T076 ArchiveBodyAdvice (WP16)
T077 RegistrationJobs (WP16)
T078 Controller and mapper (WP16)
T079 Access, flags and properties (WP16)
T080 Audit (WP16)
T081 Scenario glue and NFR-008 (WP16)
T082 Native upload test (WP16)
T151 Staging sweep and the free-space refusal (NFR-009) (WP16)

### WP17 — A · Sources: guarded HTTPS archive and local path

- **Prompt:** [tasks/WP17-a-https-and-local-sources.md](tasks/WP17-a-https-and-local-sources.md)
- **PR:** A
- **Dependencies:** WP16
- **Subtasks:**

T083 Extract GuardedHttpsExchange (WP17)
T084 Allowlist union (WP17)
T085 HttpsArchiveFetcher (WP17)
T086 LocalSourceReader (WP17)
T087 Test seam and scenarios (WP17)

### WP18 — A · Git source with the host git binary

- **Prompt:** [tasks/WP18-a-git-source.md](tasks/WP18-a-git-source.md)
- **PR:** A
- **Dependencies:** WP17
- **Subtasks:**

T088 Version check (WP18)
T089 GitCommand (WP18)
T090 URL and ref validation (WP18)
T091 Fetch, resolve, read objects (WP18)
T092 ArchUnit boundary (WP18)
T093 Fixture server and scenarios (WP18)
T152 Fake git shim and the dedicated CI job (WP18)

### WP19 — A · DELETE of an artifact and of a relationship

- **Prompt:** [tasks/WP19-a-delete-artifact-relationship.md](tasks/WP19-a-delete-artifact-relationship.md)
- **PR:** A
- **Dependencies:** WP14, WP16
- **Subtasks:**

T094 Artifact removal (WP19)
T095 Relationship removal (WP19)
T096 Controller (WP19)
T097 Concurrency (WP19)
T098 Scenarios (WP19)

### WP20 — A · Docs for PR A: host requirement, guides, status codes

- **Prompt:** [tasks/WP20-a-docs-registration.md](tasks/WP20-a-docs-registration.md)
- **PR:** A
- **Dependencies:** WP18, WP19
- **Subtasks:**

T099 Host requirement (WP20)
T100 Guides (WP20)
T101 Reference pages (WP20)
T102 Changelog and spelling (WP20)

### WP21 — B · packs: the layer merge

- **Prompt:** [tasks/WP21-b-layer-merge.md](tasks/WP21-b-layer-merge.md)
- **PR:** B
- **Dependencies:** WP12, WP07
- **Subtasks:**

T103 LayerMerge: per level and across levels (WP21)
T104 Mutability of overrides (WP21)
T105 MergedClosure and merged edges (WP21)
T106 Presets in the merge (A1) (WP21)
T107 Languages and the facts cache (WP21)
T108 ClosureView.merged() (WP21)

### WP22 — B · Contract B: effective list and skipped report, red first

- **Prompt:** [tasks/WP22-b-contract-effective-reads.md](tasks/WP22-b-contract-effective-reads.md)
- **PR:** B
- **Dependencies:** WP21
- **Subtasks:**

T109 EffectiveArtifact and reasons (WP22)
T110 SkippedReport buckets (WP22)
T111 Unreserve the two reads (WP22)
T112 Changelog fragment (WP22)
T113 Red scenarios (WP22)

### WP23 — B · packs: in-force resolution, a stratified fixed point

- **Prompt:** [tasks/WP23-b-in-force-resolution.md](tasks/WP23-b-in-force-resolution.md)
- **PR:** B
- **Dependencies:** WP21
- **Subtasks:**

T114 Request and result values (WP23)
T115 Strata 1–2 (WP23)
T116 Stratum 3: the floor to a fixed point (WP23)
T117 Stratum 4: enhancer and binding pruning (WP23)
T118 Property tests (WP23)
T119 Merged cascade (FR-038) (WP23)

### WP24 — B · activations: effective set, snapshot values and the port

- **Prompt:** [tasks/WP24-b-effective-set-port.md](tasks/WP24-b-effective-set-port.md)
- **PR:** B
- **Dependencies:** WP22, WP23
- **Subtasks:**

T120 MergedSnapshot and the port (WP24)
T121 EffectiveSet precedence (WP24)
T122 Summary and buckets (WP24)
T123 EffectiveTag (WP24)
T124 Hexagonal check (WP24)

### WP25 — B · Effective reads served, write warnings and the deny notice

- **Prompt:** [tasks/WP25-b-effective-read-service.md](tasks/WP25-b-effective-read-service.md)
- **PR:** B
- **Dependencies:** WP24, WP15
- **Subtasks:**

T125 EffectiveReadService: list and skipped (WP25)
T126 Controller: two reads (WP25)
T127 Write warnings (IC-17) (WP25)
T128 denyNotApplied on GET /activations/{id} (WP25)
T129 ActivationImpact adapter (WP25)
T130 Scenario glue and timing (WP25)

### WP26 — B · Docs for PR B: resolution model, offering page, divergences

- **Prompt:** [tasks/WP26-b-docs-merge-reads.md](tasks/WP26-b-docs-merge-reads.md)
- **PR:** B
- **Dependencies:** WP25
- **Subtasks:**

T131 Architecture pages (WP26)
T132 Divergences (WP26)
T133 ADR notes and changelog (WP26)

### WP27 — C · packs: graph projection and the action-context walk

- **Prompt:** [tasks/WP27-c-graph-and-action-walk.md](tasks/WP27-c-graph-and-action-walk.md)
- **PR:** C
- **Dependencies:** WP23
- **Subtasks:**

T134 Graph projection (WP27)
T135 Action-context walk (WP27)
T136 Unknown names (WP27)
T137 One read (WP27)
T159 One step predicate (OD-9) (WP27)
T160 E6 amendment (OD-6) (WP27)
T161 Narrow the pruned reason (D4b, packs) (WP27)

### WP28 — C · Contract C and the last two reads served

- **Prompt:** [tasks/WP28-c-contract-graph-context.md](tasks/WP28-c-contract-graph-context.md)
- **PR:** C
- **Dependencies:** WP27, WP25
- **Subtasks:**

T138 Schemas and paths (WP28)
T139 Red scenarios (WP28)
T140 Serve the graph (WP28)
T141 Serve the action context (WP28)
T142 Reservation coverage (WP28)
T162 `NodeReference` (D15) (WP28)
T163 Step references in the skipped report (#429) (WP28)
T164 Narrow the pruned reason (D4b, activations) (WP28)

### WP29 — C · Deny on a required artifact satisfied by an override, and PR C docs

- **Prompt:** [tasks/WP29-c-deny-satisfied-by-override.md](tasks/WP29-c-deny-satisfied-by-override.md)
- **PR:** C
- **Dependencies:** WP28, WP26
- **Subtasks:**

T143 Contract red first (WP29)
T144 Red scenarios (WP29)
T145 The rule (WP29)
T146 Docs (WP29)
T165 Rules page and divergences for the 2026-11-09 rulings (WP29)
