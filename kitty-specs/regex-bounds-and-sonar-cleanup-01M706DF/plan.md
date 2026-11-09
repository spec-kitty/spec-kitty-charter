# Implementation Plan: Bounded identifier patterns and Sonar cleanup

**Branch**: `fix/regex-bounds-and-sonar-cleanup` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

## Summary

Every pack and mission identifier pattern is rewritten with possessive quantifiers, so the regex engine no longer
recurses once per repeated segment. Each identifier type checks a length cap before matching: 64 for kebab ids,
255 for executable paths and expected-artifact keys. The pack schemas gain the same caps as `maxLength`, announced
as Breaking. The YAML int and float resolver and the git source path pattern get the same possessive rewrite. The
pack policy's guarded `Optional.get` becomes one `map`/`orElseGet`. A heuristic source scan guards against new
recursive patterns.

## Technical Context

- **Language**: Java 25, Gradle, Spring Boot service; modules `kernel`, `packs`, `missions`.
- **Schema validation**: networknt json-schema-validator with `Re2jRegularExpressionFactory`
  (`service/packs/.../infra/storage/Re2jRegularExpressionFactory.java`). RE2/J is linear-time and does not recurse, so
  a long id cannot overflow inside schema validation, and `maxLength` refuses it as `pack_unreadable`. Schema
  patterns stay ECMA-compatible; possessive quantifiers go only in Java code.
- **Equivalence**: the research squad fuzzed each rewrite against the current pattern, 300 000 random strings plus
  edge strings per pattern, with zero verdict or capture-group differences. Possessive is safe because each repeated
  segment starts with a character (`-`, `.`, `/`, `:`) that the class before it cannot consume.
- **Real ids**: the longest pack id in the pinned open-packs (`22c14b2`) is 55 characters
  (`refactoring-replace-magic-number-with-symbolic-constant`); the longest in this repository is 36. Manifest file
  paths reach 89 characters, so path fields are not given the 64 cap.

### Sites (main code)

| Module | File | Pattern site | Cap |
|---|---|---|---|
| missions | `domain/GuardReference.java:17-18` | `KEBAB`, groups 1 and 2 read at :43 | 64 per part |
| missions | `domain/LifecycleIdentifier.java:16` | kebab | 64 |
| missions | `domain/MissionTypeIdentifier.java:16` | letter-first kebab; `ID` reused by `MissionStep.java:48` | 64 |
| missions | `domain/ExpectedArtifact.java:28` (used :80) | dotted key | 255 |
| packs | `domain/GuardIdentifier.java:15` | kebab | 64 |
| packs | `domain/ProcedureIdentifier.java:15` | kebab | 64 |
| packs | `domain/ToolguideIdentifier.java:16` | letter-first kebab | 64 |
| packs | `domain/GuardImplementation.java:16` | groups 1-3 read at :30-35 | 64 per part |
| packs | `domain/Executable.java:29` | path | 255 |
| packs | `domain/Preset.java:47` | kebab, cap 64 exists | 64 (exists) |
| packs | `domain/SkillIdentifier.java:15` | kebab, cap 64 exists | 64 (exists) |
| packs | `infra/storage/Yaml.java:66-73` | INT and FLOAT sexagesimal branch | none (possessive only) |
| packs | `infra/fetch/git/GitSourceRules.java:41` | URL path, bounded by `MAX_URL_CHARS` | none (possessive only) |
| packs | `domain/PackPolicy.java:570-573` | guarded `Optional.get` | n/a |

### Schema fields that get `maxLength`

- **64 (kebab ids)**: the `id` of guard, lifecycle, procedure, toolguide, mission-type, mission-type `Step`,
  step-enhancement, styleguide, tactic, paradigm and directive. Also the lifecycle `StateId` and pack-descriptor
  `name`. Preset and skill already carry 64.
- **255 (composite or path-like)**: lifecycle `GuardEntry.ref` (`pack::guard:name`), asset `Executable.entry`, and
  mission-type `artifact_key` (input and output).
- **Not capped here**: file-path patterns already anchored to fixed shapes (`prompt_path`, `guidelines_path`,
  `guide_path`, preset `path`), hashes and ULIDs (fixed length), schema versions, and graph-shard URNs and edges
  (outside the 11 sites; noted for #443/#444).

## Charter Check

- **PROJECT_002 (contract)**: the schema narrowing is a contract change. It gets a fragment under
  `contracts/charter/changes/` with `### Breaking` (ADR-0022, ADR-0047); no release is cut.
- **PROJECT_001 / DIRECTIVE_034 (test first)**: each site gets a red test first, then the fix. The test is a
  ≥10 000-segment input that throws an `Error` today, or an over-cap id that is accepted today.
- **QA_003 (no fake green)**: tests assert the outcome (the refusal or the value), never only "no exception".
- **NO_FULL_HEAVY_SUITES_IN_MISSION**: targeted Gradle tasks only.
- **RECONCILE_CHANGE_SCOPE_TENSIONS**: each fix stays at its site; the shared grammar is #444.
- **DIRECTIVE_051 (supply chain)**: no new dependency.

## Project Structure

### Documentation (this mission)

```
kitty-specs/regex-bounds-and-sonar-cleanup-01M706DF/
├── spec.md
├── plan.md
├── research.md
├── checklists/requirements.md
└── tasks/
```

### Source Code (repository root)

```
service/packs/src/main/java/ai/speckitty/charter/packs/domain/        identifier types, PackPolicy
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/  Yaml.java
service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/    GitSourceRules.java
service/missions/src/main/java/ai/speckitty/charter/missions/domain/  identifier types
contracts/packs/schemas/*.json                                          maxLength
contracts/charter/changes/                                              Breaking fragment
contracts/charter/scenarios/                                            registration scenario
build-logic/src/test/kotlin/                                            regression guard
```

## Work packages (outline)

1. **WP01: packs identifiers.** Possessive rewrites and caps for GuardIdentifier, ProcedureIdentifier,
   ToolguideIdentifier, GuardImplementation, Executable, Preset and SkillIdentifier.
2. **WP02: missions identifiers.** The same for GuardReference, LifecycleIdentifier, MissionTypeIdentifier and
   ExpectedArtifact.
3. **WP03: schema caps and contract.** `maxLength` as listed above, the Breaking fragment, and a registration
   scenario showing a 65-character id refused as `500 pack_unreadable`. Includes the open-packs check (FR-007).
4. **WP04: resolver, git path, pack policy.** Yaml INT and FLOAT, GitSourceRules.PATH, and the PackPolicy fold.
5. **WP05: regression guard.** A source scan of main code, with its own fixture tests.

## Risks

- **A capture group lost in the rewrite**: GuardReference and GuardImplementation read groups. Mitigation: their
  tests assert the captured parts on the same inputs (FR-003).
- **A mutation floor**: a new cap adds a boundary mutant. Mitigation: each cap test checks the id at exactly the cap
  and one character over.
- **The guard's false positives**: it is heuristic, so a pattern it can't judge is listed in an allow-list with its
  reason.
