# Plan squad: architecture, domain model and boundaries lens

Mission: `adr-0066-step-enhancement-service-alignment-01M6ZRYK` (branch `issue-424-428-adr-0066-service-alignment`, HEAD
`92026b5d`). Reviewer: `paula-patterns` in **squad-delegate** mode: I answer from the architecture-scout lens directly and
dispatch no scout swarm.

Profile applied (degraded read of `packs/built-in/agent_profiles/paula-patterns.agent.yaml`; the CLI was not run, so
overlays are not applied): directives 001 (architectural integrity: name the owning boundary before the fix), 003 (record
decisions), 030/041 (each fix names the test that proves it), 032 (conceptual alignment). Procedure:
`adversarial-squad-deployment` (findings-disposition contract: every finding below needs one of accepted / changed /
deferred_with_rationale). Charter: `.kittify/charter/charter.md` (hexagonal integrity, DDD vocabulary, test first).

Everything below was checked against the code at HEAD. Paths are repo-relative. `S=service/packs/src/main/java/ai/speckitty/charter/packs`.

## Verdict

**Revise before tasks.** The store-binding half (IC-01) is sound in outline. Two BLOCKERs need a design change first:
the slot parser's placement fails `HexagonalArchitectureTest`, and under E6 as built no step enhancement can ever be in
force. Five MAJORs show the load-path and compose-path seams the plan names either do not exist in the code or route
through paths the gates forbid. Each has a concrete fix, and most of them make the design smaller.

## Findings

### A1 [BLOCKER] The `missions.domain` placement of `PromptSlots` / `SlotId` breaks three ArchUnit rules

- **Evidence:**
  - `plan.md:153,190-192` put the slot grammar in `missions.domain`. The plan then calls it from `packs.domain`
    (`StepEnhancementRules` rule 1, `plan.md:265-266`; C4 `Rel(policy, slots, "parse")`, `plan.md:534`), from
    `packs.application` (`EffectivePromptQuery`, `Rel(query, slots, ...)`, `plan.md:542`) and, through `SlotId`, from
    `activations.domain` (`data-model.md`, EffectiveStepPrompt).
  - `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`:
    - `PACKS_DOMAIN_IS_PURE` (:160-165): `packs.domain` may depend only on itself, the kernel, `java` and JSpecify.
    - `sliceDomainDoesNotCross` (:552-573): no slice's domain may name another slice.
    - `APPLICATION_IS_EDGE` (:258-262): `packs.application` may not name another slice's domain.
  - The same rules forbid the `PromptText` port in `packs.application` from naming `StepDocument` or `MissionType`
    (`plan.md:161-162`, "render a held StepDocument").
- **Fix:** put `PromptSlots` and `SlotId` in the kernel, beside `kernel.markdown.MarkdownLines` and `MarkdownScanner`,
  which are already the shared Markdown line rules (`StepDocument.java:3`). Every ring may name the kernel.
  - Give the port kernel types only, for example `Optional<String> promptInForce(Urn missionType, PackIdentifier winner, String step)`.
    Its adapter in `packs.infra.storage` reads the `MissionType`; that is allowed, because `infra.storage` may name
    `missions.domain` (:525-531).
  - In `activations.domain`, the values carry the kernel `SlotId` or a `String`.
  - Update the C4 view, the Structure Decision and `data-model.md` headings to match.
- **Operator:** no.

### A2 [BLOCKER] Under E6 as built, a step enhancement is never in force. The plan's fallback fix is not enough, and the owner is unclear

- **Evidence:**
  - `InForceResolution.java:329-341`: `standsOn` requires `!enhancerOut && !bindingOut`, and `enhancerOut` is true
    whenever no `enhances` target is in the set.
  - The target is `action:<M>/<s>`, and in production a step is never in the effective set:
    - It is not selectable. `ArtifactRef(ArtifactKind kind, …)` (`activations/domain/ArtifactRef.java:17,40`) cannot
      name an action, because `NodeKind.ACTION.artifactKind()` is empty (`NodeKind.java:68-70`).
    - `ClosureViewSession.requestOf` (:84-86) feeds only `ArtifactRef`s.
    - A step is not lockable either: `policy.schema.json` cannot name an action (D14).
  - So every step enhancement is pruned with `ENHANCED_TARGET_NOT_IN_FORCE`. `InForceResolutionTest:736-745` reaches the
    step only by selecting `action:m/s` directly, which the wire cannot do.
  - Consequence: SC-001, US4 AS1 and IC-07 step 4 ("enhancements … that are themselves active") can never pass.
  - ADR-0066 d4 says a fragment shows wherever the step's mission type is in force. That is a binding to the mission
    type, not "target in Eff".
  - The plan's own fallback does not fix it:
    - "one hunk in `missionTypeOf`" (`plan.md:247-251`) adds only a binding. `enhancerOut` still prunes.
    - `missionTypeOf(CharterArtifact)` (`DomainRepositoryService.java:426-430`) sees only the entity, and a step
      enhancement has no target field (C-003). It cannot compute M without the graph edges.
  - Ownership: three sources say PR C owns this:
    - D2 as accepted ("PR C keeps WP27-29 scope plus the step-enhancer binding (E6, DIV-224)", `research/grounding.md` §6 item 2);
    - the rules page ("the binding of a step enhancer is PR C", `docs/architecture/composition-and-activation-rules.md:798`);
    - the PR C hand-over ("`stepBinding` (step enhancers) are PR C's", commit `578a831d`, `handover-followups-2026-11-09.md`).
  - But no `owned_files` or shared edit in WP27–WP29 touches `InForceResolution`, `ClosureView.bindingsOf` or
    `DomainRepositoryService`. The architect's claim ("nothing in WP27–29 binds a step enhancement") is **true**, and the
    plan reads that as permission to do the work here, which contradicts D2.
- **Fix:** make this an owned, tested change, not a risk bullet.
  - **Rule:** a step enhancer is grounded exactly when the mission type of its single `enhances` target is in Eff.
    `enhancerOut` skips `action:` targets, and `bindingOf` derives M from the step-enhancement's `enhances` edge in the
    merged edges, in `packs.domain`. Amend E6's text to match.
  - **Red test, production-shaped:** select `step_enhancement:x` and `mission_type:M` (never `action:`) and expect ACTIVE.
    With M deselected, expect INACTIVE with `MISSION_TYPE_NOT_IN_FORCE`.
  - **Owner:** PR C by D2. Ask the PR C runner to add it to WP27 or WP28's file list, and make it a named dependency of
    IC-07. If the operator moves it here instead, it becomes its own IC after PR C, never a hunk in `missionTypeOf`.
- **Operator:** **yes**. Confirm the owner (D2 as ruled vs this mission) and the E6 amendment.

### A3 [MAJOR] The load-path code projection is routed through paths the code and a gate forbid

- **Evidence:**
  - (a) The store's load tolerates partial failure:
    - A binding or domain refusal becomes a `PackElementViolation` of kind `domain` (`DomainRepositoryService.java:771-777`).
    - `compositionFacts` turns any violation into `pack_unreadable` (`readable`, :600-608).
    - So on every composing read a bad fragment answers `pack_unreadable`, never a prompt code. `plan.md:329` ("default
      500 on reads") and `data-model.md` ("L load (every read …)") describe a surface that does not exist.
  - (b) `plan.md:275` routes the load refusal "via `PackFindingCode.refusal()`". But `ReservedRefusalCodesTest.offWireRules`
    (:108-131) forbids every class except `PackFinding` from calling `PackFindingCode.refusal()`, and forbids all callers
    of `PackFinding.toRefusal()`. Editing that rule is not in D19's approved set.
  - (c) Making `WholePackStore.load` throw breaks its documented contract of tolerating partial failure
    (`WholePackStore.java:53-56`). It would also break the whole-pack round trip and salvage over such a pack.
- **Fix:** emit each prompt fault as a violation whose `kind` is its wire token (`prompt_fragment_invalid`,
  `prompt_slot_malformed`, `prompt_slot_not_empty`).
  - The token comes from a pure `PackPolicy` entry point, which keeps `PackPolicy` the single owner of codes and names
    the `RefusalCode` constants, so the codes count as raised.
  - `loadStaged` already copies `violation.kind()` into the violation code (`WholePackStore.java:241-243`). That is
    N1(a) with no new machinery and no `throw` in `load`.
  - On composing reads the answer stays `pack_unreadable`, naming the file and the reason. Say so in the
    `CharterRefusalCode` descriptions instead of "default 500".
- **Operator:** N1 is already open. Add one line to it: may `raised: true` mean "raised as a violation code of
  `validation_failed`"?

### A4 [MAJOR] The `prompt_slot_not_empty` warning has no channel, and the descriptor does not reach the binding

- **Evidence:**
  - The `Pack` aggregate carries only violations. No load path carries diagnostics:
    - `PackFinding.toDiagnostic()` has no caller.
    - `contracts/charter/schemas/Diagnostic.yaml` is referenced by no path or response (only `_index.yaml`).
    - `PackValidation` has no production consumer (only `CharterWiring.java:71`).
  - FR-011's "the `prompt_slot_not_empty` diagnostic emitted" therefore has no wire.
  - If someone implements the warning as a violation for convenience, built-in becomes `pack_unreadable` on every
    compose. That breaks NFR-002.
  - `KindBinding.toEntity(PackIdentifier, D)` (`KindBinding.java:166`) gets no descriptor. "The descriptor reaches the
    check from the load" (`plan.md:308-309`) names no seam.
- **Fix:**
  - Run the gate in `DomainRepositoryService.assembly` (:684), which already holds `read.descriptor()`, or in `PackPolicy`.
  - Carry warnings as a separate, non-violation list (on `LoadedPack` or the composition facts).
  - Choose the surface: validate mode only (no wire, FR-011 reworded), or a new `diagnostics` member on a response
    (`### Breaking`).
  - Pin with a test that built-in at the pin composes without `pack_unreadable`.
- **Operator:** **yes** (the wire surface of the warning).

### A5 [MAJOR] The "raw companion text" seam does not exist on load. The slot parse would see three different inputs

- **Evidence:**
  - `Companions.read` (`$S/infra/storage/Companions.java:286-305`) parses Markdown into a `MarkdownDocument` before any
    binding sees it. `MissionTypeBinding.withCompanions` (:140-142) receives a parsed `CompanionContent.Markdown`.
  - Validate mode has the raw text (`FileSystemPackInspector.java:395-415`). The read renders the held document
    (`plan.md:418-419`).
  - So load, validate and read would parse different inputs. That contradicts "agree by construction" (`plan.md:190-192`).
  - `MarkdownCodec` keeps HTML comments as text but splits sections at any column-0 `## `, even inside a comment
    (class javadoc). A legacy non-empty slot that holds a heading is therefore split across sections.
- **Fix:** one input everywhere: the canonical text `MarkdownCodec.write(document)`. It is equal in meaning to the
  source (ADR-0036) and is exactly what the read serves.
  - Run `PromptSlots` on it at load (in assembly, see A4), in validate mode (after parse) and at read
    (`PromptTextAdapter`).
  - Also state whether `prompt` includes the frontmatter. `MissionTypeBinding.markdownOf(StepDocument)` (:341-347)
    writes the detailed description as frontmatter. The plan should say whether renderers get it.
- **Operator:** no.

### A6 [MAJOR] FR-009 creates a second authority for "this step exists"

- **Evidence:**
  - The claim that the artifact-missing branch is skipped for `action:` is **verified**
    (`PackCompositionResolver.java:505-509`: `artifactKind().isPresent()` is false for ACTION). FR-009 is therefore new code.
  - But the plan defines step existence as "the named pack's mission type `<type>` declares step `<step>`"
    (`plan.md:376-380`). WP27's `action_not_found` ("judged against the merged closure") and `EdgeIndex.stepsOf`
    (`$S/application/EdgeIndex.java:247-258`) use graph-declared `action:` nodes instead
    (`DomainRepositoryService.declaredWithoutFile`, :580-597). These are two predicates for one fact.
  - `PackCompositionFacts` holds no mission-type step lists, so the plan's predicate needs new facts. Yet IC-06's
    surfaces omit `DomainRepositoryService` and `PackCompositionFacts`.
  - Hole in the node-based predicate: an enhancing pack whose graph declares the node `action:M/s` gets it in its own
    `defined`. A bare target then "resolves" locally, and `stepsOf` lists the enhancing pack as a step place, which
    hides a missing step.
- **Fix:** define "pack P holds step M/s" once, in `packs.domain`, over composition facts: P defines `mission_type:M`
  and declares `action:M/s`.
  - WP27's `action_not_found`, `EdgeIndex.stepsOf` and FR-009 (bare and qualified) all use it.
  - Agree it with the PR C runner on #429.
  - Limit the extended artifact-missing check to `enhances` edges from `step_enhancement:` nodes, or prove NFR-002 for
    every qualified `action:` reference on the corpus.
- **Operator:** yes, light. The predicate crosses missions; the PR C runner may settle it if the operator agrees.

### A7 [MAJOR] The compose-time slot check (FR-008, D13 branch A "beside `MergedClosure`") has no inputs in `packs.domain`

- **Evidence:**
  - `MergedClosure` holds winners by URN (pack, level, policy), not artifacts.
  - `packs.domain` cannot read `MissionType` or `StepDocument` (A1).
  - `plan.md:385-387` and IC-06's surfaces (`plan.md:399-401`) name no source for the declared slots or the slots each
    enhancement names.
- **Fix:** compute per-pack slot facts in `DomainRepositoryService.compositionFacts`, where `infra.storage` may name
  `missions.domain`:
  - `declaredSlots: Map<action URN, List<SlotId>>` from each step prompt (canonical text, A5);
  - `filledSlots: Map<step_enhancement URN, (target, Set<SlotId>)>` from the single `enhances` edge.
  - The check is then pure over the merged facts, linear in enhancements (NFR-004), and the same for both D13 branches.
    Branch B filters by the in-force answer afterwards.
  - The same facts feed A2's binding and A6's step predicate.
  - Add `DomainRepositoryService`, `PackCompositionFacts` and `CompositionFactsCache` to IC-06.
- **Operator:** no.

### A8 [MINOR] IC-01 needs no edit to `DomainRepositoryService` (simplification)

- **Evidence:**
  - `assembly` iterates `KindBindings.all()` (:705-707).
  - `compositionFacts` adds every artifact whose kind `authoredInGraphs()` (:399-401), which is true for
    `STEP_ENHANCEMENT` (`NodeKind.java:61-63`).
  - The URN therefore joins `defined` from the binding alone. The plan lists the hottest file (46 commits in 90 days) as
    edited (`plan.md:234,241`).
- **Fix:** drop `DomainRepositoryService` from IC-01, keeping it as a test. Do not add the `missionTypeOf` hunk (A2).
- **Operator:** no.

### A9 [MINOR] The orphan refusal duplicates the existing unmodelled channel

- **Evidence:**
  - A companion that no entry names is already left out of `companionFiles` and listed as `unmodelled`
    (`WholePackStore.java:212,623-675`).
  - `loadStaged` refuses it (:244-248), and `write`'s caller gets the list (:65). It is never silently dropped.
- **Fix:** in `loadStaged` and validate mode, reclassify an unmodelled `*/missions/*/enhancements/*.prompt-fragment.md`
  under code `prompt_fragment_invalid` instead of throwing in `load`. D11 is met at the registration's load, and the
  round-trip gate is untouched.
- **Operator:** no. Record how "at load" in D11 is read.

### A10 [MINOR] The PR C overlap list is incomplete, and PR C does not exist yet

- **Evidence:** `plan.md:211-215` names only WP28 for `CharterRefusalCode.yaml`. WP29's shared edits also touch it (the
  `artifact_required` text, `WP29-c-deny-satisfied-by-override.md`).
- **Files both sides touch before PR C:**
  - `contracts/charter/schemas/CharterRefusalCode.yaml`: IC-02, IC-03, IC-04 here; WP28 and WP29 in PR C.
  - `service/deployable/src/test/java/.../ReservedRefusalCodesTest.java`: IC-02, IC-03, IC-04 here; WP28 in PR C.
- **Files edited after PR C (merge-hot, no ownership clash):**
  - `ClosureView.java`, `EdgeIndex.java` (WP27);
  - `PacksSession`, `ClosureViewSession`, `ActivationController` (WP28);
  - `openapi.yaml`, `schemas/_index.yaml`;
  - the rules page's E15/E16 status rows;
  - `divergence-register.md` (DIV-238).
- **Owned by PR C by ruling, not by file list:** `InForceResolution`, `ClosureView.bindingsOf` (A2).
- **PR C status:** not yet raised. The `issue-10` branch is ahead of HEAD by docs-only commits; `git diff HEAD...origin/issue-10-layer-merge-effective-reads -- service contracts` is empty.
- **Fix:** add WP29 to the overlap list. Write down the signatures IC-05 to IC-07 assume (the enhancer lookup and
  merged-closure name checks of `ActionContextWalk`, `NodeReference`), so a drift fails at the rebase rather than in
  implementation.
- **Operator:** no.

### A11 [MINOR] Same-id handling goes beyond C14 and is not stated

- **Evidence:**
  - C15 lets a descriptor `overrides` entry settle a sibling contest (`LayerMerge.java:319-337`), so one sibling's step
    enhancement wins quietly.
  - A same-id enhancement at a higher level replaces the built-in's by layer override (ADR-0048 d10).
  - Neither `data-model.md` nor US2 AS3 says this.
- **Fix:** state that fragments come only from the winning copy of each `step_enhancement` URN. Add one test for a
  cross-level replacement and one for a settled sibling.
- **Operator:** no, unless step enhancements should be excluded from layer override.

### A12 [MINOR] `StepEnhancementBinding.fileName` would place a canonical write at the pack root

- **Evidence:**
  - The default `fileName` (`KindBinding.java:138-140`), used by `PackLayout.pathOf` (:54-56) and
    `FilesystemPackStore.java:1127`, yields `<id>.step-enhancement.yaml` at the root.
  - `file-kinds.yaml:190` does not classify that path.
- **Fix:** override `fileName` to refuse, because the place cannot be derived without the edge. Add a test that a
  canonical layout cannot write one.
- **Operator:** no.

### A13 [NIT] The vocabulary drifts across layers

- **Evidence:**
  - "effective prompt" (Domain Language) vs "effective step prompt" (Key Entities, `getEffectiveStepPrompt`).
  - `PromptSlotView` and `ActiveFragment` in `activations.domain` (`plan.md:169`) vs `EffectivePromptSlot` and
    `EffectivePromptFragment` on the wire and in `data-model.md`.
- **Fix:** use one name per concept in every layer and in the glossary.
- **Operator:** no.

## Areas that hold (no finding)

- **The `KindBindings` append and NFR-001.** The fingerprint is a list of paths per bound kind
  (`PackFileSet.digested`, :224-262), so a kind with zero files adds nothing. Appending keeps every existing digest.
- **The source of the descriptor version gate.** `PackDescriptor.schemaVersion()` is an `Optional<String>`, and the
  descriptor schema's `schema_version` is `const: "1.0"`. Present means `"1.0"`, absent means legacy. Only the seam is
  missing (A4).
- **Sibling enhancers compose versus same-id conflict.** `refines()` (`DomainRepositoryService.java:615-619`) and
  `contestableDefinitions` (`PackCompositionResolver.java:604-611`) give C14 as ADR-0053 d21 amended, once the URN is in
  `defined`.
- **`activations` reaches `packs` only through `activations.infra.packs` / `PacksSession`.** The plan respects this
  (`ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER`). The ordering is computed in `packs`.
- **No second copy of PR C's walk or `NodeReference`.** IC-07 reuses WP27's enhancer lookup and name checks. The one
  divergence is N3 (404 vs `missionTypeNotInForce`), already put to the operator.
- **D13 and D14, planned as two branches.** This is acceptable once A7 makes the facts independent of the branch.
- **Over-engineering:**
  - The two finding codes for one condition are justified by `PackFindingCode`'s fixed severities.
  - The service, query, session, mapper and value layers mirror the existing effective reads.
  - The real simplifications are A3 (reuse `violation.kind()`), A8 and A9.

## Disposition table (for the orchestrator)

| Id | Severity | Operator | Proposed disposition |
|---|---|---|---|
| A1 | BLOCKER | no | accepted: slot grammar to kernel; kernel-typed port |
| A2 | BLOCKER | yes | changed: E6 step-enhancer grounding as an owned change (PR C per D2), red test production-shaped |
| A3 | MAJOR | yes (N1 rider) | accepted: violation-kind tokens via PackPolicy; reads stay pack_unreadable |
| A4 | MAJOR | yes | accepted (seam) + operator (wire surface) |
| A5 | MAJOR | no | accepted: canonical text as the single parse input |
| A6 | MAJOR | yes (light) | changed: one step predicate shared with WP27 |
| A7 | MAJOR | no | accepted: slot facts in compositionFacts |
| A8 | MINOR | no | accepted |
| A9 | MINOR | no | accepted |
| A10 | MINOR | no | accepted |
| A11 | MINOR | no | accepted |
| A12 | MINOR | no | accepted |
| A13 | NIT | no | accepted |
