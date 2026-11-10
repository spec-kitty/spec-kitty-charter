---
okf_version: "0.2"
title: Agent profiles are readable through the API
description: A client reads an agent profile and its governance from a pack or under an activation, and the effective items link to their reads.
doc_status: active
updated: 2026-11-10T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/service-contract
  - development/hypermedia-hateoas
  - guides/service-build
audience:
  - profile: contract-consumer
    guidance_text: Read this to see which profile and effective reads a client can follow, and which links reach them.
---

# 2026-11-10 — Agent profiles are readable through the API

**Agent profiles are readable through the API** ([#407](https://github.com/spec-kitty/spec-kitty-charter/issues/407),
[#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)). Before, a client found no profile in a pack and
could not reach a body from an activation. Now it starts at the API root and follows links to a profile and to every
artifact its governance walk lists. Every contract change is additive; the details are in the
[service contract](/api/service-contract/).

- **Pack-scoped profile.** Before: `agent_profile` was no served kind. After: the list, `?kind=agent_profile`, the
  references and the removal serve it, and `getPackAgentProfile` answers the body with `governance`, lineage-walked over
  the pack's `depends_on` closure. Each entry carries `edge` (`when`, `reason`). A profile the pack lacks is
  `404 artifact_not_found`.
- **Activation-scoped reads.** Before: nothing read one artifact under an activation. After: `getEffectiveArtifact`
  answers the winner of any served kind, and `getEffectiveAgentProfile` a profile with governance judged by the
  activation.
- **Two divergences from core, by operator ruling.** `suggests` entries are listed (core treats them as inert), and an
  ancestor not in force is dropped under an activation (the write cascade passes it through). Field-merge resolution
  stays with [#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102).
- **Links.** Before: a client built the URL. After: the root links `activations` (`POST`), an `Activation` links
  `artifacts{?kind,state}`, and an effective list or action-context item of a served kind links `effective-artifact`
  and `artifact`. A graph node links `effective-artifact` only. `effective-artifact` is a new relation.
- **Open-packs pin.** Before: `22c14b2`. After: `a2a9379`, which adds the `requires` edge from `java-jenny` to `java-conventions`.
- **Usability run.** `scripts/usability/service-usability-run.sh` registers the open-packs, activates this repository's
  charter and follows links from `/api/v1/` to `java-jenny`; it fails at the first profile hop on the code before this
  change. The engineering note `c1c2ab56` records the 2026-11-09 run.
- **Build.** The profile rings live in the new `:profiles-edge` module (nightly mutation floor 85), and the `:activations`
  PIT run now gets the contract schemas.
