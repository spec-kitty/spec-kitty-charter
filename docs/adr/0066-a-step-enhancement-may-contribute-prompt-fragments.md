---
title: "ADR-0066: A step enhancement may contribute prompt fragments"
description: "Operator ruling of 2026-11-08 on open-packs#73: an enhancement pack may insert text into named slots of a base mission's step prompts, so the SPDD blocks can leave built-in."
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-08'
type: ADR
kind: ADR
related:
  - adr/0040-content-form-and-payload-kinds
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0064-graph-documents-are-the-only-source-of-relationships
audience:
  - profile: pack-author
    guidance_text: Read this for how a base prompt declares a place for text, how an enhancement fills it, and what an enhancement may not do to a prompt.
  - profile: charter-service-contributor
    guidance_text: Read this before building the step-enhancement schema, the prompt slot checks or the effective-prompt read.
  - profile: core-maintainer
    guidance_text: Read this for what the renderers must read and where they place the fragments.
---

# ADR-0066: A step enhancement may contribute prompt fragments

## Status

**Accepted**, 2026-11-08. Decider: Stijn Dejongh (operator). The operator ruled on 2026-11-08
([spec-kitty-open-packs#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73), which took the question from
[#111](https://github.com/spec-kitty/spec-kitty-charter/issues/111)) that the `spdd:reasons-block` text in the built-in
`software-dev` step prompts moves into the opt-in `spdd` pack. A step enhancement could add only guards and emits, so
this record lets it add prompt text as well, within bounds.

It changes one point of [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22 and
the same point of [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decision 22. Each
carries a dated amendment and an annotation where the point stands. On review of this record the operator also ruled
that sibling step enhancers compose; decision 6 relies on that ruling, which ADR-0053 decision 21 carries as an
annotation.

## Context

The built-in `software-dev` step prompts for specify, plan, tasks, implement and review each carry a block of
guidance between `<!-- spdd:reasons-block:start -->` and `<!-- spdd:reasons-block:end -->` markers. It is guidance for
artifacts that move to the opt-in `spdd` pack (ADR-0048, topical packs; ADR-0053 decision 3). Core keeps or strips a
block by asking whether the SPDD content is active; `spec-kitty next` does not ask
([spec-kitty#361](https://github.com/spec-kitty/spec-kitty/issues/361)).

ADR-0053 decision 22 makes a step enhancement an artifact in the enhancing pack, joined to its step by an `enhances`
edge and holding only additions. After [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)
decision 12 those additions are guards and emits. So the `spdd` pack had no route to put text in a base prompt, and
the question on open-packs#73 was whether the blocks stay in `built-in` behind the gate or move. The operator decided
they move.

## Decision

<!-- markdownlint-disable MD029 -->
1. **An enhancement may contribute prompt fragments** to the prompt of the step it enhances. A fragment is Markdown
   text that goes into a slot (decision 2). It is the only way an enhancement adds text to a prompt, and the prompt is
   the only text it can reach: a fragment cannot target a step's guidelines. A step enhancement's additions are now
   guards, emits and prompt fragments. The mechanism is that of ADR-0053 decision 22, unchanged: an enhancement
   artifact joined to the step by an `enhances` edge. A base mission type stays complete, and reads the same, with no
   enhancement composed.
2. **A slot is declared by the base prompt, and holds nothing there.**
   - A slot is a pair of HTML comment markers in the step prompt's Markdown, `<!-- <slot-id>:start -->` and
     `<!-- <slot-id>:end -->`, with only whitespace between them. The syntax is that of the markers the built-in
     `software-dev` prompts carry today, and `spdd:reasons-block` is a valid slot id. Those five prompts hold text
     between the markers now, so they are not yet slots in the sense of this decision: the move empties them, and
     until then they fail the empty-slot check of decision 8, which the version gate of the Follow-ups handles.
   - A slot id is `<namespace>:<name>`, each part matching `[a-z][a-z0-9-]*`. The namespace names a concern, not a
     pack. A prompt that declares `spdd:reasons-block` depends on no `spdd` artifact, and the charter reads no pack
     from the id.
   - A prompt declares each slot id once, its start before its end, and no slot inside another.
   - Slot ids are stable identifiers, as pack names are
     ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) Risks): renaming one is a breaking
     change for the packs that fill it.
   - Only the prompt in force for the step can declare a slot. A fragment cannot, and an enhancement cannot add a slot
     to a prompt it does not own.
3. **How a fragment is authored and addressed.**
   - The enhancement carries `prompt_fragments`, a list of entries each naming one slot id and one Markdown companion
     that holds the text. The companion has the `markdown` content form
     ([ADR-0040](/adr/0040-content-form-and-payload-kinds/), as a step's prompt and guidelines have,
     [ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)) and no frontmatter. The field names,
     the file layout and the fragment companion's own `file-kinds.yaml` rule and structure schema are new and left to
     the schema work, as ADR-0053 decision 22 leaves the enhancement's layout.
   - A fragment is addressed by the pair (target step, slot id). The step comes from the enhancement's `enhances`
     edge, `<pack>::action:<type>/<step>` across packs (ADR-0053 decisions 7 and 22). The slot id comes from the
     entry.
   - A fragment is content, not a relationship. The `enhances` edge is the only relationship the enhancement authors,
     and it lives in the enhancing pack's graph documents (ADR-0064 decisions 1 and 12). A slot id names a marker in
     text, not an artifact, so it is no edge; an artifact a fragment mentions in its prose is read as text, never as a
     reference.
   - An enhancement names a slot at most once.
4. **A fragment is rendered only while its enhancement is in force.**
   - The gate is the enhancement's own activation: it is returned only when it is itself active, and nothing pulls it
     into force because its target is, or the other way round
     ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) amendment A4; ADR-0053 decision 10, so
     an absent activation key activates nothing). A step's prompt is rendered only for a mission of its type, so a
     fragment can show only where the step's mission type is in force.
   - There is no second gate on the slot and no test of whether "the SPDD artifacts are active". A pack that wants a
     fragment to follow a paradigm or a directive lists the enhancement in the same preset.
   - A slot with no fragment to render renders as nothing.
5. **Fragments of one slot are ordered by a total order that ignores activation order.** The key is the level of the
   enhancing pack (built-in, then org, then project), then the pack's name, then the enhancement's id, each compared by
   code point. The project's own text therefore comes last, nearest the author. There is no priority field: it would
   ask packs that do not know each other to agree on numbers.
6. **Fragments of sibling enhancements compose; the only conflict is a repeat.** The operator ruled on 2026-11-08, in
   review of this record, that sibling step enhancers compose (composition rule C14, built in PR A0) and that sibling
   step overrides are refused. Several enhancements into one slot are therefore not a sibling conflict. Nothing
   merges or de-duplicates text across enhancements: each fragment appears once, in the order of decision 5. The one
   repeat is an enhancement naming a slot twice (decision 3).
7. **What is refused.** A fragment can only insert between a slot's markers.
   - **Replacing or deleting base text.** No field names a range, an anchor, a pattern or a position other than a
     slot. Outside the slots, every rendering of the prompt is the base text unchanged.
   - **Adding a slot.** A fragment that contains a slot marker, of any slot id, is refused, and so is an entry naming
     a slot the step's prompt in force does not declare.
   - **Frontmatter, or an empty fragment.** A fragment is non-blank body text.
   - **A target that is not a step.** An enhancement with fragments must enhance an `action:<type>/<step>`.
   - **A target that refuses enhancement.** The integrity rules that govern an enhancement of the target apply
     unchanged: a locked target takes no fragment ([conceptual model](/context/conceptual-model/#integrity)).
   - **A prompt override that drops a slot.** A layer override of the prompt takes the place of the whole prompt
     (ADR-0053 decision 6, ADR-0059 D3) and declares its own slots. A compose where an enhancement names a slot the
     overriding prompt does not declare is refused, never silently dropped.
8. **What the charter validates**, at three moments. The refusal codes are named by the schema work; the names here
   are working names.
   - **A base prompt, at load:** the slot markers are well formed, unique, not nested, and enclose only whitespace
     (`prompt_slot_malformed`, `prompt_slot_not_empty`). The empty-slot check is switched on by a version gate (see
     the Follow-ups).
   - **An enhancement, at load:** its schema, that each fragment is a valid `markdown` body with no frontmatter and no
     slot marker, that each slot id fits the grammar, that no slot repeats, and that its `enhances` target is a step
     (`prompt_fragment_invalid`).
   - **A compose, at resolution,** whatever the activation: the target step resolves in the `depends_on` closure
     (`pack_reference_pack_missing`, `pack_reference_artifact_missing`, ADR-0053 decision 7), the prompt in force
     declares each named slot (`prompt_slot_missing`), and the target's integrity permits the enhancement.
   - The charter does not judge what a fragment says, whether it agrees with the base text, or how a prompt reads once
     rendered. It does not render.
9. **The charter serves the fragments and the renderer places them.**
   - The read of a step's effective prompt returns the base prompt text unchanged and, per slot id, the active
     fragments in the order of decision 5, each with the pack and enhancement it came from. The contract change is
     additive ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)) and belongs with the
     mission-type read (R5, [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99)) and the activation read
     (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)). This record changes no contract file.
   - A renderer replaces each slot's marker pair with its fragments, one blank line between them. A slot with none
     leaves nothing, not even the markers. Core's skill renderer and its `spec-kitty next` prompt builder both take
     the same read, so a project sees the same prompt on either path.
<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- `built-in` stops carrying guidance for an artifact of an opt-in pack. An opt-in pack that enhances a step puts its
  text in the step's prompt without editing the prompt, and a project that does not activate it renders the base
  prompt.
- One gate: whether a fragment shows is whether its enhancement is active. Both renderers read the same answer, which
  closes the gap that `spec-kitty next` builds prompts with no gate
  ([spec-kitty#361](https://github.com/spec-kitty/spec-kitty/issues/361)).
- The bound is mechanical: an enhancement can only insert between markers the base wrote, so the base pack stays
  complete without it.

### Negative

- Three repositories change, in the operator's order: this record, core support, then the move of the blocks.
- The step-enhancement schema, the slot checks and the effective-prompt read are new charter work, and the open-packs
  change cannot validate until they land.
- The other SPDD text in core, `charter/offering/spdd_reasons/charter_context.py`, is separate per-action guidance
  under the same gate. It is not a marker block, so this record does not move it.

### Risks

- A slot id is a stable identifier like a pack name. Renaming or removing one in a base prompt refuses every compose
  that holds an enhancement filling it, and so does a prompt override that omits it (decision 7). The base pack's
  maintainers keep slot ids as they keep pack names.
- A fragment can contradict the base text beside it, and the charter cannot tell: it checks shape and place, not
  meaning (decision 8). Review of the enhancing pack's pull request is the check, as for the procedure extraction of
  ADR-0064.
- A slot is open to any enhancement an org or project composes, not only the pack the base prompt had in mind. Order
  puts the project last (decision 5), so the project's own text is the last word, not a veto.

### Follow-ups

Nothing below is applied in this record. The order is the operator's: core support, then the move.

- **Core** ([spec-kitty](https://github.com/spec-kitty/spec-kitty)): read the slot markers and the per-slot fragments
  in both the skill renderer (`command_renderer.py`) and the `spec-kitty next` prompt builder
  (`runtime/next/prompt_builder.py`), and place them as decision 9 says. Until the blocks move, the renderers keep
  their existing gate for text found between a slot's markers; that path is deleted with the pin that carries the
  move.
- **open-packs** ([#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73)):
  - Add the `spdd` pack at `pack_level: org` (ADR-0053 decision 1), depending on the pack that holds the
    `software-dev` mission type, with the SPDD paradigm, directive, tactics and styleguide.
  - Add five step enhancements, one for each of specify, plan, tasks, implement and review. Each carries the block
    text for its step as a fragment for `spdd:reasons-block`, and its `enhances` edge is in the `spdd` pack's graph
    documents.
  - List the enhancements in the `spdd` preset with the paradigm, tactics and directive.
  - Empty the five prompts' marker pairs in built-in, so each declares the slot and holds no text, and move the
    pack to the descriptor version that enforces the empty-slot check, in the same change.
- **Charter schema and read work**, which must land before the open-packs change can validate: the step-enhancement
  schema with `prompt_fragments`; the slot rules, as an extension of the existing `mission-step-prompt` structure
  schema (the schema of the prompt companion in `contracts/packs/file-kinds.yaml`, which is a schema and not a kind)
  or as a check beside it; a structure schema for the fragment companion, which is new; the three validations of decision 8, and the
  effective-prompt read of decision 9.
  *(Built: the schema, the slot rules and the load checks in phase 1 (#428); the compose checks of decision 8 and the
  effective-prompt read of decision 9 in slice C, 2026-11-12 (#438). See the
  [annotation of 2026-11-12](#annotation-2026-11-12-the-compose-checks-and-the-effective-prompt-read-are-built).)*
- **The switch for the empty-slot check is a version gate**, on the pack-descriptor version, as ADR-0053 decision 8
  gated `pack_level` on the descriptor version that adds `depends_on`. A pack at an older descriptor version keeps text
  between the markers and gets a warning, not `prompt_slot_not_empty`; a pack at the new version must hold empty slots.
  So the five built-in prompts pass until open-packs moves its descriptor version together with the moved blocks. A
  lenient flag was not chosen: it is a switch someone has to remember to remove, and a pack could stay on it for
  good.

## Annotations

The decisions above stand as written. Each annotation below is dated, additive and cites its ruling.

### Annotation 2026-11-09: guards and emits are fields (D6)

Operator ruling D6 of the step-enhancement service-alignment mission (phase 1). The guard and emit additions of
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22 are fields of the step
enhancement, not edges: `add_guards`, `add_lifecycle_guards` and `emits`. They mirror
[ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) decision 10 and
[ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decisions 15 and 16. They will be built
under [#434](https://github.com/spec-kitty/spec-kitty-charter/issues/434); until then the schema keeps
`prompt_fragments` alone, and an enhancement with no fragment is refused while the three fields are not built (D12): an
absent `prompt_fragments` key with `prompt_fragment_invalid`, an empty list as a schema violation.

### Annotation 2026-11-09: how the load codes of decision 8 reach the wire (OD-3 (a), OD-7)

Operator rulings OD-3 (a) and OD-7 of the same mission. The three load codes of decision 8 are raised:

- A fault is a per-file violation code inside `422 validation_failed` at registration, one violation per fault,
  each `{pointer: <pack-relative file>, code}`.
- On a composing read the same load seat answers `500 pack_unreadable`, naming the first faulty file.
- The `prompt_slot_not_empty` warning of an unversioned descriptor is not a violation. It is listed in the
  `diagnostics` of the result of a succeeded registration.

The rules and their moments are on the [Load rules](/architecture/composition-and-activation-rules/#load-rules)
page. The compose checks of decision 8 are not built; the phase-2 mission
([#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438)) builds them.

### Annotation 2026-11-10: what the phase-1 build holds to (R-OVR, R-REL)

Two rulings of the same mission, as built in the pull request that landed it:

- R-OVR: a step enhancement may not carry an `overrides` edge. An enhancement only adds, so an `overrides` edge with the
  enhancement as its source, its own mission type included, is refused as `prompt_fragment_invalid` at the enhancement
  file. A source written with the pack's own name, `<pack>::step_enhancement:<id>`, is judged like a bare one.
- R-REL: the relationship write refuses, with `422 prompt_fragment_invalid` and nothing written, an edge that would
  break a held step enhancement: an `overrides` edge from it, or a second `enhances` edge from it.

### Annotation 2026-11-12: the compose checks and the effective prompt read are built

Slice C ([#467](https://github.com/spec-kitty/spec-kitty-charter/issues/467), mission
`effective-step-and-action-context-01M774HB`) builds what the annotation of 2026-11-09 left to phase 2
([#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438)): the three compose checks of decision 8 and the
read of decision 9. The four codes of decision 8 are now all raised; none is reserved. The rules are on the
[rules page](/architecture/composition-and-activation-rules/): C37 (the target held), C22 and C25 as amended (the
inherited lock), C38 (the slot declared) and F8 (the effective step prompt). The annotations below record the operator
rulings the build applies, each where its decision stands.

### Annotation 2026-11-12: decision 8, only an enhancement in force refuses a missing slot (OD-1, OD-4)

Operator rulings OD-1 (B) and OD-4 (i) of the phase-2 mission, carried into slice C.

- **Active only (OD-1).** Decision 8 says a compose is checked "whatever the activation". For the target and the
  integrity checks that stands: they are closure-level and refuse whatever is selected. For `prompt_slot_missing` it
  is narrowed: only an enhancement in force refuses. A held enhancement that is not in force never refuses, because it
  never renders (decision 4). The contract's description of the code says the same (`### Changed`).
- **The moments (OD-4).** The checks run on every composing read (`500`), at registration (`getPackRegistration`,
  `422`), on the impact-checked pack writes (the per-artifact registrations, `putPackRelationship`, `deletePackArtifact`
  and `deletePackRelationship`, `422`) and on the activation writes (create, replace, select, unselect, deactivate,
  reactivate and apply a preset, `422`, before anything is stored).
- **What "in force" means without an activation.** A pack has no activation of its own. At registration and on the
  impact-checked writes, the slot check runs once for each stored activation whose closure the change joins, on that
  closure as the write leaves it, and names the activation in the detail. A change that joins no stored activation's
  closure passes (research R7 of the mission).

### Annotation 2026-11-12: decision 7, a step takes its mission type's integrity (OD-2)

Operator ruling OD-2 (A). The bullet "a locked target takes no fragment" is read through the mission type: a step has
no mutability level of its own, so it inherits the level of its mission type. The level is judged along the closure
without the enhancing pack's own policy, as every write gate judges (B8).

- An enhancement of a step whose mission type is `locked` is refused `422 override_not_permitted` at registration and
  on the impact-checked writes. It is refused `500 pack_unreadable` on the reads and the activation writes, whatever
  the activation selects, as a forbidden layer override is.
- An `extendable` mission type takes the enhancement.
- No enum value is added. The description of `override_not_permitted` is broadened to cover the enhancement
  (`### Changed`).

### Annotation 2026-11-12: decision 9, the read is built, and a mission type not in force is a 409 (OD-5)

Operator ruling OD-5, which stands (Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`, 2026-11-12).

- **The read.** `getEffectiveStepPrompt`,
  `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt`, answers the prompt in force
  at the merged winner's place of the step and, per slot it declares, the fragments of the enhancements in force, in
  the order of decision 5, each with its pack and enhancement. The prompt is served as it is stored. That is what
  "the base prompt text unchanged" means here: the service neither renders nor rewrites it. A slot that still holds
  legacy text under an unversioned descriptor is listed with its fragments and a `prompt_slot_not_empty` diagnostic
  (OD-7, OD-8). The action context links the read as `prompt`.
- **The 409.** For a mission type the closure holds and the activation does not put in force, the read answers
  `409 mission_type_not_in_force`. It is judged after `activation_not_found`, `mission_type_not_found` and
  `action_not_found` (which need a closure that composes and merges), and before `prompt_slot_missing` and any `304`. Its ground is decision 4: a step's prompt
  is rendered only for a mission of its type, so a fragment shows only where the step's mission type is in force.
- **Why it differs from the action context.** The action context of the same action answers `200` with nothing in it.
  The context describes what governs an action: for a mission type that is not in force the true description is
  "nothing", and its skipped report says why. The prompt read hands a renderer the text it places in a running step.
  A mission type that is not in force has no step to run, and an empty prompt would be rendered as if it were one. A
  conflict with the activation's state is the honest answer, and the client resolves it by changing the activation.
- **Not additive.** Decision 9 calls the contract change additive. The read and its schemas are `### Added`. The new
  value of the closed `CharterRefusalCode` enum is `### Breaking` on the 0.x line
  ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)): a client that validates a refusal's code against the
  earlier enum refuses it.

## Alternatives considered

### Leave the blocks in `built-in` behind the activation gate

Not taken: the base prompt keeps carrying guidance for an artifact of a pack the project may never activate, which is
what the split of the built-in pack removes. The operator decided the blocks move.

### The enhancing pack overrides the whole step prompt

Not taken: the override replaces the base text (ADR-0059 D3), so the pack owns a copy that drifts from the base, and
two packs that both override the prompt conflict (ADR-0053 decision 5).

### Free anchors, such as "after this heading" or a pattern

Not taken: they tie the fragment to the base's wording and let a range stand for a deletion. Named slots keep the base
text unreachable.

### Let the enhancing pack declare the slot

Not taken: it is a way to edit the base text, and the base would not be the one that says where text may go.

### Carry the fragment text on a graph edge

Not taken: an edge says how two artifacts relate and may carry a short `when` or `reason`; prompt text is content,
and edges have no order of their own (ADR-0064).

### A new artifact kind for a fragment

Not taken: ADR-0053 decision 22 has one mechanism for every augmentation, and a fragment is one more addition of an
enhancement.

### An explicit `order` or priority field

Not taken: see decision 5.

## References

- [spec-kitty-open-packs#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73), the ruling and the
  split of the built-in pack; [#111](https://github.com/spec-kitty/spec-kitty-charter/issues/111), where the question
  arose.
- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decisions 7, 10, 21 and 22, and
  [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decision 22, the enhancement
  mechanism this record extends.
- [ADR-0040](/adr/0040-content-form-and-payload-kinds/) and
  [ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/), the Markdown content form and the
  prompt companions.
- [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/), guards, the other addition of
  an enhancement.
- [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) and
  [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/), activation of an enhancer and edges
  authored only in graph documents.
