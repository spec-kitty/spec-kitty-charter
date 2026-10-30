---
title: "ADR-0041: The containerized service is configured through a container profile, validated properties and mounted secrets"
description: A container profile that is never the default; validated properties fail the start; secrets only from env or /run/secrets; Spring Cloud Config rejected.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
  - adr/0033-local-active-charter-is-source-of-truth
  - adr/0034-local-and-hosted-deployment-options
  - adr/0038-one-configuration-root-a-directory-per-domain
  - architecture/service-lifecycle
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a property, a profile, a secret or an actuator endpoint to the deployable.
  - profile: governance-lead
    guidance_text: Read this for how a team runs the charter service in a container, where its secrets go, and why a configuration change is a redeploy.
  - profile: core-maintainer
    guidance_text: Read this for what changes for the local daemon (nothing) and which token a containerized service accepts until the login flow is decided.
---

# ADR-0041: The containerized service is configured through a container profile, validated properties and mounted secrets

## Status

**Accepted** — 2026-10-29 (proposed 2026-10-28), for [#227](https://github.com/spec-kitty/spec-kitty-charter/issues/227)
under epic [#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226). Decider: Stijn
Dejongh (operator). Written from the epic's accepted architect assessment and the operator
rulings of 2026-10-27 on #226.

Resolves in part [ADR-0034](/adr/0034-local-and-hosted-deployment-options/) decision 5: the
configuration keys of the containerized service. The login flow and the token format stay open.
Builds on [ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) (one configuration
root).

Supersedes in part, under the container profile only (the local option is unchanged):

- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) decision 9: the `Host` check
  compares against a configured list instead of the loopback names, and `GET /actuator/health`
  needs no token (decisions 6 and 7 below).
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) decision 2, "every request
  carries a per-worktree token": the health path is the one exception (decision 7).
- [ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/) decision 4: a configuration
  root that does not exist fails the start instead of serving no packs (decision 5).

## Context

ADR-0034 gives the service a local and a hosted deployment option and leaves configuration keys,
the login flow and the token format to later records (decision 5). Today the deployable has one
configuration: `application.yaml` binds `127.0.0.1` on port 0, `CharterProperties`
(`@ConfigurationProperties("charter")`, enabled in `CharterServiceApplication`) carries the
worktree id and the project configuration directory, and `StorageProperties` in the packs module
carries `charter.config-root`. `GuardWiring` requires `charter.worktree-token` (at least 32
characters, refused from the command line or a system property), and `WorktreeGuard` answers 403
to any `Host` other than a loopback name. Actuator, bean validation and the configuration
processor are not dependencies yet.

The operator ruled on 2026-10-27 (#226, the later of the two comments that day is in force):

- **Platform:** a platform-neutral container, configured only through env vars and mounted
  secrets, proven with docker compose. It must run unchanged on Kubernetes or a PaaS later. No
  platform-specific manifests now.
- **Tenancy:** undecided. [#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233)
  designs for one team per deployment without ruling out more later. The registration endpoints
  for packs and for projects can be shared, so one instance may serve many projects.
- **Settings without a redeploy:** none. Configuration is immutable per release and deployment.
  Packs and activations are domain state changed through the API, not configuration.
- **Container image:** [#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234), with
  its own release tags and SemVer, recorded in a separate ADR.

The epic called this profile "hosted" and left its name to this record. A container is how the
hosted option is served, but the profile describes how the process is configured, not who calls
it: a developer can run the same container on their own machine.

## Decision

1. **Two profiles; the container profile is never the default.**
   - The local configuration stays as it is: `application.yaml` binds `127.0.0.1` on port 0, and
     the launcher of ADR-0011 starts it per worktree.
   - The profile for a containerized service is named **`container`**. It is activated only
     explicitly (`SPRING_PROFILES_ACTIVE=container`), never through `spring.profiles.default` or a
     default in the image's code. `application-container.yaml` holds non-secret defaults only.
   - "Hosted" stays the name of the deployment option (ADR-0034); `container` is the name of the
     profile that serves it.
2. **Validated properties fail the start.** The container profile adds a separate
   `@ConfigurationProperties` record bound at `charter.container.*`, annotated `@Validated`, in the
   deployable module, beside `CharterProperties`. The packs module does not learn the profile. A
   missing or invalid value fails the start with a message that names the property and never a
   secret value. The profile's rules on keys outside that namespace (`server.*`,
   `charter.config-root`, `charter.worktree-token`, `charter.insecure-no-token`,
   `charter.project-config-dir`; decisions 4 to 6) are checked by a start-up validator in the
   deployable, and the guard receives its allowed hosts through its constructor from
   `GuardWiring`. This adds `spring-boot-starter-validation` to the build; `spring-boot-configuration-processor`
   generates the property metadata that the docsite reference table is checked against
   ([#230](https://github.com/spec-kitty/spec-kitty-charter/issues/230)). Both enter the build under
   strict SHA-256 dependency verification.
3. **Secrets come only from env vars or mounted files.**
   - `application-container.yaml` sets `spring.config.import=configtree:/run/secrets/`, without the
     `optional:` prefix: a container started without the mount fails the start. An empty mounted
     directory is valid when every secret comes from env vars.
   - A secret file is named after its property in dot form, for example
     `/run/secrets/charter.worktree-token`. The env-var form of the same property
     (`CHARTER_WORKTREE_TOKEN`) is the alternative. A file named in the env-var style, with
     underscores, is not mapped to the property. The token refusal message, which names only the
     env var today, names both forms under the container profile.
   - docker compose reads an env file. Only `.env.example`, with names and no values, is committed;
     `.env` is ignored by git.
   - No committed YAML file holds a secret value. The existing refusal of the command line and of
     system properties as token sources stays in force under both profiles.
   - Mounted files are read once at the start. A rotated secret takes effect on the next start.
   - **Clarified 2026-10-30** ([#229](https://github.com/spec-kitty/spec-kitty-charter/issues/229), operator
     decisions):
     - **Import and placeholder.** The import reads `configtree:${charter.container.secrets-dir:/run/secrets}/`. It
       is still required, and its default is the path above. Tests point it at a temporary directory, and an operator
       may name another absolute directory, the root excepted. The env form is `CHARTER_CONTAINER_SECRETS_DIR`, and a
       trailing slash is not needed.
     - **The command line may name the directory.** A path is not a secret, so this needs no refusal.
     - **Allowlist.** Under the container profile, a config tree may set only `charter.worktree-token` or a
       `charter.container.*` key, other than the secrets directory itself.
     - **Kubernetes tree.** The service-account tree Kubernetes mounts (`kubernetes.io/…`) is left alone.
     - **Refusal.** Any other file fails the start, naming the file and never its content.
     - **File permissions.** These are left to the platform: a check would refuse Kubernetes' secret volumes.
4. **The bind address follows the profile.**
   - Without the container profile, `server.address` must be a loopback address. A non-loopback
     bind without the container profile fails the start.
   - The container profile binds all interfaces (`server.address` unset, or `0.0.0.0`; IPv6 is
     not excluded) on a fixed port, 8080 by default. Port 0
     fails the start under the container profile, because a random port cannot be published.
     Binding loopback inside a container stays allowed (a sidecar, a test).
5. **The configuration root is required and must exist under the container profile.** ADR-0038
   decision 4 lets a missing root serve no packs. Inside a container the local default
   (`${user.home}/.spec-kitty/charter`) is the image user's home, not a volume, so a forgotten
   mount would give a running, empty service. Under the container profile `charter.config-root`
   must be absolute and must name an existing directory; otherwise the start fails. Its default
   there is the path where the image mounts its volume, which #234 fixes;
   `--configRootLocation` and any other property source still override it (ADR-0038 decision 1).
   The local behavior of ADR-0038 is unchanged.
6. **The request guard has a container form.** `WorktreeGuard`'s loopback `Host` check would refuse
   every request that reaches a container by a real host name.
   - Under the container profile the `Host` check compares against
     `charter.container.allowed-hosts`, a required, non-empty list. It still stops DNS rebinding.
   - The browser-header refusal (`Origin`, `Sec-Fetch-Site`, `Sec-Fetch-Mode`) stays as an
     interim. ADR-0020 D12 moves this guard into the local profile and gives a hosted deployment a
     CORS policy; that reshape is not part of this record.
   - The contract describes the 403 for a `Host` that is not a loopback name
     (`contracts/charter/openapi.yaml`, `responses/Forbidden.yaml`). This record does not change the
     contract. The change that builds the container guard owes a contract description that covers
     the configured list, as a 0.x minor (ADR-0022), or lands with the ADR-0020 D12 reshape.
   - Until the login flow is decided (ADR-0034 decision 5, ADR-0020 D12), the container profile
     uses the existing static bearer: `charter.worktree-token`, delivered as a mounted secret or an
     env var, with the same length rule. `charter.insecure-no-token` fails the start under the
     container profile.
   - `charter.project-config-dir` fails the start under the container profile: there is no single
     worktree `.kittify` inside a shared container. `charter.worktree-id` names the instance until
     ADR-0020 D12 renames the header.
7. **Minimal actuator exposure.** When actuator is added
   ([#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232)), only `health` is exposed
   over HTTP, with `show-details` and `show-components` set to `never`, so it reports up or down
   and nothing else. The guard will exempt `GET /actuator/health` from the token check, and from
   nothing else: the `Host` and browser-header checks still apply. A compose health check then
   needs no secret. Every other path, an unknown one included, stays behind the token.
   `/actuator/health` is not a contract operation. `env`, `configprops`, `beans`,
   `loggers`, `heapdump` and `threaddump` are not exposed, and `show-values` stays `never`.
8. **Secrets are redacted by construction.** Spring Boot sanitizes actuator output but does not mask
   log lines. So a secret is held in a type whose `toString` does not reveal it (as `WorktreeToken`
   does), never as a plain `String` component of a properties record, and no log line, refusal
   message or header carries a bound secret value.
9. **A configuration change is a redeploy.** There is no configuration refresh endpoint and no
   `@RefreshScope`. `POST /packs/refresh` (ADR-0018 decision 10) re-reads packs, which are domain
   state, and is not affected.

## Consequences

### Positive

- The local daemon is unchanged in its default configuration: same file, same bind, same
  launcher. The one new local failure is a non-loopback bind without the container profile.
- A misconfigured container fails at the start with the property named, instead of serving
  nothing or serving to anyone.
- One image runs under docker compose, Kubernetes or a PaaS: each of them can set env vars and
  mount files at `/run/secrets/`.
- Replicas started from one release and one deployment configuration cannot drift.

### Negative

- Three new build dependencies (validation, the configuration processor, then actuator), each to
  be verified.
- A container must always mount `/run/secrets/`, even when every secret comes from env vars.
- The static bearer is one shared secret per deployment. It has no scopes and no per-user
  identity until the login flow lands.
- `WorktreeGuard` and `GuardWiring` gain a profile-dependent branch to test under both profiles
  ([#231](https://github.com/spec-kitty/spec-kitty-charter/issues/231)).

### Risks

- The `health` exemption is read as a way past the guard. Mitigation: it answers up or down only,
  and the guard tests pin that no other path is exempt.
- The static bearer outlives its stopgap role. Mitigation: ADR-0034 decision 5 stays open for the
  login flow and token format, and this record names it as interim.

### Out of scope

- **Running more than one instance**, tracked in #233: the per-worktree tenancy model, the
  write-through activation copy of ADR-0033 (shared storage or sticky routing), ETag and
  optimistic-concurrency consistency across replicas (ADR-0018), source fingerprint and cache
  coherence, auth and session state, readiness and liveness probes, and draining. Because one
  instance may serve many projects, state the service holds (the activation copy, the ETags) will
  need to be keyed per project; #233 decides how.
- **The container image**, its tags and its version reporting: #234 and its own ADR.
- Platform-specific manifests (Kubernetes, PaaS).

## Alternatives considered

### Spring Cloud Config for the service's runtime configuration

Not chosen. Immutable configuration per release and deployment keeps replicas consistent. A
runtime refresh (`/actuator/refresh`, `@RefreshScope`) rebinds only the instance that receives it,
so replicas drift until each is refreshed or a bus broadcasts the change. The local daemon is
offline and loopback per worktree (ADR-0011, ADR-0034), so it has no config server to reach. Spring
Cloud also ties every Spring Boot upgrade to its release train, a supply-chain cost with no
offsetting need.

### Spring Cloud Config for delivering packs

Not chosen. Packs and `activation.yaml` are domain state (ADR-0033), registered and changed through
the API and kept under the configuration root (ADR-0038), not properties.

### Naming the profile `hosted`

Not chosen. "Hosted" names the deployment option of ADR-0034. The profile names how the process is
configured, and a developer may run the same container locally.

### `optional:configtree:` with validation alone

Not chosen. Validation would catch a missing secret, but a required import also catches a missing
mount before any property is read, and names the directory.

## References

- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) decisions 9 and 10,
  [ADR-0020](/adr/0020-offering-and-activations/) D12,
  [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/),
  [ADR-0034](/adr/0034-local-and-hosted-deployment-options/),
  [ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/)
- [Service lifecycle](/architecture/service-lifecycle/)
- Epic [#226](https://github.com/spec-kitty/spec-kitty-charter/issues/226) and its tasks #228 to
  #233; container image [#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234)
- Spring Boot reference: externalized configuration (config trees) and actuator endpoints
