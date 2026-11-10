# Phase A: surfaces that share the defect shapes of #428, #424, #429 and #430

Role: Reviewer Renata (read-only review). Repo: `/home/user/spec-kitty-charter` at `391ed51b` (main). Sibling: `/home/user/spec-kitty-open-packs` at `22c14b2`.
No file was edited. There is no CodeGraph index, so the search used grep and reads.
Paths are relative to the charter repo unless stated otherwise.

## Summary table

| # | Sibling | Shape | Disposition |
|---|---|---|---|
| A1 | `step_enhancement` is missing from `DomainRepositoryService.declaredWithoutFile` and from `KindBindings.ALL` | (a) | in-scope-now (#428) |
| A2 | Whole-pack registration refuses `*.step-enhancement.yaml` and `*.prompt-fragment.md` as "unmodelled" | (a)/(b) | in-scope-now (#428) |
| A3 | `org-charter.yaml` is classified and validated, but no store holds it and a registration refuses it | (a) | file-and-link |
| A4 | `anti_pattern` is activatable but has no binding | (a) | none needed: it is held on purpose through graph nodes |
| A5 | `content_form: text` is declared but not built, while the skill body is already held as a TEXT companion | (b) | file-and-link (low) |
| B1 | Markdown companion inventory | (b) | only the prompt fragment is unheld (#428) |
| C1 | `ClosureViewSession`: about 15 sites route through `activatable()` | (c) | in-scope-now (#429): same file, same helper |
| C2 | The contract cannot carry `action:` either: `EnhancerWithoutTarget.targets` and `RequiredNotInForce.kind` are ActivatableKind | (c) | in-scope-now (#429): the contract side of the same bucket |
| C3 | `mergedOf` / `overridesOf` drops layer overrides of guard, lifecycle, template, asset and action | (c) | belongs to PR C (WP28 `layerOverrides`): link |
| C4 | `PackRegistrationMapper.kindOf` drops non-activatable notices | (c) | already tracked (#417): link only |
| C5 | `CascadeRules.selectable()`, `ActivationDocument.knownKind`, `ActivationFileCodec.versionOneKind` | (c) | by design (the activation names only activatable kinds); one low note on v1 |
| D1 | No parity row for contract NodeKind, CascadeKind or DiagnosticCode, and they have already drifted | (d) | in-scope-now (#430) |
| D2 | No parity row for EffectiveArtifactState/Reason, ArtifactPolicy, DenyNotApplied.reason, RequiredNotInForce.reason, ActivationWarning.code, and four ActivatableKind copies | (d) | in-scope-now (#430): same test, same shape |
| D3 | No parity row for PackRegistrationStatus, PackSource(Record).kind, PackRegistration.level/outcome | (d) | file-and-link |
| D4 | Stale `x-source` paths in NodeKind, Relation, DiagnosticCode and Diagnostic | (d) | in-scope-now (#430) |
| E1 | Codes or operations that are reserved and never raised or served | (e) | split: #424, PR C, retired |

---

## (a) Kinds declared in contracts that no KindBinding or store holds

I compared the declared kinds in `contracts/packs/file-kinds.yaml` (`node_kinds` :83-101, `artifact_kinds` :103-120, `files` :141-200) with `KindBindings.ALL` (`service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java:19-33`), `PackFileSet.yamlHolder` (`PackFileSet.java:408-419`), `WholePackStore.load` (modelled set, `WholePackStore.java:210-223`) and `DomainRepositoryService.compositionFacts` / `declaredWithoutFile` (`DomainRepositoryService.java:379-421`, `:576-596`).

The artifact kinds that have no binding in `KindBindings.ALL` are `anti_pattern`, `template` and `step_enhancement`. `template` is held through `TemplateBinding` as a bytes payload (`PackFileKind.BYTES_KINDS`, `PackFileKind.java:78`; `DomainRepositoryService.java:640-653`).

**A1. `step_enhancement` is not "defined" even when a graph declares it** (in-scope-now, #428)
- `DomainRepositoryService.java:581`: `Set<NodeKind> withoutFile = EnumSet.of(NodeKind.ANTI_PATTERN, NodeKind.ACTION);`
- Every enhancement also needs a source node for its `enhances` edge.
- Without a binding, and outside `withoutFile`, a `step_enhancement:<id>` URN never enters `defined`. The consequences:
  - no `LayerMerge` winner;
  - `PackCompositionResolver.java:506-509` refuses a reference to it with `pack_reference_artifact_missing`, because `authoredInGraphs()` and `artifactKind().isPresent()` are both true and `target.holds` is false.
- This is the same root as #428, at a second site. Fixing only `yamlHolder` would leave it. The #428 fix must make step enhancements part of `defined`, by a binding (preferred) or not at all. It must not quietly add the kind to `withoutFile`.

**A2. Whole-pack registration refuses any pack that holds a step enhancement** (in-scope-now, #428)
- `WholePackStore.java:210-223` builds `modelled` from bound places, bound companions, `pack.yaml`, graphs, assets, presets, `pack.md`, the manifest and the named policy.
- An enhancement YAML and its `*.prompt-fragment.md` are in none of these, so they become `unmodelled`.
- `WholePackStore.loadStaged` (`:244-248`) then refuses the pack with `validation_failed` ("no pack file kind models this file").
- `WholePackStore.write` silently drops unmodelled files (class javadoc, `:60-63`).
- So the inspector validates the files, the store skips them, and registration refuses them: three paths treat the same file three ways.
- The open packs at `22c14b2` hold no enhancement yet (`find packs -name '*.step-enhancement.yaml'` returns nothing), so nothing is broken today. The first pack that ships one hits this.
- The #428 acceptance should include a whole-pack round trip and a `loadStaged` test for a pack with an enhancement and its fragments.

**A3. `org-charter.yaml` is classified and validated, but no store holds it** (file-and-link)
- `file-kinds.yaml:146-147` maps `*/org-charter.yaml` to schema `org-charter`.
- `PackPolicy.java:833` gives it its own finding code, `ORG_CHARTER_SCHEMA_ERROR`.
- No `KindBinding` covers it, and `yamlHolder` returns empty for it, the same way it does for an unbound kind.
- It is not in the `modelled` set of `WholePackStore.load`, so a whole-pack registration of a pack that contains one is refused as `unmodelled`, and a whole-pack write drops it.
- Draft issue: *"`org-charter.yaml` is validated but held by no store, and a whole-pack registration refuses it."* `contracts/packs/file-kinds.yaml` classifies `*/org-charter.yaml` (schema `org-charter`), and validate mode reports `org_charter_schema_error` for it (`PackPolicy.java:833`). But no `KindBinding` holds it, `PackFileSet.yamlHolder` skips it, and `WholePackStore.load` leaves it out of the modelled set. A whole-pack registration of a pack with an org charter therefore fails with `422 validation_failed` ("unmodelled"), and `WholePackStore.write` drops the file silently. Decide one of two fixes: (a) model it (a binding, or a pack-level slot like presets and the manifest), or (b) move it to `exclusions` with a reason, or refuse it by name. Add a test that classification, validate mode, the load and registration all agree on the file. Related: #428 (the same "classified, validated, unheld" shape for step enhancements).

**A4. `anti_pattern`: activatable with no binding, by design** (none found)
- `ArtifactKind.java:34` has the comment "never a standalone file".
- It is held through graph nodes in `declaredWithoutFile` (`DomainRepositoryService.java:581`). It is in `defined`, and it can win a merge.
- This is not a defect. It is the pattern #428 should not copy for `step_enhancement`, because step enhancements are files.

`guard` and `lifecycle` are bound (`GuardBinding`, `LifecycleBinding`) and are non-activatable on purpose (`ArtifactKind.java:46-56`). They are not siblings for (a). They do matter for (d) (see D1).

**A5. `text` content form: declared but not built** (file-and-link, low)
- `file-kinds.yaml:31-35` says `text` "is declared but not built yet", and a rule that names it fails the table.
- `PackFileSet.java:398` and `FileSystemPackInspector.java:205` both throw `notStored` for `TEXT`.
- But `SkillBinding.java:51-56` already holds the skill body as a `ContentForm.TEXT` companion. The companion is not classified by `file-kinds`; it is modelled through `companionFiles`.
- This is a reserved vocabulary entry that is half-built. Draft issue: *"The `text` content form is declared in `file-kinds.yaml` but refused, while skill bodies already use `ContentForm.TEXT` as a companion."* Either build the form and add a rule for skill bodies, or reword the table comment to say that text exists only as a companion form. Otherwise a reviewer cannot tell whether a skill body is classified. This does not block the four issues.

## (b) Markdown and other non-YAML companions, and how each is held

**B1. Inventory** (only the prompt fragment is unheld)

| Companion | Rule | Holder | Round trip |
|---|---|---|---|
| `toolguides/*.guide.md` | `file-kinds.yaml:155-158`, schema `toolguide-guide` | `ToolguideBinding.java:55-69` (`companionStructures`) | held, read and written with the toolguide |
| `*/pack.md` | `:161-163`, `pack-document` | the Pack document slot (`PackFileSet.markdownHolder`, `WholePackStore.java:217`) | held |
| `missions/*/steps/*.prompt.md`, `*.guidelines.md` | `:168-173` | `MissionTypeBinding.java:124-133`, `:224` | held |
| `missions/*/enhancements/*.prompt-fragment.md` | `:177-182` | **none**: `KindBindings.DEFERRED_COMPANION_STRUCTURES` (`KindBindings.java:49`), skipped by `PackFileSet.java:430-432` and validated by `FileSystemPackInspector.java:260-261`, `:407` | **unheld**, unmodelled at registration (A2) |
| skill body (`body_path`) | not classified | `SkillBinding.java:51-56`, TEXT companion | held (see A5) |
| asset payloads, `templates/**`, `missions/*/templates/**` | `payloads` `:255-265`, bytes | `AssetBinding`, `TemplateBinding` | held byte for byte |
| `README.md` under `missions/`, `assets/` | `exclusions` | none, by design | a whole-pack registration lists them as `unmodelled` and refuses them. This is already the documented behaviour ("a registration holds only modelled files"); not a sibling. |

When #428 binds `step_enhancement`, the deferred set should become empty and `isDeferredCompanionStructure` should be deleted. Leaving it as dead code would hide the next deferred kind. Three branches must go: `KindBindings.java:43-49` and `:111-113`, `PackFileSet.java:431`, and `FileSystemPackInspector.java:260`.

## (c) URN filtering through `activatable()` or ActivatableKind that drops action, mission_type or other references

Searches: `activatable()`, `.artifactKind()` and `ArtifactRef.of(` in `service/**/src/main`, and every contract schema that `$ref`s `ActivatableKind.yaml` or `ArtifactReference.yaml`.

**C1. Every filtering site in `ClosureViewSession` goes through one helper** (in-scope-now, #429)

`service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java:229-231`:

```java
private static Optional<ArtifactRef> activatable(Urn urn) {
    return ArtifactRef.of(urn).filter(ref -> ref.kind().activatable());
}
```

These sites use it:
- `:98-100` languages
- `:113-124` winners
- `:128-141` edges (source)
- `:143-147` edges (target)
- `:149-157` layer overrides (C3)
- `:171` in-force states
- `:173-174` `enhancedTargets`: the key **and** the values through `listOf`. This is how step-enhancer targets (`action:<M>/<step>`) become `[]`.
- `:177` `staleIds` through `listOf`: a stale selected or denied `action:` or guard id vanishes from the stale report.
- `:180` `requiredNotHeld` through `listOf`: a must-have `action:` item that no pack holds vanishes.
- `:191-192` `satisfiedBy` / `overriddenBy`: a non-activatable overrider becomes empty.
- `:195-205` `denyNotApplied`
- `:207-215` `overrideNotApplied`: dropped when either side is non-activatable.
- `:217-226` `requiredNotInForce`

Why these belong in #429:
- They share the helper and the file.
- Every report bucket named in `InForceStates` (`service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceStates.java:16-27`) passes through it.
- `InForceResolution.bindingOf` (`InForceResolution.java:345-351`) puts `action:` URNs into play on purpose.

Fixing only `enhancedTargets` and `requiredNotInForce` would leave `staleIds`, `requiredNotHeld` and `overrideNotApplied` with the same silent drop. The #429 test should table every bucket against a non-activatable URN.

`reachOf` / `vias` / `refs` (`:257-308`) use `ArtifactRef.of` without `activatable()`, so only `action:` is dropped there. That is documented in the class javadoc (`:38-40`) and offset by `writer()` (`:286-295`). It is not a defect, but the #429 test should assert it so the behaviour stays pinned.

**C2. The wire also cannot carry `action:`** (in-scope-now, #429)
- `contracts/charter/schemas/EnhancerWithoutTarget.yaml:27-30`: `targets.items` is `ArtifactReference.yaml`, whose `kind` is `ActivatableKind` (`ArtifactReference.yaml:10-12`).
- `RequiredNotInForce.yaml:13-16`: `kind` is `ActivatableKind`.
- `DenyNotApplied.yaml:13-16` and `OverriddenArtifact` are the same.
- Fixing the Java mapping alone is therefore not enough. `EffectiveReadMapper.java:168` (`EnhancerWithoutTarget.KindEnum.fromValue`) and `:179` (`RequiredNotInForce.KindEnum.fromValue`) would throw `IllegalArgumentException` for `action`. #429 needs a contract change, for example `targets` items as a URN string or a `NodeKind`-typed reference, with a `contracts/charter/changes/` fragment. Otherwise the fix trades a silent drop for a 500.

**C3. Layer overrides of non-activatable kinds are dropped** (PR C, link)
- `ClosureViewSession.java:149-157`, `overridesOf`, filters `closure.layerOverrides()` through `activatable()`.
- A layer override of a guard, lifecycle, template, asset or `action:` never reaches `MergedSnapshot`.
- WP28 (`origin/issue-10-layer-merge-effective-reads:kitty-specs/layer-merge-and-effective-reads-01M6R0J6/tasks/WP28-c-contract-graph-context.md`) adds `EffectiveGraph.layerOverrides: [LayerOverride]`, a new `contracts/charter/schemas/LayerOverride.yaml` ("overrides whose artifact is in force").
- WP28 should decide whether `LayerOverride.kind` is NodeKind (keep them) or ActivatableKind (drop them, documented). Hand-off note for PR C: "`ClosureViewSession.overridesOf` drops non-activatable layer overrides; pick NodeKind for `LayerOverride` or record the drop as a divergence."

**C4. Registration notices drop non-activatable kinds** (already tracked: #417)
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java:145-168` logs and drops the entry ("issue #417").
- `contracts/charter/schemas/PackRegistrationNotices.yaml:2-6` says the same.
- A lock or floor entry on an `action:` is dropped the same way. Link #417 from #429 as the sibling; do not fix it here.

**C5. Activation-side filters, by design** (none needed, one low note)
- `CascadeRules.java:342`, `:351`, `:367`, `:379`, `:386` and `:419` (`selectable()` / `requireDeniable`) restrict cascade reports to kinds that an activation can name. `SkippedDeactivated.kind` is ActivatableKind in the contract. This is consistent, not a defect.
- `ActivationDocument.java:524-531` (`knownKind`) refuses non-activatable lists with `unknown_kind`. Correct.
- Low note: `ActivationFileCodec.java:123-128` (`versionOneKind`) accepts any activatable kind except `mission_type` in a **version 1** document, so `step_enhancement` is now accepted in v1, a kind that did not exist when v1 was defined. Mention it in #428 review only if v1 must stay frozen.

## (d) Contract enums with no parity test against Java, and drifted values

There are 19 contract files under `contracts/charter` with an `enum:`. `WireTokenParityTest` (`service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java:71-117`) covers:
- ActivatableKind, through four schema copies plus `SelectedArtifacts`;
- CharterRefusalCode;
- ArtifactKindToken;
- PackLevel.

Other coverage:
- `Relation` has `RelationSetParityTest` and `RelationTableParityTest`.
- `VocabularyEqualityTest.java:273-293` checks only that the projected load-path codes are a **subset** of `DiagnosticCode` ∪ `CharterRefusalCode`. That is why `pack_unknown_key` drifted unseen.

**D1. The #430 enums, with drift found** (in-scope-now, #430)

`NodeKind.yaml:6-22` has 16 values; Java `NodeKind` has 18.
- The contract **lacks `guard` and `lifecycle`**.
- Java's `authoredInGraphs()` is false for those two (`NodeKind.java:59-61`). The parity row must either compare against `authoredInGraphs()` kinds or add both values to the contract.
- `EffectiveGraphNode.yaml` uses NodeKind, so this also decides whether PR C's graph can list guards and lifecycles. Coordinate with WP28.
- The contract description, "every artifact kind plus action and mission_type", is wrong both ways: `mission_type` is an artifact kind, and guard and lifecycle are missing.

`CascadeKind.yaml:7-21` is the 13 activatable tokens plus `all`.
- They match `ArtifactKind.activatable()` today.
- `CascadeScopeTest.java:42` checks only `all` against the activatable kinds, not the contract.

`DiagnosticCode.yaml:15-18` has `pack_level_missing`, `pack_unknown_key` and `prompt_slot_not_empty`. Java `Diagnostic.Code` (`service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java:17-24`) **lacks `pack_unknown_key`**. That is the known #430 drift; the contract keeps the value as retired.

**D2. More rows for the same test** (in-scope-now, #430)

Each Java side below is mapped by `fromValue` with only spot tests (`EffectiveReadMapperTest.java:106-110`, `:280`, `:300`; `ActivationChangeMapperTest.java:149-152`). A new Java constant would turn into a runtime 500.

| Contract | Java | Note |
|---|---|---|
| `EffectiveArtifactState.yaml` (3) | `EffectiveSet.State` (`EffectiveSet.java:45-51`) | match |
| `EffectiveArtifactReason.yaml` (9) | `EffectiveSet.Reason` (`:64-82`) | match |
| `ArtifactPolicy.yaml` (3) | `EffectiveSet.Policy` (`:95-101`) | match; `PacksAvailabilityTest.java:833` checks only MutabilityLevel ⊆ Policy |
| `DenyNotApplied.reason` (2) | `EffectiveSkippedReport.DenyReason` (`EffectiveSkippedReport.java:52-56`) | match |
| `RequiredNotInForce.reason` (2) | `EffectiveSet.Reason` (9) through `EffectiveSkippedReport.RequiredNotInForce(ArtifactRef, Reason)` (`:134`) | **type is wider than the contract**: any reason other than the two would throw at `EffectiveReadMapper.java:181`. Narrow the Java type or add a row with an explicit allowed subset. |
| `ActivationWarning.code` (2) | `ActivationChange.Code` (`ActivationChange.java:50-55`) | match |
| ActivatableKind in `DenyNotApplied.kind`, `EnhancerWithoutTarget.kind`, `RequiredNotInForce.kind`, `OverriddenArtifact.kind` | `ArtifactKind.activatable()` | **not in the `pairs()` list.** The test's comment says "each copy is a pair of its own", but only 4 of the 8 inlined copies are paired. |

**D3. Registration enums with no parity** (file-and-link)

| Contract | Java | Current state |
|---|---|---|
| `PackRegistrationStatus.status` (pending, running, succeeded, refused) | `RegistrationJobs.State` (`RegistrationJobs.java:71-79`) | match |
| `PackSource.kind` / `PackSourceRecord.kind` (https_archive, git, filesystem) | `PackSourceKind` (`PackSourceKind.java:9`) | match |
| `PackRegistration` level (org, project) and outcome (created, replaced, unchanged) | `PackRegistrationService.Outcome` (`:45`) | match |

All values match by eye; none has a row. Draft issue: *"Parity rows for the registration enums."* `WireTokenParityTest` pins ActivatableKind, CharterRefusalCode, ArtifactKindToken and PackLevel. It does not pin `PackRegistrationStatus.status` against `RegistrationJobs.State`, `PackSource.kind` / `PackSourceRecord.kind` against `PackSourceKind`, or `PackRegistration.outcome` and `level` against `PackRegistrationService.Outcome` and the registrable `PackLevel` subset. They match today, but a constant added on either side would surface as a `fromValue` 500 or an unreachable contract value. Add one `Pair` per enum, as #430 does for NodeKind, CascadeKind and DiagnosticCode. Related: #430.

**D4. Stale `x-source` paths** (in-scope-now, #430: the same files)

The `x-source.path` in these files points to `service/charter-domain/...`, which does not exist:
- `NodeKind.yaml:24` (actual: `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java`)
- `Relation.yaml:28` (actual: `service/kernel/.../Relation.java`)
- `DiagnosticCode.yaml:20` (actual: `service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java`)
- `Diagnostic.yaml:15` (actual: the same `service/packs/.../domain/Diagnostic.java`)

The other 38 `x-source` paths exist. A parity row that reads the class named by `x-source` would catch this; at minimum, #430 should fix the three files it touches.

## (e) Reserved refusal codes and reserved operations, declared but never raised or served

The `x-refusal` map in `contracts/charter/schemas/CharterRefusalCode.yaml` marks these codes `raised: false`:

| Code | Owner | Evidence |
|---|---|---|
| `prompt_slot_malformed`, `prompt_slot_not_empty`, `prompt_fragment_invalid`, `prompt_slot_missing` (500) | **#424** | description `:161-167`; `RefusalCode.java:188-195`; Java names them only in `RefusalHandler.java:365-368` (the status map) |
| `action_not_found` (404) | **PR C, WP28** ("`action_not_found` becomes raised"; `ReservedRefusalCodesTest` moves it to the raised set) | description `:29-32`, `:57-58`; `RefusalHandler.java:320` |
| `pack_level_mismatch`, `drg_schema_error`, `unresolved_edge_endpoint` (500) | retired, kept so the contract keeps accepting them; no action | description `:32-35`, `:156-158`; allow-list `ReservedRefusalCodesTest.java:70-72` |

`DiagnosticCode`:
- `prompt_slot_not_empty` is the warning form for unversioned descriptors. It is reserved for **#424** (`DiagnosticCode.yaml:11-13`) and declared in Java (`Diagnostic.java:24`), but nothing raises it.
- `pack_unknown_key` is retired and never raised. It is in the contract only, and missing from Java: that is **#430**.

Reserved operations (`x-reserved`, 501):
- `getEffectiveGraph`: `contracts/charter/paths/activations_activationId_graph.yaml:8-13`, `ActivationController.java:174`.
- `getEffectiveActionContext`: `paths/activations_activationId_missionTypes_missionType_actions_action_context.yaml:11-18`, `ActivationController.java:181`.
- Both belong to **PR C** (WP27 walk, WP28 contract and serve, `ReservedOperationCoverageTest` 2 → 0). WP29 adds no reserved surface.

Declared in docs but with no contract surface:
- The "effective-prompt read" that `docs/changelog/2026-11-08-prompt-fragment-contracts.md:42-44` promises for **#424** has no path under `contracts/charter/paths/` (grep for `prompt` finds none).
- #424 must either add it as an `x-reserved` operation, or say in the issue that it lands later.
- The same changelog (`:45`) and `step-enhancement.schema.json:5` defer the "guard and emit additions" to **#428**. That is schema content #428 must add, not only a binding.

Other reserved markers I checked, none of which are siblings:
- The `RefusalCode.java` javadoc says "Reserved (#125)" for the activation codes (`:68-88`), but the contract marks them raised (`CharterRefusalCode.yaml:44-55`). That is stale javadoc, cosmetic.
- `RelationType.propagation()` is deferred (`RelationType.java:15`, ADR-0027). It is unrelated.

## Notes for the orchestrator (anti-pointfix checklist)

- **#428** must cover:
  - `KindBindings.ALL`;
  - `declaredWithoutFile` or `defined` (A1);
  - the `WholePackStore` modelled set and the `loadStaged` refusal (A2);
  - removal of the deferred-companion branch (B1);
  - the guard and emit schema additions (E);
  - optionally, the v1 codec acceptance (C5).
- **#424** must cover the four refusal codes, the `prompt_slot_not_empty` diagnostic, and the effective-prompt read surface, which is missing from the contract.
- **#429** must cover:
  - every bucket in C1, not only `enhancedTargets` and `requiredNotInForce`;
  - the contract types in C2.
  - C3 goes to PR C; C4 links to #417.
- **#430** must cover:
  - NodeKind, including the guard and lifecycle decision (D1);
  - CascadeKind and DiagnosticCode;
  - the D2 rows, including the four unpaired ActivatableKind copies and the too-wide `RequiredNotInForce.reason` type;
  - the stale `x-source` paths (D4).
  - D3 is filed separately.
