# Decision Moment `01M84SGTK9J0BMS9GW5QNGMTA8`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `xml_report_characters`
- **Input key:** `xml_report_characters`
- **Status:** `resolved`
- **Created:** `2026-11-23T18:08:33.385549+00:00`
- **Resolved:** `2026-11-23T18:08:36.117385+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

How does the XML report handle characters XML 1.0 cannot carry?

## Options

- Reversible escape (Recommended)
- Document exit 2

## Final answer

Reversible escape (Recommended)

## Rationale

Operator ruling R-ac (2026-11-23), source: operator (AskUserQuestion in this session), answer: "Reversible escape (Recommended)". The XML mapping escapes characters XML 1.0 cannot hold in a defined, reversible way, stated in PackDoctorReport.yaml's mapping and inverted by XmlReport.read, so pack content never makes the exit 2. Contract test with a control character.

## Change log

- `2026-11-23T18:08:33.385549+00:00` - opened
- `2026-11-23T18:08:36.117385+00:00` - resolved (final_answer="Reversible escape (Recommended)")
