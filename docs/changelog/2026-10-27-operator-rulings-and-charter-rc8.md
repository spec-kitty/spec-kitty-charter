---
title: Round-trip fidelity accepted, and the charter moves to rc8 synthesis
description: ADR-0036 is accepted with its open questions answered, PROJECT_002's round trip reads as equality of meaning, and the plans record the operator rulings of 2026-10-27.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - plans/milestones
  - plans/roadmap
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the round trip now promises, the charter's new tactics, and where the new epics sit.
  - profile: governance-lead
    guidance_text: Read this for the amended PROJECT_002 wording and the two tactics added to the charter.
---

# 2026-10-27 — Round-trip fidelity accepted, and the charter moves to rc8 synthesis

The operator answered the open decisions of the day. This entry records what changed in the
repository as a result.

## What changed

- **ADR-0036 is accepted.** A round trip compares meaning, not bytes. A file waiting on an
  upstream pack fix goes on a self-expiring known-failure list, and the manifest hash question
  is decided in #195.
- **PROJECT_002 is amended.** Open-packs YAML round-trips "without loss of meaning
  (ADR-0036)": formatting and comments may change, and data may not. The project charter was
  brought to the rc8 synthesis shape to make the change:
  - each directive was registered twice, and the duplicates are gone;
  - PROJECT_001's slug is now `project-intent-directive`;
  - each project directive has a "how we apply" tactic.
- **The plans follow the rulings:**
  - cutting 0.1.0 is held;
  - the activation set is 0.2.0, and `GET /openapi.yaml` rides it;
  - a new Infra & Enablers milestone holds the contract-access, Java-client and
    hosted-configuration epics, and the hosted multi-instance spike (#233) moved to 4.x cutover;
  - hosted tenancy is an open question.

## Who is affected

Contributors: the whole-pack gate (#187) compares meaning and keeps its known-failure list. A
pack edge's authoritative copy is the graph document (#240). The rulings on the three new epics
are recorded on #207, #217 and #226.
