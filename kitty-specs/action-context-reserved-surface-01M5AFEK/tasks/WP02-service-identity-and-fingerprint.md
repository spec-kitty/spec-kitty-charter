---
work_package_id: WP02
title: Service identity and fingerprint inputs
dependencies:
- WP01
requirement_refs:
- FR-009
- FR-010
- NFR-001
- NFR-005
- C-001
- C-003
- C-005
planning_base_branch: feat/action-context-reserved-surface
merge_target_branch: feat/action-context-reserved-surface
branch_strategy: Planning artifacts for this mission were generated on feat/action-context-reserved-surface. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/action-context-reserved-surface unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-action-context-reserved-surface-01M5AFEK
base_commit: b9acb4347df8a02c381dda3a60d7e0b11a6342fe
created_at: '2026-10-19T17:22:23.209211+00:00'
subtasks:
- T007
- T008
- T009
- T010
- T011
phase: Phase 2 - The reserved surface
history:
- at: '2026-10-19T17:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-read-app/src/main/java/ai/speckitty/charter/read/ServiceInfoQuery.java
create_intent:
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/ServiceVersion.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/ProjectConfigSources.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ProjectConfigSourcesTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ConfigSteps.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ScenarioHttp.java
execution_mode: code_change
model: sonnet
owned_files:
- contracts/charter/schemas/ServiceInfo.yaml
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/examples/ServiceInfo.one-worktree.yaml
- contracts/charter/scenarios/service.feature
- gradle.properties
- service/adapter-in-rest/build.gradle.kts
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/ServiceVersion.java
- service/adapter-in-rest/src/main/java/ai/speckitty/charter/rest/InfoController.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ServiceInfo.java
- service/charter-read-app/src/main/java/ai/speckitty/charter/read/ServiceInfoQuery.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/ServiceInfoQueryTest.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/ProjectConfigSources.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/ProjectConfigSourcesTest.java
- service/charter-service/src/main/java/ai/speckitty/charter/service/CharterProperties.java
- service/charter-service/src/main/java/ai/speckitty/charter/service/CharterWiring.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/CharterPropertiesTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/CharterWiringTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ScenarioPacks.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ScenarioPacksTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/CucumberSpringConfiguration.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ConfigSteps.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ScenarioHttp.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/CharterSteps.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractConformance.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/bdd/ContractConformanceTest.java
- contracts/charter/scenarios/README.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02: Service identity and fingerprint inputs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- **Has review feedback?**: check the `review_ref` field in the event log (`spec-kitty agent tasks status`).
- Address every feedback item, and record each with `spec-kitty agent tasks add-history WP02 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

This WP covers issue #113, decisions D7 and D8 and part of D11 (`research.md` §3).

1. **`ServiceInfo.serviceVersion`.** A required string with a semantic-version pattern. It is
   the service build's own version, separate from `contractVersion`.
2. **The source fingerprint** digests:
   - every pack file the service loads, as today;
   - `config.yaml` and `charter.yaml` under a configured `charter.project-config-dir`.

   Each configuration file is digested under a fixed, domain-tagged label with a presence
   byte, so an absent file and an empty one differ and no pack name can collide with the
   label. A configured directory that does not exist fails startup. Without a directory, the
   fingerprint covers the packs only.
3. **`/info` checks its digest twice:**
   - `ServiceInfoQuery` digests, then digests again, and refuses with
     `CharterRefusal(STALE_INPUT)` on a difference.
   - `InfoController` sets `Charter-Source-Fingerprint` to the verified digest. It replaces
     the header the filter set earlier, so the response carries **one** value. WP03 removes
     the filter's digest altogether.

**Done when:**

- the new `service.feature` scenarios were seen failing on assertions, then pass;
- the unit tests cover every fingerprint case;
- `verifyAll` is green.

## Context & Constraints

**Read:**

- `spec.md`: User Story 2, FR-009 and FR-010;
- `research.md`: D7, D8, D11 and §8 findings S7 and A6;
- `data-model.md` (Configuration).

**Constraints:**

- **Runner B boundary (C-001):** do **not** edit `FileSystemCharterPackRepository`,
  `PackDescriptor*` or `CharterPack*`. The composite goes in a **new** class that wraps the
  existing `PackSources` (the pack repository is one) and adds the configuration files.
- **Pure read model (C-003):** the configuration files are digested, never parsed. #73 reads
  pack locations from `config.yaml`, and R9 reads activation from `charter.yaml`.
- **CHANGELOG:** do not edit `contracts/charter/CHANGELOG.md` (WP05). List your contract changes
  in a history note.
- **`gradle.properties`:** check the effect of `version=` on artifact names. Run
  `./gradlew :charter-service:bootJar` before and after, and compare. Nothing should
  publish a versioned artifact. Record what changed.
- **ArchUnit:** read-app is plain Java; Spring stays in `charter-service` and `adapter-in-rest`;
  `@NullMarked` packages.

## Branch Strategy

- **Planning base branch**: `feat/action-context-reserved-surface`
- **Merge target branch**: `feat/action-context-reserved-surface`
- Worktrees come from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission action-context-reserved-surface-01M5AFEK
```

**Environment:** `JAVA_HOME=/opt/jdk-25`; open-packs at `/home/user/spec-kitty-open-packs`. On a
429, retry with `--max-workers=1`. Never edit verification metadata to get past a fetch error.

## Subtasks & Detailed Guidance

### Subtask T007: Red scenarios

**First, the shared scenario HTTP seam** (post-tasks findings 3 and 4):

- `ScenarioHttp` is a scenario-scoped bean, `@ScenarioScope` from cucumber-spring. It owns:
  - `perform(method, path, headers, body)`;
  - the last `MvcResult` and its path and method;
  - an optional token that WP04 sets.
- `CharterSteps` moves its `result`, `path` and request sending onto it. **This is a
  refactor**: every existing scenario must pass unchanged before you add anything. Run the suite
  and record it.
- The `@After` conformance check reads the method and path from `ScenarioHttp`.
  `ContractConformance` takes the method, so `"get"` is no longer hardcoded.
- Every glue class (`ConfigSteps` here; WP03's and WP04's later) sends requests and asserts
  responses through `ScenarioHttp`. A `Then` step in `CharterSteps` therefore sees a request a
  `When` step in another class sent.

**Second, the type before the red run** (finding 7): add the `ProjectConfigLocation` supplier
type and the `charter.project-config-dir` property with **no effect** on the fingerprint yet.
The red run is then an assertion failure, not a compile error.

**File:** `contracts/charter/scenarios/service.feature`.

**Scenarios:**

1. **"The service names its build version"** (rewrite the existing `/info` scenario,
   `service.feature:24`). `serviceVersion` is a semantic version, and differs from no
   requirement. Add a step `the field {string} of the response body is a semantic version` in
   the new `ConfigSteps`.
2. **"The source fingerprint follows the project configuration".**
   - Given a project configuration directory with `config.yaml` and `charter.yaml`, when I GET
     `/info` and remember the fingerprint, then edit `charter.yaml`.
   - Then the fingerprint differs.
   - Editing it back gives the remembered one again.
3. **"An absent configuration file and an empty one fingerprint differently".**
4. **"Without a project configuration directory the fingerprint covers the packs only".** Today's
   behavior, kept as a positive control on the same fixture.
5. **"Editing config.yaml changes the fingerprint"**: the same as scenario 2, for `config.yaml`
   (US2-2).

**Test support:**

- **`ScenarioPacks`:**
  - It gains a project configuration directory under its temp root,
    `<root>/.kittify`, created on `reset()`.
  - Configuration is optional per scenario, so the directory always exists, and a step writes
    or deletes files in it.
  - "Without a directory" needs a Spring context without the property. Instead, test that case
    at unit level in `ProjectConfigSourcesTest`, and keep scenario 4 as "a project configuration
    directory without the two files": absence digests deterministically.
- **`CucumberSpringConfiguration`:** supplies the directory as a `@Primary` bean of the new
  supplier type T010 introduces (mirror `PackLocations`).
- **`scenarios/README.md`:** add every new step to the "Step vocabulary" list. WP03 and WP04 add
theirs the same way, out-of-map.

**`ConfigSteps`** (new glue): `Given the project configuration file {string}:`, `Given the
  project configuration file {string} is absent`.

**Red run:** capture the assertion failures and the scenario count in history, then commit.

### Subtask T008: Contract

**`ServiceInfo.yaml`:**

- Add required `serviceVersion`: `type: string`, a semantic-version `pattern` (SemVer 2.0 with an
  optional pre-release, for example `^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?$`)
  and an `x-source` to `ServiceVersion.value`.
- Rewrite `sourceFingerprint`'s description:
  - **the inputs:** pack files under their pack name; `config.yaml` and `charter.yaml` under the
    project configuration directory, with presence;
  - "it names no host path";
  - "adding an input in a later version changes values, not shape";
  - move its `x-source` to `ProjectConfigSources`.
- **Contract sets (D7):** "contractVersion names every operation this service serves; an
  additive contract set is new operations and a minor version".

**`headers/CharterSourceFingerprint.yaml`:** the same inputs. Leave the "on which responses"
sentence for WP03, which rewrites it to "on 200 answers only".

**`examples/ServiceInfo.one-worktree.yaml`:** add `serviceVersion`.

**Checks:** `./gradlew :contracts:check`.

### Subtask T009: The service version resource

**Steps:**

1. Add `version=0.1.0-SNAPSHOT` to `gradle.properties`. The service has not been released, and
   its version is independent of the contract's `1.0.0-SNAPSHOT`.
2. In `service/adapter-in-rest/build.gradle.kts`, mirror the `contractVersionResource` task:
   - a `serviceVersionResource` task writes `service-version.properties` from
     `rootProject.version`;
   - it is configuration-cache safe: a `Property<String>` input, no `project` at execution.
3. `ServiceVersion.java` (new): loads it, mirroring `ContractVersion`, with Javadoc. It fails
   fast with a clear message if the resource is missing.
4. Read-app `ServiceInfo` record: add `serviceVersion`? No. The version is a build fact, not a
   read-model fact. The controller adds it, as it does `contractVersion` (`InfoController:29`).
   Keep read-app unaware.
5. Unit test: `ServiceVersion.value()` matches the semver pattern. Put it in the existing
   `adapter-in-rest` test folder (out-of-map new test file, recorded).

### Subtask T010: `ProjectConfigSources` and the startup check

**`ProjectConfigSources implements PackSources`**, in `adapter-out-yaml`:

- **Constructor:** `(PackSources packs, Supplier<Optional<Path>> configDir)`. The supplier is read
  on every `fingerprint()`, because the scenario root changes every scenario (finding 6).
- **`fingerprint()`:**
  1. start a `Fingerprint.sha256()`;
  2. add the domain tag `charter-sources/v1`;
  3. add `packs.fingerprint()`;
  4. if a directory is configured, for each of `config.yaml` and `charter.yaml` in that fixed
     order, add the label `project-config/<name>` and a presence marker. When present, also
     add the bytes. Use `Fingerprint`'s existing length-prefixed `add`; check its API.
  5. A file that exists but cannot be read →
     `CharterRefusal(PACK_UNREADABLE, "project-config/<name> cannot be read")`. Name no host
     path.

**Unit tests (`ProjectConfigSourcesTest`):**

- an edit to each file changes the digest;
- absent and empty differ;
- no directory = a different but stable digest;
- the label cannot collide with a pack named `project-config`. Prove the domain tag separates
  them by constructing the colliding case.

**`CharterProperties`:**

- Add `@Nullable Path projectConfigDir` (`charter.project-config-dir`).
- Keep the record's existing contract.

**`CharterWiring`:**

- A `ProjectConfigLocation extends Supplier<Optional<Path>>` bean, which a test can override
  with `@Primary`.
- The `PackSources` bean that `ServiceInfoQuery` uses is now the composite.
  `CharterHeaders` uses the same bean through `ServiceInfoQuery`.
- **Startup check:** check the **property value**, not the supplier. A configured
  `charter.project-config-dir` that does not exist (or is not a directory) fails context startup
  with an `IllegalStateException` naming the property, not the path's contents. The scenario
  suite overrides the supplier and does not set the property.
- **Bean ambiguity:** `FileSystemCharterPackRepository` already implements `PackSources`, so
  inject the composite by its own type (`ProjectConfigSources`), or mark it `@Primary`. Prove it
  with `ProductionWiringTest` still green.

**Wiring tests (`CharterWiringTest` or `CharterPropertiesTest`):** the property binds; a missing
directory fails startup.

### Subtask T011: Two-digest `/info`

**`ServiceInfoQuery.answer()`:**

- `first = sources.fingerprint(); second = sources.fingerprint();` and when they differ,
  `throw new CharterRefusal(STALE_INPUT, "The inputs changed while the service info was read; read it again.")`.
- The `ServiceInfo` record carries the verified digest.
- Javadoc: the refusal and its retry class.

**`InfoController`:**

- Returns a `ResponseEntity` with `serviceVersion` in the body.
- Sets `Charter-Source-Fingerprint` with `headers.set` (replace, not add) to
  `info.sourceFingerprint()`.

**Unit test (`ServiceInfoQueryTest`):** a `PackSources` stub returning two different values
refuses `STALE_INPUT`; equal values answer. This unit test is honest: it tests the comparison.
WP03's scenario tests the detection against a real file.

**Final run:**

```bash
./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs
```

## Red-first evidence (required)

In history:

- the red run of T007 (assertion failures, scenario count);
- the green run;
- both commit hashes.

## Definition of Done

- [ ] T007 to T011 done. The scenarios were seen red, then green.
- [ ] `verifyAll` green. No runner B file touched. No CHANGELOG edit.
- [ ] History lists the contract changes and every out-of-map file with its rationale.

## Risks

- **A token or path in error messages:** never name a host path in a refusal detail.
- **Two `Charter-Source-Fingerprint` values:** in Spring 7, a `ResponseEntity` header replaces
  the filter's value (`ServletResponseHeadersAdapter.put` removes, then adds). The step that
  counts values is a regression guard, not red evidence.
- **`gradle.properties` `version`:** it may change jar file names that CI or the docsite rely on.
  Check the workflows (`.github/workflows/*.yml`) for jar names.

## Reviewer guidance

Check:

- the composite lives outside runner B's class;
- labels are domain-tagged and presence-aware;
- the missing directory fails startup;
- exactly one fingerprint header on `/info`;
- `serviceVersion` has a pattern;
- the red-first evidence is real.
