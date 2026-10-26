---
okf_version: "0.2"
title: The charter's concepts, user journeys and the decisions behind them
description: The repository documents what the charter's concepts mean before the domain model is redesigned, adding a conceptual model, eleven user journeys, scenarios and seven decisions.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0029-charter-glossary-is-owned-here
  - context/conceptual-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the conceptual model, the journeys the service must support and the decisions that shape the coming domain model.
---

# 2026-10-26 — The charter's concepts, user journeys and the decisions behind them

**You can now read what the charter's concepts mean, not just how the service behaves.** Before, the glossary was imported from core and there was no single page explaining the concepts, the journeys a person takes, or why the model is shaped as it is. Now the repository owns its glossary ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)) and adds a [conceptual model](/context/conceptual-model/), eleven user journeys with the service operations each one needs, matching scenarios in the contract archive, and seven decisions (ADR-0028 to ADR-0034) — recorded before the domain model, DTOs and storage are redesigned.

Two things change for consumers. The `impacts` relation is now an artifact's per-value list and `tension` names the relationship between two rules ([ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/)); the older ADRs it supersedes carry an inline note at each changed point. And scenarios whose service surfaces do not exist yet are tagged `@WIP` and excluded from the contract suite, so any consumer of the scenario archive must exclude `@WIP` too.

The contract-release rehearsal also stops flaking: its archive check piped `tar` into `grep -q`, which exits at the first match and killed `tar` with SIGPIPE under `pipefail` once the archive held the journey files. It now reads the whole listing.
