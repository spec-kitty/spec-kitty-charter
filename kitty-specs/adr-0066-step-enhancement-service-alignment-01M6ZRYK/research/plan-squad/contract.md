# Plan squad: contract correctness and versioning lens (reviewer-renata)

Mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK`, branch `issue-424-428-adr-0066-service-alignment`
(HEAD 92026b5d). Read-only review at the plan point.

**Profile applied.** `spec-kitty agent profile show reviewer-renata` resolved; mode `design-review`. Directives 001
(architectural integrity), 024 (locality), 032 (conceptual alignment), 041 (no fake green); tactics
code-review-incremental, language-driven-design. Squad procedure `adversarial-squad-deployment`: every finding below
needs one disposition (accepted / changed / deferred_with_rationale) with an evidence location. Charter: "Breaking the
OpenAPI contract" (ADR-0022) and "Divergence from the charter's own model" are the two never-go-wrong rules this lens
guards.

**Where this lens does not reach.** I did not judge the Java design beyond the points where it decides a wire code, the
D13/D14 merits, or the performance gates.

## Findings

### C-01 [BLOCKER] The plan never says which operation answers the three load codes, so FR-011 and SC-004 can be met without the codes ever reaching the wire

- **Evidence.**
  - Plan IC-04 (`plan.md:328-331`) says "`raised: true`, default 500 on reads", but it names no read that answers
    `prompt_slot_malformed`, `prompt_slot_not_empty` or `prompt_fragment_invalid` as the problem's top-level code.
  - Today a read turns every read violation into `pack_unreadable`
    (`service/packs/.../infra/storage/DomainRepositoryService.java:600-608`, `readable()`). A companion that fails
    `checkCompanion` becomes violation kind `companion` (`FilesystemPackStore.java:813`), and `checkCompanion` throws
    `IllegalArgumentException` (`MissionTypeBinding.java:133-136`). The plan puts the fragment frontmatter check in
    `checkCompanion` (`plan.md:226`). On a read that fault answers `pack_unreadable`, and at registration it gets
    violation code `companion`. It is never `prompt_fragment_invalid`, although `data-model.md:45` promises that code.
  - The contract already has a precedent: a code carried only as a violation code inside `validation_failed` counts
    as **not raised**. `drg_schema_error` is thrown by `WholePackStore.load` (`WholePackStore.java:196-199`), which
    registration reaches through `loadStaged` → `loadRefusingAsInvalid` (`:236-238`, `:471-481`). Yet the contract
    marks it `raised: false` (`CharterRefusalCode.yaml:236-239`; `ReservedRefusalCodesTest.java:192-194`). Under N1's
    default (a), the same rule leaves the three prompt codes unraised.
  - `ReservedRefusalCodesTest` can be passed without any real raise:
    - The reverse check (`:233-244`) needs only *some* main class to name the constant.
    - `WholePackStore` and `PackFindingCode` are the off-wire classes (`:69-72`), and the plan has exactly those
      classes name the codes.
  - `offWireRules()` (`:106-128`) let only `PackFinding` call `PackFindingCode.refusal()`. The plan has
    `WholePackStore.load` refuse "via `PackFindingCode.refusal()`" (`plan.md:275-276`), which breaks that rule.
  - The test's javadoc says "No operation reaches either" (`:48-52`). It has been stale since PR A, because
    `PackRegistrationService` reaches `WholePackStore` through the `WholePackWriteStore.Loader` port
    (`PackRegistrationService.java:151,467`).
- **Fix.** Before IC-04's contract text is written red first, state for each of the three codes:
  1. which read answers it as a top-level `500` code, if any. The code must be thrown as a `CharterRefusal` with that
     code. Recording it as a read violation is not enough, because a read violation becomes `pack_unreadable`.
  2. what registration answers (N1, below).
  3. what "raised" means for it. Either some operation answers it as a top-level code, or the `x-refusal` definition
     (`CharterRefusalCode.yaml:59-71`) is amended to count violation codes. If it is amended, `drg_schema_error` flips
     to raised by the same rule, and that is a `### Changed` of its own.

  Then update the off-wire set to match:
  - `OFF_WIRE`, `offWireRules()` and their javadoc in `ReservedRefusalCodesTest`.
  - The stale sentence in `errors.feature:186-188`.

  Make SC-004 "at least one scenario that raises it" say *where* the code appears: the top-level `code` on operation X,
  or a violation `code` inside `validation_failed`.
- **Operator.** Yes, folded into N1. Escalation class (b) shared seam/contract + (f) versioning mechanics.

### C-02 [MAJOR] N1(a) as written does not report "every fault at once" and does not name the file

- **Evidence.** `plan.md:490-492` says "every fault at once" with `{pointer: <file>, code: prompt_*}`. That holds only
  for faults recorded as `Pack` violations: `loadStaged` maps `pack.violations()` with `code = violation.kind()`
  (`WholePackStore.java:240-243`). A fault thrown from `load` is wrapped into **one** violation with pointer `"pack"`
  (`:471-481`), and the plan throws (`plan.md:275-276`). Two contract texts also contradict N1(a)'s promise:
  - The shared `Problem.violations` says "Only on a 422 activation_invalid" (`contracts/_shared/schemas/Problem.yaml`,
    `violations`).
  - `RefusalViolation.pointer` is "an RFC 6901 JSON Pointer into the request body"
    (`_shared/schemas/RefusalViolation.yaml`), but registration uses pack-relative file paths.
- **Fix.** Record each prompt fault as a `PackElementViolation` at its file, with kind equal to the refusal token
  (`prompt_fragment_invalid`, …), so `loadStaged` lists them all. Map the frontmatter fault to `prompt_fragment_invalid`
  explicitly. Amend the two `_shared` descriptions in IC-04: violations also appear on a registration's
  `validation_failed`, and a registration violation's pointer is a pack-relative path. That is `### Changed` wording.
  It is existing drift, but N1(a) makes it carry weight.
- **Operator.** No, beyond the N1 ruling.

### C-03 [MAJOR] `prompt_slot_missing` and FR-009 are judged only on reads, so a registration that breaks a slot succeeds and then every read answers 500

- **Evidence.** IC-05/IC-06 give `prompt_slot_missing` a plain `500` (`plan.md:349-352`). The fragment's `x-refusal`
  sketch has no `operations` map (`contracts/effective-step-prompt.openapi.yaml:335-339`). The contract's rule: the
  twelve operations that judge a write over the closures its pack joins answer the composition and relation codes as
  `422`, "because the request is what leaves the packs unable to compose" (`CharterRefusalCode.yaml:135-142`; impact
  check `ImpactRelations.java:208-215`). A project registration that ships a prompt override without a filled slot is
  exactly that kind of request (ADR-0066 Risks, `docs/adr/0066-*.md:174-176`).
- **Fix.** IC-06 judges the slot-declared check, and FR-009's target resolution and integrity, in the registration
  impact check over the closures the pack joins, for the D13 branch that applies. Give `prompt_slot_missing` an
  `operations` map with `getPackRegistration: 422`. The per-artifact writes and the two DELETEs can also rebuild the
  merge; add them to the map where they do. Reads keep `500`. Add a registration scenario. Class: `### Changed` (raised
  with a map) and `### Breaking` (a registration accepted today is refused).
- **Operator.** Yes, class (b), alongside D13: it decides where branch A or B is judged.

### C-04 [MAJOR] The decision-5 ordering key is "the pack's name", but the answer exposes only `packId` (the configured identifier)

- **Evidence.**
  - ADR-0066 d5 orders by "the pack's name" (`docs/adr/0066-*.md:106-109`). `data-model.md:96` and the fragment
    (`effective-step-prompt.openapi.yaml:31-33,192-193`) say the same.
  - `EffectivePromptFragment.packId` is "the configured identifier" (`:212-219`).
  - The contract keeps three pack identities apart (`schemas/RefusalPack.yaml`: `descriptorPackId`, `packId`,
    `name`).
- **Effect.** A client, the AS4.1 scenario and the NFR-003 test cannot check the order from the answer. An implementer
  may also sort by `packId`, which would diverge from d5.
- **Fix.**
  - Add a required `packName` (the descriptor `name`, `x-derived` from `pack.yaml`) to `EffectivePromptFragment`. It
    costs nothing now (`### Added`, new schema). After release it would be `### Breaking`
    (`contracts/lint/README.md:51-58`).
  - Write the key in the description and `x-derived` as (level, descriptor name, enhancement id), by code point.
  - Give the example two packs at one level with names that sort differently from their ids.
- **Operator.** No.

### C-05 [MAJOR] The plan drops a binding sub-ruling of D5 (`_links.prompt` on the action context)

- **Evidence.** In grounding §6 D5 (`research/grounding.md:271+`, item 5), the sub-decisions include "add
  `_links.prompt` on the context (recommended, after PR C)". The operator accepted "D1–D22 … as recommended"
  (`grounding.md:29`). The plan treats the other two D5 sub-decisions as binding (`research.md:14-17`) but makes this
  one "offered, not planned" (`plan.md:354-355`).
- **Fix.** Plan it in IC-05/IC-07, after PR C, when the files are no longer PR C's:
  - a `prompt` row in `docs/development/hypermedia-hateoas.md`;
  - a `RelationTableParityTest` update;
  - the link in the action-context answer and example. It must land with the built read, because no relation points
    at an unbuilt operation (`schemas/Links.yaml`).
  - a fragment. `Links` is an open map, so this is no shape change.

  Otherwise, record an explicit operator waiver.
- **Operator.** Only to waive it, class (b). Otherwise accept as planned work.

### C-06 [MAJOR] N3: `404 mission_type_not_found` for a mission type that is held but not in force breaks the code's meaning and the dispatch rule

- **Evidence.**
  - `mission_type_not_found` means "a mission type the activation's closure does not hold"
    (`CharterRefusalCode.yaml:29-30`). `ActivationNotFound` covers "what the request names does not exist"
    (`responses/ActivationNotFound.yaml:1-4`).
  - The sibling read answers a mission type that is not in force with an empty context and the reason in the skipped
    report (`paths/activations_..._context.yaml:11-13`).
  - The fragment's "the detail says which" (`effective-step-prompt.openapi.yaml:41-43`) asks clients to branch on
    `detail`. The contract forbids that: "consumers branch on [the code], never on the title", and the detail is
    free-text (`CharterRefusalCode.yaml:2-4`, dispatch paragraph `:73-80`).
  - ADR-0066 d4 gives the not-in-force case for free: "a fragment can show only where the step's mission type is in
    force" (`0066:101-102`). E6 already puts such an enhancement out of force.
- **Recommendation (contract-consistent).** Answer `200`, as the sibling does: the prompt in force, every declared slot
  with `fragments: []` (no special case), and a required `missionTypeInForce: boolean`, which is free in a new schema.
  Amend spec AS4.4 and `plan.md:415-417`. If the operator wants a refusal instead, it needs a distinct new code: an
  enum addition, `### Breaking`. A broadened `mission_type_not_found` would need `### Changed` to its definition and
  still could not be told apart from "does not exist".
- **Operator.** Yes, class (b): AS4.4 is the spec author's choice, not a ruling.

### C-07 [MINOR] N2 (only if D14 is "inherit"): do not use `artifact_locked`; follow the C21 precedent

- **Evidence.**
  - `artifact_locked` is a `409` of activation writes and carries `requiredBy` (`CharterRefusalCode.yaml:7-9,50-52`;
    `Problem.yaml` `requiredBy`).
  - On reads, a layer override that the target's level forbids answers `500 pack_unreadable`, naming the artifact,
    the packs and the level (`paths/activations_activationId_artifacts.yaml:24-27`, ADR-0063 D3).
  - At registration, an edge onto a non-open target is `422 override_not_permitted` (`ImpactRelations.java:208-215`).
  - The contract sentence "`artifact_locked` keep[s] [its] existing meaning for the other ADR-0066 refusals"
    (`CharterRefusalCode.yaml:166-167`) suggests `artifact_locked` is the code for this refusal.
- **Recommendation.**
  - Reads: `500 pack_unreadable`, worded as C21 is.
  - Registration: `422 override_not_permitted`, with its description broadened to "an overrides edge, or an enhances
    edge onto a locked target, that the target's mutability level forbids" (`### Changed`, no enum change).
  - Whatever D14 says, reword `:166-167` in IC-04, so the contract no longer suggests `artifact_locked`.
- **Operator.** Only if D14 is "inherit", class (b)+(f).

### C-08 [MINOR] The shared `OfferingUnreadable` would advertise `prompt_slot_missing` on operations that never build the layer merge

- **Evidence.**
  - `responses/OfferingUnreadable.yaml` is shared by 26 path files, among them `info`, `offering` and the pack reads
    (grep over `paths/`).
  - IC-05 adds the code's name and an example to it (`effective-step-prompt.openapi.yaml:303-313`).
  - Under D13 branch B, only reads that resolve in force can answer the code (`plan.md:388-390`).
- **Fix.** Name `prompt_slot_missing` in each operation description that can answer it, under each D13 branch. In the
  shared description, say "on the operations that build the layer merge". If C-01 resolves to top-level `500`s, name
  the three load codes there too.
- **Operator.** No.

### C-09 [MINOR] The 400 description omits the `missionType` and `action` patterns

- **Evidence.**
  - The fragment says only "an activationId outside the declared pattern is a 400" (`:41`).
  - `MissionTypePath` and `ActionPath` are `Slug` (pattern, `maxLength: 64`; `schemas/Slug.yaml`).
  - Bean validation is on (`build-logic/.../charter.contract-api.gradle.kts:50`), and
    `ProblemHandler.invalidParameter` answers a 400 without a code (`web-transport/.../ProblemHandler.java:54-56`).
- **Fix.** Name all three path params in the description, and add a scenario.
- **Operator.** No.

### C-10 [MINOR] `EffectivePromptSlot.slot` would fail `resource-property-cited`

- **Evidence.**
  - `slot: $ref: SlotId.yaml` (`effective-step-prompt.openapi.yaml:185-186`) has no `x-source` or `x-derived`, and
    neither does `SlotId.yaml` (`:248-254`).
  - The rule requires one on every property of a titled object (`contracts/lint/ruleset.yaml:384-396`).
  - In the precedents, a bare-ref target carries one itself (`Links.yaml`, `SkippedReport.yaml`).
- **Fix.** Either give `SlotId.yaml` an `x-source` (`missions.domain.PromptSlots`), or wrap `slot` in `allOf` with a
  description and `x-derived`.
- **Operator.** No.

### C-11 [MINOR] The plan misses contract and docs surfaces that list operations or raised codes

- **Evidence.**
  - `headers/EffectiveETag.yaml` and `headers/CharterSourceFingerprint.yaml:17-19` name the operations that declare
    them.
  - Docs that list operations: `docs/api/service-contract.md:88,256`, `docs/development/http-status-codes.md`
    (304/400/404/500 rows), `docs/development/rest-resource-conventions.md:137`.
  - `errors.feature:171-215` lists every raised code and carries the stale "no operation reaches the whole-pack load"
    text.
  - IC-05 and IC-08 (`plan.md:360-363,450-460`) name none of these. Only `hypermedia-hateoas.md` is listed.
- **Fix.** Add them to IC-05 and IC-08's affected surfaces. `checkRefusalExamples` does not catch a list that is
  incomplete, only a code that is not raised.
- **Operator.** No.

### C-12 [MINOR] The IC-04 `### Changed` and `### Breaking` fragments are incomplete

- **Evidence.** IC-04 lists only the raised codes, the selection 404 → 200 and the preset ids that are no longer stale
  (`plan.md:332-333`). It leaves out these observable changes:
  - A registration of a pack carrying step enhancements now succeeds. Today it is `422` "unmodelled"
    (`paths/packs_packId.yaml:33-34`, `WholePackStore.java:244-248`).
  - The whole-pack write keeps the files.
  - `listEffectiveArtifacts`, `getActivationSkippedReport` and `ActivationChange.dropped` now carry
    `step_enhancement` items, or stop dropping them.

  Also, `changes/README.md` ("Writing a fragment") asks a `### Breaking` fragment to say what core's seam must change.
- **Fix.** List these in `### Changed`. In each `### Breaking` fragment, add "core: nothing while it is not a consumer
  (epic #186)", plus the read-side effect: every verified read of a closure holding such a pack answers `500`.
- **Operator.** No.

### C-13 [MINOR] The `prompt_slot_missing` wording in the contract already takes D13 branch A, and the fragment changes it silently

- **Evidence.** `CharterRefusalCode.yaml:165` says "an active or inactive enhancement names at compose". The sketch
  drops "active or inactive" (`effective-step-prompt.openapi.yaml:339`).
- **Fix.** Keep the current wording until D13 is ruled. Branch B narrows it (`### Changed`), together with the
  ADR-0066 d8 amendment.
- **Operator.** It follows D13, which is already open.

### C-14 [NIT] `enhancement` says "(step_enhancement:<id>)", but the example is bare and `ArtifactId` admits an optional kind prefix

- **Evidence.** `effective-step-prompt.openapi.yaml:228-231`; `schemas/ArtifactId.yaml` pattern.
- **Fix.** "The declared id, without a kind prefix."

### C-15 [NIT] The `prompt_slot_not_empty` refusal description says "a warning otherwise"

- **Evidence.** `effective-step-prompt.openapi.yaml:329`.
- **Fix.** Point to `DiagnosticCode.prompt_slot_not_empty` instead.

## N1 to N3: recommendations in one place

- **N1.** Option (a), `validation_failed` with prompt violation codes on `getPackRegistration`, is consistent with the
  contract. It matches `loadStaged` and the PUT's "an invalid artifact … 422" (`paths/packs_packId.yaml:33-35`).
  Option (b)'s `operations` maps exist only for impact-check codes that are the problem's top-level code. (a) holds
  only with C-02's fixes, and with C-01's explicit choice of whether the codes count as raised.
- **N2.** C-07.
- **N3.** C-06.

## No finding

- **Conventions of the new read hold.** `operationId`, tag `Activation`, path parameters, `IfNoneMatch` /
  `EffectiveETag` / `304 NotModified`, the three `Charter-*` headers with a required fingerprint on the 200, `default`,
  no `501` and no `x-reserved`, closed objects with `x-derived`, examples carrying `_links.self`.
- **ADR-0066 holds** apart from C-04: d4 (activation gate, no pull either way), d6 (nothing merged), and d9 with D5's
  "base text unchanged" read as equal meaning.
- **The classes are right.** The new operation and schemas are `### Added`. The raised flips are `### Changed`
  (precedents `changes/2026-11-07-a-*-raised.md`, `2026-11-01-refusal-code-raised-map.md`). Selection 404 → 200 is
  `### Changed` (the 2026-11-08 fragment promised it). D10's broadened description, with no enum change, is
  `### Changed`. Content that loaded before and is refused now is `### Breaking`.
- **The compatibility baseline holds.** `0.2.0-SNAPSHOT` with no `contract-charter-v*` tag has no baseline, so the
  fragment is the gate (`contracts/lint/README.md:68-73`).
- **Other gates are unaffected.** client-java is generated from the bundle, with no hand-written surface to edit.
  `ReservedOperationCoverageTest` is unaffected by a built operation. `OperationCoverageTest` is planned. No new enum
  value, so `WireTokenParityTest` is untouched.
