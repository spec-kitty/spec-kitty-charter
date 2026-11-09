# Decision Moment `01M6Y3GZFRC5WEQQ7G6GT0YMW3`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `activation-if-match-tags`
- **Input key:** `activation-if-match-tags`
- **Status:** `resolved`
- **Created:** `2026-11-08T17:33:01.304697+00:00`
- **Resolved:** `2026-11-08T17:33:03.868481+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

getActivation's ETag now covers the closure (01M6Y11E55YP5TG1EJ1MDKCFRP) while write answers keep the bare revision. What does If-Match on activation writes match: either tag, the closure tag only, or the revision only?

## Options

- either tag: the quoted revision or the current getActivation tag
- closure tag only
- revision only

## Final answer

either tag: the quoted revision or the current getActivation tag

## Rationale

Operator, 2026-11-08. Activation writes (PUT, POST, DELETE and the sub-resource writes) accept If-Match carrying the quoted revision or the CURRENT getActivation tag (revision plus closure governanceFingerprint); a closure tag that no longer matches is 412. Write answers keep the revision ETag and do not carry denyNotApplied. WP22 declares it (IfMatch, write descriptions, Activation, Breaking entry, a @WIP GET-then-conditional-PUT scenario), WP25 serves it and updates the 17 scenarios that replay the GET tag, WP26 amends ADR-0020 D6 and ADR-0059 D5.

## Change log

- `2026-11-08T17:33:01.304697+00:00` - opened
- `2026-11-08T17:33:03.868481+00:00` - resolved (final_answer="either tag: the quoted revision or the current getActivation tag")
