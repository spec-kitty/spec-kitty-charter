---
okf_version: "0.2"
title: Packs contract files are frozen under a tagged release
description: "Once a contract-packs-v tag is merged, check refuses a change to the packs contract's files until the next -SNAPSHOT is opened (#663)."
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/pin-the-packs-contract
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change file-kinds.yaml, a pack schema or a conformance case after a packs release.
  - profile: governance-lead
    guidance_text: Read this before you cut a packs contract release.
---

# 2026-11-25 — Packs contract files are frozen under a tagged release

Operator ruling of 2026-11-25: #663 lands before the next `contract-packs-v*` tag, the release candidate cut.

- **The rule.** Once the top heading of `contracts/packs/CHANGELOG.md` is released and its `contract-packs-v<version>`
  tag is merged into HEAD, `check` (`:contracts:checkPacksChangelog`) refuses HEAD while its packs contract files
  (`file-kinds.yaml`, `schemas/`, `conformance/`) differ from the tag's, until the next `-SNAPSHOT` is opened. Before,
  such a change passed every per-change check and the published release no longer matched `main` under its version;
  only the service release gate caught it, later. Other files may change, unlike under a charter contract release,
  which refuses any later commit.
- **It fails closed when the tags are missing.** A shallow clone is refused, as the charter contract's check refuses
  it. A clone without the tags (`git clone --no-tags`) is refused too: every released heading below the top must have
  its `contract-packs-v*` tag merged into HEAD. A git error fails the build. `-PcontractBaseline=none` skips the tag
  rules on purpose outside CI and is refused under `CI=true`, as for the charter contract's check.
- **Shared git probes.** The check and the service release gate share the merged-tag and changed-file probes and the
  packs contract's file list (`build-logic`, `ContractReleaseGit.kt`). The check passes every merged `contract-packs-v*` tag
  to the gate, which names the top heading's tag by its own heading grammar.
- **The rehearsal.** `rehearse-packs-contract-release.sh` rehearses the rule at a tagged candidate: a change outside
  the packs contract passes, and a change to `file-kinds.yaml` is refused.
