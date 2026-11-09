# Contract delta of this mission

Planning material for IC-04 and IC-05 of [plan.md](../plan.md). Nothing here is bundled; the files named below are
created or edited under `contracts/` by those concerns, each with a dated fragment under `contracts/charter/changes/`
(C-001, ADR-0022, 0.x line, no released baseline).

## `effective-step-prompt.openapi.yaml` (IC-05, after PR C merges)

An OpenAPI 3.1 fragment. Its `x-files` map holds, per key, the content of one contract file with the `$ref` paths it
will use in `contracts/charter/`:

- `paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml`: the built operation
  `getEffectiveStepPrompt` (tag `Activation`, so `ActivationController` implements it). It follows the effective
  reads' conventions: `IfNoneMatch`, `ETag` = `EffectiveETag`, `304` `NotModified`, the three `Charter-*` headers, a
  `400` Problem without a code for a bad `activationId`, `missionType` or `action` (C-09), `404` through
  `ActivationNotFound` (activation, mission type, action), `500` through `OfferingUnreadable`; no `501` and no
  `x-reserved` (`built-operation-declares-no-501`). The held-but-not-in-force mission type is plan OD-5.
- `schemas/EffectiveStepPrompt.yaml`, `EffectivePromptSlot.yaml`, `EffectivePromptFragment.yaml`: closed objects
  (`additionalProperties: false`), each property with `x-derived`, as the effective-read schemas carry; `slot` is
  wrapped in `allOf` with its own `x-derived` so `resource-property-cited` passes (C-10).
  `EffectivePromptFragment` carries a required `packName`, the descriptor name, which is decision 5's second ordering
  key; `packId` is the configured identifier (C-04).
- `schemas/SlotId.yaml`: the slot grammar of ADR-0066 decision 2 (the Java value is `kernel.markdown.SlotId`, A1).
- Two examples (one with two org packs whose descriptor names sort opposite to their ids), and one
  `prompt-slot-missing` example added to `responses/OfferingUnreadable.yaml`, whose shared description is scoped to
  "the operations that build the layer merge"; each such operation names the code itself (C-08).
- `openapi.yaml` gains the path; `schemas/_index.yaml` lists the new schemas; `docs/development/hypermedia-hateoas.md`
  lists the read under `self` and gains a `prompt` rel for the action context's new `_links.prompt`, landing with the
  read with a `RelationTableParityTest` update (C-05, D5 sub-ruling).
- Lists that name operations are updated with it (C-11): `headers/EffectiveETag.yaml`,
  `headers/CharterSourceFingerprint.yaml`, `docs/api/service-contract.md`, `docs/development/http-status-codes.md`,
  `docs/development/rest-resource-conventions.md`. `checkRefusalExamples` does not catch an incomplete list.

Classes: `### Added` (operation, schemas, examples, the `prompt` link).

## `CharterRefusalCode` changes

| Code | Before | After | Concern | Class |
|---|---|---|---|---|
| `prompt_slot_malformed` | reserved, `raised: false`, 500 | judged by the base-prompt load check; a registration violation at the prompt file, `pack_unreadable` on composing reads; `raised` per OD-3 | IC-03, IC-04 | `### Changed`; the refusal of content that loaded before is `### Breaking` |
| `prompt_slot_not_empty` | reserved, 500 | the same, under descriptor `schema_version: "1.0"`; the description points to `DiagnosticCode.prompt_slot_not_empty` for unversioned packs (C-15) | IC-03, IC-04 | `### Changed`; `### Breaking` for versioned packs |
| `prompt_fragment_invalid` | reserved, 500 | the same, for the enhancement and fragment load checks; description broadened to "an invalid step enhancement or prompt fragment" (D10) | IC-02, IC-04 | `### Changed`; `### Breaking` (content that validated before) |
| `prompt_slot_missing` | reserved, 500 | raised by the compose check; scope per D13 (OD-1); `operations: {getPackRegistration: 422}` if OD-4 | IC-05, IC-06 | `### Changed`; `### Breaking` (a compose that answers today is refused; with OD-4 also a registration accepted today) |

- No enum value is added or removed (D10), so `WireTokenParityTest` and `VocabularyEqualityTest` need no new row.
- How the three load codes are raised is plan OD-3. The mechanism planned either way: each fault is a violation
  `{pointer: <pack-relative file>, code: prompt_*}` of `422 validation_failed` on `getPackRegistration` (every fault
  at once, nothing thrown), and a composing read answers `500 pack_unreadable` naming the file. Under OD-3 (a) the
  `x-refusal` definition of `raised` is amended to count such a violation code, so `drg_schema_error` flips to raised
  by the same rule (its own `### Changed`), and the `_shared` texts are amended: `Problem.violations` ("Only on a 422
  activation_invalid") and `RefusalViolation.pointer` ("a JSON Pointer into the request body") (C-02). Under (b) each
  code gains `getPackRegistration: {status: 422}` and a throw path.
- `errors.feature`'s raised-code table (:192-238) and its prose (:171-191, including the stale "no operation reaches
  the whole-pack load") change with the raise; `ReservedRefusalCodesTest`'s javadoc (:44-52) and, under OD-3 (a),
  `OFF_WIRE` and `offWireRules()` too. These are in the D19 addendum (plan OD-10).
- The paragraph "Four codes are reserved for ADR-0066 … until #424" in the `CharterRefusalCode` description is
  rewritten (IC-04 for three codes, IC-05 for the fourth). It keeps "an active or inactive enhancement" in
  `prompt_slot_missing`'s clause until D13 is ruled (C-13). Its last sentence ("… and artifact_locked keep their
  existing meanings for the other ADR-0066 refusals") is reworded whatever D14 says, so it no longer suggests
  `artifact_locked` for a step-integrity refusal (C-07). The same file's "two reads that stay reserved" sentence is
  PR C's (pinned by `ContractDescriptionsTest`), so this mission does not edit it.
- `DiagnosticCode.yaml`: "reserved for #424" is removed from `prompt_slot_not_empty`'s description (`### Changed`).

## Other contract edits (IC-04)

- `### Changed`, no shape change (C-12):
  - `PUT /activations/{activationId}/selections/step_enhancement/{artifactId}` answers `200` for a held enhancement
    instead of `404 artifact_not_found`, and preset `activated_step_enhancements` ids are no longer stale.
  - A registration of a pack carrying step enhancements succeeds (today `422` "unmodelled",
    `paths/packs_packId.yaml:33-34`); the whole-pack write keeps the enhancement files.
  - `listEffectiveArtifacts`, `getActivationSkippedReport` and `ActivationChange.dropped` carry `step_enhancement`
    items, or stop dropping them.
- Each `### Breaking` fragment says "core: nothing while it is not a consumer (epic #186)" and the read-side effect:
  every verified read of a closure holding such a pack answers `500 pack_unreadable` (C-12).
- `contracts/packs/schemas/step-enhancement.schema.json`: description only: the rules of D8–D12, "one companion per
  entry, any stem matching the pattern, each named by exactly one entry" (D11), guards and emits as fields built under
  #434 (D6). The `path` property's description drops "the `<id>.prompt-fragment.md` companion". It also says that
  `prompt_fragments: []` is refused by the schema (`minItems`) and an absent key by the D12 rule (T-08). No property
  changes.
- `contracts/packs/file-kinds.yaml`: the step-enhancement comment, same wording.
