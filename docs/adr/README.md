---
okf_version: "0.2"
title: Decision Records
description: The immutable log of decisions taken in the spec-kitty-charter repository, and the core Spec Kitty ADRs they build on.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: ADR
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Find the decisions taken here and the core decisions they build on.
  - profile: core-maintainer
    guidance_text: See which core decisions this repository builds on and where it asks core to rule.
  - profile: evaluator
    guidance_text: Read this to see which decisions are taken, proposed or inherited from core.
---

# Decision Records

Decisions taken in this repository. Records are numbered `NNNN-<slug>.md` and copied from
[the template](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/templates/adr.template), as in doc-kitty. The *decision* in an accepted record is immutable: a
later decision supersedes it rather than editing it. A record may still carry a dated,
decision-preserving **clarification** (an annotation that sharpens scope or wording without
changing what was decided); a clarification is labelled and dated, and anything that changes the
decision is a new record instead. The one exception, as in core's records, is a dated
**amendment** under the record's Status that cites the operator ruling changing part of the
decision: the original decision text stays, and each point it changes is annotated where it
stands.

The table below is generated from each ADR's frontmatter (`title`, `decision_status`,
`decision_date`) when the site is built; a pull request adds its ADR file and never edits this
page ([ADR-0047](/adr/0047-generated-indexes-and-merge-queue/)). In the repository the
block below is empty; read the list on the
[published site](https://spec-kitty.github.io/spec-kitty-charter/adr/).

<!-- BEGIN GENERATED INDEX: scripts/docs/generate_indexes.py fills this list; do not edit it by hand -->
<!-- END GENERATED INDEX -->

## Core decisions this repository builds on

These live in [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty) under
`docs/adr/`. They are not copied here.

| Core ADR | Title | Status in core | What it gives this repository |
|---|---|---|---|
| [`4.x/2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) | The charter becomes an external contract package in its own repository | Superseded (2026-10-19) by [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) and [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) | Why this repository exists; its import-move precondition is done (#6188) |
| [`4.x/2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) | Replace charter reads and writes through a staged Java service strangler | Accepted (2026-10-19, with the amendment of 2026-10-19) | Governs only the seam routing ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)): the stages, the switch at `charter.offering.api`, the seam's failure behavior, and the per-worktree daemon boundary ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)) |
| [`4.x/2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md) | Extract the charter offering first; activation rework after GA | Accepted, P4 amended 2026-10-19 | The import move is done (#6188); activation stays in core until its post-GA rework. Step P4 now replaces the Python offering in place instead of moving it here ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)) |
| [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-14-1-event-native-core-one-canonical-event-writer.md) | Event-native core: one canonical event writer | Accepted | Charter-related events, if any, go through this writer; the charter service writes none |
| [`4.x/2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) | A mission status read API replaces the CLI-bundled dashboard | Accepted | The sibling Java service: stack, binding, contract conventions, release model |
| [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) | Charter offering, active charter and activation presets | Accepted | The domain vocabulary; presets as pack data; no aliases or shims |
| [`4.x/2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md) | A Charter Pack declares its level in `pack.yaml` | Accepted | `pack_level` and its refusal codes |
| [`3.x/2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) | Doctrine layer merge semantics | Accepted | How built-in, org and project layers merge |
| [`3.x/2026-08-02-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-02-1-charter-wheel-assessment.md) | Charter wheel assessment | Accepted | The earlier, deferred wheel split that 2026-10-12-2 extends |
| [`3.x/2026-04-25-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-04-25-1-shared-package-boundary.md) | Shared package boundary | Accepted | How external contract packages are consumed |
