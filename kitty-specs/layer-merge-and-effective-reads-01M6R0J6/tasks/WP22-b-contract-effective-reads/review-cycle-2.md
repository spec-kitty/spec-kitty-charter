---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP22 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T17:47:50Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP22
---

Approved by claude:opus:reviewer-renata: approval:WP22

# WP22 review, cycle 2: approved

Reviewer: claude:opus:reviewer-renata (profile reviewer-renata). Lane-t HEAD 8ea6412a; base ffd1aa1e. Cycle 2 commit
reviewed: fbf5a4de (the only commit touching `contracts`/`service` since cycle 1's a282de30; the later merge changes
neither).

## Commands run

- `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :contracts:check`:
  BUILD SUCCESSFUL. 54 changelog fragments well formed (55 in cycle 1, minus the folded `2026-11-07-b-activation-etag.md`).
- The same flags with `:deployable:test`: 1132 tests, 1 failed. The one failure is the accepted R-20
  (`CharterContractSuite > Cucumber > executionError`: "No scenario exercises these success answers of the contract
  (R-20)": `GET /activations/{activationId}/artifacts 200`, `GET /activations/{activationId}/skipped 200`). Nothing else.

## Cycle 1 blockers

- **B1: fixed.** Decision 01M6Y3GZFRC5WEQQ7G6GT0YMW3 is declared in `parameters/IfMatch.yaml` (either tag, stale closure
  tag 412, write answers keep the revision and carry no `denyNotApplied`); in replaceActivation (the "same ETag" sentence
  now says "the same revision as its ETag, and it may carry the fetched getActivation tag as If-Match"), deleteActivation,
  select/unselect, deactivate/re-activate and applyActivationPreset; in getActivation ("only this read answers the
  closure tag and denyNotApplied"); in `schemas/Activation.yaml`; and in the Breaking block. createActivation (POST)
  takes no If-Match parameter, so the decision's "POST" has nothing to declare. Both tags share the pattern
  `^"sha256:[0-9a-f]{64}"$`, so the closure tag passes the If-Match pattern. The new `@WIP` scenario "B4 - a write takes
  the getActivation tag or the revision as If-Match, and a stale closure tag is 412" does GET, PUT with the GET tag (200,
  ETag is the quoted `/revision`, `/denyNotApplied` absent), PUT with the revision (200), re-registers acme (a closure
  pack) with the same body so the revision is unchanged, PUT with the old GET tag (412), then a fresh GET tag (200, the
  positive control). I checked the glue: `I remember the ETag` (RegistrationSteps.rememberedETag) and
  `I remember the header "ETag"` (CharterSteps.remembered) are separate stores, so the 412 step really sends the stale
  GET tag. Only the `PUT ... with If-Match the remembered header "ETag"` step is new glue (documented in the header
  comment); WP25 binds it.
- **B2: fixed.** The implementer's claim holds: `ChangelogFragments.parse` (contracts/src/main/java/.../compat/ChangelogFragments.java:160-181)
  requires a file to start with one `###` heading and refuses any `#`/`##`/`###` heading under it ("must hold one `###`
  block"). The layout is therefore three files (`-added.md` `### Added`, `-changed.md` `### Changed`,
  `2026-11-07-b-effective-reads.md` the single `### Breaking`). The Breaking block now holds `denyNotApplied` and
  `activationsAffected` (closed responses, strict clients regenerate, A0 precedent), the getActivation ETag change (and
  its new 500), and the If-Match rule.
- **B3: fixed.** getActivation's `500` is `../responses/OfferingUnreadable.yaml`.

## Cycle 1 non-blockers

- N1 done (EffectiveETag follows the closure's packs; a pack outside it leaves it unchanged; names getActivation).
- N2 done (`@WIP` "B4 - a registration that changes an activation's in-force set lists it in activationsAffected, and
  not an unrelated one": `holds exactly the ids: a-1`, with a-2 shown to exist and be in force).
- N3 done (skipped report 400 = activationId outside the pattern).
- N4 done (out-of-map lines in fbf5a4de, including the d3449d9c example).
- N5 acceptable. The domain edge (`kernel/domain/Relationship`) holds `when` and `rationale` as `Optional`, so an edge
  can state neither; requiring `reason` would force WP25 to invent text. The description says `{}` is possible and the
  artifact is still a suggestion.

## New findings (non-blocking; fold into WP25's contract touch or the closeout)

- N6 (low, wording): the 400 sentences still say "an If-Match that is neither a quoted revision nor *"
  (replaceActivation `paths/activations_activationId.yaml:61`, deleteActivation `:147`) or "neither * nor one quoted
  revision breaks its pattern" (the five sub-resource writes). Read literally, a closure tag is "neither". The pattern is
  the same for both tags, so no server or client is misled, but say "neither * nor one quoted tag (the revision or the
  getActivation tag)".
- N7 (typo): the inserted clause ends in `),.` in four descriptions: deactivate PUT
  (`activations_activationId_exclusions_kind_artifactId.yaml:17`), re-activate DELETE (`:94`), applyActivationPreset
  (`activations_activationId_presets_presetId.yaml:23`), unselect DELETE (`activations_activationId_selections_kind_artifactId.yaml:91`).
- N8 (low): `headers/EffectiveETag.yaml` names "the two effective reads and getActivation", but the reserved graph and
  action-context reads also reference it.
- N9 (fixture noise): the B4 If-Match scenario declares `other-v1`/`other-v2`, and the activationsAffected scenario
  declares `acme-v2`/`other-v2`. None of them is used. Harmless.
- N10 (hand-off notes): `research/closeout-notes-pr-b.md` (planning checkout) still points to the removed
  `2026-11-07-b-activation-etag.md`. It also lists only "scenarios that assert the ETag equals the revision", not the
  classification below. The runner should update it, because a lane cannot edit it.

## Existing non-@WIP scenarios that touch getActivation's ETag (hand-off to WP25)

Found by a script over every non-`@WIP` scenario (the last request is `GET /activations/{id}`, and the step asserts,
compares or replays its tag), then checked by hand. 28 scenarios in total, and every one needs a WP25 edit.

### A. Asserts the GET tag equals the revision: contradicts the contract, WP25 must update (27 scenarios)

A1. `the response header "ETag" is the stored ETag` right after a GET (17 scenarios, cycle 1's 17):
- activation-cascade-depth.feature:450, 467, 484, 501, 518, 535
- activation-kind-model.feature:298, 319, 393
- activation-presets.feature:371, 411, 448, 470
- activation-selections.feature:749, 1273, 1289, 1492

These are "stores nothing" checks. Assert the revision through the body (`/revision` equals the stored revision), not
through the GET tag.

A2. The GET tag is compared with a write's tag (`is the quoted field "/revision"` or `is the remembered header` across a
GET and a write) (8 scenarios):
- activation-register.feature:75 and :83 ("A fetched config registered unchanged answers 200 with the same ETag")
- activation-register.feature:106 ("A stale If-Match is refused and the current revision is kept")
- activation-register.feature:140 (the second check, after `GET /activations/a-2`)
- activation-register.feature:666 (the PUT's ETag against the GET tag; :677 is GET against GET with no closure change,
  so it stays valid)
- activation-register-guards.feature:69
- activation-kind-model.feature:447 and :499
- activation-ruling-changes.feature:185 (A4)

A3. The revision is sent as If-None-Match to getActivation and 304 is expected (2 scenarios). The revision no longer
matches the strong closure tag, so the answer is 200:
- activation-register.feature:414 (and :416, which compares the 304's tag with the write tag)
- activation-register.feature:571. This one also deletes a closure pack file before the conditional GET, so the expected
  304 is wrong twice over.

### B. Replays the GET tag as If-Match with no closure change in between: stays valid under "either tag"

- activation-register.feature:78 and :661
- activation-kind-model.feature:442, :449 and :494
- activation-ruling-changes.feature:180

Only their ETag-equality assertions (A2) change. The If-Match replays keep working.

### C. Replays the GET tag across a closure change: now 412, contradicts, WP25 must update (1 scenario)

- activation-ruling-changes.feature:212 ("A4 - a PUT of the body read before a pack upgrade removed an id drops the id
  and lists it in dropped"). It GETs, deletes `directives/X.directive.yaml` from pack `main` (a closure pack), then PUTs
  with the remembered GET tag and expects 200. Under 01M6Y3GZFRC5WEQQ7G6GT0YMW3 that stale closure tag is 412. Keep
  the A4 intent by sending the revision (the stored ETag) or re-GETting before the PUT. Do not drop the 412 rule.

### Weakened, not contradicted

- activation-kind-model.feature:454 (`differs from the remembered header`, a GET tag against a write tag) is now always
  true. Compare with the first PUT's ETag to keep the check strong.

Totals: 28 contradicting scenarios (27 in A, which is 17 + 8 + 2, and 1 in C). The 6 valid replay steps (B) all sit
in A2 scenarios, so only their equality assertions change. There is 1 weakened check. The implementer counted 26
candidates and cycle 1 counted 17 (A1 only). WP25 should work from this list. The A3 If-None-Match cases and the C
case are the ones the earlier counts most likely missed.

## Anti-pattern checklist

1. Dead code: PASS. 2. Synthetic-fixture test: PASS (`@WIP` scenarios). 3. Silent empty return: PASS. 4. FR coverage:
PASS (activationsAffected now has a scenario). 5. Frozen surface: PASS. 6. Locked decision: PASS (01M6Y11E…, 01M6Y3GZ…,
01M6Y0X5… all applied and not re-litigated). 7. Shared-file ownership: PASS (out-of-map lines complete). 8. Production
fragility: PASS.
