---
work_package_id: WP06
title: Validate-mode domain rules
dependencies:
- WP05
requirement_refs:
- FR-009
- NFR-003
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T025
- T026
- T027
- T028
- T029
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-domain/
create_intent:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Pep440.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/AssetMimeTable.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/Pep440Test.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyRulesTest.java
- service/charter-domain/pit-targets.init.gradle
- scripts/packs/pep440_corpus.py
- scripts/packs/mime_table.py
execution_mode: code_change
model: sonnet
owned_files:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Pep440.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/AssetMimeTable.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/Pep440Test.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyRulesTest.java
- service/charter-domain/src/test/resources/pep440/**
- service/charter-domain/pit-targets.init.gradle
- scripts/packs/pep440_corpus.py
- scripts/packs/mime_table.py
role: implementer
tags: []
tracker_refs: []
---

# WP06 — Validate-mode domain rules

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt.

## Objective

This WP adds the single-pack rules a schema cannot express, as pure `PackPolicy` rules over
`PackContents` facts (FR-009; rulings R3, R5, R13, R16; plan calls P3, P4, P8).

| Code | Severity | Rule | Load path? |
|---|---|---|---|
| `requires_core_invalid` | blocking | `requires_core` present and not a PEP 440 specifier set | yes (`pack_unreadable` on the wire) |
| `pack_unknown_key` | warning | one per `PackDescriptor.undeclaredKeys` entry, retired fields included | yes (`Diagnostic`) |
| `duplicate_step_id` | blocking | a mission step contract repeats a step id | validate only |
| `duplicate_artifact_id` | blocking | two artifacts of one kind share an id | validate only |
| `asset_path_escape` | blocking | an asset path is absolute, contains `..`, or is not contained in `assets/` (symlink-aware fact from the adapter) | validate only |
| `asset_mime_invalid` | blocking | the MIME type is not `type/subtype`, or disagrees with the fixed extension table | validate only |

`org_fragment_missing` is **not** in this WP. Turning it on changes the load outcome for the org
packs in `packs.feature` scenarios 2 and 3, which have no fragment. It therefore lands in WP07,
with its scenario (D6) and the fixture updates, so every commit stays green.

**Exact texts.** They are pinned here; WP07's scenarios and
[contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md) use them verbatim. `<n>` is
the pack name, `<f>` the pack-relative file.

| Code | Detail |
|---|---|
| `requires_core_invalid` | `pack.yaml of <n>: requires_core '<v>' is not a PEP 440 version specifier` |
| `pack_unknown_key` | `pack.yaml of <n> declares unknown key '<k>'; it is ignored` |
| `duplicate_step_id` | `<f>: step id '<id>' is declared more than once` |
| `duplicate_artifact_id` | `<f>: <kind> id '<id>' is already declared in <first-file>` |
| `asset_path_escape` | `<f>: asset path '<p>' escapes the pack's assets/ root` |
| `asset_mime_invalid` | `<f>: asset mime '<m>' is not a well-formed 'type/subtype' value`, or `<f>: asset mime '<m>' does not match the '.<ext>' extension (<expected>)` |

**`PACK_UNKNOWN_KEY` code.** Add it to `PackFindingCode` (WARNING). Its `diagnostic()` projection
keeps throwing `IllegalStateException` until WP07 adds `Diagnostic.Code.PACK_UNKNOWN_KEY` together
with the contract value; `WireTokenParityTest` forbids the domain constant before then. That is
safe in the meantime: the adapter fills `undeclaredKeys` only from WP07 on, so the warning never
reaches `load` before the projection exists. Test the code's severity, and the rule through
`judge`.

**FR-009, "URN prefix equals kind where a pattern cannot".** Research R4 found the schema
expresses it completely (WP02 C4, 15 generated branches). Nothing is left for the domain; record
that in the evidence.

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree (same lane as WP05).
- Implement with:
  `spec-kitty agent action implement WP06 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T025 — `Pep440.isValidSpecifierSet`

**Steps**:

1. **The corpus generator.** `scripts/packs/pep440_corpus.py` runs with
   `~/.venv-sk/bin/python -I`.
   - It records `packaging.__version__`, the version core's venv pins; print it into the corpus
     header.
   - It writes `service/charter-domain/src/test/resources/pep440/specifiers.tsv` with the columns
     `input<TAB>valid`. Validity is decided by `packaging.specifiers.SpecifierSet(input)` raising
     or not.
   - The inputs are generated cases plus this adversarial list:
     - `""`, which is valid: an empty SpecifierSet;
     - `">=1.0"`, `">=1.0,<2"`, `"~=1.4.5"`, `"~=1"` (invalid), `"==1.*"`, `">=1.*"` (invalid),
       `"!=1.0.*"`;
     - `"===foobar"`;
     - `"==1.0+local"` and `">=1.0+local"` (invalid);
     - `"1!2.0"`, `">=1!2.0"`;
     - pre, post and dev forms: `">=1.0a1"`, `">=1.0.post1"`, `">=1.0.dev1"`, `">=1.0rc1.post2.dev3"`;
     - upper case `">=1.0RC1"`, whitespace `" >= 1.0 , < 2 "`, a trailing comma `">=1.0,"`;
     - a non-ASCII digit `">=١.0"`;
     - `"banana"`;
     - core's real value `">=4.0.0rc7,<5"` (open-packs `packs/built-in.source.yaml:24` at the pin).
   - Commit the generator and the corpus. The header names the `packaging` version and the
     command.
2. **Red.** `Pep440Test` reads the corpus and asserts `isValidSpecifierSet(input) == valid` for
   every row.
3. **Implement** `final class Pep440` in the domain, using only `java.util.regex`. It follows
   `packaging`'s `Specifier` regex, which the generator can print, and the specifier-set rules:
   - comma-separated clauses, each trimmed;
   - an empty set is valid;
   - `.*` only with `==` and `!=`;
   - a local version only with `==`, `!=` and `===`;
   - `~=` needs at least two release segments;
   - `===` takes an arbitrary string with no whitespace.

   Javadoc cites PEP 440 and the `packaging` version.

### T026 — Descriptor rules

**Steps**: red tests first in `PackPolicyRulesTest`, then add to `PackPolicy.judge`:

- **`requires_core_invalid`**: from `PackDescriptor.requiresCore`. An absent value is fine.
- **`pack_unknown_key`**: one warning per undeclared key, sorted, with the text above. A retired
  field (`accompanies_doctrine_pack`) is just an undeclared key (R13). Test it explicitly.
Both are load-path findings attached to `pack.yaml` (precedence group 1). Assert through `judge`
that `requires_core_invalid` is blocking and that `pack_unknown_key` is a warning. Assert through
`load(contents, graph)` that the `requires_core_invalid` refusal is `pack_unreadable` with the
exact text. Test the `pack_unknown_key` projection in WP07.

### T027 — Duplicate id rules

Red tests, then:
- **`duplicate_step_id`**: from `StepContractFacts`. One finding per duplicated id, on the
  second and later occurrences, with the file and the place of the repeat, in the form
  `/steps/<i>/id`. Match core's "duplicate step IDs" semantics (`step_contracts.py:152-160`).
- **`duplicate_artifact_id`**: from `ArtifactFacts`, grouped by kind. The first declaration (in
  path order) is the owner; later files get the finding naming the first file. Match core's
  `duplicate_id` (`pack_validator.py:390-403`).

### T028 — Asset rules and the fixed MIME table

**Steps**:

1. **The table generator.** `scripts/packs/mime_table.py` prints Python's built-in
   `mimetypes.types_map` **without** system files: `mimetypes.MimeTypes(filenames=())` with
   strict types. Put it into `AssetMimeTable.java` as a sorted, immutable `Map<String,String>`
   (extension → type). The header comment carries the Python version and the command. This is
   plan call P8: a recorded possible divergence from core, whose `guess_type` reads platform
   files.
2. **Red tests**, then the rules:
   - **`asset_path_escape`**: `AssetFacts.containedInAssetsRoot == false`, or the declared path is
     absolute, or has a `..` segment. The adapter computes the symlink-aware flag in WP08; here,
     test with synthetic facts.
   - **`asset_mime_invalid`**: the MIME type is not `type/subtype` (non-empty on both sides of one
     `/`). Or the table has the path's extension and its type differs from the declared one.
     Extensions are lower-cased; a path with no known extension passes, as core's does.

### T029 — PIT on the new domain classes

**Steps**:

1. Commit `service/charter-domain/pit-targets.init.gradle`. It reads `-Dpit.classes` and
   `-Dpit.tests` into the pitest extension's `targetClasses` and `targetTests`. A working reference is
   the implementer's local PIT init script (not committed);
   adapt it. Add a short comment on its use.
2. Run:

   ```
   ./gradlew :charter-domain:pitest --no-configuration-cache --max-workers=2 \
     -I service/charter-domain/pit-targets.init.gradle \
     -Dpit.classes=ai.speckitty.charter.domain.PackPolicy,ai.speckitty.charter.domain.Pep440,ai.speckitty.charter.domain.PackFinding,ai.speckitty.charter.domain.PackFindingCode \
     -Dpit.tests='ai.speckitty.charter.domain.*'
   ```

   Use the DEFAULTS mutators.
3. Reach a mutation score of **at least 80%** by adding meaningful assertions, never by trivial
   tests. List every surviving mutant with an equivalence or acceptability rationale in the WP
   evidence (NFR-003).

## Definition of Done

- [ ] Each of the six rules has domain tests that were red first (assertion failures on stubs).
      The exact texts above are pinned in `PackPolicyRulesTest`.
- [ ] The PEP 440 corpus and its generator are committed with the `packaging` version, and the
      adversarial list is included.
- [ ] `AssetMimeTable` and its generator are committed with provenance.
- [ ] PIT is at 80% or more, with the survivors explained, and the init script is committed.
- [ ] The `charter-domain` module is still plain Java, and `verifyAll` is green.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Reviewer guidance (opus, reviewer-renata)

- Run the PEP 440 corpus against the implementation, and add two adversarial strings of your own.
- Check that `pack_unknown_key` is a warning, and that no rule here changes a load outcome on
  today's fixtures.
- Read the surviving mutants. Are their rationales honest?
