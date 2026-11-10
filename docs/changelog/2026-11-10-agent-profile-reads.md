---
okf_version: "0.2"
title: Agent profiles are readable through the API
description: A client reads an agent profile and its governance from a pack or under an activation.
doc_status: active
updated: 2026-11-10T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/service-contract
  - development/hypermedia-hateoas
  - guides/service-build
  - plans/engineering-notes/service-usability-run-2026-11-09
audience:
  - profile: contract-consumer
    guidance_text: Read this to see which profile and effective reads a client can follow, and which links reach them.
---

# 2026-11-10 — Agent profiles are readable through the API

**Agent profiles are readable through the API** ([#407](https://github.com/spec-kitty/spec-kitty-charter/issues/407),
[#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)). Before, a client found no profile in a pack. Now it follows
links from the API root to a profile and to every artifact its governance lists. Every contract change is
additive; see the [service contract](/api/service-contract/).

- **Pack-scoped profile.** Before: `agent_profile` was no served kind. After: the list, `?kind=agent_profile`, the
  references and the removal serve it, and `getPackAgentProfile` answers the body with `governance`, walked over the
  pack's `depends_on` closure. Each entry carries `edge` (`when`, `reason`). An unknown profile is
  `404 artifact_not_found`. An unfiltered pack listing now also returns `agent_profile` items, last, so pages change.
- **Activation-scoped reads.** Before: nothing read one artifact under an activation. After: `getEffectiveArtifact`
  answers the winner of any served kind, and `getEffectiveAgentProfile` a profile with governance judged by the
  activation.
- **Two divergences from core, by operator ruling.** `suggests` entries are listed (core treats them as inert), and an
  ancestor not in force is dropped under an activation (the write cascade keeps it).
- **Links.** The root links `activations` (`POST`), an `Activation` links `artifacts{?kind,state}`, and a served-kind
  item of an effective list or action context links `effective-artifact` and `artifact`.
- **Open-packs pin.** Before: `22c14b2`. After: `a2a9379`, which adds a `requires` edge from `java-jenny` to
  `java-conventions` and removes directive `038-structured-prompt-boundary` and five paradigms (two become a
  procedure and a toolguide, three are deleted), so a new activation that selects one is `422 unknown_artifact`. A
  stored one keeps working: `getActivation` still returns the id in `selected`, the effective reads omit it, and
  the skipped report names it under `staleIds` until the next write.
- **Usability run.** `scripts/usability/service-usability-run.sh` follows links from `/api/v1/` to `java-jenny`
  ([the 2026-11-09 note](/plans/engineering-notes/service-usability-run-2026-11-09/)). SC-001 is deferred (#452).
- **Build.** The profile rings live in the new `:profiles-edge` module.
