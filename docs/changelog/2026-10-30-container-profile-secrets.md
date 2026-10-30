---
okf_version: "0.2"
title: The container profile reads its secrets from mounted files or env vars
description: The container profile gets its own file, a required import of the secrets directory, a compose example and an env template; no secret is committed.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0041-containerized-configuration-model
  - guides/run-in-a-container
audience:
  - profile: governance-lead
    guidance_text: Read this for how a containerized service now takes its secrets and what it refuses.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a secret or a key the secrets directory may set.
---

# 2026-10-30 — The container profile reads its secrets from mounted files or env vars

This entry covers [#229](https://github.com/spec-kitty/spec-kitty-charter/issues/229), which carries out
[ADR-0041](/adr/0041-containerized-configuration-model/) decision 3:

- **Profile file.** `application-container.yaml` holds only non-secret defaults: all interfaces, on port 8080.
- **Required import.** The profile imports the secrets directory, `/run/secrets` unless
  `charter.container.secrets-dir` names another, as a required config tree. A container without the directory does not
  start.
- **Two sources.** The token and `charter.container.*` keys bind from files named after their property, or from env
  vars, which outrank the files.
- **Allowlist.** A file in the directory may set only the token and `charter.container.*` keys (operator decision,
  noted in the ADR). Kubernetes' service-account tree is left alone.
- **Token refusals.** Under the profile, they name the env var and the mounted file.
- **Compose example.** `deploy/compose/` holds a compose example and `.env.example`, with no value set. Git ignores
  `.env`, `.env.*` and `deploy/compose/secrets/`. A test fails on a committed secret.
- **Local daemon.** It is unchanged. Its `application.yaml` and token messages are pinned by tests.

How to run it: [Run the service in a container](/guides/run-in-a-container/).

**Still to come:**

- [#230](https://github.com/spec-kitty/spec-kitty-charter/issues/230): the property metadata, including
  `charter.container.secrets-dir`.
- [#231](https://github.com/spec-kitty/spec-kitty-charter/issues/231): the guard's container form.
- [#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232): actuator and a compose health check.
- [#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234): the image. Set its configuration root in the
  profile file as `${configRootLocation:<mount>}`, not as an image `ENV`, and add a `.dockerignore` that excludes
  `.env*` and `secrets/`.
- The guard reading `allowed-hosts`.
