---
title: Composition and activation rules
description: Every composition, registration, activation-write and effective-read rule as sets, relations, closures and invariants, each with its source and build status.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Architecture
kind: Reference
related:
  - architecture/logical-model
  - architecture/resolution-model
  - architecture/offering-and-activation
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
  - adr/0026-write-surface-is-functional-registration
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0061-one-replica-per-deployment
  - adr/0063-pack-mutability-policy-file
  - conformance/divergence-register
audience:
  - profile: charter-service-contributor
    guidance_text: Read this as the rule book the packs and activations slices implement; each rule names its source and whether it is built.
  - profile: pack-author
    guidance_text: Read the composition and registration rules to predict what a compose, a layer override or a lock does.
  - profile: governance-lead
    guidance_text: Read the floor, lock and read rules for what a required or locked artifact guarantees.
---

# Composition and activation rules

This page states every rule that decides **which packs compose**, **what a registration may change**, **what an
activation write stores** and **what an effective read answers**, as rule sets over sets, relations and functions.
Each rule is one or two lines of notation, a one-sentence gloss, its source and its status. Section 7 maps every rule
to its source and status in one table; section 8 holds the load rules for step enhancements and prompt fragments.

It restates decisions; it decides nothing. Where two sources disagree, the rule is written as the sources are best
read and carries a **⚠ Unclear** note. The prose pages remain the design: [Logical model](/architecture/logical-model/),
[Resolution model](/architecture/resolution-model/) and [Offering and activation](/architecture/offering-and-activation/).

> **Status labels.** **Built**: the service does it today, as of #399 at `1d37ccd9` (branch
> `issue-291-activation-writes`), which merges the landed #398. **Ruled, not built**: an operator ruling or an accepted ADR decides it, and no code does it yet.
> **Planned, not built**: the design of the layer-merge mission (`kitty-specs/layer-merge-and-effective-reads-01M6R0J6/`,
> plan, research and data model), derived from rulings but not itself a ruling. **Partly built**: the rule holds in
> part, and the note says which part. **Ruled, planned** names where a ruling is to be built: a layer-merge pull
> request (PR A, B or C of the plan) or a #399 follow-up. **Built (PR A0)** marks a rule the layer-merge mission's
> first pull request built on 2026-11-07, stacked on #399. **Built (PR B)** marks a rule its PR B built on 2026-11-08:
> the layer merge and two effective reads, the artifact list and the skipped report. **Built (PR C)** marks a rule its
> PR C built on 2026-11-09: the effective graph and the action context, the relaxed write rule, and the operator
> rulings of that date (the step enhancer's binding, the one step predicate, the node predicate, the floor of
> activatable kinds). **Built (slice C)** marks a rule the slice C mission (`effective-step-and-action-context-01M774HB`,
> [#467](https://github.com/spec-kitty/spec-kitty-charter/issues/467)) built on 2026-11-12: the ownership rule of the
> effective action context, the compose checks of a step enhancement, the effective step prompt, and the operator
> rulings on #439 and #447. Every rule marked so names the tests and scenarios that pin it.
>
> **Rulings of 2026-11-06.** The operator's answers to the ruleset consistency review (layer-merge mission,
> `research/ruleset-review-2026-11-06.md`, items A1–A15 and B1–B8) are folded into the rules and cite their decision ids (`DM-01M6RA…`, under the layer-merge mission's `decisions/`).
>
> **Rulings of 2026-11-09.** The operator's rulings at the PR C plan point (OD-6, OD-9, D3, D4(a), D15, D16 and the
> node predicate) are folded into W20, E6, E12, E14, C31, E15 and E16 and cite their decision ids (`01M702A…`, `01M7037…`,
> `01M7059…`, `01M70C4…`, `01M70CT…`). Each amended rule names the test that pins it, so a rule cannot pass on its
> wording alone.
>
> **Rulings of 2026-11-12.** The operator's rulings for slice C, relayed by the orchestrator, are folded in as dated
> amendments: the ownership rule (Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`) into E12 and E16; OD-5, the `409` of the
> prompt read (Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`), into E18 and F8; OD-1, OD-2 and OD-4 of the phase-2
> mission into C22, C25, C37, C38, G8 and F6; #439 option (b) into E5, E9 and E14; and #447 option 1 into C25 and C27.
>
> **Explicit rulings, never accidental order.** Every sentence of this page that describes a contest, a winner or a
> tie cites the ruling that settles it or the refusal that stops it. Where a tie is broken by a rule no ruling
> covers, the page says so and names the issue that tracks it (the smallest-URN tie of `satisfiedBy` and
> `overriddenBy`, E5 and E9, is #439).
> *Note 2026-11-12:* #439 is ruled (option (b)): `satisfiedBy` and `overriddenBy` name every overrider in force, by level
> then URN, so no tie is left (E9).

## 1. Notation

Plain Unicode set notation. `⊥` is "absent". A rule written `lfp X. f(X)` is the least fixed point of a monotone `f`
over a finite set, reached by iterating `f` from `∅`; `gfp X. f(X)` is the greatest one, reached by iterating `f` down
from its starting set. Both always exist here, because every universe is finite.

### Levels and packs

- `L = {B, O, R}`: the pack levels built-in, org and project. **Merge order** is `B ≺ O ≺ R`. `ℓ ⪯ ℓ'` reads "`ℓ` is
  `ℓ'` or nearer built-in". The **level that applies** to a set of levels is its `≺`-last member.
- `P`: the configured packs. For `p ∈ P`:
  - `cid(p)` the configured identifier (the directory name), `pid(p)` the descriptor `pack_id`, `name(p)` its name;
  - `ver(p)` its `pack_version`;
  - `lv(p) ∈ L` its level, read from configuration (ADR-0053 d8);
  - `deps(p) ⊆ P × Range` its `depends_on` edges, each with a PEP 440 range `ρ`;
  - `par(p) ∈ P ∪ {⊥}` its `parent_pack`;
  - `ovr(p)` its descriptor `overrides` entries `{artifact, member}`, which settle sibling conflicts.
- `succ(p) = {q | (q, ρ) ∈ deps(p)} ∪ {par(p)} ∖ {⊥}`: the packs `p` builds on.
- `Cdep(S) = lfp X. S ∪ ⋃ {q | (q, ρ) ∈ deps(p), p ∈ X}`: the `depends_on`-only closure, without `parent_pack`.

### Artifacts, kinds and edges

- `K`: the artifact kinds. `Kₐ = K ∖ {template, asset, guard, lifecycle}`: the activatable kinds, each with a `selected` and a
  `deactivated` list; `mission_type` is one of them like any other (A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; one kind registry, decision
  `DM-01M6RCY40CQQTHC130RWF43W0D`). A step `action:M/s` is a graph node of no kind in `Kₐ`: it has no `selected`
  or `deactivated` list and no state of its own, only the state of its mission type (E6, E15).
- `U`: URNs `<kind>:<id>`, with `kind: U → K`. A reference is a bare URN `u` or a qualified `n::u`.
- `holds ⊆ P × U`. A **place** is a pair `(p, u)` with `holds(p, u)`. Two packs holding one URN are two places.
- `Rel`: the relations, among them `requires`, `suggests`, `refines`, `scope`, `instantiates`, `enhances`,
  `overrides`, `replaces`, `specializes_from`, `applies`, `vocabulary`, `delegates_to`, `rejects`,
  `reconciles_tension`, `tension` and `implemented_by`.
- `E ⊆ U × Rel × U`: the edges, after each reference is resolved to the place that holds it. `r(x, y)` abbreviates
  `(x, r, y) ∈ E`.
- `Rc = {requires, suggests, refines, scope, instantiates, enhances}`: the cascade relations.
- `Lin = (specializes_from, {requires, scope})`: the lineage pass-through: the ancestry relation and the relations
  crossed from each ancestor.
- `bnd(u)`: the mission type `u` is bound to, or `⊥`. A step `action:M/s` is bound to `mission_type:M`; an artifact
  that declares a mission-type binding is bound to that mission type. A `step_enhancement` `u` with exactly one
  distinct `enhances` target, an action `a = action:M/s`, is bound to `mission_type:M` too (OD-6, decision
  `01M702A8XYB588PSGTXRKQ4NKK`; E6); every other shape is bound only as it declares.
- `hold(p, M, s) ⇔ holds(p, mission_type:M) ∧ holds(p, action:M/s)`: pack `p` **holds** the step `action:M/s` (OD-9,
  the per-pack form, the operator's wording). `held(C, M, s) ⇔ ∃p ∈ C. hold(p, M, s)`: the closure holds the step
  (the closure form, decision `01M7037V93Y077H8M4W9V8Y6B3`). The place of a held step is the merged winner (C17), so a
  layer override of a held step counts, and a step declared where no pack defines its mission type is held by none.
  `steps(M) = {action:M/s | held(C, M, s)}`.
- `defines(p, u) ⇔ holds(p, u)`; `changes(p, u) ⇔ ∃x. holds(p, x) ∧ overrides(x, u)`. An enhancement is additive and
  changes nothing (A14).
- `fold(u)`: `u` with its id in lower case (`Locale.ROOT`); two URNs with one `fold` and different spellings differ
  only by case.

### Mutability

- `M = (open < extendable < locked)`. `μ_p(u) ∈ M` is pack `p`'s declared level for `u`: its `artifacts` entry, else
  its `kind_defaults` entry, else `open`, **and only for `u` held by `p` or a pack it builds on**:
  `μ_p(u) = open` when `¬∃q ∈ C({p}). holds(q, u)` (A15, decision `DM-01M6RA97220XBR7RR447FE2JXD`). A pack whose
  descriptor names no policy file has `μ_p ≡ open`.
- `μ_S(u) = max {μ_p(u) | p ∈ S}` for a set of packs `S`: the strictest declared level.
- `ops(open) = {replace, register, relate}`, `ops(extendable) = {register, relate}`, `ops(locked) = ∅`, as
  `MutabilityLevel.permits` names them (`REPLACE`, `REGISTER`, `RELATIONSHIP`): replacing an artifact, adding one, and
  adding an edge that touches one. A layer override and an `overrides` edge are a `replace`; an enhancement and a
  removal by a derived pack (C26) are judged separately.

### Presets and the floor

- `Pre(p)`: the presets `p` holds. For a preset `π`: `π[k]` its members of kind `k ∈ Kₐ`, mission types included,
  and `must(π) ⊆ U` its must-have items. `gov(π) ⇔ must(π) ≠ ∅`.
- `Req(C)`: the required set of closure `C` (C31). `Lk(C) = {u | μ_C(u) = locked}`: the locked set.
  `Fl(C) = Req(C) ∪ Lk(C)`: the floor as the writes see it.

### The activation document

- `D = ⟨packs, sel, deny, langs⟩`, the stored `ActivationConfig` (`schemaVersion: 2`):
  - `packs`: `⊥` or a list of `(cid, version?)`, in order;
  - `langs`: `⊥` or a set;
  - `sel, deny: Kₐ → ⊥ or a set of ids`.
  The kind gate `activatedKinds` and the separate `missionTypes` list are withdrawn (A7; W34).
- `⟦x⟧ = ∅` when `x = ⊥`, else `x`: **absent means none**.
- `Sel(D) = {⟨k:i⟩ | i ∈ ⟦sel(k)⟧}`: what the document selects. `Den(D) = {⟨k:i⟩ | i ∈ ⟦deny(k)⟧}`: what it denies.
- `C(D)`: the activation's closure (C1, C2). `H(C)` its held URNs, mission types included. `Mt(C) = {u ∈ H(C) |
  kind(u) = mission_type}`.

## 2. Composition rules

Composition decides which packs one request sees, in what order, and which version of each artifact wins.

**C1 Closure.** `C(S) = lfp X. S ∪ ⋃ {succ(p) | p ∈ X}`.
The composition closure is every pack reachable from the roots over `depends_on` and `parent_pack`.
*Source:* ADR-0048 d8; ADR-0053 d6 (amended 2026-11-03); `PackCompositionResolver.compositionClosure`. **Built.**
> ⚠ **Unclear:** ADR-0048 and ADR-0053 speak of the `depends_on` closure, and ADR-0048 d5 says `parent_pack` "sets no
> load order". The built resolver walks `parent_pack` into the closure, orders by it and counts it as a cycle edge
> (C5, C6); qualified references use the `depends_on`-only closure (C12). No ADR records that lineage widens the
> closure.

**C2 Rooting.** `roots(D) = {cid | (cid, _) ∈ packs}` when `packs ≠ ⊥`, else `P`; the offering's roots are `P`.
An activation composes the closure of its own `packs`, or of every configured pack when `packs` is absent.
*Source:* ADR-0053 d6 (amended); ADR-0020 D4 (amended 2026-11-05); `ClosureQueries.snapshot`. **Built.**

**C3 Known roots.** `roots(D) ⊆ P`, else the write is refused `422 activation_invalid` (`unknown_pack`).
A root that names no configured pack composes nothing.
*Source:* FR-006 of the activation mission; `ClosureQueries.composedOfKnownRoots`, `DocumentRules`. **Built.**

**C4 Isolation.** A pack `q ∉ C(S)` is never judged by the compose of `S`.
An unrelated broken pack does not refuse an activation's closure.
*Source:* activation mission research R2, US2 AS2; `PackCompositionResolver.resolveRooted`. **Built.**

**C5 Order.** `C` is listed in a topological order of `succ` (dependencies first, a `parent_pack` before its child),
ties broken by `lv` in merge order, then by `cid` in code points (a total order, so I1 holds; ADR-0048 d9).
Each level stays contiguous, and the order never depends on how the packs were listed.
*Source:* ADR-0048 d9; logical model (Composition); `PackCompositionResolver.topologicalOrder`, `dependenciesOf`,
`STABLE_ORDER` (`service/packs/.../domain/PackCompositionResolver.java`). **Built.**

**C6 No dependency cycle.** The graph of `succ` on `C` (`depends_on` and `parent_pack` edges) is acyclic, else
`pack_dependency_cycle`; a pack that depends on itself or names itself as its `parent_pack` is a cycle.
A dependency or lineage cycle refuses the compose.
*Source:* ADR-0048 d4; `PackCompositionResolver.dependenciesOf`, `cycleOf`. **Built.**

**C7 Known dependency.** `∀p ∈ C, (q, ρ) ∈ deps(p): q ∈ P`, else `pack_dependency_unknown`.
*Source:* ADR-0048 d4. **Built.**

**C8 Range.** `∀p ∈ C, (q, ρ) ∈ deps(p): ver(q) ∈ ρ`, and the ranges all dependents declare on one `q` overlap, else
`pack_dependency_out_of_range`.
A shared dependency is loaded once, at one version every dependent accepts.
*Source:* ADR-0048 d1, d4, d8; ADR-0053 d4 (PEP 440). **Built.**

**C9 Parent resolves.** `par(p) ≠ ⊥ ⇒ par(p) ∈ P`, else `pack_parent_unresolved`.
*Source:* ADR-0048 d6. **Built.**

**C10 Level direction.** `∀q ∈ succ(p): lv(q) ⪯ lv(p)`, else `pack_level_violation`.
A pack may build on, or derive from, only packs at its own level or one nearer built-in.
*Source:* ADR-0048 d7 (amended 2026-11-03); ADR-0053 d8. **Built.**

**C11 Pack identity.** `pid` and `name` are each unique within `C`, else `pack_duplicate_id` or `pack_name_conflict`.
*Source:* ADR-0053 d6, d7 (amended); `PackCompositionResolver.indexById`, `indexByName`. **Built.**

**C12 Qualified reference.** A reference `n::u` written in `p` resolves to the `q ∈ Cdep({p})` with `name(q) = n`, else
`pack_reference_pack_missing`; and `holds(q, u)`, else `pack_reference_artifact_missing`.
A cross-pack reference must name the referring pack or a pack of its own `depends_on` closure (not its `parent_pack`
line), and that pack must hold the artifact.
*Source:* ADR-0053 d7 (amended 2026-11-03); `PackCompositionResolver.checkReferences`, `dependsOnClosure`. **Built.**

**C13 Bare reference.** A bare `u` written in `p` resolves to `p` when `holds(p, u)`; it is refused
`pack_reference_unqualified` when only another pack of `C` holds `u`; otherwise it has no holder.
*Source:* ADR-0053 d7; logical model (Qualified reference); `PackCompositionResolver.holderOf`. **Built.**
> ⚠ **Unclear:** ADR-0048 d2 rules that a link to an artifact no pack of the closure provides is refused at compose.
> The logical model and the resolution model say a bare reference with no holder simply has none, and the
> action-context walk passes through an undeclared node. Whether the d2 closure check is meant to refuse a dangling
> bare edge is not settled.

**C14 Sibling conflict.** For `u ∈ U` and `ℓ ∈ L`: `|{p ∈ C | lv(p) = ℓ ∧ defines(p, u)}| ≥ 2` or
`|{p ∈ C | lv(p) = ℓ ∧ changes(p, u)}| ≥ 2` is a conflict, refused `pack_sibling_conflict` unless settled (C15).
Two packs at one level that both define, or both override, one artifact conflict; a definer and an enhancer do not,
and two enhancers do not.
*Source:* ADR-0048 d9; ADR-0053 d5 (amended 2026-11-03), d21; A14, decision `DM-01M6RA92BS6GGCYTRCFQDYYY5S`.
**Built (PR A0):** `changedArtifacts` no longer counts `enhances`, so two sibling enhancers compose, and a settlement names exactly one winner.

**C15 Sibling settlement.** Let `Win(u, S) = {m ∈ {pid(s) | s ∈ S} | ∃p ∈ C. {artifact: u, member: m} ∈ ovr(p)}`. A
conflict on `u` among siblings `S` is settled iff `|Win(u, S)| = 1`; then that `m`'s version of `u` is the one in force
at level `ℓ`. No entry, an entry naming a non-contestant only, or entries naming two different winners leave it
refused.
The compose names exactly one winning sibling; there is no "last loaded wins".
*Source:* ADR-0048 d9; ADR-0053 d5; spec FR-012 of the layer-merge mission;
`PackCompositionResolver.refuseContests`. **Built (PR B):** settlement by exactly one winner clears the refusal, and the merge puts `m`'s version in force (#357, DIV-218). A step contest no entry settles passes the compose and is refused at read as `pack_sibling_conflict`. Until #358 any pack of `C` may settle, with no level check (DIV-219). At the #427 landing a contest whose entries name two different siblings, which closure order had settled, was made a refusal (`pack_sibling_conflict`): no tie among contestants is broken by order.
> ⚠ **Unclear:** ADR-0053 d5 says the conflict is settled by "the distribution pack's own `overrides` edge". The
> built resolver and the layer-merge spec use a descriptor `overrides` entry `{artifact, member}` in `pack.yaml`, from
> any pack of the compose. The two are not the same mechanism.

**C16 Artifact identity per level.** `∀ℓ: |{p ∈ C | lv(p) = ℓ ∧ defines(p, u)}| ≤ 1` after C15.
An artifact id is unique within a level; across levels the same id is a layer override, not a conflict.
*Source:* ADR-0053 d6 (amended 2026-11-03). **Built** (as the refusal of C14).

**C17 Layer winner.** `win(u) = the place (p, u)` whose `lv(p)` is the level that applies among
`{lv(q) | holds(q, u), q ∈ C}`, after C15.
Levels merge built-in, then org, then project; the level that applies wins. The level that applies has one place:
two definers at one level are refused by C14 and settled by C15, so no tie reaches the winner.
*Source:* ADR-0053 d6; ADR-0059 D3 (decision 3, across levels the level that applies takes the place whole); spec FR-009.
**Built (PR B).**

**C18 Whole replacement.** `content(u) = content(win(u))`; no field of a losing place is read.
A layer override takes the place of the whole artifact; nothing is inherited.
*Source:* ADR-0059 D3; spec FR-009, US3 AS1. **Built (PR B)** (DIV-216).

**C19 Edges from a loser.** `(p, x)` loses `⇒` every edge whose source place is `(p, x)` is dropped.
An edge the overridden artifact declared leaves with it.
*Source:* ADR-0059 D3; spec FR-010. **Built (PR B).**

**C20 Edges to a loser.** `(p, x)` loses and `r(y, (p, x)) ⇒ r(y, win(x))`, bare or `<pack>::` written.
What depended on the overridden id stays connected to the version in force. An `overrides` edge whose re-pointed
target is its own source never forms: it is refused at registration (C36).
*Source:* ADR-0059 D3; spec FR-010; research R3; A12, decision `DM-01M6RA8S9G57W4BW55SNVHBQ10`. **Built (PR B).**

**C21 Override gate.** A layer override of `u` by the winning pack `w` is allowed iff `μ_{C∖{w}}(u) = open`, else the
read refuses `500 pack_unreadable` naming the artifact, both packs and the level.
Only an `open` artifact may be replaced by a later level; `extendable` and `locked` refuse it.
*Source:* ADR-0012 d1, d8; ADR-0059 D3 ("where the owning pack's level allows it"); spec FR-011; research R3.
**Built (PR B)**, judged over `C ∖ {w}` (DIV-217).
> ⚠ **Unclear:** ADR-0012 d9 names the refusal `governance_policy_violation`; the layer-merge plan surfaces it as
> `500 pack_unreadable` carrying the violation, and that is what is built. The judging set without the winning pack is
> a planning decision, not a ruling. A write over a closure that does not merge answers the same `500` (E20).

**C22 Mutability along the closure.** `μ_C(u) = max_{p ∈ C} μ_p(u)`; `setBy(u)` is the first `p` in closure order with
`μ_p(u) = μ_C(u) > open`.
The level itself is the maximum, so closure order never changes it; `setBy` only names the pack a refusal cites (ADR-0048 d9 order, C5). A target's level is the strictest any pack of the closure declares, whatever the order. A pack's declaration counts
only for what it or a pack it builds on holds (notation, A15): a pack may lock the artifacts of the packs it builds on
(an enterprise pack enforcing a built-in practice), not those of an unrelated pack that shares the closure.
*Source:* ADR-0063 D3; ADR-0012 d3; `PerPackPolicy.levelIn`; A15, decision `DM-01M6RA97220XBR7RR447FE2JXD`.
**Built (PR A0):** the strictest level, and the scope limit: a `kind_defaults` entry locks only the artifacts of the pack and of the packs it builds on.
*Amended 2026-11-12 (slice C, OD-2 (A)): the inherited lock.* A step has no level of its own; it takes its mission
type's. For a step enhancement `u` held by `p` whose sole `enhances` target is `action:M/s`, the level judged is
`μ_{C ∖ {p}}(mission_type:M)`, without the enhancing pack's own policy (B8); a level declared on the step itself is not
judged. `locked` refuses the enhancement: `422 override_not_permitted` at registration and on the impact-checked writes,
naming the enhancing pack and then the locking pack (C25), and `500 pack_unreadable` on the reads and the activation
writes, whatever the activation selects, as C21 refuses a forbidden layer override. `extendable` and `open` take it.
**Built (slice C).** *Pinned by:* `EnhancementComposeTest` (`anEnhancementOfAStepWhoseMissionTypeIsLockedIsNamedWithTheLockingPack`,
`theTwinAnExtendableMissionTypeTakesTheEnhancement`, `theEnhancingPacksOwnLockIsLeftOut`,
`aLevelOnTheStepItselfIsNotTheSubject`), `ClosureViewTest`
(`anEnhancementOfAStepWhoseMissionTypeIsLockedMakesTheMergeUnreadableWhateverIsSelected`,
`theTwinAnEnhancementOfAStepWhoseMissionTypeIsExtendableIsMerged`) and the scenarios of
`step-enhancement-compose.feature` `An enhancement of a step whose mission type is locked makes the activation reads
unreadable, whatever is selected` and its twin.

**C23 Tighten, never loosen.** `S ⊆ S' ⇒ μ_S(u) ≤ μ_{S'}(u)`.
Adding a pack to a closure can raise a target's mutability, never relax it. (A consequence of C22.)
*Source:* ADR-0012 d3; ADR-0063 D3. **Built.**

**C24 Unreadable policy.** `pack.yaml` names a policy file that is missing, not UTF-8, not YAML or invalid
`⇒` the pack is `pack_unreadable` in every closure that holds it.
A lock that cannot be read is never taken as `open`.
*Source:* ADR-0063 D1, D4. **Built.**

**C25 Registration gate.** A write into pack `p` targeting `u` with operation `o` is refused
`409 governance_policy_violation` when `o ∉ ops(μ_{C({p}) ∖ {p}}(u))`: `replace` needs `open`, `register` and `relate`
need not `locked`.
The per-artifact registrations obey the level the packs `p` builds on declare; `p`'s own policy protects its artifacts
from other packs and does not bind `p` itself (B8, as C21 excludes the winning pack).
*Source:* ADR-0012 d1, d9; ADR-0026 D4; ADR-0063 D3; `PackMutabilityPolicy`, `MutabilityLevel.permits`; B8, decision
`DM-01M6RAA6Y7NVVGKVKQV05GZYES`. **Built (PR A0):** the gate is built over `C({p}) ∖ {p}`; `p`'s own policy never
refuses its own write.
*Amended 2026-11-12 (slice C):* two refusals come with the gate. A `relate` that makes a step enhancement of `p` enhance
a step whose mission type is `locked` along `C({p}) ∖ {p}` is refused `422 override_not_permitted`, not
`409 governance_policy_violation`: it is the inherited lock of C22, judged by the impact check (G8) on the registration
and on every impact-checked write. And an `overrides` edge from a kind outside `Kₐ` is refused before the gate is
asked (C27, #447). **Built (slice C).** *Pinned by:* `ImpactRelationsEnhancementTest`
(`anEnhancementOfAStepWhoseMissionTypeIsLockedIsOverrideNotPermittedNamingBothPacks`,
`theTwinAnExtendableMissionTypeTakesTheEnhancement`, `theEnhancingPacksOwnLockOnTheMissionTypeIsLeftOut`) and the
scenarios of `step-enhancement-compose.feature` `A registration that enhances a step whose mission type is locked is
refused override_not_permitted` and `A relationship write into a pack that enhances a step whose mission type is locked
is refused 422 override_not_permitted`, each with its twin.

**C26 Removal by a derived pack.** A derived pack's pack-wide removal of `u` is allowed iff `μ(u) ≠ locked`.
`open` and `extendable` artifacts may be removed by a pack built on them; `locked` ones may not.
*Source:* ADR-0030 d1–d3. **Ruled, not built** (the representation is left to a later record, ADR-0030 d7).

**C27 CRG overrides at read.** `ov(E, T) ⇔ overrides(E, T) ∧ μ_C(T) = open`. For `E` in the candidate set (E4):
`ov(E, T) ⇒ T` is not in force, reason `overridden`; `overrides(E, T) ∧ μ_C(T) ≠ open ⇒ T` stays and `(T, E)` is
listed in `overrideNotApplied`.
Both override paths share one gate: only an `open` artifact can be overridden, by a later level (C21) or by an
`overrides` edge. Such an edge onto an `extendable` or `locked` target is refused at registration
(`409 governance_policy_violation`, C25 with `replace`); one that still meets a non-`open` target at read, because
another pack tightened it later, is not applied.
*Source:* decisions `DM-01M6R0TGX3DSR54EVS58Q1V8FV`, `DM-01M6R2X2SW8KVAJCDX23PGJBMT`; research R11; spec FR-016;
A6, decision `DM-01M6RA86QPBM1VE5QZTB9AZ195`. **Built:** the registration refusal (PR A) and the read (PR B, DIV-221).
*Amended 2026-11-12 (slice C, #447, operator ruling option 1):* `overrides(E, T) ⇒ kind(E) ∈ Kₐ`. An `overrides` edge
whose source is a template, an asset, a guard or a lifecycle is refused, because such a source has no activation state
and cannot stand in for governance: at registration as the violation `overrides_source_not_activatable` of
`422 validation_failed`, at the pack-relative graph document; on `putPackRelationship` as the same violation at `/from`,
nothing written; and on a composing read that meets one in a stored pack, `500 pack_unreadable` naming the graph
document. The read and the write then never disagree on what satisfies a required artifact. An edge of another relation
from such a kind, and an `overrides` edge to one, are not judged by this rule. **Built (slice C).** *Pinned by:*
`OverrideSourceRuleTest` (`anOverridesEdgeFromEachKindThatIsNotActivatableIsOneFaultAtItsFile`,
`anOverridesEdgeFromEachActivatableKindPasses`, `theFourKindsTheRuleRefusesAreExactlyTheKindsThatAreNotActivatable`,
`aSourceQualifiedWithAPackNameIsJudgedLikeABareOne`, `anEdgeOfAnotherRelationFromATemplatePasses`,
`theRuleJudgesTheSourceOnlySoAnOverridesEdgeToATemplatePasses`, `aRelationshipOverridingFromATemplateIsRefusedAndItsTwinFromADirectivePasses`,
`validateModeReportsTheEdgeAsOneBlockingFindingAtItsGraphDocumentAndTheTwinPasses`),
`RelationshipRegistrationServiceTest` (`anOverridesEdgeFromATemplateOrAnAssetIsRefusedAndTheSameEdgeFromADirectiveRegisters`,
`aStoredOverridesEdgeFromATemplateIsALoadViolationAtItsGraphDocument`) and the seven scenarios of
`override-source-kind.feature` (a registration, two edges as two violations, a `requires` edge that registers, the
relationship write and its twin, the unreadable closure and its positive control).

**C28 Override cycle.** A cycle of `ov` that is reachable from `Base ∪ Req(C)` (E3, C31) is refused: at registration by the
impact check (G8), and at read `500 pack_unreadable` should one remain.
The walk starts at each member of `Base ∪ Req(C)` and follows `ov` edges, which end only at `open` artifacts. A cycle
that no member reaches is not judged. No cycle is broken by an arbitrary pick, and the requirement fixed point (E5)
always ends.
*Source:* spec Edge Cases, FR-016; research R2; A2, decision `DM-01M6RA7M56M1409EV9JX1EW83K`; decision
`DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`. **Built:** the registration refusal (PR A) and the read (PR B, DIV-222).

**C29 Winning preset.** For a preset id `i` held by several packs of `C`, the copy held by the last of them in closure
order (merge order) wins as the preset applied (W17); the other copies' members are ignored, but their must-have
items still count (C31). Two copies at one level are refused (C34), except a child's copy of its parent's, which wins
(operator decision `01M6ZCXAJ9MTTF9XJSR89F3NYG`, A1: within a level the replacing copy wins and the replaced copy
requires nothing).
*Source:* ADR-0053 d6 (applied to presets); activation mission Assumptions; `ClosureQueries.winningPresets`. **Built.**

**C30 Governing preset.** `gov(π) ⇔ must(π) ≠ ∅`.
A preset with at least one must-have item governs; nothing else declares it.
*Source:* ADR-0031 d1–d2; ADR-0042 §2. **Built.**

**C31 Required set.** `Req₀(C) = ⋃ {must(π) | π ∈ Pre(p), p ∈ C, gov(π)}` over the copy in force at each **level** of each preset id (A1);
`Req(C) = Req₀(C) ∩ H(C)`; `Unheld(C) = Req₀(C) ∖ Req(C)` is never put in force; reported in `requiredNotHeld` (PR B). Never stored.
The requirement of a governing preset id is the union, across the levels (built-in, org, project), of the must-have
items of the copy in force at each level. Within one level the copy of the pack last in closure order replaces the
others, a child pack's copy its parent's, and a replaced copy requires nothing. A later level can only tighten the
requirement; a must-have item no pack holds is dropped from the floor and reported.
*Source:* ADR-0031 d2; DIV-205; decision `DM-01M6R0TK6F4CC0Z00CZTS21V72` (ratifies DIV-205); `ClosureQueries`
(`RequiredFloor.of(byHolder(closurePackIds, winningPresets(composed)))`), `RequiredFloor.of`; A1, decision
`DM-01M6RA7FHRC5K8QPX7KN5FZ4S8`; A10, decision `DM-01M6RA8MG55V8C2V9PXWDTRNQS`. **Built (PR B)** for the reads: the floor is the union across the levels (A1), read from the merged closure and
restricted to held ids (A10; PR A0), and `requiredNotHeld` reports the rest. The writes' floor refusal and the registration
notices (`floorAdded`, `floorRemoved`) follow the same rule (decision `01M6YKZRM7VF19A57E40D59N81`; ruling `01M6ZCXAJ9MTTF9XJSR89F3NYG`; DIV-205, DIV-225); `/offering` keeps the
winning copies (FR-023). DIV-205 is ratified.
The floor holds activatable kinds only (D16, plan "PR C scope added by operator rulings (2026-11-09)"): the policy and
preset schemas cannot name a step (`action:`), so `Req₀(C) ⊆ Kₐ` and `requiredNotInForce` (E14) never names a step
([DIV-239](/conformance/divergence-register/)). **Built (PR C)** for that sentence, pinned by `ClosureViewSessionStepRefsTest`
(`aRequiredStepThatIsNotInForceIsDroppedAndARequiredArtifactIsKept`,
`thePolicyAndPresetSchemasCannotNameAStepSoTheFloorHoldsNone`). The within-level rule, that a replacing copy wins and a
replaced copy requires nothing, is operator decision `01M6ZCXAJ9MTTF9XJSR89F3NYG`; the union across levels is A1.

**C32 Floor at write.** `Fl(C) = Req(C) ∪ Lk(C)`.
The writes treat a locked artifact as required too.
*Source:* ADR-0012 d7 ("locked implies required"); `ClosureView.isRequired`, `ActivationExclusionService.floorOf`.
**Built.**
> ⚠ **Unclear:** ADR-0012 d7 and the code say locked implies required. The logical model says "a `locked` artifact is
> not thereby required", and the layer-merge research R11 splits the two at read (locked is forced, required is a
> requirement an override may satisfy). A15 ruled the scope of a lock, not whether a lock also forces the artifact
> active.

**C33 Offering stays a catalog.** `/offering` composes `C(P)` and never applies C17–C21 or any activation.
*Source:* ADR-0020 D1; spec FR-023; activation mission FR-020. **Built**: the offering never merges, and the merge is built (PR B) behind the effective reads and the writes only.

**C34 Same-level preset conflict.** For a preset id `i` and `p ≠ q ∈ C` with `lv(p) = lv(q)`, both holding `i`, and
neither on the other's `parent_pack` line: refused `pack_sibling_conflict`.
Two unrelated packs of one level cannot hold one preset id; a child's copy takes the place of its parent's. No
`overrides` entry settles it, since a preset is not an artifact.
*Source:* operator ruling of 2026-11-06; `PackCompositionResolver.refusePresetContests`, called from
`OfferingReadService`. **Built.**

**C35 Case-only mention.** A preset item or a policy lock in `p ∈ C` naming `u'` with `u' ∉ H(C)` and
`∃u ∈ H(C). fold(u) = fold(u')` is refused `pack_reference_artifact_missing`, naming `p` and the holder.
A mention that differs from a held id only by case would govern nothing, so it is refused; a mention of an id no pack
holds is left alone.
*Source:* operator ruling of 2026-11-06; `PackCompositionResolver.refuseCaseOnlyMatches`, called from
`OfferingReadService`. **Built.**

**C36 Edge shape.** Refused `422` at registration (G8):
`enhances⁺(E, E)` (an `enhances` cycle, `enhances(E, E)` included);
an `E` with `enhances(E, T)` whose every `enhances` chain ends at an enhancer, never at an artifact that enhances nothing;
`enhances(E, T) ∧ overrides(E, T)` for one pair;
`overrides(x, y)` whose target, re-pointed by C20, is `x` itself.
An enhancement never stands alone and always links to a real artifact; an artifact cannot both enhance and override one
target; nothing overrides itself.
*Source:* A8, decision `DM-01M6RA8G0RGRWSFW4DERH8W319`; A11 and A12, decision `DM-01M6RA8S9G57W4BW55SNVHBQ10`; operator
ruling Q8 ("enhancers do not stand on their own"). **Built:** the registration refusal (PR A: `enhances_cycle`,
`conflicting_relations`, `self_override`, `422`). PR B adds a merge-time refusal: an `overrides` edge that, re-pointed
to the winner, ends at its own source is refused at `merged()` as `500 pack_unreadable` (`self_override`), on a read and
on a write.

**C37 Step enhancement target held.** For a step enhancement `u` held by `p` whose sole `enhances` edge names
`action:M/s`, bare or qualified `n::action:M/s`: let `q = p` for a bare target, else the pack of `C` whose `name(q) = n`.
`q ∈ Cdep({p})`, else `pack_reference_pack_missing`; and `hold(q, M, s)` (OD-9), else `pack_reference_artifact_missing`.
Both name the enhancing pack and the pack the edge names.
A step enhancement lands only on a step that the pack it names holds, inside its own `depends_on` closure. It closes,
for step enhancements, the gap C12 leaves: an `action:` reference names no artifact kind, so C12 never finds one
missing. An enhancer of another shape (two targets, an action and an artifact, another kind whose target is an action)
is not judged here and keeps E6. Moments: every composing read (`getOffering` included) and the activation writes
answer `500`; a registration and the impact-checked writes (the per-artifact registrations, `putPackRelationship`)
answer `422`, as for every composition code (G8).
*Source:* ADR-0066 d7 and d8 (the target step resolves in the `depends_on` closure); OD-9, decision
`01M7037V93Y077H8M4W9V8Y6B3`; slice C spec FR-002, plan-point squad A5. **Built (slice C).** *Pinned by:*
`EnhancementComposeTest` (`aQualifiedTargetTheNamedPackDoesNotHoldIsArtifactMissingNamingBothPacks`,
`theTwinAQualifiedTargetTheNamedPackHoldsPasses`, `aQualifierNamingAPackOutsideTheDependsOnClosureIsPackMissing`,
`theTwinAQualifierNamingAPackOfTheDependsOnClosurePasses`, `aQualifierNamingNoPackOfTheComposeIsPackMissingNamingTheEnhancingPack`,
`aBareTargetIsResolvedInTheEnhancingPackItself`, `aBareTargetTheEnhancingPackDoesNotHoldIsArtifactMissingThoughAnotherPackHoldsIt`,
`anActionDeclaredWithoutItsMissionTypeIsNoHeldStep`, `anEnhancerOfAnotherShapeIsNotJudged`,
`eachEdgeIsVisitedOnceSoTheCheckIsLinear`), `ClosureViewTest` (`aStepEnhancerOfAStepNoPackHoldsIsRefusedByTheComposeAsTargetMissing`,
`theTwinAStepEnhancerOfTheHeldStepComposesAndIsInForce`,
`aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsRefusedByTheComposeAsTargetMissing`,
`theTwinAStepEnhancerOfAnActionDeclaredWithItsMissionTypeComposes`), `OfferingReadServiceTest`
(`aStepEnhancementOfAStepTheNamedPackDoesNotHoldRefusesTheCompose`, `theTwinAStepEnhancementOfAHeldStepComposes`) and
the scenarios of `step-enhancement-compose.feature` on the reads (`pack_reference_artifact_missing`,
`pack_reference_pack_missing`), the registration and the relationship write, each with its twin.

**C38 Slot declared, in force only.** For every step enhancement `u ∈ Eff`, the winning copy (C17), whose sole target
is `action:M/s`, and every slot id `σ` its `prompt_fragments` name: `σ ∈ slots(prompt(win(action:M/s)))`, the slots
the prompt companion at the merged winner's place of the step declares (F5; a step with no prompt declares none), else
`prompt_slot_missing`, naming the enhancement, its pack, the slot and the pack of the prompt. `u ∉ Eff ⇒` no refusal
(OD-1 (B)).
No fragment is ever dropped silently: a prompt override that drops a slot an enhancement in force fills is refused,
and one that drops a slot no enhancement in force fills composes. Moments and statuses (OD-4 (i)):

- every composing read answers `500`, after the composition codes and the merge's `pack_unreadable` (F6), through an
  explicit `ClosureView.requireSlots(states)` that the read session calls, so the in-force resolution stays pure;
- a registration and the impact-checked pack writes answer `422`, judged for each stored activation whose closure the
  change joins, on that closure as the write leaves it, through the gate `ActivationImpact.requireComposable`, the
  activation named in the detail and the refusal never swallowed (G8, research R7). A change that joins no stored
  activation's closure passes;
- the activation writes answer `422` before the document is stored.

*Source:* ADR-0066 d2, d7 and d8; OD-1 (B), OD-4 (i); slice C spec FR-001, FR-006; research R7, R9; plan-point squad
A2, A3. **Built (slice C).** *Pinned by:* `SlotComposeTest` (`aSlotTheBasePromptDeclaresIsComposed`,
`aSlotAnOverrideDropsIsMissingNamingTheEnhancementItsPackTheSlotAndThePromptsPack`, `theTwinAnOverrideThatDeclaresTheSlotComposes`,
`aStepWithNoPromptInForceDeclaresNoSlot`, `ofTwoEnhancementsOnlyTheOneWhoseSlotIsMissingRefuses`,
`anEnhancementThatIsNotInForceNeverRefuses`, `onlyTheWinningCopyOfASameIdEnhancementIsJudged`,
`theTwinTheWinningCopyThatNamesAnUndeclaredSlotRefuses`, `thePromptInForceIsAtTheWinnersPlaceOfTheStepNotTheWinningMissionTypes`,
`eachEnhancementIsVisitedOnceAndEachMissingSlotIsNamedOnce`), `ClosureViewTest`
(`anInForceEnhancementOfASlotTheOverrideDropsIsRefusedPromptSlotMissingWhileInForceStaysPure`,
`theTwinTheSameEnhancementNotSelectedIsNotJudged`, `theTwinAnOverrideThatDeclaresTheSlotComposes`),
`ActivationImpactAdapterTest` (`aChangeThatLeavesAnEnhancementInForceWithoutItsSlotIsRefusedNamingTheActivation`,
`theTwinTheSameChangeWhereNoStoredActivationPutsTheEnhancementInForcePasses`, `theTwinAChangeWhoseOverrideDeclaresTheSlotPasses`,
`anActivationWhoseClosureTheChangeDoesNotJoinIsNotJudged`, `aChangeToAClosureWithNoViewBeforeIsJudgedOnTheClosureItLeaves`,
`theTwinARepairThatLeavesTheClosureComposingIsLetThrough`, `theNoticesNeverRefuseWhereTheGateDoes`),
`RegistrationImpactTest` (`theWholePackRegistrationAsksTheGateAndItsRefusalIsNotSwallowed`,
`thePerArtifactPathOfTheWritesAsksTheGateAndItsRefusalIsNotSwallowed`, `theTwinAGateThatFindsNothingLetsEveryPathThrough`),
`ActivationServiceTest` (`createRefusesADocumentThatDoesNotComposeAndStoresNothing`,
`putRefusesADocumentThatDoesNotComposeAndLeavesTheStoredOne`, `aPerArtifactWriteRefusesADocumentThatDoesNotComposeBeforeItIsStored`,
each with its twin), `RefusalHandlerTest` (the `422` of each operation) and `ReservedRefusalCodesTest`; the eight
scenarios of `step-enhancement-slots.feature` (a composing read, a registration, a relationship write and an activation
write, each with its twin), and the `errors.feature` outline `prompt_slot_missing is a 422 on <operation>, which judges
an enhancement in force before it stores`.

## 3. Registration rules

Registration writes pack content. The per-artifact registrations are built (ADR-0026); the whole-pack registration
and `DELETE` were ruled on 2026-11-06 and are built in PR A (ADR-0065); the status of each rule below says how far.

**G1 Writable levels.** A registration or deletion of `p` requires `lv(p) ∈ {O, R}`; `lv(p) = B` is
`409 registration_conflict`.
The built-in level is read-only.
*Source:* decision `DM-01M6R1GQYFZ6Q6DFSWNDVTE9X1`; research R1. **Built (PR A)** for a registration: `PackRegistrationService`
refuses an upload at the built-in level with `409 registration_conflict`.

**G2 Full replacement.** After `PUT /packs/{cid}` with archive `Z` succeeds: `stored(cid) = Z`, so every artifact,
edge, preset and file absent from `Z` is absent from the pack.
Registering a whole pack replaces what was known of it, removals included.
*Source:* operator ruling of 2026-11-06; decision `DM-01M6R0TA37CQ0VEMHWSTJF2TRM`; spec FR-001, SC-003.
**Built (PR A).**

**G3 Version rule.** Let `s` be the stored pack and `z` the staged one: `rev(z) = rev(s) ⇒ 200`, nothing written;
`ver(z) = ver(s) ∧ rev(z) ≠ rev(s) ⇒ 409 pack_version_conflict`; and, with `hist(cid)` the version → content-digest
history of every version ever stored under `cid`, `hist(cid)(ver(z)) ∉ {⊥, rev(z)} ⇒ 409 pack_version_conflict`;
otherwise `z` replaces `s` and `hist(cid)(ver(z)) := rev(z)`.
Any version may replace; one version always means one content, also after it was replaced.
*Source:* decision `DM-01M6R0TCAGHF27RKCB20QSCN8W`; spec FR-002; research R4; B1, decision
`DM-01M6RA9BKJY7X9BFV93QW1KM1T`. **Built (PR A).**

**G4 Fixed identity.** On a replace: `pid(z) = pid(s) ∧ lv(z) = lv(s)`, else `422`. On a create: `pid(z)` and
`name(z)` are unused in `P`, else `409 registration_conflict`.
A pack's identity and level are fixed for its configured id.
*Source:* spec Edge Cases, FR-003; decision `DM-01M6R2X0H0VXGZZGSQC9C2SCSF`; research R1. **Built (PR A):** a replace
keeps the level and the `pack_id` (`PackRegistrationService`), and a create refuses a taken `pack_id` or name.

**G5 Archive validity.** An archive with an unknown file, a path leaving the pack, a link or device, an invalid
artifact, more than 32 MiB or more than 10,000 entries is refused (`413` or `422`) before any file is written.
*Source:* spec FR-003, NFR-004, NFR-005; decisions `DM-01M6R1GKE8TAG2QBJT5PAHJQ8E`, `DM-01M6R2WY82DF6DV2FEAK9CK4S2`.
**Built (PR A):** the bounds are `ArchiveLimits`.

**G6 Preconditions.** `If-Match ≠ rev(s) ⇒ 412`; `If-None-Match: * ∧ s exists ⇒ 412`; both headers `⇒ 400`. G3's
no-op is judged before them. Both `DELETE`s require `If-Match` (`428` when absent).
An identical re-send is a no-op even under a stale validator; a removal is always conditional.
*Source:* spec FR-003; research R-lock (API guarantees). **Built (PR A).**

**G7 Write gate on the diff.** Every artifact the replace adds, changes or removes passes C25 and C26 along
`C({z}) ∖ {z}`, else `409 governance_policy_violation`.
The packs the new pack builds on judge the diff; its own policy, old or new, does not (B8).
*Source:* ADR-0063 D3; ADR-0030 d3; plan IC-04 step 7; B8, decision `DM-01M6RAA6Y7NVVGKVKQV05GZYES`. **Built (PR A).**

**G8 Impact check.** Let `P' = P ∖ {s} ∪ {z}` and `Aff(z) = {C'(S) | S ∈ Roots, z ∈ C'(S)}`, where `Roots` is `P'`
and the roots of every stored activation, and `C'` composes over `P'`. Every closure in `Aff(z)` must pass C1–C16,
C20, C21, C27, C28 and C34–C36, and its floor must hold (E13 under I10), else `422` with the composition code, naming
the packs of the failing closure. A closure that does not contain `z` is not judged.
A registration never leaves a closure it joins unable to compose or merge, and never blocks an author over a closure it
does not join.
*Source:* plan IC-04 step 8; A5, decision `DM-01M6RA8225EXG6GAT1MM2R9T6C`. **Partly built (PR A):** the check judges the
composition and the relation rules (C1–C16, C25, C27, C28, C34–C36) over every closure the pack joins and refuses `422`
naming the packs (DIV-604). The registration code does not run the layer merge, so C21 is judged at read, not at
registration, and a floor that stops holding is reported as a notice (G20), not refused. The scope replaces the plan's
one global compose.
*Amended 2026-11-12 (slice C):* the check also judges the compose checks of a step enhancement.

- C37 and the inherited lock (C22, C25) are closure-level: they are judged over every closure the pack joins, and refuse
  `422` with `pack_reference_pack_missing`, `pack_reference_artifact_missing` or `override_not_permitted`.
- C38 needs an activation to say what is in force. It is judged through the gate `ActivationImpact.requireComposable`,
  separate from the notices and called on every check path: the full registration, the per-artifact registrations,
  the relationship write and both `DELETE`s. "Active" is judged per stored activation whose closure the change joins:
  an enhancement is active when it is in force under that stored document, on its closure as the write leaves it
  (research R7). With no activation stored the gate judges nothing.
- The relationship write (`putPackRelationship`, a `relate` in C25's terms) runs, in order, before anything is
  written: #447's `overrides_source_not_activatable` (C27), R-REL's `prompt_fragment_invalid` (F4), the reference
  check of its target (C12), the mutability gate (C25), then this check, with C37, the inherited lock and C38.

**Partly built (slice C)** for these additions; C21 and the floor stay as above. *Pinned by:* the tests named under
C37, C38, C22 and C25, among them `RegistrationImpactTest.theWholePackRegistrationAsksTheGateAndItsRefusalIsNotSwallowed`
and `RegistrationImpactTest.thePerArtifactPathOfTheWritesAsksTheGateAndItsRefusalIsNotSwallowed`.

**G9 Reference check.** For each artifact `u` a `DELETE` or replace removes from `p`, judged against the configured
packs as the request would leave them (`P'`, with `p`'s new tree): `Inbound(p, u) = {(q, a) | q ∈ P', a held by q in
P', a references u, holderOf_q(reference) = p}`, plus every descriptor `overrides` entry naming `p` as member for `u`,
plus every preset item of a pack `q ≠ p` naming `u`; `Inbound(p, u) ≠ ∅ ⇒ 409 artifact_referenced`, listing every
referring artifact or preset with its pack.
A referenced artifact cannot be removed, whether the reference comes from the same pack or from another one; what the
same request removes refers to nothing, so two artifacts that reference each other can be removed together.
*Source:* operator ruling of 2026-11-06; decisions `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`, `DM-01M6R2WSARGC382BHX89GZB64E`,
`DM-01M6R2WVRKB4JGZM40D6QF60BQ`; research R6; A10, decision `DM-01M6RA8MG55V8C2V9PXWDTRNQS`; B3, decision
`DM-01M6RA9MQ6YAP048KD43DNFS4F`. **Built (PR A)** (`InboundReferences`).

**G10 Artifact `DELETE`.** `holds(p, u)` else `404`; then G9, then C25/C26 (`locked` is `409
governance_policy_violation`); then remove `u`'s file and companions, every edge whose source is `u`, its graph node,
and every preset item of `p` naming `u`.
`DELETE` removes one artifact with what only it declared; a preset key it empties reads as not listed.
*Source:* operator ruling of 2026-11-06; decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`; spec FR-005; plan IC-06.
**Built (PR A)** (`ArtifactRemovalService`).
A preset item of the same pack is dropped (this rule); a preset item of another pack is an inbound reference (G9, A10).

**G11 Relationship `DELETE`.** Removes the one edge of the unordered pair `{from, to}` from `p`'s shard or graph
documents; `404 relationship_not_found` when `p` holds none; an edge that is a field of an artifact's body is
`409 registration_conflict`.
*Source:* decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`; ADR-0026 D1.3; spec FR-006; plan IC-06. **Built (PR A)** (`RelationshipRemovalService`).

**G12 Registration by source.** `POST /packs` fetches an allowlisted HTTPS archive, a git repository and ref (system
`git`), or a path under a configured root, then applies G1–G9 and G13.
*Source:* decisions `DM-01M6R0TA37CQ0VEMHWSTJF2TRM`, `DM-01M6R1GNR6Q0YN39568F9DPEVF`, `DM-01M6R1T0HK0F078S0VPA6YSTXW`;
spec FR-004. **Built (PR A):** an HTTPS archive, a git repository and a filesystem path (`PackSourceKind`).

**G13 Atomicity.** A registration or `DELETE` either commits the whole new tree by one directory swap, or changes no
stored file; a concurrent read sees the old pack or the new one.
*Source:* spec FR-007, NFR-006; research R7. **Built (PR A):** `PackCommitStore` commits a registration, a `DELETE` and
the per-artifact registrations by copy and one directory swap.

**G14 Lock order.** Every pack write holds `RegistryWriteLock` and then `PackWriteLocks(p)`, from the precondition to
the swap; readers take neither; downloads, extraction and staging run before the locks.
One fair registry lock closes the cross-pack check-then-act races; the pack lock serializes one pack.
*Source:* research R-lock; data model (Write coordination). **Built (PR A):** `RegistryWriteLock` and `PackWriteLocks`.

**G15 Verified read.** A read records the write generation `g` and the source fingerprint before and after it reads;
`g` odd, `g` changed, or the fingerprint changed `⇒ stale_input`. A closure read retries a `stale_input` up to 3 times.
A read never answers from a mixed state of the packs.
*Source:* decision `DM-01M6R2X563762EDCVBR02W4FK7` (operator, Q7); research R-lock; ADR-0018 D11. **Built:**
`ConsistentRead` checks the fingerprint and the write generation (`PackWriteGeneration`), and `ClosureQueries` retries a
`stale_input` up to three times.

**G16 One writer.** A deployment runs one replica; every lock of G14 and the activation store's per-id lock are
in-process only.
*Source:* ADR-0061 d1–d3. **Built** (documented, not detected).

**G17 Unconditional per-artifact registration.** A per-artifact registration `PUT` without `If-Match` is
last-writer-wins on the whole pack.
*Source:* ADR-0026 D5; research R-lock. **Built.**

**G18 Activations unaffected.** No registration or `DELETE` changes an activation document; a registration reads the
stored activations to report `activationsAffected` (G20).
A removed artifact a document still selects becomes a stale id (W9, E1).
*Source:* research R-lock (The activations side); plan IC-06; B4. **Built (PR A)** for the writes; the read is **built (PR B)**.

**G19 Asynchronous registration.** `PUT /packs/{cid}` and `POST /packs` answer `202 Accepted` with a status URI; the
status resource reports `pending`, then `registered` (with the G20 report) or `refused` (with the refusal G1–G13 would
have answered). Preconditions (G6) and the body bound (G5) may still be answered synchronously.
A client learns that a registration completed from the status URI, not from the request.
*Source:* A5, decision `DM-01M6RA8225EXG6GAT1MM2R9T6C`. **Built (PR A)** (`RegistrationJobs`).

**G20 Impact report.** A completed registration reports, for every closure in `Aff(z)` (G8): the artifacts that joined
or left `Req` or `Lk`, the `Unheld` items (C31), and the stored activations whose effective set changes, by id.
A pack upgrade that moves the floor is never silent.
*Source:* B4, decision `DM-01M6RA9SAKCBSKGX165JM3CAGS`. **Partly built:** the notices carry the floor and lock lists
(`floorAdded`, `floorRemoved`, `locksAdded`, `locksRemoved`; PR A) and `activationsAffected` (PR B). The floor lists
follow the A1 rule, the union across the levels, as the reads do (decisions `01M6YKZRM7VF19A57E40D59N81`, `01M6ZCXAJ9MTTF9XJSR89F3NYG`), and
`activationsAffected` also counts a change of the version in force of an artifact. The `Unheld` items
are not in the notices; the effective skipped report lists them as `requiredNotHeld`.

**G21 Repair writes.** A registration into `p` whose closure `C({p})` does not compose today is let through when the
closure composes after it (G8 holds for the new state); one that leaves it broken is refused as before.
A write that fixes a broken compose is not blocked by the breakage it fixes.
*Source:* ADR-0063 amendment of 2026-11-06 (repair writes); issue #404. **Ruled, not built:** today any write into a
closure that cannot compose is refused.
*Note 2026-11-12 (slice C):* the C38 gate (G8) already judges each joined closure as the write leaves it: a write is
let through when the slots compose after it, and refused when it leaves them broken, whatever they were before.
**Partly built (slice C)** for that gate alone. *Pinned by:* `ActivationImpactAdapterTest`
(`aChangeToAClosureWithNoViewBeforeIsJudgedOnTheClosureItLeaves`, `theTwinARepairThatLeavesTheClosureComposingIsLetThrough`).

## 4. Activation write rules

Every activation write stores one canonical document, judged against one snapshot of its closure. `D` is the stored
document, `D'` the one the write computes, `C = C(D)` and `t` the artifact the path names.

### Document validity

**W1 Closed body.** The body is a closed `ActivationConfig`: an unknown member, a `null`, a duplicate key, a wrong
type or more than 1 MiB is `422 activation_invalid`.
*Source:* ADR-0020 D4, D9 and build notes; activation mission FR-006. **Built.**

**W2 Absent means none.** `⟦⊥⟧ = ∅` for `sel(k)`, `mission_type` included; `packs = ⊥` means `P`; `⊥` and `∅` stay
distinct on the wire.
An absent list puts nothing of its kind in force, `skill` included.
*Source:* ADR-0053 d10; ADR-0013 d2 and ADR-0020 D6 (amended 2026-11-05); DIV-203; A7. **Built** for `sel`, which
holds every kind, `mission_type` included (A7; built, PR A0): the document has no `kinds` or `mts`.
> ⚠ **Unclear:** `langs` does not follow W2 at read. Research R2 of the layer-merge mission has an absent `languages`
> filter nothing and `[]` filter every tagged artifact, while ADR-0053 d10 says an absent activation key means none.

**W3 Canonical form.** Every known spelling of an id (the declared id, its full URN, a case variant of either) is
stored as the declared id; ids are stored sorted and once; `packs` keeps its order; `rev(D) =
"sha256:" + hex(SHA-256(JCS(D)))`, which is the `ETag`. A spelling that matches two held ids differing only by case is
`422 activation_invalid` (`ambiguous_artifact`); an unknown spelling is kept as written for W5 to refuse. A mission
type id is normalized like any other id (A7; built, PR A0).
Two equivalent documents have one revision, so `GET` is byte-stable.
*Source:* ADR-0020 D4; activation mission FR-005, research R3; operator ruling of 2026-11-06 (DIV-208 withdrawn);
`DocumentRules.normalize`, `ArtifactSpellings.resolve`. **Built.**

**W4 No empty deny list.** `deny(k) ≠ ⊥ ⇒ deny(k) ≠ ∅`; `[]` is `422` (`empty_deny_list`). A write that empties a deny
list removes the key.
*Source:* ADR-0020 D4; activation mission FR-006, US1 AS7. **Built.**

**W5 Ids in the closure.** `⋃_k ⟦sel(k)⟧ ∪ ⟦deny(k)⟧ ⊆ held_k(C)` over every `k ∈ Kₐ`, each pinned version `= ver`, no
duplicate pack; else `422` (`unknown_artifact`, `unknown_pack`, `version_not_held`,
`duplicate_pack`), after W3's normalization and, for a replace, after W6's stale drop.
A document names only what its own closure holds. A mission type is judged as any artifact is: `unknown_artifact` at
`/selected/mission_type/<i>` or `/deactivated/mission_type/<i>`, where `unknown_mission_type` was (PR A0).
*Source:* ADR-0020 D9; activation mission FR-006, FR-010; `DocumentRules.check` (`version_not_held`,
`ambiguous_artifact`), `ActivationWrites`. **Built.**

**W6 Floor refusal on the whole document.** Let `New = Den(D')` for a create and `New = Den(D') ∖ Den(D)` for a
replace. `New ∩ Lk(C) ≠ ∅ ⇒ 409 artifact_locked`; otherwise `New ∩ Req(C) ≠ ∅ ⇒ 409 artifact_required`; `requiredBy`
names each pack with its `parent_pack` chain. A replace first drops from its body the ids of `stale(D, C)` (W11) and
reports them in `dropped`; any other id the closure does not hold is still `422` (W5).
A create never denies a locked or required artifact; a replace is judged only on the deny entries it adds, so a
`GET` → `PUT` of the stored document is never refused for the floor or a stale id. Locked is judged first (ADR-0012 d7:
locked implies required, so a document that denies both is refused as locked).
*Source:* ADR-0012 d7; ADR-0013 d5; ADR-0020 D7, D9, D10; DIV-204; `DocumentRules.floorRefusal`; A4, decision
`DM-01M6RA7XC5HC8915GZT2R3H83W`. **Built (PR A0):** the whole-document refusal, judging only the added entries and the replace's stale drop. Relaxed by W26, built in PR C: the `artifact_required` half then asks the in-force
resolution of the document as it will be stored.

### Preconditions and order

**W7 Precondition order (per-artifact and preset writes).** `400` (malformed `If-Match`) → `428` (no `If-Match`) →
`404 activation_not_found` → `412` (stale `If-Match`) → `422` (body, path kind, `cascade`) → one snapshot (W9) →
path id (W10: `422 ambiguous_artifact`, then `404 artifact_not_found`, `mission_type_not_found` or `preset_not_found`) →
W11 → the change → `422` or `409` (W5, W25) → compare-and-set (W30).
Preconditions are judged before content, and no pack is read before them, except that an `If-Match` that is a closure tag
reads the closure to judge the `412` (DIV-234).
*Source:* ADR-0020 D6, D9; RFC 9110 §13.2.2; activation mission FR-010, finding F6; `ActivationWrites`.
**Built; relaxed by the either-tag If-Match (DIV-234).**

**W8 Create and replace order.** `400` (both validators, or `If-None-Match` other than `*`) → `412` (`If-None-Match: *` on
a stored id, or a stale `If-Match`) → `422` → `409`; a refused precondition takes no snapshot; without `If-Match` a
replace is last-writer-wins; `DELETE` answers `204`, then `404` on a second call.
*Source:* ADR-0020 D3, D6, D9; activation mission FR-001 to FR-004. **Built.**

**W9 One snapshot.** Every write takes exactly one verified snapshot of `C`; every walk and every rule read it.
One request never mixes two states of the packs. A closure-tag `If-Match` reads the closure once more, before the write's
snapshot, to judge the `412` (DIV-234).
*Source:* activation mission NFR-007; `ClosureQueries.snapshot`. **Built; relaxed by the either-tag If-Match (DIV-234).**

**W10 Path id.** The path id is first resolved to a declared id (W3's spellings), against `H(C)` and, when the closure
holds no match, against the ids of the stored `D`; two case-only matches are `422 ambiguous_artifact`; a mission type
id is matched exactly (until A7 is built). Then `t ∈ H(C)` (or the preset id is held), else `404`; except that an
unselect of `t ∈ Sel(D)`, or a re-activate of `t ∈ Den(D)`, proceeds for a `t ∉ H(C)`.
A write only sees its closure, but may still remove a stale id the document holds, in the spelling it was stored.
*Source:* activation mission FR-010, FR-017; ADR-0059 amendment A5 (formerly R5); operator ruling of 2026-11-06;
`ActivationWrites.declared`, `requireHeldOrStale`. **Built.**

### The document a write starts from

**W11 Stale drop.** `stale(D, C) = (Sel(D) ∪ Den(D)) ∖ H(C)`; `deadPins(D, C) = {(cid, v) ∈ packs | v ≠ ⊥,
v ≠ ver(cid)}`; the write computes from `D₀ = D ∖ stale(D, C)`, with each dead pin's version removed (its pack kept),
and reports them: the ids in `dropped`, the pins in `droppedPins`.
Every per-artifact and preset write first removes the ids the closure no longer declares and the version pins it no
longer holds, and reports them. Dropping is eager: any write drops every stale id, not only the one it names.
*Source:* operator ruling A5 (formerly R5, ADR-0059 amendment of 2026-11-06); DIV-204 notes; `StaleIds.drop`,
`ActivationWrites.current`; B1, decision `DM-01M6RA9BKJY7X9BFV93QW1KM1T`; B2, decision `DM-01M6RA9G56DTA5GFY5Z28S0DB1`.
**Built (PR A0):** stale ids and dead pins. A create keeps `422 version_not_held` for a pin in its own body.

**W12 Stale drop scope.** W11 leaves `langs` and the `cid`s of `packs` alone; an unknown pack still makes
every per-artifact write `422`; a create drops nothing; a replace drops only what W6 says.
*Source:* ADR-0059 amendment A5; activation mission FR-017; A4 and B1 (decisions above). **Built (PR A0):** a dead pin no longer
makes a per-artifact write `422 version_not_held`, and a replace drops what W6 says.

### The five writes

**W13 Select.** `Sel(D') = Sel(D₀) ∪ {t} ∪ (Reached ∩ Scope)`; `Den(D') = Den(D₀) ∖ ({t} ∪ Clr)`, with `Reached` from
W17 and `Clr` from W22.
A select adds the target and what the cascade reached in scope, and clears the target's own deny entry.
*Source:* ADR-0020 D6; ADR-0013 d3; activation mission FR-011, FR-012; `CascadeRules.applySelect`. **Built.**

**W14 Unselect.** `Sel(D') = Sel(D₀) ∖ {t}`; nothing else, no cascade.
*Source:* ADR-0020 D6; activation mission FR-011. **Built.**

**W15 Deactivate.** `Den(D') = Den(D₀) ∪ {t}`; `Sel(D') = Sel(D₀) ∖ ({t} ∪ (Cand ∖ Kept))`, with `Cand` from W23 and
`Kept` from W24.
Only the named id gets a deny entry; the cascade unselects what only it kept in force.
*Source:* ADR-0013 d3; ADR-0020 D6; activation mission FR-015; `CascadeRules.applyDeactivate`. **Built.**

**W16 Re-activate.** `Den(D') = Den(D₀) ∖ {t}`; nothing else, no cascade.
*Source:* ADR-0013 d3; ADR-0020 D6; activation mission FR-015. **Built.**

**W17 Apply a preset.** For the winning preset `π` (C29) and each kind it lists (`π[k] ≠ ∅`, `k ∈ Kₐ`, mission types
included): `sel'(k) = π[k] ∖ ⟦deny(k)⟧` without `force`, `= π[k]` with `force`. With `force`,
`Den(D') = Den(D₀) ∖ ⋃_k π[k]`. Every other key is unchanged.
A preset replaces each kind list it names, any kind included; `force` clears only the deny entries of its members. A
preset no longer sets a kind gate or a separate mission-type list.
*Source:* ADR-0059 D6; ADR-0020 D6; activation mission FR-016, open review item 4; `PresetRules.apply`; A7, decisions
`DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCY40CQQTHC130RWF43W0D`. **Built (PR A0)** in the uniform form: every kind a preset names, `skill`, `glossary_pack` and `mission_type`
included; the kind gate (`kinds' = π.kinds`) and `mts' = π.mts` are gone.

**W18 A preset walks nothing and denies nothing.** The preset write's `skipped` is empty, and `Den(D') ⊆ Den(D₀)`.
A preset member the closure does not hold is selected and then refused by W5 (`422 unknown_artifact`).
*Source:* ADR-0013 d2; `PresetRules`; Offering and activation (Presets). **Built.**

### The cascade

**W19 Cascade relations.** A walk crosses exactly `Rc`, each edge from its source to its target; `enhances(E, T)` is
crossed from `E` to `T`.
No other relation (`overrides`, `replaces`, `rejects`, `delegates_to`, `vocabulary`, `applies`, the tension edges,
`implemented_by`, pack `depends_on`) is followed.
*Source:* ADR-0059 D2.1, D2.5 and operator ruling A4; DIV-207; `CascadeRules.CASCADE_RELATIONS`. **Built.**
> ⚠ **Unclear:** two design pages still carry the superseded `enhances` pull of ADR-0059 D2.2. The logical model's
> Relations section says the cascade follows "`enhances` both ways" with a read-time pull, and the resolution model's
> step 2 names "the read-time `enhances` pull" as the next mission's. Operator ruling A4 removed the pull at write and
> at read.

**W20 Reach as a least fixed point.** For starts `S`, stops `Σ` and lineage `Lin`:
`Anc(x) = lfp Y. {a ∉ Σ | specializes_from(x, a)} ∪ {a' ∉ Σ | a ∈ Y, specializes_from(a, a')}`;
`Next(X) = {y ∈ H(C) | x ∈ X ∖ Σ, r ∈ Rc, r(x, y)} ∪ {y ∈ H(C) | x ∈ X ∖ Σ, a ∈ Anc(x), r ∈ {requires, scope}, r(a, y)}
∪ {y ∈ steps(x) | x ∈ X ∖ Σ, kind(x) = mission_type}`;
`Reach(S, Σ) = lfp X. S ∪ Next(X)`; `Reached = Reach ∖ Σ`; `Stopped = (Reach ∩ Σ) ∪ {a ∈ Σ | a an ancestor met}`;
`Passed = ⋃ {Anc(x) | x ∈ Reached}`.
The cascade is the closure of the targets over the six relations, the lineage pass-through and a mission type's
steps (crossed like `requires`), stopping at the stops.
*Source:* ADR-0059 D2.1, D2.3, D2.4; activation mission FR-012, FR-014; `Reachability.reach`, `ReachRequest.Lineage`;
A7 Q1, decision `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`. **Built (PR A0)**, the step term included.
Since PR C `steps(M)` is the held form (OD-9, notation): the cascade follows only held steps, so an action declared
where no pack of the closure defines its mission type is no longer followed, and a step declared by a pack beside a
mission type another pack defines is not held either ([DIV-242](/conformance/divergence-register/); decision
`01M7037V93Y077H8M4W9V8Y6B3`). A winning mission type keeps the unique steps of the versions it replaced (A7, C17: a
step is a URN of its own; the architect's C17/C18 dissent, recorded in the PR C plan-point dispositions, stands).
**Built (PR C)**, pinned by `ReachabilityTest.theCascadeFollowsOnlyHeldStepsSoAStepDeclaredWhereNoClosurePackDefinesItsMissionTypeIsNotEntered`,
`EdgeIndexTest` (`anActionDeclaredWithoutItsMissionTypeInAnyPackIsNotAStepOfThatMissionType`,
`aWinningMissionTypeKeepsTheUniqueStepsOfTheVersionItReplaced`) and `StepHoldingTest`.

**W21 Stops and scope.** Select: `Σ = Den(D₀) ∖ {t}`. Deactivate, own walk: `Σ = ∅` (A2). Deactivate, shared walk:
`Σ = Den(D₀) ∪ {t}`. `Scope ⊆ Kₐ` or `all = Kₐ`, `mission_type` included; no `cascade` means `Reach = {t}`.
A select stops at deny entries, a deactivate's own walk stops nowhere, and the shared walk stops at the target and the
deny entries.
*Source:* ADR-0013 d4; ADR-0020 D6 and amendment ruling 6; ADR-0059 D2.4 and operator ruling A2; activation mission
FR-012, finding F8; `CascadeRules.planSelect`, `planDeactivate`, `planShared`. **Built (PR A0)**, with `mission_type`
in `Scope`.

**W22 `includeDeactivated`.** `Σ = (Den(D₀) ∖ {t}) ∖ {u | kind(u) ∈ Scope}`; `Clr = (Reached ∪ Passed) ∩ Den(D₀) ∩
{u | kind(u) ∈ Scope}`.
It clears only the in-scope deny entries the walk passes through; an out-of-scope denied artifact still stops the walk
and keeps its entry.
*Source:* operator ruling A3 (formerly R3, ADR-0059 amendment of 2026-11-06); ADR-0013 d4; DIV-206;
`CascadeRules.walkedThrough`. **Built (PR A0):** B5 (decision
`DM-01M6RA9XQSH3E07C7VCPCVD6R4`) narrows it to `Clr = Reached ∩ Den(D₀) ∩ {u | kind(u) ∈ Scope}`: a lineage ancestor the
walk passes through and does not select keeps its deny entry.

**W23 Deactivate candidates.** `Cand = (Reached_own ∩ {u | kind(u) ∈ Scope} ∩ Sel(D₀)) ∖ Fl(C)`.
What the target's own walk reached, is selected and is in scope is a candidate, unless it is on the floor.
*Source:* ADR-0020 D6; ADR-0059 operator ruling A2; activation mission finding F2; `CascadeRules.candidates`.
**Built.**

**W24 The shared-walk keep set.** `Others = (Sel(D₀) ∪ Fl(C)) ∖ ({t} ∪ Cand)`;
`Kept = Cand ∩ Reach(Others, Den(D₀) ∪ {t}).Reached`.
A candidate another active artifact still reaches, directly or through another kept candidate, stays selected.
*Source:* ADR-0020 D6; ADR-0059 operator rulings A2, A4; activation mission FR-015, Assumptions (`skippedShared`);
`CascadeRules.planShared`. **Built.**

**W25 Floor refusal on a write.** A per-artifact or preset write judges the floor only on `Den(D') ∖ Den(D₀)`: a
deactivate of `t ∈ Lk(C)` is `409 artifact_locked`, of `t ∈ Req(C)` is `409 artifact_required`, before any walk. A
stored deny entry on the floor is tolerated and kept by every write except a deactivate that names it (W26).
*Source:* ADR-0059 D4; DIV-204; activation mission finding F1; `DocumentRules.checkWrite`,
`ActivationExclusionService`. **Built.** Relaxed by W26 (built, PR C). The deactivate refuses before the walk and the
document it computes, cascade included, is judged again before it is stored (`ActivationWrites.commit`).
> ⚠ **Unclear:** ADR-0012 d1's table forbids deactivating an `extendable` artifact, and `MutabilityLevel`'s javadoc
> repeats it, but the activation writes refuse only `locked` (and required) targets. ADR-0030 and the logical model
> treat deactivation as a project's choice, not a mutability operation.

**W26 Relaxed required rule.** A new deny entry on `T ∈ Req(C) ∖ Lk(C)` is accepted iff
`∃E ∈ Eff(D', M): ov⁺(E, T)` (E4–E8, over the document `D'` as it will be stored after the write); `T ∈ Lk(C)` always
refuses. The judgement is the in-force resolution's own: `T` is satisfied exactly when the resolution of `D'` reports
`satisfiedBy` for it (E5, E9), never a second copy of the rule, so an enhancement never satisfies.
A required artifact may be denied while an active override of it is in force in the document being stored, unless it
is locked. A create judges every deny entry of the body; a replace judges only the deny entries it adds; a deactivate
judges the named target whether or not its deny entry is already stored (a second deactivate of a locked artifact is
`409 artifact_locked`, and of a required one whose override was unselected is `409 artifact_required`); a preset applied
with `force` and a select with `includeDeactivated` add no deny entry and judge none (W6, W25, DIV-204). A replace that
adds the override and the deny entry together is accepted. The floor judgement asks the resolution once, and only for a
deny entry on a required, not locked artifact; the per-artifact writes also ask it, through `warned()`, whenever they
apply anything, so a closure whose merge refuses (C21, C28, E7) reaches them as `500 pack_unreadable` (E20, DIV-230).
A later write that removes the override (an unselect) is not refused: it judges no stored entry (only a deactivate of
`T` itself would), and the read lists the entry in `denyNotApplied` and puts `T` back in force (E5, E21).
*Source:* decision `DM-01M6R3YX23H46PXGNEFC31E6CT` (operator, Q9); research R12; plan IC-13; ADR-0059 amendment R6.
**Built (PR C).** Pinned by `DocumentRulesOverrideTest` (one case per scope: create, replace, deactivate, force preset,
select with `includeDeactivated`; the enhancer, the lock, the stored entry and the merge refusal) and by
`activation-deny-satisfied.feature`.
> ⚠ **Unclear:** ADR-0013 d2 and ADR-0020 D7 say a required artifact "is never subtracted" and is "active whatever
> the document says". The 2026-11-06 rulings let an active override hide a required artifact and let it be denied;
> ADR-0059 amendment R6 records the write rule, and those two texts still read as before.

### Report, no-op and storage

**W27 Change report.** `applied = (Sel(D') △ Sel(D₀)) ∪ (Den(D') △ Den(D₀))`, `cleared = Den(D₀) ∖ Den(D')`,
`dropped = stale(D, C)`; `applied` and `cleared` are measured against `D₀`; a kind is never an entry.
*Source:* activation mission FR-011, FR-015, FR-016, FR-017; ADR-0059 amendment A5; `ActivationChange.of`. **Built.**

**W28 Skipped report of a write.** Select: `skippedByScope = Reached ∖ Scope`, `skippedDeactivated = Stopped` (each
with `viaRequires` and `requiredBy`), `unreachedBehindDeactivated = Reach(S, ∅) ∖ (Reached ∪ Stopped)`,
`skippedShared = ∅`. Deactivate: `skippedByScope = (Reached_own ∖ Scope) ∩ Sel(D₀)`, `skippedShared = Kept` plus every
selected floor artifact the target reached that another active artifact reaches, each with `referencedBy`.
*Source:* ADR-0013 d4; ADR-0020 D6; ADR-0059 D2.4; activation mission finding F3, re-review R2; `CascadeRules`,
`Predecessors`. **Built.**

**W29 No-op.** `D' = D ∧ stale(D, C) = ∅ ⇒ 200` with `rev(D)`, empty `applied` and `cleared`, nothing stored.
A write that drops a stale id is never a no-op.
*Source:* activation mission FR-011, FR-017; ADR-0059 amendment A5. **Built.**

**W30 Compare-and-set.** `D'` is stored iff the stored revision still equals the one loaded, else `412`; the write
goes to a temporary file, forced to the device, then moved atomically, under a per-id lock.
*Source:* ADR-0020 D6, D9; activation mission FR-019, NFR-004, NFR-005, research R4. **Built.**

**W31 Who changes a deny entry.** `Den(D') ∖ Den(D₀) ⊆ {t}` and is non-empty only for a deactivate;
`Den(D₀) ∖ Den(D')` is non-empty only for a select (`{t}` and W22), a re-activate, a preset with `force`, W4 and W11.
Only the user's explicit choice adds or removes a deny entry.
*Source:* ADR-0013 d2, d3; ADR-0020 D6, amendment 2026-11-05 to ADR-0013 d2. **Built.**

**W32 Mission types in a write.** *Withdrawn* (A7). A mission type is selected, denied and cascaded like any kind
(W13–W24); a select of `M` cascades to `steps(M)` (W20); a deny of `M` is refused only when `M ∈ Fl(C)` (W25).
*Source:* A7, decisions `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`. Formerly: ADR-0020 amendment 2026-10-31; activation mission US3 AS7, finding
F8. The withdrawal is **built (PR A0)**: selection, deny and cascade treat a mission type as any kind.

**W33 Write warnings.** The change report carries `warnings`, one per artifact of `Sel(D') ∖ Sel(D₀)` that `Eff(D', M)`
leaves out, with the E9 reason that decides it.
A write that will not bring what it selected into force says so; it is still applied. An unselect, a deny and a reactivation
never warn.
*Source:* B6 and B7, decision `DM-01M6RAA2B2G9PNXN2F9SJ7B1CR`. **Partly built (PR B)** (DIV-237): the built warning codes
are `enhanced_target_not_in_force` and `overridden`. No other E9 reason is warned: the code never warns
`mission_type_not_in_force`, so a selected step of a mission type not in force is applied without a warning.

**W34 Withdrawn keys refused.** A create, replace or preset body carrying `activatedKinds` or `missionTypes` is
`422 validation_failed`, one violation per member (`removed_member`), with no deprecation window; core regenerates its DTOs first. A stored version-1 document is
upgraded on read to `schemaVersion: 2`: its `missionTypes` move to `selected.mission_type`, its kind gate is dropped
and every selection is kept (decision `DM-01M6RMJHDKNTXAM055Q8WR6SDN`; the gate was never enforced), and nothing observable changes (locks and the floor were never gated).
Each stored document's `ETag` changes once.
*Source:* A7 Q4, decision `DM-01M6RCY8MYNGNPJ247M2Y4880N`; research `a7-kind-model-2026-11-06.md` (proposed revision, items 2–3). **Built (PR A0).**

## 5. Read rules

Two of the four effective reads, `listEffectiveArtifacts` and `getActivationSkippedReport`, answer since PR B
(2026-11-08); `getEffectiveGraph` and `getEffectiveActionContext` answer since PR C (2026-11-09), so no operation is
reserved. Every rule in this section is **Built (PR B)** or **Built (PR C)** as it says. `M` is the merged closure of `C(D)` (C17–C21, C15), `W` its winning URNs of a kind
in `Kₐ`, `Lk = Lk(C)` and `Req = Req(C)`.
*Note 2026-11-12:* slice C builds a fifth effective read, the effective step prompt (`getEffectiveStepPrompt`). Its
rule is F8, in section 8; E17, E18 and E19 hold for it as they say.

**E1 Stale ids.** `staleIds = (Sel(D) ∪ Den(D)) ∖ W`; a stale id has no state.
A stored document is served as stored; what the packs no longer hold is reported, not refused.
A pinned version the closure no longer holds is reported too, as `{cid, pinned, held}` (B1).
*Source:* ADR-0059 D4; spec FR-017, US3 AS3; data model (EffectiveSkippedReport); B1, decision
`DM-01M6RA9BKJY7X9BFV93QW1KM1T`. **Built (PR B):** `staleIds` and `pinNotHeld`. `GET /activations/{id}` serves the
document as stored.

**E2 Candidates.** `Cand(D) = {u ∈ W ∩ Sel(D) | lang(u) ∧ u ∉ Den(D)}`, where
`lang(u) ⇔ langs = ⊥ ∨ tags(u) = ∅ ∨ tags(u) ∩ langs ≠ ∅`.
A selected artifact of any kind is a candidate when its languages are in force and it is not denied. There is no kind
gate; whether a bound artifact's mission type is in force is judged on the effective set (E6), not on the document.
*Source:* ADR-0020 D7; ADR-0053 d10; spec FR-013; research R2; A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY` (the kind gate withdrawn).
**Built (PR B).** The language filter applies at read only (DIV-227).

**E3 Base set.** `Base = Cand(D) ∪ Lk`, the **candidate set** E4–E7 work on; "active" in the rules below means a
member of the set the rule names, never of `Base` alone.
A locked artifact is a candidate whatever the document or the language filter says.
*Source:* ADR-0012 d7; ADR-0013 d2; ADR-0020 D7; DIV-201; research R11 (operator, Q6). **Built (PR B).**

**E4 Override hiding.** `ov⁺` is the transitive closure of `ov` (C27); `Ov(X) = {T | ∃E ∈ X. ov⁺(E, T)}`;
`A₁ = Base ∖ Ov(Base)`.
Overrides compose transitively from the top: a chain `F → E → T` of candidates leaves `F` in force and hides `E` and
`T`; a hidden overrider still hides what it overrides; a non-`open` target stays and stops the chain there (C27).
*Source:* decisions `DM-01M6R0TGX3DSR54EVS58Q1V8FV`, `DM-01M6R2X2SW8KVAJCDX23PGJBMT`; research R2 step 4, R11; A3,
decision `DM-01M6RA7RPNZS810FTFC98Z13GH`. **Built (PR B)** (DIV-221).

**E5 Requirement.** `Unsat(A) = {T ∈ Req ∖ Lk | T ∉ A ∧ ¬∃E ∈ A. ov⁺(E, T)}`;
`Step(A) = (A ∪ Unsat(A)) ∖ Ov(A ∪ Unsat(A))`; `A₂` is the fixed point of `Step` from `A₁`, satisfaction judged on the
final set.
A required artifact is satisfied by itself in force or by an artifact in force that overrides it, directly or through a
chain (`F → E → T` satisfies `T`); otherwise it is put in force, what it overrides is hidden, and the check repeats
until nothing changes. C28 refuses the `ov` cycles reachable from `Base ∪ Req` that could keep it from ending.
A required artifact is put in force whatever the language filter says: the filter narrows `Cand` (E2), and `Unsat`
does not read it (DIV-226).
*Source:* operator ruling of 2026-11-06 ("required means T, or an active override of T, must be in force");
decisions `DM-01M6R2X2SW8KVAJCDX23PGJBMT`, `DM-01M6R3YTNT1P2MBERBN7WV71ND`; research R11; A2, decision
`DM-01M6RA7M56M1409EV9JX1EW83K`; A3, decision `DM-01M6RA7RPNZS810FTFC98Z13GH`. **Built (PR B)** (DIV-225).
> ⚠ **Unclear:** decision `DM-01M6R2X2SW8KVAJCDX23PGJBMT` says an override **or an enhancement** satisfies a
> requirement; the later `DM-01M6R3YTNT1P2MBERBN7WV71ND` says an enhancer never does. This page follows the later one.
>
> *Resolved on 2026-11-08:* the sources did not say whether a required artifact the language filter excludes is put in
> force, because US3 AS4 stated it only for the kind gate, which A7 withdrew. The service puts it in force (DIV-226),
> pinned by a unit test of the in-force resolution; no contract scenario states it.

*Amended 2026-11-12 (slice C, #439 option (b)):* a requirement that several artifacts in force satisfy by overriding it is
satisfied as by one; `satisfiedBy` names them all (E9). **Built (slice C).** *Pinned by:*
`InForceResolutionOverridersTest.aRequiredArtifactTheDocumentDoesNotSelectIsSatisfiedByEveryOverriderInTheSameOrder` and
`DocumentRulesOverrideTest.aRequirementSatisfiedBySeveralOverridersIsSatisfiedAsByOne`.

**E6 Enhancer and binding pruning.** `Eff₀ = lfp X. {u ∈ A₂ | G(u, X) ∧ (bnd(u) = ⊥ ∨ bnd(u) ∈ X)}`, with
`G(u, X) = ¬∃T. enhances(u, T) ∨ ∃T ∈ X. enhances(u, T)`, except for a **step enhancer** `u` (OD-6): a
`step_enhancement` with exactly one distinct `enhances` target, an action `a = action:M/s`. For it `bnd(u) =
mission_type:M` and `G(u, X) = held(C, M, s)`; a step is never in `Eff`, so the action cannot ground `u` by
membership, and the mission type does.
An enhancer is in force only when it is grounded: one of its targets is in force, and that target is an artifact
that enhances nothing or is itself grounded. An artifact bound to mission type `M`, a step of `M` included, is in
force only while `M` is. A step enhancer is grounded exactly when the mission type of its single `enhances` target is
in force: while `mission_type:M ∉ X` it is pruned with the reason `mission_type_not_in_force`, in the bucket
`missionTypeNotInForce` (decision `01M702A8XYB588PSGTXRKQ4NKK`: the existing reason, no new refusal code in PR C); when
no pack holds the target step (the unheld-target case) it keeps its edge and is pruned with the reason
`enhanced_target_not_in_force`, so I11 holds for it too. Every other shape keeps the rule as written: a step enhancement with two
targets, with a target of another kind or with an action and an artifact, and an enhancer of another kind whose target
is an action. A required or locked artifact pruned by either condition is not in force either; it is reported
required-but-ungrounded (E14). An enhancer is never in force on its own, and nothing bound to a mission type outlives it.
*Source:* operator ruling of 2026-11-06 ("an enhancer needs its target T active to work"); decision
`DM-01M6R3YTNT1P2MBERBN7WV71ND`; research R11 step 4; data model rule 8; A8, decision `DM-01M6RA8G0RGRWSFW4DERH8W319`
(least fixed point); decision `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` (the enhancer rule wins over the floor); A7 Q1, decision
`DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S` (the binding; it replaces the former `stepEnh` exemption); OD-6 (decisions
`01M702A8XYB588PSGTXRKQ4NKK` and, for the held target, `01M7037V93Y077H8M4W9V8Y6B3`; fragment
`2026-11-09-c-step-enhancer-binding.md`). **Built (PR B)** for the binding of step contracts (DIV-224), **Built (PR C)**
for the step enhancer's binding and the unheld target ([DIV-241](/conformance/divergence-register/)). C36 refuses
`enhances` cycles at registration, so the least and greatest fixed points agree on every registered closure.
*Pinned by:* `InForceResolutionTest` (`aStepEnhancerBoundToItsMissionTypeWithNoEnhancesEdgeIsInForceOnlyWhileTheMissionTypeIs`,
`aStepEnhancerWhoseKeptEnhancesEdgeEndsAtNoWinnerIsPrunedForItsTarget`, `anEnhancerOfAStepFollowsTheStepsMissionType`),
`ClosureViewTest` (`aStepEnhancerIsActiveWhenItAndTheMissionTypeOfItsStepAreSelectedAndNoActionIs`,
`aStepEnhancerWhoseMissionTypeIsNotSelectedIsPrunedForTheMissionType`,
`aStepEnhancerOfAStepNoPackHoldsKeepsItsEdgeAndIsPrunedForItsTarget`,
`aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsPrunedForItsTarget`, and the four cases of the shapes that keep the
enhancer rule: two targets, an action and an artifact, one artifact, another kind), and `EffectiveSetTest`
(`aSelectedStepEnhancerIsInForceWhileTheMissionTypeOfItsStepIsAndNeitherListsNorWarnsAboutIt`,
`aSelectedStepEnhancerWhoseMissionTypeIsNotSelectedIsListedUnderTheMissionTypeBucket`).
*Amended 2026-11-12 (slice C, plan-point squad A5):* C37 now refuses the compose of a step enhancer whose step the pack
it names does not hold, so a closure that composes never reaches the unheld-target branch with such a step enhancer.
The branch now covers only what C37 does not refuse: the pure resolution handed an edge that ends at no winner. The
other shapes keep the enhancer rule as written. The two `ClosureViewTest` cases that pinned the branch
(`aStepEnhancerOfAStepNoPackHoldsKeepsItsEdgeAndIsPrunedForItsTarget`,
`aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsPrunedForItsTarget`) are now C37 refusal tests
(`aStepEnhancerOfAStepNoPackHoldsIsRefusedByTheComposeAsTargetMissing`,
`aStepEnhancerOfAnActionDeclaredWithoutItsMissionTypeIsRefusedByTheComposeAsTargetMissing`), each beside its twin that
composes. **Built (slice C).** *Pinned by:* `InForceResolutionTest.aStepEnhancerWhoseKeptEnhancesEdgeEndsAtNoWinnerIsPrunedForItsTarget`
(the branch on the pure resolution) and `ClosureViewSessionStepRefsTest.aStepEnhancementOfAStepNoPackHoldsNamesThatStepInTheReportOfTheRealResolution`
(the report's `NodeReference` of that branch), beside the C37 tests above.

**E7 Withdrawn overrides.** Let `Pr` be the artifacts that declare `overrides` and that E6 removed. E5 and E6 run again without the overrides of
every artifact of `Pr`, and `Pr` grows until no such artifact is newly removed. If an artifact of `Pr` is in force on the
final run, which withdrew its overrides, the answer has no stable value and the read refuses `500 pack_unreadable`
(`override_unstable`). `Eff(D, M)` is the final set.
An overrider that is pruned stops hiding what it overrode, and that can put an artifact back or remove another
enhancer, so the strata repeat to a fixed point (research R13). Only an answer that contradicts itself is refused. C36
refuses the one registered shape (an artifact that both enhances and overrides one target) that had no stable answer.
*Source:* research R13 (it replaces the one re-run of R11 step 5); A2 and A8; A11, decision
`DM-01M6RA8S9G57W4BW55SNVHBQ10`. **Built (PR B)** (DIV-223).

**E8 The effective set.** In closed form, `Eff(D, M)` is the fixed point of `Prune(Require(Hide(Cand(D) ∪ Lk)))` over the
withdrawn overrides of E7, with `Hide`, `Require` and `Prune` as E4, E5 and E6.
What is in force is computed from the document and the merged closure alone, by one function (`InForceResolution`) the
reads share, and W26, the relaxed write rule, asks it through the port for the document a write will store.
*Source:* research R2, R11, R12, R13; plan IC-09, IC-13. **Built (PR B)** for the reads, **Built (PR C)** for W26.

**E9 State and reason.** For `u ∈ W`, `(state, reason)` is the first row that holds: in force → `active, locked`,
`active, required` (a required artifact, whether selected or put in force by E5) or `active, selected`; otherwise bound
to a mission type not in `Eff` → `inactive, mission_type_not_in_force`; enhancer without a grounded target in force →
`inactive, enhanced_target_not_in_force` (E6; these two rows come before the language, deny and selection rows, so a
required or locked artifact they prune is out of force); language → `inactive, language_filtered`; denied →
`excluded, deactivated`; selected, and hidden by E4 or E5 → `inactive, overridden`; otherwise `inactive, not_selected`.
`excluded` belongs to the reason `deactivated` alone: a denied artifact that the enhancer or binding row prunes is
`inactive` with that row's reason. An artifact that is not selected is `not_selected` even when an override hides it, so `not_selected` precedes
`overridden` (research R11 step 3). A required artifact out of force that an override satisfies, directly or through a
chain, keeps its row and gains `satisfiedBy` (the overrider in force): a required, unselected `T` that `F` overrides is
`not_selected` with `satisfiedBy` `F`, and a selected one is `overridden` with `satisfiedBy` `F`.
When several artifacts in force satisfy or hide one artifact, `satisfiedBy` and `overriddenBy` name the smallest URN
(`InForceResolution.overriderOf`). No ruling settles that choice: it is PR B's behavior as found, deterministic and not
changed by PR C, and an operator ruling is tracked in #439.
*Amended 2026-11-12 (slice C, #439, operator ruling option (b)):* there is no "smallest URN" any more.
`satisfiedBy(u)` and `overriddenBy(u)` are lists of every overrider in force that satisfies or hides `u`, directly or
through a chain (the top of a chain is the one in force), sorted by the level of the pack that holds the version in
force of each overrider (built-in, then org, then project), then by full URN in code points. `satisfiedBy` is absent when
no overrider satisfies `u`. No order picks one; the state and the reason do not depend on how many are named. The lists
are carried by `EffectiveArtifact` and its copies (`EffectiveArtifactRead`, `EffectiveContextArtifact`,
`EffectiveGovernanceEntry`, `EffectiveAgentProfileRead`) and by the skipped report (E14). **Built (slice C).**
*Pinned by:* `InForceResolutionOverridersTest`
(`aSelectedArtifactHiddenByOverridersAtThreeLevelsNamesEveryOneBuiltInThenOrgThenProject`,
`aRequiredArtifactTheDocumentDoesNotSelectIsSatisfiedByEveryOverriderInTheSameOrder`,
`theOverridersOfOnePackAreSortedByUrnWithinTheirLevel`, `onlyTheOverridersInForceAreNamedAndOneIsAListOfOne`,
`anArtifactNoOverriderHidesNamesNoneAndTheTopOfAChainIsTheOneInForce`,
`theStateTheReasonAndTheBucketDoNotDependOnHowManyOverridersAreNamed`),
`InForceResolutionTest.aSatisfiedRequirementNamesEveryOverriderInForceOfOneLevelByUrn`,
`ActivationsEffectiveProfilesTest.aSatisfiedByIsTheUrnsOfEveryOverrideInTheOrderGiven`, `EffectiveSetTest.everyOverriderTheResolutionNamesIsCarriedInItsOrderAndNeverSortedAgain`,
`ClosureViewSessionStepRefsTest.everyOverriderThatIsAnArtifactIsCarriedInTheResolutionsOrderAndTheOthersAreDropped`
and the scenarios of `effective-overriders.feature`:
`A selected T hidden by three overriders in force names them all, org before project, by URN within a level`,
`A required T the document does not select names every overrider wherever it is carried` and
`One overrider in force is named in a one-element list`.
There is no `kind_not_activated` and no `kind_default` reason (A7).
*Source:* ADR-0020 D7; data model (EffectiveArtifact, Precedence); decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`
(`kind_default` dropped); A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY` (`kind_not_activated` dropped). **Built (PR B)** (DIV-229).

**E10 In force.** `u` is in force `⇔ u ∈ Eff(D, M) ⇔ state(u) = active`.
*Source:* decision `DM-01M6R2WPZM7K1FBJCFFE0R2TVR` (operator, Q1); ADR-0020 D7. **Built (PR B).**

**E11 The listing.** `listEffectiveArtifacts` lists every `u ∈ W` once, with `state`, `reason`, `required`, `policy =
μ_C(u)`, `packId` and `level` of `win(u)`, and `satisfiedBy`; paged by `(kind, id)` in code points, filtered by
`kind` and `state`; `summary` counts the whole set.
*Source:* decisions `DM-01M6R2WPZM7K1FBJCFFE0R2TVR`, `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; ADR-0059 D5; spec FR-013, FR-022.
**Built (PR B).** An out-of-force `not_selected` artifact that a `suggests` edge of an active artifact reaches also carries
`suggestion {when, reason}` (DIV-235).
> ⚠ **Unclear:** the ruling says "every closure artifact"; the data model lists only winning ids of an activatable
> kind, so templates, assets and the losing versions of a layer override are not listed.

**E12 Enhancement, both ways.** For a node `n ∈ Nd` (E15): `enhancedBy(n) = {E ∈ Eff | enhances(E, n)}`; `E ∉ Eff ⇒ E` is never
returned for `n`, and no read adds `E` or `n` to `Eff`.
An enhancement is returned only when it is itself active, and is read from either end; nothing is pulled in. From the
enhancer's end it is an edge of the graph (E15) and the action context's `enhancements` member (E16) names, for the start
step and for each artifact it lists, the enhancers in force of that node; a step enhancer enhances the step, which no
artifact entry names (decision `01M70C42KC78KB9H6KB7TK9224`).
*Source:* operator ruling A4 (ADR-0059 amendment of 2026-11-06); spec FR-015, US3 AS5, US5 AS3; DIV-207 note.
**Built (PR B)** for the list and the report, which pull nothing in and return an enhancer only in force (DIV-224);
**Built (PR C)** for the reading from either end ([DIV-244](/conformance/divergence-register/)). *Pinned by:*
`ActionContextWalkTest.eachEntryBringsTheEnhancersInForceThatEnhanceItAndTheOnesItEnhancesAndPullsNoneIn`,
`ActionContextWalkTest.theContextCarriesTheEnhancersInForceOfTheStartStepAndTheArtifactsInForceItEnhancesInUrnOrder`, and the
scenarios `US5 AS3 - an enhancer in force is an edge of the graph, read from T's side, and one out of force is not` and
`US5 AS4 - the context holds the artifacts in force the action reaches, with the suggestion only through suggests`.
*Amended 2026-11-12 (slice C, the ownership rule):* the context's `enhancements` and each item's `enhancedBy` cover the
artifacts of every pass of E16, whatever pass listed them, and still pull nothing in. **Built (slice C).** *Pinned by:*
the scenario `US5 AS3 - an enhancer in force is an edge of the graph, read from T's side, and one out of force is not`,
which now lists the tactic `e` project-wide beside `directive:T is enhanced by tactic:e`, and leaves the enhancer out of
force listed nowhere.

**E13 Floor satisfaction.** For every `T ∈ Fl` that is not an ungrounded enhancer (E6): `T ∈ Req ⇒ T ∈ Eff ∨ ∃E ∈ Eff.
ov⁺(E, T)`, and `T ∈ Lk ⇒ T ∈ Eff`; an enhancer never satisfies `T`. An ungrounded floor enhancer is reported
required-but-ungrounded.
*Source:* operator rulings of 2026-11-06 (Q6, Q8); research R11; decision `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` (A9). **Built
(PR B)** (DIV-225).

**E14 Skipped buckets.** Each `u ∈ Sel(D) ∪ Den(D)` that is not in force is listed once, in the bucket of its first
deciding row: `staleIds`, `missionTypeNotInForce`, `enhancedTargetNotInForce`, `languageFiltered`,
`skippedDeactivated` (`viaRequires = false`, `requiredBy = []` at read), `overridden` (`{artifact, overriddenBy}`).
There is no kind bucket at read (A7).
`denyNotApplied` (`{u, reason: required or locked}`) and `overrideNotApplied` (`{T, overriddenBy}`) list rules not
applied; `requiredNotInForce` lists the required or locked artifacts E6 pruned, and only artifacts: the floor holds
activatable kinds (C31, D16), so it never names a step; `requiredNotHeld` lists `Unheld` (C31);
`pinNotHeld` lists the pins E1 reports; `skippedByScope`, `unreachedBehindDeactivated` and `skippedShared` are empty at
read.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; ADR-0059 D4 and follow-ups; spec FR-017, US4 AS1; data model.
**Built (PR B)** (DIV-228); **Built (PR C)** for D16 and the step references (#429): the targets of `enhancedTargetNotInForce`
are `NodeReference`s, so a step enhancer's target step is named ([DIV-239](/conformance/divergence-register/)). The earlier
design names `requiredUngrounded`, `unheldRequired` and `deadPins` are the contract's `requiredNotInForce`,
`requiredNotHeld` and `pinNotHeld`. *Pinned by:* `ClosureViewSessionStepRefsTest`
(`aRequiredStepThatIsNotInForceIsDroppedAndARequiredArtifactIsKept`, `thePolicyAndPresetSchemasCannotNameAStepSoTheFloorHoldsNone`,
`theTargetsOfAnEnhancerNameTheStepsItEnhancesAndTheArtifactsAlike`,
`aStepEnhancementOfAStepNoPackHoldsNamesThatStepInTheReportOfTheRealResolution`) and the scenario `#429 - a target of an enhancer
without a target in force is a NodeReference, on the report and on the context`.
*Amended 2026-11-12 (slice C, #439 option (b)):* `overridden` is `{artifact, overriddenBy}` with `overriddenBy` the
sorted list of E9, and `overrideNotApplied` names its locked target once, with every overrider in force whose edge was
not applied (it was one entry per edge), so `summary.skipped` counts such a target once. **Built (slice C).** *Pinned
by:* `InForceResolutionOverridersTest.anOverrideOfALockedArtifactNotAppliedNamesEveryOverriderInForceOnceForTheTarget`,
`InForceResolutionTest.anOverriddenArtifactNamesTheTopOfTheChainAndEveryOneOfSeveral`,
`ClosureViewSessionStepRefsTest.everyOverriderThatIsAnArtifactIsCarriedInTheResolutionsOrderAndTheOthersAreDropped`
and, in `effective-overriders.feature`, the scenario
`A selected T hidden by three overriders in force names them all, org before project, by URN within a level`.

**E15 Graph projection.** `Nd(D, M) = Eff ∪ {action:M'/s | held(C, M', s) ∧ mission_type:M' ∈ Eff}`: the nodes are the
artifacts in force and the held steps of the mission types in force (the node predicate). `nodes = Nd`; `edges = {(x, r, y) ∈ E_M | x, y ∈ Nd}`;
`layerOverrides = {(u, winner, loser) | u ∈ Nd}`, one record per losing version.
The graph holds only the edges among nodes in force, after C19–C20, and lists the layer overrides applied. `Eff` never
holds a step (a step is no activatable artifact), so a step is a node exactly while its mission type is in force, and an edge
may start at one (a step `scope`s its artifacts). A template, an asset, a guard and a lifecycle have no activation state:
they are never nodes, and their layer overrides are not listed, even when a lock puts one in force inside `packs`
([DIV-240](/conformance/divergence-register/)). Nodes, edges and layer overrides are sorted (by URN; by source, relation and target;
by artifact, then the loser's level and pack), so the answer is byte-stable. The predicate is built in `packs` from the
in-force states, never handed in.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; spec FR-018, US5 AS1–AS3; research R3; decisions `01M702A71YF7WNDTG2AA89K8ZA`
(the node predicate) and `01M7037ZNKJ6JTRKGD7G695ZV1` (it amends the first: only the start node and the graph take the held
steps), `01M7037V93Y077H8M4W9V8Y6B3` (the step predicate, OD-9). **Built (PR C).** *Pinned by:* `GraphProjectionTest`,
`ClosureViewTest` (`theGraphHoldsTheArtifactsInForceAndTheHeldStepsOfAMissionTypeInForceAndNothingElse`,
`theGraphHoldsNoStepWhileTheMissionTypeIsOutOfForce`,
`aKindWithNoActivationStateIsAbsentFromTheGraphAndTheWalkEvenWhenALockPutsItInForceInsidePacks`,
`theLayerOverridesFollowTheNodesOfTheGraph`), `StepHoldingTest` (the per-pack and the closure form),
`ClosureViewSessionStepRefsTest` (`theGraphListsTheOverrideOfAHeldStepAndOfAnArtifactInForce`,
`theGraphListsNoOverrideOfATemplateAnAssetAGuardALifecycleOrAnArtifactOutOfForce`) and the scenarios `US5 AS1`, `US5 AS2` (two).

**E16 Action context.** `getEffectiveActionContext(mt, a)` is started only when `mission_type:mt ∈ Eff`, from the held step
`action:mt/a`, over `E_M` restricted to `Eff`: it lists and walks `Eff` only, and its start node is its one step (E15).
It runs ADR-0018's five steps in three passes over one listing (the ownership rule, ADR-0018 D5 as amended 2026-11-12):

1. `action`: the five steps from `action:mt/a`, ADR-0018's walk as built in PR C;
2. `mission_type`: the five steps from the roots `R₂ = {u ∈ Eff | kind(u) ∈ {directive, tactic, paradigm} ∧
   scope(mission_type:mt, u)}`;
3. `project`: the five steps from the roots `R₃ = {u ∈ Eff | kind(u) ∈ {directive, tactic, paradigm} ∧ own(u) = ∅}`,

where `own(u) = {x | scope(x, u), kind(x) ∈ {action, mission_type}}` is read from every authored `scope` edge of the
merged closure, whether its source is held or not and whatever is in force (research R2; squad A6). The first pass
that reaches an artifact lists it, in every step, vocabulary included; an artifact an earlier pass listed still seeds
the later steps and passes (ADR-0018 D3). Each item carries `appliesThrough`, the pass that lists it. So an artifact
in force that only other actions or other mission types scope is not listed, unless what a listed entry reaches
through steps 2 to 5 reaches it; a required one is no exception. The roots of passes 2 and 3 are directives, tactics
and paradigms only; what their steps 2 to 5 reach is of any kind, as in pass 1. Each pass root is seeded with explicit
hops from its own origin, and the context serializes neither `via` nor `hops`.

Each listed artifact brings its `enhancedBy` (E12); the start step's own enhancers are in `enhancements`, one
`{target, enhancedBy}` per node with an enhancer in force, sorted by `target` and each `enhancedBy` in one order: kind
token, then id, by Unicode code point (the order of the full URNs). A **suggestion** is an artifact the listing gives a
suggestion (E11: `not_selected`, reached through no hard relation by any artifact in force) that a listed entry reaches
through a `suggests` edge; it carries the nearest edge's `when` and `reason` and the `appliesThrough` of the entry whose
`suggests` edge reached it, is not walked through, and so the context's suggestions are a subset of the listing's.
Entries are sorted by full URN string (ADR-0018 D3). `depth` is 0 or 1 and absent is 0, narrower than ADR-0018 D4
([DIV-244](/conformance/divergence-register/)). `mission_type:mt ∉ Eff ⇒ artifacts = [], enhancements = []` in every pass
and `missionTypeNotInForce = {mt}`; an unknown `mt`, or an `a` no pack holds as a step of `mt` (OD-9: `held(C, mt, a)`),
is `404 mission_type_not_found` or `action_not_found`, judged on the merged closure whether or not `mt` is in force and
before a `304`. While `mission_type:mt ∈ Eff`, the context's `_links` carry `prompt`, the effective step prompt of the
same action (F8).
Whether a mission type is in force is read from the effective set, the one predicate E6 and E9 use; the walk stays on
the packs side, and the offering's walk (ADR-0018 D1, D2) is unchanged.
*Source:* ADR-0018 D1, D2, D3, D5 (D5 as amended 2026-11-12); spec FR-019, US5 AS4–AS5 of the layer-merge mission; slice
C spec FR-007 to FR-009, research R1 to R4, operator ruling Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3` (option B);
research open review item 4; A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; decisions `01M7059T4K64TAVKXN3T1FKWYG`
(suggestions), `01M70CTZP7P2EHSCK2G0RET74B` (the listing's scope), `01M70C42KC78KB9H6KB7TK9224` (`enhancements`),
`01M6Y6VE6VZQ4MXD98N93JB3GJ` (one suggestion definition).
**Built (PR C)** for pass 1 ([DIV-243](/conformance/divergence-register/), [DIV-244](/conformance/divergence-register/)).
*Pinned by:* `ActionContextWalkTest` (the five steps, `theEntriesAreSortedByFullUrnStringWhateverStepListedThem`,
`theWalkStartsAtAHeldStepWhoseWinnerIsAPackThatDoesNotDefineTheMissionType`, `anActionDeclaredWhereNoPackDefinesItsMissionTypeIsActionNotFound`,
`aMissionTypeNotInForceAnswersNoEntry`), `StepHoldingTest`, `ClosureViewTest.theWalkStartsAtTheHeldStepOfAMissionTypeInForceAndListsOnlyArtifactsInForce`,
`EffectiveSetTest.anArtifactAnotherArtifactInForceRequiresOutsideTheContextIsNoSuggestionOfTheContextAndOneNobodyRequiresIs`,
`EffectiveContextTest`, `EffectiveReadServiceTest.anUnknownNameIsRefusedBeforeTheTagIsComparedWhateverIfNoneMatchSays` and
`ClosureViewSessionStepRefsTest.theNamesOfAnActionAreJudgedAgainstTheMergedClosureAndNothingElse`; the scenarios `US5 AS4` and `US5 AS5` (two).
**Built (slice C)** for passes 2 and 3, `appliesThrough` and the `prompt` link
([DIV-245](/conformance/divergence-register/)). *Pinned by:* `ActionContextWalkOwnershipTest`
(`anUnscopedDirectiveInForceIsListedProjectWideOnEveryActionOfAMissionTypeInForce`,
`aDirectiveScopedOnlyByAnotherActionIsAbsentWhetherOrNotItIsRequired`, `aDirectiveScopedByTheMissionTypeIsListedThroughTheMissionType`,
`aDirectiveScopedByAnotherMissionTypeIsAbsent`, `aScopeEdgeFromAnUnheldSourceStillOwnsItsTarget`,
`theSameDirectiveWithoutTheEdgeIsListedProjectWide`, `onlyDirectivesTacticsAndParadigmsAreRoots`,
`theFirstPassThatReachesAnArtifactListsIt`, `anArtifactTheActionScopesAndTheMissionTypeAlsoScopesIsListedThroughTheAction`,
`anEligibleRootFirstReachedByVocabularyIsListedThroughTheActionAndStillSeedsItsPass`,
`whatARootReachesThroughAnArtifactAnEarlierPassListedNamesThatRoot`, `anArtifactAnEarlierPassListedStillSeedsTheVocabularyStepOfALaterPass`,
`anArtifactAnEarlierPassListedStillSeedsTheRequiresOfSuggestedStepOfALaterPass`, `theMissionTypePassRunsBeforeTheProjectPass`,
`aRootOutOfForceIsNoRoot`, `aSuggestionCandidateCarriesThePassThatListsIt`, `aSuggestedArtifactInForceOfAProjectRootIsListedThroughTheProject`,
`aMissionTypeNotInForceAnswersNoEntryInEveryPass`, `theOfferingWalkIsUnchanged`,
`whereNoRootAddsAnythingTheEffectiveFormAnswersWhatTheOfferingFormAnswers`, `theEvidenceListsFifteenOfSixteen`,
`theContextIsTheSameAcrossRepeatedWalksAndEveryOrderOfThePacksAndTheirLinks`, `theOwnershipRuleAddsAtMostLinearWork`),
`EdgeIndexTest` (`theScopeOwnersOfAnArtifactAreTheActionsAndMissionTypesThatScopeItHeldOrNot`,
`aScopeEdgeToATargetItsPackDoesNotHoldStillOwnsTheTargetItNames`, `onlyAScopeEdgeFromAnActionOrAMissionTypeOwnsItsTarget`,
`theScopeEdgesOfAVersionALaterLevelReplacedOwnNothing`, `theScopeOwnerBuildVisitsEachEdgeOnceAndOnlyOnce`,
`theScopeOwnersAreWorkedOutOnceAndTheHeldUrnsListEveryArtifactInUrnOrder`), `EffectiveContextTest`
(`eachItemCarriesTheAppliesThroughOfThePassThatListedIt`, `aSuggestionTakesTheAppliesThroughOfItsCandidate`,
`eachAppliesThroughHasTheTokenTheContractSpells`), `ClosureViewSessionStepRefsTest`
(`theContextMapsEachPassOfTheWalkToTheAppliesThroughOfItsEntryAndOfItsCandidate`, `everyPassOfTheWalkHasAnAppliesThroughOfTheSameName`),
`EffectiveReadMapperTest` (`everyAppliesThroughIsMappedToTheValueTheContractSpells`,
`theContextOfAMissionTypeInForceLinksThePromptOfTheSameActionAndOneNotInForceDoesNot`), `EffectiveArtifactCopiesParityTest`
(`theContextItemRepeatsEveryMemberOfTheItemAndItsRequiredMembersAndAddsAppliesThroughAlone`,
`theAppliesThroughOfTheContextItemIsOneOfTheThreeRulesOfTheOwnershipRule`); the three scenarios of
`effective-context-scoping.feature`, whose first example table is the one record of the 16 directives of the
2026-11-12 evidence (`The 16 directives of software-dev/implement: <directive> is <expected>`; `A type-scoped directive
is listed through the mission type, and another type's stays out while research is <research>`; `A suggestion carries
the appliesThrough of the pass whose suggests edge reached it`); the rewritten scenarios `US5 AS3`, `US5 AS4` and `The
context holds every kind the action scopes and the activation selects, and the unscoped directive and tactic in force
(#484)` of `effective-graph-and-context.feature`, which keep negative twins of kinds that are no root; and `FR-007 - the
context of an action of a mission type in force links its prompt, and one not in force does not` of
`effective-step-prompt.feature`.
*Resolved 2026-11-12:* the ⚠ note this rule carried ("FR-019 says the context is what the step contract names") is
settled by the ownership rule above.
*Amended 2026-11-12 (slice C, A7, OD-9; operator ruling of 2026-11-12, research R8; WP09 review cycle 1): one place
rule for a step.* `place(mt, a)` (`ClosureView.stepPlace`) is the pack of the first that holds:

1. `win(mission_type:mt)`, when that version declares `a` among its steps or its on-demand steps;
2. otherwise, when `held(C, mt, a)`, `win(action:mt/a)`, when that pack's version of `mission_type:mt` declares `a`
   (this keeps a step that only a version the winner replaced declares);
3. otherwise there is no place, and the step read answers `404 action_not_found`.

Whether a version declares a step is the kernel's `StepBearer.declaresStep`. The rule places the step for the step
read `getEffectiveMissionStep` (read from the place's pack, with its prompt and guidelines; a place step's skeleton
links name that pack's `getPackMissionSkeleton`), for the action context's `artifact` link (so it and
`effective-artifact` answer the same step), and for a step contract's `step` link under an activation (offered whenever
the step has a place). A step only a version the winning mission type replaced keeps answers `200` on the step read and
on the context. The effective step prompt (F8) is not changed by this rule: it still places the prompt at
`win(action:mt/a)`; whether it follows the one place rule waits on an operator ruling (Q3).
**Built (slice C).** *Pinned by:* the scenarios of `effective-mission-types.feature`
`WP09, A7 - a step only the replaced version keeps is read from its place, as the context and the prompt read it`,
`WP09 review H1 - a step the winning type declares is placed at the winner, whichever pack's graph declares it`,
`WP09 review H1 - the context's artifact link answers a step only the winning type declares, whose action another pack's graph declares`
and `D11 - a step contract links its mission type when the closure holds it, and its step when the step read answers it`;
`ClosureViewTest` (`aStepTheWinningVersionDeclaresIsPlacedAtTheWinnerWhicheverPacksGraphDeclaresTheAction`,
`aStepOnlyTheWinnerDeclaresIsPlacedAtTheWinnerAlthoughTheActionsWinnerIsAnotherPack`,
`aStepOnlyAReplacedVersionKeepsIsPlacedAtTheActionsWinner`, `theTwinAHeldStepNoVersionAtEitherPlaceDeclaresHasNoPlace`,
`theTwinAStepADeclaringVersionKeepsWithoutTheClosureHoldingItHasNoPlace`); `EffectiveReadServiceMissionWinnerTest`
(`aStepsPlaceIsThePackOfTheMergedWinnerOfItsActionWithTheSourceFingerprint`, `aHeldStepNoVersionAtItsPlaceDeclaresIsActionNotFound`,
`aStepTheClosureDoesNotHoldIsTheSessionsRefusalBeforeAnyPlaceIsAsked`); `EffectiveMissionTypeReadServiceTest`
(`aStepOnlyAReplacedVersionKeepsIsReadFromItsPlaceWithThatPacksFilesAndTheMergedStepContract`,
`aStepNoVersionDeclaresIsTheRefusalOfThePlaceAsTheContextGivesItAndNoPackIsRead`,
`aStepTheWinningTypeDeclaresIsReadFromTheWinnerAndNoPlaceIsAsked`,
`aStepContractWhoseStepOnlyAReplacedVersionKeepsLinksTheStepTheStepReadAnswers`,
`theTwinAStepContractWhoseStepNoVersionHoldsLinksNoStep`); `EffectiveMissionTypesControllerTest.aStepOnlyAReplacedVersionKeepsLinksItsSkeletonInThePlacesPack`;
and `EffectiveReadServiceTest.theContextNamesThePackOfTheStepsPlaceAndNoneWhenTheSetDoesNotListTheMissionType`.

**E17 Validators.** `ETag = sha256("charter-effective-read\0v2\n", read, rev(D), closureFingerprint(C), query)`; a weak match
on `If-None-Match` answers `304` with no body.
An effective answer changes with the document or the packs of its closure, and nothing else. `query` is the normalized
`kind`, `state`, cursor and page size, so each representation has its own tag; the graph's `read` has no query, and the context's is
`(mt, a, depth)`. `getActivation`'s tag has the same
recipe with no query; an activation write's `If-Match` takes `*`, `rev(D)` or that tag, and a closure tag that no
longer matches is `412`.
*Source:* ADR-0059 D5 and its amendment of 2026-11-08; research R5; decisions `01M6Y11E55YP5TG1EJ1MDKCFRP`,
`01M6Y3GZFRC5WEQQ7G6GT0YMW3`; spec FR-020. **Built (PR B)** (DIV-231, DIV-233, DIV-234).
*Amended 2026-11-12 (slice C, research R10, squad D1):* the domain separator is `"charter-effective-read\0v3\n"` for every
effective read, because the context answers more under an unchanged revision and closure fingerprint (E16), so a tag
from before never matches and every effective read moves once. A read `PROMPT` is added, the effective step prompt
(F8), with the query `(mt, a)`; the closure fingerprint digests every loaded file, the step prompts and the prompt
fragments included, so a fragment's bytes move the tag and a pack outside the closure does not. The closure fingerprint
digests each pack's files on its own, in closure order (level, then pack id, dependencies first), so it no longer
depends on the order in which the packs are configured; the tags of a closure of several packs change once with it.
**Built (slice C).** *Pinned by:* `EffectiveTagTest` (`theContextTagOfAFixedInputIsAFixedLiteral`,
`thePromptHasATagOfItsOwnPerStepThatMovesWithTheClosureAndNotWithAPackOutsideIt`),
`ClosureQueriesTest.theClosureFingerprintIsTheSameWhateverOrderThePacksAreConfiguredIn`, `EffectivePromptDeterminismTest`
(`oneHundredReadsAnswerTheSameBodyAndTheSameTag`, `everyPermutationOfTheConfiguredOrderOfTheThreePacksAnswersTheSameBodyAndTheSameTag`)
and the scenario `Research R10 - the ETag changes with a fragment's bytes and not with a pack outside the closure` of
`effective-step-prompt.feature`.

**E18 Read refusals.** `404 activation_not_found` for an unknown activation; `500` with the composition code, or
`pack_unreadable` for C21, C28 or E7, when `C` cannot compose or merge.
*Source:* spec FR-021. **Built (PR B).** `getActivation` answers the same `500`s. **Built (PR C)** for the graph and the context
(the context adds the `404`s of E16).
*Amended 2026-11-12 (slice C, OD-5, Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`):* the effective step prompt (F8)
answers, in this order and every one before a `304`: `404 activation_not_found`; then the closure's composition codes
and the merge's `pack_unreadable` (the names are judged on the merged closure, so a closure that cannot compose or merge
answers its `500` first); then `404 mission_type_not_found` and `404 action_not_found` as E16 judges them; then
`409 mission_type_not_in_force` for a mission type the closure holds and the activation does not put in force; then
`500 prompt_slot_missing` (F6). The action context answers `200` with an empty context for the
same mission type (E16); the two reads differ on purpose, and ADR-0066's annotation of decision 9 records why. Every
composing effective read also answers C37's codes, the inherited lock's `pack_unreadable` and C38's
`500 prompt_slot_missing`. **Built (slice C).** *Pinned by:* `EffectiveReadServiceTest.thePromptIsTheSessionsPromptOfTheStepUnderItsOwnTagWithTheReadsFingerprints`,
`ActivationControllerTest.thePromptAnswersItsBodyWithItsTagAndTheFingerprintItVerified`, `RefusalHandlerTest` and the
scenarios of `effective-step-prompt.feature` `FR-004 - a mission type the closure holds and the activation does not put
in force is 409, where the context answers empty`, `Research R10 - each 404 is judged before the 304, and so is the
409; a path segment outside its pattern is 400` and `Research R10 - a closure whose enhancement in force names a slot
the prompt in force does not declare is 500 before the 304`.

**E19 One verified read.** Each effective read composes and merges `C` once, from one verified read (G15), retried up
to 3 times on `stale_input`.
*Source:* spec NFR-007; decision `DM-01M6R2X563762EDCVBR02W4FK7`. **Built (PR B).**

**E20 Writes walk merged edges.** The write cascade (W20) walks `E_M`, the edges of the merged closure after C17–C20,
so a cascade never follows an edge a losing version declared; W26 and W33 read `Eff` over `M`.
*Source:* research R2 (open point), open review item 3; plan IC-07, IC-13; decision `DM-01M6R4W1H9P3451V89KHP97XKM`
(merged edges). **Built (PR B)** (DIV-230): the cascade walks the merged edges, builds the merge, and so a write over a closure that
does not merge answers `500` with `pack_unreadable` or `pack_sibling_conflict`.
> ⚠ **Unclear:** W23–W24 treat every `Fl(C)` item as active, while at read a required item may be hidden by an override
> and a floor enhancer may be ungrounded (E6). Whether the keep set should use `Eff` is not ruled.

**E21 Deny entries not applied.** `GET /activations/{id}` carries a marker (`denyNotApplied`, a count or the list) when
`Den(D) ∩ Fl(C) ≠ ∅`; the document itself is served as stored.
A project sees that a deny entry it stored no longer applies (for example after a pack upgrade raised the floor),
without an effective read.
*Source:* B4, decision `DM-01M6RA9SAKCBSKGX165JM3CAGS`. **Built (PR B)** (DIV-233): `denyNotApplied` is the list of the
deny entries a lock or the floor overrides, present only when there is one. A required artifact that an override
satisfies is not listed.

## 6. Invariants

Each invariant holds for every stored document and every answer, given the rules above. Each follows from the rules it
names.

**I1 Deterministic composition.** The closure, its order, every winner and every refusal are functions of the
configured packs alone, never of the order they are listed in. (C1, C5, C14, C15, C17, C29)

**I2 One version per id.** After the merge, `win` is a function on `H(C)`. (C14–C17)

**I3 Merge order agrees with dependencies.** `q ∈ succ(p) ⇒ lv(q) ⪯ lv(p)`, so no pack builds on a pack whose level
is merged after its own. (C10)

**I4 Monotone mutability.** `μ_C(u)` never decreases as packs join `C`. (C22, C23)

**I5 Canonical, valid storage.** Every stored `D` is canonical (W3, W4), and, at the moment it was written, every id it
holds is held by its closure (W5, W11). Its revision is `rev(D)`. A `PUT` of a fetched `config` is never refused for
the floor or a stale id (W6, A4): with nothing stale it returns the same `ETag`; otherwise it drops the stale ids and
reports them.

**I6 The floor is derived.** No stored document holds `Req`, `Lk` or a skipped report. (ADR-0020 D4, C31)

**I7 Deny entries are the user's.** Every deny entry in a stored `D` was added by a deactivate of that id, or by a
create or replace whose body named it. (W31)

**I8 No new floor denial.** No write adds a deny entry on `Lk(C)`; no write adds one on `Req(C)` except under W26.
(W6, W25, W26)

**I9 Locked is in force.** `Lk(C) ∖ Ung ⊆ Eff(D, M)` for every `D`, where `Ung` is the ungrounded floor enhancers: no target in force or, for a step enhancer, its mission type not in
force (E6); no override hides a non-`open` artifact. (E3, E4, E6, C27)

**I10 Requirement holds.** Every `T ∈ Req(C)` is in force or overridden, directly or through a chain, by an artifact
in force, unless it is an ungrounded enhancer (no target in force or, for a step enhancer, its mission type not in force),
which is reported required-but-ungrounded.
(E5, E6, E13)

**I11 No enhancer stands alone; nothing outlives its mission type.** An enhancer in force is grounded. For an enhancer
of artifacts, `E ∈ Eff ∧ ∃T. enhances(E, T) ⇒ ∃T ∈ Eff. enhances(E, T)`, and every `enhances` chain from `E` inside
`Eff` ends at an artifact that enhances nothing. A step enhancer's single target is a step, which is never in `Eff`, so
it is grounded by its mission type instead: the step is held, `held(C, M, s)`, and `bnd(E) = mission_type:M ∈ Eff`
(OD-6); an enhancer of an unheld step is pruned. Also `u ∈ Eff ∧ bnd(u) ≠ ⊥ ⇒ bnd(u) ∈ Eff`. Floor
membership is no exception to either (decision `DM-01M6RFGFW3S0YJ222QMHD2W11B`). (E6, C36, OD-6, DIV-241)

**I12 No ancestor in force by lineage.** A cascade never adds a `specializes_from` ancestor it passed through to
`Sel(D')`, and (once B5 is built) never clears its deny entry. (W20, W22)

**I13 Termination.** Every walk, every closure and every fixed point terminates: the universes are finite, each
node is visited once, and the requirement iteration ends because C28 refuses the `ov` cycles it could loop on.
(C1, W20, E5, E6, C28)

**I14 Cascading deactivate.** A cascading deactivate of `t` leaves `Sel(D₀) ∖ ({t} ∪ (Cand ∖ Kept))`: it removes `t`
and what `t`'s walk reaches unless something else active keeps it. No selection history is kept, so a cascading
select followed by a cascading deactivate of `t` also unselects what the user had selected before and only `t` reached;
the deactivate is not an exact inverse of the select. (W13, W15, W21, W23, W24, operator ruling A2; A13, decision
`DM-01M6RA8XSM9CZZX1ADX6G80T3X`)

**I15 Reports are exact.** At read, each artifact the document names and that is not in force is in exactly one bucket
(E14); at write, `applied`, `cleared` and `dropped` are disjoint (W27).

**I16 Write and read agree.** A deny entry W26 accepts is one E5 judges satisfied, because both evaluate `Eff` over the
document as stored. (W26, E8)

**I17 No mixed state.** No read or write answers from two states of the packs; a straddled read is `stale_input`.
(W9, G13, G15, E19)

**I18 The offering ignores activations.** `/offering` and `listActivationPacks` never depend on a document's
selections. (C33)

**I19 Requirements and locks only tighten.** Adding a pack, or a copy of a preset, to a closure never removes an item
from `Req(C)` or `Lk(C)`. (C22, C23, C31; A1)

**I20 No silent floor change.** Every registration that changes `Req` or `Lk` of a closure names the change and the
activations it affects (G20), and every stored deny entry that the floor overrides is marked on read (E21). (B4)

## 7. Source and status table

| Rule | Source | Status |
|---|---|---|
| C1 Closure | ADR-0048 d8; ADR-0053 d6 | Built |
| C2 Rooting | ADR-0053 d6; ADR-0020 D4 | Built |
| C3 Known roots | activation mission FR-006 | Built |
| C4 Isolation | activation mission R2, US2 | Built |
| C5 Order | ADR-0048 d9; logical model; `dependenciesOf` | Built |
| C6 No dependency cycle | ADR-0048 d4; `dependenciesOf` (`parent_pack` included) | Built |
| C7 Known dependency | ADR-0048 d4 | Built |
| C8 Range | ADR-0048 d1, d4, d8; ADR-0053 d4 | Built |
| C9 Parent resolves | ADR-0048 d6 | Built |
| C10 Level direction | ADR-0048 d7; ADR-0053 d8 | Built |
| C11 Pack identity | ADR-0053 d6, d7 | Built |
| C12 Qualified reference | ADR-0053 d7; `dependsOnClosure` | Built |
| C13 Bare reference | ADR-0053 d7 | Built |
| C14 Sibling conflict | ADR-0048 d9; ADR-0053 d5, d21; A14 | Built (PR A0) |
| C15 Sibling settlement | ADR-0053 d5; spec FR-012; A14 | Built (PR B), except that #358 still lets any pack of `C` settle |
| C16 Artifact identity per level | ADR-0053 d6 | Built |
| C17 Layer winner | ADR-0053 d6; ADR-0059 D3 | Built (PR B) |
| C18 Whole replacement | ADR-0059 D3 | Built (PR B) |
| C19 Edges from a loser | ADR-0059 D3 | Built (PR B) |
| C20 Edges to a loser | ADR-0059 D3; A12 | Built (PR B) |
| C21 Override gate | ADR-0012 d1, d8; ADR-0059 D3; FR-011 | Built (PR B) |
| C22 Mutability along the closure | ADR-0063 D3; A15; OD-2 (A) | Built (PR A0); the inherited lock of a step enhancement built (slice C) |
| C23 Tighten, never loosen | ADR-0012 d3 | Built |
| C24 Unreadable policy | ADR-0063 D4 | Built |
| C25 Registration gate | ADR-0012 d1, d9; ADR-0026 D4; B8; OD-2 (A); #447 | Built (PR A0); `422 override_not_permitted` for the inherited lock built (slice C) |
| C26 Removal by a derived pack | ADR-0030 d1 to d3 | Ruled, not built |
| C27 CRG overrides at read | `DM-01M6R0TGX3`, `DM-01M6R2X2SW`; R11; A6; #447 option 1 | Built (registration PR A, read PR B); an activatable source only built (slice C) |
| C28 Override cycle | spec FR-016; A2 | Built (registration PR A, read PR B) |
| C29 Winning preset | ADR-0053 d6 | Built |
| C30 Governing preset | ADR-0031; ADR-0042 §2 | Built |
| C31 Required set | ADR-0031; DIV-205; `DM-01M6R0TK6F`; A1, A10; D16; `01M6ZCXAJ9` | Built (PR B) for the reads, the writes' floor refusal and the registration notices (the A1 union); held-only built (PR A0); activatable kinds only built (PR C, D16); `/offering` keeps the winning copies |
| C32 Floor at write | ADR-0012 d7 | Built |
| C33 Offering stays a catalog | ADR-0020 D1; FR-023 | Built |
| C34 Same-level preset conflict | operator 2026-11-06; `refusePresetContests` | Built |
| C35 Case-only mention | operator 2026-11-06; `refuseCaseOnlyMatches` | Built |
| C36 Edge shape | A8, A11, A12 | Built (registration PR A; the merge-time refusal PR B) |
| C37 Step enhancement target held | ADR-0066 d7, d8; OD-9; slice C FR-002; squad A5 | Built (slice C) |
| C38 Slot declared, in force only | ADR-0066 d2, d7, d8; OD-1 (B), OD-4 (i); slice C FR-001, FR-006; R7 | Built (slice C) |
| G1 Writable levels | `DM-01M6R1GQYF` | Built (PR A) for a registration |
| G2 Full replacement | operator 2026-11-06; `DM-01M6R0TA37` | Built (PR A) |
| G3 Version rule | `DM-01M6R0TCAG`; B1 | Built (PR A) |
| G4 Fixed identity | spec FR-003; `DM-01M6R2X0H0` | Built (PR A) |
| G5 Archive validity | FR-003, NFR-004, NFR-005 | Built (PR A) |
| G6 Preconditions | FR-003; research R-lock | Built (PR A) |
| G7 Write gate on the diff | ADR-0063 D3; plan IC-04; B8 | Built (PR A) |
| G8 Impact check | plan IC-04; A5; slice C R7 | Partly built (PR A): composition and relation rules; not C21 or a floor refusal. C37, the inherited lock and the C38 gate per stored activation built (slice C) |
| G9 Reference check | operator 2026-11-06; `DM-01M6R0TEM8`, `DM-01M6R2WSAR`, `DM-01M6R2WVRK`; A10, B3 | Built (PR A) |
| G10 Artifact `DELETE` | operator 2026-11-06; FR-005 | Built (PR A) |
| G11 Relationship `DELETE` | `DM-01M6R0TEM8`; FR-006 | Built (PR A) |
| G12 Registration by source | `DM-01M6R1GNR6`, `DM-01M6R1T0HK`; FR-004 | Built (PR A) |
| G13 Atomicity | FR-007; research R7 | Built (PR A) |
| G14 Lock order | research R-lock | Built (PR A) |
| G15 Verified read | `DM-01M6R2X563`; ADR-0018 D11 | Built |
| G16 One writer | ADR-0061 | Built (documented) |
| G17 Unconditional per-artifact registration | ADR-0026 D5 | Built |
| G18 Activations unaffected | research R-lock; B4 | Built (PR A); the read of G20 (PR B) |
| G19 Asynchronous registration | A5 | Built (PR A) |
| G20 Impact report | B4 | Partly built: floor and lock lists (PR A), `activationsAffected` (PR B); no `Unheld` |
| G21 Repair writes | ADR-0063 amendment 2026-11-06; #404 | Ruled, not built; partly built (slice C) for the C38 gate |
| W1 Closed body | ADR-0020 D4, D9 | Built |
| W2 Absent means none | ADR-0053 d10; DIV-203; A7 | Built; one `sel` form built (PR A0) |
| W3 Canonical form | ADR-0020 D4; operator 2026-11-06; `ArtifactSpellings` | Built (spellings normalized; DIV-208 withdrawn) |
| W4 No empty deny list | ADR-0020 D4 | Built |
| W5 Ids in the closure | ADR-0020 D9; `DocumentRules.check` | Built; `unknown_artifact` for a mission type (PR A0) |
| W6 Floor refusal, whole document | ADR-0012 d7; ADR-0013 d5; ADR-0020 D9; DIV-204; A4 | Built (PR A0); relaxed by W26 (PR C) |
| W7 Precondition order | ADR-0020 D6, D9 | Built |
| W8 Create and replace order | ADR-0020 D3, D6, D9 | Built |
| W9 One snapshot | activation mission NFR-007 | Built |
| W10 Path id | activation mission FR-010; A5; `ActivationWrites.declared` | Built |
| W11 Stale drop | operator ruling A5; B1, B2 | Built (PR A0) |
| W12 Stale drop scope | A5; FR-017; A4, B1 | Built (PR A0) |
| W13 Select | ADR-0020 D6; ADR-0013 d3 | Built |
| W14 Unselect | ADR-0020 D6 | Built |
| W15 Deactivate | ADR-0013 d3; ADR-0020 D6 | Built |
| W16 Re-activate | ADR-0013 d3; ADR-0020 D6 | Built |
| W17 Apply a preset | ADR-0059 D6; ADR-0020 D6; A7 | Built (PR A0) |
| W18 A preset walks nothing | ADR-0013 d2 | Built |
| W19 Cascade relations | ADR-0059 D2, A4; DIV-207 | Built |
| W20 Reach as a least fixed point | ADR-0059 D2.1, D2.3, D2.4; DIV-206; A7 Q1; OD-9 `01M7037V93` | Built; the step term built (PR A0); held steps only (PR C, DIV-242) |
| W21 Stops and scope | ADR-0013 d4; ADR-0059 A2; A7 | Built; `mission_type` in scope built (PR A0) |
| W22 `includeDeactivated` | operator ruling A3; B5 | Built; B5 narrowing built (PR A0) |
| W23 Deactivate candidates | ADR-0059 A2; finding F2 | Built |
| W24 The shared-walk keep set | ADR-0059 A2, A4 | Built |
| W25 Floor refusal on a write | ADR-0059 D4; DIV-204 | Built; relaxed by W26 (PR C) |
| W26 Relaxed required rule | `DM-01M6R3YX23`; research R12; ADR-0059 R6 | Built (PR C) |
| W27 Change report | FR-011 to FR-017; A5 | Built |
| W28 Skipped report of a write | ADR-0013 d4; ADR-0020 D6 | Built |
| W29 No-op | FR-011; A5 | Built |
| W30 Compare-and-set | ADR-0020 D6, D9 | Built |
| W31 Who changes a deny entry | ADR-0013 d2, d3 | Built |
| W32 Mission types in a write | A7 | Withdrawn; the withdrawal built (PR A0) |
| W33 Write warnings | B6, B7 | Partly built (PR B): `enhanced_target_not_in_force` and `overridden` only |
| W34 Withdrawn keys refused | A7 Q4 | Built (PR A0) |
| E1 Stale ids | ADR-0059 D4; FR-017; B1 | Built (PR B) |
| E2 Candidates | ADR-0020 D7; ADR-0053 d10; FR-013; A7 | Built (PR B) |
| E3 Base set | ADR-0012 d7; ADR-0020 D7; Q6 | Built (PR B) |
| E4 Override hiding | `DM-01M6R0TGX3`, `DM-01M6R2X2SW`; A3, A6 | Built (PR B) |
| E5 Requirement | operator 2026-11-06; `DM-01M6R3YTNT`; A2, A3; #439 | Built (PR B); every overrider named built (slice C) |
| E6 Enhancer and binding pruning | operator 2026-11-06; `DM-01M6R3YTNT`, `DM-01M6R4W3ZM`; A8; A7 Q1; OD-6 `01M702A8XY`; squad A5 | Built (PR B); the step enhancer's binding and the unheld target built (PR C); the unheld branch narrowed by C37 (slice C) |
| E7 Withdrawn overrides | research R13; A2, A8, A11 | Built (PR B) |
| E8 The effective set | research R2, R11, R12, R13 | Built (PR B); asked by W26 (PR C) |
| E9 State and reason | ADR-0020 D7; data model; A7; research R11; #439 option (b) | Built (PR B); every overrider by level then URN built (slice C) |
| E10 In force | `DM-01M6R2WPZM` | Built (PR B) |
| E11 The listing | `DM-01M6R2WPZM`, `DM-01M6R0TNEB`; ADR-0059 D5 | Built (PR B) |
| E12 Enhancement, both ways | operator ruling A4; FR-015; `01M70C42KC`; `01M774HQ8N` | Built (PR B) for the list and the report (no pull); built (PR C) for the two-ended read; every pass of the context built (slice C) |
| E13 Floor satisfaction | operator 2026-11-06 (Q6, Q8); `DM-01M6R4W3ZM` (A9) | Built (PR B) |
| E14 Skipped buckets | `DM-01M6R0TNEB`; FR-017; D16; #439 | Built (PR B); activatable kinds only and step targets built (PR C); `overriddenBy` lists built (slice C) |
| E15 Graph projection | `DM-01M6R0TNEB`; FR-018; `01M702A71Y`, `01M7037ZNK` | Built (PR C) |
| E16 Action context | ADR-0018 D1, D2, D3, D5 (amended 2026-11-12); FR-019; A7; `01M7059T4K`, `01M70CTZP7`, `01M7037V93`; `01M774HQ8N` | Built (PR C); the ownership rule, `appliesThrough`, the `prompt` link and the step's place (A7) built (slice C) |
| E17 Validators | ADR-0059 D5 and its amendment of 2026-11-08; FR-020; slice C R10 | Built (PR B); the graph and the context built (PR C); `v3` and `PROMPT` built (slice C) |
| E18 Read refusals | FR-021; OD-5 `01M774HSKK` | Built (PR B); the graph and the context built (PR C); the prompt read and its `409` built (slice C) |
| E19 One verified read | NFR-007; `DM-01M6R2X563` | Built (PR B) |
| E20 Writes walk merged edges | research R2; `DM-01M6R4W1H9` | Built (PR B) |
| E21 Deny entries not applied | B4 | Built (PR B) |
| F1 Fragment content | FR-004; ADR-0066 d3, d7 | Built |
| F2 Enhancement structure | FR-005, FR-006; ADR-0066 d3, d6; D11 | Built |
| F3 Identity | FR-006; D8; operator ruling A2 | Built |
| F4 Target and edges | FR-006; ADR-0064; ADR-0066 d7; R-OVR; R-REL | Built |
| F5 Base prompt slots | FR-007; ADR-0066 d8 | Built |
| F6 Precedence | research P9; slice C | Built; the compose checks' order built (slice C) |
| F7 Same-id enhancements | ADR-0048 d9, d10; ADR-0066 d6 | Built |
| F8 Effective step prompt | ADR-0066 d4, d5, d9; OD-5, OD-7, OD-8; slice C FR-003 to FR-005, R8, R10 | Built (slice C) |

Decision ids are shortened to their first twelve characters in the table; the full ids are in the rule text. "Spec",
"FR", "NFR" and "plan IC" without a mission name are the layer-merge mission's
(`kitty-specs/layer-merge-and-effective-reads-01M6R0J6/`); "activation mission" is
`kitty-specs/activation-register-and-closure-01M6NXBD/`; "slice C" is
`kitty-specs/effective-step-and-action-context-01M774HB/`, whose research R7, R8 and R10 and plan-point squad findings
(A5, D1) the rows cite.

## 8. Step enhancements and prompt fragments

A step enhancement adds Markdown prompt fragments to named, empty slots of the step prompt it enhances
([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)). The service holds the enhancement and its
`*.prompt-fragment.md` companions as parts of its pack, checks them when a pack is loaded, and writes them back with
equal meaning. This section states what is **built** (the step-enhancement service-alignment mission, phase 1,
[#428](https://github.com/spec-kitty/spec-kitty-charter/issues/428) and
[#424](https://github.com/spec-kitty/spec-kitty-charter/issues/424)). Checks made when packs compose, and the read of a
step's effective prompt, are **planned** in the phase-2 mission
([#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438)); no rule below depends on them. The phase-2 mission adds their
sub-headings after this one when it builds them.
*Note 2026-11-12:* the slice C mission builds them (#438): the compose checks are C37, C38 and the inherited lock of
C22 and C25 in section 2, judged when packs compose, and the read is F8. Their sub-headings follow the load rules.

### Load rules

Each rule names the moment it is judged: **V** validate mode, **L** load (both the staged load of a registration and
every composing read), **W** the relationship write (`PUT /packs/{packId}/relationships`), **C** compose (every composing read). A fault found at L is a
violation at its file: a registration answers `422 validation_failed` and lists every fault at once, each as
`{pointer: <pack-relative file>, code}`; a composing read answers `500 pack_unreadable` naming the first faulty file.
The wire shape is the one recorded in the annotations of [ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/#annotation-2026-11-09-how-the-load-codes-of-decision-8-reach-the-wire-od-3-a-od-7).

**F1 Fragment content.** A slot marker of any id, anywhere in the text, code fences included, is refused
`prompt_fragment_invalid` at the fragment (V, L). So is a `<!--` with no `-->` after it, which would swallow the base
prompt once the fragment is rendered into it. A blank body, frontmatter, a carriage return or a byte order mark is
refused `prompt_fragment_invalid` at the fragment in validate mode; at load the store's companion read refuses it
first, at the enhancement that names the fragment, and the reason names the fragment.
*Source:* FR-004; ADR-0066 d3, d7; ADR-0036. **Built.** Moments: V, L.

**F2 Enhancement structure.** An enhancement is refused `prompt_fragment_invalid`, at its file, when it names one slot
twice, names a companion that does not exist beside it, or names a companion another entry already names (in the same
enhancement or another). A fragment under `missions/<type>/enhancements/` that no entry names (an orphan) is refused at
the fragment. Three shapes are schema violations and not `prompt_fragment_invalid`: a `slot` that breaks
`<namespace>:<name>`, `prompt_fragments: []`, and an `id` that differs from its file stem (the store's stem rule;
operator ruling A1, 2026-11-09). A file that breaks its schema gets no further rule.
*Source:* FR-005, FR-006; ADR-0066 d3, d6; D11. **Built.** Moments: V, L. The orphan is judged at V and at a
registration's staged load, not on a composing read, which holds no unmodelled file.

**F3 Identity.** An enhancement id is unique in its pack across all `<type>` directories. The second file in path
order (segment by segment, by code point) is refused `prompt_fragment_invalid` and withheld from the pack; the files of
one id share their edges, so the edge rules (F4) are judged once, at the first file. Packs may repeat an id, each at its
own level, by C14 and layer override (F7).
*Source:* FR-006; D8; operator ruling A2, 2026-11-09. **Built.** Moments: V, L.

**F4 Target and edges.** The enhancement's target is read from the pack's graph documents, never from the file or the
directory. The enhancement must have exactly one `enhances` edge, to an `action:<type>/<step>`. It may be the source of
no `overrides` edge at all: an enhancement only adds (operator ruling R-OVR, 2026-11-09). Its `<type>` directory must
equal the type of that edge. It must carry at least one `prompt_fragments` entry; an absent key is refused here, and
`prompt_fragments: []` by the schema (F2). All are `prompt_fragment_invalid`. When the edge count is not one, the target
and directory rules are skipped for that id. The relationship write refuses, with `422 prompt_fragment_invalid` and
nothing written, an edge that would give a held step enhancement an `overrides` edge or a second `enhances` edge, or
replace its one `enhances` edge (operator ruling R-REL, 2026-11-09).
*Source:* FR-006; ADR-0064; ADR-0066 d3, d7; D7, D9, D12; C-003. **Built.** Moments: V, L, W (edge rules only).

**F5 Base prompt slots.** A slot is a marker pair `<!-- <namespace>:<name>:start -->` and `<!-- ...:end -->` in a step
prompt. A prompt is refused `prompt_slot_malformed`, naming the prompt file, when a marker is unpaired, repeated,
nested, or its id breaks the grammar, whatever the pack descriptor declares. Text between a pair is refused
`prompt_slot_not_empty` when the pack descriptor declares `schema_version: "1.0"`; with no `schema_version` the load
goes on and the registration result lists the `prompt_slot_not_empty` warning in its `diagnostics`, never as a violation.
Only whitespace (space, tab, line feed) counts as empty.
*Source:* FR-007; ADR-0066 d1, d2, d8 and its version gate. **Built.** Moments: V, L; the warning is shown by a
registration only. A composing read discards it.

**F6 Precedence.** Several rules can fail in one pack, and the answer stays deterministic. A schema violation of a file
is reported alone (F2, F4). A codec refusal precedes the slot grammar (F1). A composing read names the first violation in
assembly order: the store's read violations (among them the companion faults of F1 and F2, a blank,
frontmatter-bearing, CR/BOM, missing or shared companion, as `prompt_fragment_invalid` at the enhancement), then each
kind in binding order, the templates, the presets and the pack document, then the slot faults of F5, and last the rule
faults of F1 to F4 (marker, repeated slot, edges, directory, repeated id, no entries) by path, then by rule. A
registration lists them all.
*Source:* research P9; plan IC-02. **Built.**
*Amended 2026-11-12 (slice C):* the compose checks come after the load. A composing read answers, in this order: the
composition codes (C1 to C16, C34 to C36 and C37's `pack_reference_pack_missing` and `pack_reference_artifact_missing`),
then the merge's `pack_unreadable` (C21, C28, E7, the inherited lock of C22 and #447's `overrides` source of C27), then
C38's `prompt_slot_missing`. So a slot is judged only on a closure that composes and merges, and the answer stays one
code whatever else is wrong. **Built (slice C).** *Pinned by:* the scenario `Research R10 - a closure whose
enhancement in force names a slot the prompt in force does not declare is 500 before the 304` of
`effective-step-prompt.feature`, `ClosureQueriesTest` and the C38 tests of `ClosureViewTest`.

**F7 Same-id enhancements.** A step enhancement defines its own URN `step_enhancement:<id>`, so C14 and C15 apply as to
any definer: two packs at one level that define the same id are refused `pack_sibling_conflict` unless a descriptor
`overrides` entry names one winner, and a same-id enhancement at a higher level replaces the lower one by layer
override (ADR-0048 d10). The enhancement is selectable like any other artifact (`step_enhancement` in a selection, and `activated_step_enhancements` in a preset).
*Source:* ADR-0048 d9, d10; ADR-0066 d6. **Built** with the holding of step enhancements. Moment: C.

Not built, planned in the phase-2 mission (#438): refusal when a named slot is missing from the prompt in force
(`prompt_slot_missing`), resolution of the target step across the `depends_on` closure, the integrity check of the
target, and the read of a step's effective prompt. `prompt_slot_missing` stays reserved.
*Note 2026-11-12:* all four are built in slice C, below, and `prompt_slot_missing` is raised: none of ADR-0066's four
codes is reserved any more.

### Compose checks

The compose checks of [ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/) decision 8 are
composition rules, stated in section 2 with their moments and pins: **C37** (the target held), **C22** and **C25** as
amended (the inherited lock, OD-2 (A)) and **C38** (the slot declared, an enhancement in force only, OD-1 (B), OD-4
(i)). G8 says how a registration judges them, and F6 their order on a read. Moment: **C**, and the registration (G8)
and the activation writes.

### The effective step prompt

**F8 Effective step prompt.** `getEffectiveStepPrompt(mt, a)`, under an activation `D` with `mission_type:mt ∈ Eff`
and `held(C, mt, a)`, answers:

- `prompt`: the prompt companion at the place of `win(action:mt/a)` (the place E16 and `StepHolding` give the step,
  research R8), served as it is stored (ADR-0066 d9: unchanged), and `promptPackId`, that place's pack; a step with no
  prompt answers an empty prompt, no `promptPackId` and no slots;
- `slots`: every slot the prompt declares (F5), in declaration order, each with `fragments`: the fragments of the step
  enhancements `u ∈ Eff`, winning copies only (C17), whose sole target is `action:mt/a` and that name the slot, ordered
  by `lv` of the enhancing pack (`B ≺ O ≺ R`), then `name` of that pack, then the enhancement id, each by code point
  (ADR-0066 d5), each with its pack (id, name, level) and enhancement. A slot nothing fills is listed with no fragment.
  A held enhancement that is not in force is absent, and nothing is pulled into force because its target is (ADR-0066
  d4, E12);
- `diagnostics`: `prompt_slot_not_empty` for a slot that still holds legacy text under an unversioned descriptor; the
  slot is listed with its fragments and no flag (OD-7, OD-8);
- `_links`: `self`, `effective-context` (the action context of the same action) and `effective-artifact` (the step
  under the activation, read at the step's place by E16's one place rule, A7, so it answers whenever the step has a
  place; the prompt itself is still placed at `win(action:mt/a)`, pending operator ruling Q3).

Its refusals and their order are E18's (the `409 mission_type_not_in_force` among them), its tag is E17's read
`PROMPT`, and it is one verified read (E19). The answer is byte-identical across repeated reads and every order in
which the closure's packs are configured, and its work is linear in the enhancements of the closure (slice C NFR-001,
NFR-002).
*Source:* ADR-0066 d4, d5, d9 and its annotations of 2026-11-12; OD-5, OD-7, OD-8; slice C spec FR-003 to FR-005,
FR-007; research R5, R8, R10. **Built (slice C).** *Pinned by:* `EffectivePromptQueryTest`
(`fragmentsAreOrderedByLevelThenPackNameThenEnhancementId`, `enhancementIdsOfOnePackAreComparedByCodePoint`,
`anEnhancementHeldButNotInForceIsAbsent`, `anEnhancementOfAnotherStepOrWithTwoTargetsIsAbsent`,
`aSlotNoInForceEnhancementFillsIsListedEmpty`, `aStepWithNoSlotsAnswersItsPromptAndNoSlot`,
`aStepWithNoPromptCompanionAnswersAnEmptyPromptAndNoPack`, `thePromptIsServedAsStored`,
`aSlotHoldingLegacyTextIsListedWithItsFragmentsAndADiagnostic`, `onlyTheWinningCopyOfAnEnhancementIsServed`,
`thePromptIsReadAtTheWinnersPlaceOfTheStep`, `everyEdgeTheQueryReadsIsCountedOncePerPack`,
`theCountGrowsLinearlyWithTheEnhancementsOfOneStep`, `theCountGrowsLinearlyWithTheEnhancementsSpreadOverSteps`),
`ClosureViewGovernanceTest` (`theStepPromptIsReadAgainstTheDigestOfTheClosureTheViewWasVerifiedAt`,
`theTwinTheStepPromptOfAClosureThatDidNotMoveIsServedAsStored`), `EffectivePromptTest`, `ClosureViewSessionArtifactTest`
(`theEffectivePromptIsMappedAsTextWithItsPacksSlotsFragmentsAndDiagnostic`, `aDiagnosticThePromptQueryNeverListsIsAFault`),
`EffectiveReadMapperTest` (`thePromptIsMappedWithItsSlotsInOrderItsDiagnosticsAndItsThreeLinks`,
`aStepWithNoPromptLeavesThePromptPackOutAndNamesNoStepWhileMissionTypesAreNotServed`), `ClosureViewTest`
(`theEffectivePromptOfSoftwareDevPlanServesBothExampleFragmentsBuiltInFirst`,
`theTwinWithOnlyTheMissionTypeInForceTheSlotIsListedWithNoFragment`), `EffectivePromptDeterminismTest` and the scenarios
of `effective-step-prompt.feature` (`SC-001 - on charter's example packs, plan's slot holds plan-risk-notes of built-in,
then spdd-plan of org-base`; `SC-001, squad R1 - the fragments of a slot are ordered by level, then pack name, then
enhancement id, and in no other order`; `FR-003 - an enhancement held and not in force is absent, a slot nothing fills
is listed empty, and a prompt with no slot lists none`; `FR-005, C-005 - a slot that still holds legacy text is listed
with its fragments and a prompt_slot_not_empty diagnostic`; and the refusal scenarios named under E18).

## Sources

- ADRs: [ADR-0012](/adr/0012-pack-mutability-policy/), [ADR-0013](/adr/0013-persistent-deactivation/),
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/), [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0026](/adr/0026-write-surface-is-functional-registration/),
  [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/),
  [ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/),
  [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/),
  [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/),
  [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/),
  [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/),
  [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) with its
  [amendment of 2026-11-06](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/#amendment-2026-11-06) (A2 to
  A5) and its [amendment R6 of 2026-11-09](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/#amendment-2026-11-09-r6)
  (the write rule of W26 and the read rules of research R11 for D2 and D3), [ADR-0061](/adr/0061-one-replica-per-deployment/),
  [ADR-0063](/adr/0063-pack-mutability-policy-file/).
- Design: [Logical model](/architecture/logical-model/), [Resolution model](/architecture/resolution-model/),
  [Offering and activation](/architecture/offering-and-activation/).
- Divergences: [Divergence register](/conformance/divergence-register/) (DIV-201 to DIV-245).
- Missions: `kitty-specs/activation-register-and-closure-01M6NXBD/` (spec, research) and
  `kitty-specs/layer-merge-and-effective-reads-01M6R0J6/` (spec, the resolved decisions under `decisions/`, plan,
  research, data model), and `kitty-specs/effective-step-and-action-context-01M774HB/` (slice C: spec, research
  R1 to R10, the plan-point squad's findings and the Decision Moments `01M774HQ8N5EVYDRTP16MBJCN3` and
  `01M774HSKK0SSA4147QEG9ZCKR`).
- Code, `service/packs`: `PackCompositionResolver`, `ComposeResolver`, `ClosureQueries`, `ClosureView`,
  `RequiredFloor`, `PerPackPolicy`, `Reachability`, `ReachRequest`, `PackMutabilityPolicy`, `MutabilityLevel`.
  `service/activations`: `DocumentRules`, `InForceFacts`, `CascadeRules`, `StaleIds`, `PresetRules`, `ActivationWrites`,
  `ActivationExclusionService`, `EffectiveSet`, `EffectiveContext`. PR C adds, in `service/packs`: `StepHolding`, `GraphProjection`,
  `ActionContextWalk`, `InForceResolution`'s step enhancer binding. Slice C adds, in `service/packs`: `EnhancementCompose`,
  `SlotCompose`, `OverrideSourceRule`, `EffectivePromptQuery`, the effective form of `ActionContextWalk` and
  `EdgeIndex`'s scope owners; in `service/activations`: `EffectivePrompt` and `ActivationImpactAdapter`'s gate.
- Operator rulings of 2026-11-06 not yet in an ADR: registration is a full replacement (planned ADR-0065), and the answers to the ruleset consistency review
  (`research/ruleset-review-2026-11-06.md` of the layer-merge mission; decisions `DM-01M6RA7FHR…` to `DM-01M6RAA6Y7…`).
- Code read for the fidelity corrections: `PackCompositionResolver` (`refuseContests`, `refusePresetContests`,
  `refuseCaseOnlyMatches`, `dependenciesOf`, `dependsOnClosure`), `ClosureQueries.winningPresets`, `RequiredFloor.of`,
  `ArtifactSpellings`, `DocumentRules.normalize`, `ActivationWrites.declared`, at #399 `1d37ccd9`.
