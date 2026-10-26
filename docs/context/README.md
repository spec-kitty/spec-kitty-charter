---
okf_version: "0.2"
title: Context
description: Why the charter is being extracted into a Java service, the vocabulary it uses, and the systems it sits between.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Read this section before the architecture.
---

# Context

The charter is Spec Kitty's governance authority. Today it is Python code inside the core
CLI. This repository is where it becomes a Java service with an OpenAPI contract, released
on its own. This section explains why, what the charter owns, and who talks to it. Read it
before the architecture.

- [Product](/context/product/) — what the charter is for, where it sits in the product lines,
  and what this repository will hold.
- [The charter offering](/context/charter-offering/) — why the offering is worth extracting:
  centrally governed, harness-independent agent behavior each project composes to its needs.
- [Problem](/context/problem/) — why the charter leaves the CLI, and why it becomes Java.
- [Domain](/context/domain/) — the charter vocabulary: Charter Pack, activation preset,
  active charter, pack level, DRG and the rest.
- [Conceptual model](/context/conceptual-model/) — what packs, the charter and its levers,
  mission types, profiles and values mean, before any field or endpoint.
- [System context](/context/system-context/) — the systems around the charter: the core CLI
  and runtime, the packs, the event contracts and the integration package.
- [User journeys](/context/user-journeys/) — eleven persona journeys through the charter, with the
  service operations each needs and how core does it today.
- [Audiences](/context/audience/) — who reads these docs and what each reader needs.
