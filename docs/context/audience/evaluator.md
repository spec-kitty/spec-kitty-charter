---
title: "Reader persona: Evaluator"
description: The architect, tech lead or product owner who weighs whether to depend on the charter, and needs boundaries, status and trade-offs stated plainly.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Persona
role: Architect, tech lead or product owner deciding whether to adopt or depend on the charter
goals:
  - Judge the charter's fit and boundaries before committing to it
  - See what is decided, what is open and what is only designed
responsibilities:
  - Decide whether a team or product depends on the charter
  - Hold the charter to a contract rather than to its internals
audience:
  - profile: evaluator
    guidance_text: This page describes the reader the problem, product and decision-record pages give the case to.
---

# Reader persona: Evaluator

| Field | Value |
|---|---|
| **Group** | outside this repository, decides on adoption |
| **Actor type** | human |
| **Primary goal** | Decide with evidence whether the charter is a sound thing to depend on. |

## What this reader needs

- The problem, stated with its limits: what is known, and what is not yet measured.
- Where the charter sits among the product lines, and what it deliberately is not.
- The decisions taken, with their status and the alternatives rejected.
- The trade-offs of two implementations during the transition, and the evidence that gates
  each stage.

## What frustrates them

- Claims of speed or readiness with nothing behind them.
- A boundary that is described by what it will become rather than by what it is.

## Signal that the documentation served them

An evaluator can write down the decision they would take, the risks they accept and the
open questions they would want answered first.

## Where to start

Start with the [problem](/context/problem/), then the [decision records](/adr/) and the
[open questions](/plans/open-questions/).
