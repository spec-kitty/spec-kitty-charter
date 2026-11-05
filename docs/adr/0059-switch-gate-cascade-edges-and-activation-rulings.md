---
title: "ADR-0059: The switch gate, the cascade edges, the layer-override grain and the activation write rulings"
description: "Operator rulings of 2026-11-04 and 2026-11-05: BDD scenarios and contract tests gate each core switch; cascade edges; whole-artifact layer overrides; ADR-0020 questions 4 to 7."
doc_status: active
updated: 2026-11-05T00:00:00Z
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
or changed this record's defaults, ruled the questions it had left open and ruled how the `enhances` pull behaves at read; the decisions below carry those rulings.

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

### D2. The cascade follows five relations and `enhances` (ruled)

1. **Five relations, no depth limit.** The activation cascade walks `requires`, `suggests`, `refines`, `scope`
   and `instantiates` forward, as core does. It stops at a denied artifact and reports it (ADR-0013 D4). Each
   artifact is visited once (the ADR-0025 visited-URN guard), so a cycle through `enhances` ends.
2. **`enhances`, both ways, at read.** The edge runs from the enhancing artifact to its target, and the pull follows it in
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

### D4. A stored document a pack upgrade made stale is served and flagged (ruled; ADR-0020 question 5)

- **On read.** The service answers from the stored document as it stands. An id the offering no longer has is left
  out and listed in the skipped report. A deny entry on an artifact that is now required or locked is not applied
  (ADR-0013 D2), and the skipped report lists that too.
- **On write.** A `PUT` that still carries an unknown id is refused `422 activation_invalid`, and one that denies a
  required or locked artifact `409` (ADR-0020 D9). The client cleans the document up before it writes it back.

ADR-0020 D6's round trip ("a `PUT` of a fetched `config` answers `200` with the same `ETag`") holds for a document
the offering has not made stale; ADR-0020 D6 carries a note.

### D5. The `Activation` answer's `ETag` is its revision, and the reads declare `304` (ruled; ADR-0020 questions 4 and 7)

- `summary` moves out of the `Activation` answer to an effective read, because it follows the offering. Which
  effective read carries it is settled with the contract change in #291. `governanceFingerprint` leaves
  `Activation`; the effective reads already carry it (it follows the offering, so the strong `ETag` would not otherwise
  match the bytes). The answer's bytes then depend only on the stored document, and its strong `ETag`
  is the revision.
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

## Consequences

### Positive

- Each operation has one readiness test, with a real behavior behind it, which this repository owns and runs on every pull request.
- The cascade, the merge and the writes can be built without waiting on further rulings, once the contract
  changes below land.

### Negative

- Core switches without proof that the service agrees with its Python answers. Every deliberate difference must
  be in the divergence register before core switches, or core finds it in use.
- A selected enhancer brings its target, the target's other enhancers and whatever those cascade to, within
  `activatedKinds`, so a small add-on can put a large artifact and its neighbors in force. A pulled artifact is not
  in `selected`, so unselecting cannot remove it: a deny entry is the way out.
- D3, D2.2 and D2.4 depart from core. A project with a partial override, with an artifact that enhances another, in either direction,
  or with a profile whose parent `requires` or `scope`s more, sees different content after the switch. Core's
  cascade excludes lineage, so it does not pass through `specializes_from`.
- D3, D4 and D5 change the contract. D5: `summary` and `governanceFingerprint` leave `Activation`, and `304` is
  added to five operations. D4: the skipped report, closed with four buckets in
  `contracts/charter/schemas/SkippedReport.yaml`, has no bucket for a dropped id or an unapplied deny entry. D3: no
  `DiagnosticCode` value reports a layer override (today only `pack_level_missing` and `pack_unknown_key`). D2.2: no
  `EffectiveArtifactReason` value names the `enhances` pull, and the description and `x-derived` rule of
  `SkippedDeactivated.requiredBy` assume a forward edge and a single reacher. `skippedByScope` also mixes the
  `cascade` scope with `kind_not_activated`.

## Follow-ups

- Re-scope or close #9 and #72; drop the gating role from #14.
- Drop #72 and #14 from #74's dependencies; #74 is held on the old gate.
- Ask core to amend its ADR `2026-10-03-1`, which still names the corpus as the only gate.
- Update the gate wording that still has the corpus or #72 gate consult the register or decide behavior:
  `docs/conformance/divergence-register.md` (the conformance gate paragraphs),
  `docs/conformance/README.md` (the corpus as what holds the service to core),
  `contracts/charter/scenarios/README.md` (`@provisional`, decided by the corpus) and
  [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/) D4.
- Add the divergence-register entries for D2.2, D2.4, D3 and D4.
- Change the contract for D5 (`summary` and `governanceFingerprint` leave `Activation`; `If-None-Match` and `304` on
  five operations) and the round-trip text of `contracts/charter/paths/activations_activationId.yaml` for D4.
- Add the skipped-report bucket or buckets for D4 and a `DiagnosticCode` value for D3's layer-override report, as
  0.x contract changes under [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) in the #291 contract
  change.
- Add the `EffectiveArtifactReason` value for D2.2 and widen both the description and the `x-derived` rule of
  `SkippedDeactivated.requiredBy`, as 0.x contract changes in the #291 contract change. The same change widens
  `skippedByScope`'s rule to mean outside `activatedKinds` at read and separates it from `kind_not_activated`.
- State in the #291 contract change whether `ActivationChange.skipped` includes what the read-time pull skips.
- Have `getEffectiveGraph` carry the layer-override report in the #291 contract change. `Diagnostic` today describes
  one pack's load, so the contract change either widens its description or adds a schema that carries the report.
- Update `docs/architecture/offering-and-activation.md` for D2.2, D4 and D5 (the `Activation` example and canonical
  form, and the cascade text). Include the `docs/development/http-status-codes.md` 304 row, whose Proposed note on an
  activation's `ETag` is now decided (D5), in the contract-change follow-up.
- Note the answer on ADR-0010 and ADR-0021 D5 (done in this change).
- Build D4 to D6 in #291 and D2 and D3 in #10 and #11.

## References

- [ADR-0055](/adr/0055-cutover-sequencing-the-java-service-first/), open question 1.
- [ADR-0020](/adr/0020-offering-and-activations/), open questions 4 to 7.
- [ADR-0013](/adr/0013-persistent-deactivation/), cascade and deny-list.
- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) d6 and d7.
- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/), the action-context walk, which is not the
  activation cascade: its `suggests` depth and step order are unchanged.
- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) and
  [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) D5, which named the corpus as the gate.
