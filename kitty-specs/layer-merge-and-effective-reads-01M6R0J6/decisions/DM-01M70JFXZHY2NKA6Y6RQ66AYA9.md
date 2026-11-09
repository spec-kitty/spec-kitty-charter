# Decision Moment `01M70JFXZHY2NKA6Y6RQ66AYA9`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp29-ic13-fragment-section`
- **Input key:** `wp29-ic13-fragment-section`
- **Status:** `resolved`
- **Created:** `2026-11-09T16:33:04.497354+00:00`
- **Resolved:** `2026-11-09T16:33:17.212883+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

WP29 review MINOR-1: the IC-13 fragment (2026-11-07-c-deny-satisfied-by-override.md) uses ### Changed, but a documented 409 artifact_required becomes 200/201 for the same input; the mission's precedent files a refusal-becomes-success as ### Breaking. Which section?

## Options

- keep ### Changed: the WP, contract-delta and the operator handover rule it; the response shapes do not change; the fragment says plainly that a documented 409 artifact_required now succeeds when an active override satisfies the requirement
- change to ### Breaking: a documented 409 becomes 200/201 for the same input

## Final answer

keep ### Changed: the WP, contract-delta and the operator handover rule it; the response shapes do not change; the fragment says plainly that a documented 409 artifact_required now succeeds when an active override satisfies the requirement

## Rationale

Runner: the planning artefacts (WP29, contract-delta 'The relaxed write rule (IC-13)') and the operator's PR C handover (### Changed list: deny satisfied by override IC-13) all file IC-13 as Changed: the write accepts more, no response shape changes, and oasdiff does not flag it. The a0 Breaking precedent was a 422 becoming a silent drop (data a client sent is no longer stored), which a client must react to; here a client that handled 409 keeps working. The fragment is amended in the WP29 fix cycle to state the relaxed 409 explicitly and that core's in-process backend still refuses (DIV-204), so a reader sees the behaviour change without the section.

## Change log

- `2026-11-09T16:33:04.497354+00:00` - opened
- `2026-11-09T16:33:17.212883+00:00` - resolved (final_answer="keep ### Changed: the WP, contract-delta and the operator handover rule it; the response shapes do not change; the fragment says plainly that a documented 409 artifact_required now succeeds when an active override satisfies the requirement")
