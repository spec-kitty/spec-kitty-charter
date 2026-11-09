# Decision Moment `01M6Y6VE6VZQ4MXD98N93JB3GJ`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `suggestion-scope`
- **Input key:** `suggestion-scope`
- **Status:** `resolved`
- **Created:** `2026-11-08T18:31:09.787390+00:00`
- **Resolved:** `2026-11-08T18:31:12.599254+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

Which out-of-force artifacts carry EffectiveArtifact.suggestion: every artifact out of force that an active artifact suggests, or only those whose reason is not_selected?

## Options

- only not_selected (reached only through suggests, not otherwise ruled out)
- every out-of-force artifact an active artifact suggests

## Final answer

only not_selected (reached only through suggests, not otherwise ruled out)

## Rationale

Runner, 2026-11-08, from the WP24 review (F2, F3). #408 ruling 3 lists an artifact reached only through suggests as a suggestion: something the client may still choose. A denied, overridden, language-filtered or mission-type-filtered artifact is out of force for its own reason, and its reason already says why; attaching a suggestion would invite selecting what the rules exclude. enhances is not a hard relation (it pulls nothing in), so an artifact an active one enhances keeps its suggestion. One definition for the list and, in PR C, the action context.

## Change log

- `2026-11-08T18:31:09.787390+00:00` - opened
- `2026-11-08T18:31:12.599254+00:00` - resolved (final_answer="only not_selected (reached only through suggests, not otherwise ruled out)")
