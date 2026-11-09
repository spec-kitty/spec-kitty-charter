# Decision Moment `01M7058YAH0EP1JGNW5Q9TCXGX`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-c-context-conditional-entries`
- **Input key:** `pr-c-context-conditional-entries`
- **Status:** `resolved`
- **Created:** `2026-11-09T12:42:03.985070+00:00`
- **Resolved:** `2026-11-09T12:42:05.926107+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

FR-019 lists an artifact the action reaches only through suggests as conditional (when, reason). E16 restricts the context to Eff. Does the context list an unselected suggested artifact?

## Options

- no: the context lists in-force artifacts only (E16); an in-force entry reached only through suggests is conditional, from the walk edge when/reason
- yes: list unselected suggestions as conditional entries

## Final answer

no: the context lists in-force artifacts only (E16); an in-force entry reached only through suggests is conditional, from the walk edge when/reason

## Rationale

Runner after WP27 (implementer's open question): E16 restricts the walk to Eff and FR-019 says the context answers in-force artifacts; #408 ruling 3 marks how an entry is reached, not whether an unselected one is listed. The listing's suggestion carrier (01M6Y6VE6VZQ4MXD98N93JB3GJ) applies to not_selected artifacts in the effective listing, not to context entries; WP28 maps conditional from the walk's per-entry when/reason.

## Change log

- `2026-11-09T12:42:03.985070+00:00` - opened
- `2026-11-09T12:42:05.926107+00:00` - resolved (final_answer="no: the context lists in-force artifacts only (E16); an in-force entry reached only through suggests is conditional, from the walk edge when/reason")
