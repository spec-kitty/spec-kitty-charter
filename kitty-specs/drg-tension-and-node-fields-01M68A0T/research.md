# Research: DRG tension edges and node fields round-trip

The pre-spec squad's findings are in [research/pre-spec-squad.md](research/pre-spec-squad.md). This file
records the plan-phase decisions and the dispositions of the plan-point adversarial squad's findings.

## Decisions

### D1 — Tension is a kernel value beside the relationships

- **Decision**: a kernel `Tension` record (`source`, `target`, `when`, `rationale`; no strength), held in
  `DoctrineGraph.tensions()` beside `relationships()`. It is not a `RelationType`. Recorded as
  DM-01M68A2TYC1VMDPCJ8B76602YH.
- **Rationale**:
  - ADR-0028 D3 counts tension apart from a pair's one structural relationship. `Relationship`'s
    identity is the unordered pair, so a tension cannot be a `Relationship` without breaking that
    identity or the coexistence rule. The built-in pack has exactly that case: `DIRECTIVE_024` /
    `DIRECTIVE_025` hold both `in_tension_with` and `requires`.
  - It changes no `RelationType`, no `Relation` value and no `Relation.yaml` enum, and `/relationships`
    neither accepts nor answers a new token (C-001).
- **Alternatives considered**:
  - A `RelationType.TENSION` constant: an HTTP contract change (the operator's decision), and it would
    still need a pair-identity exception.
  - Keep IMPACTS −1 and teach the writer to emit `in_tension_with` for strength −1: the retired
    reading stays, and an authored `impacts: -1` edge would be rewritten as a tension, a new loss.
  - Keep the raw relation token on `Relationship`: it leaks storage spelling into the kernel.

### D2 — `in_tension_with` is a storage spelling

- **Decision**: the word stays in `PackPolicy` and the writer as the written spelling of tension
  (ADR-0036 D4). The kernel has no token for it.
- **Rationale**: ADR-0028 names the relationship `tension`, while today's files spell it
  `in_tension_with`. When the files move to a new spelling, only storage changes.
- **Alternative considered**: a `Tension.TOKEN` constant in the kernel. Rejected: the kernel would carry
  a pack spelling that ADR-0028 already renames.

### D3 — Node fields on `DrgNode`

- **Decision**: `DrgNode(urn, label, provenance, tags, bodyPath)`, with `tags` an immutable ordered
  `List<String>`. The two-argument constructor is kept and means no extra fields.
- **Rationale**:
  - ADR-0036 L246-249 makes every declared field part of a node's value. E15 makes `tags` a sequence.
  - `body_path` belongs to a fragment node, and a shard node never has one. The writer of each shape
    writes the fields its schema allows: the shard writer never writes `body_path`, and the fragment
    writer never writes `provenance` or `tags`. The schemas (`additionalProperties: false`) keep a
    written document valid.
- **Alternative considered**: a shard-node type and a fragment-node type in the kernel. Rejected: the
  DRG has one node concept, and the document shape is a storage concern.

### D4 — A fragment write from the domain

- **Decision**: `GraphShardWriter.writeFragment(DoctrineGraph)`, written in the fragment's key order.
- **Rationale**: FR-007 needs `body_path` to survive a write, and the gate round-trips no fragment.
  Without a domain fragment write, "survives write" cannot be proven on the production path.
- **Alternative considered**: assert only that `body_path` reaches `DrgNode`. Rejected: that proves
  half the round trip.

### D5 — Records

- **Decision**:
  - A new ADR (number picked at writing time) records D1–D4 as the representation ADR-0028 D5 deferred.
  - DIV-018 and the `Relation`/`RelationType` javadoc drop the −1 claim.
  - The `Relation.yaml` description text and a CHANGELOG line under Unreleased say the service holds
    `in_tension_with` as tension and does not answer it as a relationship.
- **Rationale**: FR-010; the CHANGELOG previously promised the −1 reading. No enum value or path
  changes.

## Plan-point adversarial squad

Findings and dispositions are recorded below.

Point-cut: plan. Question: does the design hold against ADR-0028 and ADR-0036 and the code, avoid any
HTTP contract change, and leave no loss, consumer or fakeable test unaddressed? Cast (each
profile-loaded, read-only):

- `architect-alphonso`: domain and ADR fit;
- `java-jenny`: static gates and implementation traps;
- `reviewer-renata`: non-vacuity and the ratchet.

All three verdicts: **APPROVE-WITH-CHANGES**. The lenses agree on the HTTP contract question: no change,
and no HTTP path renders a `DoctrineGraph`.

| # | Sev | Lens(es) | Finding | Disposition |
| --- | --- | --- | --- | --- |
| S1 | HIGH | architect, jenny, renata | `RelationshipShard.read` keeps only relationships, so a hand-edited `registered.graph.yaml` tension would be dropped silently on the next registration rewrite | **Accepted, changed.** `PackPolicy.registeredGraph` reports a tension in the registered shard as a finding: tension is authored in a pack's graph shards and is never registered. `CommitStorePackWriteStore`'s existing "does not bind … refusing to rewrite it" refusal then fires, so nothing is dropped. Red test in `RelationshipShardRoundTripTest`. The spec edge case is corrected. |
| S2 | HIGH | renata | The pinned pack has no node provenance and no tension `when`, and no writer-side test exists, so the gate cannot prove FR-006 or a tension's `when` | **Accepted.** A production-path case in `DomainRoundTripTest`: a shard with provenance, `tags: [b, a]`, `A in_tension_with B` (with `when` and `reason`) and `B requires A`. It asserts the gate comparator reports nothing, with a mutated control that it does report. |
| S3 | HIGH | jenny | The fragment edge schema has no `when` and the fragment root has no header, so `writeFragment` could emit an invalid document | **Accepted.** `writeFragment` writes no root header and writes edges as `source, target, relation, impacts?, reason?`. A graph holding what a fragment cannot hold (a `when`, a node `provenance` or `tags`) is refused with `IllegalArgumentException`. Tested. |
| S4 | HIGH | renata | `GraphDocumentReaderTest`'s graph-equality round trip is vacuous if `equals` leaves out tensions or node fields; `Relationship.equals` ignores `type` | **Accepted.** `DoctrineGraphTest` asserts that graphs differing only by a tension, or only in a node's tag order, are unequal. Round-trip tests assert `tensions()` and each relationship's `type()` explicitly. |
| S5 | MEDIUM | architect | The shard writer would drop `bodyPath` silently, and the fragment writer would drop `provenance`/`tags` | **Accepted.** Each writer refuses a node carrying a field its shape cannot hold, as a programming error. Recorded in the ADR. |
| S6 | MEDIUM | architect | ADR-0028 D3 "at most one tension" per pair and canonical orientation are not enforced | **Deferred, recorded.** The reader keeps what is written (ADR-0036 D4). Pack consistency (a second tension on a pair, a non-canonical orientation) is reported by the gate and fixed in open-packs. The ADR names the deferral; the pinned pack has neither. |
| S7 | MEDIUM | architect | `Tension` used `source`/`target`, the DSO's words; the kernel `Relationship` says `from`/`to` | **Accepted.** `Tension(from, to, when, rationale)`. |
| S8 | MEDIUM | architect, renata, jenny | Records list incomplete | **Accepted.** FR-010 covers: `logical-model.md`; `Relation` IMPACTS and RECONCILES_TENSION javadoc; `RelationType` IMPACTS ("the one symmetric relation"); `GraphDocumentReader` class javadoc; `PackPolicy` `graph` and `edge` javadoc; DIV-018; `Relation.yaml` description; CHANGELOG; the `VocabularyEqualityTest` name and javadoc. The check is a grep for `-1`, `−1` and "strength -1" in tension context. |
| S9 | MEDIUM | renata | FR-008 is blind through the comparator (absent, null and `[]` compare equal) | **Accepted.** Assert on the written text that the keys are absent, on the same fixture as the present case. |
| S10 | MEDIUM | renata | The gate's read-back never re-binds the written shard | **Accepted.** `DomainRoundTrip.shard` refuses a written shard that does not build without findings into the same nodes, relationships (with types) and tensions. |
| S11 | MEDIUM | renata | The IMPACTS −1 tests must be rewritten red-first, not deleted; `relationships().isEmpty()` goes blind | **Accepted.** Rewritten to assert one `Tension` and no relationship. The impacts-on-tension finding test also asserts `tensions()` is empty, with the valid tension on the same fixture as positive control. |
| S12 | MEDIUM | renata | `VocabularyEqualityTest` name and javadoc state the −1 reading | **Accepted.** Renamed to `…PlusExactlyTheTensionSpelling`. It also asserts that `PackPolicy` binds that spelling. |
| S13 | MEDIUM | jenny | The `edge` shape | **Accepted.** An early `tension(raw, origin)` branch, with endpoint and self-loop checks shared in one helper; `LEGACY_TENSION_STRENGTH` removed. |
| S14 | MEDIUM | jenny, renata | Orphaned load fixtures still record −1 | **Accepted, changed.** Both fixtures are corrected to the new meaning. Their runner was deleted in `2bb3f762` (63 orphaned cases); restoring it is filed as a follow-up draft, not done here. |
| S15 | MEDIUM | jenny | Read `tags` with `YamlFields.strings`, as `GraphDocumentYaml` does; extract node helpers | **Accepted.** |
| S16 | LOW | architect, jenny | `DoctrineGraph` `putIfAbsent`: the first declaration wins for all node fields; tensions are not de-duplicated | **Recorded** in the ADR and pinned by a test. `merge` has no production caller. |
| S17 | LOW | jenny | Doclint for the new record components and constructors; `NodeKind.plural()` round-trip property test | **Accepted.** |
| S18 | LOW | architect | research D1's rationale for rejecting "IMPACTS −1 + writer special case" was imprecise | **Accepted.** Reworded below. |
| S19 | LOW | jenny | Complexity ≤ 15 is not enforced by any tool | **Accepted.** Held by review; noted in the PR. |
| S20 | LOW | renata | `writeFragment` has no production caller, so its write half is helper-level | **Accepted, labelled.** Its javadoc says it exists to prove the fragment round trip; it is compared through `MeaningComparator` with a dropped-`body_path` control. |

**D1 rationale, corrected (S18).** Keeping IMPACTS −1 and special-casing the writer would still model
tension as the relation ADR-0028 retires, inside the structural pair identity that D3 says it is not
part of. Today no `impacts` strength is carried at all (ADR-0025), so the writer could not tell an
authored `impacts: -1` from a tension.

## Implementation note: the ADR number is 0044, not 0045

WP03 planned ADR-0045, because ADR-0044 exists on the #194 PR-B branch
(`issue-194-mission-type-consolidation`). `scripts/docs/check_docs.py adr` refuses a gap in the
numbering ("no ADR 0044"), so 0045 would keep the docs gate red until PR-B lands.

The charter's rule picks the number "against `main` and the open PRs". PR-B is not an open PR, and
0044 is free on `main`. The record is therefore **ADR-0044**. The collision with the PR-B branch is
known and flagged in the PR hand-off: whoever lands second renumbers.

## Pre-PR adversarial squad

The squad reviewed the clean branch `issue-235-236-drg-round-trip-clean`. Cast (each profile-loaded,
read-only):

- `architect-alphonso`: HTTP, contract and layering;
- `debugger-debbie`: edge cases and divergence;
- `curator-carla`: records and glossary.

All three verdicts: **SHIP-WITH-CHANGES**. Nothing blocks.

| # | Sev | Lens | Finding | Disposition |
| --- | --- | --- | --- | --- |
| P1 | MEDIUM | architect, debugger | `PackPolicy.judge` reads `registered.graph.yaml` with `graph()`, so a hand-edited tension there loads and validates clean while a registration into the pack is refused (`pack_unreadable`) | **Recorded; operator decision.** Routing `judge` through `registeredGraph` would stop the whole pack from loading after a hand-edit, a wider observable change than this bugfix. ADR-0044 D5 documents the divergence. A test pins it (`PackPolicyTest.aHandEditedTensionInTheRegisteredShardLoadsCleanWhileARegistrationRewriteReportsIt`). The options go to the operator in the PR: refuse in validate, or carry tensions through a registration rewrite. |
| P2 | MEDIUM | architect | Decided-language on a Proposed ADR | **Changed.** `logical-model.md` now says "proposed by ADR-0044". ADR-0044's Status names the mission agent as proposer and the operator as decider; acceptance is asked in the PR. |
| P3 | MEDIUM | curator | `Relation.yaml` said "a tension … is not a relation", but the glossary names tension a CRG relation | **Changed.** It now reads "not a value of this enum (not a relation type of the service)". |
| P4 | MEDIUM | curator | The contract CHANGELOG pointed only at `Removed`; `Added` still states the −1 reading in present tense | **Changed.** The entry now points at both, as records of the reading as it was then. The heading says "CRG" (terminology.yaml), and the entry says #236 has no contract surface. |
| P5 | LOW | curator | The ADR-0028 note sat between "Supersedes" and the "also supersedes" paragraph | **Changed.** It moved after that paragraph. |
| P6 | LOW | curator | `DrgNode` javadoc said "Doctrine Reference Graph", a retired term | **Changed** to "Charter Reference Graph (CRG)". |
| P7 | LOW | architect | `Tension` javadoc said the spelling "is storage's"; ADR-0044 D2 says "the pack policy and the writers" | **Changed.** Aligned with D2. |
| P8 | LOW | architect | A tension self-loop reports "a relationship cannot join X to itself" | **Kept.** Finding texts are stable on purpose (the load fixtures and tests pin them). A follow-up may reword it. |
| P9 | LOW | debugger | `YamlFields.strings` skips non-string entries silently; only the schema guards it | **Deferred, follow-up drafted.** `YamlFields` is shared by every kind's reader, so hardening it to throw is out of this bugfix's scope. |
| P10 | LOW | debugger | Two `GraphDocumentReaderTest` round trips use `DoctrineGraph.equals` (pair identity) | **No change.** Both already assert each relationship's type, `when` and rationale explicitly, and the tension and node fields by value. |
| P11 | LOW | debugger | A node declared twice is dropped without a finding | **Deferred, follow-up drafted.** Behaviour unchanged; ADR-0044 D7 states it. |
| P12 | LOW | curator | `tasks.md`, the WP03 prompt and `lanes.json` still say ADR-0045 | **Noted in the PR hand-off.** Editing them would make the recorded analysis stale and rewrite runtime-owned state. |
| P13 | LOW | curator | "−1" and "-1" spellings are mixed | **No change.** Cosmetic. |
