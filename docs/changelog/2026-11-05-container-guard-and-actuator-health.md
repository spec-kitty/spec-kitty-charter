---
okf_version: "0.2"
title: A container answers its host names and a health check
description: "Under the container profile the guard accepts charter.container.allowed-hosts, GET /actuator/health answers without a token, and the start refuses unsafe exposure."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/run-in-a-container
  - adr/0041-containerized-configuration-model
audience:
  - profile: governance-lead
    guidance_text: Read this before you reach a containerized charter service by its real host name or probe its health.
  - profile: charter-service-contributor
    guidance_text: Read this for the guard's policy, the exposure start-up check and the shared 400 for parameter violations.
---

# 2026-11-05 — A container answers its host names and a health check

**Under the container profile, the request guard accepts the host names in `charter.container.allowed-hosts`**
([#268](https://github.com/spec-kitty/spec-kitty-charter/issues/268), ADR-0041 decision 6).

- A request must carry `Host`, and the port is not judged.
- Each entry is a host name, a dotted IPv4 address or a bracketed IPv6 address. A wildcard, a port, a scheme, a
  trailing dot and the any-address fail the start.
- Without the profile, the guard still accepts only the loopback names.
- The contract's 403 description says so (a changelog fragment, no version change).

**`GET /actuator/health` answers `{"status":"UP"}`, and nothing else is exposed**
([#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232), ADR-0041 decision 7). Actuator is a new
dependency, verified by SHA-256.

- Under the container profile, that exact request needs no token. The host and browser checks still apply.
- Locally, it needs the token like every path.
- No other endpoint, detail, component, probe group or JMX bean is exposed.
- Forwarded headers are ignored in a container.

**A new start-up check refuses unsafe exposure under both profiles**
([#231](https://github.com/spec-kitty/spec-kitty-charter/issues/231)). It refuses:

- a local `server.address` that is not loopback;
- a management server of its own;
- a widened exposure;
- health details;
- in a container, request-detail logging.

Tests pin that the container profile is never a default, and that a container without its token does not start.

**Every controller answers a parameter that breaks its declared bound with the plain 400 problem**, not only the
artifact list.

[Run the service in a container](/guides/run-in-a-container/) now has a health-check section.
