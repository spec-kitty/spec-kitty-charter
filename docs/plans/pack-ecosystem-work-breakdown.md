---
title: Pack ecosystem work breakdown
description: "Ordered, sized work for epic #142 after ADR-0048 to ADR-0052: charter schema and service work, the open-packs split and curation, and the core follow-ups."
doc_status: draft
updated: 2026-11-12T00:00:00Z
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
[ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/), all Accepted on 2026-11-02,
for epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142). The rulings are in the
[design notes](/plans/pack-ecosystem-design-notes/). Nothing here is filed yet: the "proposed issue" titles are for
the operator to file or merge into existing children.

Sizes: **S** is up to a day, **M** a few days, **L** a Mission of a week or more. Contract-family items (they change
`contracts/`) land one at a time, as the merge queue requires.

## Ordered work (charter)

| # | Work | ADR | Size | Depends on | Tracker |
|---|---|---|---|---|---|
| 1 | ~~Operator rulings on the open decisions~~ (done 2026-11-02, [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) and [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/)); ~~ADRs 0048 to 0052 accepted, and the "superseded in part" notes added to the Accepted records they touch~~ (done 2026-11-02); ~~glossary entries for the new terms~~ (done 2026-11-02; "Gate" deprecated for "Guard" by ADR-0054) | all | S | none | #142 |
| 2 | ~~`depends_on` and `parent_pack` checks in the pack-descriptor schema; the range grammar~~ (done 2026-11-03: the closed `depends_on` and `overrides` items are in the pack-descriptor schema, a declared dependency forces `pack_level`, `kernel.domain.Pep440Range` decides the PEP 440 range satisfaction, and an unresolvable `parent_pack` is refused) | 0048 | M | 1 | #145, #146 |
| 3 | ~~The dependency resolver: ordering, cycles, ranges, dedupe by `pack_id`, sibling refusal; a compatibility shim keeping the single `built-in` tree loading~~ (done 2026-11-03: `PackCompositionResolver` orders one closure — dependencies first, a shared dependency once, merge order across levels kept — and the offering read refuses a broken compose with the eleven new refusal codes, the unsettled sibling conflict included; ranges on one pack that share no version are refused. The pack-qualified `<pack-name>::<urn>` form is accepted in graph edge endpoints and in every typed-reference id, and the compose refuses a bare cross-pack reference and a qualified reference outside the referring pack's own `depends_on` closure, from both carriers. The offering response carries what the compose resolved, each declared edge with its satisfying version, and each pack's descriptor `pack_id`. Open: the pack pin does not answer its mirror of those facts yet; the activation reads it waited on (#125) are built) | 0048 | L | 2 | #145 |
| 4 | The `glossary` kind: schema, binding, migration of four glossaries, the two context reads | 0049 | L | 1 | proposed: "glossary contexts and terms" |
| 5 | The `anti_pattern` kind and `rejects`; drop inline `anti_patterns` from procedure and styleguide | 0052 | M | 1 | #154 |
| 6 | ~~The authoring checklist for the tightened procedure definition~~ (done 2026-11-02, [Author a procedure](/guides/author-a-procedure/); applied by review. The closed schema has no output field and refuses any undeclared key and `type: action`, but not an `action:` URN as a reference id: #350) | 0052 | S | 1 | #150, #350 |
| 7 | The `event_registry` kind and the shared `emit` entry shape | 0051 | M | 2 (resolution uses the closure) | proposed: "event registry kind" |
| 8 | ~~The `guard` and `lifecycle` kinds; the asset `executable` block and the `implemented_by` relation~~ (done 2026-11-03, #354; validate mode and per-file writes do not run the closure checks yet, fix before item 13) | 0050, 0054 | M | 1 | #156 |
| 9 | Mission-type schema: transitions, guard entries `{ref, config?, blocking?}` (#155, closed into #156), actions, the `submachine` field, requirement kinds (#164), `term:` links | 0050 | L | 4, 7, 8 | #148, #155, #164 |
| 10 | Step enhancements and `overrides` checks, with the drift warning | 0050 | M | 3, 9 | #148 |
| 11 | Mission type as an ordinary activatable kind (presets, deny list) | 0050 | M | 9 | #148 |
| 12 | `applies_to_stacks` beside `applies_to_languages` | notes | S | 1 | #163 |
| 13 | Contract sets for the new reads and registrations (glossary, anti-pattern, event registry, guard, lifecycle, embedded lifecycle) | all | M each | the kind it serves | proposed, one per kind |
| 14 | ~~Close #153 (not planned), #161 (moot)~~ (done 2026-11-02); close #147 (into #146); ~~document the single daemon per worktree (#183)~~ (done 2026-11-02, [one writer per pack](/architecture/service-lifecycle/#one-writer-per-pack); a fail-fast guard is #349) | notes | S | 1 | #153, #161, #183, #349 |

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

From the census of spec-kitty-open-packs at `1060bfc`. The operator adopted it as the starting cut on 2026-11-02 (ADR-0053 decision 2).

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
  `mission-wrap-up-sequence`. (`development-assist-test-cleanup` stays a product procedure, ADR-0053 decision 29.)
- **Cut candidates:** directive 039 (a culture bundle named after a person); the brownfield-onboarding paradigm
  (marked provisional in its own text); overlaps to merge in curation: directive 028 against the toolguide of the
  same title, 036 against `black-box-integration-testing`, 042 against `common-docs`, the three mutation-testing
  artifacts, and the five refactoring-discipline artifacts; the empty `skills/` directory.

## Core follow-up list (post-cutover unless marked)

From a read of spec-kitty/spec-kitty at `9aed3a8`. No core PR is opened from this repository. Each follow-up is filed
as a task on 2026-11-02. C1 and C12 are in the "4.x cutover" milestone, under the cutover epic #69. C2 to C11 are
in "Post-cutover", under the epic #361 (core adopts the pack-ecosystem design, operator 2026-11-03). C9 was rewritten
after ADR-0054.

| # | Follow-up | Driven by | Size | After | Issue |
|---|---|---|---|---|---|
| C1 | Retire the dead glossary learning and clarification pipeline (pipeline, attachment, middleware, clarification, checkpoint, resolution, `glossary_hook`, `build_glossary_drg_layer`); no production caller | 0049 decision 17 | M | none; may precede the cutover | #332 |
| C2 | Point the chokepoint and the template renderer at the active glossary contexts; drop the four `GlossaryScope` values; keep `mission_local` as runtime state | 0049 | L | charter 4 | #333 |
| C3 | Project the terminology gates' banned lists from `avoid` (no checker reads `synonyms_to_avoid` today) | 0049 decision 15 | M | C2 | #334 |
| C4 | Rename `glossary_pack` to `glossary` in activation keys and `--include` tokens, with a migration | 0049 | M | charter 4 | #335 |
| C5 | Make mission type an artifact kind: remove `MissionTypeNotAnArtifactKind`, the special `CHARTER_KIND_TOKENS` entry and the `mission_type_activations` key | 0050 decision 6 | M | charter 11 | #336 |
| C6 | Read `mission_types/<type>/` bundles (runtime bridge, discovery, step repository, org expected artifacts) | 0050 decision 4 | M | open-packs P2 | #337 |
| C7 | Load the lane machine from the declared lifecycle: `wp_state.py` reads edges and guards from the artifact, guards resolve through an evaluator registry | 0050 decisions 17 to 21 | L | charter 8, open-packs P6 | #338 |
| C8 | Replace `GateBinding{on_transition, handler, handler_kind, fail_open}` with `transitions[].guard`; blocking expected artifacts as the built-in guard; additive step enhancements | 0050 decisions 12, 13, 22 | M | charter 9, 10 | #339 |
| C9 | A guard runner for the guard/1 interface and the `builtin:` guard table; fail-closed, with `on_indeterminate` and per-entry `blocking` (#340) | 0054 | M | charter 8 | #340 |
| C10 | Send declared `emit`s; generic step events beside the `Specify*`, `Plan*`, `Tasks*` ones | 0051 | M | charter 7, the spec-kitty-events issue | #341 |
| C11 | Remove `software-dev` special-casing: work-package behavior from whether a step references a lifecycle | 0050 decision 18 | L | C5, C7 | #342 |
| C12 | Render activated anti-patterns in review prompts instead of the hardcoded checklist | 0052 | S | none; may precede the cutover | #343 |
| C13 | Make the switch gate block core's switch: the routing proxy reads `switch-gate.yaml` from the vendored `charter-scenarios.tar.gz` and refuses to route an operation to the service unless the register holds it `ready`, naming it; an operation with no entry is not switched (fail closed). Recorded 2026-11-12 from #72; not filed (issue-tracker Rule 5) | 0059 D1 (amended 2026-11-12), 0021 D5 | S | the proxy (roadmap step 6), contract 1.0.0 | none |

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
8. ~~**Core C1 to C12**~~ filed 2026-11-02 as #332 to #343: C1 and C12 under the cutover epic #69, C2 to C11 under
   the epic #361.

{#open-decisions-and-recommended-defaults}

## Rulings on the open decisions

The operator ruled every open decision on 2026-11-02: [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) records the rulings, and [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) the guard model that replaces
the proposed `gate` kind. The rulings that differ from the recommended defaults:

| Decision | Ruling | Record |
|---|---|---|
| Range grammar | PEP 440 version specifiers, as `requires_core` | ADR-0053 decision 4 |
| Cross-pack references | written `<pack-name>::<urn>`, required across packs | ADR-0053 decision 7 |
| What an empty active charter means | an absent activation key means none, so an empty charter puts nothing in force and a partly empty one only what it names | ADR-0053 decision 10 |
| URN kind tokens | kebab case directly (`mission-type:<id>`), in the same change as the path token, with no split-migration reader | ADR-0053 open question 3 (ruled 2026-11-02) |
| The glossary context URN | `glossary_context:<id>` | ADR-0053 decision 13 |
| The `gate` kind | none; guards are artifacts implemented by executable assets | ADR-0054 |
| Action code assets | allowed, behind trust (#157) | ADR-0053 decision 17 |
| Lifecycle reference field | `submachine: {lifecycle, per: work_package, add_guards?}` | ADR-0053 decision 19 |
| Sibling step enhancements | sibling overrides refused unless settled; sibling enhancers compose (amended 2026-11-08) | ADR-0053 decision 21 |
| How an enhancement is authored | an additions artifact (guards, emits, prompt fragments) plus a CRG `enhances` edge, as for profiles | ADR-0053 decision 22, ADR-0066 |
| `{kind}` path token spelling | the kebab operator token (`mission-type`); breaking | ADR-0053 decision 23 |
| Census corrections to the procedure sorting | (a) and (c); not (b) | ADR-0053 decision 29 |
| Anti-pattern shape | `bad_example` required | ADR-0053 decision 30 |
| Severity of a migrated anti-pattern | no default; assigned in curation | ADR-0053 decision 32 |

Every other decision took its recommended default; ADR-0053 lists them all.
