---
title: Why the charter offering matters to an organisation
description: How the charter offering gives an organisation centrally governed, harness-independent agent behaviour that each project and repository composes to its own needs.
doc_status: draft
updated: 2026-10-17
type: Context
kind: Explanation
related:
  - context/product
  - context/system-context
  - plans/overhaul-approach
  - architecture/behaviour-and-scenarios
audience:
  - profile: governance-lead
    guidance_text: Read this for what the charter offering gives a team and why it is governed centrally.
  - profile: individual-developer
    guidance_text: Read this for how the charter composes to a single project's needs.
  - profile: non-engineer-developer
    guidance_text: Start here; this page explains the charter offering's value without engineering detail.
  - profile: core-maintainer
    guidance_text: Read this for the boundary the offering owns and what it deliberately leaves out.
---

# The charter offering

The **charter offering** is the read side of the charter: given the Charter Packs a project
draws from and what the project has activated, it answers one question — *what governs this
step?* — and returns the directives, tactics, styleguides, procedures, agent profiles and
glossary for one action. It is the subsystem this repository is built to own. This page is
about why that subsystem is worth extracting and running on its own; [Product](/context/product/)
covers what the charter is and where it sits among the product lines.

## Centrally governed, distributed execution

Agent behaviour is **configuration**, not code: the rules an organisation wants its agents to
follow are authored once, as doctrine, and distributed to every project that adopts them. The
offering is what makes that authored doctrine *in force* at the point of work:

- **One authored source.** Directives, tactics, styleguides, procedures, profiles and glossary
  live in Charter Packs — built-in, organisation and project — authored in
  `spec-kitty-open-packs`, not scattered through each repository's own setup.
- **Governed from the centre.** An organisation ships its standards in an org pack; every
  repository that registers it inherits them. Changing the standard in one place changes what
  governs every adopting repository, without editing each one.
- **Executed at the edge.** The offering resolves that doctrine locally, per worktree, into the
  guidance for the step an agent is actually doing. Central authority, local execution, with no
  round-trip to a server the work depends on.

Without a single answer to *what governs this step?*, authored governance drops silently
somewhere between the pack and the agent. The offering exists so there is one answer, derived
from one source, and a failure to produce it is reported rather than swallowed.

## Independent of the agent harness

The guidance the offering returns is **harness-independent**. The same resolved charter feeds
Claude Code, Codex and any other agent harness, because what it returns is doctrine, not
tool-specific wiring. An organisation's behaviour configuration is therefore not locked to one
vendor's agent: the charter is the stable surface, and the harness is swappable beneath it. This
is the reason the offering is being extracted behind a contract — so that every caller, whatever
harness it runs, reaches the same answer across one published surface rather than through a
particular tool's internals.

## Mix-and-match per project and per repository

Charter Packs are **layered** — built-in, then organisation, then project — and a project
**activates** only the entries it wants. That gives each repository a composition of its own:

- A repository takes the built-in base, adds the org packs its team mandates, and selects
  per-entry what applies to its work (`spec-kitty charter activate`), leaving the rest inert.
- A monorepo and a single-service repository in the same organisation can draw on the same org
  doctrine yet put different subsets in force.
- An org pack can **require** a rule for every adopter while still letting a project add or
  override with its own, and unsound compositions are refused with a reason rather than quietly
  accepted.

The result is governance that is consistent where it must be and tailored where it should be —
the same doctrine, composed to each project's and repository's needs.

## Why this matters

- **Consistency with autonomy.** Teams share standards without hand-copying configuration into
  every repository, and each repository still tunes what applies.
- **Drift control.** One authored source and one resolution answer mean a standard cannot be
  half-applied in one repository and current in another by accident.
- **Onboarding.** A new repository inherits the organisation's agent behaviour by registering a
  pack, not by reconstructing a setup.

## The boundary this repository owns

The offering **answers questions**; it does not author the doctrine (that is
`spec-kitty-open-packs`) and it does not write events (that is the core CLI's one canonical event
writer — see [Events and the ledger](/architecture/events-and-ledger/)). Its role and boundary
are defined behaviourally, by the promise-first acceptance scenarios in
[Behaviour and acceptance scenarios](/architecture/behaviour-and-scenarios/), which this
repository treats as the contract to specify before any implementation is chosen.
