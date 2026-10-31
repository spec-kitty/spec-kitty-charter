# Draft issue: build the activation writes (ADR-0020 D3, D6)

**Title:** Build the activation writes: the activation document, its storage and the nine write
operations reserved by #125

## Why

#125 declared the activation set in the contract and reserved every operation (`x-reserved`,
`501`, ADR-0020 D8). Core's seam codes against it now; the service must answer it before the
first activation route is switched (ADR-0021 D5, ADR-0022 D6). The four effective reads are
#103's (R9); this issue is the nine writes.

## What

- An `ActivationRepository` port in the storage ring and an in-memory and a file adapter (ADR-0020
  D2, ADR-0005 D4), and an `activations` slice module (ADR-0006; generalize the `PACKS_*`
  ArchUnit constants, #140).
- `createActivation`, `replaceActivation`, `getActivation`, `deleteActivation`,
  `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
  `reactivateActivationArtifact`, `applyActivationPreset`: each built removes its `x-reserved`,
  its `501` and its 501 pin, and binds the proposed functional scenarios of
  `docs/architecture/offering-and-activation.md`.
- Canonical form and the JCS revision (ADR-0020 D4); `ETag`, `If-Match`, `If-None-Match`,
  `412`, `428` (D3, D6, D9).
- The refusals become raised: `activation_not_found`, `artifact_not_found`, `preset_not_found`,
  `artifact_required`, `artifact_locked`, `activation_invalid`, with `requiredBy` and
  `violations`; delete their declaration pins and `ReservedRefusalCodesTest` entries.
- Build notes from #125 (research D-15): a String-to-enum converter for snake_case path kinds;
  unknown JSON members on activation bodies refused (422, never silently dropped); a body that
  fails bean validation answered `422 activation_invalid` with `violations`; the `NON_NULL`
  mix-in keeps absent apart from `[]`.
- `Link.method` gains `DELETE` for the remove, unselect and re-activate relations.

## Acceptance

- Every write answers as declared; the round trip (a `PUT` of a fetched `config` answers `200`
  with the same `ETag`) is pinned by a scenario and a property test (ADR-0020 D6).
- `verifyAll` green.

Parent: #44. Related: #125, #103, #140.
