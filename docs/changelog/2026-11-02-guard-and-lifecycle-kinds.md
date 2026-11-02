---
okf_version: "0.2"
title: Guards, lifecycles and executable assets are stored and checked
description: "The guard and lifecycle kinds, the asset executable block and the derived implemented_by relation load, round-trip and fail closed when their references do not resolve."
doc_status: active
updated: 2026-11-02T00:00:00Z
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

**Before:** ADR-0054 and ADR-0050 described guards and lifecycles; the store held neither, and no asset could say it
runs.

**Now:**

- **`guard`** (`guards/<id>.guard.yaml`) and **`lifecycle`** (`lifecycles/<id>.lifecycle.yaml`) are pack kinds with
  closed schemas, bound in the kind registry and compared by meaning (ADR-0036). Both are in force with their pack,
  not activatable, and not served over the API until their contract set (work breakdown item 13).
- **The asset `executable` block**: a `runtime` from a closed allowlist (`python`), and an `entry` that is the asset's
  own path and one argv element. A shell, an escaping path and a command string are refused.
- **`implemented_by`** is derived from a guard's field into the graph, never authored. Until item 13, no authored edge
  names a guard or a lifecycle.
- **Load-time checks fail closed:** a guard's asset must be held and executable; its `config_schema` must compile
  offline; a lifecycle's guard entries must resolve and their `config` fit. A pack-qualified reference waits for the
  pack dependency closure. What does not close is left out with a `reference` violation.
- **Rulings (operator, 2026-11-02):** six `builtin:` ids; ADR-0042 S4 admits `config_schema` and an entry's `config`;
  the event key is `event`, not `on`; `"*"` leaves out the target; action points wait for item 7; the changed-files
  input stays open. ADR-0042, ADR-0050 and ADR-0054 record them.

**Not yet:** the trust check is the client's (#157); a per-file write and validate mode do not run the closure checks;
a lifecycle mints no edge to its guards.
