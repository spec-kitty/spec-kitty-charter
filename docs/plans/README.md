---
okf_version: "0.2"
title: Plans
description: Forward-looking work for the charter service — the bootstrap roadmap, the questions still open and dated engineering notes that feed decisions.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Find the roadmap and the open questions.
  - profile: core-maintainer
    guidance_text: Find what is planned and what is waiting for a core ruling.
---

# Plans

Future work for this repository. The target design lives in
[Architecture](/architecture/), and settled decisions in [Decision Records](/adr/).

- [High-level approach](/plans/overhaul-approach/) — the behavior-first, strangler-fig shape of
  the overhaul: scenarios and boundary first, then the published OpenAPI contract.
- [Roadmap](/plans/roadmap/) — the bootstrap steps, from the contract skeleton to a shadow
  read, and how they line up with the core's seam work (epic spec-kitty#6099).
- [Milestones](/plans/milestones/) — the tracker's milestones (4.x cutover, post-cutover,
  backlog), what closes each one, and which issue goes where.
- [Open questions](/plans/open-questions/) — decisions this design depends on that nobody has
  made yet, each with a suggested default.
- [Pack ecosystem design notes](/plans/pack-ecosystem-design-notes/) — working rulings of the
  operator design pass on epic #142, formalized as ADR-0048 to ADR-0052 (Accepted).
- [Pack ecosystem work breakdown](/plans/pack-ecosystem-work-breakdown/) — the ordered, sized work for epic
  #142, the open-packs and core follow-ups, and the open decisions with recommended defaults.
- [Issue triage, 2026-11-02](/plans/triage-2026-11-02/) — every open issue typed, prioritized, parented and
  given a milestone after the pack-ecosystem rulings, with the decisions left to the operator.
- [Re-scope proposals, triage 2026-11-02](/plans/triage-2026-11-02-rescopes/) — the proposed new scope of each
  issue the triage marked for re-scoping, for the operator to approve before any issue body changes.
- [Re-scopes applied, 2026-11-12](/plans/triage-2026-11-12-rescopes-applied/) — which proposals were written into
  the issue bodies, what changed from the draft, and the open-packs issue left to file.
- [Next slices after the 2026-11-11 triage](/plans/next-slices-2026-11-11/) — slice B next, with its two
  rulings first, slice C in parallel, and six quick-win slices, each with its tracking issue.
- [Next slices after the 2026-11-12 slice review](/plans/next-slices-2026-11-12/) — where slices A to E stand, and
  the slices that follow C and D (B close-out, A2, F, G) in their approved order.
- [Charter example packs](/plans/charter-example-packs/) — the plan for ADR-0070: charter-owned example
  packs replace the open-packs corpus in charter's tests.
- [Engineering notes](/plans/engineering-notes/) — dated, point-in-time assessments that feed decisions and
  issues, starting with the [test suite assessment of 2026-11-03](/plans/engineering-notes/test-suite-assessment-2026-11-03/).
