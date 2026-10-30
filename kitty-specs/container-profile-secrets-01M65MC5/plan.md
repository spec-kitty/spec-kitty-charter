# Implementation Plan: Container profile file and secret import

**Branch**: `issue-229-hosted-config-secrets` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/container-profile-secrets-01M65MC5/spec.md`

## Summary

ADR-0041 decision 3 says a containerized service takes its secrets only from mounted files or env vars. This mission
delivers that for the `container` profile:

- `service/deployable/src/main/resources/application-container.yaml` holds only non-secret defaults: `server.address:
  0.0.0.0` and `server.port: 8080`. It also holds the required import
  `configtree:${charter.container.secrets-dir:/run/secrets}/`, with no `optional:` prefix. The path placeholder is
  the operator's decision `01M65MGKX68G0EGHV2AETWWKNX`.
- Under the container profile, `ContainerStartupCheck` refuses two things (FR-010, research R10):
  - a config-tree file that names a property other than `charter.worktree-token` or `charter.container.*`;
  - a secrets directory that is blank, relative, the root, or writable by others.
- When the token is missing or comes from a refused source, `GuardWiring`'s refusals under the container profile name
  both `CHARTER_WORKTREE_TOKEN` and `/run/secrets/charter.worktree-token`, and stop offering the development flag. The
  local messages are unchanged.
- `deploy/compose/compose.yaml` and `deploy/compose/.env.example` show the setup:
  - the image comes from `${CHARTER_IMAGE:?…}`;
  - the env file is read;
  - `./secrets` is bind-mounted read-only at `/run/secrets` (long syntax, `create_host_path: false`, so a missing
    directory fails);
  - a named volume holds the configuration root.

  `.gitignore` ignores `.env` and `.env.*` (except `.env.example`) everywhere, and `deploy/compose/secrets/`.
- Tests prove four things:
  - the shipped profile binds from files and from env vars;
  - a missing secrets directory fails the start;
  - the shipped files hold no secret;
  - the local `application.yaml` is unchanged.
- Docs:
  - a changelog entry;
  - a note in ADR-0041 decision 3 clarifying the placeholder;
  - the service-lifecycle page (stale "proposed" status fixed, container profile described);
  - a how-to for running under compose.

## Technical Context

**Language/Version**: Java 25 (CI); Spring Boot 4.1.1
**Primary Dependencies**: none new. The tests use Spring Boot config data (`configtree:`) and `snakeyaml-engine`, which
is already a test dependency of the deployable.
**Storage**: N/A. The secrets directory and the configuration root are mounted by the platform.
**Testing**:
- JUnit 5 with AssertJ.
- Start tests use `SpringApplicationBuilder` runs of the shipped `CharterServiceApplication` (`WebApplicationType.NONE`).
  `ApplicationContextRunner` does no config-data processing, so it cannot exercise the import.
- File tests parse the committed YAML and env files.
- `git check-ignore` decides the ignore rules.
- The guard message tests stay on `ApplicationContextRunner` (`GuardWiringTest`).

**Target Platform**: the charter service, either as a local loopback daemon (unchanged) or as a container under the
`container` profile.
**Project Type**: single (Gradle multi-module). The changes are in `service/deployable`, `deploy/compose`,
`.gitignore` and `docs`.
**Performance Goals**: N/A. Secrets are read once at start.
**Constraints**:
- zero new suppressions;
- complexity of at most 15;
- no new dependency;
- no secret value in any committed file, message or log;
- the local path is unchanged.

**Scale/Scope**:
- 1 resource file;
- 1 production class changed;
- 2 new test classes;
- 2 test classes changed;
- 2 compose files;
- 1 build-file addition (the test JVM receives the repository paths);
- 4 docs pages.

## Charter Check

- **No unverified dependency:** passes. Nothing is added, and `verification-metadata.xml` is untouched.
- **Contract not broken:** passes. No HTTP or contract change is made. The guard's `Host` check is untouched (C-002).
- **Adversarial squad at plan and before merge:** this is a configuration and secrets change, which is
  architecture-adjacent.
  - A plan-point squad of two lenses (architect, security-minded reviewer) challenges this plan before tasks.
  - The pre-PR squad runs before the draft PR.
- **Calls made without review are logged:**
  - The placeholder seam was the operator's call (decision record).
  - No `config-root` default (research R3) and the compose layout (research R8) are logged in `research.md` and in
    the changelog.
- **Test first:** every work package writes its failing tests before the change. The positive controls are named in
  the spec's no-op column.
- **Living documentation:** the ADR note, the service-lifecycle page, the changelog and the how-to land with the code.

## Project Structure

### Documentation (this mission)

```
kitty-specs/container-profile-secrets-01M65MC5/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md (+ tasks/)
```

### Source Code (repository root)

```
service/deployable/
├── build.gradle.kts                                    (test JVM gets the compose dir and .gitignore as inputs)
└── src/
    ├── main/java/ai/speckitty/charter/deployable/GuardWiring.java   (container-profile token messages)
    ├── main/java/ai/speckitty/charter/deployable/ContainerStartupCheck.java (config-tree keys, secrets directory)
    ├── main/resources/application-container.yaml                    (new)
    └── test/java/ai/speckitty/charter/deployable/
        ├── ContainerSecretsTest.java                   (new: shipped-profile starts, files and env)
        ├── ShippedConfigurationTest.java               (new: committed files, no secret, local unchanged)
        ├── ContainerPropertiesTest.java                (port-0 test made explicit; shipped starts get a secrets dir)
        ├── ContainerStartupCheckTest.java              (config-tree keys, secrets directory)
        └── GuardWiringTest.java                        (container-profile messages, local unchanged)
deploy/compose/
├── compose.yaml                                        (new)
└── .env.example                                        (new)
.gitignore                                              (.env and secrets/ under deploy/compose)
docs/
├── adr/0041-containerized-configuration-model.md       (decision 3: clarification note)
├── architecture/service-lifecycle.md                   (stale status; container profile)
├── guides/run-in-a-container.md + guides/README.md     (new how-to, linked from the hub)
└── changelog/2026-10-30-container-profile-secrets.md + changelog/README.md
```

## Complexity Tracking

No violations.

## Implementation Concern Map

### IC-01: Profile file and required secrets import

- **Purpose**: load non-secret defaults and the required config-tree import under the `container` profile only.
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-004, FR-009, C-001, C-003
- **Squad folds**: research R10, rows 1, 5, 6 and 10
- **Affected surfaces**:
  - `application-container.yaml`;
  - `ContainerSecretsTest`;
  - `ContainerPropertiesTest` (shipped starts need a secrets dir; the port-0 test passes `--server.port=0`
    explicitly).
- **Sequencing/depends-on**: none
- **Risks**:
  - The location must end in `/` for `configtree:`. The yaml appends the slash after the placeholder, so the key holds
    a plain directory path.
  - The env-var form of the key is `CHARTER_CONTAINER_SECRETS_DIR` (placeholder resolution maps dots and dashes to
    underscores).
  - Under Spring's order, env vars outrank imported config data. A secret given both ways takes the env var; this is
    documented.
  - The default-path failure depends on `/run/secrets/` not existing on the test machine. So the default is pinned by
    reading the committed file, and the failure is proven with an override that names a missing directory. No test is
    skipped conditionally.

### IC-02: Container-profile token messages

- **Purpose**: under the container profile, name both token sources in the two refusals (ADR-0041 decision 3, handed
  over from #228).
- **Relevant requirements**: FR-005, NFR-004
- **Affected surfaces**: `GuardWiring`, `GuardWiringTest`
- **Sequencing/depends-on**: none
- **Risks**:
  - The profile is read through `Environment.matchesProfiles`; the packs module does not learn it (C-001).
  - The message never carries the token.

### IC-05: The config tree sets only its own keys

- **Purpose**: under the container profile, a secrets mount cannot reconfigure the service. The secrets directory must
  be safe.
- **Relevant requirements**: FR-010, NFR-004
- **Affected surfaces**: `ContainerStartupCheck`, `ContainerStartupCheckTest`, `ContainerSecretsTest`
- **Sequencing/depends-on**: IC-01
- **Risks**:
  - Config-tree sources are found by type (`ConfigTreePropertySource`), not by name.
  - Messages name the file and never the content.
  - The allowlist is narrower than ADR-0041 decision 3, so it is an open review item for the operator.

### IC-03: Compose example, env template and ignore rules

- **Purpose**: show a compose setup that commits no secret, and check it.
- **Relevant requirements**: FR-006, FR-007, FR-008
- **Affected surfaces**:
  - `deploy/compose/compose.yaml` and `deploy/compose/.env.example`;
  - `.gitignore`;
  - `ShippedConfigurationTest`;
  - the deployable build file (test inputs).
- **Sequencing/depends-on**: IC-01 (the profile and keys it names)
- **Risks**:
  - CI's change routing does not cover `deploy/`. The test reads the files as declared inputs of the deployable test
    task, so a change to them reruns the check.
  - `docker compose config` validates the file locally; CI has no compose step.
  - The image does not exist until #234.

### IC-04: Living docs

- **Purpose**:
  - a clarification note in ADR-0041 decision 3 (the placeholder, the env-var form, the trailing slash);
  - the service-lifecycle page brought current;
  - a how-to for compose;
  - a changelog entry naming the follow-ups (#230, #231, #232, #234, the guard reading `allowed-hosts`).
- **Relevant requirements**: SC-005
- **Affected surfaces**: `docs/adr/0041-…`, `docs/architecture/service-lifecycle.md`, `docs/guides/`, `docs/changelog/`
- **Sequencing/depends-on**: IC-01 to IC-03
- **Risks**:
  - `check_docs.py` checks: no "feature", 50-to-180-character descriptions, `related:` slugs that resolve, the hub
    link.
  - cspell: put `configtree` in code spans.
  - `docs/changelog/README.md` is the hottest file, so a rebase conflict is likely.
