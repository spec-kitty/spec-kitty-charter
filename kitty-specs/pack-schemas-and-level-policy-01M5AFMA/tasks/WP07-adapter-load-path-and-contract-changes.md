---
work_package_id: WP07
title: Adapter load path on the schemas, and the contract changes scenarios-first
dependencies:
- WP06
- WP03
- WP04
requirement_refs:
- C-001
- C-005
- FR-008
- FR-013
- SC-004
- SC-006
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T024
- T030
- T031
- T032
- T033
- T034
- T035
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
- at: '2026-10-19T19:05:00Z'
  event: revised
  by: post-tasks squad (renata, priti, ivan)
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/
create_intent:
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemPackInspector.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/FileSystemPackInspectorTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/LoadOutcomeTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/VocabularyEqualityTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/SchemaKeywordCoverageTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackDescriptorReaderTest.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyRulesTest.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/schemas/DiagnosticCode.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/packs.feature
- contracts/charter/scenarios/action_context.feature
- contracts/charter/CHANGELOG.md
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Diagnostic.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyRulesTest.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/GraphDocumentReader.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackDescriptorReader.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemPackInspector.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemCharterPackRepository.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/GraphDocumentReaderTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackDescriptorReaderTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/FileSystemCharterPackRepositoryTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/FileSystemPackInspectorTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/LoadOutcomeTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/VocabularyEqualityTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/SchemaKeywordCoverageTest.java
- service/adapter-out-yaml/src/test/resources/load-fixtures/**
- service/charter-service/src/test/java/ai/speckitty/charter/service/ProductionWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/CharterWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ScenarioPacksTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP07 — Adapter load path on the schemas, and the contract changes scenarios-first

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt: scenarios first, test first, and no incompatible contract change
beyond the rulings. Read the **mission-wide rules** in [tasks.md](../tasks.md) before you start.

## Objective

This WP wires everything together on the **load path**:

- The three load-path file kinds (`pack.yaml`, root `*.graph.yaml` shards and `drg/fragment.yaml`)
  become **facts** read through WP04's `PackSchemas`, and `PackPolicy` judges them.
- The hand-coded structural checks go, so each rule has one authority (FR-008).
- The operator's wire changes land **scenario-first** (FR-013; D1-D7 in
  [contracts/contract-deltas.md](../contracts/contract-deltas.md)).
- The org-fragment rule (R16) and the `pack_unknown_key` wire projection land here too, together
  with the scenarios and fixtures they change, so every commit stays green.
- Every pinned text that no ruling changes stays as it is (FR-003, SC-004).

Read first:
- [spec.md](../spec.md): US3, FR-003, FR-008, FR-013, C-001, C-005, and rulings R3, R5-R8, R13,
  R16;
- [plan.md](../plan.md): "One owner", "Load is a projection of judge", "Precedence" and "The
  detail vocabulary";
- [contracts/contract-deltas.md](../contracts/contract-deltas.md) and
  [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md), which carry the WP06
  texts;
- `contracts/charter/scenarios/README.md`, which lists the step vocabulary;
- `contracts/charter/CHANGELOG.md`, `## 1.0.0-SNAPSHOT`.

**Parallel runner A** (#95, #113) also edits `packs.feature` and `CHANGELOG.md`. Keep every hunk
minimal and self-contained (risk K2).

## Branch Strategy and environment

- **Branches.** The planning base and merge target are `feat/pack-schemas-and-level-policy`. You
  work in the lane worktree, the same lane as WP01, WP05 and WP06.
- **Open-packs checkout.** Pass `-PopenPacksDir=/home/user/spec-kitty-open-packs` (absolute) to
  every Gradle command: it is the new layout at the WP03 pin, URN-only (#52).
- **Breaking-change check.** Do **not** use `-PcontractBaseline=none` to silence it. First run
  `git fetch --tags origin` and `git tag --list 'contract-charter-v*'`.
  - If a tag exists, stop and report to the orchestrator: D1 would be breaking (K3).
  - If no tag exists and the gate refuses only because the clone is shallow, then
    `-PcontractBaseline=none` is acceptable, because there is no baseline to compare. Say so in
    the evidence.
- **Implement command:**
  `spec-kitty agent action implement WP07 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T024 — Vocabulary equality and schema keyword coverage (moved here from WP05)

**Steps**:

1. **`VocabularyEqualityTest`**, run red first against a deliberately wrong expectation, then fixed.
   It reads the bundled schemas and `file-kinds.yaml` through `PackSchemas` and `PackFileKind`.
   It asserts four equalities:
   - the kinds in the URN alternation of `graph-shard.schema.json` equal the `NodeKind` tokens,
     which equal `file-kinds.yaml` `node_kinds`;
   - the `org-fragment.schema.json` node `kind` enum equals the `fragment_kinds` keys, which equal
     the domain of `NodeKind.fromPlural`;
   - the `file-kinds.yaml` artifact kinds equal the `ArtifactKind` tokens;
   - every load-path `PackFindingCode` projection token exists in
     `contracts/charter/schemas/DiagnosticCode.yaml` or `CharterRefusalCode.yaml`. This one is
     enforced after T030 adds D1.
2. **`SchemaKeywordCoverageTest`** walks every bundled schema and collects every keyword in use.
   It fails if a keyword has neither a vocabulary row in `PackPolicy` nor the declared fixed
   fallback. Expose the row table, or a `handles(keyword)` method, from `PackPolicy` for the test.

### T030 — Contract deltas, scenarios and domain switches first (red)

**Steps**:

1. **D1.** Add the enum value `pack_unknown_key` to `DiagnosticCode.yaml`. Its description: a key
   in `pack.yaml` that the pack descriptor schema does not declare, retired fields included; it is
   reported, not refused (R5, R13).

   **In the same commit**, add `Diagnostic.Code.PACK_UNKNOWN_KEY("pack_unknown_key")` and map
   `PackFindingCode.PACK_UNKNOWN_KEY.diagnostic()` to it. `adapter-in-rest`'s
   `WireTokenParityTest` requires the domain enum and the contract enum to match exactly.
2. **D2.** In `CharterRefusalCode.yaml`, the `unresolved_edge_endpoint` description says the code
   is no longer raised: edge endpoints are URNs, and a non-URN endpoint is `drg_schema_error` (R6).
   It stays in the enum.
3. **`org_fragment_missing` (R16).**
   - Add the rule to `PackPolicy.judge`. The detail is `org pack <n> has no drg/fragment.yaml`.
   - It fires when the configured level is ORG, or on a bare path when the declared level is ORG,
     and the fragment is `Absent`. It never fires on `Unreadable`.
   - It belongs to precedence group 4, attached to `pack.yaml`.
   - Add its domain tests to `PackPolicyRulesTest`, red first, covering four cases: configured ORG
     with the fragment Absent; configured BUILT_IN; bare path with declared ORG; fragment
     Unreadable.
4. **Scenarios** in `packs.feature` (`@contract`, with inline fixtures). Use the exact texts from
   contracts/pack-finding-codes.md, which are WP06's:
   - **D3 (new).** "A pack descriptor with an undeclared key loads with a diagnostic". The org pack
     `internal` has `pack.yaml` with `pack_level: org` and `surprise: true`, plus a minimal
     `drg/fragment.yaml`. The answer is 200, with the diagnostic
     `{"code": "pack_unknown_key", "detail": "pack.yaml of internal declares unknown key 'surprise'; it is ignored"}`.
   - **D4 (changed, `:83-99`).** It becomes 500 `drg_schema_error` with the detail
     `qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN`. The title stays "Invalid DRG
     document". Rename the scenario.
   - **D5 (new).** `requires_core: banana` gives 500 `pack_unreadable`, title "Pack unreadable",
     with the WP06 text.
   - **D6 (new).** The org pack `lonely` has only `pack.yaml` (`pack_level: org`). It gives 500
     `pack_unreadable` with the detail `org pack lonely has no drg/fragment.yaml`.
   - **D7 (fixtures).** In scenario 1, endpoint `QA_001` becomes `directive:QA_001`, and
     `pack_name: qa` is removed from both fragment fixtures. Scenarios 2 and 3 (`internal`,
     `rogue`) get a minimal `drg/fragment.yaml`, so their pinned outcomes stay. Fix
     `action_context.feature` only where the new rules require it.
5. **CHANGELOG.** One line per change under `## 1.0.0-SNAPSHOT`: D1, D3, D5 and D6 under
   `### Added`; D2, D4 and D7 under `### Changed`. Cite #114 and the ruling.
6. **Red.** Run `./gradlew :charter-service:test --tests '*CharterContractSuite*' -PopenPacksDir=… --max-workers=2`.
   D3 to D6 fail as assertion failures on the HTTP answer, not compile errors. Record the output.

### T031 — `GraphDocumentReader` reports facts

**Steps**:

1. `read(text, shape, origin)` returns `PackContents.FileRead<RawGraph>`:
   - `Unreadable(ParseFailure)` on a YAML error, a duplicate key or a non-string key. Keep the
     first-line rule: no host paths. It **never** throws on content.
   - `Unreadable(violations)` when `PackSchemas.validate("graph-shard" | "org-fragment", …)`
     reports any violation.
   - Otherwise `Read(RawGraph)`.
2. Delete:
   - `SHARD_KEYS`, `FRAGMENT_KEYS`, the node keys and `EDGE_KEYS`, and `checkKeys`;
   - the type checks and the URN-to-kind check;
   - `pluralKind` (replaced by `NodeKind.fromPlural`);
   - the endpoint binding, the legacy-tension mapping and `checkStrength` (now in `PackPolicy`).

   It may still throw on I/O.
3. Rewrite `GraphDocumentReaderTest` to assert facts: the keyword, pointer and property of each
   violation.

### T032 — `PackDescriptorReader` reports facts

**Steps**:

1. It returns `FileRead<PackDescriptor>` through `PackSchemas.validate("pack-descriptor", …)`.
   - A parse failure becomes `Unreadable(ParseFailure)`.
   - When the descriptor is valid, fill `declaredLevel`, `requiresCore` and the other fields.
   - `undeclaredKeys` are the top-level keys missing from the schema's `properties`. Get that key
     set from `PackSchemas`; do not hard-code it. Retired fields count.
2. Remove the hand-coded checks.
3. Move the WP01 ratchets ("has no <key>", "must be a string", "declares an unknown pack_level",
   "is not a mapping") to repository-level assertions in T034. Their texts must stay unchanged:
   `PackPolicy` produces them now.

### T033 — `FileSystemPackInspector` (LOAD_PATH); the repository delegates

**Steps**:

1. `FileSystemPackInspector implements PackInspector`.
   - `inspect(root, LOAD_PATH)` resolves `root.locator()` to a `Path`. It reads `pack.yaml`, the
     root shards (sorted) and `drg/fragment.yaml`, each through the readers, as `Absent`,
     `Unreadable` or `Read`. It returns `PackContents`.
   - I/O failures throw `CharterRefusal(PACK_UNREADABLE, "... cannot be read")`, as today.
   - `FULL` throws `UnsupportedOperationException("FULL scope lands in WP08")`.
2. `FileSystemCharterPackRepository.loadPack(location)` becomes
   `PackPolicy.load(inspector.inspect(new PackRoot(name, Optional.of(level), path.toString()), LOAD_PATH))`.
   That is WP05's single-argument overload. **Delete** WP01's `load(contents, graph)` overload and
   its tests, which now live in `PackPolicyTest`. `fingerprint()` is unchanged.

### T034 — Load outcomes, precedence and pinned texts on the adapter

**Steps**:

1. **`LoadOutcomeTest`** is parametrized over `src/test/resources/load-fixtures/<case>/`. There is
   one case per scenario fixture, plus multi-defect and two-fault packs. Each case has an
   **independent oracle**, `expected.json`: either the refusal code and detail, or the exact
   diagnostics list, written by hand from the spec and the pinned texts. The test asserts
   `repository.load` against that file.

   This is not the load-versus-judge parity check: that would be a tautology on one scope. WP08
   compares LOAD_PATH against FULL.
2. **Precedence.**
   - **Two-fault**: a level mismatch plus a bad shard refuses with the shard's `drg_schema_error`.
   - **Multi-defect shard**: an unknown root key, a bad node at index 10 and a bad edge at index 0
     refuse with the root key's text.
3. **Pinned texts.** Assert at repository level every literal in "Pinned detail texts" in
   [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md) that a fixture can
   trigger. These include the old `GraphDocumentReaderTest:97-151` cases and the descriptor
   ratchets; the R6 row uses its new text.
4. **Red.** Write these against the pre-T031 code where they compile. Record the failures, which
   are assertion failures.

### T035 — Fixture updates and the full gate

**Steps**:

1. Update `FileSystemCharterPackRepositoryTest`, `ProductionWiringTest`, `CharterWiringTest` and
   `ScenarioPacksTest` where they build org packs without a fragment, use bare endpoints or carry
   fragment header fields. Change assertions only where R6, R7 or R16 require it, and note each in
   the commit.
2. Run the full gate,
   `./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs --max-workers=2`, and get
   it all green. Note the breaking-change check rule above.

## Definition of Done

- [ ] D1 to D7 are in the contract, scenarios and CHANGELOG. Each new or changed scenario was red
      first, as an assertion failure. `Diagnostic.Code.PACK_UNKNOWN_KEY` and the contract value
      landed together, and `WireTokenParityTest` is green.
- [ ] `org_fragment_missing` is live, with its domain tests and scenario D6.
- [ ] The readers report facts only. The hand-coded structural checks and the old `load` overload
      are gone.
- [ ] `LoadOutcomeTest` (independent oracle), the precedence tests, the repository-level pinned
      texts, `VocabularyEqualityTest` and `SchemaKeywordCoverageTest` are all green.
- [ ] `verifyAll` is green.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Reviewer guidance (opus, reviewer-renata)

- Read every changed scenario against the rulings.
- Grep `adapter-out-yaml/src/main` for `RefusalCode.` and for literals that look like finding
  texts. There must be none, apart from the I/O refusals.
- Edit one `expected.json` and check that `LoadOutcomeTest` fails.
