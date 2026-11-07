---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-06T18:26:25Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 review, cycle 2 (reviewer-renata)

## What I checked
- **Gate.** I re-ran it with `--rerun-tasks`: `:kernel:check :packs:check :activations:check :deployable:check :client-java:check :contracts:check` gives BUILD SUCCESSFUL and EXIT 0. In deployable, 991 tests ran with 0 skipped, 0 failed and 0 errors. The ruling-changes and kind-model features both ran.
- **Red against #399.** I exported 8188c996 and ran `activation-ruling-changes.feature` with the bodies set to schemaVersion 1. Seven of the eight scenarios failed with real assertion failures, each for the ruling's reason:
  - A4 (two scenarios): 409 artifact_required and 422 activation_invalid;
  - B1: 422 version_not_held;
  - B5: cleared held [agent_profile:q, directive:R];
  - A14: 500 pack_sibling_conflict;
  - A15: 409 artifact_locked on OD;
  - B8: 409 governance_policy_violation.
  A10 passed there, as reported.
- **Guard probe.** I added a probe feature with one undefined step. The run fails, and the guard's AssertionError is attached as Suppressed. Note that Cucumber 8.0.4 already reports the step itself as FAILED (UndefinedStepException), so the guard works as defence in depth.
- **Scenario diffs.** I classified every changed scenario against c041c3c3:
  - The presets, register, selections and guards rewrites follow the rulings: A4, B1, B5, A7/Q2/Q3/Q4, the v2 schema, and the canonical form (`no field` in place of `0 items`).
  - None is weakened, and none is deleted without a replacement. Several are stronger: they now carry controls on a-2 and the ETag checks.
- **Contract changes.**
  - `Activation.dropped` and `droppedPins` are needed because replace answers `Activation`.
  - The mapper, the mix-in and the GraalVM hint are correct for the new members.
  - Removing `deniable` and `withDropped(List)` is safe: neither has a remaining caller.

## Required changes
**Issue 1. The A10 @WIP scenario mixes two PRs, and only one owner can remove its tag.**
- `activation-ruling-changes.feature` has one @WIP scenario that holds two halves: the PR B report (`GET …/skipped`, `requiredNotHeld`, WP22) and the PR A `DELETE` (409 `artifact_referenced`, WP19).
- WP19's prompt tells it to un-WIP "the A10 DELETE scenario". It cannot, because the report half needs PR B, which lands after PR A.
- WP22's prompt does not mention this file, so nobody would ever bind the report half.

Fix:
1. Split it into two @WIP scenarios: "A10 - an artifact another pack's preset names cannot be deleted" for WP19, and "A10 - the unheld must-have item is in requiredNotHeld" for WP22.
2. Give each scenario its own note naming its WP.
3. Name both in the fragment and in the feature header.
4. Tell the orchestrator that WP22 needs the second one added to its prompt.

**Issue 2. The client guide still lists the old generator options (FR-042).**
- `docs/guides/generate-a-client.md` gives the openapi-generator command (line ~131) and the options table (line ~217). Neither includes `containerDefaultToNull=true`.
- The guide also says "This repository's own Java client applies the same options in its build", which is now false.
- A Java consumer who follows the guide gets exactly the FR-042 defect: an absent `languages` is sent back as `[]`, which means "keep only untagged artifacts".

Fix:
1. Add `containerDefaultToNull=true` to the Java command and to the options table, with a one-line why: an absent list stays absent on the round trip, because `[]` and absent differ for `languages` and `packs`.
2. Say in the guide (or the PR A0 notes for WP09) that the getters of optional lists that are absent now return `null` instead of `[]` in every client model. For example, `ActivationChange.getDropped()` and `CharterRefusal.getViolations()` when absent. This is a visible change for callers of the client.

No WP owns the guide (WP09's owned_files leave it out), so make this edit here as a recorded out-of-map edit.

## Minor (fix now if convenient, else hand on)
- `2026-11-07-a0-ruling-changes.md` says that the 412 after eight lost attempts of a replace without If-Match leaves "the behaviour unchanged". Against #399 (8188c996) that is new: there, a replace without If-Match was an unconditional CAS and never answered 412. WP04 introduced it in this PR, so word it as a change.
- `ActivationRuntimeHintsTest.MIXED_IN` does not list `Activation`, which is now mixed in. Add it, so the hint is pinned like the others.
- The new violation code `removed_member` is named only in the ActivationConfig.yaml text. Name it in a fragment, as `unknown_member` and `duplicate_member` are named in theirs.
- The A14 scenario has no control in the same answer. A positive control would be, for example, that each enhancer's tactic is listed by the closure or the offering.
- T036 asked for the red of `activation-kind-model.feature` to be recorded before T034 and T035. Only the ruling-changes red is recorded. Add one line to the hand-off: which scenarios failed against c041c3c3, and how.
