# Tasks: Round-trip fidelity decision (ADR-0036)

A documentation mission: the work is two generation slices (plan.md), run in order by one author,
then validation. No lanes or worktrees.

| ID | Slice | Done when | Requirements |
|---|---|---|---|
| T1 | G1: write ADR-0036 | The ADR records the four rulings, the generic rules, the refusal path, the DRG canonical form, the per-kind table covering every `file-kinds.yaml` rule plus non-YAML files, the filing checklist, the scope of schemaless kinds, the charter reading and the worked examples | FR-001…FR-008, FR-010, NFR-002, NFR-003 |
| T2 | G2: pointers, index, changelog | Row 0036 in the index; dated pointers on ADR-0017/0025/0027 and their index rows; open question #29; Planned note in `descriptors.md`; changelog entry and index line; spelling words | FR-009, FR-011 |
| T3 | Validate | Docs checks clean (NFR-001); adversarial squad run and every finding dispositioned in `audit-report.md` (FR-012); fresh-context worked-example check (SC-001) | FR-011, FR-012, SC-001…SC-004 |
| T4 | Publish | `release.md`; draft PR to `main` referencing #188; CI green; hand-off comment | — |
