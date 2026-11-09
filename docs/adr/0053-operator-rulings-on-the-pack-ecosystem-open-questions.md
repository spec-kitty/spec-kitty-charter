---
title: "ADR-0053: Operator rulings on the pack-ecosystem open questions"
description: "The operator's rulings of 2026-11-02 on every open question of ADR-0048 to ADR-0052, and the decisions of those records that the rulings amend."
doc_status: active
updated: 2026-11-08T00:00:00Z
decision_status: Accepted; layer-override grain of d6 settled by ADR-0059 (2026-11-04); Amendment 2026-11-05 (ADR-0064); Amendment 2026-11-08 (ADR-0066, prompt fragments and sibling enhancers)
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0049-glossary-contexts-and-terms
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0051-event-registry-a-thin-provider-neutral-consistency-check
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0020-offering-and-activations
  - adr/0033-local-active-charter-is-source-of-truth
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: governance-lead
    guidance_text: Read this for the operator's answer to each open question of the pack-ecosystem records.
  - profile: pack-author
    guidance_text: Read this for the rulings that change how a pack is written, among them pack-qualified cross-pack references and the anti-pattern shape.
  - profile: charter-service-contributor
    guidance_text: Read this before building any part of epic #142; it settles the defaults the work breakdown planned with.
---

# ADR-0053: Operator rulings on the pack-ecosystem open questions

## Status

**Accepted**, 2026-11-02. Decider: Stijn Dejongh (operator). The operator answered every open question of
[ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) to
[ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) in one session on 2026-11-02
(epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142)). This record holds the answers.

Each of the five records carries an **Amendment 2026-11-02** under its Status that points here, and its changed
points are annotated where they stand. The guard model that replaces ADR-0050's proposed `gate` kind is its own
record, [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/).

*2026-11-05: [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) settles the grain of d6's
layer override (decision 3) and the edges the cascade follows (decision 2).*

### Amendment 2026-11-05

The operator ruled on [#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240) on 2026-11-05 that an edge is authored only in a pack's graph documents
([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). One point of this record changes; the original text stands and it is annotated:

- decision 22: a step enhancement's additions are guards and emits; it holds no `references`, and guidance it adds
  to a step is a graph edge ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12).

### Amendment 2026-11-08

The operator ruled on 2026-11-08 ([spec-kitty-open-packs#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73);
review of [#422](https://github.com/spec-kitty/spec-kitty-charter/pull/422)) on two points. The original text stands and
each is annotated:

- decision 22: a step enhancement may also contribute prompt fragments to slots the step's prompt declares, the text of
  the `spdd:reasons-block` moving into the `spdd` pack. The rules are a new record, [ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/).
- decision 21: sibling step overrides are refused; sibling step enhancers compose (composition rule C14).

## Context

ADR-0048 to ADR-0052 were accepted on 2026-11-02 with 32 open questions, each carrying a recommended default.
The [work breakdown](/plans/pack-ecosystem-work-breakdown/) planned with those defaults until the operator ruled.
The operator took most defaults and changed several:

- cross-pack references carry the pack's name;
- an empty active charter puts nothing in force after the split;
- the range grammar is PEP 440;
- the glossary context URN token is `glossary_context`;
- a declared action may run pack code behind trust;
- the `{kind}` path segment uses the kebab operator token;
- the anti-pattern shape requires a bad example and has no default severity.

The operator also replaced ADR-0050's `gate` kind with asset-backed guards (ADR-0054).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### Pack composition (ADR-0048)

1. **Our opt-in packs are `org`** (question 1). `java`, `python`, `drupal`, `qa` and their like declare
   `pack_level: org`; no new pack level is added.
2. **Foundation membership follows the census proposal** (question 2) in the
   [work breakdown](/plans/pack-ecosystem-work-breakdown/#foundation-membership-census-proposal). It is the starting
   cut; curation pull requests refine it per artifact. The presets stay on `built-in`.
3. **`built-in` aggregates `foundation` and `writing`** (question 3). Every other topical pack and every stack pack
   is opt-in.
4. **The range grammar is PEP 440** (question 4). `depends_on[].range` takes a PEP 440 version-specifier set
   (`>=1.2,<2`), the grammar the descriptor's `requires_core` already uses, so a descriptor has one range grammar.
5. **A sibling conflict is settled by the distribution pack's own `overrides` edge** (question 5) on the contested
   artifact, naming the member whose version wins. No new edge type.
   *Amended 2026-11-03 (operator, #145 pre-PR review): a sibling conflict also covers two packs at one level that both define the same artifact id, not only two that both change it, and is refused as `pack_sibling_conflict`. Until [#358](https://github.com/spec-kitty/spec-kitty-charter/issues/358) any pack of the compose may declare the edge, not only the distribution pack. Until [#357](https://github.com/spec-kitty/spec-kitty-charter/issues/357) settling only clears the refusal; the named member's version is not yet put in force.*
   *2026-11-08 (layer-merge mission, PR B): #357 is built. The layer merge puts the named member's version in force at that level, a step shares its mission type's settlement, and a step contest no entry settles is refused at read as `pack_sibling_conflict`, which the compose lets through. #358 is still open: any pack of the compose may declare the entry, and a settlement is not gated by level. The divergence register records both (DIV-218, DIV-219).*
6. **A compose is one `depends_on` closure at one level** (question 6). An id is unique within it, except where an
   explicit `overrides` replaces it. Across levels the layer merge applies (project over org over built-in).
   *Amended 2026-11-03 (operator, #145 pre-PR review): the closure is rooted at one level's configured packs and includes dependencies at levels nearer built-in. Today every configured pack is a root; roots from the activation set come with [#360](https://github.com/spec-kitty/spec-kitty-charter/issues/360). Pack ids and pack names are unique across a compose without exception. An artifact id is unique within a level except where an `overrides` edge settles it; across levels the same id is a legitimate layer override.*
   *2026-11-05: the layer override takes the place of the whole artifact; fields it does not declare are not
   inherited ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) decision 3).*
7. **A reference that crosses a pack boundary is qualified with the target pack's name** (question 6, operator
   addition), written `<pack-name>::<urn>`, for example `foundation::directive:DIRECTIVE_001`.
   - The prefix is required on every reference to an artifact of another pack. A reference inside one pack stays
     bare.
   - A bare reference that resolves only in another pack is refused at load.
     *Amended 2026-11-03 (operator, #145 pre-PR review): it is refused when the compose resolves, not at load (`pack_reference_unqualified`). The check is also at the artifact level: a qualified reference must name a pack in the *referring* pack's own `depends_on` closure (`pack_reference_pack_missing`), and that pack must hold the artifact (`pack_reference_artifact_missing`).*
   - Pack names must be unique within a compose and stable.
   - This amends ADR-0048 decision 10 ("URNs stay global and unqualified"). The artifact's own URN stays
     unqualified; only references across packs carry the prefix.
8. **An undeclared `pack_level`** (question 7): the level rule reads the configured level, and the
   pack-descriptor version that adds `depends_on` makes `pack_level` required.
9. **The foundation pack's id is `foundation`** (question 8).
10. **After the split, only what the charter activates is in force** (questions 1 and 9, both ruled on 2026-11-02). An
    absent activation key means none, not the per-kind default, so an empty active charter puts nothing in force and a
    charter that sets one key puts only that key in force. A project activates a preset or artifacts explicitly;
    deactivation is expressed by activating nothing, which is what the profiles and presets system exists for. This
    replaces, at the split, the meaning that
    [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/) decision 5 kept from
    [ADR-0020](/adr/0020-offering-and-activations/) decision 6 (each kind at its default) for the empty charter and
    for every absent key. It is a breaking change.

### Glossary contexts (ADR-0049)

11. **Layout**: `glossaries/<context-id>.glossary.yaml`, one context per file (question 1).
12. **`applies_to` is advisory** (question 2).
13. **The glossary context URN token is `glossary_context`** (question 3): a context is `glossary_context:<id>`.
    This avoids core's retiring `glossary` node kind, so the seam no longer waits for core to retire it. It amends
    ADR-0049 decision 4; term URNs stay `term:<context-id>/<term-id>`.
    The split from the kind token is intended (operator, 2026-11-02): the artifact kind and its operator and path
    token stay `glossary`, and only the URN prefix is `glossary_context`.
14. **Term `status` is `draft`, `active` or `deprecated`** (question 4).

### Mission types and lifecycles (ADR-0050)

15. **The terminal state is `end`** (question 1), reserved and refused as a step id.
16. **No `gate` kind** (question 2). Guards are enforced checks implemented by executable assets; see
    [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/), which supersedes ADR-0050
    decision 14 and amends decision 12.
17. **A declared action may name a code asset the pack delivers, for the client to run, behind trust** (question 3,
    the notes' question 10). The asset must pass the trust policy of
    [#157](https://github.com/spec-kitty/spec-kitty-charter/issues/157); until then, only packs the project
    explicitly trusts may supply one (as for guards, ADR-0054). The field that names the asset, the relation it is
    derived into, and the interface the client runs it to are left to the schema work (work-breakdown item 9); this
    ruling settles only that a code asset may be named and run. This amends ADR-0050 decision 16, under which an
    action only emits.
18. **The lifecycle layout and shape are as proposed** (question 4, ADR-0050 decision 17).
19. **A step references its lifecycle with a generic submachine field** (question 5):
    `submachine: {lifecycle: <id>, per: work_package, add_guards?: [...]}`. `per` names what the submachine is
    instantiated for, and `work_package` is its only value for now. `add_guards` lets the step add guards to the
    lifecycle's transitions, add-only (ADR-0054 decision 10). This amends ADR-0050 decision 18
    (`work_package_lifecycle`).
20. **`force` is not declared in this cut** (question 6); core keeps its rules.
21. **Sibling step enhancements are refused unless the distribution pack settles them** (question 7).
    ADR-0048 decision 9 stands as written, for steps as for every artifact.
    *Amended 2026-11-08 (operator ruling, review of [#422](https://github.com/spec-kitty/spec-kitty-charter/pull/422)): sibling step overrides are refused; sibling step enhancers compose (C14). An enhancement changes nothing, so two of them on one step are no conflict. The refusal above stands for overrides.*
22. **A step enhancement uses the same mechanism as every other augmentation** (question 8). It is an artifact in
    the enhancing pack that holds only the additions (references, guards, emits *[amended: guards and emits from 2026-11-05, and prompt fragments from 2026-11-08, see below]*), plus a CRG `enhances` edge from it
    to the target step, `action:<type>/<step>`, pack-qualified as `<pack>::action:<type>/<step>` when the step is in
    another pack (decision 7). This is how an agent profile is augmented today. The edge
    and merge rules are the same, and for a step the merge is add-only. This amends ADR-0050 decisions 22 and 23 (a
    mechanism of its own, and a step-enhancement file with a `target:` field). The enhancing artifact's file layout
    is left to the schema work.
    *Amended 2026-11-05 ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 12): the additions lose `references`. An enhancement holds guards and emits; guidance it adds to a step is an edge in the enhancing pack's graph documents.*
    *Amended 2026-11-08 (operator, [open-packs#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73)): the additions gain prompt fragments, text the enhancement inserts into slots the step's prompt declares. An enhancement now holds guards, emits and prompt fragments; the slot, activation, ordering and refusal rules are [ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/).*
23. **The `{kind}` path segment uses the kebab operator token** (question 9): `/packs/{packId}/artifacts/mission-type/{id}`,
    `event-registry`, `anti-pattern`, and the same in `selections/{kind}` and `exclusions/{kind}`.
    - This is a breaking change on the 0.x line, with a `### Breaking` entry (ADR-0022): every multi-word `{kind}`
      path, `Artifact.kind` and the member it names, and the `SelectableKind` and `ActivatableKind` path values
      change.
    - The kernel's `ArtifactKind` gains an operator-token lookup beside `fromToken`, which takes the snake_case
      token and is what resolves the path today. The parameter's slug pattern (`^[a-z0-9][a-z0-9_-]*$`) already
      admits the kebab token.
24. **The lower-impact defaults are taken** (question 10):
    - [#161](https://github.com/spec-kitty/spec-kitty-charter/issues/161) is closed as moot;
    - [#153](https://github.com/spec-kitty/spec-kitty-charter/issues/153) is closed as not planned until a need
      shows;
    - [#164](https://github.com/spec-kitty/spec-kitty-charter/issues/164)'s requirement kinds (core #5956 D1 to D5)
      go into the mission-type schema.
25. **`emit` is authored in either form and stored as `{namespace, type}`** (question 11): the writer normalizes the
    joined string to the object, a change of form that keeps the meaning
    ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)).

### Event registry (ADR-0051)

26. **An event registry is not activatable** (question 1); it is in force with its pack.
27. **Namespaces overlap when equal or nested on a dot boundary** (question 2).
28. **Layout `event_registries/<id>.event-registry.yaml`; a registry is not a CRG node** (question 3).

### Procedures and anti-patterns (ADR-0052)

29. **Census corrections** (question 1): two of the three are taken.
    - Taken: (a) re-home `situational-assessment`, `legacy-codebase-triage`, `bdd-scenario-lifecycle`,
      `drill-down-documentation` and `documentation-gap-prioritization` in curation.
    - Taken: (c) `post-merge-arch-gate-adjudication` and `adversarial-squad-deployment` stay product guidance, not
      internal-pack candidates.
    - **Not taken**: (b). `development-assist-test-cleanup` stays a fit procedure, as originally ruled, and does not
      move with `mission-wrap-up-sequence`.
30. **The anti-pattern shape is as proposed, except that `bad_example` is required** (question 2); `good_example`
    stays optional. This amends ADR-0052 decision 8.
31. **Anti-pattern nodes declared only in a graph fragment are refused after the migration** (question 3).
32. **A migrated anti-pattern has no default severity** (question 4). Curation assigns each one. A migrated entry
    without one is refused, so the migration cannot finish until every entry is assigned. This amends ADR-0052
    decision 15.

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- The work breakdown can start: every planning default is now a ruling.
- Cross-pack references say which pack they rely on, so the `depends_on` closure check can name the missing pack.
- The glossary seam no longer waits for core.

### Negative

- Pack-qualified references change every cross-pack edge and reference in spec-kitty-open-packs once packs split.
  `built-in` today is one pack, so no reference crosses a pack boundary yet. The split (work breakdown P1) writes
  the prefixes.
- The kebab `{kind}` token and the empty-charter ruling are breaking changes, for the contract and for projects
  that rely on today's "empty means defaults".
- The anti-pattern migration cannot finish until curation has written a bad example and a severity for about 150
  entries.

### Risks

- A pack rename breaks every reference to it. Pack names therefore become stable identifiers, and renaming one is a
  breaking change for its dependents.

### Follow-ups

- Work breakdown item 1 is done; its open-decisions table is replaced by a pointer here.
- The pack-descriptor schema: a PEP 440 `range`, a required `pack_level`, and `name` unique within a compose.
- The URN grammar and the closure check gain the `<pack>::` qualifier.
- Close #161 and #153 (as not planned), with a link to this record.
- The contract change set for the kebab `{kind}` token (ADR-0009, ADR-0022).

## Open questions

1. ~~**A partly empty charter.**~~ **Ruled 2026-11-02 (operator):** an absent activation key means none, so an empty
   active charter puts nothing in force and a partly empty one puts only the keys it names in force. Deactivation is
   expressed by activating nothing; the profiles and presets system exists for this. Recorded in decision 10 and as a
   breaking change. The per-kind default is withdrawn, not kept beside a "none" spelling.
2. ~~**Where an enhancing artifact lives** (decision 22).~~ **Ruled 2026-11-03 (operator), as recommended:** beside
   the kind it adds to in the enhancing pack, with the `enhances` edge in that pack's graph shard, as for profiles. No
   separate enhancements area. Recorded with [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/).

   **Annotation 2026-11-09 (operator ruling D7 of the step-enhancement service-alignment mission, phase 1).** For a
   step enhancement, "beside the kind it adds to" is the per-type directory `missions/<type>/enhancements/`: the file
   is `missions/<type>/enhancements/<id>.step-enhancement.yaml`, with its `*.prompt-fragment.md` companions beside it.
   The place is per mission type, not a pack-wide area, so the ruling above stands and no separate enhancements area
   exists. The `<type>` directory must equal the type of the enhancement's single `enhances` edge. It is checked
   against the edge and never read in its place, because the graph document stays the only statement of the target
   ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). An enhancement id is unique per pack,
   across the type directories. See [Load rules](/architecture/composition-and-activation-rules/#load-rules) and
   [Pack schema: Step enhancement](/architecture/pack-schemas/step-enhancement/).
3. ~~**URN kind tokens**~~ **Ruled 2026-11-02 (operator):** the multi-word URN kind tokens move to kebab case directly
   (`agent-profile:<id>`, `mission-step-contract:<id>`, `mission-type:<id>`, `anti-pattern:<id>`,
   `event-registry:<id>`), in the same change as the kebab path token. No split-migration reader: the service has no
   consumers yet, so there is nothing to keep loading the snake-case spelling for. The glossary context token stays
   `glossary_context` (decision 13), because it is not a multi-word kind token but the deliberate split of a URN
   prefix from its `glossary` kind.

## Alternatives considered

Each question was asked with its recommended default and two or three alternatives. The options not taken are listed
in the session record of 2026-11-02; the reasons for each default are in the open-questions sections of ADR-0048 to
ADR-0052.

## References

- ADR-0048 to ADR-0052, open questions; [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/).
- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142); #153; #157; #161; #164.
- [Pack ecosystem work breakdown](/plans/pack-ecosystem-work-breakdown/).
- Amendment 2026-11-08: [ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/).
