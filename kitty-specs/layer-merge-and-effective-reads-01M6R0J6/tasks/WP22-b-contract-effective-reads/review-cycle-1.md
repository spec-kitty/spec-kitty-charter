---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-08T17:05:07Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP22
---

# WP22 review, cycle 1: changes requested

Reviewer: claude:opus:reviewer-renata (profile reviewer-renata). Lane-t HEAD a282de30, base ffd1aa1e.
WP22 commits reviewed: 4e87c62e 80fe869e d3449d9c 7234c696 8543c8c2 eddd1136 085d7613 76f86878 90e88ba6 a282de30.

## Commands run

- `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :contracts:check`: BUILD SUCCESSFUL. 55 fragments well formed; lint, closure and refusal-example checks pass.
- The same flags with `:deployable:test`: 1132 tests, 1 failed, and the one failure is the accepted R-20 (`CharterContractSuite > Cucumber > executionError`: "No scenario exercises these success answers of the contract (R-20)": `GET /activations/{activationId}/artifacts 200`, `GET /activations/{activationId}/skipped 200`). Nothing else failed.

## What is good (no action needed)

- The contract matches plan IC-08, IC-17, IC-20 (read side), data-model EffectiveSkippedReport and contract-delta for the members, required-ness, enums and buckets. `skippedByScope` stays, always empty at read (DM-01M6RN5MTWC98PXEVJNFQSRRJX). Q3 is applied: `activationsAffected` is a sorted `[ActivationId]`. Q4 is applied: the required `warnings` is under Breaking. The read 404 is `UnknownActivation` (`activation_not_found`). The 400 cursor is bound to the tag. The 500 is `OfferingUnreadable`, with `pack_unreadable` for a refused override and for an override cycle. The keyset cursor is over (kind, id), and `summary` is identical on every page.
- The suggestion carrier (01M6Y0X5E9ANR25MC8RPFXYM72) is declared as an optional `EffectiveArtifact.suggestion {when, reason}`, and the notices still name activatable kinds only (#417).
- Red first holds. 4e87c62e moves `ReservedOperationCoverageTest` to 2 before the contract change. The NullOmissionTest reds that the new optional models caused are fixed by adding them to the mix-in and the native hints (76f86878, a282de30). No test was weakened, and no `@Disabled` or suppression was added. The OperationCoverageTest and LinkChecksTest fixtures are retargeted to the graph and context reads, which stay reserved. That is not a weakening: OperationCoverageTest now also asserts that the two built reads are declared for coverage.
- Both feature files are `@WIP` throughout (5 + 32 scenarios) and contain every scenario T113 lists, plus the B4 getActivation ETag scenario. Every request step is followed by a status assertion. The only exceptions are the archive PUTs, which use the existing `poll ... until it ends` / `/state is succeeded` idiom. Every absence check I sampled (US3 AS3, A8, B1, A10, B4 deny) has a positive control in the same answer. The A8 ring writes pack files directly (Q6). The A10 scenario in activation-ruling-changes.feature is kept in line (`/report/requiredNotHeld`, a bare preset item id, a positive control).
- `ActivationChangeMapper` passes `[]` for the new required members, and its Javadoc says so until WP25 serves them. That is documented, not a silent empty return.

## Blocking

### B1 (high): getActivation's new ETag breaks the GET-then-conditional-write flow, and the contract does not say what If-Match takes now

Decision 01M6Y11E55YP5TG1EJ1MDKCFRP makes getActivation's ETag cover the revision plus the closure's fingerprint. The contract applies that only to the GET. Every write still answers and compares the bare revision:

- `contracts/charter/paths/activations_activationId.yaml:27-28`: GET now uses `EffectiveETag`, while PUT/POST keep `ActivationETag` (`:96-97`, `:112`).
- `contracts/charter/paths/activations_activationId.yaml:54-55` (replaceActivation) still promises "a PUT of a fetched config answers 200 with the same ETag". That is false now: the fetched ETag is the closure tag, and the PUT answers the revision.
- `contracts/charter/parameters/IfMatch.yaml:5`: If-Match is "the quoted ETag of an Activation answer". After this change, a client that sends getActivation's ETag as If-Match, as RFC 9110 13.1.1 and ADR-0020 D6 intend, gets a 412 on every replace, delete and sub-resource write. The contract says nothing about which tag a write compares.
- `contracts/charter/schemas/Activation.yaml:3-6` says "The ETag covers the activation's revision and the governanceFingerprint…" for every Activation answer, including the POST and PUT answers, which declare `ActivationETag`. It also does not say whether a write's Activation answer carries `denyNotApplied`.
- Existing non-`@WIP` scenarios rely on GET's ETag being the revision and replay it as If-Match:
  - `activation-register.feature:67` (GET, then PUT with the remembered ETag, then the same ETag) and `:85` (the GET after a 412 equals the remembered PUT ETag);
  - `activation-ruling-changes.feature:159` (A4: GET then PUT with the remembered ETag, then the same ETag);
  - `activation-kind-model.feature:297-299` and similar (GET ETag "is the stored ETag", then a sub-resource write "with the stored ETag");
  - `activation-cascade-depth.feature:449-535`;
  - in all, 17 GET assertions across activation-*.feature.

  `research/closeout-notes-pr-b.md` hands WP25 only "the scenarios that assert the ETag equals the revision". It does not cover the If-Match replays, which are a contract question, not glue.

Fix:
1. Get a runner or operator ruling (a follow-up to 01M6Y11E55YP5TG1EJ1MDKCFRP) on what an activation write's If-Match matches. My recommendation: a write accepts either the quoted revision or the activation's current getActivation tag, so GET-then-PUT keeps working and A4's flow survives. The alternative is that writes answer and compare the closure tag too.
2. Then declare the ruling in `IfMatch.yaml`, in the replaceActivation, deleteActivation and sub-resource write descriptions (fix the "same ETag" sentence at `activations_activationId.yaml:55`), and in `Activation.yaml`'s description (which answers carry which tag, and whether write answers carry `denyNotApplied`).
3. Add the client-visible consequence to the Breaking entry, and add a `@WIP` scenario pinning GET → conditional PUT.
4. Extend the closeout-notes WP25 hand-off to list every non-`@WIP` scenario above by file:line.

### B2 (medium): optional members added to closed responses of built operations are under Added, not Breaking

`contracts/lint/README.md` ("Adding a response property is breaking", DM 01M5986E711JT296MA6NDN91MG) sets the rule, and the A0 fragment `2026-11-07-a0-ruling-breaking.md:12` follows it for `Activation.dropped`.

- `Activation.denyNotApplied` reaches the closed `Activation` answered by getActivation, createActivation and replaceActivation, all on `main`.
- `PackRegistrationNotices.activationsAffected` reaches the closed notices of the registerPack result, which is on `main` with PR A merged.

Both appear only in `changes/2026-11-07-b-effective-reads-added.md:27-30`.

Fix: add both to `changes/2026-11-07-b-effective-reads.md`, the single PR B Breaking block. For each, state that the object is closed and a strict client must regenerate. Fold `2026-11-07-b-activation-etag.md` into that same block, as T112 and contract-delta ("Each PR's fragment carries one `### Breaking` block listing all its breaks") ask. The Added entries may stay as the end-state description.

### B3 (medium): getActivation declares composition 500s it does not document

`contracts/charter/paths/activations_activationId.yaml:11-13` now says "A closure that cannot compose is 500 with its code (pack_unreadable among them)", but its `'500'` (`:46-47`) is still `_shared/responses/Problem.yaml`.

Fix: reference `../responses/OfferingUnreadable.yaml`, as the two effective reads do, so the codes and examples are declared.

## Non-blocking (fix in this cycle if convenient)

- N1: `contracts/charter/headers/EffectiveETag.yaml:2-4` still says "changes when the activation's revision or the offering's governanceFingerprint changes". The reads now say the tag follows the closure's packs (not the offering), and kind, state and cursor. getActivation also uses this header now, and it is not "an effective read". contract-delta says "wording unchanged", but the wording now contradicts US3 AS7 ("a change to a pack outside the closure keeps the tag"). Reword it, or give getActivation its own header.
- N2: `PackRegistration.notices.activationsAffected` is declared with no scenario at all (C-001). Add a `@WIP` scenario: a re-registration lists the affected activation, and an activation not rooted at the pack is not listed (the control). WP25 T129 binds it.
- N3: `paths/activations_activationId_skipped.yaml:50-51` adds a `400` that the description never explains (no cursor and no filter on this read). Say what it is for, presumably an activationId outside the pattern, as getActivation says.
- N4: some out-of-map lines are missing:
  - `examples/EffectiveArtifactList.first-page.yaml` (d3449d9c);
  - `changes/2026-11-07-b-effective-reads-added.md`, `-changed.md` and `2026-11-07-b-activation-etag.md` (owned_files names only `2026-11-07-b-effective-reads.md`).

  A one-line note in the next commit is enough.
- N5: `EffectiveArtifactSuggestion` has no required member, so `suggestion: {}` is valid. If a suggests edge always has a `reason`, make `reason` required. Otherwise say in the description that both may be absent, as it partly does.

## Anti-pattern checklist

1. Dead code: PASS (no new production types; the mapper changes are live).
2. Synthetic-fixture test: PASS (contract-structure tests only; the scenarios are `@WIP` by DM-01M6RN6GC7PJS8BGHMAYM4JSSW).
3. Silent empty return: PASS (the `List.of()` in ActivationChangeMapper is documented in its Javadoc as WP25's to serve).
4. FR coverage: PASS (each FR has a named `@WIP` scenario; WP25 records the red and the green). activationsAffected is the gap, see N2.
5. Frozen surface: PASS.
6. Locked decision: PASS for MUST-NOTs. B1 is an incomplete application of 01M6Y11E55YP5TG1EJ1MDKCFRP, not a contradiction of it.
7. Shared-file ownership: PASS with N4 (out-of-map lines present for every shared edit except those listed). WP25 owns ActivationChangeMapper next.
8. Production fragility: PASS (no new raise).
