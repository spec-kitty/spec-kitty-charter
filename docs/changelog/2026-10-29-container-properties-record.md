---
okf_version: "0.2"
title: The container profile's properties are validated at the start
description: Under the container profile, a validated charter.container.* record fails the start when charter.container.allowed-hosts is missing or invalid; bean validation enters the build.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0041-containerized-configuration-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a key to charter.container.* or touching request validation.
  - profile: governance-lead
    guidance_text: Read this for what a container start now refuses.
---

# 2026-10-29 — The container profile's properties are validated at the start

For [#228](https://github.com/spec-kitty/spec-kitty-charter/issues/228), the deployable gains
`ContainerProperties`, the validated `charter.container.*` record of
[ADR-0041](/adr/0041-containerized-configuration-model/) decision 2:

- **Container profile.** The record is bound only under the `container` profile. There it holds
  `charter.container.allowed-hosts`, a required list of host names. An absent or empty list, or a
  blank entry, fails the start, naming the key.
- **Start-up check.** Under the profile, the start also fails, naming the property, when:
  - `server.port` is 0;
  - `charter.config-root` is not an existing absolute directory;
  - `charter.insecure-no-token` is set, whatever its value;
  - `charter.project-config-dir` is set.

  These are ADR-0041 decisions 4 to 6. The local `application.yaml` binds port 0, so a container
  must set `SERVER_PORT` until `application-container.yaml` exists.
- **Local daemon.** Without the profile neither the record nor the check applies, so the local
  daemon starts as before.
- **New dependency.** `spring-boot-starter-validation` (Hibernate Validator) enters the build under
  strict SHA-256 verification.

**Side effect on the HTTP surface.** With bean validation on the classpath, Spring MVC checks the
generated API's `@Valid` registration bodies. A body missing a required field is now a 400 problem
(the contract's `default` response) instead of a 500 `internal_error`; the contract is unchanged.
Controllers also become method-validation proxies; a `@Size` breach on a path variable, unreachable
today, would be a 500 until a follow-up maps it.

Still to come: `application-container.yaml`, the secret import and the token message
([#229](https://github.com/spec-kitty/spec-kitty-charter/issues/229)); the configuration metadata
([#230](https://github.com/spec-kitty/spec-kitty-charter/issues/230)); the guard tests and the
local loopback rule ([#231](https://github.com/spec-kitty/spec-kitty-charter/issues/231)), with the
guard's use of `allowed-hosts` as a follow-up; actuator
([#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232)). Until the guard is built, a
service under the `container` profile still accepts only loopback `Host` names.
