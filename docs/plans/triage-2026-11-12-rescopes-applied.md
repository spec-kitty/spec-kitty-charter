---
okf_version: "0.2"
title: Re-scopes applied, 2026-11-12
description: Which of the 2026-11-02 re-scope proposals were written into issue bodies on 2026-11-12, what changed from the draft because main moved on, and the open-packs issue left to file.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/triage-2026-11-02-rescopes
  - plans/milestones
audience:
  - profile: governance-lead
    guidance_text: Check what was applied, and file the open-packs draft at the end of the page.
  - profile: charter-service-contributor
    guidance_text: The issue bodies listed here are current; read the issue, not the 2026-11-02 proposal.
---

# Re-scopes applied, 2026-11-12

On 2026-11-12 the operator approved a set of the [2026-11-02 re-scope proposals](/plans/triage-2026-11-02-rescopes/),
and an Op (planner profile, `01M77A6WPWR41FF6SQP8V1E9P4`) wrote them into the issue bodies and titles. Each issue
got a comment saying what changed and why. No code changed. Before it was written in, each proposal was checked
against `main` at `fd20faa`. Where `main` had moved on since 2026-11-02, the issue follows `main`, as the table
below says.

The rule applied throughout is the charter's: the charter's model is canonical, and core aligns at incorporation
(`charter.md`, "Designing during the rewrite"). So no issue keeps a core dependency or a core acceptance criterion.

## Applied

| Issue | Title now | What changed |
|---|---|---|
| #14 | Java charter (8/8): Read shadow | Optional, diagnostics-only shadow route in core's proxy; the corpus criteria dropped (ADR-0059 D1). Now `priority:P3`, Post-cutover. |
| #15 | Nightly gating and WIP passes of the contract scenarios, with Allure results | Two nightly passes of the existing `@contract` scenarios plus Allure; no new runner. |
| #16 | Epic: Creeds and Values | Authorities ADR-0014 and ADR-0028; core B1, B2 and C dropped; #82 in the ordered steps; #152 listed as Backlog research. |
| #17 | Creeds and Values (1/7): Plan page mapping the value-layer increments to this repository | Narrowed to the plan page; D-1 and D-5 are done; it names an owner for D-6. |
| #18 to #22 | unchanged | Authorities ADR-0014 and ADR-0028; core Mission C coordination dropped from #18; ADR-0014 rules added where they apply. |
| #23 | unchanged | Core B1 dropped; reads #20's per-value `impacts` lists; D-6 ruled here; waits on #82. |
| #82 | unchanged | Rewritten to the per-value `impacts` list (ADR-0028 d1). |
| #152 | unchanged | `Parent:` line now #16; stays in Backlog. |
| #42 | Select an agent profile by role in the service | The selection order of ADR-0069, the fallback of ADR-0068, ADR-0032's two open questions kept. |
| #61 | Pack-author how-to: open, extendable and locked in policy.yaml | Core doc edits and glossary terms dropped. |
| #69 | Epic: the 4.x cutover, a per-operation switch to the service | Gate text is ADR-0059 D1; charter-side steps only. |
| #154 | The anti_pattern kind: its schema and refusals (ADR-0052) | Charter half only; curation moves to open-packs (draft below). |
| #157 | Trust policy for executable pack assets, and SBOMs | Core trust baseline (spec-kitty#2540) dropped; the #143 order recorded. |
| #158, #159 | unchanged | Core trust baseline dropped; order #158, then #157 with #40, then #159. |
| #163 | unchanged | Core acceptance criterion dropped. |
| #164 | Mission-type requirement kinds: the declaration in the mission-type schema | Narrowed to requirement kinds. |
| #205 | Refresh class-diagram and c4-components PlantUML: kernel move, graph-only edges and the new slices | Widened to ADR-0064 and the profiles, missions and activations slices; stays P3. |

No other priority or milestone changed.

## Where main had moved on since the draft

- **#154:** the draft asked for a `rejects` list on every guidance kind. [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)
  decision 12 retired that list before it was built, so the issue asks for `rejects` edges in the pack's graph
  documents.
- **#205:** the draft named the profiles and missions slices; `main` now also has an `activations` slice, and the
  class diagram still gives artifacts `references()` and `relationships()`, which ADR-0064 removes.
- **#16 and #152:** the draft split the epic with #77, which is now closed. #16 says the `tension` relationship is
  outside the epic, and #152's `Parent:` line names #16.
- **#69:** the draft listed core-side steps. The 2026-11-11 triage moved them to #361, so the epic lists the
  charter-side steps only, with the gate of [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1.
- **#42:** ADR-0068 and ADR-0069 decided the fallback profile and the primary and alternative roles after the
  draft. ADR-0032's two open questions (ties; how a role pin combines with the order) are still open.
- **#61:** the policy file and its schema are on `main` ([ADR-0063](/adr/0063-pack-mutability-policy-file/)), so
  the dependency on #57 is dropped.

## Decided during the Op

- **#82, the anti-pattern vector** (operator, 2026-11-12): an anti-pattern authors its own `impacts` list. Nothing
  is derived from the guidance that rejects it, so the sign-flip rule is gone.

## Left to file: open-packs anti-pattern curation

The Op had no access to `spec-kitty/spec-kitty-open-packs`. This is the draft, `open-packs#TBD-anti-pattern-curation`,
for the orchestrator to file and link from #154.

> **Curate the built-in anti-pattern corpus into `anti_pattern` artifacts (ADR-0052 d15)**
>
> Migrate the inline `anti_patterns` blocks of the styleguides and procedures into `anti_pattern` files, one per
> artifact, once the charter kind lands (spec-kitty-charter#154).
>
> - Merge entries by meaning, read by hand: ADR-0052 decision 15 counts about ten clusters over about 27 entries,
>   for about 150 distinct built-in anti-patterns.
> - Assign each one a `severity` (`smell`, `severe` or `breaking`); there is no default (ADR-0053 d32).
> - Write a `bad_example` for each one; it is required (ADR-0053 d30).
> - Place each in the pack whose guidance rejects it; the general ones go in `foundation` (ADR-0052 d11).
> - Replace the inline blocks with `rejects` edges in the pack's graph documents (ADR-0064 d12), and give the 13
>   file-less graph nodes a file (ADR-0053 d31).
> - Optional: give an anti-pattern its own `impacts` list (spec-kitty-charter#82).
