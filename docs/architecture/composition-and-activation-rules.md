---
title: Composition and activation rules
description: Every composition, registration, activation-write and effective-read rule as sets, relations, closures and invariants, each with its source and build status.
doc_status: draft
updated: 2026-11-08T00:00:00Z
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
to its source and status in one table.

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
> the layer merge and two effective reads, the artifact list and the skipped report.
>
> **Rulings of 2026-11-06.** The operator's answers to the ruleset consistency review (layer-merge mission,
> `research/ruleset-review-2026-11-06.md`, items A1–A15 and B1–B8) are folded into the rules and cite their decision ids (`DM-01M6RA…`, under the layer-merge mission's `decisions/`).

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

- `K`: the artifact kinds. `Kₐ = K ∖ {template, asset}`: the activatable kinds, each with a `selected` and a
  `deactivated` list; `mission_type` is one of them like any other (A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; one kind registry, decision
  `DM-01M6RCY40CQQTHC130RWF43W0D`).
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
  that declares a mission-type binding is bound to that mission type. `steps(M) = {u ∈ H(C) | bnd(u) = M, kind(u) =
  action}`.
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
ties broken by `lv` in merge order, then by `cid` in code points.
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
`PackCompositionResolver.refuseContests`. **Built (PR B):** settlement by exactly one winner clears the refusal, and the merge puts `m`'s version in force (#357, DIV-218). A step contest no entry settles passes the compose and is refused at read as `pack_sibling_conflict`. Until #358 any pack of `C` may settle, with no level check (DIV-219).
> ⚠ **Unclear:** ADR-0053 d5 says the conflict is settled by "the distribution pack's own `overrides` edge". The
> built resolver and the layer-merge spec use a descriptor `overrides` entry `{artifact, member}` in `pack.yaml`, from
> any pack of the compose. The two are not the same mechanism.

**C16 Artifact identity per level.** `∀ℓ: |{p ∈ C | lv(p) = ℓ ∧ defines(p, u)}| ≤ 1` after C15.
An artifact id is unique within a level; across levels the same id is a layer override, not a conflict.
*Source:* ADR-0053 d6 (amended 2026-11-03). **Built** (as the refusal of C14).

**C17 Layer winner.** `win(u) = the place (p, u)` whose `lv(p)` is the level that applies among
`{lv(q) | holds(q, u), q ∈ C}`, after C15.
Levels merge built-in, then org, then project; the level that applies wins.
*Source:* ADR-0053 d6; ADR-0059 D3; spec FR-009. **Built (PR B).**

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
A target's level is the strictest any pack of the closure declares, whatever the order. A pack's declaration counts
only for what it or a pack it builds on holds (notation, A15): a pack may lock the artifacts of the packs it builds on
(an enterprise pack enforcing a built-in practice), not those of an unrelated pack that shares the closure.
*Source:* ADR-0063 D3; ADR-0012 d3; `PerPackPolicy.levelIn`; A15, decision `DM-01M6RA97220XBR7RR447FE2JXD`.
**Built (PR A0):** the strictest level, and the scope limit: a `kind_defaults` entry locks only the artifacts of the pack and of the packs it builds on.

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

**C28 Override cycle.** A cycle of `ov` that is reachable from `Base ∪ Req(C)` (E3, C31) is refused: at registration by the
impact check (G8), and at read `500 pack_unreadable` should one remain.
The walk starts at each member of `Base ∪ Req(C)` and follows `ov` edges, which end only at `open` artifacts. A cycle
that no member reaches is not judged. No cycle is broken by an arbitrary pick, and the requirement fixed point (E5)
always ends.
*Source:* spec Edge Cases, FR-016; research R2; A2, decision `DM-01M6RA7M56M1409EV9JX1EW83K`; decision
`DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`. **Built:** the registration refusal (PR A) and the read (PR B, DIV-222).

**C29 Winning preset.** For a preset id `i` held by several packs of `C`, the copy held by the last of them in closure
order (merge order) wins as the preset applied (W17); the other copies' members are ignored, but their must-have
items still count (C31). Two copies at one level are refused (C34).
*Source:* ADR-0053 d6 (applied to presets); activation mission Assumptions; `ClosureQueries.winningPresets`. **Built.**

**C30 Governing preset.** `gov(π) ⇔ must(π) ≠ ∅`.
A preset with at least one must-have item governs; nothing else declares it.
*Source:* ADR-0031 d1–d2; ADR-0042 §2. **Built.**

**C31 Required set.** `Req₀(C) = ⋃ {must(π) | π ∈ Pre(p), p ∈ C, gov(π)}` over **every** copy of each preset id;
`Req(C) = Req₀(C) ∩ H(C)`; `Unheld(C) = Req₀(C) ∖ Req(C)` is never put in force; reported in `requiredNotHeld` (PR B). Never stored.
The requirement is the union of the must-have items of every governing preset of the closure, so a later copy of a
preset can only tighten it; a must-have item no pack holds is dropped from the floor and reported.
*Source:* ADR-0031 d2; DIV-205; decision `DM-01M6R0TK6F4CC0Z00CZTS21V72` (ratifies DIV-205); `ClosureQueries`
(`RequiredFloor.of(byHolder(closurePackIds, winningPresets(composed)))`), `RequiredFloor.of`; A1, decision
`DM-01M6RA7FHRC5K8QPX7KN5FZ4S8`; A10, decision `DM-01M6RA8MG55V8C2V9PXWDTRNQS`. **Built (PR B)** for the reads: the floor is the union over every governing copy (A1), read from the merged closure and
restricted to held ids (A10; PR A0), and `requiredNotHeld` reports the rest. The writes' floor (`ClosureView.floor()`)
still reads the winning copies only (DIV-225). DIV-205 is ratified.

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
ruling Q8 ("enhancers do not stand on their own"). **Ruled, not built** (layer-merge PR B).

## 3. Registration rules

Registration writes pack content. The per-artifact registrations are built (ADR-0026); the whole-pack registration
and `DELETE` are ruled on 2026-11-06 and planned as ADR-0065.

**G1 Writable levels.** A registration or deletion of `p` requires `lv(p) ∈ {O, R}`; `lv(p) = B` is
`409 registration_conflict`.
The built-in level is read-only.
*Source:* decision `DM-01M6R1GQYFZ6Q6DFSWNDVTE9X1`; research R1. **Ruled, not built.**

**G2 Full replacement.** After `PUT /packs/{cid}` with archive `Z` succeeds: `stored(cid) = Z`, so every artifact,
edge, preset and file absent from `Z` is absent from the pack.
Registering a whole pack replaces what was known of it, removals included.
*Source:* operator ruling of 2026-11-06; decision `DM-01M6R0TA37CQ0VEMHWSTJF2TRM`; spec FR-001, SC-003.
**Ruled, not built.**

**G3 Version rule.** Let `s` be the stored pack and `z` the staged one: `rev(z) = rev(s) ⇒ 200`, nothing written;
`ver(z) = ver(s) ∧ rev(z) ≠ rev(s) ⇒ 409 pack_version_conflict`; and, with `hist(cid)` the version → content-digest
history of every version ever stored under `cid`, `hist(cid)(ver(z)) ∉ {⊥, rev(z)} ⇒ 409 pack_version_conflict`;
otherwise `z` replaces `s` and `hist(cid)(ver(z)) := rev(z)`.
Any version may replace; one version always means one content, also after it was replaced.
*Source:* decision `DM-01M6R0TCAGHF27RKCB20QSCN8W`; spec FR-002; research R4; B1, decision
`DM-01M6RA9BKJY7X9BFV93QW1KM1T`. **Ruled, not built** (layer-merge PR B).

**G4 Fixed identity.** On a replace: `pid(z) = pid(s) ∧ lv(z) = lv(s)`, else `422`. On a create: `pid(z)` and
`name(z)` are unused in `P`, else `409 registration_conflict`.
A pack's identity and level are fixed for its configured id.
*Source:* spec Edge Cases, FR-003; decision `DM-01M6R2X0H0VXGZZGSQC9C2SCSF`; research R1. **Ruled, not built:** the level
`lv(z) ∈ {O, R}` and `lv(z) = lv(s)` on a replace (the decision). **Planned, not built:** the fixed `pid` and the
create-time uniqueness.

**G5 Archive validity.** An archive with an unknown file, a path leaving the pack, a link or device, an invalid
artifact, more than 32 MiB or more than 10,000 entries is refused (`413` or `422`) before any file is written.
*Source:* spec FR-003, NFR-004, NFR-005; decisions `DM-01M6R1GKE8TAG2QBJT5PAHJQ8E`, `DM-01M6R2WY82DF6DV2FEAK9CK4S2`.
**Ruled, not built.**

**G6 Preconditions.** `If-Match ≠ rev(s) ⇒ 412`; `If-None-Match: * ∧ s exists ⇒ 412`; both headers `⇒ 400`. G3's
no-op is judged before them. Both `DELETE`s require `If-Match` (`428` when absent).
An identical re-send is a no-op even under a stale validator; a removal is always conditional.
*Source:* spec FR-003; research R-lock (API guarantees). **Planned, not built.**

**G7 Write gate on the diff.** Every artifact the replace adds, changes or removes passes C25 and C26 along
`C({z}) ∖ {z}`, else `409 governance_policy_violation`.
The packs the new pack builds on judge the diff; its own policy, old or new, does not (B8).
*Source:* ADR-0063 D3; ADR-0030 d3; plan IC-04 step 7; B8, decision `DM-01M6RAA6Y7NVVGKVKQV05GZYES`. **Planned, not
built.**

**G8 Impact check.** Let `P' = P ∖ {s} ∪ {z}` and `Aff(z) = {C'(S) | S ∈ Roots, z ∈ C'(S)}`, where `Roots` is `P'`
and the roots of every stored activation, and `C'` composes over `P'`. Every closure in `Aff(z)` must pass C1–C16,
C20, C21, C27, C28 and C34–C36, and its floor must hold (E13 under I10), else `422` with the composition code, naming
the packs of the failing closure. A closure that does not contain `z` is not judged.
A registration never leaves a closure it joins unable to compose or merge, and never blocks an author over a closure it
does not join.
*Source:* plan IC-04 step 8; A5, decision `DM-01M6RA8225EXG6GAT1MM2R9T6C`. **Ruled, not built** (layer-merge PR B;
the scope replaces the plan's one global compose).

**G9 Reference check.** For each artifact `u` a `DELETE` or replace removes from `p`, judged against the configured
packs as the request would leave them (`P'`, with `p`'s new tree): `Inbound(p, u) = {(q, a) | q ∈ P', a held by q in
P', a references u, holderOf_q(reference) = p}`, plus every descriptor `overrides` entry naming `p` as member for `u`,
plus every preset item of a pack `q ≠ p` naming `u`; `Inbound(p, u) ≠ ∅ ⇒ 409 artifact_referenced`, listing every
referring artifact or preset with its pack.
A referenced artifact cannot be removed, whether the reference comes from the same pack or from another one; what the
same request removes refers to nothing, so two artifacts that reference each other can be removed together.
*Source:* operator ruling of 2026-11-06; decisions `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`, `DM-01M6R2WSARGC382BHX89GZB64E`,
`DM-01M6R2WVRKB4JGZM40D6QF60BQ`; research R6; A10, decision `DM-01M6RA8MG55V8C2V9PXWDTRNQS`; B3, decision
`DM-01M6RA9MQ6YAP048KD43DNFS4F`. **Ruled, not built.**

**G10 Artifact `DELETE`.** `holds(p, u)` else `404`; then G9, then C25/C26 (`locked` is `409
governance_policy_violation`); then remove `u`'s file and companions, every edge whose source is `u`, its graph node,
and every preset item of `p` naming `u`.
`DELETE` removes one artifact with what only it declared; a preset key it empties reads as not listed.
*Source:* operator ruling of 2026-11-06; decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`; spec FR-005; plan IC-06.
**Ruled, not built.**
A preset item of the same pack is dropped (this rule); a preset item of another pack is an inbound reference (G9, A10).

**G11 Relationship `DELETE`.** Removes the one edge of the unordered pair `{from, to}` from `p`'s shard or graph
documents; `404 relationship_not_found` when `p` holds none; an edge that is a field of an artifact's body is
`409 registration_conflict`.
*Source:* decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`; ADR-0026 D1.3; spec FR-006; plan IC-06. **Ruled, not built.**

**G12 Registration by source.** `POST /packs` fetches an allowlisted HTTPS archive, a git repository and ref (system
`git`), or a path under a configured root, then applies G1–G9 and G13.
*Source:* decisions `DM-01M6R0TA37CQ0VEMHWSTJF2TRM`, `DM-01M6R1GNR6Q0YN39568F9DPEVF`, `DM-01M6R1T0HK0F078S0VPA6YSTXW`;
spec FR-004. **Ruled, not built.**

**G13 Atomicity.** A registration or `DELETE` either commits the whole new tree by one directory swap, or changes no
stored file; a concurrent read sees the old pack or the new one.
*Source:* spec FR-007, NFR-006; research R7. **Partly built:** the per-artifact registrations commit by copy and swap
(`PackCommitStore`); the whole-pack swap is ruled, not built.

**G14 Lock order.** Every pack write holds `RegistryWriteLock` and then `PackWriteLocks(p)`, from the precondition to
the swap; readers take neither; downloads, extraction and staging run before the locks.
One fair registry lock closes the cross-pack check-then-act races; the pack lock serializes one pack.
*Source:* research R-lock; data model (Write coordination). **Partly built:** `PackWriteLocks` is built; the registry
lock is planned, not built.

**G15 Verified read.** A read records the write generation `g` and the source fingerprint before and after it reads;
`g` odd, `g` changed, or the fingerprint changed `⇒ stale_input`. A closure read retries a `stale_input` up to 3 times.
A read never answers from a mixed state of the packs.
*Source:* decision `DM-01M6R2X563762EDCVBR02W4FK7` (operator, Q7); research R-lock; ADR-0018 D11. **Partly built:**
the fingerprint check (`ConsistentRead`) is built; the generation and the retry are ruled, not built.

**G16 One writer.** A deployment runs one replica; every lock of G14 and the activation store's per-id lock are
in-process only.
*Source:* ADR-0061 d1–d3. **Built** (documented, not detected).

**G17 Unconditional per-artifact registration.** A per-artifact registration `PUT` without `If-Match` is
last-writer-wins on the whole pack.
*Source:* ADR-0026 D5; research R-lock. **Built.**

**G18 Activations unaffected.** No registration or `DELETE` reads or changes an activation document.
A removed artifact a document still selects becomes a stale id (W9, E1).
*Source:* research R-lock (The activations side); plan IC-06. **Ruled, not built** (with G2 and G10).

**G19 Asynchronous registration.** `PUT /packs/{cid}` and `POST /packs` answer `202 Accepted` with a status URI; the
status resource reports `pending`, then `registered` (with the G20 report) or `refused` (with the refusal G1–G13 would
have answered). Preconditions (G6) and the body bound (G5) may still be answered synchronously.
A client learns that a registration completed from the status URI, not from the request.
*Source:* A5, decision `DM-01M6RA8225EXG6GAT1MM2R9T6C`. **Ruled, not built** (layer-merge PR B; the contract delta and
G6's status codes are to be revised to it).

**G20 Impact report.** A completed registration reports, for every closure in `Aff(z)` (G8): the artifacts that joined
or left `Req` or `Lk`, the `Unheld` items (C31), and the stored activations whose effective set changes, by id.
A pack upgrade that moves the floor is never silent.
*Source:* B4, decision `DM-01M6RA9SAKCBSKGX165JM3CAGS`. **Ruled, not built** (layer-merge PR B).

**G21 Repair writes.** A registration into `p` whose closure `C({p})` does not compose today is let through when the
closure composes after it (G8 holds for the new state); one that leaves it broken is refused as before.
A write that fixes a broken compose is not blocked by the breakage it fixes.
*Source:* ADR-0063 amendment of 2026-11-06 (repair writes); issue #404. **Ruled, not built:** today any write into a
closure that cannot compose is refused.

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
`GET` → `PUT` of the stored document is never refused for the floor or a stale id. Locked is judged first.
*Source:* ADR-0012 d7; ADR-0013 d5; ADR-0020 D7, D9, D10; DIV-204; `DocumentRules.floorRefusal`; A4, decision
`DM-01M6RA7XC5HC8915GZT2R3H83W`. **Built (PR A0):** the whole-document refusal, judging only the added entries and the replace's stale drop. Relaxed by W26.

### Preconditions and order

**W7 Precondition order (per-artifact and preset writes).** `400` (malformed `If-Match`) → `428` (no `If-Match`) →
`404 activation_not_found` → `412` (stale `If-Match`) → `422` (body, path kind, `cascade`) → one snapshot (W9) →
path id (W10: `422 ambiguous_artifact`, then `404 artifact_not_found`, `mission_type_not_found` or `preset_not_found`) →
W11 → the change → `422` or `409` (W5, W25) → compare-and-set (W30).
Preconditions are judged before content, and no pack is read before them.
*Source:* ADR-0020 D6, D9; RFC 9110 §13.2.2; activation mission FR-010, finding F6; `ActivationWrites`. **Built.**

**W8 Create and replace order.** `400` (both validators, or `If-None-Match` other than `*`) → `412` (`If-None-Match: *` on
a stored id, or a stale `If-Match`) → `422` → `409`; a refused precondition takes no snapshot; without `If-Match` a
replace is last-writer-wins; `DELETE` answers `204`, then `404` on a second call.
*Source:* ADR-0020 D3, D6, D9; activation mission FR-001 to FR-004. **Built.**

**W9 One snapshot.** Every write takes exactly one verified snapshot of `C`; every walk and every rule read it.
One request never mixes two states of the packs.
*Source:* activation mission NFR-007; `ClosureQueries.snapshot`. **Built.**

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
stored deny entry on the floor is tolerated and kept.
*Source:* ADR-0059 D4; DIV-204; activation mission finding F1; `DocumentRules.checkWrite`,
`ActivationExclusionService`. **Built.** Relaxed by W26.
> ⚠ **Unclear:** ADR-0012 d1's table forbids deactivating an `extendable` artifact, and `MutabilityLevel`'s javadoc
> repeats it, but the activation writes refuse only `locked` (and required) targets. ADR-0030 and the logical model
> treat deactivation as a project's choice, not a mutability operation.

**W26 Relaxed required rule.** A new deny entry on `T ∈ Req(C) ∖ Lk(C)` is accepted iff
`∃E ∈ Eff(D', M): overrides(E, T)` (E4–E8, over the document as stored after the write); `T ∈ Lk(C)` always refuses.
A required artifact may be denied while an active override of it is in force, unless it is locked.
*Source:* decision `DM-01M6R3YX23H46PXGNEFC31E6CT` (operator, Q9); research R12; plan IC-13. **Ruled, not built.**
> ⚠ **Unclear:** ADR-0013 d2 and ADR-0020 D7 say a required artifact "is never subtracted" and is "active whatever
> the document says". The 2026-11-06 rulings let an active override hide a required artifact and let it be denied.
> Research R12 plans an ADR-0059 amendment (A6, after the relabelled A2–A5) to record it; until then the ADR texts and
> the ruling disagree.

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
leaves out, with the E9 reason that decides it (for example `enhanced_target_not_in_force`,
`mission_type_not_in_force`).
A write that will not bring what it selected into force says so; it is still applied.
*Source:* B6 and B7, decision `DM-01M6RAA2B2G9PNXN2F9SJ7B1CR`. **Ruled, not built** (needs `Eff`; layer-merge PR C,
with W26).

**W34 Withdrawn keys refused.** A create, replace or preset body carrying `activatedKinds` or `missionTypes` is
`422 validation_failed`, one violation per member (`removed_member`), with no deprecation window; core regenerates its DTOs first. A stored version-1 document is
upgraded on read to `schemaVersion: 2`: its `missionTypes` move to `selected.mission_type`, its kind gate is dropped
and every selection is kept (decision `DM-01M6RMJHDKNTXAM055Q8WR6SDN`; the gate was never enforced), and nothing observable changes (locks and the floor were never gated).
Each stored document's `ETag` changes once.
*Source:* A7 Q4, decision `DM-01M6RCY8MYNGNPJ247M2Y4880N`; research `a7-kind-model-2026-11-06.md` (proposed revision, items 2–3). **Built (PR A0).**

## 5. Read rules

Two of the four effective reads, `listEffectiveArtifacts` and `getActivationSkippedReport`, answer since PR B
(2026-11-08); `getEffectiveGraph` and `getEffectiveActionContext` answer `501` until PR C. Every rule in this section
is **Built (PR B)** or **Ruled, not built** as it says. `M` is the merged closure of `C(D)` (C17–C21, C15), `W` its winning URNs of a kind
in `Kₐ`, `Lk = Lk(C)` and `Req = Req(C)`.

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

**E6 Enhancer and binding pruning.** `Eff₀ = lfp X. {u ∈ A₂ | (¬∃T. enhances(u, T) ∨ ∃T ∈ X. enhances(u, T)) ∧
(bnd(u) = ⊥ ∨ bnd(u) ∈ X)}`.
An enhancer is in force only when it is grounded: one of its targets is in force, and that target is an artifact
that enhances nothing or is itself grounded. An artifact bound to mission type `M`, a step of `M` included, is in
force only while `M` is. A required or locked artifact pruned by either condition is not in force either; it is
reported required-but-ungrounded (E14). An enhancer is never in force on its own, and nothing bound to a mission type
outlives it.
*Source:* operator ruling of 2026-11-06 ("an enhancer needs its target T active to work"); decision
`DM-01M6R3YTNT1P2MBERBN7WV71ND`; research R11 step 4; data model rule 8; A8, decision `DM-01M6RA8G0RGRWSFW4DERH8W319`
(least fixed point); decision `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` (the enhancer rule wins over the floor); A7 Q1, decision
`DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S` (the binding; it replaces the former `stepEnh` exemption). **Built (PR B)**
(DIV-224); only step contracts are bound, and the binding of a step enhancer is PR C. C36 refuses `enhances` cycles at registration, so the least and greatest fixed points agree on every
registered closure.

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
reads share. W26, the relaxed write rule, is not built.
*Source:* research R2, R11, R12, R13; plan IC-09, IC-13. **Built (PR B)** for the reads.

**E9 State and reason.** For `u ∈ W`, `(state, reason)` is the first row that holds: in force → `active, locked`,
`active, required` (a required artifact, whether selected or put in force by E5) or `active, selected`; otherwise bound
to a mission type not in `Eff` → `inactive, mission_type_not_in_force`; enhancer without a grounded target in force →
`inactive, enhanced_target_not_in_force` (E6; these two rows come before the language, deny and selection rows, so a
required or locked artifact they prune is out of force); language → `inactive, language_filtered`; denied →
`excluded, deactivated`; selected, and hidden by E4 or E5 → `inactive, overridden`; otherwise `inactive, not_selected`.
An artifact that is not selected is `not_selected` even when an override hides it, so `not_selected` precedes
`overridden` (research R11 step 3). A required artifact out of force that an override satisfies, directly or through a
chain, keeps its row and gains `satisfiedBy` (the overrider in force): a required, unselected `T` that `F` overrides is
`not_selected` with `satisfiedBy` `F`, and a selected one is `overridden` with `satisfiedBy` `F`.
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

**E12 Enhancement, both ways.** For `T ∈ Eff`: `enhancedBy(T) = {E ∈ Eff | enhances(E, T)}`; `E ∉ Eff ⇒ E` is never
returned for `T`, and no read adds `E` or `T` to `Eff`.
An enhancement is returned only when it is itself active, and is read from either end; nothing is pulled in.
*Source:* operator ruling A4 (ADR-0059 amendment of 2026-11-06); spec FR-015, US3 AS5; DIV-207 note.
**Partly built (PR B):** the list and the report pull nothing in and return an enhancer only in force (DIV-224); the
reading from either end belongs to the effective graph and action context, **Ruled, not built** (PR C).

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
applied; `requiredNotInForce` lists the required or locked artifacts E6 pruned; `requiredNotHeld` lists `Unheld` (C31);
`pinNotHeld` lists the pins E1 reports; `skippedByScope`, `unreachedBehindDeactivated` and `skippedShared` are empty at
read.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; ADR-0059 D4 and follow-ups; spec FR-017, US4 AS1; data model.
**Built (PR B)** (DIV-228). The earlier design names `requiredUngrounded`, `unheldRequired` and `deadPins` are the
contract's `requiredNotInForce`, `requiredNotHeld` and `pinNotHeld`.

**E15 Graph projection.** `nodes = Eff`; `edges = {(x, r, y) ∈ E_M | x, y ∈ Eff}`; `layerOverrides = {(u, winner,
loser) | u ∈ Eff}`, one record per losing version.
The graph holds only the edges among artifacts in force, after C19–C20, and lists the layer overrides applied.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; spec FR-018, US5 AS1–AS3; research R3. **Ruled, not built.**

**E16 Action context.** `getEffectiveActionContext(mt, a)` is ADR-0018's five-step walk from `action:mt/a` over
`E_M` restricted to `Eff`, each listed artifact with `enhancedBy` (E12); `mission_type:mt ∉ Eff ⇒ artifacts = []` and
`missionTypeNotInForce = {mt}`; an unknown `mt` or `a` is `404 mission_type_not_found` or `action_not_found`.
Whether a mission type is in force is read from the effective set, the one predicate E6 and E9 use; the walk stays on
the packs side.
*Source:* ADR-0018 D1, D2, D5; spec FR-019, US5 AS4–AS5; research open review item 4; A7, decision `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`. **Planned,
not built.**
> ⚠ **Unclear:** FR-019 says the context is what "the step contract names"; the plan reads it as the ADR-0018 walk.
> The data model allows `depth` 0 or 1, while ADR-0018 D4 allows 0 to 2.

**E17 Validators.** `ETag = sha256("charter-effective-read\0v1\n", read, rev(D), closureFingerprint(C), query)`; a weak match
on `If-None-Match` answers `304` with no body.
An effective answer changes with the document or the packs of its closure, and nothing else. `query` is the normalized
`kind`, `state`, cursor and page size, so each representation has its own tag. `getActivation`'s tag has the same
recipe with no query; an activation write's `If-Match` takes `*`, `rev(D)` or that tag, and a closure tag that no
longer matches is `412`.
*Source:* ADR-0059 D5 and its amendment of 2026-11-08; research R5; decisions `01M6Y11E55YP5TG1EJ1MDKCFRP`,
`01M6Y3GZFRC5WEQQ7G6GT0YMW3`; spec FR-020. **Built (PR B)** (DIV-231, DIV-233, DIV-234).

**E18 Read refusals.** `404 activation_not_found` for an unknown activation; `500` with the composition code, or
`pack_unreadable` for C21, C28 or E7, when `C` cannot compose or merge.
*Source:* spec FR-021. **Built (PR B).** `getActivation` answers the same `500`s.

**E19 One verified read.** Each effective read composes and merges `C` once, from one verified read (G15), retried up
to 3 times on `stale_input`.
*Source:* spec NFR-007; decision `DM-01M6R2X563762EDCVBR02W4FK7`. **Built (PR B).**

**E20 Writes walk merged edges.** The write cascade (W20) walks `E_M`, the edges of the merged closure after C17–C20,
so a cascade never follows an edge a losing version declared; W26 and W33 read `Eff` over `M`.
*Source:* research R2 (open point), open review item 3; plan IC-07, IC-13; decision `DM-01M6R4W1H9P3451V89KHP97XKM`
(merged edges). **Built (PR B)** (DIV-230): the cascade walks the merged edges, builds the merge, and so a write over a closure that
does not merge answers `500 pack_unreadable`.
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

**I9 Locked is in force.** `Lk(C) ∖ Ung ⊆ Eff(D, M)` for every `D`, where `Ung` is the floor enhancers with no
grounded target in force (E6); no override hides a non-`open` artifact. (E3, E4, E6, C27)

**I10 Requirement holds.** Every `T ∈ Req(C)` is in force or overridden, directly or through a chain, by an artifact
in force, unless it is an enhancer without a grounded target in force, which is reported required-but-ungrounded.
(E5, E6, E13)

**I11 No enhancer stands alone; nothing outlives its mission type.** `E ∈ Eff ∧ ∃T. enhances(E, T) ⇒ ∃T ∈ Eff.
enhances(E, T)`, and every `enhances` chain from `E` inside `Eff` ends at an artifact that enhances nothing;
`u ∈ Eff ∧ bnd(u) ≠ ⊥ ⇒ bnd(u) ∈ Eff`. Floor membership is no exception to either (decision `DM-01M6RFGFW3S0YJ222QMHD2W11B`). (E6, C36)

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
| C22 Mutability along the closure | ADR-0063 D3; A15 | Built (PR A0) |
| C23 Tighten, never loosen | ADR-0012 d3 | Built |
| C24 Unreadable policy | ADR-0063 D4 | Built |
| C25 Registration gate | ADR-0012 d1, d9; ADR-0026 D4; B8 | Built (PR A0) |
| C26 Removal by a derived pack | ADR-0030 d1 to d3 | Ruled, not built |
| C27 CRG overrides at read | `DM-01M6R0TGX3`, `DM-01M6R2X2SW`; R11; A6 | Built (registration PR A, read PR B) |
| C28 Override cycle | spec FR-016; A2 | Built (registration PR A, read PR B) |
| C29 Winning preset | ADR-0053 d6 | Built |
| C30 Governing preset | ADR-0031; ADR-0042 §2 | Built |
| C31 Required set | ADR-0031; DIV-205; `DM-01M6R0TK6F`; A1, A10 | Built (PR B) for the reads; held-only built (PR A0); the writes' floor reads the winning copies only |
| C32 Floor at write | ADR-0012 d7 | Built |
| C33 Offering stays a catalog | ADR-0020 D1; FR-023 | Built |
| C34 Same-level preset conflict | operator 2026-11-06; `refusePresetContests` | Built |
| C35 Case-only mention | operator 2026-11-06; `refuseCaseOnlyMatches` | Built |
| C36 Edge shape | A8, A11, A12 | Ruled, planned (PR B) |
| G1 Writable levels | `DM-01M6R1GQYF` | Ruled, not built |
| G2 Full replacement | operator 2026-11-06; `DM-01M6R0TA37` | Ruled, not built |
| G3 Version rule | `DM-01M6R0TCAG`; B1 | Ruled, planned (PR B) |
| G4 Fixed identity | spec FR-003; `DM-01M6R2X0H0` | Level ruled, not built; `pid` and uniqueness planned, not built |
| G5 Archive validity | FR-003, NFR-004, NFR-005 | Ruled, not built |
| G6 Preconditions | FR-003; research R-lock | Planned, not built |
| G7 Write gate on the diff | ADR-0063 D3; plan IC-04; B8 | Planned, not built |
| G8 Impact check | plan IC-04; A5 | Ruled, planned (PR B) |
| G9 Reference check | operator 2026-11-06; `DM-01M6R0TEM8`, `DM-01M6R2WSAR`, `DM-01M6R2WVRK`; A10, B3 | Ruled, planned (PR B) |
| G10 Artifact `DELETE` | operator 2026-11-06; FR-005 | Ruled, not built |
| G11 Relationship `DELETE` | `DM-01M6R0TEM8`; FR-006 | Ruled, not built |
| G12 Registration by source | `DM-01M6R1GNR6`, `DM-01M6R1T0HK`; FR-004 | Ruled, not built |
| G13 Atomicity | FR-007; research R7 | Partly built |
| G14 Lock order | research R-lock | Partly built |
| G15 Verified read | `DM-01M6R2X563`; ADR-0018 D11 | Partly built |
| G16 One writer | ADR-0061 | Built (documented) |
| G17 Unconditional per-artifact registration | ADR-0026 D5 | Built |
| G18 Activations unaffected | research R-lock | Ruled, not built |
| G19 Asynchronous registration | A5 | Ruled, planned (PR B) |
| G20 Impact report | B4 | Ruled, planned (PR B) |
| G21 Repair writes | ADR-0063 amendment 2026-11-06; #404 | Ruled, not built |
| W1 Closed body | ADR-0020 D4, D9 | Built |
| W2 Absent means none | ADR-0053 d10; DIV-203; A7 | Built; one `sel` form built (PR A0) |
| W3 Canonical form | ADR-0020 D4; operator 2026-11-06; `ArtifactSpellings` | Built (spellings normalized; DIV-208 withdrawn) |
| W4 No empty deny list | ADR-0020 D4 | Built |
| W5 Ids in the closure | ADR-0020 D9; `DocumentRules.check` | Built; `unknown_artifact` for a mission type (PR A0) |
| W6 Floor refusal, whole document | ADR-0012 d7; ADR-0013 d5; ADR-0020 D9; DIV-204; A4 | Built (PR A0); relaxed by W26 |
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
| W20 Reach as a least fixed point | ADR-0059 D2.1, D2.3, D2.4; DIV-206; A7 Q1 | Built; the step term built (PR A0) |
| W21 Stops and scope | ADR-0013 d4; ADR-0059 A2; A7 | Built; `mission_type` in scope built (PR A0) |
| W22 `includeDeactivated` | operator ruling A3; B5 | Built; B5 narrowing built (PR A0) |
| W23 Deactivate candidates | ADR-0059 A2; finding F2 | Built |
| W24 The shared-walk keep set | ADR-0059 A2, A4 | Built |
| W25 Floor refusal on a write | ADR-0059 D4; DIV-204 | Built; relaxed by W26 |
| W26 Relaxed required rule | `DM-01M6R3YX23`; research R12 | Ruled, not built |
| W27 Change report | FR-011 to FR-017; A5 | Built |
| W28 Skipped report of a write | ADR-0013 d4; ADR-0020 D6 | Built |
| W29 No-op | FR-011; A5 | Built |
| W30 Compare-and-set | ADR-0020 D6, D9 | Built |
| W31 Who changes a deny entry | ADR-0013 d2, d3 | Built |
| W32 Mission types in a write | A7 | Withdrawn; the withdrawal built (PR A0) |
| W33 Write warnings | B6, B7 | Ruled, planned (PR C) |
| W34 Withdrawn keys refused | A7 Q4 | Built (PR A0) |
| E1 Stale ids | ADR-0059 D4; FR-017; B1 | Built (PR B) |
| E2 Candidates | ADR-0020 D7; ADR-0053 d10; FR-013; A7 | Built (PR B) |
| E3 Base set | ADR-0012 d7; ADR-0020 D7; Q6 | Built (PR B) |
| E4 Override hiding | `DM-01M6R0TGX3`, `DM-01M6R2X2SW`; A3, A6 | Built (PR B) |
| E5 Requirement | operator 2026-11-06; `DM-01M6R3YTNT`; A2, A3 | Built (PR B) |
| E6 Enhancer and binding pruning | operator 2026-11-06; `DM-01M6R3YTNT`, `DM-01M6R4W3ZM`; A8; A7 Q1 | Built (PR B) |
| E7 Withdrawn overrides | research R13; A2, A8, A11 | Built (PR B) |
| E8 The effective set | research R2, R11, R12, R13 | Built (PR B) |
| E9 State and reason | ADR-0020 D7; data model; A7; research R11 | Built (PR B) |
| E10 In force | `DM-01M6R2WPZM` | Built (PR B) |
| E11 The listing | `DM-01M6R2WPZM`, `DM-01M6R0TNEB`; ADR-0059 D5 | Built (PR B) |
| E12 Enhancement, both ways | operator ruling A4; FR-015 | Partly built (PR B): no pull; the two-ended read is PR C |
| E13 Floor satisfaction | operator 2026-11-06 (Q6, Q8); `DM-01M6R4W3ZM` (A9) | Built (PR B) |
| E14 Skipped buckets | `DM-01M6R0TNEB`; FR-017 | Built (PR B) |
| E15 Graph projection | `DM-01M6R0TNEB`; FR-018 | Ruled, not built |
| E16 Action context | ADR-0018 D1, D2; FR-019; A7 | Planned, not built |
| E17 Validators | ADR-0059 D5 and its amendment of 2026-11-08; FR-020 | Built (PR B) |
| E18 Read refusals | FR-021 | Built (PR B) |
| E19 One verified read | NFR-007; `DM-01M6R2X563` | Built (PR B) |
| E20 Writes walk merged edges | research R2; `DM-01M6R4W1H9` | Built (PR B) |
| E21 Deny entries not applied | B4 | Built (PR B) |

Decision ids are shortened to their first twelve characters in the table; the full ids are in the rule text. "Spec",
"FR", "NFR" and "plan IC" without a mission name are the layer-merge mission's
(`kitty-specs/layer-merge-and-effective-reads-01M6R0J6/`); "activation mission" is
`kitty-specs/activation-register-and-closure-01M6NXBD/`.

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
  A5), [ADR-0061](/adr/0061-one-replica-per-deployment/), [ADR-0063](/adr/0063-pack-mutability-policy-file/).
- Design: [Logical model](/architecture/logical-model/), [Resolution model](/architecture/resolution-model/),
  [Offering and activation](/architecture/offering-and-activation/).
- Divergences: [Divergence register](/conformance/divergence-register/) (DIV-201 to DIV-215).
- Missions: `kitty-specs/activation-register-and-closure-01M6NXBD/` (spec, research) and
  `kitty-specs/layer-merge-and-effective-reads-01M6R0J6/` (spec, the resolved decisions under `decisions/`, plan,
  research, data model).
- Code, `service/packs`: `PackCompositionResolver`, `ComposeResolver`, `ClosureQueries`, `ClosureView`,
  `RequiredFloor`, `PerPackPolicy`, `Reachability`, `ReachRequest`, `PackMutabilityPolicy`, `MutabilityLevel`.
  `service/activations`: `DocumentRules`, `CascadeRules`, `StaleIds`, `PresetRules`, `ActivationWrites`,
  `ActivationExclusionService`.
- Operator rulings of 2026-11-06 not yet in an ADR: registration is a full replacement (planned ADR-0065), the floor
  and override rules Q6, Q8 and Q9 (planned ADR-0059 amendment A6), and the answers to the ruleset consistency review
  (`research/ruleset-review-2026-11-06.md` of the layer-merge mission; decisions `DM-01M6RA7FHR…` to `DM-01M6RAA6Y7…`).
- Code read for the fidelity corrections: `PackCompositionResolver` (`refuseContests`, `refusePresetContests`,
  `refuseCaseOnlyMatches`, `dependenciesOf`, `dependsOnClosure`), `ClosureQueries.winningPresets`, `RequiredFloor.of`,
  `ArtifactSpellings`, `DocumentRules.normalize`, `ActivationWrites.declared`, at #399 `1d37ccd9`.
