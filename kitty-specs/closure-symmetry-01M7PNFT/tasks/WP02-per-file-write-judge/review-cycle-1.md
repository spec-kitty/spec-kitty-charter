---
affected_files: []
cycle_number: 1
mission_slug: closure-symmetry-01M7PNFT
reproduction_command:
reviewed_at: '2026-11-18T09:24:56Z'
reviewer_agent: claude-reviewer
wp_id: WP02
---

# WP02 review: changes requested (independent reviewer claude-reviewer, session_019C2Tk5VeSiEfU1VoQEpCNv)

Reviewed at fd90c34 (16c64d7 + c6b8c59, red test c166efa) against the WP02 prompt, FR-004, the operator rulings, research D4 and plan-review finding 5.

## What is right
- Both choke points judge under `exclusivelyAt` (registry lock + PackWriteLocks), before `requireClosuresCompose`, the impact check and staging. A finding is `REGISTRATION_CONFLICT` with `Finding.refusal()`.
- The store reads the pack itself for a removal (`load(pack).without(...)`), so no caller can skip the check. `Removal` is unchanged.
- `mayBreakReferences` is sound. An asset removal deletes only the asset's YAML and companions (`ArtifactFiles.of`), not payloads, so `Pack.without` keeping `assetFiles` matches the disk.
- Red-first is in history (c166efa before 16c64d7). CommitStoreClosureTest (4), ArtifactRemovalServiceTest (45), the registration services and RegistrationConcurrencyTest are all green locally.

## Required (blocking: DoD / subtask gaps)
1. **`PackWithoutTest` is missing.** It is in the DoD and owned_files. `Pack.without` is a new public domain method with a stated contract that nothing tests directly: it keeps the id, descriptor, other artifacts, asset files, presets, document and violations, and a URN of a non-artifact kind or of an absent artifact removes nothing. Please add `service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackWithoutTest.java` covering those cases.
2. **T009 control 3 is missing: "a relationship-only change is not refused".** Add it to `CommitStoreClosureTest`. Use a `Change` that carries relationships and no artifact, over a pack whose stored state does not close (or at least over the fixture), and assert the commit goes through. It is cheap, and it pins the `REFERENCE_KINDS` gate. A write of a non-reference kind (e.g. a directive) passing unjudged would be a welcome second control.

## Non-blocking
- The WP was moved to for_review with `--force` because the code landed before the claim. This is noted, and acceptable given the history.
- A removal's 422 impact check runs before the 409 judge (plan-review finding 5); this is recorded. OK.
