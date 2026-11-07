### Breaking

- `replaceActivation` drops instead of refusing what the packs no longer hold in the document it replaces (A4,
  decision `DM-01M6RA7XC5HC8915GZT2R3H83W`): an id of the body the closure no longer holds that the replaced document
  named is dropped and listed in the answer's new `dropped`, where it was `422 activation_invalid`
  (`unknown_artifact`); an unknown id the replaced document did not name is still `422`. Its description no longer
  says that the write never drops an id the client sent.
- `replaceActivation` drops a version pin no pack of the closure holds and lists it in the answer's new
  `droppedPins`, where it was `422 version_not_held` (B1, decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`; the per-artifact
  writes and `applyActivationPreset` report theirs in `ActivationChange.droppedPins`, as
  `2026-11-07-a0-one-kind-model.md` says). `createActivation` keeps `422 version_not_held` for a pin in its own body.
- `Activation` gains two optional members, on a `replaceActivation` answer (`200` or `201`) only and only when the
  write dropped something: `dropped` (`ArtifactReference[]`, sorted) and `droppedPins` (`PackPin[]`, each as the body
  wrote it). A read never carries them, so its bytes still depend on the stored document alone (ADR-0059 D5).
  `Activation` is closed, so a strict client must regenerate before it reads a replace answer that dropped something.
- Core's seam: core regenerates its strict DTOs from this contract (as for the one kind model), reads `dropped` and
  `droppedPins` on a replace answer instead of handling a `422`, and treats `config` as the document stored. Nothing
  is edited in core by this change (C-003).
