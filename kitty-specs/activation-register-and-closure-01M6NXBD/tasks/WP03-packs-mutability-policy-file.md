---
work_package_id: WP03
title: packs per-pack mutability policy file (ADR-0063)
dependencies:
- WP02
requirement_refs:
- FR-022
- FR-007
- C-005
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: 47565e9fae3172d5f1c0bc3aaca3a11418fc2ec6
created_at: '2026-11-05T19:44:15.241520+00:00'
subtasks:
- T013
- T014
- T015
- T016
- T017
- T018
- T019
- T094
- T095
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PerPackPolicy.java
create_intent:
- contracts/packs/schemas/policy.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PerPackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicyRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PolicyYaml.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PerPackPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicyYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicySchemaTest.java
- docs/adr/0063-pack-mutability-policy-file.md
- contracts/charter/changes/2026-11-05-pack-mutability-policy-file.md
execution_mode: code_change
model: ''
owned_files:
- contracts/packs/schemas/pack-descriptor.schema.json
- contracts/packs/schemas/policy.schema.json
- contracts/packs/file-kinds.yaml
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackPaths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicySource.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PerPackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicyRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PolicyYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReader.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PerPackPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicyYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicySchemaTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorReaderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- docs/adr/0063-pack-mutability-policy-file.md
- contracts/charter/changes/2026-11-05-pack-mutability-policy-file.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP03: packs per-pack mutability policy file (ADR-0063)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Let a pack declare locks (FR-022, US6), so `artifact_locked` can be raised end to end:

- `pack.yaml` gains an optional `mutability_policy` naming a pack-root policy file;
- a new closed `contracts/packs/schemas/policy.schema.json` validates it (`schema_version`, `kind_defaults`,
  `artifacts`; role locks and pins are reserved and absent);
- `PolicyYaml` reads the file into the existing `MutabilityPolicySource`;
- a `PerPackPolicy` registry, keyed by pack, replaces the single global source. `levelIn(closure, urn)` answers the
  **highest** level along the closure and the pack that set it (a later pack raises, never lowers, ADR-0012 D3);
- the registration write gate `PackMutabilityPolicy` and the activation lock query (WP04) share that one function;
- a named but missing or invalid file makes the pack unreadable (`pack_unreadable`) on the offering and on any
  closure that holds it;
- ADR-0063 records the format.

No pack ships a policy file yet, so no registration answer changes.

Implementation command:
`spec-kitty agent action implement WP03 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-03a. **Research:** R5, R6; squad findings B5, A6.
- **Decisions:** `DM-01M6PCWSA65497T9A7J80K6V56` (build the reader now), `DM-01M6PD07Z86A3SPFTXJEEZGE4R` (format).
- **ADRs cited by ADR-0063:** ADR-0012 D2 (the policy lives with the pack), D4 (levels per kind and per artifact), D5
  (the built-in pack's own file and the `replaceable-builtins.yaml` migration are follow-ups); ADR-0030 (role locks and
  pins, reserved); ADR-0042 (pack-level files).
- **ADR numbering:** 0059 is on `main` (PR #376 merged); 0060 (native build nightly) and 0061 (one replica per
  deployment, PR #392) are taken. This ADR was written as 0062 and renumbered **0063** on 2026-11-06, after
  main took 0062 (nightly mutation floor).
- **Collisions:** PR #377 touches `DomainRepositoryService`, `PackCommitStore` and `PackMutabilityPolicyTest`; PR #390
  touches `FilesystemPackStore`. Rebase in that order; keep their logic and re-apply this WP's additions on top.
  **Gate (P3):** start only after #377 merges, or after rebasing over #377's head; record every touched `packs` file
  in the review evidence. Also rebase over #379, #384, #391, #392 and #394 as they land.
- **Grounding:**
  - `MutabilityPolicySource` (packs domain) already models `kind_defaults` and URN entries, with `levelOf(Pack,
    Urn)`. Its `pack` parameter is unused today: the source is global.
  - `PackMutabilityPolicy` is a `@Component @Primary` `WritePolicyGate` whose no-arg constructor uses an empty
    source.
  - `PackDescriptor` (kernel) carries pack-relative paths in `PackPaths(activeCharter, packDrg)`. The new
    `mutability_policy` path belongs there (5 constructor call sites, versus 50 for `PackDescriptor`).
  - `DomainRepositoryService.compositionFacts` already assembles the pack and fails closed with `pack_unreadable`.
    That is where the policy file is read for the compose, so the offering and every closure judge it with no
    extra read. `PackCompositionFacts` gains the parsed policy.
  - `contracts/packs/file-kinds.yaml` classifies every pack file. `PackSchemasTest` fails when a schema file is not
    listed.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP02 (the closure function the gate uses).
- Rebase onto the lane base, after #377 and #390 if they have merged, before review.

## Subtask Guidance

### Subtask T013: Schemas and the file-kind rule

**Purpose:** the format is contract first (C-001 applies to pack schemas too).

**Steps (red first):**
1. `PolicySchemaTest` (packs, `infra/storage`): valid files pass, and each of these fails: an unknown top-level key;
   a level other than `open`, `extendable`, `locked`; a URN key that is not a bare URN; a kind token outside the
   artifact kinds; `schema_version` other than 1; a `roles` or `pins` key (reserved, so rejected while closed).
2. Create `contracts/packs/schemas/policy.schema.json`, closed (`additionalProperties: false`), modelled on the
   existing schemas' header style: `$schema`, `$id`, `title`, `description`. `kind_defaults` is an object whose keys
   are the artifact-kind tokens (reuse the `$defs` kind enum the other schemas repeat; `VocabularyEqualityTest`
   checks the lists agree) and whose values are the level enum. `artifacts` has `patternProperties` on the URN
   grammar, also with level values.
3. In `pack-descriptor.schema.json`, add an optional `mutability_policy`: a string naming a **pack-root** YAML file
   (`pattern: "^[A-Za-z0-9][A-Za-z0-9._-]*\\.ya?ml$"`, no `/`). Extend the long `description` text with one
   paragraph, in the same style.
4. In `contracts/packs/file-kinds.yaml`, add a `files` rule `match: "*/policy.yaml"`, `schema: policy`, beside the
   pack-level files block (#195). **Decide and record in ADR-0063:** the whole-pack gate classifies only
   `policy.yaml`. Either restrict the descriptor value to `policy.yaml` for now (`const`), or keep the pattern and
   make the store read the named file regardless of its classification. Recommended: `const: policy.yaml` in this
   version, with the key kept so a later version can widen it. Flag the choice for the operator in the ADR's open
   questions. **As implemented:** `const: policy.yaml`. The descriptor names the pack-relative path, the schema
   constrains it to `policy.yaml` for now, and ADR-0063 records the choice.

**Validation:** `./gradlew :packs:test --tests '*PolicySchemaTest*' --tests '*PackSchemasTest*'
--tests '*VocabularyEqualityTest*' --tests '*PackLevelSchemasTest*'`.

### Subtask T014: The descriptor path in the kernel and the reader

**Steps (red first):**
1. `PackDescriptorReaderTest`: a `pack.yaml` with `mutability_policy: policy.yaml` reads into
   `descriptor.paths().mutabilityPolicy()` as `Optional.of("policy.yaml")`; without the key it is empty.
2. `PackPaths` gains `Optional<String> mutabilityPolicy` (the third component). Keep a two-argument constructor so
   that the existing call sites compile unchanged, and update `none()`.
3. `PackDescriptorDso` and `PackDescriptorReader` map the key. If the DSO writes descriptors back (whole-pack
   round trip), write the key in schema order.

### Subtask T015: `PolicyYaml`, the reader

**Steps (red first):**
1. `PolicyYamlTest`: a valid file gives a `MutabilityPolicySource` whose `levelOf` returns the URN entry, then the
   kind default, then `OPEN`. A file failing the schema throws a typed failure carrying the schema message.
2. Create `infra/storage/PolicyYaml.java`, package-private like `PresetYaml`. Read through the existing
   `ParsedYaml`/`EmbeddedSchemas` path used by the other pack files (look at how `PresetYaml` is validated), and
   build the source with `MutabilityPolicySource.builder()`. Map kind tokens through `NodeKind`/`ArtifactKind`
   `fromToken`, and levels through `MutabilityLevel.fromToken`.
3. No new library: the YAML engine and the networknt validator are already on the module's classpath.

### Subtask T016: Read the policy per pack; fail closed

**Purpose:** the offering and every closure judge the file (US6 AS3).

**Steps (red first, `DomainRepositoryServiceTest`):**
1. A pack whose descriptor names `policy.yaml` and ships it: `compositionFacts(pack).policy()` holds its levels.
2. The file is missing: `compositionFacts` throws `CharterRefusal(PACK_UNREADABLE, "… names mutability_policy
   policy.yaml, which …")`.
3. The file fails its schema: the same, naming the file.
4. No key: `policy()` is `MutabilityPolicySource.empty()`.

**Implementation:**
- `PackCompositionFacts` gains `MutabilityPolicySource policy` as a fourth component. Keep a three-argument
  constructor defaulting to `empty()`, so the fakes in the application tests compile unchanged.
- In `DomainRepositoryService.compositionFacts`, after `readable(pack)`, read the named file through the pack's
  store (the same resolved store the graphs come from) and parse it with `PolicyYaml`. Extract
  `policyOf(Resolved, Pack)` so that `compositionFacts` stays under 60 lines. It is already near the limit; split
  the edge loop into a helper at the same time.
- Create the domain port `MutabilityPolicyRepository` (`MutabilityPolicySource policyOf(PackIdentifier pack)`). Ports
  live in a domain ring (`PORT_INTERFACES_RESIDE_IN_DOMAIN`). `DomainRepositoryService` implements it with the same
  helper.

### Subtask T017: `PerPackPolicy` and `levelIn(closure, urn)`

**Steps (red first, `PerPackPolicyTest`, pure domain):**
1. The built-in pack leaves `directive:D1` open, and an org pack locks it: `levelIn([builtin, org], D1)` answers
   `LOCKED` set by org. With the order reversed, still `LOCKED` by org: the highest wins and order does not lower it.
2. A kind default `extendable` in one pack and a URN entry `open` in a later one: the answer is `extendable` (never
   lowered).
3. No pack names the URN or its kind: `OPEN`, with no setting pack.
4. Ties: the first pack in closure order that reached the max is the one reported.

**Implementation:** `record PerPackPolicy(Map<String descriptorPackId, MutabilityPolicySource> byPack)` with
`Level levelIn(List<String> closurePackIds, Urn urn)`, returning a small record `(MutabilityLevel level,
Optional<String> setBy)`. Compare levels by ordinal (OPEN < EXTENDABLE < LOCKED); add a `rank()` or `atLeast` to
`MutabilityLevel` only if it is clearer. That enum is not in `owned_files`; prefer a comparator inside
`PerPackPolicy`.

Change `MutabilityPolicySource.levelOf(Pack, Urn)` to `levelOf(Urn)` only if every caller can be updated in this WP.
Otherwise leave it and add an overload, and do not leave the unused `pack` parameter misleading: document it.

### Subtask T018: The write gate shares the function

**Purpose:** a registration into pack P is gated at the highest level along P's composition closure (squad B5).

**Steps (red first, `PackMutabilityPolicyTest`, in `src/test/.../packs/domain/` where it lives today):**
1. With a fake `MutabilityPolicyRepository` and a fake closure (P depends on Q, Q locks `directive:X`), registering
   `directive:X` into P is refused `GOVERNANCE_POLICY_VIOLATION`, and the message names Q.
2. With no policy anywhere, every operation is permitted (today's behaviour).

**Implementation:**
- `PackMutabilityPolicy` takes `MutabilityPolicyRepository` and a closure supplier. The closure is the pack's
  `compositionClosure` (WP02) over the configured descriptors. To avoid an application-to-application dependency
  cycle, inject `OfferingReadService` and use `composed(Set.of(pack.id()))` for the ordered closure ids. Keep the
  existing constructor `PackMutabilityPolicy(MutabilityPolicySource)` for tests, as a single-pack closure.
- `authorize` builds a `PerPackPolicy` for the closure ids and calls `levelIn`. Keep the method under the limits.

**Validation:** the registration scenarios of `directives.feature` and `relationships.feature` stay green (no pack
ships a file, so no answer changes).

### Subtask T019: ADR-0063 and the open-packs note

**Steps:**
1. **Number check (A11):** at write time, list `docs/adr/` on `main` and the ADR files of the open PRs
   (`gh pr list --state open` then `gh pr diff <n> --name-only | grep docs/adr/`). Take the next free number. 0062 is
   expected (0059 is on `main`, 0060 is the native build ADR and #392 holds 0061); if it is taken, renumber the file and every reference to it
   (WP10's amendment notes, the T094 fragment), and say so in the review notes.
   Write `docs/adr/0063-pack-mutability-policy-file.md` with the same frontmatter shape as
   `docs/adr/0058-*.md` (`okf_version`, `title`, `description`, `doc_status`, `updated`, `decision_status:
   Accepted`, `decision_date: '2026-11-05'`, `type: ADR`, `kind: ADR`, `related`, `audience`).
2. Sections: Status, Context, Decision (D1 the descriptor key; D2 the file and its closed schema; D3 the highest
   level along the closure wins and a later pack never lowers it; D4 a missing or invalid named file makes the pack
   unreadable; D5 role locks and pins reserved), Consequences, Open questions (the `policy.yaml`-only choice from
   T013; the built-in pack's own file and `replaceable-builtins.yaml` per ADR-0012 D5; open-packs ships none yet),
   and Related. Cite ADR-0012 D2, D4, D5, ADR-0030, ADR-0042 and ADR-0020 D9.
3. The open-packs note (that the key exists and no pack is required to ship a file) goes in the ADR's Consequences.
   WP10's docs changelog fragment links it.
4. Run `python3 scripts/docs/check_docs.py all` and `npx cspell@9.2.0 --no-progress`. If a new word fails
   spelling, add it to `cspell.json` in sorted position. `cspell.json` is not in this WP's map: record it as an
   out-of-map edit in the review notes, or reword.

### Subtask T094: Contract changelog fragment for the pack-schema change (R3)

**Purpose:** the repository records pack-schema changes in the charter contract changelog (see
`contracts/charter/CHANGELOG.md` around lines 788 and 805: pack-file schema behaviour is logged there, and
`contracts/packs/` has no changelog of its own). Since ADR-0047, a change adds a fragment instead of editing the
CHANGELOG.

**Steps:**
1. Create `contracts/charter/changes/2026-11-05-pack-mutability-policy-file.md` (date it the day you write it), with
   one `### Added` block:
   - `pack.yaml` gains the optional `mutability_policy`;
   - the new closed `contracts/packs/schemas/policy.schema.json` (`schema_version`, `kind_defaults`, `artifacts`;
     role locks and pins reserved);
   - a named file that is missing or invalid makes the pack `pack_unreadable` (no new refusal code);
   - the file is digested into the source fingerprint (T095);
   - cite ADR-0063.
2. Follow `contracts/charter/changes/README.md` (exactly one `###` block). Run `./gradlew :contracts:check`.

### Subtask T095: Digest `policy.yaml` in the fingerprint (J3)

**Purpose:** a policy edit must move the source fingerprint, or a lock change would be served from a stale
`ConsistentRead`.

**Steps (red first, `PackFileSetTest`):**
1. A pack with `policy.yaml`: editing a level changes the digest list's bytes, so the fingerprint moves.
2. A pack without `policy.yaml`: its digest list is unchanged from before this WP. This is the rule of the pack-level-files mission (#195, ADR-0042: NFR-002 there, not this mission's NFR-002) that the
   `packLevelTail` Javadoc states.
3. In `PackFileSet.packLevelTail`, append the descriptor-named policy file after the manifest, when it is a regular
   file. As implemented, T013 chose `const: policy.yaml`, so append `policy.yaml`. Update the Javadoc's ordered list.
4. Run `:packs:test --tests '*PackFileSet*'`, and `:packs:timingBudgets` alone, report-only.

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction (not yet enforced on `main`; the reviewer measures by hand).
`DomainRepositoryService.compositionFacts` must not grow past 60 lines; split it. No new suppression, no disabled
test.

## Gate commands

```bash
./gradlew :kernel:check
./gradlew :packs:check -x openPacksRoundTrip
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*'
python3 scripts/docs/check_docs.py all
```

## Definition of Done

- [ ] `policy.schema.json` (closed) and the descriptor key exist; `file-kinds.yaml` lists the file;
      `PackSchemasTest` and `VocabularyEqualityTest` pass.
- [ ] `PolicyYaml` reads a file into `MutabilityPolicySource`; a missing or invalid named file is `pack_unreadable` on
      `compositionFacts`.
- [ ] `PerPackPolicy.levelIn` answers the highest level and the setting pack; the tests cover both orders.
- [ ] `PackMutabilityPolicy` uses `PerPackPolicy` over the pack's closure; the existing registration scenarios are
      unchanged.
- [ ] ADR-0063 is written and the docs checks pass.
- [ ] The contract fragment for the pack-schema change exists; `:contracts:check` is green.
- [ ] `policy.yaml` is digested: a policy edit moves the fingerprint, and a pack without one keeps its digest.
- [ ] No new suppression, and no disabled test.

## Risks

- **The whole-pack gate and the derived manifest** (ADR-0042) must not refuse a pack that ships `policy.yaml`. The
  `files` rule handles classification. Run `:packs:openPacksRoundTrip` if the checkout is present (no open pack
  ships the file, so it should be unaffected).
- **The write gate now composes per registration.** That is one closure compose per write. It is acceptable here
  (registrations are rare), but note the cost in the Javadoc.
- **#377's rewrite of `PackMutabilityPolicyTest`** may conflict; keep both sets of cases.

## Reviewer guidance (reviewer-renata)

- **Verify the "never lower" rule** with the reversed-order test. A test with one order only cannot tell "highest
  wins" from "last wins".
- **Verify fail-closed:** demand the missing-file and invalid-file cases on `compositionFacts`, and one offering-level
  test or scenario run showing `pack_unreadable` names the pack.
- **Non-fakeable evidence:** the schema test's rejection list (each reserved key named), the
  `PackMutabilityPolicyTest` refusal message naming the locking pack, and `git diff --stat` showing no change to any
  registration scenario.
- **Check ADR-0063 cites ADR-0012 D2, D4, D5 and ADR-0030**, and records the `policy.yaml` decision as open.
