---
okf_version: "0.2"
title: Guards, lifecycles and executable assets are stored and checked
description: "The guard and lifecycle kinds, the asset executable block and the derived implemented_by relation load, round-trip and fail closed when their references do not resolve."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - architecture/pack-schemas
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: pack-author
    guidance_text: Read this for the new guard and lifecycle files and the executable block on an asset sidecar.
  - profile: charter-service-contributor
    guidance_text: Read this for what the store now checks at load and what is left to the contract work.
---

# 2026-11-02 — Guards, lifecycles and executable assets are stored and checked

Work breakdown item 8, [#156](https://github.com/spec-kitty/spec-kitty-charter/issues/156).

**Before:** ADR-0054 and ADR-0050 described guards and lifecycles; the store held neither.

**Now:**

- **`guard`** (`guards/<id>.guard.yaml`) and **`lifecycle`** (`lifecycles/<id>.lifecycle.yaml`) are pack kinds with
  closed schemas, compared by meaning (ADR-0036). Both are in force with their pack, not activatable, and not served
  over the API until their contract set (work breakdown item 13).
- **The asset `executable` block**: a `runtime` from a closed allowlist (`python`) and an `entry`, one argv element
  naming a payload file of the same pack. A shell, an escaping path or a command string is refused.
- **`implemented_by`** is worked out from a guard's own field, never authored. Nothing reads it yet.
- **Load-time checks fail closed:** an executable's entry is a payload the pack holds; a guard's asset is held
  and executable; its `config_schema` must compile offline, use no `$ref` or other reference keyword, and hold only
  linear-time (RE2) patterns; a lifecycle's guard entries resolve and their `config` fits. A qualified reference
  waits for the dependency closure. What does not close is left out (`reference` violation).
- **A lifecycle is refused** with no terminal state, an unreachable state, or two targets for one state and event.
- **Rulings (operator, 2026-11-02):** six `builtin:` ids; ADR-0042 S4 admits `config_schema` and an entry's `config`;
  the event key is `event`, not `on`; `"*"` leaves out the target; action points wait for item 7; the changed-files
  input stays open. 2026-11-03: an entry may name any pack payload; `add_guards` uses `transition`; the lifecycle
  refusals above; re2j patterns. ADR-0042, ADR-0050 and ADR-0054 record them.

**Not yet:** the trust check is the client's (#157); a per-file write and validate mode do not run the closure checks;
a lifecycle mints no edge to its guards.
