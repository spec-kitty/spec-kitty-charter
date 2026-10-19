---
title: "Reader persona: Charter service contributor"
description: The engineer who builds the Java charter service, its OpenAPI contract and the conformance corpus in this repository.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Persona
role: Java engineer building the charter service, its contract and its conformance corpus
goals:
  - Build the service contract-first
  - Reproduce core's Python behavior exactly until it retires
responsibilities:
  - Keep the domain free of framework and transport types
  - Add a fixture with every behavior change
audience:
  - profile: charter-service-contributor
    guidance_text: This page describes the reader the architecture, plans and contributing pages are written for.
---

# Reader persona: Charter service contributor

| Field | Value |
|---|---|
| **Group** | inside this repository |
| **Actor type** | human |
| **Primary goal** | Build the charter service, contract-first, so that it gives the same answer as the Python charter in core. |

## What this reader needs

- The target design: containers, modules, the dependency rule and the lifecycle.
- The behavior to reproduce, written down: the logical model, the descriptors and the resolution model.
- The order of work: contract and fixtures first, then generated code, then implementation.
- The decisions already taken and the ones still open, each with the default to work from.

## What frustrates them

- Core's Python behavior is the reference, and it is spread over many modules.
- Not knowing whether a page describes what exists or what is only designed.
- A decision that is open but written as if it were settled.

## Signal that the documentation served them

A contributor can pick the next roadmap step, find the page that defines the behavior it must match, and name the fixture that proves it.

## Where to start

Start with the [product](/context/product/), then the [architecture overview](/architecture/overview/) and the [roadmap](/plans/roadmap/).
