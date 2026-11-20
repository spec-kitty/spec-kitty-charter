---
okf_version: "0.2"
title: pack-check fails a malformed pack.yaml instead of crashing
description: A malformed pack_version, depends_on range or requires_core is a problem at pack.yaml (exit 1), CLOSURE is skipped rather than handed it, and a case variant has its own code.
doc_status: active
updated: 2026-11-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/check-packs
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0073-real-packs-register-as-they-are
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a CI gate runs pack-check, or matches a refusal of a case-variant file by its code.
---

# 2026-11-20 — pack-check fails a malformed pack.yaml instead of crashing

**A malformed `pack.yaml` field is a problem, not a crash** (#624, operator ruling 2026-11-20: the checker must fail
on malformed contents). Released `pack-check` 0.1.0-rc.3 exited `2` with `internal error` and no report on a
`pack_version` that is not a version. Now:

- A `pack_version`, a `depends_on` range or a `requires_core` that is not PEP 440 is one `validation_failed` problem
  for the pack, with `pack_version_invalid`, `dependency_range_invalid` or `requires_core_invalid` at `pack.yaml`,
  and exit `1`. This holds for `--pack`, for `--config-root` and with `--round-trip`.
- `CLOSURE` judges only descriptors the read accepted. A pack with a malformed descriptor stops it, as a pack with no
  readable facts or no level already did, and the report's `skipped` names the pack. The pack's own fault is its one
  problem.
- A field the descriptor schema refuses (`pack_level`, `pack_id`, `schema_version`, `name`, an undeclared key) was
  already `pack_unreadable` at `pack.yaml`, exit `1`; a test now pins every field.
- Exit `2` is for the command line, the file system and the output, and for a defect of `pack-check` itself. Pack
  content never reaches it.

**A case variant has its own code.** A root file that differs only in letter case from a root rule's file
(`Pack.yaml` beside `pack.yaml`, `Readme.md`) is refused under the new violation code `case_variant`, not `retired`.
It is still refused by name under that rule ([ADR-0073](/adr/0073-real-packs-register-as-they-are/) decision 3). This
breaks the charter and packs contracts: a consumer that matches the refusal by its code matches `case_variant`.
