---
okf_version: "0.2"
title: Development
description: The conventions contributors follow when they change the charter API and the service, so every operation reads and behaves the same way.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Guide
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Read the page for the convention you are about to apply before you change a path, a response or a status code.
  - profile: contract-consumer
    guidance_text: Read this for the rules every operation of the contract follows, so you can rely on them.
  - profile: core-maintainer
    guidance_text: Read this for how the seam should read links, status codes and refusal codes.
---

# Development

Conventions for changing the charter API. Each page states the rule, shows it on this contract,
and names the mistake to avoid. Decisions behind them are in [Decision Records](/adr/); the
operations themselves are in the [service contract](/api/service-contract/).

- [REST resource conventions](/development/rest-resource-conventions/) — naming paths,
  choosing methods, parameters, media types, versioning and the headers every response carries.
- [Hypermedia (HATEOAS)](/development/hypermedia-hateoas/) — how responses link to what a client
  can do next, the `Links` component and the relation names.
- [HTTP status codes](/development/http-status-codes/) — which status this API answers with, what
  each one means, its body and whether a client may retry.

> **Status:** the REST conventions and status codes describe the contract as it is, and mark
> what is proposed. The `Links` component and the root resource are proposed by
> [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/); the offering, the activation
> resources, conditional requests and the hosted-first reshape by
> [ADR-0020](/adr/0020-offering-and-activations/). None is in the contract yet.
