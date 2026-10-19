---
title: "Reader persona: Core maintainer"
description: The maintainer of the Spec Kitty core CLI, who owns the seam, the import move and the GA gate that this repository's extraction belongs to.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Persona
role: Maintainer of the core CLI who owns the seam, the import move and the GA gate
goals:
  - Land the extraction without weakening core's guarantees
  - Rule on the decisions that are core's to take
responsibilities:
  - Accept or amend the core ADRs this repository builds on
  - Build the seam and ingest pinned contract releases
audience:
  - profile: core-maintainer
    guidance_text: This page describes the reader the strangler, roadmap, open-questions and decision-record pages are written for.
---

# Reader persona: Core maintainer

| Field | Value |
|---|---|
| **Group** | outside this repository, on the consuming side |
| **Actor type** | human |
| **Primary goal** | Land the extraction without weakening what core guarantees: one answer to what governs this step, and a gate that proves it. |

## What this reader needs

- The stages from Python-only to Java-only, and the evidence each stage needs.
- What core itself must change: accept or amend its ADRs, build the seam, ingest the contract.
- Which decisions are core's to take, and which default this repository assumes until then.
- How a pinned contract release is raised and what must pass when it is.

## What frustrates them

- Finding a core decision restated here with a different status.
- A dependency on core work that is not named, dated or owned.

## Signal that the documentation served them

A maintainer can list the core-side actions, see which ones gate 4.0.0 GA, and rule on an open question without reading the whole tree.

## Where to start

Start with the [problem](/context/problem/), the [strangler transition](/architecture/strangler-transition/) and the [open questions](/plans/open-questions/).
