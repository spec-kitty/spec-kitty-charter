---
work_package_id: WP04
title: Validator dependency and schema loading
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- C-002
- C-006
- FR-006
- FR-007
- NFR-005
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/
create_intent:
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackSchemas.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackFileKind.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackSchemasTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackFileKindTest.java
execution_mode: code_change
model: sonnet
owned_files:
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- service/adapter-out-yaml/build.gradle.kts
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackSchemas.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackFileKind.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackSchemasTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackFileKindTest.java
- service/adapter-out-yaml/src/test/resources/schema-fixtures/**
role: implementer
tags: []
tracker_refs: []
---

# WP04 — Validator dependency and schema loading

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`,
and the supply-chain tactic: `spec-kitty charter context --include tactic:supply-chain-install-safety`.
The charter wins over this prompt: "no unverified dependency enters the build".

## Objective

This WP adds `com.networknt:json-schema-validator:3.0.8` to `adapter-out-yaml` only, under the
supply-chain rules (FR-007, C-002, C-006, DIRECTIVE_051). It does so without moving the service's
Jackson (3.1.5) or slf4j (2.0.18) versions.

It then loads the 14 bundled schemas strictly, offline and once (FR-006, NFR-005), and classifies
files using `contracts/packs/file-kinds.yaml`.

This WP does **not** change the load path; WP07 does. Here the schemas are loadable and tested
on their own.

Read first:
- [research.md](../research.md) R2: the decision, the build snippet, the API notes, the
  DIRECTIVE_051 table, the binary-compatibility and slf4j findings;
- [plan.md](../plan.md): "Schema loading" and IC-04;
- `docs/guides/service-build.md:105-117` ("Change a dependency").

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json`.
- This WP depends on WP01 (`PackContents.SchemaViolation`), WP02 (schemas) and WP03 (the build file and the pin).
- Pass `-PopenPacksDir=/home/user/spec-kitty-open-packs` (absolute; the new layout at the pin) to every Gradle command.
- Implement with:
  `spec-kitty agent action implement WP04 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T016 — Catalog entry and build file

**Steps**:

1. `gradle/libs.versions.toml`:
   - `[versions]`: add `json-schema-validator = "3.0.8"`, with a comment naming the DIRECTIVE_051
     review (mission `pack-schemas-and-level-policy-01M5AFMA`, research R2).
   - `[libraries]`: add
     - `json-schema-validator = { module = "com.networknt:json-schema-validator", version.ref = "json-schema-validator" }`;
     - `jackson-databind = { module = "tools.jackson.core:jackson-databind" }` (BOM-versioned);
     - `slf4j-api = { module = "org.slf4j:slf4j-api" }` (BOM-versioned).
2. `service/adapter-out-yaml/build.gradle.kts`. Keep WP03's pin wiring and add:

   ```kotlin
   // networknt json-schema-validator validates pack files against contracts/packs/schemas (ADR-0017).
   // The Spring Boot BOM is used here only to pin Jackson 3 and slf4j to the versions the service runs;
   // networknt's own Jackson (3.2.x) and slf4j pins are excluded so the service classpath does not move.
   implementation(platform(catalog.lib("spring-boot-bom")))
   implementation(catalog.lib("json-schema-validator")) {
       exclude(group = "tools.jackson.core")
       exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
       exclude(group = "org.slf4j", module = "slf4j-api")
   }
   implementation(catalog.lib("jackson-databind"))
   implementation(catalog.lib("slf4j-api"))
   ```
3. Bundle the schemas as resources: add `contracts/packs` to the **main** resources under the
   prefix `packs/`, with `tasks.processResources { from(rootProject.layout.projectDirectory.dir("contracts/packs")) { into("packs") } }`.
   (`charter-service/build.gradle.kts:29-31` shows the same idiom for `processTestResources`.) Then
   `classpath:packs/schemas/<kind>.schema.json` and `classpath:packs/file-kinds.yaml` resolve.
   Make it a declared input, so a schema change re-runs the tests.

### T017 — Verification metadata and dependency evidence

**Steps**:

1. Generate the metadata with
   `./gradlew --write-verification-metadata sha256 verifyAll -PopenPacksDir=<pinned checkout> --max-workers=2`.
   - On HTTP 429 from Maven Central, wait and retry.
   - **Never** edit `gradle/verification-metadata.xml` by hand to get past a fetch error.
2. Review `git diff gradle/verification-metadata.xml`. Expect exactly two new components
   (research R2):
   - `com.networknt:json-schema-validator:3.0.8`, jar and pom;
   - `com.ethlo.time:itu:1.14.0`, jar and pom.

   Cross-check the jar hashes against the research values
   (`d3f4c61b…` for networknt, `5cf40ab0…` for itu). Any other new component needs a one-line
   justification in the evidence, or a fix.
3. Record `dependencyInsight` for `tools.jackson.core:jackson-databind` and `org.slf4j:slf4j-api`
   on `:charter-service:runtimeClasspath`. Both must resolve to the Boot BOM versions (3.1.5 and
   2.0.18) and not to 3.2.1 or 2.0.19.
4. Add one sentence to the evidence for `docs/guides/service-build.md`. WP09 owns that file and
   writes the sentence: "a non-Spring module may import the Boot BOM as a platform to pin a
   transitive to the service's version".

### T018 — `PackSchemas` and `PackFileKind`

**Steps**:

1. `PackSchemas` (package-private where possible; Javadoc everywhere) sets up the registry. Use
   the 3.x API names verified against the 3.0.8 jar:
   - packages: `com.networknt.schema.{SchemaRegistry, Schema, SchemaLocation, Error}`,
     `com.networknt.schema.dialect.{Dialect, Dialects}`,
     `com.networknt.schema.keyword.DisallowUnknownKeywordFactory` and
     `com.networknt.schema.resource.SchemaLoader`;
   - import `com.networknt.schema.Error` explicitly: it clashes with `java.lang.Error`.
   - It builds one `SchemaRegistry` with
     `Dialect.builder(Dialects.getDraft202012()).unknownKeywordFactory(DisallowUnknownKeywordFactory.getInstance()).build()`.
   - It maps the prefix `https://schemas.spec-kitty.ai/charter/packs/` to `classpath:packs/schemas/`
     with `schemaIdResolvers`.
   - Its schema loader (`Builder.schemaLoader(l -> l.allow(...))`) allows only that prefix and the
     bundled Draft 2020-12 metaschema IRIs (`https://json-schema.org/draft/2020-12/...`). The
     `allow` and `block` predicates on `AbsoluteIri` run before resolver mapping, and an `allow`
     list alone is enough. A blocked IRI throws `InvalidSchemaException`, not the
     `FileNotFoundException` research R2 saw with the default loader.
   - It never calls `fetchRemoteResources()`.
   - On first use it validates each of the 14 schemas against the bundled 2020-12 metaschema, then
     compiles and caches each schema in a `Map<String, Schema>`, thread-safe.
   - A metaschema failure throws `IllegalStateException` naming the file. That is a build or
     configuration defect, not a pack finding.
   - API: `List<SchemaViolation> validate(String kind, String file, @Nullable Object yamlTree)`.
     `Yaml.load` returns `@Nullable` for an empty document; a `null` tree validates as JSON `null`,
     so the schema's `type: object` refuses it.
     - It converts with `JsonMapper.builder().build().valueToTree(yamlTree)`; the mapper is cached.
     - It maps each networknt `Error` to the domain's `PackContents.SchemaViolation(file, pointer segments, keyword, property, value, args)`.
     - It passes keywords and arguments through and **never** `getMessage()`, which is localized.
     - The pointer segments come from `getInstanceLocation()`, a `NodePath`. Read them with
       `getNameCount()` and `getElement(i)`; never parse `toString()`.
2. `PackFileKind` reads the bundled `file-kinds.yaml` with snakeyaml-engine and classifies a
   pack-relative path. It returns:
   - `Mapped(kind, schema)`;
   - `Excluded(class, reason)`;
   - or `Unclassified`.

   It also exposes the `node_kinds` and `fragment_kinds` lists for the WP05 equality test.

### T019 — `PackSchemasTest` and `PackFileKindTest` (red-first)

Write the tests before `PackSchemas`, run them red, and record the run. Fixtures go under
`src/test/resources/schema-fixtures/`.

**`PackSchemasTest`** (FR-006, NFR-005) asserts that:
- each of the 14 bundled schemas has `$schema` = 2020-12, a unique `$id` with the prefix, and
  passes the metaschema;
- every `$id` and every `$ref` in the bundle ends in `.json` or is a local `#/...` reference. A
  `.yaml` IRI would route to the excluded YAML mapper and throw `NoClassDefFoundError`;
- `contracts/packs/VENDORED.md` names every schema file, read from the repository path through a
  system property, or bundled;
- a planted broken schema with `"type": "nope"` fails the metaschema check with the file named;
- a planted schema with an unknown keyword (`"frobnicate": 1`) is refused at load;
- a planted schema with **no `$schema`** fails the bundle check, with the file named;
- a planted schema with a **dangling local `$ref`** (`#/$defs/missing`) is refused at load;
- a planted schema with `$ref: "https://example.invalid/x.json"` is refused with
  `InvalidSchemaException`, because the loader allows nothing but the bundled prefixes. Prove no
  remote fetch is possible: assert the configured loader has **no** remote resource loader (no
  `fetchRemoteResources()`). Add a second case in which a non-bundled `https://` IRI on the
  allowed prefix that does not exist on the classpath also fails, without a network call;
- a planted pair of bundled schemas where one `$ref`s the other by its `$id` resolves (the
  positive control);
- a valid minimal `pack.yaml` tree gives zero violations, and one with `pack_version` missing
  gives a `required` violation with property `pack_version` and an empty pointer;
- the conversion keeps YAML types: integer `1` is an integer, `1.0` a number and `"1.0"` a string.

**`PackFileKindTest`** asserts that:
- each `files` rule maps a sample path;
- each exclusion class matches a sample;
- an unknown file such as `foo/bar.yaml` is `Unclassified`;
- no sample path is matched by two rules.

## Definition of Done

- [ ] The catalog entry has an exact version, and the build file carries the exclusions and the
      BOM platform with a comment.
- [ ] The verification-metadata diff is exactly networknt and itu (jar and pom each), generated by
      Gradle, never hand-edited.
- [ ] `dependencyInsight` shows Jackson 3.1.5 and slf4j 2.0.18 on the service runtime classpath.
- [ ] `PackSchemasTest` and `PackFileKindTest` are green, after a recorded red run.
- [ ] `verifyAll` is green (WP01's ArchUnit rule D still holds: networknt is used only in `..yaml..`).
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Risks

- **K5 (429s).** Bounded retries.
- **K6 (Jackson drift).** The `dependencyInsight` evidence guards it.
- **Silent laxity.** The default `unknownKeywordFactory` is lax, so the strict factory is
  mandatory.

## Reviewer guidance (opus, reviewer-renata)

- Re-run `dependencyInsight` yourself.
- Diff the verification metadata and check that nothing else was added.
- Try a schema that `$ref`s a remote IRI and one with an unknown keyword; both must be refused.
- Check that no `getMessage()` result reaches a `SchemaViolation`.
