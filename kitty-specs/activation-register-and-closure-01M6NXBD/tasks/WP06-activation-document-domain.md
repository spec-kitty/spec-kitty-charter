---
work_package_id: WP06
title: activation document domain (document, revision, JCS, closure snapshot, rules)
dependencies:
- WP05
requirement_refs:
- FR-005
- FR-006
- FR-007
- FR-008
- FR-010
- FR-017
- C-002
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-register-and-closure-01M6NXBD
base_commit: d6b423c7b6b999d64ae6dbfcd89c0d30870c4803
created_at: '2026-11-05T20:07:48.094722+00:00'
subtasks:
- T034
- T035
- T036
- T037
- T038
- T039
- T040
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationId.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/PackRef.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationDocument.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Revision.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Jcs.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ActivationIdTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ActivationDocumentTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/JcsTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/RevisionTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/DocumentRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ClosureSnapshots.java
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/ArtifactKindTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationId.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/PackRef.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationDocument.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Revision.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/Jcs.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/DocumentRules.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ActivationIdTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ActivationDocumentTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/JcsTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/RevisionTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/DocumentRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ClosureSnapshots.java
role: implementer
tags: []
tracker_refs: []
---

# WP06: activation document domain (document, revision, JCS, closure snapshot, rules)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Build the pure activation domain:

- the `ActivationDocument` aggregate, with three-state allow lists, a never-empty deny list and a canonical form;
- `ActivationId` and `Revision` (`sha256:` over the RFC 8785 serialisation);
- an in-house `Jcs` canonicaliser (no new dependency);
- the `ClosureSnapshot` value the application receives from `AvailablePacks`;
- `DocumentRules`, which checks a document against a snapshot. It answers `422 activation_invalid` violations for
  unknown ids, kinds, packs and presets, duplicate packs, pins that differ from the held version, and empty deny
  lists. It answers a `409` refusal for a deny entry on a required or locked artifact.

The kernel's `ArtifactKind.InForceWhenAbsent` collapses to a single meaning, `NONE` (absent means none, R8). The domain
holds no port and reaches no application ring (squad A1).

Implementation command:
`spec-kitty agent action implement WP06 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A. **Concern:** IC-05. **Research:** R2 (pins), R3 (JCS), R5 (floor and locks), R8 (absent means none);
  squad A1, A8 (kinds from `ArtifactKind.activatable()`, no new kind list), R4 (empty deny list is 422).
- **Data model:** `data-model.md`, the "Activation slice" section is the authority for fields and invariants.
- **Requirements:** FR-005 (canonical form and revision), FR-006 (validation), FR-007 (floor refusals), FR-008
  (absent means none, distinct on the wire), FR-010 (body ids resolve in the closure), FR-017 (a write that still
  carries a stale id is 422).
- **Decisions:** `DM-01M6NXQJ61D2GSFJFK02XW6F9N` (absent means none; absent `packs` still means every configured
  pack).
- **Collisions:** none on these new files. `ArtifactKind` may be touched by other open PRs; rebase.
- **Grounding:**
  - `ArtifactKind` declares `InForceWhenAbsent { ALL, REQUIRED }`, with `SKILL` as `REQUIRED` and every other kind
    `ALL`. Its only users are the enum itself and `ArtifactKindTest`.
  - Research R3 says "walks Jackson's `JsonNode`", but the activations **domain** must be pure (WP05's per-slice
    `domainIsPure`: `java`, JSpecify, kernel, own slice). So `Jcs` serialises the domain's **own** value tree
    (`Map`/`List`/`String`/`Integer`/`Boolean`), not a `JsonNode`. The adapter maps DTO to domain; the domain
    computes the revision from its canonical form. Record this deviation in `Jcs`'s Javadoc.
  - The contract's grammars: `ActivationId` `^[A-Za-z0-9][A-Za-z0-9-]{0,63}$`; `ArtifactId`
    `^[A-Za-z0-9][A-Za-z0-9_.-]*$`; `Slug` `^[a-z0-9][a-z0-9_-]*$` (pack ids, mission types).
  - `ActivationConfig` member order (the canonical "members in the order of this schema", though JCS re-sorts keys
    anyway): `schemaVersion`, `packs`, `activatedKinds`, `missionTypes`, `selected`, `deactivated`, `languages`.
  - The kernel `CharterRefusal` carries `violations` and `requiredBy` since WP01.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP05 (the module and its architecture rules).
- Rebase onto the lane base before review.

## Subtask Guidance

### Subtask T034: Absent means none in the kernel

**Steps (red first):**
1. `ArtifactKindTest`: every kind's `inForceWhenAbsent()` is `NONE`; the enum has exactly one constant. (Or remove
   the accessor and the enum, if nothing else needs them. Grep first; the plan says "→ NONE with all users updated",
   so keep the enum with one value and say why in its Javadoc: ADR-0053 d10, the service rules it now; core's per-kind
   default diverges, divergence entry in WP10.)
2. Change every constant's last argument to `InForceWhenAbsent.NONE`; delete `ALL` and `REQUIRED`.
3. `git grep -n "InForceWhenAbsent"` must show only the enum and its test.

**Validation:** `./gradlew :kernel:check`.

### Subtask T035: `ActivationId`, `PackRef`, `Revision`

**Steps (red first):**
1. `ActivationIdTest`: the grammar accepts a ULID and a UUID, rejects an empty id, 65 characters, a leading `-`
   and `a/b`. `ActivationId.mint(Supplier<String> ulid)` returns an id matching the grammar. The domain does not
   generate ULIDs itself (no library); the application supplies the generator in WP09.
2. `RevisionTest`: `Revision.of(byte[] canonicalUtf8)` gives `sha256:` + 64 lowercase hex characters; `token()`;
   value equality. `Revision.parse("sha256:…")` round-trips. A known vector: the SHA-256 of `{"schemaVersion":1}`
   (compute it once with `printf '{"schemaVersion":1}' | sha256sum` and pin it).
3. `PackRef(String packId, Optional<String> version)` with the `Slug` grammar on `packId`, a non-blank version of at
   most 64 characters, and `equals` on both.

### Subtask T036: `Jcs`, the RFC 8785 subset

**Purpose:** a deterministic byte form across JVM and native image (FR-005).

**Steps (red first, `JcsTest`):**
1. Vectors (J9):
   - **key order** by UTF-16 code units, which is `String.compareTo` order and is correct for RFC 8785 §3.2.3. Pin a
     pair whose order differs between code points and UTF-16 code units: a key holding U+1F600 (a surrogate pair,
     `"😀"`) sorts **before** a key holding U+FFFD (`"�"`), because 0xD83D < 0xFFFD;
   - **strings**, using the **RFC 8785 §3.2.2.2 examples** (not Appendix B, which is about numbers): `"`, `\`, and the
     control characters U+0000–U+001F as `\b \t \n \f \r` or `\u00xx` in lowercase hex; `/` **not** escaped;
     non-ASCII emitted as raw UTF-8, not escaped. Pin the RFC's example string and its expected bytes;
   - **lone surrogates are refused** (`IllegalArgumentException`): a high surrogate with no low one, or a low one
     alone. Valid JSON text from a client cannot carry them as UTF-8, but a `\uD800` escape can;
   - **integers** in ECMAScript form (`1`, `0`, `-5`). A magnitude above 2^53 − 1 (`9007199254740991`) is refused,
     since it has no exact I-JSON form; no other number type is accepted;
   - `true` and `false`; `null` is **refused** (the document never holds `null`);
   - nested arrays keep their order.
2. Implement `public final class Jcs` with `static byte[] canonical(Object value)`, recursing over
   `Map<String, ?>`, `List<?>`, `String`, `Integer`/`Long`, `Boolean`. Use a `StringBuilder` and one helper per value
   kind so each method stays short.

### Subtask T037: `ActivationDocument` and its canonical form

**Purpose:** the aggregate, with FR-005 and FR-008 invariants enforced at construction.

**Fields** (data model): `int schemaVersion`; `Optional<List<PackRef>> packs` (absent, or non-empty, order kept);
`Optional<SortedSet<ArtifactKind>> activatedKinds`; `Optional<SortedSet<String>> missionTypes`;
`Optional<SortedSet<String>> languages`; `Map<ArtifactKind, Optional<SortedSet<String>>> selected` (a key present
with an empty set is `[]`, and a missing key is absent); `Map<ArtifactKind, SortedSet<String>> deactivated` (never
empty sets). Use an `EnumMap`, and kinds only from `ArtifactKind.activatable()` (squad A8). `mission_type` is not a
`selected` key: it is `missionTypes`.

**Steps (red first, `ActivationDocumentTest`):**
1. Unsorted, duplicated `selected.directive` normalises sorted and once (US1 AS1). `packs` keeps written order.
2. `[]` stays `[]` and absent stays absent, for `activatedKinds`, `missionTypes`, `languages` and each
   `selected.<kind>`; both give `inForce(kind)` false, and they stay **distinct** in `toCanonicalTree()` (FR-008).
3. `selected: {}` (an empty object) is left out of the canonical form, while `selected.directive: []` is kept (the
   contract's canonical-form text).
4. A deny list constructed empty is rejected by the builder with a violation (`/deactivated/<kind>`,
   `empty_deny_list`), not by an exception. The document builder collects violations; see T039.
5. `withoutDeny(kind, id)` drops the key when the list empties (reactivate, WP13); `withDeny` and `withSelected`
   return new documents (immutable).
6. `toCanonicalTree()` returns the `Map`/`List` tree `Jcs` serialises, and `revision()` is
   `Revision.of(Jcs.canonical(toCanonicalTree()))`. The same content built in a different order gives an equal
   revision.
7. `Absent packs` (FR-008): `packs()` is empty, meaning every configured pack. The domain does not resolve it; the
   application passes `Optional.empty()` to `AvailablePacks`.

Keep the builder small: one `ActivationDocument.Builder` with one method per member, and a `build()` that returns
`Built(document, violations)`, so the HTTP mapper (WP09) and the rules share one place where shape violations are
made.

### Subtask T038: `ClosureSnapshot`, the value from the port

**Purpose:** everything the rules (and later the cascade) need, as a value (squad A1).

**Shape** (`activations.domain.ClosureSnapshot`, a record with defensive copies): `List<PackEntry> packs` (configured
id, descriptor `pack_id`, level token, name, version, `List<Dependency>`; the closure read maps these to
`OfferingPack`); `Map<ArtifactKind, Set<String>> held`; `Set<String> missionTypes`; `Map<String, List<String>>
requiredBy` (URN text to requiring descriptor `pack_id`s); `Map<String, String> lockedBy` (URN text to the locking
pack); `Map<String, PresetView> presets` (id to members per kind, `activatedKinds`, mission types, must-have flags, and
the holding pack); `String fingerprint`.

No `packs` type crosses into this record. The adapter (WP09) copies from `ClosureView`. Add `ClosureSnapshots`, a test
fixture builder, to the test tree for WP06 to WP14's domain tests.

### Subtask T039: `DocumentRules`

**Purpose:** FR-006, FR-007, FR-010 (body half) and FR-017 (write half), as pure functions.

**Steps (red first, `DocumentRulesTest`, using `ClosureSnapshots`):**
1. `selected.directive: [NOPE]`, which the closure does not hold: a violation `/selected/directive/0`,
   `unknown_artifact`, naming the id (US1 AS5).
2. `deactivated.tactic: [X]` where X is unknown: `/deactivated/tactic/0`, `unknown_artifact`.
3. `missionTypes: [ghost]`: `/missionTypes/0`, `unknown_mission_type`.
4. `packs: [{packId: zeta}]` not configured: `/packs/0/packId`, `unknown_pack`. A duplicate pack id:
   `/packs/1/packId`, `duplicate_pack`. A pin `version` that differs from the held version: `/packs/0/version`,
   `version_not_held` (R2 open item).
5. An empty deny list: `/deactivated/directive`, `empty_deny_list` (US1 AS7).
6. `activatedKinds` naming a kind outside `ArtifactKind.activatable()`: `/activatedKinds/0`, `unknown_kind`.
   (Schema validation usually catches it first; keep the rule for the domain path.)
7. Every violation is collected (not first-only), in JSON Pointer order; the result is
   `CharterRefusal.withViolations(ACTIVATION_INVALID, "…", violations)`.
8. **The floor:** a deny entry on a URN in `requiredBy` gives `409 ARTIFACT_REQUIRED` with `requiredBy` naming each
   requiring pack (US1 AS6). On a URN in `lockedBy`, `409 ARTIFACT_LOCKED` with the locking pack. When both apply,
   **locked** wins (locked implies required, ADR-0012 D7). The 409 is checked only after there are no 422 violations.
9. `checkStored(document, snapshot)` (FR-017 read half) is **not** built here: `GET` serves the stored document
   unchanged (Edge Cases).

**Implementation:** `public final class DocumentRules` with `static Optional<CharterRefusal> check(ActivationDocument,
ClosureSnapshot)`, split into `packViolations`, `idViolations`, `denyViolations` and `floorRefusal`; each is a short
loop. Use the kernel `RefusalViolation` and `RequiringPack`.

### Subtask T040: Purity and gates

**Steps:**
1. `./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'`: the activations domain names only `java`,
   JSpecify, kernel and itself.
2. `./gradlew :activations:check :kernel:check`.
3. Grep the domain for `org.springframework`, `tools.jackson` and `ai.speckitty.charter.packs`: none.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand.

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. `Jcs.escape` and `DocumentRules.check` are
the hotspots; use one helper per case. No new suppression, and no disabled test.

## Gate commands

```bash
./gradlew :kernel:check
./gradlew :activations:check
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'
```

## Definition of Done

- [ ] `InForceWhenAbsent` has the single value `NONE`, and nothing else names a removed constant.
- [ ] `Jcs` passes the pinned vectors: the RFC 8785 §3.2.2.2 strings, the UTF-16 ordering case, and the refusals of
      lone surrogates and integers above 2^53 − 1.
- [ ] `ActivationDocument` enforces the canonical form and keeps absent and `[]` distinct; equal content gives equal
      revisions.
- [ ] `DocumentRules` collects every 422 violation with its pointer and code, and raises 409 required or locked with
      the right `requiredBy`.
- [ ] The domain is pure (ArchUnit green, and the grep is clean).
- [ ] No new suppression, and no disabled test.

## Risks

- **JCS subtleties.** Key sorting is by UTF-16 code units, not by code points. The supplementary-plane vector is the
  guard.
- **Violation codes are a new vocabulary.** Keep them to the set above and list them in the `DocumentRules` Javadoc.
  WP08 cites them in the contract's `ActivationInvalid` example only if the example names one.
- **Pins:** refusing a differing pin is an open review item (R2). Keep it in one rule so a later ruling changes one
  place.

## Reviewer guidance (reviewer-renata)

- **Run `JcsTest` and read the vectors.** The UTF-16 versus code-point case (U+1F600 before U+FFFD) must be present. A
  sort by `String.compareTo` is correct (UTF-16), and a sort by code points is not. Check the §3.2.2.2 string vector,
  the lone-surrogate refusals and the 2^53 bound.
- **Absent versus `[]`:** check `ActivationDocumentTest` asserts both `inForce == false` **and** different canonical
  trees.
- **Floor precedence:** locked wins over required; check the test that has both.
- **Non-fakeable evidence:** the pinned `sha256` of `{"schemaVersion":1}` in `RevisionTest`, recomputed by the
  reviewer with `sha256sum`.
