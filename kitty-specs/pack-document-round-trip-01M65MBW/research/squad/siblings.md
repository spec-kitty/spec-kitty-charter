# #260 sibling research (planner-priti, read-only)

## 1. Churn since 2026-08-01 (all of it is 2026-10-20..29)
| File | Commits | Missions |
|---|---|---|
| PackFileSet.java (fingerprint `digested`/`packLevelTail`) | 17 | #189, #190, #192, #193, #195, #194 (5 commits on 10-29) |
| Pack.java (domain) | 13 | WP07 era, #171, #189, #193, #195 |
| WholePackStore.java | 11 | #189, #192, #193, #195, #194 (e6fe0f8) |
| DomainRoundTrip.java | 9 | #187, #190, #191, #193, #195 x3, #194 |
| expected-red.yaml | 9 | #187, #190, #191, #192, #193, #195 x2, #194 re-pin a0dfb51 |
| file-kinds.yaml | 8 | #114, R20, #192, #193, #195 x2, #194 x2 (b1d4f22, 812a755) |
| WholePackExpectationTest.java | 8 | #187, #192, #247, #193, #195 |
| PackFileKind.java | 5 | #193, #194 x3 |
| WholePackRoundTripTest.java (holds REQUIRED_KINDS) | 8 | #190, #191, #193, #195, #194 |
Hottest (PackFileSet), last 5: 73b7abb +1/-2, 812a755 +15/-3, e6fe0f8 +12/-4, 1300e55 +37/-10 (all #194), ef2ca57 +3/-3 (#195).
Also fingerprint-relevant: KindBindings (9), KindBinding (7), Companions (6), FileSystemPackInspector (10).
The churn is #194 PR-A (merged at feeff0a, the current origin/main) stacked on #195. It has gone quiet since.

## 2. Siblings
- Remote branches: there is NO #194 PR-B branch and no #229 branch (`git branch -r` lists only merged feature
  branches). `feat/194a-...` holds only acceptance bookkeeping beyond main. So no diff can be predicted yet.
- PR-B intent (from #194 research/squad/planner.md, spec Out of Scope):
  - file-kinds.yaml: **deletes** the 7 mission exclusions (lines 148-172: step.yaml, actions/*/index.yaml,
    mission.yaml, mission-runtime.yaml, expected-artifacts.yaml, governance-profile.yaml, mission_types/*.yaml,
    and maybe templates/plan-field-declaration.yaml). It adds `files` rules plus new schemas, and possibly markdown
    rules for prompt.md/guidelines.md.
  - expected-red: removes the `other:missions` block, which is lines 8-10, directly above `other:pack.md` (lines 11-13).
  - REQUIRED_KINDS: appends mission kinds; possibly B1/B2 split.
  - node_kinds already has action/mission_type; artifact_kinds may grow (that change also touches the schemas'
    VocabularyEqualityTest lists).
  - Biggest hidden overlap: `PackFileSet.markdownHolder` throws `notStored` for any non-companion markdown file
    "until its first consumer (#260 for pack.md) wires it". If PR-B treats prompts as markdown `files` rules rather than
    companions, it needs the same seam. #260 should own that seam and land first.
- #229 (hosted config): it covers `application-container.yaml`, the secret import and the token message (container-properties
  mission C-002). None of it is in the packs surface. Expect no conflict, apart from changelog/hub docs lines.

Recommendations for #260:
1. file-kinds.yaml: add `*/pack.md` as one `files` rule (schema `pack-document`, `content_form: markdown`), placed
   after the toolguide guide rule at the end of `files`. Touch no exclusion. Add a schema file of its own.
2. expected-red: delete only lines 11-13 (`other:pack.md`) and keep `other:missions`. The hunks are adjacent, so the
   rebase keeps both deletions.
3. REQUIRED_KINDS: append `"pack-document"); // #260` after pack-manifest. Kind token `pack-document`
   (#195 FR-014, WP08 T031).
4. Fingerprint: put pack.md in `packLevelTail` between the presets and the manifest (ADR-0042 D9). It is self-contained, and PR-B will not touch the
   tail, because mission files are bound kinds and so are digested earlier.
5. Pack slot: a separate `document()` slot (ADR-0042 D10). Do not extract `PackLevelContent` here (it conflicts with PR-B).
6. Make the generic standalone-markdown store path the first commit, so PR-B can rebase onto it. Tell the PR-B session.

## 3. Prior mission #195 (pack-level-files-round-trip-01M60H1A)
- FR-005: pack.md is held in #194's markdown form once PR-A merges. Status "Deferred to #260 (operator, 2026-10-28)".
  It is paired with a lossy-body control. FR-013 is the fingerprint half; FR-014 adds pack-document to REQUIRED_KINDS and removes other:pack.md.
- SC-001b: deferred to #260. Built-in pack.md goes green and `other:pack.md` leaves expected-red.
- plan D7: append presets, then manifest, "and later pack.md, between them". IC-08 depends on PR-A + IC-06.
  (plan.md:65 still says "pack.md compares by bytes", which is stale and superseded by the decision moment.)
- DM-01M60HM8BV7CE17HYE1QS829K4 (resolved): pack.md uses the markdown form, not bytes. Its WP goes last and depends on PR-A;
  other:pack.md stays expected-red until then.
- WP08: **canceled** in status.json (event 2026-10-28T09:54, "Split off to #260 ... blocked on #194"). 7 done,
  1 canceled. meta has no accepted_at.
- Recommendation: leave #195's artifacts alone; they are a historical record and their status is terminal. Cite FR-005/SC-001b/D7, the DM and WP08 (T030-T032) as
  sources in the new spec. Update ADR-0042 decisions 8/9 to past tense, plus a follow-up line, in docs.
- New mission already scaffolded on this branch: kitty-specs/pack-document-round-trip-01M65MBW (commit 426adeb).
  Its meta.json purpose_context says "ADR-0043 decision 9". That is **wrong**: the fingerprint order is ADR-0042 decision 9
  (renumbered in ef2ca57). ADR-0043 is #194's.

## 4. CLI flow (spec-kitty 4.0.0rc8)
Mission dir: meta.json, spec.md, plan.md, research.md, data-model.md, quickstart.md, checklists/, decisions/,
tasks.md, tasks/WPnn-*.md, lanes.json, status.events.jsonl/status.json, issue-matrix.json, acceptance-matrix.json,
analysis-report.md, retrospective.yaml.
1. Create (done): `spec-kitty agent mission create "<slug>" --mission-type software-dev --friendly-name ... --purpose-tldr ... --purpose-context ... --pr-bound --json` (+ `--target-branch`). Then `spec-kitty agent mission branch-context --json`.
2. Specify: write spec.md. Decisions via `spec-kitty agent decision open|resolve`. Then `spec-kitty spec-commit --mission <slug> --message ...`.
3. Plan: `spec-kitty agent mission setup-plan --mission <slug> --json` and `spec-kitty agent context resolve --action plan --mission <slug>`.
4. Tasks: write tasks.md + WP files, then `spec-kitty agent tasks map-requirements --mission <slug> --json` and `spec-kitty agent mission finalize-tasks --mission <slug> --json` (`--validate-only` first).
5. Analyze (optional): `spec-kitty agent mission record-analysis`.
6. Implement: `spec-kitty agent action implement WPxx --agent claude --mission <slug>`, then `spec-kitty agent tasks move-task WPxx --to for_review`.
7. Review: `spec-kitty agent action review WPxx --agent claude`, then `move-task WPxx --to approved --actor claude --mission <slug> --note ...`.
8. Accept: `spec-kitty accept --mission <slug>` (`--diagnose`), then `spec-kitty consolidate --mission <slug>` or `spec-kitty agent mission merge`. Driver loop: `spec-kitty next --mission <slug>`.
