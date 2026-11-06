---
schema_version: 1
artifact_type: spec-kitty.analysis-report
command: /spec-kitty.analyze
mission_slug: layer-merge-and-effective-reads-01M6R0J6
mission_id: 01M6R0J642MF7F33XVMMPSBSAT
generated_at: '2026-11-06T14:56:51.722033+00:00'
analyzer_agent: unknown
input_artifacts:
  spec.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/spec.md
    sha256: 4e760faa58e9fb5b9fd689ee042b26599248b0e26ed57f605ca1eef001aac3ce
  plan.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/plan.md
    sha256: 6e326da604cd0c133254854df600077b4de140cf1a556392b58a8859c756997e
  tasks.md:
    path: kitty-specs/layer-merge-and-effective-reads-01M6R0J6/tasks.md
    sha256: f5344281eb42e237c4cd28163433f58ab4fb3ae7c41ae6edaba11307d7716f79
  charter:
    path: .kittify/charter/charter.yaml
    sha256: 4294d3d4acf870b61bc424ac17d3d34708093d4dbc6126f5b1f72941b23497cc
verdict: ready
issue_counts:
  critical: 0
  high: 0
  medium: 3
  low: 4
  info: 0
findings:
- id: D1
  severity: medium
  category: charter
  summary: WP10 checks the ADR-0065 number only against docs/adr on the branch; the charter says a number is chosen against main and the open PRs.
- id: D2
  severity: medium
  category: charter
  summary: WP29 owns two docs paths but its exit check omits the charter docs gates (check_docs.py, markdownlint-cli2, cspell).
- id: U1
  severity: medium
  category: underspecification
  summary: The git >= 2.50.1 runtime requirement is documented (WP20) but no WP makes the shipped service image carry git; the native and boot images are built without it.
- id: C1
  severity: low
  category: coverage
  summary: NFR-001, C-004 and C-005 are not in any WP's requirement_refs, although every WP body applies NFR-001 and C-004 and tasks.md slices by C-005.
- id: I1
  severity: low
  category: inconsistency
  summary: lane-planning holds WP09 (PR A0) and WP10 (PR A); C-005's four stacked PRs need the closeout to split that lane by commit.
- id: I2
  severity: low
  category: inconsistency
  summary: WP06 tells the implementer to search packs/built-in/ for activated_kinds; built-in packs are not in this repository (they come from spec-kitty-open-packs, where no preset carries the key).
- id: U2
  severity: low
  category: underspecification
  summary: NFR-005's corpus includes git trees, but real-git cases run only in the git-source CI job, so the 20-archive count in check is not stated per job.
---

## Specification Analysis Report

Mission `layer-merge-and-effective-reads-01M6R0J6`: spec.md (42 FRs, 12 NFRs, 8 constraints, 5 SCs), plan.md (IC-01 to IC-20), tasks.md and 29 WPs (WP01–WP29, 4 stacked PRs: A0, A, B, C), against `.kittify/charter/charter.md`.

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| D1 | Charter | MEDIUM | tasks/WP10-a-adr-0065.md:72-73 | The number check covers the branch only; the charter ("Orchestrating cloud sessions") chooses ADR numbers against main and the open PRs. | Add to WP10: check `main` and the open PRs (#240 holds 0064) before writing; stop and report on a clash. |
| D2 | Charter | MEDIUM | tasks/WP29-c-deny-satisfied-by-override.md (owned_files, exit check) | WP29 edits two docs pages without running the docs gates the charter lists before merge. | Add `check_docs.py` (all eight), markdownlint-cli2 and cspell to WP29's exit check, as WP09/10/20/26 do. |
| U1 | Underspecification | MEDIUM | tasks/WP20-a-docs-registration.md:114; build-logic/src/main/kotlin/charter.native-image.gradle.kts | "The container image must carry it" is prose only; no WP adds git to the image build or states that the shipped image has no git source. | Either add a subtask that puts git >= 2.50.1 into the image, or state in WP20 that the shipped image has no git (git source answers 503) and operators add it. |
| C1 | Coverage | LOW | WP frontmatter `requirement_refs` | NFR-001, C-004 and C-005 are cited in bodies but mapped to no WP. | Map NFR-001 and C-004 to the code WPs and C-005 to WP09 (or tasks.md) with `map-requirements`. |
| I1 | Inconsistency | LOW | lanes.json (lane-planning) | One lane spans two PRs. | Keep; split by commit at closeout (already noted by the post-tasks squad). |
| I2 | Inconsistency | LOW | tasks/WP06-a0-presets-any-kind.md:147 | Points at a directory that does not exist here. | Reword: built-ins come from spec-kitty-open-packs (pinned in packs/open-packs.lock.yaml); none carries `activated_kinds`; `packs/qa/presets/qa.yaml` uses `activated_mission_types`, which must keep round-tripping. |
| U2 | Underspecification | LOW | spec.md NFR-005; tasks/WP13, WP18 | The >= 20 crafted archives span check and the git-source job. | State that the corpus count is tar+zip in `check` plus git trees in the git-source job. |

**Coverage Summary Table:**

| Requirement Key | Has Task? | Task IDs | Notes |
|-----------------|-----------|----------|-------|
| FR-001 to FR-008 (registration, delete, atomic replace, ADR) | Yes | WP10, WP11, WP13, WP14, WP16, WP17, WP18, WP19 | |
| FR-009 to FR-012 (layer merge, overrides, sibling) | Yes | WP21 | |
| FR-013 to FR-022 (effective reads, skipped report, graph, context, conditional reads, reasons) | Yes | WP22 to WP25, WP27 to WP29 | |
| FR-023 (offering unchanged) | Yes | WP21 | SC-004 ratchet |
| FR-024 to FR-027 (async, impact check, version history, notices) | Yes | WP10, WP11, WP15, WP16, WP22, WP25 | |
| FR-028 to FR-030 (requirement union, fixed point, grounded enhancers) | Yes | WP07, WP21, WP23 | |
| FR-031 to FR-036 (A0 built-behaviour rulings, removal against new tree) | Yes | WP04, WP05, WP07, WP08, WP14, WP19, WP22, WP25 | |
| FR-037 (access and audit) | Yes | WP11, WP16 | |
| FR-038 (cascade over merged edges) | Yes | WP23 | |
| FR-039 (pack read) | Yes | WP11, WP16 | |
| FR-040 to FR-042 (kind model, storage v2, client round trip) | Yes | WP01 to WP06, WP08, WP09, WP23 | |
| NFR-001 | Body only | all WPs | C1 |
| NFR-002 to NFR-012 | Yes | WP01, WP08, WP11 to WP18, WP20 to WP25, WP27, WP28 | NFR-003 build guard is the operation-count budget (DM-01M6RN5SG19KXAHH4VGT9QBYA1) |
| C-001, C-002, C-003, C-006, C-007, C-008 | Yes | various | |
| C-004, C-005 | Body only | all WPs / tasks.md | C1 |
| SC-001 to SC-005 | Yes | WP08, WP09, WP11, WP14, WP21, WP22, WP25, WP28 | SC-005 trace page `docs/conformance/ruleset-scenario-trace.md` |

**Charter Alignment Issues:** D1, D2 (no MUST violated; both are gaps in how a WP applies a charter rule). Checked and aligned: contract breaks on 0.x with `### Breaking` fragments (ADR-0022); Commons Compress 1.28.0 enters with SHA-256 verification entries (WP13, never turning verification off); open-packs round-trip kept (`PresetRoundTripTest` owned in A0); `verifyAll` once per PR at closeout (NO_FULL_HEAVY_SUITES_IN_MISSION); test first, red by a failing assertion with a guard on undefined steps; `@WIP` used per the existing `CharterContractSuite` convention and removed by the glue WP; no core changes (C-003).

**Stale-text scan:** no "pending" placement text, TODO, TBD or placeholder left in spec, plan, tasks, data model, contract delta, quickstart or WP prompts. `activatedKinds`, `missionTypes`, `kind_not_activated` and `kind_default` appear only as things removed or refused. `skippedByScope` is consistently the write report of the `cascade` scope. `requiredNotHeld` and `requiredNotInForce` match across spec FR-017, data-model.md, contract-delta.md and WP22. `data-model.md:145` `MergedSnapshot.missionTypes` is pack facts, not the removed document member.

**Unmapped Tasks:** none. Every WP maps to at least one FR, and no dependency runs backwards across A0 → A → B → C.

**Metrics:**

- Total Requirements: 67 (42 FR, 12 NFR, 8 C, 5 SC)
- Total Work Packages: 29; at most 8 subtasks each; every WP has its "Do This First" section
- Coverage: 100% of FRs and SCs; 64/67 requirements mapped in frontmatter (C1)
- Ambiguity Count: 0
- Duplication Count: 0
- Critical Issues Count: 0

## Next Actions

- No CRITICAL or HIGH findings: implementation may start.
- Suggested before or during the first lane: fold D1, D2 and U1 into WP10, WP29 and WP20 (a planning edit makes this report stale, so re-run `/spec-kitty.analyze` afterwards), and map C-004, C-005 and NFR-001 with `spec-kitty agent tasks map-requirements`.
