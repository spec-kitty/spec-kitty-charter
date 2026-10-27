# Implementation Plan: Glossary pack and skill round trip

**Branch**: `feat/192-glossary-pack-skill-round-trip` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Decision record**: a new ADR-0040, *a kind binding can carry companion files* (0039 is earmarked
for #227). ADR-0037 is not amended a third time.
**Base**: stacked on #191's branch (PR #245), which brings the schema-name seam
(`KindBinding.schemaName()`, `KindBindings.bySchema`) and the registry-driven gate. Rebased onto
`main` when #191 (or #190, which carries the same seam) merges.

## Summary

Bind two kinds in the ADR-0037 registry. Each gets a domain entity in `packs.domain`, and a DSO,
binding and YAML format in `packs.infra.storage`:
- `glossary_pack` (schema and file token `glossary-pack`);
- `skill` (schema and file token `skill`).

Add their closed schemas to `contracts/packs/schemas/` and map them in `file-kinds.yaml`. First, add
the one new seam this mission needs: a binding may declare **companion files**. These are text files
named by the artifact, relative to its file, that are read, written and digested with it. The skill
uses this for its `body_path` prompt. The gate change is the removal of the `other:glossary_packs`
expected-red entry. The kernel, the HTTP contract and the controllers are untouched.

## Technical Context

**Language/Version**: Java 25 (CI); local builds may use `-Pcharter.javaVersion=21`
**Primary Dependencies**: Gradle; snakeyaml-engine (through `ParsedYaml`, `YamlDumpEmitter`); networknt JSON Schema (through `PackSchemas`); JUnit 5, AssertJ, ArchUnit. No dependency is added, upgraded or removed (DIRECTIVE_051: nothing to check).
**Storage**: pack files on disk (`*.glossary-pack.yaml`, `*.skill.yaml` and the skill's `body_path` Markdown), validated by `contracts/packs/schemas/glossary-pack.schema.json` and `skill.schema.json`
**Testing**: unit tests per class; a corpus test over every pinned file of both kinds (4 glossary packs, 5 skill pairs); the whole-pack gate (`:packs:wholePackRoundTrip`) and the seam test (`:packs:openPacksRoundTrip`) against open-packs at `openPacksRef`
**Target Platform**: the charter service (Spring Boot), Linux
**Project Type**: single (Gradle multi-module service; work stays in `service/packs` and `contracts/packs`)
**Performance Goals**: none new; two more kinds per pack load (9 small files and 5 bodies in the pinned packs). The digest reads each skill file once more to find its body.
**Constraints**: no HTTP contract change; shared files get append-only edits except the companion seam (one isolated commit); unknown fields refused; no workaround for pack inconsistencies
**Scale/Scope**: about 16 new source files, 2 new schemas, 8 edited shared files, about 8 test classes

## Charter Check

- **ADR-0006 slices.** The entities and their value types live in `packs.domain`. They depend on the
  kernel and the JDK only. ADR-0029 is the docsite glossary, so no glossary slice owns glossary
  packs. ✅
- **ADR-0015 / ADR-0017.** Pack schemas are canonical here; core consumes them.
  - `skill.schema.json` is imported from core's current `skill.schema.yaml`, unchanged in meaning.
  - `glossary-pack.schema.json` is authored here from core's `GlossaryPack`/`GlossaryTerm`. Core
    exports none, so this is the first canonical one; core is told on #192.
  - Both are closed. ✅
- **ADR-0035.** Both entities implement `CharterArtifact`. ✅
- **ADR-0036.**
  - Unknown fields are refused by the closed schemas in `PackSchemas`.
  - The DSOs keep what was written: absent stays absent, and no default is filled (core's
    `model_invocable` forcing is not applied).
  - Per-kind rows: "Sets: none" for both kinds; `terms` is a sequence. A skill body compares as
    text. ADR-0040 records these rows.
  - ✅
- **ADR-0037.** Each kind joins by one line in `KindBindings` and one in `YamlFormats`. Decision 4
  says these missions widen the registry for non-YAML content; ADR-0040 is that widening, text only
  (C-004). ✅ (needs the operator's acceptance with the ADR)
- **Charter "how work lands".** This is a domain and architecture change.
  - The pre-spec squad ran ([research/grounding-squad.md](research/grounding-squad.md)).
  - A post-plan squad and a pre-PR squad follow.
  - Calls made without review are logged on #192. ✅
- **Charter gates.** `verifyAll` on Java 25, the docs checks and the style check. ✅ planned.

## Project Structure

### Documentation (this mission)

```
kitty-specs/glossary-pack-skill-round-trip-01M5YVEC/
├── spec.md
├── plan.md
├── research.md
├── research/grounding-squad.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── tasks/
```

### Source Code (repository root)

```
contracts/packs/
├── schemas/glossary-pack.schema.json       (new, authored from core's model, closed)
├── schemas/skill.schema.json               (new, imported from core's skill.schema.yaml)
├── file-kinds.yaml                         (edit: two files rules; two exclusions removed)
└── README.md                               (edit: schema count)
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/
│   ├── GlossaryPack.java                   (new: entity + builder)
│   ├── GlossaryPackIdentifier.java         (new)
│   ├── GlossaryTerm.java                   (new: value)
│   ├── Skill.java                          (new: entity + builder; holds the body text)
│   ├── SkillIdentifier.java                (new)
│   ├── SkillForm.java                      (new: prompt | wrapper)
│   ├── SkillParameter.java                 (new: value)
│   ├── SkillInvocation.java                (new: value)
│   └── SkillExpansion.java                 (new: value)
└── infra/storage/
    ├── KindBinding.java                    (edit: companion hooks, defaults empty)
    ├── FilesystemPackStore.java            (edit: read/upsert companions, contained)
    ├── WholePackStore.java                 (edit: write companions; companions are modelled)
    ├── PackFileSet.java                    (edit: digest companions after their artifact file)
    ├── KindBindings.java                   (edit: append two bindings)
    ├── YamlFormats.java                    (edit: append two formats)
    ├── GlossaryPackBinding.java / GlossaryPackDso.java / GlossaryPackYaml.java   (new)
    └── SkillBinding.java / SkillDso.java / SkillYaml.java                        (new)
service/packs/src/test/...                  (new tests; KindBindingsTest and DomainRoundTripTest lists)
service/packs/src/test/resources/whole-pack/expected-red.yaml   (edit: remove other:glossary_packs)
docs/adr/0040-…md, docs/adr/README.md       (new ADR + index row)
docs/changelog/2026-10-27-glossary-pack-and-skill-kinds.md (new) and docs/changelog/README.md (append)
```

## Design

### Companion files (IC-01)

Two default methods on `KindBinding`, so the existing kinds are unaffected. One accessor names
both the paths and, once filled, the contents, so paths and contents cannot drift (post-plan P6):

```java
/** A file read and written with an artifact: its path relative to the artifact's file, and its content once read. */
record Companion(String path, Optional<CompanionContent> content) {}
/** sealed CompanionContent permits Text; Bytes is #193's to add (P7). */
default List<Companion> companions(D dso) { return List.of(); }
/** The shape with each companion's content filled in, keyed by the paths companions(dso) named. */
default D withCompanions(D dso, Map<String, CompanionContent> contents) { return dso; }
```

- **Path rule.** A companion path is relative and normalizes to a file inside the pack directory.
  It does not end in `.yaml`, so it can never be `pack.yaml`, a graph shard or an artifact file
  (post-plan P1). Two artifacts may not name the same companion, case ignored. The second is a
  read violation (P5).
- **Read** (`FilesystemPackStore.readKind`, `readOne`). Once a file reads, each companion is
  resolved against the file's directory.
  - It must pass the path rule.
  - It must be a regular file, not followed through a link, whose real path is in the pack (P9).
  - It must be UTF-8 text.
  - A companion that breaks any of these gives a `PackElementViolation`. The file is the
    artifact's, the kind is `companion`, and the reason names the path. The artifact is then **not**
    added, so the whole-pack write refuses the pack (spec US2 scenario 2).
  - The text is passed to `withCompanions` byte for byte. Nothing is trimmed and line endings are
    not converted.
  - `body_path` is kept exactly as written; only its normalized form is used for paths.
- **Upsert** (`FilesystemPackStore.write`; the commit path through `PackCommitStore`, which stages a
  copy and swaps the directory).
  - Each companion with content is written through the contained `writeAtomically` before the
    artifact file.
  - A companion without content is refused, as is one whose path breaks the rule.
  - A **new** artifact refuses a companion target that already exists (as `newFile` does).
  - A **replaced** artifact deletes the companions its old file named and the new one does not, so a
    changed `body_path` leaves no orphan. The commit swap makes this atomic (P3).
  - Companion-bearing kinds are written only through the commit path. No direct save port exists for
    skills, and the ADR states the rule (P4).
- **Whole-pack write** (`WholePackStore.write`). After the artifact, each companion is `put` at
  `parent(layout path)/path`. `put` refuses a path that leaves the pack or one written twice.
- **Whole-pack load.** Companion paths join the `modelled` set, so a body is never listed as
  unmodelled (US2 scenario 4).
- **Digest** (`PackFileSet.digested`). After each file of a kind that declares companions, its
  companions are added.
  - A companion is added when it passes the path rule, is a regular file in the pack, is UTF-8 text
    and is not already listed (post-plan R4).
  - The paths come from `binding.companions(dso)` on the schema-valid read of the file. That is the
    same read path the store uses, so there is one source of truth. It is static, as
    `PackFileKind` and `locate` already are, so the three digest callers keep their constructors.
    ADR-0040 records the trade-off (P2, changed).
  - A file that does not read contributes only itself; the load reports it, and fixing it changes
    the file, so the fingerprint moves anyway.
  - Bound kinds keep their order and the new kinds are appended. A pack with no skill digests the
    same files as before (NFR-002).
  - A change to a body changes the fingerprint (US2 scenario 3).
- **What it is not.** Companions carry text only. #193 adds `Bytes` to `CompanionContent`; the
  revision digests (`PackCommitStore`, `CommitStorePackWriteStore`) read text and must move to
  bytes then. ADR-0040 says so. Toolguide `guide_path` can reuse the hooks later (C-004).
- **Javadoc.** `KindBinding`'s "a binding knows no format and no path" is amended: a binding names
  companion paths, relative to its artifact's file, and nothing else (P8).

### Glossary pack (IC-02)

- **Schema `glossary-pack`.** The top level is closed:
  - required: `id`, `provenance`, `terms` (at least one item);
  - optional: `description` (a string, or null as core allows).
- **Term (`$defs/GlossaryTerm`).** It is closed:
  - required: `surface`, `definition` (strings), `confidence` (number) and `status` (string; core
    has no enum);
  - optional: `see_also`, `synonyms_to_avoid`, `aliases`, `banned_synonyms` (arrays of strings, or
    null) and `introduced_in_mission` (a string, or null).
- **Entity.** `GlossaryPack` holds the identifier, pack, provenance, `Optional` description and
  `List<GlossaryTerm>` in authored order.
  - Invariants: at least one term, and unique surfaces (the message names the duplicate).
  - `label()` is the id and `schemaVersion()` is empty (the file has none). `relationships()` is
    `List.of()` (FR-010).
- **`GlossaryTerm`.** It is a record:
  - `surface`, `definition`, `double confidence`, `status`;
  - `List<String>` for each list. Absent, `null` and empty mean the same (ADR-0036 decision 2), and
    the writer omits an empty list, as the precedent DSOs do (post-plan P10);
  - `Optional<String>` for `introduced_in_mission`.
- **Storage.**
  - `GlossaryPackDso` mirrors the file.
  - `GlossaryPackYaml` reads `confidence` as a `Number`, which the schema guarantees, and writes it
    through `present`, so it stays a number (`1.0` included).
  - The key order is `id`, `provenance`, `description`, `terms`. A term writes `surface`,
    `definition`, `confidence`, `status`, then the optionals.
- **Binding.** `GlossaryPackBinding` sets `schemaName()` and `fileToken()` to `glossary-pack`, and
  `idOf` is the `id`.

### Skill (IC-03)

- **Schema `skill`.** It is core's `skill.schema.yaml` as JSON:
  - closed at every level;
  - prompt form requires `body_path`; wrapper form requires `expands_to`; the two exclude each other;
  - `expands_to.target` pattern `^(builtin:.+|cli:spec-kitty( .*)?)$`;
  - the `$id` follows this repository's convention.
- **Entity.** `Skill` holds:
  - the identifier, pack, schema version, title, description and `triggers` (`List`);
  - `form` (`SkillForm`, an enum, as the precedent types closed tokens);
  - `Optional` `bodyPath` and `Optional` `body` (the text);
  - `Optional<SkillExpansion>`, `parameters` (`List<SkillParameter>`) and
    `Optional<SkillInvocation>`;
  - `tools` and `maintainers` (`List`), and `version`, `overrides` and `enhances` (`Optional` text).
  - Lists follow ADR-0036 decision 2: empty means absent (P10). Booleans stay `Optional<Boolean>`,
    since `false` and absent differ.
- **Skill invariants.**
  - A prompt skill has a body path and a body, and no expansion.
  - A wrapper skill has an expansion, and no body path and no body.
  - Core's other checks are validation, not fidelity, and are not invariants here (spec A2).
- **Values.**
  - `SkillParameter(name, Optional<Boolean> required, Optional description, Optional default)`.
  - `SkillInvocation(Optional<Boolean> userInvocable, Optional<Boolean> modelInvocable,
    List<String> sideEffects)`. It is kept as written, never forced.
  - `SkillExpansion(target, Optional args)`.
- **Storage.**
  - `SkillDso` mirrors the file plus `Optional<String> body`.
  - `SkillYaml` reads booleans as `Boolean` and writes them through `present`.
  - The key order is `schema_version`, `id`, `title`, `description`, `triggers`, `form`, `body_path`,
    `expands_to`, `parameters`, `invocation`, `tools`, `version`, `maintainers`, `overrides`,
    `enhances`.
- **Binding.** `SkillBinding.companions(dso)` is `[Companion(body_path, body)]` when `body_path`
  is present. `withCompanions` fills `body`.

### Gate and corpus (IC-04)

- **Vocabulary.** The two `file-kinds.yaml` rules move from `exclusions` to `files`. Schemas,
  counts and keywords (post-plan R2, R3):
  - The pinned counts go from 14 to 16: `PackFileKindTest` (`fileRules`), `PackSchemasTest`
    (`bundledKinds`, the test name and javadoc), the `PackSchemas` javadoc and the README.
  - `PackFileKindTest`'s two sample paths move from `EXCLUDED` to `MAPPED`.
  - The skill schema brings `not` and `maxLength`, which no bundled schema used before. They join
    `SchemaKeywordCoverageTest`'s fallback set, and a test shows each yields a stable violation
    detail.
  - Each schema's `$id` follows `PackSchemas`' prefix rule.
- **Gate.** `expected-red.yaml` loses `other:glossary_packs`. The census kind of
  `spec-kitty-core.glossary-pack.yaml` becomes `glossary-pack`, modelled through the registry
  (#191's `DomainRoundTrip`).
- **A modelled kind the pinned pack has no file of (post-plan R1).** The gate fails a modelled kind
  with no census file (`EMPTY_MODELLED_KIND`), and built-in has no skill, only `skills/.gitkeep`.
  `expected-red.yaml` gains an `empty_kinds` list. Each entry has a kind, a reason and an issue, and
  the list is ratcheted both ways:
  - a listed kind that gains a file fails with `STALE_BASELINE`;
  - a listed kind that is not modelled fails too;
  - an unlisted empty kind still fails;
  - `WholePackExpectationTest` gets a positive control for each.

  The `skill` entry also says the gate does not yet pair a companion with its artifact (R5): the
  ratchet forces that work the day built-in gains a skill.
- **Corpus test (FR-009).**
  - Each `*.glossary-pack.yaml` and `*.skill.yaml` under the pinned open-packs `packs/` is read
    through the store, with the companions resolved against the source directory.
  - Each is mapped to the entity and back, written to a temp directory with its companions, and
    re-read.
  - The test compares the source and written YAML with `MeaningComparator`, and the bodies exactly.
  - It asserts the covered list (4 + 5).
  - A lossy writer, one that drops `see_also`, is the positive control.
- **Seam test.** `WholePackOpenPacksRoundTripTest` needs no change: its `meaning` compares DSOs, and
  `SkillDso` holds the body. It now loads `internal`'s skills and glossary pack with no violation.

### Docs (IC-05)

- ADR-0040 and its index row.
- The per-kind rows for both kinds, recorded in ADR-0040 as ADR-0036's table asks.
- A changelog entry and its index line.
- No change to the logical model beyond what the ADR states.

## Complexity Tracking

| Choice | Why | Simpler alternative rejected |
|---|---|---|
| Companion hooks on `KindBinding` | The issue keeps the skill and body as a pair. A body that is read but not digested escapes the fingerprint; one that is not read is lost on write. #193 needs the same widening. | Leaving the body unmodelled leaves the issue undone (operator decision `01M5YVEVMPMHPE0GDFQD0BZA6F`). Putting the body in the YAML strategy would make the format own paths, which the store owns. |
| Digest reads skill files through the store's schema-valid read path (static) | The digest must name the body, and only the parsed file knows its `body_path`; one source of truth (`companions(dso)`). | A `*.skill.md` naming convention is not what the schema says (`body_path` is free). Passing the strategy into `digested` changes three callers' constructors and the deployable wiring for one kind. |
| `empty_kinds` in the gate's expectation | built-in has no skill, and the gate fails a modelled kind with no file. | Leaving `skill` out of the registry-driven modelled set would make the gate lie about what is bound. |
| Stacking on #191 | Its schema-name seam and registry-driven gate are exactly what these kinds need. | Building the same seam a third time (C-003). |

## Implementation Concern Map

### IC-01 — Companion-file seam
`KindBinding` hooks; `FilesystemPackStore` read and upsert; `WholePackStore` write and modelled set;
`PackFileSet` digest. One isolated commit. A fixture binding in tests proves read, write, digest,
containment and missing-body refusal before any real kind uses the hooks. The existing kinds'
behavior and fingerprints are unchanged. FR-004, FR-005, NFR-002.

### IC-02 — Glossary pack kind
Schema, file rule (and the vocabulary counts and keyword coverage of IC-04's first bullet for this
schema), entity and values, DSO, format, binding, registration, unit tests (every field, term
order, number type, unknown key at each level, duplicate surface, no terms). FR-001, FR-003,
FR-006, FR-007, FR-010, NFR-001.

### IC-03 — Skill kind
Schema, file rule, entity and values, DSO, format, binding with companions, registration, unit tests
(every field, booleans, absent `model_invocable` with `side_effects`, wrapper form planted, missing
or escaping body, unknown key at each level). FR-002, FR-003, FR-005, FR-006, FR-007, FR-010,
NFR-001.

### IC-04 — Gate and corpus
`expected-red.yaml` (remove `other:glossary_packs`, add `empty_kinds: skill`); the `empty_kinds`
ratchet in `WholePackExpectation` with positive controls; the corpus test over 4 + 5 pinned files
with a positive control; the gate and seam runs. FR-008, FR-009.

### IC-05 — Docs and ADR
ADR-0040 with the per-kind rows; changelog; README schema count. NFR-003.
