---
work_package_id: WP01
title: 'The :client-java module: okhttp-gson client from the bundle, tolerant, --release 21, ProblemDetails'
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- FR-009
- FR-010
- FR-011
- FR-012
planning_base_branch: claude/client-java-218-224
merge_target_branch: claude/client-java-218-224
branch_strategy: Planning artifacts for this mission were generated on claude/client-java-218-224. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/client-java-218-224 unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
history: []
agent_profile: java-jenny
authoritative_surface: client-java/
create_intent:
- build-logic/src/test/kotlin/TolerantClientNormalizerTest.kt
execution_mode: code_change
model: ''
owned_files:
- build-logic/src/main/kotlin/charter.client-java.gradle.kts
- build-logic/src/main/kotlin/CheckTreesIdentical.kt
- build-logic/src/main/kotlin/SchemaWalker.kt
- build-logic/src/main/kotlin/SingleModelPerSchemaNormalizer.kt
- build-logic/src/main/kotlin/TolerantClientNormalizer.kt
- build-logic/src/test/kotlin/TolerantClientNormalizerTest.kt
- client-java/build.gradle.kts
- client-java/templates/**
- client-java/src/main/**
- client-java/src/test/java/ai/speckitty/charter/client/ProblemDetailsTest.java
- client-java/src/test/java/ai/speckitty/charter/client/AdditiveMinorTest.java
- client-java/src/test/java/ai/speckitty/charter/client/ClientOutputTest.java
- gradle/libs.versions.toml
- gradle/verification-metadata.xml
- settings.gradle.kts
role: implementer
tags: []
tracker_refs: []
---

# WP01: The :client-java module: okhttp-gson client from the bundle, tolerant, --release 21, ProblemDetails

Design: plan.md D-1 to D-3; research R1 to R5.

## Subtasks

- **T001** Extract `SchemaWalker` from `SingleModelPerSchemaNormalizer` (behaviour unchanged; its test stays green).
- **T002** `TolerantClientNormalizer` plus `TolerantClientNormalizerTest`: closed objects open, open maps and real schemas kept, #119 fix applied.
- **T003** `charter.client-java` convention plugin, `client-java/build.gradle.kts`, header template, `settings.gradle.kts` include; generated source set outside the gates.
- **T004** Catalog entries and verification metadata (`--write-verification-metadata sha256`; review: additions only).
- **T005** Tests first: `ProblemDetailsTest`, `AdditiveMinorTest`, `ClientOutputTest`; then `ProblemDetails`.
- **T006** `checkClientReproducible` in `check`.

## Done when

`./gradlew :client-java:check :build-logic:check` passes on Java 25; the class files are major 65; two generations are identical; a positive control (server normalizer, default options) breaks the tolerance tests.
