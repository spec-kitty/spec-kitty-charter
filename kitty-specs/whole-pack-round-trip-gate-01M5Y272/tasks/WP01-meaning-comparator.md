---
work_package_id: WP01
title: Meaning comparator
dependencies: []
requirement_refs:
- FR-003
- FR-004
planning_base_branch: feat/whole-pack-round-trip-gate
merge_target_branch: feat/whole-pack-round-trip-gate
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-round-trip-gate. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-round-trip-gate unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-round-trip-gate-01M5Y272
base_commit: 1d9963ac27ffe2f46c30ada496925a671e84ebfd
created_at: '2026-10-27T07:03:34.189462+00:00'
subtasks:
- T001
- T002
- T003
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparator.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparatorTest.java
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparator.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparatorTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Meaning comparator

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

A test-scope `MeaningComparator` that decides whether a written YAML document means the same as its source
document, and when not, returns the **first difference** with a readable path. It is the oracle of the whole-pack
gate (spec FR-003, FR-004; research R-4). Its rules are provisional until the #188 fidelity ADR lands, so they
live behind one policy seam (C-005).

## Branch Strategy

Planning base and final merge target: `feat/whole-pack-round-trip-gate`. The execution worktree is allocated per
computed lane from `lanes.json`; do not pick a base branch by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission whole-pack-round-trip-gate-01M5Y272
```

## Context

- Operator rulings (#188): unknown fields refused; equality by meaning, not bytes; pack inconsistencies are fixed in
  the packs (no special cases); DRG edges compare in canonical form — order and duplicates do not count.
- ADR-0025 / ADR-0027: edge identity. Do **not** use `DoctrineGraph.equals` (order-sensitive list) or
  `Relationship.equals` (unordered pair only). Compare edge **maps** over every field.
- `docs/architecture/architectural-patterns-applied.md`: list order inside an artefact (steps, references) is
  meaningful.
- Parse with the package's `Yaml.load(String)` (package-private, same package) so null forms match the readers.

## Subtask T001 — Specification by example (write first)

`MeaningComparatorTest` (JUnit 6 + AssertJ, plain `test` task — no tag). Each rule has an equal case **and** an
unequal twin, so no rule can be made lenient unnoticed:

| Case | Source | Written | Expected |
|---|---|---|---|
| key order | `a: 1\nb: 2` | `b: 2\na: 1` | equal |
| quoting / style | `id: 'x'` | `id: x` | equal |
| comments, blank lines | `# c\nid: x` | `id: x` | equal |
| block vs flow list | `l:\n- a` | `l: [a]` | equal |
| missing key | `a: 1\nb: 2` | `a: 1` | difference at `b`, written `<absent>` |
| extra key | `a: 1` | `a: 1\nz: 3` | difference at `z`, source `<absent>` |
| changed scalar | `a: 1` | `a: 2` | difference at `a` |
| type change | `v: '1.0'` | `v: 1.0` | difference at `v` (string vs number) |
| list order | `l: [a, b]` | `l: [b, a]` | difference at `l[0]` |
| nested path | `steps:\n- title: t` | `steps:\n- title: u` | difference at `steps[0].title` |
| empty list vs absent | `l: []` | `{}` | difference at `l` (strict, R-4; operator Q2 on #187) |
| null vs absent | `n: null` | `{}` | difference at `n` |
| multi-line text | `d: |\n  x\n` | `d: "x\n"` | equal |

## Subtask T002 — Comparator core

```java
final class MeaningComparator {
    /** The rule set; one instance until #188 lands. */
    static MeaningComparator provisional() { ... }

    /** @param kind the gate kind of the file (selects graph-shard rules) */
    Optional<Difference> compare(String kind, @Nullable Object source, @Nullable Object written);

    /** Parses both texts with the readers' YAML settings, then compares. */
    Optional<Difference> compareTexts(String kind, String sourceText, String writtenText);

    /** Duplicate edges seen in the last graph-shard comparison of source, as information. */
    record Difference(String path, String source, String written) {}
}
```

- Maps: compare key sets (report the first missing/extra key in sorted key order — deterministic), then recurse per
  key in sorted order.
- Lists: same size and element-wise, in order; size mismatch reports the first index that differs or is absent.
- Scalars: `Objects.equals` on the loaded Java values (String vs Integer vs Double vs Boolean are different).
- Render values compactly (`String.valueOf`, truncated to ~80 chars), `<absent>` for a missing key.
- Paths: `a.b`, `l[2]`, `steps[0].title`; root is `$`.

## Subtask T003 — Graph-shard rules

For `kind == "graph-shard"` (the gate kind of `*.graph.yaml`), at the **document root only**:

- `edges`: build a `TreeSet<String>` of canonical edge renderings — each edge map rendered with keys sorted
  (`source=…,target=…,relation=…,impacts=…,when=…,reason=…`; absent keys omitted). Compare the two sets. First
  difference path: `edges{<canonical rendering>}` with `<absent>` on the side lacking it. Duplicates collapse
  (ruling); record them via `duplicateEdges(Object document)` → `List<String>` for the report's information line.
- `nodes`: map keyed by `urn`; compare key sets, then each node map recursively (path `nodes{<urn>}.label`).
- Every other root key compares normally (`schema_version`, `generated_at`, `generated_by`).
- Tests: permuted + duplicated edges → equal; one changed `relation` → unequal naming the edge; one removed edge →
  unequal; node label change → `nodes{<urn>}.label`; reordered nodes → equal; an edge differing only in `when` →
  unequal (all fields count, unlike `Relationship.equals`).

## Definition of Done

- All T001/T003 cases pass; `./gradlew :packs:test --tests '*MeaningComparatorTest'` green on Java 25 (or 21
  fallback locally).
- Style gates clean (`:packs:spotlessCheck :packs:checkstyleTest` and Error Prone / NullAway on compile).
- No production code changed.

## Reviewer guidance

Look for a lenient rule without an unequal twin; for order-freedom leaking beyond `edges`/`nodes` of a graph
shard; for comparison of DSOs or domain objects instead of parsed trees.
