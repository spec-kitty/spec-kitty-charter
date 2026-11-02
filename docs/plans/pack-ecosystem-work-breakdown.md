---
title: Pack ecosystem work breakdown
description: "Ordered, sized work for epic #142 after ADR-0048 to ADR-0052: charter schema and service work, the open-packs split and curation, and the core follow-ups."
doc_status: draft
updated: 2026-11-02T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/pack-ecosystem-design-notes
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0049-glossary-contexts-and-terms
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0051-event-registry-a-thin-provider-neutral-consistency-check
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
audience:
  - profile: governance-lead
    guidance_text: Read this for the order the pack-ecosystem work lands in and the issues proposed for it.
  - profile: charter-service-contributor
    guidance_text: Read this to pick up a slice of the pack-ecosystem work and see what it waits for.
  - profile: core-maintainer
    guidance_text: Read the core follow-up list for what core changes after the cutover, and in which order.
---

# Pack ecosystem work breakdown

The work that follows from [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) to
[ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/), all Proposed on 2026-11-02,
for epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142). The rulings are in the
[design notes](/plans/pack-ecosystem-design-notes/). Nothing here is filed yet: the "proposed issue" titles are for
the operator to file or merge into existing children.

Sizes: **S** is up to a day, **M** a few days, **L** a Mission of a week or more. Contract-family items (they change
`contracts/`) land one at a time, as the merge queue requires.

## Ordered work (charter)

| # | Work | ADR | Size | Depends on | Tracker |
|---|---|---|---|---|---|
| 1 | Operator rulings on the open decisions (below); ADRs 0048 to 0052 accepted, and the "superseded in part" notes added to the Accepted records they touch | all | S | none | #142 |
| 2 | `depends_on` and `parent_pack` checks in the pack-descriptor schema; the range grammar | 0048 | M | 1 | #145, #146 |
| 3 | The dependency resolver: ordering, cycles, ranges, dedupe by `pack_id`, sibling refusal; a compatibility shim keeping the single `built-in` tree loading | 0048 | L | 2 | #145 |
| 4 | The `glossary` kind: schema, binding, migration of four glossaries, the two context reads | 0049 | L | 1 | proposed: "glossary contexts and terms" |
| 5 | The `anti_pattern` kind and `rejects`; drop inline `anti_patterns` from procedure and styleguide | 0052 | M | 1 | #154 |
| 6 | The authoring checklist for the tightened procedure definition (the closed schema already refuses outputs and `action:` references) | 0052 | S | 1 | #150 |
| 7 | The `event_registry` kind and the shared `emit` entry shape | 0051 | M | 2 (resolution uses the closure) | proposed: "event registry kind" |
| 8 | The `gate` and `lifecycle` kinds | 0050 | M | 1 | #156; proposed: "lifecycle kind" |
| 9 | Mission-type schema: transitions, guards (closes #155), actions, `work_package_lifecycle`, requirement kinds (#164), `term:` links | 0050 | L | 4, 7, 8 | #148, #155, #164 |
| 10 | Step enhancements and `overrides` checks, with the drift warning | 0050 | M | 3, 9 | #148 |
| 11 | Mission type as an ordinary activatable kind (presets, deny list) | 0050 | M | 9 | #148 |
| 12 | `applies_to_stacks` beside `applies_to_languages` | notes | S | 1 | #163 |
| 13 | Contract sets for the new reads and registrations (glossary, anti-pattern, event registry, gate, lifecycle, embedded lifecycle) | all | M each | the kind it serves | proposed, one per kind |
| 14 | Close #153 (not planned), #161 (moot), #147 (into #146); document the single daemon per worktree (#183) | notes | S | 1 | #153, #161, #183 |

Items 4 to 8 are independent of each other once item 1 is done, and can run in parallel as long as only one of them
changes `contracts/` at a time.

## Ordered work (spec-kitty-open-packs)

Sibling-repository work; draft issues use `open-packs#TBD-<slug>` until the orchestrator files them.

| # | Work | Size | Depends on |
|---|---|---|---|
| P1 | Split `built-in` into foundation and topical members, with `built-in` as a distribution pack (`open-packs#TBD-named-packs-split`) | L | charter 3 |
| P2 | `missions/` to `mission_types/<type>/`, step contracts into the bundles (`open-packs#TBD-mission-types-bundle`) | M | charter 9, with P1 |
| P3 | Stack packs `java`, `python`, `drupal`, `javascript` as opt-in packs (`open-packs#TBD-stack-packs`) | M | P1 |
| P4 | Procedure re-homing and anti-pattern migration with dedupe (`open-packs#TBD-curation-pass`) | L | charter 5, 6, 8 |
| P5 | Glossary migration to contexts; term ids; deprecated terms (`open-packs#TBD-glossary-contexts`) | M | charter 4 |
| P6 | The shipped work-package lifecycle from core's lane table, and an `ai.spec-kitty` event registry (`open-packs#TBD-lifecycle-and-registry`) | M | charter 7, 8 |
| P7 | Fix the internal glossary stem (open-packs#47) | S | none |

## Foundation membership (census proposal)

From the census of spec-kitty-open-packs at `1060bfc`. The operator rules on it (ADR-0048 open question 2).

- **Foundation:**
  - **directives (about 30):** 001, 003, 010, 018, 024, 025, 028 to 035, 040, 041, 043 to 046, 048 to 053,
    `reconcile-change-scope-tensions`, `disciplined-refactoring`, `red-main-release-discipline`;
  - **paradigms (8):** domain-driven-design, specification-by-example, `behaviour-driven-development`,
    deep-module-design, execution-lanes, trunk-based, git-flow, shared-branch-ci;
  - **agent profiles (13):** generic-agent, human-in-charge, implementer-ivan, reviewer-renata,
    architect-alphonso, planner-priti, analyst-annie, researcher-robbie, curator-carla, debugger-debbie,
    paula-patterns, designer-dagmar, retrospective-facilitator. Their stack-specific examples are removed;
  - **styleguides (5):** testing-principles, test-desiderata-and-boundaries, quadruple-a-test-format,
    boring-code-review, kitty-glossary-writing;
  - **toolguides (3):** efficient-local-tooling, git-agent-commit-signing, git-worktree-pr-workflow;
  - **tactics (about 60):** the root tactics (less the reasons-canvas and common-docs ones), `testing/*`,
    `security/*`, `code-patterns/*`, and the analysis tactics not taken by `architecture`;
  - the four mission types and their step contracts, the `spec-kitty-core` glossary. The two presets stay on `built-in` (ADR-0048 decision 12).
- **Topical packs:**
  - **writing:** 9 styleguides (plain-language, professional-communications, divio-type-discipline,
    docs-accessibility, docs-freshness-sla, publication-authority, common-docs, meeting-minutes-format,
    research-citation-discipline), 6 profiles (comms-cleo, scribe-sally, minutes-mahad, lexical-larry,
    diagram-daisy, synthesizer-sam), 2 diagram toolguides, `tactics/communication/*`, the
    extract-then-publish-separation paradigm, the 2 meeting templates, the audience assets, directives 042 and 047,
    `action-item-attribution` and `minutes-stand-alone`;
  - **architecture** (with DDD): aggregate-design-rules, contextive, c4-incremental-detail-modeling,
    `use-c4-model-techniques`, the DDD analysis tactics and `architecture/*`;
  - **refactoring:** `refactoring/*`, `semantic-compression/*`, the semantic-compression paradigm, randy-reducer;
  - **`spdd`:** the structured-prompt-driven-development paradigm, reasons-canvas-writing, directive 038, the two
    reasons-canvas tactics;
  - **testing extras** fold into `qa`: mutation-aware-test-design, given-when-then-authoring, gherkin, sonar,
    directive 036, `use-mutation-testing`;
  - **pack-authoring** (or `internal`): the pack-curation profile, deployable-skill-authoring, and the onboard and
    migrate mission types re-homed from procedures.
- **Stack packs (opt-in):** `java` (java-jenny, java-conventions, java-supply-chain, maven-review-checks),
  `python` (python-pedro, python-conventions, python-review-checks, python-supply-chain, python-mutation-tools),
  `drupal` (drupal-dries, drupal-conventions, drupal-security-performance, drupal-review-checks), `javascript`
  (node-norris, javascript-supply-chain, typescript-mutation-tools; frontend-freddy, the atomic-design paradigm and
  its state and review tactics). `powershell-syntax` stays in foundation until a second PowerShell artifact exists.
- **Moves to `internal`:** `tiered-standards`, the `agentic-framework-core-team` audience, `mission-tracer-files`,
  `mission-wrap-up-sequence`, `development-assist-test-cleanup`.
- **Cut candidates:** directive 039 (a culture bundle named after a person); the brownfield-onboarding paradigm
  (marked provisional in its own text); overlaps to merge in curation: directive 028 against the toolguide of the
  same title, 036 against `black-box-integration-testing`, 042 against `common-docs`, the three mutation-testing
  artifacts, and the five refactoring-discipline artifacts; the empty `skills/` directory.

## Core follow-up list (post-cutover unless marked)

From a read of spec-kitty/spec-kitty at `9aed3a8`. No core PR is opened from this repository; core files and orders
these.

| # | Follow-up | Driven by | Size | After |
|---|---|---|---|---|
| C1 | Retire the dead glossary learning and clarification pipeline (pipeline, attachment, middleware, clarification, checkpoint, resolution, `glossary_hook`, `build_glossary_drg_layer`); no production caller | 0049 decision 17 | M | none; may precede the cutover |
| C2 | Point the chokepoint and the template renderer at the active glossary contexts; drop the four `GlossaryScope` values; keep `mission_local` as runtime state | 0049 | L | charter 4 |
| C3 | Project the terminology gates' banned lists from `avoid` (no checker reads `synonyms_to_avoid` today) | 0049 decision 15 | M | C2 |
| C4 | Rename `glossary_pack` to `glossary` in activation keys and `--include` tokens, with a migration | 0049 | M | charter 4 |
| C5 | Make mission type an artifact kind: remove `MissionTypeNotAnArtifactKind`, the special `CHARTER_KIND_TOKENS` entry and the `mission_type_activations` key | 0050 decision 6 | M | charter 11 |
| C6 | Read `mission_types/<type>/` bundles (runtime bridge, discovery, step repository, org expected artifacts) | 0050 decision 4 | M | open-packs P2 |
| C7 | Load the lane machine from the declared lifecycle: `wp_state.py` reads edges and guards from the artifact, guards resolve through an evaluator registry | 0050 decisions 17 to 21 | L | charter 8, open-packs P6 |
| C8 | Replace `GateBinding{on_transition, handler, fail_open}` with `transitions[].guard`; blocking expected artifacts as the built-in guard; additive `enhances` | 0050 decisions 12, 13, 22 | M | charter 9, 10 |
| C9 | A gate registry with the `smell` / `severe` / `breaking` ladder; `fail_open` derived from severity (#3416) | 0050 decision 14, 0052 decision 10 | M | charter 8 |
| C10 | Send declared `emit`s; generic step events beside the `Specify*`, `Plan*`, `Tasks*` ones | 0051 | M | charter 7, the spec-kitty-events issue |
| C11 | Remove `software-dev` special-casing: work-package behavior from whether a step references a lifecycle | 0050 decision 18 | L | C5, C7 |
| C12 | Render activated anti-patterns in review prompts instead of the hardcoded checklist | 0052 | S | none; may precede the cutover |

Two upstream items sit outside both repositories: an issue on spec-kitty-events for generic step events (the notes'
E4), and agreement there on who owns the lane names (ADR-0050 decision 21).

## Proposed issues (not filed)

1. **Glossary contexts and terms** (charter, child of #142): kind rename, schema, migration, the two reads. ADR-0049.
2. **Event registry kind** (charter, child of #142): ADR-0051.
3. **Lifecycle kind** (charter, child of #142, beside #156): ADR-0050 decisions 17 to 21.
4. **Step enhancements and overrides** (charter, child of #148): ADR-0050 decisions 22 to 25.
5. **Contract sets for the pack-ecosystem kinds** (charter): one per kind, sequenced through the merge queue.
6. **Generic step events** (spec-kitty-events): the notes' E4.
7. **Open-packs: named-packs split, mission-type bundles, stack packs, curation pass, glossary contexts, lifecycle
   and registry** (spec-kitty-open-packs, P1 to P6).
8. **Core C1 to C12** (spec-kitty/spec-kitty, under core epic #2466).

## Open decisions and recommended defaults

The defaults the ADRs plan with until the operator rules. Each is restated in its ADR.

| Decision | Recommended default | Where |
|---|---|---|
| Tier of our opt-in packs | `org` | ADR-0048 Q1 |
| Foundation membership | the census proposal above | ADR-0048 Q2 |
| `built-in` members besides foundation | `writing` only | ADR-0048 Q3 |
| Range grammar | exact, `^`, `~`, comparison operators, space as AND | ADR-0048 Q4 |
| Settling a sibling conflict | the distribution pack's own `overrides` edge | ADR-0048 Q5 |
| Glossary `applies_to` | advisory | ADR-0049 decision 14 |
| Glossary file layout | `glossaries/<context-id>.glossary.yaml` | ADR-0049 decision 6 |
| Terminal state name | reserved `end` | ADR-0050 decision 11 |
| Lifecycle reference field | `work_package_lifecycle` on the step | ADR-0050 decision 18 |
| How `force` is declared | not declared; core keeps its rules | ADR-0050 decision 20 |
| How an enhancement is authored | a step-enhancement file in the type's bundle path | ADR-0050 decision 23 |
| Action code assets | not in the first cut; actions emit only | ADR-0050 decision 16 |
| Namespace overlap | equal or nested on a dot boundary (keeps joined identifiers unique) | ADR-0051 decision 7 |
| Event registry activation | not activatable; in force with its pack | ADR-0051 decision 5 |
| Census corrections to the procedure sorting: re-home five "fit" procedures, move `development-assist-test-cleanup` with the wrap-up sequence, keep `post-merge-arch-gate-adjudication` and `adversarial-squad-deployment` as product guidance | take all three | ADR-0052 decision 7 |
| Graph-fragment anti-pattern nodes after the migration | refused | ADR-0052 decision 14 |
| The `gate` kind and its shape | a declared kind: severity on the shared ladder, optional `config_schema` | ADR-0050 decision 14 |
| `lifecycle` and `gate` activatable on their own | no; a mission type that references them brings them into force | ADR-0050 follow-ups |
| Sibling step enhancements | compose (add-only), narrowing ADR-0048 decision 9 to `overrides` for steps | ADR-0050 decision 23 |
| One stored `emit` spelling | accept both when authored; store `{namespace, type}` | ADR-0050 open question 11 |
| `{kind}` path token spelling | the snake_case kind token (`mission_type`), as the contract accepts today | ADR-0050 open question 9 |
| The `glossary:` URN | keep it; the seam carries it once core's retiring `glossary` node kind is gone | ADR-0049 decision 4 |
| What a "compose" is | one `depends_on` closure at one level; ids unique within it, except an explicit `overrides` | ADR-0048 open question 6 |
| An undeclared `pack_level` | the level rule reads the configured level; `pack_level` becomes required | ADR-0048 open question 7 |
| The foundation pack's id | `foundation` | ADR-0048 open question 8 |
| What an empty active charter means | revisit at acceptance; until then it keeps ADR-0020 decision 6 | ADR-0048 open question 9 |

The notes write the read path as `/packs/{packId}/artifacts/mission-type/{id}`. The contract's `{kind}` segment
takes the snake_case kind token (`ArtifactKind.fromToken`), and the closed `SelectableKind` and `ActivatableKind`
enums spell `mission_type`. Reading the notes' path as that token costs nothing; the hyphenated operator token
would be a breaking change on every `{kind}` path.
