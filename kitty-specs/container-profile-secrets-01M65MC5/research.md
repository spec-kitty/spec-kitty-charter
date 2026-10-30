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

## R10: Plan-point adversarial squad (architecture and security lenses)

Two read-only lenses challenged the plan before tasks:

- `architect-alphonso`;
- `reviewer-renata`, focused on secrets.

Each finding is listed with its disposition.

| # | Finding | Lens | Severity | Disposition |
|---|---------|------|----------|-------------|
| 1 | Compose's `env_file` turns `KEY=` into an empty env var. Env vars outrank the config tree, so an empty `CHARTER_WORKTREE_TOKEN` hides a mounted token, and the start fails as "blank". An empty `CHARTER_CONTAINER_SECRETS_DIR` makes the import `configtree:/`. | both | blocker | **Folded.** `.env.example` holds the secret keys only as comments. `CHARTER_CONTAINER_SECRETS_DIR` is not in it. A test refuses a live secret key in it. Under the container profile, the blank refusal says an empty env var hides the file, and a start test pins it. |
| 2 | A short-syntax bind mount (`./secrets:/run/secrets:ro`) creates a missing host directory, so the required import passes. | both | major | **Folded.** The compose example uses a long-syntax bind with `read_only: true` and `bind.create_host_path: false`, and a test asserts it. Top-level `secrets:` was weighed: it checks each file, not the directory, and needs a `target:` per name, so it was not chosen. The quickstart creates the token under `umask 077`. |
| 3 | Any file in the secrets directory sets any property: `server.address`, `charter.config-root`, `logging.*`, `spring.config.import`. | security | major | **Folded (FR-010).** Under the container profile, the start-up check lists the property names of every config-tree source. It refuses any name other than `charter.worktree-token` or `charter.container.*`, naming the file and never its content. **Open review item:** this is narrower than ADR-0041 decision 3 states, and a future secret, such as a TLS key-store password, will need adding to the list. |
| 4 | The "no secret committed" check was trivially passable. | security | major | **Folded (FR-008).** It covers four things: tracked `.env*` files other than `.env.example`, and tracked files under a `secrets/` directory; flattened YAML, env and compose keys whose normalized name is the token's, with a non-empty value that is not a `${…}` reference; secret-shaped scalars (32 or more characters from a token alphabet); and every `application*.yaml` on the main resources path. Each form has a planted positive control. |
| 5 | The spec and plan disagree on the import literal. | architecture | major | **Folded.** One form: `configtree:${charter.container.secrets-dir:/run/secrets}/`. A value set for the key takes no trailing slash. |
| 6 | Shipped-start tests could pass for the wrong reason: a builder token hides the file, a `MapPropertySource` stand-in never tests env-name mapping, and "start fails" may fail for another reason. | architecture | major | **Folded.** A token-free helper. The token is proven by a request through the registered guard. Env vars come from a `StandardEnvironment` whose `getSystemEnvironment()` is overridden, so the env-name mapping, `CHARTER_CONTAINER_SECRETS_DIR` included, is exercised when the import resolves. Each failure asserts its exception type and the path or key it names. |
| 7 | A secrets directory that others can write lets any process plant a token. Messages hard-code `/run/secrets`. | both | minor | **Folded (FR-010).** A blank, relative, root or others-writable secrets directory fails the start. Messages name the resolved directory. **Residual:** the check runs after the import, so a `configtree:/` import is read before it is refused. The key is kept out of `.env.example` and documented. |
| 8 | Boot can print secret values: `PropertyFileContent.toString()` is the secret, and failure analyzers print `Value:`. | security | minor | **Folded (NFR-004).** Failing-start tests assert that the captured output and the message do not contain the test token, with one positive control. The token never becomes a record component. The how-to warns against running `docker compose config` with a real `.env`. |
| 9 | Only one trailing newline is trimmed, so trailing spaces lock an operator out. | security | minor | **Documented** in the how-to. No new refusal. |
| 10 | The env form of the secrets directory is `CHARTER_CONTAINER_SECRETS_DIR`; `…_SECRETSDIR` is not resolved when the import resolves. `secrets-dir` sits in the record's namespace without being a component. | both | minor | **Folded.** A test pins the env form. The ADR note records it. The configuration metadata for the key is #230's (the configuration processor). |
| 11 | `CHARTER_CONFIGROOT` outranks `--configRootLocation`, which only feeds the yaml placeholder. | architecture | minor | **Documented** in the data model. Handed to #234: set the image default in the profile file as `${configRootLocation:<mount>}`, not as an image `ENV`. |
| 12 | The `.gitignore` coverage was too narrow. #234's image needs a `.dockerignore`. | security | minor | **Folded.** `.env`, `.env.*` and `!.env.example` repository-wide, plus `deploy/compose/secrets/`. The `.dockerignore` is noted for #234 in the changelog. |
| 13 | "Byte-for-byte" in SC-004 does not match a content test. | architecture | minor | **Folded.** SC-004 now states the content assertion. |
| 14 | No ADR violation. The placeholder is a sound reading of decision 3 and goes in as a dated note under it. | architecture | — | Accepted. |

## R11: Pre-PR adversarial squad (architecture, security, tests and gates)

Three read-only lenses reviewed the consolidated branch:

- `architect-alphonso`: approve with changes;
- `reviewer-renata`, focused on security: changes requested;
- `qa-strategist-quinn`: not ready.

None found a blocker. `verifyAll` was green before the folds.

**Operator decision (2026-10-30).** Ship the allowlist, hardened, and drop the permission check. Two findings
triggered the decision:

- Kubernetes secret volumes mount as `1777`, read-only, so the permission check would refuse every default Kubernetes
  start.
- Kubernetes mounts its service-account tree under `/var/run/secrets/kubernetes.io`.

| # | Finding | Lens | Severity | Disposition |
|---|---------|------|----------|-------------|
| 1 | The permission check refuses Kubernetes secret volumes. It checks only the top directory, and a planted `charter.container.secrets-dir` file redirects what it inspects. | security, architecture | major | **Folded (operator):** the permission check is removed, and file permissions are left to the platform. |
| 2 | The allowlist trips on Kubernetes' service-account tree. The message says "secrets directory" for any config tree. | architecture | major | **Folded:** `kubernetes.io.*` names are ignored, and the message says "the config tree <path>". |
| 3 | A config tree could set `charter.container.secrets-dir`. | security | major | **Folded:** it is refused from a tree. |
| 4 | Lazy initialization could skip the check. | security | minor | **Folded:** a `LazyInitializationExcludeFilter` keeps it eager, and a test pins this. |
| 5 | A PR touching only `deploy/`, `.gitignore` or `.env*` skips the service job. | tests | major | **Folded:** `ci.yml` `service_re` and the `service.yml` push paths include them. |
| 6 | `.git/index` as a test input defeats the build cache. | tests | major | **Folded:** the input is the output of `git ls-files`, which is content, not stat data. |
| 7 | The scan flags long paths and image digests. It misses commented secrets in the template. Quotes skew it. | tests | minor | **Folded:** the token alphabet has no `/`, letters and digits are both required, `sha256:` digests are exempt, commented `KEY=value` lines in the template are scanned, and values are unquoted. Each has a control. |
| 8 | The tracked-path rule refuses any `/secrets/` path, and a stray `charter.worktree-token` file is missed. | tests, security | minor | **Folded:** the rule covers `deploy/**/secrets/`, `.env*` anywhere, and a file named `charter.worktree-token` anywhere. |
| 9 | Unit tests read the host's `/run/secrets`. | tests | minor | **Folded:** `container()` names a test directory. |
| 10 | Git errors cannot be diagnosed. | tests | minor | **Folded:** stderr goes into the message. |
| 11 | The secrets-dir constants have the wrong owner. | architecture | minor | **Folded:** they now live in `ContainerProperties`, documented as in the namespace but not a component. |
| 12 | The trailing-slash wording disagrees with the code. | architecture | minor | **Folded:** the docs say a trailing slash is not needed. |
| 13 | The how-to leaves the image unable to read the files, and the reason to prefer files is not given. | security, architecture | minor | **Folded:** `chown`/`chmod 0440` guidance, a warning against widening, and `docker inspect` exposure. |
| 14 | The command line may name the secrets directory. | security | minor | **Accepted:** a path is not a secret, and the ADR note says so. |
| 15 | `secrets-dir` has no property metadata. | architecture | minor | **Handed to #230** (configuration processor). |
| 16 | `ShippedConfigurationTest` needs a `.git` checkout, so a build from a source archive fails. | architecture | minor | **Kept, failing loudly** (the charter allows no skip). Noted for #234's image build, which should not run the tests from an archive. |
| 17 | SC-001's default-path start is proven by the content pin plus an overridden-path start (analysis I1). | tests | info | **Stated in the PR.** |
