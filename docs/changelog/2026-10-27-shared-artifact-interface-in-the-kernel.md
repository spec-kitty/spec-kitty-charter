---
okf_version: "0.2"
title: The shared artifact interface lives in the kernel
description: CharterArtifact and PackIdentifier move to the kernel so a second slice can implement it, the artifact schema version becomes optional, and a cloud-session setup guide lands.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the shared artifact interface now lives, the optional schema version, and how to set up a cloud session.
  - profile: core-maintainer
    guidance_text: Read this for the kernel-boundary move and the ADR-0006 slice rule it unblocks.
---

# 2026-10-27 — The shared artifact interface lives in the kernel

**A second slice can now implement the shared artifact interface.** Before, `CharterArtifact` and `PackIdentifier` lived in `packs.domain`, so only the packs slice could implement them — but ADR-0006 lets a slice depend on the kernel and itself only, and the coming mission-type and agent-profile slices need the same interface. Both types move to `kernel.domain` ([ADR-0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/), settling [#171](https://github.com/spec-kitty/spec-kitty-charter/issues/171)); the kernel stays pure (`PackIdentifier` depends only on `kernel.validation`). A positive ArchUnit control proves a non-packs slice can implement the interface.

`schemaVersion()` becomes `Optional<String>` on the interface and the five domain entities: assets have no schema version and profiles make it optional, while the five existing kinds still require one, so their callers unwrap it. The storage records keep a plain `String`; serialization is unchanged.

A new [cloud-session setup guide](/development/cloud-session-setup/) records the Java 25 toolchain, the domains a session must allow, the per-session steps and the troubleshooting for the failures a real session hit, so every contributor's cloud session runs what CI runs.
