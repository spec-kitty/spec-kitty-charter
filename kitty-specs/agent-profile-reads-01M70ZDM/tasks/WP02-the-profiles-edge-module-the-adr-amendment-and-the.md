---
work_package_id: WP02
title: The :profiles-edge module, the ADR amendment and the ArchUnit rules
dependencies:
- WP01
requirement_refs:
- C-001
- NFR-002
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 2b0023b1d12864d2fb17e9c8046846c0c6e3daa8
created_at: '2026-11-09T21:27:57.344311+00:00'
subtasks:
- T005
- T006
- T007
- T008
- T009
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: service/profiles-edge/
create_intent:
- service/profiles-edge/build.gradle.kts
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/packs/package-info.java
execution_mode: code_change
model: ''
owned_files:
- service/profiles-edge/build.gradle.kts
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/config/**
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/config/**
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/packs/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/packs/config/**
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/package-info.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/config/**
- settings.gradle.kts
- service/deployable/build.gradle.kts
- service/deployable/src/main/java/ai/speckitty/charter/deployable/DeployableConfiguration.java
- service/charter-reports/build.gradle.kts
- .github/workflows/sonar.yml
- scripts/ci/mutation_summary.py
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/profiles/**
- service/kernel/src/main/java/ai/speckitty/charter/kernel/transport/**
- docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md
- docs/adr/0006-onion-vertical-slices-archunit.md
- docs/architecture/hexagonal-service.md
role: implementer
tags: []
tracker_refs: []
---
# WP02: The :profiles-edge module, the ADR amendment and the ArchUnit rules

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Create the `:profiles-edge` Gradle module, record why it exists, and gate its rings. This WP ships the empty
skeleton: package-infos and `config` classes, with no behaviour. The mission-types session mirrors it as
`:missions-edge`, so push this WP early and send its commit SHA to the orchestrator.

## Context

- Operator decision `01M710E3M0B37N99XEK4A7JEY0`. Plan: IC-02, fold A1 and A2 (the architecture lens).
- **Why a new module:** `:packs` depends on `:profiles`, because `packs.infra.storage` binds `AgentProfile` (ADR-0037,
  amendment of 2026-10-27). Rings in `:profiles` that call `packs.application` would create a cycle.
- **Dependency direction:** `:profiles` ← `:packs` ← `:activations` ← `:profiles-edge` ← `:deployable`.

## Subtasks

### T005 — The ADR amendment

1. Add an amendment dated 2026-11-09 to `docs/adr/0037-…`, titled "The slice edge module".
   - A slice whose domain `packs.infra.storage` binds keeps that domain in its own module (`:profiles`).
   - Its `application`, `infra.api`, `infra.packs` and `infra.activations` rings live in `:<slice>-edge`, with the
     slice's package names unchanged.
   - Options considered:
     - split out the domain module;
     - put the rings in `:packs`;
     - a neutral view type in `packs.domain`;
     - amend the rule.
   - Also record that the mission-types slice follows this pattern (`:missions-edge`).
2. Add a short amendment to ADR-0006: one slice may span a domain module and an edge module.
3. Update the module table in `docs/architecture/hexagonal-service.md`:
   - add a `profiles-edge` row;
   - update the `profiles` row ("domain ring only; its other rings are in `profiles-edge`").
4. Keep to the docs style: markdownlint-cli2, cspell, `check_docs.py` (front matter, related links).

### T006 — The module and its registrations (every list; fold A1)

1. **`service/profiles-edge/build.gradle.kts`.**
   - Plugins: `charter.spring-conventions`, `charter.hand-written` and `charter.mutation-conventions`. Copy the
     shape of `service/activations/build.gradle.kts`.
   - Declare `mutationFloors` like the other modules.
   - Never `domain-conventions`: it refuses external dependencies.
   - Dependencies: `api(project(":profiles"))`, plus `implementation` of `:packs`, `:activations` and
     `:web-transport`, and `api(project(":charter-api"))`. `:activations` does not pass `:packs` on, so declare
     `:packs` explicitly.
2. **`settings.gradle.kts`.** Modules are listed by hand at lines 20 to 35: add `profiles-edge`.
3. **`:deployable`.** Add `implementation(project(":profiles-edge"))`.
   - The slice rules use `allowEmptyShould(true)`, so a module missing from the classpath passes with nothing
     checked. This line is what puts the new rings under the gate.
4. **`DeployableConfiguration`.** Its `@ComponentScan` (lines 21 to 29) lists the `*.config` packages explicitly.
   Add:
   - `ai.speckitty.charter.profiles.application.config`
   - `ai.speckitty.charter.profiles.infra.api.config`
   - `ai.speckitty.charter.profiles.infra.packs.config`
   - `ai.speckitty.charter.profiles.infra.activations.config`

   Each package holds one empty `@Configuration` class.
5. **`charter-reports/build.gradle.kts` (`serviceModules`).** It throws on a module it does not know, and maps each
   module to one package. Change it to map a module to a list of packages:
   - `profiles` → `ai.speckitty.charter.profiles.domain`
   - `profiles-edge` → `ai.speckitty.charter.profiles.application` and `ai.speckitty.charter.profiles.infra`

   Without this, the two Javadoc groups collide under `-Werror`. Run `:charter-reports:aggregateJavadoc` to check.
6. **Two lists that drift silently:**
   - `.github/workflows/sonar.yml` (lines 156 to 161);
   - `scripts/ci/mutation_summary.py` (line 28).

   Add the module to both.

### T007 — The ArchUnit rules, each with a bite fixture (fold A2)

`applicationIsEdge("profiles")` already holds `profiles.application`. Add these rules to
`HexagonalArchitectureTest`:

- **(a)** `profiles..`, outside `profiles.infra.packs`, names no `packs..` class.
- **(b)** `profiles.infra.packs` names only `packs.application` and `packs.domain` of packs. Mirror
  `ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION`, around line 1000.
- **(c)** `profiles..`, outside `profiles.infra.activations`, names no `activations..` class.
- **(d)** `profiles.infra.activations` names only `activations.application` and `activations.domain`.
- **(e)** No `packs..` or `activations..` class names `profiles.application..` or `profiles.infra..`. Today only
  `profiles.infra..` is covered.

For each rule:

1. Add a bad fixture under `service/deployable/src/test/java/ai/speckitty/charter/profiles/.../badfixture/`.
2. Add an `assertBites` test, the way the existing rules do.

Then check that the existing rules see the new packages:

- the `SLICES` list;
- `sliceDomainDoesNotCross`;
- `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS`;
- `everyHandWrittenPackageIsNullMarked`.

`profiles.infra.api` must not name `packs.infra.api`: no `Hypermedia`, no `ArtifactDtos`, no `CharterHeaders`.

### T008 — Move the fingerprint header name into the kernel

1. Add `ai.speckitty.charter.kernel.transport.CharterHeaders` (or `HeaderNames`), holding the
   `Charter-Source-Fingerprint` name. If the kernel's purity rule allows it, put the
   `Charter-Contract-Version` name there too.
2. Switch the existing users to it: `packs.infra.api.CharterHeaders` and the activations controller's local copy.
   Keep this an equality refactor: no wire change.
3. Check that `KERNEL_IS_PURE` allows a constants class in `kernel.transport`. If it does not, put the class in
   `web-transport` instead, which every slice's `infra.api` may name, and note the choice.

### T009 — Skeleton smoke test

1. Add a `:deployable` wiring test: the context starts, and the four `profiles` `config` beans are present.
2. Run:
   ```
   ./gradlew --max-workers=1 :profiles-edge:check :deployable:test --tests '*HexagonalArchitectureTest*'      --tests '*CharterWiringTest*' :charter-reports:aggregateJavadoc
   ```

## Definition of Done

- The module builds, and the deployable wires it.
- Every new rule bites on its fixture and passes on the real code.
- The Javadoc aggregate is green.
- The ADR amendment and the module table are written.
- The commit SHA is sent to the orchestrator (via the review notes) for the mission-types session.

## Risks

- A rule's package glob that does not match classes in the new module passes vacuously. That is why each rule
  needs its bite test.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP02 --agent claude --mission agent-profile-reads-01M70ZDM`

## Rules that hold for every WP of this mission

- **Charter wins.** Read `.kittify/charter/charter.md`. Both are gates:
  - no divergence from the charter's own model, contract and packs;
  - no fake green. Never skip, disable or weaken a test to get green.
- **Test first.** Write the failing test or scenario first, see it red, then make it green. Record the red run in the
  WP's commit message body (one line: the test and its failure).
- **Gradle:**
  - JDK: `JAVA_HOME=/usr/lib/jvm/temurin-25`.
  - Always `--max-workers=1`, in the foreground, one run at a time, never `--stop`.
  - On a Maven Central 429, wait and rerun.
- **Before you hand off:**
  - Run the module's tests, plus `./gradlew --max-workers=1 :deployable:test --tests '*HexagonalArchitectureTest*'`
    when you touch a ring.
  - Run `./gradlew --max-workers=1 spotlessApply` and the module's `check`.
  - The final WP runs the full `verifyAll`.
- **Commits:**
  - Each commit is green on its own: the repo lands commits individually.
  - Commits are SSH-signed, which is automatic here.
  - End each message with:
    ```
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
    Claude-Session: https://claude.ai/code/session_01E5wJUBWJn7yEXdCeUEJGmP
    ```
  - No model identifiers in code, comments or commit text.
- **Contract changes** follow ADR-0022 (0.x line) and ADR-0047:
  - one fragment per `###` block in `contracts/charter/changes/`;
  - never edit `contracts/charter/CHANGELOG.md`;
  - every 2xx response gets an example with `_links`;
  - every new 2xx is exercised by an `@contract` scenario (`OperationCoverageTest`).
- **Javadoc:** every new package has a `package-info.java` annotated `@NullMarked`. Javadoc runs with `-Werror`.
- **Design references:** `plan.md` (the IC sections, then the "Fold of the plan-point squad", which wins where they
  differ), `data-model.md` and `research.md` in `kitty-specs/agent-profile-reads-01M70ZDM/`.
