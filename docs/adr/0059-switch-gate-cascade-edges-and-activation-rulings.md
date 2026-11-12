---
title: "ADR-0059: The switch gate, the cascade edges, the layer-override grain and the activation write rulings"
description: "Operator rulings of 2026-11-04 to 2026-11-09: BDD scenarios and contract tests gate each core switch; cascade edges; whole-artifact layer overrides; ADR-0020 questions 4 to 7."
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-04'
type: ADR
kind: ADR
related:
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0020-offering-and-activations
  - adr/0013-persistent-deactivation
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0016-conformance-corpus-harness-over-core-cli
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
audience:
  - profile: governance-lead
    guidance_text: Read this for what proves an operation is ready for core to switch to it, and the activation rules the service builds to.
  - profile: charter-service-contributor
    guidance_text: Read this before building the activation writes (#291), the cascade or the layer merge (#10, #11).
---

# ADR-0059: The switch gate, the cascade edges, the layer-override grain and the activation write rulings

## Status

**Accepted**, 2026-11-04. Decider: Stijn Dejongh (operator). The operator ruled these points in one session on
2026-11-04, before the activation writes ([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)) and
the resolution domain ([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)) are built. On 2026-11-05 the operator confirmed
or changed this record's defaults, ruled the questions it had left open and ruled how the `enhances` pull behaves at read; the decisions below carry those rulings. On 2026-11-06 the operator amended D2 and D4 for the activation writes; the
[amendment](#amendment-2026-11-06) below records it, and each amended decision points there.
On 2026-11-07 the layer-merge mission's PR A0 built the rulings that change what #399 shipped (ruleset review A4, B1 and
B5), the one kind model (A7) and a `depth` on the cascade (#408); the [amendment of 2026-11-07](#amendment-2026-11-07)
records them, and D2, D4 and D6 carry a note where it changes them. On 2026-11-08 the mission's PR B built the layer
merge and two effective reads; the [amendment of 2026-11-08](#amendment-2026-11-08) records the two operator decisions
that change D5 and ticks the follow-ups PR B closes. On 2026-11-09 its PR C built the effective graph and action context
and the relaxed write rule; the [amendment R6](#amendment-2026-11-09-r6) records the write rule and the read rules of
research R11 that amend D2 and D3. On 2026-11-12 the switch-gate mission (`switch-gate-coverage-01M77A7P`) made D1's
gate an explicit, checked register; the [amendment of 2026-11-12](#amendment-2026-11-12-the-switch-gate-register)
records the rule it checks.

This record answers [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/) open question 1 and
[ADR-0020](/adr/0020-offering-and-activations/) open questions 4 to 7. It also settles two points that
[ADR-0013](/adr/0013-persistent-deactivation/), ADR-0020 and
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) left open: the edges the cascade
follows, and the grain of a layer override. Each of those records points here.

Core's ADR `2026-10-03-1` still names the conformance corpus as the only gate. Core amends it to match; this
repository does not.

## Context

- **The switch gate.** ADR-0055 D3 made this repository's design the acceptance oracle and retired core's Python
  answers as the reference. Its open question 1 asked what then proves an operation ready for core to switch to it.
  [#9](https://github.com/spec-kitty/spec-kitty-charter/issues/9),
  [#72](https://github.com/spec-kitty/spec-kitty-charter/issues/72) and #74 were held on it.
- **The cascade.** ADR-0013 D4 and ADR-0020 D6 fix the cascade's stop at a deny entry and its report buckets, but
  name no relations. Core walks five relations (`requires`, `suggests`, `refines`, `scope`, `instantiates`)
  with no depth limit. Core also declares `enhances`, `overrides` and `specializes_from`, but its cascade does not
  follow them. The pack ecosystem uses them, and adds pack-qualified references (ADR-0048 to ADR-0054).
- **The layer override.** ADR-0053 d6 ruled that across levels the same id is a legitimate layer override,
  applied project over org over built-in, with no edge needed. It did not say whether the override takes the
  place of the whole artifact or only of the fields it declares. Core's ADR `3.x/2026-05-16-1` chose a field
  merge with a collision warning; this repository's glossary says a layer override "takes the place of" the
  artifact.
- **The activation writes.** ADR-0020 open questions 4 to 7 must be ruled before #291 builds the writes.

## Decision

### D1. The design's BDD scenarios and contract tests gate each core switch (ruled)

An operation is ready for core to switch to it when its design-derived BDD scenarios and its contract tests pass
against the service. The scenarios are the `@contract` scenarios in `contracts/charter/scenarios/`, accepted against
the hand-authored and fixture packs of ADR-0055 D3; the contract tests are those `./gradlew verifyAll` runs. An operation needs at least one behavioral `@contract`
scenario: a `@reserved` scenario that pins a `501` does not count toward the gate, so a reserved operation is not
ready. Nothing is run against core's Python answers as a gate:

- the conformance corpus over core's CLI ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)) is not
  a gate;
- no shadow comparison and no observation period gate a switch. Core's shadow route
  ([#14](https://github.com/spec-kitty/spec-kitty-charter/issues/14)) may still report differences as diagnostics.

Where the service's answer differs from core's on purpose, the
[divergence register](/conformance/divergence-register/) records it, so core knows what changes when it switches. The
register informs; it does not gate.

**Why.** ADR-0055 D3: core's Python implements the model the design replaces. A gate that compares against it would
pin the service to that model.

**What follows.** #72 (conformance as a blocking gate) and #9 (the corpus harness) are re-scoped or closed, and #14
loses its gating role. Those changes are follow-ups, not part of this record.

*Amended 2026-11-12:* what passes is stated per declared answer, and the verdict per operation is the register
`contracts/charter/scenarios/switch-gate.yaml`, checked by the contract suite; see the
[amendment](#amendment-2026-11-12-the-switch-gate-register).

### D2. The cascade follows five relations and `enhances` (ruled)

1. **Five relations, no depth limit.** The activation cascade walks `requires`, `suggests`, `refines`, `scope`
   and `instantiates` forward, as core does. It stops at a denied artifact and reports it (ADR-0013 D4). Each
   artifact is visited once (the ADR-0025 visited-URN guard), so a cycle through `enhances` ends.
   *Superseded 2026-11-06 (A4):* the cascade walks six relations, these five and `enhances`, all forward at write
   time; the visited guard ends a cycle through any of them, and there is no read-time pull for a cycle to run
   through. See the [amendment](#amendment-2026-11-06).
   *Amended 2026-11-07 (#408, decision `DM-01M6TDH0SY5Q224GX709DXWG8K`):* "no depth limit" stays the default, and a
   cascading select or deactivate may now send `depth`, which counts `suggests` hops only; see the
   [amendment](#amendment-2026-11-07).
2. **`enhances`, both ways, at read.** *Superseded 2026-11-06 (A4):* `enhances` is a cascade relation like the
   others, walked at write time in its declared direction, and there is no read-time pull; see the
   [amendment](#amendment-2026-11-06). The rest of this point is kept as the record of the 2026-11-05 ruling. The edge runs from the enhancing artifact to its target, and the pull follows it in
   both directions. When a target comes into force, every artifact that enhances it joins and is walked in turn. When
   an enhancer comes into force, its target joins and is walked in turn. Any route into the effective set triggers the
   pull: selection, the cascade, a kind default, and the required or locked floor.
   - **Timing.** The pull expands the effective set when a read runs. It does not write to `selected`, so `selected`
     stays what the client chose, and an enhancer a pack upgrade adds joins on the next read. Two narrow exceptions
     touch `enhances` at write time, both without writing to `selected`: a write with `includeDeactivated` walks
     `enhances` to clear the deny entries the pull would reach, and a cascading deactivate counts an `enhances` edge
     as a reference when it decides what is shared. Clearing a deny entry is the only thing the write's `enhances`
     walk does.
   - **Scope.** At read, the pull is bounded by the document's `activatedKinds`. It never puts in force an artifact of
     a kind `activatedKinds` leaves out, or one the language filter excludes; those keep their reason. For reads,
     `skippedByScope` means outside `activatedKinds`, and the #291 contract change separates it from the
     `kind_not_activated` reason. `cascade` remains a write parameter and scopes the five-relation walk of a write.
   - **Deny.** A denied artifact is skipped and reported, as any denied artifact is. An enhancer whose target is
     denied stays in force and adds nothing; the report names the target. A target whose enhancer is denied stays in
     force without the addition. What lies only behind the denied artifact goes in `unreachedBehindDeactivated`.
   - **Reporting.** `SkippedDeactivated.requiredBy` is an array that lists the artifacts whose pull reached the
     skipped artifact, on either direction of the edge. The visited guard of rule 1 stops a second walk, but it must
     not drop a later artifact that also reaches it.
   - **Cascading deactivate.** A cascading deactivate does not walk `enhances` to remove anything, and it records a
     deny entry only for the named id. It removes from `selected` only what the five relations reach, less what is
     shared. An artifact an active enhancer still references counts as shared: it is kept and reported in
     `skippedShared`.
   - **Step targets.** A step target (`action:<type>/<step>`, ADR-0053 d22) is not put in force and not reported. An
     enhancer of a step applies when its mission type is in force, and a mission type in force pulls its step
     enhancers. Nothing the pull does adds to `missionTypes`.
     *Amended 2026-11-07 (A7 Q1, decision `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`):* a mission type is an ordinary kind, so
     selecting one with a cascade crosses to its steps like `requires`, and rule W32 ("a mission type never cascades")
     is withdrawn; there is no `missionTypes` list to add to. See the [amendment](#amendment-2026-11-07).
   - **Provenance.** A new `EffectiveArtifactReason` value names the `enhances` pull, in the #291 contract change. It
     applies only to an artifact in force through the pull alone; an artifact in force by another route keeps that
     route's reason.
3. **Pack-qualified references are followed.** A `<pack-name>::<urn>` target is resolved in the compose and walked
   under the same relations (ADR-0053 d7).
4. **`specializes_from` is walked as lineage, not as a candidate.** The walk passes through a profile's parent to
   reach what the parent `requires` or `scope`s, and only those two relations, up the whole chain of ancestors. No
   ancestor is put in force. A denied ancestor stops the pass-through at that ancestor and is reported (ADR-0013 D4);
   what lies behind a denied ancestor goes in `unreachedBehindDeactivated`. The `cascade` scope does not filter an
   ancestor, which is not a candidate.
   *Amended 2026-11-06 (A2, A3):* a cascading deactivate's own walk passes through the lineage as a select's walk
   does, but stops at no deny entry, so it crosses a denied ancestor a select would stop at; and with
   `includeDeactivated` a denied ancestor is passed through only when its kind is in the `cascade` scope; see the
   [amendment](#amendment-2026-11-06).
5. **No other edge is followed.** `overrides` and `replaces` are replacement edges: following them would bring the
   replaced original back into force. `rejects` points at an anti-pattern (ADR-0052). `delegates_to` is a runtime
   handoff. `vocabulary`, `applies`, `reconciles_tension`, the tension edges and the derived `implemented_by` are
   not followed either, as in core. Pack-level
   `depends_on` is not an artifact edge: activation stays per artifact (ADR-0048).

**Why.** The five relations keep core's reach, so a project activates what it activated before. In this repository's
[logical model](/architecture/logical-model/#relations) each of them points from an artifact to what it needs in
force with it: a hard dependency, a recommendation, the target it narrows, what it puts in force within its scope,
or its template. `enhances` keeps an enhancing artifact and the artifact it adds to in force together, unless one is denied. The other edges
either replace content or point at what must not be activated.
*Superseded 2026-11-06 (A4):* `enhances` is walked forward like the five, so a cascade from an enhancer puts its
target in force, and a target puts none of its enhancers in force; see the [amendment](#amendment-2026-11-06).

### D3. A layer override takes the place of the whole artifact (ruled)

When the same artifact id is in force at more than one level, the artifact from the level that applies (project
over org over built-in) takes the place of the other one whole, where the owning pack's level allows it
([ADR-0012](/adr/0012-pack-mutability-policy/) and ADR-0053 d6). Fields it does not declare are not inherited.
A pack adds to an artifact without replacing it through an explicit `enhances` edge. The service reports each layer
override in its diagnostics, in place of core's collision warning.

Graph-shard edges declared at a level nearer built-in follow the same rule. An edge that starts at the overridden id
is dropped with the artifact it came from. An edge that ends at the overridden id still applies and resolves to the
overriding artifact, so what depends on the id stays connected.

This differs from core's field merge (core ADR `3.x/2026-05-16-1`). The divergence register records the
difference.

**Why.** The overriding artifact says everything it takes from no lower level, with no partial-field surprises; an `enhances` edge into the id still adds to it, and that enhancer joins under D2.2.
*Superseded 2026-11-06 (A4):* the enhancer does not join because the id is in force; a read returns it with the id
only when it is itself active. See the [amendment](#amendment-2026-11-06).

### D4. A stored document a pack upgrade made stale is served and flagged (ruled; ADR-0020 question 5)

- **On read.** The service answers from the stored document as it stands. An id the offering no longer has is left
  out and listed in the skipped report. A deny entry on an artifact that is now required or locked is not applied
  (ADR-0013 D2), and the skipped report lists that too.
- **On write.** A `PUT` that still carries an unknown id is refused `422 activation_invalid`, and one that denies a
  required or locked artifact `409` (ADR-0020 D9). The client cleans the document up before it writes it back.
  *Amended 2026-11-06 (A5):* a per-artifact or preset write drops the stale ids itself and reports them; see the
  [amendment](#amendment-2026-11-06).
  *Amended 2026-11-07 (ruleset A4 and B1):* a `PUT` drops them too, and a pack version pin no pack of the closure holds
  is dropped and reported by every write but a create; see the [amendment](#amendment-2026-11-07).
  *Amended 2026-11-08 (DIV-233):* "served unchanged" holds only while the closure can be read. `getActivation` also
  answers `500` for a document that names a pack that is no longer configured, for a closure whose layer merge refuses
  and for an in-force refusal, and then serves no document; see the [amendment](#amendment-2026-11-08).
  *Amended 2026-11-09 (R6, operator ruling Q9):* a deny entry on a required, not locked artifact is accepted when an
  active override of it satisfies the requirement in the document as it will be stored; see the
  [amendment R6](#amendment-2026-11-09-r6).

ADR-0020 D6's round trip ("a `PUT` of a fetched `config` answers `200` with the same `ETag`") holds for a document
the offering has not made stale; ADR-0020 D6 carries a note.

### D5. The `Activation` answer's `ETag` is its revision, and the reads declare `304` (ruled; ADR-0020 questions 4 and 7)

- `summary` moves out of the `Activation` answer to an effective read, because it follows the offering. Which
  effective read carries it is settled with the contract change in #291. `governanceFingerprint` leaves
  `Activation`; the effective reads already carry it (it follows the offering, so the strong `ETag` would not otherwise
  match the bytes). The answer's bytes then depend only on the stored document, and its strong `ETag`
  is the revision. *Amended 2026-11-08: `getActivation` carries `denyNotApplied`, which depends on the packs, so its
  strong `ETag` covers the revision and the closure's `governanceFingerprint`. See the
  [amendment](#amendment-2026-11-08).*
- `getActivation` and the four effective reads, `listEffectiveArtifacts`, `getActivationSkippedReport`,
  `getEffectiveGraph` and `getEffectiveActionContext`, are to declare `If-None-Match` and `304 Not Modified` in the
  contract change; today only `getOpenApiDocument` declares them. An effective read's `ETag` covers the revision and
  the `governanceFingerprint`, because its answer depends on both.

**Why.** A strong `ETag` must change with the bytes (RFC 9110). A success status added after 1.0.0 is breaking
(ADR-0020 D8).

### D6. A preset replaces each key it lists (ruled; ADR-0020 question 6)

`applyActivationPreset` replaces each key the preset lists, `activatedKinds` and `missionTypes` (or its [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) successor) included, and
`ActivationChange.applied` reports them. Keys the preset does not list are left as they are. `force` clears the deny
entries that match the preset's members (ADR-0020 D6). This is core's behavior.

*Amended 2026-11-07 (A7 Q3 and Q4, decisions `DM-01M6RCY40CQQTHC130RWF43W0D`, `DM-01M6RCY8MYNGNPJ247M2Y4880N`):* the
document has no `activatedKinds` or `missionTypes` to replace. A preset replaces each kind list it names, any kind
included, and `activated_mission_types` is applied as `selected.mission_type`; a preset sets no kind gate. See the
[amendment](#amendment-2026-11-07).

## Amendment 2026-11-06

**Operator rulings of 2026-11-06** on the activation writes
([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)). They amend D2 and D4 as stated; the rest of
this record stands.

- **A4. `enhances` is a relation like the others; no special case.** It supersedes D2.2.
  - *At write.* The cascade walks `enhances` in its declared direction, from the enhancer to its target (`E enhances
    T` runs from E to T), exactly as it walks `requires` and `scope`. The relation set is core's five and `enhances`.
    Selecting E with a cascade selects T; selecting T selects nothing more; deactivating E with a cascade unselects T
    unless another active artifact needs it, which the shared walk finds over `enhances` like any other relation.
    The write-time special cases are gone: `includeDeactivated` no longer clears what an `enhances` pull reaches, and
    a cascading deactivate no longer counts a one-hop enhancer as a reference of its own.
  - *At read* (the effective reads, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103), not built
    yet). An enhancement is returned only when it is itself active. `E enhances T` also reads "T is enhanced by E",
    so resolving T returns E when E is active. There is no automatic read-time pull: an enhancer does not come into
    force because its target does, nor a target because its enhancer does. The `EffectiveArtifactReason` value for
    the pull that the D2.2 follow-up asked for is not needed.
- **A3. `includeDeactivated` clears only within the cascade's scope.** A select with `includeDeactivated` clears
  only the deny entries its walk passes through within the `cascade` scope: what it reaches of a kind in scope, each
  lineage ancestor it passes through whose kind is in scope, and the target. A denied artifact outside the scope is
  treated as without `includeDeactivated`: the walk stops there and reports it in `skippedDeactivated`, with what lies
  only behind it in `unreachedBehindDeactivated`, and its deny entry stays. A denied ancestor of a kind outside the
  scope stops the lineage the same way. This reverses the reading of 2026-11-06 (squad finding F4) that cleared the
  out-of-scope deny entries the walk went through.
- **A5. Stale ids are dropped on write.** Every per-artifact and preset write (select, unselect, deactivate,
  re-activate, apply a preset) first removes from the stored document every id the activation's closure no longer
  declares, in `selected`, `deactivated` and `missionTypes`, instead of answering `422` until a full `PUT`. The write
  reports them in a new optional `ActivationChange.dropped`, so the drop is never silent, and a dropped id is a
  change: a write that only drops stale ids is not a no-op and gets a new revision. `PUT` and create are unchanged:
  an unknown id in a body the client sent is still `422`. An unknown pack or a pinned version in `packs` is not
  dropped either: such a document still makes every write `422` (`unknown_pack`, `version_not_held`), which only a
  full `PUT` recovers. `applied` and `cleared` are measured after the drop, so a dropped id appears only in
  `dropped`. Any write removes the ids the packs no longer declare, so an unselect or a re-activate whose path names
  a stale id still in the stored list it removes from (`selected` or `missionTypes`, `deactivated`) proceeds: the id
  is dropped with the others, reported in `dropped`, and the write gets a new revision. A select or a deactivate of
  an id the closure no longer holds, and an unselect or a re-activate of an id in neither the closure nor that stored
  list, is still `404 artifact_not_found` or `mission_type_not_found` (FR-010) and drops nothing. This answers the open review item on stale documents (research item 7).
- **A2. A cascading deactivate follows the lineage.** The target's own walk passes through every
  `specializes_from` ancestor to what it `requires` or `scope`s, as a select's walk does (D2.4), so selecting a
  profile with a cascade and then deactivating it with a cascade round-trips: what the select reached through an
  ancestor leaves `selected` again. Unlike a select's walk, it stops at no deny entry: it crosses a denied ancestor
  that a select would stop at, as it already crosses a denied artifact on the forward relations.
  The shared-reference rule still applies: an artifact another active artifact needs is kept and reported in
  `skippedShared`. This replaces the earlier reading of FR-015 ("the five relations" only), which left the
  deactivate's walk without the lineage.

## Amendment 2026-11-07

**Rulings of 2026-11-06 and 2026-11-07, built by PR A0** of the layer-merge mission
(`layer-merge-and-effective-reads-01M6R0J6`). They amend D2, D4 and D6 as stated; the rest of this record stands.
Each item names its decision id. The labels `A4`, `B1`, `B5`, `A7` and `Q1` to `Q4` below are the ruleset review's
(`research/ruleset-review-2026-11-06.md` of that mission), not the A-numbers of the amendment above: ruleset A4 is the
replace round trip, where A4 above is `enhances`. The contract changes are the `2026-11-07-a0-*.md` fragments under
`contracts/charter/changes/`, and the divergences are DIV-209 to DIV-215 of the
[divergence register](/conformance/divergence-register/).

- **B5. `includeDeactivated` clears only what the walk reaches and selects.** Decision
  `DM-01M6RA9XQSH3E07C7VCPCVD6R4`. It refines A3 above. A lineage ancestor the walk passes through, which it never
  selects, keeps its deny entry: clearing it would lift a deny the unselect never restores, and a cascading select has
  no inverse that puts it back. The target and every artifact the walk reaches within the `cascade` scope are still
  cleared, and a denied artifact outside the scope still stops the walk and keeps its entry. DIV-206 carries the
  change.
- **Ruleset A4. A replace is judged against the document it replaces.** Decision `DM-01M6RA7XC5HC8915GZT2R3H83W`. It refines
  D4's write rule. `replaceActivation` judges the floor only on the deny entries it adds, so a deny entry the replaced
  document already held is kept when a pack now requires or locks it, and a `GET` then `PUT` of one body is never
  refused without a pack change. It also drops what the packs no longer hold: an id of the body that the closure no
  longer holds and that the replaced document named is dropped and listed in the answer's `dropped`, where it was
  `422 unknown_artifact`. An unknown id the replaced document did not name is still `422`, and so is any unknown id
  in a create. A replace without `If-Match` is judged again from a fresh read when another writer changes the
  activation in between, up to eight times, and answers `412` with nothing stored when every attempt loses. DIV-209.
- **Ruleset B1. A write drops a version pin no pack holds.** Decisions `DM-01M6RA9BKJY7X9BFV93QW1KM1T`,
  `DM-01M6RN5Y406SB4KA13QYMQ2P8J`. It refines A5 above, which left a pin to `422 version_not_held` for a full `PUT`
  to recover. `replaceActivation`, the four per-artifact writes and `applyActivationPreset` drop a pin no pack of the
  closure holds and report it in `droppedPins` (`ActivationChange.droppedPins`, and `Activation.droppedPins` on a
  replace answer), so a pack upgrade never leaves an activation that only a full `PUT` can repair. `createActivation`
  keeps `422 version_not_held` for a pin in its own body, an input error. The reads' half (`pinNotHeld`) is built
  in PR B (2026-11-08). DIV-209.
- **A7. One kind model, Q1 to Q4.** Decisions `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S` (Q1),
  `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC` (Q2), `DM-01M6RCY40CQQTHC130RWF43W0D` (Q3), `DM-01M6RCY8MYNGNPJ247M2Y4880N` (Q4).
  It supersedes the kind gate that D2.2 bounded the pull by, and refines D2 and D6.
  - The document is `packs`, `selected`, `deactivated` and `languages`. Mission types, steps and agent profiles are
    ordinary kinds of it: nothing is in force because its kind is "activated", and a select brings its artifact into
    force. `activatedKinds` and `missionTypes` are refused on a create or replace, `422 validation_failed` with one violation
    per member (`removed_member`) naming its replacement; there is no deprecation window (Q4). DIV-212.
  - **The mission-type cascade (Q1; D2.2 step targets, W32 withdrawn).** Selecting a mission type with a cascade
    crosses to the steps it holds like `requires` and selects what they reach, and a cascading deactivate of one
    unselects what only it kept. `CascadeKind` gains `mission_type`. PR A0 closes #410 once a probe over the pinned
    built-in pack confirms that `mission_type/software-dev` with `{"cascade": ["all"]}` selects its steps' governance.
    A mission type's steps come from the id hierarchy (`action:<M>/<step>`), structurally: that containment is neither
    an authored nor a derived edge, so the cascade to steps does not contradict
    [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 7. DIV-214.
  - **Deny (Q2).** A mission type is deniable like any kind unless it is required or locked (`409 artifact_required`
    or `artifact_locked`); an unknown one on the exclusions path is `404 mission_type_not_found`. DIV-214.
  - **Presets (Q3; D6).** A preset may name any kind, `skill` and `glossary_pack` included, and carries no
    `activated_kinds`: a preset that does refuses the pack as `500 pack_unreadable`, as the preset item id grammar does; a `422` on registration is planned for PR A. DIV-211.
  - **Storage.** `schemaVersion` is `2`. A stored version 1 document is answered as its upgrade (`missionTypes` become
    `selected.mission_type`, every selection is kept, `activatedKinds` is discarded; decision
    `DM-01M6RMJHDKNTXAM055Q8WR6SDN`) and stored as version 2 on the next write (decision `DM-01M6RFGMDH859CJP3RHHF9YC3H`),
    so every revision and `ETag` changes once. DIV-213.
  - **Reads.** The reasons `kind_not_activated` and `kind_default` went with the effective reads of PR B (FR-022;
    built 2026-11-08, DIV-229).
- **#408 ruling 1. The write cascade gains `depth`.** Decisions `DM-01M6TDH0SY5Q224GX709DXWG8K`,
  `DM-01M6TF3BA808JMW0W0A0CPXBNW`; built by WP30. It amends D2.1: the cascade keeps its relations with no limit by
  default, and `SelectionRequest` and `ExclusionRequest` take an optional `depth` for iterative deepening.
  - *Meaning.* `depth` counts `suggests` hops only, as ADR-0018 D4's `depth` does on the action-context walk. Hard
    relations (`requires` and the others the cascade crosses), `enhances`, the `specializes_from` pass-through and a
    mission type's steps are walked in full, because truncating a hard relation would select an artifact whose
    requirements are not selected. `depth: 0` follows no `suggests` edge; an absent `depth` is unlimited, as in core.
  - *The walk.* It is layered by `suggests` hops (a 0-1 breadth-first search): each place is expanded once, in its
    lowest layer, and pass-through and step places inherit the hop count of the place that produced them. The bound
    applies before entering a place, and in the walk behind a stop. An artifact reached over `requires` is selected at
    any depth, even when a `suggests` path also reaches it.
  - *Deactivate.* A cascading deactivate honours `depth` in the target's own walk. The shared walk, which decides what
    other active artifacts keep, is unlimited, so a kept candidate is never dropped by the bound.
  - *Report.* `SkippedReport.skippedByDepth` lists the `suggests` targets one hop past the bound, present only when a
    cascade and a `depth` were sent, so a client knows whether a deeper `depth` would reach more.
  - *Refusals.* A `depth` that is not an integer is `422 activation_invalid` with the violation code `unreadable_body`
    at `/depth`; a negative one is `below_minimum` and one past the int32 range `above_maximum`. The last two are new
    violation codes. `depth` without `cascade` is accepted and does nothing. An older server answers
    `422 unknown_member` at `/depth`.
  - *Surprises.* A `suggests` edge is never crossed through the lineage. An `enhances` edge after a `suggests` hop costs
    nothing. Unlike ADR-0018 D4's read `depth` (default 2, maximum 2), the write `depth` has no default and no maximum.
  - *The round trip.* A cascading deactivate at depth N undoes a select at depth N only in what it removes. When
    another active artifact reaches a candidate over `suggests`, the candidate is kept and reported in `skippedShared`:
    with `O suggests H` and `T requires H`, a select of `T` and a deactivate of `T` leave `H` in `skippedShared` with
    `referencedBy` `O`. DIV-215: core has no write-cascade depth.
- **#408 rulings 2 and 3, planned.** The language filter applies at read only: the activation document stays
  language-neutral, and a read with no activation applies no filtering (FR-013 and FR-017, PR B). A `suggests` edge's
  `when` surfaces as conditional in the effective reads, above all the effective action context: an artifact reached
  only through `suggests` is listed as a suggestion carrying its `when` and `reason`, while one reached through
  `requires` and the other hard relations stays a plain entry (FR-013 and FR-019, PR B and C). *Built on 2026-11-08
  for the effective artifact list (PR B); the action context's half is PR C.*

## Amendment 2026-11-08

**Two operator decisions of 2026-11-08 on the layer-merge mission (`layer-merge-and-effective-reads-01M6R0J6`), built by
its PR B.** They amend D5 as stated; the rest of this record stands. The contract change is the `### Breaking` block of
`contracts/charter/changes/2026-11-07-b-effective-reads.md`, and the divergences are DIV-231, DIV-233 and DIV-234 of the
[divergence register](/conformance/divergence-register/).

- **D5. `getActivation`'s `ETag` covers the closure** (decision `01M6Y11E55YP5TG1EJ1MDKCFRP`). `Activation` keeps the
  `denyNotApplied` of the plan (a stored deny entry the required floor or a lock overrides, judged against the packs of
  the closure). The answer's bytes then depend on the packs as well as the stored document, so its strong `ETag` is no
  longer the quoted revision: it is the tag of the revision and the closure's `governanceFingerprint`, the digest of
  the packs of the activation's closure, as an effective read's tag is. It changes when a pack of the closure changes,
  and not when a pack outside it does. `getActivation` can answer `500` with a composition code or `pack_unreadable`
  (also for a document that names a pack no longer configured, which D4's "served unchanged" no longer covers), and
  still sends no `Charter-Source-Fingerprint`. `summary` stays out of `Activation`. The effective reads' tags
  follow D5's first rule and are per representation: `kind`, `state`, the cursor and the page size are part of the
  digest's input, and the digest is the closure's, no longer `ServiceInfo.sourceFingerprint`.
- **`If-Match` takes either tag** (decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`; [ADR-0020](/adr/0020-offering-and-activations/)
  D6 carries the note). An activation write's `If-Match` is `*`, the quoted revision or the current `getActivation`
  tag, and a closure tag that no longer matches is `412`. A write's answer keeps the revision as its `ETag` and carries
  no `denyNotApplied`.

## Amendment 2026-11-09 (R6)

**The write rule of operator ruling Q9, and the read rules of research R11 that amend D2 and D3, built by PR C of the
layer-merge mission (`layer-merge-and-effective-reads-01M6R0J6`).** The labels `R6`, `R11` and `R12` are that mission's
research items (`research.md`), not the A-numbers of the amendments above; `R11` carries the operator rulings Q6 and Q8
of 2026-11-06 and `R12` carries Q9. The contract change is
`contracts/charter/changes/2026-11-07-c-deny-satisfied-by-override.md` (`### Breaking`, decision `01M70NGH42RWKBMZ6MN2D6QHNY`: #399's
shipped write answers change), the divergence is DIV-204 (updated) with DIV-239 to DIV-244, and the rules are W26, E5 to E6 and E12 to E16 of
[Composition and activation rules](/architecture/composition-and-activation-rules/). ADR-0065 is about pack
registration and is not the place.

- **D4. A deny entry a lock refuses and a requirement an override satisfies** (decision `DM-01M6R3YX23H46PXGNEFC31E6CT`,
  operator, Q9: relax it, for an override only, and assuming `T` is not locked, since overrides are not permitted on locked artifacts). D4's
  "one that denies a required or locked artifact `409`" holds for a locked artifact, and for a required one unless an
  active override of it satisfies the requirement.
  - The test is the in-force resolution's, over the document as it will be stored after the write: the required `T` is
    satisfied when an artifact in force overrides it, directly or through a chain, and "in force" is the read's
    (selected or required, within the language filter, bound to a mission type in force, not denied, not hidden by an
    override). An enhancement never satisfies. A locked `T` is refused `409 artifact_locked` whatever overrides it.
  - The write and the read cannot disagree, with one exception: the write asks the same `InForceResolution` the reads
    share, through the `PacksSession` of its request, and a deny entry it accepts is one the next read applies. The
    exception is an overrider of a non-activatable kind (a template, a guard, a lifecycle): the read shows the required
    artifact hidden by it, and the write finds no satisfier, so it refuses the deny entry
    ([#447](https://github.com/spec-kitty/spec-kitty-charter/issues/447)).
  - Scope, as DIV-204: a create judges every deny entry of its body; a replace judges only the deny entries it adds
    (A4 of the amendment of 2026-11-07); a deactivate judges the named target whether or not its deny entry is already
    stored; a preset applied with `force` and a select with `includeDeactivated` add no deny entry and judge none. A
    replace that adds the override and the deny entry together is accepted.
  - Removing the override later is not refused. The stored deny entry is then not applied: the read puts `T` back in
    force and lists the entry in `denyNotApplied` with the reason `required`, and only a write that adds the entry, or
    deactivates its target again, judges it.
  - The floor judgement asks the resolution only for a deny entry on a required, not locked artifact. The per-artifact
    writes also ask it whenever they apply anything (DIV-230), so a closure whose layer merge refuses (D3,
    `500 pack_unreadable`) reaches them as well. `CascadeRules` is unchanged: a
    floor artifact is never a cascading-deactivate candidate.
  - ADR-0013 D2 and ADR-0020 D7 say a required artifact is "never subtracted" and "active whatever the document says".
    The rulings of 2026-11-06 let an active override hide a required artifact, so those texts read as this amendment
    qualifies them: a required artifact is out of force exactly while an artifact in force overrides it.
- **D2 and D3 at read: what R11 rules** (operator rulings Q6 and Q8, decision `DM-01M6R3YTNT1P2MBERBN7WV71ND`).
  - **Required is a requirement.** A governing preset's must-have item is satisfied by itself in force or by an artifact
    in force that overrides it (D3: an `overrides` edge of the merged closure hides its target at read, as a layer
    override replaces it whole); otherwise it is put in force. A locked artifact is always in force and no override is
    applied to it; a deny entry on it is not applied.
  - **An enhancer needs its target** (D2.2, superseded by A4 for the pull; the read side stays): an artifact that
    `enhances` is in force only while a target of its enhancement is; a ring holds nothing up. A step enhancer's target is
    a step, which is never in force, so it is grounded by its mission type: exactly when the mission type of its single
    `enhances` target is in force (OD-6, decision `01M702A8XYB588PSGTXRKQ4NKK`), and pruned otherwise, an unheld target
    step included.
  - **The effective graph and the action context** (D3 and ADR-0018): the graph is the merged edges among the nodes in
    force, resolved through the layer overrides, with the overrides applied; its nodes are the artifacts in force and the
    held steps of the mission types in force (decisions `01M702A71YF7WNDTG2AA89K8ZA`, `01M7037ZNKJ6JTRKGD7G695ZV1`). The
    context is ADR-0018's walk from the held step over the artifacts in force, with the enhancers in force of each node
    read from the node's side (`enhancements`, decision `01M70C42KC78KB9H6KB7TK9224`) and the suggestions the listing
    gives (decisions `01M6Y6VE6VZQ4MXD98N93JB3GJ`, `01M7059T4K64TAVKXN3T1FKWYG`, `01M70CTZP7P2EHSCK2G0RET74B`). The context's
    `depth` counts `suggests` hops as D4 of ADR-0018 says; the contract allows 0 and 1.
  - A step is held by a pack that defines its mission type and declares it, and by the closure when some pack holds it
    (decision `01M7037V93Y077H8M4W9V8Y6B3`), so the cascade of D2.1 follows only held steps.
- **Floor of activatable kinds** (D16 of the PR C plan): the policy and preset schemas cannot name a step, so the
  required floor holds activatable kinds only and `requiredNotInForce` never names a step.

## Amendment 2026-11-12: the switch-gate register

**D1 made checkable, by the switch-gate mission (`switch-gate-coverage-01M77A7P`; issues #9 and #72).** D1 asks for at
least one behavioral `@contract` scenario and passing contract tests. Issue #9 asked for positive, refusal and
provenance cases where the operation has them. The mission makes both one rule the build checks:

- **Ready means every declared answer is asserted on the operation.** An operation is ready when every status its
  responses declare by number (not `default`) is asserted on that operation by a `@contract` scenario, and every
  exchange of those scenarios passes the contract checks `./gradlew verifyAll` runs on it: the schema, the links (R-20)
  and the fingerprint rule. A success answer counts from any exchange; any other answer counts only where a step asserts
  its status, so a setup request's incidental refusal covers nothing. There are no shared statuses: the worktree
  guard's 401 and 403 are asserted on every operation (`every-operation.feature`). Provenance is the fingerprint rule,
  which the suite checks on every `GET` (ADR-0018 D11). This is stricter than D1's "at least one behavioral scenario",
  and it includes D1's bar: a ready operation has its success answer exercised.
- **The register is the published verdict.** `contracts/charter/scenarios/switch-gate.yaml` holds one entry per
  operation, `ready` or `held` with the declared `statuses` it is held for and a reason. The suite fails when an
  operation has no entry or an entry names none (fail closed), when a ready operation has an answer no scenario
  asserts, when a held one would pass or the answers no scenario asserts are not exactly the statuses it names (a new gap
  cannot hide behind an old entry), and when a reserved operation is ready. The register ships in `charter-scenarios.tar.gz` with the contract release; a change to
  its keys, or an operation moved from ready to held, is a `### Breaking` change (ADR-0022).
- **Ready is necessary, not sufficient.** The first switch still needs contract 1.0.0 (ADR-0022 D6).
- **What is held, and why.** `deleteActivation` and `getPackRegistration` declare a `500` that only a failure of the
  activation storage or of the registration job table raises; both are kept in memory and no seam lets a scenario fail them. They
  stay held until a seam can fail that storage; the other 44 operations are ready. Each held entry names the
  `statuses` it is held for (here `[500]`), so a held operation that gains another gap fails the suite.
- **Not yet enforced.** Nothing compares the register with the previous release, so "an operation moved from ready to
  held is a `### Breaking` change" is a release-review responsibility: the reviewer of a contract release diffs
  `switch-gate.yaml` against the last tag and records the change. A check of that diff is not built.
- **Core's half.** Refusing a switch the register does not hold ready is core's proxy (ADR-0021 D5), built at
  incorporation; it is recorded in the [work breakdown](/plans/pack-ecosystem-work-breakdown/), not filed here
  (issue-tracker Rule 5).

## Consequences

### Positive

- Each operation has one readiness test, with a real behavior behind it, which this repository owns and runs on every pull request.
- The cascade, the merge and the writes can be built without waiting on further rulings, once the contract
  changes below land.

### Negative

- Core switches without proof that the service agrees with its Python answers. Every deliberate difference must
  be in the divergence register before core switches, or core finds it in use.
- *Superseded 2026-11-06 (A4):* a selected enhancer brought its target, the target's other enhancers and whatever
  those cascade to, within `activatedKinds`. With A4 a cascading select of an enhancer selects its target and what
  that cascades to, into `selected`, where unselecting or a cascading deactivate removes it again.
- D3, D2.2 and D2.4 depart from core. A project with a partial override, with an artifact that enhances another, in either direction,
  or with a profile whose parent `requires` or `scope`s more, sees different content after the switch. Core's
  cascade excludes lineage, so it does not pass through `specializes_from`.
  *Superseded in part 2026-11-06 (A4):* the `enhances` departure is one direction only: core's cascade does not
  follow `enhances`, and this one walks it forward, from the enhancer to its target.
- D3, D4 and D5 change the contract. D5: `summary` and `governanceFingerprint` leave `Activation`, and `304` is
  added to five operations. D4: the skipped report, closed with four buckets in
  `contracts/charter/schemas/SkippedReport.yaml`, has no bucket for a dropped id or an unapplied deny entry. D3: no
  `DiagnosticCode` value reports a layer override (today only `pack_level_missing` and `pack_unknown_key`). D2.2: no
  `EffectiveArtifactReason` value names the `enhances` pull, and the description and `x-derived` rule of
  `SkippedDeactivated.requiredBy` assume a forward edge and a single reacher. `skippedByScope` also mixes the
  `cascade` scope with `kind_not_activated`.
  *Superseded 2026-11-06 (A4):* the D2.2 point is moot: there is no `enhances` pull, so no `EffectiveArtifactReason`
  value names one, and every edge the cascade walks is forward. See the [amendment](#amendment-2026-11-06).

## Follow-ups

- Re-scope or close #9 and #72; drop the gating role from #14.
- Drop #72 and #14 from #74's dependencies; #74 is held on the old gate.
- Ask core to amend its ADR `2026-10-03-1`, which still names the corpus as the only gate.
- Update the gate wording that still has the corpus or #72 gate consult the register or decide behavior:
  `docs/conformance/divergence-register.md` (the conformance gate paragraphs),
  `docs/conformance/README.md` (the corpus as what holds the service to core),
  `contracts/charter/scenarios/README.md` (`@provisional`, decided by the corpus) and
  [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/) D4.
- Add the divergence-register entries for D2.2, D2.4, D3 and D4. *D3: DIV-216 to DIV-220 (PR B, 2026-11-08).*
- Change the contract for D5 (`summary` and `governanceFingerprint` leave `Activation`; `If-None-Match` and `304` on
  five operations) and the round-trip text of `contracts/charter/paths/activations_activationId.yaml` for D4.
- Add the skipped-report bucket or buckets for D4 and a `DiagnosticCode` value for D3's layer-override report, as
  0.x contract changes under [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) in the #291 contract
  change. *Done in PR B (2026-11-08) for the buckets: the skipped report has ten new typed ones (DIV-228). D3's report
  is the typed `LayerOverride` list that the effective graph carries (PR C), not a `DiagnosticCode` value.*
- Add the `EffectiveArtifactReason` value for D2.2 (no longer needed: A4 of the 2026-11-06 amendment removes the
  read-time pull) and widen both the description and the `x-derived` rule of
  `SkippedDeactivated.requiredBy`, as 0.x contract changes in the #291 contract change. The same change widens
  `skippedByScope`'s rule to mean outside `activatedKinds` at read and separates it from `kind_not_activated`.
  *Done in PR B (2026-11-08), as built: with the kind gate gone, `skippedByScope` is always empty at read, and
  `skippedDeactivated` has `viaRequires` false and `requiredBy` empty at read (DIV-228, DIV-229).*
- State in the #291 contract change whether `ActivationChange.skipped` includes what the read-time pull skips (moot:
  A4 of the 2026-11-06 amendment removes the read-time pull).
- Have `getEffectiveGraph` carry the layer-override report in the #291 contract change. `Diagnostic` today describes
  one pack's load, so the contract change either widens its description or adds a schema that carries the report.
- Update `docs/architecture/offering-and-activation.md` for D2.2, D4 and D5 (the `Activation` example and canonical
  form, and the cascade text). *D5 corrected for the amendment of 2026-11-08 in PR B.* Include the `docs/development/http-status-codes.md` 304 row, whose Proposed note on an
  activation's `ETag` is now decided (D5), in the contract-change follow-up.
- Note the answer on ADR-0010 and ADR-0021 D5 (done in this change).
- Build D4 to D6 in #291 and D2 and D3 in #10 and #11. *D3 is built in PR B (2026-11-08): the layer merge and the
  effective list and skipped report.*
- Amendment of 2026-11-07: PR A0 builds B5, A4, B1, A7 and `depth`; the reads' halves (`pinNotHeld`, the language filter
  at read, the conditional `suggests` entries) are for PRs B and C; the ruleset page's floor and override rules Q6, Q8
  and Q9 remain the planned amendment A6. *PR B (2026-11-08) built `pinNotHeld`, the language filter at read, the
  suggestion on the effective list, and Q6 and Q8. PR C (2026-11-09) built the action context's suggestion and Q9 (a deny
  entry an override satisfies, the relaxed write rule); the [amendment R6](#amendment-2026-11-09-r6) records them.*
- Amendment R6 of 2026-11-09: the write rule of Q9 and the read rules of R11 for D2 and D3 (done here); PR C built the effective
  graph and the action context (D3 and ADR-0018).
- Amendment of 2026-11-08: amend D5 for the closure tag and ADR-0020 D6 for the either-tag `If-Match` (done here).

## References

- [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/), open question 1.
- [ADR-0020](/adr/0020-offering-and-activations/), open questions 4 to 7.
- [ADR-0013](/adr/0013-persistent-deactivation/), cascade and deny-list.
- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) d6 and d7.
- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/), the action-context walk, which is not the
  activation cascade: its `suggests` depth and step order are unchanged.
- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) and
  [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) D5, which named the corpus as the gate.
