---
okf_version: "0.2"
title: The containerized service's configuration model is proposed
description: ADR-0041 proposes a container profile with validated properties, secrets only from env or /run/secrets, a profile-bound bind address, and no Spring Cloud Config.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0041-containerized-configuration-model
  - adr/0034-local-and-hosted-deployment-options
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before the container profile's properties, secrets and guards are built (#228 to #232).
  - profile: governance-lead
    guidance_text: Read this for how a team will run the charter service in a container.
---

# 2026-10-28 — The containerized service's configuration model is proposed

[ADR-0041](/adr/0041-containerized-configuration-model/) (proposed, for
[#227](https://github.com/spec-kitty/spec-kitty-charter/issues/227)) records how the charter
service is configured when it runs in a container. It resolves the configuration-key part of
[ADR-0034](/adr/0034-local-and-hosted-deployment-options/) decision 5; the login flow and token
format stay open.

The local daemon does not change. A `container` profile, never the default, holds non-secret
defaults; a validated `charter.container.*` record fails the start on a missing or invalid value;
secrets come only from env vars or files under `/run/secrets/`, a required import. Local binds
loopback; the container binds all interfaces on a fixed port, requires an existing configuration
root, checks `Host` against a configured list and keeps the static bearer until the login flow is
decided. Only `health` is exposed. A configuration change is a redeploy, and Spring Cloud Config is
rejected for both runtime configuration and pack delivery. Running more than one instance is left
to [#233](https://github.com/spec-kitty/spec-kitty-charter/issues/233). Nothing is built yet.
