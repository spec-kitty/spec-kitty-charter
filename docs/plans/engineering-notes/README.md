---
okf_version: "0.2"
title: Engineering notes
description: Point-in-time engineering assessments and findings, dated, that feed decisions and issues rather than state conventions in force.
doc_status: draft
updated: 2026-11-14T00:00:00Z
type: Plan
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Read a note for the evidence behind a recommendation; the decision it feeds lands in an ADR or an issue.
  - profile: governance-lead
    guidance_text: Read these for dated assessments and the operator rulings recorded in them.
---

# Engineering notes

Dated, point-in-time assessments. Each note records what was measured, on which revision, and what
it recommends. Decisions it feeds land in a [decision record](/adr/) or an issue. A note's
measurements are not updated as the code moves on; a later assessment gets its own note. A note may
carry dated clarifications, marked with their date, that correct a reading of its findings.

- [Test suite assessment, 2026-11-03](/plans/engineering-notes/test-suite-assessment-2026-11-03/) —
  how good the test suite is against the packs' testing rubric, and the five weaknesses ranked by
  risk. It also gives the SonarCloud recommendation (nightly, report-only) and the operator's rulings.
- [Code and test suite quality assessment, 2026-11-12](/plans/engineering-notes/code-and-test-quality-assessment-2026-11-12/) —
  the 2026-11-03 assessment re-run nine days on, plus a production-code lens: six test gaps proved by
  planted breaks, the slow and timing-sensitive tests, the code hotspots, and four questions for the operator.
- [Service usability run, 2026-11-09](/plans/engineering-notes/service-usability-run-2026-11-09/) — how
  ready the service is for core. It registers the open-packs, activates this repository's charter, and reads a profile, its
  guidance and a mission. It gives the gaps with their issues and the recommended order of the next work.
- [Service usability run, 2026-11-10: mission types](/plans/engineering-notes/service-usability-run-2026-11-10-mission-types/) —
  a client that starts at the API root reaches `software-dev`, its 13 steps, its 5 step contracts and its skeletons,
  from the pack and under an activation. It is the proof of SC-001 of the mission type reads (#99).
- [Service usability run, 2026-11-12: the slice B pin](/plans/engineering-notes/service-usability-run-2026-11-12-slice-b-pin/) —
  at open-packs `1692a3b`, `internal` and `qa` register as they are and the run passes all 146 hops (#466).
- [Open-packs drift watch research, 2026-11-13](/plans/engineering-notes/open-packs-drift-watch-research-2026-11-13/) —
  why the drift watch (#509) stays red on `main` after #545, where its design leaks, what gates what across
  charter and open-packs, and the operator's rulings on each recommendation.
- [Mutation testing no-report research, 2026-11-13](/plans/engineering-notes/mutation-testing-no-report-research-2026-11-13/) —
  why each red mutation run since 2026-11-10 left a module with no report, what main already fixes,
  and the failure class still open: a `test` system property PIT's minion never receives (#461).
- [Service usability run, 2026-11-14: a mission walk under the charter](/plans/engineering-notes/service-usability-run-2026-11-14/) —
  after slices C and D: the profile, the mission type and a paradigm in a pack, then this repository's charter as an
  activation, every `software-dev` step's context and prompt, and the review squad.
- [Usability walk as a BDD test: research, 2026-11-14](/plans/engineering-notes/usability-walk-as-bdd-research-2026-11-14/) —
  whether the usability sequence can become a BDD test: a squad's verdict, the draft scenarios, and the operator's
  ruling that it is a per-PR journey over the example packs, not a nightly.
