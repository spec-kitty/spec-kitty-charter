# Tampered variant of `acme-review-pack` (for PR-PACK-INTEGRITY-002)

This fixture represents `acme-review-pack` whose `skills/spk-review-gate/SKILL.md` was altered
**after** the pack was pinned. The pack manifest still records the original `content_hash`, so at
resolve time the recomputed hash of the altered bytes will not match — resolution must fail closed
with `INTEGRITY_FAILURE`, naming `spk-review-gate` as the constituent that failed.

- `pack-manifest.yaml` — identical to the good `acme-review-pack/pack-manifest.yaml` (the recorded,
  pre-tamper hashes).
- `skills/spk-review-gate/SKILL.md` — altered bytes (see the file); its real hash differs from the
  `content_hash` recorded for `spk-review-gate`.

Illustrative fixture: hashes are not computed in the (not-yet-wired) harness. When the Cucumber-JVM
runner lands, the step for "one constituent's bytes no longer match its recorded content hash" binds
to this tree.
