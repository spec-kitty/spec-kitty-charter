---
okf_version: "0.2"
title: Sharper refusals for escaping paths and a placeholder token
description: An escaping asset path or fragment body_path is refused as an escape, and a worktree token given as a placeholder stops the start.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0040-content-form-and-payload-kinds
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new wording of path-escape refusals and the placeholder-token start refusal.
---

# 2026-11-12 — Sharper refusals for escaping paths and a placeholder token

Three refusals now say what is actually wrong
([#496](https://github.com/spec-kitty/spec-kitty-charter/issues/496)).

- **An asset path that leaves `assets/` is refused as an escape**
  ([#382](https://github.com/spec-kitty/spec-kitty-charter/issues/382);
  [ADR-0040](/adr/0040-content-form-and-payload-kinds/), amendment 2026-11-11). Before: a sidecar path such as
  `../../x` read `payload assets/../../x does not exist`, as if the file were only missing. After: it reads
  `asset path '../../x' escapes the pack's assets/ root`, whether or not a file is at the target. A refused
  registration lists such a sidecar once, no longer a second time as `unmodelled`.
- **A fragment `body_path` that leaves the pack root is refused**
  ([#254](https://github.com/spec-kitty/spec-kitty-charter/issues/254)). Before: the pack was accepted. After: a
  registration answers `422 validation_failed` with a `drg_schema_error` at `drg/fragment.yaml`, and a composing read
  answers `500 pack_unreadable`. No example pack carries such a path.
- **A worktree token given as a placeholder stops the start**
  ([#121](https://github.com/spec-kitty/spec-kitty-charter/issues/121)). Before: `charter.worktree-token=${X}` resolved
  the token from another source, the command line among them, where other processes can read it. After: the service
  refuses to start and says the value must be the token itself.
