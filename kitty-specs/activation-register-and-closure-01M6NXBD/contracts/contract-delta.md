# Contract delta (sketch)

The authoritative change lands in `contracts/charter/` with a fragment in `contracts/charter/changes/`. This page
lists it so the work packages and the reviewers share one view.

## PR A

- **New operation `listActivationPacks`** — `GET /activations/{activationId}/packs`.
  - `200` returns an `ActivationPackList`: `{ packs: [OfferingPack], _links }`, in dependency order; items reuse the
    offering's pack shape (`ResolvedDependency` for `dependsOn`).
  - `404 activation_not_found`.
  - `500` with an `OfferingUnreadable` composition code (`pack_*`, `stale_input`).
  - `ETag`: opaque, covering the revision and the pack fingerprint.
- **`Activation` loses `summary` and `governanceFingerprint`** (ADR-0059 D5); `summary` moves to
  `EffectiveArtifactList`.
- **`If-None-Match` / `304`** on `getActivation` and the four effective reads.
- **Absent-means-none texts** in `ActivationConfig`, `SelectedArtifacts`, `activatedKinds` and the select, unselect and
  deactivate path descriptions.
- **`CharterRefusalCode`**: `activation_not_found`, `activation_invalid`, `artifact_required` and `artifact_locked` become
  raised (create and replace refuse a denied floor artifact).
- **`ActivationConfig` descriptions:** absent and `[]` mean none (ADR-0053 d10). The `packs` text drops "a later pack
  wins" (ADR-0053 d5/d6).
- **Remove `x-reserved`** from `createActivation`, `getActivation`, `replaceActivation` and `deleteActivation` (13 → 9 reserved).
- **Pack schemas:** `pack-descriptor.schema.json` gains `mutability_policy`; the new `policy.schema.json` is added.

## PR B

- **Remove `x-reserved`** from the five per-artifact and preset writes.
- **`CharterRefusalCode`**: `artifact_not_found`, `mission_type_not_found` and `preset_not_found` become raised.
- **Refusals stay as declared:** `409 artifact_required` and `artifact_locked` with `requiredBy`;
  `404 preset_not_found`.
