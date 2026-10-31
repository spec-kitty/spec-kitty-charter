# Post-plan adversarial review: #194 PR-B (mission-type consolidation)

Profile: architect-alphonso (design, evaluate, decide). Read-only. Lens: architectural integrity and domain modelling.
Inputs: ADR-0044, `kitty-specs/mission-type-consolidation-01M65MCK/{spec,plan,data-model,research}.md`, `decisions/` (D1, D1a,
D2 to D6, treated as binding), `research/squad/*`, `service/{kernel,packs,profiles,deployable}`, ADR-0035 to 0043, open packs at
`5df92a5`. I relitigate no ruling. Every finding is a place where the design fails a ruling, an ADR or the code as it is now.

Paths are shortened: `S/` = `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`,
`T/` = `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/`, `M/` = `kitty-specs/mission-type-consolidation-01M65MCK/`.

## Ranked findings

### 1. BLOCKER: the store's create path refuses the nested `fileName` that FR-007 requires
- **Evidence:** `S/FilesystemPackStore.java:837-843` `newFile` resolves `kind.fileName(id)` and refuses anything whose parent is
  not the pack directory (`if (!directory.equals(target.getParent()))`, the guard against separator and climb injection).
  `PackLayout.java:9-13,54-56` documents the canonical place as "its kind's file name directly in the pack directory". Plan
  IC-04 (`M/plan.md:86`) and FR-007 set `fileName(id)` to `missions/<id>/<id>.mission-type.yaml`, so `FilesystemPackStore.write`
  of a new mission type fails with "does not name a file in the pack". `T/KindBindingsTest.java:135`
  (`theDefaultFileNameClassifiesBackToTheKind`) writes `pack.resolve(name)` without creating parents and fails too. Only
  `WholePackStore.put` (`:487`, which runs `createDirectories`) would work, so the harness passes while the store's own upsert
  breaks.
- **Fix:** keep the containment guard but restate it. Validate the id first (kebab slug: no `/`, no `..`), then require
  `target.equals(directory.resolve(binding.fileName(id)).normalize())` with `target.startsWith(directory)`, and let
  `writeAtomically` create the directories. Update the `PackLayout`, `newFile` and `KindBinding.fileName` Javadoc ("directly in
  the pack directory" is no longer true) and the KindBindingsTest probe (`createDirectories(parent)`). Name this in IC-04.

### 2. BLOCKER: the `retired` section has no designed path through the code, and the Markdown families escape it
- **Evidence:** `PackFileKind.Classification` is sealed (`S/PackFileKind.java:72`: `Mapped | Excluded | Unclassified`). A
  fourth classification has to be handled in every exhaustive switch: `Companions.claimedByRule` (`S/Companions.java:174-190`),
  `FileSystemPackInspector.readAll` (`:213-224`) and the gate census (`T/WholePackCensus.java:107-114`, which maps everything that
  is not Mapped to `other:<segment>`). Plan IC-05 (`M/plan.md:94-97`) names none of them. The store load does not see such a
  file: `PackFileSet.loaded` drops every non-Mapped file (`S/PackFileSet.java:319-321`), and `PackRead` has no channel for a file
  that belongs to no kind. In validate mode the YAML walk lists only `*.yaml` (`FileSystemPackInspector.java:212`). The Markdown
  walk (`:226-231`) reads only files classified as companion structures. So the retired `prompt.md`, `guidelines.md` and
  `command-templates/*.md` (ADR-0044 §6) are never refused in validate mode. Spec US4 (`M/spec.md:98-110`) lists only the seven
  YAML families, so its test would not catch this.
- **Fix:**
  - Add `Retired(match, reason)` to the sealed hierarchy and collect it once, in `PackFileSet.Listing.retired`.
  - Have `FilesystemPackStore.read` and validate mode both report from that one listing: the store as a `PackElementViolation`
    (`file`, `retired`, `reason`), the inspector as `FileRead.Unreadable` mapped to `ARTIFACT_SCHEMA_ERROR`. Validate mode must not
    keep a second walk.
  - `claimedByRule` refuses a retired path. The census gives retired files their own `retired:` bucket, so a stale file reads as
    red and is not folded into `other:`.
  - The US4 fixture holds one file of each of the eight families, the `.md` ones included, at both the root layout
    (`qa/mission_types/`, `qa/mission-steps/`) and the `missions/` layout.
  - Have the overlap check cover retired rules in both directions. Add a sunset note: the section is permanent legacy vocabulary
    in the "single data authority", so give it a removal trigger in the ADR.
- **Alternative to weigh:** treat any unclassified file under a `missions/<t>/` directory as refused, after giving
  `missions/<t>/templates/**` a payload or exclusion rule. That needs no list of legacy names, and it closes finding 6's hole as
  well.

### 3. MAJOR: retired files would be read but not fingerprinted, which breaks the PackFileSet/ADR-0039 promise
- **Evidence:** the `PackFileSet` class doc (`S/PackFileSet.java:30-36`) says "a file the service loads cannot escape the
  fingerprint". ADR-0039 Context gives the same reason: a file that is read but not digested can change unseen. `digested`
  (`:211-247`) lists only bound kinds, companions, shards, fragment, payloads and the pack-level tail. A retired file that
  produces a violation is outside the digest, so deleting it changes no fingerprint or ETag (`ConsistentRead`,
  `FileSystemPackSources.java:35`, `PackCommitStore`). The served load could then keep reporting a violation that no longer
  exists.
- **Fix:** append the retired files to the digest in the tail, after the manifest, in path order, so a pack without any keeps
  its exact digest (the NFR-002 pattern of #193 and ADR-0042). Add a `PackFileSetTest` case that plants one. State the digest
  position in IC-05.

### 4. MAJOR: `prompt_path` is described three incompatible ways, and the domain is made to know storage paths
- **Evidence:**
  - ADR-0044 §3 (`:138-139`) and FR-001 make `prompt_path` and `guidelines_path` authored fields.
  - `M/data-model.md:40` calls them "derived from the id (S8)".
  - FR-006 (`M/spec.md:153`) makes "companions' paths are `steps/<id>…`" a `MissionType` invariant.
  - ADR-0042 S8 (`docs/adr/0042…:108`) says a derived field is *declared* derived in the per-kind table, recomputed by the writer
    and verified rather than compared. ADR-0044 declares no such table, and it cites S8 as a reason to *drop* fields
    (`:156`), which misreads S8.
  - ADR-0037 d5 (`:167`) and `PackLayout` keep a file's place out of the entities. An invariant in `:missions` on `steps/<id>…`
    breaks that, and `ToolguideBinding` sets the precedent of a path checked in the binding, not in the domain.
- **Fix:**
  - Domain: `MissionStep` holds `Optional<StepDocument>` only, with no path. Drop the path clause from FR-006.
  - ADR-0044 §3: declare `prompt_path` and `guidelines_path` as S8-derived, giving the derivation (`steps/<id>.prompt.md`), and
    say the field's presence is what is authored. S13 still requires the field, because ADR-0039 d1 says a companion is named by
    a field.
  - Binding: `MissionTypeBinding.toEntity` refuses a path that is not the derived one, naming the type, step and path (US2 AC2).
    `toDso` writes the derived path.
  - Gate: the gate verifies the path rather than comparing it.

### 5. MAJOR: US2 AC4 ("an unowned `steps/*.prompt.md` fails the read") has no mechanism in the code or the plan
- **Evidence:** a Markdown file classified as a companion structure but named by no artifact is dropped by
  `PackFileSet.markdownHolder` (`S/PackFileSet.java:352-357`, which returns empty). `WholePackStore.load` lists it as
  `unmodelled` (`S/WholePackStore.java:186-199,255-266`) and adds no violation. Validate mode validates it alone and accepts it
  (`FileSystemPackInspector.java:226-239`). A toolguide guide behaves the same way today, which ADR-0043 (`:79,374`) records as a
  gate-level red, not a load failure. No IC designs the check.
- **Fix:** choose one.
  - (a) Add an owned-companion check: after `readKind`, every file the listing classifies as a companion structure must be in the
    `claimed` set, otherwise it is a `companion` violation naming the file. Apply it in both the load and validate mode, for
    toolguide guides too, and add a CHANGELOG line because it is a behaviour change for guides.
  - (b) Reword AC4 to "the whole-pack gate reports it red and the load lists it unmodelled". Prefer (a), because it is the "cannot
    hide" property that §6 asks of retired files.

### 6. MAJOR: the `files` glob is narrower than the kind, so a misplaced mission-type file is skipped without a word
- **Evidence:** `*/missions/*/*.mission-type.yaml` (`M/plan.md:73`, FR-003). Every other artifact rule is `**/*.<token>.yaml`
  (`contracts/packs/file-kinds.yaml` `files`). A `missions/x/steps/y.mission-type.yaml`, `x.mission-type.yaml` at the pack root, or
  `missions/a/b/a.mission-type.yaml` is `Unclassified`, and the header says such a file is "left unclassified, not refused". That
  is the same hide hazard the retired section exists to close. The planned placement check then never fires, because only files
  the glob already places reach it.
- **Fix:** use `**/*.mission-type.yaml` and make placement the refusal (finding 7). Keep the companion globs narrow, since
  ownership covers them.

### 7. MAJOR: the placement rule is unrecorded in the ADR and is built as a second mechanism beside the stem rule
- **Evidence:** ADR-0044 §5 shows the layout but does not say the place is *enforced*. ADR-0037 d5 says a place is authoring, not
  meaning. Spec AC1.2 (`M/spec.md:46-47`) refuses a misplaced file. Plan IC-04 adds a new `KindBinding.placementMismatch` default
  "used by FileStems callers". `FileStems.mismatch` (`S/FileStems.java:40-45`) already runs in both the load (`FilesystemPackStore`
  `:518`) and validate mode (`FileSystemPackInspector.misnamedArtifact` `:357-368`).
- **Fix:** record in ADR-0044 §5 that the place of a mission type is fixed (S1 extended: the file's pack-relative path is
  `fileName(id)`), as a stated exception to ADR-0037 d5. Implement it as one generalisation of the existing rule: when
  `idIsFileStem()` holds, `FileStems.mismatch` compares the pack-relative path with `binding.fileName(id)`. For the existing kinds
  the result is unchanged: it reduces to the stem comparison when `fileName` has no directory. Validate mode then reports it as the
  same `const` violation at `/id`, with no new hook. Drop `placementMismatch`.

### 8. MAJOR: the qa and cave-kitty DRG fragments point into retired files, and the migration scope omits them
- **Evidence:** open packs `qa/drg/fragment.yaml:95-98` and `cave-kitty/drg/fragment.yaml:16-18` hold a `kind: mission_types` node
  with `body_path: mission_types/<id>/governance-profile.yaml`. The research found this (`M/research/squad/architect.md:22`,
  `divergence.md:75,256`). ADR-0044 §8, spec US5 and plan IC-07 do not carry it into the migration. The service stores
  `FragmentNode.bodyPath` (`S/GraphDocument.java:179`) without resolving it, so the gate stays green while core's validate breaks on
  a dangling path. The fragment `title` ("QA Mission Type") is also a further home of the label.
- **Fix:** add to IC-07 and to FR-010's mapping table: rewrite each fragment `body_path` to
  `missions/<id>/<id>.mission-type.yaml`, which the fragment writer round-trips. Add a corpus assertion that every fragment
  `body_path` names a modelled file. List the label duplication as a #189 follow-up.

### 9. MAJOR: a parallel reference vocabulary, and Markdown content rules copied across the slice boundary
- **Evidence:**
  - ADR-0043 S14 (`:103`) says a reference `type` is "an artifact-kind token". `ProcedureReference(ArtifactKind type, …)`
    (`packs/domain/ProcedureReference.java`) follows it, and the schemas carry an `ArtifactKind` `$defs` enum.
  - The data model adds `MissionReference` with a new `ReferenceType` enum (`M/data-model.md` Values), a second vocabulary for the
    same meaning (S6).
  - `StepDocument` restates the Markdown content rules: "no CR, no H1/H2 at column 0 outside a fence". In the code those rules live
    in `MarkdownCodec` and `packs.domain.Guide.CONTENT_RULES` (`GuideSection.java`), and `:missions` cannot depend on either
    (SLICES_DO_NOT_CROSS).
  - A drift lets the domain accept content the codec cannot write. `planCompanions` then throws "a Markdown model the canonical
    form cannot express" at write time (`S/FilesystemPackStore.java:707-712`), not at load.
- **Fix:**
  - `MissionReference(ArtifactKind type, String id, …)`, with an invariant that `type` is one of the six doctrine kinds, so the
    schema enum is a subset of `ArtifactKind`. Better still, lift one `ArtifactReference {type, id, when, reason}` value into
    `kernel.domain` and use it from procedures and missions. That is a small refactor and it removes the third reference record
    (`profiles` has its own).
  - Lift the content rule into the kernel next to it, and add a property test in `:packs` that every `StepDocument` the domain
    accepts survives `MarkdownCodec.write`→`parse`.
  - Use the kernel `ModularRuleset`/`ConstraintBreachException` idiom of `:profiles` and `:packs`, not bare
    `IllegalArgumentException` (`M/data-model.md:4`).

### 10. MAJOR: NFR-001 ("no response changes") is not true once `mission_type` resolves as an artifact
- **Evidence:** `RelationshipRegistrationService.registerRelationship` (`packs/application/…:84-90`) refuses with 409
  `REGISTRATION_CONFLICT` a `from` URN that is not among `pack.artifacts()` URNs (`:114-116`). Today
  `mission_type:software-dev` is refused. After PR-B it is accepted and goes through `WritePolicyGate` (201). That registers a
  minted relationship from a mission type over HTTP, which sits uneasily with "references mint no relationship until #189". The
  research recorded it (`M/research.md` F3: "`/relationships` accepts mission URNs").
- **Fix:** either reword NFR-001 to "no schema or operation change" and record the behaviour change, with a contract scenario
  under `contracts/charter/scenarios/` and a CHANGELOG line, or keep mission types out of `urnsOf` until #189. Decide in the
  ADR's Consequences, not by default.

### 11. MINOR: the `ArtifactKind.MISSION_TYPE` facts as planned cannot be written, and some fact tests are hand-pinned
- **Evidence:** IC-02 (`M/plan.md:60-61`) says "in force when absent: no". `InForceWhenAbsent` has only `ALL` and `REQUIRED`
  (`kernel/…/ArtifactKind.java`). The `activatable()` Javadoc says false is "only for kinds that resolve specially (`asset`,
  `template`)". These tests flip:
  - `ArtifactKindTest:48` (`fromPlural("mission_types")).isEmpty()`) and the `everyArtifactKindIsANodeKind` exclusion list.
  - The NodeKind Javadoc ("empty for ACTION and MISSION_TYPE").
  - `VocabularyEqualityTest:169`, the hand list of "ten kinds", which gains `mission-type`.
  - `NodeKind.fromPlural`'s `"mission_types"` case, which becomes dead code.
- **Fix:** `MISSION_TYPE("mission_type","mission_types", false,false,false, InForceWhenAbsent.ALL)`, with the Javadoc saying that
  presets activate it through `activated_mission_types`. Remove the special case, and list each test edit in IC-02. Add the
  divergence entry from the ADR's Risks.

### 12. MINOR: `template` is a path to an unmodelled file, and its base is contradictory
- **Evidence:** ADR-0044 §3 (`:140`) says `template_path` is "relative to the mission-type file", so
  `templates/plan-template.md` resolves to `missions/<t>/templates/…`. `M/data-model.md` StepTemplate says "relative, inside the
  pack, `templates/...`", which reads as the pack-level `templates/` (the modelled `template` kind). The pinned source is a bare
  `template_file`. Nothing checks that the target exists, so the path is unvalidated and points at a file that `other:missions`
  keeps unmodelled. `artifact_key` also stays as a key name while `artifact_key` is renamed to `id` in expected artifacts.
- **Fix:** fix one base (relative to the mission-type file) in all three documents. Have the binding check that the path resolves
  inside `missions/<id>/templates/` and exists, or record it as an unchecked path until mission templates are modelled (the #186
  follow-up).

### 13. MINOR: the meaning of `on_demand_steps` is ambiguous for equality of meaning
- **Evidence:** ADR §2 says "an unordered set", the data model says `Set` (insertion-ordered), the YAML holds a list, and the gate
  compares trees. If the set is truly unordered, a reordered list is equal in meaning and the gate would need a comparator
  exception, which S16's rationale forbids. Without that exception, the order is meaningful.
- **Fix:** state "authored order, kept; ids unique; not a run order". The domain uses a `List` with uniqueness, D1's `Set`
  multiplicity is preserved by the id invariant, and the comparator needs no exception.

### 14. MINOR: the `other:missions` narrowing is cosmetic, and the gate wiring is incomplete
- **Evidence:** the census maps excluded files to `other:<segment>` (`T/WholePackCensus.java:114`). `missions/**/README.md`
  (now excluded) and `missions/built_in_step_contracts/__init__.py` therefore stay `other:missions` under an entry whose reason
  will say "mission templates". IC-06 does not add `mission-type` to `REQUIRED_KINDS`
  (`T/WholePackRoundTripTest.java:62`), which F6 of the research lists. `*/missions/**/README.md` also re-buckets
  `missions/<t>/templates/**/README.md` (five files), which `missionTemplatesAreNotTemplates` (`:156-176`) samples.
- **Fix:** say in the reason exactly what remains (templates, READMEs, `__init__.py`), add `mission-type` to `REQUIRED_KINDS`, and
  re-check the template census test.

### 15. MINOR: the ArchUnit wiring and document hygiene are incomplete
- **Evidence and fixes:**
  - **ArchUnit.** IC-01 adds `SLICES` and a purity rule. `DOMAIN_RINGS` (`HexagonalArchitectureTest:61`) also needs
    `MISSIONS_DOMAIN`, otherwise ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD and PORT_INTERFACES_RESIDE_IN_DOMAIN skip the new
    ring.
  - **Stale research.** `M/research.md:51-57` (F3) still says `mission_step` becomes an artifact kind and `NodeKind` gains
    `mission_step`, which contradicts D1 and ADR §2. Annotate it as superseded.
  - **"Two kinds".** "Two kinds in the model" (`M/spec.md:22`) uses *kind* for a type that is neither an artifact kind nor a node
    kind. Say "two domain types, one artifact kind" to keep R14 and the terminology rule clean.
  - **Doubled frontmatter rule.** IC-03 forbids `frontmatter` in the structure schema and IC-04 refuses it again in
    `checkCompanion`. Keep the schema rule only, so there is one rule.
  - **Frontmatter `description`.** The harness drops a frontmatter `description` "else dropped" even when it differs from the
    runtime description. That is not a repeat (S5), so drop it only if equal; otherwise open an open-packs issue.
  - **#260.** Both PRs edit `PackFileSet.loaded`/`markdownHolder` (#260's kind-routed Markdown path), not only the YAML ledgers.
    Add that to NFR-004's shared-file list, and keep the `isCompanionStructure` check first in `markdownHolder` so mission
    companions never reach #260's route.

## What holds up
- Several companions per artifact already work: `withCompanions` and `planCompanions` loop and claim per path
  (`S/FilesystemPackStore.java:539-570,656-720`). The ownership rule admits the two new companion rules, provided each
  companion's `structureSchema` equals its own rule's schema name (`Companions.claimedByRule:182-185`).
- Digest order: `MissionTypeBinding` comes after `ProcedureBinding` in `KindBindings`, so packs without missions keep their
  digest (`PackFileSet.digested:219-234`). Packs with missions change once, as ADR-0044 says.
- The `:missions` boundary is sound if the DSO keeps `MarkdownDocument` and only the binding maps it to `StepDocument`
  (`M/data-model.md` DSO section). The `:profiles` precedent (`packs/build.gradle.kts` `api(project(":profiles"))`) applies.
- `NodeKind.of(ArtifactKind)` (`valueOf(name)`) works for `MISSION_TYPE` without reordering. The `VocabularyEqualityTest`
  fragment-plural derivation is unchanged.
