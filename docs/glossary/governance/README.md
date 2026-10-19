---
okf_version: "0.2"
title: "Governance"
description: "Glossary terms for the Governance context."
kind: Glossary
glossary_context: "Governance"
doc_status: active
generated:
  by: "doc-kitty-glossary/0.4.0"
  at: "2026-10-19T15:57:59Z"
  content_hash: "sha256:791727aafa83"
---

# Governance

Rule ownership, precedence and policy controls: the charter interview and compiler, pack levels, and agent-profile activation.

{#abstract-base-profile}

## Abstract Base Profile

An agent profile that is referenced via a `specializes_from` lineage edge but is not itself activated in the project charter. It acts as a shared-element store that concrete profiles inherit from; it is not directly selectable as a runtime persona. Resolving a profile whose lineage traverses an abstract base (an ancestor not in the activated set) yields a lineage warning rather than an error.

{#activated-vs-available-profile}

## Activated vs Available Profile

An available profile is any agent profile present in a layer of the charter offering (built-in, org pack, or project layer). An activated profile is an available profile that the project charter has explicitly turned on via `activated_agent_profiles`. Three-state semantics: when the key is absent, every available profile is activated; an explicit empty set activates none; an explicit set activates only the listed IDs. Only activated profiles are directly selectable by default surfaces; available-but-not-activated profiles surface only under `--all`/`--show-available`.

{#activation-chokepoint}

## Activation Chokepoint

The single activation filter applied by `charter.activation.resolver.ActiveCharterService` (the activation-aware wrapper). It enforces the project charter's per-kind activation state on a doctrine service's `paradigms`, `procedures`, and `agent_profiles` surfaces, so every profile-resolving path passes through one consistent filter rather than re-implementing activation logic. The factory `charter.activation.active_charter_service_builder.build_active_charter_service` is the single construction seam that routes callers through this chokepoint.

{#adr-architectural-decision-record}

## ADR (Architectural Decision Record)

Immutable record of a significant technical/domain decision, with context and consequences.

{#charter-compiler}

## Charter Compiler

The processor that takes the HiC's interview answers and their selected doctrine artifacts, and combines them into a finalized charter document and supporting governance files.

{#charter-interview}

## Charter Interview

A guided question-and-answer process that walks the HiC through their project's preferences, constraints, and artifact selections. Answers are saved to `answers.yaml` and used to compile the charter.

{#clarification-burst-policy}

## Clarification Burst Policy

Rule that limits clarification interruption by prioritizing highest-impact conflicts first and capping prompt count per burst.

{#glossary-strictness-policy}

## Glossary Strictness Policy

Governance rule for how semantic conflicts are treated (`warn` vs `block`) under each strictness mode.

{#governance-resolution}

## Governance Resolution

The result of checking the HiC's active charter against the charter offering — confirming that the referenced paradigms, directives, and tools actually exist and are compatible with each other.

{#pack-level}

## Pack Level

The tier a Charter Pack occupies, declared by the `pack_level` field in the pack's `pack.yaml` — one of `built-in`, `org` or `project`, in ascending precedence (ADR 2026-10-11-2). The level is no longer inferred from where a pack is wired in; a pack states its own level. The three level names have one authority, `kernel.pack_tiers`, and the tier token and the provenance/layer value share a single spelling, `built-in` (the former no-hyphen `builtin` provenance spelling is folded away). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`), and a `pack.yaml` that omits the field (`PACK_LEVEL_MISSING`); for one release an org pack with no `pack.yaml` loads with a deprecation warning that names the file to add, before the refusal takes effect.

{#precedence-rule}

## Precedence Rule

Ordering used when policy settings conflict.
