# #583 census: PackIdentifier uses (read-only research)

Paths are relative to `service/`. `pkg` = `packs/src/main/java/ai/speckitty/charter/packs`.

Facts that frame the split:
- `kernel/.../PackIdentifier.java:41-43`: the constructor only checks not-blank. `isPackId` (slug(63)) exists, and the javadoc says the
  constructor does not enforce it "because the descriptor's pack_id also travels as a pack identifier (#552 Q3)".
- `contracts/packs/schemas/pack-descriptor.schema.json`: `pack_id` is a `$ref #/$defs/Ulid`. `name` is `^[a-z0-9][a-z0-9_-]*$`,
  max 64. **Underscores are allowed in the name, so a name is not a slug.**
- `PackDescriptor.packId` is a `String` (`kernel/.../PackDescriptor.java:28`). `RefusalPack(descriptorPackId, packId, name)`
  (`kernel/.../RefusalPack.java:14`) already separates the ULID from the configured id.
- `MergedClosure.Winner(urn, PackIdentifier packId /*configured*/, String descriptorPackId /*ULID*/, ...)`
  (`pkg/domain/MergedClosure.java:75`), built in `LayerMerge.java:272-278` from `pack.configured().id()` and
  `pack.descriptor().packId()`.

## 1. Every `new PackIdentifier(` in src/main (67 sites; 30 are controllers)

Legend: REG = registered id, ULID = descriptor pack_id, NAME = descriptor name, STAGE = staging or temp directory name,
DIR = name of a live directory, which can also be an aside copy (`<id>.pack-old-<n>`) or a link's real target, OTHER.

### Controllers: REG (URL path param `packId`), 30 sites
profiles-edge AgentProfilesController:52; artifacts-edge SkillsController:46,55; RoutingCatalogsController:43,52;
GlossariesController:48,57,66; AssetsController:50,59,69; packs infra/api StyleguideController:67,94;
PresetsController:45,54; ArtifactController:76,101,111; ToolguideController:104,116; DirectiveController:53,77;
ParadigmController:52,66; PackRemovalController:47,54; RelationshipController:54; missions-edge
MissionTypeController:47,56,63. Each is `new PackIdentifier(packId)` on the generated API's path param.

### Activation adapters: REG (configured id carried as a String), 6 sites
- profiles-edge ActivationsEffectiveProfiles:131 `artifact.packId()`. `EffectiveSet.EffectiveArtifact.packId` comes from
  `MergedSnapshot.Winner.packId`, which `ClosureViewSession.java:524-526` fills with `winner.packId().value()` (configured).
- artifacts-edge ActivationsArtifacts:162 `winner.packId()`. `EffectiveHeldWinner` is built from `winner.packId().value()` at
  ClosureViewSession:245-248.
- ActivationsArtifacts:171 `artifact.packId()`: same source as ActivationsEffectiveProfiles:131.
- missions-edge ActivationsMissionTypes:42 `winner.packId()`. `EffectiveArtifactRead.packId` is `winner.holder().packId().value()`
  (ClosureViewSession:146-148).
- ActivationsMissionTypes:56 `place.packId()`. `ClosureViewSession:309` maps `ClosureView.stepPlace`, which is
  `StepHolding.placeOf` returning `MergedClosure.Winner::packId` (configured).

### Merge, edge and composition sites in packs/activations
- ULID: EdgeIndex:218 `winner.descriptorPackId()`, which builds a `Place`.
- ULID: EdgeIndex:649 `idOf` = `pack.descriptor().packId()`.
- ULID: ClosureView:365 `edges.inPack(new PackIdentifier(winner.descriptorPackId()))`.
- ULID: activations ClosureViewSession:540, the same `inPack(descriptorPackId)`.
- REG: ClosureView:886 `dropped.pack()`. `Enhancing.pack` = `enhancer.packId().value()` (ClosureView:875), and
  `AgentProfileEnhancements:33,46` passes it back through `refused.pack()`.
- REG: ProfileEnhancementRule:95 `dropped.get().pack()`, same chain (Enhancing built at :88 from `winner.packId().value()`).
- NAME: InboundReferences:214 `staged.descriptor().name()`, the stand-in id of a new staged pack (`replacing` is empty). A name
  can hold `_`.
- REG: RegistrationJobs:719 `storedRevision(packId)`, the registration's target id (admitted by
  `RegistrationAdmission:207 isPackId`).
- REG: PackRegistrationService:679 and :705 `upload.packId()`, the target id (`requireValidPackId` at :354-361).

### Storage sites in `pkg/infra/storage`
- STAGE: PackCommitStore:303 `staging.getFileName()`, which is `.pack-commit-*` (:398). It is the key passed to
  `FilesystemPackStore` to write the artifact into the staging dir.
- STAGE: PackCommitStore:494 `staged.directory().getFileName()`. This is a temp root or the archive's top-level folder
  (`PackTree.java:152`, user-controlled). It is the `revisionOf` name.
- REG: PackCommitStore:622 `storedPair(live, packId)`, the history's pack id.
- REG: PackCommitStore:711 `create(packId, ...)`: the revision of `.pack-write-*` staging, named after the target id.
- DIR: PackCommitStore:938 `over()`: `directoryOf(location)`, the configured entry, so in practice REG.
- DIR: ArtifactFiles:40, the stored pack's directory name, used as the reader key.
- ULID: ArtifactFiles:42 `read.descriptor().packId()`, passed to `PackAssembly.assembly`.
- STAGE: WholePackStore:295 `read.contents().root().name()`, the staged tree root name. Only refusal text and facts use it.
- DIR: WholePackStore:385 `loadStored(directory)`, the directory name, or "pack".
- DIR: WholePackStore:416 `salvageStored`, the directory name, as the reader key.
- REG: DomainRepositoryService:348 `location.name()` (`configured()`), the entry name under the domain.
- DIR: DomainRepositoryService:650 `writable()`: the **real path** name, which can be a link target with any name.
- DIR/aside: DomainRepositoryService:677 `over(directory)` from `PackLocator.read`, so it can be the aside copy
  `<id>.pack-old-*`.
- REG: CommitStorePackWriteStore:339 `configuredName`, which is `location(pack).name()`.
- DIR/aside: CommitStorePackWriteStore:359 and :375, inside `reading()` (PackLocator.read).
- DIR/STAGE: CommitStorePackWriteStore:443 `withoutLinks(directory)`.
- ULID: StoreRead:113 `read.descriptor().packId()`, which becomes `Pack.id` and every artifact's `packId`.
- DIR/STAGE: StoreRead:199 `packDir.getFileName()`, the reader key.
- OTHER: StoreRead:260 `name`, the caller's `name` argument: REG from DomainRepositoryService:489 and
  CompositionFactsBuilder:73, the directory name from WholePackStore, PackManifestDerivation:90 and FileSystemPackInspector.
  It is used for facts and messages.
- DIR: PackLocator:116 `name.toString()` of the location directory or aside copy, as the reader key for `readDescriptor`.
- OTHER: DomainRoundTrip:186, the name of the round-trip source directory (pack-validation tool).

Tally: REG 43 (30 controllers, 6 activation adapters, ClosureView:886, ProfileEnhancementRule:95, RegistrationJobs:719,
PackRegistrationService:679,705, PackCommitStore:622,711, DomainRepositoryService:348, CommitStorePackWriteStore:339 = 45;
PackCommitStore:938 is a REG-valued DIR), ULID 6, NAME 1, STAGE 3, DIR/aside/real about 10, OTHER 2. The per-site rows above
are authoritative over these counts.

Method-reference constructions outside the 67:
- `PacksAvailability.java:104` `PackIdentifier::new` over the activation's root ids.
- `ClosureRoots.java:157`, same.
- Activation `PackRef` (`activations/domain/PackRef.java:17`) uses its own grammar, `[a-z0-9][a-z0-9_-]{0,63}`, which
  allows `_` and 64 characters, so it diverges from slug(63).

## 2. What `CharterArtifact.packId()` carries

| Path | Value |
|---|---|
| `DomainRepositoryService.load(pack)`, through `assemble(pack, ...)` (:536, 580-588) and `StoreRead.assembly(pack, ...)` | **whatever id the caller passed**. This is REG from HTTP. PackLocator also accepts a ULID, and then the artifact carries the ULID. |
| `StoreRead.of` (:113, 119, 135): `held.id()` and every artifact | **ULID**. Used by whole-pack registration (WholePackStore.loadStaged and loadStored, `PackAssembly.build(held)`), FileSystemPackInspector (pack-check), PackManifestDerivation, CompositionFactsBuilder.underResolution, and DomainRepositoryService.compositionFacts. |
| ArtifactFiles:42 | **ULID** |
| EdgeIndex `EdgeNode(packId=place.pack)` (:79, :358) | **ULID** |
| Typed registrations (Tactic and similar built in controllers or services from the path id) | **REG** |

**Not consistent.** `Pack` guards `artifact.packId().equals(id)` (`pkg/domain/Pack.java:149`), so each aggregate is
internally consistent. Across paths the same artifact carries REG or ULID.

Readers of `artifact.packId()` and what they compare it to:
- Wire values:
  - `ArtifactDtos.java:58`: the `pack` field of the Artifact envelope (REG on HTTP paths).
  - `ProfileDtos.java:84`: the pack field and the links.
  - artifacts-edge `AssetBodies:65,78`, `SkillBodies:68,81`, `GlossaryBodies:69,103,140`, `RoutingCatalogBodies:69,81`,
    `VocabularyBodies:96`: links `packs/{id}/...`.
  - `PacksAssets:103`, `PacksSkills:90`, `PacksGlossaries:96,120`, `PacksRoutingCatalogs:84`.
- `DomainRepositoryService:192 saveArtifact(artifact.packId(), artifact)` sends it to `PackLocator.locate`, which accepts
  either identity.
- `GraphWalk:84,129-130` uses `component.packId()` as the `Held` key and as `holdingPack`'s `referringPack`. These artifacts
  are EdgeNodes, so the value is a ULID.

## 3. Maps and keys

- **EdgeIndex** (`pkg/application/EdgeIndex.java`):
  - `byPack` (:130, filled at :152 via `idOf`), `Place.pack` (:59), `outgoing` and `winners` (:218) are all keyed by **ULID**.
  - `holdingPack(ref, referringPack)` (:349-354) looks up `byPack` by ULID, maps to `configured().id()`, and
    `.orElse(referringPack)`. This **silently accepts a REG** argument. It then calls
    `PackCompositionResolver.holderOf(closure, referring /*REG*/, ref)` (domain `PackCompositionResolver.java:494-515`,
    which compares `configured().id()`) and maps the result back to ULID through `idOf`.
  - `inPack(pack)` (:357) expects a ULID.
- **ClosureView**:
  - Uses ULID only for `edges.inPack` (:365).
  - Closure order is `closurePackIds.indexOf(held.descriptorPackId())` (:894; ProfileEnhancers:50, InForceProfiles:132).
  - Everything read or served uses `winner.packId()` (REG): `read(won.packId(), ...)` :819, `Holder(won.packId())` :826,
    `stepPlace`.
  - `refusalPack(PackIdentifier)` (:919-927) matches `configured().id()` (REG). Its fallback is
    `new RefusalPack(pack.value(), pack.value(), pack.value())`, which puts a REG value into `descriptorPackId` and `name`.
- **ClosureViewSession** (activations):
  - `MergedSnapshot.Winner.packId` is REG (:524).
  - `edgesOf` uses ULID for `inPack` (:540).
  - `EffectiveHeldWinner` and `EffectiveArtifactRead` are REG.
  - `PacksAvailability:208-221` keeps a `descriptorPackId -> configured` map.
- **LayerMerge**:
  - `byDescriptorPackId` (:137-142) is keyed by ULID.
  - The winner holds both identities (:272).
  - The refusal is `RefusalPack(descriptor.packId(), configured.id(), name)` (:122-127).
- **InboundReferences**:
  - `Member.id` is REG for stored packs (:220) and NAME for a new staged pack (:214).
  - It goes to the wire as `RefusalViolation` pointer `referrer.pack().value() + "/" + ...` (:102) and as
    `RefusalPack(referrer.descriptorPackId(), referrer.pack().value(), name)` (:108-111).
- **ProfileEnhancementRule and the Gateway**:
  - `Enhancing.pack` and `Dropped.pack` are REG strings (ProfileEnhancementGateway.java:23,44).
  - `Breach.packs` is a `List<PackIdentifier>` of REG values.
  - `RegistrationImpact.involved` (:493-501) maps it to `RefusalPack(descriptor.packId(), pack.value(), name)`.
- **PackMutabilityPolicy**: `Member(PackIdentifier configured, String descriptorPackId, ...)` (:77). `rootOf` (:410-423)
  accepts `Pack.id()` as either REG or ULID.
- **Wire**:
  - `RefusalHandler:256` serializes `RefusalPack` as is.
  - `EffectiveBodies:239` `HoldingPack(holder.packId().value() /*REG*/, holder.descriptorPackId(), name)`.
  - `ActivationMapper:343-351` sends `entry.packId()` (REG) as the link and `descriptorPackId` separately.

## 4. Split points: signatures that receive both identities

- `ComposeResolver.holdingPack(ref, PackIdentifier referringPack)` (`pkg/domain/ComposeResolver.java:27`) and its EdgeIndex
  implementation. It is called with ULID (GraphWalk:129, EdgeIndex:598,625) and tolerates REG (:350-352). The return is
  ULID. Its parameter should become `PackUlid`.
- `ComposeResolver.inPack(PackIdentifier)` (:38). It is only ever called with ULID: ClosureView:365, ClosureViewSession:540,
  GraphWalk:105, EdgeIndex:581.
- `EdgeIndex.Place(PackIdentifier pack, Urn)` and `EdgeNode.packId` should become `PackUlid`.
- `PackLocator.locate(..., PackIdentifier pack)` (`PackLocator.java:44-57`). It matches `location.name()` and then
  "declares" the pack_id (:91-121). Every `PackDescriptorRepository.find`, `PackRepository.load`, `compositionFacts`,
  `currentRevision`, `configuredName` and so on inherits this. The javadoc says so explicitly:
  - `PackWriteStore.java:126`
  - `PackSummaryQuery:91,126,140,175`
  - `PackDocumentQuery:16,77`
  - `RegistrationImpact:231,288`
  - `ForeignArtifactRead:139,290-306` (`configuredId` translates ULID to REG)
  - `RoutingCatalogReads:18,27`
  - `PackMutabilityPolicy.rootOf:410`
- `PackAssembly.assembly(PackIdentifier pack, ...)` (`PackAssembly.java:61-253`) and `Pack.Builder.id`. These receive REG
  from DomainRepositoryService and ULID from StoreRead and ArtifactFiles.
- `CharterArtifact.packId()` (kernel), as described in section 2.
- `FilesystemPackStore` (`:350 root.resolve(id.value())`). It uses PackIdentifier purely as a **directory-name key**. It is
  the source of every STAGE and DIR site and of the PackLocator:116 site.

## 5. Staging sites

- PackCommitStore:303: `stagingKey` is the **reader and writer argument** to `FilesystemPackStore` for
  `DomainRepositoryService.writeArtifact(store, key, artifact)` into `.pack-commit-*`. It is not a lock key and not a
  revision lookup.
- PackCommitStore:494: `revisionOf(staged.directory(), id)`. The id is only the `packName` for
  `PackFileSet.digested(packDir, name)` (:909-915), where it appears in messages. The comment at :911 says it is "named after
  the pack, not the directory". The STAGE name here is a temp or archive name. It is a **revision computation label**.
- PackCommitStore:711 is REG (`create(packId)`). Aside copies `.pack-old-*` (:173, :872) reach DomainRepositoryService:677
  and CommitStorePackWriteStore:359,375 through `PackLocator.read`. They are reader keys too.
- WholePackStore:295 (staged root name) is used only for `CompositionFactsBuilder.heldFacts` refusal text (:121-136).
- **Fix shape:** make `FilesystemPackStore` and `PackFileSet` take a `Path` or a plain directory-name String, not
  `PackIdentifier`. That removes every STAGE and DIR wrap.

## 6. Read paths that accept a stored directory whose name is not a slug

- `StorageRootLocations.isPack` (:464-467) only excludes names that start with `.` or contain `.pack-old-`. `location()`
  (:470-473) makes `PackLocation(name, ...)` for any other name, for example `My_Pack`.
- `directoryOf` (:402-426) already has a refusal hook: `refuse(entry, reason)` (:452-458) logs a WARN once and does not serve
  the entry (it uses `whyNotAPack`).
- **Natural place for refuse-on-read:** in `all()` before `location(candidate.entry())` (:157), or in `location()`. Add
  `if (!PackIdentifier.isPackId(name)) refuse(entry, "is not a pack id: " + GRAMMAR_DETAIL)`. Then
  DomainRepositoryService:348 (`configured()`), PackLocator, CommitStorePackWriteStore and PackCommitStore never see a
  non-slug REG. The DIR sites (650 real target, 677 aside, PackLocator:116) are not REG and should stop using PackIdentifier.
- Code family:
  - Today this path only logs. It is not a refusal on the wire.
  - If a named code must be surfaced (pack listing or diagnostics), `StorageCodes` (`pkg/infra/storage/rules/StorageCodes.java`)
    is the storage-edge family of `ReasonCode` violations (PACK_UNREADABLE, UNREADABLE, RETIRED and others). It fits a
    per-pack read fault, for example a new `violation("pack_id_invalid")`, or reuse PACK_UNREADABLE.
  - The kernel `RefusalCode` is the wire enum (needs a contract change). Its closest existing codes are `PACK_UNREADABLE`
    (409/422 style) and `PACK_NOT_FOUND` (a 404, `notFound()`).
  - The upload path already uses `VALIDATION_FAILED` + violation `invalid_pack_id` (PackRegistrationService:354-361).

## 7. HTTP mapping

- There is no `@ExceptionHandler(ConstraintBreachException)` in web-transport. `ConstraintBreachException extends
  RuntimeException` (`kernel/validation/ConstraintBreachException.java:6`), so a breach thrown from a controller falls to
  `ProblemHandler.unexpected` (`web-transport/.../ProblemHandler.java:101-105`), which answers **500 internal_error** and logs
  it.
- The Styleguide, Toolguide, Directive and Paradigm controllers catch `ConstraintBreachException` only around the body
  assembly (for example StyleguideController:175), not around `new PackIdentifier(packId)` (:67).
- In practice that code is not reached. The contract declares the pattern:
  - `contracts/charter/parameters/PackId.yaml` has `minLength 1, maxLength 63, pattern '^[a-z0-9]+(-[a-z0-9]+)*$'`. 28 path
    files use it through `$ref`.
  - `paths/packs_packId.yaml:2-11` and `packs_packId_doc.yaml:2-10` declare the pattern inline and say "A value outside the
    grammar breaks the parameter's schema and is a 400 Problem without a code".
  - The generated interfaces use `useBeanValidation=true` (`build-logic/.../charter.contract-api.gradle.kts:50`) and are
    `@Validated`. A non-slug path id therefore becomes a `ConstraintViolationException`, which
    `ProblemHandler.invalidParameter` (:130-139) and `InvalidParameter:272` (`Pattern`) turn into **400 without a code** before
    the controller runs.
  - So a ULID can never be addressed over HTTP, even though `PackLocator` would resolve one.
  - `PackId.yaml:4` still says "as the pack declares it". That is stale wording: it should say the configured id.

## 8. Test literals `new PackIdentifier("...")` (non-main sources)

| module | total | ULID (26 chars) | slug | other |
|---|---|---|---|---|
| packs | 324 | 14 | 284 | 26 |
| artifacts-edge | 37 | 0 | 35 | 2 (`q/a`) |
| activations | 18 | 0 | 18 | 0 |
| profiles-edge | 15 | 0 | 15 | 0 |
| missions-edge | 10 | 0 | 10 | 0 |
| deployable | 5 | 2 | 3 | 0 |
| profiles 4, missions 2, routing 2, routing-edge 1 | all slug | | | |
| kernel | 1 | 0 | 0 | 1 (`" "`, a blank-error test) |

- The packs "other" literals are mostly fake short ULIDs: `01PACK` x14, `02OTHER` x4, and `01TOOLPACK`, `01TEMPLATEPACK`,
  `01LEFT`, `01FAMILYPACK`, `01ASSETPACK` x1 each. The rest are error-handling cases: `my pack`, `a/b`, `../elsewhere`.
- Files with the 16 real 26-char ULID literals:
  - deployable: DoctrineWiringRoundTripTest, WiringRoundTripTest.
  - packs: TacticReadWiredTest, DoctrineRepositoryContractTest, DoctrineValidationParityTest, DomainRepositoryServiceTest (x2),
    GlossaryYamlTest, GuardAndLifecycleYamlTest, KindBindingsTest, PlacedWriteTest (x2), ProcedureEdgesTest, ProcedureYamlTest,
    SkillYamlTest, StepEnhancementYamlTest.
- There are also 136 non-literal test constructions (constants and variables), which are not classified here.
