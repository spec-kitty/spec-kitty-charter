# Data model: Step enhancements and prompt fragments

Phase 1 output of `/spec-kitty.plan`, revised after the plan-point squad (2026-11-09). The vocabulary follows the
spec's Domain Language, with one name per concept in every layer (A13). The concept is the **effective step prompt**.
The domain values and the wire schemas share the names `EffectiveStepPrompt`, `EffectivePromptSlot` and
`EffectivePromptFragment`.

**Split (operator ruling, 2026-11-09).** This mission is phase 1 of 2. It builds the invariants judged at V and L,
and the existing sibling-conflict rule at C. The invariants judged at C that the compose checks add, the composition
facts and the `EffectiveStepPrompt` read model (moment R) moved to the phase-2 mission. Their sections are marked
"(moved to the phase-2 mission)" and kept as the record.

Each invariant names the refusal (or diagnostic) that holds it, and the moment it is judged:

- **V** validate mode;
- **L** load: `DomainRepositoryService.assembly` given the pack's graph read, which both the registration's staged
  load (`WholePackStore.load`) and every composing read (`assemble` → `readable`) call;
- **W** the relationship write (`RelationshipRegistrationService.registerRelationship`, before `writeStore.commit`
  writes `registered.graph.yaml`; operator ruling R-REL, 2026-11-09): only the edge invariants of a held step
  enhancement, answered as a top-level `422 prompt_fragment_invalid`, nothing written;
- **C** compose (the existing sibling-conflict rule here; the new compose checks are phase 2, after PR C);
- **R** the effective-prompt read (the phase-2 mission).

**How a load fault reaches the wire.** A fault judged at L is a violation at its file, with `kind` equal to the code
shown: `422 validation_failed` at registration, `500 pack_unreadable` naming the first violation (in assembly order)
on a composing read. The seat, the order and the meaning of "raised" are stated once, in
[contracts/README.md](contracts/README.md#how-a-load-fault-reaches-the-wire-canonical) (OD-3 (a)).

## SlotId (value, `kernel.markdown`)

- **Grammar:** `<namespace>:<name>`, each part `[a-z][a-z0-9-]*`; the whole matches
  `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` (ADR-0066 d2; `step-enhancement.schema.json` `slot`).
- The namespace names a concern, not a pack: no pack is read from it.
- Compared by code point. Stable like a pack name: renaming one is a breaking change for packs that fill it.
- In the kernel so that `packs.domain`, `packs.application` and `activations.domain` may all name it (A1).

| Invariant | Refusal | When |
|---|---|---|
| A marker's id fits the grammar | `prompt_slot_malformed` (in a prompt) | V, L |
| An entry's slot fits the grammar | schema violation (the file gets no semantic rule) | V, L |

## Slot (value, `kernel.markdown`)

A marker pair in a step prompt: `<!-- <slot-id>:start -->` … `<!-- <slot-id>:end -->`. A marker candidate is an HTML
comment whose trimmed content ends in `:start` or `:end`; other comments are text.

- **Fields:** `id: SlotId`, `position` (declaration order in the prompt), `empty: boolean`.
- **Parse:** `PromptSlots.parse(text)` answers the declared slots in order, or the faults found.
- **Input:** always the **canonical text** `MarkdownCodec.write(document)` of the held prompt, frontmatter included
  (A5). Load, validate mode and the read parse the same text, and the read serves it. At the load seat `document` is
  the companion `MarkdownDocument` the store read into the mission type's DSO (`MissionTypeDso.Step.prompt()`, filled
  by `MissionTypeBinding.withCompanions`, `MissionTypeBinding.java:139-147`), never the built `MissionType`'s `StepDocument` re-rendered by
  `MissionTypeBinding.markdownOf(StepDocument)` (:341-349), a second rendering through the domain that the load need
  not trust to agree (third analysis, A11).
- **Whitespace:** "only whitespace" means the explicit set ASCII space U+0020, tab U+0009 and LF U+000A, never
  `Character.isWhitespace`. NBSP U+00A0 and U+200B are text. A CR cannot reach
  the parser, because `MarkdownCodec` refuses it first. The full edge-case table is in plan IC-03 (T-05).

| Invariant | Refusal | When |
|---|---|---|
| Every start has its end after it; every end has its start | `prompt_slot_malformed` | V, L |
| A slot id is declared once per prompt | `prompt_slot_malformed` | V, L |
| No start inside an open slot | `prompt_slot_malformed` | V, L |
| Only whitespace between a pair, descriptor `schema_version: "1.0"` | `prompt_slot_not_empty` | V, L |
| The same, descriptor unversioned | warning `prompt_slot_not_empty` (Diagnostic) on the **warnings list**, never a violation; the load goes on, and the registration result's `diagnostics` lists it (A4, OD-7). The list's seat: a `List<PackFinding> warnings` out-parameter of the graph-taking `DomainRepositoryService.assembly` form, filled as `places` is, carried on a new `WholePackStore.LoadedPack` field to `WholePackWriteStore.Contents`; `Pack.java` is unchanged (E-3) and a composing read discards the list (third analysis, A8) | V, L |
| Only the prompt in force declares slots (no fragment, no enhancement adds one) | see PromptFragment, StepEnhancement | V, L; C (phase 2) |

## PromptFragment (value, `packs.domain`)

The Markdown body of one `*.prompt-fragment.md` companion, held by its step enhancement and written back at its path
with equal meaning (ADR-0036, ADR-0040).

- **Fields:** `path` (the companion's name, beside the enhancement file), `body` (the parsed Markdown document).
- **Meaning** is the canonical render. The round-trip comparators include each body, so a dropped or altered fragment
  fails the round trip, and a mutant test shows it (T-03).
- **Line endings:** `MarkdownCodec` refuses a CR and a BOM (`MarkdownCodec.java:139-145`). "LF line endings" therefore
  means refused, not normalised.

| Invariant | Refusal | When |
|---|---|---|
| Readable by `MarkdownCodec` (no CR, no BOM) | `prompt_fragment_invalid` (reported before the slot grammar runs) | V, L |
| No frontmatter | `prompt_fragment_invalid` | V, L |
| Non-blank, no frontmatter (the closed `prompt-fragment` structure schema: an optional non-blank H1, a preamble and H2 sections, non-blank text in at least one of them; an H1 is allowed, `contracts/packs/schemas/prompt-fragment.schema.json`, fourth analysis A6) | `prompt_fragment_invalid` | V, L |
| No slot marker of any id (canonical text, any position, fences included) | `prompt_fragment_invalid` | V, L |
| Named by exactly one entry of one enhancement | `prompt_fragment_invalid`, at the **second naming enhancement's** file (path order), as the store's `named by two artifacts` refusal points (`FilesystemPackStore.java:804-814`) | V, L |
| Not orphaned in an `enhancements/` directory | `prompt_fragment_invalid` at the fragment. **V:** `COMPANION_ORPHAN` from the facts (the inspector passes every fragment it read; validate mode has no unmodelled list). **L (registration only):** `loadStaged` reclassifies the store's `unmodelled` entry; the facts at load carry no orphan set, so the rule never fires there. Not judged on a composing read (A9, reading of D11; third analysis A9). A fragment in a directory one of whose enhancement files the read refused or withheld is not judged an orphan, at either moment: it stays an `unmodelled` entry at registration, as every file of a refused artifact does (third analysis, A4) | V, L (registration) |
| Prose naming an artifact is text, never a reference (d3) | none (no edge is read from it) | — |

## StepEnhancement (entity, `packs.domain`, implements `CharterArtifact`)

An artifact of the enhancing pack at `missions/<type>/enhancements/<id>.step-enhancement.yaml` (C-006), holding only
additions (ADR-0053 d22). URN `step_enhancement:<id>`; activatable (`ArtifactKind.STEP_ENHANCEMENT`).

- **Fields:**
  - `id`, pattern `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`;
  - `schemaVersion`, `"1.0"`;
  - `promptFragments: List<Entry>`, each `Entry {slot: SlotId, path: String, fragment: PromptFragment}`. The entity
    **accepts an empty list**: the absent-key shape is refused by the D12 rule (`NO_FRAGMENTS`), never by the entity's
    constructor. A breach there would be recorded by `collect` as a `domain` violation named by the id, not the file
    (`DomainRepositoryService.java:761-763`) (third analysis, A10).

  There is no target, range, anchor, position or priority field (d3, d5, d7; C-003). Guards and emits are not in the
  shape (D6, #434).
- **The `enhances` edge** is not a field. It lives in the pack's graph documents:
  `step_enhancement:<id> → [<pack>::]action:<type>/<step>`. The target step is read from it, never from the directory.
- **Place:** held in `PackLayout`, because the type directory is not derivable from the id. A whole-pack write puts the
  file back where it was read. The binding's `fileName` refuses, so a canonical layout cannot place one at the pack
  root (A12).
- **Same id:** a higher level's copy replaces a lower one by layer override, and a sibling contest settled by a
  descriptor `overrides` entry leaves one winner. Only the winning copy's fragments are served (A11).

Rows judged at C, other than the existing sibling-conflict rule, and rows judged at R are marked *(phase 2)*: record
only — phase 2 (#438) owns them.

| Invariant | Refusal | When |
|---|---|---|
| Schema-valid (closed, `schema_version` const) | schema violation (`artifact_schema_error` in V; a schema violation at L). A schema-invalid file gets no semantic rule (T-08) | V, L |
| `prompt_fragments: []` | schema violation (`minItems`), **not** `prompt_fragment_invalid`. Documented, and tested beside the absent-key shape (T-08) | V, L |
| `prompt_fragments` absent (D12) | `prompt_fragment_invalid` | V, L |
| An entry's `slot` breaks `<namespace>:<name>` (FR-005) | schema violation (the `slot` pattern), **not** `prompt_fragment_invalid`; the file gets no semantic rule | V, L |
| `id` equals the file stem | schema violation, **not** `prompt_fragment_invalid` (operator ruling A1): the store's stem rule, since the binding's `idIsFileStem()` is true. L: a `schema` violation at the file (`FilesystemPackStore.readKind`, :686-690, through `FileStems.mismatch`); V: a `const` violation at `/id` (`FileSystemPackInspector.misnamedArtifact`, :470-479). The file gets no semantic rule | V, L |
| The id (equal to the stem, by the row above) is unique in the pack across type directories (D8), judged before the generic duplicate-id rule | `prompt_fragment_invalid` at the second file in path order. **Path order** is one comparator, `PackPolicy.comparePaths` (`PackPolicy.java:989`, segment by segment, by code point), at V and L alike, never the store listing's `String` order: for `missions/plan/` and `missions/plan-x/` the two disagree, and the second file is the `plan-x/` one (third analysis A5). At L the second file is also **withheld** from `Pack.Builder` (operator ruling A2), so `Pack.guardKind`'s same-id rule (`Pack.java:148-152`) never breaches at `build()`. At V the generic `DUPLICATE_ARTIFACT_ID` is not raised for `step_enhancement` (A9). The files of one id share the URN, so their edges cannot be told apart: the edge rules are judged once for the id, at the first file, and a `STEM_REPEATED` file gets no other identity or edge fault (A2) | V, L |
| Each slot at most once per enhancement (d3, d6) | `prompt_fragment_invalid` | V, L |
| Each entry's companion exists beside the file | `prompt_fragment_invalid` | V, L |
| Exactly one `enhances` edge, target an `action:<type>/<step>` (D9, d7). When the count is not 1, the target and directory rules are skipped for that id (A2) | `prompt_fragment_invalid` | V, L, W |
| No `overrides` edge has the enhancement as its source, whatever its target, its own mission type included (R-OVR: an enhancement only adds) | `prompt_fragment_invalid` | V, L, W |
| The `<type>` directory equals the edge's `<type>` (D7, C-003) | `prompt_fragment_invalid` | V, L |
| Two packs at one level defining the same id | `pack_sibling_conflict` (C14, existing) | C |
| A qualified target names a pack of the `depends_on` closure that holds the step *(phase 2)* | `pack_reference_pack_missing` / `pack_reference_artifact_missing` ("holds the step" per OD-9) | C (and at registration by OD-4) |
| A bare target resolves in the enhancing pack itself *(phase 2)* | `pack_reference_unqualified` when it resolves only elsewhere (T-16) | C |
| The prompt in force for the target declares each named slot *(phase 2)* | `prompt_slot_missing`. Inactive enhancements: not refused (OD-1 (B), ruled); registration: judged too (OD-4 (i), ruled) | C |
| The target's integrity permits enhancement *(phase 2)* | OD-2 (A), ruled: inherited level; `500 pack_unreadable` on reads, `422 override_not_permitted` at registration | C |
| In force only through its mission type (E6) *(phase 2)* | none today: as built, never in force; PR C owns the E6 amendment (OD-6, ruled) | R |
| Rendered only while itself in force; nothing pulled in either way (d4, E12) *(phase 2)* | none (absent from the read) | R |

## Where each enhancement or fragment fault points (third analysis, A3, A4)

Checked against the code at `4b244b87`. "yaml" is the enhancement's `*.step-enhancement.yaml`, "fragment" the
companion's `*.prompt-fragment.md`. At registration the pointer is `violation.file()` (`WholePackStore.java:239-243`).

| Fault | Who judges it first | L / registration pointer and kind | V pointer |
|---|---|---|---|
| Companion unreadable by `MarkdownCodec` (CR, BOM); structure breach (blank, frontmatter: the parsed model's `frontmatter` key breaks the closed `prompt-fragment` schema); missing; named twice (same or another enhancement); a YAML or excluded path | the store's companion read, `FilesystemPackStore.withCompanions` (:785-819), before any rule | **yaml**, reason `<companion path>: <why>`; kind `companion` (hard-coded at :813) remapped to `prompt_fragment_invalid` by the graph-taking `assembly` (A3). The enhancement is left out of `read.elements`, so its yaml and its companions are also listed `unmodelled` (existing behaviour for every refused file, `WholePackStore.java:211-225`); the companions are **not** reclassified as orphans (A4) | codec and structure: **fragment** (the inspector's `readCompanion`, `FileSystemPackInspector.java:332-337`, mapped by the structure `prompt-fragment`); missing: **yaml** (`COMPANION_MISSING`); named twice: the **second naming yaml** (`COMPANION_SHARED`) |
| Slot marker in a fragment | rule `FRAGMENT_HAS_MARKER` | **fragment** (the enhancement is held) | **fragment** |
| Slot id grammar; `prompt_fragments: []`; an undeclared key; id ≠ stem (A1) | the schema / the store's stem rule | **yaml**, kind `schema` (not `prompt_fragment_invalid`); also listed `unmodelled` | **yaml** (`artifact_schema_error`; `const` at `/id` for the stem) |
| Repeated slot, edge count, non-step target, `overrides` edge, directory mismatch, absent `prompt_fragments` | rules `SLOT_REPEATED`, `EDGE_COUNT`, `EDGE_TARGET_NOT_STEP`, `OVERRIDES_EDGE`, `DIRECTORY_MISMATCH`, `NO_FRAGMENTS` | **yaml**, `prompt_fragment_invalid`; the enhancement and its companions stay held (no `unmodelled` entry) | **yaml** |
| Repeated id across type directories | rule `STEM_REPEATED` | the **second yaml** in path order, `prompt_fragment_invalid`; withheld from the builder (A2), so also listed `unmodelled` | the second **yaml** |
| Orphan fragment | `loadStaged` (L) / rule `COMPANION_ORPHAN` (V) | **fragment**, registration only | **fragment** |

At **W** (R-REL) there is no violation list: the relationship PUT answers a top-level `422` problem whose `code` is
`prompt_fragment_invalid` and whose detail names the enhancement's URN and the broken rule (`overrides` edge, second
`enhances` edge, or an edit that would replace its one `enhances` edge).

The FRAGMENT_BLANK and FRAGMENT_FRONTMATTER rules of WP03 stay as the pure statement of FR-004 and are unit-tested on
facts, but no pack reaches them: the companion read refuses those fragments first, at both moments. A companion that
the inspector could not read is marked unreadable in the facts, and no rule judges it again.

## Composition facts added (IC-06, `packs.infra.storage` → `packs.domain`) (moved to the phase-2 mission)

> **Record only — phase 2 (#438) owns it.** Kept as input to the phase-2 mission's re-plan; it binds no work package
> of this mission.

Computed in `DomainRepositoryService.compositionFacts` (A7) and cached with the other facts:

- `declaredSlots: Map<action URN, List<SlotId>>`, from each step prompt's canonical text.
- `filledSlots: Map<step_enhancement URN, (target action URN, Set<SlotId>)>`, from the single `enhances` edge and
  the entries.

The compose check is a pure function over the merged facts, linear in enhancements, and the same for both D13
branches. Branch B filters by the in-force answer afterwards. The same facts feed OD-6's binding and OD-9's step
predicate.

## EffectiveStepPrompt (read model, `activations.domain`; wire `EffectiveStepPrompt`) (moved to the phase-2 mission)

> **Record only — phase 2 (#438) owns it.** Kept as input to the phase-2 mission's re-plan; it binds no work package
> of this mission.

Kept as the record. The rulings since this section was written: OD-5 chose `409 mission_type_not_in_force` for a
mission type held but not in force (not the squad's `200`), OD-7 adds a `diagnostics` member to this answer, and OD-8
lists a slot holding legacy text with no flag. The phase-2 mission re-validates the section after PR C merges.

What the charter serves for one step under one activation; the renderer places it (d9). Built from one verified read
and one compose.

- `activationId`, `revision` (activation), `governanceFingerprint` (closure digest, as the effective reads),
  `missionType`, `action`.
- `prompt`: the prompt in force (the layer-merge winner, possibly an override), as its canonical text, frontmatter
  included (D5, A5). Outside the slots it is the base text, with the same meaning (d7).
- `slots: List<EffectivePromptSlot>`, in declaration order; **every** declared slot, empty or not (D5). A slot that
  still holds legacy text: OD-8.
  - `EffectivePromptSlot {slot: SlotId, fragments: List<EffectivePromptFragment>}`.
  - `EffectivePromptFragment {packId, packName, level, enhancement (declared id, no kind prefix), text (canonical
    render)}` (C-04, C-14, T-03).
- `_links.self`. The action context gains `_links.prompt` pointing here (C-05, after PR C).
- A mission type the closure holds but the activation does not put in force: OD-5. The squad recommends a required
  `missionTypeInForce: boolean`, with every slot listed with `fragments: []`.

| Invariant | How it is held | When |
|---|---|---|
| Fragments of a slot are totally ordered by (level built-in < org < project, descriptor `name`, enhancement id), code point | one comparator, in `packs`. The adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-001 test runs 100 reads and every permutation of three packs' configured order, comparing the body **and** the ETag, on a fixture where id, configured name and descriptor name sort differently (T-09) | R |
| Nothing merged or de-duplicated (d6) | one item per (winning enhancement, entry) | R |
| Only enhancements in force contribute (d4) | filtered by the in-force answer of the same session (needs OD-6) | R |
| A slot no fragment fills is listed with `fragments: []` | from the slot parse, not from the fragments | R |
| Unknown activation / mission type / action | `404 activation_not_found` / `mission_type_not_found` / `action_not_found` | R |
| A bad `activationId`, `missionType` or `action` | `400` without a code (C-09) | R |
| Same activation state ⇒ identical body and `ETag`; a fragment byte edit ⇒ new ETag | `EffectiveTag(revision, closure fingerprint)` + path (T-04) | R |
| Work linear in the closure's step enhancements | adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-002 count gate at 1,000 and 2,000, through named hooks (T-10) | R |

## Code projection

| Finding (`PackFindingCode`) | Severity | Projects to |
|---|---|---|
| `PROMPT_SLOT_MALFORMED` | blocking | violation kind `prompt_slot_malformed` (`RefusalCode.PROMPT_SLOT_MALFORMED`, named by `PackPolicy`) |
| `PROMPT_SLOT_NOT_EMPTY` | blocking | violation kind `prompt_slot_not_empty` (`RefusalCode.PROMPT_SLOT_NOT_EMPTY`) |
| `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` | warning | `Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY`, on the warnings list |
| `PROMPT_FRAGMENT_INVALID` | blocking | violation kind `prompt_fragment_invalid` (`RefusalCode.PROMPT_FRAGMENT_INVALID`) |

`PackFindingTest` (`service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java`) pins the
code set by severity and wire value (`LOAD_PATH_BLOCKING` :17-24, the warning set :30-44, the no-wire-value loop
:67-77). Each new code extends its expected sets in the commit that adds it (operator ruling OD-10 row 11): WP04 adds
`PROMPT_FRAGMENT_INVALID` to `LOAD_PATH_BLOCKING`; WP05 adds `PROMPT_SLOT_MALFORMED` and `PROMPT_SLOT_NOT_EMPTY` to it,
and `PROMPT_SLOT_NOT_EMPTY_UNVERSIONED` to the warning set, the severity expectation and the `:70` skip (its `diagnostic()` projection
is required: `VocabularyEqualityTest` calls `diagnostic()` on every WARNING code).

The relationship write (R-REL) is not a pack finding: `RelationshipRegistrationService` throws
`CharterRefusal(RefusalCode.PROMPT_FRAGMENT_INVALID, …)`, which `putPackRelationship` answers `422` through the code's
`x-refusal` `operations` entry and `RefusalHandler`'s per-operation table.

`prompt_slot_missing` is a compose refusal (`RefusalCode.PROMPT_SLOT_MISSING`), not a pack finding, raised by the
phase-2 mission. Nothing calls
`PackFindingCode.refusal()` outside `PackFinding` (`ReservedRefusalCodesTest.offWireRules`).
