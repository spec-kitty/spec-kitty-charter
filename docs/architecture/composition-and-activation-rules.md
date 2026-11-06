---
title: Composition and activation rules
description: Every composition, registration, activation-write and effective-read rule as sets, relations, closures and invariants, each with its source and build status.
doc_status: draft
updated: 2026-11-06T00:00:00Z
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

> **Status labels.** **Built**: the service does it today (branch `issue-10-layer-merge-effective-reads`, on top of
> #398 and #399). **Ruled, not built**: an operator ruling or an accepted ADR decides it, and no code does it yet.
> **Planned, not built**: the design of the layer-merge mission (`kitty-specs/layer-merge-and-effective-reads-01M6R0J6/`,
> plan, research and data model), derived from rulings but not itself a ruling. **Partly built**: the rule holds in
> part, and the note says which part.

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

### Artifacts, kinds and edges

- `K`: the artifact kinds. `Kₐ = K ∖ {template, asset}`: the activatable kinds. `Kₛ = Kₐ ∖ {mission_type}`: the kinds
  with a `selected` and a `deactivated` list (a mission type lives in `missionTypes`).
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
- `defines(p, u) ⇔ holds(p, u)`; `changes(p, u) ⇔ ∃x. holds(p, x) ∧ (enhances(x, u) ∨ overrides(x, u))`.

### Mutability

- `M = (open < extendable < locked)`. `μ_p(u) ∈ M` is pack `p`'s declared level for `u`: its `artifacts` entry, else
  its `kind_defaults` entry, else `open`. A pack whose descriptor names no policy file has `μ_p ≡ open`.
- `μ_S(u) = max {μ_p(u) | p ∈ S}` for a set of packs `S`: the strictest declared level.
- `ops(open) = {override, enhance, remove}`, `ops(extendable) = {enhance, remove}`, `ops(locked) = ∅`.

### Presets and the floor

- `Pre(p)`: the presets `p` holds. For a preset `π`: `π[k]` its members of kind `k`, `π.kinds` its kind gate,
  `π.mts` its mission types, and `must(π) ⊆ U` its must-have items. `gov(π) ⇔ must(π) ≠ ∅`.
- `Req(C)`: the required set of closure `C`. `Lk(C) = {u | μ_C(u) = locked}`: the locked set.
  `Fl(C) = Req(C) ∪ Lk(C)`: the floor as the writes see it.

### The activation document

- `D = ⟨packs, kinds, mts, sel, deny, langs⟩`, the stored `ActivationConfig`:
  - `packs`: `⊥` or a list of `(cid, version?)`, in order;
  - `kinds ⊆ Kₐ`, `mts` (mission type ids) and `langs`: each `⊥` or a set;
  - `sel, deny: Kₛ → ⊥ or a set of ids`.
- `⟦x⟧ = ∅` when `x = ⊥`, else `x`: **absent means none**.
- `Sel(D) = {⟨k:i⟩ | i ∈ ⟦sel(k)⟧} ∪ {⟨mission_type:i⟩ | i ∈ ⟦mts⟧}`: what the document selects.
  `Den(D) = {⟨k:i⟩ | i ∈ ⟦deny(k)⟧}`: what it denies.
- `C(D)`: the activation's closure (C1, C2). `H(C)` its held URNs. `Mt(C)` its held mission types.

## 2. Composition rules

Composition decides which packs one request sees, in what order, and which version of each artifact wins.

**C1 Closure.** `C(S) = lfp X. S ∪ ⋃ {succ(p) | p ∈ X}`.
The composition closure is every pack reachable from the roots over `depends_on` and `parent_pack`.
*Source:* ADR-0048 d8; ADR-0053 d6 (amended 2026-11-03); `PackCompositionResolver.compositionClosure`. **Built.**
> ⚠ **Unclear:** ADR-0048 and ADR-0053 speak of the `depends_on` closure, and ADR-0048 d5 says `parent_pack` "sets no
> load order". The resolver also walks `parent_pack` into the closure (research R2 of the activation mission). Whether
> lineage is meant to widen the closure is not stated in an ADR.

**C2 Rooting.** `roots(D) = {cid | (cid, _) ∈ packs}` when `packs ≠ ⊥`, else `P`; the offering's roots are `P`.
An activation composes the closure of its own `packs`, or of every configured pack when `packs` is absent.
*Source:* ADR-0053 d6 (amended); ADR-0020 D4 (amended 2026-11-05); `ClosureQueries.snapshot`. **Built.**

**C3 Known roots.** `roots(D) ⊆ P`, else the write is refused `422 activation_invalid` (`unknown_pack`).
A root that names no configured pack composes nothing.
*Source:* FR-006 of the activation mission; `ClosureQueries.composedOfKnownRoots`, `DocumentRules`. **Built.**

**C4 Isolation.** A pack `q ∉ C(S)` is never judged by the compose of `S`.
An unrelated broken pack does not refuse an activation's closure.
*Source:* activation mission research R2, US2 AS2; `PackCompositionResolver.resolveRooted`. **Built.**

**C5 Order.** `C` is listed in a topological order of `succ` (dependencies first), ties broken by `lv` in merge order,
then by `cid` in code points.
Each level stays contiguous, and the order never depends on how the packs were listed.
*Source:* ADR-0048 d9; logical model (Composition); `PackCompositionResolver.topologicalOrder`, `STABLE_ORDER`.
**Built.**

**C6 No dependency cycle.** The `depends_on` graph on `C` is acyclic, else `pack_dependency_cycle`.
A dependency cycle refuses the compose.
*Source:* ADR-0048 d4. **Built.**

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

**C12 Qualified reference.** A reference `n::u` written in `p` resolves to the `q ∈ C({p})` with `name(q) = n`, else
`pack_reference_pack_missing`; and `holds(q, u)`, else `pack_reference_artifact_missing`.
A cross-pack reference must name a pack in the referring pack's own closure, and that pack must hold the artifact.
*Source:* ADR-0053 d7 (amended 2026-11-03); `PackCompositionResolver.checkReferences`. **Built.**

**C13 Bare reference.** A bare `u` written in `p` resolves to `p` when `holds(p, u)`; it is refused
`pack_reference_unqualified` when only another pack of `C` holds `u`; otherwise it has no holder.
*Source:* ADR-0053 d7; logical model (Qualified reference); `PackCompositionResolver.holderOf`. **Built.**
> ⚠ **Unclear:** ADR-0048 d2 rules that a link to an artifact no pack of the closure provides is refused at compose.
> The logical model and the resolution model say a bare reference with no holder simply has none, and the
> action-context walk passes through an undeclared node. Whether the d2 closure check is meant to refuse a dangling
> bare edge is not settled.

**C14 Sibling conflict.** For `u ∈ U` and `ℓ ∈ L`: `|{p ∈ C | lv(p) = ℓ ∧ defines(p, u)}| ≥ 2` or
`|{p ∈ C | lv(p) = ℓ ∧ changes(p, u)}| ≥ 2` is a conflict, refused `pack_sibling_conflict` unless settled (C15).
Two packs at one level that both define, or both change, one artifact conflict; a definer and an enhancer do not.
*Source:* ADR-0048 d9; ADR-0053 d5 (amended 2026-11-03), d21. **Built.**

**C15 Sibling settlement.** A conflict on `u` among siblings `S` is settled when `∃p ∈ C, {artifact: u, member: m} ∈
ovr(p)` with `m ∈ {pid(s) | s ∈ S}`; then `m`'s version of `u` is the one in force at level `ℓ`.
The compose names the winning sibling explicitly; there is no "last loaded wins".
*Source:* ADR-0048 d9; ADR-0053 d5; spec FR-012 of the layer-merge mission. **Partly built:** settlement clears the
refusal (built); putting `m`'s version in force (#357) is ruled, not built; until #358 any pack of `C` may settle.
> ⚠ **Unclear:** ADR-0053 d5 says the conflict is settled by "the distribution pack's own `overrides` edge". The
> built resolver and the layer-merge spec use a descriptor `overrides` entry `{artifact, member}` in `pack.yaml`, from
> any pack of the compose. The two are not the same mechanism.

**C16 Artifact identity per level.** `∀ℓ: |{p ∈ C | lv(p) = ℓ ∧ defines(p, u)}| ≤ 1` after C15.
An artifact id is unique within a level; across levels the same id is a layer override, not a conflict.
*Source:* ADR-0053 d6 (amended 2026-11-03). **Built** (as the refusal of C14).

**C17 Layer winner.** `win(u) = the place (p, u)` whose `lv(p)` is the level that applies among
`{lv(q) | holds(q, u), q ∈ C}`, after C15.
Levels merge built-in, then org, then project; the level that applies wins.
*Source:* ADR-0053 d6; ADR-0059 D3; spec FR-009. **Ruled, not built.**

**C18 Whole replacement.** `content(u) = content(win(u))`; no field of a losing place is read.
A layer override takes the place of the whole artifact; nothing is inherited.
*Source:* ADR-0059 D3; spec FR-009, US3 AS1. **Ruled, not built.**

**C19 Edges from a loser.** `(p, x)` loses `⇒` every edge whose source place is `(p, x)` is dropped.
An edge the overridden artifact declared leaves with it.
*Source:* ADR-0059 D3; spec FR-010. **Ruled, not built.**

**C20 Edges to a loser.** `(p, x)` loses and `r(y, (p, x)) ⇒ r(y, win(x))`, bare or `<pack>::` written.
What depended on the overridden id stays connected to the version in force.
*Source:* ADR-0059 D3; spec FR-010; research R3. **Ruled, not built.**

**C21 Override gate.** A layer override of `u` by the winning pack `w` is allowed iff `μ_{C∖{w}}(u) = open`, else the
read refuses `500 pack_unreadable` naming the artifact, both packs and the level.
Only an `open` artifact may be replaced by a later level; `extendable` and `locked` refuse it.
*Source:* ADR-0012 d1, d8; ADR-0059 D3 ("where the owning pack's level allows it"); spec FR-011; research R3.
**Ruled, not built** (the judging set `C ∖ {w}` is **Planned, not built**).
> ⚠ **Unclear:** ADR-0012 d9 names the refusal `governance_policy_violation`; the layer-merge plan surfaces it as
> `500 pack_unreadable` carrying the violation. The judging set without the winning pack is a planning decision, not a
> ruling.

**C22 Mutability along the closure.** `μ_C(u) = max_{p ∈ C} μ_p(u)`; `setBy(u)` is the first `p` in closure order with
`μ_p(u) = μ_C(u) > open`.
A target's level is the strictest any pack of the closure declares, whatever the order.
*Source:* ADR-0063 D3; ADR-0012 d3; `PerPackPolicy.levelIn`. **Built.**

**C23 Tighten, never loosen.** `S ⊆ S' ⇒ μ_S(u) ≤ μ_{S'}(u)`.
Adding a pack to a closure can raise a target's mutability, never relax it. (A consequence of C22.)
*Source:* ADR-0012 d3; ADR-0063 D3. **Built.**

**C24 Unreadable policy.** `pack.yaml` names a policy file that is missing, not UTF-8, not YAML or invalid
`⇒` the pack is `pack_unreadable` in every closure that holds it.
A lock that cannot be read is never taken as `open`.
*Source:* ADR-0063 D1, D4. **Built.**

**C25 Registration gate.** A write into pack `p` targeting `u` is refused `409 governance_policy_violation` when its
operation is not in `ops(μ_{C({p})}(u))`: replacing an artifact needs `open`, adding one or an edge touching one needs
not `locked`.
The per-artifact registrations obey the owner's level along the written pack's closure.
*Source:* ADR-0012 d1, d9; ADR-0026 D4; ADR-0063 D3; `PackMutabilityPolicy`, `MutabilityLevel.permits`. **Built.**

**C26 Removal by a derived pack.** A derived pack's pack-wide removal of `u` is allowed iff `μ(u) ≠ locked`.
`open` and `extendable` artifacts may be removed by a pack built on them; `locked` ones may not.
*Source:* ADR-0030 d1–d3. **Ruled, not built** (the representation is left to a later record, ADR-0030 d7).

**C27 CRG overrides at read.** For active `E` (E4): `overrides(E, T) ∧ T ∉ Lk(C) ⇒ T` is not in force, reason
`overridden`; `overrides(E, T) ∧ T ∈ Lk(C) ⇒ T` stays and `(T, E)` is listed in `overrideNotApplied`.
An active overriding artifact hides its target, unless the target is locked.
*Source:* decisions `DM-01M6R0TGX3DSR54EVS58Q1V8FV`, `DM-01M6R2X2SW8KVAJCDX23PGJBMT`; research R11; spec FR-016.
**Ruled, not built.**
> ⚠ **Unclear:** "overrides are not permitted on locked artifacts" (operator, Q9) reads either as a refusal of the
> `overrides` edge (ADR-0012 d1 and d9: `governance_policy_violation`, and C25 refuses an edge touching a locked
> artifact at registration) or as a read that leaves the override unapplied (research R11). The plan builds the second.
> ADR-0012 also forbids override of an `extendable` artifact, while the read rule exempts only a locked target.

**C28 Override cycle.** A cycle of `overrides` among active artifacts refuses the read `500 pack_unreadable`.
No cycle is broken by an arbitrary pick.
*Source:* spec Edge Cases, FR-016; research R2. **Ruled, not built.**

**C29 Winning preset.** For a preset id `i` held by several packs of `C`, the copy held by the last of them in closure
order (merge order) wins; the other copies are ignored, their must-have items included.
*Source:* ADR-0053 d6 (applied to presets); activation mission Assumptions; `ClosureQueries.winningPresets`. **Built.**

**C30 Governing preset.** `gov(π) ⇔ must(π) ≠ ∅`.
A preset with at least one must-have item governs; nothing else declares it.
*Source:* ADR-0031 d1–d2; ADR-0042 §2. **Built.**

**C31 Required set.** `Req(C) = ⋃ {must(π) | π a winning preset of C, gov(π)}`, never stored.
The requirement is the must-have items of the governing presets of the whole closure.
*Source:* ADR-0031 d2; DIV-205; decision `DM-01M6R0TK6F4CC0Z00CZTS21V72` (ratifies DIV-205); `RequiredFloor.of`.
**Built** (the ratification is ruled; DIV-205 stays open until the reads are built).

**C32 Floor at write.** `Fl(C) = Req(C) ∪ Lk(C)`.
The writes treat a locked artifact as required too.
*Source:* ADR-0012 d7 ("locked implies required"); `ClosureView.isRequired`, `ActivationExclusionService.floorOf`.
**Built.**
> ⚠ **Unclear:** ADR-0012 d7 and the code say locked implies required. The logical model says "a `locked` artifact is
> not thereby required", and the layer-merge research R11 splits the two at read (locked is forced, required is a
> requirement an override may satisfy).

**C33 Offering stays a catalog.** `/offering` composes `C(P)` and never applies C17–C21 or any activation.
*Source:* ADR-0020 D1; spec FR-023; activation mission FR-020. **Built** (the offering); the "never merges" half is
**Ruled, not built** with the merge.

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
`ver(z) = ver(s) ∧ rev(z) ≠ rev(s) ⇒ 409 pack_version_conflict`; otherwise `z` replaces `s`.
Any version may replace; one version always means one content.
*Source:* decision `DM-01M6R0TCAGHF27RKCB20QSCN8W`; spec FR-002; research R4. **Ruled, not built.**

**G4 Fixed identity.** On a replace: `pid(z) = pid(s) ∧ lv(z) = lv(s)`, else `422`. On a create: `pid(z)` and
`name(z)` are unused in `P`, else `409 registration_conflict`.
A pack's identity and level are fixed for its configured id.
*Source:* spec Edge Cases, FR-003; decision `DM-01M6R2X0H0VXGZZGSQC9C2SCSF`; research R1. **Planned, not built.**

**G5 Archive validity.** An archive with an unknown file, a path leaving the pack, a link or device, an invalid
artifact, more than 32 MiB or more than 10,000 entries is refused (`413` or `422`) before any file is written.
*Source:* spec FR-003, NFR-004, NFR-005; decisions `DM-01M6R1GKE8TAG2QBJT5PAHJQ8E`, `DM-01M6R2WY82DF6DV2FEAK9CK4S2`.
**Ruled, not built.**

**G6 Preconditions.** `If-Match ≠ rev(s) ⇒ 412`; `If-None-Match: * ∧ s exists ⇒ 412`; both headers `⇒ 400`. G3's
no-op is judged before them. Both `DELETE`s require `If-Match` (`428` when absent).
An identical re-send is a no-op even under a stale validator; a removal is always conditional.
*Source:* spec FR-003; research R-lock (API guarantees). **Planned, not built.**

**G7 Write gate on the diff.** Every artifact the replace adds, changes or removes passes C25 and C26 along the new
pack's closure, else `409 governance_policy_violation`.
*Source:* ADR-0063 D3; ADR-0030 d3; plan IC-04 step 7. **Planned, not built.**

**G8 Compose check.** `P ∖ {s} ∪ {z}` composes under C1–C16, else `422` with the composition code.
A registration never leaves the configured packs unable to compose.
*Source:* plan IC-04 step 8. **Planned, not built.**

**G9 Reference check.** For each artifact `u` a `DELETE` or replace removes from `p`:
`Inbound(p, u) = {(q, a) | q ∈ P, a held by q, a references u, holderOf_q(reference) = p}`, plus every descriptor
`overrides` entry naming `p` as member for `u`; `Inbound(p, u) ≠ ∅ ⇒ 409 artifact_referenced`.
A referenced artifact cannot be removed, whether the reference comes from the same pack or from another one.
*Source:* operator ruling of 2026-11-06; decisions `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`, `DM-01M6R2WSARGC382BHX89GZB64E`,
`DM-01M6R2WVRKB4JGZM40D6QF60BQ`; research R6. **Ruled, not built.**

**G10 Artifact `DELETE`.** `holds(p, u)` else `404`; then G9, then C25/C26 (`locked` is `409
governance_policy_violation`); then remove `u`'s file and companions, every edge whose source is `u`, its graph node,
and every preset item of `p` naming `u`.
`DELETE` removes one artifact with what only it declared; a preset key it empties reads as not listed.
*Source:* operator ruling of 2026-11-06; decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX`; spec FR-005; plan IC-06.
**Ruled, not built.**
> ⚠ **Unclear:** decision `DM-01M6R0TEM8YJK1J0DP0JSMSJNX` drops a preset item of the same pack that names the artifact,
> while `DM-01M6R2WSARGC382BHX89GZB64E` refuses a delete that another artifact of the same pack references. A preset
> is not an artifact, so the plan drops its item; whether a same-pack preset item counts as a reference is not ruled.

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

## 4. Activation write rules

Every activation write stores one canonical document, judged against one snapshot of its closure. `D` is the stored
document, `D'` the one the write computes, `C = C(D)` and `t` the artifact the path names.

### Document validity

**W1 Closed body.** The body is a closed `ActivationConfig`: an unknown member, a `null`, a duplicate key, a wrong
type or more than 1 MiB is `422 activation_invalid`.
*Source:* ADR-0020 D4, D9 and build notes; activation mission FR-006. **Built.**

**W2 Absent means none.** `⟦⊥⟧ = ∅` for `sel(k)`, `kinds` and `mts`; `packs = ⊥` means `P`; `⊥` and `∅` stay distinct
on the wire.
An absent list puts nothing of its kind in force, `skill` included.
*Source:* ADR-0053 d10; ADR-0013 d2 and ADR-0020 D6 (amended 2026-11-05); DIV-203. **Built.**
> ⚠ **Unclear:** `langs` does not follow W2 at read. Research R2 of the layer-merge mission has an absent `languages`
> filter nothing and `[]` filter every tagged artifact, while ADR-0053 d10 says an absent activation key means none.

**W3 Canonical form.** Ids are stored as declared, sorted and once; `packs` keeps its order; `rev(D) =
"sha256:" + hex(SHA-256(JCS(D)))`, which is the `ETag`.
Two equivalent documents have one revision, so `GET` is byte-stable.
*Source:* ADR-0020 D4; activation mission FR-005, research R3. **Built.**
> ⚠ **Unclear:** ADR-0020 D4 rules that another known spelling of an id is normalized to the declared id. The register
> matches ids exactly (DIV-208), so another spelling is an unknown id.

**W4 No empty deny list.** `deny(k) ≠ ⊥ ⇒ deny(k) ≠ ∅`; `[]` is `422` (`empty_deny_list`). A write that empties a deny
list removes the key.
*Source:* ADR-0020 D4; activation mission FR-006, US1 AS7. **Built.**

**W5 Ids in the closure.** `⋃_k ⟦sel(k)⟧ ∪ ⟦deny(k)⟧ ⊆ held_k(C)`, `⟦mts⟧ ⊆ Mt(C)`, each pinned version `= ver`, no
duplicate pack; else `422` (`unknown_artifact`, `unknown_mission_type`, `unknown_pack`, `version_not_held`,
`duplicate_pack`).
A document names only what its own closure holds.
*Source:* ADR-0020 D9; activation mission FR-006, FR-010; `DocumentRules.check`. **Built.**

**W6 Floor refusal on the whole document.** For create and replace: `Den(D') ∩ Lk(C) ≠ ∅ ⇒ 409 artifact_locked`;
otherwise `Den(D') ∩ Req(C) ≠ ∅ ⇒ 409 artifact_required`; `requiredBy` names each pack with its `parent_pack` chain.
A document that denies a locked or required artifact is never stored; locked is judged first.
*Source:* ADR-0012 d7; ADR-0013 d5; ADR-0020 D7, D9, D10; DIV-204; `DocumentRules.floorRefusal`. **Built.**
Relaxed by W26.

### Preconditions and order

**W7 Precondition order (per-artifact and preset writes).** `400` (malformed `If-Match`) → `428` (no `If-Match`) →
`404 activation_not_found` → `412` (stale `If-Match`) → `422` (body, path kind, `cascade`) → one snapshot (W8) →
`404 artifact_not_found`, `mission_type_not_found` or `preset_not_found` → W9 → the change → `422` or `409` (W5, W25) →
compare-and-set (W29).
Preconditions are judged before content, and no pack is read before them.
*Source:* ADR-0020 D6, D9; RFC 9110 §13.2.2; activation mission FR-010, finding F6; `ActivationWrites`. **Built.**

**W8 Create and replace order.** `400` (both validators, or `If-None-Match` other than `*`) → `412` (`If-None-Match: *` on
a stored id, or a stale `If-Match`) → `422` → `409`; a refused precondition takes no snapshot; without `If-Match` a
replace is last-writer-wins; `DELETE` answers `204`, then `404` on a second call.
*Source:* ADR-0020 D3, D6, D9; activation mission FR-001 to FR-004. **Built.**

**W9 One snapshot.** Every write takes exactly one verified snapshot of `C`; every walk and every rule read it.
One request never mixes two states of the packs.
*Source:* activation mission NFR-007; `ClosureQueries.snapshot`. **Built.**

**W10 Path id.** `t ∈ H(C)` (or `t ∈ Mt(C)`, or the preset id is held), else `404`; except that an unselect of
`t ∈ Sel(D)`, or a re-activate of `t ∈ Den(D)`, proceeds for a `t ∉ H(C)`.
A write only sees its closure, but may still remove a stale id the document holds.
*Source:* activation mission FR-010, FR-017; ADR-0059 amendment R5; `ActivationWrites.requireHeldOrStale`. **Built.**

### The document a write starts from

**W11 Stale drop.** `stale(D, C) = (Sel(D) ∪ Den(D)) ∖ (H(C) ∪ Mt(C))`; the write computes from
`D₀ = D ∖ stale(D, C)` and reports `dropped = stale(D, C)`.
Every per-artifact and preset write first removes the ids the closure no longer declares and reports them.
*Source:* operator ruling R5 (ADR-0059 amendment of 2026-11-06); DIV-204 notes; `StaleIds.drop`,
`ActivationWrites.current`. **Built.**

**W12 Stale drop scope.** W11 leaves `packs`, `kinds` and `langs` alone; an unknown pack or a pinned version the closure does not hold
still makes every per-artifact write `422`; create and replace drop nothing (an unknown id in their body is `422`).
*Source:* ADR-0059 amendment R5; activation mission FR-017. **Built.**

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

**W17 Apply a preset.** For the winning preset `π` (C29) and each key it lists (`π[k] ≠ ∅`, `π.kinds ≠ ∅`,
`π.mts ≠ ∅`): `sel'(k) = π[k] ∖ ⟦deny(k)⟧` without `force`, `= π[k]` with `force`; `kinds' = π.kinds ∩ Kₛ`;
`mts' = π.mts`. With `force`, `Den(D') = Den(D₀) ∖ ⋃_k π[k]`. Every other key is unchanged.
A preset replaces each key it lists; `force` clears only the deny entries of its members.
*Source:* ADR-0059 D6; ADR-0020 D6; activation mission FR-016, open review item 4; `PresetRules.apply`. **Built.**

**W18 A preset walks nothing and denies nothing.** The preset write's `skipped` is empty, and `Den(D') ⊆ Den(D₀)`.
A preset member the closure does not hold is selected and then refused by W5 (`422 unknown_artifact`).
*Source:* ADR-0013 d2; `PresetRules`; Offering and activation (Presets). **Built.**

### The cascade

**W19 Cascade relations.** A walk crosses exactly `Rc`, each edge from its source to its target; `enhances(E, T)` is
crossed from `E` to `T`.
No other relation (`overrides`, `replaces`, `rejects`, `delegates_to`, `vocabulary`, `applies`, the tension edges,
`implemented_by`, pack `depends_on`) is followed.
*Source:* ADR-0059 D2.1, D2.5 and operator ruling R4; DIV-207; `CascadeRules.CASCADE_RELATIONS`. **Built.**
> ⚠ **Unclear:** two design pages still carry the superseded `enhances` pull of ADR-0059 D2.2. The logical model's
> Relations section says the cascade follows "`enhances` both ways" with a read-time pull, and the resolution model's
> step 2 names "the read-time `enhances` pull" as the next mission's. Operator ruling R4 removed the pull at write and
> at read.

**W20 Reach as a least fixed point.** For starts `S`, stops `Σ` and lineage `Lin`:
`Anc(x) = lfp Y. {a ∉ Σ | specializes_from(x, a)} ∪ {a' ∉ Σ | a ∈ Y, specializes_from(a, a')}`;
`Next(X) = {y ∈ H(C) | x ∈ X ∖ Σ, r ∈ Rc, r(x, y)} ∪ {y ∈ H(C) | x ∈ X ∖ Σ, a ∈ Anc(x), r ∈ {requires, scope}, r(a, y)}`;
`Reach(S, Σ) = lfp X. S ∪ Next(X)`; `Reached = Reach ∖ Σ`; `Stopped = (Reach ∩ Σ) ∪ {a ∈ Σ | a an ancestor met}`;
`Passed = ⋃ {Anc(x) | x ∈ Reached}`.
The cascade is the closure of the targets over the six relations and the lineage pass-through, stopping at the stops.
*Source:* ADR-0059 D2.1, D2.3, D2.4; activation mission FR-012, FR-014; `Reachability.reach`, `ReachRequest.Lineage`.
**Built.**

**W21 Stops and scope.** Select: `Σ = Den(D₀) ∖ {t}`. Deactivate, own walk: `Σ = ∅` (R2). Deactivate, shared walk:
`Σ = Den(D₀) ∪ {t}`. `Scope ⊆ Kₛ` or `all = Kₛ`; `mission_type ∉ Scope` always; no `cascade` means `Reach = {t}`.
A select stops at deny entries, a deactivate's own walk stops nowhere, and the shared walk stops at the target and the
deny entries.
*Source:* ADR-0013 d4; ADR-0020 D6 and amendment ruling 6; ADR-0059 D2.4 and operator ruling R2; activation mission
FR-012, finding F8; `CascadeRules.planSelect`, `planDeactivate`, `planShared`. **Built.**

**W22 `includeDeactivated`.** `Σ = (Den(D₀) ∖ {t}) ∖ {u | kind(u) ∈ Scope}`; `Clr = (Reached ∪ Passed) ∩ Den(D₀) ∩
{u | kind(u) ∈ Scope}`.
It clears only the in-scope deny entries the walk passes through; an out-of-scope denied artifact still stops the walk
and keeps its entry.
*Source:* operator ruling R3 (ADR-0059 amendment of 2026-11-06); ADR-0013 d4; DIV-206; `CascadeRules.walkedThrough`.
**Built.**

**W23 Deactivate candidates.** `Cand = (Reached_own ∩ {u | kind(u) ∈ Scope} ∩ Sel(D₀)) ∖ Fl(C)`.
What the target's own walk reached, is selected and is in scope is a candidate, unless it is on the floor.
*Source:* ADR-0020 D6; ADR-0059 operator ruling R2; activation mission finding F2; `CascadeRules.candidates`.
**Built.**

**W24 The shared-walk keep set.** `Others = (Sel(D₀) ∪ Fl(C)) ∖ ({t} ∪ Cand)`;
`Kept = Cand ∩ Reach(Others, Den(D₀) ∪ {t}).Reached`.
A candidate another active artifact still reaches, directly or through another kept candidate, stays selected.
*Source:* ADR-0020 D6; ADR-0059 operator rulings R2, R4; activation mission FR-015, Assumptions (`skippedShared`);
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
> Research R12 plans an ADR-0059 amendment R6 to record it; until then the ADR texts and the ruling disagree.

### Report, no-op and storage

**W27 Change report.** `applied = (Sel(D') △ Sel(D₀)) ∪ (Den(D') △ Den(D₀))`, `cleared = Den(D₀) ∖ Den(D')`,
`dropped = stale(D, C)`; `applied` and `cleared` are measured against `D₀`; a kind is never an entry.
*Source:* activation mission FR-011, FR-015, FR-016, FR-017; ADR-0059 amendment R5; `ActivationChange.of`. **Built.**

**W28 Skipped report of a write.** Select: `skippedByScope = Reached ∖ Scope`, `skippedDeactivated = Stopped` (each
with `viaRequires` and `requiredBy`), `unreachedBehindDeactivated = Reach(S, ∅) ∖ (Reached ∪ Stopped)`,
`skippedShared = ∅`. Deactivate: `skippedByScope = (Reached_own ∖ Scope) ∩ Sel(D₀)`, `skippedShared = Kept` plus every
selected floor artifact the target reached that another active artifact reaches, each with `referencedBy`.
*Source:* ADR-0013 d4; ADR-0020 D6; ADR-0059 D2.4; activation mission finding F3, re-review R2; `CascadeRules`,
`Predecessors`. **Built.**

**W29 No-op.** `D' = D ∧ stale(D, C) = ∅ ⇒ 200` with `rev(D)`, empty `applied` and `cleared`, nothing stored.
A write that drops a stale id is never a no-op.
*Source:* activation mission FR-011, FR-017; ADR-0059 amendment R5. **Built.**

**W30 Compare-and-set.** `D'` is stored iff the stored revision still equals the one loaded, else `412`; the write
goes to a temporary file, forced to the device, then moved atomically, under a per-id lock.
*Source:* ADR-0020 D6, D9; activation mission FR-019, NFR-004, NFR-005, research R4. **Built.**

**W31 Who changes a deny entry.** `Den(D') ∖ Den(D₀) ⊆ {t}` and is non-empty only for a deactivate;
`Den(D₀) ∖ Den(D')` is non-empty only for a select (`{t}` and W22), a re-activate, a preset with `force`, W4 and W11.
Only the user's explicit choice adds or removes a deny entry.
*Source:* ADR-0013 d2, d3; ADR-0020 D6, amendment 2026-11-05 to ADR-0013 d2. **Built.**

**W32 Mission types in a write.** A select of kind `mission_type` adds the id to `mts` and never cascades; a mission
type has no deny list (`422` on the exclusions path); a walk that reaches a mission type never adds it to `mts` and
never makes it a candidate.
*Source:* ADR-0020 amendment 2026-10-31 (design decisions); activation mission US3 AS7, finding F8. **Built.**

## 5. Read rules

The four effective reads (`listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`,
`getEffectiveActionContext`) answer `501` today. Every rule in this section is **Ruled, not built** or **Planned, not
built** unless it says otherwise. `M` is the merged closure of `C(D)` (C17–C21, C15), `W` its winning URNs of a kind
in `Kₐ`, `Lk = Lk(C)` and `Req = Req(C)`.

**E1 Stale ids.** `staleIds = (Sel(D) ∪ Den(D)) ∖ W`; a stale id has no state.
A stored document is served as stored; what the packs no longer hold is reported, not refused.
*Source:* ADR-0059 D4; spec FR-017, US3 AS3; data model (EffectiveSkippedReport). **Ruled, not built.**
`GET /activations/{id}` serving the document unchanged is **Built**.

**E2 Candidates.** `Cand(D) = {u ∈ W ∩ Sel(D) | kind(u) ∈ ⟦kinds⟧ ∪ {mission_type} ∧ lang(u) ∧ mt(u) ∧ u ∉ Den(D)}`,
where `lang(u) ⇔ langs = ⊥ ∨ tags(u) = ∅ ∨ tags(u) ∩ langs ≠ ∅` and `mt(u) ⇔ u` is bound to no mission type, or to
one in `⟦mts⟧`.
A selected artifact is a candidate when its kind is activated, its languages and mission type are in force, and it is
not denied.
*Source:* ADR-0020 D7; ADR-0053 d10; ADR-0059 D2.2 (step targets); spec FR-013; research R2. **Planned, not built.**
> ⚠ **Unclear:** whether `activatedKinds` gates mission types. The precedence table applies "kind outside
> `activatedKinds`" to every kind, and `kinds ⊆ Kₐ` may list `mission_type`, while `missionTypes` is its own key.

**E3 Base set.** `Base = Cand(D) ∪ Lk`.
A locked artifact is in force whatever the document, the kind gate or the filters say.
*Source:* ADR-0012 d7; ADR-0013 d2; ADR-0020 D7; DIV-201; research R11 (operator, Q6). **Ruled, not built.**

**E4 Override hiding.** `Ov(X) = {T | ∃E ∈ X. overrides(E, T)}`; `A₁ = Base ∖ (Ov(Base) ∖ Lk)`.
Overrides apply from the whole base set at once, so a chain `F → E → T` hides both `E` and `T`; a locked target stays
(C27).
*Source:* decisions `DM-01M6R0TGX3DSR54EVS58Q1V8FV`, `DM-01M6R2X2SW8KVAJCDX23PGJBMT`; research R2 step 4, R11.
**Ruled, not built.**

**E5 Requirement.** `Unsat = {T ∈ Req ∖ Lk | T ∉ A₁ ∧ ¬∃E ∈ A₁. overrides(E, T)}`;
`A₂ = (A₁ ∪ Unsat) ∖ (Ov(Unsat) ∖ Lk)`.
A required artifact is satisfied by itself in force or by an active override of it; otherwise it is put in force, and
what it overrides is hidden once more.
*Source:* operator ruling of 2026-11-06 ("required means T, or an active override of T, must be in force");
decisions `DM-01M6R2X2SW8KVAJCDX23PGJBMT`, `DM-01M6R3YTNT1P2MBERBN7WV71ND`; research R11. **Ruled, not built.**
> ⚠ **Unclear:** decision `DM-01M6R2X2SW8KVAJCDX23PGJBMT` says an override **or an enhancement** satisfies a
> requirement; the later `DM-01M6R3YTNT1P2MBERBN7WV71ND` says an enhancer never does. This page follows the later one.
> Whether an unsatisfied required artifact is also put in force when the language or mission-type filter excludes it
> is not stated; US3 AS4 states it only for the kind gate.

**E6 Enhancer pruning.** `Eff₀ = gfp X ⊆ A₂. {u ∈ A₂ | u ∈ Lk ∨ ¬∃T. enhances(u, T) ∨ stepEnh(u) ∨ ∃T ∈ X.
enhances(u, T)}`.
An enhancer is in force only while at least one of its targets is; pruning repeats down chains of enhancers.
*Source:* operator ruling of 2026-11-06 ("an enhancer needs its target T active to work"); decision
`DM-01M6R3YTNT1P2MBERBN7WV71ND`; research R11 step 4; data model rule 8. **Ruled, not built** (the greatest fixed
point is this page's reading of "repeats down chains").
> ⚠ **Unclear:** the operator says an enhancer whose target is not in force "has no effect". The plan makes it
> `inactive` (`enhanced_target_not_in_force`); the superseded ADR-0059 D2.2 had it "stay in force and add nothing".
> The order "requirement, then pruning" also leaves open whether a required or locked enhancer with no target in
> force stays in force; this page keeps a locked one (data model rule 1). The question is open as decision
> `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ`.

**E7 Withdrawn overrides.** If `Eff₀` removed an artifact that declares `overrides`, E4–E6 run once more without
those overrides; if that run removes another overriding enhancer, the read refuses `500 pack_unreadable`.
`Eff(D, M)` is the result.
*Source:* research R11 step 5, open review item 7. **Planned, not built.**

**E8 The effective set.** In closed form, `Eff(D, M) = Prune(Require(Hide(Cand(D) ∪ Lk)))`, with `Hide`, `Require`
and `Prune` as E4, E5 and E6 (and E7 once).
What is in force is computed from the document and the merged closure alone, by one function the reads and W26 share.
*Source:* research R2, R11, R12; plan IC-09, IC-13. **Planned, not built.**

**E9 State and reason.** For `u ∈ W`, `(state, reason)` is the first row that holds: locked → `active, locked`; kind
outside `⟦kinds⟧` → `inactive, kind_not_activated`; language → `inactive, language_filtered`; mission type not in
force → `inactive, mission_type_not_in_force`; denied → `excluded, deactivated`; not selected →
`inactive, not_selected`; overridden by an active artifact → `inactive, overridden`; enhancer without a target in
force → `inactive, enhanced_target_not_in_force`; otherwise `active, selected`. A required artifact active by the
table gets `required`; one put in force by E5 is `active, required`; one satisfied by an override keeps its row and
gains `satisfiedBy`.
*Source:* ADR-0020 D7; data model (EffectiveArtifact, Precedence); decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`
(`kind_default` dropped). **Planned, not built.**

**E10 In force.** `u` is in force `⇔ u ∈ Eff(D, M) ⇔ state(u) = active`.
*Source:* decision `DM-01M6R2WPZM7K1FBJCFFE0R2TVR` (operator, Q1); ADR-0020 D7. **Ruled, not built.**

**E11 The listing.** `listEffectiveArtifacts` lists every `u ∈ W` once, with `state`, `reason`, `required`, `policy =
μ_C(u)`, `packId` and `level` of `win(u)`, and `satisfiedBy`; paged by `(kind, id)` in code points, filtered by
`kind` and `state`; `summary` counts the whole set.
*Source:* decisions `DM-01M6R2WPZM7K1FBJCFFE0R2TVR`, `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; ADR-0059 D5; spec FR-013, FR-022.
**Ruled, not built.**
> ⚠ **Unclear:** the ruling says "every closure artifact"; the data model lists only winning ids of an activatable
> kind, so templates, assets and the losing versions of a layer override are not listed.

**E12 Enhancement, both ways.** For `T ∈ Eff`: `enhancedBy(T) = {E ∈ Eff | enhances(E, T)}`; `E ∉ Eff ⇒ E` is never
returned for `T`, and no read adds `E` or `T` to `Eff`.
An enhancement is returned only when it is itself active, and is read from either end; nothing is pulled in.
*Source:* operator ruling R4 (ADR-0059 amendment of 2026-11-06); spec FR-015, US3 AS5; DIV-207 note.
**Ruled, not built.**

**E13 Floor satisfaction.** `∀T ∈ Req: T ∈ Eff ∨ ∃E ∈ Eff. overrides(E, T)`, and `∀T ∈ Lk: T ∈ Eff`; an enhancer never
satisfies `T`.
*Source:* operator rulings of 2026-11-06 (Q6, Q8); research R11. **Ruled, not built.**

**E14 Skipped buckets.** Each `u ∈ Sel(D) ∪ Den(D)` that is not in force is listed once, in the bucket of its first
deciding row: `staleIds`, `skippedByScope` (kind not activated), `languageFiltered`, `missionTypeNotInForce`,
`skippedDeactivated` (`viaRequires = false`, `requiredBy = []` at read), `overridden`, `enhancedTargetNotInForce`.
`denyNotApplied` (`{u, reason: required or locked}`) and `overrideNotApplied` (`{T, overriddenBy}`) list rules not
applied; `unreachedBehindDeactivated` and `skippedShared` are empty at read.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; ADR-0059 D4 and follow-ups; spec FR-017, US4 AS1; data model.
**Ruled, not built** (the bucket set is **Planned**).

**E15 Graph projection.** `nodes = Eff`; `edges = {(x, r, y) ∈ E_M | x, y ∈ Eff}`; `layerOverrides = {(u, winner,
loser) | u ∈ Eff}`, one record per losing version.
The graph holds only the edges among artifacts in force, after C19–C20, and lists the layer overrides applied.
*Source:* decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; spec FR-018, US5 AS1–AS3; research R3. **Ruled, not built.**

**E16 Action context.** `getEffectiveActionContext(mt, a)` is ADR-0018's five-step walk from `action:mt/a` over
`E_M` restricted to `Eff`, each listed artifact with `enhancedBy` (E12); `mt ∉ ⟦mts⟧ ⇒ artifacts = []` and
`missionTypeNotInForce = {mt}`; an unknown `mt` or `a` is `404 mission_type_not_found` or `action_not_found`.
*Source:* ADR-0018 D1, D2, D5; spec FR-019, US5 AS4–AS5; research open review item 4. **Planned, not built.**
> ⚠ **Unclear:** FR-019 says the context is what "the step contract names"; the plan reads it as the ADR-0018 walk.
> The data model allows `depth` 0 or 1, while ADR-0018 D4 allows 0 to 2.

**E17 Validators.** `ETag = sha256("charter-effective-read\0v1\n", rev(D), fingerprint(C))`; a weak match on
`If-None-Match` answers `304` with no body.
An effective answer changes with the document or the packs, and nothing else.
*Source:* ADR-0059 D5; spec FR-020; research R5. **Ruled, not built** (the tag recipe is **Planned**).

**E18 Read refusals.** `404 activation_not_found` for an unknown activation; `500` with the composition code, or
`pack_unreadable` for C21, C28 or E7, when `C` cannot compose or merge.
*Source:* spec FR-021. **Ruled, not built.**

**E19 One verified read.** Each effective read composes and merges `C` once, from one verified read (G15), retried up
to 3 times on `stale_input`.
*Source:* spec NFR-007; decision `DM-01M6R2X563762EDCVBR02W4FK7`. **Ruled, not built.**

**E20 Writes stay unmerged.** The write cascade (W20) walks the closure's unmerged edges; only W26 reads `M`.
*Source:* research R2 (open point), open review item 3; plan IC-07, IC-13. **Planned, not built.**
> ⚠ **Unclear:** whether the write cascade should follow merged edges is not ruled (open decision
> `DM-01M6R4W1H9P3451V89KHP97XKM`), so a cascade from an overridden
> `D` may still follow an edge `D`'s losing version declared. Likewise W23–W24 treat every `Fl(C)` item as active,
> while at read a required item may be hidden by an override.

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
holds is held by its closure (W5, W11). Its revision is `rev(D)`, and a `PUT` of a fetched, non-stale `config` returns
the same `ETag`.

**I6 The floor is derived.** No stored document holds `Req`, `Lk` or a skipped report. (ADR-0020 D4, C31)

**I7 Deny entries are the user's.** Every deny entry in a stored `D` was added by a deactivate of that id, or by a
create or replace whose body named it. (W31)

**I8 No new floor denial.** No write adds a deny entry on `Lk(C)`; no write adds one on `Req(C)` except under W26.
(W6, W25, W26)

**I9 Locked is in force.** `Lk(C) ⊆ Eff(D, M)` for every `D`; no override hides a locked artifact. (E3, E4, C27)

**I10 Requirement holds.** Every `T ∈ Req(C)` is in force or overridden by an artifact in force. (E13)

**I11 No enhancer stands alone.** `E ∈ Eff ∧ ∃T. enhances(E, T) ⇒ (∃T ∈ Eff. enhances(E, T)) ∨ stepEnh(E) ∨ E ∈ Lk`.
(E6)

**I12 No ancestor in force by lineage.** A cascade never adds a `specializes_from` ancestor it passed through to
`Sel(D')`. (W20, W22)

**I13 Termination.** Every walk, every closure and every fixed point terminates: the universes are finite and each
node is visited once. (C1, W20, E6)

**I14 Round trip of a cascade.** With no deny entry met, no floor item and no other active referrer, a cascading select
of `t` followed by a cascading deactivate of `t` with the same scope leaves `Sel` as it was, less `t`. (W13, W15, W21,
operator ruling R2)

**I15 Reports are exact.** At read, each artifact the document names and that is not in force is in exactly one bucket
(E14); at write, `applied`, `cleared` and `dropped` are disjoint (W27).

**I16 Write and read agree.** A deny entry W26 accepts is one E5 judges satisfied, because both evaluate `Eff` over the
document as stored. (W26, E8)

**I17 No mixed state.** No read or write answers from two states of the packs; a straddled read is `stale_input`.
(W9, G13, G15, E19)

**I18 The offering ignores activations.** `/offering` and `listActivationPacks` never depend on a document's
selections. (C33)

## 7. Source and status table

| Rule | Source | Status |
|---|---|---|
| C1 Closure | ADR-0048 d8; ADR-0053 d6 | Built |
| C2 Rooting | ADR-0053 d6; ADR-0020 D4 | Built |
| C3 Known roots | activation mission FR-006 | Built |
| C4 Isolation | activation mission R2, US2 | Built |
| C5 Order | ADR-0048 d9; logical model | Built |
| C6 No dependency cycle | ADR-0048 d4 | Built |
| C7 Known dependency | ADR-0048 d4 | Built |
| C8 Range | ADR-0048 d1, d4, d8; ADR-0053 d4 | Built |
| C9 Parent resolves | ADR-0048 d6 | Built |
| C10 Level direction | ADR-0048 d7; ADR-0053 d8 | Built |
| C11 Pack identity | ADR-0053 d6, d7 | Built |
| C12 Qualified reference | ADR-0053 d7 | Built |
| C13 Bare reference | ADR-0053 d7 | Built |
| C14 Sibling conflict | ADR-0048 d9; ADR-0053 d5, d21 | Built |
| C15 Sibling settlement | ADR-0053 d5; spec FR-012 | Partly built (#357, #358) |
| C16 Artifact identity per level | ADR-0053 d6 | Built |
| C17 Layer winner | ADR-0053 d6; ADR-0059 D3 | Ruled, not built |
| C18 Whole replacement | ADR-0059 D3 | Ruled, not built |
| C19 Edges from a loser | ADR-0059 D3 | Ruled, not built |
| C20 Edges to a loser | ADR-0059 D3 | Ruled, not built |
| C21 Override gate | ADR-0012 d1, d8; ADR-0059 D3; FR-011 | Ruled, not built |
| C22 Mutability along the closure | ADR-0063 D3 | Built |
| C23 Tighten, never loosen | ADR-0012 d3 | Built |
| C24 Unreadable policy | ADR-0063 D4 | Built |
| C25 Registration gate | ADR-0012 d1, d9; ADR-0026 D4 | Built |
| C26 Removal by a derived pack | ADR-0030 d1 to d3 | Ruled, not built |
| C27 CRG overrides at read | `DM-01M6R0TGX3`, `DM-01M6R2X2SW`; R11 | Ruled, not built |
| C28 Override cycle | spec FR-016 | Ruled, not built |
| C29 Winning preset | ADR-0053 d6 | Built |
| C30 Governing preset | ADR-0031; ADR-0042 §2 | Built |
| C31 Required set | ADR-0031; DIV-205; `DM-01M6R0TK6F` | Built |
| C32 Floor at write | ADR-0012 d7 | Built |
| C33 Offering stays a catalog | ADR-0020 D1; FR-023 | Built; the merge half ruled, not built |
| G1 Writable levels | `DM-01M6R1GQYF` | Ruled, not built |
| G2 Full replacement | operator 2026-11-06; `DM-01M6R0TA37` | Ruled, not built |
| G3 Version rule | `DM-01M6R0TCAG` | Ruled, not built |
| G4 Fixed identity | spec FR-003; `DM-01M6R2X0H0` | Planned, not built |
| G5 Archive validity | FR-003, NFR-004, NFR-005 | Ruled, not built |
| G6 Preconditions | FR-003; research R-lock | Planned, not built |
| G7 Write gate on the diff | ADR-0063 D3; plan IC-04 | Planned, not built |
| G8 Compose check | plan IC-04 | Planned, not built |
| G9 Reference check | operator 2026-11-06; `DM-01M6R0TEM8`, `DM-01M6R2WSAR`, `DM-01M6R2WVRK` | Ruled, not built |
| G10 Artifact `DELETE` | operator 2026-11-06; FR-005 | Ruled, not built |
| G11 Relationship `DELETE` | `DM-01M6R0TEM8`; FR-006 | Ruled, not built |
| G12 Registration by source | `DM-01M6R1GNR6`, `DM-01M6R1T0HK`; FR-004 | Ruled, not built |
| G13 Atomicity | FR-007; research R7 | Partly built |
| G14 Lock order | research R-lock | Partly built |
| G15 Verified read | `DM-01M6R2X563`; ADR-0018 D11 | Partly built |
| G16 One writer | ADR-0061 | Built (documented) |
| G17 Unconditional per-artifact registration | ADR-0026 D5 | Built |
| G18 Activations unaffected | research R-lock | Ruled, not built |
| W1 Closed body | ADR-0020 D4, D9 | Built |
| W2 Absent means none | ADR-0053 d10; DIV-203 | Built |
| W3 Canonical form | ADR-0020 D4 | Built (no spelling normalization, DIV-208) |
| W4 No empty deny list | ADR-0020 D4 | Built |
| W5 Ids in the closure | ADR-0020 D9 | Built |
| W6 Floor refusal, whole document | ADR-0012 d7; ADR-0013 d5; ADR-0020 D9; DIV-204 | Built; relaxed by W26 |
| W7 Precondition order | ADR-0020 D6, D9 | Built |
| W8 Create and replace order | ADR-0020 D3, D6, D9 | Built |
| W9 One snapshot | activation mission NFR-007 | Built |
| W10 Path id | activation mission FR-010; R5 | Built |
| W11 Stale drop | operator ruling R5 | Built |
| W12 Stale drop scope | R5; FR-017 | Built |
| W13 Select | ADR-0020 D6; ADR-0013 d3 | Built |
| W14 Unselect | ADR-0020 D6 | Built |
| W15 Deactivate | ADR-0013 d3; ADR-0020 D6 | Built |
| W16 Re-activate | ADR-0013 d3; ADR-0020 D6 | Built |
| W17 Apply a preset | ADR-0059 D6; ADR-0020 D6 | Built |
| W18 A preset walks nothing | ADR-0013 d2 | Built |
| W19 Cascade relations | ADR-0059 D2, R4; DIV-207 | Built |
| W20 Reach as a least fixed point | ADR-0059 D2.1, D2.3, D2.4; DIV-206 | Built |
| W21 Stops and scope | ADR-0013 d4; ADR-0059 R2 | Built |
| W22 `includeDeactivated` | operator ruling R3 | Built |
| W23 Deactivate candidates | ADR-0059 R2; finding F2 | Built |
| W24 The shared-walk keep set | ADR-0059 R2, R4 | Built |
| W25 Floor refusal on a write | ADR-0059 D4; DIV-204 | Built; relaxed by W26 |
| W26 Relaxed required rule | `DM-01M6R3YX23`; research R12 | Ruled, not built |
| W27 Change report | FR-011 to FR-017; R5 | Built |
| W28 Skipped report of a write | ADR-0013 d4; ADR-0020 D6 | Built |
| W29 No-op | FR-011; R5 | Built |
| W30 Compare-and-set | ADR-0020 D6, D9 | Built |
| W31 Who changes a deny entry | ADR-0013 d2, d3 | Built |
| W32 Mission types in a write | ADR-0020 amendment 2026-10-31 | Built |
| E1 Stale ids | ADR-0059 D4; FR-017 | Ruled, not built (the stored document is served: built) |
| E2 Candidates | ADR-0020 D7; ADR-0053 d10; FR-013 | Planned, not built |
| E3 Base set | ADR-0012 d7; ADR-0020 D7; Q6 | Ruled, not built |
| E4 Override hiding | `DM-01M6R0TGX3`, `DM-01M6R2X2SW` | Ruled, not built |
| E5 Requirement | operator 2026-11-06; `DM-01M6R3YTNT` | Ruled, not built |
| E6 Enhancer pruning | operator 2026-11-06; `DM-01M6R3YTNT` | Ruled, not built |
| E7 Withdrawn overrides | research R11 | Planned, not built |
| E8 The effective set | research R2, R11, R12 | Planned, not built |
| E9 State and reason | ADR-0020 D7; data model | Planned, not built |
| E10 In force | `DM-01M6R2WPZM` | Ruled, not built |
| E11 The listing | `DM-01M6R2WPZM`, `DM-01M6R0TNEB`; ADR-0059 D5 | Ruled, not built |
| E12 Enhancement, both ways | operator ruling R4; FR-015 | Ruled, not built |
| E13 Floor satisfaction | operator 2026-11-06 (Q6, Q8) | Ruled, not built |
| E14 Skipped buckets | `DM-01M6R0TNEB`; FR-017 | Ruled, not built |
| E15 Graph projection | `DM-01M6R0TNEB`; FR-018 | Ruled, not built |
| E16 Action context | ADR-0018 D1, D2; FR-019 | Planned, not built |
| E17 Validators | ADR-0059 D5; FR-020 | Ruled, not built |
| E18 Read refusals | FR-021 | Ruled, not built |
| E19 One verified read | NFR-007; `DM-01M6R2X563` | Ruled, not built |
| E20 Writes stay unmerged | research R2, open item 3 | Planned, not built |

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
  [amendment of 2026-11-06](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/#amendment-2026-11-06) (R2 to
  R5), [ADR-0061](/adr/0061-one-replica-per-deployment/), [ADR-0063](/adr/0063-pack-mutability-policy-file/).
- Design: [Logical model](/architecture/logical-model/), [Resolution model](/architecture/resolution-model/),
  [Offering and activation](/architecture/offering-and-activation/).
- Divergences: [Divergence register](/conformance/divergence-register/) (DIV-201 to DIV-208).
- Missions: `kitty-specs/activation-register-and-closure-01M6NXBD/` (spec, research) and
  `kitty-specs/layer-merge-and-effective-reads-01M6R0J6/` (spec, the resolved decisions under `decisions/`, plan,
  research, data model).
- Code, `service/packs`: `PackCompositionResolver`, `ComposeResolver`, `ClosureQueries`, `ClosureView`,
  `RequiredFloor`, `PerPackPolicy`, `Reachability`, `ReachRequest`, `PackMutabilityPolicy`, `MutabilityLevel`.
  `service/activations`: `DocumentRules`, `CascadeRules`, `StaleIds`, `PresetRules`, `ActivationWrites`,
  `ActivationExclusionService`.
- Operator rulings of 2026-11-06 not yet in an ADR: registration is a full replacement (planned ADR-0065), the floor
  and override rules Q6, Q8 and Q9 (planned ADR-0059 amendment R6).
