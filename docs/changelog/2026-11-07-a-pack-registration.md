---
okf_version: "0.2"
title: "Whole-pack registration and removal (PR A)"
description: "A pack is registered whole from an archive or a source, polled as a status, and removed by artifact or edge. Registration needs a host git for the git source."
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/compose-packs
  - guides/service-build
  - guides/run-in-a-container
  - conformance/divergence-register
  - conformance/ruleset-scenario-trace
audience:
  - profile: core-maintainer
    guidance_text: Read this before you switch core's pack writes to the service; the divergences below are core's migration notes.
---

# 2026-11-07 — Whole-pack registration and removal (PR A)

PR A of the layer-merge mission builds the write side of packs. Divergences: DIV-601 to DIV-613
([register](/conformance/divergence-register/)). Contract: the `2026-11-07-a-*.md` and `2026-11-08-a-*.md` fragments.

- **Register whole.** `PUT /packs/{packId}` takes an archive and `POST /packs` takes a source (https archive, git,
  local path). Both answer `202` and a status to poll. The service holds exactly the pack sent. See
  [Compose packs](/guides/compose-packs/#register-a-pack).
- **Version rule.** A version is a label. The same version with other content is `409 pack_version_conflict`.
- **Strict impact check.** A write after which a closure no longer composes, or breaks a relation rule, is `422`.
  Core warns.
- **Remove.** `DELETE` of an artifact and of a registered relationship, each with `If-Match`.
  The relationship `DELETE` runs the edge gate and compares pack-qualified targets.
- **Host requirement.** The git source runs the system `git`, 2.50.1 or later; no JGit. A new required CI job,
  `git-source`, runs the real-git tests.
- **Off by default.** `charter.registration.enabled` and each `charter.pack-sources.<kind>.enabled`.
- **Notices** name activatable kinds only; others are logged (#417).

Nothing is edited or filed in core (C-003).
