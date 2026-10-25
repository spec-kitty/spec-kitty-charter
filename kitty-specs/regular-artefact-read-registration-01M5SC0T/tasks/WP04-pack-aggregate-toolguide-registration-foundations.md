---
work_package_id: WP04
title: Pack aggregate + Toolguide domain/storage + registration foundations (commit store, refusals, ETag)
dependencies:
- WP02
- WP03
requirement_refs:
- FR-008
- FR-015
- NFR-002
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
subtasks:
- T019
- T020
- T021
- T022
- T023
- T024
- T025
- T026
- T027
phase: Phase 4 - Aggregate & write foundations
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Toolguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ToolguideIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ToolguideRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ToolguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/WritePolicyGate.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PermissiveWritePolicyGate.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ToolguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideDsoRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RefusalHandlerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRevisionTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Toolguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ToolguideIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ToolguideRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ToolguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/WritePolicyGate.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PermissiveWritePolicyGate.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RefusalHandler.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AggregateRetrofitTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/ToolguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideDsoRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RefusalHandlerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRevisionTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 — Pack aggregate + Toolguide + registration foundations

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Lay the write-path foundations the registration slices (WP05/07/08/09) stand on, and add the net-new
**toolguide** kind so `Pack` can hold it. This WP is the **single owner of `Pack.java`**. It delivers:
(1) the `Toolguide` entity + `ToolguideIdentifier` + `ToolguideDso` + `ToolguideRepository` + the
enumerated toolguide storage seam (so the `Pack` collection compiles); (2) `Pack`'s new `toolguides`
collection + invariants (FR-008 aggregate revalidation foundation, FR-015 entity); (3) the
**aggregate-commit store** (stage whole pack dir + atomic directory swap, NFR-002); (4) the
`RefusalHandler` **code→status map** + new `RefusalCode` validation(422)/conflict(409) tokens; and
(5) the pack/artifact **ETag revision digest** util (distinct from the read-path `ConsistentRead`
fingerprint).

Ground truth: [data-model.md](../data-model.md) "Pack «AggregateRoot»" + "Storage DSOs" + Toolguide
row; [spec.md](../spec.md) FR-008, FR-015, NFR-002, US3; research [D3](../research.md),[D4](../research.md);
**ADR-0026 Decisions 3–5**, **ADR-0012** (levels, for WP06), `contracts/packs/schemas/toolguide.schema.json`.

## Context & Constraints (facts that shape this WP)

- **The toolguide storage seam is enumerated, not generic.** Adding a kind requires matched additions
  across: `ToolguideDso` (new); `SerializationStrategy` (two new methods `String writeToolguide(ToolguideDso)`
  + `FileRead<ToolguideDso> readToolguide(String text, String file)`); `YamlSerializationStrategy`
  (implement both, schema-validated, in canonical key order); `PackFileSet.LoadedKind.TOOLGUIDE`
  (token + file-name mapping — mirror how directive/styleguide/paradigm are listed; the bundled JSON
  schema already exists at `contracts/packs/schemas/toolguide.schema.json`, so wire to it, do not
  author a new schema); `FilesystemPackStore` (`writeToolguide`/`readToolguide` + a `readKind` line in
  `read(...)` + a `toolguides` field on the `PackRead` record); `DomainRepositoryService`
  (`save(Toolguide)` + `load(pack, ToolguideIdentifier)` + include toolguides in the `Pack load(...)`
  aggregate assembly). Follow the directive seam exactly as the template.
- **No aggregate commit exists today.** `DomainRepositoryService.save(child)` → a single-file temp+
  `ATOMIC_MOVE` upsert via `FilesystemPackStore.writeAtomically`. ADR-0026 D3 says the registration
  path is **load → mutate → revalidate the whole `Pack` → commit atomically**, NOT the per-child
  `save()`. The commit must be whole-pack atomic (NFR-002: a refused/failed registration changes
  **zero** stored files). Build **`PackCommitStore`** for stage + atomic directory swap.
- `Pack` already guards its invariants in the constructor (`RULES.guard`). "Registration revalidation"
  (FR-008) is realised by rebuilding the `Pack` via its builder (which guards) — the registration
  **services** (WP05+) do load→mutate→rebuild; your job on `Pack` is to add the `toolguides`
  collection + its two invariants + a builder adder + accessor, so a rebuild revalidates toolguides
  too.
- **ETag revision ≠ read fingerprint** (ADR-0026 D5): the `ConsistentRead`/`Charter-Source-Fingerprint`
  is a freshness check over the read inputs; the write-side revision (`PackRevision`) is a digest of
  the pack/artifact state used as the `If-Match` / `ETag` token. Define it distinctly.
- **New RefusalCode tokens are contract-additive (ADR-0009)**: `422` validation and `409` conflict/
  policy are genuine `RefusalCode`s; `412` (precondition failed) and `428` (precondition required) are
  **NOT** `RefusalCode`s — they are plain `Problem` bodies carried by the controllers (WP05). Add the
  new enum constants + their snake_case tokens to both `RefusalCode` and the contract enum
  `contracts/charter/schemas/CharterRefusalCode.yaml` (the generated `CharterRefusal.CodeEnum` must
  accept the token, or `RefusalHandler.respond` throws on `fromValue`). `governance_policy_violation`
  already exists (used by WP06) — do not re-add it.
- You do **not** wire any Spring `@Bean` in `deployable`: `PackCommitStore` is a `@Service`/`@Component`
  in `infra.storage` (auto-scanned by `StorageConfiguration`); `PackRevision` is a plain util/`@Service`
  in `application`. Keep the onion: domain pure, storage at the edge.

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP04 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :packs:test --tests '*ToolguideEntityTest' --tests '*ToolguideDsoRoundTripTest' --tests '*PackAggregateTest' --tests '*AggregateRetrofitTest' --tests '*PackCommitStoreTest' --tests '*RefusalHandlerTest' --tests '*PackRevisionTest'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T019 — `Toolguide` entity + `ToolguideIdentifier` + `ToolguideRepository`
- Follow the `Directive`/`Tactic` house style exactly: `final class Toolguide implements CharterArtifact`,
  package-private all-args ctor + `RULES.guard(this)`, public fluent chainable builder, identity
  equality on `id`.
- Fields from `contracts/packs/schemas/toolguide.schema.json`: `ToolguideIdentifier id`,
  `PackIdentifier packId`, `String schemaVersion`, `String tool`, `String title`, `String guidePath`
  (relative `*.md`, pattern `^[^/].*\.md$`), `String summary`, `List<String> commands`,
  `List<String> appliesToLanguages`, `Optional<String> lastUpdated`, `List<String> references`.
- `ToolguideIdentifier(String value)` record: pattern `^[a-z][a-z0-9-]*$` (per schema); throw
  `IllegalArgumentException` otherwise.
- `CharterArtifact` accessors: `kind()=ArtifactKind.TOOLGUIDE`, `label()=title`,
  `description()=Optional.of(summary)`, `relationships()=List.of()` (bare-string citations mint no
  edges — data-model). Required-field invariants per schema `required: [id, schema_version, tool,
  title, guide_path, summary]`; `schemaVersion` matches `^1\.0$`; non-blank `tool`/`title`/`summary`;
  `guidePath` matches the relative-`.md` pattern.
- `ToolguideRepository` port (domain): `void save(Toolguide)` + `Optional<Toolguide> load(PackIdentifier,
  ToolguideIdentifier)` — mirror `DirectiveRepository`.

### T020 — `ToolguideDso` + enumerated storage seam
- `ToolguideDso` flat record (storage) mirroring the YAML fields (record equality = round-trip
  assertion): `id, schemaVersion, tool, title, guidePath, summary, List<String> commands,
  List<String> appliesToLanguages, Optional<String> lastUpdated, List<String> references`. Compact
  ctor: non-null required, copy lists.
- Extend `SerializationStrategy` (+ `YamlSerializationStrategy`) with `writeToolguide`/`readToolguide`
  (schema-validated via the bundled `toolguide` schema, canonical key order matching the JSON schema
  property order). Add `PackFileSet.LoadedKind.TOOLGUIDE` (file token `toolguide`, i.e.
  `<id>.toolguide.yaml`). Add `FilesystemPackStore.writeToolguide/readToolguide`, a `readKind` line and
  a `toolguides` list on `PackRead`. Add the `DomainRepositoryService` port impl (`save`/`load`) + the
  DSO↔entity mapping (`toDso`/`buildToolguide`) + include toolguides in `Pack load(...)` assembly.
- Verify `PackSchemas.bundled()` resolves the `toolguide` schema (it is already vendored); if a
  registry/list of kinds needs the new entry, add it.

### T021 — `Pack` aggregate: toolguides collection + invariants
- Add `private final List<Toolguide> toolguides;` with constructor param + `List.copyOf`, a
  `toolguides()` accessor, and a `Builder.toolguide(Toolguide)` adder + the builder field + wire it in
  `build()`.
- Add two `RULES` invariants mirroring the directive ones: "Every toolguide of a pack must belong to
  that pack" (packId match) and "A pack must not hold two toolguides with the same identifier"
  (HashSet of `ToolguideIdentifier`). This is the FR-008 aggregate-revalidation foundation: because a
  registration rebuilds the `Pack` via the builder, these invariants gate every write.
- Do not change the existing collections/invariants.

### T022 — `PackCommitStore` (stage + atomic directory swap, NFR-002)
- New `@Service` in `infra.storage`: given a pack identity and the mutated `Pack` (plus the
  registered-relationship shard text from WP02's `RelationshipShard`), commit the **whole pack
  atomically**: stage a complete copy of the pack directory to a sibling temp dir, write every child
  file + descriptor + the relationship shard into the staging dir via `SerializationStrategy`, then
  **atomically swap** the staging dir into place (atomic rename of directories; fall back to a
  move-aside-then-rename that still leaves zero partial state on failure — document the FS assumption).
- NFR-002 guarantee: on any failure before the swap, the live pack directory is **byte-for-byte
  unchanged** and the staging dir is cleaned up. Reuse `FilesystemPackStore`'s pack-location resolution
  and containment checks; reuse the `Rename` seam idea for a failure-injection test.
- Expose a method the registration services call, e.g. `PackRevision commit(PackIdentifier pack, Pack
  mutated, String relationshipShardText)` returning the new revision (T025).

### T023 — `RefusalCode` validation/conflict tokens + contract enum
- Add two enum constants with snake_case tokens: a validation code (`422`, e.g. `VALIDATION_FAILED`
  → `validation_failed`) and a conflict code (`409`, e.g. `REGISTRATION_CONFLICT` → `registration_conflict`
  — pick grounded names; a duplicate-id POST and an unresolvable-`from` are conflicts). Keep tokens
  stable (they are contractual, ADR-0009 additive). Update `notFound()` only if appropriate (these are
  not not-founds). Add the same tokens to `contracts/charter/schemas/CharterRefusalCode.yaml` so the
  generated `CharterRefusal.CodeEnum` accepts them. Update `ReservedRefusalCodesTest` if it enumerates
  the code set.

### T024 — `RefusalHandler` code→status map
- Replace the "every non-404 → 500" behaviour with a real map: not-found codes → 404 (unchanged),
  the validation code → 422, the conflict/policy codes (`registration_conflict`,
  `governance_policy_violation`) → 409, `stale_input` stays as today, `internal_error` → 500. Add
  `title()` cases for the new codes. 412/428 are NOT handled here (controllers return those as plain
  `Problem` bodies). Keep the existing logging discipline.

### T025 — `PackRevision` ETag digest util
- A util that computes a stable revision token (digest) for a pack/artifact's committed state, used as
  the `ETag` and matched by `If-Match`. It MUST be distinct from `ConsistentRead`'s source fingerprint
  (ADR-0026 D5) — base it on the committed child content (e.g. SHA-256 over the serialized DSOs +
  relationship shard), not on the read-input digest. Provide `compute(...)` and an `matches(ifMatch,
  current)` helper for the controllers.

### T027 — `WritePolicyGate` port + permissive default (foundation for WP06)
- Create `WritePolicyGate` (domain port): `void authorize(Pack pack, Operation operation, Urn target)`
  (or returns a refusal), with an `Operation` enum `REGISTER | REPLACE | RELATIONSHIP`. It is the
  single seam the registration services consult before mutating, so FR-011's mutability gate (WP06)
  can land **without editing any registration service or the commit store**.
- Provide `PermissiveWritePolicyGate` (application `@Component`, `@ConditionalOnMissingBean` or a
  non-`@Primary` default) that authorizes everything — today's behaviour (all artifacts effectively
  `open`). WP06 supplies the real ADR-0012 implementation as the `@Primary` bean. Do **not** model the
  levels here (that is WP06); just the port + the permissive default so WP05+ can call it now.

### T026 — Tests (test-first, red before green)
- `ToolguideEntityTest`: valid toolguide builds; each required-field/pattern invariant rejects (with a
  same-fixture positive control); `guidePath` relative-`.md` pattern enforced; `relationships()` empty;
  identity equality.
- `ToolguideDsoRoundTripTest`: `readToolguide(writeToolguide(dso)).equals(dso)` with present/empty/
  absent optionals distinct (NFR-003).
- `PackAggregateTest` (update): a pack with toolguides validates; a foreign-pack toolguide and a
  duplicate-id toolguide are each rejected **with a same-fixture positive control** that the valid
  case passes (FR-008). Keep existing collection assertions.
- `AggregateRetrofitTest` (update): adjust any `edges()`→`relationships()` references WP03 changed;
  keep Tactic-as-CharterArtifact behaviour assertions.
- `PackCommitStoreTest`: a successful commit replaces the pack content and returns a new revision; an
  **injected failure before the swap leaves the live pack byte-for-byte unchanged** and no staging
  residue (NFR-002, the key control); a second commit is atomic too.
- `RefusalHandlerTest`: each code maps to its status (404/422/409/500) and title; the new tokens
  resolve through `CharterRefusal.CodeEnum`.
- `PackRevisionTest`: a change to committed content changes the revision; an unchanged commit yields
  an equal revision; the revision differs from the read-path fingerprint for the same pack.

## Test strategy

Test-first. The NFR-002 atomicity test (injected pre-swap failure → zero file change) and the FR-008
duplicate-id-rejected-with-valid-control are the load-bearing scenarios; both carry same-fixture
positive controls (DIRECTIVE_041). Targeted `:packs:test` only (NO_FULL_HEAVY_SUITES_IN_MISSION).

## Definition of Done

- [ ] `Toolguide` + `ToolguideIdentifier` + `ToolguideDso` + `ToolguideRepository` exist; the
  enumerated toolguide seam compiles end-to-end and round-trips (NFR-003); `Pack` holds toolguides
  with belongs-to/no-dup invariants (FR-008/FR-015).
- [ ] `PackCommitStore` commits the whole pack atomically (stage+swap); a pre-swap failure changes
  zero files (NFR-002).
- [ ] `RefusalCode` has validation(422)/conflict(409) tokens mirrored in `CharterRefusalCode.yaml`;
  `RefusalHandler` maps every code to its correct status; 412/428 stay out of `RefusalCode`.
- [ ] `PackRevision` yields a stable ETag distinct from the read fingerprint.
- [ ] `Pack.java` touched only by this WP; red+green hashes recorded; targeted `:packs:test` green;
  domain/onion ArchUnit gates pass.

## Risks

- **Non-atomic commit**: a directory swap that is not truly atomic on the target FS can leave partial
  state — document the FS assumption and make the failure-injection test prove zero-partial-write.
- **Seam drift**: missing one of the enumerated toolguide additions makes the pack fail to load/round-
  trip; follow the directive seam as a checklist.
- **Contract enum omission**: adding a `RefusalCode` without the matching `CharterRefusalCode.yaml`
  token makes `RefusalHandler.respond` throw at runtime — add both.
- **ETag = fingerprint confusion**: reusing `ConsistentRead`'s fingerprint as the ETag conflates
  freshness with concurrency (ADR-0026 D5) — keep them separate.

## Reviewer guidance

Confirm `Pack.java` is single-owned here; the toolguide seam is complete and round-trips with
present-vs-absent controls; the commit store is whole-pack atomic (pre-swap failure = zero change);
the refusal map covers 404/422/409/500 and the new tokens are in the contract enum; and the ETag
revision is distinct from the read fingerprint.
