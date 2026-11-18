---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-17T05:57:20Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP05
---

# WP05 review, cycle 1: changes requested

Reviewer: `claude::reviewer-renata`, independent. The reviewer ran `verifyAll -PcontractBaseline=none` and `:pack-validation:wholePackRoundTrip` on the final head; both are green.

These checks held:
- The golden changed only for T059.
- No ArchUnit rule changed.
- The port types, `readTree` and the purity checks are in place.
- The five load paths run `StoreRead` over one walk.
- The S2 prechecks and the F4 root behave as specified.
- Open case 9's two named cases hold, and the repointed tests were not weakened.

Two MAJOR findings block approval.

## Must fix

### M1. An orphan companion is held, committed and left out of the revision digest (data risk)

`FileSystemPackInspector.java:491` (`case PackFileKind.Mapped mapped -> {}`) treats every mapped file as held. Take a `toolguides/x.guide.md` that no toolguide names:
- Nothing refuses it or warns about it: `StoreRead.untaken` covers only enhancements and fragments.
- `removeNotHeld` keeps it, so it is committed.
- The aggregate never loads it.
- `PackFileSet.digested` leaves it out, so the revision does not move when the file changes.

Before WP05, registration removed it as not held, with a warning. Restore that.

- In the census, list a mapped file that is neither in the digested set nor a prompt fragment as not held, with the kind `UNNAMED` and the reason "no artifact of the pack names it". The orphan prompt fragment keeps `prompt_fragment_invalid`, unchanged.
- Validate mode's read skips it too, so both surfaces warn about it with `file_not_held`.
- Add plants to `HeldSetAgreementTest`: an orphan guide and an orphan step prompt.
- Update the R2 note: remove the "now held" side effect and record the restored behaviour.

### M2. `CompositionFactsBuilder.underResolution` is a second read and assembly path

`CompositionFactsBuilder.java:69-97` runs its own `store.readGraphs`, `store.read`, `LoadChecks.assembly(...).build()` and `PolicyYaml.readNamed`. A `build()` breach there throws a bare `ConstraintBreachException`, while the compose read answers `Refused(validation_failed)`. `CompositionFactsBuilderTest` is a parity check between two paths, which T027 forbids.

- Run `StoreRead.of(dir, name, PackFileSet.walk(...), …)` in `underResolution`.
- Move the compose read's mapping into one `CompositionFactsBuilder` method that takes a `StoreRead`. That mapping is `DomainRepositoryService.java:493-517`: the graph refusal, then `Refused`, `requireReadable`, the policy and `factsRefusal`. The compose read and `underResolution` both call it.
- Rewrite `CompositionFactsBuilderTest` per T027: the facts over every example pack equal the ones the golden composition answers were built from. It is not a comparison between two builders.
- Cover the path in `OneReadTest`.

## Fold in this cycle

1. **MINOR-1.** `OneReadTest.java:34`, `theRegistrationLoadReadsOnce`: also assert `PackFileSet.walks()` and `StoreRead.runs()`.
2. **MINOR-3.** `WholePackCensus.java:83-100` still walks and classifies on its own. Point it at the `TreeCensus` (T025). If that cannot be done behaviour-preserving in this WP, stop and tell the orchestrator; do not defer it silently.
3. **MINOR-4.** Add the T026 test for an existing directory that cannot be listed, through a failing-walk seam: the container runs as root, so `chmod` will not block the listing.
4. **MINOR-5.** Route `PackManifestDerivation.derivedAt` (`:88-112`) through `StoreRead`, so the stamp and the census derive the manifest by one path (A-D1).
5. **NIT (mime tests).** In `FileSystemPackInspectorTest`, assert the moved MIME tests through `readTree(...).readFaults()`, not through a `PackFinding` the `mime()` helper builds itself.

## Orchestrator-held

- **MINOR-2:** `LoadChecks` gets a deletion owner in WP12. The orchestrator adds it.
- **MINOR-6:** the orchestrator records open case 9's `UNNAMED` kind in the planning artifacts after this cycle.
- **`PackContents.root`** (a host locator) stays until WP09, as recorded.

## Exit

- `verifyAll -PcontractBaseline=none` and `:pack-validation:wholePackRoundTrip` are green.
- The golden is unchanged apart from T059. Explain any new row.
- Commits are signed, end `(#554)` and carry no trailer.
