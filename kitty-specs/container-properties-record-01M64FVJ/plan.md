# Implementation Plan: Container profile properties record

**Branch**: `feat/228-container-config-properties` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/container-properties-record-01M64FVJ/spec.md`

## Summary

**Update (operator, #228 issuecomment-6506747587):** the start-up check on keys outside the namespace is part of this
mission (IC-04, WP03).

ADR-0041 decision 2 adds a separate, validated properties record at `charter.container.*` for the `container`
profile. This mission builds it in the deployable module:

- `ContainerProperties` is a `@ConfigurationProperties("charter.container")` `@Validated` record, beside
  `CharterProperties`.
- `ContainerWiring` is a `@Profile("container")` configuration class that enables the record. The application's
  component scan picks it up, as it does `CharterWiring` and `GuardWiring`.
- `spring-boot-starter-validation` enters the build under strict SHA-256 verification.
- Tests cover every failure, the valid binding, the profile-off path and the shipped application.

## Technical Context

**Language/Version**: Java 25 (CI); Spring Boot 4.1.1
**Primary Dependencies**: `spring-boot-starter-validation` (new: Hibernate Validator 9.1.3.Final, `tomcat-embed-el`,
`jboss-logging`, `classmate`), Spring Boot configuration properties
**Storage**: N/A
**Testing**: JUnit 5 with AssertJ. `ApplicationContextRunner` covers the profile and binding cases; a
`SpringApplicationBuilder` run of `CharterServiceApplication` covers the shipped wiring; MockMvc pins the HTTP side
effect in `ProblemResponsesTest`.
**Target Platform**: The charter service: a local loopback daemon, or a container under the `container` profile
**Project Type**: single (Gradle multi-module; only `service/deployable` and the build files change)
**Performance Goals**: N/A (start-up binding only)
**Constraints**: zero new suppressions; complexity at most 15; strict dependency verification; local path unchanged
**Scale/Scope**: 2 production classes, 1 new test class, 1 test added to an existing class, 3 build files

## Charter Check

- **No unverified dependency:** passes. `gradle/verification-metadata.xml` is regenerated with
  `--write-verification-metadata sha256` (docs/guides/service-build.md) and reviewed. The new entries are only the
  starter's own tree.
- **Contract not broken:** passes, with one finding. The contract is unchanged. Putting bean validation on the
  classpath turns a registration body that lacks a required field from a 500 `internal_error` into a 400 problem,
  which each operation's `default` response admits. It is pinned in a test and raised on #228 (research.md R2).
- **Adversarial squad at plan and before merge:** this is configuration, not contract, architecture or domain work.
  The pre-PR squad still runs (brief), and the HTTP finding is reviewed there.
- **Calls made without review are logged:** R1 and R2 are open on #228 and logged in research.md.
- **Designing during the rewrite:** passes; no core dependency.

## Project Structure

### Documentation (this mission)

```
kitty-specs/container-properties-record-01M64FVJ/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md (+ tasks/)
```

### Source Code (repository root)

```
gradle/libs.versions.toml                       # + spring-boot-starter-validation alias
gradle/verification-metadata.xml                # regenerated
service/deployable/build.gradle.kts             # + implementation(validation starter)
service/deployable/src/main/java/ai/speckitty/charter/deployable/
├── ContainerProperties.java                    # the record
└── ContainerWiring.java                        # @Profile("container") enablement
service/deployable/src/test/java/ai/speckitty/charter/deployable/
├── ContainerPropertiesTest.java                # every failure, the binding, profile off, shipped app
└── ProblemResponsesTest.java                   # + the 400 side effect
docs/changelog/2026-10-29-container-properties-record.md (+ README index line)
```

**Structure Decision**: everything stays in the deployable, the composition root. The packs module does not learn
the profile (ADR-0041 decision 2).

## Complexity Tracking

No violations.

## Implementation Concern Map

### IC-01 — Validated container record and its build dependency

- **Purpose**: bind `charter.container.*` into one validated record under the `container` profile only, and fail
  the start naming the property.
- **Relevant requirements**: FR-001, FR-002, FR-003, NFR-001, NFR-002, NFR-003, C-001 to C-004
- **Affected surfaces**: `ContainerProperties`, `ContainerWiring`, `ContainerPropertiesTest`, the catalog, the
  deployable build file and the verification metadata
- **Sequencing/depends-on**: none
- **Risks**:
  - Spring reports the Java field name (`allowedHosts`), so each constraint message names the canonical key itself.
  - `@Profile` on the enabling class, not on the record, keeps the namespace unbound without the profile.

### IC-02 — The validation side effect on the HTTP surface

- **Purpose**: make the HTTP change that bean validation brings deliberate: pin it in a test and record it.
- **Relevant requirements**: FR-004
- **Affected surfaces**: `ProblemResponsesTest`, the changelog entry
- **Sequencing/depends-on**: IC-01 (the dependency)
- **Risks**: the operator may prefer to keep MVC validation off (#228); that would reverse the pin, a small change.

### IC-03 — Living docs

- **Purpose**: a changelog entry, plus follow-ups noted for #229 to #232 and for the guard consuming
  `allowed-hosts`.
- **Relevant requirements**: SC-003
- **Affected surfaces**: `docs/changelog/`
- **Sequencing/depends-on**: IC-01, IC-02
- **Risks**: the docs checks (`check_docs.py`, markdownlint, cspell) must pass; `related:` slugs must be real pages.

### IC-04 — Start-up check on keys outside the namespace

- **Purpose**: under the `container` profile, fail the start on port 0, on a configuration root that is unset, relative,
  missing or not a directory, on `charter.insecure-no-token` and on `charter.project-config-dir` (ADR-0041 decisions 4
  to 6, settled into #228 by the operator).
- **Relevant requirements**: FR-005, FR-006, FR-007, FR-008, SC-004
- **Affected surfaces**: `ContainerStartupCheck` (a `@Profile("container")` configuration class, an `InitializingBean`),
  `ContainerStartupCheckTest`, and the shipped-application tests in `ContainerPropertiesTest`
- **Sequencing/depends-on**: IC-01 (the profile constant)
- **Risks**: builder default properties rank below `application.yaml`, so tests of the shipped application pass the
  port and the root as arguments. The local `port: 0` makes a container start without `SERVER_PORT` fail until
  #229 lands; that failure is correct, and it is pinned.
