---
title: "ADR-0052: Procedures tightened, and anti-patterns as authored artifacts"
description: "The procedure kind stays with a tightened definition and a re-homing list; anti-patterns become authored files with a severity, referenced through rejects."
doc_status: draft
updated: 2026-11-02T00:00:00Z
decision_status: 'Proposed'
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - plans/pack-ecosystem-work-breakdown
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0049-glossary-contexts-and-terms
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - architecture/logical-model
  - plans/pack-ecosystem-design-notes
audience:
  - profile: pack-author
    guidance_text: Read this for what a procedure may and may not be, and for how to author an anti-pattern and reject it from guidance.
  - profile: governance-lead
    guidance_text: Read this for which shipped procedures move to another kind or pack, and how anti-patterns are curated.
  - profile: charter-service-contributor
    guidance_text: Read this before changing the procedure schema or binding the anti_pattern kind.
---

# ADR-0052: Procedures tightened, and anti-patterns as authored artifacts

## Status

**Proposed**, 2026-11-02. Decider: Stijn Dejongh (operator). Formalizes the operator rulings of 2026-11-02 on
[#150](https://github.com/spec-kitty/spec-kitty-charter/issues/150) (procedures) and
[#154](https://github.com/spec-kitty/spec-kitty-charter/issues/154) (anti-patterns, AP1 to AP4), recorded in the
[pack ecosystem design notes](/plans/pack-ecosystem-design-notes/) (sections 5 and 6), on epic
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

If accepted, it **supersedes** [ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/)
(Proposed) decision 3 ("anti-patterns: entries, not a kind"), and **supersedes in part**
[ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) (Accepted) decision 4, where an anti-pattern is
a graph node only and a standalone file "must not exist". If decision 14 is ruled as recommended, it also
**supersedes in part** [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) (Accepted) R14, for the
`anti_pattern` kind. ADR-0043 decision 2 (the procedure shape) stands, with the
changes below.

## Context

- **Procedures.** The built-in pack ships 22 procedures (spec-kitty-open-packs `1060bfc`). Some are a technique
  inside one step, as intended; others are deliverable-producing workflows, standing rules, or state machines. With
  mission types as declared state machines
  ([ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/)) and the `lifecycle` kind, the
  misfits have a better home. #150 asked whether the kind survives at all.
- **Anti-patterns.** Today an anti-pattern is an inline entry of a styleguide or a procedure, or a graph node with no
  file. The census counts **165 inline entries in 40 built-in files** (85 in 24 styleguides, 80 in 16 procedures),
  and 228 in 54 files across all packs. That corrects the notes' "131 blocks in 18 styleguides and 16 procedures".
  `anti_pattern.graph.yaml` also lists 13 nodes with no file at all.
- The 3.x squad kept anti-patterns inline because a graph node could not hold a description. Under the sanitized
  storage shapes (ADR-0043) a file can, so that reason no longer holds.
- Core renders a hardcoded anti-pattern checklist in review prompts instead of the activated ones, and has no
  severity enum (its "smell" marker is free text).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### The procedure kind stays, with a tightened definition (#150, ruled)

1. **`procedure` stays a kind** (option 1 of #150).
2. **A procedure is a repeatable technique used inside one step.** The definition is tightened with three refusals:
   - **it may not produce the Mission's deliverables.** A procedure that ends in a deliverable is a mission type or
     a step;
   - **it may not span steps.** A procedure that starts in one step and ends in another is a mission type, a step's
     actions, or a lifecycle;
   - **it may not be a rule.** A standing obligation ("at least once per release") is a directive, with tactics for
     the technique.
3. **The check.** What a schema can see is already guaranteed: `procedure.schema.json` is closed, has no expected
   output field, admits no `action:` reference, and requires an entry and an exit condition (ADR-0043 decision 2).
   The schema keeps it so; no new load refusal is needed. The three refusals above are a curation check, applied by
   review against a written checklist in the pack-authoring guide.
4. **Every procedure may carry `rejects` references to anti-patterns** (decision 9), replacing its inline
   `anti_patterns`.

### Re-homing the shipped procedures (ruled list; census corrections proposed)

5. **Fit as procedures** (ruled, by title and description): `disciplined-defect-diagnosis`, `test-first-bug-fixing`,
   `refactoring`, `example-mapping-workshop`, `bdd-scenario-lifecycle`, `event-storming-discovery`,
   `domain-aware-decision-interview`, `situational-assessment`, `legacy-codebase-triage`,
   `documentation-gap-prioritization`, `drill-down-documentation`, `adversarial-squad-deployment`,
   `development-assist-test-cleanup`, `post-merge-arch-gate-adjudication`, `glossary-maintenance-workflow`.
6. **Re-homed** (ruled; the census confirms each against the bodies):
   - `spike-timebox-policy` and `mission-tracer-files` become directives, with tactics for the technique;
   - `issue-triage-state-machine` becomes a `lifecycle`;
   - `mission-wrap-up-sequence` becomes an on-demand mission-type step or its own mission type;
   - `onboard-external-agent-to-pack` and `migrate-project-guidance-to-spec-kitty-charter` become small mission types
     (both produce pack artifacts);
   - `meeting-minutes-pipeline` becomes a small mission type, or a procedure of the `writing` pack if it is cut down
     to the technique.
   - internal-pack candidates (ruled as candidates): `mission-tracer-files`, `mission-wrap-up-sequence`,
     `post-merge-arch-gate-adjudication`, and possibly `adversarial-squad-deployment`.
7. **Census corrections, proposed for operator confirmation** (the ruling sorted by title and description; the
   census read the bodies). None is applied until ruled:
   - five of the "fit" procedures break the tightened definition in their bodies and would be re-homed in
     curation: `situational-assessment` and `legacy-codebase-triage` end in deliverables (a canvas and prestudy, a
     reviewed triage report); `bdd-scenario-lifecycle` spans steps; `drill-down-documentation` reads as a styleguide
     rule; `documentation-gap-prioritization` carries a standing rule;
   - `development-assist-test-cleanup` is step 2 of `mission-wrap-up-sequence` and would move with it, out of the
     "fit" list and into the internal-pack candidates;
   - `post-merge-arch-gate-adjudication` and `adversarial-squad-deployment` are product guidance (they drive the
     product's own lanes and CLI; the internal pack already holds the team-specific refinement), so they would leave
     the internal-pack candidates.

### Anti-patterns as authored artifacts (#154, ruled)

8. **AP1, its own file.** An anti-pattern is authored as its own artifact, kind `anti_pattern` (operator token
   `anti-pattern`), at `anti_patterns/<id>.anti-pattern.yaml`. Proposed shape, closed:

   | Field | Required | Shape |
   |---|---|---|
   | `schema_version` | yes | `"1.0"` |
   | `id` | yes | kebab-case, the file stem |
   | `title` | yes | string |
   | `description` | yes | string |
   | `severity` | yes | `smell`, `severe` or `breaking` |
   | `bad_example` | no | string |
   | `good_example` | no | string |

9. **References.** Guidance rejects an anti-pattern with a `rejects` reference `{type: anti_pattern, id, reason}`,
   held in a `rejects` list of its own, beside `references`, with the same shape on every guidance kind. Where a
   kind's references mint relationships today (directive, tactic, paradigm), and for the styleguide, whose
   relationships are always empty today (FR-013), a `rejects` entry mints the existing
   `Relation.rejects`, never `suggests`; where they mint none until
   [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189) rules (procedure, mission type and step), it is
   kept verbatim like them. Today a `references` entry of `type: anti_pattern` is minted as `suggests`, which the
   cascade follows, so it would activate the anti-pattern; such entries migrate into `rejects`. Styleguides,
   procedures, directives, tactics, paradigms, mission types and steps may carry `rejects`.
10. **AP2, severity** is a three-level ladder: `smell` (worth a remark), `severe` (should block review), `breaking`
    (must block). The same ladder is used by the `gate` kind (ADR-0050 decision 14), to align with core's
    (spec-kitty/spec-kitty#3416).
11. **AP3, placement.** An anti-pattern lives in the pack whose guidance rejects it. The foundation pack holds the
    general ones. A `rejects` across packs needs the target's pack in `depends_on`
    ([ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)).
12. **AP4, glossary `avoid` stays separate** from anti-patterns
    ([ADR-0049](/adr/0049-glossary-contexts-and-terms/) decision 16): `avoid` is about words, an anti-pattern about
    practice, with different consumers, shapes and activation. A later `see_also` from an anti-pattern to term URNs is
    additive.
13. **Activation.** An anti-pattern is activatable, as the logical model already says; activating the guidance that
    rejects it does not activate it on its own.
14. **Graph-fragment anti-pattern nodes** (recommended): after the migration, an `anti_pattern` node declared only in a
    graph fragment, with no file, is refused. That supersedes in part
    [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) R14 for this kind, at acceptance.

### Migration and curation

15. **The inline entries are migrated out and deduplicated** in a curation pass. Matching by name merges almost
    nothing (165 to 164); merging by meaning, read by hand, gives about ten clusters over about 27 entries, so
    roughly **150 distinct built-in anti-patterns** (about 205 across all packs). The 13 file-less graph nodes seed the
    general set in the foundation pack. Severity is assigned per anti-pattern in curation; the default is `smell`.
16. **Order.** The kind and its schema land first; the migration runs by the service's own writer, then a re-pin, as
    ADR-0043 and ADR-0044 did. The procedure and styleguide schemas drop `anti_patterns` in the same contract-family
    change.

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- An anti-pattern has one home, a severity and an address; review prompts can render the activated ones.
- Duplicates across styleguides and procedures collapse to one entry each.
- The procedure kind means one thing, so a pack author knows where a workflow belongs.

### Negative

- About 150 new files in the built-in tree, and a curation pass over 165 entries and 22 procedures.
- Two schema changes (procedure, styleguide) that drop a field, breaking on the 0.x line.

### Risks

- The tightened definition is partly judgment. Without the written checklist it will be applied unevenly.

### Follow-ups

Nothing below is applied in this record.

- **Pack schemas** (#114): `anti-pattern.schema.json`; a `rejects` list on directive, tactic, paradigm, procedure,
  styleguide, the mission type and its steps; `anti_patterns` removed from `procedure.schema.json` and
  `styleguide.schema.json`; `file-kinds.yaml` rules (`*.anti-pattern.yaml`, and the kind in the artifact kind
  list); the `pack-manifest` constituent kind enum.
- **Contract, additive** ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)): the kind's
  read paths.
- **Contract, breaking on a 0.x minor** (a `### Breaking` entry, ADR-0022): `antiPatterns` leaves the `Styleguide`
  response (where it is required) and the `StyleguideRegistration` request, `StyleguideAntiPattern` and the example
  `Artifact.styleguide.yaml` retire; `Styleguide`'s "relationships always empty" invariant (FR-013) ends once
  `rejects` mints; the `Artifact` `oneOf` gains the kind.
- **Kernel facts** (`ArtifactKind`): `anti_pattern`'s documentation ("never a standalone file") and its absence from
  `CHARTER_KIND_TOKENS` change; the logical model's kind-table note follows at acceptance.
- **spec-kitty-open-packs**: the curation pass (re-homing, the anti-pattern migration and dedupe).
- **Core** (can come before the cutover): render the activated anti-patterns in review prompts instead of the
  hardcoded checklist.

## Open questions

Recommended defaults the operator rules on.

1. **Census corrections** (decision 7): take all three: re-home the five procedures in curation, move
   `development-assist-test-cleanup` with the wrap-up sequence, and keep `post-merge-arch-gate-adjudication` and
   `adversarial-squad-deployment` as product guidance.
2. **The anti-pattern shape and layout** (decision 8): as proposed there.
3. **Graph-fragment anti-pattern nodes** (decision 14): refused after the migration.
4. **Default severity of a migrated entry** (decision 15): `smell`, raised in curation.

## Alternatives considered

### Retire the procedure kind (#150 option 2)

Rejected by the operator. About half the shipped procedures are genuine in-step techniques with no better home.

### Keep anti-patterns inline (ADR-0043 decision 3)

Rejected by the operator. It duplicates entries, gives them no address or severity, and its reason (a graph node
could not hold a description) no longer holds.

### Merge glossary `avoid` into anti-patterns

Rejected (AP4). Different consumers, shapes and activation.

## References

- [Pack ecosystem design notes](/plans/pack-ecosystem-design-notes/), sections 5 and 6.
- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142); #150; #154.
- spec-kitty-open-packs at `1060bfc` (the census); core spec-kitty/spec-kitty#3416 (severity ladder).
