# Research: Step enhancements and prompt fragments in the charter service

Phase 0 output of `/spec-kitty.plan`. The evidence is in [research/grounding.md](research/grounding.md) (the Phase A
brief, cited as **[G]** with its section) and the squad reports under [research/squad/](research/squad/)
(code-grounding **[JEN]**, invariants-contract **[ALP]**, similar-surfaces **[REN]**). This file records each decision
the plan builds to; it does not repeat the evidence. The operator ruled D1–D22 on 2026-11-09 ([G] "Operator rulings").
D1–D4 and D22 concern delivery shape and process and are cited where they matter; D15–D18 belong to PR C (#429) and
the #430 Op and are out of this mission's scope.

**Split (operator ruling, 2026-11-09).** This mission is now phase 1 of 2 (see plan.md, "Split into two missions").
The records below are kept whole, as the rationale for both missions. D5, D13, D14, P5, P6 and the served half of P7
are built by the phase-2 mission, and are marked so.

## Binding rulings

### D5 — The effective-prompt read is a new built operation (built by the phase-2 mission)

The canonical-render reading of "unchanged" also fixes the text phase 1's slot grammar parses at load (P2).

- **Decision:** a new operation `getEffectiveStepPrompt`,
  `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt`, built (no `x-reserved`),
  classed `### Added`. It lists every slot the prompt in force declares, with `fragments: []` when nothing fills it.
  The base text is the prompt in force rendered from the held `StepDocument` with equal meaning (ADR-0036).
- **Rationale:** ADR-0066 d9 calls the change additive; under ADR-0022 and the hand-over rule only a new operation is
  non-breaking ([ALP] §2 #424, §5 Q6). The store holds a parsed `StepDocument`, not bytes ([JEN] §1 "Mission-step
  prompt read path"), so "unchanged" can only mean equal meaning. Listing every slot lets a renderer remove the marker
  pair of an empty slot without parsing the prompt itself (d9: "a slot with none leaves nothing").
- **Alternatives:** a member on `EffectiveActionContext` (Breaking, and a WP28-owned file); a reserved operation built
  later (collides with PR C driving reserved operations to 0); defer to R5 #99 (the hand-over forbids taking #99, and
  #424 would stay open); list filled slots only; a raw-text holding of prompts (a second source of truth beside the
  parsed document). [G] §6 D5.
- **Third sub-ruling, `_links.prompt` (C-05):** D5's sub-decisions included "add `_links.prompt` on the context
  (recommended, after PR C)", which the operator accepted with the rest. An earlier draft treated it as offered only.
  It is now planned in IC-05/IC-07, after PR C: a `prompt` rel row, `RelationTableParityTest`, and the link in the
  action-context answer and example, landing with the built read.

### D6 — Guards and emits are fields, recorded and not built

- **Decision:** the shape is ruled (fields `add_guards`, `add_lifecycle_guards`, `emits`, mirroring ADR-0054 d10 and
  ADR-0050 d15–16) and recorded by a dated annotation; the schema stays at `prompt_fragments`; the work is #434.
- **Rationale:** ADR-0054 d2 and ADR-0064 d11 already make guards data, and an emit names an event type, not a node.
  The base mission-type schema has no step transitions or action points, so the load checks would have nothing to
  check against ([ALP] §3.2).
- **Alternatives:** add the fields now with partial checks (a contract promising checks the service cannot run).
  [G] §6 D6.

### D7 — Keep `missions/<type>/enhancements/` and annotate ADR-0053 open question 2

- **Decision:** the layout stays; ADR-0053 OQ2 gets a dated annotation that for steps "beside the kind" is the per-type
  `enhancements/` directory. The directory must equal the `enhances` edge's type, checked against the edge (C-003). No
  new ADR: an annotation of an existing ruling records it, so ADR-0067 is not taken.
- **Rationale:** #425's contract, the service classification and open-packs#175 already agree ([ALP] §3.1 option A).
  The equality check turns the second statement of the target into an enforced invariant.
- **Alternatives:** beside the step in `steps/` (literal wording, ten renames, Breaking); a top-level
  `step_enhancements/` (contradicts "no separate area"). [G] §6 D7.

### D8 — Ids are unique per pack; no URN grammar change

- **Decision:** a load check refuses a `step-enhancement` stem repeated across type directories of one pack, with
  `prompt_fragment_invalid`. `ArtifactId` keeps its pattern. **Amended by operator ruling A2 (2026-11-09):** the second
  file in path order is also withheld from `Pack.Builder` at load, so `Pack.guardKind`'s same-id rule never breaches
  (plan ruled table). An id that differs from its stem is a schema violation, not this rule (A1). **Path order
  (fourth analysis, A5)** is `PackPolicy.comparePaths` (segment by segment, by code point) at every moment, never the
  store listing's `String` order (`PackFileSet.regularFiles`, `PackFileSet.java:485-495`, which sorts `plan-x/` before
  `plan/`). The files of one id share their edges, so the edge rules are judged once per id, at its first file, and a
  repeated file gets no other identity or edge fault (A2).
- **Rationale:** ADR-0048 d10 already makes ids unique across a compose apart from layer overrides; the only gap is two
  types in one pack ([ALP] §3.3). Qualification would change the selection path grammar, preset ids and policy keys.
- **Alternatives:** `step_enhancement:<type>/<stem>` as templates are (Breaking, large). [G] §6 D8.

### D9 — Exactly one `enhances` edge, to an `action:`

- **Decision:** each step enhancement has exactly one `enhances` edge in its pack's graph documents, its target
  `action:<type>/<step>`, bare or `<pack>::`-qualified. When the count is not 1, the target and directory rules are
  not judged for that id (fourth analysis, A2). Judged at validate, load and registration, and, with R-OVR, on the
  relationship write (operator ruling R-REL, 2026-11-09: a `PUT /packs/{packId}/relationships` that would give a held
  enhancement an `overrides` edge, a second `enhances` edge, or replace its one `enhances` edge answers `422
  prompt_fragment_invalid` before the commit).
- **Rationale:** ADR-0066 d3 addresses a fragment by "the step"; the directory names one type; more targets would put
  one fragment into several steps and leave the directory check undefined ([ALP] §5 Q4).
- **Alternatives:** E6's multi-target allowance for enhancers in general. [G] §6 D9.

### D10 — The #428 semantic rules reuse `prompt_fragment_invalid`

- **Decision:** directory ≠ edge type, a repeated stem, a missing or extra `enhances` edge, a non-step target, an empty
  enhancement and the companion rules all answer `prompt_fragment_invalid`; its description is broadened to "an
  invalid step enhancement or prompt fragment". No new enum value.
- **Rationale:** no enum change, so no `### Breaking` for the vocabulary and no `WireTokenParityTest` row.
- **Alternatives:** a new `step_enhancement_invalid` (Breaking). [G] §6 D10.

### D11 — One companion per entry, any stem, each named once, orphans refused

- **Decision:** each `prompt_fragments` entry names one companion whose name matches the schema's `path` pattern
  (`^[a-z][a-z0-9-]*\.prompt-fragment\.md$`, any stem); each companion is named by exactly one entry; a
  `*.prompt-fragment.md` in an `enhancements/` directory that no entry names is refused at load.
- **Rationale:** an enhancement may fill several slots (d3), so companions cannot all be `<id>.prompt-fragment.md`;
  ownership must be total for the round trip, or a write drops the orphan ([ALP] §5 Q7). The schema's pattern already
  admits any stem; only its description ("the `<id>.prompt-fragment.md` companion") is reworded.
- **Alternatives:** require `<id>` stems (cannot express two slots); allow shared or orphan fragments (silent drop on
  write, against C-002). [G] §6 D11.

### D12 — An empty enhancement is refused

- **Decision:** a step enhancement with no `prompt_fragments` entry is refused with `prompt_fragment_invalid` while
  guards and emits are not built.
- **Rationale:** with no addition it would disappear silently from every prompt (#428), and nothing else makes it an
  activation anchor today.
- **Alternatives:** allow it as an anchor. Revisit with #434, when guards and emits can be its additions. [G] §6 D12.

### D19 — Test-expectation edits and versioning mechanics, as a set

- **Decision:** approved together: `KindBindingsTest:198-210` flips (the deferred-companion branch is deleted with it);
  the four prompt codes move to raised in `ReservedRefusalCodesTest` as each is raised by a main class; the refusal
  description wording in `CharterRefusalCode.yaml` and its pinned tests changes with the raise; fragments land with
  the contract change in the same commit, classed `### Changed`, `### Breaking` and `### Added`.
- **Rationale:** each edit is a consequence of the issue itself (escalation classes (c) and (f) of
  `semi-auto-delivery`), not a weakening.
- **Alternatives:** none proposed. [G] §6 D19, §3.3.
- **Addendum ruled 2026-11-09 (OD-10, T-01):** the plan forces further assertion edits that D19 does not name:
  `KindBindingsTest:64` and :85-90, `FileSystemPackInspectorTest:380-417`, `ActivationControllerTest:30,57-58`, the
  `errors.feature:192-238` raised-code table and its prose, and `ReservedRefusalCodesTest`'s javadoc and off-wire set.
  They are listed with before and after in plan OD-10; the operator approved all eight rows as a set. Row 5
  (`ActivationControllerTest`) is the phase-2 mission's. Row 9 (`ReservedRefusalCodesTest:189-194`, `DRG_SCHEMA_ERROR`
  expected raised) was approved separately on 2026-11-09 (plan ruled table, A3). Row 10 (`KindBindingsTest:141-150`
  and `:172-185` skip the bindings not placed by id alone, with a positive control that the step enhancement's
  `fileName` refuses) was approved on 2026-11-09 (plan ruled table, A5).
- **Erratum (2026-11-09, second analysis, A2):** "move to raised in `ReservedRefusalCodesTest`" is met by the YAML
  `raised` flag alone: the test reads `raisedCodes()` and `unraisedCodes()` from `CharterRefusalCode.yaml`. No list in
  the test is edited. Earlier plan text that called `ReservedRefusalCodesTest:342-345` "the reserved list" was wrong:
  those lines sit inside the `NamesEveryCode` probe, which must name every `RefusalCode` value
  (`theProbeNamesEveryCode`), and stay unedited.

### D20 — `empty_kinds: step-enhancement` in the whole-pack expectations

- **Decision:** `service/packs/src/test/resources/whole-pack/expected-red.yaml` gains an `empty_kinds` entry for
  `step-enhancement`, issue #428, with the reason text ruled on 2026-11-09 (OD-11): "Step enhancements and their
  fragments round-trip in StepEnhancementYamlTest, WholePackStoreTest and the DomainRoundTrip fixture test; built-in
  has none at the pin (open-packs#175 ships them in packs/spdd)." The originally approved wording, "stale when
  built-in gains one", is superseded.
- **Rationale:** the gate reads only `packs/built-in` (`WholePackRoundTripTest.java:57,84`), which holds no step
  enhancement; open-packs#175 puts its enhancements in `packs/spdd`, so the entry does not go stale when #175 merges
  ([G] §1 correction to [JEN]). Precedent: skill, guard, lifecycle.
- **Alternatives:** none honest; leaving the kind unbound would leave #428 open. [G] §6 D20.
- **Wording question (T-14), ruled 2026-11-09 (OD-11):** the operator took the squad's proposed wording, quoted
  above.

### D21 — Prove on fixtures; bump the pin later

- **Decision:** acceptance is proven on fixture packs shaped like the `spdd` pack; #435 bumps `openPacksRef` and adds a
  `packs/spdd` corpus test after open-packs#175 merges.
- **Rationale:** #175 is blocked on this mission, so waiting for it is circular.
- **Alternatives:** wait for #175. [G] §6 D21.

## Open items (deferred to the operator), since ruled

The full list, with options, the squad's recommendation, the escalation class and what each blocks, is in plan.md,
section "Operator decisions (plan point, 2026-11-09): ruled", items OD-1 to OD-12. The operator ruled D13 as OD-1 (B)
and D14 as OD-2 (A) on 2026-11-09; both are built by the phase-2 mission. The records below keep the options.

### D13 — Does an inactive enhancement refuse a compose when an override dropped its slot? (ruled OD-1 (B); the phase-2 mission)

- **Status:** resolved 2026-11-09 (OD-1: branch B), decision `01M6ZS10GDRY7NAA4PA5BK5EB9`. Built by the phase-2
  mission (#438). The branches below are the record of the options.
- **Branch A (ADR-0066 d8 as written, "whatever the activation"):** every enhancement of the closure is judged; a
  compose whose prompt in force lacks a named slot refuses with `prompt_slot_missing` on every merged read, writes that
  build the merge included. Consequence: a project cannot override a built-in prompt while any composed pack fills one
  of its slots, even unused ([ALP] §5 Q5).
- **Branch B (amend d8):** only an active enhancement refuses; an inactive one is not refused (domain diagnostic only,
  no wire surface). Needs a dated ADR-0066 amendment.
- **What the plans do:** branch B was ruled, so the phase-2 mission's IC-06 filters the slot check by the in-force
  answer and adds a dated ADR-0066 d8 amendment. [G] §6 D13.

### D14 — Does a step inherit its mission type's mutability level? (ruled OD-2 (A); the phase-2 mission)

- **Status:** resolved 2026-11-09 (OD-2: branch A, with `500 pack_unreadable` on reads and
  `422 override_not_permitted` at registration), decision `01M6ZS12SJAZYET6A3E06R0T74`. Built by the phase-2 mission
  (#438). The branches below are the record of the options.
- **Branch A (inherit):** a step's integrity is its mission type's; a locked mission type refuses an enhancement of its
  steps. Its refusal code and status are part of plan OD-2 (formerly N2; squad recommendation C-07).
- **Branch B (no inheritance):** `policy.schema.json` cannot name an `action`, so every step is open and ADR-0066 d7's
  integrity bullet is vacuous for steps; the plan pins that with a test and a note.
- **What the plans do:** branch A was ruled, so the phase-2 mission's IC-06 adds the integrity check. [G] §6 D14;
  [ALP] §5 Q2–Q3.

## Design decisions taken at the plan-point squad (2026-11-09)

Each was checked against the code at `92026b5d` before it was folded. The finding ids are those of
[research/plan-squad/](research/plan-squad/); [dispositions.md](research/plan-squad/dispositions.md) has the full table.

### P1 — The slot grammar lives in the kernel (A1)

- **Decision:** `kernel.markdown.PromptSlots` and `SlotId`, beside `MarkdownLines` and `MarkdownScanner`. The
  `PromptText` port in `packs.application` uses kernel types only, and its adapter in `packs.infra.storage` reads the
  `MissionType`.
- **Rationale:** `packs.domain`, `packs.application` and `activations.domain` all use the grammar. In
  `missions.domain` it would break `PACKS_DOMAIN_IS_PURE`, `sliceDomainDoesNotCross` and `APPLICATION_IS_EDGE`
  (`HexagonalArchitectureTest.java:160-165`, :552-573, :258-262). The kernel is nameable from every ring.
- **Alternatives:** `missions.domain` (refused by three rules); a copy per slice (two grammars).

### P2 — One parse input: the canonical text (A5, T-05)

- **Decision:** load, validate mode and the read all run `PromptSlots` on `MarkdownCodec.write(document)`, frontmatter
  included. The read serves the same text. A corpus property test shows that canonicalisation keeps every marker.
- **Rationale:** `Companions.read` parses Markdown before any binding sees it (`Companions.java:286-305`), so there is
  no raw-text seam on load. Three inputs would not "agree by construction".
- **Alternatives:** raw text at load (no such seam); a new raw-text holding (a second source of truth, already
  rejected under D5).

### P3 — Load faults are violations whose kind is the wire token (A3, C-02; build-to default of OD-3)

- **Decision:** `PackPolicy` answers the token. Each fault is a `PackElementViolation` at its file, recorded in the
  shared `DomainRepositoryService.assembly` given the pack's graph read, so that the registration and every composing
  read see it (plan erratum E-1). Nothing is thrown for a prompt fault. The wire effect is stated once in
  contracts/README.md, "How a load fault reaches the wire (canonical)".
- **Rationale:** `loadStaged` already copies `violation.kind()` into the code (`WholePackStore.java:239-243`). A throw
  becomes one violation with pointer `"pack"` (:471-481) and breaks `load`'s partial-failure contract.
  `offWireRules` forbids calling `PackFindingCode.refusal()` outside `PackFinding`.
- **Alternatives:** throwing via `PackFindingCode.refusal()` (forbidden by a gate, and it loses faults). OD-3 (a),
  ruled 2026-11-09, counts it as "raised" under an amended definition.

### P4 — Warnings travel apart from violations (A4)

- **Decision:** the unversioned `prompt_slot_not_empty` warning is held on a separate list. The check runs in
  `DomainRepositoryService.assembly`, which holds the descriptor. Its wire surface, ruled 2026-11-09 (OD-7), is the
  registration result's new `diagnostics` member.
- **Rationale:** if the warning were a violation, built-in would answer `pack_unreadable` on every compose (NFR-002).
  `KindBinding.toEntity` gets no descriptor.

### P5 — Slot facts are composition facts (A7) (moved to the phase-2 mission)

- **Decision:** `compositionFacts` computes per pack `declaredSlots` and `filledSlots`. The compose check is pure over
  the merged facts and the same for both D13 branches.
- **Rationale:** `MergedClosure` holds winners, not artifacts, and `packs.domain` cannot read `MissionType`.
  `infra.storage` may name `missions.domain`.

### P6 — The ordering key's second component is the descriptor `name`, served as `packName` (C-04) (moved to the phase-2 mission)

- **Decision:** `EffectivePromptFragment` gains a required `packName`. The key is (level, descriptor name, enhancement
  id), by code point.
- **Rationale:** ADR-0066 d5 says "the pack's name"; `packId` is the configured identifier. Qualifiers are already
  resolved by descriptor `name` (`PackCompositionResolver.checkReferences`). Without it, no client or test can check
  the order.

### P7 — Served fragment text is the canonical render (T-03) (served half moved to the phase-2 mission)

Phase 1 keeps the CR/BOM refusal under `prompt_fragment_invalid` and the round-trip meaning with its mutant test;
serving the text is the phase-2 mission's.

- **Decision:** `EffectivePromptFragment.text` is `MarkdownCodec.write` of the held fragment document, as the prompt
  is. A CR or a BOM is refused at load under `prompt_fragment_invalid`. Round-trip meaning includes each fragment
  body, with a mutant test.
- **Rationale:** D5's reading for prompts applies equally. `MarkdownCodec.read` refuses `\r` and a BOM
  (`MarkdownCodec.java:139-145`), so "LF line endings" means refused, not normalised.

### P8 — Reading of D11's "refused at load" (A9)

- **Decision:** an orphan fragment is refused at the registration's staged load, by reclassifying the existing
  `unmodelled` entry under `prompt_fragment_invalid`, and in validate mode by `COMPANION_ORPHAN` from the facts (validate
  mode has no unmodelled list; third analysis, A9). Fragments of a directory holding a refused or withheld enhancement
  are not judged orphans (A4). On a composing read it is not held,
  like every unmodelled file. IC-01 raises no prompt code (T-15).
- **Rationale:** the store already keeps an unnamed companion out of `companionFiles` and lists it as unmodelled
  (`WholePackStore.java:212,623-675`), and `loadStaged` refuses it (:244-248). It is never silently dropped. A throw
  in `load` would break the round trip.

### P9 — Refusal precedence (T-08)

- **Decision:**
  - `prompt_fragments: []` answers the schema path (`minItems`). An absent key answers `prompt_fragment_invalid` (D12).
    Both are tested, and the schema code for `[]` is documented.
  - An entry whose slot id breaks `<namespace>:<name>` answers the schema path too (the `slot` pattern), while a
    repeated slot, a missing companion and an orphan answer `prompt_fragment_invalid` (FR-005). Both sides are tested.
  - A schema-invalid file gets no semantic rule.
  - A codec refusal precedes the slot grammar.
  - A read names the first violation in assembly order (contracts/README.md, "How a load fault reaches the wire";
    plan erratum E-3), the step-enhancement faults among them in (path, `StepEnhancementFault`) order.
- **Rationale:** `[]` already fails the schema (`PromptFragmentContractsTest:111-119`). Changing the schema would be a
  contract change. Determinism of the single `500`.

### P10 — Fragments come from the winning copy of each URN (A11)

- **Decision:** a same-id enhancement at a higher level replaces the lower one (ADR-0048 d10). A sibling contest settled
  by a descriptor `overrides` entry (C15) leaves one winner. Only the winner's fragments are served. Step enhancements
  are not excluded from layer override.

## Facts the plan relied on that differ from the brief

- The descriptor version fix is commit `2ced50b7` ("keep a pack descriptor's schema_version through a store write");
  no commit `e3fbc066` exists on `main`. The version is `PackDescriptor.schemaVersion()` in `service/kernel`.
- The four reserved `x-refusal` entries carry no `operations` map; registration today wraps load refusals into
  `422 validation_failed` with violation codes (`WholePackStore.loadRefusingAsInvalid`, `loadStaged`). See plan OD-3.
- `PackCompositionResolver.checkQualifiedReference` skips the artifact-missing check for `action:` targets (their
  `artifactKind()` is empty), so adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-002's step resolution is new code, not only a test.
- The pinned built-in prompts carry other HTML comments with a colon (`<!-- decision_id: <id> -->`); the marker grammar
  recognises only comments ending in `:start` or `:end`.
- (Plan squad, A2) Under E6 as built (`InForceResolution.java:329-336`), no step enhancement is ever in force: its
  `action:` target is never in the effective set. The earlier "one hunk in `missionTypeOf`" fallback does not fix it.
  See plan OD-6.
- (Plan squad, C-01/T-07) A registration's thrown load fault becomes one violation with pointer `"pack"`, and a read
  turns any violation into `pack_unreadable`. See plan OD-3.
- (Plan squad, A8) `DomainRepositoryService` needs no edit for the URN to join `defined`: `assembly` iterates
  `KindBindings.all()` and `compositionFacts` adds every `authoredInGraphs()` kind.
