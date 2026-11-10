# Contract delta of this mission (phase 1)

Planning material for IC-04 of [plan.md](../plan.md), done by WP04 and WP05. Nothing here is bundled; the files named
below are edited under `contracts/` by those work packages, each with a dated fragment under
`contracts/charter/changes/` (C-001, ADR-0022, 0.x line, no released baseline).

Phase 1 changes only:

- the `CharterRefusalCode` descriptions of the three load codes, and the `x-refusal` definition of `raised` (OD-3 (a));
- the registration result's new `diagnostics` member (OD-7), drafted in
  [registration-diagnostics.fragment.yaml](registration-diagnostics.fragment.yaml);
- the descriptions and fragments listed under "Other contract edits (IC-04)".

**Moved to the phase-2 mission (operator ruling, 2026-11-09).** The draft of the effective-prompt read
(`effective-step-prompt.openapi.yaml`: the `getEffectiveStepPrompt` path, the `EffectiveStepPrompt`,
`EffectivePromptSlot`, `EffectivePromptFragment` and `SlotId` schemas, their examples, the `prompt_slot_missing`
example, `409 mission_type_not_in_force`, the prompt read's `diagnostics` member and the action context's
`_links.prompt`) and this README's former IC-05 section are no longer in this mission. They are parked with the
phase-2 mission's material and re-validated after PR C merges.

## How a load fault reaches the wire (canonical)

This is the one statement of operator ruling OD-3 (a) and of the load seat (plan errata E-1, E-3). The spec, plan,
data-model, the fragment draft, WP04, WP05 and WP06 point here instead of restating it.

- **One seat for every load fault.** A fault judged at load (FR-004..FR-007, R-OVR) is recorded as a
  `PackElementViolation` at its pack-relative file, with `kind` equal to its wire token (`prompt_fragment_invalid`,
  `prompt_slot_malformed`, `prompt_slot_not_empty`), chosen by `PackPolicy`. It is recorded in
  `DomainRepositoryService.assembly`, in a form that also takes the pack's graph read (WP04 adds it; WP05's slot check
  joins the same assembly). Both load paths call that form:
  - `WholePackStore.load`, which reads the graphs before the assembly, and through it the registration's `loadStaged`;
  - `DomainRepositoryService.assemble`, which reads the graphs too, and through it `readable`, which every composing
    read uses (`compositionFacts`, `:599-608`).

  Nothing is thrown for such a fault, so `WholePackStore.load` keeps its partial-failure contract (:53-56). The
  three-argument `assembly` stays for its other callers (`ArtifactFiles`, tests), which hold no graph read. The edge
  rules (the single `enhances` edge, its target, the directory, R-OVR) run only over a graph read with no violation;
  a graph that cannot be read is refused on its own (`drg_schema_error` at registration, `pack_unreadable` on reads).
- **What the graph-taking form does before the existing assembly** (third analysis; checked at `4b244b87`). It builds
  `StepEnhancementFacts` from the read's DSOs and the graph read, runs the rules, and derives a judged copy of the
  `FilesystemPackStore.PackRead` record (:143-150) that it hands to the three-argument form (:684):
  - **A3:** each read violation of kind `companion` whose file is a `*.step-enhancement.yaml` gets the kind
    `prompt_fragment_invalid` (token from `PackPolicy`), same file, same reason, same position. The store hard-codes
    `"companion"` (`FilesystemPackStore.java:813`) and `KindBinding` has no hook for it, so the remap sits here, the
    one seat both load paths reach; `FilesystemPackStore.java` (beyond WP01's A2-harness write) and `KindBinding.java`
    are not edited for it.
  - **A2 (operator ruling):** each file the rules answer `STEM_REPEATED` for (the second and later file of one id, in
    path order) is dropped from the copy's step-enhancement elements, so `collect` (:736-765) never adds it to
    `Pack.Builder` and `Pack.guardKind`'s same-id rule (`Pack.java:148-152`) never breaches at `build()`. Without this,
    `WholePackStore.load` throws `VALIDATION_FAILED` (:206-210), which `loadRefusingAsInvalid` (:472-481) turns into
    one `pack` violation, and `assemble` throws.
  - The rule faults are appended last; WP05 adds a `List<PackFinding> warnings` out-parameter beside `places` (A8).
- **At registration.** `getPackRegistration` answers `422 validation_failed`, and every fault is a violation
  `{pointer: <pack-relative file>, code: prompt_*}`, all at once (`loadStaged` copies `violation.kind()` into the
  code, `WholePackStore.java:239-243`). An orphan fragment is refused here and in validate mode; a composing read does
  not hold it, like every unmodelled file.
- **On a composing read.** The read answers `500 pack_unreadable` naming the **first** violation in assembly order:
  the store's read violations, each bound kind in `KindBindings` order, the templates, the presets, the pack document,
  then the slot faults (WP05) and last the step-enhancement rule faults (WP04), those in WP03's (pack-relative path,
  `StepEnhancementFault`) order. `Pack.java` is unchanged and nothing re-sorts; the order is deterministic because the
  store's listing is sorted. WP04 pins it with a test (plan IC-02, precedence 4). No read answers a prompt code as the
  top-level `code`.
- **On the relationship write (operator ruling R-REL, 2026-11-09).** `putPackRelationship` refuses, before the commit
  of `registered.graph.yaml`, an edge that would give a held step enhancement an `overrides` edge, a second `enhances`
  edge, or replace its one `enhances` edge: a top-level `422` problem with `code: prompt_fragment_invalid`, nothing
  written. It is the one operation that carries the code top-level: `prompt_fragment_invalid` gains
  `operations: {putPackRelationship: {status: 422}}`, `RefusalHandler`'s per-operation table the same entry, and the
  path's `422` description names it (WP04; plan IC-02 "Where").
- **What "raised" means (OD-3 (a)).** The `x-refusal` definition of `raised` (`CharterRefusalCode.yaml:59-71`) is
  amended so that `raised: true` includes "raised as a violation code of `422 validation_failed`". Beside it the
  contract says what `status` means for such a code (A19): on the load paths no problem carries it as its top-level
  `code` (the one exception is `prompt_fragment_invalid` on `putPackRelationship`, R-REL, declared by its
  `operations` entry); the problem
  that carries it answers `422` at registration, and a composing read answers `500 pack_unreadable`. The declared
  `status: 500` is kept (no shape change) and is read as the status of the read that meets the fault.
- **`drg_schema_error` flips to raised** by the same definition. It already reaches a registration as a violation
  code (`loadRefusingAsInvalid`, `WholePackStore.java:472-481`). Its single owner is WP04, with its own `### Changed`.
- **The `_shared` texts** are amended (C-02): `Problem.violations` ("Only on a 422 activation_invalid") and
  `RefusalViolation.pointer` ("a JSON Pointer into the request body").

## `CharterRefusalCode` changes

| Code | Before | After | Concern | Class |
|---|---|---|---|---|
| `prompt_slot_malformed` | reserved, `raised: false`, 500 | judged by the base-prompt load check; a registration violation at the prompt file, `pack_unreadable` on composing reads; `raised: true` under the amended definition (OD-3 (a)) | IC-03, IC-04 (WP05) | `### Changed`; the refusal of content that loaded before is `### Breaking` |
| `prompt_slot_not_empty` | reserved, 500 | the same, under descriptor `schema_version: "1.0"`; the description points to `DiagnosticCode.prompt_slot_not_empty` for unversioned packs (C-15) | IC-03, IC-04 (WP05) | `### Changed`; `### Breaking` for versioned packs |
| `prompt_fragment_invalid` | reserved, 500 | the same, for the enhancement and fragment load checks; description broadened to "an invalid step enhancement or prompt fragment" (D10); `operations: {putPackRelationship: {status: 422}}` for the relationship write (R-REL) | IC-02, IC-04 (WP04) | `### Changed`; `### Breaking` (content that validated before; a relationship write that answered `2xx`) |
| `drg_schema_error` | `raised: false` (a registration violation code) | `raised: true` by the same amended definition (OD-3 (a)) | IC-04 (WP04) | `### Changed` |

`prompt_slot_missing` stays reserved in this mission. Raising it is the phase-2 mission's.

- No enum value is added or removed (D10), so `WireTokenParityTest` and `VocabularyEqualityTest` need no new row.
- How the codes reach the wire: [the canonical section above](#how-a-load-fault-reaches-the-wire-canonical).
- `errors.feature`'s raised-code table (:192-238) and its prose (:171-191, including the stale "no operation reaches
  the whole-pack load") change with the raise; in `ReservedRefusalCodesTest`, the javadoc's off-wire paragraph (:48-52), `OFF_WIRE`
  (:69-72), `offWireRules()` (:106-131) and the assertion `unraisedCodes().contains("DRG_SCHEMA_ERROR")` (:189-194,
  now expected in `raisedCodes()`) change too. These are rows 6–9 of the D19 addendum (plan OD-10, approved).
- **`ReservedRefusalCodesTest` gains no list edit.** `raisedCodes()` and `unraisedCodes()` are read from the YAML
  flags, so flipping a flag is the whole move. The array at :290-345 is the `NamesEveryCode` probe, which
  `theProbeNamesEveryCode` requires to name every `RefusalCode` value; it is never edited (plan erratum E-2).
- Two more paragraphs of the `CharterRefusalCode` description become false with the flip and are rewritten in WP04's
  raise commit: :32-35 ("pack_level_mismatch and drg_schema_error are no longer raised … not reached by any
  operation"; it then says `drg_schema_error` is raised as a registration violation code and only
  `pack_level_mismatch` stays unraised) and :58-60 ("outside the two classes no operation reaches (the whole-pack
  load and its findings)"; it then names only `PackFindingCode`'s off-wire projection). The amended definition
  (:62-71) also drops its example "a code that only a part no operation reaches refuses with is not raised
  (drg_schema_error)". `ContractDescriptionsTest` pins none of these texts today.
- The paragraph "Four codes are reserved for ADR-0066 … until #424" in the `CharterRefusalCode` description is
  rewritten for the three load codes. It keeps `prompt_slot_missing` reserved and keeps "an active or inactive
  enhancement" in its clause: the phase-2 mission narrows it with the raise (OD-1, C-13). Its last sentence ("… and
  artifact_locked keep their existing meanings for the other ADR-0066 refusals") is reworded so it no longer suggests
  `artifact_locked` for a step-integrity refusal (C-07). The same file's "two reads that stay reserved" sentence is
  PR C's (pinned by `ContractDescriptionsTest`), so this mission does not edit it.
- `DiagnosticCode.yaml`: "reserved for #424" is removed from `prompt_slot_not_empty`'s description, which says it is
  delivered in the registration result's `diagnostics` (`### Changed`).

## The registration result's `diagnostics` member (OD-7, WP05)

- `contracts/charter/schemas/PackRegistration.yaml` gains `diagnostics`: an array of `Diagnostic.yaml` items listing
  load warnings (today only `prompt_slot_not_empty` for an unversioned descriptor), empty when there are none. The
  object stays closed, so the addition is `### Breaking` (OD-7). The shape is bound to the draft: the member is
  **optional** (not in `required`, like `outcome` and `notices`), present in the result of a succeeded registration,
  and **`getPack` carries none**. WP05's `### Breaking` fragment states exactly that.
- `contracts/charter/examples/PackRegistrationStatus.succeeded.yaml` shows the member.
- The draft is [registration-diagnostics.fragment.yaml](registration-diagnostics.fragment.yaml), with the three
  `x-refusal` entries as they read after phase 1.

## Other contract edits (IC-04)

- `### Changed`, no shape change (C-12):
  - `PUT /activations/{activationId}/selections/step_enhancement/{artifactId}` answers `200` for a held enhancement
    instead of `404 artifact_not_found`, and preset `activated_step_enhancements` ids are no longer stale.
  - A registration of a pack carrying step enhancements succeeds (today `422` "unmodelled",
    `paths/packs_packId.yaml:33-34`); the whole-pack write keeps the enhancement files.
  - A selected step enhancement is still reported not in force until PR C's E6 amendment lands (OD-6). What
    `listEffectiveArtifacts`, `getActivationSkippedReport` and `ActivationChange.dropped` show for it is delivered,
    with scenarios, by PR C and the phase-2 mission (#438); this mission's fragment does not claim it.
- Each `### Breaking` fragment says "core: nothing while it is not a consumer (epic #186)" and the read-side effect:
  every verified read of a closure holding such a pack answers `500 pack_unreadable` (C-12).
- `contracts/packs/schemas/step-enhancement.schema.json`: description only: the rules of D8–D12, "one companion per
  entry, any stem matching the pattern, each named by exactly one entry" (D11), guards and emits as fields built under
  #434 (D6). The `path` property's description drops "the `<id>.prompt-fragment.md` companion". It also says that
  `prompt_fragments: []` is refused by the schema (`minItems`) and an absent key by the D12 rule (T-08). The top-level
  description's sentence "A semantic check beside this schema requires the id to equal the file stem … violations are
  prompt_fragment_invalid" is rewritten (operator ruling A1): an id that differs from its file stem is refused by the
  store's stem rule as a schema violation, like the `slot` pattern and `minItems`; the repeated slot, the missing,
  shared or orphan companion, the edge rules, the directory, a repeated id and the absent key are
  `prompt_fragment_invalid`. No property changes. Owner: WP04 (T021), which owns the file; it is contract text, so
  WP04's `### Changed` fragment carries a line for it.
- `contracts/packs/file-kinds.yaml`: the step-enhancement comment, same wording.
- `contracts/charter/paths/packs_packId_relationships.yaml`: the `422` description names `prompt_fragment_invalid` for
  an edge that would break a held step enhancement's edge rules (R-REL, WP04; `### Breaking`).
