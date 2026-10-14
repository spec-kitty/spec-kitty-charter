---
title: "Reader persona: Contract consumer"
description: The engineer or agent harness that calls the charter, through the Python package or the service contract, and depends on a stable, versioned surface.
doc_status: active
updated: 2026-10-14
type: Context
kind: Persona
role: Engineer or agent harness that calls the charter through its package or service
goals:
  - Depend on the public surface only
  - Upgrade without surprise
responsibilities:
  - Pin a release and handle each refusal by code
  - Treat anything outside the public surface as internal
audience:
  - profile: contract-consumer
    guidance_text: This page describes the reader the API, contract and service lifecycle pages are written for.
---

# Reader persona: Contract consumer

| Field | Value |
|---|---|
| **Group** | outside this repository, calls the charter |
| **Actor type** | human |
| **Primary goal** | Depend on the charter without depending on its internals, and upgrade without surprise. |

## What this reader needs

- The public surface: what is in it, and that anything outside it carries no promise.
- The versioning rules: what is a minor release and what is a major one.
- The error model: refusals as named `Problem` bodies, never an empty success.
- How the service is started, reached and kept fresh.

## What frustrates them

- A surface described as available when it is only designed.
- A breaking change that arrives without a version change.

## Signal that the documentation served them

A consumer can write a call against the surface, handle each refusal by code, and know which release it may pin.

## Where to start

Start with the [API](/api/), then [Versioning and compatibility](/api/compatibility/).
