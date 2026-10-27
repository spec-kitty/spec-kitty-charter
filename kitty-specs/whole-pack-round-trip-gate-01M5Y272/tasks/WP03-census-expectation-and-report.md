---
work_package_id: WP03
title: Census, expectation, ratchet and report
dependencies: []
requirement_refs:
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- NFR-002
- NFR-003
planning_base_branch: feat/whole-pack-round-trip-gate
merge_target_branch: feat/whole-pack-round-trip-gate
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-round-trip-gate. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-round-trip-gate unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-round-trip-gate-01M5Y272
base_commit: 5c559a55803dcc9d9d2c4895e30f08cae1258d59
created_at: '2026-10-27T07:06:43.913140+00:00'
subtasks:
- T009
- T010
- T011
- T012
- T013
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Census, expectation, ratchet and report

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Account for every file of a pack exactly once, judge the observed per-file result against a checked-in
expectation with a **two-way ratchet**, and render a deterministic report (spec FR-006–FR-010; research R-5–R-7;
data-model.md).

## Branch Strategy

Planning base and final merge target: `feat/whole-pack-round-trip-gate`; lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP03 --agent claude --mission whole-pack-round-trip-gate-01M5Y272
```

## Subtask T009 — Census

`WholePackCensus.of(Path packDir)` → `SortedMap<String path, String kind>` over every regular file (no symlink
following; `/` separators). Kind: `PackFileKind.bundled().classify(packDirName + "/" + path)` → `Mapped` gives the
schema name; otherwise `other:<first path segment>` (a root file gives `other:<file name>`). Note:
`PackFileKind` globs are relative to the directory holding the pack directories, so prefix the pack dir name.

## Subtask T010 — Expectation loading

`WholePackExpectation.load(String expectedRedYaml, String exclusionsYaml)` (texts, so tests pass inline YAML; the
gate loads classpath resources `whole-pack/expected-red.yaml` and `whole-pack/exclusions.yaml`).

- `expected-red.yaml`: `kinds: [{kind, reason, issue}]`, `files: [{path, reason, issue}]`.
- `exclusions.yaml`: `exclusions: [{match, reason}]`; `match` is a pack-relative glob (`*` within a segment, `**`
  across); reuse `FileSystems.getDefault().getPathMatcher("glob:" + match)` or a small translator — test `**/` at
  the root (`**/README.md` must match `README.md`).
- Refuse (IllegalArgumentException, message names the entry) a missing/blank `reason` or `issue`, an unknown key,
  a duplicate entry.

## Subtask T011 — Ratchet evaluation

`Evaluation evaluate(SortedMap<String,String> census, Set<String> modelledKinds, Map<String, Optional<String>>
observed)` where `observed` holds, for each path of a modelled kind, empty for green or the red detail.

Per file: modelled kind → GREEN or RED(detail); unmodelled → RED("no adapter for kind <k>"). Then: excluded (match)
→ EXCLUDED unless kind is modelled (problem `EXCLUDED_MODELLED`); RED with a `files` entry or a `kinds` entry →
EXPECTED_RED(detail + " — expected: <reason> (<issue>)").

Problems (each a line naming the entry; any problem fails the gate):

- `UNEXPECTED_RED path (kind): detail`
- `UNEXPECTEDLY_GREEN path` — a `files` entry whose file is green → "remove it from expected-red.yaml"
- `UNEXPECTEDLY_GREEN kind` — a `kinds` entry whose files are all green or excluded
- `STALE_BASELINE path` / `STALE_BASELINE kind` — entry matches no file / no file of that kind
- `STALE_EXCLUSION match` — matches no file
- `EXCLUDED_MODELLED path`
- `EMPTY_MODELLED_KIND kind` — a modelled kind with zero files (vacuity guard)

## Subtask T012 — Report

`WholePackReport.render(Evaluation, String packLabel, String pin)` → Markdown: header (pack, pin, totals), a kind
table sorted by kind (`| kind | files | green | expected red | excluded | red |`), a problems section (or "none"),
then one row per file sorted by path (`| path | kind | status | detail |`, pipes escaped). No timestamps — two runs
give identical bytes (NFR-002). Optional information lines (e.g. duplicate edges) appended per file.

## Subtask T013 — Tests (first)

Small hand-built census/observed maps:

- matching expectation → no problems; statuses as expected
- unlisted red → `UNEXPECTED_RED`; listed green file → `UNEXPECTEDLY_GREEN`; kind entry whose files all pass →
  `UNEXPECTEDLY_GREEN kind`
- stale baseline path / kind; stale exclusion; exclusion over a modelled file
- modelled kind with zero files → `EMPTY_MODELLED_KIND`
- every census path appears exactly once in the evaluation; counts sum to the census size
- loader refuses a blank reason / missing issue / unknown key
- report rendering is byte-identical across two calls and escapes `|`
- census over a temp dir: `a.tactic.yaml` → `tactic`; `missions/x/y.yaml` → `other:missions`; `pack.md` →
  `other:pack.md`; `x.graph.yaml` → `graph-shard`; `pack.yaml` → `pack-descriptor`

## Definition of Done

`./gradlew :packs:test --tests '*WholePackExpectationTest'` green; style gates clean; no production change.

## Reviewer guidance

Try to make the gate pass with a do-nothing change: empty census, empty observed, everything baselined by kind.
Each must produce a problem or be impossible.
