---
okf_version: "0.2"
title: "Governance"
description: "Glossary terms for the Governance context."
kind: Glossary
glossary_context: "Governance"
doc_status: active
generated:
  by: "doc-kitty-glossary/0.5.0"
  at: "2026-10-27T09:25:39Z"
  content_hash: "sha256:a467bff454be"
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

The single activation filter applied by `charter.activation.resolver.ActiveCharterService` (the activation-aware wrapper). It enforces the project charter's per-kind activation state on a charter service's `paradigms`, `procedures`, and `agent_profiles` surfaces, so every profile-resolving path passes through one consistent filter rather than re-implementing activation logic. The factory `charter.activation.active_charter_service_builder.build_active_charter_service` is the single construction seam that routes callers through this chokepoint.

{#adr-architectural-decision-record}

## ADR (Architectural Decision Record)

Immutable record of a significant technical/domain decision, with context and consequences.

{#alt-role}

## Alt-role

A further role a profile can fill besides its primary role; for example a Python implementer that can also review.

**Meta:**

- **status:** `canonical`

{#charter-compiler}

## Charter Compiler

The processor that takes the HiC's interview answers and their selected charter artifacts, and combines them into a finalized charter document and supporting governance files.

{#charter-interview}

## Charter Interview

A guided question-and-answer process that walks the HiC through their project's preferences, constraints, and artifact selections. Answers are saved to `answers.yaml` and used to compile the charter.

{#clarification-burst-policy}

## Clarification Burst Policy

Rule that limits clarification interruption by prioritizing highest-impact conflicts first and capping prompt count per burst.

{#creed-project}

## Creed (project)

The project's own voice: prose telling agents the intent, plus value weights with rationales for heuristics. Stored through the register API with the project's active charter. Distinct from a profile's ethos.

**Meta:**

- **status:** `canonical`

{#ethos}

## Ethos

A profile's self-story: prose about what the profile is about and what it values, not a list of weights. Distinct from the project creed. A profile's opening statement is derived from its purpose, specialization, context and ethos, not stored.

**Meta:**

- **status:** `canonical`

{#fallback-profile}

## Fallback profile

The profile the service returns, marked as a fallback, when no profile fits the role a step asks for. A pack declares it; built-in ships a role-neutral, locked one, and a derived pack may declare a more specific one but cannot remove built-in's. The service hard-codes only the rule. With no fallback available it refuses with a clear not-found message.

**Meta:**

- **status:** `canonical`

{#glossary-strictness-policy}

## Glossary Strictness Policy

Governance rule for how semantic conflicts are treated (`warn` vs `block`) under each strictness mode.

{#governance-resolution}

## Governance Resolution

The service resolves the project's active charter against an activation context and returns the artifacts that apply to that step (directives, profile, prompts and the rest).

{#hosted-deployment}

## Hosted deployment

The charter service run centrally; the CLI is configured with its endpoint and a standard login token and fails closed with a clear error when the host is unreachable. Self-hosting for one team is open-core; managed, multi-tenant hosting with cross-project reporting is commercial (part of the Brain Kitty line).

**Meta:**

- **status:** `canonical`

{#local-deployment}

## Local deployment

The charter service spawned by the CLI on the developer's machine; the CLI connects to it and keeps working offline. Part of open-core.

**Meta:**

- **status:** `canonical`

{#opening-statement}

## Opening statement

How a profile introduces itself to an agent at the start of work. Derived from the profile's purpose, specialization, applicable context and ethos, and computed for clients, not stored.

**Meta:**

- **status:** `canonical`

{#operator-role}

## Operator (role)

The role performed by a human, the Human in Charge; a role like any other. A profile can be marked as performed by a human. In a procedure it marks where to ask the human for input or action; a step (for mission steps: sensible, not yet needed) or a role pin can route work, such as every review, to a human.

**Meta:**

- **status:** `canonical`

{#pack-level}

## Pack Level

The tier a Charter Pack occupies, declared by the `pack_level` field in the pack's `pack.yaml` — one of `built-in`, `org` or `project`, in ascending precedence (ADR 2026-10-11-2). The level is no longer inferred from where a pack is wired in; a pack states its own level. The three level names have one authority, `kernel.pack_tiers`, and the tier token and the provenance/layer value share a single spelling, `built-in` (the former no-hyphen `builtin` provenance spelling is folded away). Loading refuses a pack whose declared level disagrees with where it is configured (`PACK_LEVEL_MISMATCH`), and a `pack.yaml` that omits the field (`PACK_LEVEL_MISSING`); for one release an org pack with no `pack.yaml` loads with a deprecation warning that names the file to add, before the refusal takes effect.

{#precedence-rule}

## Precedence Rule

Ordering used when policy settings conflict.

{#primary-role}

## Primary role

The one role a profile is chiefly for. In profile selection a profile whose primary role matches the step's role beats one that fills it as an alt-role.

**Meta:**

- **status:** `canonical`

{#profile}

## Profile

An agent profile: a named specialist who fills one primary role and optionally alt-roles, declares the context it fits, carries an ethos, and may specialize another profile. Steps never name a profile; the service selects one for the role a step asks for.

**Meta:**

- **status:** `canonical`

{#profile-selection}

## Profile selection

How the service picks the profile for a step: role fit first (primary role beats alt-role), then specialization (the `specializes_from` lineage plus `routing-priority`) among profiles whose declared context fits, then the declared fallback profile, else a not-found refusal. A role pin overrides the choice for its role.

**Meta:**

- **status:** `canonical`

{#profile-tie}

## Profile tie

Two or more equally fitting profiles after profile selection. There is no hidden tie-break; whether the service returns all candidates for the client to choose, or picks one at random with a warning, is still open (ADR-0032).

**Meta:**

- **status:** `proposed`

{#role}

## Role

What a mission step asks for and a profile fills, such as implementer, reviewer or planner. Roles are the only link between mission types and profiles. They are extensible by packs and become known when their pack is registered; a derived pack can remove or lock a role like an artifact. A profile has one primary role and optional alt-roles.

**Meta:**

- **status:** `canonical`

{#role-pin}

## Role pin

A derived pack's setting that fixes the profile filling a role: every step asking for that role gets the pinned profile, whatever the mission type. Profiles that specialize the pinned one also fit. Role pins, role locks and other integrity settings live in a policy file at the pack root.

**Meta:**

- **status:** `canonical`

{#upgrade-pack-version}

## Upgrade (pack version)

Moving a project to a newer version of an offering pack. Opt-in per project: the service holds versions side by side, and the project moves when it re-registers or updates its active charter, which is the acknowledgement. A new version reaches the service through its API, verified against the pack checksum. An upgrade never writes the active charter; newly required or locked items apply on the next read, vanished items are reported as skipped, deactivations persist. An outdated project gets a notice with every activation response.

**Meta:**

- **status:** `canonical`

{#write-through-active-charter}

## Write-through active charter

The project's local `activation.yaml` is the source of truth for its active charter and acts as a write-through cache: a change to it results in a call to the service, whose copy answers activation and resolution requests. On a cold start the two are compared; when they disagree, local wins visibly (the CLI shows the difference and warns before re-registering). The hosted side never changes a project's charter on its own.

**Meta:**

- **status:** `canonical`
