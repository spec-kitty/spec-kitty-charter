---
title: "Reader persona: Pack author and doctrine curator"
description: The person who writes and curates Charter Packs, built-in, org or project, and needs to know the descriptor files, fields and resolution rules the charter applies.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Persona
role: Author of built-in, org and project Charter Packs
goals:
  - Write packs that load, merge and activate as intended
  - Fix a failing pack from a named refusal
responsibilities:
  - Declare each descriptor with its required fields
  - Link artifacts through graph edges, not descriptor fields
audience:
  - profile: pack-author
    guidance_text: This page describes the reader the logical model, descriptors, descriptor schemas and resolution model are written for.
---

# Reader persona: Pack author and doctrine curator

| Field | Value |
|---|---|
| **Group** | outside this repository, writes the inputs the charter reads |
| **Actor type** | human |
| **Primary goal** | Author a pack that loads, merges and activates as intended, and learn from a named refusal when it does not. |

## What this reader needs

- Which descriptor file declares what, where it lives and how it is named.
- Which fields are required, and which edges belong in the graph and not in the file.
- How the built-in, org and project layers merge and what activation does with them.
- The refusal codes, so a failing pack can be fixed without guessing.

## What frustrates them

- Authored governance that loads and validates, then reaches no agent.
- A field that exists in one schema and is restated, differently, in another page.

## Signal that the documentation served them

An author can place a new descriptor, write its required fields, link it into the graph and predict how the layers merge.

## Where to start

Start with the [logical model](/architecture/logical-model/), then the [descriptors](/architecture/descriptors/).
