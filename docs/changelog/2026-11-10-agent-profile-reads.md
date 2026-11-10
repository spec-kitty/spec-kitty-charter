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
[#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)). Before, a client found no profile in a pack. Now it
follows links from the API root to a profile and to every artifact its governance lists. Every contract change is
additive; see the [service contract](/api/service-contract/).

- **Pack-scoped profile.** Before: `agent_profile` was no served kind. After: the list, `?kind=agent_profile`, the
  references and the removal serve it, and `getPackAgentProfile` answers the body with `governance`, walked over the
  pack's `depends_on` closure. Each entry carries `edge` (`when`, `reason`). An unknown profile is
  `404 artifact_not_found`.
- **Activation-scoped reads.** Before: nothing read one artifact under an activation. After: `getEffectiveArtifact`
  answers the winner of any served kind, and `getEffectiveAgentProfile` a profile with governance judged by the
  activation.
- **Two divergences from core, by operator ruling.** `suggests` entries are listed (core treats them as inert), and an
  ancestor not in force is dropped under an activation (the write cascade passes it through).
- **Links.** The root links `activations` (`POST`), an `Activation` links
  `artifacts{?kind,state}`, and an effective list or action-context item of a served kind links `effective-artifact`
  and `artifact`.
- **Open-packs pin.** Before: `22c14b2`. After: `a2a9379`, which adds the `requires` edge from `java-jenny` to
  `java-conventions`. It removes directive `038-structured-prompt-boundary` and five paradigms (`git-flow` and
  `execution-lanes` become a procedure and a toolguide; `shared-branch-ci`, `extract-then-publish-separation` and
  `structured-prompt-driven-development` are deleted), so an activation that selects one is `422 unknown_artifact`.
- **Usability run.** `scripts/usability/service-usability-run.sh` follows links from `/api/v1/` to `java-jenny` and fails
  at the first profile hop before this change ([the 2026-11-09 note](/plans/engineering-notes/service-usability-run-2026-11-09/)).
  SC-001 is deferred: procedures have no read, and the project pack cannot register (#452).
- **Build.** The profile rings live in the new `:profiles-edge` module.
