# Research: Container profile file and secret import

A read-only research squad ran before the spec, with three lenses, each loaded with its profile:

- `researcher-robbie`: files in scope, contracts and test seams.
- `architect-alphonso`: invariants and governing ADRs.
- `qa-strategist-quinn`: gates and churn.

Their findings are folded in below. Spring Boot is 4.1.1 (`gradle/libs.versions.toml:4`). Baseline: `origin/main` at `feeff0a`.

## R1: Naming follows ADR-0041 and PR #266, not the issue text

- **Finding**: issue #229 was written before ADR-0041 was accepted. It says `application-hosted.yaml` and `charter.hosted.*`. ADR-0041 decides otherwise:
  - The profile is `container` and the file is `application-container.yaml` (decision 1, `docs/adr/0041-containerized-configuration-model.md:83-86`).
  - The namespace is `charter.container.*` (decision 2, `:88-90`).
  - The ADR rejects the name `hosted` explicitly ("Alternatives considered: Naming the profile `hosted`", `:219-222`).

  PR #266 (#228) landed `ContainerProperties.PROFILE = "container"` (`ContainerProperties.java:30`), and its spec set the same precedent: follow the ADR over the issue text (`kitty-specs/container-properties-record-01M64FVJ/spec.md:20-22`).
- **Decision**: align to the ADR and to what merged. This needs no operator decision. "Hosted" stays the name of the ADR-0034 deployment option.

## R2: Work handed to #229 by the #228 mission

The #228 decision `DM-01M64FW0PR3KV90HJ3S8K4201P`, its `research.md:18` and `docs/changelog/2026-10-29-container-properties-record.md:46-47` hand #229 three items:

- `application-container.yaml`;
- the required secret import;
- the **ADR-0041 decision 3 token message**: under the container profile, the missing-token refusal names both the env var `CHARTER_WORKTREE_TOKEN` and the mounted file `/run/secrets/charter.worktree-token`.

Today both refusals name only the env var:

- the missing-token refusal (`GuardWiring.java:93-96`);
- the wrong-source refusal (`unreadableSourceRefusal`, `:135-138`).

The #228 spec (`spec.md:121-122`) also notes that until #229 lands, a container has to set `SERVER_PORT` itself. Decision 4's default port, 8080, belongs here.

Explicitly **not** #229's work:

- the non-loopback bind refusal without the profile (decision 4) → #231;
- the guard reading `allowed-hosts` (decision 6) → follow-up;
- actuator and health (decision 7) → #232;
- the image and its mount path (decision 5's default) → #234;
- the property reference table → #230.

## R3: What `application-container.yaml` may and may not hold (ADR-0041 decisions 1 to 5 and 8)

- **May hold**:
  - `spring.config.import` of the config tree, with no `optional:` prefix;
  - `server.address: 0.0.0.0`;
  - `server.port: 8080`;
  - comments on how secret files are named.
- **Must not hold**:
  - any secret value, including a placeholder token;
  - `charter.container.allowed-hosts`: required, depends on the deployment, no default;
  - a `charter.config-root` default: the image mount path is #234's to fix;
  - `charter.insecure-no-token` or `charter.project-config-dir`;
  - `spring.profiles.*`: the profile is never a default.
- **Consequence for `charter.config-root`**: with no default in the profile file, the root inherited from `application.yaml` (`${user.home}/.spec-kitty/charter`) does not exist in a container. `ContainerStartupCheck` then fails the start until a root is mounted, which is the fail-closed outcome decision 5 wants.

## R4: How the token reaches the guard, and why a config tree is accepted

- `GuardWiring` reads `charter.worktree-token` straight from the `Environment` (`GuardWiring.java:91`). It refuses the token only when it appears in property sources named `commandLineArgs`, `springApplicationCommandLineArgs` or `systemProperties`, or in `spring.application.json` taken from one of those (`:54-55`, `:116-133`).
- A config-tree property source is not on that list, so a token read from `/run/secrets/charter.worktree-token` is accepted. A token from the environment variable `CHARTER_WORKTREE_TOKEN` is accepted too.
- Mounted files are named in dot form. A file named in the env-var style (`CHARTER_WORKTREE_TOKEN`) maps to no property (decision 3); a test pins that.
- The token stays in `WorktreeToken`, never as a `String` component of a record (decision 8), and is never put in a message.

## R5: The required import needs a test seam (operator decision)

- **Finding**:
  - Three tests in `ContainerPropertiesTest` start the shipped `CharterServiceApplication` under the container profile (`:92-130`, `shippedService`). After this change they load `application-container.yaml`.
  - A required `configtree:/run/secrets/` fails every such start on a machine without `/run/secrets/`, including the CI runner.
  - A `spring.config.import` given on the command line adds an import; it does not replace the yaml's.
  - `ApplicationContextRunner` does no ConfigData processing, so only a `SpringApplicationBuilder` start exercises the import.
  - Spring Boot resolves `${…}` placeholders in import locations against the sources already loaded: command line, system properties and environment.
- **Options**:
  1. A placeholder that defaults to the ADR path, `configtree:${…:/run/secrets/}`, still with no `optional:` prefix. A test points it at a temporary directory. The import stays required, but the path becomes overridable, which is a reading of decision 3's literal path.
  2. Keep the literal path, and remove or weaken every positive start of the shipped container profile. That leaves the "values bind into the record" acceptance criterion unproven against the shipped file.
- **Decision**: put to the operator; see the decision record in this mission.

## R6: Existing tests this change touches

`ContainerPropertiesTest.theServiceAsItShipsRefusesItsLocalRandomPortUnderTheContainerProfile` (`:116-121`) relies on the profile inheriting the local port, 0. Once the profile file sets 8080, the test is rewritten as an outcome test (the shipped container profile listens on 8080 and a port of 0 is still refused). It is not deleted.

## R7: Gates touching the surface and how to keep them green honestly

- **Java** (`build-logic/.../charter.hand-written.gradle.kts`):
  - javac `-Xlint:all -Werror`;
  - Spotless (palantir) on main and test sources;
  - Checkstyle with maxWarnings and maxErrors at 0, and `suppressions.xml` at 0 entries, which stays at 0;
  - Error Prone;
  - NullAway (main code);
  - doclint `-Xdoclint:all -Werror` on main code.
- **No dependency is added**: `snakeyaml-engine` is already a test dependency of the deployable (`build.gradle.kts:20`), so `verification-metadata.xml` is unchanged.
- **Docs** (`scripts/docs/check_docs.py`, all eight checks):
  - frontmatter, with descriptions of 50 to 180 characters and resolvable audiences;
  - `related:` slugs must resolve;
  - freshness: no future `updated`;
  - links, and terms: no "feature";
  - changelog naming, under 3000 characters, listed newest first in `docs/changelog/README.md`;
  - orphans.

  Also cspell (put `configtree` in backticks, or add it to `docs/_meta/spelling-words.txt`).
- **Not covered by any check**:
  - CI's change routing (`.github/workflows/ci.yml:50-51`) covers no root-level file or new `deploy/` directory.
  - Nothing scans the repository for secrets (no gitleaks), and nothing checks `application*.yaml` content.

  The tests in this mission close those gaps for the files they own: the profile file, `.env.example`, the compose example and `.gitignore`.

## R8: Where the compose example and `.env.example` go

- The repository has no Dockerfile, compose file, `.dockerignore` or examples directory, and #234 (the image) has not landed.
- **Decision**: `deploy/compose/compose.yaml`, with `.env.example` beside it. Compose reads `.env` from the project directory.
  - The image is `${CHARTER_IMAGE:?…}`, which fails fast; no tag is invented.
  - `.env` is ignored by a commented section of its own in `.gitignore`, kept outside the block Spec Kitty manages.
  - A deployable test reads the compose file and `.env.example`, so the CI service group covers them.

## R9: 90-day churn and collisions

- `service/deployable/`: 26 commits; `application.yaml`: 3.
- `.gitignore`: 6.
- `docs/architecture/service-lifecycle.md`: 17. Lines 29–30 still call ADR-0041 "proposed", which is stale; fix it (boy-scout).
- `docs/changelog/README.md`: 53, the hottest file. Expect a top-line rebase conflict.
- Open pull requests: none on these paths. Siblings #260 and #194 PR-B work on the packs surface.
