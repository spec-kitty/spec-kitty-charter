---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: layer-merge-and-effective-reads-01M6R0J6
mission_id: 01M6R0J642MF7F33XVMMPSBSAT
generated_at: '2026-11-07T21:22:39.141381+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/spec.md
    sha256: 9f652e53c50689d6cbafcf1f8c3f45bcdd30dfbe36e180acdb78b17fca1d3e88
  plan.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/plan.md
    sha256: 03d33bf58e09ea77f5c3928a5be0d8f22dcbb8faffbb11b17d5c96dad4d97e21
  tasks.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/tasks.md
    sha256: 521629a48b49c0af6e484f673af42ced1e51fa73204c00adcc47cb8c3354850d
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: ready
issue_counts:
  medium: 1
  critical: 0
  high: 0
  low: 7
  info: 0
findings:
- id: C1
  severity: low
  category: coverage
  summary: NFR-001, C-004 and C-005 are cited in WP bodies but mapped to no WP's requirement_refs (carried).
- id: I1
  severity: low
  category: inconsistency
  summary: lane-planning holds WP09 (PR A0, merged) and WP10 (PR A); split by commit at closeout (carried).
- id: U2
  severity: low
  category: underspecification
  summary: NFR-005's 20-archive count spans check and the git-source job (carried).
- id: I3
  severity: low
  category: inconsistency
  summary: research.md R6 and line ~266 are superseded by the plan's 2026-11-07 fold; marked with a note rather than rewritten.
- id: I5
  severity: low
  category: inconsistency
  summary: Several WP prompts (WP11, WP14, WP19) carry markdownlint list-spacing and double-blank-line flags inherited from the WP template; kitty-specs is outside the repo's markdownlint scope.
- id: U4
  severity: low
  category: underspecification
  summary: getPackRegistration carrying the registration refusal codes in x-refusal is stated as a recommendation; WP11 settles it in the contract.
- id: I6
  severity: medium
  category: inconsistency
  summary: 'FR-025 named a registration and a DELETE only; plan IC-15 adds per-artifact PUT/POST by operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW. Fixed: FR-025 amended.'
- id: C2
  severity: low
  category: coverage
  summary: 'WP19 builds the per-artifact impact check but did not reference FR-025. Fixed: mapped.'
---

## Specification Analysis Report (re-run, 2026-11-07, after the WP11-WP16 hand-offs and rulings)

Re-run before WP17 and WP19 start. Since the last report (64805e76) the planning branch gained: the per-artifact impact
check (plan IC-15, WP19; operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW), the C21 ruling (DM-01M6VD94VEVPSC1JXQ1RA7YYHQ),
the decisions DM-01M6TZRAA7JQR2H5WN4SKNXGQ8, DM-01M6V4WMF8CD8NBN7A065E9ZQ5, DM-01M6V6N74ER28H8S6TSVZ8ZEY2,
DM-01M6VEDYX8TQ4AKCP7C19WAP75 and DM-01M6VXMCDQJWBJXGRQNMF5G6SV (notices name activatable kinds only; #417), the
hand-offs from WP13-WP16 into WP15, WP16 and WP20, the gate scope and supply-chain notes in
research/closeout-notes-pr-a.md, and issue-matrix rows #397, #403, #404 and #417. WP10-WP16 are approved.

### Findings in this delta

| ID | Category | Severity | Location(s) | Summary | Disposition |
|----|----------|----------|-------------|---------|-------------|
| I6 | Inconsistency | MEDIUM | spec.md FR-025; plan IC-15 | FR-025 named a registration and a `DELETE` only; the plan's IC-15 now also checks per-artifact `PUT`/`POST` (operator ruling). | Fixed in this commit: FR-025 names the per-artifact writes and cites the ruling. |
| C2 | Coverage | LOW | tasks/WP19 frontmatter | WP19 builds the per-artifact impact check but did not reference FR-025. | Fixed in this commit: FR-025 mapped to WP19. |

Every hand-off was checked against the spec and the plan. Each is either an implementation detail inside an existing FR,
or a documentation item for WP20, and none widens scope. The WP16 contract additions are recorded in fragments: the
`503` with `Retry-After` on activation writes under `### Changed`, and the served registration paths. #416 (lookup cache)
and #417 (notice kinds) are follow-ups outside this mission.

### Coverage summary (phase A, current)

| Requirement | WPs |
|---|---|
| FR-001, FR-003 | WP11, WP13, WP14 (+WP16 for FR-001) |
| FR-004, NFR-005, NFR-012 | WP16 (FR-004, NFR-004), WP17, WP18 |
| FR-005, FR-006, FR-007 | WP11, WP19 |
| FR-024, NFR-008, NFR-009 | WP16 |
| FR-025 | WP10, WP15, WP19 |
| FR-036 | WP14, WP19 |
| FR-037, NFR-010 | WP11, WP16 |
| FR-008, NFR-011, C-008 | WP18, WP20 |

FR coverage: 43/43 mapped. Critical: 0, high: 0. The MEDIUM finding is fixed.

### Next actions

WP17 and WP19 may start (both depend on WP16 alone; WP19 also on WP14). WP18 follows WP17, and WP20 follows WP18 and WP19.

## Specification Analysis Report (re-run, 2026-11-07, phase A delta)

Re-run after the phase A plan delta (commits 983b449d, 64f8d366) and the WP10 review fold (IC-04 step 6 and IC-06 wording): the plan predated main's #403 (ADR-0064) and #390.
Folded: the 14 delta-squad findings, the operator's rulings DM-01M6TQ62HXYF362GAFS2BZ3PGF (a relationship DELETE
removes only registered edges; `409 relationship_not_registered` otherwise) and DM-01M6TQ67C9ZTNGZB9CY28JS54F (PR A
stays strict; #404 out of scope), the runner's follow-ups DM-01M6TR1KDAJ64GMJD6AYNZKT59, and the operator's resolution
of DM-01M6TKXG93KR109CJKFX3HBB05 (keep the 422). PR A0 items (WP01-WP09, WP30) are approved and in PR #414.

### Findings

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| C1 | Coverage | LOW | WP frontmatter | NFR-001, C-004, C-005 unmapped (carried). | Map with `map-requirements`. |
| I1 | Inconsistency | LOW | lanes.json | lane-planning spans PR A0 and A (carried). | Split by commit at closeout. |
| U2 | Underspecification | LOW | spec.md NFR-005 | Archive count spans two jobs (carried). | State per job in WP13/WP18. |
| I3 | Inconsistency | LOW | research.md R6, ~266 | Superseded text kept with a pointer. | None; the plan is authoritative. |
| I5 | Inconsistency | LOW | tasks/WP11, WP14, WP19 | Template markdownlint flags. | None needed (out of lint scope). |
| U4 | Underspecification | LOW | plan IC-02/IC-14 | x-refusal placement is a recommendation. | WP11 decides in the contract. |

### Coverage summary (phase A)

| Requirement | WPs |
|---|---|
| FR-001, FR-003 | WP11, WP13, WP14 (+WP16 for FR-001) |
| FR-005, FR-006 | WP11, WP19 |
| FR-025 | WP10, WP15 |
| FR-036 | WP14, WP19 |
| FR-037, NFR-010 | WP11, WP16 |

`relationship_not_registered` is consistent across spec, plan, contract-delta, WP11 and WP19.

### Charter alignment

No conflict. Contract first (C-001), strict refusals, no suppressions; the plan-point squad ran against main's
current state before phase A code.

### Metrics

- FR coverage: 100%; critical: 0; high: 0; ambiguity: 0; duplication: 0.

### Next actions

Only LOW findings: phase A may proceed (WP10, WP12, WP13 first).
