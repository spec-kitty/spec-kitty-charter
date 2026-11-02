# Decision Moment `01M6EVZQX7RRC4CM27JG3ZD2JX`

- **Mission:** `docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09`
- **Origin flow:** `specify`
- **Slot key:** `specify.toolkit.schematic-diagrams`
- **Input key:** `schematic_diagram_rendering`
- **Status:** `resolved`
- **Created:** `2026-11-02T19:32:39.975260+00:00`
- **Resolved:** `2026-11-02T19:33:23.953630+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Schematic pages only draw their schema diagram when the docsite renders diagrams at build time against a PlantUML service; the docsite renders in the browser today, so the diagram section is left out. Should the docsite switch to build-time rendering?

## Options

- Keep browser rendering; Schematic pages without the diagram
- Switch to build-time rendering with the pinned PlantUML service
- Other

## Final answer

Switch to build-time rendering with the pinned PlantUML service

## Rationale

_(none)_

## Change log

- `2026-11-02T19:32:39.975260+00:00` — opened
- `2026-11-02T19:33:23.953630+00:00` — resolved (final_answer="Switch to build-time rendering with the pinned PlantUML service")
