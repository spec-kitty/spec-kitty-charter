# Decision Moment `01M6Y11E55YP5TG1EJ1MDKCFRP`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `getactivation-etag-deny-not-applied`
- **Input key:** `getactivation-etag-deny-not-applied`
- **Status:** `resolved`
- **Created:** `2026-11-08T16:49:34.885834+00:00`
- **Resolved:** `2026-11-08T16:49:37.647902+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

Activation.denyNotApplied (IC-17 B4) depends on the packs, while ADR-0059 D5 makes getActivation's strong ETag the revision alone. Drop the field from Activation, make the tag cover the packs, or accept a stale tag?

## Options

- drop it from Activation (skipped report only)
- tag covers the packs (revision + closure fingerprint); amend ADR-0059 D5
- accept a stale tag

## Final answer

tag covers the packs (revision + closure fingerprint); amend ADR-0059 D5

## Rationale

Operator, 2026-11-08. getActivation keeps denyNotApplied; its strong ETag covers the revision and the closure's governanceFingerprint, as the effective reads' tags do, so the tag changes with the bytes (RFC 9110). WP22 declares it (### Breaking: the ETag of getActivation is no longer the bare revision), WP25 serves it (T128), WP26 amends ADR-0059 D5 and the WP09 note that getActivation carries no fingerprint.

## Change log

- `2026-11-08T16:49:34.885834+00:00` - opened
- `2026-11-08T16:49:37.647902+00:00` - resolved (final_answer="tag covers the packs (revision + closure fingerprint); amend ADR-0059 D5")
