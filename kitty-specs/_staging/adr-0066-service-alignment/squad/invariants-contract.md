# Phase A: invariants and contract for #424, #428, #429 and #430

Author: Architect Alphonso (analysis mode), read-only. Repo `spec-kitty-charter` at `main` 391ed51b (holds #425 and
#427). Contract `info.version` is `0.2.0-SNAPSHOT` and no `contract-charter-v*` tag exists, so there is no
released baseline. On this line a break needs only a `### Breaking` fragment (ADR-0022 D2;
`contracts/lint/README.md` "The released baseline"). open-packs is pinned at `openPacksRef=22c14b2d`
(`gradle.properties:10`).

---

## 1. Invariants that must survive

| # | Invariant | Source |
|---|---|---|
| I-1 | **Round trip by meaning.** Every file the store reads is written back: the same set of paths, each with the same meaning. A missing or extra file is a difference. Unknown fields are refused, never carried through. A Markdown file compares its frontmatter as YAML and its body as text with LF line endings (amended by ADR-0040: the file-kind rule's `content_form` decides the form). So `*.step-enhancement.yaml` and every `*.prompt-fragment.md` must be **held and written back**. Today both are validated and then dropped (`KindBindings.DEFERRED_COMPANION_STRUCTURES`), and they survive the round trip only because neither side holds them. | ADR-0036 d1.1, d1.2, d2 ("Source against written", "Other files"), d3; `docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md`; charter "What must never go wrong"; whole-pack round trip gate (charter, Gates before merge) |
| I-2 | **Edges compare from graph documents only**, as a set. | ADR-0036 d1.4, amended by ADR-0064 d9 |
| I-3 | **A step enhancement is an artifact holding only additions**, joined to `action:<type>/<step>` by a CRG `enhances` edge, written `<pack>::action:...` across packs. Its additions are guards, emits and prompt fragments; it holds no `references` and no `target:` field. It is add-only: it cannot remove or reorder steps, change transitions or remove a guard. | ADR-0053 d22, amended 2026-11-05 and 2026-11-08; ADR-0050 d22 and d23, as amended; `docs/adr/0053-...md` lines 159–167 |
| I-4 | **Layout ruling.** An enhancing artifact lives "beside the kind it adds to in the enhancing pack, with the `enhances` edge in that pack's graph shard, as for profiles. No separate enhancements area." | ADR-0053 Open question 2, ruled 2026-11-03, recorded with ADR-0055 (lines 35–36) |
| I-5 | **Sibling step enhancers compose**, and sibling step overrides are refused. Two packs at one level that both *define* one id still conflict (C14 `defines`). | ADR-0053 d21, amended; ADR-0048 d9, amended; rules page C14 (line 200) |
| I-6 | **Guards.** A transition names guards by entry, `guard: [{ref, config?, blocking?}]`, "in mission types, lifecycles and step enhancements alike". `ref` is bare in the same pack and `<pack>::guard:<id>` across packs. `config` is checked against the guard's `config_schema` at load. Guards are add-only: a step adds guards to a lifecycle with `submachine.add_guards: [{transition:{from,to}, guard:[...]}]`, and only an override may remove a guard. | ADR-0054 d2, d4 (load checks), d10 (amended: `transition`, not `on`); ADR-0050 d12 (amended), d24; `contracts/packs/schemas/lifecycle.schema.json` `$defs.GuardEntry` |
| I-7 | **Relationships live only in graph documents.** No artifact field mints an edge. Lifecycle guards, guard `implemented_by`, `activated_*` lists and similar fields are data, not edges. Guidance an enhancement adds is an edge in its pack's graph documents. | ADR-0064 d1, d2, d11 (table: "lifecycle guards — a transition's guard"), d12 |
| I-8 | **Pack is provenance, not identity.** URNs are global and unqualified. An id must be unique across every pack of a compose, apart from layer overrides. | ADR-0048 d10, amended by ADR-0053 d7; `Urn.yaml` |
| I-9 | **Contract versioning.** On 0.x a minor may break, with a `### Breaking` fragment that says what core's seam must change. Any response-shape change is breaking, an added optional response property included (`severity-levels.txt`, DM 01M5986E711JT296MA6NDN91MG). Hand-over rule: "`### Breaking` where a documented response of an operation on `main` changes (a new member on a closed response counts)". Precedent for strictness: refusing a body that used to be accepted is breaking (`2026-11-08-b-activation-body-strictness-breaking.md`). Precedent for raising a reserved code (`raised: false → true`): `### Changed` (`2026-11-01-refusal-code-raised-map.md`, `2026-11-07-a-artifact-referenced-raised.md`). Removing `x-reserved` from a new read: `### Added` (`2026-11-07-b-effective-reads-added.md`). | ADR-0022 D2, D4; `docs/api/compatibility.md`; `contracts/charter/changes/README.md`; charter PROJECT_002 |
| I-10 | **E6, enhancer and binding pruning.** An enhancer is in force only when it is grounded in a target in force. An artifact bound to mission type M is in force only while M is. A required or locked artifact pruned this way is reported required-but-ungrounded (E14, `requiredNotInForce`). The binding of a step enhancer is "PR C". | `docs/architecture/composition-and-activation-rules.md` E6 (l.787–799), E14 (l.857–866); DIV-224, DIV-228 (`docs/conformance/divergence-register.md`) |
| I-11 | **E12, enhancement both ways.** An enhancement is returned only when it is itself active, and nothing is pulled in. This is ADR-0066 d4's gate. | rules page E12; ADR-0059 amendment A4 |
| I-12 | **Integrity.** Open allows override, enhance and remove. Extendable allows enhance and remove. Locked allows nothing. An enhancement is "judged separately" from `replace` and `relate`. | `docs/context/conceptual-model.md` #integrity (l.131–147); rules page §1 Mutability (l.102–112), C26, C27 |
| I-13 | **Divergence ledger.** Every deliberate difference from core gets a stable `DIV-*` id, never reused. Activation is the block DIV-201..237, so the next free number there is DIV-238. The ledger has two forms of one list, the page and `divergences.yaml`. | `docs/conformance/divergence-register.md` l.25–60; PR C constraint C-007 |
| I-14 | **Hexagonal rule.** Walks and merges live in `packs`. `activations` reaches `packs` only through `AvailablePacks` and `activations.infra.packs` (`ClosureViewSession`). | plan C-002; `HexagonalArchitectureTest` |

### ADR-0066 decisions 1–9 in brief (`docs/adr/0066-a-step-enhancement-may-contribute-prompt-fragments.md`)

1. An enhancement may contribute Markdown prompt fragments to the prompt of the step it enhances. A fragment cannot
   reach the step's guidelines. The additions are now guards, emits and fragments, and the mechanism is unchanged
   (an `enhances` edge). A base type reads the same with no enhancement composed.
2. Only the base prompt (the prompt in force) declares a slot: a marker pair `<!-- ns:name:start/end -->` with
   only whitespace between. The id is `<namespace>:<name>`, each part matching `[a-z][a-z0-9-]*`; the namespace names
   a concern, not a pack. Each slot is declared once, start before end, with no nesting. Slot ids are stable like
   pack names. A fragment or enhancement never adds a slot.
3. The enhancement carries `prompt_fragments: [{slot, companion}]`. The companion is Markdown with no frontmatter.
   A fragment is addressed by (target step from the `enhances` edge, slot id). A fragment is content, not an edge,
   and the slot id is no edge either. An enhancement names a slot at most once.
4. A fragment renders only while its enhancement is itself active, with no pull either way and no second gate.
   An empty slot renders as nothing.
5. Fragments in one slot follow a total order: pack level (built-in < org < project), then pack name, then
   enhancement id, each compared by code point. There is no priority field.
6. Sibling fragments compose. Nothing is merged or de-duplicated. The only conflict is an enhancement repeating a
   slot.
7. Refused: replacing or deleting base text; adding a slot (a marker inside a fragment, or an entry naming a slot
   the prompt in force does not declare); frontmatter or a blank fragment; a target that is not a step; a target
   whose integrity refuses enhancement (locked); a prompt override that drops a slot an enhancement names.
8. Validation at three moments. Base prompt at load: `prompt_slot_malformed`, and `prompt_slot_not_empty` behind
   the descriptor-version gate. Enhancement at load: `prompt_fragment_invalid`. Compose at resolution, "whatever
   the activation": the target resolves (`pack_reference_*`), the slot is declared (`prompt_slot_missing`), and
   integrity permits it. The charter does not judge meaning and does not render.
9. The effective-prompt read returns the base text unchanged plus, per slot, the active fragments in d5 order,
   each with its pack and enhancement. The renderer places them: one blank line between fragments, and an empty
   slot leaves nothing, not even the markers. The contract change is "additive" and "belongs with" the mission-type
   read R5 (#99) and the activation read R9 (#103).
   Follow-up: the empty-slot check is gated on the pack-descriptor version (`schema_version: "1.0"`). An older
   descriptor gets a warning only.

---

## 2. Contract changes per issue

The contract files are `contracts/charter/**` (OpenAPI) and `contracts/packs/**` (pack schemas, `file-kinds.yaml`).
Classes follow I-9.

### #424 (ADR-0066 checks and the effective-prompt read)

| Change | File(s) | Class |
|---|---|---|
| `prompt_slot_malformed`, `prompt_slot_not_empty`, `prompt_fragment_invalid` and `prompt_slot_missing` move to `raised: true`, each with an `operations:` map: `422` on the registration reads and writes (`getPackRegistration` for a refused job, plus the per-kind writes if they load the pack), as `pack_reference_artifact_missing` does today; a default of `500` on reads that compose. The description paragraph "reserved … until #424" is rewritten. `ReservedRefusalCodesTest` moves the four codes into the raised set. | `schemas/CharterRefusalCode.yaml` | `### Changed` (raised-map precedent) |
| `DiagnosticCode.prompt_slot_not_empty` (warning) is raised. "Reserved for #424" is removed from the description. | `schemas/DiagnosticCode.yaml` | `### Changed` |
| **New refusals of pack content that loads today.** A slot pair holding text under descriptor `schema_version "1.0"`; a malformed marker in any prompt (malformed markers are refused whatever the descriptor version). Any open-packs prompt that carries a stray marker would newly fail. At pin 22c14b2 the built-in descriptor is unversioned and its five blocks are well formed, so only warnings result. | behaviour, `mission-step-prompt` raw check | `### Breaking` (body/pack-strictness precedent). One line is enough: the pinned packs are unaffected. |
| **Compose refusal `prompt_slot_missing`** also hits a closure whose project prompt override drops a slot, even when the enhancement is inactive (d8 "whatever the activation"). | behaviour | `### Breaking` (a compose that answers today is refused) |
| **Effective-prompt read (d9).** No operation exists for it. Recommended shape: a new operation `getEffectiveStepPrompt`, `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt`. It reuses the context path's parameters, `IfNoneMatch`, `EffectiveETag`, and the `404 mission_type_not_found` / `action_not_found` answers. New closed schemas: `EffectiveStepPrompt {activationId, revision, governanceFingerprint, missionType, action, prompt (text, unchanged), slots: [{slot, fragments: [{packId, level, enhancement (ArtifactId), text}]}], _links}`. A slot with no active fragment is listed with `fragments: []` or omitted (to be decided; I recommend listing every declared slot). | new `paths/…_prompt.yaml`, new schemas, `openapi.yaml`, `_index.yaml`, example, scenario `effective-step-prompt.feature` | `### Added` while it is a new path. It turns `### Breaking` if it is instead a member of `EffectiveActionContext` (an existing documented response). |
| `ActivationWarning` / `EffectiveArtifactReason`: no change needed. | – | – |

### #428 (store binding, guards and emits, layout and identity)

| Change | File(s) | Class |
|---|---|---|
| Store binding: `PUT …/selections/step_enhancement/{id}` stops answering `404 artifact_not_found`, and preset `activated_step_enhancements` ids stop showing as stale. No shape changes, but the answers change. The #425 fragment already announced that this lands with #424/#428. | behaviour; `PackRegistrationNotices` stays limited to activatable kinds (#417) | `### Changed` |
| Step-enhancement schema: the description drops "guard and emit additions … not in this shape yet (#428)". If guards and emits are added as fields (recommended, §3.2): optional `guards` and `emits` (shapes in §3.2). The schema stays closed, and each new field is optional. | `contracts/packs/schemas/step-enhancement.schema.json` | Pack-schema addition, optional: `### Added` |
| Semantic rule: the `<type>` directory must equal the type of the `enhances` edge. Rule: exactly one `enhances` edge, and its target is an `action:` (see §5 Q4). The violation is `prompt_fragment_invalid`, or a new code if it should not carry the "fragment" name (§5 Q8). | `step-enhancement.schema.json` description, `file-kinds.yaml` comment, `CharterRefusalCode` text | `### Breaking` (it refuses pack content that validates today) |
| Id rule (§3.3): either the description states that a `step_enhancement` id is unique per pack, or the URN id becomes `<type>/<stem>`. | schema description; `Urn`/`ArtifactId` patterns already admit `/` for templates (`ArtifactId` does **not**: its pattern `[A-Za-z0-9][A-Za-z0-9_.-]*` has no `/`) | Per-pack uniqueness: `### Breaking`, minor. Qualification: `### Breaking`, larger, because `ArtifactId` must admit `/` on the selection paths, the preset id grammar changes and spdd edges are re-spelled. |
| Layout: the file-kind `match` changes only if the layout moves (§3.1). | `contracts/packs/file-kinds.yaml` l.187–194 | `### Breaking` if it moves; none if kept |

### #429 (step targets in the skipped report)

| Change | File(s) | Class |
|---|---|---|
| `EnhancerWithoutTarget.targets` must be able to name `action:<M>/<step>`. Options in §4. Any option changes a documented response of `getActivationSkippedReport`, which is built on `main` since PR B. | `schemas/EnhancerWithoutTarget.yaml` (+ a new `NodeReference.yaml` or `StepReference.yaml`) | `### Breaking` under every option |
| `RequiredNotInForce` carrying an `action:` URN: `kind` is `ActivatableKind`, so this is the same type problem. See §4: a ruling (no contract change) is recommended. | `schemas/RequiredNotInForce.yaml` | none if ruled out; `### Breaking` if widened |
| Docs: an E14 clarification and, if the step-target fix diverges from core, DIV-238. | rules page E14; divergence register | docs only |

### #430 (parity tests)

| Change | File(s) | Class |
|---|---|---|
| Parity tests for `NodeKind`, `CascadeKind` and `DiagnosticCode` against their Java enums, in the style of `WireTokenParityTest` / `VocabularyEqualityTest`. Drift already present: `NodeKind.yaml` and `DiagnosticCode.yaml` carry stale `x-source` paths (`service/charter-domain/...`, a module that does not exist; the real files are `service/kernel/.../NodeKind.java` and `service/packs/.../domain/Diagnostic.java`). `pack_unknown_key` is in the contract enum but absent from `Diagnostic.Code`. | `x-source` fixes | `x-source` fix: none, or `### Changed` (metadata) |
| `pack_unknown_key`: either keep it with an explicit "retired, never raised" allowlist in the parity test, or delete it from the enum. No release exists, so the "client built against an earlier draft" rationale does not hold. | `schemas/DiagnosticCode.yaml` | Keep: none. Delete: `### Breaking` (it changes the response enum; a strict client still works, but its generated DTO changes). Recommend keep plus allowlist, the smallest diff. |

---

## 3. #428 rulings

### 3.1 Layout

The facts first. #425 placed the enhancement at `missions/<type>/enhancements/<id>.step-enhancement.yaml`, with the
fragments beside it (`file-kinds.yaml` l.187–194). The spdd pack on open-packs branch `issue-73-spdd-prompt-fragments`
(commit e8a24d6, open-packs#175, **not** an ancestor of the pin 22c14b2) uses exactly that layout:
`packs/spdd/missions/software-dev/enhancements/spdd-{specify,plan,tasks,implement,review}.step-enhancement.yaml`
and `.prompt-fragment.md`. It also holds a `file-kinds.local.yaml` that mirrors the rule, `drg/fragment.yaml` nodes
with `kind: step_enhancements`, and edges `step_enhancement:spdd-plan → built-in::action:software-dev/plan`. Nothing
in the pinned open-packs holds a step enhancement, so the pin does not constrain the decision; #175 does.

How should ADR-0053 Q2 be read? "Beside the kind it adds to … as for profiles. No separate enhancements area." A step
belongs to its mission type. A step's own files live at `missions/<type>/steps/<step>.{prompt,guidelines}.md`.
`missions/<type>/enhancements/` is *beside the mission type it adds to*, but it is a dedicated `enhancements/`
directory, which is arguably the area the ruling excluded. The ruling's intent ("as for profiles": an enhancing profile
sits in `agent_profiles/` with the profiles) points to placing it *with the step*.

| Option | Description | Pros | Cons | spdd / #175 impact |
|---|---|---|---|---|
| **A. Keep #425 layout and annotate** ADR-0053 Q2 (and ADR-0066 Follow-ups) with a dated note: "for steps, 'beside the kind' is `missions/<type>/enhancements/`; the directory is per mission type, not a pack-wide enhancements area". | none | Zero churn; #425 contract, service classification and #175 already agree; one directory groups an enhancement with its fragments; no clash with `steps/` file-kind rules | Reads as reinterpreting a ruling; needs operator sign-off on the annotation | none |
| **B. Move beside the step**: `missions/<type>/steps/<stem>.step-enhancement.yaml` + `<stem>.prompt-fragment.md` | literal "beside the kind" | Closest to the ruling's wording; the step and its additions sit together | `steps/` today holds only the base step's companions, so the file-kind matches must disambiguate (`*.prompt.md` vs `*.prompt-fragment.md`; both end in `.md`, and the existing `*/missions/*/steps/*.prompt.md` glob does not match `x.prompt-fragment.md`, so this is safe, but it should be pinned by a test); an enhancing pack that holds no mission type gets a `missions/<type>/steps/` tree for a type it does not own, which suggests ownership it does not have | rename 10 files, update `drg/fragment.yaml` `body_path`s and `file-kinds.local.yaml`; contract `### Breaking` |
| **C. Top-level kind directory** `step_enhancements/<type>/<stem>.step-enhancement.yaml` (like `guards/`, `lifecycles/`) | "beside the kind" read as its own kind | An enhancing pack does not mimic the base's `missions/` tree; uniform with other top-level kinds | Contradicts "no separate enhancements area" outright; largest churn | full move; `### Breaking` |

**Recommendation: A.** The spdd pack never owns `software-dev`, so the "as for profiles" analogy is imperfect either
way. A keeps the three repositories consistent as they stand. Pair it with the directory-equals-type check (#428 item
4), which turns the second statement of the target into an enforced invariant rather than a drift risk. **Operator
decision required** (it annotates a ruling).

### 3.2 Guard and emit shape: fields, not edges

- ADR-0054 d2 already rules the guard entry shape "in mission types, lifecycles and step enhancements alike".
  ADR-0064 d11 classes "lifecycle guards — a transition's guard" as data, not relationship authoring. A guard entry
  carries `config` and `blocking`, which an edge (`when`/`reason` only) cannot hold. An emit names an *event type*
  resolved against an event registry (ADR-0050 d16, ADR-0051), not a DRG node, so it cannot be an edge either.
  **So both are fields.** The ADR-0064 "edges for guidance" pattern applies only to what relates two artifacts.
- Shape, mirroring ADR-0054 d10 (amended) and ADR-0050 d15/16:
  - `add_guards: [{transition: {from, to}, guard: [GuardEntry]}]`. For a step, `from` is the step and `to` is a
    successor step or `end`. A step that runs a lifecycle submachine also needs
    `add_lifecycle_guards: [{transition:{from,to}, guard:[...]}]`, the same as `submachine.add_guards`.
  - `emits: {on_enter?: {emit:[event]}, on_exit?: {emit:[event]}}`, add-only. The renderer or runtime appends these
    to the base action points.
- **Caveat that makes this an operator decision.** `mission-type.schema.json` today has no step `transitions`, no
  `submachine`, no `guard` and no action points (`on_enter`/`on_exit`). Only `lifecycle.schema.json` has
  `GuardEntry`. Fields added to the enhancement now would add to structures the base cannot yet declare, so the
  load-time checks ("transition exists", "event resolves in the registry") would have nothing to check against.
  - Option (i): add the fields now with `GuardEntry` `$ref`'d from a shared def, and validate only `ref` resolution
    and `config`.
  - Option (ii, recommended): rule "fields, shape as above" in an ADR-0066 or ADR-0053 annotation. Keep the schema at
    `prompt_fragments` only, change its description from "not in this shape yet (#428)" to "added with the step
    transitions and action points (issue X)", and open that issue. This avoids a contract that promises checks the
    service cannot run, and leaves the schema closed.

### 3.3 Id qualification

- ADR-0048 d10 already makes ids unique across the whole compose, so "unique per pack" is weaker than what holds.
  The real gap is narrower: two mission types in **one** pack can both have `enhancements/plan.step-enhancement.yaml`
  and collide on `step_enhancement:plan`. A collision across packs at one level is already C14 (`defines`), and one
  across levels is a layer override, which is the useful case of a project replacing an org enhancement.
- Options:
  - (a) **Unique per pack plus the compose rule as today.** A load check refuses a stem repeated across type
    directories. No URN change. spdd's stems are already unique (`spdd-plan`, …).
  - (b) **Qualify as templates are**, `step_enhancement:<type>/<stem>`. This matches the template precedent
    (ADR-0044 d5; `logical-model.md` l.683–685). But `ArtifactId` has no `/`, so selection paths
    (`/selections/step_enhancement/{artifactId}`), preset ids and `policy.yaml` keys need grammar changes. `Urn`
    and the graph-shard patterns already allow `/`. spdd ids and edges are re-spelled.
- **Recommendation: (a).** It is the smallest change, matches ADR-0048 d10, and needs no grammar change on the
  activation surface. Templates need qualification because their id *is* a path, whereas an enhancement has an
  authored `id`. Report the violation as `prompt_fragment_invalid` or a dedicated code (§5 Q8).

---

## 4. #429 contract shape, and overlap with PR C

### 4.1 Step targets of `EnhancerWithoutTarget`

| Option | Shape | Pros | Cons |
|---|---|---|---|
| **1. `targets: [Urn]`** (minItems 1) | reuse `Urn.yaml` | Uniform with `Activation.denyNotApplied` and `EffectiveGraphNode.urn`; admits every node kind | changes the item type from object to string; core's DTO changes more |
| **2. New `NodeReference {kind: NodeKind, id}`** used only here (minItems 1, since C36 guarantees ≥1 target) | object, like `ArtifactReference` | Keeps the object form; `ArtifactReference` stays `ActivatableKind`, so selection and deny shapes are untouched | needs a new id type that admits `/` (`action` ids are `<type>/<step>`); `ArtifactId` cannot be reused |
| **3. Keep `targets: [ArtifactReference]` and add `stepTargets: [{missionType, step}]`** with `anyOf` requiring one of the two to be non-empty | additive member | Most explicit about steps | two members for one fact; a client must read both; it is still a new member on a closed response, so still Breaking |
| 4. Widen `ArtifactReference.kind` to `NodeKind` | – | one schema | Rejected: `ArtifactReference` types selections, `satisfiedBy`, `SharedSkip`, notices and dropped ids, where `action` is meaningless. It breaks the activatable-only invariant (I-8, ADR-0020) |

**Recommendation: option 2,** named `NodeReference` (`kind: NodeKind`, `id: string` with
`pattern ^[A-Za-z0-9][A-Za-z0-9_./-]*$`), `minItems: 1`. It is `### Breaking` (a documented response of
`getActivationSkippedReport` changes). The same type should serve WP28's `enhancedBy` for steps if PR C exposes one
(below).

**Second item (a pruned required or locked `action:`).** A floor `action:` cannot be authored under today's schemas.
`policy.schema.json` `artifacts` keys and `kind_defaults` exclude `action`. Preset lists have no action kind
(`preset.schema.json`). C31's `must(π)` therefore never holds an `action:`. The issue's example ("a policy locks
`action:software-dev/implement`") is refused at load by the policy schema. **Recommendation:** rule that E14
`requiredNotInForce` covers activatable kinds, *because* `Fl(C)` holds only activatable kinds by construction. Write
that sentence into E14 and C31. Have `InForceResolution` assert it, or type it so that an `action:` cannot enter
`locked` or `required` (I-12, directive 043, closing the class by construction). Pin both with a unit test. No
contract change and no DIV entry is needed, since this is not a difference from core. If the operator later lets a
policy lock a step (§5 Q2), revisit with `NodeReference`.

### 4.2 Overlap with PR C (WP27–WP29)

| Surface | #424 effective prompt / #428 / #429 | PR C |
|---|---|---|
| `service/activations/.../infra/packs/ClosureViewSession.java` | #429 changes `activatable()` filtering of enhancer targets (l.172–174, 233); #428 adds `step_enhancement` winners and bindings | WP28 shared edit: "map them" (projection, walk); handover: `MergedSnapshot.missionTypes` and `stepBinding` are PR C's |
| `service/packs/.../domain/InForceResolution.java` (`Edges.bindings`, grounding) | #428: a step enhancer's binding to its mission type (E6 "binding of a step enhancer is PR C") | PR C per E6/DIV-224 and the handover |
| `activations.domain.MergedSnapshot`, `PackCompositionFacts.bindings` | #428 feeds `step_enhancement` facts | PR C `stepBinding` |
| `schemas/EffectiveActionContext.yaml`, `paths/…_context.yaml` | if the effective prompt became a member of the context, or step enhancers were listed in it | WP28 owns both |
| Unknown-name refusals (`mission_type_not_found`, `action_not_found`), `CharterRefusalCode.yaml` | the prompt read needs the same 404s | WP28 raises `action_not_found` (a shared edit in `CharterRefusalCode.yaml`; #424 edits the same file for the four prompt codes, so a textual merge conflict is likely) |
| `ActionContextWalk` / `ClosureView.actionContext` (packs) | the prompt read needs "prompt in force for step s under the merged closure" plus the active enhancers of `action:M/s`, the same E12 enhancers-both-ways lookup | WP27 owns it |
| `EffectiveReadService`, `ActivationController`, `PacksSession` | a new read method plus a port | WP28 shared edits |
| `ReservedOperationCoverageTest` | a new reserved prompt read would make the count 1, not 0 | WP28 drives it to 0 |
| `SkippedReport`/`EnhancerWithoutTarget` | #429 | WP28 reuses the extended `SkippedReport` in the context |
| rules page E6, E12, E14, E16; divergence register | #424/#428/#429 docs | WP29 docs (DIV-204, offering-and-activation, logical-model) |

A gap neither side owns: `EffectiveActionContext.artifacts` lists `EffectiveArtifact` (an activatable kind) with
`enhancedBy`. A **step enhancer enhances the action itself**, which is not an artifact in that list, so an active step
enhancement has no place in the PR C context.

**Proposed ownership split**

1. **PR C (WP27–WP29) lands first, unchanged in scope, plus the step-enhancer binding** that E6, DIV-224 and the
   handover already assign to it. That is `MergedSnapshot.missionTypes`/`stepBinding` and grounding against
   `action:M/s`. Its tests are fixture-only, so the store binding (#428 item 1) is not needed for them. PR C does
   **not** touch the prompt or `EnhancerWithoutTarget`.
2. **#429 rides with PR C** if PR C is still open, because it is one schema (`EnhancerWithoutTarget`) on a read PR C
   already extends and a `ClosureViewSession` hunk WP28 already edits. Otherwise it goes in the #424/#428 mission
   immediately after. Either way the `NodeReference` type is introduced once and reused.
3. **The #424/#428 mission owns** the store binding and round trip, the load and compose checks, the four codes, and
   the effective-prompt read as a **new operation** (no edit to `EffectiveActionContext.yaml` or the context path).
   It builds on WP27's `ActionContextWalk`/`ClosureView` accessors and uses `NodeReference` for the step. It also
   decides whether the action context gains a `stepEnhancements` member (Breaking) or only a `_links.prompt` link.
   The link is not breaking: `Links.yaml` is an open map ("clients must tolerate a key they do not know"). A new rel
   needs a row in `docs/development/hypermedia-hateoas.md`, and it touches no WP28-owned file. Recommended: the link
   only.
4. **Sequencing:** the mission branches from `main` after PR C merges. If it must run in parallel, it stays off the
   files WP27/WP28 own (listed above) and coordinates on `CharterRefusalCode.yaml` only by separate hunks.

---

## 5. Contradictions and underspecification (operator decisions)

1. **Q1 Layout (§3.1).** Is `missions/<type>/enhancements/` "beside the kind" (ADR-0053 Q2), or the excluded
   "separate enhancements area"? Recommendation: A, keep and annotate.
2. **Q2 "A locked target takes no fragment" (d7) cannot fire for a step.** `policy.schema.json` cannot name an
   `action:` key or kind, so a step's integrity is always `open` today. Does a step inherit its mission type's level
   (`mission_type:<type>` entry or kind default)? If yes, write it into the rules page §1 and the conceptual model.
   If no, d7's integrity bullet is vacuous for steps.
3. **Q3 Is an enhancement an `enhance` or a `relate` op?** The rules page says an enhancement is "judged separately"
   from `ops`. An `enhances` edge is also an edge that touches the target (`relate`), which `extendable` permits and
   `locked` refuses. That agrees with the conceptual model, but the separate judgement is not written down.
4. **Q4 Cardinality of `enhances` per step enhancement.** d3 addresses a fragment by "the step" (singular), and the
   directory names one type. E6 allows several targets. Rule: exactly one `enhances` edge per `step_enhancement`,
   whose target is an `action:`. Otherwise one fragment would land in several steps, possibly of different types,
   and the directory check is undefined.
5. **Q5 Compose refusal "whatever the activation" (d8) vs d4 and d7.** An *inactive* enhancement in the closure
   whose slot a project prompt override dropped refuses the whole compose with `500` on every read. Is that
   intended? It makes a project unable to override a built-in prompt while any composed pack fills a slot of it,
   even an unused one. The alternative: refuse only when the enhancement is active, and warn otherwise.
6. **Q6 Where the effective-prompt read lives and how it is classed.** d9 says it "belongs with" R5 (#99, which
   the hand-over forbids taking) and R9 (#103), and calls it "additive (ADR-0009)". Under ADR-0022 and the hand-over
   rule, only a *new operation* is non-breaking; a member on `EffectiveActionContext` is `### Breaking`.
   Recommendation: a new operation under the activation set. The operator should confirm, and say whether every
   declared slot is listed (with `fragments: []`) or only filled ones.
7. **Q7 Companion naming.** The step-enhancement schema and `file-kinds.yaml` say "the `<id>.prompt-fragment.md`
   companion". An enhancement may fill several slots (d3) and so needs several companions, which cannot all be
   `<id>`. The schema's `path` pattern does not require `<id>`. Also unstated: may two enhancements share one
   fragment file, and is an orphan fragment (named by none) refused? (That matters for round trip and ownership.)
   Recommendation: one companion per entry, any stem, named by exactly one entry, orphans refused.
8. **Q8 Which code for the #428 semantic rules** (directory ≠ edge type, duplicate stem, missing or multiple
   `enhances` edges, no fragment and no other addition)? `prompt_fragment_invalid` is the only reserved code, but
   its name is fragment-specific. Options: reuse it with the description broadened ("an invalid step enhancement or
   prompt fragment"), which costs no new enum value, or add `step_enhancement_invalid` (a new enum value,
   `### Breaking`).
9. **Q9 An empty enhancement.** `prompt_fragments` is optional and guards and emits do not exist yet, so an
   enhancement with no additions is schema-valid ("an enhancement that carries no fragment disappears silently",
   #428). Refuse it (`minProperties` beyond id), or allow it as a pure activation anchor?
10. **Q10 Guards and emits now or later (§3.2).** "Fields" is settled by ADR-0054 d2 and ADR-0064 d11. Whether to
    add them before mission-type steps declare transitions and action points is a decision; recommendation:
    later, with an annotation and an issue.
11. **Q11 Id rule (§3.3).** Per-pack uniqueness (recommended) or `<type>/<stem>` qualification.
12. **Q12 `pack_unknown_key` (#430).** Keep it in the enum with an allowlist (recommended) or delete it.
13. **Q13 #429 second item.** Accept "E14 covers activatable kinds; the floor holds only activatable kinds by
    construction" (recommended; no DIV entry), versus widening `RequiredNotInForce`.
14. **Minor inconsistency to fix in passing:** `NodeKind.yaml` and `DiagnosticCode.yaml` `x-source` point at a
    non-existent `service/charter-domain` module. The #430 parity tests should also assert each `x-source` path.
