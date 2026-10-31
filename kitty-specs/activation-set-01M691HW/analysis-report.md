---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: activation-set-01M691HW
mission_id: 01M691HWY6ZRY0NVZYDYZ7ZD77
generated_at: '2026-10-31T13:32:26.116322+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/activation-set-01M691HW/spec.md
    sha256: ab5512da1f058a3c088c6f66136e1630de8c02b2320d5910e3dfc72e77185218
  plan.md:
    path: kitty-specs/activation-set-01M691HW/plan.md
    sha256: aa9e1649a23915c9ff028771690a917f22e26648f8eb3f335f0199f989591335
  tasks.md:
    path: kitty-specs/activation-set-01M691HW/tasks.md
    sha256: 47641a8e062d0ce8cc9b369a5ff0a0003f2925a72e9f470f8da74bc46c6a2687
  charter:
    path: .kittify/charter/charter.yaml
    sha256: af2620decc50721d9aa4e6f169d3e1e8c718412cc7d347a2891ca6d8aacbd351
verdict: ready
issue_counts:
  low: 3
  critical: 0
  medium: 2
  high: 0
  info: 0
findings:
- id: I1
  severity: medium
  category: inconsistency
  summary: spec FR-003 and C-003 still say Activation._links carries `self` only; research D-8 makes `_links` the open-keyed Links component (self is the only relation used now).
- id: I2
  severity: medium
  category: inconsistency
  summary: "plan.md 'Design decisions' 3-4 and the Project Structure tree predate the squad: the 501 is now an interceptor before binding (research D-11), and file names follow data-model.md / WP03, not the plan tree."
- id: I3
  severity: low
  category: inconsistency
  summary: plan.md Technical Context says ~12 new schema files; data-model.md and WP03 imply about 30.
- id: C1
  severity: low
  category: coverage
  summary: T031 (draft build issue under kitty-specs/research) has no owning WP file entry because code WPs cannot own kitty-specs paths; the orchestrating agent writes it.
- id: U1
  severity: low
  category: underspecification
  summary: The follow-up issue that will build the nine writes does not exist yet; the WP02 scenario descriptions and the declaration pins must name it once filed (placeholder until then).
---

## Specification Analysis Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| I1 | Inconsistency | MEDIUM | spec.md FR-003, C-003; research.md D-8 | `_links` described as `self` only vs the open `Links` component | Read FR-003/C-003 as "relations: `self` only for now"; research D-8 and data-model.md govern; fix wording in WP05 docs pass |
| I2 | Inconsistency | MEDIUM | plan.md Design decisions 3-4, Project Structure | Predates the squad folds (interceptor, names) | research.md D-11 and WP03/WP04 prompts govern; no task change |
| I3 | Inconsistency | LOW | plan.md Technical Context | Schema file count understated | Informational |
| C1 | Coverage | LOW | tasks.md T031 | No owning WP for a kitty-specs planning note | Orchestrator writes it alongside WP05 |
| U1 | Underspecification | LOW | WP02 T008/T009 | Build issue for writes not yet filed | Use a TBD placeholder; replace at closeout |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 activation-config-closed | Yes | T012, T013 | |
| FR-002 json-schema-mirror-parity | Yes | T012, T017, T018, T025 | three validators |
| FR-003 activation-envelope | Yes | T014 | |
| FR-004 change-and-skipped-report | Yes | T014 | |
| FR-005 operations-declared | Yes | T020, T021 | |
| FR-006 effective-reads | Yes | T014, T021 | |
| FR-007 reserved-marker-501 | Yes | T021, T023 | |
| FR-008 conditional-headers | Yes | T020 | |
| FR-009 bodies-and-semantics | Yes | T014, T021 | |
| FR-010 refusal-codes | Yes | T022, T024 | |
| FR-011 refusal-members | Yes | T015 | |
| FR-012 lint-reserved | Yes | T001-T003 | |
| FR-013 lint-204 | Yes | T004 | |
| FR-014 501-scenarios | Yes | T007, T008, T010, T011 | |
| FR-015 service-501 | Yes | T023 | |
| FR-016 declaration-pins | Yes | T009 | |
| FR-017 changelog | Yes | T027 | |
| FR-018 adr-amendment | Yes | T028 | |
| FR-019 docs | Yes | T029 | |
| NFR-001..005 | Yes | T026, T032, WP DoDs | |

**Charter Alignment Issues:** none. Plan-point adversarial squad ran (research.md S1-S31); pre-PR squad is planned at closeout; scenarios are written red first; verifyAll on Java 25 is a gate.

**Unmapped Tasks:** none (T031 is a planning note, see C1).

**Metrics:** 19 FR + 5 NFR + 6 C + 5 SC; 32 tasks; coverage 100%; ambiguity 0; duplication 0; critical 0.

### Next Actions

No critical or high finding: implementation may start. I1/I2 are resolved by research.md and the WP prompts, which are the binding design; the WP05 docs pass corrects the wording.
