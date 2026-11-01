---
work_package_id: WP03
title: Generic artifact reads (added beside the per-kind routes)
dependencies:
- WP02
requirement_refs:
- FR-003
- FR-004
- FR-007
- NFR-005
- C-005
- C-008
- C-009
planning_base_branch: issue-126-offering-reads
merge_target_branch: issue-126-offering-reads
branch_strategy: Planning artifacts for this mission were generated on issue-126-offering-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-126-offering-reads unless the human explicitly redirects the landing branch.
subtasks:
- T011
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - The resource tree
history:
- at: '2026-10-31T14:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent: claude
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/paths/packs_packId_artifacts.yaml
- contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/ArtifactSummary.yaml
- contracts/charter/schemas/ArtifactPage.yaml
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/parameters/ArtifactKind.yaml
- contracts/charter/parameters/ArtifactId.yaml
- contracts/charter/parameters/ArtifactKindFilter.yaml
- contracts/charter/responses/ArtifactNotFound.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactPosition.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactCursor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactKindTokenConverter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactCursorTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/openapi.yaml
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/README.md
- contracts/charter/paths/packs_packId_artifacts*.yaml
- contracts/charter/schemas/Artifact*.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/parameters/**
- contracts/charter/responses/ArtifactNotFound.yaml
- contracts/charter/responses/_index.yaml
- contracts/charter/examples/**
- contracts/_shared/schemas/PageInfo.yaml
- contracts/_shared/VENDORED.md
- contracts/charter/CHANGELOG.md
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactPosition.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactCursor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactKindTokenConverter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RefusalHandler.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/Hypermedia*.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/*Controller.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReadConsistencySteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ScenarioPacks.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – Generic artifact reads (added beside the per-kind routes)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and follow
its guidance before you read the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Add these two operations:

- `GET /packs/{packId}/artifacts` (`listPackArtifacts`: `?kind=`, `pageSize`, `pageCursor`), answering
  `ArtifactPage`;
- `GET /packs/{packId}/artifacts/{kind}/{artifactId}` (`getPackArtifact`), answering the closed `Artifact`
  envelope.

Also add the new refusal `kind_not_found`. **Leave the per-kind routes in place.** WP04 removes them, so
every intermediate state keeps working and every `Location` still resolves.

## Context

- Spec US2, FR-003, FR-004, FR-007; data-model.md; research R-04, R-05, R-06, R-16, R-17, R-19 and R-21.
- Squad findings 5, 6, 9, 15 and 21.
- **Served kinds**: `tactic`, `directive`, `styleguide`, `paradigm`, `toolguide`, in this order. Their
  existing schemas are embedded unchanged: `Tactic.yaml`, `Directive.yaml`, `Styleguide.yaml`,
  `Paradigm.yaml` and `Toolguide.yaml`.
- **Summary `title`**:
  - `name` for a tactic or a paradigm;
  - `title` for a directive, a styleguide or a toolguide.
- **Existing read services**: `TacticReadService` and its siblings in `packs.application`. Each returns
  `ConsistentRead.Verified<T>`, and the writable kinds also offer `*WithRevision` → `Revised<T>` (ETag).
  - Each service rewrites `PACK_UNREADABLE` from `resolve` into `PACK_NOT_FOUND` for an unconfigured pack.
    See `TacticReadService.packRefusal` (:103-108).
  - Your list path must do the same (R-19, squad finding 9).
- **`Pack`** (`packs.domain.Pack`): `artifacts(ArtifactKind)` and the typed views. `ArtifactKind` (kernel)
  has `token()`, `plural()`, `fromToken`, `fromPlural`.
- **Controllers** keep their private `toDto` mappers. Move the five domain → DTO mappers into one
  package-private `ArtifactDtos`, and have the per-kind controllers call it. That is a pure move; behaviour
  is unchanged.
- **ArchUnit**:
  - `infra.api` may not name `profiles.domain` or `missions.domain` types, nor reach storage;
  - `application` may not name Spring web types;
  - port signatures name only domain types.
- **Scenarios** address operations by path below `/api/v1` (`contracts/charter/scenarios/README.md`).
  - Exact-body steps exist. Keep `_links` inside the exact bodies; never add an "apart from `_links`"
    escape.
  - Today the `field` step reads only a top-level key (`CharterSteps.java:167-170`), and the list step
    expects a JSON array (`ReadConsistencySteps:101`).

## Subtasks

### T011 — Red scenarios and new steps

1. New steps (in `CharterSteps`, plus a README row each):
   - `the field at "<json-pointer>" is "<value>"`, and `… is <number>` / `… is true|false`;
   - `following "next" links from the answer, the "items" ids are:` followed by a table of
     `kind | id`. It follows `_links.next.href`, stripping the base path, until absent.
   - `the answer has no link "<rel>"`;
   - `the problem has no code`. Assert that the `code` member is absent; do not compare it to `""`.
2. `contracts/charter/scenarios/artifacts.feature` (`@contract`). Use the `Given the org pack … with the
   file …` steps the kind features use. Cover:
   - **Item per kind.** For each of the five kinds: the 200 exact body with `kind`, `id`, `packId`, the
     kind member and `_links` (`self`, `collection`). It carries the fingerprint. The four writable kinds
     carry an `ETag`, and a tactic carries none.
   - **List with a filter.** The `items` are summaries with `title` and `self`. The answer carries `page`,
     `_links.self` (with the query) and `_links.first`.
   - **List without a filter** (R-06): the served-kind order, then the id in code-point order. Include ids
     `Zeta` and `alpha` to pin code-point order.
   - **Paging** (R-21):
     - exactly `pageSize` items: no `next`, and `hasNextPage` false;
     - `pageSize=1` across the tactic→directive boundary;
     - `next` keeps `kind` and `pageSize`;
     - a deleted anchor between pages: delete the anchor's file, then follow `next`;
     - bad cursors: one for another kind, one that is not base64, base64 of `procedure/x`, one with no
       slash — each a 400 problem with no code;
     - `pageSize` of 0, 201 and `abc` — each a 400;
     - an empty pack, and an empty kind.
   - **Refusals:**
     - an unknown pack on the list and on the item: 404 `pack_not_found`;
     - an unknown path kind (`procedure`, `bogus`): 404 `kind_not_found`;
     - an unknown `?kind=bogus`: a 400 problem with no code;
     - an unknown id: 404 with that kind's code;
     - **positive control for R-17**: `?kind=tactic` answers 200, written **before** the 400 case.
   - **Read consistency:** `stale_input` on the list and the item, reusing the "changes while the next
     read is answered" steps.
3. Run `./gradlew :deployable:test --tests '*CharterContractSuite*'` and see the new scenarios fail with a
   404 (no operation). Record this in the activity log.

### T012 — Contract for the generic reads

1. **Schemas.**
   - `ArtifactKindToken`: an enum of the five tokens, lower case, which `enum-snake-case` accepts.
   - `ArtifactSummary`: `kind`, `id`, `title`, `_links`.
   - `ArtifactPage`: `items`, `page` (`$ref ../../_shared/schemas/PageInfo.yaml`), `_links`.
   - `Artifact`: closed, `required [kind, id, packId, _links]`, five optional members, plus
     `oneOf: [{required: [tactic]}, …]`. Copy the `ToolguideRegistration` precedent.
   - Every property is described and cited (`x-source` pointing at the adapter or service symbol that
     fills it, or `x-derived`).
2. **Parameters.**
   - `ArtifactKind`: path, a string with a slug `pattern` and no enum, so the service answers
     `kind_not_found` (R-05).
   - `ArtifactId`: path.
   - `ArtifactKindFilter`: query `kind`, `$ref ArtifactKindToken`.
   - Plus the shared `PageSize` and `PageCursor` (`_shared/parameters/`).
3. **Paths.** `paths/packs_packId_artifacts.yaml` and `paths/packs_packId_artifacts_kind_artifactId.yaml`,
   with the tag `Artifacts` (add it to `openapi.yaml` tags) and full descriptions.
   - The fingerprint is required on each 200 (WP01's rule enforces it).
   - The `ETag` header on the item 200 is `required: false`, and the description says writable kinds only.
   - 400 → the shared Problem; 401/403 as everywhere; 404 → the new `responses/ArtifactNotFound.yaml`
     (codes `pack_not_found`, `kind_not_found` and the five `*_not_found`, with examples that WP01's
     raised-code check accepts); 500 → `OfferingUnreadable`.
4. **`_links` examples.** Give each 200 an example whose `_links` uses only `self`, `collection`, `first`
   and `next` (WP06 lints the keys).
5. **`PageInfo`** (R-16).
   - Add an `x-derived` citation to each property, and record the change in `_shared/VENDORED.md`.
   - **Spike**: build, then inspect the generated `PageInfo.nextPageCursor`. If it is a `oneOf` wrapper type
     instead of a nullable `String`, narrow the vendored schema to `type: [string, "null"]` with the
     `PageCursor` pattern, and record it in `VENDORED.md` (C-009).
6. `./gradlew :contracts:check` is green. The `Links` map in `_links` is now in the bundle, so WP02's
   exemption is exercised for real.

### T013 — `kind_not_found`

1. **Red first.** `WireTokenParityTest` and `VocabularyEqualityTest` (or the equivalent parity test) fail
   once the contract enum gains `kind_not_found` and the kernel does not.
2. **`CharterRefusalCode.yaml`.**
   - Add the enum value `kind_not_found`.
   - Add an `x-refusal` entry with `status: 404`, `raised: true` and a description: "the path names an
     artifact kind this service does not serve".
3. **`RefusalCode.KIND_NOT_FOUND`.** Add it to `notFound()` and to the `RefusalHandler` status and title
   ("Kind not found").
4. `ReservedRefusalCodesTest` stays as it is: it pins only the unraised codes.

### T014 — `ArtifactReadService` (application)

1. **`ArtifactPosition`**: a record `(ArtifactKind kind, String id)` with a comparator in served-kind
   order, then by id in code-point order.
2. **`ArtifactReadService`**, constructor-injected with `PackRepository`, `ConsistentRead` and the five
   per-kind read services. Methods:
   - `Verified<Page> list(PackIdentifier, Optional<ArtifactKind> filter, Optional<ArtifactPosition> after, int size)`
     - **one** verified read;
     - `PackRepository.load` inside `consistentRead.read`;
     - the pack refusal is rewritten as in `TacticReadService`;
     - the result is a list of `(kind, id, title)` entries strictly after `after`, at most `size` of them,
       plus whether more remain.
   - `Revised/Verified<…> item(PackIdentifier, String kindToken, String id)`
     - checks the pack first (`pack_not_found`), then resolves the token to a served kind (else
       `KIND_NOT_FOUND`);
     - delegates to the per-kind read service (its `*WithRevision` for the writable kinds);
     - returns a small sealed result the adapter maps by kind.
     - The pack check must not take a second, unverified read. The pack is known through the per-kind
       service's own load, which already rewrites to `pack_not_found`. Order the checks so that an unknown
       pack with an unknown kind answers `pack_not_found`, for example by loading the pack via
       `PackRepository.load` inside the same `consistentRead.read` before checking the token.
   - The served-kind set is a constant: the five `ArtifactKind`s.
3. **Unit tests** (`ArtifactReadServiceTest`, with the in-memory fakes the other `*ReadServiceTest`s use):
   - order;
   - `after` exclusivity;
   - a deleted anchor;
   - the `size` boundary and `hasMore`;
   - the filter;
   - the pack rewrite;
   - an unknown kind;
   - pack before kind.

### T015 — `ArtifactController` (adapter)

1. `ArtifactController implements ArtifactsApi` (generated, tag `Artifacts`), under
   `@RequestMapping("${charter.api.base-path:/api/v1}")`.
2. **`ArtifactKindTokenConverter`**: a Spring `Converter<String, ArtifactKindToken>` using the generated
   `fromValue`, registered through a `WebMvcConfigurer`. Put it in `infra.api.config` or the deployable
   wiring, whichever the existing config pattern uses. An unknown value throws `IllegalArgumentException`,
   which becomes a `MethodArgumentTypeMismatchException` and therefore a 400 Problem through `ProblemHandler`.
3. **`ArtifactCursor`**: encodes and decodes the opaque base64url of `<kind>/<id>`. Any decode failure, a
   non-served kind, or a kind other than the filter's throws an adapter exception that `ProblemHandler`
   maps to a 400 plain Problem (`Problems.of(BAD_REQUEST)`). No refusal code. Unit tests in
   `ArtifactCursorTest`.
4. **Envelope mapping** in `ArtifactDtos`: kind → member. Build links with `Hypermedia` and `Relation`:
   - an item's `self` is `…/packs/{p}/artifacts/{kind}/{id}`, and `collection` is
     `…/packs/{p}/artifacts?kind={kind}`;
   - a page's `self` is the request path with its query (`kind`, `pageSize`, `pageCursor` when given),
     `first` drops `pageCursor`, and `next` is present only when there are more items.
   - No `edit` link (R-24).
5. **Headers.**
   - The fingerprint goes on every 200, through `CharterHeaders.fingerprint`.
   - `ETag` comes from `Revised` for the writable kinds; a tactic has none.
6. `pageSize` bounds come from the generated bean-validation annotations. Confirm that a 0 or a 201 answers
   400 through method validation. If Spring does not validate the interface parameters, add `@Validated`
   the way the existing controllers do, or check the bound in the adapter.
7. **`ArtifactControllerTest`**: the mapping for each kind, the links, the cursor round-trip, and the 400s.

### T016 — Verify and CHANGELOG

1. Run `./gradlew verifyAll`. Everything must be green, and the T011 scenarios must now pass.
2. Append to the mission block in `CHANGELOG.md`:
   - the added operations and schemas;
   - `kind_not_found`;
   - the `PageInfo` citation (and narrowing, if the spike required it);
   - each scenario added.

## Definition of Done

- [ ] All `artifacts.feature` scenarios pass, and they failed first (logged).
- [ ] The per-kind routes and their scenarios are untouched and green.
- [ ] `verifyAll` is green. No new suppressions. Complexity ≤ 15. ArchUnit is unchanged and green.
- [ ] A `?kind=tactic` positive scenario passes, and `?kind=bogus` is a 400.

## Risks and notes

- The generator and `Artifact`'s `oneOf`: if the generator emits a broken composed model, follow exactly
  what it does for `ToolguideRegistration` (the same keyword placement). C-009: the service fills exactly
  one member itself.
- Do not fold the per-kind read services into one (#169; C-005).

## Reviewer guidance

- Run the paging scenarios and read their step code. "Every artifact exactly once" must collect ids across
  pages and compare them to the full table.
- Check that `pack_not_found` comes before `kind_not_found` for `GET /packs/nope/artifacts/bogus/x`.

## Branch strategy

The planning base and the merge target are both `issue-126-offering-reads`. The lane comes from
`lanes.json`. Implement with:
`spec-kitty agent action implement WP03 --agent claude --mission gate-a-read-surface-01M691ND`.
