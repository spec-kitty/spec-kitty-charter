---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T11:16:37Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP11
---

# WP11 review, cycle 1 (reviewer-renata): changes requested

Gate: `syncPacks` then `:contracts:check :packs:check :deployable:check :client-java:check` with `-PcontractBaseline=none`
passes (BUILD SUCCESSFUL, `deployable:test` executed). Most of the contract is right (see "What passes" below). It is
rejected for one defect: an existing guard was weakened so the gate would pass, and that leaves a false contract claim
that nothing checks.

## Issue 1 (blocking): `ReservedOperationCoverageTest` is weakened, and the seven reserved operations do not answer 501

The seven new operations carry `x-reserved` (contract set `pack-registration`), and the tag text and the fragment say
each "answers 501 until it is built (ADR-0020 D8)". No main class implements the generated `RegistrationApi`
(`grep -rn RegistrationApi service --include=*.java` outside `build/` finds nothing), and nothing serves
`/packs`, `/pack-registrations/{id}`, `PUT /packs/{packId}` or the two DELETEs. So the service answers these requests
with the router's 404 or 405, not 501. The contract claims behaviour the service does not have.

ADR-0020 D8 says each reserved operation also (a) is implemented by one controller that answers 501 and (b) is pinned
by a `@contract @reserved` scenario that it answers 501. `ReservedOperationCoverageTest` enforced (b) for every
`x-reserved` operation, and that check is exactly what would have found the missing controller. The new
`isActivationSet` filter in `reserved()` removes the seven operations from **both** checks of the class:

- `everyReservedOperationIsPinnedAndEveryPinCallsAReservedOperation`: the new operations need no pin;
- `noReservedOperationTakesABody`: `PUT /packs/{packId}` (a binary body of up to 32 MiB) and `POST /packs` both declare
  a `requestBody`. The test exists to bring back the "501 before the body is bound" pin when that happens, and it is
  now skipped for them. That is the case where the pin matters most.

Before this change the guard covered every reserved operation, so this narrows an existing guard to make the gate
green. The reviewer guidance says no test may be weakened to pass.

Fix (any of these shapes is acceptable, as long as no guard is narrowed):

1. Keep the guard general and key it by contract set. For each `x-reserved` `contractSet`, a pin file:
   `activation` → `activations.feature`, `pack-registration` → a non-`@WIP` file such as
   `scenarios/registration/reserved-registration.feature`. Pins and reserved operations must still match one to one for
   each set. Keep `theActivationSetReservesFourOperations` and add a count for the new set (7). Apply
   `noReservedOperationTakesABody` to every set, or replace it, for the new set, with the "501 before the body is bound"
   pin for the PUT (an unbindable or oversized body still answers 501) and for the POST.
2. Make the 501 real: a controller implementing `RegistrationApi`, marked `@ReservedOperation` at type level, in
   `packs.infra.api`. Name it after WP16's `PackRegistrationController` so WP16, WP17, WP18 and WP19 build it by removing
   marks. This file belongs to WP16. Record the shared-file coordination in the hand-off, or ask the planner to
   reassign it.
3. The pins are `@contract @reserved` scenarios, not `@WIP`. They do not count toward NFR-002 or ADR-0059 D1, and each
   serving WP deletes its pin when it removes `x-reserved`, as the activation set did.

## Issue 2 (should fix in this cycle): `getPackRegistration` declares and describes the fingerprint against decision DM-01M6TV6VDNHNCVWRHHC74WATV0

The decision rules that the status 200 carries no `Charter-Source-Fingerprint`. The contract does the opposite in
three places:

- `paths/pack-registrations_registrationId.yaml` declares the header on the 200;
- the operation description says "The answer is verified against the source fingerprint and carries it";
- the Added fragment says "It is a verified read: it declares `Charter-Source-Fingerprint`, as the lint rule for every
  `GET` `200` requires".

The decision gives the lint exemption to WP16. This WP already edits `contracts/lint/ruleset.yaml` (the 202
exemption), so the clean fix is to do it here:

- add `/pack-registrations/{registrationId}` to both `fingerprint-on-verified-reads` and
  `fingerprint-only-on-verified-reads`, in the given list (the way the activation reads are);
- drop the header and the sentence;
- correct the fragment.

If you leave it to WP16, say so in the hand-off. WP16's T076 text mentions only the lint list, so its prompt must also
name the header, the description sentence and the fragment line.

## Issue 3 (should fix): shared-file edits need their rationale

These files are outside `owned_files` and the move-task note gives no coordination rationale for them:

- `RefusalHandler.java`
- `ReservedOperationCoverageTest.java`
- `LinkChecksTest.java`
- `contracts/lint/ruleset.yaml`
- `PackRegistrationJson.java` and `PackRegistrationJsonTest.java`
- `responses/PackPreconditionFailed.yaml` and `responses/PackRegistrationUnavailable.yaml`
- the examples
- `PackRegistrationNotices`, `PackRegistrationProblem` and `PackSourceRecord`
- `scenarios/README.md`
- the `-breaking` and `-changes` fragments

Most are needed: the exhaustive switch in `RefusalHandler`, the `LinkChecksTest` negative case that `getPack` now
answers, the 202 lint rule, the NullOmission mix-in, and one block per fragment. State this in the hand-off (checklist
item 7).

## Nits (fix if touched, or carry as follow-ups)

- `PackSource.url`: contract-delta says `format: uri` plus `^https://`. Only the pattern is declared.
- IC-14 says a succeeded status carries `_links.pack`. The succeeded example has only `_links.self` on the status, and
  the pack's link is inside `result._links.self`. Add `pack` (if the relation table admits it) or record the deviation.
- Several `x-refusal.operations` entries repeat the entry's own status. Examples: `pack_version_conflict` →
  `getPackRegistration: 409`, `pack_source_unavailable` → `503`, `registration_conflict` and `validation_failed` →
  `getPackRegistration`. The description defines `operations` as a status *override*, so these entries are noise.
  Either drop them or say in the `x-refusal` text that an entry may also mark where a code is answered.
- `override_not_permitted` scenario (US1 AS8 says "naming both packs"): it asserts only `packs/0/packId == acme`. Also
  assert that the locking pack (`main-pack`) is named. AS8 is about a built-in lock, and the fixture uses an org lock.
  Say so in the comment.
- `# bound by: WP15` on the version-conflict scenario and on the three IC-15 scenarios: WP15 precedes WP16, which
  builds the PUT, so WP15 cannot run them through HTTP. WP16's T081 says it binds "the PUT-path scenarios". Write
  "bound by: WP16 (rules of WP15)", or raise the sequencing with the planner.
- `deletePackArtifact` answers `artifact_not_found` for a missing id, while `getPackArtifact` on the same path answers
  the per-kind code (`directive_not_found`). This follows the plan, but one resource now has two not-found codes. Say
  so in the description.
- `Link.yaml` still says "a pack or artifact write is never DELETE (ADR-0026 D2)". The fragment says it "is to
  follow". Name who updates it (WP19 or WP20).
- The spec edge case "a registration queue that is full: `503` with `Retry-After`" has no scenario. WP16's
  deterministic test covers it, so add a line to the feature header saying so.

## What passes

- Status codes per operation match IC-02, IC-06, IC-14 and contract-delta:
  - PUT and POST: 202 with `Location` and a `PackRegistrationStatus` body, plus 400/401/403/412/413/415/503;
  - 428 only on the POST, for the git branch-ref rule;
  - 409 and 422 only in a `refused` status;
  - the DELETEs answer 204 with `ETag`, per-code 404s, 409, strict 422 (DM-...TQ67), 412 and 428;
  - `relationship_not_registered` covers authored, derived and both-held edges (DM-...TQ62, DM-...TR1K (2));
  - the artifact DELETE answers `registration_conflict` (DM-...TR1K (1)).
- `x-refusal` placement on `getPackRegistration`, stated in the code text. `registration_timed_out` is 504 and
  `pack_storage_full` is 503, both retryable.
- `IfMatch.yaml` is extended without a new parameter and stays `required: false`, with the 428 stated in each DELETE.
  That is what the plan's finding 14 asks for, and it matches the activation sub-resource precedent.
- The 400 for a bad `packId` or `registrationId` matches DM-...TV6V (2).
- The 14 codes are `raised: false`, as `ReservedRefusalCodesTest` requires (no main class may name them) and as
  `RefusalExamples` documents for reserved operations. `RefusalHandler.statusOf` is right for every code.
- Fragments: Breaking holds the PR-PACK-REGISTER-003 reversal, the retirement of `register-pack.feature` and the async
  201 to 202 change. The `IfMatch.yaml` description change is Changed: not breaking, since the pattern and `required`
  are unchanged. Each file has one block and nothing is duplicated.
- The 202 exemption in the lint rule is sound. `success-schemas-carry-links` still requires `_links` on the 202.
- Scenarios cover US1 AS1 to AS7, AS9 (floor; `activationsAffected` arrives in PR B), US2 AS1 to AS4, the level edge
  case, the finding-3 load refusals, the decisions, 401 and 503. Absences are paired with positive controls.
- The governance REMOVE omission is justified in the file header (unreachable through the API, pinned by WP19's unit
  tests).
- The schema split (`PackSourceRecord`, `PackRegistrationNotices`, `PackRegistrationProblem`, with code optional for
  the 412, 502 and 504) and the `PackRegistrationJson` mix-in with its native hints are sound.
