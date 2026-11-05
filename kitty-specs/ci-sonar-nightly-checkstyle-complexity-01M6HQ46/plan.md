# Implementation Plan: Nightly SonarCloud and per-PR complexity gate

**Historical; superseded in part.** See the note at the top of [spec.md](spec.md): the complexity limits, register and stale check did not land.

**Branch**: `ci-sonar-nightly-checkstyle-complexity` | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/ci-sonar-nightly-checkstyle-complexity-01M6HQ46/spec.md`

## Summary

Two independent changes implement Proposed ADR-0056.

1. **Per-PR complexity gate.** The Checkstyle configuration the build already runs gains four
   complexity modules, with the limits the operator ruled. The methods that exceed a limit today are
   registered one by one, by XPath, in the existing single register. A build-logic check fails
   `verifyAll` when an entry has gone stale or the register's Size count is wrong.
2. **Nightly SonarCloud.** A new scheduled workflow builds trusted `main` and runs the generic scan
   action that core pins. It is reported only and skips green until the token exists.

## Engineering Alignment (confirmed 2026-11-04, decision `01M6JHQW6K8TH51XPHZK5VNJNB`)

- **Limits:** `CyclomaticComplexity` 15 (switch block as a single decision point, as measured),
  `NPathComplexity` 1000, `MethodLength` 60 (empty lines not counted), and `NestedIfDepth` 1. They
  apply to main and test sources.
- **Register.** Each existing violator gets one `suppress-xpath` entry in
  `config/checkstyle/suppressions.xml`.
  - The entry names the method, for example
    `//CLASS_DEF//METHOD_DEF[./IDENT[@text='isGlobalV4']]` with `files="AddressPolicy\.java"`.
  - A new method in the same file gets no exemption, and line drift does not matter.
  - The file switches to the XPath DTD. Both `SuppressionFilter` and `SuppressionXpathFilter` read it,
    so there is still one register (C-003).
- **Stale-entry check.** A build-logic task re-runs the build's own Checkstyle over only the register's
  files, with the register empty. A verification task then fails `check` when:
  - any rule an entry lists no longer fires on its method (stale or too wide);
  - an entry lacks rule, file, reason or sunset;
  - the Size count disagrees with the entries.
- **Workflow.** `.github/workflows/sonar.yml` runs on `schedule` and `workflow_dispatch` only, with
  `if: github.repository == 'spec-kitty/spec-kitty-charter' && github.ref == 'refs/heads/main'`.
  - **Steps:** a token probe that skips green with a notice, the checkout, JDK 25, the Gradle setup
    action already used in `service.yml`, `syncPacks` with open-packs at the pin, `verifyAll`, the
    scan, and the quality-gate read written to the step summary.
  - **Reported only:** every step after the probe is `continue-on-error`.
  - **Pins:** `SonarSource/sonarqube-scan-action` v8.2.1 and `sonarqube-quality-gate-action` v1.2.1
    are pinned to core's SHAs.
- **Guard for FR-005.** A test asserts:
  - `sonar.yml` has no `pull_request`, `pull_request_target` or `merge_group` trigger;
  - `ci.yml` does not call it;
  - the gate job's `needs` do not include it.

## Technical Context

**Language/Version**: Java 25 (Temurin) sources under check; Kotlin (Gradle Kotlin DSL) for build-logic; GitHub Actions YAML
**Primary Dependencies**: Checkstyle 14.3.0 (already declared in `gradle/libs.versions.toml`); Gradle's built-in `checkstyle` plugin; `SonarSource/sonarqube-scan-action` v8.2.1 and `sonarqube-quality-gate-action` v1.2.1 (GitHub Actions, SHA-pinned as in core)
**Storage**: N/A (files: `config/checkstyle/*.xml`, `.github/workflows/sonar.yml`)
**Testing**: JUnit 6 tests in `build-logic/src/test/kotlin` (register parser, stale detection, workflow-trigger guard); planted over-limit methods in a test fixture to prove each limit bites; `./gradlew verifyAll`
**Target Platform**: GitHub-hosted `ubuntu-24.04` runners; local JDK 25 builds
**Project Type**: single (Gradle multi-project build plus CI configuration)
**Performance Goals**: complexity checks add under 30 s to `verifyAll` (NFR-001); nightly run under 20 min (NFR-002)
**Constraints**: no new Gradle plugin or library (C-001); `CI gate / gate` unchanged (C-002); one register (C-003); no PR-authored code next to `SONAR_TOKEN` (NFR-003)
**Scale/Scope**: about 6,300 methods checked; about 15 register entries; 1 new workflow

**Supply-chain check (DIRECTIVE_051).** No Gradle or library dependency changes. The two new
third-party actions are pinned to the exact SHAs that core already runs and reviews
(`22918119ff8e1ca75a623e15c8296b6ea4fbe28f`, `7a5fffe8e523c40e0c740b6bc2712ab503e52efa`). Every
other action reuses a pin already present in this repository's workflows. Copying the pins is not a
new trust decision, and research.md records the disposition.

## Charter Check

| Policy | Status |
|---|---|
| Non-trivial work runs as a mission | Met: this mission. |
| Adversarial review squad for contract, architecture or domain changes | This is a CI and build-gate change, with no contract or domain change. A two-lens review (correctness, CI security) runs before the PR. Any call made without review is logged in the PR. |
| No core issues as prerequisites | Met: core is only read for its action pins. |
| No compatibility with core's formats | N/A. |

No violations, so Complexity Tracking is empty.

## Project Structure

### Documentation (this mission)

```text
kitty-specs/ci-sonar-nightly-checkstyle-complexity-01M6HQ46/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── decisions/
```

### Source Code (repository root)

```text
config/checkstyle/
├── checkstyle.xml          # + 4 complexity modules, + SuppressionXpathFilter
└── suppressions.xml        # XPath DTD; ~15 suppress-xpath entries with rule/file/reason/sunset
build-logic/src/main/kotlin/
├── charter.hand-written.gradle.kts   # register probe + verification task wired into check
└── CheckstyleRegister.kt             # parse register, compare with probe report (pure, unit-tested)
build-logic/src/test/kotlin/
├── CheckstyleRegisterTest.kt         # stale / missing field / wrong Size / matching entry
└── SonarWorkflowGuardTest.kt         # triggers, ci.yml call, gate needs
.github/workflows/sonar.yml           # new
docs/guides/service-build.md          # limits, register, nightly report
docs/changelog/2026-11-04-*.md        # entry
```

**Structure Decision**: the register logic lives in build-logic as a pure Kotlin unit, so it is tested
there like `ServiceReleaseGate`. No service module changes.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Complexity limits and the method-scoped register

- **Purpose**: make complexity block in the per-PR gate, with today's violators listed method by method.
- **Relevant requirements**: FR-001, FR-002, NFR-001, C-001, C-003
- **Affected surfaces**: `config/checkstyle/checkstyle.xml`, `config/checkstyle/suppressions.xml`
- **Sequencing/depends-on**: none
- **Risks**: an XPath that matches nothing silently exempts nothing. That is safe, but it leaves a dead
  entry, which IC-02 catches. Several methods break more than one rule. Each method has one entry, whose `checks` regex lists
  every rule it breaks, so the register stays at about 15 entries (SC-002).

### IC-02 — Stale-entry and register-format check

- **Purpose**: keep the register shrink-only and non-vacuous.
- **Relevant requirements**: FR-002, SC-002
- **Affected surfaces**: `build-logic/src/main/kotlin/` (new `CheckstyleRegister.kt`, wiring in
  `charter.hand-written.gradle.kts`), `build-logic/src/test/kotlin/`
- **Sequencing/depends-on**: IC-01 (the register format)
- **Risks**:
  - Configuration-cache safety: the probe must not reach into other projects.
  - Mapping a probe violation to its method: complexity checks report the method's declaration line,
    and the method's name is read from the source at that line.

### IC-03 — Nightly reported-only SonarCloud workflow

- **Purpose**: report duplication, security hotspots and new-code coverage nightly, without blocking.
- **Relevant requirements**: FR-003, FR-004, FR-005, NFR-002, NFR-003, NFR-004, C-002
- **Affected surfaces**: `.github/workflows/sonar.yml`, `build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt`
- **Sequencing/depends-on**: none
- **Risks**:
  - The analysis is not exercised until the operator adds the token, so this PR proves only the
    skip-green path. The analysis path is checked by dispatch afterwards.
  - The project key `spec-kitty_spec-kitty-charter` is an assumption the operator confirms.

### IC-04 — Documentation

- **Purpose**: tell contributors the limits, how to read and shrink the register, and where the nightly
  report lives.
- **Relevant requirements**: FR-006
- **Affected surfaces**: `docs/guides/service-build.md`, `docs/changelog/`
- **Sequencing/depends-on**: IC-01, IC-03
- **Risks**: the docs gate (terminology, spelling).
